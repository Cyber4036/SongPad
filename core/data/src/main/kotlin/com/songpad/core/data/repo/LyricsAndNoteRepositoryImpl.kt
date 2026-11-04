package com.songpad.core.data.repo

import com.songpad.core.data.db.withTransactionCompat
import com.songpad.core.data.db.SongPadDatabase
import com.songpad.core.data.db.entity.LyricsDocumentEntity
import com.songpad.core.data.db.entity.NoteEntity
import com.songpad.core.data.db.entity.SectionEntity
import com.songpad.core.model.data.LyricsRepository
import com.songpad.core.model.data.NoteRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.DomainException
import com.songpad.core.model.error.runDomain
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.lyrics.InMemoryLyricsDraftStore
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.LyricsDraftStore
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionOrdering
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.lyrics.withDrafts
import com.songpad.core.model.note.Note
import com.songpad.core.model.note.NoteDraft
import com.songpad.core.model.note.NoteValidation
import com.songpad.core.model.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val db: SongPadDatabase,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val draftStore: LyricsDraftStore,
) : LyricsRepository {
    constructor(
        db: SongPadDatabase,
        ids: IdGenerator,
        clock: Clock,
    ) : this(db, ids, clock, InMemoryLyricsDraftStore())

    private val dao get() = db.lyricsDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDocument(songId: String): Flow<LyricsDocument?> = dao.observeDocument(songId).flatMapLatest { doc ->
        if (doc == null) flowOf(null)
        else combine(dao.observeSections(doc.id), draftStore.drafts) { secs, drafts ->
            doc.toDomain(secs).withDrafts(drafts)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDocumentById(documentId: String): Flow<LyricsDocument?> = dao.observeDocumentById(documentId).flatMapLatest { doc ->
        if (doc == null) flowOf(null)
        else combine(dao.observeSections(doc.id), draftStore.drafts) { secs, drafts ->
            doc.toDomain(secs).withDrafts(drafts)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUnassigned(): Flow<List<LyricsDocument>> = dao.observeUnassignedDocuments().flatMapLatest { docs ->
        if (docs.isEmpty()) flowOf(emptyList())
        else combine(
            combine(docs.map { d -> dao.observeSections(d.id).map { d.toDomain(it) } }) { it.toList() },
            draftStore.drafts,
        ) { arr, drafts ->
            arr.map { it.withDrafts(drafts) }.filter { it.hasNonEmptyText }
        }
    }

    override suspend fun getDocumentById(documentId: String): LyricsDocument? {
        val doc = dao.documentById(documentId) ?: return null
        return doc.toDomain(dao.sections(doc.id)).withDrafts(draftStore.drafts.value)
    }

    override fun saveSectionDraft(sectionId: String, bodyText: String) {
        draftStore.putDraft(sectionId, bodyText, clock.nowMillis())
    }

    override fun getSectionDraft(sectionId: String): String? =
        draftStore.getDraft(sectionId)?.bodyText

    override fun observeSectionDrafts(): Flow<Map<String, String>> =
        draftStore.drafts.map { map -> map.mapValues { it.value.bodyText } }

    override suspend fun flushDrafts(): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val pending = draftStore.drafts.value
        if (pending.isEmpty()) return@runDomain
        db.withTransactionCompat {
            val touchedDocs = mutableSetOf<String>()
            for ((sectionId, draft) in pending) {
                val s = dao.section(sectionId)
                if (s != null) {
                    val ts = maxOf(clock.nowMillis(), draft.updatedAtMillis)
                    dao.updateSection(s.copy(bodyText = draft.bodyText, sync = s.sync.touched(ts)))
                    touchedDocs += s.documentId
                }
                if (draftStore.getDraft(sectionId)?.bodyText == draft.bodyText) {
                    draftStore.removeDraft(sectionId)
                }
            }
            val now = clock.nowMillis()
            for (docId in touchedDocs) {
                dao.documentById(docId)?.let { dao.updateDocument(it.copy(sync = it.sync.touched(now))) }
            }
        }
    }

    override suspend fun ensureDocument(songId: String): Result<LyricsDocument> = runDomain({ DataError.Io(it.message ?: "") }) {
        db.withTransactionCompat {
            val existing = dao.documentForSong(songId)
            if (existing != null) return@withTransactionCompat existing.toDomain(dao.sections(existing.id)).withDrafts(draftStore.drafts.value)
            val song = db.songDao().get(songId) ?: throw DomainException(DataError.NotFound("Song", songId))
            val doc = LyricsDocumentEntity(ids.newId(), songId, song.name, newSync(clock.nowMillis()))
            dao.insertDocument(doc)
            doc.toDomain(emptyList())
        }
    }

    override suspend fun createStandalone(name: String?): Result<LyricsDocument> = runDomain({ DataError.Io(it.message ?: "") }) {
        db.withTransactionCompat {
            val now = clock.nowMillis()
            val resolvedName = name?.trim()?.takeIf { it.isNotEmpty() }
                ?: com.songpad.core.model.usecase.AutoIncrementNaming.nextName(
                    com.songpad.core.model.usecase.AutoIncrementNaming.LYRIC_PREFIX,
                    dao.allActiveNames(),
                )
            val doc = LyricsDocumentEntity(ids.newId(), null, resolvedName, newSync(now))
            dao.insertDocument(doc)
            val firstSection = SectionEntity(
                id = ids.newId(),
                documentId = doc.id,
                tag = SectionTag.VERSE.name,
                customLabel = null,
                number = null,
                bodyText = "",
                sortOrder = 0,
                sync = newSync(now),
            )
            dao.insertSection(firstSection)
            doc.toDomain(listOf(firstSection))
        }
    }

    override suspend fun insertSection(songId: String, tag: SectionTag, customLabel: String?, atIndex: Int?): Result<Section> =
        runDomain({ DataError.Io(it.message ?: "") }) {
            val doc = ensureDocument(songId).getOrThrow()
            insertSectionInternal(doc.id, tag, customLabel, atIndex)
        }

    override suspend fun insertSectionInDocument(documentId: String, tag: SectionTag, customLabel: String?, atIndex: Int?): Result<Section> =
        runDomain({ DataError.Io(it.message ?: "") }) {
            dao.documentById(documentId) ?: throw DomainException(DataError.NotFound("LyricsDocument", documentId))
            insertSectionInternal(documentId, tag, customLabel, atIndex)
        }

    private suspend fun insertSectionInternal(documentId: String, tag: SectionTag, customLabel: String?, atIndex: Int?): Section {
        val now = clock.nowMillis()
        return db.withTransactionCompat {
            val current = dao.sections(documentId).map { it.toDomain() }
            val section = Section(ids.newId(), documentId, tag, customLabel, null, "", current.size, com.songpad.core.model.sync.SyncMeta.create(now))
            val reordered = SectionOrdering.renumber(SectionOrdering.insert(current, section, atIndex ?: current.size))
            val entities = reordered.map { it.toEntity() }
            dao.insertSection(entities.first { it.id == section.id })
            dao.updateSections(entities.filter { it.id != section.id })
            dao.documentById(documentId)?.let { dao.updateDocument(it.copy(sync = it.sync.touched(now))) }
            reordered.first { it.id == section.id }
        }
    }

    override suspend fun updateSectionText(sectionId: String, bodyText: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val now = clock.nowMillis()
        val s = dao.section(sectionId) ?: throw DomainException(DataError.NotFound("Section", sectionId))
        dao.updateSection(s.copy(bodyText = bodyText, sync = s.sync.touched(now)))
        dao.documentById(s.documentId)?.let { dao.updateDocument(it.copy(sync = it.sync.touched(now))) }
        if (draftStore.getDraft(sectionId)?.bodyText == bodyText) {
            draftStore.removeDraft(sectionId)
        }
    }

    override suspend fun updateSectionTag(sectionId: String, tag: SectionTag, customLabel: String?): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val now = clock.nowMillis()
        db.withTransactionCompat {
            val s = dao.section(sectionId) ?: throw DomainException(DataError.NotFound("Section", sectionId))
            val current = dao.sections(s.documentId).map {
                val base = it.toDomain()
                val withDraft = draftStore.getDraft(it.id)?.let { d -> base.copy(bodyText = d.bodyText) } ?: base
                if (it.id == sectionId) withDraft.copy(tag = tag, customLabel = if (tag == SectionTag.CUSTOM) customLabel else null, meta = it.sync.touched(now).toMeta())
                else withDraft
            }
            val renumbered = SectionOrdering.renumber(current)
            dao.updateSections(renumbered.map { it.toEntity() })
            dao.documentById(s.documentId)?.let { dao.updateDocument(it.copy(sync = it.sync.touched(now))) }
        }
    }

    /** Sort-order-only write, deliberately not [reorder]'s general full-entity path: a move can land while a
     * different section's autosave debounce is still pending, and rewriting whole rows from a stale in-memory
     * snapshot would risk clobbering that section's just-typed bodyText with what was on disk when this read
     * happened (§21 no-destructive-editing). */
    override suspend fun moveSection(sectionId: String, toIndex: Int): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        db.withTransactionCompat {
            val s = dao.section(sectionId) ?: throw DomainException(DataError.NotFound("Section", sectionId))
            val current = dao.sections(s.documentId).map { it.toDomain() }
            SectionOrdering.renumber(SectionOrdering.move(current, sectionId, toIndex)).forEach { dao.updateSortOrder(it.id, it.sortOrder, it.number) }
        }
    }

    override suspend fun deleteSection(sectionId: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val now = clock.nowMillis()
        draftStore.removeDraft(sectionId)
        db.withTransactionCompat {
            val s = dao.section(sectionId) ?: throw DomainException(DataError.NotFound("Section", sectionId))
            dao.updateSection(s.copy(sync = s.sync.softDeleted(now)))
            val remaining = dao.sections(s.documentId).map {
                val base = it.toDomain()
                draftStore.getDraft(it.id)?.let { d -> base.copy(bodyText = d.bodyText) } ?: base
            }
            dao.updateSections(SectionOrdering.renumber(SectionOrdering.normalize(remaining)).map { it.toEntity() })
            dao.documentById(s.documentId)?.let { dao.updateDocument(it.copy(sync = it.sync.touched(now))) }
        }
    }

    override suspend fun renameDocument(documentId: String, name: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw DomainException(DataError.InvalidInput("Give the lyrics a name."))
        val doc = dao.documentById(documentId) ?: throw DomainException(DataError.NotFound("LyricsDocument", documentId))
        dao.updateDocument(doc.copy(name = trimmed, sync = doc.sync.touched(clock.nowMillis())))
    }

    override suspend fun assignToSong(documentId: String, songId: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        flushDrafts()
        val now = clock.nowMillis()
        db.withTransactionCompat {
            val song = db.songDao().get(songId) ?: throw DomainException(DataError.NotFound("Song", songId))
            val sourceDoc = dao.documentById(documentId) ?: throw DomainException(DataError.NotFound("LyricsDocument", documentId))
            if (sourceDoc.songId == songId) return@withTransactionCompat
            val existingTarget = dao.documentForSong(songId)
            if (existingTarget == null) {
                dao.updateDocument(sourceDoc.copy(songId = songId, sync = sourceDoc.sync.touched(now)))
                val secs = dao.sections(sourceDoc.id).map { it.copy(sync = it.sync.touched(now)) }
                dao.updateSections(secs)
            } else {
                val targetSecs = dao.sections(existingTarget.id)
                val sourceSecs = dao.sections(sourceDoc.id)
                val nonEmptyTarget = targetSecs.filter { it.bodyText.isNotBlank() }
                val blankTarget = targetSecs.filter { it.bodyText.isBlank() }
                val nonEmptySource = sourceSecs.filter { it.bodyText.isNotBlank() }.ifEmpty { sourceSecs.take(1) }
                val blankSource = sourceSecs.filter { sec -> nonEmptySource.none { it.id == sec.id } }

                if (nonEmptySource.isNotEmpty() && blankTarget.isNotEmpty()) {
                    dao.updateSections(blankTarget.map { it.copy(sync = it.sync.softDeleted(now)) })
                }
                if (blankSource.isNotEmpty()) {
                    dao.updateSections(blankSource.map { it.copy(sync = it.sync.softDeleted(now)) })
                }

                val keptTarget = if (nonEmptySource.isNotEmpty()) nonEmptyTarget else targetSecs
                val movedSource = nonEmptySource.map { it.copy(documentId = existingTarget.id, sync = it.sync.touched(now)) }
                val combined = (keptTarget + movedSource).mapIndexed { idx, s -> s.toDomain().copy(sortOrder = idx) }
                val renumbered = SectionOrdering.renumber(combined).map { it.toEntity() }
                dao.updateSections(renumbered)
                dao.updateDocument(existingTarget.copy(sync = existingTarget.sync.touched(now)))
                dao.updateDocument(sourceDoc.copy(songId = null, sync = sourceDoc.sync.softDeleted(now)))
            }
            db.songDao().update(song.copy(sync = song.sync.touched(now)))
        }
    }

    override suspend fun deleteDocument(documentId: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val now = clock.nowMillis()
        db.withTransactionCompat {
            val doc = dao.documentById(documentId) ?: return@withTransactionCompat
            val secs = dao.sections(doc.id).map { it.copy(sync = it.sync.softDeleted(now)) }
            draftStore.removeDrafts(secs.map { it.id })
            if (secs.isNotEmpty()) dao.updateSections(secs)
            dao.updateDocument(doc.copy(songId = null, sync = doc.sync.softDeleted(now)))
        }
    }

    private fun Section.toEntity() = SectionEntity(id, documentId, tag.name, customLabel, number, bodyText, sortOrder, meta.toColumns())
}

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val db: SongPadDatabase,
    private val ids: IdGenerator,
    private val clock: Clock,
) : NoteRepository {
    private val dao get() = db.noteDao()

    override fun observeForSong(songId: String): Flow<List<Note>> = dao.observeForSong(songId).map { l -> l.map { it.toDomain() } }

    /** Empty notes are rejected here, in the domain layer, not by the DB (§6.1). */
    override suspend fun create(draft: NoteDraft): Result<Note> = runDomain({ DataError.Io(it.message ?: "") }) {
        NoteValidation.validate(draft)?.let { throw DomainException(it) }
        val note = NoteEntity(ids.newId(), draft.songId, draft.bodyText.trim(), draft.voiceTakeId, draft.mediaUri, draft.sectionRefId, draft.takeRefId, newSync(clock.nowMillis()))
        dao.insert(note)
        note.toDomain()
    }

    override suspend fun update(noteId: String, bodyText: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val n = dao.get(noteId) ?: throw DomainException(DataError.NotFound("Note", noteId))
        if (!NoteValidation.hasContent(bodyText, n.voiceTakeId, n.mediaUri)) throw DomainException(DataError.InvalidInput("A note needs text, a voice note, or an attachment."))
        dao.update(n.copy(bodyText = bodyText.trim(), sync = n.sync.touched(clock.nowMillis())))
    }

    override suspend fun softDelete(noteId: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val n = dao.get(noteId) ?: throw DomainException(DataError.NotFound("Note", noteId))
        dao.update(n.copy(sync = n.sync.softDeleted(clock.nowMillis())))
    }
}
