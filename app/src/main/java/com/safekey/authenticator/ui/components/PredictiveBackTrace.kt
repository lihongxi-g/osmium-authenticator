package com.safekey.authenticator.ui.components

import android.os.SystemClock
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
    private var frames = 0
    private var lastRenderedPx = Float.NaN
    private var lastSampleAt = 0L
    private var maxGapMs = 0L
    private var frameTicks = 0
    private var lastFrameAt = 0L
    private var maxFrameMs = 0L
    private var maxLagMs = 0L

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
        frames = 0
        lastRenderedPx = Float.NaN
        lastSampleAt = 0L
        maxGapMs = 0L
        frameTicks = 0
        lastFrameAt = 0L
        maxFrameMs = 0L
        maxLagMs = 0L
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

    /**
     * One displayed frame. The longest gap between two of them is the render-stall detector: a
     * heavy frame shows up here as a large number, while a stalled input stream shows up in
     * [sampleTiming] instead — telling those two apart is the whole point of keeping both.
     */
    fun frameTick() {
        val now = System.nanoTime()
        if (lastFrameAt != 0L) {
            val gapMs = (now - lastFrameAt) / 1_000_000L
            if (gapMs > maxFrameMs) maxFrameMs = gapMs
        }
        lastFrameAt = now
        frameTicks++
    }

    /**
     * Delivery latency of one gesture event: how long it took to reach this callback after the
     * platform stamped it. Small numbers mean the events are being processed in the frame they were
     * produced in; large ones would mean they are being queued somewhere on the way in — which is the
     * one thing that cannot be diagnosed from the outside.
     */
    fun latency(frameTimeMillis: Long) {
        if (frameTimeMillis <= 0L) return
        val lag = SystemClock.uptimeMillis() - frameTimeMillis
        if (lag > maxLagMs) maxLagMs = lag
    }

    private var notes = ""

    /**
     * A one-off diagnostic string carried into the next readout, for facts that have no numeric home —
     * whether the navigationevent input attached to the platform at all, and why not if it did not.
     */
    fun note(text: String) {
        notes = if (notes.isEmpty()) text else notes + "," + text
    }

    /** Which stream ended up driving the offset. */
    fun driver(name: String) {
        mode = name
    }

    /**
     * One step of the per-frame follow loop, with the offset it rendered. Counting these is what
     * proves the loop is actually running on a given device (a stalled follow shows up as a handful
     * of frames for a gesture that lasted hundreds of milliseconds).
     */
    fun followStep(renderedPx: Float) {
        frames++
        lastRenderedPx = renderedPx
    }

    /**
     * Time since the previous sample, in milliseconds. This is the number that says whether the
     * platform's gesture stream is sparse (tens of ms apart) or effectively per frame; a whole
     * design decision hinges on it, so it is measured rather than assumed.
     */
    fun sampleTiming() {
        val now = System.currentTimeMillis()
        if (lastSampleAt != 0L) {
            val gap = now - lastSampleAt
            if (gap > maxGapMs) maxGapMs = gap
        }
        lastSampleAt = now
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
            append(" · maxGap ").append(maxGapMs).append("ms")
            append(" · frames ").append(frameTicks)
            append(" · maxFrame ").append(maxFrameMs).append("ms")
            append(" · maxLag ").append(maxLagMs).append("ms")
            append(" · last ").append(fmt(lastRenderedPx, 0))
            append(" · ").append(outcome)
            append(" · ").append(millis).append("ms")
            append(" · width ").append(widthPx.toInt())
            if (notes.isNotEmpty()) append(" · ").append(notes)
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
