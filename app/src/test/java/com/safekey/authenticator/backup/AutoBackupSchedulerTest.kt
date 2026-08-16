package com.safekey.authenticator.backup

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
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
}
