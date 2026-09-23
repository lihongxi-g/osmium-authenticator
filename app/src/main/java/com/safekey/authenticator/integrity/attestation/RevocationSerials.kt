package com.safekey.authenticator.integrity.attestation

import java.math.BigInteger

/**
 * Serial-number normalization for the offline revocation snapshot.
 *
 * The bundled `attestation_status.json` mixes two key formats — of its 1746
 * entries, 976 are pure decimal renderings of 64-bit serials (17-20 digits) and
 * the rest are lowercase hex — while the verifier matches
 * `cert.serialNumber.toString(16)`, which is lowercase hex with no leading
 * zeros. Without normalization every decimal entry (and every zero-padded hex
 * entry) could never match a certificate, so a revoked attestation key would
 * still verify.
 *
 * [normalize] therefore stores, for every entry, the key itself plus its
 * unpadded hex form(s); a lookup by `toString(16)` then succeeds whichever
 * format the file used.
 *
 * Pure JVM — unit-tested in RevocationSerialsTest.
 */
internal object RevocationSerials {

    private const val HEX_DIGITS = "0123456789abcdef"

    /** Expanded set: every entry in its raw, unpadded-hex and decimal-to-hex form. */
    fun normalize(raw: Set<String>): Set<String> {
        val out = HashSet<String>(raw.size * 2)
        for (key in raw) {
            val lower = key.trim().lowercase()
            if (lower.isEmpty()) continue
            out += lower
            unpaddedHex(lower)?.let { out += it }
            decimalToHex(lower)?.let { out += it }
        }
        return out
    }

    /** Lowercase hex without leading zeros (the form `serialNumber.toString(16)` uses). */
    fun unpaddedHex(key: String): String? {
        val k = key.trim().lowercase()
        if (k.isEmpty() || k.any { it !in HEX_DIGITS }) return null
        val stripped = k.trimStart('0')
        return if (stripped.isEmpty()) "0" else stripped
    }

    /** Hex form of a decimal serial rendering, or null when [key] is not decimal. */
    fun decimalToHex(key: String): String? {
        val k = key.trim()
        if (k.isEmpty() || k.length > 40 || k.any { !it.isDigit() }) return null
        return try {
            BigInteger(k).toString(16)
        } catch (_: NumberFormatException) {
            null
        }
    }
}
