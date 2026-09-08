package com.songpad.core.model.midi

import com.songpad.core.model.take.MidiAsset

// ── §15 FROZEN CONTRACT — implemented in :core:midi. ──

interface MidiCaptureEngine {
    /** Frame indices come from AudioRecorderEngine.frameIndexFor(). */
    fun onKeyDown(note: Int, velocity: Int, eventTimeNanos: Long)
    fun onKeyUp(note: Int, eventTimeNanos: Long)
    /** Null when no events were captured (a plain voice take). */
    suspend fun commit(takeId: String): Result<MidiAsset?>
    fun reset()
}

/** One captured key event on the audio frame timeline (§8.5 events.log record). */
data class MidiEvent(
    val frameIndex: Long,
    val note: Int,
    val velocity: Int,
    val isNoteOn: Boolean,
) {
    init {
        require(note in 0..127) { "note out of range: $note" }
        require(velocity in 0..127) { "velocity out of range: $velocity" }
    }
}
