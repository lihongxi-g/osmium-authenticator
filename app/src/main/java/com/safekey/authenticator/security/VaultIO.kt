package com.safekey.authenticator.security

import com.safekey.authenticator.model.VaultFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

    /** Hard cap for imported payloads (matches the WebDAV download limit) —
     *  a huge picked file must fail as a format error, not OOM the app. */
    const val MAX_PAYLOAD_BYTES = 64 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Serialize + password-encrypt a vault into the portable envelope JSON. */
    fun encrypt(vault: VaultFile, password: CharArray): String {
        val plain = json.encodeToString(vault)
        return VaultCrypto.encrypt(plain, password)
    }

    /**
     * Serialize a vault WITHOUT encryption — developer-mode "plaintext
     * export" only. Every call site must warn the user first.
     */
    fun encodePlain(vault: VaultFile): String = json.encodeToString(vault)

    /**
     * Parse a plaintext (unencrypted) vault payload — developer-mode
     * "plaintext import" only.
     *
     * The payload comes straight from user-chosen storage and carries no
     * password to validate against, so the Osmium magic fields are checked
     * strictly: JSON that merely happens to parse (e.g. `{}` or an unrelated
     * object) must fail here instead of decoding into an empty vault with
     * default values.
     *
     * @throws VaultFormatException with wrongPassword=false when the payload
     *   is oversized or is not a plaintext Osmium vault.
     */
    fun decodePlain(payload: ByteArray): VaultFile {
        if (payload.size > MAX_PAYLOAD_BYTES) {
            throw VaultFormatException(wrongPassword = false)
        }
        val text = String(payload, Charsets.UTF_8)
        val root = try {
            json.decodeFromString<JsonObject>(text)
        } catch (e: Exception) {
            throw VaultFormatException(wrongPassword = false, cause = e)
        }
        val format = (root["format"] as? JsonPrimitive)?.contentOrNull
        if (format != "osmium-vault" && format != "safekey-vault") {
            throw VaultFormatException(wrongPassword = false)
        }
        if (root["accounts"] !is JsonArray) {
            throw VaultFormatException(wrongPassword = false)
        }
        return try {
            json.decodeFromString<VaultFile>(text)
        } catch (e: Exception) {
            throw VaultFormatException(wrongPassword = false, cause = e)
        }
    }

    /**
     * True when [payload] can be imported as a plaintext vault. Never throws —
     * safe to call on any user-picked bytes when deciding which hint to show.
     */
    fun isPlainVault(payload: ByteArray): Boolean =
        try {
            decodePlain(payload)
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Decrypt + parse a backup payload (the raw bytes of an exported file).
     *
     * @throws VaultFormatException with wrongPassword=true when decryption
     *   fails (wrong password, corrupted data, or not an encrypted file),
     *   and wrongPassword=false when the decrypted JSON is not an Osmium vault.
     */
    fun decrypt(payload: ByteArray, password: CharArray): VaultFile {
        if (payload.size > MAX_PAYLOAD_BYTES) {
            throw VaultFormatException(wrongPassword = false)
        }
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
