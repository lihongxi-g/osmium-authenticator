package com.safekey.authenticator.model

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
    val tagIds: List<String> = emptyList()
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
