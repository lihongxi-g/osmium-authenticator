package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount

/**
 * Parser for **Bitwarden** vault exports that carry TOTP secrets.
 *
 * Format facts cross-checked (2026-10) against beemdevelopment/Aegis
 * `BitwardenImporter` (GPL-3.0).
 *
 * Two accepted shapes:
 *  - JSON vault export: `items[].login.totp` holds an `otpauth://` (or
 *    `steam://`) link. Items without `login` or without a TOTP link are
 *    skipped — a Bitwarden vault is mostly passwords.
 *  - CSV export: the `login_totp` column holds the same links.
 *
 * Aegis falls back from JSON to CSV whenever ANY item lacks `login`, which
 * quietly drops every TOTP in a normal vault; here the JSON path tolerates
 * mixed items and only the "not a Bitwarden file at all" case falls through.
 */
object BitwardenImporter : AuthenticatorImporter {

    override val id = "bitwarden"
    override val displayName = "Bitwarden"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        if (c.startsWith("{")) return "\"items\"" in c && "\"login\"" in c
        // CSV export: the login_totp header is Bitwarden-specific.
        return c.lineSequence().take(1).any { "login_totp" in it.lowercase() }
    }

    override fun parse(content: String): List<VaultAccount> {
        val c = content.cleanJsonText()
        val links = if (c.startsWith("{")) parseJsonLinks(c) else parseCsvLinks(c)
        val rows = links.mapNotNull { vaultRowFromLink(it) }.toMutableList()
        if (rows.isEmpty()) {
            throw ImporterException(
                ImporterError.EMPTY,
                "no TOTP entry found in this Bitwarden export (only items with a login.totp field can be imported)"
            )
        }
        return rows
    }

    private fun parseJsonLinks(c: String): List<String> {
        val root = try {
            parseJsonElement(c)
        } catch (e: Exception) {
            throw ImporterException(ImporterError.NOT_JSON, "not valid JSON")
        }
        val obj = root.asObj ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "Bitwarden export must be a JSON object"
        )
        val items = obj.arr("items") ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "Bitwarden export has no items array"
        )
        val links = mutableListOf<String>()
        items.forEach { element ->
            val login = element.asObj?.obj("login") ?: return@forEach
            val totp = login.str("totp")?.trim().orEmpty()
            if (totp.isNotEmpty()) links.add(totp)
        }
        return links
    }

    private fun parseCsvLinks(c: String): List<String> {
        val rows = parseCsvRows(c)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        val column = header.indexOf("login_totp")
        if (column < 0) return emptyList()
        return rows.drop(1).mapNotNull { row -> row.getOrNull(column)?.trim()?.takeIf { it.isNotEmpty() } }
    }
}
