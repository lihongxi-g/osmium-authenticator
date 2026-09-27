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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.data.AppSettings
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * How far across the screen the page travels for a full platform back gesture. Only a fallback:
 * it is used when the platform does not report touch coordinates (below API 34), where the page
 * can only be driven by `progress`. The platform's edge gesture commits after roughly a third of
 * the screen, so matching that is what makes the page track the finger instead of running ahead.
 */
private const val GESTURE_GAIN = 0.35f

/** Peak alpha of the scrim that covers the revealed page. */
private const val SCRIM_MAX_ALPHA = 0.35f

/**
 * Predictive-back navigation container.
 *
 * Layout and behaviour follow KernelSU's navigation shell (miuix-nav, Apache-2.0 — see the
 * sources page for attribution):
 *
 * - the reveal fraction is written 1:1 from the gesture and only ever read inside deferred
 *   `graphicsLayer { }` blocks, so a gesture frame costs no recomposition;
 * - the page being left slides out over the page underneath, which stays put (no parallax), so
 *   the sliver it shows at the screen edge is that page's own left margin rather than content
 *   shifted in from further right, behind a scrim;
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
    /** Commit glide duration (developer-mode "back animation duration"). */
    commitDurationMillis: Int = AppSettings.MOTION_DURATION_DEFAULT,
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
    // Container width in pixels: the reference the platform's touch coordinates are measured
    // against, so the page can follow the finger in real pixels.
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val commitGlide = remember(commitDurationMillis) {
        tween<Float>(commitDurationMillis, easing = FastOutSlowInEasing)
    }

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
                // Origin of the finger travel, seeded from the first event: an edge gesture starts
                // on the edge, so that reading is the zero the follow is measured from.
                var touchStart = -1f
                events.collect { event ->
                    // Deliberately not clamped from above: some platforms keep counting past the
                    // commit point, and clamping that away freezes the page while the finger
                    // keeps moving. Only the resulting offset is clamped.
                    val fraction = event.progress.coerceAtLeast(0f)
                    if (!showPrevious && fraction > 0.02f) showPrevious = true
                    val fingerX = event.touchX
                    if (touchStart < 0f) touchStart = fingerX
                    // Prefer the real finger position. `progress` is capped at 1 once the gesture
                    // would commit — a third of the way across the screen — so a progress-driven
                    // page stops following while the finger still has half the screen to go.
                    // Touch coordinates are reported in window pixels from API 34 up.
                    val reveal = if (fingerX > 0f && containerWidthPx > 0f) {
                        ((fingerX - touchStart) / containerWidthPx).coerceIn(0f, 1f)
                    } else {
                        (fraction * GESTURE_GAIN).coerceIn(0f, 1f)
                    }
                    systemReveal.snapTo(reveal)
                }
                // The platform committed. Glide the rest of the way out while this layout is
                // still on screen, then pop — otherwise the pop snaps the page back into place.
                dragging = false
                settling = true
                systemReveal.animateTo(targetValue = 1f, animationSpec = commitGlide)
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
                        animationSpec = commitGlide
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
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .background(MaterialTheme.colorScheme.background)
            .then(fallbackModifier)
    ) {
        if (showPrevious && previous != null) {
            // Deliberately untransformed. Shifting this layer (the usual parallax) puts content
            // from further right into the sliver at the screen edge, where it is cut mid-glyph
            // and reads as characters leaking through the page on top.
            Box(modifier = Modifier.fillMaxSize()) {
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
                    // must be zero no matter what the driver still holds. Otherwise the offset is
                    // snapped to whole device pixels: a fractional edge gets composited against
                    // the page underneath and shows up as a translucent seam.
                    translationX = if (committed) 0f else round(progress() * size.width)
                }
                // Opaque floor under the page: nothing underneath can ever show through the
                // page's own transparent areas.
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}
