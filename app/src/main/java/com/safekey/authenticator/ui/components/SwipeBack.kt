package com.safekey.authenticator.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Edge swipe-back with finger-following animation (跟手):
 * - drag starts only from the ~40dp left edge zone so list scrolling is unaffected
 * - while dragging, the offset is written straight into snapshot state
 *   (zero per-frame coroutines — keeps 120Hz scrolling smooth)
 * - release past ~25% width completes the back navigation; otherwise it bounces back
 */
@Composable
fun SwipeBackContainer(
    canGoBack: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    var dragPx by remember { mutableStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val settle = remember { Animatable(0f) }

    val offsetPx = if (settling) settle.value else dragPx

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(canGoBack) {
                if (!canGoBack) return@pointerInput
                val edgePx = 40f * density
                val widthPx = size.width.toFloat()
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
                                // Pop FIRST, then animate the old screen out.
                                // Popping inside the animation coroutine let a
                                // second gesture or back press mutate the nav
                                // stack mid-transition — one of the triggers of
                                // the ComposerImpl pendingStack underflow crash.
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
