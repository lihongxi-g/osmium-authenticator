package com.safekey.authenticator.backup

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.safekey.authenticator.data.AppSettings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
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
     * [hour]:[minute] is wall-clock time in [zone] (the device's own zone by
     * default) — the same zone the UI labels the schedule with, so a user
     * outside the developer's timezone reads the time they actually picked.
     * Every candidate is resolved through java.time, so DST transitions cannot
     * skew it: a nonexistent time in a spring-forward gap shifts forward, an
     * ambiguous fall-back time keeps its earlier offset, and day steps preserve
     * the wall-clock hour.
     */
    fun nextRunMillis(
        now: Long,
        hour: Int,
        minute: Int,
        intervalDays: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
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
     * Short, user-visible name of [zone] (an offset like "GMT+05:30" or a
     * regional abbreviation) used to label the scheduled and last-run times.
     * Derived from the zone itself — a fixed label would be wrong for every
     * user in another timezone.
     */
    fun zoneLabel(zone: ZoneId = ZoneId.systemDefault()): String =
        zone.getDisplayName(TextStyle.SHORT, Locale.getDefault())

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

    /**
     * True when the schedule is overdue by more than one interval: the system
     * scheduler let it freeze (OEM battery policies), or WorkManager never
     * executed the pending job. The caller then runs the backup in-process
     * instead of waiting for the system to get around to it.
     *
     * Never true when no run has happened yet — the pending scheduled job
     * still handles the first run. Pure function, unit-tested.
     */
    fun isDue(now: Long, lastRun: Long, intervalDays: Int): Boolean {
        if (lastRun <= 0L) return false
        val intervalMs = intervalDays.coerceAtLeast(1) * 24L * 3600_000L
        return now - lastRun > intervalMs
    }
}
