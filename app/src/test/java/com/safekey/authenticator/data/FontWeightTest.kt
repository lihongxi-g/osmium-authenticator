package com.safekey.authenticator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weight slider is a percentage of each face's own range. */
class FontWeightTest {

    @Test
    fun `ceiling differs per bundled face`() {
        assertEquals(700, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_LORA))
        assertEquals(800, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_MANROPE))
        assertEquals(900, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_LITERATA))
        assertEquals(700, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_HAN_SERIF))
        assertEquals(400, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_SYSTEM))
    }

    @Test
    fun `zero percent keeps the design weights`() {
        listOf(700, 800, 900).forEach { ceiling ->
            assertEquals(400, AppSettings.bodyWeightAt(0, ceiling))
            assertEquals(500, AppSettings.fontWeightAt(500, 0, ceiling))
            assertEquals(700, AppSettings.fontWeightAt(700, 0, ceiling))
        }
    }

    @Test
    fun `hundred percent reaches the ceiling on every face`() {
        listOf(700, 800, 900).forEach { ceiling ->
            assertEquals(ceiling, AppSettings.bodyWeightAt(100, ceiling))
            assertEquals(ceiling, AppSettings.fontWeightAt(600, 100, ceiling))
        }
    }

    @Test
    fun `delta is zero at rest and reaches the ceiling at full`() {
        listOf(700, 800, 900).forEach { ceiling ->
            assertEquals(0, AppSettings.fontWeightDelta(0, ceiling))
            assertEquals(ceiling - 400, AppSettings.fontWeightDelta(100, ceiling))
        }
    }

    @Test
    fun `the hierarchy never inverts`() {
        listOf(0, 25, 50, 75, 100).forEach { percent ->
            listOf(700, 800, 900).forEach { ceiling ->
                val body = AppSettings.fontWeightAt(400, percent, ceiling)
                val title = AppSettings.fontWeightAt(600, percent, ceiling)
                assertTrue("body $body should not exceed title $title", body <= title)
            }
        }
    }

    @Test
    fun `out of range percentages are clamped`() {
        assertEquals(400, AppSettings.bodyWeightAt(-50, 900))
        assertEquals(900, AppSettings.bodyWeightAt(500, 900))
        assertEquals(0, AppSettings.snapFontWeightPercent(-10))
        assertEquals(100, AppSettings.snapFontWeightPercent(120))
        assertEquals(50, AppSettings.snapFontWeightPercent(52))
    }
}
