package com.safekey.authenticator.totp.exporter

import com.safekey.authenticator.model.VaultAccount

/**
 * Formats Osmium can hand its accounts to, for users who want to move to
 * another authenticator.
 *
 * Every format here is PLAINTEXT by nature (that is what a migration target
 * needs) and is written from in-memory secrets only — nothing leaves the device
 * until the user picks a destination in the system file dialog.
 *
 * Google Authenticator is absent on purpose: it only imports (transfer QR), it
 * never exports, so there is nothing Osmium could write for it.
 */
enum class ExportFormat(
    val id: String,
    /** Suggested file name in the system "save as" dialog. */
    val fileName: String,
    /** MIME type handed to the CREATE_DOCUMENT intent. */
    val mimeType: String
) {
    /** One otpauth:// link per line — Aegis, Ente Auth, scripts, KeePassXC clipboard. */
    OTP_LINKS("otpauth_links", "osmium-accounts-otpauth.txt", "text/plain"),

    /** Aegis plaintext vault JSON. */
    AEGIS_JSON("aegis", "osmium-accounts-aegis.json", "application/json"),

    /** KeePassXC-compatible CSV with a TOTP column. */
    KEEPASS_CSV("keepassxc", "osmium-accounts-keepassxc.csv", "text/csv")
}

/** Renders [accounts] in the requested [format]. Pure JVM, unit tested. */
object Exporters {

    fun build(format: ExportFormat, accounts: List<VaultAccount>): String = when (format) {
        ExportFormat.OTP_LINKS -> accounts.joinToString("\n") { it.toOtpLink() }
        ExportFormat.AEGIS_JSON -> AegisJsonExporter.build(accounts)
        ExportFormat.KEEPASS_CSV -> KeepassCsvExporter.build(accounts)
    }
}
