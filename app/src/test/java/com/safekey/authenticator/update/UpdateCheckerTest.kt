package com.safekey.authenticator.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun newerPatchWins() {
        assertTrue(UpdateChecker.isNewer("2.3.2", "2.3.1"))
    }

    @Test
    fun newerMinorWins() {
        assertTrue(UpdateChecker.isNewer("2.4.0", "2.3.9"))
    }

    @Test
    fun newerMajorWins() {
        assertTrue(UpdateChecker.isNewer("3.0.0", "2.9.9"))
    }

    @Test
    fun equalIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("2.3.2", "2.3.2"))
    }

    @Test
    fun olderIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("2.3.1", "2.3.2"))
    }

    @Test
    fun shorterVersionPadsWithZero() {
        // 2.4 == 2.4.0, so 2.4 > 2.3.9
        assertTrue(UpdateChecker.isNewer("2.4", "2.3.9"))
        // 2.4 == 2.4.0, not newer
        assertFalse(UpdateChecker.isNewer("2.4", "2.4.0"))
    }

    @Test
    fun fourPartVersionsCompare() {
        assertTrue(UpdateChecker.isNewer("2.3.2.1", "2.3.2"))
        assertFalse(UpdateChecker.isNewer("2.3.2", "2.3.2.1"))
    }

    @Test
    fun suffixedSegmentsKeepTheirPosition() {
        // The fix/build suffix must not shift the remaining segments: dropping
        // it turned 2.5.3-fix1 into [2,5], which never ranked as newer.
        assertTrue(UpdateChecker.isNewer("2.5.3-fix1", "2.5.2"))
        assertFalse(UpdateChecker.isNewer("2.5.2-fix1", "2.5.2"))
        assertTrue(UpdateChecker.isNewer("2.6.0-beta1", "2.5.9"))
        assertFalse(UpdateChecker.isNewer("2.5.1-fix3", "2.5.2"))
    }

    @Test
    fun garbageNeverWins() {
        assertFalse(UpdateChecker.isNewer("abc", "2.3.2"))
        assertFalse(UpdateChecker.isNewer("", "2.3.2"))
        assertFalse(UpdateChecker.isNewer("2.3.x", "2.3.2"))
    }
}
