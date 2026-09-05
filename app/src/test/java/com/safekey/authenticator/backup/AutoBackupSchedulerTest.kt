package com.safekey.authenticator.backup

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
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
}
