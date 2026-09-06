package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount

/**
 * Parser for LastPass Authenticator "accounts.json" exports
 * (Settings → Transfer accounts → Export accounts to file).
 *
 * LastPass publishes no schema; field names are cross-checked (2026-09)
 * against two independent community converters that parse real exports:
 * tghw/lastpass_aegis_convert and PaulSorensen/lastpass-2fa-converter (MIT).
 * Both agree on: `accounts` (and `Other Accounts`) arrays of entries with
 * `secret`, `userName`, `issuerName` / `originalIssuerName`, optional
 * `algorithm`, `digits`, `timeStep`.
 *
 * Export shape:
 * ```
 * { "accounts": [
 *     { "accountID": "…", "originalIssuerName": "GitHub",
 *       "issuerName": "GitHub", "userName": "octocat",
 *       "secret": "…", "algorithm": "SHA1", "digits": 6,
 *       "timeStep": 30, "isFavorite": false } ],
 *   "Other Accounts": [ … ] }
 * ```
 * LastPass Authenticator only issues TOTP tokens; defaults SHA1/6/30 apply
 * when the optional fields are absent.
 */
object LastPassImporter : AuthenticatorImporter {

    override val id = "lastpass"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        return c.startsWith("{") && ("\"accounts\"" in c || "\"Other Accounts\"" in c)
    }

    override fun parse(content: String): List<VaultAccount> {
        val root = try {
            parseJsonElement(content)
        } catch (e: Exception) {
            throw ImporterException(ImporterError.NOT_JSON, "not valid JSON")
        }
        val obj = root.asObj ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "accounts.json must be a JSON object"
        )
        // The app exports into two arrays: "accounts" (this device) and
        // "Other Accounts" (shared/legacy). Both are importable.
        val arrays = buildList {
            obj.arr("accounts")?.let { add(it) }
            obj.arr("Other Accounts")?.let { add(it) }
        }
        if (arrays.isEmpty()) {
            throw ImporterException(
                ImporterError.UNRECOGNIZED, "not a LastPass accounts.json (no accounts array)"
            )
        }

        val rows = mutableListOf<VaultAccount>()
        arrays.forEach { array ->
            array.forEachIndexed { i, element ->
                val account = element.asObj ?: throw ImporterException(
                    ImporterError.UNRECOGNIZED, "LastPass account #${i + 1} is not a JSON object"
                )
                vaultRow(
                    issuer = account.str("originalIssuerName") ?: account.str("issuerName"),
                    label = account.str("userName"),
                    secret = account.str("secret"),
                    typeRaw = "totp", // LastPass Authenticator issues TOTP only
                    algorithm = account.str("algorithm"),
                    digits = account.intOrNull("digits"),
                    period = account.intOrNull("timeStep"),
                    counter = null
                )?.let { rows.add(it) }
            }
        }
        if (rows.isEmpty()) {
            throw ImporterException(ImporterError.EMPTY, "LastPass file contains no account entries")
        }
        return rows
    }
}
