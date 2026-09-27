package com.safekey.authenticator.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Developer-mode readout of the last predictive-back gesture.
 *
 * Field diagnostics only, and deliberately global: the drag is fed by two input streams — the
 * platform's gesture events and our own pointer samples — and the only way to tell which one is
 * actually live on a given device (and where one of them stalls) is to look at the numbers after
 * a real gesture. All fields are plain values; [text] is the single observable the UI reads.
 */
object PredictiveBackTrace {

    /** One-line summary of the last finished gesture, shown in the developer screen. */
    val text: MutableState<String> = mutableStateOf("")

    private var edge = "?"
    private var events = 0
    private var maxProgress = 0f
    private var touchSamples = 0
    private var firstTouch = Float.NaN
    private var lastTouch = Float.NaN
    private var pointerSamples = 0
    private var maxFinger = Float.NaN
    private var mode = "?"
    private var startedAt = 0L

    fun begin(rightEdge: Boolean) {
        edge = if (rightEdge) "R" else "L"
        events = 0
        maxProgress = 0f
        touchSamples = 0
        firstTouch = Float.NaN
        lastTouch = Float.NaN
        pointerSamples = 0
        maxFinger = Float.NaN
        mode = "?"
        startedAt = System.currentTimeMillis()
    }

    fun edgeLocked(rightEdge: Boolean) {
        edge = if (rightEdge) "R" else "L"
    }

    /** A platform gesture event: its progress and the touch position it carries (0 = none). */
    fun platformEvent(progress: Float, touchX: Float) {
        events++
        if (progress > maxProgress) maxProgress = progress
        if (touchX > 0f) {
            touchSamples++
            if (firstTouch.isNaN()) firstTouch = touchX
            lastTouch = touchX
        }
    }

    /** A sample from our own pointer stream, in container pixels. */
    fun pointerSample(fingerX: Float) {
        pointerSamples++
        if (maxFinger.isNaN() || fingerX > maxFinger) maxFinger = fingerX
    }

    /** Which stream ended up driving the offset. */
    fun driver(name: String) {
        mode = name
    }

    fun finish(outcome: String, widthPx: Float, travelPx: Float) {
        val millis = System.currentTimeMillis() - startedAt
        val travel = if (widthPx > 0f) (travelPx / widthPx * 100f).toInt() else 0
        text.value = buildString {
            append(edge).append(" · ")
            append("events ").append(events)
            append(" · prog ").append(fmt(maxProgress, 2))
            append(" · touch ").append(touchSamples)
            if (touchSamples > 0) {
                append(" (").append(fmt(firstTouch, 0)).append("→").append(fmt(lastTouch, 0)).append(")")
            }
            append(" · ptr ").append(pointerSamples)
            if (pointerSamples > 0) append(" (max ").append(fmt(maxFinger, 0)).append(")")
            append(" · driver ").append(mode)
            append(" · travel ").append(travel).append("%")
            append(" · ").append(outcome)
            append(" · ").append(millis).append("ms")
            append(" · width ").append(widthPx.toInt())
        }
    }

    private fun fmt(value: Float, decimals: Int): String {
        if (value.isNaN()) return "-"
        return when (decimals) {
            0 -> value.toInt().toString()
            else -> ((value * 100f).toInt() / 100.0).toString()
        }
    }
}
