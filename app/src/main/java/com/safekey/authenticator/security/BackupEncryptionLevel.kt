package com.safekey.authenticator.security

/**
 * Encryption strength for a *new* portable backup.
 *
 * The level only decides how the file is written; reading a backup always
 * follows the parameters stored inside that file, so a backup made on a
 * high-memory phone restores on any phone that is actually able to run the
 * KDF (see [BackupEncryptionGate]).
 *
 * [memoryKib] is KiB, not KB and not MiB — Argon2's m parameter is counted in
 * 1 KiB blocks. 64 MiB is therefore 65536.
 */
enum class BackupEncryptionLevel(
    val id: String,
    /** Argon2 m (KiB). 0 for PBKDF2, which has no memory cost. */
    val memoryKib: Int,
    /** Argon2 t. */
    val timeCost: Int,
    /** Argon2 p. Note: BouncyCastle's generator is single-threaded, so this
     *  changes the derived key but does not shorten the wait. */
    val parallelism: Int,
    /** Minimum physical RAM the UI requires before offering this level, in GiB. */
    val requiredTotalMemGb: Int
) {
    /** PBKDF2-HMAC-SHA256 120k + AES-256-GCM. The pre-2.6.6.3 format, byte for byte. */
    STANDARD("standard", 0, 0, 0, 0),

    /** Argon2id, RFC 9106 second recommendation: m=64 MiB, t=3, p=4. */
    MEDIUM("medium", 64 * 1024, 3, 4, 6),

    /** Argon2id m=128 MiB, t=3, p=4. */
    HIGH("high", 128 * 1024, 3, 4, 8);

    val isArgon2: Boolean get() = this != STANDARD

    /** Position in the strength slider: 0 = standard, 1 = medium, 2 = high. */
    val sliderIndex: Int get() = ordinal

    companion object {
        /** Value stored in SettingsRepository; unknown/legacy strings fall back to [MEDIUM]. */
        fun fromId(id: String?): BackupEncryptionLevel =
            entries.firstOrNull { it.id == id } ?: MEDIUM

        val highest: BackupEncryptionLevel get() = entries.last()
    }
}
