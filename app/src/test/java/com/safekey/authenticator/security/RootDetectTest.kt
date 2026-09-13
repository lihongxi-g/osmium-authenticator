package com.safekey.authenticator.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootDetectTest {

    // ------------------------------------------------------------- mounts

    @Test
    fun `mounts with magisk traces are detected`() {
        val mounts = """
            /dev/block/dm-0 /system ext4 ro,seclabel,relatime 0 0
            magisk /system/etc/hosts tmpfs rw,seclabel,relatime 0 0
            tmpfs /sbin tmpfs rw,seclabel,relatime,mode=755 0 0
        """.trimIndent()
        assertEquals(setOf("magisk"), RootAnalysis.mountsTokens(mounts))
    }

    @Test
    fun `plain stock mounts produce no tokens`() {
        val mounts = """
            /dev/block/dm-0 /system ext4 ro,seclabel,relatime 0 0
            tmpfs /dev tmpfs rw,seclabel,nosuid,relatime,mode=755 0 0
            /dev/block/dm-3 /data f2fs rw,seclabel,nosuid,nodev,noatime 0 0
            /dev/block/loop4 /apex/com.android.art ext4 ro,seclabel,nodev,noatime 0 0
        """.trimIndent()
        assertTrue(RootAnalysis.mountsTokens(mounts).isEmpty())
    }

    @Test
    fun `kernelsu and susfs mount traces are detected case-insensitively`() {
        val mounts = """
            KernelSU /data/adb/modules tmpfs rw,seclabel 0 0
            susfs /system/etc/hosts tmpfs rw 0 0
        """.trimIndent()
        val tokens = RootAnalysis.mountsTokens(mounts)
        assertTrue(tokens.contains("kernelsu"))
        assertTrue(tokens.contains("susfs"))
    }

    @Test
    fun `empty mounts text yields no tokens`() {
        assertTrue(RootAnalysis.mountsTokens("").isEmpty())
    }

    // -------------------------------------------------------------- maps

    @Test
    fun `injected zygisk and lsposed libs are detected`() {
        val maps = """
            7f0000000000-7f0000001000 r-xp 00000000 00:00 0 /data/adb/magisk/libzygisk.so
            7f0000100000-7f0000101000 r-xp 00000000 00:00 0 /dev/lsposed/libdexposed.so
        """.trimIndent()
        val tokens = RootAnalysis.mapsTokens(maps)
        assertTrue(tokens.contains("zygisk"))
        assertTrue(tokens.contains("lsposed"))
    }

    @Test
    fun `ordinary process maps produce no tokens`() {
        val maps = """
            7b0000000000-7b0000100000 r--p 00000000 fd:05 101 /system/lib64/libc.so
            7b0000200000-7b0000800000 r-xp 00000000 fd:05 102 /data/app/~~abc/com.safekey.authenticator/base.apk
            7b0001000000-7b0001010000 r--p 00000000 00:00 0 [anon:libc_malloc]
        """.trimIndent()
        assertTrue(RootAnalysis.mapsTokens(maps).isEmpty())
    }

    // ------------------------------------------------------------ kernel

    @Test
    fun `kernelsu kernel string is detected`() {
        val kernel = "6.1.75-android14-g16c5f6cd5e9b-ab12268718-KernelSU #1 SMP PREEMPT"
        assertTrue(RootAnalysis.kernelTokens(kernel).contains("kernelsu"))
    }

    @Test
    fun `stock kernel string is clean`() {
        val kernel = "5.15.149-android13-8-31753739-ohpiy1hb #1 SMP PREEMPT Fri Mar 8"
        assertTrue(RootAnalysis.kernelTokens(kernel).isEmpty())
    }

    @Test
    fun `susfs kernel string is detected`() {
        val kernel = "6.1.57-android14-o-cctv18-SUSFS"
        assertTrue(RootAnalysis.kernelTokens(kernel).contains("susfs"))
    }

    // ------------------------------------------------------------- props

    @Test
    fun `unlocked boot props produce findings`() {
        val props = mapOf(
            "ro.boot.verifiedbootstate" to "orange",
            "ro.boot.vbmeta.device_state" to "unlocked",
            "ro.boot.flash.locked" to "0",
            "ro.debuggable" to "0",
            "ro.secure" to "1"
        )
        val findings = RootAnalysis.propFindings(props)
        assertEquals(3, findings.size)
        assertTrue(findings.contains("verifiedbootstate=orange"))
        assertTrue(findings.contains("vbmeta.device_state=unlocked"))
        assertTrue(findings.contains("flash.locked=0"))
    }

    @Test
    fun `stock boot props produce no findings`() {
        val props = mapOf(
            "ro.boot.verifiedbootstate" to "green",
            "ro.boot.vbmeta.device_state" to "locked",
            "ro.boot.flash.locked" to "1",
            "ro.debuggable" to "0",
            "ro.secure" to "1"
        )
        assertTrue(RootAnalysis.propFindings(props).isEmpty())
    }

    @Test
    fun `debuggable system is reported`() {
        val props = mapOf(
            "ro.boot.verifiedbootstate" to "green",
            "ro.debuggable" to "1"
        )
        assertEquals(listOf("ro.debuggable=1"), RootAnalysis.propFindings(props))
    }

    @Test
    fun `missing props are never treated as findings`() {
        assertTrue(RootAnalysis.propFindings(emptyMap()).isEmpty())
    }

    // -------------------------------------------------------- build tags

    @Test
    fun `test-keys build is flagged`() {
        assertTrue(RootAnalysis.buildTagFindings("test-keys", "user").contains("build.tags=test-keys"))
    }

    @Test
    fun `userdebug build type is flagged`() {
        assertTrue(RootAnalysis.buildTagFindings("release-keys", "userdebug").contains("build.type=userdebug"))
    }

    @Test
    fun `release build has no findings`() {
        assertTrue(RootAnalysis.buildTagFindings("release-keys", "user").isEmpty())
    }

    // ------------------------------------------------------------ verdict

    @Test
    fun `strong hit marks the device as rooted`() {
        val report = RootReport(
            0L,
            listOf(
                RootSignal("manager_packages", RootTier.STRONG, hit = true, detail = "Magisk"),
                RootSignal("boot_props", RootTier.INFO, hit = true, detail = "verifiedbootstate=orange")
            )
        )
        assertTrue(report.rooted)
        assertEquals(1, report.strongHits.size)
        assertEquals(1, report.infoHits.size)
    }

    @Test
    fun `info-only hits never mark the device as rooted`() {
        val report = RootReport(
            0L,
            listOf(
                RootSignal("manager_packages", RootTier.STRONG, hit = false),
                RootSignal("boot_props", RootTier.INFO, hit = true, detail = "verifiedbootstate=orange")
            )
        )
        assertFalse(report.rooted)
        assertTrue(report.strongHits.isEmpty())
    }

    @Test
    fun `clean report is not rooted`() {
        val report = RootReport(
            0L,
            listOf(
                RootSignal("manager_packages", RootTier.STRONG, hit = false),
                RootSignal("boot_props", RootTier.INFO, hit = false)
            )
        )
        assertFalse(report.rooted)
    }
}
