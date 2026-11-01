package com.safekey.authenticator.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.safekey.authenticator.data.AppSettings

/**
 * Builds the tick the app fires on every tap.
 *
 * The master switch decides *whether* the app vibrates. When "follow system"
 * is on, the strength comes from the platform's `haptic_feedback_intensity`
 * setting instead of the slider: following the system changes how strong the
 * tick is, never whether there is one.
 */
@Composable
fun rememberHapticTick(
    enabled: Boolean,
    followSystem: Boolean,
    intensityPercent: Int
): () -> Unit {
    val context = LocalContext.current
    return remember(enabled, followSystem, intensityPercent) {
        val tick: () -> Unit = {
            if (enabled) {
                val percent =
                    if (followSystem) systemIntensityPercent(context) else intensityPercent
                vibrateOnce(context, percent)
            }
        }
        tick
    }
}

/**
 * Fires [tick] on every tap inside the subtree, the way the system's touch
 * feedback behaves for its own controls.
 *
 * Observes on the final pass and never consumes an event, so clicks, long
 * presses, drags and the swipe-back gesture keep working untouched. A pointer
 * that moves past the touch slop (scrolling a list, dragging the slider) is a
 * gesture, not a tap, and stays silent.
 */
fun Modifier.tapHaptics(tick: () -> Unit): Modifier = pointerInput(tick) {
    val slop = viewConfiguration.touchSlop
    awaitPointerEventScope {
        var downAt: Offset? = null
        var downTime = 0L
        var dragged = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull()
            when (event.type) {
                PointerEventType.Press -> {
                    if (event.changes.count { it.pressed } > 1 || change == null) {
                        // Multi-touch is a pinch/zoom, not a button press.
                        downAt = null
                    } else {
                        downAt = change.position
                        downTime = System.currentTimeMillis()
                        dragged = false
                    }
                }
                PointerEventType.Move -> {
                    val start = downAt
                    val position = change?.position
                    if (start != null && position != null &&
                        (position - start).getDistance() > slop
                    ) {
                        dragged = true
                    }
                }
                PointerEventType.Release -> {
                    val start = downAt
                    downAt = null
                    if (start != null && !dragged && System.currentTimeMillis() - downTime <= 1500L) {
                        tick()
                    }
                }
                else -> Unit
            }
        }
    }
}

/**
 * Fires one tick at [intensityPercent] whatever the switches say.
 *
 * Used by the settings page, where the strength should be felt while it is
 * being adjusted.
 */
fun previewHaptic(context: Context, intensityPercent: Int) {
    vibrateOnce(context, intensityPercent)
}

@Suppress("DEPRECATION")
private fun vibrateOnce(context: Context, intensityPercent: Int) {
    if (intensityPercent <= 0) return
    val vibrator = vibratorOf(context) ?: return
    if (!vibrator.hasVibrator()) return

    val duration = AppSettings.hapticDurationMs(intensityPercent)
    val amplitude = AppSettings.hapticAmplitude(intensityPercent)
    // A device without amplitude control rejects a custom amplitude, so ask
    // before handing one over instead of dropping the whole tick.
    val effect = if (runCatching { vibrator.hasAmplitudeControl() }.getOrDefault(false)) {
        VibrationEffect.createOneShot(duration, amplitude)
    } else {
        VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    val fired = runCatching { vibrator.vibrate(effect) }.isSuccess
    if (!fired) {
        // Last resort: the call every ROM accepts.
        runCatching { vibrator.vibrate(duration) }
    }
}

@Suppress("DEPRECATION")
private fun vibratorOf(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

/**
 * The platform's touch-feedback strength.
 *
 * `haptic_feedback_intensity` is a hidden setting: readable on most devices,
 * absent on some. Anything unreadable falls back to a mid strength rather than
 * to silence.
 */
private fun systemIntensityPercent(context: Context): Int {
    val raw = runCatching {
        Settings.System.getInt(context.contentResolver, "haptic_feedback_intensity", -1)
    }.getOrDefault(-1)
    return AppSettings.hapticSystemIntensity(raw)
}
