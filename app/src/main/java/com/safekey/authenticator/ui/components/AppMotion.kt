package com.safekey.authenticator.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * Material 3 Expressive motion tokens.
 *
 * These are the spring constants published by Google in
 * `androidx.compose.material3.tokens.ExpressiveMotionTokens` (Apache-2.0).
 * They are reproduced here because the expressive MotionScheme API is still
 * `internal` in the stable material3 release the app builds against
 * (1.3.0 / Compose 1.7.3) — only the *values* are needed to get the same feel.
 *
 * Two families:
 *  - spatial: things that move, grow or slide (can overshoot slightly, which is
 *    what makes the motion read as "expressive")
 *  - effects: alpha / colour / fade (never overshoots, so text never ghosts)
 *
 * Kept intentionally small: the app only uses the default and fast tiers plus
 * the slow tier for one-shot hero moments.
 */
object AppMotion {
    /** Movement, default tier. */
    fun <T> spatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Movement, fast tier — for small elements and press feedback. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Movement, slow tier — hero moments. */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 200f)

    /** Alpha / colour, default tier. */
    fun <T> effects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 1600f)

    /** Alpha / colour, fast tier. */
    fun <T> effectsFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 3800f)
}

/**
 * Expressive press feedback: the surface shrinks slightly while held and springs
 * back on release. Draw-phase only (scale is a graphics layer), so it costs no
 * layout or recomposition of the card content.
 *
 * Callers pass the same [InteractionSource] to their clickable modifier so the
 * press state is shared instead of tracked twice.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val factor by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) AppMotion.spatialFast() else AppMotion.spatial(),
        label = "pressScale"
    )
    return this.scale(factor)
}
