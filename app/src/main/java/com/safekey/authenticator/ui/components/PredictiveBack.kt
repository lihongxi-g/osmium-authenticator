package com.safekey.authenticator.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * How far across the screen the page travels for a full platform back gesture. The platform's
 * edge gesture commits after roughly a third of the screen, so matching that is what makes the
 * page track the finger 1:1 instead of running ahead of it.
 */
private const val GESTURE_GAIN = 0.35f

/** How far the revealed page slides in from the leading edge, relative to the width. */
private const val PARALLAX_FRACTION = 0.12f

/** Peak alpha of the scrim that covers the revealed page. */
private const val SCRIM_MAX_ALPHA = 0.35f

/** Default commit glide, used when the caller does not pass its own duration. */
private const val DEFAULT_COMMIT_SETTLE_MILLIS = 185

/**
 * Predictive-back navigation container.
 *
 * Layout and behaviour follow KernelSU's navigation shell (miuix-nav, Apache-2.0 — see the
 * sources page for attribution):
 *
 * - the reveal fraction is written 1:1 from the gesture and only ever read inside deferred
 *   `graphicsLayer { }` blocks, so a gesture frame costs no recomposition;
 * - the page being left slides out over a solid backdrop, and the page underneath parallaxes in
 *   from the leading edge behind a scrim, so a gap never shows another screen's edge;
 * - the moving page is never rounded-clipped: a leading-edge radius cuts a notch out of the
 *   screen edge, which reads as the page underneath leaking through;
 * - a cancelled gesture springs back with no overshoot; a committed one glides the rest of the
 *   way out *before* the pop, so the pop itself is invisible instead of a jump or a flash.
 *
 * @param systemPredictiveBack use the platform gesture (Android 13+ with predictive back on).
 * @param edgeSwipeFallback drive the same animation from an edge drag instead (older platforms).
 * @param navKey changes whenever the top screen changes; used to reset after a commit.
 * @param previous the screen underneath, rendered once the gesture is clearly under way. The
 *   caller freezes it at gesture start so the layer below cannot change mid-gesture.
 */
@Composable
fun PredictiveBackContainer(
    canGoBack: Boolean,
    systemPredictiveBack: Boolean,
    edgeSwipeFallback: Boolean,
    navKey: Any?,
    previous: (@Composable () -> Unit)?,
    onGestureStart: () -> Unit = {},
    /** Duration of the commit glide (the slider in the appearance settings). */
    commitDurationMillis: Int = DEFAULT_COMMIT_SETTLE_MILLIS,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    // The platform gesture feeds an Animatable (its callbacks run in an ordinary coroutine);
    // the edge-swipe fallback writes plain state, because pointer callbacks run inside a
    // restricted coroutine scope that cannot suspend on an Animatable.
    val systemReveal = remember { Animatable(0f) }
    val dragReveal = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    var showPrevious by remember { mutableStateOf(false) }

    // 0f = settled, 1f = the page has travelled all the way off the screen.
    val progress: () -> Float =
        if (systemPredictiveBack) ({ systemReveal.value }) else ({ dragReveal.floatValue })

    // Platform-driven gesture. PredictiveBackHandler also covers the back button and the system
    // back gesture on every platform that supports it, so nothing else is registered there.
    if (systemPredictiveBack) {
        PredictiveBackHandler(enabled = canGoBack) { events: Flow<BackEventCompat> ->
            try {
                if (!dragging) {
                    dragging = true
                    onGestureStart()
                }
                events.collect { event ->
                    val fraction = event.progress.coerceIn(0f, 1f)
                    if (!showPrevious && fraction > 0.02f) showPrevious = true
                    systemReveal.snapTo(fraction * GESTURE_GAIN)
                }
                // The platform committed. Glide the rest of the way out while this layout is
                // still on screen, then pop — otherwise the pop snaps the page back into place.
                dragging = false
                settling = true
                systemReveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(commitDurationMillis, easing = FastOutSlowInEasing)
                )
                settling = false
                committed = true
                onBack()
            } catch (cancelled: CancellationException) {
                dragging = false
                settling = true
                systemReveal.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                settling = false
                showPrevious = false
                throw cancelled
            }
        }
    }

    // Reset once the pop has been composed: the revealed page is the top page by then, so both
    // layers line up exactly and the reset is invisible.
    LaunchedEffect(navKey) {
        if (committed) {
            systemReveal.snapTo(0f)
            dragReveal.floatValue = 0f
            committed = false
            showPrevious = false
        }
    }

    val fallbackModifier = if (edgeSwipeFallback) {
        Modifier.pointerInput(canGoBack, edgeSwipeFallback) {
            if (!canGoBack) return@pointerInput
            val edgePx = with(density) { 40.dp.toPx() }
            val thresholdPx = with(density) { 8.dp.toPx() }
            val widthPx = size.width.toFloat()
            var travelled = -1f
            while (true) {
                // The gesture itself has to stay inside the restricted pointer scope, so it only
                // writes plain state — the settle animation runs once we are back outside.
                awaitEachGesture {
                    travelled = -1f
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x > edgePx) return@awaitEachGesture
                    // Only a real horizontal drag takes over: a vertical scroll that happens to
                    // start near the edge must still scroll.
                    awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    } ?: return@awaitEachGesture
                    var total = 0f
                    dragging = true
                    onGestureStart()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.isConsumed) break
                        change.consume()
                        total = (total + change.positionChange().x).coerceAtLeast(0f)
                        if (!showPrevious && total > thresholdPx) showPrevious = true
                        // 1:1 with the finger: the page follows the drag in pixels.
                        dragReveal.floatValue = (total / widthPx).coerceIn(0f, 1f)
                    }
                    travelled = total
                }
                if (travelled < 0f) continue
                dragging = false
                settling = true
                if (travelled > widthPx * 0.15f) {
                    animate(
                        initialValue = dragReveal.floatValue,
                        targetValue = 1f,
                        animationSpec = tween(commitDurationMillis, easing = FastOutSlowInEasing)
                    ) { value, _ -> dragReveal.floatValue = value }
                    settling = false
                    committed = true
                    onBack()
                } else {
                    animate(
                        initialValue = dragReveal.floatValue,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) { value, _ -> dragReveal.floatValue = value }
                    settling = false
                    showPrevious = false
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(fallbackModifier)
    ) {
        if (showPrevious && previous != null) {
            // The page underneath parallaxes in from the leading edge. Deliberately opaque — a
            // translucent page over a moving one is what reads as "not fully covering".
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -(1f - progress()) * size.width * PARALLAX_FRACTION
                    }
            ) {
                previous()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress()
                        alpha = SCRIM_MAX_ALPHA * 4f * p * (1f - p)
                    }
                    .background(Color.Black)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // While committed the page underneath is already the top page, so the offset
                    // must be zero no matter what the driver still holds.
                    translationX = if (committed) 0f else progress() * size.width
                }
                // Opaque floor under the page: nothing underneath can ever show through the
                // page's own transparent areas.
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}
