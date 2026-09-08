package com.songpad.core.audio.capture

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * §7.2 rule 5: peak/RMS per block on the WRITER thread, published to Kotlin at 10 Hz. Also accumulates the
 * precomputed `peaks.wave` file (§9.1) at [peaksPerSecond] so scrubbing and the transport agree (§7.6).
 *
 * Amplitude published = max(peak, 1.8×RMS) normalized to 0..1, which reads well for both voice and piano.
 */
class PeakReducer(
    private val sampleRate: Int = 48_000,
    private val publishHz: Int = 10,
    private val peaksPerSecond: Int = 100,
    private val onAmplitude: (Float) -> Unit,
) {
    private val samplesPerPublish = sampleRate / publishHz
    private val samplesPerPeak = sampleRate / peaksPerSecond
    private var pubPeak = 0
    private var pubSumSq = 0.0
    private var pubCount = 0
    private var peakAcc = 0
    private var peakCount = 0
    private val peaks = ArrayList<Float>(peaksPerSecond * 60 * 5)

    fun consume(src: ShortBuffer, count: Int) {
        for (i in 0 until count) {
            val s = src.get(i).toInt()
            val a = abs(s)
            if (a > pubPeak) pubPeak = a
            pubSumSq += s.toDouble() * s
            pubCount++
            if (a > peakAcc) peakAcc = a
            peakCount++
            if (peakCount >= samplesPerPeak) { peaks += peakAcc / 32768f; peakAcc = 0; peakCount = 0 }
            if (pubCount >= samplesPerPublish) publish()
        }
    }

    private fun publish() {
        val rms = sqrt(pubSumSq / pubCount)
        val v = maxOf(pubPeak.toDouble(), 1.8 * rms) / 32768.0
        onAmplitude(v.coerceIn(0.0, 1.0).toFloat())
        pubPeak = 0; pubSumSq = 0.0; pubCount = 0
    }

    fun flush() { if (pubCount > 0) publish() }

    fun peaksSnapshot(): FloatArray = peaks.toFloatArray()

    /** `peaks.wave`: magic "SPKS", uint16 peaksPerSecond, then float32 BE peaks. */
    fun writePeaksFile(file: File) {
        file.parentFile?.mkdirs()
        DataOutputStream(FileOutputStream(file).buffered()).use { out ->
            out.writeBytes("SPKS"); out.writeShort(peaksPerSecond)
            peaks.forEach { out.writeFloat(it) }
        }
    }

    fun reset() { pubPeak = 0; pubSumSq = 0.0; pubCount = 0; peakAcc = 0; peakCount = 0; peaks.clear() }

    companion object {
        fun readPeaksFile(file: File): FloatArray {
            if (!file.exists() || file.length() < 6) return FloatArray(0)
            val bytes = file.readBytes()
            val buf = java.nio.ByteBuffer.wrap(bytes)
            if (buf.get() != 'S'.code.toByte() || buf.get() != 'P'.code.toByte() || buf.get() != 'K'.code.toByte() || buf.get() != 'S'.code.toByte()) return FloatArray(0)
            buf.short
            val n = buf.remaining() / 4
            return FloatArray(n) { buf.float }
        }
    }
}
