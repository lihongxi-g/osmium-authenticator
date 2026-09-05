package com.safekey.authenticator.backup

import android.content.Context
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.safekey.authenticator.data.AppSettings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Scheduling for the unattended automatic backup.
 *
 * A OneTimeWorkRequest is scheduled for the next occurrence of the configured
 * time-of-day (stepping by the interval in days). The worker re-schedules the
 * next run when it finishes, so the time-of-day stays accurate instead of
 * drifting like a plain PeriodicWorkRequest would. WorkManager persists the
 * work across reboots and Doze — no custom alarm plumbing.
 */
object AutoBackupScheduler {

    const val WORK_NAME = "osmium-auto-backup"

    /**
     * Next occurrence of [hour]:[minute] strictly after [now], stepping by
     * [intervalDays] days. Pure function — unit-tested without Android.
     *
     * Resolves every candidate through the local zone (java.time), so DST
     * transitions cannot skew the wall-clock time: a nonexistent time in a
     * spring-forward gap shifts forward, an ambiguous fall-back time keeps
     * its earlier offset, and day steps preserve the wall-clock hour.
     */
    fun nextRunMillis(now: Long, hour: Int, minute: Int, intervalDays: Int): Long {
        val zone = ZoneId.systemDefault()
        val nowZoned = Instant.ofEpochMilli(now).atZone(zone)
        var candidate = ZonedDateTime.of(
            nowZoned.toLocalDate().atTime(hour, minute), zone
        )
        if (candidate.toInstant().toEpochMilli() <= now) {
            candidate = candidate.plusDays(intervalDays.coerceAtLeast(1).toLong())
        }
        return candidate.toInstant().toEpochMilli()
    }

    /**
     * (Re)schedule the backup for the next occurrence. When auto-backup is
     * disabled this cancels any pending work instead.
     */
    fun schedule(context: Context, settings: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (!settings.autoBackupEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = System.currentTimeMillis()
        val next = nextRunMillis(
            now = now,
            hour = settings.autoBackupHour,
            minute = settings.autoBackupMinute,
            intervalDays = settings.autoBackupIntervalDays
        )
        val delay = (next - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Ensure a schedule exists without disturbing a pending one. */
    fun ensureScheduled(context: Context, settings: AppSettings) {
        if (!settings.autoBackupEnabled) return
        val wm = WorkManager.getInstance(context)
        val now = System.currentTimeMillis()
        val next = nextRunMillis(
            now = now,
            hour = settings.autoBackupHour,
            minute = settings.autoBackupMinute,
            intervalDays = settings.autoBackupIntervalDays
        )
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay((next - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Trigger a backup right now (the worker re-schedules the next run). */
    fun runNow(context: Context) {
        val builder = OneTimeWorkRequestBuilder<AutoBackupWorker>()
        if (Build.VERSION.SDK_INT >= 31) {
            // Expedited on Android 8–11 runs as a foreground service and
            // REQUIRES the worker to implement getForegroundInfo() — without
            // it the work fails with IllegalStateException. Gate expedited to
            // Android 12+; older devices run a plain (still immediate) work
            // request, which is fine because runNow only fires while the app
            // is in the foreground anyway.
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            builder.build()
        )
    }

    /**
     * Catch-up for frozen schedules: when the last successful run is older
     * than the configured interval, OEM battery policies have been blocking
     * the scheduled job — run one now. Never fires when no run has happened
     * yet (the pending scheduled job still handles the first run).
     */
    fun maybeCatchUp(context: Context, settings: AppSettings) {
        if (!settings.autoBackupEnabled) return
        val last = settings.autoBackupLastTime
        if (last <= 0L) return
        val intervalMs = settings.autoBackupIntervalDays * 24L * 3600_000L
        if (System.currentTimeMillis() - last > intervalMs) {
            runNow(context)
        }
    }
}
