package com.songpad.core.model.take

/**
 * What `FinalizeTakeUseCase` hands to `TakeRepository.commit()` (§15, §16): a take whose audio is
 * already on disk as a committed `.pcm`, plus an optional MIDI sidecar. `songName` is set when the
 * user is naming a new Song at save time ("Midnight Riff"); `songId` when saving into an existing one.
 */
data class TakeDraft(
    val takeId: String,
    val name: String,
    val type: TakeType,
    val songId: String?,
    val newSongName: String?,
    val audio: CapturedAudio,
    val midi: MidiAsset?,
    val qualityMode: QualityMode,
) {
    init {
        require(songId == null || newSongName == null) { "A draft targets an existing song or a new one, not both." }
    }
}

/** Ground-truth result of `AudioRecorderEngine.stopAndCommit()` (§15). */
data class CapturedAudio(
    val takeId: String,
    val relativePath: String,
    val frames: Long,
    val byteSize: Long,
    val peaksPath: String?,
) {
    val durationMs: Long get() = AudioFormat.framesToMs(frames)
}

/** An in-flight capture found on cold start (§9.4). */
data class OrphanedTake(
    val takeId: String,
    val relativePcmTmpPath: String,
    val byteSize: Long,
    val hasDbRow: Boolean,
    val hasEventsLog: Boolean,
    val songId: String?,
) {
    val durationMs: Long get() = AudioFormat.durationMsForBytes(byteSize)
    val isNoise: Boolean get() = durationMs < AudioFormat.MIN_RECOVERABLE_MS
}
