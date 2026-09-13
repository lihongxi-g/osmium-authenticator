package com.safekey.authenticator.integrity

import android.content.Context
import com.safekey.authenticator.security.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pure scoring rules — the single place that maps checks to a level. */
internal object IntegrityScoring {

    /**
     * INFO-class checks that indicate a modified/unverified system (not
     * root): bootloader state and build tags.
     */
    private val UNVERIFIED_TRIGGERS = setOf("boot_props", "build_tags")

    fun levelOf(checks: List<IntegrityCheck>): IntegrityLevel {
        if (checks.any { it.hit && it.severity == IntegritySeverity.FAIL }) {
            return IntegrityLevel.COMPROMISED
        }
        if (checks.any { it.hit && it.severity == IntegritySeverity.WARN }) {
            return IntegrityLevel.SUSPICIOUS
        }
        if (checks.any { it.hit && it.id in UNVERIFIED_TRIGGERS }) {
            return IntegrityLevel.UNVERIFIED
        }
        return IntegrityLevel.CLEAN
    }
}

/**
 * Runs every K1 probe and produces a scored report. Never throws; probe
 * failures degrade to misses. Runs on the IO dispatcher.
 *
 * [force] re-runs the `su` runtime probe (used by the developer-driven
 * refresh); automatic scans reuse its per-process result.
 */
object IntegrityEngine {

    suspend fun scan(context: Context, force: Boolean = false): IntegrityReport =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val checks = mutableListOf<IntegrityCheck>()
            checks += IntegrityProbes.managerPackages(appContext)
            checks += IntegrityProbes.suBinaries()
            checks += IntegrityProbes.rootDirs()
            checks += IntegrityProbes.mountTraces()
            checks += IntegrityProbes.injectedLibs()
            checks += IntegrityProbes.kernelStrings()
            checks += IntegrityProbes.bootProps()
            checks += IntegrityProbes.buildTags()
            checks += IntegrityProbes.selinuxState()
            checks += IntegrityProbes.systemRw()
            checks += IntegrityProbes.runtimeSu(rerun = force)

            val level = IntegrityScoring.levelOf(checks)
            // Gated: only reaches the in-app log when the developer-mode
            // "detailed logging" switch is on (see AppLog.detection).
            AppLog.detection(
                "integrity scan: level=$level fired=" +
                    checks.filter { it.hit }.joinToString(",") { it.id }
            )
            IntegrityReport(System.currentTimeMillis(), checks, level)
        }
}
