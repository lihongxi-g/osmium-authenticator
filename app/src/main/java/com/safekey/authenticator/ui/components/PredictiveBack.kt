package com.safekey.authenticator.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.data.AppSettings
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

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
 * Stiffness of the spring that carries the page to the newest finger sample.
 *
 * Measured on the production device: a full swipe carries only about ten gesture samples, roughly
 * 50 px and 24 ms apart. Snapping to each sample therefore moves the page in visible jumps and
 * leaves it standing still in between — which is what reads as dropped frames and as being stuck.
 * Interpolating instead gives motion on every displayed frame; the seed velocity (below) keeps the
 * page under the finger rather than trailing it.
 */
private const val FOLLOW_STIFFNESS = 5000f

/** Angular frequency of the follow spring, used for the no-overshoot velocity bound. */
private val FOLLOW_OMEGA = sqrt(FOLLOW_STIFFNESS)

/** What the follow loop animates towards: the newest sample and the speed it is moving at. */
private data class Follow(val settling: Boolean, val targetPx: Float, val velocityPx: Float)

/**
 * Predictive-back navigation container.
 *
 * Input model and geometry are both the result of field measurements (see the project's
 * predictive-back notes). Two platform facts shape the design:
 *
 * - the gesture stream is *sparse* (about ten events per swipe) and carries the finger position, so
 *   the rendered offset follows the newest sample through a critically damped spring instead of
 *   jumping to it — and the sample's own velocity is fed in, so following does not add a lag;
 * - that stream is **not** delivered to the app as ordinary touch events while the system owns the
 *   gesture (`ptr 0` on device), so the extra pointer sampler below is only a bonus for platforms
 *   that do forward them.
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

    // Rendered offset in pixels: what the travelling page's translation reads. Animatable is the
    // right tool *here* — it is what turns sparse gesture samples into per-frame motion.
    val dragged = remember { Animatable(0f) }
    // Newest finger-derived offset. Cheap plain state, rewritten on every sample.
    val targetPx = remember { mutableFloatStateOf(0f) }
    // Speed of that target, in px/s, sampled between events and fed into the follow spring so the
    // page neither trails the finger nor shoots past it.
    var targetVelocityPx = 0f
    var lastTargetPx = 0f
    var lastSampleNanos = 0L
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
    val followSpring = remember {
        spring<Float>(dampingRatio = 1f, stiffness = FOLLOW_STIFFNESS)
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

    /** Publish a new target, deriving its speed from the gap since the previous sample. */
    fun setTarget(value: Float) {
        val now = System.nanoTime()
        val seconds = (now - lastSampleNanos) / 1_000_000_000f
        if (lastSampleNanos != 0L && seconds > 0.001f) {
            targetVelocityPx = (value - lastTargetPx) / seconds
        }
        lastTargetPx = value
        lastSampleNanos = now
        targetPx.floatValue = value
    }

    /** Forget the sample history so a new gesture cannot inherit the previous one's speed. */
    fun resetTargetHistory() {
        lastSampleNanos = 0L
        targetVelocityPx = 0f
        lastTargetPx = dragged.value
    }

    // The per-frame follow. Restarts on every new sample, seeded with the sample's velocity clamped
    // to the no-overshoot bound of a critically damped spring — the same trick the navigation shell
    // this geometry comes from uses for its settle.
    LaunchedEffect(Unit) {
        snapshotFlow { Follow(settling, targetPx.floatValue, targetVelocityPx) }
            .collectLatest { (isSettling, target, velocity) ->
                if (isSettling) return@collectLatest
                val gap = target - dragged.value
                val bound = FOLLOW_OMEGA * abs(gap)
                val seed = velocity.coerceIn(-bound, bound)
                dragged.animateTo(target, followSpring, initialVelocity = seed)
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
                    // Only drive the offset once the platform has confirmed a back gesture: a drag
                    // that starts near the edge can also be a list scrolling.
                    if (dragging && !settling) {
                        fromRight = startedRight
                        setTarget(offsetForFinger(change.position.x, startedRight, width))
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
                    resetTargetHistory()
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
                    // Only publish a target; the follow loop above turns it into motion. Our own
                    // pointer samples win when they exist, then the platform's touch position, then
                    // its progress as the last resort.
                    if (fingerPx.floatValue >= 0f) {
                        PredictiveBackTrace.driver("pointer")
                    } else if (event.touchX > 0f && widthPx > 0f) {
                        PredictiveBackTrace.driver("touchX")
                        setTarget(offsetForFinger(event.touchX, fromRight, widthPx))
                    } else {
                        PredictiveBackTrace.driver("progress")
                        setTarget(
                            (fraction * FALLBACK_PROGRESS_GAIN * gestureGain)
                                .coerceIn(0f, 1f) * widthPx
                        )
                    }
                }
                // Committed. Target the far edge, let the settle glide finish, then pop — otherwise
                // the pop snaps the page back into place.
                val from = dragged.value
                dragging = false
                settling = true
                targetPx.floatValue = widthPx
                dragged.animateTo(widthPx, commitGlide)
                settling = false
                committed = true
                PredictiveBackTrace.finish("commit", widthPx, from)
                onBack()
            } catch (cancelled: CancellationException) {
                val from = dragged.value
                dragging = false
                settling = true
                dragged.animateTo(0f, cancelSpring)
                settling = false
                targetPx.floatValue = 0f
                resetTargetHistory()
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
            dragged.snapTo(0f)
            targetPx.floatValue = 0f
            resetTargetHistory()
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
                    resetTargetHistory()
                    PredictiveBackTrace.begin(startedRight)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.isConsumed) break
                        change.consume()
                        setTarget(offsetForFinger(change.position.x, startedRight, width))
                    }
                }
                val travelled = targetPx.floatValue
                dragging = false
                settling = true
                if (travelled > width * FALLBACK_COMMIT_FRACTION) {
                    targetPx.floatValue = width
                    dragged.animateTo(width, commitGlide)
                    settling = false
                    committed = true
                    PredictiveBackTrace.finish("commit", width, travelled)
                    onBack()
                } else {
                    dragged.animateTo(0f, cancelSpring)
                    settling = false
                    targetPx.floatValue = 0f
                    resetTargetHistory()
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
                                (dragged.value / widthPx).coerceIn(0f, 1f)
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
                        val pass = round(dragged.value)
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
