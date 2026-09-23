package com.safekey.authenticator.integrity

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * K0 early snapshot — captured once at process start (SafeKeyApp.onCreate),
 * before a hiding framework would have reason to react to this process.
 * K2 re-reads the same observables and reports any drift.
 *
 * Memory-only by design: never persisted, never logged by default. The
 * snapshot's whole value is that it predates any late-stage manipulation.
 */
internal object IntegrityEarly {

    /** Paths probed at both ends of the session. */
    internal val BIT_PATHS = listOf(
        // Root-manager specific paths only — never the bare /data/adb folder,
        // which exists on stock Android (shared with the adb infrastructure).
        "/data/adb/magisk", "/system/bin/su", "/system/xbin/su", "/sbin/.magisk"
    )

    /** Completed once [capture] has filled every snapshot. */
    private val capturedSignal = CompletableDeferred<Unit>()

    @Volatile private var captured = false
    @Volatile private var props: Map<String, String>? = null
    @Volatile private var bits: Map<String, Boolean>? = null
    @Volatile private var suspiciousMounts: Set<String>? = null

    fun capture() {
        if (captured) return
        captured = true
        try {
            props = runCatching { IntegrityProbes.readProps() }.getOrDefault(emptyMap())
            bits = BIT_PATHS.associateWith { path ->
                runCatching { File(path).exists() }.getOrDefault(false)
            }
            suspiciousMounts = runCatching {
                IntegrityConsistency.suspiciousMountPoints(readMounts(), mountinfo = false)
            }.getOrDefault(emptySet())
        } finally {
            // Signals waiting scans even when a probe above blew up.
            capturedSignal.complete(Unit)
        }
    }

    /**
     * Waits (bounded) until the process-start snapshot exists. Without this
     * handshake a scan that starts first reads nulls, and since the snapshot is
     * taken exactly once, the drift layer would be skipped for that report and
     * the baseline would silently post-date the scan.
     */
    suspend fun awaitCaptured(timeoutMs: Long = 1_500L): Boolean =
        withTimeoutOrNull(timeoutMs) { capturedSignal.await() } != null

    fun propsSnapshot(): Map<String, String>? = props

    fun bitsSnapshot(): Map<String, Boolean>? = bits

    fun suspiciousMountsSnapshot(): Set<String>? = suspiciousMounts

    private fun readMounts(): String = try {
        val file = File("/proc/self/mounts")
        if (file.exists() && file.canRead()) file.readText() else ""
    } catch (_: Exception) {
        ""
    }
}
