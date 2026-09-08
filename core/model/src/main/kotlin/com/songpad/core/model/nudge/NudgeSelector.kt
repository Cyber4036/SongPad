package com.songpad.core.model.nudge

/**
 * §11.2 selection rules. Deterministic, testable, emits at most one candidate per run.
 *
 * Priority (highest first): an ongoing Song with an empty section → an unassigned Take older than 48 h →
 * a Song with real investment untouched for [staleDays]. A song nudged recently is skipped so the same
 * idea is not repeated (§11.4 conservative cadence). The jitter/cadence gate is [isCadenceSatisfied].
 */
class NudgeSelector(
    private val unassignedTakeAgeMillis: Long = 48L * 60 * 60 * 1000,
    private val staleDays: Int = 7,
    private val minIntervalMillis: Long = 24L * 60 * 60 * 1000,
) {
    fun select(snapshot: NudgeSnapshot): NudgeCandidate? {
        snapshot.ongoingSongsWithEmptySection
            .firstOrNull { it.id !in snapshot.recentlyNudgedSongIds }
            ?.let { return NudgeCandidate(NudgeKind.EMPTY_SECTION, it.id, null, it.name) }

        snapshot.unassignedTakes
            .filter { snapshot.nowMillis - it.createdAt >= unassignedTakeAgeMillis }
            .maxByOrNull { it.createdAt }
            ?.let { return NudgeCandidate(NudgeKind.UNASSIGNED_TAKE, null, it.id, null) }

        val staleCutoff = snapshot.nowMillis - staleDays.toLong() * 24 * 60 * 60 * 1000
        snapshot.investedSongs
            .filter { it.hasLyrics && it.hasTakes && it.updatedAt < staleCutoff && it.id !in snapshot.recentlyNudgedSongIds }
            .minByOrNull { it.updatedAt }
            ?.let { return NudgeCandidate(NudgeKind.STALE_INVESTED_SONG, it.id, null, it.name) }

        return null
    }

    /** True when enough time has passed since the last nudge, including ±jitter so users are not nudged at one clock position. */
    fun isCadenceSatisfied(nowMillis: Long, lastNudgeAtMillis: Long?, jitterMillis: Long): Boolean {
        if (lastNudgeAtMillis == null) return true
        return nowMillis - lastNudgeAtMillis >= minIntervalMillis + jitterMillis
    }
}
