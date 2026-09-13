package com.safekey.authenticator.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Root / system-modification detection for the hardened-on-root behaviour and
 * the developer-mode tools introduced in 2.4.2.
 *
 * All checks are local (no network, no permissions, no Google Play services)
 * and best-effort by nature: root-hiding stacks (Magisk DenyList + Shamiko,
 * HMA, KernelSU + SUSFS) can suppress most signals. A STRONG hit marks the
 * device as rooted (used to force the app's security features and disable
 * risky features); INFO hits are only surfaced in the developer-mode report.
 * False positives are escapable via developer mode by design.
 *
 * Signal tiers:
 *  - STRONG: root manager packages, legacy su binaries, /data/adb artifacts,
 *            mount traces, libraries injected into this process, kernel strings.
 *  - INFO:   boot/debug system properties and build tags — these indicate a
 *            *modified system*, not necessarily root, so they never trigger
 *            the forced behaviour on their own.
 */

enum class RootTier { STRONG, INFO }

data class RootSignal(
    val id: String,
    val tier: RootTier,
    val hit: Boolean,
    val detail: String = ""
)

data class RootReport(
    val checkedAt: Long,
    val signals: List<RootSignal>
) {
    val rooted: Boolean get() = signals.any { it.tier == RootTier.STRONG && it.hit }
    val strongHits: List<RootSignal> get() = signals.filter { it.tier == RootTier.STRONG && it.hit }
    val infoHits: List<RootSignal> get() = signals.filter { it.tier == RootTier.INFO && it.hit }
}

/** Pure string analysis — unit-tested on the JVM without Android. */
internal object RootAnalysis {

    private val MOUNT_TOKENS = listOf("magisk", "kernelsu", "susfs", "zygisk", "shamiko", "apatch")
    private val MAP_TOKENS = listOf("magisk", "zygisk", "lsposed", "riru", "shamiko", "kernelsu", "susfs")
    private val KERNEL_TOKENS = listOf("kernelsu", "susfs", "apatch", "magisk")

    fun mountsTokens(text: String): Set<String> = matchTokens(text, MOUNT_TOKENS)

    fun mapsTokens(text: String): Set<String> = matchTokens(text, MAP_TOKENS)

    fun kernelTokens(text: String): Set<String> = matchTokens(text, KERNEL_TOKENS)

    private fun matchTokens(text: String, tokens: List<String>): Set<String> {
        if (text.isEmpty()) return emptySet()
        val lower = text.lowercase()
        return tokens.filterTo(LinkedHashSet()) { lower.contains(it) }
    }

    /**
     * Boot/debug properties that indicate a modified system (informational).
     * Reads fail-closed: an empty/absent value is never reported.
     */
    fun propFindings(props: Map<String, String>): List<String> {
        val out = mutableListOf<String>()
        fun value(key: String) = props[key]?.trim().orEmpty()

        val bootState = value("ro.boot.verifiedbootstate")
        if (bootState.isNotEmpty() && !bootState.equals("green", ignoreCase = true)) {
            out += "verifiedbootstate=$bootState"
        }
        if (value("ro.boot.vbmeta.device_state").equals("unlocked", ignoreCase = true)) {
            out += "vbmeta.device_state=unlocked"
        }
        if (value("ro.boot.flash.locked") == "0") out += "flash.locked=0"
        if (value("ro.debuggable") == "1") out += "ro.debuggable=1"
        if (value("ro.secure") == "0") out += "ro.secure=0"
        return out
    }

    /** Build.TAGS / Build.TYPE findings (informational). */
    fun buildTagFindings(tags: String, type: String): List<String> {
        val out = mutableListOf<String>()
        if (tags.contains("test-keys")) out += "build.tags=test-keys"
        if (type.isNotEmpty() && !type.equals("user", ignoreCase = true)) out += "build.type=$type"
        return out
    }
}

object RootDetector {

    // Keep in sync with the <queries> list in AndroidManifest.xml.
    private val ROOT_PACKAGES = listOf(
        "com.topjohnwu.magisk" to "Magisk",
        "io.github.vvb2060.magisk" to "Magisk Alpha",
        "io.github.huskydg.magisk" to "Kitsune Mask",
        "me.weishu.kernelsu" to "KernelSU",
        "com.rifsxd.ksunext" to "KernelSU Next",
        "com.sukisu.ultra" to "SukiSU Ultra",
        "me.bmax.apatch" to "APatch",
        "org.lsposed.manager" to "LSPosed",
        "com.tsng.hidemyapplist" to "Hide My Applist",
        "eu.chainfire.supersu" to "SuperSU",
        "com.noshufou.android.su" to "Superuser",
        "com.koushikdutta.superuser" to "Superuser (Koush)",
        "com.thirdparty.superuser" to "Superuser (thirdparty)",
        "com.yellowes.su" to "Superuser (yellowes)",
        "com.kingroot.kinguser" to "KingRoot",
        "com.kingo.root" to "KingoRoot",
        "com.zhiqupk.root.global" to "root (zhiqupk)",
        "com.alephzain.framaroot" to "Framaroot",
        "com.geohot.towelroot" to "TowelRoot",
        "de.robv.android.xposed.installer" to "Xposed Installer"
    )

