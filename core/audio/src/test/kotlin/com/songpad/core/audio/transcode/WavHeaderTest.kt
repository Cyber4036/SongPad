package com.songpad.core.audio.transcode

import com.google.common.truth.Truth.assertThat
import com.songpad.core.audio.playback.RawPcmDataSource
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
}
