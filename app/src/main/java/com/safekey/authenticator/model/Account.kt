package com.safekey.authenticator.model

/**
 * Domain model for a TOTP account. In-memory representation holds the plaintext
 * secret (Base32 string) — it is only persisted in encrypted form via [AccountEntity].
 */
data class Account(
    val id: String,
    val issuer: String,
    val label: String,
    val secret: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val sortOrder: Long,
    val createdAt: Long,
    val copyCount: Int = 0,
    val updatedAt: Long,
    val type: String = TYPE_TOTP,
    val counter: Long = 0,
    val hidden: Boolean = false,
    val tags: List<Tag> = emptyList()
) {
    /** Service (issuer) is the title; a blank issuer shows "Unknown".
     *  Account names are auto-generated at add time when left blank, so
     *  [label] is never empty in practice (falls back to "Unknown" anyway). */
    val displayTitle: String get() = issuer.ifBlank { "Unknown" }
    val displaySubtitle: String get() = label.ifBlank { "Unknown" }
    /** Steam Guard accounts use the 26-char Steam alphabet. */
    val isSteam: Boolean get() = issuer.equals("Steam", ignoreCase = true)
    val isHotp: Boolean get() = type == TYPE_HOTP

    companion object {
        const val TYPE_TOTP = "totp"
        const val TYPE_HOTP = "hotp"
        const val ALGO_SHA1 = "SHA1"
        const val ALGO_SHA256 = "SHA256"
        const val ALGO_SHA512 = "SHA512"
        val SUPPORTED_ALGORITHMS = listOf(ALGO_SHA1, ALGO_SHA256, ALGO_SHA512)
        val SUPPORTED_DIGITS = listOf(6, 8)
    }
}
