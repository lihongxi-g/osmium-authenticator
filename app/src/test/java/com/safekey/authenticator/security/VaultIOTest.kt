package com.safekey.authenticator.security

import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.model.VaultFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultIOTest {

    private val password = "correct-horse-battery".toCharArray()

    private fun sampleVault() = VaultFile(
        version = 1,
        format = "osmium-vault",
        exportedAt = 123456789L,
        accounts = listOf(
            VaultAccount(issuer = "Google", label = "a@gmail.com", secret = "JBSWY3DPEHPK3PXP")
        )
    )

    @Test
    fun `encrypt then decrypt round-trips the vault`() {
        val envelope = VaultIO.encrypt(sampleVault(), password)
        val vault = VaultIO.decrypt(envelope.toByteArray(Charsets.UTF_8), password)
        assertEquals(1, vault.accounts.size)
        assertEquals("Google", vault.accounts[0].issuer)
        assertEquals("JBSWY3DPEHPK3PXP", vault.accounts[0].secret)
    }

    @Test
    fun `wrong password is flagged as wrongPassword`() {
        val envelope = VaultIO.encrypt(sampleVault(), password)
        try {
            VaultIO.decrypt(envelope.toByteArray(Charsets.UTF_8), "wrong-password".toCharArray())
            fail("expected VaultFormatException")
        } catch (e: VaultFormatException) {
            assertTrue(e.wrongPassword)
        }
    }

    @Test
    fun `decrypted payload that is not a vault is flagged as format error`() {
        // A valid encrypted envelope whose plaintext is not an Osmium vault.
        val envelope = VaultCrypto.encrypt("{\"format\":\"something-else\"}", password)
        try {
            VaultIO.decrypt(envelope.toByteArray(Charsets.UTF_8), password)
            fail("expected VaultFormatException")
        } catch (e: VaultFormatException) {
            assertFalse(e.wrongPassword)
        }
    }

    @Test
    fun `oversized payload is rejected as a format error, not an OOM`() {
        val huge = ByteArray(VaultIO.MAX_PAYLOAD_BYTES + 1)
        try {
            VaultIO.decrypt(huge, password)
            fail("expected VaultFormatException")
        } catch (e: VaultFormatException) {
            assertFalse(e.wrongPassword)
        }
    }

    @Test
    fun `tags survive an encrypt-decrypt round trip`() {
        val vault = VaultFile(
            version = 2,
            format = "osmium-vault",
            exportedAt = 123456789L,
            accounts = listOf(
                VaultAccount(issuer = "Google", label = "a@gmail.com", secret = "JBSWY3DPEHPK3PXP", tagIds = listOf("t1"))
            ),
            tags = listOf(com.safekey.authenticator.model.VaultTag("t1", "Work", "blue"))
        )
        val envelope = VaultIO.encrypt(vault, password)
        val restored = VaultIO.decrypt(envelope.toByteArray(Charsets.UTF_8), password)
        assertEquals(1, restored.tags.size)
        assertEquals("Work", restored.tags[0].name)
        assertEquals(listOf("t1"), restored.accounts[0].tagIds)
    }

    @Test
    fun `plaintext export round-trips through decodePlain`() {
        val json = VaultIO.encodePlain(sampleVault())
        val vault = VaultIO.decodePlain(json.toByteArray(Charsets.UTF_8))
        assertEquals(1, vault.accounts.size)
        assertEquals("Google", vault.accounts[0].issuer)
        assertEquals("JBSWY3DPEHPK3PXP", vault.accounts[0].secret)
    }

    @Test
    fun `decodePlain rejects an encrypted envelope`() {
        val envelope = VaultIO.encrypt(sampleVault(), password)
        try {
            VaultIO.decodePlain(envelope.toByteArray(Charsets.UTF_8))
            fail("expected VaultFormatException")
        } catch (e: VaultFormatException) {
            assertFalse(e.wrongPassword)
        }
    }

    @Test
    fun `decodePlain rejects json without the vault marker instead of returning an empty vault`() {
        // Every VaultFile field has a default, so plain JSON such as {} would
        // silently decode into an empty vault if the magic fields were not checked.
        for (bad in listOf("{}", "{\"hello\":1}", "[]", "not json at all")) {
            try {
                VaultIO.decodePlain(bad.toByteArray(Charsets.UTF_8))
                fail("expected VaultFormatException for: $bad")
            } catch (e: VaultFormatException) {
                assertFalse(e.wrongPassword)
            }
        }
    }

    @Test
    fun `isPlainVault recognizes plaintext files only`() {
        val plain = VaultIO.encodePlain(sampleVault()).toByteArray(Charsets.UTF_8)
        val envelope = VaultIO.encrypt(sampleVault(), password).toByteArray(Charsets.UTF_8)
        assertTrue(VaultIO.isPlainVault(plain))
        assertFalse(VaultIO.isPlainVault(envelope))
        assertFalse(VaultIO.isPlainVault(ByteArray(0)))
    }
}
