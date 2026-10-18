package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount
import kotlinx.serialization.json.JsonArray

/**
 * Parser for **andOTP** plaintext backups.
 *
 * Format facts cross-checked (2026-10) against beemdevelopment/Aegis
 * `AndOtpImporter` (GPL-3.0, same licence family as Osmium) and andOTP's own
 * `otp_token` schema.
 *
 * Plain export shape — a JSON array (andOTP has no top-level object):
 * ```
 * [{ "type": "TOTP"|"HOTP"|"STEAM", "algorithm": "SHA1", "digits": 6,
 *    "secret": "BASE32", "period": 30, "counter": 0,
 *    "label": "account", "issuer": "GitHub" }]
 * ```
 * Differences handled here:
 *  - `issuer` only exists in newer backups; older ones pack both halves into
 *    `label` as "Issuer - account", which is split back apart.
 *  - andOTP password-protected backups are BINARY (AES-GCM), not JSON. They
 *    cannot be detected from text at all, so they surface as an unrecognized
 *    file — the fix is to export again without a password.
 */
object AndOtpImporter : AuthenticatorImporter {

    override val id = "andotp"
    override val displayName = "andOTP"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        // Raivo exports are also JSON arrays with a secret; its "kind" key is
        // what tells them apart, and RaivoImporter runs first anyway.
        return c.startsWith("[") && "\"secret\"" in c && "\"type\"" in c && "\"kind\"" !in c
    }

    override fun parse(content: String): List<VaultAccount> {
        val root = try {
            parseJsonElement(content)
        } catch (e: Exception) {
            throw ImporterException(ImporterError.NOT_JSON, "not valid JSON")
        }
        val array = root as? JsonArray ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "andOTP export must be a JSON array"
        )

        val rows = mutableListOf<VaultAccount>()
        array.forEachIndexed { i, element ->
            val entry = element.asObj ?: throw ImporterException(
                ImporterError.UNRECOGNIZED, "andOTP entry #${i + 1} is not a JSON object"
            )
            val labelRaw = entry.str("label").orEmpty()
            val issuerRaw = entry.str("issuer")
            // Old backups: label carries "Issuer - account".
            val issuer = issuerRaw ?: labelRaw.takeIf { " - " in it }?.substringBefore(" - ")
            val label = if (issuerRaw == null && " - " in labelRaw) {
                labelRaw.substringAfter(" - ")
            } else {
                labelRaw
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
