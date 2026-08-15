package com.safekey.authenticator.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP + RFC 4226 HOTP generator.
 *
 * Uses the platform's JCE MAC implementations (HmacSHA1/SHA256/SHA512) —
 * no hand-rolled cryptography.
 */
object TotpGenerator {

    /**
     * Steam Guard alphabet. Steam uses the same TOTP core (HMAC-SHA1, 30s,
     * 5-char output) but maps the 4-byte code onto a 26-char alphabet
     * instead of decimal digits. Widely documented / verified by the open
     * source community (SteamDesktopAuthenticator etc.).
     */
    const val STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"

    /** Generate the TOTP code valid at [timeMs] for the given parameters.
     *  For HOTP pass [counter] explicitly (time-based window is skipped). */
    fun generate(
        secret: ByteArray,
        timeMs: Long,
        period: Int,
        digits: Int,
        algorithm: String,
        steamAlphabet: String? = null,
        counter: Long? = null
    ): String {
        val c = counter ?: (Math.floorDiv(timeMs, 1000L) / period)
        return hotp(secret, c, digits, algorithm, steamAlphabet)
    }

    /** RFC 4226 HOTP with a long counter. */
    fun hotp(
        secret: ByteArray,
        counter: Long,
        digits: Int,
        algorithm: String,
        steamAlphabet: String? = null
    ): String {
        val mac = Mac.getInstance(macName(algorithm))
        mac.init(SecretKeySpec(secret, macName(algorithm)))
        // Big-endian 8-byte counter covering the full 64-bit range
        val msg = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            msg[i] = (c and 0xFF).toByte()
            c = c ushr 8
        }
        val hash = mac.doFinal(msg)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        if (steamAlphabet != null) {
            // Steam: 26-ary encoding of the full 4-byte value, 5 chars,
            // LSB-first, concatenated directly (no reversal — that's the
            // canonical SteamGuard algorithm).
            var value = binary
            val sb = StringBuilder(5)
            for (i in 0 until 5) {
                sb.append(steamAlphabet[value % steamAlphabet.length])
                value /= steamAlphabet.length
            }
            return sb.toString()
        }

        val otp = binary % pow10(digits)
        return otp.toString().padStart(digits, '0')
    }

    /** Seconds remaining until the current code expires. */
    fun remainingSeconds(timeMs: Long, period: Int): Int {
        val elapsed = Math.floorMod(Math.floorDiv(timeMs, 1000L), period.toLong())
        return (period - elapsed).toInt()
    }

    /** Fraction of the current period already elapsed, in [0, 1). */
    fun periodFraction(timeMs: Long, period: Int): Float {
        val elapsed = Math.floorMod(Math.floorDiv(timeMs, 1000L), period.toLong())
        return elapsed.toFloat() / period.toFloat()
    }

    private fun macName(algorithm: String): String = when (algorithm.uppercase()) {
        "SHA256" -> "HmacSHA256"
        "SHA512" -> "HmacSHA512"
        else -> "HmacSHA1"
    }

    private fun pow10(digits: Int): Int = when (digits) {
        8 -> 100_000_000
        else -> 1_000_000
    }
}
