package com.songpad.core.data.testing

import com.songpad.core.model.data.LyricsRepository
import com.songpad.core.model.data.NoteRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionOrdering
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.Note
import com.songpad.core.model.note.NoteDraft
import com.songpad.core.model.note.NoteValidation
import com.songpad.core.model.sync.SyncMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeLyricsRepository(val store: InMemoryStore = InMemoryStore()) : LyricsRepository {
    override fun observeDocument(songId: String): Flow<LyricsDocument?> = store.documents.map { it[songId] }

    override suspend fun ensureDocument(songId: String): Result<LyricsDocument> {
        store.documents.value[songId]?.let { return Result.success(it) }
        val doc = LyricsDocument(store.ids.newId(), songId, emptyList(), SyncMeta.create(store.clock.nowMillis()))
        store.documents.update { it + (songId to doc) }
        return Result.success(doc)
    }

    override suspend fun insertSection(songId: String, tag: SectionTag, customLabel: String?, atIndex: Int?): Result<Section> {
        val doc = ensureDocument(songId).getOrThrow()
        val now = store.clock.nowMillis()
        val section = Section(store.ids.newId(), doc.id, tag, customLabel, null, "", doc.sections.size, SyncMeta.create(now))
        val sections = SectionOrdering.renumber(SectionOrdering.insert(doc.sections, section, atIndex ?: doc.sections.size))
        store.documents.update { it + (songId to doc.copy(sections = sections, meta = doc.meta.touched(now))) }
        return Result.success(sections.first { it.id == section.id })
    }

    override suspend fun updateSectionText(sectionId: String, bodyText: String): Result<Unit> = mutateSection(sectionId) { it.copy(bodyText = bodyText) }

    override suspend fun moveSection(sectionId: String, toIndex: Int): Result<Unit> {
        val (songId, doc) = find(sectionId) ?: return domainFailure(DataError.NotFound("Section", sectionId))
        val moved = SectionOrdering.renumber(SectionOrdering.move(doc.sections, sectionId, toIndex))
        store.documents.update { it + (songId to doc.copy(sections = moved)) }
        return Result.success(Unit)
    }

    override suspend fun deleteSection(sectionId: String): Result<Unit> {
        val (songId, doc) = find(sectionId) ?: return domainFailure(DataError.NotFound("Section", sectionId))
        val remaining = SectionOrdering.renumber(SectionOrdering.normalize(doc.sections.filter { it.id != sectionId }))
        store.documents.update { it + (songId to doc.copy(sections = remaining)) }
        return Result.success(Unit)
    }

    private fun find(sectionId: String): Pair<String, LyricsDocument>? =
        store.documents.value.entries.firstOrNull { e -> e.value.sections.any { it.id == sectionId } }?.let { it.key to it.value }

    private fun mutateSection(sectionId: String, f: (Section) -> Section): Result<Unit> {
        val (songId, doc) = find(sectionId) ?: return domainFailure(DataError.NotFound("Section", sectionId))
        val now = store.clock.nowMillis()
        val sections = doc.sections.map { if (it.id == sectionId) f(it).copy(meta = it.meta.touched(now)) else it }
        store.documents.update { it + (songId to doc.copy(sections = sections, meta = doc.meta.touched(now))) }
        return Result.success(Unit)
    }
}

class FakeNoteRepository(val store: InMemoryStore = InMemoryStore()) : NoteRepository {
    override fun observeForSong(songId: String): Flow<List<Note>> =
        store.notes.map { m -> m.values.filter { it.songId == songId && !it.meta.deleted }.sortedBy { it.meta.createdAt } }

    override suspend fun create(draft: NoteDraft): Result<Note> {
        NoteValidation.validate(draft)?.let { return domainFailure(it) }
        val note = Note(store.ids.newId(), draft.songId, draft.bodyText, draft.voiceTakeId, draft.mediaUri, draft.sectionRefId, draft.takeRefId, SyncMeta.create(store.clock.nowMillis()))
        store.notes.update { it + (note.id to note) }
        return Result.success(note)
    }

    override suspend fun update(noteId: String, bodyText: String): Result<Unit> {
        val n = store.notes.value[noteId] ?: return domainFailure(DataError.NotFound("Note", noteId))
        if (!NoteValidation.hasContent(bodyText, n.voiceTakeId, n.mediaUri)) return domainFailure(DataError.InvalidInput("A note needs text, a voice note, or an attachment."))
        store.notes.update { it + (noteId to n.copy(bodyText = bodyText, meta = n.meta.touched(store.clock.nowMillis()))) }
        return Result.success(Unit)
    }

    override suspend fun softDelete(noteId: String): Result<Unit> {
        val n = store.notes.value[noteId] ?: return domainFailure(DataError.NotFound("Note", noteId))
        store.notes.update { it + (noteId to n.copy(meta = n.meta.softDeleted(store.clock.nowMillis()))) }
        return Result.success(Unit)
    }
}
