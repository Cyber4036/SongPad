package com.songpad.core.midi.capture

import com.songpad.core.model.midi.MidiEvent
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * §8.5 crash-safe MIDI log: `takes/{takeId}/events.log`, a trivial fixed-width binary record
 * `frameIndex:int64 · note:uint8 · velocity:uint8 · on:uint8 · pad:uint8` = 11 bytes, big-endian, appended
 * and fsynced on the same ~1 s cadence as the PCM. A truncated file yields whole records only; on recovery the
 * log is converted to a MIDI file covering exactly the frames the recovered PCM contains.
 */
class EventsLog(private val file: File) {
    private var out: DataOutputStream? = null
    private var fileOut: FileOutputStream? = null
    private var pendingSinceSync = 0

    fun open() {
        file.parentFile?.mkdirs()
        fileOut = FileOutputStream(file, true)
        out = DataOutputStream(java.io.BufferedOutputStream(fileOut))
    }

    fun append(e: MidiEvent) {
        val o = out ?: return
        o.writeLong(e.frameIndex); o.writeByte(e.note); o.writeByte(e.velocity); o.writeByte(if (e.isNoteOn) 1 else 0); o.writeByte(0)
        pendingSinceSync++
    }

    /** flush + fsync; called on the writer cadence, never on the touch path. */
    fun sync() {
        val o = out ?: return
        if (pendingSinceSync == 0) return
        o.flush(); fileOut?.fd?.sync(); pendingSinceSync = 0
    }

    fun close() { sync(); out?.close(); out = null; fileOut = null }
    fun delete(): Boolean = file.delete()

    companion object {
        const val RECORD_BYTES = 11

        /** Reads whole records; ignores a trailing partial record; optionally truncates to [maxFrameExclusive] (§8.5 recovery). */
        fun read(file: File, maxFrameExclusive: Long = Long.MAX_VALUE): List<MidiEvent> {
            if (!file.exists()) return emptyList()
            val bytes = file.readBytes()
            val n = bytes.size / RECORD_BYTES
            val buf = ByteBuffer.wrap(bytes)
            val events = ArrayList<MidiEvent>(n)
            repeat(n) {
                val frame = buf.long
                val note = buf.get().toInt() and 0xFF
                val vel = buf.get().toInt() and 0xFF
                val on = buf.get().toInt() != 0
                buf.get()
                if (frame < maxFrameExclusive && note <= 127 && vel <= 127) events += MidiEvent(frame, note, vel, on)
            }
            return closeDangling(events, maxFrameExclusive)
        }

        /** Notes still held at the truncation point get a note-off at the last frame so the SMF is well-formed. */
        fun closeDangling(events: List<MidiEvent>, endFrame: Long): List<MidiEvent> {
            val held = LinkedHashSet<Int>()
            for (e in events) if (e.isNoteOn) held += e.note else held -= e.note
            if (held.isEmpty()) return events
            val last = if (endFrame == Long.MAX_VALUE) (events.maxOfOrNull { it.frameIndex } ?: 0L) + 1 else endFrame - 1
            return events + held.map { MidiEvent(maxOf(last, 0), it, 0, false) }
        }
    }
}
