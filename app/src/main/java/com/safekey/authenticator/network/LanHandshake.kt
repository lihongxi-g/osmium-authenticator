package com.safekey.authenticator.network

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * LAN transfer handshake (protocol v2).
 *
 * v1 handed the encrypted vault to any peer that sent the magic string, which
 * turned the 6-digit pairing code into an *offline* secret: an attacker on the
 * same network could capture the payload and brute-force the 10^6 candidates at
 * leisure. v2 keeps the code as a shared secret but never releases a byte of the
 * payload before the peer proves it knows the code for this session:
 *
 *   client -> server : MAGIC (protocol version)
 *   server -> client : 32-byte random nonce
 *   client -> server : HMAC-SHA256(key = PBKDF2(code, nonce), msg = nonce || MAGIC)
 *   server -> client : ACK + payload, encrypted with the pairing code
 *
 * A wrong proof ends the connection, so guessing is now an *online* attack
 * bounded by the server's attempt budget (see LanTransferServer).
 *
 * Pure JCE, no Android APIs — unit-tested on the JVM (LanHandshakeTest).
 */
internal object LanHandshake {

    /** Protocol version marker; peers on the old protocol are rejected. */
    const val MAGIC = "OSMIUM_TRANSFER_V2"
    const val ACK = "OSMIUM_TRANSFER_ACK"

    const val NONCE_BYTES = 32

    /** HMAC-SHA256 output length. */
    const val PROOF_BYTES = 32

    /** Server reply when the proof does not match — no payload is sent. */
    const val DENY = "OSMIUM_TRANSFER_DENY"

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val MAC_ALGORITHM = "HmacSHA256"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"

    fun newNonce(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(NONCE_BYTES).also { random.nextBytes(it) }

    /** Proof that the caller knows [pairingCode] for the session [nonce]. */
    fun proof(pairingCode: CharArray, nonce: ByteArray): ByteArray {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(SecretKeySpec(deriveKey(pairingCode, nonce), MAC_ALGORITHM))
        return mac.doFinal(proofMessage(nonce))
    }

    /** Constant-time comparison of an expected and a received proof. */
    fun matches(expected: ByteArray, received: ByteArray): Boolean =
        MessageDigest.isEqual(expected, received)

    /** Sessions must agree byte-for-byte, including the version marker. */
    private fun proofMessage(nonce: ByteArray): ByteArray =
        nonce + MAGIC.toByteArray(Charsets.UTF_8)

    private fun deriveKey(pairingCode: CharArray, nonce: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(pairingCode, nonce, ITERATIONS, KEY_BITS)
        return factory.generateSecret(spec).encoded
    }
}
