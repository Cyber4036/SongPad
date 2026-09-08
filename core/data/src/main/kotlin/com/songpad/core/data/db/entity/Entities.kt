package com.songpad.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// §6.1 schema. UUIDv7 TEXT primary keys, epoch-millisecond INTEGER timestamps, FKs as drawn.
// No WITHOUT ROWID anywhere — FTS5 joins and secondary indexes need a rowid.

@Entity(
    tableName = "song",
    indices = [Index("updated_at"), Index("sync_state"), Index("status")],
)
data class SongEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: String,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Int = 0,
    @Embedded val sync: SyncColumns,
)

@Entity(
    tableName = "take",
    foreignKeys = [ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["song_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("song_id"), Index("status"), Index("updated_at"), Index("sync_state")],
)
data class TakeEntity(
    @PrimaryKey val id: String,
    /** NULL is the first-class independent Take (Phase 0 §4). */
    @ColumnInfo(name = "song_id") val songId: String?,
    val name: String,
    val type: String,
    val status: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "audio_asset_id") val audioAssetId: String?,
    @ColumnInfo(name = "midi_asset_id") val midiAssetId: String?,
    @Embedded val sync: SyncColumns,
)

@Entity(
    tableName = "audio_asset",
    foreignKeys = [ForeignKey(entity = TakeEntity::class, parentColumns = ["id"], childColumns = ["take_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("take_id", unique = true), Index("updated_at"), Index("sync_state")],
)
data class AudioAssetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "take_id") val takeId: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    /** Carries the quality mode per take (§4.3). */
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int,
    @ColumnInfo(name = "bit_depth") val bitDepth: Int,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "peaks_path") val peaksPath: String?,
    @Embedded val sync: SyncColumns,
)

@Entity(
    tableName = "midi_asset",
    foreignKeys = [ForeignKey(entity = TakeEntity::class, parentColumns = ["id"], childColumns = ["take_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("take_id", unique = true), Index("updated_at"), Index("sync_state")],
)
data class MidiAssetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "take_id") val takeId: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    val ppq: Int,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "event_count", defaultValue = "0") val eventCount: Int = 0,
    @Embedded val sync: SyncColumns,
)

@Entity(
    tableName = "lyrics_document",
    foreignKeys = [ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["song_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("song_id", unique = true), Index("updated_at"), Index("sync_state")],
)
data class LyricsDocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "song_id") val songId: String,
    @Embedded val sync: SyncColumns,
)

@Entity(
    tableName = "section",
    foreignKeys = [ForeignKey(entity = LyricsDocumentEntity::class, parentColumns = ["id"], childColumns = ["document_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("document_id", "sort_order"), Index("updated_at"), Index("sync_state")],
)
data class SectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    val tag: String,
    @ColumnInfo(name = "custom_label") val customLabel: String?,
    val number: Int?,
    /** May be empty (Phase 0 §8). */
    @ColumnInfo(name = "body_text") val bodyText: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @Embedded val sync: SyncColumns,
)

@Entity(
    tableName = "note",
    foreignKeys = [
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["song_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TakeEntity::class, parentColumns = ["id"], childColumns = ["voice_take_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("song_id"), Index("voice_take_id"), Index("updated_at"), Index("sync_state")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "body_text") val bodyText: String,
    @ColumnInfo(name = "voice_take_id") val voiceTakeId: String?,
    @ColumnInfo(name = "media_uri") val mediaUri: String?,
    @ColumnInfo(name = "section_ref_id") val sectionRefId: String?,
    @ColumnInfo(name = "take_ref_id") val takeRefId: String?,
    @Embedded val sync: SyncColumns,
)

@Entity(
    tableName = "nudge",
    foreignKeys = [ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["target_song_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("target_song_id"), Index("updated_at"), Index("sync_state")],
)
data class NudgeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "target_song_id") val targetSongId: String?,
    @ColumnInfo(name = "target_take_id") val targetTakeId: String?,
    val kind: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_read", defaultValue = "0") val isRead: Int = 0,
    @Embedded val sync: SyncColumns,
)
