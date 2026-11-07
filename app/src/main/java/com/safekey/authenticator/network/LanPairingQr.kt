package com.safekey.authenticator.network

/**
 * The pairing payload the receiving device renders as a QR code.
 *
 * Format: `OSMIUM1|<host>|<port>|<code>`
 *
 * Why a QR instead of digits typed by hand: the code alone identifies the
 * session, but the *address* still had to be discovered (mDNS) or typed, and a
 * human typing 12 digits gets it wrong. The QR carries both, so one scan pairs
 * the two devices, and nothing about the trust model changes — the payload is
 * only visible on the receiving device's screen, exactly like the digits it
 * replaces (that screen also blocks screenshots).
 *
 * Parsing is strict on purpose: a QR code can be produced by anyone, so a
 * payload is only accepted when every field is well formed *and* the target is a
 * local unicast address (the same rule the hand-typed address must satisfy).
 */
internal object LanPairingQr {

    /** Bumped only if the field layout changes; old payloads then fail cleanly. */
    const val PREFIX = "OSMIUM1"

    private const val SEPARATOR = "|"

    data class Payload(val host: String, val port: Int, val code: String)

    /** Text to draw, or null when the session is not ready to be paired yet. */
    fun encode(host: String, port: Int, code: String): String? {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return null
        if (port !in 1..65535) return null
        if (!LanSession.isWellFormedCode(code)) return null
        return listOf(PREFIX, trimmed, port.toString(), code).joinToString(SEPARATOR)
    }

    /** Parsed pairing target, or null for anything that is not exactly one of ours. */
    fun parse(raw: String): Payload? {
        val parts = raw.trim().split(SEPARATOR)
        if (parts.size != 4) return null
        if (parts[0] != PREFIX) return null
        val host = parts[1].trim()
        val port = parts[2].trim().toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        val code = parts[3].trim()
        if (!LanSession.isWellFormedCode(code)) return null
        // A scanned address gets the same scrutiny as a typed one.
        if (LanSendTarget.literalIsLocalUnicast(host) != true) return null
        return Payload(host, port, code)
    }
}
