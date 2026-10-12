package com.songpad.feature.capture

import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.testing.FakeAudioRecorderEngine
import com.songpad.core.data.testing.FakeMidiCaptureEngine
import com.songpad.core.data.testing.FakePianoSamplerEngine
import com.songpad.core.data.testing.FakePlaybackController
import com.songpad.core.data.testing.FakeSettingsRepository
import com.songpad.core.data.testing.FakeSongRepository
import com.songpad.core.data.testing.FakeStorageProbe
import com.songpad.core.data.testing.FakeTakeRepository
import com.songpad.core.data.testing.FakeTranscodeScheduler
import com.songpad.core.data.testing.InMemoryStore
import com.songpad.core.model.audio.InterruptionReason
import com.songpad.core.model.audio.PlaybackSource
import com.songpad.core.model.audio.RecordingState
import com.songpad.core.model.error.CaptureError
import com.songpad.core.model.state.PermissionState
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.DiscardRecordingUseCase
import com.songpad.core.model.usecase.FinalizeTakeUseCase
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
    private val piano = FakePianoSamplerEngine()
    private val settings = FakeSettingsRepository()
    private val takes = FakeTakeRepository(store)
    private val songs = FakeSongRepository(store)
    private val transcodes = FakeTranscodeScheduler()
    private val storage = FakeStorageProbe()
    private val bound = mutableListOf<String>()

    private lateinit var vm: CaptureViewModel
    private val effects = mutableListOf<CaptureEffect>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = CaptureViewModel.create(
            engine, playback, midi, MidiTakeBinder { bound += it }, piano, settings,
            StartRecordingUseCase(engine, settings, store.ids, store.clock, storage),
            FinalizeTakeUseCase(engine, midi, takes, settings, transcodes),
            DiscardRecordingUseCase(engine, midi),
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
    fun `selecting voice plus piano changes the ready state's type`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.SelectType(TakeType.VOICE_PIANO)); dispatcher.scheduler.advanceUntilIdle()
        assertThat((vm.state.value as CaptureState.Ready).type).isEqualTo(TakeType.VOICE_PIANO)
    }

    @Test
    fun `piano sampler arms only while a piano take is recording, and forwards key events`() = runVm {
        assertThat(piano.loadAsyncCallCount).isEqualTo(1)  // §8.1: loading starts at screen-open, not the toggle

        granted()
        vm.dispatch(CaptureIntent.SelectType(TakeType.VOICE_PIANO)); dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        // The engine.state collector can bounce the derived state through Recording more than once
        // during Start (e.g. an intermediate EngineChanged reduce); arm() is idempotent on the native
        // side (PianoOutputStream::open()/start() no-op if already open/started), so >=1 is the real
        // contract, not exactly 1.
        assertThat(piano.armCallCount).isAtLeast(1)
        assertThat(piano.lastArmedMode).isEqualTo(TakeType.VOICE_PIANO)

        vm.dispatch(CaptureIntent.KeyDown(note = 64, velocity = 90, eventTimeNanos = 1L)); dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(CaptureIntent.KeyUp(note = 64, eventTimeNanos = 2L)); dispatcher.scheduler.advanceUntilIdle()
        assertThat(piano.noteOnCalls).containsExactly(64 to 90)
        assertThat(piano.noteOffCalls).containsExactly(64)
        assertThat(midi.events).hasSize(2)  // both the sampler and the MIDI sidecar hear the same key event

        vm.dispatch(CaptureIntent.Pause); dispatcher.scheduler.advanceUntilIdle()
        assertThat(piano.disarmCallCount).isAtLeast(1)  // same idempotent-call caveat as arm() above
    }

    @Test
    fun `a voice-only take never touches the piano sampler`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(CaptureIntent.KeyDown(note = 60, velocity = 100, eventTimeNanos = 1L)); dispatcher.scheduler.advanceUntilIdle()
        assertThat(piano.armCallCount).isEqualTo(0)
        assertThat(piano.noteOnCalls).isEmpty()
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

    @Test
    fun `failed resume stays paused and emits user message`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(CaptureIntent.Pause); dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(CaptureState.Paused::class.java)

        engine.failResumeWith = CaptureError.StreamOpenFailed("resume failed")
        vm.dispatch(CaptureIntent.Resume); dispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.state.value).isInstanceOf(CaptureState.Paused::class.java)
        val msg = effects.filterIsInstance<CaptureEffect.Message>().single()
        assertThat(msg.text).contains("Couldn't resume")
    }

    @Test
    fun `reset discards non-idle engine`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(CaptureIntent.Pause); dispatcher.scheduler.advanceUntilIdle()
        assertThat(engine.state.value).isInstanceOf(RecordingState.Paused::class.java)

        vm.dispatch(CaptureIntent.Reset); dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(CaptureState.Ready::class.java)
        assertThat(engine.state.value).isEqualTo(RecordingState.Idle)
    }

    @Test
    fun `saving a loose take emits Finished effect`() = runVm {
        granted()
        vm.dispatch(CaptureIntent.Start); dispatcher.scheduler.advanceUntilIdle()
        engine.simulateFrames(48_000 * 2); dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(CaptureIntent.Save(takeName = "Loose riff", newSongName = null)); dispatcher.scheduler.advanceUntilIdle()
        val saved = vm.state.value as CaptureState.Saved
        assertThat(saved.take.songId).isNull()
        assertThat(effects).contains(CaptureEffect.Finished)
    }
}
