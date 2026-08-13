package com.safekey.authenticator.security

import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.model.VaultFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCryptoTest {

    private val json = Json { encodeDefaults = true }

    private fun sampleVault(): String {
        val vault = VaultFile(
            exportedAt = 1234567890L,
            accounts = listOf(
                VaultAccount("Google", "user@gmail.com", "JBSWY3DPEHPK3PXP", "SHA1", 6, 30),
                VaultAccount("GitHub", "octocat", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "SHA256", 8, 60)
            )
        )
        return json.encodeToString(vault)
    }

    @Test
    fun `encrypt then decrypt round trip`() {
        val plain = sampleVault()
        val envelope = VaultCrypto.encrypt(plain, "correct horse battery".toCharArray())
        // ciphertext must not contain plaintext
        assertFalse(envelope.contains("JBSWY3DPEHPK3PXP"))
        assertFalse(envelope.contains("user@gmail.com"))
        val decrypted = VaultCrypto.decrypt(envelope, "correct horse battery".toCharArray())
        assertEquals(plain, decrypted)
    }

    @Test
    fun `wrong password fails`() {
        val envelope = VaultCrypto.encrypt(sampleVault(), "right-password".toCharArray())
        try {
            VaultCrypto.decrypt(envelope, "wrong-password".toCharArray())
            org.junit.Assert.fail("expected failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("password", ignoreCase = true) == true ||
                e.message?.contains("corrupt", ignoreCase = true) == true)
        }
    }

    @Test
    fun `tampered ciphertext fails`() {
        val envelope = VaultCrypto.encrypt(sampleVault(), "password123".toCharArray())
        val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(envelope)
        // flip one character in the base64 ciphertext — GCM tag check must fail
        val original = parsed.toString()
        val tampered = original.replaceFirst("G", "A")
        try {
            VaultCrypto.decrypt(tampered, "password123".toCharArray())
            org.junit.Assert.fail("expected failure")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test
    fun `two encryptions of same input differ`() {
        val plain = sampleVault()
        val e1 = VaultCrypto.encrypt(plain, "pw".toCharArray())
        val e2 = VaultCrypto.encrypt(plain, "pw".toCharArray())
        assertFalse(e1 == e2) // random salt + IV
    }

    @Test
    fun `unicode content survives round trip`() {
        val vault = VaultFile(
            accounts = listOf(VaultAccount("微信国际版", "用户@示例.com", "JBSWY3DPEHPK3PXP"))
        )
        val plain = json.encodeToString(vault)
        val envelope = VaultCrypto.encrypt(plain, "password123".toCharArray())
        assertEquals(plain, VaultCrypto.decrypt(envelope, "password123".toCharArray()))
    }

    @Test
    fun `empty account list round trip`() {
        val plain = json.encodeToString(VaultFile())
        val envelope = VaultCrypto.encrypt(plain, "password123".toCharArray())
        assertEquals(plain, VaultCrypto.decrypt(envelope, "password123".toCharArray()))
    }
}
