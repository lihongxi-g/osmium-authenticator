package com.safekey.authenticator.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Portable export/import format (version 1).
 * Serialized as JSON, then encrypted with [com.safekey.authenticator.security.VaultCrypto].
 */
@Serializable
data class VaultTag(
    val id: String = "",
    val name: String = "",
    val color: String = "blue"
)

@Serializable
data class VaultAccount(
    val issuer: String = "",
    val label: String = "",
    val secret: String = "",
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val type: String = "totp",
    val counter: Long = 0,
    val tagIds: List<String> = emptyList(),
    /**
     * Optional entry icon as standard Base64 (same representation Aegis uses
     * in its `icon` field). Empty means "no icon". Defaults keep older
     * backups readable and let older builds ignore the field
     * (`ignoreUnknownKeys`), so the vault version does not have to change.
     */
    val icon: String = "",
    val iconMime: String = ""
)

@Serializable
data class VaultFile(
    val version: Int = 1,
    val format: String = "osmium-vault",
    val exportedAt: Long = 0L,
    val accounts: List<VaultAccount> = emptyList(),
    val tags: List<VaultTag> = emptyList(),
    /** Present when the exporting device had an app PIN — import then requires it. */
    val pinSalt: String = "",
    val pinHash: String = ""
)

/** Envelope written to disk: encryption metadata + ciphertext. */
@Serializable
data class EncryptedVault(
    val version: Int = 1,
    val format: String = "osmium-encrypted",
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int = 0,
    val salt: String = "",
    val iv: String = "",
    val ciphertext: String = ""
)

/**
 * Version 2 envelope: Argon2id. Kept as a separate class on purpose.
 *
 * [EncryptedVault] is left byte-for-byte alone so a PBKDF2 export still
 * serializes exactly as it did before (`encodeDefaults = true` would otherwise
 * start emitting the new fields with default values into old-format files).
 *
 * [memoryKib], [timeCost] and [parallelism] are the only inputs to decryption;
 * [level] is a display hint and must never be trusted. [iterations] does not
 * exist here — reusing it for Argon2's t would make the two formats
 * indistinguishable.
 */
@Serializable
data class EncryptedVaultArgon2(
    val version: Int = 2,
    val format: String = "osmium-encrypted",
    val kdf: String = "argon2id",
    @SerialName("memory_kib") val memoryKib: Int = 0,
    @SerialName("time_cost") val timeCost: Int = 0,
    val parallelism: Int = 0,
    val level: String = "",
    val salt: String = "",
    val iv: String = "",
    val ciphertext: String = ""
)
