package com.safekey.authenticator.totp.exporter

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import java.nio.charset.StandardCharsets

/**
 * Serializes an account into a standard `otpauth://` enrollment link.
 *
 * Deliberately pure JVM (no `android.net.Uri`): the export writers are unit
 * tested on the desktop, and percent-encoding has to follow RFC 3986 exactly —
 * only unreserved characters stay literal, everything else becomes %XX.
 *
 * The label keeps the `issuer:account` colon LITERAL (the Key URI spec treats
 * it as the separator, not data) because that is what other authenticators
 * expect when they parse an imported link.
 */
internal fun VaultAccount.toOtpLink(): String {
    val labelPart = if (issuer.isBlank()) {
        encodeComponent(label.ifBlank { "Osmium" })
    } else {
        encodeComponent(issuer) + ":" + encodeComponent(label)
    }
    val type = if (type == Account.TYPE_HOTP) Account.TYPE_HOTP else Account.TYPE_TOTP
    val query = StringBuilder()
    query.append("secret=").append(encodeComponent(secret))
    if (issuer.isNotBlank()) query.append("&issuer=").append(encodeComponent(issuer))
    query.append("&algorithm=").append(encodeComponent(algorithm))
    query.append("&digits=").append(digits)
    if (type == Account.TYPE_HOTP) {
        query.append("&counter=").append(counter)
    } else {
        query.append("&period=").append(period)
    }
    return "otpauth://$type/$labelPart?$query"
}

private const val HEX = "0123456789ABCDEF"

/** RFC 3986 percent-encoding of one URI component (UTF-8 bytes). */
internal fun encodeComponent(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        val unreserved = code < 128 && (char.isLetterOrDigit() || char == '-' || char == '.' || char == '_' || char == '~')
        if (unreserved) {
            out.append(char)
        } else {
            out.append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
        }
    }
    return out.toString()
}
