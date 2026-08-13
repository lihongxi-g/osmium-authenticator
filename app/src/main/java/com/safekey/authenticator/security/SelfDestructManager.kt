package com.safekey.authenticator.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Self-destruct mode implementation.
 *
 * "Randomly re-encrypt everything so even the app cannot decrypt it" is
 * achieved cryptographically: the master key that encrypts every stored
 * field is DELETED from the AndroidKeyStore (keys there are non-exportable
 * and unrecoverable), then every account row and PIN blob is wiped.
 * Any residual ciphertext is AES-256-GCM under a destroyed key — mathematically
 * unrecoverable, which is stronger than overwriting with another key we'd
 * have to store somewhere.
 */
class SelfDestructManager {

    fun isKeyDestroyed(): Boolean {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return !ks.containsAlias(MASTER_KEY_ALIAS)
    }

    /** Delete the master key (irreversible). Call BEFORE wiping data. */
    fun destroyMasterKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(MASTER_KEY_ALIAS)) {
            ks.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    /** Rotate to a fresh random master key (used after a normal wipe). */
    fun generateFreshMasterKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        // must match CryptoManager's alias
        const val MASTER_KEY_ALIAS = "safekey_master_key_v1"
    }
}
