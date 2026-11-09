package com.safekey.authenticator.backup

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AutoBackupSchedulerTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+8"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun laterTodayStillToday() {
        val now = at(2026, 8, 16, 2, 0)
        assertEquals(at(2026, 8, 16, 3, 0), AutoBackupScheduler.nextRunMillis(now, 3, 0, 1))
    }

    @Test
    fun pastTheTimeRollsToTomorrow() {
        val now = at(2026, 8, 16, 4, 0)
        assertEquals(at(2026, 8, 17, 3, 0), AutoBackupScheduler.nextRunMillis(now, 3, 0, 1))
    }

    @Test
    fun exactlyAtTheTimeRollsOver() {
        val now = at(2026, 8, 16, 3, 0)
        assertEquals(at(2026, 8, 17, 3, 0), AutoBackupScheduler.nextRunMillis(now, 3, 0, 1))
    }

    @Test
    fun intervalStepsDays() {
        val now = at(2026, 8, 16, 4, 0)
        assertEquals(at(2026, 8, 19, 3, 0), AutoBackupScheduler.nextRunMillis(now, 3, 0, 3))
    }

    @Test
    fun longIntervalStillRunsTodayWhenBeforeTime() {
        val now = at(2026, 8, 16, 2, 0)
        assertEquals(at(2026, 8, 16, 3, 0), AutoBackupScheduler.nextRunMillis(now, 3, 0, 7))
    }

    @Test
    fun oddMinuteIsRespected() {
        val now = at(2026, 8, 16, 0, 0)
        assertEquals(at(2026, 8, 16, 23, 45), AutoBackupScheduler.nextRunMillis(now, 23, 45, 1))
    }

    @Test
    fun monthBoundaryRollsOver() {
        val now = at(2026, 8, 31, 23, 0)
        assertEquals(at(2026, 9, 1, 3, 0), AutoBackupScheduler.nextRunMillis(now, 3, 0, 1))
    }

    // ---- DST behaviour (America/New_York) ---------------------------------
    // nextRunMillis resolves candidates through the local zone, so wall-clock
    // times survive DST transitions deterministically.

    @Test
    fun springForwardGapShiftsForwardToExistingTime() {
        // 2026-03-08 02:00 -> 03:00 EDT: 02:30 does not exist that night.
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val zone = ZoneId.of("America/New_York")
        val now = atZ(zone, 2026, 3, 8, 1, 0) // 06:00 UTC (EST)
        // A gap time resolves forward to the first real instant (03:30 EDT).
        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),
            AutoBackupScheduler.nextRunMillis(now, 2, 30, 1)
        )
    }

    @Test
    fun fallBackAmbiguousTimeKeepsEarlierOffset() {
        // 2026-11-01 02:00 -> 01:00 EST: 01:30 occurs twice (EDT then EST).
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val zone = ZoneId.of("America/New_York")
        val now = atZ(zone, 2026, 10, 31, 20, 0) // 00:00 UTC (EDT)
        // The first (EDT, -04:00) 01:30 = 05:30 UTC.
        assertEquals(
            Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),
            AutoBackupScheduler.nextRunMillis(now, 1, 30, 1)
        )
    }

    @Test
    fun dayStepAcrossDstKeepsWallClockHour() {
        // 09:00 on the day after the US spring forward is 09:00 EDT
        // (13:00 UTC), not a naive +24h from yesterday's EST instant.
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val zone = ZoneId.of("America/New_York")
        val now = atZ(zone, 2026, 3, 7, 12, 0) // 09:00 already passed today
        assertEquals(
            Instant.parse("2026-03-08T13:00:00Z").toEpochMilli(),
            AutoBackupScheduler.nextRunMillis(now, 9, 0, 1)
        )
    }

    private fun atZ(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
            .toInstant().toEpochMilli()

    // ---- the configured hour is wall-clock time in the device's own zone ----
    // Regression: the screen used to label the schedule as GMT+8 while the hour
    // the user picks is local, so anyone outside UTC+8 read the wrong time.

    @Test
    fun wallClockTimeIsInterpretedInTheSystemZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val zone = ZoneId.of("America/Los_Angeles")
        val now = atZ(zone, 2026, 8, 16, 2, 0) // 09:00 UTC
        // 10:00 local (PDT, UTC-7) is 17:00 UTC — never a GMT+8 reading.
        assertEquals(
            Instant.parse("2026-08-16T17:00:00Z").toEpochMilli(),
            AutoBackupScheduler.nextRunMillis(now, 10, 0, 1)
        )
    }

    @Test
    fun anExplicitZoneInterpretsTheWallClockTime() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val now = atZ(kolkata, 2026, 8, 16, 2, 0) // 20:30 UTC the day before
        // 03:00 IST (UTC+5:30) = 21:30 UTC.
        assertEquals(
            Instant.parse("2026-08-15T21:30:00Z").toEpochMilli(),
            AutoBackupScheduler.nextRunMillis(now, 3, 0, 1, kolkata)
        )
    }

    @Test
    fun zoneLabelIsDerivedFromTheZone() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val east = AutoBackupScheduler.zoneLabel(ZoneId.of("+05:30"))
            val west = AutoBackupScheduler.zoneLabel(ZoneId.of("-08:00"))
            assertTrue("empty label", east.isNotBlank())
            assertTrue("zones share a label: $east / $west", east != west)
        } finally {
            Locale.setDefault(previous)
        }
    }

    // ---------------------------------------------------- catch-up (isDue)

    private val day = 24L * 3600_000L

    /** Success-only shorthand; error cases call the full signature below. */
    private fun isDue(now: Long, lastRun: Long, intervalDays: Int): Boolean =
        AutoBackupScheduler.isDue(now, lastRun, "", intervalDays, hour = 3, minute = 0)

    @Test
    fun neverRunIsNeverDue() {
        // The pending scheduled job still handles the first run.
        assertFalse(isDue(at(2026, 8, 16, 3, 0), 0L, 1))
        assertFalse(isDue(at(2026, 8, 16, 3, 0), -1L, 1))
    }

    @Test
    fun aRunInsideTheIntervalIsNotDue() {
        val last = at(2026, 8, 16, 3, 0)
        // Twelve hours later the next slot (tomorrow 03:00) is still ahead.
        assertFalse(isDue(last + day / 2, last, 1))
    }

    @Test
    fun theSlotItselfIsDue() {
        // Once the configured time-of-day arrives the run is due, even though
        // exactly one interval has elapsed — the scheduled trigger may have
        // been skipped by the system, and the catch-up is the fallback.
        val last = at(2026, 8, 16, 3, 0)
        assertTrue(isDue(last + day, last, 1))
    }

    @Test
    fun changingTheBackupTimeRetiresTheOldSlot() {
        // The reported bug: the time was moved from 22:15 to 22:20 and the
        // app still backed up at 22:15 (and again at 22:20).
        val lastRun = at(2026, 8, 16, 22, 15)      // yesterday, on the old time
        val changedAt = at(2026, 8, 17, 20, 0)     // moved to 22:20 tonight

        // Catch-up at 22:16 must NOT run: the 22:20 slot is still ahead.
        assertFalse(
            AutoBackupScheduler.isDue(
                at(2026, 8, 17, 22, 16), lastRun, "", 1, hour = 22, minute = 20,
                configChangedAt = changedAt
            )
        )
        // A trigger still armed for the old 22:15 is stale, not a second run.
        assertEquals(
            AutoBackupScheduler.TriggerVerdict.STALE_SLOT,
            AutoBackupScheduler.classifyTrigger(
                at(2026, 8, 17, 22, 15), lastRun, "", hour = 22, minute = 20,
                configChangedAt = changedAt
            )
        )
        // The real slot runs exactly once.
        assertEquals(
            AutoBackupScheduler.TriggerVerdict.RUN,
            AutoBackupScheduler.classifyTrigger(
                at(2026, 8, 17, 22, 20), lastRun, "", hour = 22, minute = 20,
                configChangedAt = changedAt
            )
        )
        assertEquals(
            AutoBackupScheduler.TriggerVerdict.ALREADY_RAN,
            AutoBackupScheduler.classifyTrigger(
                at(2026, 8, 17, 22, 23), at(2026, 8, 17, 22, 20), "", hour = 22,
                minute = 20, configChangedAt = changedAt
            )
        )
        // And the catch-up that follows a genuinely skipped slot still works.
        assertTrue(
            AutoBackupScheduler.isDue(
                at(2026, 8, 18, 22, 30), at(2026, 8, 17, 22, 20), "", 1,
                hour = 22, minute = 20, configChangedAt = changedAt
            )
        )
    }

    @Test
    fun anOverdueRunIsDue() {
        val last = at(2026, 8, 16, 3, 0)
        assertTrue(isDue(last + day + 1, last, 1))
        assertTrue(isDue(last + 5 * day, last, 4))
    }

    @Test
    fun intervalIsClampedToOneDay() {
        // A stored 0 must not make every launch look overdue.
        val last = at(2026, 8, 16, 3, 0)
        assertFalse(isDue(last + day / 2, last, 0))
        assertTrue(isDue(last + day + 1, last, 0))
    }

    @Test
    fun aFailedRunIsRetriedAtTheNextOpportunity() {
        // One bad night (a skipped or failed unattended run) must not cost a
        // whole interval: the next app start retries it.
        val failed = at(2026, 8, 16, 3, 0)
        assertFalse(
            AutoBackupScheduler.isDue(
                failed + 5 * 60_000L, failed, "INTEGRITY_UNKNOWN", 1, hour = 3, minute = 0
            )
        )
        assertTrue(
            AutoBackupScheduler.isDue(
                failed + AutoBackupScheduler.RETRY_GAP_MS,
                failed,
                "INTEGRITY_UNKNOWN",
                1,
                hour = 3,
                minute = 0
            )
        )
        // Works beyond the interval too: a stale failure is still a retry.
        assertTrue(
            AutoBackupScheduler.isDue(
                failed + day, failed, "IOException: x", 7, hour = 3, minute = 0
            )
        )
    }

    @Test
    fun aClockSkewedIntoTheFutureIsNotDue() {
        val last = at(2026, 8, 16, 3, 0)
        assertFalse(isDue(last - day, last, 1))
    }

    // ------------------------------------------- duplicate-trigger guard

    private fun verdict(now: Long, lastRun: Long, lastError: String = ""): String =
        AutoBackupScheduler.classifyTrigger(
            now, lastRun, lastError, hour = 3, minute = 0
        ).name

    @Test
    fun aSuccessForTheSlotAbsorbsTheSecondTrigger() {
        // The alarm and the WorkManager job both aim at one slot; whichever
        // fires second must not write another file.
        assertEquals("ALREADY_RAN", verdict(at(2026, 8, 16, 3, 5), at(2026, 8, 16, 3, 0)))
        // A late delivery hours after the slot still belongs to that slot.
        assertEquals("ALREADY_RAN", verdict(at(2026, 8, 16, 7, 30), at(2026, 8, 16, 3, 0)))
    }

    @Test
    fun aFailureForTheSlotIsRetriedNotAbsorbed() {
        assertEquals(
            "RUN",
            verdict(at(2026, 8, 16, 3, 5), at(2026, 8, 16, 3, 0), "IOException: x")
        )
    }

    @Test
    fun aSuccessFromAnOlderSlotDoesNotAbsorbTheNewOne() {
        // Yesterday's run does not cover today's slot — a late WorkManager job
        // a whole slot behind must not swallow the run the user expects.
        assertEquals("RUN", verdict(at(2026, 8, 16, 3, 0), at(2026, 8, 15, 3, 0)))
        // A manual run just *before* the slot is not a run for that slot
        // either: whoever sets the time a couple of minutes ahead still gets
        // their run (the old window-based guard swallowed it).
        assertEquals("RUN", verdict(at(2026, 8, 16, 3, 5), at(2026, 8, 16, 2, 58)))
    }

    @Test
    fun neverRunOrClockRollbackStillRuns() {
        val now = at(2026, 8, 16, 3, 0)
        assertEquals("RUN", verdict(now, 0L))
        // A timestamp in the future (clock rolled back) covers nothing.
        assertEquals("RUN", verdict(now, now + day))
    }
}
