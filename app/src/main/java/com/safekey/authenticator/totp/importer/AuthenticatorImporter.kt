package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount

/**
 * Contract for parsers that convert another authenticator's export file into
 * Osmium's portable [VaultAccount] list.
 *
 * All implementations are pure JVM code (no Android dependencies) so they can
 * be unit-tested on the desktop, mirroring the existing `totp/` parser style
 * (see [com.safekey.authenticator.totp.GoogleMigrationParser]).
 *
 * File formats documented / verified in 2026-09 against:
 *  - Aegis importers (GPL-3.0): beemdevelopment/Aegis
 *    `app/src/main/java/com/beemdevelopment/aegis/importers/`
 *  - 2FAS: twofas/2fas-android + Aegis test fixtures
 *  - Raivo legacy format: OtpTranslate article (tygertec.com) + samples
 *    accepted by Ente Auth's Raivo importer.
 * Attribution for these references is shown in-app (About → Attributions).
 */
interface AuthenticatorImporter {

    /** Stable identifier used for logs and diagnostics, e.g. "aegis". */
    val id: String

    /**
     * True when [content] looks like this format. Sniffs structural markers
     * (root shape + distinctive keys); it must be cheap and must never throw.
     * Detection runs before parsing so the UI can auto-select the format —
     * the user never has to pick a source app.
     */
    fun detect(content: String): Boolean

    /**
     * Parses [content] into importable accounts.
     *
     * Policy:
     *  - Whole-file problems throw [ImporterException] carrying a machine
     *    readable [ImporterError] plus a human-readable detail.
     *  - Structurally readable entries are ALWAYS returned, even when Osmium
     *    cannot reproduce them (see [ImportSupport.issue]); the UI disables
     *    those rows with a reason instead of silently dropping them.
     *  - Steam entries are remapped to `issuer = "Steam"` + TOTP type because
     *    Osmium identifies Steam accounts by issuer (Account.isSteam).
     *
     * @throws ImporterException on malformed / encrypted / unsupported files.
     */
    fun parse(content: String): List<VaultAccount>
}

/** Why an entire file cannot be imported. UI maps each value to a localized message. */
enum class ImporterError {
    /** Content is not UTF-8 text / not parseable as JSON at all. */
    NOT_JSON,

    /** Valid JSON, but not an export of any supported authenticator. */
    UNRECOGNIZED,

    /** Password-encrypted export — decryption ships in a later version. */
    ENCRYPTED_UNSUPPORTED,

    /** Export from a schema/format version that is not supported yet. */
    VERSION_UNSUPPORTED,

    /** File parsed, but contains no account entries. */
    EMPTY
}

/** Why a single parsed entry cannot be imported. UI maps each value to a localized message. */
enum class EntryIssue {
    /** Entry type is not TOTP/HOTP (e.g. motp). */
    UNSUPPORTED_TYPE,

    /** Algorithm is not SHA1/SHA256/SHA512. */
    UNSUPPORTED_ALGORITHM,

    /** Code length is not 6 or 8 (non-Steam). Osmium cannot generate others. */
    UNSUPPORTED_DIGITS,

    /** Secret is empty, not Base32, or decodes to fewer than 10 bytes. */
    INVALID_SECRET
}

/**
 * Signals a whole-file import failure. [error] is machine readable so the UI
 * can pick a localized message; [message] (from [detail]) holds an optional
 * technical/actionable hint.
 */
class ImporterException(
    val error: ImporterError,
    detail: String? = null
) : Exception(detail)
