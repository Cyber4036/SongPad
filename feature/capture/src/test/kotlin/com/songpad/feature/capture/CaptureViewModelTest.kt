package com.songpad.feature.capture

import com.google.common.truth.Truth.assertThat
import com.songpad.core.audio.recovery.AudioRecoveryManager
import com.songpad.core.data.testing.FakeAudioRecorderEngine
import com.songpad.core.data.testing.FakeMidiCaptureEngine
import com.songpad.core.data.testing.FakePlaybackController
import com.songpad.core.data.testing.FakeSettingsRepository
import com.songpad.core.data.testing.FakeSongRepository
import com.songpad.core.data.testing.FakeStorageProbe
import com.songpad.core.data.testing.FakeTakeRepository
import com.songpad.core.data.testing.FakeTranscodeScheduler
import com.songpad.core.data.testing.InMemoryStore
import com.songpad.core.model.audio.InterruptionReason
import com.songpad.core.model.audio.PlaybackSource
import com.songpad.core.model.error.CaptureError
import com.songpad.core.model.state.PermissionState
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.DiscardRecordingUseCase
import com.songpad.core.model.usecase.FinalizeTakeUseCase
import com.songpad.core.model.usecase.RecoverTakeUseCase
import com.songpad.core.model.usecase.StartRecordingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryStore()
    private val engine = FakeAudioRecorderEngine()
    private val playback = FakePlaybackController()
    private val midi = FakeMidiCaptureEngine()
    private val settings = FakeSettingsRepository()
    private val takes = FakeTakeRepository(store)
    private val songs = FakeSongRepository(store)
    private val transcodes = FakeTranscodeScheduler()
    private val storage = FakeStorageProbe()
    private val bound = mutableListOf<String>()
    private val recovery = AudioRecoveryManager(takes)

    private lateinit var vm: CaptureViewModel
    private val effects = mutableListOf<CaptureEffect>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = CaptureViewModel.create(
            engine, playback, midi, MidiTakeBinder { bound += it }, settings,
            StartRecordingUseCase(engine, settings, store.ids, store.clock, storage),
            FinalizeTakeUseCase(engine, midi, takes, settings, transcodes),
            DiscardRecordingUseCase(engine, midi),
            RecoverTakeUseCase(takes, transcodes),
            recovery,
        )
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun runVm(block: suspend () -> Unit) = runTest(dispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        block()
        job.cancel()
    }

    private suspend fun granted() {
        vm.dispatch(CaptureIntent.PermissionResult(PermissionState.Granted)); dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `vertical slice - start, record, pause, review, save into new song, navigate`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(CaptureState.Recording::class.java)
        assertThat(bound).isEmpty() // voice take: MIDI log not opened

        engine.simulateFrames(48_000L * 15); dispatcher.scheduler.advanceUntilIdle()
        assertThat((vm.state.value as CaptureState.Recording).elapsedMs).isEqualTo(15_000)

        vm.dispatch(CaptureIntent.Pause); dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(CaptureState.Paused::class.java)

        vm.dispatch(CaptureIntent.PlayReview); dispatcher.scheduler.advanceUntilIdle()
        val src = playback.played.single()
        assertThat(src).isInstanceOf(PlaybackSource.InFlightPcm::class.java)
        assertThat((src as PlaybackSource.InFlightPcm).byteLength).isEqualTo(48_000L * 15 * 2)
        assertThat(vm.state.value).isInstanceOf(CaptureState.Reviewing::class.java)

        vm.dispatch(CaptureIntent.Scrub(0.5f)); dispatcher.scheduler.advanceUntilIdle()
        assertThat((vm.state.value as CaptureState.Reviewing).review.positionMs).isEqualTo(7_500)

        vm.dispatch(CaptureIntent.Save(takeName = "", newSongName = "Midnight Riff")); dispatcher.scheduler.advanceUntilIdle()
        val saved = vm.state.value as CaptureState.Saved
        assertThat(saved.take.status).isEqualTo(TakeStatus.TRANSCODING)
        assertThat(store.songs.value.values.single().name).isEqualTo("Midnight Riff")
        assertThat(effects.filterIsInstance<CaptureEffect.NavigateToSong>().single().songId).isEqualTo(saved.take.songId)
        assertThat(transcodes.enqueued).containsExactly(saved.take.id)
        assertThat(playback.state.value).isEqualTo(com.songpad.core.model.audio.PlaybackState.Idle)
    }

    @Test
    fun `start without permission asks for it instead of touching the engine`() = runVm {
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        assertThat(effects).containsExactly(CaptureEffect.RequestMicrophonePermission)
        assertThat(engine.calls).isEmpty()
    }

    @Test
    fun `permanent denial is a real state with a settings action`() = runVm {
        vm.dispatch(CaptureIntent.PermissionResult(PermissionState.PermanentlyDenied)); dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(CaptureState.PermissionDenied(TakeType.VOICE, null, permanent = true))
    }

    @Test
    fun `low storage refuses to start`() = runVm {
        granted()
        storage.freeBytes = 10L * 1024 * 1024
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(CaptureState.LowStorage::class.java)
        assertThat(engine.calls).isEmpty()
        vm.dispatch(CaptureIntent.Reset); dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(CaptureState.Ready::class.java)
    }

    @Test
    fun `interruption never auto-resumes - user chooses`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        engine.simulateFrames(48_000); dispatcher.scheduler.advanceUntilIdle()
        engine.interrupt(InterruptionReason.FOCUS_LOSS_TRANSIENT); dispatcher.scheduler.advanceUntilIdle()
        val s = vm.state.value as CaptureState.Interrupted
        assertThat(s.reason).isEqualTo(InterruptionReason.FOCUS_LOSS_TRANSIENT)
        assertThat(engine.calls).doesNotContain("resume")
        vm.dispatch(CaptureIntent.Resume); dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(CaptureState.Recording::class.java)
    }

    @Test
    fun `discard while recording asks for confirmation, then tears down`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        engine.simulateFrames(48_000); dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(CaptureIntent.RequestDiscard); dispatcher.scheduler.advanceUntilIdle()
        assertThat((vm.state.value as CaptureState.Paused).confirmDiscard).isTrue()
        vm.dispatch(CaptureIntent.ConfirmDiscard); dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(CaptureState.Discarded(TakeType.VOICE, null))
        assertThat(engine.calls).contains("discard")
        assertThat(midi.resetCount).isEqualTo(1)
    }

    @Test
    fun `nothing captured is a failed state, not a crash`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(CaptureIntent.Save("", "Empty")); dispatcher.scheduler.advanceUntilIdle()
        val s = vm.state.value as CaptureState.Failed
        assertThat(s.error).isEqualTo(CaptureError.NothingCaptured)
        assertThat(store.songs.value).isEmpty()
    }

    @Test
    fun `piano types are gated until the spikes return`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.SelectType(TakeType.VOICE_PIANO)); dispatcher.scheduler.advanceUntilIdle()
        assertThat((vm.state.value as CaptureState.Ready).type).isEqualTo(if (PianoCaptureGate.ENABLED) TakeType.VOICE_PIANO else TakeType.VOICE)
    }

    @Test
    fun `saving into an existing song keeps the take there`() = runVm {
        val song = songs.create("Existing").getOrThrow()
        vm.bind(song.id)
        granted()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        engine.simulateFrames(48_000 * 3); dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(CaptureIntent.Save("Bridge idea", null)); dispatcher.scheduler.advanceUntilIdle()
        val saved = vm.state.value as CaptureState.Saved
        assertThat(saved.take.songId).isEqualTo(song.id)
        assertThat(saved.take.name).isEqualTo("Bridge idea")
    }
}
