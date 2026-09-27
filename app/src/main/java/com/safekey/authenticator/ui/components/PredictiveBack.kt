package com.safekey.authenticator.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.data.AppSettings
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Converts platform `progress` into a travel fraction for the last-resort path, used only when the
 * platform sends no touch coordinates. The edge gesture commits after roughly a third of the way
 * across, so matching that keeps the page with the finger.
 */
private const val FALLBACK_PROGRESS_GAIN = 0.35f

/** Peak alpha of the scrim that covers the page underneath. */
private const val SCRIM_MAX_ALPHA = 0.35f

/**
 * Predictive-back navigation container: the offset is written straight from each gesture sample.
 *
 * Measured on the production device (developer-screen readout): a fast flick produced ten samples
 * across a ~68 ms drag, i.e. roughly 150 per second — the stream is effectively per frame, and its
 * touch coordinates line up with the container's own pixel width (the page edge sat exactly under
 * the fingertip). So the page is simply placed where the finger is, with no interpolation, no
 * spring and no follow loop: every attempt at being cleverer than that here has only added lag —
 * and one of them added so much lag that the page looked frozen, because a fast finger outran the
 * clamp that was meant to stop it overshooting.
 *
 * What is kept from the earlier rounds, because each one fixed a real defect:
 *
 * - the page underneath is resident (composed at rest), untransformed and unclipped. Composing it
 *   during the gesture dropped frames; shifting it (parallax) left its content cut mid-glyph in the
 *   sliver at the screen edge; clipping it re-recorded its whole draw on every gesture frame;
 * - the travelling page is never rounded-clipped (a leading-edge radius cuts a notch out of the
 *   screen edge, which reads as the page underneath leaking through) and its offset is snapped to
 *   whole device pixels (a fractional edge composites against the page underneath and shows up as a
 *   translucent seam);
 * - the page leaves towards the edge the gesture started on, so both edges work, and `committed` is
 *   cleared at the start of every gesture so a pop that never landed cannot pin the page at zero;
 * - a cancelled gesture springs back with no overshoot; a committed one glides the rest of the way
 *   out *before* the pop, so the pop itself is invisible instead of a jump or a flash.
 *
 * @param systemPredictiveBack use the platform gesture (Android 13+ with predictive back on).
 * @param edgeSwipeFallback drive the same animation from an edge drag instead (older platforms).
 * @param navKey changes whenever the top screen changes; used to reset after a commit.
 * @param previous the screen underneath. The caller keeps it current across the navigation stack so
 *   this layer can stay resident.
 */
