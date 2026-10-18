package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount

/**
 * Parser for any CSV/TSV-style export that carries enrollment links in a
 * column — the shape produced by **KeePassXC**, **1Password**, **Dashlane**,
 * **LastPass** and most spreadsheet-based backups.
 *
 * Strategy: locate the link column by CONTENT (the first cell of a row that
 * starts with `otpauth://` or `steam://`) instead of by header name, because
 * every product names it differently (`TOTP`, `otp`, `OTPAuth`, `totp_secret`,
 * …). The header row is then used only to fill the gaps: a title-ish column
 * becomes the issuer when the link has none, a username-ish column becomes the
 * account name.
 *
 * Files with a `login_totp` header are handled by [BitwardenImporter] and are
 * skipped here.
 */
object CsvOtpImporter : AuthenticatorImporter {

    override val id = "csv"
    override val displayName = "CSV export"

    private val TITLE_COLUMNS = listOf("title", "name", "service", "issuer", "site", "item")
    private val ACCOUNT_COLUMNS = listOf("username", "account", "login", "user", "email")

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        if (c.startsWith("{") || c.startsWith("[")) return false
        if (',' !in c) return false
        if ("otpauth://" !in c && "steam://" !in c) return false
        return "login_totp" !in c.lineSequence().take(1).firstOrNull().orEmpty().lowercase()
    }

    override fun parse(content: String): List<VaultAccount> {
        val rows = parseCsvRows(content)
        if (rows.isEmpty()) throw ImporterException(ImporterError.EMPTY, "empty file")

        val header = rows.first().map { it.trim().lowercase() }
        val titleColumn = header.indexOfFirst { name -> TITLE_COLUMNS.any { name == it } }
        val accountColumn = header.indexOfFirst { name -> ACCOUNT_COLUMNS.any { name == it } }

        val accounts = mutableListOf<VaultAccount>()
        rows.drop(1).forEach { row ->
            val linkIndex = row.indexOfFirst { cell ->
                val t = cell.trim()
                t.startsWith("otpauth://", ignoreCase = true) || t.startsWith("steam://", ignoreCase = true)
            }
            if (linkIndex < 0) return@forEach
            var account = vaultRowFromLink(row[linkIndex]) ?: return@forEach
            val title = row.getOrNull(titleColumn)?.trim().orEmpty()
            val user = row.getOrNull(accountColumn)?.trim().orEmpty()
            if (account.issuer.isBlank() && title.isNotEmpty()) {
                account = account.copy(issuer = title)
            }
            if (account.label.isBlank() && user.isNotEmpty()) {
                account = account.copy(label = user)
            }
            accounts.add(account)
        }
        if (accounts.isEmpty()) {
            throw ImporterException(
                ImporterError.EMPTY, "no otpauth:// or steam:// link found in this CSV export"
            )
        }
        return accounts
    }
}
