package com.songpad.core.midi.smf

import com.google.common.truth.Truth.assertThat
import com.songpad.core.model.midi.MidiEvent
import org.junit.Test

/** §18: known event sequence → byte-exact SMF output. */
class SmfWriterTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }

    @Test
    fun `empty performance is a valid file with tempo and end of track`() {
        val bytes = SmfWriter.write(emptyList())
        assertThat(hex(bytes)).isEqualTo(
            "4D 54 68 64 00 00 00 06 00 00 00 01 01 E0 " + // MThd, len 6, format 0, 1 track, 480 PPQ
                "4D 54 72 6B 00 00 00 0B " +                    // MTrk, length 11
                "00 FF 51 03 07 A1 20 " +                       // tempo 500,000 µs
                "00 FF 2F 00",                                  // end of track
        )
    }

    @Test
    fun `note on and off land on the exact ticks derived from frames`() {
        // Frame 24,000 = one quarter note at 120 BPM = tick 480 → VLQ 83 60. Off 12,000 frames later = 240 ticks → 81 70.
        val events = listOf(MidiEvent(24_000, 60, 100, true), MidiEvent(36_000, 60, 0, false))
        val bytes = SmfWriter.write(events)
        val track = bytes.copyOfRange(0x16, bytes.size)
        assertThat(hex(track)).isEqualTo("00 FF 51 03 07 A1 20 83 60 90 3C 64 81 70 80 3C 00 00 FF 2F 00")
        // Track length field = 21 bytes
        assertThat(bytes[0x12].toInt() shl 24 or (bytes[0x13].toInt() shl 16) or (bytes[0x14].toInt() shl 8) or (bytes[0x15].toInt() and 0xFF)).isEqualTo(track.size)
    }

    @Test
    fun `events are sorted by frame with note-offs before note-ons on equal frames`() {
        val events = listOf(MidiEvent(100, 64, 90, true), MidiEvent(50, 60, 90, true), MidiEvent(100, 60, 0, false))
        val bytes = SmfWriter.write(events)
        val track = hex(bytes.copyOfRange(0x16 + 7, bytes.size - 4))
        // frame 50 → tick 1; frame 100 → tick 2. Order: on 60 @1, off 60 @2, on 64 @2.
        assertThat(track).isEqualTo("01 90 3C 5A 01 80 3C 00 00 90 40 5A")
    }

    @Test
    fun `five minute performance stays within VLQ range and one tick is 50 frames`() {
        val fiveMinFrames = 48_000L * 300
        val bytes = SmfWriter.write(listOf(MidiEvent(fiveMinFrames, 60, 1, true), MidiEvent(fiveMinFrames + 50, 60, 0, false)))
        val (delta, _) = Vlq.decode(bytes, 0x16 + 7)
        assertThat(delta).isEqualTo(fiveMinFrames / 50)
    }
}
