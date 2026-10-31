package com.safekey.authenticator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vibration amplitude/duration derived from the intensity percentage. */
class HapticSettingsTest {

    @Test
    fun `amplitude spans the platform range`() {
        assertEquals(1, AppSettings.hapticAmplitude(0))
        assertEquals(255, AppSettings.hapticAmplitude(100))
        assertEquals(128, AppSettings.hapticAmplitude(50))
    }

    @Test
    fun `out of range intensities are clamped`() {
        assertEquals(1, AppSettings.hapticAmplitude(-40))
        assertEquals(255, AppSettings.hapticAmplitude(500))
    }

    /**
     * 10-20 ms (the first cut) went unnoticed on real phones, so the tick has
     * to stay in a range a phone actually renders as a buzz.
     */
    @Test
    fun `duration is long enough to be felt`() {
        assertEquals(25L, AppSettings.hapticDurationMs(0))
        assertEquals(60L, AppSettings.hapticDurationMs(100))
        assertEquals(60L, AppSettings.hapticDurationMs(999))
        assertTrue(AppSettings.hapticDurationMs(60) >= 25L)
    }

    @Test
    fun `a mid intensity is neither the weakest nor the strongest`() {
        assertTrue(AppSettings.hapticAmplitude(50) in 2..254)
    }

    /**
     * Out of the box the app vibrates with its own effect: the system path
     * stays silent on ROMs whose touch-feedback switch is off, which reads as
     * "the feature does not work".
     */
    @Test
    fun `own vibration is the default and the system path is opt-in`() {
        val defaults = AppSettings()
        assertTrue(defaults.hapticEnabled)
        assertFalse(defaults.hapticFollowSystem)
        assertEquals(AppSettings.HAPTIC_INTENSITY_DEFAULT, defaults.hapticIntensityPercent)
        assertTrue(
            AppSettings.HAPTIC_SYSTEM_INTENSITY_PERCENT in
                AppSettings.HAPTIC_INTENSITY_MIN..AppSettings.HAPTIC_INTENSITY_MAX
        )
    }
}
