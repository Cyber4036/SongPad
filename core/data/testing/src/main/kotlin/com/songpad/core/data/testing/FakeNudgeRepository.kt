package com.songpad.core.data.testing

import com.songpad.core.model.data.NudgeRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.nudge.Nudge
import com.songpad.core.model.nudge.NudgeCandidate
import com.songpad.core.model.nudge.NudgeSnapshot
import com.songpad.core.model.sync.SyncMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeNudgeRepository(val store: InMemoryStore = InMemoryStore()) : NudgeRepository {
    var snapshot: NudgeSnapshot = NudgeSnapshot(store.clock.nowMillis(), emptyList(), emptyList(), emptyList(), emptySet(), null)
    val missingTargets = mutableSetOf<String>()

    override fun observeHistory(): Flow<List<Nudge>> = store.nudges.map { m -> m.values.filter { !it.meta.deleted }.sortedByDescending { it.meta.createdAt } }

    override suspend fun record(candidate: NudgeCandidate, title: String, body: String): Result<Nudge> {
        val nudge = Nudge(store.ids.newId(), candidate.targetSongId, candidate.targetTakeId, candidate.kind, title, body, false, SyncMeta.create(store.clock.nowMillis()))
        store.nudges.update { it + (nudge.id to nudge) }
        return Result.success(nudge)
    }

    override suspend fun markRead(nudgeId: String): Result<Unit> {
        val n = store.nudges.value[nudgeId] ?: return domainFailure(DataError.NotFound("Nudge", nudgeId))
        store.nudges.update { it + (nudgeId to n.copy(isRead = true)) }
        return Result.success(Unit)
    }

    override suspend fun snapshot(): NudgeSnapshot = snapshot

    override suspend fun targetExists(songId: String?, takeId: String?): Boolean {
        if (songId != null) return songId !in missingTargets && store.songs.value[songId]?.meta?.deleted == false
        if (takeId != null) return takeId !in missingTargets && store.takes.value[takeId]?.meta?.deleted == false
        return false
    }
}
