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

    /** Floor between a failed attempt and the catch-up retry that follows it. */
    const val RETRY_GAP_MS = 15L * 60_000L

    /**
     * Most recent occurrence of [hour]:[minute] at or before [now] — the slot a
     * trigger belongs to.
     *
     * This is what makes dedupe slot-based instead of window-based: a success
     * at or after this instant already produced the file for the slot, so the
     * second trigger (alarm vs job) must not write another one. A fixed
     * "recent success" window got it wrong in both directions — a backup time
     * the user moved inside the window was swallowed, and a stale trigger
     * armed for an old time was not absorbed at all.
     */
    fun slotStartMillis(
        now: Long,
        hour: Int,
        minute: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val nowZoned = Instant.ofEpochMilli(now).atZone(zone)
        val candidate = ZonedDateTime.of(nowZoned.toLocalDate().atTime(hour, minute), zone)
        return if (candidate.toInstant().toEpochMilli() > now) {
            candidate.minusDays(1).toInstant().toEpochMilli()
        } else {
            candidate.toInstant().toEpochMilli()
        }
    }

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

    /**
     * Ensure a schedule exists without disturbing a pending one.
     *
     * `KEEP` on purpose: replacing would cancel a run that is in flight when
     * the app comes to the foreground, and a half-written backup is worse than
     * a slightly early one. A job left armed for a time the user has since
     * changed is not harmless by luck — [classifyTrigger] drops it as
     * [TriggerVerdict.STALE_SLOT], and its own `finally` re-arms the current
     * target, so the schedule converges either way.
     */
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
     * True when the caller should run a catch-up backup now.
     *
     * Anchored to the configured **slot**, not to "interval since the last
     * run". That distinction was a real bug: with `now - lastRun > interval`,
     * the catch-up fired as soon as 24 hours had passed since the last success
     * — i.e. at whatever time of day the user happened to open the app. The
     * user moved the backup time from 22:15 to 22:20 and still got a run at
     * 22:15, because opening the app at 22:15 was already "overdue".
     *
     * Two cases are due:
     *
     *  • the last run **succeeded** and the next occurrence of [hour]:[minute]
     *    after it — or after the last schedule change, whichever is later — has
     *    already passed: the system scheduler let the slot go;
     *  • the last run **failed or was skipped** ([lastError] non-empty) — an
     *    unattended attempt that produced nothing must be retried at the first
     *    opportunity (the next app start) instead of waiting a whole interval.
     *    The [retryGapMs] floor keeps a launch loop from hammering a broken
     *    path.
     *
     * Never true when no run has happened yet — the first scheduled run still
     * owns that. Pure function, unit-tested.
     */
    fun isDue(
        now: Long,
        lastRun: Long,
        lastError: String,
        intervalDays: Int,
        hour: Int,
        minute: Int,
        /** When the user last changed the schedule (0 = never). */
        configChangedAt: Long = 0L,
        retryGapMs: Long = RETRY_GAP_MS,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (lastRun <= 0L) return false
        if (lastError.isNotEmpty()) return now - lastRun >= retryGapMs
        // The clock starts at whichever came last: the previous run, or the
        // moment the user moved the time. Anchoring on the run alone made a
        // rescheduled night look served — a 22:20 schedule inherited from a
        // 22:15 run is due at 22:15 the next day, which is the off-time backup
        // this whole anchoring exists to stop.
        val anchor = maxOf(lastRun, configChangedAt)
        return now >= nextRunMillis(anchor, hour, minute, intervalDays, zone)
    }

    /** What a trigger that just fired should do. */
    enum class TriggerVerdict {
        /** Run the backup. */
        RUN,

        /** This slot already produced a backup — the other trigger got there first. */
        ALREADY_RAN,

        /**
         * The trigger was armed for a time the user has since changed (pending
         * work survives an app update); the new schedule owns the run, so this
         * one must not write a file at the old time.
         */
        STALE_SLOT
    }

    /**
     * Decide what the trigger that fired at [now] should do.
     *
     * Slot-based rather than "within N minutes of the last success" on purpose
     * (see [slotStartMillis]): the window version had to let "the user just
     * changed the schedule" through, and that escape hatch is what produced two
     * backups at two times — the catch-up run at the old time plus the newly
     * armed run at the new one.
     *
     * A failed run ([lastError] non-empty) never counts as covering a slot: the
     * other trigger may retry it. A success timestamped in the future (clock
     * rolled back) does not count either. Pure function, unit-tested.
     */
    fun classifyTrigger(
        now: Long,
        lastRun: Long,
        lastError: String,
        hour: Int,
        minute: Int,
        configChangedAt: Long = 0L,
        zone: ZoneId = ZoneId.systemDefault()
    ): TriggerVerdict {
        val slot = slotStartMillis(now, hour, minute, zone)
        if (slot < configChangedAt) return TriggerVerdict.STALE_SLOT
        val covered = lastRun > 0L && lastRun <= now && lastError.isEmpty() && lastRun >= slot
        return if (covered) TriggerVerdict.ALREADY_RAN else TriggerVerdict.RUN
    }
}
