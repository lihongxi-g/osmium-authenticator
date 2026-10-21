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
        AutoBackupScheduler.isDue(now, lastRun, "", intervalDays)

    @Test
    fun neverRunIsNeverDue() {
        // The pending scheduled job still handles the first run.
        assertFalse(isDue(at(2026, 8, 16, 3, 0), 0L, 1))
        assertFalse(isDue(at(2026, 8, 16, 3, 0), -1L, 1))
    }

    @Test
    fun aRunInsideTheIntervalIsNotDue() {
        val last = at(2026, 8, 16, 3, 0)
        assertFalse(isDue(last + day / 2, last, 1))
        // Exactly on the boundary is still on time (strictly greater).
        assertFalse(isDue(last + day, last, 1))
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
            AutoBackupScheduler.isDue(failed + 5 * 60_000L, failed, "INTEGRITY_UNKNOWN", 1)
        )
        assertTrue(
            AutoBackupScheduler.isDue(
                failed + AutoBackupScheduler.RETRY_GAP_MS, failed, "INTEGRITY_UNKNOWN", 1
            )
        )
        // Works beyond the interval too: a stale failure is still a retry.
        assertTrue(AutoBackupScheduler.isDue(failed + day, failed, "IOException: x", 7))
    }

    @Test
    fun aClockSkewedIntoTheFutureIsNotDue() {
        val last = at(2026, 8, 16, 3, 0)
        assertFalse(isDue(last - day, last, 1))
    }

    // ------------------------------------------- duplicate-trigger guard

    @Test
    fun aRecentSuccessAbsorbsTheSecondTrigger() {
        // The alarm and the WorkManager job both aim at one slot; whichever
        // fires second must not write another file.
        val now = at(2026, 8, 16, 3, 5)
        assertTrue(
            AutoBackupScheduler.isRecentSuccess(now, at(2026, 8, 16, 3, 0), "")
        )
        assertTrue(
            AutoBackupScheduler.isRecentSuccess(now, at(2026, 8, 16, 2, 50), "")
        )
    }

    @Test
    fun aRecentFailureIsRetriedNotAbsorbed() {
        val now = at(2026, 8, 16, 3, 5)
        assertFalse(
            AutoBackupScheduler.isRecentSuccess(now, at(2026, 8, 16, 3, 0), "IOException: x")
        )
    }

    @Test
    fun aSuccessOlderThanTheWindowIsNotAbsorbed() {
        // A late WorkManager run after a successful alarm run is a harmless
        // second file; a newly chosen backup time must still run.
        val now = at(2026, 8, 16, 6, 0)
        assertFalse(
            AutoBackupScheduler.isRecentSuccess(now, at(2026, 8, 16, 3, 0), "")
        )
        // Exactly on the boundary is no longer "recent".
        assertFalse(
            AutoBackupScheduler.isRecentSuccess(
                now, now - AutoBackupScheduler.RECENT_SUCCESS_WINDOW_MS, ""
            )
        )
    }

    @Test
    fun neverRunOrClockRollbackIsNotAbsorbed() {
        val now = at(2026, 8, 16, 3, 0)
        assertFalse(AutoBackupScheduler.isRecentSuccess(now, 0L, ""))
        assertFalse(AutoBackupScheduler.isRecentSuccess(now, now + day, ""))
    }
}
