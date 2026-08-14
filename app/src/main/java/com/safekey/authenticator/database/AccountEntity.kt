package com.safekey.authenticator.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity. Issuer, label and secret are stored ENCRYPTED (AES-256-GCM,
 * per-field random IV). No plaintext sensitive data is ever written to disk.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val issuerIv: String,
    val issuerCiphertext: String,
    val labelIv: String,
    val labelCiphertext: String,
    val secretIv: String,
    val secretCiphertext: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val sortOrder: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val copyCount: Int = 0
)
