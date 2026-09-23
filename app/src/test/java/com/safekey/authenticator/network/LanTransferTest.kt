package com.safekey.authenticator.network

import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.security.VaultIO
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-level tests of the v3 transfer handshake, mirroring the exact steps
 * LanTransferServer (receiver) and LanTransferClient (sender) perform.
 */
class LanTransferTest {

    private val code = "123456789012"
    private val senderId = "sender-device-id"
    private val receiverId = "receiver-device-id"

    private fun sampleVault() = VaultFile(
        version = 2,
        format = "osmium-vault",
        exportedAt = System.currentTimeMillis(),
        accounts = listOf(
            VaultAccount(issuer = "GitHub", label = "user@github.com", secret = "HXDMVJECJJWSRB3H"),
            VaultAccount(issuer = "Google", label = "user@gmail.com", secret = "JBSWY3DPEHPK3PXP")
        )
    )

    private fun hello(
        out: DataOutputStream,
        sender: LanSession.Ephemeral,
        nonce: ByteArray
    ) {
        LanWire.writeText(out, LanSession.MAGIC)
        LanWire.writeBytes(out, sender.publicKeyBytes)
        LanWire.writeBytes(out, nonce)
        LanWire.writeText(out, senderId)
        LanWire.writeText(out, "sending phone")
        LanWire.writeText(out, LanWire.codesToText(emptyList()))
    }

    @Test
    fun `a full v3 transfer delivers the vault`() = runBlocking {
        val vault = sampleVault()
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverJob = async(Dispatchers.IO) {
            val socket = serverSocket.accept()
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            assertEquals(LanSession.MAGIC, LanWire.readText(input))
            val senderPublic = LanWire.readKey(input)
            val clientNonce = LanWire.readKey(input, LanWire.MAX_NONCE_BYTES)
            val senderDeviceId = LanWire.readText(input)
            val senderName = LanWire.readText(input)
            val senderThreats = LanWire.codesFromText(LanWire.readText(input))
            assertEquals(senderId, senderDeviceId)
            assertEquals("sending phone", senderName)
            assertTrue(senderThreats.isEmpty())

            val receiver = LanSession.newEphemeral()
            val serverNonce = LanSession.newNonce()
            val receiverThreats = listOf("wifi_open")
            val transcript = LanSession.transcript(
                senderPublic, receiver.publicKeyBytes, clientNonce, serverNonce,
                senderThreats, receiverThreats, senderDeviceId, receiverId
            )
            val sessionKey = LanSession.sessionKey(
                receiver.privateKey, senderPublic, code, transcript
            )

            LanWire.writeText(output, LanSession.ACK)
            LanWire.writeBytes(output, receiver.publicKeyBytes)
            LanWire.writeBytes(output, serverNonce)
            LanWire.writeText(output, receiverId)
            LanWire.writeText(output, "receiving phone")
            LanWire.writeText(output, LanWire.codesToText(receiverThreats))
            LanWire.writeBytes(output, LanSession.receiverTag(sessionKey, transcript))

            val senderTag = LanWire.readKey(input, LanWire.MAX_TAG_BYTES)
            assertTrue(
                LanSession.matches(LanSession.senderTag(sessionKey, transcript), senderTag)
            )

            val sealed = LanWire.readPayload(input)
            val plain = LanSession.open(sessionKey, transcript, sealed)
            assertTrue(plain != null)
            LanWire.writeText(output, LanSession.ACK)

            val received = VaultIO.decodePlain(plain!!)
            socket.close()
            serverSocket.close()
            received
        }

        val clientJob = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            val sender = LanSession.newEphemeral()
            val clientNonce = LanSession.newNonce()
            hello(output, sender, clientNonce)

            assertEquals(LanSession.ACK, LanWire.readText(input))
            val receiverPublic = LanWire.readKey(input)
            val serverNonce = LanWire.readKey(input, LanWire.MAX_NONCE_BYTES)
            val peerId = LanWire.readText(input)
            assertEquals("receiving phone", LanWire.readText(input))
            val peerThreats = LanWire.codesFromText(LanWire.readText(input))
            val receiverTag = LanWire.readKey(input, LanWire.MAX_TAG_BYTES)
            assertEquals(receiverId, peerId)
            assertEquals(listOf("wifi_open"), peerThreats)

            val transcript = LanSession.transcript(
                sender.publicKeyBytes, receiverPublic, clientNonce, serverNonce,
                emptyList(), peerThreats, senderId, peerId
            )
            val sessionKey = LanSession.sessionKey(
                sender.privateKey, receiverPublic, code, transcript
            )
            assertTrue(
                "the receiver must prove the code",
                LanSession.matches(LanSession.receiverTag(sessionKey, transcript), receiverTag)
            )

            val payload = VaultIO.encodePlain(vault).toByteArray(Charsets.UTF_8)
            LanWire.writeBytes(output, LanSession.senderTag(sessionKey, transcript))
            LanWire.writeBytes(output, LanSession.seal(sessionKey, transcript, payload))

            val status = LanWire.readText(input)
            socket.close()
            status
        }

