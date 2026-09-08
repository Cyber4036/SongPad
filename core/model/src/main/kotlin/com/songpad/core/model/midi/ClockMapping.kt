package com.songpad.core.model.midi

import com.songpad.core.model.take.AudioFormat

/**
 * §8.3 — normative. The audio hardware frame clock is the master timeline; the system monotonic clock
 * is only a capture-time stamp converted into frames.
 *
 * Anchors are `(framePosition, presentationTimeNanos)` pairs sampled ~1 Hz from `AudioTimestamp`.
 * The mapping is a linear fit `frame = slope × (t − anchorNanos) + anchorFrame`, where the slope is
 * measured (frames per nanosecond, NOT exactly 48000/1e9) and refreshed as anchors arrive. A fixed
 * `t0` anchor with a nominal slope — the rejected approach — accumulates the DMA clock's tens-of-ppm
 * drift, which over five minutes is tens of milliseconds.
 *
 * Thread-safety: [addAnchor] is called from the engine's writer thread and [frameIndexFor] from the UI
 * thread; both are cheap and guarded by a lock that is never held across I/O.
 */
class ClockMapping(
    private val nominalFramesPerNano: Double = AudioFormat.SAMPLE_RATE / 1e9,
    private val maxAnchors: Int = 16,
    /** Output latency measured in spike S-2 (§8.3): subtracted so the note lands where the user heard it. */
    var outputLatencyFrames: Long = 0,
) {
    private val lock = Any()
    private val anchorFrames = ArrayDeque<Long>()
    private val anchorNanos = ArrayDeque<Long>()
    private var slope = nominalFramesPerNano
    private var refFrame = 0L
    private var refNanos = 0L
    private var hasAnchor = false

    val anchorCount: Int get() = synchronized(lock) { anchorFrames.size }
    val framesPerNano: Double get() = synchronized(lock) { slope }

    /** Called with a fresh `AudioTimestamp`. Ignores anchors that go backwards in either dimension. */
    fun addAnchor(framePosition: Long, presentationTimeNanos: Long) {
        synchronized(lock) {
            if (hasAnchor && (framePosition <= anchorFrames.last() || presentationTimeNanos <= anchorNanos.last())) return
            anchorFrames.addLast(framePosition)
            anchorNanos.addLast(presentationTimeNanos)
            while (anchorFrames.size > maxAnchors) {
                anchorFrames.removeFirst(); anchorNanos.removeFirst()
            }
            hasAnchor = true
            refit()
        }
    }

    /** Frame index for a UI-thread event time; clamped at 0. Before any anchor exists, returns 0 — callers must not record MIDI before the stream reports its first timestamp. */
    fun frameIndexFor(eventTimeNanos: Long): Long {
        synchronized(lock) {
            if (!hasAnchor) return 0
            val raw = Math.round(slope * (eventTimeNanos - refNanos).toDouble() + refFrame.toDouble())
            return (raw - outputLatencyFrames).coerceAtLeast(0)
        }
    }

    fun reset() {
        synchronized(lock) {
            anchorFrames.clear(); anchorNanos.clear()
            slope = nominalFramesPerNano; refFrame = 0; refNanos = 0; hasAnchor = false
        }
    }

    /** Least-squares slope through the retained anchors, referenced to the newest one so extrapolation error stays small. */
    private fun refit() {
        val n = anchorFrames.size
        refFrame = anchorFrames.last()
        refNanos = anchorNanos.last()
        if (n < 2) { slope = nominalFramesPerNano; return }
        val firstNanos = anchorNanos.first()
        val firstFrame = anchorFrames.first()
        val spanNanos = refNanos - firstNanos
        if (spanNanos < 200_000_000L) { // < 200 ms of span: a fit would be dominated by timestamp jitter
            slope = nominalFramesPerNano; return
        }
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (i in 0 until n) {
            val x = (anchorNanos.elementAt(i) - firstNanos).toDouble()
            val y = (anchorFrames.elementAt(i) - firstFrame).toDouble()
            sx += x; sy += y; sxx += x * x; sxy += x * y
        }
        val denom = n * sxx - sx * sx
        val fitted = if (denom == 0.0) nominalFramesPerNano else (n * sxy - sx * sy) / denom
        // Sanity clamp: a real DMA clock is within ±500 ppm of nominal; anything else is a broken anchor.
        val lo = nominalFramesPerNano * (1 - 500e-6)
        val hi = nominalFramesPerNano * (1 + 500e-6)
        slope = fitted.coerceIn(lo, hi)
    }
}
