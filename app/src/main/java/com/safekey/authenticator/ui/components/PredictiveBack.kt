package com.safekey.authenticator.ui.components

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.activity.compose.PredictiveBackHandler
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import androidx.activity.BackEventCompat
import com.safekey.authenticator.data.AppSettings
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Converts platform `progress` into a travel fraction when no touch coordinates are reported. */
private const val FALLBACK_PROGRESS_GAIN = 0.35f

/** Peak alpha of the scrim that covers the page underneath. */
private const val SCRIM_MAX_ALPHA = 0.35f

/** How close to an edge the finger must land before an edge drag counts as a back swipe. */
private const val EDGE_ZONE_DP = 40

/**
 * How long a *finished* gesture may go completely silent — with the offset still parked — before the
 * offset is walked back to rest.
 *
 * A gesture sometimes ends without any terminal callback — the platform's own gesture machinery takes
 * it over near the screen edges — and then nothing sends the page home: it parked mid-offset until the
 * next swipe. Silence is the only signal available, and the recovery is deliberately harmless: if the
 * finger is in fact still down, the next sample arrives within milliseconds and writes the finger
 * position straight back over it.
 */
private const val STALL_RECOVERY_MS = 1500L

/** How often the stall recovery checks. */
private const val STALL_POLL_MS = 300L

