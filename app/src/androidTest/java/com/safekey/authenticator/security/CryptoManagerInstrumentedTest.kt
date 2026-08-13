package com.safekey.authenticator.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the AndroidKeyStore-backed CryptoManager.
 * These require a real device/emulator — they are NOT run in CI.
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class CryptoManagerInstrumentedTest {

    private val crypto = CryptoManager()

    @Test
    fun encryptThenDecryptRoundTrip() {
        val plain = "JBSWY3DPEHPK3PXP"
        val enc = crypto.encrypt(plain)
        assertEquals(plain, crypto.decrypt(enc))
    }

    @Test
    fun ciphertextNeverContainsPlaintext() {
        val plain = "JBSWY3DPEHPK3PXP-SECRET"
        val enc = crypto.encrypt(plain)
        assertTrue(!enc.ciphertext.contains(plain))
        assertTrue(!enc.iv.contains(plain))
    }

    @Test
    fun unicodeRoundTrip() {
        val plain = "用户@示例.com/微信国际版"
        assertEquals(plain, crypto.decrypt(crypto.encrypt(plain)))
    }

    @Test
    fun sameInputProducesDifferentCiphertext() {
        val plain = "JBSWY3DPEHPK3PXP"
        assertNotEquals(crypto.encrypt(plain).ciphertext, crypto.encrypt(plain).ciphertext)
    }

    @Test
    fun tamperedCiphertextFails() {
        val enc = crypto.encrypt("JBSWY3DPEHPK3PXP")
        val flipped = if (enc.ciphertext.first() == 'A') "B" + enc.ciphertext.drop(1)
        else "A" + enc.ciphertext.drop(1)
        try {
            crypto.decrypt(enc.copy(ciphertext = flipped))
            org.junit.Assert.fail("expected decryption failure")
        } catch (_: Exception) {
            // expected — GCM tag verification
        }
    }
}
