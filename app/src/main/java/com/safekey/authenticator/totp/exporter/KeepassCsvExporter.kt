package com.safekey.authenticator.totp.exporter

import com.safekey.authenticator.model.VaultAccount

/**
 * Writes a CSV that KeePassXC imports directly and that spreadsheet-based
 * backups can read.
 *
 * Header follows KeePassXC's own CSV export
 * (`Group,Title,Username,Password,URL,Notes,TOTP,Icon,Last Modified,Created`),
 * where the TOTP column accepts a full `otpauth://` link. Osmium puts every
 * account in the "Osmium" group, the issuer in Title and the account name in
 * Username, so the entry stays recognizable in a password manager.
 *
 * Password/URL/Notes stay empty on purpose: Osmium never had that data, and a
 * dummy value would be worse than a blank field.
 */
internal object KeepassCsvExporter {

    private const val HEADER = "Group,Title,Username,Password,URL,Notes,TOTP,Icon,Last Modified,Created"

    fun build(accounts: List<VaultAccount>): String {
        val out = StringBuilder(HEADER).append("\r\n")
        accounts.forEach { account ->
            val title = account.issuer.ifBlank { account.label }
            val fields = listOf(
                "Osmium",
                title,
                account.label,
                "",
                "",
                "",
                account.toOtpLink(),
                "0",
                "",
                ""
            )
            out.append(fields.joinToString(",") { escape(it) }).append("\r\n")
        }
        return out.toString()
    }

    /** RFC 4180 escaping: quote when the value contains a comma, quote or newline. */
    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return '"' + value.replace("\"", "\"\"") + '"'
    }
}
