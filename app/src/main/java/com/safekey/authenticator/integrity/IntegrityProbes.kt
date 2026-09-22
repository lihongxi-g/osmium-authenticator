package com.safekey.authenticator.integrity

import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * K1 probes — best-effort local checks: no permissions, no network, no Play
 * services. Every probe catches its own failures and reports a miss; a scan
 * must never take the app down. Root-hiding stacks (Magisk DenyList +
 * Shamiko, HMA, KernelSU + SUSFS) can suppress individual signals — that is
 * expected, the layered engine widens the net across independent sources.
 */
internal object IntegrityProbes {

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
        "/system_ext/bin/su", "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
        // Magisk exposes su through the debug ramdisk on several setups.
        "/debug_ramdisk/su"
    )

    private val ROOT_DIRS = listOf(
        "/data/adb/magisk", "/data/adb/modules", "/data/adb/ksu", "/data/adb/ksud",
        "/data/adb/ap", "/data/adb/apd", "/data/adb/shamiko", "/sbin/.magisk"
    )

    private val BOOT_PROP_KEYS = listOf(
        "ro.boot.verifiedbootstate", "ro.boot.vbmeta.device_state",
        "ro.boot.flash.locked", "ro.debuggable", "ro.secure",
        // Read by the K3 probe for the attestation boot-hash cross-check.
        "ro.boot.vbmeta.digest"
    )

    // ------------------------------------------------------------- probes

    /**
     * Anti-repackaging: the app's own signing certificate must match the
     * official fingerprint baked into the build.
     *
     * Only a real digest mismatch is hard evidence. A check that could not run
     * (PackageManager hiccup, provider unavailable) is a miss here — the
     * start-up gate in MainActivity already fails closed for that case, and
     * turning it into FAIL made the report claim "compromised device" for a
     * transient lookup failure.
     */
    fun apkSignature(context: Context): IntegrityCheck {
        val verdict = runCatching {
            com.safekey.authenticator.security.IntegrityCheck.signatureVerdict(context)
        }.getOrDefault(
            com.safekey.authenticator.security.IntegrityCheck.SignatureVerdict.UNAVAILABLE
        )
        return when (verdict) {
            com.safekey.authenticator.security.IntegrityCheck.SignatureVerdict.MATCH ->
                IntegrityCheck("apk_signature", IntegritySeverity.INFO, false, "")
            com.safekey.authenticator.security.IntegrityCheck.SignatureVerdict.MISMATCH ->
                IntegrityCheck(
                    "apk_signature", IntegritySeverity.FAIL, true,
                    "signing certificate mismatch"
                )
            com.safekey.authenticator.security.IntegrityCheck.SignatureVerdict.UNAVAILABLE ->
                IntegrityCheck(
                    "apk_signature", IntegritySeverity.INFO, false,
                    "signature check unavailable"
                )
        }
    }

    /**
     * Installed root-manager apps (Magisk, KernelSU, LSPosed, …) — a weak
     * indicator only. Having a manager app installed does not prove the
     * device is rooted (leftovers, companion setups, hidden root), so this
     * is a WARN: doubt, never a hard verdict.
     */
    fun managerPackages(context: Context): IntegrityCheck {
        val found = checkManagerPackages(context)
        return IntegrityCheck(
            "manager_packages", IntegritySeverity.WARN,
            found.isNotEmpty(), found.joinToString(", ")
        )
    }

    @Suppress("DEPRECATION")
    private fun checkManagerPackages(context: Context): List<String> = try {
        val pm = context.packageManager
        ROOT_PACKAGES.mapNotNull { (pkg, label) ->
            try {
                pm.getPackageInfo(pkg, 0)
                label
            } catch (_: Exception) {
                null
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun suBinaries(): IntegrityCheck = existenceCheck("su_binaries", SU_PATHS)

    fun rootDirs(): IntegrityCheck = existenceCheck("root_dirs", ROOT_DIRS)

    /**
     * FAIL-class existence check over candidate paths.
     *
     * Uses the tri-state [pathVerdict] rather than `File.exists()`: exists()
     * reports EACCES as "absent", and every /data/adb entry is unreadable for an
     * untrusted app (the directory is 0700 root:root), so the root-manager paths
     * this probe exists for could never fire. Unobservable paths are listed for
     * the record — a miss, never a verdict.
     */
    private fun existenceCheck(id: String, paths: List<String>): IntegrityCheck {
        val verdicts = runCatching {
            paths.distinct().associateWith { pathVerdict(it) }
        }.getOrDefault(emptyMap())
        val found = verdicts.filterValues { it == RouteVerdict.SEEN }.keys.toList()
        val blind = verdicts.filterValues { it == RouteVerdict.UNKNOWN }.keys.toList()
        val detail = buildString {
            append(found.joinToString(", "))
            if (blind.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("unobservable: ").append(blind.joinToString(", "))
            }
        }
        return IntegrityCheck(id, IntegritySeverity.FAIL, found.isNotEmpty(), detail)
    }

    /** Tri-state existence verdict for one path (see [RouteVerdict]). */
    private fun pathVerdict(path: String): RouteVerdict = try {
        Os.stat(path)
        RouteVerdict.SEEN
    } catch (error: Exception) {
        val errno = (error as? ErrnoException)?.errno
        if (errno == OsConstants.ENOENT || errno == OsConstants.ENOTDIR) {
            RouteVerdict.NOT_SEEN
        } else {
            RouteVerdict.UNKNOWN
        }
    }

    fun mountTraces(): IntegrityCheck {
        val tokens = runCatching {
            IntegrityAnalysis.mountsTokens(readTextQuietly("/proc/self/mounts"))
        }.getOrDefault(emptySet())
        return IntegrityCheck(
            "mount_traces", IntegritySeverity.FAIL,
            tokens.isNotEmpty(), tokens.joinToString(", ")
        )
    }

    fun injectedLibs(): IntegrityCheck {
        val tokens = runCatching {
            IntegrityAnalysis.mapsTokens(readTextQuietly("/proc/self/maps"))
        }.getOrDefault(emptySet())
        return IntegrityCheck(
            "injected_libs", IntegritySeverity.FAIL,
            tokens.isNotEmpty(), tokens.joinToString(", ")
        )
    }

    fun kernelStrings(): IntegrityCheck {
        val kernel = kernelString()
        val tokens = IntegrityAnalysis.kernelTokens(kernel)
        val detail = if (tokens.isNotEmpty()) {
            tokens.joinToString(", ") + " · " + kernel.take(64)
        } else ""
        return IntegrityCheck("kernel_strings", IntegritySeverity.FAIL, tokens.isNotEmpty(), detail)
    }

    fun bootProps(): IntegrityCheck {
        val findings = runCatching {
            IntegrityAnalysis.propFindings(readProps())
        }.getOrDefault(emptyList())
        return IntegrityCheck(
            "boot_props", IntegritySeverity.INFO,
            findings.isNotEmpty(), findings.joinToString("; ")
        )
    }

    fun buildTags(): IntegrityCheck {
        val findings = IntegrityAnalysis.buildTagFindings(
            Build.TAGS.orEmpty(), Build.TYPE.orEmpty()
        )
        return IntegrityCheck(
            "build_tags", IntegritySeverity.INFO,
            findings.isNotEmpty(), findings.joinToString("; ")
        )
    }

    fun selinuxState(): IntegrityCheck {
        val enforce = readTextQuietly("/sys/fs/selinux/enforce").trim()
        val permissive = IntegrityAnalysis.selinuxPermissive(enforce.takeIf { it.isNotEmpty() })
        return IntegrityCheck(
            "selinux_state", IntegritySeverity.WARN,
            permissive, if (permissive) "enforce=0 (permissive)" else ""
        )
    }

    fun systemRw(): IntegrityCheck {
        val mounted = runCatching {
            IntegrityAnalysis.rwSystemMounts(readTextQuietly("/proc/self/mounts"))
        }.getOrDefault(emptyList())
        return IntegrityCheck(
            "system_rw", IntegritySeverity.WARN,
            mounted.isNotEmpty(), mounted.joinToString(", ")
        )
    }

    @Volatile
    private var runtimeSuCached: IntegrityCheck? = null

    /**
     * `su -c id` probe. A uid=0 result is hard evidence; an absent or denied
     * su is a quiet miss. Runs at most once per process unless [rerun] is set
     * (developer-driven force scan), so a root manager's grant prompt cannot
     * pop on every foreground scan.
     */
    fun runtimeSu(rerun: Boolean = false): IntegrityCheck {
        val cached = runtimeSuCached
        if (cached != null && !rerun) return cached
        val result = runRuntimeSu()
        runtimeSuCached = result
        return result
    }

    private fun runRuntimeSu(): IntegrityCheck {
        return try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(1500, TimeUnit.MILLISECONDS)
            val output = if (finished) {
                process.inputStream.bufferedReader().use { it.readText() }
            } else {
                process.destroy()
                ""
            }
            val granted = finished && process.exitValue() == 0 && output.contains("uid=0")
            IntegrityCheck(
                "su_runtime", IntegritySeverity.FAIL,
                granted, if (granted) "su -c id -> uid=0" else ""
            )
        } catch (_: Exception) {
            IntegrityCheck("su_runtime", IntegritySeverity.FAIL, false, "")
        }
    }

    // ------------------------------------------------------ K2 consistency

    /**
     * K2: the three proc mount views must agree on suspicious entries — a
     * framework that hides mounts from one view usually misses another.
     */
    fun mountViewCross(): IntegrityCheck {
        val mismatch = runCatching {
            IntegrityConsistency.mountViewMismatch(
                readTextQuietly("/proc/mounts"),
                readTextQuietly("/proc/self/mounts"),
                readTextQuietly("/proc/self/mountinfo")
            )
        }.getOrNull()
        return IntegrityCheck(
            "mount_cross", IntegritySeverity.WARN,
            mismatch != null, mismatch.orEmpty()
        )
    }

    /**
     * K2: existence verdicts for su-related paths through three routes
     * (File.exists, Os.stat, parent listing). Each route is tri-state; a
     * route that cannot look at all reports UNKNOWN and never counts as
     * evidence. Only a real contradiction — some route sees the path while
     * another positively does not — is a mismatch.
     *
     * /data/adb is an existing folder on modern Android shared with the
     * platform adb infrastructure (SELinux label adb_data_file) — Magisk
     * picked it precisely because its presence is not a root indicator.
     * It is therefore never a candidate here; only root-manager-specific
     * subpaths are cross-checked. And since no app can ever list /data, the
     * listing route for those is structurally blind and reports UNKNOWN —
     * a miss, never a contradiction.
     * (Real-device finding, v2.4.3 test round: false warning on an unrooted
     * phone caused by the blind listing route.)
     */
    fun fileViewCross(): IntegrityCheck {
        val routes = runCatching { readFileRoutes() }.getOrDefault(emptyMap())
        val mismatched = IntegrityConsistency.fileRouteMismatch(routes)
        val detail = mismatched.joinToString(", ") { path ->
            "$path (${IntegrityConsistency.routeSummary(routes[path].orEmpty())})"
        }
        return IntegrityCheck(
            "file_cross", IntegritySeverity.WARN,
            mismatched.isNotEmpty(), detail
        )
    }

    private fun readFileRoutes(): Map<String, List<RouteVerdict>> {
        val candidates = (SU_PATHS + listOf("/data/adb/magisk", "/sbin/.magisk")).distinct()
        val out = LinkedHashMap<String, List<RouteVerdict>>()
        for (path in candidates) {
            val statVerdict = pathVerdict(path)
            // File.exists() cannot tell ENOENT from EACCES; when the precise
            // stat above is blind too, this route must stay blind instead of
            // claiming absence.
            val exists = runCatching { File(path).exists() }.getOrDefault(false)
            val fileVerdict = when {
                exists -> RouteVerdict.SEEN
                statVerdict == RouteVerdict.UNKNOWN -> RouteVerdict.UNKNOWN
                else -> RouteVerdict.NOT_SEEN
            }
            // An unlistable parent (no app can list /data) leaves this route
            // blind — a structural miss, not a contradiction.
            val listingVerdict = runCatching {
                val entry = File(path)
                val entries = entry.parentFile?.list()
                when {
                    entries == null -> RouteVerdict.UNKNOWN
                    entries.contains(entry.name) -> RouteVerdict.SEEN
                    else -> RouteVerdict.NOT_SEEN
                }
            }.getOrDefault(RouteVerdict.UNKNOWN)
            out[path] = listOf(fileVerdict, statVerdict, listingVerdict)
        }
        return out
    }

    /**
     * K2: compares the K0 early snapshot (SafeKeyApp.onCreate) with the live
     * state — late module activation or dynamic hiding shows up as drift.
     */
    suspend fun stateDrift(): IntegrityCheck {
        // The K0 snapshot is captured on a background thread at process start;
        // wait for it (bounded) so a scan that wins the race does not silently
        // skip the whole drift layer.
        IntegrityEarly.awaitCaptured()
        val beforeProps = IntegrityEarly.propsSnapshot()
        val beforeBits = IntegrityEarly.bitsSnapshot()
        val beforeMounts = IntegrityEarly.suspiciousMountsSnapshot()
        if (beforeProps == null || beforeBits == null || beforeMounts == null) {
            return IntegrityCheck("state_drift", IntegritySeverity.WARN, false, "")
        }
        val afterProps = readProps()
        val afterBits = IntegrityEarly.BIT_PATHS.associateWith { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }
        val afterMounts = runCatching {
            IntegrityConsistency.suspiciousMountPoints(
                readTextQuietly("/proc/self/mounts"), mountinfo = false
            )
        }.getOrDefault(emptySet())
        val changes = runCatching {
            IntegrityConsistency.driftChanges(
                beforeProps, afterProps, beforeBits, afterBits, beforeMounts, afterMounts
            )
        }.getOrDefault(emptyList())
        return IntegrityCheck(
            "state_drift", IntegritySeverity.WARN,
            changes.isNotEmpty(), changes.joinToString("; ").take(200)
        )
    }

    // ------------------------------------------------------------ helpers

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

    /** Single boot property lookup for the other probes (batched getprop). */
    internal fun bootProp(key: String): String? = readProps()[key]

    /** One batched `/system/bin/getprop` call; empty map when unavailable. */
    internal fun readProps(): Map<String, String> {
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
