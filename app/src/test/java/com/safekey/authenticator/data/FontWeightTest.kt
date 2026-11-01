package com.safekey.authenticator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weight slider stores an absolute body weight and every stop maps to a
 * font file that actually ships, which is what makes the change visible.
 */
class FontWeightTest {

    @Test
    fun `ceiling matches the heaviest file of each face`() {
        assertEquals(700, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_LORA))
        assertEquals(800, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_MANROPE))
        assertEquals(900, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_LITERATA))
        assertEquals(400, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_SYSTEM))
    }

    @Test
    fun `every face offers one stop per hundred from 400 to its ceiling`() {
        assertEquals(listOf(400, 500, 600, 700), AppSettings.fontWeightStops(AppSettings.FONT_FAMILY_LORA))
        assertEquals(listOf(400, 500, 600, 700, 800), AppSettings.fontWeightStops(AppSettings.FONT_FAMILY_MANROPE))
        assertEquals(
            listOf(400, 500, 600, 700, 800, 900),
            AppSettings.fontWeightStops(AppSettings.FONT_FAMILY_LITERATA)
        )
        assertEquals(listOf(400), AppSettings.fontWeightStops(AppSettings.FONT_FAMILY_SYSTEM))
    }

    @Test
    fun `delta is zero at the baseline and reaches the ceiling`() {
        assertEquals(0, AppSettings.fontWeightDelta(400, AppSettings.FONT_FAMILY_LORA))
        assertEquals(300, AppSettings.fontWeightDelta(700, AppSettings.FONT_FAMILY_LORA))
        assertEquals(400, AppSettings.fontWeightDelta(800, AppSettings.FONT_FAMILY_MANROPE))
        assertEquals(500, AppSettings.fontWeightDelta(900, AppSettings.FONT_FAMILY_LITERATA))
    }

    @Test
    fun `a weight above the face ceiling is clamped, not lost`() {
        assertEquals(700, AppSettings.clampFontWeightBody(900, AppSettings.FONT_FAMILY_LORA))
        assertEquals(300, AppSettings.fontWeightDelta(900, AppSettings.FONT_FAMILY_LORA))
        assertEquals(0, AppSettings.fontWeightDelta(900, AppSettings.FONT_FAMILY_SYSTEM))
    }

    @Test
    fun `raw slider values snap onto the stop grid`() {
        assertEquals(500, AppSettings.snapFontWeightBody(540, AppSettings.FONT_FAMILY_LORA))
        assertEquals(600, AppSettings.snapFontWeightBody(560, AppSettings.FONT_FAMILY_LORA))
        assertEquals(700, AppSettings.snapFontWeightBody(900, AppSettings.FONT_FAMILY_LORA))
        assertEquals(400, AppSettings.snapFontWeightBody(300, AppSettings.FONT_FAMILY_LORA))
    }

    @Test
    fun `the token hierarchy never inverts`() {
        listOf(
            AppSettings.FONT_FAMILY_LORA,
            AppSettings.FONT_FAMILY_MANROPE,
            AppSettings.FONT_FAMILY_LITERATA
        ).forEach { key ->
            AppSettings.fontWeightStops(key).forEach { body ->
                val delta = AppSettings.fontWeightDelta(body, key)
                val bodyToken = 400 + delta
                val titleToken = (600 + delta).coerceAtMost(900)
                assertTrue("body $bodyToken should not exceed title $titleToken", bodyToken <= titleToken)
            }
        }
    }
}
