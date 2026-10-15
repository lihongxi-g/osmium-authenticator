package com.safekey.authenticator.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** The v4 payload cannot leave the sender until both authenticated peers agree. */
internal object LanTransferConsent {
    const val SOCKET_TIMEOUT_MS = 120_000

    suspend fun send(
        input: DataInputStream,
        output: DataOutputStream,
        senderProof: ByteArray,
        expectedReadyProof: ByteArray,
        approve: suspend () -> Boolean,
        payload: () -> ByteArray
    ): Boolean {
        if (!approve()) return false
        LanWire.writeBytes(output, senderProof)
        if (!MessageDigest.isEqual(expectedReadyProof, LanWire.readKey(input, LanWire.MAX_TAG_BYTES))) return false
        LanWire.writeBytes(output, payload())
        return true
    }

    suspend fun authorize(
        input: DataInputStream,
        output: DataOutputStream,
        expectedSenderProof: ByteArray,
        readyProof: ByteArray,
        approve: suspend () -> Boolean
    ): Boolean {
        if (!MessageDigest.isEqual(expectedSenderProof, LanWire.readKey(input, LanWire.MAX_TAG_BYTES))) return false
        if (!approve()) return false
        LanWire.writeBytes(output, readyProof)
        return true
    }
}