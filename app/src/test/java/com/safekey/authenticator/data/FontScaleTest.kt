package com.safekey.authenticator.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Text-size percent, the master switch and the combined-scale ceiling. */
class FontScaleTest {

    @Test
    fun `percent maps straight to a multiplier when enabled`() {
        assertEquals(0.8f, AppSettings.effectiveFontScale(true, 80, 1.0f), 0.0001f)
        assertEquals(0.9f, AppSettings.effectiveFontScale(true, 90, 1.0f), 0.0001f)
        assertEquals(1.0f, AppSettings.effectiveFontScale(true, 100, 1.0f), 0.0001f)
        assertEquals(1.3f, AppSettings.effectiveFontScale(true, 130, 1.0f), 0.0001f)
        assertEquals(2.0f, AppSettings.effectiveFontScale(true, 200, 1.0f), 0.0001f)
    }

    @Test
    fun `the master switch off restores the default size`() {
        assertEquals(1.0f, AppSettings.effectiveFontScale(false, 200, 1.0f), 0.0001f)
        assertEquals(1.0f, AppSettings.effectiveFontScale(false, 80, 1.0f), 0.0001f)
        assertEquals(1.0f, AppSettings.effectiveFontScale(false, 130, 2.0f), 0.0001f)
    }

    @Test
    fun `out of range percents are clamped`() {
        assertEquals(2.0f, AppSettings.effectiveFontScale(true, 500, 1.0f), 0.0001f)
        assertEquals(0.8f, AppSettings.effectiveFontScale(true, 10, 1.0f), 0.0001f)
    }

    @Test
    fun `legacy step ids keep their size`() {
        assertEquals(90, AppSettings.fontScalePercentOfLegacyStep("small"))
        assertEquals(100, AppSettings.fontScalePercentOfLegacyStep("default"))
        assertEquals(115, AppSettings.fontScalePercentOfLegacyStep("large"))
        assertEquals(130, AppSettings.fontScalePercentOfLegacyStep("xlarge"))
        assertEquals(100, AppSettings.fontScalePercentOfLegacyStep("garbage"))
    }

    @Test
    fun `combined scale saturates at the ceiling`() {
        // 200% system leaves no room: the app value must not add anything.
        assertEquals(1.0f, AppSettings.effectiveFontScale(true, 130, 2.0f), 0.0001f)
        // 180% system leaves 2.0 / 1.8 = 1.111x for the app.
        assertEquals(1.1111f, AppSettings.effectiveFontScale(true, 130, 1.8f), 0.001f)
        // 150% system still has room for the full 130%.
        assertEquals(1.3f, AppSettings.effectiveFontScale(true, 130, 1.5f), 0.0001f)
    }

    @Test
    fun `100 percent never shrinks even on an oversized system scale`() {
        assertEquals(1.0f, AppSettings.effectiveFontScale(true, 100, 2.5f), 0.0001f)
        // A value above the cap adds nothing; it must not pull the system down.
        assertEquals(1.0f, AppSettings.effectiveFontScale(true, 200, 2.5f), 0.0001f)
    }
}
