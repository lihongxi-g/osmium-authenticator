package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount

/**
 * Parser for the plain-text "one otpauth:// link per line" export.
 *
 * This is the format used by **Ente Auth** (its export is a list of enrollment
 * links, which is why Aegis imports Ente Auth files through its generic
 * `GoogleAuthUriImporter`), by Google Authenticator's URI-list export and by
 * most scripts/self-made backups.
 *
 * Shape (UTF-8 text, one link per line, blank lines ignored):
 * ```
 * otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30
 * otpauth://hotp/Example:user?secret=...&counter=7
 * steam://JBSWY3DPEHPK3PXP
 * ```
 * Lines that are not a link, or whose link cannot be reproduced by Osmium
 * (e.g. 7-digit TOTP), are skipped; when nothing usable is left the file is
 * reported as [ImporterError.EMPTY] instead of silently importing zero rows.
 */
object OtpUriListImporter : AuthenticatorImporter {

    override val id = "otpauth_links"
    override val displayName = "otpauth link list"

    override fun detect(content: String): Boolean = content.lineSequence().any { line ->
        val trimmed = line.trim()
        trimmed.startsWith("otpauth://", ignoreCase = true) ||
            trimmed.startsWith("steam://", ignoreCase = true)
    }

    override fun parse(content: String): List<VaultAccount> {
        val rows = mutableListOf<VaultAccount>()
        content.lineSequence().forEach { line ->
            vaultRowFromLink(line)?.let { rows.add(it) }
        }
        if (rows.isEmpty()) {
            throw ImporterException(
                ImporterError.EMPTY,
                "no otpauth:// or steam:// link could be read from this text file"
            )
        }
        return rows
    }
}
