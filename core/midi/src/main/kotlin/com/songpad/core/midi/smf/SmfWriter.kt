package com.songpad.core.midi.smf

import com.songpad.core.model.midi.MidiEvent
import com.songpad.core.model.midi.MidiTiming
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * §8.4 SMF Type 0 serializer, hand-written, byte-exact-testable on the JVM (§18). No dependency.
 *
 *   0x00 MThd · 0x04 header length 6 · 0x08 format 0 · 0x0A 1 track · 0x0C 480 PPQ (01 E0)
 *   0x0E MTrk · 0x12 track length (BE) · 0x16 tempo meta 500,000 µs/quarter (120 BPM)
 *   … [VLQ] 90 kk vv (Note On, ch 0) · [VLQ] 80 kk 00 (Note Off, ch 0) · end 00 FF 2F 00
 *
 * Ticks are derived from frame index (`frame / 50`), never from wall time (§8.3).
 */
object SmfWriter {
    private val MTHD = byteArrayOf(0x4D, 0x54, 0x68, 0x64)
    private val MTRK = byteArrayOf(0x4D, 0x54, 0x72, 0x6B)

    /** Serializes [events] (any order; sorted by frame, then note-offs before note-ons at equal frames). */
    fun write(events: List<MidiEvent>, ppq: Int = MidiTiming.PPQ, microsPerQuarter: Int = MidiTiming.MICROS_PER_QUARTER): ByteArray {
        val sorted = events.sortedWith(compareBy<MidiEvent> { it.frameIndex }.thenBy { if (it.isNoteOn) 1 else 0 })
        val track = ByteArrayOutputStream()
        // Tempo meta event at delta 0.
        track.write(byteArrayOf(0x00, 0xFF.toByte(), 0x51, 0x03, (microsPerQuarter ushr 16).toByte(), (microsPerQuarter ushr 8).toByte(), microsPerQuarter.toByte()))
        var lastTick = 0L
        for (e in sorted) {
            val tick = MidiTiming.frameToTick(e.frameIndex)
            Vlq.write(track, tick - lastTick)
            lastTick = tick
            if (e.isNoteOn) track.write(byteArrayOf(0x90.toByte(), e.note.toByte(), e.velocity.coerceIn(1, 127).toByte()))
            else track.write(byteArrayOf(0x80.toByte(), e.note.toByte(), 0x00))
        }
        track.write(byteArrayOf(0x00, 0xFF.toByte(), 0x2F, 0x00))
        val trackBytes = track.toByteArray()

        val out = ByteArrayOutputStream(14 + 8 + trackBytes.size)
        DataOutputStream(out).apply {
            write(MTHD); writeInt(6); writeShort(0); writeShort(1); writeShort(ppq)
            write(MTRK); writeInt(trackBytes.size); write(trackBytes)
            flush()
        }
        return out.toByteArray()
    }
}
