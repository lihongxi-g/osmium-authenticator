package com.safekey.authenticator.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Storage and verification for the app PIN and the self-destruct PIN.
 *
 * The PIN itself is NEVER stored — only a salted PBKDF2 hash, further
 * encrypted with the AndroidKeyStore master key. Irrecoverable by design:
 * there is no reset path; the only way out is clearing app data (or the
 * self-destruct mode).
 */
class PinManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("safekey_pin", Context.MODE_PRIVATE)
    private val crypto = CryptoManager()

    // ------------------------------------------------------------ app PIN

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_BLOB)

    fun setPin(pin: String) {
        val h = PinHasher.hashPin(pin)
        val blob = crypto.encrypt("${h.salt}:${h.hash}")
        prefs.edit().putString(KEY_PIN_BLOB, "${blob.iv}.${blob.ciphertext}").apply()
    }

    fun verifyPin(pin: String): Boolean {
        val blob = prefs.getString(KEY_PIN_BLOB, null) ?: return false
        return try {
            val parts = blob.split(".", limit = 2)
            val field = CryptoManager.EncryptedField(parts[0], parts[1])
            val decrypted = crypto.decrypt(field)
            val salt = decrypted.substringBefore(":")
            val hash = decrypted.substringAfter(":")
            PinHasher.verify(pin, salt, hash)
        } catch (_: Exception) {
            false
        }
    }

    fun getPinHashForExport(): Pair<String, String>? {
        val blob = prefs.getString(KEY_PIN_BLOB, null) ?: return null
        return try {
            val parts = blob.split(".", limit = 2)
            val field = CryptoManager.EncryptedField(parts[0], parts[1])
            val decrypted = crypto.decrypt(field)
            decrypted.substringBefore(":") to decrypted.substringAfter(":")
        } catch (_: Exception) {
            null
        }
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_BLOB).apply()
    }

    // --------------------------------------------------- self-destruct PIN

    fun hasDestroyPin(): Boolean = prefs.contains(KEY_DESTROY_PIN_BLOB)

    fun setDestroyPin(pin: String) {
        val h = PinHasher.hashPin(pin)
        val blob = crypto.encrypt("${h.salt}:${h.hash}")
        prefs.edit().putString(KEY_DESTROY_PIN_BLOB, "${blob.iv}.${blob.ciphertext}").apply()
    }

    fun verifyDestroyPin(pin: String): Boolean {
        val blob = prefs.getString(KEY_DESTROY_PIN_BLOB, null) ?: return false
        return try {
            val parts = blob.split(".", limit = 2)
            val field = CryptoManager.EncryptedField(parts[0], parts[1])
            val decrypted = crypto.decrypt(field)
            PinHasher.verify(pin, decrypted.substringBefore(":"), decrypted.substringAfter(":"))
        } catch (_: Exception) {
            false
        }
    }

    fun clearDestroyPin() {
        prefs.edit().remove(KEY_DESTROY_PIN_BLOB).apply()
    }

    fun wipeAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PIN_BLOB = "pin_blob"
        private const val KEY_DESTROY_PIN_BLOB = "destroy_pin_blob"
    }
}
