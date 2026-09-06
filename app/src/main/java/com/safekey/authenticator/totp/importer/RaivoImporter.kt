package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount

/**
 * Parser for legacy Raivo OTP plain JSON exports (pre-2023 acquisition /
 * pre-ZIP backups).
 *
 * Raivo was acquired by Mobime in 2023; current versions only export
 * password-protected ZIP archives, which are out of scope here. The legacy
 * format is documented by the OtpTranslate article (tygertec.com) with a real
 * export sample and cross-checked against community importers (Ente Auth
 * accepts the same JSON). If the current Raivo format has drifted, this
 * importer still covers the widely used legacy JSON.
 *
 * Legacy export shape — a JSON array (a single object is also accepted):
 * ```
 * [{ "kind": "TOTP", "account": "google@gmail.com",
 *    "secret": "…", "issuer": "Google.com", "timer": "30",
 *    "digits": "6", "counter": "0", "algorithm": "SHA1",
 *    "iconType": "…", "iconValue": "…" }]
 * ```
 * Every value is a STRING in real exports (timer/digits/counter included);
 * numeric JSON values are tolerated too. `kind` values: TOTP / HOTP.
 */
object RaivoImporter : AuthenticatorImporter {

    override val id = "raivo"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        return "\"kind\"" in c && "\"account\"" in c && "\"secret\"" in c
    }

    override fun parse(content: String): List<VaultAccount> {
        val root = try {
            parseJsonElement(content)
        } catch (e: Exception) {
            throw ImporterException(ImporterError.NOT_JSON, "not valid JSON")
        }
        val entries: List<kotlinx.serialization.json.JsonElement> = when (root) {
            is kotlinx.serialization.json.JsonArray -> root.toList()
            is kotlinx.serialization.json.JsonObject -> listOf(root)
            else -> throw ImporterException(
                ImporterError.UNRECOGNIZED, "Raivo export must be a JSON array of entries"
            )
        }

        val rows = mutableListOf<VaultAccount>()
        entries.forEachIndexed { i, element ->
            val entry = element.asObj ?: throw ImporterException(
                ImporterError.UNRECOGNIZED, "Raivo entry #${i + 1} is not a JSON object"
            )
            vaultRow(
                issuer = entry.str("issuer"),
                label = entry.str("account"),
                secret = entry.str("secret"),
                typeRaw = entry.str("kind"),
                algorithm = entry.str("algorithm"),
                digits = entry.intOrNull("digits"),
                period = entry.intOrNull("timer"),
                counter = entry.longOrNull("counter")
            )?.let { rows.add(it) }
        }
        if (rows.isEmpty()) {
            throw ImporterException(ImporterError.EMPTY, "Raivo file contains no account entries")
        }
        return rows
    }
}
