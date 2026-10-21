package com.safekey.authenticator.backup

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.security.AppLog
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
     * Two triggers for one slot (alarm + job) must not write two files. Kept
     * short on purpose: a wider window would also swallow a run the user just
     * asked for by moving the backup time to "in a minute".
     */
    const val RECENT_SUCCESS_WINDOW_MS = 30L * 60_000L

    /** Floor between a failed attempt and the catch-up retry that follows it. */
    const val RETRY_GAP_MS = 15L * 60_000L

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
     * (Re)schedule the backup for the next occurrence: a WorkManager job (it
     * survives reboots and runs in Doze maintenance windows) plus an
     * AlarmManager wake-up ([AutoBackupAlarm], the one that actually fires at
     * night on phones that freeze background work). Both feed the same
     * [AutoBackupRunner] and a duplicate trigger is absorbed by
     * [AutoBackupRunner.runScheduled].
     *
     * When auto-backup is disabled both are cancelled.
     */
    fun schedule(context: Context, settings: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (!settings.autoBackupEnabled) {
            AppLog.d("auto-backup schedule: disabled → work+alarm cancelled")
            wm.cancelUniqueWork(WORK_NAME)
            AutoBackupAlarm.cancel(context)
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
        AppLog.d(
            "auto-backup schedule: ${settings.autoBackupHour}:" +
                settings.autoBackupMinute.toString().padStart(2, '0') +
                " every ${settings.autoBackupIntervalDays}d, in ${delay / 60_000} min"
        )
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        AutoBackupAlarm.schedule(context, settings)
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
        // Alarms do not survive reboots, app updates or a clock change, so this
        // runs on every start (cheap and idempotent: same PendingIntent).
        AutoBackupAlarm.schedule(context, settings)
    }

    /**
     * True when the caller should run a catch-up backup now. Two cases:
     *
     *  • the last run **succeeded** and lies further back than the interval —
     *    the system scheduler let the slot pass (OEM battery policies, or a
     *    WorkManager job that was never delivered);
     *  • the last run **failed or was skipped** ([lastError] non-empty) — an
     *    unattended attempt that produced nothing must be retried at the first
     *    opportunity (the next app start) instead of waiting a whole interval,
     *    which is what made a single bad night look like a dead feature. The
     *    [retryGapMs] floor keeps a launch loop from hammering a broken path.
     *
     * Never true when no run has happened yet — the first scheduled run still
     * owns that. Pure function, unit-tested.
     */
    fun isDue(
        now: Long,
        lastRun: Long,
        lastError: String,
        intervalDays: Int,
        retryGapMs: Long = RETRY_GAP_MS
    ): Boolean {
        if (lastRun <= 0L) return false
        if (lastError.isNotEmpty()) return now - lastRun >= retryGapMs
        val intervalMs = intervalDays.coerceAtLeast(1) * 24L * 3600_000L
        return now - lastRun > intervalMs
    }

    /**
     * True when the last run already succeeded inside [windowMs]: the alarm and
     * the WorkManager job target the same scheduled time and either may fire
     * late, so the second trigger must not write a second file for the same
     * slot. A failed run does not count — the other trigger may retry it.
     * Pure function, unit-tested.
     */
    fun isRecentSuccess(
        now: Long,
        lastRun: Long,
        lastError: String,
        windowMs: Long = RECENT_SUCCESS_WINDOW_MS
    ): Boolean =
        lastRun > 0L && lastError.isEmpty() && now >= lastRun && now - lastRun < windowMs
}
