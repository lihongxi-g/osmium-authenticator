package com.safekey.authenticator.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSessionTest {

    private val code = "123456789012"

    private fun transcript(
        senderPub: ByteArray,
        receiverPub: ByteArray,
        clientNonce: ByteArray = ByteArray(LanSession.NONCE_BYTES) { 1 },
        serverNonce: ByteArray = ByteArray(LanSession.NONCE_BYTES) { 2 },
        senderThreats: List<String> = emptyList(),
        receiverThreats: List<String> = emptyList(),
        senderId: String = "sender-device",
        receiverId: String = "receiver-device"
    ) = LanSession.transcript(
        senderPub, receiverPub, clientNonce, serverNonce,
        senderThreats, receiverThreats, senderId, receiverId
    )

    @Test
    fun `both sides derive the same session key`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val t = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val senderKey = LanSession.sessionKey(
            sender.privateKey, receiver.publicKeyBytes, code, t
        )
        val receiverKey = LanSession.sessionKey(
            receiver.privateKey, sender.publicKeyBytes, code, t
        )
        assertArrayEquals(senderKey.encoded, receiverKey.encoded)
    }

    @Test
    fun `a wrong pairing code never produces matching tags`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val t = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val senderKey = LanSession.sessionKey(sender.privateKey, receiver.publicKeyBytes, code, t)
        val attackerKey = LanSession.sessionKey(
            receiver.privateKey, sender.publicKeyBytes, "999999999999", t
        )
        val expected = LanSession.senderTag(senderKey, t)
        val received = LanSession.senderTag(attackerKey, t)
        assertFalse(LanSession.matches(expected, received))
        assertEquals(expected.size, received.size)
    }

    @Test
    fun `a substituted public key breaks the handshake`() {
        // Man-in-the-middle: the receiver answers with the attacker's key.
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val attacker = LanSession.newEphemeral()
        val realTranscript = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val senderKey = LanSession.sessionKey(
            sender.privateKey, attacker.publicKeyBytes, code, realTranscript
        )
        val receiverKey = LanSession.sessionKey(
            receiver.privateKey, sender.publicKeyBytes, code, realTranscript
        )
        assertFalse(
            LanSession.matches(
                LanSession.receiverTag(senderKey, realTranscript),
                LanSession.receiverTag(receiverKey, realTranscript)
            )
        )
    }

    @Test
    fun `the transcript separates the two directions`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val t = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val key = LanSession.sessionKey(sender.privateKey, receiver.publicKeyBytes, code, t)
        assertFalse(
            LanSession.matches(
                LanSession.senderTag(key, t),
                LanSession.receiverTag(key, t)
            )
        )
        assertEquals(32, LanSession.senderTag(key, t).size)
    }

    @Test
    fun `a different nonce produces a different session key`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val first = transcript(
            sender.publicKeyBytes, receiver.publicKeyBytes, serverNonce = ByteArray(32) { 2 }
        )
        val second = transcript(
            sender.publicKeyBytes, receiver.publicKeyBytes, serverNonce = ByteArray(32) { 3 }
        )
        val a = LanSession.sessionKey(sender.privateKey, receiver.publicKeyBytes, code, first)
        val b = LanSession.sessionKey(sender.privateKey, receiver.publicKeyBytes, code, second)
        assertNotEquals(a.encoded.toList(), b.encoded.toList())
    }

    @Test
    fun `threat codes and identities are bound into the transcript`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val plain = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val withThreat = transcript(
            sender.publicKeyBytes, receiver.publicKeyBytes, receiverThreats = listOf("wifi_open")
        )
        val withOtherId = transcript(
            sender.publicKeyBytes, receiver.publicKeyBytes, receiverId = "other-device"
        )
        assertFalse(plain.contentEquals(withThreat))
        assertFalse(plain.contentEquals(withOtherId))
    }

    @Test
    fun `threat code order and duplicates do not change the transcript`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val first = transcript(
            sender.publicKeyBytes, receiver.publicKeyBytes,
            receiverThreats = listOf("vpn_active", "wifi_open", "vpn_active")
        )
        val second = transcript(
            sender.publicKeyBytes, receiver.publicKeyBytes,
            receiverThreats = listOf("wifi_open", "vpn_active")
        )
        assertArrayEquals(first, second)
    }

    @Test
    fun `payload sealed with the session key round-trips`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val t = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val senderKey = LanSession.sessionKey(sender.privateKey, receiver.publicKeyBytes, code, t)
        val receiverKey = LanSession.sessionKey(receiver.privateKey, sender.publicKeyBytes, code, t)
        val vault = "{\"format\":\"osmium-vault\",\"accounts\":[]}".toByteArray(Charsets.UTF_8)

        val sealed = LanSession.seal(senderKey, t, vault)
        val opened = LanSession.open(receiverKey, t, sealed)
        assertArrayEquals(vault, opened)
        assertTrue(sealed.size > vault.size + LanSession.IV_BYTES)
    }

    @Test
    fun `tampered payload opens to null`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val t = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val key = LanSession.sessionKey(sender.privateKey, receiver.publicKeyBytes, code, t)
        val sealed = LanSession.seal(key, t, "secret".toByteArray(Charsets.UTF_8))
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 0x01).toByte()
        assertNull(LanSession.open(key, t, sealed))
    }

    @Test
    fun `payload bound to the transcript fails under a different transcript`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val good = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val other = transcript(
            sender.publicKeyBytes, receiver.publicKeyBytes, senderId = "someone-else"
        )
        val key = LanSession.sessionKey(sender.privateKey, receiver.publicKeyBytes, code, good)
        val sealed = LanSession.seal(key, good, "secret".toByteArray(Charsets.UTF_8))
        assertNull(LanSession.open(key, other, sealed))
    }

    @Test
    fun `a truncated payload opens to null`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val t = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val key = LanSession.sessionKey(sender.privateKey, receiver.publicKeyBytes, code, t)
        val sealed = LanSession.seal(key, t, "secret".toByteArray(Charsets.UTF_8))
        assertNull(LanSession.open(key, t, sealed.copyOfRange(0, LanSession.IV_BYTES)))
    }

    @Test
    fun `sealing twice never reuses the iv`() {
        val sender = LanSession.newEphemeral()
        val receiver = LanSession.newEphemeral()
        val t = transcript(sender.publicKeyBytes, receiver.publicKeyBytes)
        val key = LanSession.sessionKey(sender.privateKey, receiver.publicKeyBytes, code, t)
        val first = LanSession.seal(key, t, "same".toByteArray(Charsets.UTF_8))
        val second = LanSession.seal(key, t, "same".toByteArray(Charsets.UTF_8))
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `pairing codes are twelve digits`() {
        repeat(50) {
            val generated = LanSession.newPairingCode()
            assertEquals(LanSession.PAIRING_CODE_DIGITS, generated.length)
            assertTrue(LanSession.isWellFormedCode(generated))
            assertTrue(generated.all { it.isDigit() })
        }
    }

    @Test
    fun `user input is normalized and grouped for display`() {
        assertEquals("123456789012", LanSession.normalizeCode("1234 5678-9012"))
        assertEquals("123456789012", LanSession.normalizeCode(" 1234 5678 9012 "))
        assertEquals("1234 5678 9012", LanSession.groupedCode("123456789012"))
        assertFalse(LanSession.isWellFormedCode("12345678901"))
        assertFalse(LanSession.isWellFormedCode("12345678901a"))
    }

    @Test
    fun `ephemeral keys are unique and correctly encoded`() {
        val first = LanSession.newEphemeral()
        val second = LanSession.newEphemeral()
        assertFalse(first.publicKeyBytes.contentEquals(second.publicKeyBytes))
        assertEquals("X.509", first.publicKeyBytes.let { java.security.KeyFactory.getInstance("EC")
            .generatePublic(java.security.spec.X509EncodedKeySpec(it)).format })
    }

    @Test
    fun `matches requires the same length and content`() {
        val value = ByteArray(32) { 7 }
        assertTrue(LanSession.matches(value, value.copyOf()))
        assertFalse(LanSession.matches(value, value.copyOf(16)))
        val changed = value.copyOf().also { it[0] = 8 }
        assertFalse(LanSession.matches(value, changed))
    }

    @Test
    fun `deny markers are distinct`() {
        assertNotEquals(LanSession.DENY, LanSession.DENY_BLOCKED)
        assertNotEquals(LanSession.ACK, LanSession.DENY_PEER_BLOCKED)
    }
}
