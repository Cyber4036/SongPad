package com.songpad.core.data.repo

import androidx.room.withTransaction
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
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionOrdering
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.Note
import com.songpad.core.model.note.NoteDraft
import com.songpad.core.model.note.NoteValidation
import com.songpad.core.model.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
) : LyricsRepository {
    private val dao get() = db.lyricsDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDocument(songId: String): Flow<LyricsDocument?> = dao.observeDocument(songId).flatMapLatest { doc ->
        if (doc == null) flowOf(null) else dao.observeSections(doc.id).map { doc.toDomain(it) }
    }

    override suspend fun ensureDocument(songId: String): Result<LyricsDocument> = runDomain({ DataError.Io(it.message ?: "") }) {
        db.withTransaction {
            val existing = dao.documentForSong(songId)
            if (existing != null) return@withTransaction existing.toDomain(dao.sections(existing.id))
            db.songDao().get(songId) ?: throw DomainException(DataError.NotFound("Song", songId))
            val doc = LyricsDocumentEntity(ids.newId(), songId, newSync(clock.nowMillis()))
            dao.insertDocument(doc)
            doc.toDomain(emptyList())
        }
    }

    override suspend fun insertSection(songId: String, tag: SectionTag, customLabel: String?, atIndex: Int?): Result<Section> =
        runDomain({ DataError.Io(it.message ?: "") }) {
            val doc = ensureDocument(songId).getOrThrow()
            val now = clock.nowMillis()
            db.withTransaction {
                val current = dao.sections(doc.id).map { it.toDomain() }
                val section = Section(ids.newId(), doc.id, tag, customLabel, null, "", current.size, com.songpad.core.model.sync.SyncMeta.create(now))
                val reordered = SectionOrdering.renumber(SectionOrdering.insert(current, section, atIndex ?: current.size))
                val entities = reordered.map { it.toEntity() }
                dao.insertSection(entities.first { it.id == section.id })
                dao.updateSections(entities.filter { it.id != section.id })
                reordered.first { it.id == section.id }
            }
        }

    override suspend fun updateSectionText(sectionId: String, bodyText: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val s = dao.section(sectionId) ?: throw DomainException(DataError.NotFound("Section", sectionId))
        dao.updateSection(s.copy(bodyText = bodyText, sync = s.sync.touched(clock.nowMillis())))
    }

    override suspend fun moveSection(sectionId: String, toIndex: Int): Result<Unit> = reorder(sectionId) { SectionOrdering.move(it, sectionId, toIndex) }

    override suspend fun deleteSection(sectionId: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val now = clock.nowMillis()
        db.withTransaction {
            val s = dao.section(sectionId) ?: throw DomainException(DataError.NotFound("Section", sectionId))
            dao.updateSection(s.copy(sync = s.sync.softDeleted(now)))
            val remaining = dao.sections(s.documentId).map { it.toDomain() }
            dao.updateSections(SectionOrdering.renumber(SectionOrdering.normalize(remaining)).map { it.toEntity() })
        }
    }

    private suspend fun reorder(sectionId: String, f: (List<Section>) -> List<Section>): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        db.withTransaction {
            val s = dao.section(sectionId) ?: throw DomainException(DataError.NotFound("Section", sectionId))
            val current = dao.sections(s.documentId).map { it.toDomain() }
            dao.updateSections(SectionOrdering.renumber(f(current)).map { it.toEntity() })
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
