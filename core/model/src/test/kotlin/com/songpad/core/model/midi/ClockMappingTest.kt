package com.songpad.core.model.midi

import com.google.common.truth.Truth.assertThat
import com.songpad.core.model.take.AudioFormat
import org.junit.Test
import kotlin.math.abs

/**
 * §18 "Clock mapping": synthetic anchor pairs with injected DMA drift → frame indices within tolerance
 * over 5 simulated minutes. Threshold mirrors S-3: < 10 ms cumulative error.
 */
class ClockMappingTest {

    private fun simulate(driftPpm: Double, jitterNanos: Long = 0, anchorEverySec: Int = 1): Long {
        val trueFramesPerNano = AudioFormat.SAMPLE_RATE * (1 + driftPpm * 1e-6) / 1e9
        val mapping = ClockMapping()
        var worstErrorFrames = 0L
        val rnd = java.util.Random(3)
        var t = 10_000_000_000L // arbitrary monotonic origin
        val startNanos = t
        val totalSeconds = 300
        for (sec in 0..totalSeconds) {
            t = startNanos + sec * 1_000_000_000L
            if (sec % anchorEverySec == 0) {
                val jitter = if (jitterNanos > 0) (rnd.nextGaussian() * jitterNanos).toLong() else 0
                val trueFrame = ((t - startNanos) * trueFramesPerNano).toLong()
                mapping.addAnchor(trueFrame, t + jitter)
            }
            // A key press 400 ms after the anchor, i.e. extrapolation, which is the realistic case.
            val eventT = t + 400_000_000L
            val trueFrameAtEvent = ((eventT - startNanos) * trueFramesPerNano).toLong()
            val mapped = mapping.frameIndexFor(eventT)
            worstErrorFrames = maxOf(worstErrorFrames, abs(mapped - trueFrameAtEvent))
        }
        return worstErrorFrames
    }

    @Test
    fun `with 40 ppm drift and no jitter, error stays far under 10 ms over 5 minutes`() {
        val worst = simulate(driftPpm = 40.0)
        assertThat(AudioFormat.framesToMs(worst)).isLessThan(2)
    }

    @Test
    fun `with 80 ppm drift and 50 us anchor jitter, error stays under 10 ms`() {
        val worst = simulate(driftPpm = 80.0, jitterNanos = 50_000)
        assertThat(AudioFormat.framesToMs(worst)).isLessThan(10)
    }

    @Test
    fun `the rejected t0-anchor approach accumulates the drift`() {
        // Nominal slope from a single anchor at t0 — what §8.3 forbids.
        val driftPpm = 40.0
        val trueFramesPerNano = AudioFormat.SAMPLE_RATE * (1 + driftPpm * 1e-6) / 1e9
        val mapping = ClockMapping()
        mapping.addAnchor(0, 0)
        val fiveMinutes = 300_000_000_000L
        val trueFrame = (fiveMinutes * trueFramesPerNano).toLong()
        val errorMs = AudioFormat.framesToMs(abs(mapping.frameIndexFor(fiveMinutes) - trueFrame))
        assertThat(errorMs).isAtLeast(11) // 40 ppm × 300 s = 12 ms — musically unusable
    }

    @Test
    fun `output latency is subtracted so the note lands where it was heard`() {
        val mapping = ClockMapping(outputLatencyFrames = 1_440) // 30 ms at 48 kHz
        mapping.addAnchor(0, 0)
        mapping.addAnchor(48_000, 1_000_000_000L)
        assertThat(mapping.frameIndexFor(2_000_000_000L)).isEqualTo(96_000 - 1_440)
    }

    @Test
    fun `no anchor yet maps to frame zero and never negative`() {
        val mapping = ClockMapping()
        assertThat(mapping.frameIndexFor(123_456_789)).isEqualTo(0)
        mapping.addAnchor(100, 1_000_000_000L)
        assertThat(mapping.frameIndexFor(0)).isEqualTo(0)
    }

    @Test
    fun `one tick is exactly fifty frames`() {
        assertThat(MidiTiming.FRAMES_PER_TICK).isEqualTo(50)
        assertThat(MidiTiming.frameToTick(24_000)).isEqualTo(480)
        assertThat(MidiTiming.MICROS_PER_QUARTER).isEqualTo(500_000)
    }
}
