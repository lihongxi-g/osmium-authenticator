package com.safekey.authenticator.totp

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
    val period: Int
)

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
        if (!uri.host.equals("totp", ignoreCase = true)) {
            throw IllegalArgumentException("Only TOTP (time-based) accounts are supported")
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
            if (d != 6 && d != 8) throw IllegalArgumentException("Unsupported digits: $d")
            d
        }

        val period = (query["period"] ?: "30").let { raw ->
            val p = raw.toIntOrNull() ?: throw IllegalArgumentException("Invalid period: $raw")
            if (p !in 1..600) throw IllegalArgumentException("Invalid period: $p")
            p
        }

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
            period = period
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
