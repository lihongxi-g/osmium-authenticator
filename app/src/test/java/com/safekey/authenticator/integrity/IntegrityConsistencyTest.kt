package com.safekey.authenticator.integrity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityConsistencyTest {

    // ------------------------------------------------------- mount parsing

    @Test
    fun `unescape decodes octal space`() {
        assertEquals(
            "/mnt/my point",
            IntegrityConsistency.unescapeMountField("/mnt/my\\040point")
        )
    }

    @Test
    fun `unescape leaves plain fields untouched`() {
        assertEquals(
            "/system/bin",
            IntegrityConsistency.unescapeMountField("/system/bin")
        )
    }

    @Test
    fun `fstab rows expose suspicious mount points`() {
        val text = """
            /dev/block/dm-4 /system erofs ro,seclabel 0 0
            magisk /sbin/.magisk/mirror tmpfs rw 0 0
            tmpfs /tmp tmpfs rw 0 0
        """.trimIndent()
        assertEquals(
            setOf("/sbin/.magisk/mirror"),
            IntegrityConsistency.suspiciousMountPoints(text, mountinfo = false)
        )
    }

    @Test
    fun `mountinfo rows expose suspicious mount points`() {
        val text = """
            1025 24 0:24 / /data/adb/modules rw,nosuid,nodev - tmpfs tmpfs rw
            890 25 253:1 / /data rw,seclabel - ext4 /dev/block/dm-2 rw
        """.trimIndent()
        assertEquals(
            setOf("/data/adb/modules"),
            IntegrityConsistency.suspiciousMountPoints(text, mountinfo = true)
        )
    }

    // ------------------------------------------------------ mount mismatch

    @Test
    fun `identical views are consistent`() {
        val fstab = "magisk /sbin/.magisk/mirror tmpfs rw 0 0\n"
        val mountinfo = "1025 24 0:24 / /sbin/.magisk/mirror rw - tmpfs tmpfs rw\n"
        assertNull(IntegrityConsistency.mountViewMismatch(fstab, fstab, mountinfo))
    }

    @Test
    fun `a suspicious entry hidden from one view is a mismatch`() {
        val full = "magisk /sbin/.magisk/mirror tmpfs rw 0 0\n"
        val stripped = "tmpfs /tmp tmpfs rw 0 0\n"
        val mountinfo = "1025 24 0:24 / /sbin/.magisk/mirror rw - tmpfs tmpfs rw\n"
        val detail = IntegrityConsistency.mountViewMismatch(full, stripped, mountinfo)
        assertNotNull(detail)
        assertTrue(detail!!.contains("disagree"))
    }

    @Test
    fun `an unreadable view is a miss, never a mismatch`() {
        // Reporting it as a hit made every scan on such a device "SUSPICIOUS"
        // although no divergence had been observed. It is listed informationally.
        val view = "tmpfs /tmp tmpfs rw 0 0\n"
        assertNull(IntegrityConsistency.mountViewMismatch(view, view, ""))
        assertEquals(
            listOf("mountinfo"),
            IntegrityConsistency.unreadableMountViews(view, view, "")
        )
        assertEquals(
            listOf("mounts", "self/mounts", "mountinfo"),
            IntegrityConsistency.unreadableMountViews("", "", "")
        )
    }

    @Test
    fun `one readable view cannot disagree with itself`() {
        val view = "magisk /sbin/.magisk/mirror tmpfs rw 0 0\n"
        assertNull(IntegrityConsistency.mountViewMismatch(view, "", ""))
    }

    @Test
    fun `all views unreadable is a miss, not a mismatch`() {
        assertNull(IntegrityConsistency.mountViewMismatch("", "", ""))
    }

    // -------------------------------------------------------- file routes

    @Test
    fun `file routes agreeing on absence produce no mismatch`() {
        assertEquals(
            emptyList<String>(),
            IntegrityConsistency.fileRouteMismatch(
                mapOf(
                    "/system/bin/su" to listOf(
                        RouteVerdict.NOT_SEEN, RouteVerdict.NOT_SEEN, RouteVerdict.NOT_SEEN
                    )
                )
            )
        )
    }

    @Test
    fun `a blind route never counts as evidence`() {
        // Regression: /data/adb exists on modern Android (adb infrastructure),
        // but no app can ever list /data — the listing route stays UNKNOWN and
        // must not turn "exists" into a mismatch warning.
        // (v2.4.3 test round: false warning on an unrooted phone.)
        assertEquals(
            emptyList<String>(),
            IntegrityConsistency.fileRouteMismatch(
                mapOf(
                    "/data/adb" to listOf(
                        RouteVerdict.SEEN, RouteVerdict.SEEN, RouteVerdict.UNKNOWN
                    ),
                    "/data/adb/magisk" to listOf(
                        RouteVerdict.NOT_SEEN, RouteVerdict.NOT_SEEN, RouteVerdict.UNKNOWN
                    )
                )
            )
        )
    }

    @Test
    fun `all routes blind is a miss, not a mismatch`() {
        assertEquals(
            emptyList<String>(),
            IntegrityConsistency.fileRouteMismatch(
                mapOf(
                    "/data/adb" to listOf(
                        RouteVerdict.UNKNOWN, RouteVerdict.UNKNOWN, RouteVerdict.UNKNOWN
                    )
                )
            )
        )
    }

    @Test
    fun `a route seeing what another denies is reported`() {
        assertEquals(
            listOf("/system/bin/su"),
            IntegrityConsistency.fileRouteMismatch(
                mapOf(
                    "/system/bin/su" to listOf(
                        RouteVerdict.SEEN, RouteVerdict.SEEN, RouteVerdict.NOT_SEEN
                    ),
                    "/data/adb" to listOf(
                        RouteVerdict.NOT_SEEN, RouteVerdict.NOT_SEEN, RouteVerdict.NOT_SEEN
                    )
                )
            )
        )
    }

    @Test
    fun `a java-level hide caught by the stat route is reported`() {
        // A hook that fakes "absent" through java.io.File while the native
        // stat still sees the path: that contradiction is the signal.
        assertEquals(
            listOf("/system/bin/su"),
            IntegrityConsistency.fileRouteMismatch(
                mapOf(
                    "/system/bin/su" to listOf(
                        RouteVerdict.NOT_SEEN, RouteVerdict.SEEN, RouteVerdict.SEEN
                    )
                )
            )
        )
    }

    @Test
    fun `route summary renders tri-state verdicts`() {
        assertEquals(
            "seen,seen,unknown",
            IntegrityConsistency.routeSummary(
                listOf(RouteVerdict.SEEN, RouteVerdict.SEEN, RouteVerdict.UNKNOWN)
            )
        )
        assertEquals(
            "absent,unknown,seen",
            IntegrityConsistency.routeSummary(
                listOf(RouteVerdict.NOT_SEEN, RouteVerdict.UNKNOWN, RouteVerdict.SEEN)
            )
        )
    }

    // ------------------------------------------------------------- drift

    @Test
    fun `no changes produce no drift`() {
        assertEquals(
            emptyList<String>(),
            IntegrityConsistency.driftChanges(
                mapOf("ro.boot.flash.locked" to "1"),
                mapOf("ro.boot.flash.locked" to "1"),
                mapOf("/data/adb/magisk" to false),
                mapOf("/data/adb/magisk" to false),
                emptySet(),
                emptySet()
            )
        )
    }

    @Test
    fun `property file and mount changes are reported`() {
        val changes = IntegrityConsistency.driftChanges(
            mapOf("ro.boot.flash.locked" to "1", "ro.secure" to "1"),
            mapOf("ro.boot.flash.locked" to "0", "ro.secure" to "1"),
            mapOf("/data/adb/magisk" to false),
            mapOf("/data/adb/magisk" to true),
            emptySet(),
            setOf("/data/adb/modules")
        )
        assertEquals(3, changes.size)
        assertTrue(changes.any { it.contains("ro.boot.flash.locked") })
        assertTrue(changes.any { it.contains("/data/adb/magisk") })
        assertTrue(changes.any { it.contains("mounts") })
    }

    @Test
    fun `a failed re-read of a value never counts as change`() {
        assertEquals(
            emptyList<String>(),
            IntegrityConsistency.driftChanges(
                mapOf("ro.boot.flash.locked" to "1"),
                emptyMap(),
                mapOf("/data/adb/magisk" to false),
                emptyMap(),
                emptySet(),
                emptySet()
            )
        )
    }
}
