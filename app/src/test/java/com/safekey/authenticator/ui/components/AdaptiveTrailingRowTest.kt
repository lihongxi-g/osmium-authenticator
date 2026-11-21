package com.safekey.authenticator.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTrailingRowTest {
    @Test
    fun longPermissionStatusMovesBelowInsteadOfLeavingOnlyAFewPixelsForTheDescription() {
        // Representative widths at 360 dp: the 244 px status would otherwise leave 4 px.
        assertTrue(shouldStackTrailing(width = 256, gap = 8, mainMinWidth = 57, trailingWidth = 244))
    }

    @Test
    fun shortStatusAndSwitchStayInline() {
        assertFalse(shouldStackTrailing(width = 256, gap = 8, mainMinWidth = 57, trailingWidth = 53))
        assertFalse(shouldStackTrailing(width = 256, gap = 8, mainMinWidth = 57, trailingWidth = 52))
    }

    @Test
    fun primaryTextGetsAtLeastHalfTheWidthEvenIfItsWordsCouldBreakEarlier() {
        assertFalse(shouldStackTrailing(width = 256, gap = 8, mainMinWidth = 57, trailingWidth = 120))
        assertTrue(shouldStackTrailing(width = 256, gap = 8, mainMinWidth = 57, trailingWidth = 121))
    }

    @Test
    fun longestWordAlsoFitsWhenTrailingItemIsInline() {
        assertFalse(shouldStackTrailing(width = 256, gap = 8, mainMinWidth = 196, trailingWidth = 52))
        assertTrue(shouldStackTrailing(width = 256, gap = 8, mainMinWidth = 197, trailingWidth = 52))
    }

    @Test
    fun longActionStacksOnANarrowScreenButFitsOnAWideOne() {
        assertTrue(shouldStackTrailing(width = 240, gap = 8, mainMinWidth = 92, trailingWidth = 208))
        assertFalse(shouldStackTrailing(width = 480, gap = 8, mainMinWidth = 92, trailingWidth = 208))
    }

    @Test
    fun emptyTrailingContentDoesNotCauseTheMainTextToLoseSpace() {
        assertFalse(shouldStackTrailing(width = 120, gap = 8, mainMinWidth = 160, trailingWidth = 0))
    }
}