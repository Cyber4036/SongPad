package com.songpad.core.midi.smf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** §18: VLQ boundaries at 127/128/16383/16384, plus the spec's worked example. */
class VlqTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }

    @Test
    fun `worked example from the spec`() { assertThat(hex(Vlq.encode(1060))).isEqualTo("88 24") }

    @Test
    fun `boundaries`() {
        assertThat(hex(Vlq.encode(0))).isEqualTo("00")
        assertThat(hex(Vlq.encode(127))).isEqualTo("7F")
        assertThat(hex(Vlq.encode(128))).isEqualTo("81 00")
        assertThat(hex(Vlq.encode(16383))).isEqualTo("FF 7F")
        assertThat(hex(Vlq.encode(16384))).isEqualTo("81 80 00")
        assertThat(hex(Vlq.encode(0x0FFF_FFFF))).isEqualTo("FF FF FF 7F")
    }

    @Test
    fun `round trips`() {
        listOf(0L, 1, 127, 128, 255, 1060, 16383, 16384, 2_097_151, 2_097_152, 0x0FFF_FFFFL).forEach { v ->
            val enc = Vlq.encode(v)
            val (dec, n) = Vlq.decode(enc, 0)
            assertThat(dec).isEqualTo(v); assertThat(n).isEqualTo(enc.size)
        }
    }
}
