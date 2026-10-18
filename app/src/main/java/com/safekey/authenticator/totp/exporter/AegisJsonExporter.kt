package com.safekey.authenticator.totp.exporter

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Writes an Aegis-compatible PLAINTEXT vault file.
 *
 * The envelope mirrors what Aegis itself produces and what Osmium's
 * [com.safekey.authenticator.totp.importer.AegisImporter] already reads, so the
 * output round-trips through our own importer (covered by unit tests) and opens
 * in Aegis "Import from file → Aegis plaintext":
 * ```
 * { "version": 1, "header": { "slots": null, "params": null },
 *   "db": { "version": 1, "entries": [
 *     { "type": "totp", "uuid": "…", "name": "label", "issuer": "…",
 *       "note": null, "icon": null,
 *       "info": { "secret": "…", "algo": "SHA1", "digits": 6, "period": 30 } } ] } }
 * ```
 * HOTP entries carry `counter` instead of `period`; Steam entries use type
 * "steam" with Aegis' fixed 5 digits.
 *
 * The `uuid` is derived from issuer+label+secret so repeated exports stay
 * stable and Aegis can recognize duplicates when a user imports twice.
 */
internal object AegisJsonExporter {

    private val json = Json { prettyPrint = true }

    fun build(accounts: List<VaultAccount>): String {
        val entries = buildJsonArray {
            accounts.forEach { account -> add(entry(account)) }
        }
        val root = buildJsonObject {
            put("version", JsonPrimitive(1))
            put("header", buildJsonObject {
                put("slots", JsonNull)
                put("params", JsonNull)
            })
            put("db", buildJsonObject {
                put("version", JsonPrimitive(1))
                put("entries", entries)
            })
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    private fun entry(account: VaultAccount): JsonObject {
        val isSteam = account.issuer.equals("Steam", ignoreCase = true)
        val isHotp = account.type == Account.TYPE_HOTP
        val type = when {
            isSteam -> "steam"
            isHotp -> Account.TYPE_HOTP
            else -> Account.TYPE_TOTP
        }
        val info = buildJsonObject {
            put("secret", JsonPrimitive(account.secret))
            put("algo", JsonPrimitive(account.algorithm))
            put("digits", JsonPrimitive(if (isSteam) 5 else account.digits))
            if (isHotp) {
                put("counter", JsonPrimitive(account.counter))
            } else {
                put("period", JsonPrimitive(account.period))
            }
        }
        return buildJsonObject {
            put("type", JsonPrimitive(type))
            put("uuid", JsonPrimitive(stableUuid(account)))
            put("name", JsonPrimitive(account.label))
            put("issuer", JsonPrimitive(account.issuer))
            put("note", JsonNull)
            put("icon", JsonNull)
            put("info", info)
        }
    }

    /** Deterministic, format-valid UUID so re-exports keep the same identity. */
    private fun stableUuid(account: VaultAccount): String {
        val seed = "${account.issuer}|${account.label}|${account.secret}"
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
