package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.totp.Base32
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parser for **FreeOTP** / **FreeOTP+** JSON exports.
 *
 * Format facts cross-checked (2026-10) against beemdevelopment/Aegis
 * `FreeOtpPlusImporter` + `FreeOtpImporter.DecryptedStateV1` (GPL-3.0).
 *
 * Shape:
 * ```
 * { "tokenOrder": ["0","1"], "tokens": [
 *     { "type": "TOTP", "algo": "SHA1", "digits": 6,
 *       "secret": [35,71,83,87,...],        // NOTE: a byte array, not Base32
 *       "period": 30, "counter": 0,
 *       "issuerExt": "GitHub", "label": "octocat" } ] }
 * ```
 * Two details that break naive parsers:
 *  - `secret` is a JSON ARRAY of raw bytes in the file export (FreeOTP+ stores
 *    the token as Java-serialized bytes), so it is Base32-encoded here.
 *  - the issuer lives in `issuerExt`, not `issuer`; `label` is the account.
 * FreeOTP marks Steam with `issuerExt = "Steam"`, which [vaultRow] maps to
 * Osmium's Steam handling.
 */
object FreeOtpPlusImporter : AuthenticatorImporter {

    override val id = "freeotp"
    override val displayName = "FreeOTP"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        return c.startsWith("{") && "\"tokens\"" in c
    }

    override fun parse(content: String): List<VaultAccount> {
        val root = try {
            parseJsonElement(content)
        } catch (e: Exception) {
            throw ImporterException(ImporterError.NOT_JSON, "not valid JSON")
        }
        val obj = root.asObj ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "FreeOTP export must be a JSON object"
        )
        val tokens = obj.arr("tokens") ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "FreeOTP export has no tokens array"
        )

        val rows = mutableListOf<VaultAccount>()
        tokens.forEachIndexed { i, element ->
            val token = element.asObj ?: throw ImporterException(
                ImporterError.UNRECOGNIZED, "FreeOTP token #${i + 1} is not a JSON object"
            )
            val secret = byteArraySecret(token["secret"]) ?: token.str("secret")
            vaultRow(
                issuer = token.str("issuerExt") ?: token.str("issuer"),
                label = token.str("label"),
                secret = secret,
                typeRaw = token.str("type"),
                algorithm = token.str("algo"),
                digits = token.intOrNull("digits"),
                period = token.intOrNull("period"),
                counter = token.longOrNull("counter")
            )?.let { rows.add(it) }
        }
        if (rows.isEmpty()) {
            throw ImporterException(ImporterError.EMPTY, "FreeOTP file contains no token entries")
        }
        return rows
    }

    /** Base32-encodes a JSON byte array ("secret": [35, 71, …]); null if it is not one. */
    private fun byteArraySecret(element: kotlinx.serialization.json.JsonElement?): String? {
        val array = element as? JsonArray ?: return null
        if (array.isEmpty()) return null
        val bytes = ByteArray(array.size)
        array.forEachIndexed { index, item ->
            val value = (item as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
            if (value < 0 || value > 255) return null
            bytes[index] = value.toByte()
        }
        return Base32.encode(bytes).replace("=", "")
    }
}
