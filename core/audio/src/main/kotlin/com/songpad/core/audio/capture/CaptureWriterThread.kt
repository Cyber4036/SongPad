package com.songpad.core.audio.capture

import android.os.Process
import com.songpad.core.model.midi.ClockMapping
import com.songpad.core.model.take.AudioFormat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * §7.2 rule 4: the dedicated WRITER thread, `THREAD_PRIORITY_AUDIO` (one level below the capture callback).
 * Drains the native ring buffer to disk, runs the peak reducer, samples `AudioTimestamp` anchors ~1 Hz for the
 * §8.3 clock mapping, and calls the MIDI log sync on the same cadence. Disk I/O never happens on the capture
 * thread. This is a plain `Thread`, deliberately NOT a coroutine dispatcher.
 */
class CaptureWriterThread(
    private val native: NativeCapture,
    private val writer: PcmWriter,
    private val reducer: PeakReducer,
    private val clockMapping: ClockMapping,
    private val onSyncCadence: () -> Unit,
    private val onError: (Throwable) -> Unit,
) : Thread("songpad-capture-writer") {

    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    val framesWritten = AtomicLong(0)
    private val drainBuffer = NativeCapture.allocateDrainBuffer(DRAIN_SAMPLES)
    private var lastAnchorNanos = 0L

    fun shutdown() { running.set(false); interrupt() }
    fun setPaused(value: Boolean) { paused.set(value) }

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        running.set(true)
        try {
            while (running.get()) {
                val n = native.drain(drainBuffer, DRAIN_SAMPLES)
                if (n > 0) {
                    val synced = writer.write(drainBuffer, n)
                    reducer.consume(drainBuffer, n)
                    framesWritten.set(writer.samplesWritten / AudioFormat.CHANNELS)
                    if (synced) onSyncCadence()
                    maybeAnchor()
                } else {
                    if (paused.get()) writer.sync()
                    try { sleep(POLL_MS) } catch (_: InterruptedException) { if (!running.get()) break }
                }
            }
            // Final drain so a clean stop loses nothing that reached the ring.
            var n = native.drain(drainBuffer, DRAIN_SAMPLES)
            while (n > 0) { writer.write(drainBuffer, n); reducer.consume(drainBuffer, n); n = native.drain(drainBuffer, DRAIN_SAMPLES) }
            framesWritten.set(writer.samplesWritten / AudioFormat.CHANNELS)
            reducer.flush()
        } catch (t: Throwable) {
            onError(t)
        }
    }

    private fun maybeAnchor() {
        val now = System.nanoTime()
        if (now - lastAnchorNanos < ANCHOR_INTERVAL_NANOS) return
        lastAnchorNanos = now
        val ts = native.timestamp() ?: return
        clockMapping.addAnchor(ts[0], ts[1])
    }

    companion object {
        /** 20 ms of audio per drain; the ring holds 2 s, so the writer has 100× headroom. */
        private const val DRAIN_SAMPLES = 960
        private const val POLL_MS = 5L
        private const val ANCHOR_INTERVAL_NANOS = 1_000_000_000L
    }
}
