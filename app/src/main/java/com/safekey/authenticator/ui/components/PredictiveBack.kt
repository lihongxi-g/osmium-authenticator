package com.safekey.authenticator.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.window.BackEvent
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
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
import com.safekey.authenticator.data.AppSettings
import kotlin.math.round
import kotlinx.coroutines.launch

/** Converts platform `progress` into a travel fraction when no touch coordinates are reported. */
private const val FALLBACK_PROGRESS_GAIN = 0.35f

/** Peak alpha of the scrim that covers the page underneath. */
private const val SCRIM_MAX_ALPHA = 0.35f

/** How close to an edge the finger must land before an edge drag counts as a back swipe. */
private const val EDGE_ZONE_DP = 40

/**
 * Predictive-back navigation container.
 *
 * ## Input: the platform callback itself, not a compatibility wrapper
 *
 * The back gesture is taken straight from `OnBackInvokedDispatcher` (API 33+). The navigation shell
 * KernelSU uses reads the very same stream, but through `androidx.navigationevent`, whose Kotlin
 * metadata requires Kotlin 2.x — this app builds on Kotlin 1.9, and pulling that library in would
 * mean rebuilding the whole toolchain. Registering the platform callback directly gives the same
 * events without the extra layer, and it drops the one thing the older `androidx.activity`
 * predictive-back wrapper adds: a channel plus a coroutine between the platform callback and the
 * app. Here the offset is written *inside* `onBackProgressed`, on the main thread, in the frame the
 * event belongs to, which is as immediate as a gesture can get on Android.
 *
 * Touch coordinates are used when the platform reports them (API 34+), because they are exact: a
 * measured swipe had the page edge precisely under the fingertip. Below that, and in the reported
 * readout, the platform's `progress` is the fallback.
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
        animate(from, widthPx, animationSpec = commitGlide) { value, _ ->
            renderedPx.floatValue = value
        }
        committed = true
        PredictiveBackTrace.finish("commit", widthPx, from)
        onBack()
    }

    suspend fun springBack() {
        val from = renderedPx.floatValue
        animate(from, 0f, animationSpec = cancelSpring) { value, _ ->
            renderedPx.floatValue = value
        }
        showScrim = false
        PredictiveBackTrace.finish("cancel", widthPx, from)
    }

    // Measurement only: while a drag is in flight, tick once per displayed frame so the readout can
    // report the longest frame gap. It drives nothing, and it stops as soon as the drag does — a
    // permanently running frame loop would keep the app rendering every vsync for nothing.
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (true) {
            withFrameNanos { PredictiveBackTrace.frameTick() }
        }
    }

    if (systemPredictiveBack) {
        DisposableEffect(activity, canGoBack, gestureGain) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || activity == null || !canGoBack) {
                onDispose { }
            } else {
                val callback = PlatformBackCallback(
                    onStarted = { event ->
                        dragging = true
                        // Every gesture starts from rest. A commit whose pop never landed leaves
                        // `committed` set, and that pins the page at zero for the whole next drag.
                        committed = false
                        showScrim = false
                        fromRight = event.swipeEdge == BackEvent.EDGE_RIGHT
                        PredictiveBackTrace.begin(fromRight)
                    },
                    onProgressed = { event ->
                        if (dragging) {
                            val width = widthPx
                            // Deliberately not clamped from above: some platforms keep counting past
                            // the commit point, and clamping that away freezes the page while the
                            // finger keeps moving.
                            val fraction = event.progress.coerceAtLeast(0f)
                            val touchX = if (Build.VERSION.SDK_INT >= 34) event.touchX else 0f
                            PredictiveBackTrace.platformEvent(fraction, touchX)
                            PredictiveBackTrace.latency(event.frameTimeMillis)
                            if (!showScrim && fraction > 0.02f) showScrim = true
                            renderedPx.floatValue = if (touchX > 0f && width > 0f) {
                                PredictiveBackTrace.driver("touchX")
                                offsetForFinger(touchX, fromRight, width)
                            } else {
                                PredictiveBackTrace.driver("progress")
                                (fraction * FALLBACK_PROGRESS_GAIN * gestureGain)
                                    .coerceIn(0f, 1f) * width
                            }
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
                val unregister = registerBackCallback(activity, callback)
                onDispose { unregister() }
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

    // Edge-drag fallback for platforms without the platform callback: same geometry, driven straight
    // by the finger. It consumes its touches, because with no system gesture in play the drag is the
    // app's own and nothing else should act on it.
    val fallbackModifier = if (edgeSwipeFallback) {
        Modifier.fallbackEdgeSwipe(
            enabled = canGoBack,
            gain = gestureGain,
            onStart = {
                dragging = true
                committed = false
                showScrim = true
                PredictiveBackTrace.begin(fromRight)
            },
            onEdge = { rightEdge -> fromRight = rightEdge },
            onDrag = { travelPx -> renderedPx.floatValue = travelPx },
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
                        drawExposedStrip(renderedPx.floatValue, fromRight)
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
                            drawExposedStrip(renderedPx.floatValue, fromRight)
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

/**
 * Draws only the strip the travelling page has left uncovered, with a pixel of slack towards the
 * covered side so that rounding the page's offset can never leave a backdrop line at the edge.
 */
private inline fun ContentDrawScope.drawExposedStrip(exposed: Float, fromRight: Boolean) {
    if (fromRight) {
        val left = size.width - exposed
        if (left >= size.width) return
        clipRect(left - 1f, 0f, size.width, size.height) { this@drawExposedStrip.drawContent() }
    } else {
        if (exposed <= 0f) return
        clipRect(0f, 0f, exposed + 1f, size.height) { this@drawExposedStrip.drawContent() }
    }
}

/**
 * The platform's back-gesture callbacks, forwarded verbatim. They arrive on the main thread: no
 * channel, no coroutine and no Compose snapshot sits between the system and the caller.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PlatformBackCallback(
    private val onStarted: (BackEvent) -> Unit,
    private val onProgressed: (BackEvent) -> Unit,
    private val onCommitted: () -> Unit,
    private val onCancelled: () -> Unit
) : OnBackInvokedCallback {
    override fun onBackStarted(backEvent: BackEvent) = onStarted(backEvent)
    override fun onBackProgressed(backEvent: BackEvent) = onProgressed(backEvent)
    override fun onBackInvoked() = onCommitted()
    override fun onBackCancelled() = onCancelled()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun registerBackCallback(activity: Activity, callback: OnBackInvokedCallback): () -> Unit {
    val dispatcher = activity.onBackInvokedDispatcher
    dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
    return { runCatching { dispatcher.unregisterOnBackInvokedCallback(callback) } }
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

/**
 * Edge-drag fallback for platforms without the platform callback: the app owns the gesture, so it
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
