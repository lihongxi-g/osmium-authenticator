package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount

/**
 * Parser for **Stratum** / **Authenticator Pro** JSON exports.
 *
 * Format facts cross-checked (2026-10) against beemdevelopment/Aegis
 * `StratumImporter.JsonState` (GPL-3.0) and stratumauth/app
 * `Stratum.Core/Generator/HashAlgorithm.cs` (GPL-3.0).
 *
 * Shape — note the CAPITALIZED keys and numeric enums:
 * ```
 * { "Authenticators": [
 *     { "Type": 2, "Issuer": "GitHub", "Username": "octocat",
 *       "Secret": "BASE32", "Algorithm": 0, "Digits": 6,
 *       "Period": 30, "Counter": 0 } ] }
 * ```
 *  - `Type`: 1 = HOTP, 2 = TOTP, 4 = Steam.
 *  - `Algorithm`: index into Sha1=0, Sha256=1, Sha512=2. Anything higher is
 *    mapped to a token Osmium deliberately does not support (MD5), so the row
 *    is flagged in the preview instead of being imported with a wrong
 *    algorithm.
 *  - The encrypted `.authpro` export is binary (Argon2id + AES, header
 *    "AUTHENTICATORPRO") and surfaces as an unrecognized file.
 */
object StratumImporter : AuthenticatorImporter {

    private const val TYPE_HOTP = 1
    private const val TYPE_TOTP = 2
    private const val TYPE_STEAM = 4

    /** Stratum's HashAlgorithm enum, in ordinal order. */
    private val ALGORITHMS = listOf("SHA1", "SHA256", "SHA512")

    override val id = "stratum"
    override val displayName = "Stratum / Authenticator Pro"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        return c.startsWith("{") && "\"Authenticators\"" in c
    }

    override fun parse(content: String): List<VaultAccount> {
        val root = try {
            parseJsonElement(content)
        } catch (e: Exception) {
            throw ImporterException(ImporterError.NOT_JSON, "not valid JSON")
        }
        val obj = root.asObj ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "Stratum export must be a JSON object"
        )
        val entries = obj.arr("Authenticators") ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "Stratum export has no Authenticators array"
        )

        val rows = mutableListOf<VaultAccount>()
        entries.forEach { element ->
            val entry = element.asObj ?: return@forEach
            val typeRaw = when (entry.intOrNull("Type")) {
                TYPE_HOTP -> "hotp"
                TYPE_TOTP -> "totp"
                TYPE_STEAM -> "steam"
                // Unknown type: keep the raw token so ImportSupport flags the
                // row instead of silently importing it as TOTP.
                else -> "unknown"
            }
            val algorithmIndex = entry.intOrNull("Algorithm")
            val algorithm = ALGORITHMS.getOrNull(algorithmIndex ?: 0)
                ?: "MD5" // out of range / MD5: unsupported on purpose
            vaultRow(
                issuer = entry.str("Issuer"),
                label = entry.str("Username"),
                secret = entry.str("Secret"),
                typeRaw = typeRaw,
                algorithm = algorithm,
                digits = entry.intOrNull("Digits"),
                period = entry.intOrNull("Period"),
                counter = entry.longOrNull("Counter")
            )?.let { rows.add(it) }
        }
        if (rows.isEmpty()) {
            throw ImporterException(ImporterError.EMPTY, "Stratum file contains no authenticator entries")
        }
        return rows
    }
}
