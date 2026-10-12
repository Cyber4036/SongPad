package com.songpad.core.data.repo

import com.songpad.core.data.db.withTransactionCompat
import com.songpad.core.data.db.SongPadDatabase
import com.songpad.core.data.db.entity.SongEntity
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.DomainException
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.error.runDomain
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongDetail
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.song.SongStatus
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
class SongRepositoryImpl @Inject constructor(
    private val db: SongPadDatabase,
    private val takeRepository: TakeRepositoryImpl,
    private val ids: IdGenerator,
    private val clock: Clock,
) : SongRepository {

    private val songs get() = db.songDao()

    override fun observeSongs(filter: SongFilter): Flow<List<Song>> = when (filter) {
        SongFilter.ALL -> songs.observeNotArchived()
        SongFilter.FAVORITES -> songs.observeFavorites()
        SongFilter.ONGOING -> songs.observeByStatus(SongStatus.ONGOING.name)
        SongFilter.FINISHED -> songs.observeByStatus(SongStatus.FINISHED.name)
        SongFilter.ARCHIVED -> songs.observeByStatus(SongStatus.ARCHIVED.name)
    }.map { list -> list.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSong(songId: String): Flow<SongDetail?> = songs.observe(songId).flatMapLatest { song ->
        if (song == null) return@flatMapLatest flowOf(null)
        val lyrics = db.lyricsDao().observeDocument(songId).flatMapLatest { doc ->
            if (doc == null) flowOf(null) else db.lyricsDao().observeSections(doc.id).map { doc.toDomain(it) }
        }
        combine(takeRepository.observeForSong(songId), lyrics, db.noteDao().observeForSong(songId)) { takes, doc, notes ->
            SongDetail(song.toDomain(), takes, doc, notes.map { it.toDomain() })
        }
    }

    /** Phase 0 §24.3: lightweight creation; adopting a loose take happens in the same transaction. */
    override suspend fun create(name: String, adoptTakeId: String?): Result<Song> = runDomain({ DataError.Io(it.message ?: "") }) {
        val now = clock.nowMillis()
        db.withTransactionCompat {
            val song = SongEntity(ids.newId(), name.trim(), SongStatus.ONGOING.name, 0, newSync(now))
            songs.insert(song)
            if (adoptTakeId != null) {
                val take = db.takeDao().get(adoptTakeId) ?: throw DomainException(DataError.NotFound("Take", adoptTakeId))
                db.takeDao().update(take.copy(songId = song.id, sync = take.sync.touched(now)))
            }
            song.toDomain()
        }
    }

    override suspend fun setStatus(songId: String, status: SongStatus): Result<Unit> = mutate(songId) { it.copy(status = status.name) }
    override suspend fun toggleFavorite(songId: String): Result<Unit> = mutate(songId) { it.copy(isFavorite = if (it.isFavorite == 1) 0 else 1) }
    override suspend fun rename(songId: String, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return domainFailure(DataError.InvalidInput("Give the song a name."))
        if (nameExists(trimmed, excludeId = songId)) return domainFailure(DataError.InvalidInput("A song with that name already exists."))
        return mutate(songId) { it.copy(name = trimmed) }
    }

    override suspend fun softDelete(songId: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val s = songs.get(songId) ?: throw DomainException(DataError.NotFound("Song", songId))
        songs.update(s.copy(sync = s.sync.softDeleted(clock.nowMillis())))
    }

    override suspend fun getSong(songId: String): Song? = songs.get(songId)?.toDomain()

    override suspend fun nameExists(name: String, excludeId: String?): Boolean =
        songs.nameCount(name.trim(), excludeId) > 0

    private suspend fun mutate(songId: String, f: (SongEntity) -> SongEntity): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val s = songs.get(songId) ?: throw DomainException(DataError.NotFound("Song", songId))
        songs.update(f(s).copy(sync = s.sync.touched(clock.nowMillis())))
    }
}
