package com.safekey.authenticator.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Edge swipe-back with finger-following animation (跟手):
 * - drag starts only from the ~40dp left edge zone so list scrolling is unaffected
 * - while dragging, the offset is written straight into snapshot state
 *   (zero per-frame coroutines — keeps 120Hz scrolling smooth)
 * - release past ~25% width completes the back navigation; otherwise it bounces back
 *
 * With [predictiveBack] on (the appearance setting, default), the system back
 * gesture feeds its progress in through PredictiveBackHandler, so the gesture
 * previews the transition while the finger is still down. With it off the
 * screens are drawn flat and back navigation only happens on release — the
 * caller's BackHandler pops with the usual transition.
 */
@Composable
fun SwipeBackContainer(
    canGoBack: Boolean,
    predictiveBack: Boolean = true,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!predictiveBack) {
        content()
        return
    }

    val scope = rememberCoroutineScope()
    var dragPx by remember { mutableStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val settle = remember { Animatable(0f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()

        // System predictive back (Android 15+ in-app). Registered after the
        // BackHandler in MainActivity, and the dispatcher is last-in-first-out,
        // so this one handles back whenever there is a previous screen.
        PredictiveBackHandler(enabled = canGoBack && widthPx > 0f) { progress ->
            var last = 0f
            try {
                progress.collect { event ->
                    last = event.progress.coerceIn(0f, 1f)
                    dragPx = last * widthPx // direct state write, no coroutine
                }
                // Gesture completed: pop FIRST, then animate the old screen out
                // (popping inside the animation coroutine can mutate the nav
                // stack mid-transition).
                settling = true
                onBack()
                scope.launch {
                    settle.snapTo(last * widthPx)
                    settle.animateTo(widthPx, tween(200, easing = FastOutSlowInEasing))
                    dragPx = 0f
                    settling = false
                }
            } catch (cancelled: CancellationException) {
                // Gesture abandoned (finger lifted short / cancelled): slide
                // back to where the screen was, then rethrow.
                settling = true
                scope.launch {
                    settle.snapTo(last * widthPx)
                    settle.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                    dragPx = 0f
                    settling = false
                }
                throw cancelled
            }
        }

        val offsetPx = if (settling) settle.value else dragPx

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(canGoBack, widthPx) {
                    if (!canGoBack) return@pointerInput
                    val edgePx = 40f * density
                    var total = 0f
                    var edgeActive = false
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            total = 0f
                            edgeActive = start.x < edgePx
                        },
                        onHorizontalDrag = { change, amount ->
                            if (edgeActive && !settling) {
                                change.consume()
                                total = (total + amount).coerceAtLeast(0f)
                                dragPx = total // direct state write, no coroutine
                            }
                        },
                        onDragEnd = {
                            if (edgeActive && total > 0f) {
                                edgeActive = false
                                if (total > widthPx * 0.25f) {
                                    // Popping inside the animation coroutine let
                                    // a second gesture or back press mutate the
                                    // nav stack mid-transition — one of the
                                    // triggers of the ComposerImpl pendingStack
                                    // underflow crash.
                                    settling = true
                                    onBack()
                                    scope.launch {
                                        settle.snapTo(total)
                                        settle.animateTo(widthPx, tween(200, easing = FastOutSlowInEasing))
                                        dragPx = 0f
                                        settling = false
                                    }
                                } else {
                                    settling = true
                                    scope.launch {
                                        settle.snapTo(total)
                                        settle.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                        dragPx = 0f
                                        settling = false
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            if (edgeActive) {
                                edgeActive = false
                                settling = true
                                scope.launch {
                                    settle.snapTo(total)
                                    settle.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                    dragPx = 0f
                                    settling = false
                                }
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(offsetPx.roundToInt(), 0) }
            ) {
                content()
            }
        }
    }
}
