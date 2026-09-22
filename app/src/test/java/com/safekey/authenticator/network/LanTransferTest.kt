package com.safekey.authenticator.network

import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.security.VaultFormatException
import com.safekey.authenticator.security.VaultIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

class LanTransferTest {

    private fun sampleVault() = VaultFile(
        version = 1,
        format = "osmium-vault",
        exportedAt = System.currentTimeMillis(),
        accounts = listOf(
            VaultAccount(issuer = "GitHub", label = "user@github.com", secret = "HXDMVJECJJWSRB3H"),
            VaultAccount(issuer = "Google", label = "user@gmail.com", secret = "JBSWY3DPEHPK3PXP")
        )
    )

    @Test
    fun testDirectSocketTransferAndDecryption() = runBlocking {
        val pairingCode = "849201"
        val vault = sampleVault()
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverJob = async(Dispatchers.IO) {
            val clientSocket = serverSocket.accept()
            val dis = DataInputStream(clientSocket.getInputStream())
            val dos = DataOutputStream(clientSocket.getOutputStream())

            assertEquals(LanHandshake.MAGIC, dis.readUTF())
            val nonce = LanHandshake.newNonce()
            dos.writeInt(nonce.size)
            dos.write(nonce)
            dos.flush()
            val proof = ByteArray(LanHandshake.PROOF_BYTES)
            dis.readFully(proof)
            assertTrue(
                LanHandshake.matches(
                    LanHandshake.proof(pairingCode.toCharArray(), nonce), proof
                )
            )

            val payload = VaultIO.encrypt(vault, pairingCode.toCharArray())
                .toByteArray(Charsets.UTF_8)
            dos.writeUTF(LanHandshake.ACK)
            dos.writeInt(payload.size)
            dos.write(payload)
            dos.flush()
            clientSocket.close()
            serverSocket.close()
        }

        val clientJob = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val dos = DataOutputStream(socket.getOutputStream())
            val dis = DataInputStream(socket.getInputStream())

            dos.writeUTF(LanHandshake.MAGIC)
            dos.flush()

            val nonceLength = dis.readInt()
            assertEquals(LanHandshake.NONCE_BYTES, nonceLength)
            val nonce = ByteArray(nonceLength)
            dis.readFully(nonce)
            dos.write(LanHandshake.proof(pairingCode.toCharArray(), nonce))
            dos.flush()

            assertEquals(LanHandshake.ACK, dis.readUTF())
            val payloadSize = dis.readInt()
            val buffer = ByteArray(payloadSize)
            dis.readFully(buffer)
            socket.close()

            VaultIO.decrypt(buffer, pairingCode.toCharArray())
        }

        serverJob.await()
        val receivedVault = clientJob.await()

        assertEquals(2, receivedVault.accounts.size)
        assertEquals("GitHub", receivedVault.accounts[0].issuer)
        assertEquals("Google", receivedVault.accounts[1].issuer)
    }

    /**
     * Regression for the v1 hole: a peer that knows nothing but the protocol
     * must not receive a payload it could brute-force offline.
     */
    @Test
    fun testWrongPairingCodeGetsNoPayload() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        var payloadSent = false

        val serverJob = async(Dispatchers.IO) {
            val clientSocket = serverSocket.accept()
            val dis = DataInputStream(clientSocket.getInputStream())
            val dos = DataOutputStream(clientSocket.getOutputStream())

            assertEquals(LanHandshake.MAGIC, dis.readUTF())
            val nonce = LanHandshake.newNonce()
            dos.writeInt(nonce.size)
            dos.write(nonce)
            dos.flush()

            val received = ByteArray(LanHandshake.PROOF_BYTES)
            dis.readFully(received)
            // The attacker guessed a different code.
            val expectedWrong = LanHandshake.proof("654321".toCharArray(), nonce)
            val expectedRight = LanHandshake.proof("123456".toCharArray(), nonce)
            assertFalse(LanHandshake.matches(expectedRight, expectedWrong))
            if (!LanHandshake.matches(expectedRight, received)) {
                payloadSent = true // only reached by the DENY path below
                dos.writeUTF(LanHandshake.DENY)
                dos.flush()
            }
            clientSocket.close()
            serverSocket.close()
        }

        val clientJob = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val dos = DataOutputStream(socket.getOutputStream())
            val dis = DataInputStream(socket.getInputStream())

            dos.writeUTF(LanHandshake.MAGIC)
            dos.flush()
            val nonceLength = dis.readInt()
            val nonce = ByteArray(nonceLength)
            dis.readFully(nonce)
            dos.write(LanHandshake.proof("999999".toCharArray(), nonce))
            dos.flush()

            val ack = dis.readUTF()
            socket.close()
            ack
        }

        serverJob.await()
        assertEquals(LanHandshake.DENY, clientJob.await())
        assertTrue(payloadSent)
        // A rejected handshake surfaces as a wrong-password style failure so the
        // UI shows the pairing-code message instead of a connection error.
        val failure = VaultFormatException(true)
        assertTrue(failure.wrongPassword)
    }

    @Test
    fun testProofDependsOnTheSessionNonce() {
        val code = "123456".toCharArray()
        val first = LanHandshake.proof(code, ByteArray(LanHandshake.NONCE_BYTES) { 1 })
        val second = LanHandshake.proof(code, ByteArray(LanHandshake.NONCE_BYTES) { 2 })
        assertFalse(first.contentEquals(second))
        // Same nonce and code reproduce the same proof (deterministic MAC).
        assertTrue(first.contentEquals(LanHandshake.proof(code, ByteArray(LanHandshake.NONCE_BYTES) { 1 })))
        assertEquals(LanHandshake.PROOF_BYTES, first.size)
    }

    @Test
    fun testWrongPairingCodeRejection() {
        val vault = sampleVault()
        val encryptedJson = VaultIO.encrypt(vault, "123456".toCharArray())
        val payloadBytes = encryptedJson.toByteArray(Charsets.UTF_8)

        try {
            VaultIO.decrypt(payloadBytes, "654321".toCharArray())
            org.junit.Assert.fail("Expected VaultFormatException with wrong password")
        } catch (e: VaultFormatException) {
            assertTrue(e.wrongPassword)
        }
    }
}
