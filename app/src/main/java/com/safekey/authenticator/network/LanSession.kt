package com.safekey.authenticator.network

import java.security.KeyAgreement
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * LAN transfer session — protocol v3.
 *
 * Threat model. The transfer runs over a shared local network the app does not
 * control: anyone on that network can connect, capture every byte and run a
 * man-in-the-middle between the two devices. Earlier protocols handed out data
 * protected only by a short shared code, which an attacker could take home and
 * crack offline.
 *
 * Design (all primitives are plain JCE so this runs on Android 8+ and on the
 * JVM in unit tests):
 *
 *  1. Both sides generate an ephemeral P-256 key pair for the session.
 *  2. Session key = PBKDF2-HMAC-SHA256(password = pairing code (12 digits),
 *     salt = SHA-256(ECDH secret || transcript), 120k rounds, 256 bit).
 *     - The ECDH secret makes the key unique per session and binds it to the
 *       key pairs actually exchanged, so a man-in-the-middle that substitutes
 *       its own keys derives a different key and cannot produce valid tags.
 *     - A captured transcript can only be attacked by guessing the code; with
 *       10^12 codes and 120k PBKDF2 rounds per guess that is far out of reach.
 *  3. Authentication is mutual and transcript-bound: the receiver sends
 *     HMAC(sessionKey, "receiver" || transcript), the sender answers with
 *     HMAC(sessionKey, "sender" || transcript). Waiting for the receiver's tag
 *     first means a wrong code is rejected before the sender releases anything.
 *     Failed attempts are counted by the receiver and end the session.
 *  4. The vault is sealed with AES-256-GCM under a key derived from the session
 *     key; the transcript is the additional authenticated data, so neither the
 *     payload nor the handshake can be replayed or reordered.
 *  5. Threat codes and device identities travel inside the transcript, so an
 *     attacker cannot forge or strip them (they are covered by both tags).
 *
 * Pure JCE, no Android APIs — unit-tested on the JVM (LanSessionTest).
 */
internal object LanSession {

    /** Protocol marker sent by the sender first. */
    const val MAGIC = "OSMIUM_TRANSFER_V3"

    /** Receiver accepted the payload. */
    const val ACK = "OSMIUM_TRANSFER_ACK3"

    /** Receiver refused (bad proof, bad payload, wrong state). */
    const val DENY = "OSMIUM_TRANSFER_DENY3"

    /** Receiver refused because the sender is on its 24h block list. */
    const val DENY_BLOCKED = "OSMIUM_TRANSFER_DENY_BLOCKED3"

    /** Sender refused because the receiver is on its 24h block list. */
    const val DENY_PEER_BLOCKED = "OSMIUM_TRANSFER_DENY_PEER_BLOCKED3"

    const val NONCE_BYTES = 32
    const val IV_BYTES = 12
    const val TAG_BITS = 128

    /** Pairing code length in digits (10^12 combinations). */
    const val PAIRING_CODE_DIGITS = 12

    /** Wrong-code attempts allowed per sharing session. */
    const val MAX_FAILED_ATTEMPTS = 10

    /** Largest handshake text (identity / code lists) accepted from a peer. */
    const val MAX_HANDSHAKE_TEXT = 256

    private const val CURVE = "secp256r1"
    private const val KDF_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val SENDER_LABEL = "osmium-lan-v3/sender"
    private const val RECEIVER_LABEL = "osmium-lan-v3/receiver"
    private const val PAYLOAD_INFO = "osmium-lan-v3/payload"

    /** One ephemeral key pair; the private key never leaves the device. */
    class Ephemeral internal constructor(
        val privateKey: PrivateKey,
        val publicKeyBytes: ByteArray
    )

