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
    fun `an unreadable view is a mismatch when others read fine`() {
        val view = "tmpfs /tmp tmpfs rw 0 0\n"
        val detail = IntegrityConsistency.mountViewMismatch(view, view, "")
        assertNotNull(detail)
        assertTrue(detail!!.contains("unreadable"))
    }

    @Test
    fun `all views unreadable is a miss, not a mismatch`() {
        assertNull(IntegrityConsistency.mountViewMismatch("", "", ""))
    }

    // -------------------------------------------------------- file routes

    @Test
    fun `file routes agreeing produce no mismatch`() {
        assertEquals(
            emptyList<String>(),
            IntegrityConsistency.fileRouteMismatch(
                mapOf("/system/bin/su" to listOf(false, false, false))
            )
        )
    }

    @Test
    fun `file routes disagreeing are reported`() {
        assertEquals(
            listOf("/system/bin/su"),
            IntegrityConsistency.fileRouteMismatch(
                mapOf(
                    "/system/bin/su" to listOf(true, true, false),
                    "/data/adb" to listOf(false, false, false)
                )
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
                mapOf("/data/adb" to false),
                mapOf("/data/adb" to false),
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
            mapOf("/data/adb" to false),
            mapOf("/data/adb" to true),
            emptySet(),
            setOf("/data/adb/modules")
        )
        assertEquals(3, changes.size)
        assertTrue(changes.any { it.contains("ro.boot.flash.locked") })
        assertTrue(changes.any { it.contains("/data/adb") })
        assertTrue(changes.any { it.contains("mounts") })
    }

    @Test
    fun `a failed re-read of a value never counts as change`() {
        assertEquals(
            emptyList<String>(),
            IntegrityConsistency.driftChanges(
                mapOf("ro.boot.flash.locked" to "1"),
                emptyMap(),
                mapOf("/data/adb" to false),
                emptyMap(),
                emptySet(),
                emptySet()
            )
        )
    }
}
