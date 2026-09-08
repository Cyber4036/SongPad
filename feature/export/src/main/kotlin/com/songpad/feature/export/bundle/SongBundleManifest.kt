package com.songpad.feature.export.bundle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `song.json` (§12.1). Carries `schemaVersion` from the first release so a future import can migrate.
 * Bump [SCHEMA_VERSION] on any incompatible change and record the migration in docs/adr.
 */
@Serializable
data class SongBundleManifest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val generator: String,
    val exportedAtMillis: Long,
    val mode: String,
    val song: SongInfo,
    val takes: List<TakeInfo>,
    val sections: List<SectionInfo>,
    val notes: List<NoteInfo>,
) {
    @Serializable
    data class SongInfo(val id: String, val name: String, val status: String, val favorite: Boolean, val createdAtMillis: Long, val updatedAtMillis: Long)

    @Serializable
    data class TakeInfo(
        val id: String,
        val name: String,
        val type: String,
        val durationMs: Long,
        val createdAtMillis: Long,
        /** Path inside the archive, e.g. `audio/01 Verse idea.m4a`; null in Lyrics-only mode. */
        val audioFile: String?,
        /** The stored format the audio came from (§12.5 honesty rule): "flac", "m4a" or "pcm". */
        val sourceFormat: String?,
        val exportFormat: String?,
        val midiFile: String?,
    )

    @Serializable
    data class SectionInfo(val id: String, val label: String, val tag: String, val sortOrder: Int)

    @Serializable
    data class NoteInfo(val id: String, val createdAtMillis: Long, val sectionRefId: String?, val takeRefId: String?)

    companion object {
        const val SCHEMA_VERSION = 1
        val json: Json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
    }
}
