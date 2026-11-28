package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.icons.IconLimits
import com.safekey.authenticator.model.VaultAccount

/**
 * Parser for Aegis Authenticator export files (plaintext).
 *
 * Format facts cross-checked (2026-09) against beemdevelopment/Aegis
 * (GPL-3.0): VaultFile envelope + VaultEntry JSON, incl. the Aegis test
 * fixtures used verbatim in the unit tests.
 *
 * Plain export shape:
 * ```
 * { "version": 1, "header": { "slots": null, "params": null },
 *   "db": { "version": 1, "entries": [
 *     { "type": "totp"|"hotp"|"steam", "uuid": "…", "name": "…",
 *       "issuer": "…", "note": null, "icon": null,
 *       "info": { "secret": "…", "algo": "SHA1", "digits": 6,
 *                 "period": 30 | "counter": 1 } } ] } }
 * ```
 * Password-encrypted exports keep the same envelope but `db` becomes a single
 * Base64 ciphertext string and `header.slots` carries the scrypt slots —
 * [parse] reports [ImporterError.ENCRYPTED_UNSUPPORTED] with an actionable
 * hint (decryption is planned for a later version).
 */
object AegisImporter : AuthenticatorImporter {

    override val id = "aegis"
    override val displayName = "Aegis"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        return c.startsWith("{") && "\"db\"" in c && "\"header\"" in c
    }

    override fun parse(content: String): List<VaultAccount> {
        val root = try {
            parseJsonElement(content)
        } catch (e: Exception) {
            throw ImporterException(ImporterError.NOT_JSON, "not valid JSON")
        }
        val obj = root.asObj ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "Aegis export must be a JSON object"
        )

        // Encrypted vaults store db as a Base64 ciphertext string instead of
        // a JSON object holding "entries".
        val db = obj.obj("db") ?: run {
            val dbRaw = obj["db"]
            if (dbRaw != null) {
                throw ImporterException(
                    ImporterError.ENCRYPTED_UNSUPPORTED,
                    "this Aegis file is password-encrypted; export it again without a password"
                )
            }
            throw ImporterException(ImporterError.UNRECOGNIZED, "not an Aegis export (missing db)")
        }
        val entries = db.arr("entries") ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "Aegis db has no entries array"
        )

        val rows = mutableListOf<VaultAccount>()
        entries.forEachIndexed { i, element ->
            val entry = element.asObj ?: throw ImporterException(
                ImporterError.UNRECOGNIZED, "Aegis entry #${i + 1} is not a JSON object"
            )
            val info = entry.obj("info")
            val row = vaultRow(
                issuer = entry.str("issuer"),
                label = entry.str("name"),
                secret = info?.str("secret"),
                typeRaw = entry.str("type"),
                algorithm = info?.str("algo"), // Aegis spells it "algo"
                digits = info?.intOrNull("digits"),
                period = info?.intOrNull("period"),
                counter = info?.longOrNull("counter")
            ) ?: return@forEachIndexed
            // Aegis embeds the chosen icon in the export: base64 bytes plus a
            // MIME type (`icon`, `icon_mime`; `icon_hash` is not trusted).
            val icon = decodeIcon(entry.iconString("icon"), entry.iconString("icon_mime"))
            rows.add(
                if (icon == null) row
                else row.copy(icon = icon.first, iconMime = icon.second)
            )
        }
        if (rows.isEmpty()) {
            throw ImporterException(ImporterError.EMPTY, "Aegis file contains no account entries")
        }
        return rows
    }

    /** Base64 icon → bytes + MIME, rejecting unknown types and oversized payloads. */
    private fun decodeIcon(base64: String?, mime: String?): Pair<String, String>? {
        if (base64.isNullOrBlank()) return null
        // Bound the string before decoding: a multi-megabyte Base64 payload
        // would allocate its bytes before the size check below could run.
        if (base64.length > MAX_ICON_BASE64_CHARS) return null
        val normalizedMime = mime?.trim()?.lowercase() ?: return null
        if (normalizedMime !in IconLimits.ALLOWED_MIME) return null
        val bytes = try {
            java.util.Base64.getDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            return null
        }
        // Keep the Base64 string as-is (the model stores Base64); only the
        // decoded size is checked. 512 KB matches the storage cap and Aegis'
        // own 512 px limit is applied when the icon is rendered/stored.
        if (bytes.isEmpty() || bytes.size > IconLimits.MAX_STORED_BYTES) return null
        return base64 to normalizedMime
    }

    /** `JsonNull` is a `JsonPrimitive` with content "null", so it needs its own check. */
    private fun kotlinx.serialization.json.JsonObject.iconString(key: String): String? {
        val element = this[key] ?: return null
        if (element is kotlinx.serialization.json.JsonNull) return null
        val primitive = element as? kotlinx.serialization.json.JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.content.takeIf { it.isNotBlank() }
    }

    /** Ceiling on the Base64 text itself (≈ 512 KB of bytes plus padding). */
    private const val MAX_ICON_BASE64_CHARS = 700_000
}
