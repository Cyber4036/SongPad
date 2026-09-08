package com.songpad.core.data.testing

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.songpad.core.model.error.CaptureError
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainErrorOrNull
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.AssignTakeToSongUseCase
import com.songpad.core.model.usecase.CreateSongFromTakeUseCase
import com.songpad.core.model.usecase.DiscardRecordingUseCase
import com.songpad.core.model.usecase.FinalizeTakeUseCase
import com.songpad.core.model.usecase.StartRecordingUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UseCaseTest {
    private val store = InMemoryStore()
    private val engine = FakeAudioRecorderEngine()
    private val midi = FakeMidiCaptureEngine()
    private val settings = FakeSettingsRepository()
    private val takes = FakeTakeRepository(store)
    private val songs = FakeSongRepository(store)
    private val transcodes = FakeTranscodeScheduler()
    private val storage = FakeStorageProbe()
    private val start = StartRecordingUseCase(engine, settings, store.ids, store.clock, storage)
    private val finalize = FinalizeTakeUseCase(engine, midi, takes, settings, transcodes)

    @Test
    fun `the vertical slice - record, save into a new song, take is observable and transcode enqueued`() = runTest {
        val session = start(TakeType.VOICE, songId = null).getOrThrow()
        assertThat(session.qualityMode).isEqualTo(QualityMode.STANDARD)
        engine.simulateFrames(48_000L * 15)

        val take = finalize(session, "", newSongName = "Midnight Riff").getOrThrow()
        assertThat(take.name).isEqualTo("Voice take")
        assertThat(take.status).isEqualTo(TakeStatus.TRANSCODING)
        assertThat(take.durationMs).isEqualTo(15_000)
        assertThat(take.songId).isNotNull()
        assertThat(store.songs.value.values.single().name).isEqualTo("Midnight Riff")
        assertThat(transcodes.enqueued).containsExactly(take.id)
        assertThat(settings.firstTakeSaved).isTrue()
        assertThat(midi.resetCount).isEqualTo(1) // voice take: MIDI engine reset, not committed

        takes.observeForSong(take.songId!!).test {
            assertThat(awaitItem().map { it.id }).containsExactly(take.id)
        }
    }

    @Test
    fun `quality mode is snapshotted at start - new recordings only`() = runTest {
        settings.setQualityMode(QualityMode.LOSSLESS)
        val session = start(TakeType.VOICE, null).getOrThrow()
        settings.setQualityMode(QualityMode.STANDARD)
        assertThat(session.qualityMode).isEqualTo(QualityMode.LOSSLESS)
    }

    @Test
    fun `low storage refuses to start with a typed error`() = runTest {
        storage.freeBytes = 50L * 1024 * 1024
        val r = start(TakeType.VOICE, null)
        assertThat(r.domainErrorOrNull()).isInstanceOf(CaptureError.LowStorage::class.java)
        assertThat(engine.calls).isEmpty()
    }

    @Test
    fun `engine failure propagates as a typed error`() = runTest {
        engine.failStartWith = CaptureError.AudioFocusNotGranted
        assertThat(start(TakeType.VOICE, null).domainErrorOrNull()).isEqualTo(CaptureError.AudioFocusNotGranted)
    }

    @Test
    fun `an empty capture is rejected and nothing is committed`() = runTest {
        val session = start(TakeType.VOICE, null).getOrThrow()
        val r = finalize(session, "x", null)
        assertThat(r.domainErrorOrNull()).isEqualTo(CaptureError.NothingCaptured)
        assertThat(store.takes.value).isEmpty()
        assertThat(transcodes.enqueued).isEmpty()
    }

    @Test
    fun `voice plus piano commits ONE take with a midi sidecar`() = runTest {
        val session = start(TakeType.VOICE_PIANO, songId = null).getOrThrow()
        engine.simulateFrames(48_000L * 5)
        midi.onKeyDown(60, 100, 500_000_000); midi.onKeyUp(60, 900_000_000)
        val take = finalize(session, "", null).getOrThrow()
        assertThat(take.type).isEqualTo(TakeType.VOICE_PIANO)
        assertThat(take.midi).isNotNull()
        assertThat(take.midi!!.eventCount).isEqualTo(2)
        assertThat(store.takes.value).hasSize(1)
        assertThat(take.isUnassigned).isTrue()
    }

    @Test
    fun `an unassigned take can be adopted by a new song or assigned to an existing one`() = runTest {
        val session = start(TakeType.VOICE, null).getOrThrow()
        engine.simulateFrames(48_000)
        val take = finalize(session, "Riff", null).getOrThrow()
        assertThat(take.isUnassigned).isTrue()

        val song = CreateSongFromTakeUseCase(songs)("  New Song ", take.id).getOrThrow()
        assertThat(takes.getTake(take.id)!!.songId).isEqualTo(song.id)

        val other = songs.create("Other").getOrThrow()
        AssignTakeToSongUseCase(takes, songs)(take.id, other.id).getOrThrow()
        assertThat(takes.getTake(take.id)!!.songId).isEqualTo(other.id)

        assertThat(CreateSongFromTakeUseCase(songs)("   ", null).domainErrorOrNull()).isInstanceOf(DataError.InvalidInput::class.java)
        assertThat(AssignTakeToSongUseCase(takes, songs)(take.id, "nope").domainErrorOrNull()).isInstanceOf(DataError.NotFound::class.java)
    }

    @Test
    fun `discard tears down engine and midi`() = runTest {
        start(TakeType.PIANO, null).getOrThrow()
        midi.onKeyDown(64, 90, 1)
        DiscardRecordingUseCase(engine, midi)().getOrThrow()
        assertThat(engine.calls.last()).isEqualTo("discard")
        assertThat(midi.events).isEmpty()
    }

    @Test
    fun `soft deleted takes disappear from reactive queries`() = runTest {
        val session = start(TakeType.VOICE, null).getOrThrow()
        engine.simulateFrames(48_000)
        val take = finalize(session, "Riff", null).getOrThrow()
        takes.softDelete(take.id).getOrThrow()
        takes.observeUnassigned().test { assertThat(awaitItem()).isEmpty() }
    }
}
