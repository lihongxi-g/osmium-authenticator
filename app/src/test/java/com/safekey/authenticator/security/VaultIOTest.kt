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
}
