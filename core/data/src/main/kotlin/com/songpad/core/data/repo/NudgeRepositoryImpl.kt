package com.songpad.core.data.repo

import com.songpad.core.data.db.SongPadDatabase
import com.songpad.core.data.db.entity.NudgeEntity
import com.songpad.core.model.data.NudgeRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.DomainException
import com.songpad.core.model.error.runDomain
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.nudge.Nudge
import com.songpad.core.model.nudge.NudgeCandidate
import com.songpad.core.model.nudge.NudgeSnapshot
import com.songpad.core.model.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NudgeRepositoryImpl @Inject constructor(
    private val db: SongPadDatabase,
    private val ids: IdGenerator,
    private val clock: Clock,
) : NudgeRepository {
    private val dao get() = db.nudgeDao()

    override fun observeHistory(): Flow<List<Nudge>> = dao.observeHistory().map { l -> l.map { it.toDomain() } }

    /** §11.3: every dispatched nudge writes a row so in-app history is complete even with notifications off. */
    override suspend fun record(candidate: NudgeCandidate, title: String, body: String): Result<Nudge> = runDomain({ DataError.Io(it.message ?: "") }) {
        val n = NudgeEntity(ids.newId(), candidate.targetSongId, candidate.targetTakeId, candidate.kind.name, title, body, 0, newSync(clock.nowMillis()))
        dao.insert(n)
        n.toDomain()
    }

    override suspend fun markRead(nudgeId: String): Result<Unit> = runDomain({ DataError.Io(it.message ?: "") }) {
        val n = dao.get(nudgeId) ?: throw DomainException(DataError.NotFound("Nudge", nudgeId))
        dao.update(n.copy(isRead = 1, sync = n.sync.touched(clock.nowMillis())))
    }

    /** Gathers the §11.2 rule inputs. The rules themselves run in the pure `NudgeSelector`. */
    override suspend fun snapshot(): NudgeSnapshot {
        val now = clock.nowMillis()
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000
        val invested = db.songDao().ongoing().map { s ->
            NudgeSnapshot.InvestedSong(
                id = s.id, name = s.name, updatedAt = s.sync.updatedAt,
                hasLyrics = db.lyricsDao().nonEmptySectionCount(s.id) > 0,
                hasTakes = db.takeDao().countForSong(s.id) > 0,
            )
        }
        return NudgeSnapshot(
            nowMillis = now,
            ongoingSongsWithEmptySection = db.lyricsDao().ongoingSongsWithEmptySection().map { NudgeSnapshot.SongRef(it.id, it.name) },
            unassignedTakes = db.takeDao().unassigned().map { NudgeSnapshot.TakeRef(it.id, it.sync.createdAt) },
            investedSongs = invested,
            recentlyNudgedSongIds = dao.recentlyNudgedSongIds(weekAgo).toSet(),
            lastNudgeAtMillis = dao.lastNudgeAt(),
        )
    }

    override suspend fun targetExists(songId: String?, takeId: String?): Boolean = when {
        songId != null -> db.songDao().get(songId) != null
        takeId != null -> db.takeDao().get(takeId) != null
        else -> false
    }
}
