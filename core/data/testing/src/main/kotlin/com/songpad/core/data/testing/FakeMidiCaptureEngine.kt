package com.songpad.core.data.testing

import com.songpad.core.model.midi.MidiCaptureEngine
import com.songpad.core.model.midi.MidiEvent
import com.songpad.core.model.midi.MidiTiming
import com.songpad.core.model.take.MidiAsset
import com.songpad.core.model.take.TakePaths

class FakeMidiCaptureEngine(private val frameOf: (Long) -> Long = { it / 20_833 }) : MidiCaptureEngine {
    val events = mutableListOf<MidiEvent>()
    var resetCount = 0

    override fun onKeyDown(note: Int, velocity: Int, eventTimeNanos: Long) {
        events += MidiEvent(frameOf(eventTimeNanos), note, velocity, true)
    }

    override fun onKeyUp(note: Int, eventTimeNanos: Long) {
        events += MidiEvent(frameOf(eventTimeNanos), note, 0, false)
    }

    override suspend fun commit(takeId: String): Result<MidiAsset?> {
        if (events.isEmpty()) return Result.success(null)
        val asset = MidiAsset("midi-$takeId", takeId, TakePaths.midi(takeId), MidiTiming.PPQ, 22L + events.size * 8L, events.size)
        events.clear()
        return Result.success(asset)
    }

    override fun reset() { resetCount++; events.clear() }
}
