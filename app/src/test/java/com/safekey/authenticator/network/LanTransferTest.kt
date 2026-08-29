package com.safekey.authenticator.network

import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.security.VaultFormatException
import com.safekey.authenticator.security.VaultIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        val encryptedJson = VaultIO.encrypt(vault, pairingCode.toCharArray())
        val payloadBytes = encryptedJson.toByteArray(Charsets.UTF_8)

        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverJob = async(Dispatchers.IO) {
            val clientSocket = serverSocket.accept()
            val dis = DataInputStream(clientSocket.getInputStream())
            val dos = DataOutputStream(clientSocket.getOutputStream())

            val magic = dis.readUTF()
            assertEquals("OSMIUM_TRANSFER_V1", magic)

            dos.writeUTF("OSMIUM_TRANSFER_ACK")
            dos.writeInt(payloadBytes.size)
            dos.write(payloadBytes)
            dos.flush()
            clientSocket.close()
            serverSocket.close()
        }

        val clientJob = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val dos = DataOutputStream(socket.getOutputStream())
            val dis = DataInputStream(socket.getInputStream())

            dos.writeUTF("OSMIUM_TRANSFER_V1")
            dos.flush()

            val ack = dis.readUTF()
            assertEquals("OSMIUM_TRANSFER_ACK", ack)

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

    @Test
    fun testWrongPairingCodeRejection() = runBlocking {
        val pairingCode = "123456"
        val wrongCode = "654321"
        val vault = sampleVault()
        val encryptedJson = VaultIO.encrypt(vault, pairingCode.toCharArray())
        val payloadBytes = encryptedJson.toByteArray(Charsets.UTF_8)

        try {
            VaultIO.decrypt(payloadBytes, wrongCode.toCharArray())
            org.junit.Assert.fail("Expected VaultFormatException with wrong password")
        } catch (e: VaultFormatException) {
            assertTrue(e.wrongPassword)
        }
    }
}