/**
 * Predictive-back navigation container.
 *
 * ## The driver is the one KernelSU's shell uses
 *
 * The gesture arrives through `androidx.navigationevent` — the same dispatcher miuix-nav (the
 * navigation shell behind KernelSU's back animation) reads its events from, and the reason this
 * app's Kotlin moved to 2.0: the library's published metadata is 2.0 and cannot be read by a 1.9
 * compiler. The events land in [BackGestureHandler] on the main thread, and the offset is written
 * *there*, in the frame the event belongs to. The older `androidx.activity` predictive-back wrapper
 * this replaces put a channel plus a coroutine between the platform callback and the app, so a
 * gesture frame could be a frame or more behind the finger, and bursts of samples could arrive
 * together after a stall.
 *
 * Touch coordinates are preferred when the platform reports them, because they are exact: a
 * measured swipe had the page edge precisely under the fingertip. `progress` is the fallback.
 *
 * ## Geometry and drawing (each rule here fixed a real defect)
 *
 * - the page underneath is resident (composed at rest) and untransformed. Composing it during the
 *   gesture dropped frames; shifting it (parallax) left its content cut mid-glyph in the sliver at
 *   the screen edge. It is only *drawn* on the exposed strip, and it sits in a layer of its own so
 *   that clip re-records two calls per frame instead of the whole page's draw;
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
    val renderedPx = remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    var showScrim by remember { mutableStateOf(false) }
    // Which edge the current gesture came from, locked for that gesture: the geometry must not flip
    // halfway through.
    var fromRight by remember { mutableStateOf(false) }
    // Wall clock of the newest gesture sample, and whether a settle animation is in flight; the two
    // together are what the stall recovery watches.
    var lastSampleAtMs = 0L
    var settling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

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

    suspend fun glideOut() {
        val from = renderedPx.floatValue
        settling = true
        try {
            animate(from, widthPx, animationSpec = commitGlide) { value, _ ->
                renderedPx.floatValue = value
            }
        } finally {
            settling = false
        }
        committed = true
        PredictiveBackTrace.finish("commit", widthPx, from)
        onBack()
    }

    suspend fun springBack() {
        val from = renderedPx.floatValue
        settling = true
        try {
            animate(from, 0f, animationSpec = cancelSpring) { value, _ ->
                renderedPx.floatValue = value
            }
        } finally {
            settling = false
        }
        showScrim = false
        PredictiveBackTrace.finish("cancel", widthPx, from)
    }

    // Self-healing for every way a gesture can fail to send the page home: a gesture that ends with no
    // terminal callback at all (the platform's own gesture machinery takes over near the screen edges),
    // a settle whose coroutine was already cancelled by the time it tried to animate, or a glide that
    // never finishes. The visible symptom is always the same — the page parked mid-offset with nothing
    // happening — so a single check covers them all: if the offset has sat still and no settle is in
    // flight, walk it home. Harmless when the finger is in fact still down, because the next sample
    // lands milliseconds later and writes the finger position straight back over it.
    LaunchedEffect(Unit) {
        while (true) {
            delay(STALL_POLL_MS)
            // Never while a drag is in flight: a silent drag means the finger is held still, and
            // springing the page home under a held finger is precisely the twitch a user reports as
            // "it convulses while I swipe". Only a gesture that has already ended can be stalled — a
            // settle that never ran, or a glide that never finished.
            if (!dragging &&
                !settling &&
                renderedPx.floatValue > 0.5f &&
                SystemClock.uptimeMillis() - lastSampleAtMs > STALL_RECOVERY_MS
            ) {
                PredictiveBackTrace.finish("stalled", widthPx, renderedPx.floatValue)
                springBack()
            }
        }
    }

    // Measurement only: while a gesture or its settle is in flight, tick once per displayed frame so
    // the readout can report the longest frame gap. It drives nothing, and it stops as soon as the
    // settle finishes — a permanently running frame loop would keep the app rendering every vsync for
    // nothing.
    LaunchedEffect(dragging, settling) {
        if (!dragging && !settling) return@LaunchedEffect
        while (true) {
            withFrameNanos { PredictiveBackTrace.frameTick() }
        }
    }

    // A dispatcher of our own for the navigationevent path, fed by the platform's own back-invoked
    // dispatcher. Nothing provides a dispatcher in this app (a plain single-activity Compose host has
    // no view-tree owner, and the library's `rememberNavigationEventDispatcherOwner` would throw in
    // that situation), so it is wired by hand here: the two calls below are its whole platform side.
    val platformBackDispatcher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity?.onBackInvokedDispatcher
    } else {
        null
    }
    val backDispatcher = remember { NavigationEventDispatcher() }
    val dispatcher = if (systemPredictiveBack) backDispatcher else null
    DisposableEffect(dispatcher, canGoBack, gestureGain) {
        if (dispatcher == null || !canGoBack) {
            onDispose { }
        } else {
            val handler = BackGestureHandler(
                onStarted = { event ->
                    dragging = true
                    lastSampleAtMs = SystemClock.uptimeMillis()
                    // Every gesture starts from rest. A commit whose pop never landed leaves
                    // `committed` set, and that pins the page at zero for the whole next drag.
                    committed = false
                    showScrim = false
                    fromRight = event.swipeEdge == NavigationEvent.EDGE_RIGHT
                    PredictiveBackTrace.begin(fromRight)
                },
                onProgressed = { event ->
                    if (dragging) {
                        val width = widthPx
                        // Deliberately not clamped from above: some platforms keep counting past the
                        // commit point, and clamping that away freezes the page while the finger
                        // keeps moving.
                        val fraction = event.progress.coerceAtLeast(0f)
                        val touchX = event.touchX
                        PredictiveBackTrace.platformEvent(fraction, touchX)
                        PredictiveBackTrace.latency(event.frameTimeMillis)
                        if (!showScrim && fraction > 0.02f) showScrim = true
                        renderedPx.floatValue = if (touchX > 0f && width > 0f) {
                            PredictiveBackTrace.driver("nav touchX")
                            offsetForFinger(touchX, fromRight, width)
                        } else {
                            PredictiveBackTrace.driver("nav progress")
                            (fraction * FALLBACK_PROGRESS_GAIN * gestureGain)
                                .coerceIn(0f, 1f) * width
                        }
                        lastSampleAtMs = SystemClock.uptimeMillis()
                        PredictiveBackTrace.followStep(renderedPx.floatValue)
                    }
                },
                onCommitted = {
                    // Also the discrete case (the back button): no gesture, no progress events.
                    if (!dragging) {
                        dragging = true
                        committed = false
                        PredictiveBackTrace.begin(false)
                    }
                    dragging = false
                    scope.launch { glideOut() }
                },
                onCancelled = {
                    dragging = false
                    scope.launch { springBack() }
                }
            )
            dispatcher.addHandler(handler, NavigationEventDispatcher.PRIORITY_DEFAULT)
            onDispose { handler.remove() }
        }
    }

    // Registered first, so the navigationevent input above takes the gesture — but kept as a fallback
    // for the cases where that input is not in play (a platform without the system gesture callbacks,
    // or the input failing to attach). The platform hands a gesture to exactly one registered callback,
    // so these two can never both drive it; the readout's driver column says which one did.
    if (systemPredictiveBack) {
        PredictiveBackHandler(enabled = canGoBack) { events: Flow<BackEventCompat> ->
            try {
                if (!dragging) {
                    dragging = true
                    committed = false
                    showScrim = false
                    lastSampleAtMs = SystemClock.uptimeMillis()
                    PredictiveBackTrace.begin(fromRight)
                }
                var edgeLocked = false
                events.collect { event ->
                    if (!edgeLocked) {
                        edgeLocked = true
                        fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                        PredictiveBackTrace.edgeLocked(fromRight)
                    }
                    if (dragging) {
                        val width = widthPx
                        val fraction = event.progress.coerceAtLeast(0f)
                        val touchX = event.touchX
                        PredictiveBackTrace.platformEvent(fraction, touchX)
                        PredictiveBackTrace.latency(0L)
                        if (!showScrim && fraction > 0.02f) showScrim = true
                        renderedPx.floatValue = if (touchX > 0f && width > 0f) {
                            PredictiveBackTrace.driver("act touchX")
                            offsetForFinger(touchX, fromRight, width)
                        } else {
                            PredictiveBackTrace.driver("act progress")
                            (fraction * FALLBACK_PROGRESS_GAIN * gestureGain)
                                .coerceIn(0f, 1f) * width
                        }
                        lastSampleAtMs = SystemClock.uptimeMillis()
                        PredictiveBackTrace.followStep(renderedPx.floatValue)
                    }
                }
                dragging = false
                scope.launch { glideOut() }
            } catch (cancelled: CancellationException) {
                // This coroutine is already cancelled, so anything suspending here — the settle
                // animation above all — is cancelled on the spot and the page parks wherever the
                // finger left it. The settle therefore has to run on a scope that is still alive.
                dragging = false
                scope.launch { springBack() }
                throw cancelled
            }
        }
    }

    // The platform hands a gesture to a single callback, and the most recently registered one wins. This
    // registration therefore sits *after* the androidx.activity handler below, which is what makes the
    // navigationevent input the driver that actually takes the gesture. It is handed the platform's own
    // OnBackInvokedDispatcher, wrapped in the library's input: those two calls are its whole platform
    // side, and neither is reachable from a composition local here (a plain single-activity Compose host
    // has no view-tree dispatcher owner, and the library's `rememberNavigationEventDispatcherOwner`
    // throws in exactly that situation).
    if (platformBackDispatcher != null) {
        DisposableEffect(backDispatcher, platformBackDispatcher) {
            // The priority argument is not optional for this to work: the single-argument addInput
            // registers the input with priority -1, i.e. bound to no priority level at all, and the
            // input only registers its platform callback when it is told that an enabled back handler
            // exists *at its own priority*. With priority -1 that notification never arrives and the
            // input sits there silently unregistered — which looks exactly like "the driver never
            // takes the gesture" no matter which order the callbacks are registered in.
            val input = runCatching {
                OnBackInvokedDefaultInput(platformBackDispatcher).also {
                    backDispatcher.addInput(it, NavigationEventDispatcher.PRIORITY_DEFAULT)
                }
            }.onFailure { PredictiveBackTrace.note("input fail:" + it::class.simpleName) }.getOrNull()
            if (input != null) PredictiveBackTrace.note("input ok")
            onDispose { if (input != null) runCatching { backDispatcher.removeInput(input) } }
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
    // the finger. It consumes its touches, because with no system gesture in play the drag is the
    // app's own and nothing else should act on it.
    val fallbackModifier = if (edgeSwipeFallback) {
        Modifier.fallbackEdgeSwipe(
            enabled = canGoBack,
            gain = gestureGain,
            onStart = {
                dragging = true
                committed = false
                showScrim = true
                lastSampleAtMs = SystemClock.uptimeMillis()
                PredictiveBackTrace.begin(fromRight)
            },
            onEdge = { rightEdge -> fromRight = rightEdge },
            onDrag = { travelPx ->
                renderedPx.floatValue = travelPx
                lastSampleAtMs = SystemClock.uptimeMillis()
            },
            onEnd = { traveled, width ->
                if (traveled > width * 0.15f) {
                    glideOut()
                } else {
                    springBack()
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
            // Resident and untransformed, but only ever drawn on the strip the travelling page has
            // left uncovered. Without that, a gesture frame pays for two full-screen pages plus a
            // full-screen scrim — about four screenfuls at 120 Hz. The page sits in a layer of its
            // own so the clip re-records two calls per frame instead of the page's entire draw.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawExposedStrip(renderedPx.floatValue)
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                ) {
                    previous()
                }
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
                        .drawWithContent {
                            drawExposedStrip(renderedPx.floatValue)
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
                    // Always to the right, whichever edge the gesture came from: the animation is
                    // deliberately not mirrored (the follow distance still tracks the finger, the
                    // direction does not).
                    translationX = if (committed) 0f else round(renderedPx.floatValue)
                }
                // Opaque floor under the page: nothing underneath can ever show through the page's
                // own transparent areas.
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}

/**
 * Bridges the dispatcher's back-gesture callbacks to plain lambdas. The callbacks are invoked
 * synchronously on the main thread by the dispatcher — no channel, no coroutine and no Compose
 * snapshot sits between the platform and the write that moves the page.
 */
