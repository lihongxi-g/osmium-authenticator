package com.safekey.authenticator.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Text-size steps and the combined-scale ceiling. */
class FontScaleTest {

    @Test
    fun `steps map to their multipliers`() {
        assertEquals(0.9f, AppSettings.fontScaleOf(AppSettings.FONT_SMALL), 0.0001f)
        assertEquals(1.0f, AppSettings.fontScaleOf(AppSettings.FONT_DEFAULT), 0.0001f)
        assertEquals(1.15f, AppSettings.fontScaleOf(AppSettings.FONT_LARGE), 0.0001f)
        assertEquals(1.3f, AppSettings.fontScaleOf(AppSettings.FONT_XLARGE), 0.0001f)
    }

    @Test
    fun `unknown step falls back to no scaling`() {
        assertEquals(1.0f, AppSettings.fontScaleOf("gigantic"), 0.0001f)
        assertEquals(1.0f, AppSettings.fontScaleOf(""), 0.0001f)
    }

    @Test
    fun `the small step actually shrinks`() {
        assertEquals(0.9f, AppSettings.effectiveFontScale(AppSettings.FONT_SMALL, 1.0f), 0.0001f)
        assertEquals(0.9f, AppSettings.effectiveFontScale(AppSettings.FONT_SMALL, 1.6f), 0.0001f)
    }

    @Test
    fun `the largest step applies at the default system size`() {
        assertEquals(1.3f, AppSettings.effectiveFontScale(AppSettings.FONT_XLARGE, 1.0f), 0.0001f)
    }

    @Test
    fun `combined scale saturates at the ceiling`() {
        // 200% system leaves no room: the step must not add anything.
        assertEquals(1.0f, AppSettings.effectiveFontScale(AppSettings.FONT_XLARGE, 2.0f), 0.0001f)
        // 180% system leaves 2.0 / 1.8 = 1.111x for the app step.
        assertEquals(1.1111f, AppSettings.effectiveFontScale(AppSettings.FONT_XLARGE, 1.8f), 0.001f)
        // 150% system still has room for the full 1.3 step.
        assertEquals(1.3f, AppSettings.effectiveFontScale(AppSettings.FONT_XLARGE, 1.5f), 0.0001f)
    }

    @Test
    fun `default never shrinks even on an oversized system scale`() {
        assertEquals(1.0f, AppSettings.effectiveFontScale(AppSettings.FONT_DEFAULT, 2.5f), 0.0001f)
        // A step above the cap adds nothing; it must not pull the system down.
        assertEquals(1.0f, AppSettings.effectiveFontScale(AppSettings.FONT_XLARGE, 2.5f), 0.0001f)
    }
}
