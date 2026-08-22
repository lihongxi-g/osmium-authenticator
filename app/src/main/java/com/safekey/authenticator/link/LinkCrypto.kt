package com.safekey.authenticator.link

import java.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/** Small, platform-only crypto helper for Osmium Link's ephemeral sessions. */
object LinkCrypto {
    private const val AES = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "EC"
    private const val CURVE = "secp256r1"

    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
        initialize(java.security.spec.ECGenParameterSpec(CURVE))
    }.generateKeyPair()

    fun encodePublicKey(key: PublicKey): String = Base64.getEncoder().withoutPadding().encodeToString(key.encoded)

    fun decodePublicKey(encoded: String): PublicKey = KeyFactory.getInstance(KEY_ALGORITHM)
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))

    fun fingerprint(key: PublicKey): String = MessageDigest.getInstance("SHA-256")
        .digest(key.encoded).take(6).joinToString(" ") { "%02X".format(it) }

    fun deriveKey(privateKey: PrivateKey, peer: PublicKey, context: ByteArray): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peer, true)
        val shared = agreement.generateSecret()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(shared, "HmacSHA256"))
        return mac.doFinal(context).copyOf(32)
    }

    fun randomPairingCode(): String = "%06d".format(Random.nextInt(0, 1_000_000))

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): String {
        val iv = Random.nextBytes(12)
        val cipher = Cipher.getInstance(AES)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val body = cipher.doFinal(plaintext)
        return Base64.getEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(iv.size + body.size).put(iv).put(body).array())
    }

    fun decrypt(key: ByteArray, encoded: String, aad: ByteArray = ByteArray(0)): ByteArray {
        val all = Base64.getDecoder().decode(encoded)
        require(all.size > 12) { "Invalid Link ciphertext" }
        val cipher = Cipher.getInstance(AES)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return cipher.doFinal(all.copyOfRange(12, all.size))
    }
}
