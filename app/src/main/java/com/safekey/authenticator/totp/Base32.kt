package com.safekey.authenticator.totp

import java.io.ByteArrayOutputStream

/**
 * RFC 4648 Base32 codec with lenient input handling (lowercase, spaces,
 * dashes, optional padding) — real-world secrets are often pasted with noise.
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DECODE_TABLE = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, c -> table[c.code] = index }
        // lowercase → uppercase mapping
        for (c in 'a'..'z') table[c.code] = table[c.uppercaseChar().code]
    }

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(ALPHABET[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        while (sb.length % 8 != 0) sb.append('=')
        return sb.toString()
    }

    /**
     * @throws IllegalArgumentException when the input contains a character
     * outside the Base32 alphabet.
     */
    fun decode(input: String): ByteArray {
        val cleaned = input
            .replace(" ", "")
            .replace("-", "")
            .replace("=", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")
        if (cleaned.isEmpty()) return ByteArray(0)

        val out = ByteArrayOutputStream()
        var buffer = 0
        var bitsLeft = 0
        for (c in cleaned) {
            val v = if (c.code < DECODE_TABLE.size) DECODE_TABLE[c.code] else -1
            if (v < 0) {
                throw IllegalArgumentException("Invalid Base32 character: $c")
            }
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.write((buffer shr bitsLeft) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    /** True when the string decodes successfully (used for input validation). */
    fun isValid(input: String): Boolean = try {
        decode(input).isNotEmpty()
    } catch (_: Exception) {
        false
    }
}
