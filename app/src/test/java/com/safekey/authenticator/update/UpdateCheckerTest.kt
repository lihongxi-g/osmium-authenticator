package com.safekey.authenticator.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun releasedBuildOutranksItsOwnPreview() {
        // A user testing "2.6.1-beta" must be offered the final 2.6.1: the
        // numeric-only compare treated them as equal and never offered it.
        assertTrue(UpdateChecker.isNewer("2.6.1", "2.6.1-beta"))
        assertFalse(UpdateChecker.isNewer("2.6.1-beta", "2.6.1"))
        assertFalse(UpdateChecker.isNewer("2.6.1-beta", "2.6.1-beta"))
        // A preview of the next version is still newer than the released one.
        assertTrue(UpdateChecker.isNewer("2.6.2-beta", "2.6.1"))
    }

    @Test
    fun previewSuffixesStillOrder() {
        assertTrue(UpdateChecker.isNewer("2.6.1-beta1", "2.6.1-beta"))
        assertFalse(UpdateChecker.isNewer("2.6.1-beta", "2.6.1-beta1"))
        assertTrue(UpdateChecker.isNewer("2.6.1-beta", "2.6.1-alpha"))
        // Pre-release identifiers compare as text, as semver prescribes.
        assertFalse(UpdateChecker.isNewer("2.6.1-beta10", "2.6.1-beta2"))
    }

    @Test
    fun garbageNeverWins() {
        assertFalse(UpdateChecker.isNewer("abc", "2.3.2"))
        assertFalse(UpdateChecker.isNewer("", "2.3.2"))
        assertFalse(UpdateChecker.isNewer("2.3.x", "2.3.2"))
    }

    // ---------------------------------------------------- update channels

    private fun candidate(tag: String, preview: Boolean = false) =
        UpdateCandidate(tag = tag, notes = "", url = "https://example.com/$tag", isPreview = preview)

    @Test
    fun previewChannelOffIgnoresPreReleases() {
        val releases = listOf(candidate("v2.6.0"), candidate("v2.6.1-beta", preview = true))
        assertEquals(
            "v2.6.0",
            UpdateChecker.newestForChannels(releases, includeStable = true, includePreview = false)?.tag
        )
        assertEquals(
            "v2.6.1-beta",
            UpdateChecker.newestForChannels(releases, includeStable = true, includePreview = true)?.tag
        )
        // Preview only: the stable release is not offered.
        assertEquals(
            "v2.6.1-beta",
            UpdateChecker.newestForChannels(releases, includeStable = false, includePreview = true)?.tag
        )
        assertNull(UpdateChecker.newestForChannels(releases, includeStable = false, includePreview = false))
        assertNull(UpdateChecker.newestForChannels(emptyList(), includeStable = true, includePreview = true))
    }

    @Test
    fun newestVersionWinsAcrossChannels() {
        // An older pre-release must not shadow a newer stable release.
        val releases = listOf(
            candidate("v2.6.2"),
            candidate("v2.6.1-beta", preview = true),
            candidate("v2.5.9", preview = true)
        )
        val newest = UpdateChecker.newestForChannels(
            releases, includeStable = true, includePreview = true
        )
        assertEquals("v2.6.2", newest?.tag)
        assertFalse(newest?.isPreview ?: true)
    }

    @Test
    fun channelChoiceKeepsTheNotesOfTheChosenRelease() {
        val releases = listOf(
            candidate("v2.6.0"),
            UpdateCandidate("v2.6.1-beta", "beta notes", "https://example.com/beta", isPreview = true)
        )
        val newest = UpdateChecker.newestForChannels(
            releases, includeStable = true, includePreview = true
        )
        assertEquals("beta notes", newest?.notes)
        assertTrue(newest?.isPreview ?: false)
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
