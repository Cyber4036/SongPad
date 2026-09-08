package com.songpad.core.data.repo

import com.songpad.core.data.db.entity.AudioAssetEntity
import com.songpad.core.data.db.entity.LyricsDocumentEntity
import com.songpad.core.data.db.entity.MidiAssetEntity
import com.songpad.core.data.db.entity.NoteEntity
import com.songpad.core.data.db.entity.NudgeEntity
import com.songpad.core.data.db.entity.SectionEntity
import com.songpad.core.data.db.entity.SongEntity
import com.songpad.core.data.db.entity.SyncColumns
import com.songpad.core.data.db.entity.TakeEntity
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.Note
import com.songpad.core.model.nudge.Nudge
import com.songpad.core.model.nudge.NudgeKind
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.sync.SyncMeta
import com.songpad.core.model.sync.SyncState
import com.songpad.core.model.take.AudioAsset
import com.songpad.core.model.take.MidiAsset
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.take.TakeType

internal fun SyncColumns.toMeta() = SyncMeta(createdAt, updatedAt, deleted == 1, deletedAt, SyncState.valueOf(syncState), revision)
internal fun SyncMeta.toColumns() = SyncColumns(createdAt, updatedAt, if (deleted) 1 else 0, deletedAt, syncState.name, revision)
internal fun newSync(now: Long) = SyncMeta.create(now).toColumns()
internal fun SyncColumns.touched(now: Long) = copy(updatedAt = now, revision = revision + 1)
internal fun SyncColumns.softDeleted(now: Long) = copy(updatedAt = now, deleted = 1, deletedAt = now, revision = revision + 1)

internal fun SongEntity.toDomain() = Song(id, name, SongStatus.valueOf(status), isFavorite == 1, sync.toMeta())
internal fun AudioAssetEntity.toDomain() = AudioAsset(id, takeId, relativePath, mimeType, sampleRate, bitDepth, byteSize, peaksPath)
internal fun MidiAssetEntity.toDomain() = MidiAsset(id, takeId, relativePath, ppq, byteSize, eventCount)
internal fun TakeEntity.toDomain(audio: AudioAssetEntity?, midi: MidiAssetEntity?) =
    Take(id, songId, name, TakeType.valueOf(type), TakeStatus.valueOf(status), durationMs, audio?.toDomain(), midi?.toDomain(), sync.toMeta())

internal fun SectionEntity.toDomain() = Section(id, documentId, SectionTag.fromStored(tag), customLabel, number, bodyText, sortOrder, sync.toMeta())
internal fun LyricsDocumentEntity.toDomain(sections: List<SectionEntity>) = LyricsDocument(id, songId, sections.map { it.toDomain() }, sync.toMeta())
internal fun NoteEntity.toDomain() = Note(id, songId, bodyText, voiceTakeId, mediaUri, sectionRefId, takeRefId, sync.toMeta())
internal fun NudgeEntity.toDomain() = Nudge(id, targetSongId, targetTakeId, runCatching { NudgeKind.valueOf(kind) }.getOrDefault(NudgeKind.USER_REMINDER), title, body, isRead == 1, sync.toMeta())