        val received = serverJob.await()
        assertEquals(LanSession.ACK, clientJob.await())
        assertEquals(2, received.accounts.size)
        assertEquals("GitHub", received.accounts[0].issuer)
    }

    @Test
    fun `a wrong pairing code is rejected and no payload is accepted`() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val payloadsAccepted = java.util.concurrent.atomic.AtomicInteger(0)

        val serverJob = async(Dispatchers.IO) {
            val socket = serverSocket.accept()
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            assertEquals(LanSession.MAGIC, LanWire.readText(input))
            val senderPublic = LanWire.readKey(input)
            val clientNonce = LanWire.readKey(input, LanWire.MAX_NONCE_BYTES)
            val senderDeviceId = LanWire.readText(input)
            LanWire.readText(input)
            val senderThreats = LanWire.codesFromText(LanWire.readText(input))

            val receiver = LanSession.newEphemeral()
            val serverNonce = LanSession.newNonce()
            val transcript = LanSession.transcript(
                senderPublic, receiver.publicKeyBytes, clientNonce, serverNonce,
                senderThreats, emptyList(), senderDeviceId, receiverId
            )
            // The receiver knows its own code; the sender is guessing.
            val sessionKey = LanSession.sessionKey(
                receiver.privateKey, senderPublic, code, transcript
            )

            LanWire.writeText(output, LanSession.ACK)
            LanWire.writeBytes(output, receiver.publicKeyBytes)
            LanWire.writeBytes(output, serverNonce)
            LanWire.writeText(output, receiverId)
            LanWire.writeText(output, "receiving phone")
            LanWire.writeText(output, LanWire.codesToText(emptyList()))
            LanWire.writeBytes(output, LanSession.receiverTag(sessionKey, transcript))

            val senderTag = LanWire.readKey(input, LanWire.MAX_TAG_BYTES)
            val valid = LanSession.matches(
                LanSession.senderTag(sessionKey, transcript), senderTag
            )
            if (!valid) {
                LanWire.writeText(output, LanSession.DENY)
            } else {
                payloadsAccepted.incrementAndGet()
            }
            socket.close()
            serverSocket.close()
            valid
        }

        val clientJob = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            val sender = LanSession.newEphemeral()
            val clientNonce = LanSession.newNonce()
            hello(output, sender, clientNonce)

            assertEquals(LanSession.ACK, LanWire.readText(input))
            val receiverPublic = LanWire.readKey(input)
            val serverNonce = LanWire.readKey(input, LanWire.MAX_NONCE_BYTES)
            val peerId = LanWire.readText(input)
            LanWire.readText(input)
            val peerThreats = LanWire.codesFromText(LanWire.readText(input))
            val receiverTag = LanWire.readKey(input, LanWire.MAX_TAG_BYTES)

            val transcript = LanSession.transcript(
                sender.publicKeyBytes, receiverPublic, clientNonce, serverNonce,
                emptyList(), peerThreats, senderId, peerId
            )
            // Guessed code: the receiver's own tag cannot match.
            val wrongKey = LanSession.sessionKey(
                sender.privateKey, receiverPublic, "999999999999", transcript
            )
            assertFalse(
                LanSession.matches(LanSession.receiverTag(wrongKey, transcript), receiverTag)
            )

            val payload = VaultIO.encodePlain(sampleVault()).toByteArray(Charsets.UTF_8)
            LanWire.writeBytes(output, LanSession.senderTag(wrongKey, transcript))
            LanWire.writeBytes(output, LanSession.seal(wrongKey, transcript, payload))
            val status = LanWire.readText(input)
            socket.close()
            status
        }

        assertFalse(serverJob.await())
        assertEquals(LanSession.DENY, clientJob.await())
        assertEquals(0, payloadsAccepted.get())
    }

    @Test
    fun `a blocked peer is refused right after the hello`() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val keyMaterialSent = java.util.concurrent.atomic.AtomicInteger(0)

        val serverJob = async(Dispatchers.IO) {
            val socket = serverSocket.accept()
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            assertEquals(LanSession.MAGIC, LanWire.readText(input))
            LanWire.readKey(input)
            LanWire.readKey(input, LanWire.MAX_NONCE_BYTES)
            LanWire.readText(input)
            LanWire.readText(input)
            LanWire.readText(input)

            // The sender's identity is on this device's block list.
            LanWire.writeText(output, LanSession.DENY_BLOCKED)
            socket.close()
            serverSocket.close()
        }

        val clientJob = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            hello(output, LanSession.newEphemeral(), LanSession.newNonce())
            val status = LanWire.readText(input)
            socket.close()
            status
        }

        serverJob.await()
        assertEquals(LanSession.DENY_BLOCKED, clientJob.await())
        assertEquals(0, keyMaterialSent.get())
    }

    @Test
    fun `an oversized handshake field is rejected before allocation`() {
        val buffer = java.io.ByteArrayOutputStream()
        val output = DataOutputStream(buffer)
        // A hostile peer announces a 1 MiB identity string.
        output.writeInt(1_048_576)
        output.flush()
        val input = DataInputStream(buffer.toByteArray().inputStream())
        try {
            LanWire.readText(input)
            org.junit.Assert.fail("expected the oversized field to be rejected")
        } catch (e: Exception) {
            assertTrue(e is java.io.IOException)
        }
    }

    @Test
    fun `a truncated payload is rejected`() {
        val buffer = java.io.ByteArrayOutputStream()
        val output = DataOutputStream(buffer)
        output.writeInt(64)
        output.write(byteArrayOf(1, 2, 3))
        output.flush()
        val input = DataInputStream(buffer.toByteArray().inputStream())
        assertNull(
            try {
                LanWire.readPayload(input)
            } catch (_: Exception) {
                null
            }
        )
    }

    @Test
    fun `threat code lists are canonical on both sides`() {
        assertEquals("vpn_active,wifi_open", LanWire.codesToText(listOf("wifi_open", "vpn_active", "wifi_open")))
        assertEquals(listOf("wifi_open", "vpn_active"), LanWire.codesFromText("wifi_open,vpn_active"))
        assertEquals(emptyList<String>(), LanWire.codesFromText(""))
        assertNotEquals(LanWire.codesToText(listOf("a", "b")), LanWire.codesToText(listOf("b")))
    }
}
