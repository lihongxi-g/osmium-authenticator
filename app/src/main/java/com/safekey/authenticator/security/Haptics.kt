package com.safekey.authenticator.security

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * App-wide haptic feedback with a user-adjustable intensity (0–100%).
 * All taps go through here so the strength setting applies everywhere.
 */
object Haptics {

    @Volatile
    private var intensity: Float = 1f

    /** 0–100 */
    fun setIntensity(percent: Int) {
        intensity = percent.coerceIn(0, 100) / 100f
    }

    /** Light tap — copying a code, pressing a PIN key. */
    fun tick(context: Context) = vibrate(context, 12L, 0.7f)

    /** Medium tap — drag start, saved actions. */
    fun medium(context: Context) = vibrate(context, 24L, 0.9f)

    /** Heavy — self-destruct triggered. */
    fun heavy(context: Context) = vibrate(context, 80L, 1f)

    private fun vibrate(context: Context, durationMs: Long, scale: Float) {
        val pct = intensity
        if (pct <= 0.01f) return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val amplitude = (255 * pct * scale).toInt().coerceIn(1, 255)
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } catch (_: Exception) {
            // some devices reject amplitude-based effects — fall back to plain
            try {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, 128))
            } catch (_: Exception) {
            }
        }
    }
}
