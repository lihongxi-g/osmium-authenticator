package com.safekey.authenticator.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.safekey.authenticator.data.AppSettings

/**
 * One short tick, provided by the theme according to the haptic settings.
 *
 * Call sites do not know whether the tick comes from the system or from a
 * custom VibrationEffect — they just call it.
 */
val LocalHapticTick = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Builds the tick used by the app's primary interactions (copying a code, the
 * PIN keypad).
 *
 * "Follow system" hands the decision to the platform: `performHapticFeedback`
 * obeys the system haptics switch and intensity, and the app fires no
 * VibrationEffect of its own. With it off, the custom switch decides and the
 * intensity maps to amplitude/duration.
 */
@Composable
fun rememberHapticTick(
    enabled: Boolean,
    followSystem: Boolean,
    intensityPercent: Int
): () -> Unit {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(enabled, followSystem, intensityPercent) {
        val tick: () -> Unit = {
            if (followSystem) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else if (enabled) {
                vibrateOnce(context, intensityPercent)
            }
        }
        tick
    }
}

private fun vibrateOnce(context: Context, intensityPercent: Int) {
    if (intensityPercent <= 0) return
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    val effect = VibrationEffect.createOneShot(
        AppSettings.hapticDurationMs(intensityPercent),
        AppSettings.hapticAmplitude(intensityPercent)
    )
    // A device that refuses the effect (some OEM power policies) must not
    // crash the tap that triggered it.
    runCatching { vibrator.vibrate(effect) }
}
