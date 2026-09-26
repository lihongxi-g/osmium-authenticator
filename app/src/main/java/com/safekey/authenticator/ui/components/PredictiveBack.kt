package com.safekey.authenticator.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Predictive-back navigation container.
 *
 * The geometry and the way the driving value is consumed follow KernelSU's navigation shell
 * (miuix-nav, Apache-2.0 — see the sources page for attribution):
 *
 * - the page being left slides out linearly with the gesture, snapped to whole device pixels,
 *   so it tracks the finger 1:1;
 * - the page underneath parallaxes in by a quarter of its width with a light alpha falloff;
 * - a scrim darkens the revealed page (peaking mid-gesture, so both settled states are clean);
 * - the moving page is corner-clipped while it travels.
 *
 * The driving value is only ever read inside deferred `graphicsLayer { }` blocks, so a gesture
 * frame never triggers recomposition. A cancelled gesture springs back to rest (no overshoot);
 * a completed one hands the pop to [onBack] — the caller must then skip its own transition,
 * because this container has already animated the page out.
 *
 * @param systemPredictiveBack use the platform gesture (Android 13+ with predictive back on).
 * @param edgeSwipeFallback drive the same animation from an edge drag instead (older platforms).
 * @param navKey changes whenever the top screen changes; used to reset after a commit.
 * @param previous the screen underneath, rendered while the gesture runs. The caller freezes it
 *   at gesture start so the layout below cannot change mid-gesture.
 */
@Composable
fun PredictiveBackContainer(
    canGoBack: Boolean,
    systemPredictiveBack: Boolean,
    edgeSwipeFallback: Boolean,
    navKey: Any?,
    previous: (@Composable () -> Unit)?,
    onGestureStart: () -> Unit = {},
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val systemProgress = remember { Animatable(0f) }
    val dragProgress = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }

    val gesturing = dragging || settling || committed
    // Deferred read of the driving value: touching it inside graphicsLayer does not recompose.
    val progress: () -> Float = if (systemPredictiveBack) {
        { systemProgress.value }
    } else {
        { dragProgress.floatValue }
    }

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
                    systemProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
                // The platform reached its commit point: the page is already out of the way,
                // so popping now is visually a no-op.
                systemProgress.snapTo(1f)
                committed = true
                dragging = false
                onBack()
            } catch (cancelled: CancellationException) {
                dragging = false
                settling = true
                systemProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                settling = false
                throw cancelled
            }
        }
    }

    // After the pop the container returns to rest without animating anything: the frozen
    // "previous" screen is the screen that just became the top one, so both layers agree.
    LaunchedEffect(navKey) {
        if (committed) {
            systemProgress.snapTo(0f)
            dragProgress.floatValue = 0f
            committed = false
        }
    }

    val fallbackModifier = if (edgeSwipeFallback) {
        Modifier.pointerInput(canGoBack, edgeSwipeFallback) {
            if (!canGoBack) return@pointerInput
            val edgePx = with(density) { 40.dp.toPx() }
            val widthPx = size.width.toFloat()
            var total = 0f
            var edgeActive = false
            fun settleBack() {
                settling = true
                scope.launch {
                    animate(
                        initialValue = dragProgress.floatValue,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { value, _ -> dragProgress.floatValue = value }
                    settling = false
                }
            }
            detectHorizontalDragGestures(
                onDragStart = { start ->
                    total = 0f
                    edgeActive = start.x < edgePx
                    if (edgeActive) {
                        dragging = true
                        onGestureStart()
                    }
                },
                onHorizontalDrag = { change, amount ->
                    if (edgeActive) {
                        change.consume()
                        total = (total + amount).coerceAtLeast(0f)
                        // A full gesture step is a third of the width — the distance the
                        // platform's edge gesture travels — which keeps the motion 1:1.
                        dragProgress.floatValue = (total / (widthPx * 0.33f)).coerceIn(0f, 1f)
                    }
                },
                onDragEnd = {
                    if (edgeActive) {
                        edgeActive = false
                        dragging = false
                        if (total > widthPx * 0.18f) {
                            dragProgress.floatValue = 1f
                            committed = true
                            onBack()
                        } else {
                            settleBack()
                        }
                    }
                },
                onDragCancel = {
                    if (edgeActive) {
                        edgeActive = false
                        dragging = false
                        settleBack()
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Box(modifier = Modifier.fillMaxSize().then(fallbackModifier)) {
        if (gesturing && previous != null) {
            // The screen underneath: parallaxes in a quarter width with a light alpha falloff.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = 1f - progress()
                        translationX = -f * size.width * 0.25f
                        alpha = 1f - 0.1f * f
                    }
            ) {
                previous()
            }
            // Scrim over the revealed screen. It peaks mid-gesture and is zero at both ends,
            // so neither the start nor the commit frame flashes a dimmed page.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress()
                        alpha = 0.35f * 4f * p * (1f - p)
                    }
                    .background(Color.Black)
            )
        }
        // The screen on top: follows the gesture, corner-clipped while it travels.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = if (committed) 0f else round(progress() * size.width)
                }
                .clip(if (gesturing) RoundedCornerShape(16.dp) else RectangleShape)
        ) {
            content()
        }
    }
}
