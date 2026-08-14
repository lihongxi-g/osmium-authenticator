package com.safekey.authenticator.security

import com.safekey.authenticator.model.EncryptedVault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption for the portable export/import format.
 *
 * PBKDF2-HMAC-SHA256 key derivation + AES-256-GCM. Pure JCE — portable across
 * devices (unlike the AndroidKeyStore master key, which never leaves the device),
 * so a vault exported on phone A can be imported on phone B.
 */
object VaultCrypto {

    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Encrypt the plaintext vault JSON with a user password. Returns the envelope JSON. */
    fun encrypt(plaintextJson: String, password: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val envelope = EncryptedVault(
            iterations = ITERATIONS,
            salt = Base64.getEncoder().encodeToString(salt),
            iv = Base64.getEncoder().encodeToString(iv),
            ciphertext = Base64.getEncoder().encodeToString(ciphertext)
        )
        return json.encodeToString(envelope)
    }

    /**
     * Decrypt an envelope. Throws [IllegalArgumentException] on wrong password
     * or corrupted data (GCM tag verification failure).
     */
    fun decrypt(envelopeJson: String, password: CharArray): String {
        val envelope = try {
            json.decodeFromString<EncryptedVault>(envelopeJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not an Osmium encrypted file", e)
        }
        // accept vaults exported by old SafeKey builds too
        if (envelope.format != "osmium-encrypted" && envelope.format != "safekey-encrypted") {
            throw IllegalArgumentException("Not an Osmium encrypted file")
        }
        val salt = Base64.getDecoder().decode(envelope.salt)
        val iv = Base64.getDecoder().decode(envelope.iv)
        val ct = Base64.getDecoder().decode(envelope.ciphertext)
        val key = deriveKey(password, salt, envelope.iterations)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plaintext = try {
            cipher.doFinal(ct)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong password or corrupted data", e)
        }
        return String(plaintext, Charsets.UTF_8)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
