package com.safekey.authenticator.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * Admission control for the receiving socket.
 *
 * Two independent bounds:
 *  - at most [maxConcurrent] peers are handled at the same time, so a burst of
 *    connections cannot pin every handler thread and file descriptor the app
 *    owns,
 *  - one address may start an exchange at most once per [perAddressCooldownMs],
 *    so a peer that keeps reconnecting cannot keep the handshake busy.
 *
 * A rejection here is deliberately *not* a pairing-code failure: only a wrong
 * code may spend the session's attempt budget, otherwise any stranger could end
 * a legitimate session by opening a handful of connections.
 *
 * Android-free (the clock is injected) so the rules are unit tested on the JVM.
 */
internal class LanConnectionLimiter(
    private val maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    private val perAddressCooldownMs: Long = DEFAULT_PER_ADDRESS_COOLDOWN_MS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        /** Handlers allowed at once; a phone never needs more for a two-device transfer. */
        const val DEFAULT_MAX_CONCURRENT = 4

        /** One address may start one exchange per this window. */
        const val DEFAULT_PER_ADDRESS_COOLDOWN_MS = 5_000L

        /** Addresses remembered before stale entries are pruned. */
        private const val MAX_TRACKED_ADDRESSES = 256
    }

    private val slots = Semaphore(maxConcurrent)
    private val lastAttempt = ConcurrentHashMap<String, Long>()

    /**
     * True when [address] may be served now. The caller must call [release]
     * once the exchange is over — every admitted connection owns one slot.
     *
     * The cooldown starts only when a connection is actually accepted: a peer
     * turned away because the pool was full may try again as soon as a slot
     * frees, otherwise a busy moment would lock out the legitimate sender.
     */
    fun admit(address: String): Boolean {
        val now = clock()
        val previous = lastAttempt[address]
        if (previous != null && now - previous < perAddressCooldownMs) return false
        if (!slots.tryAcquire()) return false
        val replaced = lastAttempt.put(address, now)
        if (replaced != null && now - replaced < perAddressCooldownMs) {
            // Another thread admitted this address first; give the slot back.
            slots.release()
            return false
        }
        prune(now)
        return true
    }

    /** Frees the slot taken by one admitted connection. */
    fun release() {
        slots.release()
    }

    /** Peers currently being served; a seam for tests. */
    fun activeCount(): Int = maxConcurrent - slots.availablePermits()

    /** Drops addresses whose cooldown expired, so the map cannot grow forever. */
    private fun prune(now: Long) {
        if (lastAttempt.size <= MAX_TRACKED_ADDRESSES) return
        lastAttempt.entries.removeIf { entry -> now - entry.value >= perAddressCooldownMs }
    }
}