private class BackGestureHandler(
    private val onStarted: (NavigationEvent) -> Unit,
    private val onProgressed: (NavigationEvent) -> Unit,
    private val onCommitted: () -> Unit,
    private val onCancelled: () -> Unit
) : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, true, false) {

    override fun onBackStarted(event: NavigationEvent) = onStarted(event)

    override fun onBackProgressed(event: NavigationEvent) = onProgressed(event)

    override fun onBackCompleted() = onCommitted()

    override fun onBackCancelled() = onCancelled()
}

/**
 * Draws only the strip the travelling page has left uncovered — always the leading (left) edge, since
 * the animation is not mirrored — with a pixel of slack towards the covered side so that rounding the
 * page's offset can never leave a backdrop line at the edge.
 */
private inline fun ContentDrawScope.drawExposedStrip(exposed: Float) {
    if (exposed <= 0f) return
    clipRect(0f, 0f, exposed + 1f, size.height) { this@drawExposedStrip.drawContent() }
}

/**
 * Edge-drag fallback for platforms without the system gesture: the app owns the gesture, so it
 * consumes its touches and drives the offset straight from the finger. Same geometry as the system
 * path.
 */
private fun Modifier.fallbackEdgeSwipe(
    enabled: Boolean,
    gain: Float,
    onStart: () -> Unit,
    onEdge: (rightEdge: Boolean) -> Unit,
    onDrag: (travelPx: Float) -> Unit,
    // Suspending: the settle animations run here, outside the restricted pointer gesture scope.
    onEnd: suspend (travelPx: Float, widthPx: Float) -> Unit
): Modifier = if (!enabled) {
    this
} else {
    this.pointerInput(enabled, gain) {
        val edgePx = EDGE_ZONE_DP.dp.toPx()
        val width = size.width.toFloat()
        while (true) {
            var traveled = -1f
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

/** Unwraps the activity from a possibly wrapped context; null when there is none. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
