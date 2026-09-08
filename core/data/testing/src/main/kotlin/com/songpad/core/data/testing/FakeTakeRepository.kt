package com.songpad.core.data.testing

import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.sync.SyncMeta
import com.songpad.core.model.take.AudioAsset
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.OrphanedTake
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeDraft
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.take.TakeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeTakeRepository(val store: InMemoryStore = InMemoryStore()) : TakeRepository {
    var failCommitWith: DomainError? = null
    val orphans = mutableListOf<OrphanedTake>()
    val hardDeleted = mutableListOf<String>()

    override fun observeForSong(songId: String): Flow<List<Take>> =
        store.takes.map { m -> m.values.filter { it.songId == songId && !it.meta.deleted }.sortedByDescending { it.meta.createdAt } }

    override fun observeUnassigned(): Flow<List<Take>> =
        store.takes.map { m -> m.values.filter { it.songId == null && !it.meta.deleted }.sortedByDescending { it.meta.createdAt } }

    override fun observeTake(takeId: String): Flow<Take?> = store.takes.map { it[takeId]?.takeIf { t -> !t.meta.deleted } }

    override suspend fun getTake(takeId: String): Take? = store.takes.value[takeId]?.takeIf { !it.meta.deleted }

    override suspend fun commit(draft: TakeDraft): Result<Take> {
        failCommitWith?.let { return domainFailure(it) }
        val now = store.clock.nowMillis()
        var songId = draft.songId
        if (draft.newSongName != null) {
            val song = Song(store.ids.newId(), draft.newSongName!!, SongStatus.ONGOING, false, SyncMeta.create(now))
            store.songs.update { it + (song.id to song) }
            songId = song.id
        }
        val audio = AudioAsset(
            id = store.ids.newId(), takeId = draft.takeId, relativePath = draft.audio.relativePath,
            mimeType = AudioFormat.MIME_RAW_PCM, sampleRate = AudioFormat.SAMPLE_RATE, bitDepth = AudioFormat.BIT_DEPTH,
            byteSize = draft.audio.byteSize, peaksPath = draft.audio.peaksPath,
        )
        val take = Take(draft.takeId, songId, draft.name, draft.type, TakeStatus.TRANSCODING, draft.audio.durationMs, audio, draft.midi, SyncMeta.create(now))
        store.takes.update { it + (take.id to take) }
        return Result.success(take)
    }

    override suspend fun assignToSong(takeId: String, songId: String): Result<Unit> = mutate(takeId) { it.copy(songId = songId) }
    override suspend fun rename(takeId: String, name: String): Result<Unit> = mutate(takeId) { it.copy(name = name) }
    override suspend fun softDelete(takeId: String): Result<Unit> = mutate(takeId) { it.copy(meta = it.meta.softDeleted(store.clock.nowMillis())) }
    override suspend fun findOrphaned(): List<OrphanedTake> = orphans.toList()

    override suspend fun markTranscoded(takeId: String, storedRelativePath: String, mimeType: String, byteSize: Long): Result<Unit> =
        mutate(takeId) { it.copy(status = TakeStatus.READY, audio = it.audio?.copy(relativePath = storedRelativePath, mimeType = mimeType, byteSize = byteSize)) }

    override suspend fun markFailed(takeId: String): Result<Unit> = mutate(takeId) { it.copy(status = TakeStatus.FAILED) }
    override suspend fun markOrphaned(takeId: String): Result<Unit> = mutate(takeId) { it.copy(status = TakeStatus.ORPHANED) }

    override suspend fun recoverOrphan(orphan: OrphanedTake, name: String): Result<Take> {
        orphans.remove(orphan)
        val now = store.clock.nowMillis()
        val audio = AudioAsset(store.ids.newId(), orphan.takeId, TakePaths.pcm(orphan.takeId), AudioFormat.MIME_RAW_PCM, AudioFormat.SAMPLE_RATE, AudioFormat.BIT_DEPTH, orphan.byteSize, null)
        val take = Take(orphan.takeId, orphan.songId, name, com.songpad.core.model.take.TakeType.VOICE, TakeStatus.TRANSCODING, orphan.durationMs, audio, null, SyncMeta.create(now))
        store.takes.update { it + (take.id to take) }
        return Result.success(take)
    }

    override suspend fun hardDeleteOrphan(takeId: String): Result<Unit> {
        orphans.removeAll { it.takeId == takeId }
        hardDeleted += takeId
        store.takes.update { it - takeId }
        return Result.success(Unit)
    }

    private fun mutate(takeId: String, f: (Take) -> Take): Result<Unit> {
        val t = store.takes.value[takeId] ?: return domainFailure(DataError.NotFound("Take", takeId))
        store.takes.update { it + (takeId to f(t).let { u -> if (u.meta.deleted) u else u.copy(meta = u.meta.touched(store.clock.nowMillis())) }) }
        return Result.success(Unit)
    }
}
