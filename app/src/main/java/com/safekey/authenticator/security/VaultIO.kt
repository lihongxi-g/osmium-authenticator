package com.safekey.authenticator.security

import com.safekey.authenticator.model.VaultFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Thrown when a backup payload cannot be turned into a [VaultFile].
 * [wrongPassword] distinguishes a password/decryption failure from a
 * format failure so the UI can show the right message.
 */
class VaultFormatException(
    val wrongPassword: Boolean,
    cause: Throwable? = null
) : Exception(if (wrongPassword) "Wrong password or corrupted data" else "Not an Osmium vault", cause)

/**
 * Shared encrypt/decrypt helpers for the portable vault format, used by both
 * the file-based export/import screens and the WebDAV backup feature — one
 * implementation, one format.
 */
object VaultIO {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Serialize + password-encrypt a vault into the portable envelope JSON. */
    fun encrypt(vault: VaultFile, password: CharArray): String {
        val plain = json.encodeToString(vault)
        return VaultCrypto.encrypt(plain, password)
    }

    /**
     * Decrypt + parse a backup payload (the raw bytes of an exported file).
     *
     * @throws VaultFormatException with wrongPassword=true when decryption
     *   fails (wrong password, corrupted data, or not an encrypted file),
     *   and wrongPassword=false when the decrypted JSON is not an Osmium vault.
     */
    fun decrypt(payload: ByteArray, password: CharArray): VaultFile {
        val plainJson = try {
            VaultCrypto.decrypt(String(payload, Charsets.UTF_8), password)
        } catch (e: IllegalArgumentException) {
            throw VaultFormatException(wrongPassword = true, cause = e)
        }
        val vault = try {
            json.decodeFromString<VaultFile>(plainJson)
        } catch (e: Exception) {
            throw VaultFormatException(wrongPassword = false, cause = e)
        }
        if (vault.format != "osmium-vault" && vault.format != "safekey-vault") {
            throw VaultFormatException(wrongPassword = false)
        }
        return vault
    }
}
