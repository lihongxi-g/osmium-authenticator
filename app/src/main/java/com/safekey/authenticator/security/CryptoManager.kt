package com.safekey.authenticator.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Field-level AES-256-GCM encryption backed by an AndroidKeyStore key.
 *
 * The key is non-exportable hardware-backed (when the device supports it).
 * Every field is encrypted with a fresh random 12-byte IV.
 * Plaintext never touches disk or logs.
 */
class CryptoManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Synchronized: concurrent first-time callers would race to generate the
     * same alias (KeyGenerator.generateKey throws for an existing alias), which
     * crashed batch imports (N concurrent addAccount coroutines on a fresh
     * install). Serialize key access — cipher instances stay per-call.
     */
    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plaintext: String): EncryptedField {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return EncryptedField(
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    fun decrypt(field: EncryptedField): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(field.iv, Base64.NO_WRAP)
        val ct = Base64.decode(field.ciphertext, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    data class EncryptedField(val iv: String, val ciphertext: String)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "safekey_master_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
