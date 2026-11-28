package com.safekey.authenticator.security

import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.model.VaultFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Argon2id envelope tests.
 *
 * The 128 MiB case needs ~140 MB of heap inside the test JVM; build.gradle.kts
 * gives unit tests 1.5 GB so this is not flaky.
 */
class BackupEncryptionTest {

    private val json = Json { encodeDefaults = true }

    private fun sampleVault(): String = json.encodeToString(
        VaultFile(
            exportedAt = 1234567890L,
            accounts = listOf(
                VaultAccount("Google", "user@gmail.com", "JBSWY3DPEHPK3PXP"),
                VaultAccount("GitHub", "octocat", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
            )
        )
    )

    private fun envelope(text: String) = json.parseToJsonElement(text) as JsonObject

    @Test
    fun `medium round trip and envelope shape`() {
        val plain = sampleVault()
        val password = "correct horse battery".toCharArray()
        val text = VaultCrypto.encrypt(plain, password, BackupEncryptionLevel.MEDIUM)

        val root = envelope(text)
        assertEquals("argon2id", root["kdf"]!!.jsonPrimitive.content)
        assertEquals("2", root["version"]!!.jsonPrimitive.content)
        assertEquals("65536", root["memory_kib"]!!.jsonPrimitive.content)
        assertEquals("3", root["time_cost"]!!.jsonPrimitive.content)
        assertEquals("4", root["parallelism"]!!.jsonPrimitive.content)
        assertEquals("medium", root["level"]!!.jsonPrimitive.content)
        assertFalse("v2 must not carry a PBKDF2 iteration count", root.containsKey("iterations"))
        // secret material must not leak into the envelope
        assertFalse(text.contains("JBSWY3DPEHPK3PXP"))
        assertFalse(text.contains("user@gmail.com"))

        assertEquals(plain, VaultCrypto.decrypt(text, "correct horse battery".toCharArray()))
    }

    @Test
    fun `high round trip`() {
        val plain = sampleVault()
        val text = VaultCrypto.encrypt(plain, "pw-high".toCharArray(), BackupEncryptionLevel.HIGH)
        val root = envelope(text)
        assertEquals("131072", root["memory_kib"]!!.jsonPrimitive.content)
        assertEquals("high", root["level"]!!.jsonPrimitive.content)
        assertEquals(plain, VaultCrypto.decrypt(text, "pw-high".toCharArray()))
    }

    @Test
    fun `standard envelope is still the v1 format`() {
        val text = VaultCrypto.encrypt(
            sampleVault(), "pw".toCharArray(), BackupEncryptionLevel.STANDARD
        )
        val root = envelope(text)
        assertEquals("1", root["version"]!!.jsonPrimitive.content)
        assertEquals("PBKDF2-HMAC-SHA256", root["kdf"]!!.jsonPrimitive.content)
        assertEquals("120000", root["iterations"]!!.jsonPrimitive.content)
        // none of the v2 fields may appear in an old-format file
        assertFalse(root.containsKey("memory_kib"))
        assertFalse(root.containsKey("time_cost"))
        assertFalse(root.containsKey("parallelism"))
        assertFalse(root.containsKey("level"))
    }

    @Test
    fun `wrong password fails on argon2 envelope`() {
        val text = VaultCrypto.encrypt(
            sampleVault(), "right-password".toCharArray(), BackupEncryptionLevel.MEDIUM
        )
        try {
            VaultCrypto.decrypt(text, "wrong-password".toCharArray())
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `tampered argon2 ciphertext fails`() {
        val text = VaultCrypto.encrypt(
            sampleVault(), "password123".toCharArray(), BackupEncryptionLevel.MEDIUM
        )
        val tampered = text.replaceFirst("\"ciphertext\":\"", "\"ciphertext\":\"A")
        try {
            VaultCrypto.decrypt(tampered, "password123".toCharArray())
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `unknown kdf is reported as needing a newer app`() {
        val text = VaultCrypto.encrypt(sampleVault(), "pw".toCharArray())
            .replace("PBKDF2-HMAC-SHA256", "scrypt-v9")
        try {
            VaultCrypto.decrypt(text, "pw".toCharArray())
            throw AssertionError("expected failure")
        } catch (e: UnsupportedKdfException) {
            assertTrue(e.message!!.contains("scrypt-v9"))
        }
    }

    @Test
    fun `hostile argon2 parameters are rejected before allocating`() {
        val base = VaultCrypto.encrypt(
            sampleVault(), "pw".toCharArray(), BackupEncryptionLevel.MEDIUM
        )
        val cases = listOf(
            "\"memory_kib\":65536" to "\"memory_kib\":1048576", // > 512 MiB
            "\"time_cost\":3" to "\"time_cost\":99",
            "\"parallelism\":4" to "\"parallelism\":16",
            // below 8 * p: Argon2 requires m >= 8p
            "\"memory_kib\":65536" to "\"memory_kib\":16"
        )
        for ((from, to) in cases) {
            val hostile = base.replace(from, to)
            val started = System.currentTimeMillis()
            try {
                VaultCrypto.decrypt(hostile, "pw".toCharArray())
                throw AssertionError("expected rejection for $to")
            } catch (_: IllegalArgumentException) {
                // expected
            }
            assertTrue(
                "rejection must be immediate for $to",
                System.currentTimeMillis() - started < 1_000
            )
        }
    }

    @Test
    fun `missing params are rejected instead of deriving`() {
        val broken = """{"version":2,"format":"osmium-encrypted","kdf":"argon2id",
            "salt":"AAAAAAAAAAAAAAAAAAAAAA==","iv":"AAAAAAAAAAAAAAAA",
            "ciphertext":"AAAAAAAAAAAAAAAAAAAAAA=="}"""
        try {
            VaultCrypto.decrypt(broken, "pw".toCharArray())
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
