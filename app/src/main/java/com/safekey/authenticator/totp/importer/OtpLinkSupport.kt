package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.totp.OtpUriParser
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Turns one enrollment LINK into a [VaultAccount].
 *
 * Several sources store their tokens as links instead of field sets:
 * Bitwarden (`login.totp`), Proton Authenticator (`content.uri`), and the
 * plain-text otpauth export shared by Ente Auth and Google Authenticator's URI
 * export. All of them are normalized here so each importer stays tiny and
 * applies the exact same rules as the QR / paste path ([OtpUriParser]).
 *
 * Steam Guard tokens are mapped locally instead of through [OtpUriParser]:
 * that parser rejects Steam on purpose (manual entry has to state
 * `issuer = Steam`), but during a migration the token is unambiguous, and
 * Osmium renders Steam codes purely from the issuer.
 */
internal fun vaultRowFromLink(raw: String?): VaultAccount? {
    val link = raw?.trim().orEmpty()
    if (link.isEmpty()) return null

    // Bitwarden stores Steam tokens as "steam://<base32 secret>".
    if (link.startsWith("steam://", ignoreCase = true)) {
        val secret = percentDecode(link.removePrefix("steam://").substringBefore('?').substringBefore('/'))
        return if (secret.isBlank()) null else steamRow(secret)
    }
    if (!link.startsWith("otpauth://", ignoreCase = true)) return null

    if (linkIsSteam(link)) {
        val secret = queryParams(link)["secret"].orEmpty()
        return if (secret.isBlank()) null else steamRow(secret)
    }

    val parsed = try {
        OtpUriParser.parse(link)
    } catch (_: IllegalArgumentException) {
        return null
    }
    return vaultRow(
        issuer = parsed.issuer,
        label = parsed.label,
        secret = parsed.secret,
        typeRaw = parsed.type,
        algorithm = parsed.algorithm,
        digits = parsed.digits,
        period = parsed.period,
        counter = parsed.counter
    )
}

private fun steamRow(secret: String): VaultAccount? = vaultRow(
    issuer = "Steam",
    label = "Steam",
    secret = secret,
    typeRaw = "steam",
    algorithm = null,
    digits = null,
    period = null,
    counter = null
)

/** True when the link carries `issuer=Steam` or a "Steam:account" label prefix. */
private fun linkIsSteam(link: String): Boolean {
    if (queryParams(link)["issuer"]?.equals("Steam", ignoreCase = true) == true) return true
    val label = link.substringAfter("://").substringAfter('/', "")
    return percentDecode(label).substringBefore(':').trim().equals("Steam", ignoreCase = true)
}

private fun queryParams(link: String): Map<String, String> =
    link.substringAfter('?', "")
        .split('&')
        .filter { it.isNotEmpty() }
        .associate { part ->
            percentDecode(part.substringBefore('=')) to percentDecode(part.substringAfter('=', ""))
        }

/** Percent-decodes [value], returning it untouched when it is not valid encoding. */
internal fun percentDecode(value: String): String = try {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
} catch (_: Exception) {
    value
}
