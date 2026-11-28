package com.safekey.authenticator.security

import android.app.ActivityManager
import android.content.Context

/**
 * Decides which [BackupEncryptionLevel] values this phone may write.
 *
 * The gate looks at the *real* total RAM reported by the platform, never at
 * the Android version: an Android 14 entry-level phone can have 3 GB while an
 * Android 12 flagship has 12 GB.
 *
 * Note the thresholds are deliberately strict (64 MiB needs 6 GB, 128 MiB
 * needs 8 GB). "6 GB" on a spec sheet is ~5.6-5.8 GiB reported by
 * [ActivityManager.MemoryInfo.totalMem], so a phone sold as 6 GB will usually
 * *not* clear the 6 GiB bar — that is intended: the levels are opt-in and the
 * safe default (STANDARD) always works.
 */
object BackupEncryptionGate {

    private const val BYTES_PER_GIB = 1024L * 1024L * 1024L

    /** Raw total RAM in bytes, or 0 when the platform cannot tell us. */
    fun totalMemBytes(context: Context): Long = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        info.totalMem
    } catch (_: Exception) {
        0L
    }

    /** Total RAM rounded down to whole GiB, for the "this phone has N GB" line. */
    fun totalMemGb(context: Context): Int =
        (totalMemBytes(context) / BYTES_PER_GIB).toInt()

    /**
     * [ignoreMemoryLimit] is the developer-mode override: it skips the RAM
     * gate entirely. The write path then relies on the heap check and the
     * OutOfMemoryError catch, which is exactly what the disclaimer warns about.
     */
    fun isSupported(
        context: Context,
        level: BackupEncryptionLevel,
        ignoreMemoryLimit: Boolean = false
    ): Boolean {
        if (level == BackupEncryptionLevel.STANDARD) return true
        if (ignoreMemoryLimit) return true
        val total = totalMemBytes(context)
        // No reading at all: allow only the level that needs nothing.
        if (total <= 0L) return level == BackupEncryptionLevel.STANDARD
        return total >= level.requiredTotalMemGb * BYTES_PER_GIB
    }

    /**
     * Strongest level this phone may write. STANDARD is always available, so
     * this never returns null and the export can never be blocked.
     */
    fun highestSupported(
        context: Context,
        ignoreMemoryLimit: Boolean = false
    ): BackupEncryptionLevel =
        BackupEncryptionLevel.entries.lastOrNull { isSupported(context, it, ignoreMemoryLimit) }
            ?: BackupEncryptionLevel.STANDARD

    /** Clamps a stored preference to what this phone can actually run. */
    fun clamp(
        context: Context,
        level: BackupEncryptionLevel,
        ignoreMemoryLimit: Boolean = false
    ): BackupEncryptionLevel {
        val highest = highestSupported(context, ignoreMemoryLimit)
        return if (level.ordinal > highest.ordinal) highest else level
    }
}
