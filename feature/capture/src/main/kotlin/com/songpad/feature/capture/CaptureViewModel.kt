package com.songpad.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.model.audio.AudioRecorderEngine
import com.songpad.core.model.audio.PlaybackController
import com.songpad.core.model.audio.PlaybackSource
import com.songpad.core.model.audio.PlaybackState
import com.songpad.core.model.audio.RecordingState
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.error.CaptureError
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.error.domainError
import com.songpad.core.model.logging.Logger
import com.songpad.core.model.midi.MidiCaptureEngine
import com.songpad.core.model.mvi.MviStore
import com.songpad.core.model.piano.PianoSamplerEngine
import com.songpad.core.model.state.PermissionState
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.CaptureSession
import com.songpad.core.model.usecase.DiscardRecordingUseCase
import com.songpad.core.model.usecase.FinalizeTakeUseCase
import com.songpad.core.model.usecase.StartRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §16 vertical slice owner. All transitions run through one serial [MviStore] reducer so the engine's
 * legal state machine is enforced in one place (§7.2 "MVI only for the capture screen").
 *
 * The constructor is fake-friendly: every dependency is a §15 contract or a pure use case, so the JVM test
 * drives the whole Start → Pause → Review → Save journey without Android.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val engine: AudioRecorderEngine,
    private val playback: PlaybackController,
    private val midi: MidiCaptureEngine,
    private val midiBinder: MidiTakeBinder,
    private val piano: PianoSamplerEngine,
    private val settings: SettingsRepository,
    private val startRecording: StartRecordingUseCase,
    private val finalizeTake: FinalizeTakeUseCase,
    private val discardRecording: DiscardRecordingUseCase,
    private val logger: Logger,
) : ViewModel() {

    private var initialSongId: String? = null

    private val store = MviStore<CaptureState, CaptureIntent, CaptureEffect>(
        initialState = CaptureState.Ready(TakeType.VOICE, null, PermissionState.Unknown, QualityMode.STANDARD),
        scope = viewModelScope,
        handler = { intent -> handle(intent) },
    )

    val state: StateFlow<CaptureState> = store.state
    val effects: SharedFlow<CaptureEffect> = store.effects

    /** 10 Hz normalized amplitude for the live waveform; passed straight through (§7.2). */
    val amplitudes: SharedFlow<Float> = engine.amplitudes

    private val _reviewPeaks = MutableStateFlow(FloatArray(0))
    /** Amplitudes accumulated during this recording, for the paused-review scrubber. */
    val reviewPeaks: StateFlow<FloatArray> = _reviewPeaks.asStateFlow()

    private val peakBuffer = ArrayList<Float>(6_000)

    init {
        // §8.1: sample loading begins when the screen opens, never gated behind the Piano toggle.
        piano.loadAsync()
        // The Oboe output stream itself only needs to be armed while the mic is actually capturing
        // (§8.1 / ADR-0035), so drive it off every path that enters/leaves Recording.
        viewModelScope.launch {
            state.map { (it as? CaptureState.Recording)?.type }.distinctUntilChanged().collect { type ->
                if (type != null && type != TakeType.VOICE) {
                    piano.arm(type)
                } else {
                    piano.disarm()
                }
            }
        }
        viewModelScope.launch { engine.state.collect { store.dispatch(CaptureIntent.EngineChanged(it)) } }
        viewModelScope.launch { playback.state.collect { store.dispatch(CaptureIntent.PlaybackChanged(it)) } }
        viewModelScope.launch {
            engine.amplitudes.collect { a ->
                if (store.current is CaptureState.Recording) {
                    peakBuffer += a
                    if (peakBuffer.size % 10 == 0) _reviewPeaks.value = peakBuffer.toFloatArray()
                }
            }
        }
        viewModelScope.launch {
            settings.qualityMode.collect { mode ->
                store.reduce { s -> if (s is CaptureState.Ready) s.copy(qualityMode = mode) else s }
            }
        }
    }

    /** Called once by the screen with its route argument. */
    fun bind(songId: String?) {
        if (initialSongId == songId) return
        initialSongId = songId
        store.reduce { s -> if (s is CaptureState.Ready) s.copy(songId = songId) else s }
    }

    fun dispatch(intent: CaptureIntent) = store.dispatch(intent)

    override fun onCleared() = piano.disarm()

    private suspend fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.handle(intent: CaptureIntent) {
        when (intent) {
            is CaptureIntent.SelectType -> reduce { s -> if (s is CaptureState.Ready) s.copy(type = intent.type) else s }
            is CaptureIntent.PermissionResult -> onPermission(intent.state)
            CaptureIntent.Start -> start()
            CaptureIntent.Pause -> pause()
            CaptureIntent.Resume -> resume()
            CaptureIntent.PlayReview -> playReview()
            CaptureIntent.PauseReview -> playback.pause()
            is CaptureIntent.Scrub -> scrub(intent.fraction)
            is CaptureIntent.Save -> save(intent.takeName, intent.newSongName)
            CaptureIntent.RequestDiscard -> requestDiscard()
            CaptureIntent.ConfirmDiscard -> discard()
            CaptureIntent.CancelDiscard -> reduce { s -> s.withConfirmDiscard(false) }
            CaptureIntent.Reset -> reset()
            is CaptureIntent.KeyDown -> if (current.isCapturingPiano()) {
                midi.onKeyDown(intent.note, intent.velocity, intent.eventTimeNanos)
                piano.noteOn(intent.note, intent.velocity)
            }
            is CaptureIntent.KeyUp -> if (current.isCapturingPiano()) {
                midi.onKeyUp(intent.note, intent.eventTimeNanos)
                piano.noteOff(intent.note)
            }
            is CaptureIntent.EngineChanged -> onEngine(intent.state)
            is CaptureIntent.PlaybackChanged -> onPlayback(intent.state)
        }
    }

    // ── transitions ──

    private fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.onPermission(permission: PermissionState) {
        reduce { st ->
            when (permission) {
                PermissionState.Granted -> when (st) {
                    is CaptureState.Ready -> st.copy(permission = permission)
                    is CaptureState.PermissionDenied -> CaptureState.Ready(st.type, st.songId, permission, st.qualityModeOrDefault())
                    else -> st
                }
                PermissionState.PermanentlyDenied -> if (st.isIdle()) CaptureState.PermissionDenied(st.type, st.songId, permanent = true) else st
                PermissionState.Denied, PermissionState.NeedsRationale -> if (st.isIdle()) CaptureState.PermissionDenied(st.type, st.songId, permanent = false) else st
                PermissionState.Unknown -> if (st is CaptureState.Ready) st.copy(permission = permission) else st
            }
        }
    }

    private suspend fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.start() {
        val s = current as? CaptureState.Ready ?: return
        if (s.permission != PermissionState.Granted) {
            emit(CaptureEffect.RequestMicrophonePermission)
            return
        }
        peakBuffer.clear(); _reviewPeaks.value = FloatArray(0)
        startRecording(s.type, s.songId)
            .onSuccess { session ->
                if (session.type != TakeType.VOICE) midiBinder.beginTake(session.takeId)
                reduce { CaptureState.Recording(session, 0) }
            }
            .onFailure { t -> reduce { failedState(t.domainError, s.type, s.songId) } }
    }

    private suspend fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.pause() {
        val s = current as? CaptureState.Recording ?: return
        engine.pause()
            .onSuccess { reduce { CaptureState.Paused(s.session, s.elapsedMs) } }
            .onFailure { t -> logger.error(TAG, t.domainError) }
    }

    private suspend fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.resume() {
        val session = when (val s = current) {
            is CaptureState.Paused -> s.session
            is CaptureState.Reviewing -> s.session
            is CaptureState.Interrupted -> s.session
            else -> return
        }
        if (playback.state.value.sourceOrNull?.takeId == session.takeId) playback.stop()
        engine.resume()
            .onSuccess { reduce { CaptureState.Recording(session, elapsedMsOf(current)) } }
            .onFailure { t ->
                logger.error(TAG, t.domainError)
                emit(CaptureEffect.Message("Couldn't resume. Try again or save what you have."))
            }
    }

    private fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.playReview() {
        val (session, review) = when (val s = current) {
            is CaptureState.Paused -> s.session to s.review
            is CaptureState.Reviewing -> s.session to s.review
            else -> return
        }
        val playing = playback.state.value
        if (playing.sourceOrNull?.takeId == session.takeId && playing is PlaybackState.Paused) {
            playback.play(playing.source); return
        }
        val bytes = AudioFormat.bytesForFrames(engine.framesWritten.value)
        playback.play(PlaybackSource.InFlightPcm(session.takeId, session.pcmTmpPath, bytes))
        if (review.positionMs > 0) playback.seekTo(review.positionMs)
    }

    private fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.scrub(fraction: Float) {
        val s = current
        val (session, review) = when (s) {
            is CaptureState.Paused -> s.session to s.review
            is CaptureState.Reviewing -> s.session to s.review
            else -> return
        }
        val position = (fraction.coerceIn(0f, 1f) * review.durationMs).toLong()
        if (playback.state.value.sourceOrNull?.takeId == session.takeId) playback.seekTo(position)
        reduce { st ->
            when (st) {
                is CaptureState.Paused -> st.copy(review = st.review.copy(positionMs = position))
                is CaptureState.Reviewing -> st.copy(review = st.review.copy(positionMs = position))
                else -> st
            }
        }
    }

    private suspend fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.save(takeName: String, newSongName: String?) {
        val session = when (val s = current) {
            is CaptureState.Recording -> s.session
            is CaptureState.Paused -> s.session
            is CaptureState.Reviewing -> s.session
            is CaptureState.Interrupted -> s.session
            else -> return
        }
        if (playback.state.value.sourceOrNull?.takeId == session.takeId) playback.stop()
        reduce { CaptureState.Saving(session) }
        finalizeTake(session, takeName, newSongName)
            .onSuccess { take ->
                reduce { CaptureState.Saved(take) }
                val targetSongId = take.songId
                if (targetSongId != null) {
                    emit(CaptureEffect.NavigateToSong(targetSongId))
                } else {
                    emit(CaptureEffect.Finished)
                }
            }
            .onFailure { t ->
                val error = t.domainError
                logger.error(TAG, error)
                reduce { CaptureState.Failed(error, session.type, session.songId) }
            }
    }

    /** Discard is confirmed only when loss is possible (Phase 0 §5/§21): a zero-length recording just goes. */
    private suspend fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.requestDiscard() {
        val s = current
        if (s is CaptureState.Recording) {
            if (s.elapsedMs <= 0) { discard(); return }
            engine.pause()
            reduce { CaptureState.Paused(s.session, s.elapsedMs, confirmDiscard = true) }
            return
        }
        reduce { st -> st.withConfirmDiscard(true) }
    }

    private suspend fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.discard() {
        val s = current
        val (type, songId) = s.type to s.songId
        if (s is CaptureState.Ready || s is CaptureState.Discarded) return
        playback.stop()
        discardRecording().onFailure { t -> logger.error(TAG, t.domainError) }
        peakBuffer.clear(); _reviewPeaks.value = FloatArray(0)
        reduce { CaptureState.Discarded(type, songId) }
    }

    private suspend fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.reset() {
        val s = current
        val mode = settings.qualityMode.first()
        if (engine.state.value !is RecordingState.Idle) discardRecording()
        reduce { CaptureState.Ready(s.type, s.songId ?: initialSongId, PermissionState.Granted.takeIf { s !is CaptureState.PermissionDenied } ?: PermissionState.Unknown, mode) }
    }

    private fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.onEngine(engineState: RecordingState) {
        when (engineState) {
            is RecordingState.Recording -> reduce { s -> if (s is CaptureState.Recording) s.copy(elapsedMs = engineState.elapsedMs) else s }
            is RecordingState.Interrupted -> reduce { s ->
                when (s) {
                    is CaptureState.Recording -> CaptureState.Interrupted(s.session, engineState.reason, s.elapsedMs)
                    is CaptureState.Paused -> CaptureState.Interrupted(s.session, engineState.reason, s.elapsedMs)
                    is CaptureState.Reviewing -> CaptureState.Interrupted(s.session, engineState.reason, s.elapsedMs)
                    else -> s
                }
            }
            is RecordingState.Failed -> reduce { s ->
                when (s) {
                    is CaptureState.Recording, is CaptureState.Paused, is CaptureState.Reviewing, is CaptureState.Interrupted ->
                        CaptureState.Failed(engineState.error, s.type, s.songId)
                    else -> s
                }
            }
            RecordingState.Idle, is RecordingState.Paused -> Unit
        }
    }

    private fun MviStore<CaptureState, CaptureIntent, CaptureEffect>.onPlayback(p: PlaybackState) {
        val takeId = when (val s = current) {
            is CaptureState.Paused -> s.session.takeId
            is CaptureState.Reviewing -> s.session.takeId
            else -> return
        }
        val source = p.sourceOrNull as? PlaybackSource.InFlightPcm ?: return
        if (source.takeId != takeId) return
        reduce { s ->
            val (session, elapsed, review, confirm) = when (s) {
                is CaptureState.Paused -> Quad(s.session, s.elapsedMs, s.review, s.confirmDiscard)
                is CaptureState.Reviewing -> Quad(s.session, s.elapsedMs, s.review, s.confirmDiscard)
                else -> return@reduce s
            }
            when (p) {
                is PlaybackState.Playing -> CaptureState.Reviewing(session, elapsed, ReviewPlayback(p.positionMs, p.durationMs.takeIf { it > 0 } ?: elapsed, true), confirm)
                is PlaybackState.Loading -> CaptureState.Reviewing(session, elapsed, review.copy(isPlaying = true), confirm)
                is PlaybackState.Paused -> CaptureState.Paused(session, elapsed, ReviewPlayback(p.positionMs, p.durationMs.takeIf { it > 0 } ?: elapsed, false), confirm)
                is PlaybackState.Ended -> CaptureState.Paused(session, elapsed, ReviewPlayback(0, p.durationMs.takeIf { it > 0 } ?: elapsed, false), confirm)
                is PlaybackState.Error -> { logger.error(TAG, p.error); CaptureState.Paused(session, elapsed, review.copy(isPlaying = false), confirm) }
                PlaybackState.Idle -> CaptureState.Paused(session, elapsed, review.copy(isPlaying = false), confirm)
            }
        }
    }

    // ── helpers ──

    private fun failedState(error: DomainError, type: TakeType, songId: String?): CaptureState = when (error) {
        is CaptureError.LowStorage -> CaptureState.LowStorage(type, songId, error.freeBytes)
        CaptureError.PermissionDenied -> CaptureState.PermissionDenied(type, songId, permanent = false)
        else -> { logger.error(TAG, error); CaptureState.Failed(error, type, songId) }
    }

    private fun elapsedMsOf(s: CaptureState): Long = when (s) {
        is CaptureState.Recording -> s.elapsedMs
        is CaptureState.Paused -> s.elapsedMs
        is CaptureState.Reviewing -> s.elapsedMs
        is CaptureState.Interrupted -> s.elapsedMs
        else -> 0
    }

    private fun CaptureState.withConfirmDiscard(value: Boolean): CaptureState = when (this) {
        is CaptureState.Paused -> copy(confirmDiscard = value)
        is CaptureState.Reviewing -> copy(confirmDiscard = value)
        is CaptureState.Interrupted -> copy(confirmDiscard = value)
        else -> this
    }

    private fun CaptureState.isIdle(): Boolean = this is CaptureState.Ready || this is CaptureState.PermissionDenied || this is CaptureState.Discarded

    private fun CaptureState.qualityModeOrDefault(): QualityMode = (this as? CaptureState.Ready)?.qualityMode ?: QualityMode.STANDARD

    private fun CaptureState.isCapturingPiano(): Boolean = this is CaptureState.Recording && type != TakeType.VOICE

    private data class Quad(val session: CaptureSession, val elapsed: Long, val review: ReviewPlayback, val confirm: Boolean)

    companion object {
        private const val TAG = "CaptureViewModel"

        /** Test seam: builds a store-backed instance without Hilt. */
        fun create(
            engine: AudioRecorderEngine, playback: PlaybackController, midi: MidiCaptureEngine, midiBinder: MidiTakeBinder,
            piano: PianoSamplerEngine, settings: SettingsRepository, start: StartRecordingUseCase, finalize: FinalizeTakeUseCase,
            discard: DiscardRecordingUseCase, logger: Logger = Logger.Noop,
        ) = CaptureViewModel(engine, playback, midi, midiBinder, piano, settings, start, finalize, discard, logger)
    }
}
