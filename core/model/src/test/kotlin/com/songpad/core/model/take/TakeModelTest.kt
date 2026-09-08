package com.songpad.core.model.take

import com.google.common.truth.Truth.assertThat
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.song.SongStatusTransitions
import com.songpad.core.model.state.StorageState
import com.songpad.core.model.usecase.DurationFormat
import org.junit.Test

class TakeModelTest {
    @Test
    fun `audio format constants match the spec`() {
        assertThat(AudioFormat.BYTES_PER_MS).isEqualTo(96)
        assertThat(AudioFormat.durationMsForBytes(96L * 1000 * 161)).isEqualTo(161_000)
        assertThat(AudioFormat.framesToMs(48_000 * 15)).isEqualTo(15_000)
    }

    @Test
    fun `paths derive from the take id and never store absolute paths`() {
        assertThat(TakePaths.pcmTmp("abc")).isEqualTo("takes/abc/audio.pcm.tmp")
        assertThat(TakePaths.stored("abc", QualityMode.LOSSLESS)).isEqualTo("takes/abc/audio.flac")
        assertThat(TakePaths.takeIdOf("takes/abc/audio.m4a")).isEqualTo("abc")
        assertThat(TakePaths.takeIdOf("notes/n/image.jpg")).isNull()
    }

    @Test
    fun `orphan noise threshold is one second`() {
        val short = OrphanedTake("t", "p", 96L * 900, false, false, null)
        val ok = OrphanedTake("t", "p", 96L * 1500, false, false, null)
        assertThat(short.isNoise).isTrue()
        assertThat(ok.isNoise).isFalse()
    }

    @Test
    fun `song status transitions are reversible with reverse labels`() {
        assertThat(SongStatusTransitions.toggleFinished(SongStatus.ONGOING)).isEqualTo(SongStatus.FINISHED)
        assertThat(SongStatusTransitions.toggleFinished(SongStatus.FINISHED)).isEqualTo(SongStatus.ONGOING)
        assertThat(SongStatusTransitions.archiveActionLabel(SongStatus.ARCHIVED)).isEqualTo("Unarchive")
        assertThat(SongStatusTransitions.matches(SongStatus.ARCHIVED, false, SongFilter.ALL)).isFalse()
        assertThat(SongStatusTransitions.matches(SongStatus.ARCHIVED, true, SongFilter.FAVORITES)).isTrue()
    }

    @Test
    fun `storage state thresholds`() {
        assertThat(StorageState.of(1L shl 30, AudioFormat.LOW_STORAGE_BYTES, AudioFormat.CRITICAL_STORAGE_BYTES)).isInstanceOf(StorageState.Normal::class.java)
        assertThat(StorageState.of(50L shl 20, AudioFormat.LOW_STORAGE_BYTES, AudioFormat.CRITICAL_STORAGE_BYTES)).isInstanceOf(StorageState.Low::class.java)
        assertThat(StorageState.of(5L shl 20, AudioFormat.LOW_STORAGE_BYTES, AudioFormat.CRITICAL_STORAGE_BYTES)).isInstanceOf(StorageState.Critical::class.java)
    }

    @Test
    fun `duration format`() {
        assertThat(DurationFormat.mmss(161_000)).isEqualTo("2:41")
        assertThat(DurationFormat.mmss(3_723_000)).isEqualTo("1:02:03")
        assertThat(DurationFormat.mmss(0)).isEqualTo("0:00")
    }
}
