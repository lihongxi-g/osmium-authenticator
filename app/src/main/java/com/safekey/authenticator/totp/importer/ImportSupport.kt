package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.totp.Base32

/**
 * Importability rules shared by every source format and the import UI.
 *
 * Osmium's generator can only reproduce TOTP/HOTP with SHA1/SHA256/SHA512 and
 * 6/8 digits (see TotpGenerator.pow10 — any other length silently produces
 * wrong codes, so such entries must be surfaced as unsupported, never
 * imported with mangled parameters). Steam accounts are the exception: their
 * code is always 5 Steam-alphabet chars and the digits field is ignored.
 */
object ImportSupport {

    /** Minimum secret size in bytes, mirroring [com.safekey.authenticator.totp.OtpUriParser]. */
    const val MIN_SECRET_BYTES = 10

    /** Osmium identifies Steam accounts by their issuer (Account.isSteam). */
    fun isSteamAccount(issuer: String): Boolean = issuer.equals("Steam", ignoreCase = true)

    /**
     * Returns the [EntryIssue] that prevents [account] from being imported,
     * or null when the entry can be reproduced faithfully by Osmium.
     */
    fun issue(account: VaultAccount): EntryIssue? {
        if (account.secret.isBlank()) return EntryIssue.INVALID_SECRET
        val decoded = try {
            Base32.decode(account.secret)
        } catch (_: IllegalArgumentException) {
            return EntryIssue.INVALID_SECRET
        }
        if (decoded.size < MIN_SECRET_BYTES) return EntryIssue.INVALID_SECRET

        if (account.type != Account.TYPE_TOTP && account.type != Account.TYPE_HOTP) {
            return EntryIssue.UNSUPPORTED_TYPE
        }
        if (account.algorithm.uppercase() !in Account.SUPPORTED_ALGORITHMS) {
            return EntryIssue.UNSUPPORTED_ALGORITHM
        }
        if (!isSteamAccount(account.issuer) && account.digits !in Account.SUPPORTED_DIGITS) {
            return EntryIssue.UNSUPPORTED_DIGITS
        }
        return null
    }
}

/** How a source's type token maps onto Osmium's model. */
internal data class TypeMapping(val type: String, val isSteam: Boolean)

/**
 * Maps a source type token ("totp"/"TOTP"/"hotp"/"steam"/…) to Osmium types.
 * Steam entries become TOTP rows whose issuer is forced to "Steam" (that is
 * how Osmium renders Steam codes). Anything unknown is kept lowercased so the
 * UI can flag it as an unsupported row instead of guessing.
 */
internal fun mapEntryType(raw: String?): TypeMapping = when (val t = raw?.trim()?.lowercase()) {
    null, "" -> TypeMapping(Account.TYPE_TOTP, isSteam = false)
    Account.TYPE_TOTP -> TypeMapping(Account.TYPE_TOTP, isSteam = false)
    Account.TYPE_HOTP -> TypeMapping(Account.TYPE_HOTP, isSteam = false)
    "steam" -> TypeMapping(Account.TYPE_TOTP, isSteam = true)
    else -> TypeMapping(t, isSteam = false)
}

/** Canonical secret form used across Osmium: uppercase Base32, no padding/whitespace. */
internal fun normalizeSecret(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.uppercase()
        .replace(" ", "").replace("\t", "").replace("\n", "").replace("\r", "")
        .replace("=", "").replace("-", "").replace("_", "").replace(".", "")
}

/**
 * Builds a [VaultAccount] from one source entry with the shared mapping rules
 * (type mapping, Steam issuer, secret normalization, defaults 30s/6 digits/
 * SHA1/counter 0). Returns null only when the entry carries no name at all —
 * such entries cannot be shown or matched, so they are dropped.
 */
internal fun vaultRow(
    issuer: String?,
    label: String?,
    secret: String?,
    typeRaw: String?,
    algorithm: String?,
    digits: Int?,
    period: Int?,
    counter: Long?
): VaultAccount? {
    val mapping = mapEntryType(typeRaw)
    var issuerOut = issuer?.trim().orEmpty()
    if (mapping.isSteam) issuerOut = "Steam"
    val labelOut = label?.trim().orEmpty()
    if (issuerOut.isEmpty() && labelOut.isEmpty()) return null

    return VaultAccount(
        issuer = issuerOut,
        label = labelOut,
        secret = normalizeSecret(secret),
        algorithm = algorithm?.trim()?.uppercase()?.ifEmpty { Account.ALGO_SHA1 } ?: Account.ALGO_SHA1,
        digits = digits?.takeIf { it > 0 } ?: 6,
        period = period?.takeIf { it > 0 } ?: 30,
        type = mapping.type,
        counter = (counter ?: 0L).coerceAtLeast(0L)
    )
}
