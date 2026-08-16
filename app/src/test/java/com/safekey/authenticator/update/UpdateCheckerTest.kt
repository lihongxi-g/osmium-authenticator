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
    fun garbageNeverWins() {
        assertFalse(UpdateChecker.isNewer("abc", "2.3.2"))
        assertFalse(UpdateChecker.isNewer("", "2.3.2"))
        assertFalse(UpdateChecker.isNewer("2.3.x", "2.3.2"))
    }
}
