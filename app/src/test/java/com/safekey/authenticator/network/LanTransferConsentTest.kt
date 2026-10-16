package com.safekey.authenticator.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LanTransferConsentTest {
    private val senderProof = ByteArray(32) { 1 }
    private val readyProof = ByteArray(32) { 2 }
    private val payload = ByteArray(64) { 3 }

    private fun input(vararg fields: ByteArray): DataInputStream {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        fields.forEach { LanWire.writeBytes(out, it) }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }

    @Test fun senderWaitsForTheUserBeforeWritingAnything() = runTest {
        val approval = CompletableDeferred<Boolean>()
        val waiting = CompletableDeferred<Unit>()
        val bytes = ByteArrayOutputStream()
        var encoded = false
        val result = async {
            LanTransferConsent.send(input(readyProof), DataOutputStream(bytes), senderProof, readyProof,
                approve = { waiting.complete(Unit); approval.await() },
                payload = { encoded = true; payload })
        }
        waiting.await()
        assertEquals(0, bytes.size())
        assertFalse(encoded)
        approval.complete(false)
        assertFalse(result.await())
        assertEquals(0, bytes.size())
        assertFalse(encoded)
    }

    @Test fun noPayloadIsEncodedWithoutAnAuthenticatedReadyResponse() = runTest {
        val bytes = ByteArrayOutputStream()
        var encoded = false
        assertFalse(LanTransferConsent.send(input(ByteArray(32)), DataOutputStream(bytes), senderProof, readyProof,
            approve = { true }, payload = { encoded = true; payload }))
        assertFalse(encoded)
        val sent = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        assertArrayEquals(senderProof, LanWire.readKey(sent, LanWire.MAX_TAG_BYTES))
        assertEquals(-1, sent.read())
    }

    @Test fun receiverDoesNotAuthorizeUntilTheRiskDialogIsAccepted() = runTest {
        val approval = CompletableDeferred<Boolean>()
        val waiting = CompletableDeferred<Unit>()
        val bytes = ByteArrayOutputStream()
        val result = async {
            LanTransferConsent.authorize(input(senderProof), DataOutputStream(bytes), senderProof, readyProof) {
                waiting.complete(Unit)
                approval.await()
            }
        }
        waiting.await()
        assertEquals(0, bytes.size())
        approval.complete(true)
        assertTrue(result.await())
        assertArrayEquals(readyProof, LanWire.readKey(DataInputStream(ByteArrayInputStream(bytes.toByteArray())), 32))
    }

    @Test fun dismissingTheReceiverDialogDoesNotAuthorizeThePayload() = runTest {
        val bytes = ByteArrayOutputStream()
        assertFalse(LanTransferConsent.authorize(input(senderProof), DataOutputStream(bytes), senderProof, readyProof) { false })
        assertEquals(0, bytes.size())
    }

    @Test fun wrongSenderProofDoesNotShowARiskDialog() = runTest {
        val bytes = ByteArrayOutputStream()
        var prompted = false
        assertFalse(LanTransferConsent.authorize(input(ByteArray(32)), DataOutputStream(bytes), senderProof, readyProof) {
            prompted = true
            true
        })
        assertFalse(prompted)
        assertEquals(0, bytes.size())
    }

    @Test fun bothApprovalsAllowExactlyOnePayload() = runTest {
        val bytes = ByteArrayOutputStream()
        assertTrue(LanTransferConsent.send(input(readyProof), DataOutputStream(bytes), senderProof, readyProof,
            approve = { true }, payload = { payload }))
        val sent = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        assertArrayEquals(senderProof, LanWire.readKey(sent, 32))
        assertArrayEquals(payload, LanWire.readPayload(sent))
        assertEquals(-1, sent.read())
    }
}
