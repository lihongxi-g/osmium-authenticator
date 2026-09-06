package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount

/**
 * Parser for 2FAS export files (".2fas", no-password export).
 *
 * Format facts cross-checked (2026-09) against Aegis' TwoFASImporter
 * (beemdevelopment/Aegis, GPL-3.0), the twofas/2fas-android schema and Aegis
 * test fixtures (schemaVersion 1..4, used verbatim in the unit tests).
 *
 * Plain export shape (schemaVersion 1..4; details drifted between versions):
 * ```
 * { "schemaVersion": 4, "appOrigin": "android", "services": [
 *     { "name": "GitHub", "secret": "…", "otp": {
 *         "account": "octocat", "issuer": "GitHub", "label": "…",
 *         "digits": 6, "period": 30, "algorithm": "SHA1",
 *         "counter": 1, "tokenType": "TOTP"|"HOTP"|"STEAM" } } ],
 *   "groups": [] }
 * ```
 * - schemaVersion 1/2 have no `tokenType` (all TOTP) and v1 even omits the
 *   otp parameters — defaults apply (SHA1 / 6 digits / 30 s).
 * - `name` is the service title shown in 2FAS (fallback for the issuer).
 * - Password-encrypted exports keep `services` empty and carry the ciphertext
 *   in `servicesEncrypted` ("data:salt:iv", PBKDF2 10k + AES-256-GCM) →
 *   reported as [ImporterError.ENCRYPTED_UNSUPPORTED].
 */
object TwoFasImporter : AuthenticatorImporter {

    /** Highest 2FAS schemaVersion whose entry shape is known. */
    private const val MAX_SCHEMA_VERSION = 4

    override val id = "2fas"

    override fun detect(content: String): Boolean {
        val c = content.cleanJsonText()
        return c.startsWith("{") && "\"services\"" in c && "\"schemaVersion\"" in c
    }

    override fun parse(content: String): List<VaultAccount> {
        val root = try {
            parseJsonElement(content)
        } catch (e: Exception) {
            throw ImporterException(ImporterError.NOT_JSON, "not valid JSON")
        }
        val obj = root.asObj ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "2FAS export must be a JSON object"
        )

        val encrypted = (obj["servicesEncrypted"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.content?.isNotEmpty() == true
        if (encrypted) {
            throw ImporterException(
                ImporterError.ENCRYPTED_UNSUPPORTED,
                "this 2FAS backup is password-protected; export it again without a password (取消勾选密码)"
            )
        }

        val schemaVersion = obj.intOrNull("schemaVersion")
        if (schemaVersion != null && schemaVersion > MAX_SCHEMA_VERSION) {
            throw ImporterException(
                ImporterError.VERSION_UNSUPPORTED,
                "2FAS schemaVersion $schemaVersion is newer than supported ($MAX_SCHEMA_VERSION); update Osmium or export from 2FAS again"
            )
        }

        val services = obj.arr("services") ?: throw ImporterException(
            ImporterError.UNRECOGNIZED, "2FAS export has no services array"
        )

        val rows = mutableListOf<VaultAccount>()
        services.forEachIndexed { i, element ->
            val service = element.asObj ?: throw ImporterException(
                ImporterError.UNRECOGNIZED, "2FAS service #${i + 1} is not a JSON object"
            )
            // 2FAS entries without an otp block are not OTP tokens (e.g.
            // leftover service stubs) — nothing importable can be derived.
            val otp = service.obj("otp") ?: return@forEachIndexed

            val serviceName = service.str("name")
            val labelRaw = otp.str("label")
            val labelPrefix = labelRaw?.takeIf { ":" in it }?.substringBefore(":")
            val account = otp.str("account")
            val label = account
                ?: labelRaw?.let { if (":" in it) it.substringAfter(":").trim() else it.trim() }

            val issuer = serviceName
                ?: otp.str("issuer")
                ?: labelPrefix

            vaultRow(
                issuer = issuer,
                label = label,
                secret = service.str("secret"),
                typeRaw = otp.str("tokenType"),
                algorithm = otp.str("algorithm"),
                digits = otp.intOrNull("digits"),
                period = otp.intOrNull("period"),
                counter = otp.longOrNull("counter")
            )?.let { rows.add(it) }
        }
        if (rows.isEmpty()) {
            throw ImporterException(
                ImporterError.EMPTY,
                "2FAS file contains no importable services (are the services stored in a group export?)"
            )
        }
        return rows
    }
}