    private val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sbin/su",
        "/vendor/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su",
        "/system_ext/bin/su", "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su"
    )

    private val ROOT_DIRS = listOf(
        "/data/adb/magisk", "/data/adb/modules", "/data/adb/ksu", "/data/adb/ksud",
        "/data/adb/ap", "/data/adb/apd", "/data/adb/shamiko", "/sbin/.magisk"
    )

    private val BOOT_PROP_KEYS = listOf(
        "ro.boot.verifiedbootstate", "ro.boot.vbmeta.device_state",
        "ro.boot.flash.locked", "ro.debuggable", "ro.secure"
    )

    /** Runs every check and returns a full report. Never throws. */
    suspend fun scan(context: Context): RootReport = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val signals = mutableListOf<RootSignal>()

        signals += run {
            val found = checkManagerPackages(appContext)
            RootSignal("manager_packages", RootTier.STRONG, found.isNotEmpty(), found.joinToString(", "))
        }
        signals += run {
            val found = SU_PATHS.filter { File(it).exists() }
            RootSignal("su_binaries", RootTier.STRONG, found.isNotEmpty(), found.joinToString(", "))
        }
        signals += run {
            val found = ROOT_DIRS.filter { File(it).exists() }
            RootSignal("root_dirs", RootTier.STRONG, found.isNotEmpty(), found.joinToString(", "))
        }
        signals += run {
            val tokens = runCatching { RootAnalysis.mountsTokens(readTextQuietly("/proc/self/mounts")) }
                .getOrDefault(emptySet())
            RootSignal("mount_traces", RootTier.STRONG, tokens.isNotEmpty(), tokens.joinToString(", "))
        }
        signals += run {
            val tokens = runCatching { RootAnalysis.mapsTokens(readTextQuietly("/proc/self/maps")) }
                .getOrDefault(emptySet())
            RootSignal("injected_libs", RootTier.STRONG, tokens.isNotEmpty(), tokens.joinToString(", "))
        }
        signals += run {
            val kernel = kernelString()
            val tokens = RootAnalysis.kernelTokens(kernel)
            val detail = if (tokens.isNotEmpty()) {
                tokens.joinToString(", ") + " · " + kernel.take(64)
            } else ""
            RootSignal("kernel_strings", RootTier.STRONG, tokens.isNotEmpty(), detail)
        }
        signals += run {
            val findings = runCatching { RootAnalysis.propFindings(readProps()) }.getOrDefault(emptyList())
            RootSignal("boot_props", RootTier.INFO, findings.isNotEmpty(), findings.joinToString("; "))
        }
        signals += run {
            val findings = RootAnalysis.buildTagFindings(
                Build.TAGS.orEmpty(), Build.TYPE.orEmpty()
            )
            RootSignal("build_tags", RootTier.INFO, findings.isNotEmpty(), findings.joinToString("; "))
        }

        RootReport(System.currentTimeMillis(), signals)
    }

    @Suppress("DEPRECATION")
    private fun checkManagerPackages(context: Context): List<String> {
        val pm = context.packageManager
        return ROOT_PACKAGES.mapNotNull { (pkg, label) ->
            try {
                pm.getPackageInfo(pkg, 0)
                label
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun kernelString(): String {
        val fromUname = try {
            val u = Os.uname()
            "${u.release} ${u.version}".trim()
        } catch (_: Exception) {
            ""
        }
        if (fromUname.isNotEmpty()) return fromUname
        return try {
            File("/proc/version").readText().trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun readTextQuietly(path: String): String = try {
        val f = File(path)
        if (f.exists() && f.canRead()) f.readText() else ""
    } catch (_: Exception) {
        ""
    }

    /** One batched `/system/bin/getprop` call; empty map when unavailable. */
    private fun readProps(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val text = try {
            val process = ProcessBuilder("/system/bin/getprop")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy()
            output
        } catch (_: Exception) {
            return out
        }
        for (key in BOOT_PROP_KEYS) {
            // getprop prints lines like: [ro.boot.flash.locked]: [1]
            val match = Regex("\\[" + Regex.escape(key) + "]: \\[(.*?)]").find(text)
            if (match != null) out[key] = match.groupValues[1].trim()
        }
        return out
    }
}

/**
 * Process-wide holder for the latest root report. Scans are throttled;
 * [refresh] with force=true is used by the developer-mode "run root check".
 * Diagnostic logging records signal IDs only — never raw device content.
 */
object RootState {

    /** Minimum gap between automatic scans (cheap, but no need to spam). */
    const val MIN_INTERVAL_MS = 30_000L

    private val _report = MutableStateFlow<RootReport?>(null)
    val report: StateFlow<RootReport?> = _report

    @Volatile private var lastScanAt = 0L

    @Volatile private var scanning = false

    suspend fun refresh(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && _report.value != null && now - lastScanAt < MIN_INTERVAL_MS) return
        if (scanning) return
        scanning = true
        try {
            val report = RootDetector.scan(context.applicationContext)
            _report.value = report
            lastScanAt = System.currentTimeMillis()
            if (report.rooted) {
                AppLog.d("root scan: POSITIVE (" + report.strongHits.joinToString(",") { it.id } + ")")
            } else {
                AppLog.d("root scan: negative (info=" + report.infoHits.joinToString(",") { it.id } + ")")
            }
        } catch (e: Exception) {
            AppLog.d("root scan failed: " + e.javaClass.simpleName)
        } finally {
            scanning = false
        }
    }
}
