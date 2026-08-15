package com.safekey.authenticator.totp

import android.net.Uri
import com.safekey.authenticator.model.Account
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Result of parsing an otpauth:// URI. */
data class ParsedOtpUri(
    val issuer: String,
    val label: String,
    val secret: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val type: String = Account.TYPE_TOTP,
    val counter: Long = 0
)

/**
 * Serializes an account back into a standard otpauth:// URI (for sharing).
 * Mirrors the exact parameters Osmium parsed on import.
 */
fun Account.toOtpUri(): String {
    val labelPart = if (issuer.isNotBlank()) Uri.encode("$issuer:$label") else Uri.encode(label)
    val issuerPart = Uri.encode(issuer)
    val base = "otpauth://$type/$labelPart?secret=$secret" +
        "&issuer=$issuerPart&algorithm=$algorithm&digits=$digits"
    return if (isHotp) "$base&counter=$counter" else "$base&period=$period"
}

/**
 * Parser for otpauth:// URIs as produced by most TOTP enrollment QR codes.
 *
 * Format: otpauth://totp/Issuer:account?secret=...&issuer=...&algorithm=...&digits=...&period=...
 *
 * @throws IllegalArgumentException with a user-facing reason on any malformed input.
 */
object OtpUriParser {

    fun parse(rawUri: String): ParsedOtpUri {
        val trimmed = rawUri.trim()
        if (!trimmed.startsWith("otpauth://", ignoreCase = true)) {
            throw IllegalArgumentException("Not an otpauth:// URI")
        }
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed URI", e)
        }
        if (!uri.scheme.equals("otpauth", ignoreCase = true)) {
            throw IllegalArgumentException("Unsupported scheme: ${uri.scheme}")
        }
        val type = when {
            uri.host.equals("totp", ignoreCase = true) -> Account.TYPE_TOTP
            uri.host.equals("hotp", ignoreCase = true) -> Account.TYPE_HOTP
            else -> throw IllegalArgumentException("Unsupported type: ${uri.host} (expected totp or hotp)")
        }

        val query = parseQuery(uri.rawQuery)
        val secretRaw = query["secret"]?.trim().orEmpty()
        if (secretRaw.isEmpty()) {
            throw IllegalArgumentException("Missing secret parameter")
        }
        val secretBytes = try {
            Base32.decode(secretRaw)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Secret is not valid Base32", e)
        }
        if (secretBytes.size < 10) {
            throw IllegalArgumentException("Secret is too short (${secretBytes.size} bytes)")
        }
        // Normalize secret back to canonical Base32 without padding
        val secret = Base32.encode(secretBytes).replace("=", "")

        val algorithmRaw = query["algorithm"]?.uppercase() ?: "SHA1"
        val algorithm = when (algorithmRaw) {
            "SHA1", "SHA256", "SHA512" -> algorithmRaw
            else -> throw IllegalArgumentException("Unsupported algorithm: $algorithmRaw")
        }

        val digits = (query["digits"] ?: "6").let { raw ->
            val d = raw.toIntOrNull() ?: throw IllegalArgumentException("Invalid digits: $raw")
            // Steam Guard codes are 5 characters — accept digits=5 only when
            // the account is marked as Steam (query issuer or label prefix).
            val steamByQuery = query["issuer"]?.equals("Steam", ignoreCase = true) == true
            val rawLabel = urlDecode(uri.rawPath.removePrefix("/"))
            val steamByLabel = rawLabel.equals("Steam", ignoreCase = true) ||
                rawLabel.startsWith("Steam:", ignoreCase = true)
            if (d != 6 && d != 8 && !(d == 5 && (steamByQuery || steamByLabel))) {
                throw IllegalArgumentException("Unsupported digits: $d")
            }
            d
        }

        val period = (query["period"] ?: "30").let { raw ->
            val p = raw.toIntOrNull() ?: throw IllegalArgumentException("Invalid period: $raw")
            if (p !in 1..600) throw IllegalArgumentException("Invalid period: $p")
            p
        }

        // HOTP: initial counter (RFC 4226, defaults to 0)
        val counter = if (type == Account.TYPE_HOTP) {
            (query["counter"] ?: "0").let { raw ->
                val c = raw.toLongOrNull() ?: throw IllegalArgumentException("Invalid counter: $raw")
                if (c < 0) throw IllegalArgumentException("Negative counter: $c")
                c
            }
        } else 0L

        val labelRaw = urlDecode(uri.rawPath.removePrefix("/"))
        if (labelRaw.isBlank()) {
            throw IllegalArgumentException("Missing account label")
        }

        // issuer resolution: query param wins, then the label prefix ("Issuer:account")
        val issuerParam = query["issuer"]?.trim().orEmpty()
        val labelPrefixIssuer = labelRaw.substringBefore(":", "").takeIf { labelRaw.contains(":") }
        val issuer = when {
            issuerParam.isNotBlank() -> issuerParam
            labelPrefixIssuer?.isNotBlank() == true -> labelPrefixIssuer
            else -> ""
        }
        val label = when {
            issuerParam.isNotBlank() && labelPrefixIssuer != null -> labelRaw.substringAfter(":")
            labelPrefixIssuer != null -> labelRaw.substringAfter(":")
            else -> labelRaw
        }.trim()

        return ParsedOtpUri(
            issuer = issuer.trim(),
            label = label,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            type = type,
            counter = counter
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { part ->
            if (part.isEmpty()) return@mapNotNull null
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            urlDecode(key) to urlDecode(value)
        }.toMap()
    }

    private fun urlDecode(s: String): String = try {
        URLDecoder.decode(s, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        s
    }
}
