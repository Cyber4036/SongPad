package com.songpad.core.model.nudge

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NudgeSelectorTest {
    private val now = 1_725_000_000_000L
    private val hour = 60L * 60 * 1000
    private val selector = NudgeSelector()

    private fun snapshot(
        empty: List<NudgeSnapshot.SongRef> = emptyList(),
        unassigned: List<NudgeSnapshot.TakeRef> = emptyList(),
        invested: List<NudgeSnapshot.InvestedSong> = emptyList(),
        recent: Set<String> = emptySet(),
    ) = NudgeSnapshot(now, empty, unassigned, invested, recent, null)

    @Test
    fun `nothing eligible yields no nudge`() {
        assertThat(selector.select(snapshot())).isNull()
    }

    @Test
    fun `empty section on an ongoing song wins over everything`() {
        val s = snapshot(
            empty = listOf(NudgeSnapshot.SongRef("s1", "Midnight Riff")),
            unassigned = listOf(NudgeSnapshot.TakeRef("t1", now - 72 * hour)),
        )
        assertThat(selector.select(s)?.kind).isEqualTo(NudgeKind.EMPTY_SECTION)
        assertThat(selector.select(s)?.targetSongId).isEqualTo("s1")
    }

    @Test
    fun `unassigned take must be older than 48 hours`() {
        assertThat(selector.select(snapshot(unassigned = listOf(NudgeSnapshot.TakeRef("t1", now - 47 * hour))))).isNull()
        val c = selector.select(snapshot(unassigned = listOf(NudgeSnapshot.TakeRef("t1", now - 49 * hour))))
        assertThat(c?.kind).isEqualTo(NudgeKind.UNASSIGNED_TAKE)
        assertThat(c?.targetTakeId).isEqualTo("t1")
    }

    @Test
    fun `stale invested song requires both lyrics and takes and seven days`() {
        val eightDays = now - 8L * 24 * hour
        val onlyLyrics = NudgeSnapshot.InvestedSong("a", "A", eightDays, hasLyrics = true, hasTakes = false)
        val both = NudgeSnapshot.InvestedSong("b", "B", eightDays, hasLyrics = true, hasTakes = true)
        val fresh = NudgeSnapshot.InvestedSong("c", "C", now - 2L * 24 * hour, hasLyrics = true, hasTakes = true)
        assertThat(selector.select(snapshot(invested = listOf(onlyLyrics, fresh)))).isNull()
        assertThat(selector.select(snapshot(invested = listOf(onlyLyrics, both, fresh)))?.targetSongId).isEqualTo("b")
    }

    @Test
    fun `recently nudged songs are skipped`() {
        val s = snapshot(empty = listOf(NudgeSnapshot.SongRef("s1", "A"), NudgeSnapshot.SongRef("s2", "B")), recent = setOf("s1"))
        assertThat(selector.select(s)?.targetSongId).isEqualTo("s2")
    }

    @Test
    fun `cadence gate honours interval plus jitter`() {
        assertThat(selector.isCadenceSatisfied(now, null, 0)).isTrue()
        assertThat(selector.isCadenceSatisfied(now, now - 23 * hour, 0)).isFalse()
        assertThat(selector.isCadenceSatisfied(now, now - 25 * hour, 0)).isTrue()
        assertThat(selector.isCadenceSatisfied(now, now - 25 * hour, 2 * hour)).isFalse()
    }
}
