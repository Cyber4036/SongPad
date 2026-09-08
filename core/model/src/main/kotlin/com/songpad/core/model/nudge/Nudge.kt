package com.songpad.core.model.nudge

import com.songpad.core.model.sync.SyncMeta

/** In-app history row (§6.1). A nudge survives its target's deletion as a read item. */
data class Nudge(
    val id: String,
    val targetSongId: String?,
    val targetTakeId: String?,
    val kind: NudgeKind,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val meta: SyncMeta,
)

enum class NudgeKind { EMPTY_SECTION, UNASSIGNED_TAKE, STALE_INVESTED_SONG, USER_REMINDER }

/** A deterministic rule outcome from [NudgeSelector]: at most one per worker run (§11.2). */
data class NudgeCandidate(
    val kind: NudgeKind,
    val targetSongId: String?,
    val targetTakeId: String?,
    val songName: String?,
)

/** Inputs the worker gathers from the repositories before running the rules. Pure data so the rules are JVM-tested. */
data class NudgeSnapshot(
    val nowMillis: Long,
    val ongoingSongsWithEmptySection: List<SongRef>,
    val unassignedTakes: List<TakeRef>,
    val investedSongs: List<InvestedSong>,
    val recentlyNudgedSongIds: Set<String>,
    val lastNudgeAtMillis: Long?,
) {
    data class SongRef(val id: String, val name: String)
    data class TakeRef(val id: String, val createdAt: Long)
    data class InvestedSong(val id: String, val name: String, val updatedAt: Long, val hasLyrics: Boolean, val hasTakes: Boolean)
}