@Composable
fun PredictiveBackContainer(
    canGoBack: Boolean,
    systemPredictiveBack: Boolean,
    edgeSwipeFallback: Boolean,
    navKey: Any?,
    previous: (@Composable () -> Unit)?,
    /** Commit glide duration (developer-mode "back animation duration"). */
    commitDurationMillis: Int = AppSettings.MOTION_DURATION_DEFAULT,
    /** Developer-mode multiplier on how far the page travels per finger pixel. */
    gestureGain: Float = AppSettings.GESTURE_GAIN_DEFAULT,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    // Rendered offset in pixels: what the travelling page's translation reads, and the only thing
    // the drag writes. Plain state, read exclusively inside the deferred block below.
    val renderedPx = remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    var showScrim by remember { mutableStateOf(false) }
    // Which edge the current gesture came from, locked for that gesture: the geometry must not flip
    // halfway through.
    var fromRight by remember { mutableStateOf(false) }

    val commitGlide = remember(commitDurationMillis) {
        tween<Float>(commitDurationMillis, easing = FastOutSlowInEasing)
    }
    val cancelSpring = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    }

    /** Offset in pixels for a finger sitting at [finger], for a gesture started at [rightEdge]. */
    fun offsetForFinger(finger: Float, rightEdge: Boolean, width: Float): Float {
        if (width <= 0f) return 0f
        val travelled = if (rightEdge) width - finger else finger
        return (travelled * gestureGain).coerceIn(0f, width)
    }

    // Platform-driven gesture. PredictiveBackHandler also covers the back button, so nothing else is
    // registered on platforms that support it.
    if (systemPredictiveBack) {
        PredictiveBackHandler(enabled = canGoBack) { events: Flow<BackEventCompat> ->
            try {
                if (!dragging) {
                    dragging = true
                    // Every gesture starts from rest. A commit whose pop never landed leaves
                    // `committed` set, and that pins the page at zero for the whole next drag.
                    committed = false
                    showScrim = true
                    PredictiveBackTrace.begin(fromRight)
                }
                var edgeLocked = false
                events.collect { event ->
                    if (!edgeLocked) {
                        edgeLocked = true
                        fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                        PredictiveBackTrace.edgeLocked(fromRight)
                    }
                    // Deliberately not clamped from above: some platforms keep counting past the
                    // commit point, and clamping that away freezes the page while the finger keeps
                    // moving.
                    val fraction = event.progress.coerceAtLeast(0f)
                    PredictiveBackTrace.platformEvent(fraction, event.touchX)
                    PredictiveBackTrace.sampleTiming()
                    // 1:1 with the finger. The platform's touch position is in window pixels, the
                    // same space as this container's width.
                    renderedPx.floatValue = if (event.touchX > 0f && widthPx > 0f) {
                        PredictiveBackTrace.driver("touchX")
                        offsetForFinger(event.touchX, fromRight, widthPx)
                    } else {
                        PredictiveBackTrace.driver("progress")
                        (fraction * FALLBACK_PROGRESS_GAIN * gestureGain).coerceIn(0f, 1f) * widthPx
                    }
                    PredictiveBackTrace.followStep(renderedPx.floatValue)
                }
                // Committed. Glide the rest of the way out, then pop — otherwise the pop snaps the
                // page back into place.
                val from = renderedPx.floatValue
                dragging = false
                animate(from, widthPx, animationSpec = commitGlide) { value, _ ->
                    renderedPx.floatValue = value
                }
                committed = true
                PredictiveBackTrace.finish("commit", widthPx, from)
                onBack()
            } catch (cancelled: CancellationException) {
                val from = renderedPx.floatValue
                dragging = false
                animate(from, 0f, animationSpec = cancelSpring) { value, _ ->
                    renderedPx.floatValue = value
                }
                showScrim = false
                PredictiveBackTrace.finish("cancel", widthPx, from)
                throw cancelled
            }
        }
    }

    // Reset once the pop has been composed: the page underneath is the top page by then, so both
    // layers line up exactly and the reset is invisible.
    LaunchedEffect(navKey) {
        if (committed) {
            renderedPx.floatValue = 0f
            committed = false
            showScrim = false
        }
    }

    // Edge-drag fallback for platforms without the system gesture: same geometry, driven straight by
    // the finger. This one consumes its touches, because with no system gesture in play the drag is
    // the app's own and nothing else should act on it.
    val fallbackModifier = if (edgeSwipeFallback) {
        Modifier.fallbackEdgeSwipe(
            enabled = canGoBack,
            gain = gestureGain,
            onDrag = { travelPx -> renderedPx.floatValue = travelPx },
            onStart = {
                dragging = true
                committed = false
                showScrim = true
            },
            onEdge = { rightEdge -> fromRight = rightEdge },
            onEnd = { traveled, width ->
                dragging = false
                if (traveled > width * 0.15f) {
                    animate(traveled, width, animationSpec = commitGlide) { value, _ ->
                        renderedPx.floatValue = value
                    }
                    committed = true
                    PredictiveBackTrace.finish("commit", width, traveled)
                    onBack()
                } else {
                    animate(traveled, 0f, animationSpec = cancelSpring) { value, _ ->
                        renderedPx.floatValue = value
                    }
                    showScrim = false
                    PredictiveBackTrace.finish("cancel", width, traveled)
                }
            }
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .background(MaterialTheme.colorScheme.background)
            .then(fallbackModifier)
    ) {
        if (previous != null) {
            // Resident and deliberately untransformed and unclipped. See the notes above: each of
            // those three was tried and each one cost more than it saved.
            Box(modifier = Modifier.fillMaxSize()) {
                previous()
            }
            if (showScrim) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = if (widthPx > 0f) {
                                (renderedPx.floatValue / widthPx).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            // Zero at both ends: a linear ramp flashes a grey layer on the first and
                            // the last frame of the gesture.
                            alpha = SCRIM_MAX_ALPHA * 4f * p * (1f - p)
                        }
                        .background(Color.Black)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // While committed, the layer underneath is already the top page, so the offset
                    // must be zero no matter what the driver still holds.
                    translationX = if (committed) {
                        0f
                    } else {
                        val pass = round(renderedPx.floatValue)
                        if (fromRight) -pass else pass
                    }
                }
                // Opaque floor under the page: nothing underneath can ever show through the page's
                // own transparent areas.
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}

/** How close to an edge the finger must land before a drag counts as a back swipe. */
private const val EDGE_ZONE_DP = 40

/**
 * Edge-drag fallback for platforms without the system predictive back: the app owns the gesture, so
 * it consumes its touches and drives the offset straight from the finger. Same geometry as the
 * system path.
 */
private fun Modifier.fallbackEdgeSwipe(
    enabled: Boolean,
    gain: Float,
    onStart: () -> Unit,
    onEdge: (rightEdge: Boolean) -> Unit,
    onDrag: (travelPx: Float) -> Unit,
    onEnd: (travelPx: Float, widthPx: Float) -> Unit
): Modifier = if (!enabled) {
    this
} else {
    this.pointerInput(enabled, gain) {
        val edgePx = EDGE_ZONE_DP.dp.toPx()
        val width = size.width.toFloat()
        while (true) {
            var traveled = -1f
            // The gesture itself has to stay inside the restricted pointer scope, so it only writes
            // through the callbacks; the settle runs once we are back outside.
            awaitEachGesture {
                traveled = -1f
                val down = awaitFirstDown(requireUnconsumed = false)
                val startedRight = down.position.x > width - edgePx
                if (down.position.x > edgePx && !startedRight) return@awaitEachGesture
                // Only a real horizontal drag takes over: a vertical scroll that happens to start near
                // the edge must still scroll.
                awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                } ?: return@awaitEachGesture
                onStart()
                onEdge(startedRight)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed) break
                    change.consume()
                    val travel = if (startedRight) {
                        width - change.position.x
                    } else {
                        change.position.x
                    }
                    val value = (travel * gain).coerceIn(0f, width)
                    traveled = value
                    onDrag(value)
                }
            }
            if (traveled < 0f) continue
            onEnd(traveled, width)
        }
    }
}
