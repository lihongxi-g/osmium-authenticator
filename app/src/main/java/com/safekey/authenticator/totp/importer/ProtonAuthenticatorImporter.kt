package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parser for **Proton Authenticator** plaintext exports.
 *
 * Format facts cross-checked (2026-10) against beemdevelopment/Aegis
 * `ProtonAuthenticatorImporter` (GPL-3.0).
 *
 * Plain export shape:
 * ```
 * { "version": 1, "entries": [
 *     { "content": { "name": "GitHub", "uri": "otpauth://totp/GitHub:octocat?secret=…" } } ] }
 * ```
 * The password-protected variant replaces `entries` with
 * `"salt"` + `"content"` (Argon2id + AES-256-GCM, AAD
 * `proton.authenticator.export.v1`) and is reported as
 * [ImporterError.ENCRYPTED_UNSUPPORTED] with an actionable hint.
 */
object ProtonAuthenticatorImporter : AuthenticatorImporter {

    override val id = "proton"
    override val displayName = "Proton Authenticator"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        if (!c.startsWith("{")) return false
        if ("\"entries\"" !in c) return false
        if ("\"db\"" in c || "\"services\"" in c || "\"tokens\"" in c) return false
        return "\"content\"" in c || "\"uri\"" in c
    }

    override fun parse(content: String): List<VaultAccount> {
        val root = try {
            parseJsonElement(content)
        } catch (e: Exception) {
            throw ImporterException(ImporterError.NOT_JSON, "not valid JSON")
        }
        val obj = root.asObj ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "Proton Authenticator export must be a JSON object"
        )

        if (obj["salt"] != null && obj["content"] != null) {
            val version = obj.intOrNull("version")
            if (version != null && version != 1) {
                throw ImporterException(
                    ImporterError.VERSION_UNSUPPORTED,
                    "Proton Authenticator export version $version is not supported yet"
                )
            }
            throw ImporterException(
                ImporterError.ENCRYPTED_UNSUPPORTED,
                "this Proton Authenticator file is password-protected; export it again without a password"
            )
        }

        val entries = obj.arr("entries") ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "Proton Authenticator export has no entries array"
        )
        val rows = mutableListOf<VaultAccount>()
        entries.forEach { element ->
            val content = element.asObj?.obj("content") ?: return@forEach
            val link = content.str("uri") ?: return@forEach
            var row = vaultRowFromLink(link) ?: return@forEach
            // "name" is the service title in Proton; use it to fill gaps.
            val name = (content["name"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (name.isNotEmpty()) {
                if (row.issuer.isBlank()) row = row.copy(issuer = name)
                else if (row.label.isBlank()) row = row.copy(label = name)
            }
            rows.add(row)
        }
        if (rows.isEmpty()) {
            throw ImporterException(
                ImporterError.EMPTY, "Proton Authenticator file contains no importable entries"
            )
        }
        return rows
    }
}
