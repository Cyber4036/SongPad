package com.songpad.core.data.testing

import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongDetail
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.song.SongStatusTransitions
import com.songpad.core.model.sync.SyncMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSongRepository(val store: InMemoryStore = InMemoryStore()) : SongRepository {

    override fun observeSongs(filter: SongFilter): Flow<List<Song>> = store.songs.map { m ->
        m.values.filter { !it.meta.deleted && SongStatusTransitions.matches(it.status, it.isFavorite, filter) }
            .sortedByDescending { it.meta.updatedAt }
    }

    override fun observeSong(songId: String): Flow<SongDetail?> =
        combine(store.songs, store.takes, store.documents, store.notes) { songs, takes, docs, notes ->
            val song = songs[songId]?.takeIf { !it.meta.deleted } ?: return@combine null
            SongDetail(
                song = song,
                takes = takes.values.filter { it.songId == songId && !it.meta.deleted }.sortedByDescending { it.meta.createdAt },
                lyrics = docs[songId],
                notes = notes.values.filter { it.songId == songId && !it.meta.deleted }.sortedByDescending { it.meta.createdAt },
            )
        }

    override suspend fun create(name: String, adoptTakeId: String?): Result<Song> {
        val now = store.clock.nowMillis()
        val song = Song(store.ids.newId(), name, SongStatus.ONGOING, false, SyncMeta.create(now))
        store.songs.update { it + (song.id to song) }
        if (adoptTakeId != null) {
            val take = store.takes.value[adoptTakeId] ?: return domainFailure(DataError.NotFound("Take", adoptTakeId))
            store.takes.update { it + (adoptTakeId to take.copy(songId = song.id, meta = take.meta.touched(now))) }
        }
        return Result.success(song)
    }

    override suspend fun setStatus(songId: String, status: SongStatus): Result<Unit> = mutate(songId) { it.copy(status = status) }
    override suspend fun toggleFavorite(songId: String): Result<Unit> = mutate(songId) { it.copy(isFavorite = !it.isFavorite) }
    override suspend fun softDelete(songId: String): Result<Unit> = mutate(songId) { it.copy(meta = it.meta.softDeleted(store.clock.nowMillis())) }
    override suspend fun rename(songId: String, name: String): Result<Unit> = mutate(songId) { it.copy(name = name) }
    override suspend fun getSong(songId: String): Song? = store.songs.value[songId]?.takeIf { !it.meta.deleted }

    private fun mutate(songId: String, f: (Song) -> Song): Result<Unit> {
        val s = store.songs.value[songId] ?: return domainFailure(DataError.NotFound("Song", songId))
        val u = f(s)
        store.songs.update { it + (songId to (if (u.meta.deleted) u else u.copy(meta = u.meta.touched(store.clock.nowMillis())))) }
        return Result.success(Unit)
    }
}
