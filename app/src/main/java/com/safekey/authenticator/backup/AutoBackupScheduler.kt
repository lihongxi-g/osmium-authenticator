package com.safekey.authenticator.backup

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.safekey.authenticator.data.AppSettings
import java.util.Calendar
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
     */
    fun nextRunMillis(now: Long, hour: Int, minute: Int, intervalDays: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        var candidate = cal.timeInMillis
        if (candidate <= now) {
            cal.add(Calendar.DAY_OF_YEAR, intervalDays.coerceAtLeast(1))
            candidate = cal.timeInMillis
        }
        return candidate
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

    /** Trigger a backup right now (the worker re-schedules the next run).
     *  Expedited: no initial delay is set, so the request is eligible for
     *  high-priority execution. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
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
