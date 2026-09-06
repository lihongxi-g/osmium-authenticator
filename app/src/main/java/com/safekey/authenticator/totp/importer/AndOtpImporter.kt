package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount

/**
 * Parser for andOTP plaintext backups (JSON array of entries).
 *
 * Format facts cross-checked (2026-09) against Aegis' AndOtpImporter
 * (beemdevelopment/Aegis, GPL-3.0) and the andOTP project's own export
 * format. andOTP is archived upstream; no schema drift expected.
 *
 * Plain export shape (top level is a bare array, not an object):
 * ```
 * [{ "type": "TOTP"|"HOTP"|"STEAM", "secret": "…", "issuer": "…",
 *    "label": "…", "digits": 6, "period": 30, "algorithm": "SHA1",
 *    "counter": 1, "tags": [] }]
 * ```
 * - Types arrive UPPERCASE; secrets are padded Base32 ("…======").
 * - Password-encrypted and OpenPGP backups are BINARY, not JSON — they fail
 *   [detect] and surface as unrecognized files with an encrypted hint.
 * - Old andOTP versions without an issuer key encode it as "Issuer - Name"
 *   inside the label (mirroring Aegis' fallback).
 */
object AndOtpImporter : AuthenticatorImporter {

    override val id = "andotp"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        if (!c.startsWith("[")) return false
        // An empty export array is structurally andOTP's plaintext backup too
        // (an empty Raivo export would look identical; andOTP wins the tie).
        return c == "[]" || ("\"secret\"" in c && "\"type\"" in c && "\"label\"" in c)
    }

    override fun parse(content: String): List<VaultAccount> {
        val root = try {
            parseJsonElement(content)
        } catch (e: Exception) {
            throw ImporterException(
                ImporterError.NOT_JSON,
                "andOTP plaintext exports are JSON arrays; encrypted/OpenPGP backups are not supported yet"
            )
        }
        val array = root.asArr ?: throw ImporterException(
            ImporterError.UNRECOGNIZED,
            "andOTP plaintext export must be a JSON array of entries"
        )

        val rows = mutableListOf<VaultAccount>()
        array.forEachIndexed { i, element ->
            val entry = element.asObj ?: throw ImporterException(
                ImporterError.UNRECOGNIZED,
                "andOTP entry #${i + 1} is not a JSON object"
            )
            val rawIssuer = entry.str("issuer")
            val rawLabel = entry.str("label")
            // Legacy andOTP backups without an issuer field: "Issuer - Name"
            var issuer = rawIssuer
            var label = rawLabel
            if (issuer.isNullOrBlank() && rawLabel?.contains(" - ") == true) {
                issuer = rawLabel.substringBefore(" - ").trim()
                label = rawLabel.substringAfter(" - ").trim()
            }
            vaultRow(
                issuer = issuer,
                label = label,
                secret = entry.str("secret"),
                typeRaw = entry.str("type"),
                algorithm = entry.str("algorithm"),
                digits = entry.intOrNull("digits"),
                period = entry.intOrNull("period"),
                counter = entry.longOrNull("counter")
            )?.let { rows.add(it) }
        }
        if (rows.isEmpty()) {
            throw ImporterException(ImporterError.EMPTY, "andOTP file contains no account entries")
        }
        return rows
    }
}
