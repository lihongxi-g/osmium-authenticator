package com.safekey.authenticator.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Edge swipe-back with finger-following animation (跟手):
 * - drag starts only from the ~40dp left edge zone so list scrolling is unaffected
 * - the whole screen tracks the finger via Animatable offset
 * - release past ~25% width completes the back navigation; otherwise it bounces back
 */
@Composable
fun SwipeBackContainer(
    canGoBack: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }

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
                        if (edgeActive) {
                            change.consume()
                            total = (total + amount).coerceAtLeast(0f)
                            scope.launch { dragOffset.snapTo(total) }
                        }
                    },
                    onDragEnd = {
                        if (edgeActive && total > 0f) {
                            edgeActive = false
                            if (total > widthPx * 0.25f) {
                                scope.launch {
                                    dragOffset.animateTo(widthPx, tween(200, easing = FastOutSlowInEasing))
                                    dragOffset.snapTo(0f)
                                    onBack()
                                }
                            } else {
                                scope.launch { dragOffset.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                            }
                        }
                    },
                    onDragCancel = {
                        if (edgeActive) {
                            edgeActive = false
                            scope.launch { dragOffset.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .graphicsLayer {
                    val p = abs(dragOffset.value / (size.width.toFloat().takeIf { it > 0 } ?: 1f))
                    shadowElevation = 8f * p
                }
        ) {
            content()
        }
    }
}
