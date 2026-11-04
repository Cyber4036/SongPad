package com.songpad.core.data.testing

import com.songpad.core.model.data.LyricsRepository
import com.songpad.core.model.data.NoteRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionDraft
import com.songpad.core.model.lyrics.SectionOrdering
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.lyrics.withDrafts
import com.songpad.core.model.note.Note
import com.songpad.core.model.note.NoteDraft
import com.songpad.core.model.note.NoteValidation
import com.songpad.core.model.sync.SyncMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeLyricsRepository(val store: InMemoryStore = InMemoryStore()) : LyricsRepository {
    override fun observeDocument(songId: String): Flow<LyricsDocument?> =
        combine(store.documents, store.sectionDrafts) { m, drafts ->
            (m[songId] ?: m.values.firstOrNull { it.songId == songId && !it.meta.deleted })?.withDrafts(drafts)
        }

    override fun observeDocumentById(documentId: String): Flow<LyricsDocument?> =
        combine(store.documents, store.sectionDrafts) { m, drafts ->
            m.values.firstOrNull { it.id == documentId && !it.meta.deleted }?.withDrafts(drafts)
        }

    override fun observeUnassigned(): Flow<List<LyricsDocument>> =
        combine(store.documents, store.sectionDrafts) { m, drafts ->
            m.values
                .filter { it.songId == null && !it.meta.deleted }
                .map { it.withDrafts(drafts) }
                .filter { it.hasNonEmptyText }
                .sortedByDescending { it.meta.updatedAt }
        }

    override suspend fun getDocumentById(documentId: String): LyricsDocument? =
        store.documents.value.values.firstOrNull { it.id == documentId && !it.meta.deleted }
            ?.withDrafts(store.sectionDrafts.value)

    override fun saveSectionDraft(sectionId: String, bodyText: String) {
        val now = store.clock.nowMillis()
        store.sectionDrafts.update { it + (sectionId to SectionDraft(sectionId, bodyText, now)) }
    }

    override fun getSectionDraft(sectionId: String): String? =
        store.sectionDrafts.value[sectionId]?.bodyText

    override fun observeSectionDrafts(): Flow<Map<String, String>> =
        store.sectionDrafts.map { map -> map.mapValues { it.value.bodyText } }

    override suspend fun flushDrafts(): Result<Unit> {
        val pending = store.sectionDrafts.value
        for ((sectionId, draft) in pending) {
            mutateSection(sectionId) { it.copy(bodyText = draft.bodyText) }
            if (store.sectionDrafts.value[sectionId]?.bodyText == draft.bodyText) {
                store.sectionDrafts.update { it - sectionId }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun ensureDocument(songId: String): Result<LyricsDocument> {
        store.documents.value[songId]?.takeIf { !it.meta.deleted }?.let { return Result.success(it.withDrafts(store.sectionDrafts.value)) }
        store.documents.value.values.firstOrNull { it.songId == songId && !it.meta.deleted }?.let { return Result.success(it.withDrafts(store.sectionDrafts.value)) }
        val doc = LyricsDocument(store.ids.newId(), songId, emptyList(), SyncMeta.create(store.clock.nowMillis()))
        store.documents.update { it + (songId to doc) }
        return Result.success(doc)
    }

    override suspend fun createStandalone(name: String?): Result<LyricsDocument> {
        val now = store.clock.nowMillis()
        val existingNames = store.documents.value.values.filter { !it.meta.deleted }.map { it.name }
        val resolvedName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: com.songpad.core.model.usecase.AutoIncrementNaming.nextName(
                com.songpad.core.model.usecase.AutoIncrementNaming.LYRIC_PREFIX,
                existingNames,
            )
        val docId = store.ids.newId()
        val firstSection = Section(store.ids.newId(), docId, SectionTag.VERSE, null, null, "", 0, SyncMeta.create(now))
        val doc = LyricsDocument(docId, null, listOf(firstSection), SyncMeta.create(now), resolvedName)
        store.documents.update { it + (doc.id to doc) }
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

    override suspend fun insertSectionInDocument(documentId: String, tag: SectionTag, customLabel: String?, atIndex: Int?): Result<Section> {
        val (key, doc) = findByDocId(documentId) ?: return domainFailure(DataError.NotFound("LyricsDocument", documentId))
        val now = store.clock.nowMillis()
        val section = Section(store.ids.newId(), doc.id, tag, customLabel, null, "", doc.sections.size, SyncMeta.create(now))
        val sections = SectionOrdering.renumber(SectionOrdering.insert(doc.sections, section, atIndex ?: doc.sections.size))
        store.documents.update { it + (key to doc.copy(sections = sections, meta = doc.meta.touched(now))) }
        return Result.success(sections.first { it.id == section.id })
    }

    override suspend fun updateSectionText(sectionId: String, bodyText: String): Result<Unit> =
        mutateSection(sectionId) { it.copy(bodyText = bodyText) }.also {
            if (store.sectionDrafts.value[sectionId]?.bodyText == bodyText) {
                store.sectionDrafts.update { m -> m - sectionId }
            }
        }

    override suspend fun updateSectionTag(sectionId: String, tag: SectionTag, customLabel: String?): Result<Unit> {
        val (key, doc) = find(sectionId) ?: return domainFailure(DataError.NotFound("Section", sectionId))
        val now = store.clock.nowMillis()
        val updated = doc.sections.map {
            if (it.id == sectionId) it.copy(tag = tag, customLabel = if (tag == SectionTag.CUSTOM) customLabel else null, meta = it.meta.touched(now)) else it
        }
        val renumbered = SectionOrdering.renumber(updated)
        store.documents.update { it + (key to doc.copy(sections = renumbered, meta = doc.meta.touched(now))) }
        return Result.success(Unit)
    }

    override suspend fun moveSection(sectionId: String, toIndex: Int): Result<Unit> {
        val (key, doc) = find(sectionId) ?: return domainFailure(DataError.NotFound("Section", sectionId))
        val moved = SectionOrdering.renumber(SectionOrdering.move(doc.sections, sectionId, toIndex))
        store.documents.update { it + (key to doc.copy(sections = moved)) }
        return Result.success(Unit)
    }

    override suspend fun deleteSection(sectionId: String): Result<Unit> {
        val (key, doc) = find(sectionId) ?: return domainFailure(DataError.NotFound("Section", sectionId))
        store.sectionDrafts.update { it - sectionId }
        val remaining = SectionOrdering.renumber(SectionOrdering.normalize(doc.sections.filter { it.id != sectionId }))
        store.documents.update { it + (key to doc.copy(sections = remaining)) }
        return Result.success(Unit)
    }

    override suspend fun renameDocument(documentId: String, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return domainFailure(DataError.InvalidInput("Give the lyrics a name."))
        val (key, doc) = findByDocId(documentId) ?: return domainFailure(DataError.NotFound("LyricsDocument", documentId))
        val now = store.clock.nowMillis()
        store.documents.update { it + (key to doc.copy(name = trimmed, meta = doc.meta.touched(now))) }
        return Result.success(Unit)
    }

    override suspend fun assignToSong(documentId: String, songId: String): Result<Unit> {
        flushDrafts()
        val (sourceKey, sourceDoc) = findByDocId(documentId) ?: return domainFailure(DataError.NotFound("LyricsDocument", documentId))
        if (sourceDoc.songId == songId) return Result.success(Unit)
        val now = store.clock.nowMillis()
        val existingTarget = store.documents.value[songId]?.takeIf { !it.meta.deleted }
        val nonEmptySource = sourceDoc.sections.filter { it.bodyText.isNotBlank() }.ifEmpty { sourceDoc.sections.take(1) }
        if (existingTarget == null) {
            val moved = sourceDoc.copy(
                songId = songId,
                sections = SectionOrdering.renumber(SectionOrdering.normalize(nonEmptySource)),
                meta = sourceDoc.meta.touched(now),
            )
            store.documents.update { (it - sourceKey) + (songId to moved) }
        } else {
            val keptTarget = existingTarget.sections.filter { it.bodyText.isNotBlank() }
            val movedSecs = nonEmptySource.map { it.copy(documentId = existingTarget.id, meta = it.meta.touched(now)) }
            val mergedSecs = SectionOrdering.renumber((keptTarget + movedSecs).mapIndexed { idx, s -> s.copy(sortOrder = idx) })
            val updatedTarget = existingTarget.copy(sections = mergedSecs, meta = existingTarget.meta.touched(now))
            store.documents.update { (it - sourceKey) + (songId to updatedTarget) }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteDocument(documentId: String): Result<Unit> {
        val (key, doc) = findByDocId(documentId) ?: return Result.success(Unit)
        val secIds = doc.sections.map { it.id }.toSet()
        store.sectionDrafts.update { it - secIds }
        store.documents.update { it - key }
        return Result.success(Unit)
    }

    private fun findByDocId(documentId: String): Pair<String, LyricsDocument>? =
        store.documents.value.entries.firstOrNull { e -> e.value.id == documentId && !e.value.meta.deleted }?.let { it.key to it.value }

    private fun find(sectionId: String): Pair<String, LyricsDocument>? =
        store.documents.value.entries.firstOrNull { e -> !e.value.meta.deleted && e.value.sections.any { it.id == sectionId } }?.let { it.key to it.value }

    private fun mutateSection(sectionId: String, f: (Section) -> Section): Result<Unit> {
        val (key, doc) = find(sectionId) ?: return domainFailure(DataError.NotFound("Section", sectionId))
        val now = store.clock.nowMillis()
        val sections = doc.sections.map { if (it.id == sectionId) f(it).copy(meta = it.meta.touched(now)) else it }
        store.documents.update { it + (key to doc.copy(sections = sections, meta = doc.meta.touched(now))) }
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
