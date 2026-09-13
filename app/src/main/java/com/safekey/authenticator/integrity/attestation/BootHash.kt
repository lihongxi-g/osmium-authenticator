package com.safekey.authenticator.integrity.attestation

import java.util.Base64

/**
 * Normalization helpers for the verified-boot hash cross-check: the
 * attestation chain carries the boot hash as bytes while
 * `ro.boot.vbmeta.digest` is a hex (occasionally base64) string. Pure logic,
 * unit-tested on the JVM.
 */
internal object BootHash {

    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")
    private const val HEX_CHARS = "0123456789abcdef"

    fun toHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX_CHARS[v ushr 4])
            out.append(HEX_CHARS[v and 0x0F])
        }
        return out.toString()
    }

    /** Normalizes the runtime property value to lowercase hex, or null. */
    fun propToHex(value: String?): String? {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (HEX64.matches(v)) return v.lowercase()
        return try {
            val decoded = Base64.getDecoder().decode(v)
            if (decoded.size == 32) toHex(decoded) else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Compares the attested hash (hex) with the runtime property. Returns
     * null when either side is missing or the formats are not comparable —
     * unavailable data must never fabricate a mismatch.
     */
    fun compare(attestedHex: String?, runtimeProp: String?): Boolean? {
        val attested = attestedHex?.trim()?.lowercase()?.takeIf { HEX64.matches(it) } ?: return null
        val runtime = propToHex(runtimeProp) ?: return null
        return attested == runtime
    }
}
