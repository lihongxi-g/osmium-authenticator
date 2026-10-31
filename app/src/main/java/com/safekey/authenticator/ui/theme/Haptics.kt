package com.safekey.authenticator.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.safekey.authenticator.data.AppSettings

/**
 * One short tick, provided by the theme according to the haptic settings.
 *
 * Call sites do not know whether the tick follows the system switch or the
 * app's own intensity — they just call it.
 */
val LocalHapticTick = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Builds the tick used by the app's primary interactions (copying a code, the
 * PIN keypad).
 *
 * Both paths drive the vibrator ourselves, because `performHapticFeedback`
 * turns out to be a coin flip on OEM ROMs: some report the feedback as
 * handled and vibrate nothing, and a ComposeView is not a great carrier for
 * it either. What the two paths differ in is only *who decides*:
 *
 *  - "Follow system": the platform's touch-feedback switch decides whether we
 *    vibrate; the strength is a fixed, perceptible mid value.
 *  - Otherwise the master switch decides, and the slider percentage maps to
 *    both amplitude and duration.
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
            if (followSystem) {
                if (systemHapticsEnabled(context)) {
                    vibrateOnce(context, AppSettings.HAPTIC_SYSTEM_INTENSITY_PERCENT)
                }
            } else if (enabled) {
                vibrateOnce(context, intensityPercent)
            }
        }
        tick
    }
}

/**
 * Fires one tick at [intensityPercent] whatever the switches say.
 *
 * Backs the "test the vibration" row, so the strength can be felt while it is
 * being adjusted instead of hunting for a code to copy.
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

/** `Settings.System.HAPTIC_FEEDBACK_ENABLED`; unreadable means "assume on". */
private fun systemHapticsEnabled(context: Context): Boolean =
    runCatching {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) != 0
    }.getOrDefault(true)
