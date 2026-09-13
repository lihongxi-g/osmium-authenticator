package com.safekey.authenticator.security

import android.content.Context
import com.safekey.authenticator.integrity.IntegrityEngine
import com.safekey.authenticator.integrity.IntegrityReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide holder for the latest device-integrity report.
 *
 * 2026-09 refactor: detection moved to the layered engine
 * (com.safekey.authenticator.integrity — K1 local probes, K2 consistency
 * cross-checks, K3 Key Attestation). This object stays as the lightweight
 * facade the UI and workers consume, so the root-hardening overlay and
 * developer-mode tools keep working unchanged.
 *
 * Scans are throttled and guarded against concurrent runs; [refresh] with
 * force=true is used by the developer-mode "run root check". Detection
 * results only reach the in-app log when the developer-mode "detailed
 * logging" switch is on (see AppLog.detection).
 */
object RootState {

    /** Minimum gap between automatic scans (cheap, but no need to spam). */
    const val MIN_INTERVAL_MS = 30_000L

    private val _report = MutableStateFlow<IntegrityReport?>(null)
    val report: StateFlow<IntegrityReport?> = _report

    @Volatile private var lastScanAt = 0L

    private val scanning = AtomicBoolean(false)

    suspend fun refresh(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && _report.value != null && now - lastScanAt < MIN_INTERVAL_MS) return
        if (!scanning.compareAndSet(false, true)) return
        try {
            _report.value = IntegrityEngine.scan(context.applicationContext, force = force)
            lastScanAt = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.detection("integrity scan failed: " + e.javaClass.simpleName)
        } finally {
            scanning.set(false)
        }
    }
}
