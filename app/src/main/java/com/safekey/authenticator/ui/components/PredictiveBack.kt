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
import androidx.compose.ui.platform.LocalView
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

/**
 * Peak alpha of the scrim that covers the page underneath, matching the reference navigation shell's
 * `dimAmount` default (KernelSU renders through miuix, whose `NavDisplayEffects.dimAmount` is 0.5).
 */
private const val SCRIM_MAX_ALPHA = 0.5f

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
 * The gesture is meant to arrive through `androidx.navigationevent` — the same dispatcher miuix-nav
 * (the navigation shell behind KernelSU's back animation) reads its events from, and the reason this
 * app's Kotlin moved to 2.0: the library's published metadata is 2.0 and cannot be read by a 1.9
 * compiler. The events land in [BackGestureHandler] on the main thread, and the offset is written
 * *there*, in the frame the event belongs to. The `androidx.activity` predictive-back wrapper — still
 * composed as a net, and *above* this container's own registration — puts a channel plus a coroutine
 * between the platform callback and the app instead, so on that path a gesture frame can be a frame or
 * more behind the finger and bursts of samples can arrive together after a stall.
 *
 * Both paths register on the platform's dispatcher at the same priority, where a same-priority
 * registration replaces the previous callback: the newest registration takes the gesture. That is why
 * the fallback is composed first here, why the input is re-registered on every navigation, and why the
 * fallback is retired for good once the navigationevent input has been handed a gesture — on a real
 * device the driver column read `act` for a whole session because the fallback re-registered after us
 * on the one frame that mattered. The readout's `driver` field is the proof either way.
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
    // Snapshot state on purpose: the layer block below is its only reader, and a layer-block read
    // invalidates the layer without recomposing anything — which is what we want at the pop.
    var committed by remember { mutableStateOf(false) }
    // Everything else the gesture writes lives in a plain holder. None of it is read during
    // composition, and keeping it in snapshot state meant every start and every end of a gesture
    // recomposed this container — and with it both resident pages, since the content and preview
    // lambdas take an unstable ViewModel — landing on exactly the frame the drag begins and the
    // frame the settle begins.
    val g = remember { GestureState() }
    // How often this container's body has run since the last gesture started; readout only, and the
    // number that says whether that split actually holds on a device.
    g.compositions++
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
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

    /**
     * Retires the androidx.activity fallback for the rest of the session once the navigationevent
     * input has actually been handed a gesture.
     *
     * Both paths register on the platform's dispatcher at the same priority, where the newest
     * registration takes the gesture — so a fallback that is never taken away keeps winning races
     * (every push re-registers it, and it registers after us on that frame). Once this path has
     * demonstrably received a gesture we know it is live, so the alternative is no longer a net and
     * only a competitor. Called once the settle has finished, never mid-gesture: flipping it
     * recomposes this container, which is the one thing that must not happen on a gesture frame.
     */
    fun retireFallbackIfProven() {
        if (g.sawNav && !BackGestureActivity.navDelivered) BackGestureActivity.navDelivered = true
    }

    suspend fun glideOut() {
        val from = renderedPx.floatValue
        g.settling = true
        // Held across the pop as well, so the 2 Hz tick cannot rebuild the account list in the
        // middle of the settle; the reset effect below releases it once the pop has composed.
        BackGestureActivity.active = true
        try {
            animate(from, widthPx, animationSpec = commitGlide) { value, _ ->
                renderedPx.floatValue = value
            }
        } finally {
            g.settling = false
        }
        committed = true
        PredictiveBackTrace.finish("commit", widthPx, from, g.compositions)
        retireFallbackIfProven()
        onBack()
    }

    suspend fun springBack() {
        val from = renderedPx.floatValue
        g.settling = true
        BackGestureActivity.active = true
        try {
            animate(from, 0f, animationSpec = cancelSpring) { value, _ ->
                renderedPx.floatValue = value
            }
        } finally {
            g.settling = false
            // Nothing is pending on this path — no pop — so nothing else will release it. Unless a
            // new gesture has already taken over, in which case it still owns the flag.
            if (!g.dragging) BackGestureActivity.active = false
        }
        PredictiveBackTrace.finish("cancel", widthPx, from, g.compositions)
        retireFallbackIfProven()
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
            // The finished readout is handed to the developer screen from here rather than from the
            // gesture itself: that state write recomposes the whole developer screen, and from inside
            // the settle it showed up as an 88 ms "settle frame" that was only the readout redrawing
            // itself. Never while something is in flight, or back-to-back swipes would pay for it.
            if (!g.dragging && !g.settling) PredictiveBackTrace.publish()
            val silentMs = SystemClock.uptimeMillis() - g.lastSampleAtMs
            if (g.dragging) {
                // Never walk the page home under a finger: a silent drag means the finger is held
                // still, and springing the page home then is precisely the twitch a user reports as
                // "it convulses while I swipe". The flag is a different matter — a drag this stale is
                // not being held, and the countdown must not stay frozen for the rest of the
                // session. (The drag state itself is left alone: clearing it would throw away the
                // samples of a finger that is in fact still down.)
                if (silentMs > STALL_RECOVERY_MS * 4) BackGestureActivity.active = false
            } else if (!g.settling) {
                if (renderedPx.floatValue <= 0.5f) {
                    // Nothing pending and nothing parked: no gesture owns the screen any more, so
                    // the countdown may tick again. Also the net under a commit whose pop never
                    // arrived, and under any path that set the flag and then died.
                    BackGestureActivity.active = false
                } else if (silentMs > STALL_RECOVERY_MS) {
                    // A settle that never ran, or a glide that never finished: the page is parked
                    // mid-offset with nothing coming. Only a gesture that has already ended can be
                    // stalled this way.
                    PredictiveBackTrace.finish("stalled", widthPx, renderedPx.floatValue, g.compositions)
                    springBack()
                }
            }
        }
    }

    // The settle releases this flag itself, and the reset effect below releases it once a commit's
    // pop has composed; writes happen straight from the gesture callbacks rather than from an effect
    // watching the gesture state, because an effect only runs after the frame it is keyed on — the
    // first frames of a drag could still be handed a tick that rebuilds the whole account list.

    // The androidx.activity predictive-back wrapper, kept only as the net under the navigationevent
    // input below — the path this container is built around, and the only one whose events reach the
    // offset in the frame they were produced in instead of through a channel and a coroutine.
    //
    // It is composed *above* our own registration deliberately, and that is not cosmetic: both paths
    // register on the platform's dispatcher at the same priority, where a same-priority registration
    // replaces the previous callback, so the *last* registration is the one a gesture is handed to.
    // This composable registers from inside its own effect while our input registers from a
    // notification that `addHandler`/`addInput` deliver synchronously (navigationevent's
    // `updateBackInvokedCallbackState`), so declaration order is what decides it — and declared here,
    // it can never end up after us.
    //
    // Retired for the rest of the session once the navigationevent input has actually been handed a
    // gesture: at that point it is proven live, the alternative has nothing left to fall back to, and
    // leaving it registered only wins races it should lose. The readout's driver column says whether
    // that happened.
    if (systemPredictiveBack && !BackGestureActivity.navDelivered) {
        PredictiveBackHandler(enabled = canGoBack) { events: Flow<BackEventCompat> ->
            try {
                if (!g.dragging) {
                    g.dragging = true
                    BackGestureActivity.active = true
                    committed = false
                    g.lastSampleAtMs = PredictiveBackTrace.sample()
                    g.compositions = 0
                    PredictiveBackTrace.begin(g.fromRight, view.display?.refreshRate ?: 0f)
                }
                var edgeLocked = false
                events.collect { event ->
                    if (!edgeLocked) {
                        edgeLocked = true
                        g.fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                        PredictiveBackTrace.edgeLocked(g.fromRight)
                    }
                    if (g.dragging) {
                        val width = widthPx
                        val fraction = event.progress.coerceAtLeast(0f)
                        val touchX = event.touchX
                        PredictiveBackTrace.platformEvent(fraction, touchX)
                        PredictiveBackTrace.latency(0L)
                        renderedPx.floatValue = if (touchX > 0f && width > 0f) {
                            PredictiveBackTrace.driver("act touchX")
                            offsetForFinger(touchX, g.fromRight, width)
                        } else {
                            PredictiveBackTrace.driver("act progress")
                            (fraction * FALLBACK_PROGRESS_GAIN * gestureGain)
                                .coerceIn(0f, 1f) * width
                        }
                        g.lastSampleAtMs = PredictiveBackTrace.sample()
                    }
                }
                g.dragging = false
                scope.launch { glideOut() }
            } catch (cancelled: CancellationException) {
                // This coroutine is already cancelled, so anything suspending here — the settle
                // animation above all — is cancelled on the spot and the page parks wherever the
                // finger left it. The settle therefore has to run on a scope that is still alive.
                g.dragging = false
                scope.launch { springBack() }
                throw cancelled
            }
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
                    g.dragging = true
                    // The platform has just handed this path a gesture: it is live on this device.
                    g.sawNav = true
                    // Straight away, not via an effect settling on the next frame: from here until
                    // the settle finishes, no tick may rebuild the account list.
                    BackGestureActivity.active = true
                    g.lastSampleAtMs = PredictiveBackTrace.sample()
                    // Every gesture starts from rest. A commit whose pop never landed leaves
                    // `committed` set, and that pins the page at zero for the whole next drag.
                    committed = false
                    g.fromRight = event.swipeEdge == NavigationEvent.EDGE_RIGHT
                    g.compositions = 0
                    PredictiveBackTrace.begin(g.fromRight, view.display?.refreshRate ?: 0f)
                },
                onProgressed = { event ->
                    if (g.dragging) {
                        val width = widthPx
                        // Deliberately not clamped from above: some platforms keep counting past the
                        // commit point, and clamping that away freezes the page while the finger
                        // keeps moving.
                        val fraction = event.progress.coerceAtLeast(0f)
                        val touchX = event.touchX
                        PredictiveBackTrace.platformEvent(fraction, touchX)
                        PredictiveBackTrace.latency(event.frameTimeMillis)
                        renderedPx.floatValue = if (touchX > 0f && width > 0f) {
                            PredictiveBackTrace.driver("nav touchX")
                            offsetForFinger(touchX, g.fromRight, width)
                        } else {
                            PredictiveBackTrace.driver("nav progress")
                            (fraction * FALLBACK_PROGRESS_GAIN * gestureGain)
                                .coerceIn(0f, 1f) * width
                        }
                        g.lastSampleAtMs = PredictiveBackTrace.sample()
                    }
                },
                onCommitted = {
                    // Also the discrete case (the back button): no gesture, no progress events.
                    if (!g.dragging) {
                        g.dragging = true
                        committed = false
                        g.compositions = 0
                        PredictiveBackTrace.begin(false, view.display?.refreshRate ?: 0f)
                    }
                    g.dragging = false
                    BackGestureActivity.active = true
                    scope.launch { glideOut() }
                },
                onCancelled = {
                    g.dragging = false
                    BackGestureActivity.active = true
                    scope.launch { springBack() }
                }
            )
            dispatcher.addHandler(handler, NavigationEventDispatcher.PRIORITY_DEFAULT)
            onDispose { handler.remove() }
        }
    }

    if (platformBackDispatcher != null) {
        // Re-registered on every navigation and once more when the activity fallback retires.
        //
        // The keys are the mechanism, not bookkeeping. Our input registers its platform callback from
        // a notification delivered synchronously by `addHandler`/`addInput`, and this app registers its
        // fallback (the androidx.activity composable above) from inside that composable's own effect —
        // so on any frame where both re-register, declaration order decides who is on top. The
        // fallback re-registers whenever `canGoBack` flips, i.e. on the push out of the root and on the
        // pop back to it; keyed on `canGoBack` (and `navKey`, for any other registration it may do),
        // our input is re-created after it in the same frame and lands on top again. Keyed on
        // `navDelivered` too, because retiring the fallback must be followed by a fresh registration:
        // if the platform's same-priority slot was holding the fallback's callback, removing it leaves
        // that slot empty, and an input that still believes it is registered would never re-register —
        // leaving the back gesture with no consumer at all.
        DisposableEffect(backDispatcher, platformBackDispatcher, canGoBack, navKey, BackGestureActivity.navDelivered) {
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
        }
        // The pop has composed, so this page is the top page and nothing owns the screen any more.
        if (!g.dragging && !g.settling) BackGestureActivity.active = false
    }

    // Edge-drag fallback for platforms without the system gesture: same geometry, driven straight by
    // the finger. It consumes its touches, because with no system gesture in play the drag is the
    // app's own and nothing else should act on it.
    val fallbackModifier = if (edgeSwipeFallback) {
        Modifier.fallbackEdgeSwipe(
            enabled = canGoBack,
            gain = gestureGain,
            onStart = {
                g.dragging = true
                BackGestureActivity.active = true
                committed = false
                g.lastSampleAtMs = PredictiveBackTrace.sample()
                g.compositions = 0
                PredictiveBackTrace.begin(g.fromRight, view.display?.refreshRate ?: 0f)
            },
            onEdge = { rightEdge -> g.fromRight = rightEdge },
            onDrag = { travelPx ->
                renderedPx.floatValue = travelPx
                g.lastSampleAtMs = PredictiveBackTrace.sample()
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
            // Resident, like the preview above it, and drawn with no layer of its own. Two reasons:
            // `graphicsLayer { alpha }` below 1 makes Compose render the node into an offscreen
            // buffer, i.e. a fresh full-screen layer every frame of the gesture; and gating the whole
            // subtree on a composition state inserted and removed it mid-gesture on top of that. A
            // translucent rect clipped to the exposed strip draws the same picture with neither cost.
            // The curve is linear in the depth of the covered layer (miuix's `scrimFraction =
            // relativeDepth`), so the scrim lightens as the page underneath is revealed: more
            // revealed, brighter — a hump-shaped curve would darken the whole first half.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val exposed = renderedPx.floatValue
                        if (exposed > 0f) {
                            val p = if (widthPx > 0f) (exposed / widthPx).coerceIn(0f, 1f) else 0f
                            clipRect(
                                0f,
                                0f,
                                (exposed + 1f).coerceAtMost(size.width),
                                size.height
                            ) {
                                drawRect(Color.Black, alpha = SCRIM_MAX_ALPHA * (1f - p))
                            }
                        }
                    }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Cached in a layer of its own: with only a translation, the page's whole content
                // (a list of cards) is otherwise re-recorded on every frame of the drag. Offscreen
                // makes each frame a composite of an already-rendered texture, re-recorded only when
                // the content itself changes (the countdown tick, not the gesture).
                // Two modifiers on purpose: the parameterised overload and the lambda overload cannot
                // be combined in one call (the trailing lambda resolves to a positional parameter that
                // is not the block), and the layer modifiers merge on the same node anyway.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .graphicsLayer {
                    // While committed, the layer underneath is already the top page, so the offset
                    // must be zero no matter what the driver still holds. Always to the right,
                    // whichever edge the gesture came from: the animation is deliberately not
                    // mirrored (the follow distance still tracks the finger, the direction does not).
                    translationX = if (committed) 0f else round(renderedPx.floatValue)
                    // The one place that sees every frame of a gesture, its settle and the pop — so
                    // the readout's frame-gap detector is fed from here. It used to be a
                    // `withFrameNanos` loop, which paid a frame callback, a coroutine resume and a
                    // dispatch per frame to measure the very work it was adding to.
                    PredictiveBackTrace.frameTick(g.settling)
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
    // Always draws, even at rest: the clip is one pixel then, which is invisible because the
    // travelling page covers the full width. What it buys is that the preview's GPU layer is
    // allocated while the screen is idle instead of on the first frame of the first gesture — a
    // full-screen texture allocation (about 14 MB at 1264x2800) is a dropped frame when it lands
    // there, and the first drag after entering a screen is exactly where users notice one.
    clipRect(0f, 0f, (exposed + 1f).coerceAtMost(size.width), size.height) { this@drawExposedStrip.drawContent() }
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

/**
 * True while a back gesture owns the screen.
 *
 * The accounts list refreshes twice a second (a fresh `List<AccountUi>`, codes included), which
 * recomposes every visible card. Landing one of those on a gesture frame pushes it over the 8.33 ms a
 * 120 Hz display allows, so the drag drops a frame — intermittently, which is exactly how users
 * describe it. The ticker reads this flag and waits; the gesture lasts a few hundred milliseconds.
 *
 * Deliberately *not* snapshot state: its only reader is the ViewModel's ticker coroutine, and a
 * snapshot write from inside a composition-adjacent scope is an invalidation nobody consumes.
 */
object BackGestureActivity {
    @Volatile var active: Boolean = false

    /**
     * True once the navigationevent input — the path this app's gesture handling is built on — has
     * actually been handed a back gesture. Until then the androidx.activity fallback stays registered
     * as a net; after that it would only win registration races, so the container stops composing it.
     * Snapshot state because that decision is read during composition (once per session).
     */
    var navDelivered by mutableStateOf(false)
}

/**
 * Everything a gesture writes about itself, none of it read during composition.
 *
 * A plain holder rather than snapshot state: writing these used to invalidate the container, whose
 * body re-invokes the content and preview lambdas — which take an unstable ViewModel, so they cannot
 * be skipped while it is being recomposed — and the recomposition landed on the first frame of the
 * drag and the frame the settle starts, i.e. on the two frames a user actually sees.
 */
private class GestureState {
    /** A drag is in flight. */
    var dragging = false
    /** A settle animation (commit glide or cancel spring) is in flight. */
    var settling = false
    /** Which edge this gesture started from, locked for the gesture: the geometry must not flip
     *  halfway through. */
    var fromRight = false
    /** Wall clock of the newest sample; the stall recovery watches this. */
    var lastSampleAtMs = 0L
    /** How often the container's body has run since the last gesture began (readout only). */
    var compositions = 0
    /** Set once this session's navigationevent handler has been handed a real gesture. */
    var sawNav = false
}
