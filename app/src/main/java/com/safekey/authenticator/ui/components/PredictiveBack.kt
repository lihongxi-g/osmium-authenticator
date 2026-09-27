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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.data.AppSettings
import kotlin.math.exp
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Converts platform `progress` into a travel fraction for the last-resort path, used only when
 * neither our own pointer stream nor the platform's touch coordinates are available. The edge
 * gesture commits after roughly a third of the screen, so matching that keeps the page with the
 * finger. This is a platform fact, not the user's follow gain — that multiplies on top of it.
 */
private const val FALLBACK_PROGRESS_GAIN = 0.35f

/** Peak alpha of the scrim that covers the page underneath. */
private const val SCRIM_MAX_ALPHA = 0.35f

/** How close to an edge the finger must land before a drag counts as a back swipe. */
private const val EDGE_ZONE_DP = 40

/** Fraction of the width an edge drag must travel before the fallback counts it as a back gesture. */
private const val FALLBACK_COMMIT_FRACTION = 0.15f

/**
 * Angular frequency of the spring that carries the page to the predicted finger position.
 *
 * Measured on the production device: a whole swipe carries only about ten gesture samples, roughly
 * 50 px and 24 ms apart, and the system does not forward ordinary touch events while it owns the
 * gesture. Moving the page on each sample alone therefore leaves it motionless for two frames out of
 * three — the "dropped frames" and "stuck" reports. The finger position is extrapolated between
 * samples instead, and the spring only smooths the corners, so the page moves on every frame.
 */
private const val FOLLOW_OMEGA = 70f

/**
 * How far ahead the last sample's speed may be trusted, in seconds. Roughly one sample interval, so
 * a finger that stops or turns around cannot leave the page running away for longer than that.
 */
private const val PREDICT_HORIZON_S = 0.025f

