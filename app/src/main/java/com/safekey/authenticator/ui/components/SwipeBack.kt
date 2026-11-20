package com.safekey.authenticator.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Edge swipe-back that reports its progress instead of drawing the screens
 * itself.
 *
 * The caller gets a 0..1 progress (0 = stay put, 1 = the screen sits a full
 * width to the right) and draws both the screen being dragged away and the one
 * it returns to. That two-layer drawing is what a predictive preview needs: a
 * single translated screen only reveals empty background.
 *
 * - the system back gesture feeds its progress in through PredictiveBackHandler
 * - an edge drag (~40dp) does the same where no system gesture arrives, so
 *   three-button navigation keeps working
 * - release past ~25% completes: the slide finishes, then [onBack] pops without
 *   a second animation; shorter drags slide back
 *
 * With [predictiveBack] off the content is drawn flat at progress 0 and back
 * navigation is left to the caller's BackHandler.
 */
@Composable
fun SwipeBackContainer(
    canGoBack: Boolean,
    predictiveBack: Boolean,
    onBack: () -> Unit,
    content: @Composable (progress: State<Float>) -> Unit
) {
    if (!predictiveBack) {
        content(remember { mutableFloatStateOf(0f) })
        return
    }

    val progress = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf(false) }

    /** Slide the current offset to [target]; the caller follows [progress]. */
    suspend fun slideTo(target: Float) {
        animate(
            initialValue = progress.floatValue,
            targetValue = target,
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        ) { value, _ ->
            progress.floatValue = value
        }
    }

    // System predictive back (Android 15+ in-app). Registered after the
    // BackHandler in MainActivity and the dispatcher is last-in-first-out, so
    // this one handles back whenever a previous screen exists.
    PredictiveBackHandler(enabled = canGoBack) { events ->
        try {
            events.collect { event ->
                // Direct state write, no coroutine: one frame per pointer move.
                progress.floatValue = event.progress.coerceIn(0f, 1f)
            }
            // Gesture completed: finish the slide, clear the preview, then pop.
            // The preview is cleared first so the caller's pop cannot leave the
            // same screen composed twice for a frame.
            settling = true
            slideTo(1f)
            progress.floatValue = 0f
            onBack()
            settling = false
        } catch (cancelled: CancellationException) {
            // Gesture abandoned. The animation has to run in a scope that is not
            // already cancelled, then the cancellation is passed on.
            scope.launch {
                settling = true
                slideTo(0f)
                settling = false
            }
            throw cancelled
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(canGoBack) {
                if (!canGoBack) return@pointerInput
                val edgePx = 40f * density
                val widthPx = size.width.toFloat()
                if (widthPx <= 0f) return@pointerInput
                var active = false
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        active = !settling && start.x < edgePx
                    },
                    onHorizontalDrag = { change, amount ->
                        if (active && !settling) {
                            change.consume()
                            progress.floatValue =
                                (progress.floatValue + amount / widthPx).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        if (active) {
                            active = false
                            scope.launch {
                                val done = progress.floatValue > 0.25f
                                settling = true
                                slideTo(if (done) 1f else 0f)
                                progress.floatValue = 0f
                                if (done) onBack()
                                settling = false
                            }
                        }
                    },
                    onDragCancel = {
                        if (active) {
                            active = false
                            scope.launch {
                                settling = true
                                slideTo(0f)
                                settling = false
                            }
                        }
                    }
                )
            }
    ) {
        content(progress)
    }
}
