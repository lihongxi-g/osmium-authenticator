package com.safekey.authenticator.integrity

import android.content.Context
import android.os.Build
import android.system.Os
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
        "ro.boot.flash.locked", "ro.debuggable", "ro.secure"
    )

    // ------------------------------------------------------------- probes

    /**
     * Anti-repackaging: the app's own signing certificate must match the
     * official fingerprint baked into the build. The startup check already
     * refuses to run a re-signed APK; surfacing it here keeps the integrity
     * report complete. Fails closed, like the startup check.
     */
    fun apkSignature(context: Context): IntegrityCheck {
        val tampered = runCatching {
            com.safekey.authenticator.security.IntegrityCheck.isTampered(context)
        }.getOrDefault(true)
        return IntegrityCheck(
            "apk_signature", IntegritySeverity.FAIL,
            tampered, if (tampered) "signing certificate mismatch" else ""
        )
    }

    fun managerPackages(context: Context): IntegrityCheck {
        val found = checkManagerPackages(context)
        return IntegrityCheck(
            "manager_packages", IntegritySeverity.FAIL,
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

    fun suBinaries(): IntegrityCheck {
        val found = runCatching {
            SU_PATHS.filter { File(it).exists() }
        }.getOrDefault(emptyList())
        return IntegrityCheck(
            "su_binaries", IntegritySeverity.FAIL,
            found.isNotEmpty(), found.joinToString(", ")
        )
    }

    fun rootDirs(): IntegrityCheck {
        val found = runCatching {
            ROOT_DIRS.filter { File(it).exists() }
        }.getOrDefault(emptyList())
        return IntegrityCheck(
            "root_dirs", IntegritySeverity.FAIL,
            found.isNotEmpty(), found.joinToString(", ")
        )
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
