package com.safekey.authenticator.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The receiver's admission rules: a stranger may waste one slot for one attempt,
 * never the session, and never the whole handler pool.
 */
class LanConnectionLimiterTest {

    private var now = 1_000L
    private fun limiter(
        maxConcurrent: Int = 4,
        cooldownMs: Long = 5_000L
    ) = LanConnectionLimiter(maxConcurrent, cooldownMs) { now }

    @Test
    fun firstAttemptFromAnAddressIsAdmitted() {
        val limiter = limiter()
        assertTrue(limiter.admit("192.168.1.20"))
        assertEquals(1, limiter.activeCount())
    }

    @Test
    fun reconnectWithinTheCooldownIsRefused() {
        val limiter = limiter()
        assertTrue(limiter.admit("192.168.1.20"))
        limiter.release()
        now += 4_999
        assertFalse("a peer may not retry inside the cooldown", limiter.admit("192.168.1.20"))
        now += 1
        assertTrue("after the cooldown the same address is served again", limiter.admit("192.168.1.20"))
    }

    @Test
    fun differentAddressesDoNotShareTheCooldown() {
        val limiter = limiter()
        assertTrue(limiter.admit("192.168.1.20"))
        assertTrue(limiter.admit("192.168.1.21"))
        assertEquals(2, limiter.activeCount())
    }

    @Test
    fun concurrencyIsCappedAndReleaseFreesASlot() {
        val limiter = limiter(maxConcurrent = 2)
        assertTrue(limiter.admit("10.0.0.1"))
        assertTrue(limiter.admit("10.0.0.2"))
        assertFalse("a third simultaneous handler is refused", limiter.admit("10.0.0.3"))
        assertEquals(2, limiter.activeCount())
        limiter.release()
        assertEquals(1, limiter.activeCount())
        assertTrue(limiter.admit("10.0.0.3"))
    }

    @Test
    fun aRefusedAttemptDoesNotConsumeARealSlot() {
        val limiter = limiter(maxConcurrent = 1)
        assertTrue(limiter.admit("10.0.0.1"))
        assertFalse(limiter.admit("10.0.0.2"))
        limiter.release()
        assertTrue("the pool is back to full size", limiter.admit("10.0.0.2"))
    }

    @Test
    fun aFullyDrainedPoolRecoversAfterEachRelease() {
        val limiter = limiter(maxConcurrent = 2)
        repeat(4) { round ->
            assertTrue("round $round admits the first peer", limiter.admit("10.0.1.$round"))
            assertTrue("round $round admits the second peer", limiter.admit("10.0.2.$round"))
            limiter.release()
            limiter.release()
            assertEquals(0, limiter.activeCount())
        }
    }
}
