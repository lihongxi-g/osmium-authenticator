package com.safekey.authenticator.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.platform.LocalContext
import com.safekey.authenticator.data.AppSettings
import kotlinx.coroutines.launch

/**
 * Builds the tick the app fires on a click.
 *
 * The master switch decides whether the app vibrates at all; the slider sets
 * the strength, which maps to both amplitude and duration.
 */
@Composable
fun rememberHapticTick(enabled: Boolean, intensityPercent: Int): () -> Unit {
    val context = LocalContext.current
    return remember(enabled, intensityPercent) {
        val tick: () -> Unit = {
            if (enabled) vibrateOnce(context, intensityPercent)
        }
        tick
    }
}

/**
 * An [IndicationNodeFactory] that fires [tick] on every click, layered over the
 * normal ripple so the visual feedback is untouched.
 *
 * Hooking the indication instead of the pointer stream is what keeps this to
 * *clickable* things: buttons, switches, settings rows, the PIN keypad, dialog
 * actions, list items. Backgrounds, scroll areas, the slider (a drag, not a
 * click) and the swipe-back gesture never fire it. Dialog content inherits the
 * composition locals, so its buttons are covered as well.
 */
class HapticIndication(
    private val tick: () -> Unit,
    private val base: IndicationNodeFactory
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        HapticIndicationNode(tick, interactionSource, base)

    override fun equals(other: Any?): Boolean =
        other is HapticIndication && other.base == base

    override fun hashCode(): Int = base.hashCode() * 31 + 7
}

private class HapticIndicationNode(
    private val tick: () -> Unit,
    private val interactionSource: InteractionSource,
    base: IndicationNodeFactory
) : DelegatingNode() {

    init {
        // The ripple keeps drawing; we only listen.
        delegate(base.create(interactionSource))
    }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                // Release, not Press: a touch that turns into a scroll, or a
                // press that gets cancelled, is not a click and stays silent.
                if (interaction is PressInteraction.Release) tick()
            }
        }
    }
}

/**
 * Fires one tick at [intensityPercent] whatever the switch says.
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
