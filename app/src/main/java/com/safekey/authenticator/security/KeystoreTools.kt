package com.safekey.authenticator.security

import android.content.Context
import android.security.keystore.KeyInfo
import androidx.room.withTransaction
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.database.AccountEntity
import com.safekey.authenticator.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.SecretKeyFactory

/**
 * Read-only Keystore inspection plus the developer-mode "re-encrypt every
 * stored field" maintenance operation.
 *
 * Re-encryption keeps the same non-exportable master key (it never leaves
 * the AndroidKeyStore) and re-wraps every stored ciphertext with a fresh
 * IV. Because the key is unchanged, old and new ciphertexts stay
 * interchangeable: a failure mid-way never locks the user out, and the JSON
 * snapshot written before the operation is a belt-and-braces recovery
 * artifact (kept on failure, deleted on success).
 */
object KeystoreTools {

    data class KeystoreStatus(
        val alias: String,
        val present: Boolean,
        val securityLevel: String?,
        val keySize: Int?,
        val roundtripOk: Boolean?,
        val accountCount: Int,
        val rowsOk: Int
    )

    data class ReencryptResult(
        val ok: Boolean,
        val fields: Int,
        val error: String?
    )

    private const val SNAPSHOT_NAME = "reencrypt-snapshot.json"

    // ------------------------------------------------------------------ status

    suspend fun status(context: Context): KeystoreStatus = withContext(Dispatchers.IO) {
        val app = context.applicationContext as SafeKeyApp
        val crypto = CryptoManager()

        var present = false
        var securityLevel: String? = null
        var keySize: Int? = null
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            present = keyStore.containsAlias(CryptoManager.KEY_ALIAS_NAME)
            if (present) {
                val key = (keyStore.getEntry(CryptoManager.KEY_ALIAS_NAME, null)
                    as? KeyStore.SecretKeyEntry)?.secretKey
                if (key != null) {
                    val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                    val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
                    securityLevel = when (info.securityLevel) {
                        1 -> "TrustedEnvironment"
                        2 -> "StrongBox"
                        else -> "Software"
                    }
                    keySize = info.keySize
                }
            }
        } catch (_: Exception) {
            // Report what we could gather; missing hardware info is fine.
        }

        val roundtrip = try {
            val probe = "osmium-selftest"
            crypto.decrypt(crypto.encrypt(probe)) == probe
        } catch (_: Exception) {
            false
        }

        val entities = app.accountDao.getAll()
        var rowsOk = 0
        for (e in entities) {
            val ok = try {
                crypto.decrypt(CryptoManager.EncryptedField(e.secretIv, e.secretCiphertext))
                crypto.decrypt(CryptoManager.EncryptedField(e.issuerIv, e.issuerCiphertext))
                crypto.decrypt(CryptoManager.EncryptedField(e.labelIv, e.labelCiphertext))
                true
            } catch (_: Exception) {
                false
            }
            if (ok) rowsOk++
        }

        KeystoreStatus(
            alias = CryptoManager.KEY_ALIAS_NAME,
            present = present,
            securityLevel = securityLevel,
            keySize = keySize,
            roundtripOk = roundtrip,
            accountCount = entities.size,
            rowsOk = rowsOk
        )
    }

    // -------------------------------------------------------------- re-encrypt

    suspend fun reencryptAll(context: Context): ReencryptResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext as SafeKeyApp
        val crypto = CryptoManager()
        val db = AppDatabase.get(context)
        val entities = app.accountDao.getAll()

        // 1. Decrypt + re-encrypt every row in memory first.
        val refreshed = ArrayList<AccountEntity>(entities.size)
        for (e in entities) {
            try {
                val issuer = crypto.decrypt(CryptoManager.EncryptedField(e.issuerIv, e.issuerCiphertext))
                val label = crypto.decrypt(CryptoManager.EncryptedField(e.labelIv, e.labelCiphertext))
                val secret = crypto.decrypt(CryptoManager.EncryptedField(e.secretIv, e.secretCiphertext))
                val ni = crypto.encrypt(issuer)
                val nl = crypto.encrypt(label)
                val ns = crypto.encrypt(secret)
                refreshed += e.copy(
                    issuerIv = ni.iv,
                    issuerCiphertext = ni.ciphertext,
                    labelIv = nl.iv,
                    labelCiphertext = nl.ciphertext,
                    secretIv = ns.iv,
                    secretCiphertext = ns.ciphertext
                )
            } catch (_: Exception) {
                return@withContext ReencryptResult(
                    ok = false,
                    fields = 0,
                    error = "Account ${e.id.take(8)}… could not be decrypted — nothing was changed"
                )
            }
        }

        // 2. Snapshot current ciphertexts (recovery artifact on failure).
        val snapshot = File(context.filesDir, SNAPSHOT_NAME)
        try {
            snapshot.writeText(Json.encodeToString(entities.map { it.snapshotFields() }))
        } catch (_: Exception) {
            snapshot.delete()
        }

        // 3. Write the refreshed ciphertexts atomically.
        try {
            db.withTransaction {
                for (e in refreshed) app.accountDao.update(e)
            }
        } catch (ex: Exception) {
            return@withContext ReencryptResult(
                ok = false,
                fields = 0,
                error = "Database write failed (${ex.javaClass.simpleName}) — snapshot kept"
            )
        }

        // 4. Same for the PIN blobs and the stored passwords.
        var fields = refreshed.size * 3
        runCatching { fields += PinManager(context).reencryptBlobs() }
        runCatching { fields += app.settingsRepository.reencryptSecretFields() }

        // 5. Verify every rewritten secret decrypts.
        val verifyOk = refreshed.all {
            runCatching {
                crypto.decrypt(CryptoManager.EncryptedField(it.secretIv, it.secretCiphertext))
            }.isSuccess
        }
        if (verifyOk) {
            snapshot.delete()
        }
        ReencryptResult(
            ok = verifyOk,
            fields = fields,
            error = if (verifyOk) null else "Verification failed — snapshot kept"
        )
    }

    private fun AccountEntity.snapshotFields(): Map<String, String> = mapOf(
        "id" to id,
        "issuerIv" to issuerIv,
        "issuerCiphertext" to issuerCiphertext,
        "labelIv" to labelIv,
        "labelCiphertext" to labelCiphertext,
        "secretIv" to secretIv,
        "secretCiphertext" to secretCiphertext
    )
}
