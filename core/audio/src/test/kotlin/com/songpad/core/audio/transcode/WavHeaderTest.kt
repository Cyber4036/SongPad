package com.songpad.core.audio.transcode

import com.google.common.truth.Truth.assertThat
import com.songpad.core.audio.playback.RawPcmDataSource
import com.songpad.core.audio.transcode.FlacHeader.readUpToNBytes
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class WavHeaderTest {
    @Test
    fun `44 byte canonical header for 48k mono 16 bit`() {
        val h = RawPcmDataSource.wavHeader(96_000)
        assertThat(h).hasLength(44)
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        assertThat(String(h, 0, 4, Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(b.getInt(4)).isEqualTo(36 + 96_000)
        assertThat(String(h, 8, 4, Charsets.US_ASCII)).isEqualTo("WAVE")
        assertThat(b.getShort(20).toInt()).isEqualTo(1)          // PCM
        assertThat(b.getShort(22).toInt()).isEqualTo(1)          // mono
        assertThat(b.getInt(24)).isEqualTo(48_000)
        assertThat(b.getInt(28)).isEqualTo(96_000)               // byte rate
        assertThat(b.getShort(34).toInt()).isEqualTo(16)
        assertThat(b.getInt(40)).isEqualTo(96_000)               // data size
    }

    @Test
    fun `42 byte canonical FLAC STREAMINFO header and in-place repair of headerless FLAC frames`() {
        val totalSamples = 48_000L * 14L // 14 seconds at 48 kHz
        val header = FlacHeader.buildStreamInfoHeader(totalSamples = totalSamples)
        assertThat(header).hasLength(42)
        assertThat(FlacHeader.hasFlacMagic(header)).isTrue()
        assertThat(header[4].toInt() and 0xFF).isEqualTo(0x80) // isLast = 1, type = 0 (STREAMINFO)
        assertThat(header[7].toInt() and 0xFF).isEqualTo(34)   // STREAMINFO length = 34

        val packed64 = ByteBuffer.wrap(header, 18, 8).order(ByteOrder.BIG_ENDIAN).long
        val decodedSampleRate = (packed64 ushr 44) and 0xFFFFFL
        val decodedChannelsMinusOne = (packed64 ushr 41) and 0x7L
        val decodedBpsMinusOne = (packed64 ushr 36) and 0x1FL
        val decodedSamples = packed64 and 0xFFFFFFFFFL
        assertThat(decodedSampleRate).isEqualTo(48_000L)
        assertThat(decodedChannelsMinusOne).isEqualTo(0L)
        assertThat(decodedBpsMinusOne).isEqualTo(15L)
        assertThat(decodedSamples).isEqualTo(totalSamples)

        // Simulate a headerless FLAC file starting directly with a FLAC audio frame sync word (0xFF 0xF8 0xC9 ...)
        val tmpDir = Files.createTempDirectory("flac-repair-test").toFile()
        try {
            val brokenFlac = File(tmpDir, "audio.flac")
            val fakeFrameBytes = byteArrayOf(
                0xFF.toByte(), 0xF8.toByte(), 0xC9.toByte(), 0x18.toByte(),
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
            )
            brokenFlac.writeBytes(fakeFrameBytes)
            assertThat(FlacHeader.hasFlacMagic(brokenFlac)).isFalse()

            val repaired = FlacHeader.ensureValidFlacFile(brokenFlac, totalSamples = totalSamples)
            assertThat(repaired).isTrue()
            assertThat(FlacHeader.hasFlacMagic(brokenFlac)).isTrue()
            assertThat(brokenFlac.length()).isEqualTo(42L + fakeFrameBytes.size)

            val allBytes = brokenFlac.readBytes()
            assertThat(allBytes.copyOfRange(42, allBytes.size)).isEqualTo(fakeFrameBytes)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `readUpToNBytes reads partial and full streams without API 33 readNBytes`() {
        val data = ByteArray(100) { it.toByte() }
        val first12 = ByteArrayInputStream(data).use { it.readUpToNBytes(12) }
        assertThat(first12).hasLength(12)
        assertThat(first12).isEqualTo(data.copyOf(12))

        val all = ByteArrayInputStream(data).use { it.readUpToNBytes(500) }
        assertThat(all).hasLength(100)
        assertThat(all).isEqualTo(data)
    }
}
