package com.songpad.core.audio.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun samples(n: Int, seed: Int = 1) = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder()).asShortBuffer().also { for (i in 0 until n) it.put(i, ((i * seed) % 32767).toShort()) }

    @Test
    fun `fsync cadence is roughly one second of audio, not every buffer`() {
        val f = File(tmp.root, "audio.pcm.tmp")
        val w = PcmWriter(f, syncEverySamples = 48_000)
        w.open(append = false)
        var syncs = 0
        repeat(150) { if (w.write(samples(960), 960)) syncs++ } // 150 × 20 ms = 3 s
        assertThat(syncs).isEqualTo(3)
        assertThat(w.samplesSynced).isEqualTo(144_000)
        w.close()
        assertThat(f.length()).isEqualTo(150L * 960 * 2)
        assertThat(w.syncCount).isEqualTo(4)
    }

    @Test
    fun `bytes are little endian and a truncated file is still whole samples`() {
        val f = File(tmp.root, "audio.pcm.tmp")
        val w = PcmWriter(f); w.open(append = false)
        val src = samples(4, seed = 1000); src.put(0, 0x1234)
        w.write(src, 4); w.close()
        val bytes = f.readBytes()
        assertThat(bytes[0].toInt() and 0xFF).isEqualTo(0x34)
        assertThat(bytes[1].toInt() and 0xFF).isEqualTo(0x12)
        f.writeBytes(bytes.copyOf(5)) // kill mid-sample
        assertThat(com.songpad.core.model.take.AudioFormat.framesForBytes(f.length())).isEqualTo(2)
    }
}
