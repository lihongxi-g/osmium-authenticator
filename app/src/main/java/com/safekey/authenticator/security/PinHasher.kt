package com.safekey.authenticator.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Pure-JVM PIN hashing (PBKDF2-HMAC-SHA256 + random salt).
 * Kept free of Android APIs so it is unit-testable; storage/encryption
 * wrappers live in [PinManager].
 */
object PinHasher {

    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    data class PinHash(val salt: String, val hash: String)

    fun hashPin(pin: String): PinHash {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin.toCharArray(), salt, ITERATIONS)
        return PinHash(
            salt = java.util.Base64.getEncoder().encodeToString(salt),
            hash = java.util.Base64.getEncoder().encodeToString(hash)
        )
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        val saltBytes = java.util.Base64.getDecoder().decode(salt)
        val candidate = pbkdf2(pin.toCharArray(), saltBytes, ITERATIONS)
        val expected = java.util.Base64.getDecoder().decode(expectedHash)
        return java.security.MessageDigest.isEqual(candidate, expected)
    }

    private fun pbkdf2(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        return factory.generateSecret(spec).encoded
    }
}
