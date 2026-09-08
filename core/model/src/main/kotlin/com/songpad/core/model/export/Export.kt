package com.songpad.core.model.export

import com.songpad.core.model.take.AudioFormat

/** Phase 0 §13 three conceptual modes. */
enum class ExportMode { EVERYTHING, AUDIO_TAKES, LYRICS_ONLY }

/** §12.4. The user-facing label is normative: a WAV decoded from AAC is never "lossless". */
enum class ExportAudioFormat(val extension: String, val mime: String) {
    STORED("", ""),          // direct copy of what is stored: m4a or flac
    WAV("wav", AudioFormat.MIME_WAV),
    MP3("mp3", AudioFormat.MIME_MP3),
    ;

    /** Label for the confirmation screen given the take's stored format. */
    fun label(sourceIsLossless: Boolean): String = when (this) {
        STORED -> if (sourceIsLossless) "Lossless (FLAC)" else "Compressed — small files (M4A)"
        WAV -> if (sourceIsLossless) "Uncompressed — for DAWs (bit-exact from lossless)" else "Uncompressed — for DAWs (decoded from compressed source)"
        MP3 -> "MP3 — universal compatibility"
    }
}

/** What an encoder receives (§12.5). Encoders never learn which quality mode produced it. */
data class ExportSource(
    val takeId: String,
    val relativePath: String,
    val mimeType: String,
    val isLossless: Boolean,
    val durationMs: Long,
    val byteSize: Long,
)

data class ExportRequest(
    val songId: String,
    val mode: ExportMode,
    val audioFormat: ExportAudioFormat,
)

/** Phase 0 §20 "ExportJob — transient state object if needed". The §17 state set. */
sealed interface ExportState {
    data class Selecting(val request: ExportRequest, val summary: ExportSummary?) : ExportState
    data class Preparing(val progress: Float, val currentItem: String?) : ExportState
    data class Success(val bundleName: String, val byteSize: Long) : ExportState
    data class Failure(val error: com.songpad.core.model.error.DomainError) : ExportState
    data object Cancelled : ExportState
}

/** The export screen summarizes what will be included before confirmation (Phase 0 §13). */
data class ExportSummary(
    val songName: String,
    val takeCount: Int,
    val midiCount: Int,
    val sectionCount: Int,
    val noteCount: Int,
    val losslessTakeCount: Int,
    val estimatedBytes: Long,
) {
    val hasLossyTakes: Boolean get() = takeCount > losslessTakeCount
}