    fun newEphemeral(): Ephemeral {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(CURVE), SecureRandom())
        val pair = generator.generateKeyPair()
        return Ephemeral(pair.private, pair.public.encoded)
    }

    fun newNonce(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(NONCE_BYTES).also { random.nextBytes(it) }

    /** Twelve random digits — 10^12 combinations, verified against PBKDF2. */
    fun newPairingCode(random: SecureRandom = SecureRandom()): String {
        val digits = StringBuilder(PAIRING_CODE_DIGITS)
        while (digits.length < PAIRING_CODE_DIGITS) {
            // nextInt(10) is unbiased here (10 divides no power of two, so the
            // range is drawn per digit instead of modulo on a wider number).
            digits.append(random.nextInt(10))
        }
        return digits.toString()
    }

    /** Strips everything a user might type between the digits. */
    fun normalizeCode(input: String): String = input.filter { it.isDigit() }

    fun isWellFormedCode(code: String): Boolean =
        code.length == PAIRING_CODE_DIGITS && code.all { it.isDigit() }

    /** Display form: "1234 5678 9012". */
    fun groupedCode(code: String): String =
        code.chunked(4).joinToString(" ")

    /**
     * Canonical handshake transcript. Both sides must build it from the same
     * canonical inputs, so the code lists are deduplicated and sorted here —
     * otherwise a peer could reorder them and still produce matching tags.
     */
    fun transcript(
        senderPublic: ByteArray,
        receiverPublic: ByteArray,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        senderThreatCodes: List<String>,
        receiverThreatCodes: List<String>,
        senderDeviceId: String,
        receiverDeviceId: String
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(MAGIC.toByteArray(Charsets.UTF_8))
        out.write(senderPublic)
        out.write(receiverPublic)
        out.write(clientNonce)
        out.write(serverNonce)
        out.write(canonical(senderThreatCodes).toByteArray(Charsets.UTF_8))
        out.write(canonical(receiverThreatCodes).toByteArray(Charsets.UTF_8))
        out.write(senderDeviceId.toByteArray(Charsets.UTF_8))
        out.write(receiverDeviceId.toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    private fun canonical(codes: List<String>): String =
        codes.filter { it.isNotBlank() }.distinct().sorted().joinToString(",")

    /**
     * Session key for one handshake: ECDH with the peer's ephemeral key, then
     * PBKDF2 over the pairing code with the transcript as salt.
     */
    fun sessionKey(
        privateKey: PrivateKey,
        peerPublicKeyBytes: ByteArray,
        pairingCode: String,
        transcript: ByteArray
    ): SecretKeySpec {
        val peerPublic = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublic, true)
        val shared = agreement.generateSecret()
        val salt = MessageDigest.getInstance("SHA-256").digest(shared + transcript)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pairingCode.toCharArray(), salt, KDF_ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun senderTag(sessionKey: SecretKeySpec, transcript: ByteArray): ByteArray =
        hmac(sessionKey, SENDER_LABEL, transcript)

    fun receiverTag(sessionKey: SecretKeySpec, transcript: ByteArray): ByteArray =
        hmac(sessionKey, RECEIVER_LABEL, transcript)

    /** Constant-time comparison. */
    fun matches(expected: ByteArray, actual: ByteArray): Boolean =
        MessageDigest.isEqual(expected, actual)

    /** AES-256-GCM over [plaintext]; output is iv || ciphertext || tag. */
    fun seal(sessionKey: SecretKeySpec, transcript: ByteArray, plaintext: ByteArray): ByteArray {
        val key = payloadKey(sessionKey)
        val iv = newNonce(SecureRandom()).copyOf(IV_BYTES)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(transcript)
        return iv + cipher.doFinal(plaintext)
    }

    /** Opens a sealed payload; null on any authentication failure. */
    fun open(sessionKey: SecretKeySpec, transcript: ByteArray, sealed: ByteArray): ByteArray? {
        if (sealed.size <= IV_BYTES) return null
        return try {
            val key = payloadKey(sessionKey)
            val iv = sealed.copyOfRange(0, IV_BYTES)
            val body = sealed.copyOfRange(IV_BYTES, sealed.size)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(transcript)
            cipher.doFinal(body)
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------- internals

    private fun hmac(key: SecretKeySpec, label: String, transcript: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key.encoded, HMAC_ALGORITHM))
        mac.update(label.toByteArray(Charsets.UTF_8))
        mac.update(transcript)
        return mac.doFinal()
    }

    /**
     * HKDF-SHA256 (RFC 5869) over the session key, so the payload never reuses
     * the authentication key directly.
     */
    private fun payloadKey(sessionKey: SecretKeySpec): SecretKeySpec {
        val prk = hkdfExtract(sessionKey.encoded)
        val okm = hkdfExpand(prk, PAYLOAD_INFO.toByteArray(Charsets.UTF_8), KEY_BITS / 8)
        return SecretKeySpec(okm, "AES")
    }

    private fun hkdfExtract(ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        // Empty salt is defined by RFC 5869 as a zero-filled block of hash size.
        mac.init(SecretKeySpec(ByteArray(32), HMAC_ALGORITHM))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))
        mac.update(info)
        mac.update(1)
        return mac.doFinal().copyOf(length)
    }
}
