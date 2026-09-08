package com.songpad.feature.nudges

import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.testing.FakeNudgeRepository
import com.songpad.core.data.testing.FakeSettingsRepository
import com.songpad.core.data.testing.InMemoryStore
import com.songpad.core.model.nudge.Nudge
import com.songpad.core.model.nudge.NudgeCandidate
import com.songpad.core.model.nudge.NudgeKind
import com.songpad.core.model.nudge.NudgeSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random

class NudgeRunTest {
    private val store = InMemoryStore()
    private val nudges = FakeNudgeRepository(store)
    private val settings = FakeSettingsRepository()
    private val notified = mutableListOf<Nudge>()
    private val notifier = NudgeNotifier { notified += it; true }
    private val noJitter = Random(0).let { object : Random() { override fun nextBits(bitCount: Int) = 0; override fun nextLong(from: Long, until: Long) = 0L } }

    private fun run() = NudgeRun(nudges, settings, store.clock, random = noJitter)
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `disabled toggle - no row, no notification`() = runTest {
        settings.setNudgesEnabled(false)
        assertThat(run().execute(notifier)).isEqualTo(NudgeRun.Outcome.Disabled)
        assertThat(store.nudges.value).isEmpty()
    }

    @Test
    fun `empty section wins, row is written, copy is gentle, last-nudge time is stored`() = runTest {
        nudges.snapshot = NudgeSnapshot(
            nowMillis = store.clock.nowMillis(),
            ongoingSongsWithEmptySection = listOf(NudgeSnapshot.SongRef("s1", "Midnight Riff")),
            unassignedTakes = listOf(NudgeSnapshot.TakeRef("t1", store.clock.nowMillis() - 3 * day)),
            investedSongs = emptyList(), recentlyNudgedSongIds = emptySet(), lastNudgeAtMillis = null,
        )
        val outcome = run().execute(notifier) as NudgeRun.Outcome.Nudged
        assertThat(outcome.nudge.kind).isEqualTo(NudgeKind.EMPTY_SECTION)
        assertThat(outcome.nudge.title).contains("Midnight Riff")
        assertThat(outcome.nudge.body).doesNotContainMatch("(?i)you haven't|overdue|streak")
        assertThat(notified).hasSize(1)
        assertThat(store.nudges.value.values.single().id).isEqualTo(outcome.nudge.id)
        assertThat(settings.lastNudgeAtMillis.first()).isEqualTo(store.clock.nowMillis())
    }

    @Test
    fun `cadence - a second run within 24h is TooSoon, after 24h it can nudge again`() = runTest {
        nudges.snapshot = nudges.snapshot.copy(unassignedTakes = listOf(NudgeSnapshot.TakeRef("t1", store.clock.nowMillis() - 3 * day)))
        assertThat(run().execute(notifier)).isInstanceOf(NudgeRun.Outcome.Nudged::class.java)
        store.clock.advance(day / 2)
        assertThat(run().execute(notifier)).isEqualTo(NudgeRun.Outcome.TooSoon)
        store.clock.advance(day / 2)
        nudges.snapshot = nudges.snapshot.copy(nowMillis = store.clock.nowMillis())
        assertThat(run().execute(notifier)).isInstanceOf(NudgeRun.Outcome.Nudged::class.java)
    }

    @Test
    fun `history row is written even when the notification cannot be shown`() = runTest {
        nudges.snapshot = nudges.snapshot.copy(unassignedTakes = listOf(NudgeSnapshot.TakeRef("t1", store.clock.nowMillis() - 3 * day)))
        val outcome = run().execute { false } as NudgeRun.Outcome.Nudged
        assertThat(outcome.notified).isFalse()
        assertThat(store.nudges.value).hasSize(1)
    }

    @Test
    fun `nothing to say leaves no trace`() = runTest {
        assertThat(run().execute(notifier)).isEqualTo(NudgeRun.Outcome.NothingToSay)
        assertThat(store.nudges.value).isEmpty()
        assertThat(settings.lastNudgeAtMillis.first()).isNull()
    }

    @Test
    fun `copy exists for every kind`() {
        NudgeKind.entries.forEach { kind ->
            val t = NudgeCopy.forCandidate(NudgeCandidate(kind, "s", null, "Song"))
            assertThat(t.title).isNotEmpty(); assertThat(t.body).isNotEmpty()
        }
    }
}
