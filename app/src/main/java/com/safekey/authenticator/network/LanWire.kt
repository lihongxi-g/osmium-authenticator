package com.safekey.authenticator.network

import com.safekey.authenticator.security.VaultIO
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** A peer device as seen during one transfer. */
data class LanPeer(
    val ip: String,
    val mac: String?,
    val deviceId: String,
    val name: String,
    val threatCodes: List<String> = emptyList()
) {
    /** Highest severity among the peer's reported codes, or null when clean. */
    val worstThreat: LanThreatSeverity?
        get() = threatCodes.map { severityOf(it) }.maxByOrNull { it.ordinal }

    /** Severity of a peer-reported code; unknown codes count as low. */
    private fun severityOf(code: String): LanThreatSeverity = when (code) {
        "wifi_open", "wifi_wep", "gateway_changed", "arp_mac_conflict" -> LanThreatSeverity.HIGH
        "wifi_enterprise", "gateway_virtual", "http_proxy", "arp_ip_conflict" ->
            LanThreatSeverity.MEDIUM
        else -> LanThreatSeverity.LOW
    }
}

/**
 * Length-checked framing for the LAN transfer handshake.
 *
 * Every field is length-prefixed and every read is bounded before allocation:
 * a peer, a port scanner or a truncated stream must not be able to make this
 * app allocate an arbitrary amount of memory or half-read a message.
 */
internal object LanWire {

    const val MAX_TEXT_BYTES = 256
    const val MAX_KEY_BYTES = 512
    const val MAX_NONCE_BYTES = 64
    const val MAX_TAG_BYTES = 64

    fun writeBytes(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun readBytes(input: DataInputStream, limit: Int): ByteArray {
        val size = input.readInt()
        if (size < 0 || size > limit) {
            throw IOException("Field size out of range: $size")
        }
        val buffer = ByteArray(size)
        input.readFully(buffer)
        return buffer
    }

    /** Public key, nonce or HMAC tag. */
    fun readKey(input: DataInputStream, limit: Int = MAX_KEY_BYTES): ByteArray =
        readBytes(input, limit)

    fun writeText(out: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writeBytes(out, if (bytes.size > MAX_TEXT_BYTES) bytes.copyOf(MAX_TEXT_BYTES) else bytes)
    }

    fun readText(input: DataInputStream): String =
        String(readBytes(input, MAX_TEXT_BYTES), Charsets.UTF_8)

    /** Sealed vault payload, capped like every other import path. */
    fun readPayload(input: DataInputStream): ByteArray =
        readBytes(input, VaultIO.MAX_PAYLOAD_BYTES)

    /** Threat codes travel as a comma separated list (bounded on both sides). */
    fun codesToText(codes: List<String>): String =
        codes.distinct().sorted().take(32).joinToString(",")

    fun codesFromText(text: String): List<String> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(32)
}
