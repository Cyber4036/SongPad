package com.songpad.core.model.take

import com.songpad.core.model.sync.SyncMeta

/** Phase 0 §9 filter pills map 1:1 onto this. Piano+Voice is ONE take with audio plus MIDI (Phase 0 §7). */
enum class TakeType { VOICE, PIANO, VOICE_PIANO }

/** §6.2 lifecycle. A RECORDING row seen at cold start is by definition orphaned. */
enum class TakeStatus { RECORDING, TRANSCODING, READY, ORPHANED, FAILED }

data class Take(
    val id: String,
    val songId: String?,
    val name: String,
    val type: TakeType,
    val status: TakeStatus,
    val durationMs: Long,
    val audio: AudioAsset?,
    val midi: MidiAsset?,
    val meta: SyncMeta,
) {
    val isUnassigned: Boolean get() = songId == null
    val isPlayable: Boolean get() = status == TakeStatus.READY || status == TakeStatus.TRANSCODING
}

/** Stored recording file and technical metadata (§6.1). `mimeType` carries the quality mode (§4.3). */
data class AudioAsset(
    val id: String,
    val takeId: String,
    val relativePath: String,
    val mimeType: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val byteSize: Long,
    val peaksPath: String?,
) {
    val isLossless: Boolean get() = mimeType == AudioFormat.MIME_FLAC
    val isRawPcm: Boolean get() = mimeType == AudioFormat.MIME_RAW_PCM
}

/** SMF Type 0 sidecar for piano-capable takes (§6.1). Written in V1, read by nothing (§22). */
data class MidiAsset(
    val id: String,
    val takeId: String,
    val relativePath: String,
    val ppq: Int,
    val byteSize: Long,
    val eventCount: Int = 0,
)
