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
 *
 * Every method here is called from a per-frame or per-sample path, so each one is a handful of field
 * writes and nothing else: no allocation, no string building (that happens once, in [finish]) and no
 * Compose snapshot write. The frame counter no longer drives a `withFrameNanos` loop — the caller
 * ticks it from the layer block it already has.
 */
object PredictiveBackTrace {

    /** One-line summary of the last finished gesture, shown in the developer screen. */
    val text: MutableState<String> = mutableStateOf("")

    /**
     * Two displayed frames further apart than this are not part of the same run of frames — the gap
     * between them is idle time, not a stall, and counting it would drown the signal.
     */
    private const val FRAME_RUN_GAP_MS = 120L

    private var edge = "?"
    private var events = 0
    private var maxProgress = 0f
    private var touchSamples = 0
    private var firstTouch = Float.NaN
    private var lastTouch = Float.NaN
    private var mode = "?"
    private var startedAt = 0L
    private var lastSampleAt = 0L
    private var maxGapMs = 0L
    private var frameTicks = 0
    private var lastFrameAt = 0L
    private var maxFrameDragMs = 0L
    private var maxFrameSettleMs = 0L
    private var maxLagMs = 0L
    private var notes = ""

    fun begin(rightEdge: Boolean) {
        edge = if (rightEdge) "R" else "L"
        events = 0
        maxProgress = 0f
        touchSamples = 0
        firstTouch = Float.NaN
        lastTouch = Float.NaN
        mode = "?"
        startedAt = System.currentTimeMillis()
        lastSampleAt = 0L
        maxGapMs = 0L
        frameTicks = 0
        lastFrameAt = 0L
        maxFrameDragMs = 0L
        maxFrameSettleMs = 0L
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

    /**
     * One gesture sample. Returns the wall clock for the caller's own stall watch, and records the
     * gap to the previous sample — the readout's `maxGap`, the number that says whether the
     * platform's stream is per-frame or sparse. (A whole design decision hinges on that, so it is
     * measured rather than assumed.)
     */
    fun sample(): Long {
        val now = SystemClock.uptimeMillis()
        if (lastSampleAt != 0L) {
            val gap = now - lastSampleAt
            if (gap > maxGapMs) maxGapMs = gap
        }
        lastSampleAt = now
        return now
    }

    /**
     * One displayed frame of a gesture, its settle or the pop that follows — called from the
     * travelling page's layer block, which is the only place that sees all of them.
     *
     * The longest gap between two of them is the render-stall detector, split by phase because the
     * two phases have different jobs: the drag is geometry (a missed frame shows up as the page
     * lagging the finger) while the settle is the animation plus a whole navigation pop. A heavy
     * frame shows up here, while a stalled input stream shows up in `maxGap` and a queued one in
     * `maxLag` — telling those three apart is the whole point of keeping all of them.
     */
    fun frameTick(settling: Boolean) {
        val now = System.nanoTime()
        if (lastFrameAt != 0L) {
            val gapMs = (now - lastFrameAt) / 1_000_000L
            if (gapMs <= FRAME_RUN_GAP_MS) {
                if (settling) {
                    if (gapMs > maxFrameSettleMs) maxFrameSettleMs = gapMs
                } else {
                    if (gapMs > maxFrameDragMs) maxFrameDragMs = gapMs
                }
            }
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
     * @param compositions recompositions of the navigation container since the gesture began. One is
     *   the baseline (the frame the readout itself is built on); anything above that means the
     *   container recomposed mid-gesture and re-ran the two resident pages with it.
     */
    fun finish(outcome: String, widthPx: Float, travelPx: Float, compositions: Int = 0) {
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
            append(" · driver ").append(mode)
            append(" · travel ").append(travel).append("%")
            append(" · maxGap ").append(maxGapMs).append("ms")
            append(" · frames ").append(frameTicks)
            append(" · frame ").append(maxOf(maxFrameDragMs, maxFrameSettleMs))
            append("ms (drag ").append(maxFrameDragMs)
            append(" · settle ").append(maxFrameSettleMs).append(")")
            append(" · comp ").append(compositions)
            append(" · maxLag ").append(maxLagMs).append("ms")
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
