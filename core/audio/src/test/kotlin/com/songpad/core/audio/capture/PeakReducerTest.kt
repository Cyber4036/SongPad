package com.songpad.core.audio.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class PeakReducerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `publishes ten values per second of audio in the 0 to 1 range and writes a peaks file`() {
        val published = mutableListOf<Float>()
        val r = PeakReducer(onAmplitude = { published += it })
        val n = 48_000 * 2
        val buf = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        for (i in 0 until n) buf.put(i, (sin(2 * PI * 440 * i / 48_000) * 16_000).toInt().toShort())
        r.consume(buf, n)
        assertThat(published).hasSize(20)
        assertThat(published.all { it in 0f..1f }).isTrue()
        assertThat(published.first()).isWithin(0.1f).of(0.62f) // 1.8 × RMS of a 16k-amplitude sine ≈ 0.62
        val f = File(tmp.root, "peaks.wave")
        r.writePeaksFile(f)
        val back = PeakReducer.readPeaksFile(f)
        assertThat(back).hasLength(200)
        assertThat(back[0]).isWithin(0.02f).of(16_000f / 32_768f)
    }
}