/**
 * Predictive-back navigation container.
 *
 * Input model and geometry are both the result of field measurements (see the project's
 * predictive-back notes):
 *
 * - the platform's gesture stream carries the finger position but only about ten samples per swipe,
 *   so the page is driven by an extrapolation of those samples (position + speed) through a
 *   critically damped spring, integrated by hand once per displayed frame;
 * - that stream is **not** delivered to the app as ordinary touch events while the system owns the
 *   gesture, so the extra pointer sampler below is only a bonus for platforms that do forward them.
 *
 * Rendering: the offset is read exclusively inside deferred `graphicsLayer { }` blocks, so a gesture
 * frame costs no recomposition. The page underneath is resident (composed at rest), untransformed
 * and unclipped — composing it during the gesture dropped frames, and clipping it re-recorded the
 * whole page's draw on every gesture frame. The travelling page is never rounded-clipped and its
 * offset is snapped to whole device pixels, because a fractional edge composites against the page
 * underneath and shows up as a translucent seam. It leaves towards the edge the gesture started on,
 * so both edges work. A cancelled gesture springs back with no overshoot; a committed one glides the
 * rest of the way out *before* the pop, so the pop itself is invisible instead of a jump or a flash.
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
    val density = LocalDensity.current

    // Rendered offset in pixels: what the travelling page's translation reads. Plain state written
    // by the frame loop below (and by the settle animations).
    val renderedPx = remember { mutableFloatStateOf(0f) }
    // Newest finger sample, its speed, and when it arrived. Plain values: the frame loop reads them
    // every frame, so they never need to trigger anything themselves.
    var samplePx = 0f
    var sampleVelocityPx = 0f
    var sampleNanos = 0L
    // Latest finger position from our own pointer stream, in container pixels (-1 = none yet).
    val fingerPx = remember { mutableFloatStateOf(-1f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
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

    // Offset in pixels for a finger sitting at [finger], for a gesture that started at [rightEdge].
    fun offsetForFinger(finger: Float, rightEdge: Boolean, width: Float): Float {
        if (width <= 0f) return 0f
        val travelled = if (rightEdge) width - finger else finger
        return (travelled * gestureGain).coerceIn(0f, width)
    }

    /** Publish a finger sample, deriving its speed from the gap since the previous one. */
    fun setFinger(finger: Float, rightEdge: Boolean, width: Float) {
        val value = offsetForFinger(finger, rightEdge, width)
        val now = System.nanoTime()
        if (sampleNanos != 0L) {
            val seconds = (now - sampleNanos) / 1_000_000_000f
            if (seconds > 0.001f) sampleVelocityPx = (value - samplePx) / seconds
        }
        samplePx = value
        sampleNanos = now
    }

    /** Drop the sample history so a new gesture cannot inherit the previous one's speed. */
    fun resetSamples(at: Float) {
        samplePx = at
        sampleVelocityPx = 0f
        sampleNanos = 0L
    }

    // The follow loop. One integration step per displayed frame: extrapolate the finger from the
    // newest sample and its speed, then move the page towards that through a critically damped
    // spring — hand-written rather than delegated to Animatable/snapshotFlow so that every step is
    // visible here (a previous version stalled on device in exactly that delegation).
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        var velocity = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastFrameNanos == 0L) {
                    0f
                } else {
                    ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                }
                lastFrameNanos = now
                if (dt <= 0f || settling || (sampleNanos == 0L && !dragging)) {
                    return@withFrameNanos
                }
                // Where the finger is now, according to the newest sample and its speed.
                val elapsed = if (sampleNanos == 0L) {
                    0f
                } else {
                    (now - sampleNanos) / 1_000_000_000f
                }
                val predicted = samplePx + sampleVelocityPx * elapsed.coerceAtMost(PREDICT_HORIZON_S)
                val target = if (dragging) predicted else 0f
                // Critically damped step, closed form: with error e and e'(0) = v,
                //   e(t) = (e + (v + w·e)·t)·exp(-w·t)
                // which crosses zero (overshoots) only when the bracket has the opposite sign to e.
                val error = renderedPx.floatValue - target
                var bracket = velocity + FOLLOW_OMEGA * error
                if (bracket * error < 0f) bracket = 0f
                val decay = exp(-FOLLOW_OMEGA * dt)
                val nextError = (error + bracket * dt) * decay
                renderedPx.floatValue = target + nextError
                velocity = (bracket - FOLLOW_OMEGA * (error + bracket * dt)) * decay
                PredictiveBackTrace.followStep(renderedPx.floatValue)
            }
        }
    }

    /**
     * Our own view of the finger. The platform's event stream is the authority on *whether* this is
     * a back gesture, but on the production device it delivers no ordinary touch events while it
     * owns the gesture, so this sampler is a bonus: platforms that do forward them give a denser
     * feed than the gesture stream. Samples are observed, never consumed.
     */
    val pointerModifier = if (systemPredictiveBack) {
        Modifier.pointerInput(canGoBack, systemPredictiveBack, gestureGain) {
            val edgePx = with(density) { EDGE_ZONE_DP.dp.toPx() }
            val width = size.width.toFloat()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startedRight = down.position.x > width - edgePx
                if (!startedRight && down.position.x > edgePx) return@awaitEachGesture
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    fingerPx.floatValue = change.position.x
                    // Only follow once the platform has confirmed a back gesture: a drag that starts
                    // near the edge can also be a list scrolling.
                    if (dragging && !settling) {
                        fromRight = startedRight
                        setFinger(change.position.x, startedRight, width)
                    }
                    PredictiveBackTrace.pointerSample(change.position.x)
                }
                fingerPx.floatValue = -1f
            }
        }
    } else {
        Modifier
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
                    resetSamples(renderedPx.floatValue)
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
                    // Only publish a sample; the frame loop turns it into motion. Our own pointer
                    // samples win when they exist, then the platform's touch position, then its
                    // progress as the last resort.
                    if (fingerPx.floatValue >= 0f) {
                        PredictiveBackTrace.driver("pointer")
                    } else if (event.touchX > 0f && widthPx > 0f) {
                        PredictiveBackTrace.driver("touchX")
                        setFinger(event.touchX, fromRight, widthPx)
                    } else {
                        PredictiveBackTrace.driver("progress")
                        val value =
                            (fraction * FALLBACK_PROGRESS_GAIN * gestureGain)
                                .coerceIn(0f, 1f) * widthPx
                        samplePx = value
                        sampleNanos = System.nanoTime()
                    }
                }
                // Committed. Glide the rest of the way out, then pop — otherwise the pop snaps the
                // page back into place.
                val from = renderedPx.floatValue
                dragging = false
                settling = true
                animate(from, widthPx, animationSpec = commitGlide) { value, _ ->
                    renderedPx.floatValue = value
                }
                settling = false
                resetSamples(widthPx)
                committed = true
                PredictiveBackTrace.finish("commit", widthPx, from)
                onBack()
            } catch (cancelled: CancellationException) {
                val from = renderedPx.floatValue
                dragging = false
                settling = true
                animate(from, 0f, animationSpec = cancelSpring) { value, _ ->
                    renderedPx.floatValue = value
                }
                settling = false
                resetSamples(0f)
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
            resetSamples(0f)
            committed = false
            showScrim = false
        }
    }

    // Edge-drag fallback for platforms without the system gesture: same geometry, same follow loop,
    // driven straight by the finger. This one consumes its touches, because with no system gesture in
    // play the drag is the app's own and nothing else should act on it.
    val fallbackModifier = if (edgeSwipeFallback) {
        Modifier.pointerInput(canGoBack, edgeSwipeFallback, gestureGain) {
            if (!canGoBack) return@pointerInput
            val edgePx = with(density) { EDGE_ZONE_DP.dp.toPx() }
            val width = size.width.toFloat()
            while (true) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedRight = down.position.x > width - edgePx
                    if (down.position.x > edgePx && !startedRight) return@awaitEachGesture
                    // Only a real horizontal drag takes over: a vertical scroll that happens to start
                    // near the edge must still scroll.
                    awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    } ?: return@awaitEachGesture
                    dragging = true
                    committed = false
                    showScrim = true
                    fromRight = startedRight
                    resetSamples(renderedPx.floatValue)
                    PredictiveBackTrace.begin(startedRight)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.isConsumed) break
                        change.consume()
                        setFinger(change.position.x, startedRight, width)
                    }
                }
                val travelled = renderedPx.floatValue
                dragging = false
                settling = true
                if (travelled > width * FALLBACK_COMMIT_FRACTION) {
                    animate(travelled, width, animationSpec = commitGlide) { value, _ ->
                        renderedPx.floatValue = value
                    }
                    settling = false
                    resetSamples(width)
                    committed = true
                    PredictiveBackTrace.finish("commit", width, travelled)
                    onBack()
                } else {
                    animate(travelled, 0f, animationSpec = cancelSpring) { value, _ ->
                        renderedPx.floatValue = value
                    }
                    settling = false
                    resetSamples(0f)
                    showScrim = false
                    PredictiveBackTrace.finish("cancel", width, travelled)
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .background(MaterialTheme.colorScheme.background)
            .then(pointerModifier)
            .then(fallbackModifier)
    ) {
        if (previous != null) {
            // Resident and deliberately untransformed. Shifting this layer (the usual parallax) puts
            // content from further right into the sliver at the screen edge, where it is cut
            // mid-glyph and reads as characters leaking through the page on top. No clip either: the
            // page on top is opaque and covers it, so this layer's cached display list is simply
            // replayed, whereas clipping here forced a re-record of the whole page every gesture
            // frame.
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
                            // Zero at both ends: a linear ramp flashes a grey layer on the first
                            // and the last frame of the gesture.
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
