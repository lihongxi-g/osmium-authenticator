package com.safekey.authenticator.update

import org.junit.Assert.assertEquals
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

    // ------------------------------------------------- release notes (update dialog)

    @Test
    fun releaseNotes_dropTheMarkdownSyntax() {
        assertEquals("Osmium v2.5.2", ReleaseNotes.clean("# Osmium v2.5.2"))
        assertEquals("Fixed a bug", ReleaseNotes.clean("**Fixed** a bug"))
        assertEquals("a link", ReleaseNotes.clean("[a link](https://example.com)"))
        assertEquals("quoted", ReleaseNotes.clean("> quoted"))
        assertEquals("• one\n• two", ReleaseNotes.clean("- one\n- two"))
        assertEquals("code", ReleaseNotes.clean("`code`"))
    }

    @Test
    fun releaseNotes_keepParagraphsAndDropBlankRuns() {
        assertEquals("a\n\nb", ReleaseNotes.clean("a\r\n\r\n\r\nb"))
        assertEquals("text", ReleaseNotes.clean("  \n\n text \n "))
        assertEquals("", ReleaseNotes.clean("   "))
    }

    @Test
    fun releaseNotes_capAbsurdLength() {
        val cleaned = ReleaseNotes.clean("x".repeat(20_000))
        assertTrue(cleaned.length < 5_000)
        assertTrue(cleaned.endsWith("…"))
    }
}
