package com.songpad.core.model.midi

import com.songpad.core.model.take.AudioFormat

/**
 * §8.3 / §8.4: 48 kHz · 480 PPQ · 120 BPM ⇒ a quarter note is 24,000 frames and ONE TICK IS EXACTLY
 * 50 FRAMES. The integer relationship is deliberate; ticks are derived from frame index, never wall time.
 */
object MidiTiming {
    const val PPQ = 480
    const val BPM = 120
    const val MICROS_PER_QUARTER = 60_000_000 / BPM // 500,000
    const val FRAMES_PER_QUARTER = AudioFormat.SAMPLE_RATE * 60 / BPM // 24,000
    const val FRAMES_PER_TICK = FRAMES_PER_QUARTER / PPQ // 50

    init {
        check(FRAMES_PER_QUARTER % PPQ == 0) { "frames per tick must be an integer" }
    }

    fun frameToTick(frameIndex: Long): Long = frameIndex / FRAMES_PER_TICK
    fun tickToFrame(tick: Long): Long = tick * FRAMES_PER_TICK
}
