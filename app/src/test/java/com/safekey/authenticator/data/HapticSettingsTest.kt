package com.safekey.authenticator.data

import org.junit.Assert.assertEquals
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

    @Test
    fun `duration stays a short tick`() {
        assertEquals(10L, AppSettings.hapticDurationMs(0))
        assertEquals(20L, AppSettings.hapticDurationMs(100))
        assertEquals(20L, AppSettings.hapticDurationMs(999))
    }

    @Test
    fun `a mid intensity is neither the weakest nor the strongest`() {
        assertTrue(AppSettings.hapticAmplitude(50) in 2..254)
    }
}
