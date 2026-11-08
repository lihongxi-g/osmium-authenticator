package com.safekey.authenticator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weight slider stores an absolute body weight and every stop maps to a
 * font file that actually ships, which is what makes the change visible.
 *
 * Lora is retired as a bundled face (2.6.4.1 was the last version to ship it),
 * but its stored key survives: installs that picked it must land on Literata
 * with Literata's range, not silently lose their serif interface.
 */
class FontWeightTest {

    @Test
    fun `ceiling matches the heaviest file of each face`() {
        assertEquals(900, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_LITERATA))
        assertEquals(400, AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_SYSTEM))
    }

    @Test
    fun `the retired Lora key inherits the range of the face it resolves to`() {
        assertEquals(
            AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_LITERATA),
            AppSettings.fontWeightCeiling(AppSettings.FONT_FAMILY_LORA)
        )
        assertEquals(900, AppSettings.clampFontWeightBody(900, AppSettings.FONT_FAMILY_LORA))
    }

    @Test
    fun `every face offers one stop per hundred from 400 to its ceiling`() {
        assertEquals(
            listOf(400, 500, 600, 700, 800, 900),
            AppSettings.fontWeightStops(AppSettings.FONT_FAMILY_LITERATA)
        )
        assertEquals(listOf(400), AppSettings.fontWeightStops(AppSettings.FONT_FAMILY_SYSTEM))
        assertEquals(
            AppSettings.fontWeightStops(AppSettings.FONT_FAMILY_LITERATA),
            AppSettings.fontWeightStops(AppSettings.FONT_FAMILY_LORA)
        )
    }

    @Test
    fun `delta is zero at the baseline and reaches the ceiling`() {
        assertEquals(0, AppSettings.fontWeightDelta(400, AppSettings.FONT_FAMILY_LITERATA))
        assertEquals(300, AppSettings.fontWeightDelta(700, AppSettings.FONT_FAMILY_LITERATA))
        assertEquals(500, AppSettings.fontWeightDelta(900, AppSettings.FONT_FAMILY_LITERATA))
    }

    @Test
    fun `a weight above the face ceiling is clamped, not lost`() {
        assertEquals(900, AppSettings.clampFontWeightBody(900, AppSettings.FONT_FAMILY_LITERATA))
        assertEquals(0, AppSettings.fontWeightDelta(900, AppSettings.FONT_FAMILY_SYSTEM))
    }

    @Test
    fun `raw slider values snap onto the stop grid`() {
        assertEquals(500, AppSettings.snapFontWeightBody(540, AppSettings.FONT_FAMILY_LITERATA))
        assertEquals(600, AppSettings.snapFontWeightBody(560, AppSettings.FONT_FAMILY_LITERATA))
        assertEquals(900, AppSettings.snapFontWeightBody(1200, AppSettings.FONT_FAMILY_LITERATA))
        assertEquals(400, AppSettings.snapFontWeightBody(300, AppSettings.FONT_FAMILY_LITERATA))
    }

    @Test
    fun `the token hierarchy never inverts`() {
        listOf(
            AppSettings.FONT_FAMILY_LITERATA,
            // Retired key, same range as Literata — it must not invert either.
            AppSettings.FONT_FAMILY_LORA
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
