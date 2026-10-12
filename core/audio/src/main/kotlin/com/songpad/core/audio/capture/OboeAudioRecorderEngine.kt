package com.songpad.core.audio.capture

import android.content.Context
import android.util.Log
import com.songpad.core.audio.focus.AudioFocusGuard
import com.songpad.core.audio.service.CaptureForegroundService
import com.songpad.core.model.audio.AudioRecorderEngine
import com.songpad.core.model.audio.CaptureConfig
import com.songpad.core.model.audio.InterruptionReason
import com.songpad.core.model.audio.RecordingState
import com.songpad.core.model.error.CaptureError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.midi.ClockMapping
import com.songpad.core.model.state.CaptureSessionFlag
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.CapturedAudio
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.model.take.TakePaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §15 `AudioRecorderEngine` on Oboe (§7.1). Owns: the native stream, the writer thread, the PCM file, the
 * peak reducer, the §8.3 clock mapping, the audio-focus guard and the FGS lifetime. Interruptions (§7.4)
 * move the state to `Interrupted` and never auto-resume.
 *
 * Every public suspend call serializes on [mutex] so the state machine has one writer. The 10 Hz amplitude
 * flow uses DROP_OLDEST so a slow UI can never back-pressure audio (§7.2 rule 5).
 */
@Singleton
class OboeAudioRecorderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: TakeFileStore,
    private val focus: AudioFocusGuard,
) : AudioRecorderEngine {

    private val _amplitudes = MutableSharedFlow<Float>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val amplitudes: SharedFlow<Float> = _amplitudes.asSharedFlow()
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()
    private val _framesWritten = MutableStateFlow(0L)
    override val framesWritten: StateFlow<Long> = _framesWritten.asStateFlow()

    val clockMapping = ClockMapping()
    /** Registered by :core:midi so its `events.log` syncs on the same ~1 s cadence (§8.5). */
    @Volatile var onSyncCadence: () -> Unit = {}

    private val mutex = Mutex()
    private var native: NativeCapture? = null
    private var writer: PcmWriter? = null
    private var reducer: PeakReducer? = null
    private var thread: CaptureWriterThread? = null
    private var config: CaptureConfig? = null
    private val ticker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "songpad-capture-ticker").apply { isDaemon = true } }
    private var tick: java.util.concurrent.ScheduledFuture<*>? = null

    override fun frameIndexFor(eventTimeNanos: Long): Long = clockMapping.frameIndexFor(eventTimeNanos)

    override suspend fun start(config: CaptureConfig): Result<Unit> = withContext(Dispatchers.IO) { startImpl(config) }

    private suspend fun startImpl(config: CaptureConfig): Result<Unit> = mutex.withLock {
        if (_state.value !is RecordingState.Idle) return domainFailure(CaptureError.AlreadyRecording)
        if (!focus.request(onLoss = ::onFocusLoss)) return domainFailure(CaptureError.AudioFocusNotGranted)

        val nc = NativeCapture()
        val rc = nc.open()
        if (rc != 0) { nc.close(); focus.abandon(); return domainFailure(CaptureError.StreamOpenFailed("oboe result $rc")) }

        val pcm = PcmWriter(files.file(TakePaths.pcmTmp(config.takeId)))
        try { pcm.open(append = false) } catch (e: java.io.IOException) { nc.close(); focus.abandon(); return domainFailure(CaptureError.Io(e.message ?: "open failed")) }
        val red = PeakReducer(onAmplitude = { _amplitudes.tryEmit(it) })
        clockMapping.reset()
        clockMapping.addAnchor(0L, System.nanoTime())
        val t = CaptureWriterThread(nc, pcm, red, clockMapping, onSyncCadence = { onSyncCadence() }, onError = ::onWriterError)

        this.native = nc; this.writer = pcm; this.reducer = red; this.thread = t; this.config = config
        CaptureSessionFlag.set(true)
        CaptureForegroundService.start(context, config.takeId)
        focus.registerRouteListeners(onBecomingNoisy = { onInterrupted(InterruptionReason.HEADPHONES_UNPLUGGED) }, onBluetoothLost = { onInterrupted(InterruptionReason.BLUETOOTH_DISCONNECTED) })

        t.start()
        val src = nc.start()
        if (src != 0) { teardown(deleteFile = true); return domainFailure(CaptureError.StreamOpenFailed("start result $src")) }
        _state.value = RecordingState.Recording(0)
        tick = ticker.scheduleAtFixedRate({ onTick() }, 100, 100, TimeUnit.MILLISECONDS)
        Log.i(TAG, "capture started ${config.takeId} ${nc.streamInfo()}")
        Result.success(Unit)
    }

    override suspend fun pause(): Result<Unit> = withContext(Dispatchers.IO) { pauseImpl() }

    private suspend fun pauseImpl(): Result<Unit> = mutex.withLock {
        val s = _state.value as? RecordingState.Recording ?: return domainFailure(CaptureError.NotRecording)
        native?.pause(); thread?.setPaused(true)
        _state.value = RecordingState.Paused(s.elapsedMs)
        Result.success(Unit)
    }

    override suspend fun resume(): Result<Unit> = withContext(Dispatchers.IO) { resumeImpl() }

    private suspend fun resumeImpl(): Result<Unit> = mutex.withLock {
        when (_state.value) {
            is RecordingState.Paused, is RecordingState.Interrupted -> Unit
            else -> return domainFailure(CaptureError.NotRecording)
        }
        if (!focus.request(onLoss = ::onFocusLoss)) return domainFailure(CaptureError.AudioFocusNotGranted)
        val nc = native ?: return domainFailure(CaptureError.NotRecording)
        if (nc.errorOccurred()) {
            // Route change closed the stream (§7.4 Bluetooth row): reopen on the new route; buffers are recomputed by Oboe.
            nc.closeStream(); val rc = nc.open(); if (rc != 0) return domainFailure(CaptureError.StreamOpenFailed("reopen result $rc"))
        }
        thread?.setPaused(false)
        var rc = nc.start()
        if (rc != 0) {
            nc.closeStream()
            rc = nc.open()
            if (rc == 0) rc = nc.start()
        }
        if (rc != 0) {
            thread?.setPaused(true)
            return domainFailure(CaptureError.StreamOpenFailed("resume result $rc"))
        }
        _state.value = RecordingState.Recording(elapsedMs())
        Result.success(Unit)
    }

    /** §7.3: stop → drain → flush → fsync → rename `.tmp` → `.pcm`. The presence of `audio.pcm` is the commit marker. */
    override suspend fun stopAndCommit(takeId: String): Result<CapturedAudio> = withContext(Dispatchers.IO) { stopAndCommitImpl(takeId) }

    private suspend fun stopAndCommitImpl(takeId: String): Result<CapturedAudio> = mutex.withLock {
        if (_state.value is RecordingState.Idle) return domainFailure(CaptureError.NothingCaptured)
        val cfg = config ?: return domainFailure(CaptureError.NotRecording)
        native?.pause()
        thread?.let { it.shutdown(); it.join(5_000) }
        val w = writer ?: return domainFailure(CaptureError.NotRecording)
        val frames: Long
        try {
            w.close()
            frames = w.samplesWritten / AudioFormat.CHANNELS
            reducer?.writePeaksFile(files.file(TakePaths.peaks(cfg.takeId)))
            if (!files.commitPcm(cfg.takeId)) return domainFailure(CaptureError.Io("rename .pcm.tmp → .pcm failed"))
        } catch (e: java.io.IOException) {
            return domainFailure(CaptureError.Io(e.message ?: "commit failed"))
        } finally {
            teardown(deleteFile = false)
        }
        Result.success(CapturedAudio(takeId, TakePaths.pcm(cfg.takeId), frames, AudioFormat.bytesForFrames(frames), TakePaths.peaks(cfg.takeId)))
    }

    override suspend fun discard(): Result<Unit> = withContext(Dispatchers.IO) { discardImpl() }

    private suspend fun discardImpl(): Result<Unit> = mutex.withLock {
        teardown(deleteFile = true)
        Result.success(Unit)
    }

    /** Called when the writer flushed a 1 s block, and by [onTick] to keep elapsed time moving. */
    private fun onTick() {
        val t = thread ?: return
        _framesWritten.value = t.framesWritten.get()
        (_state.value as? RecordingState.Recording)?.let { _state.value = RecordingState.Recording(elapsedMs()) }
        native?.let { if (it.errorOccurred() && _state.value is RecordingState.Recording) onInterrupted(InterruptionReason.BLUETOOTH_DISCONNECTED) }
    }

    private fun elapsedMs(): Long = AudioFormat.framesToMs(thread?.framesWritten?.get() ?: 0L)

    private fun onFocusLoss(permanent: Boolean) {
        // §7.4: transient loss → pause and stay paused; permanent loss → also pause; FinalizeTakeUseCase decides commit.
        onInterrupted(if (permanent) InterruptionReason.FOCUS_LOSS_PERMANENT else InterruptionReason.FOCUS_LOSS_TRANSIENT)
    }

    private fun onInterrupted(reason: InterruptionReason) {
        if (_state.value !is RecordingState.Recording) return
        native?.pause(); thread?.setPaused(true)
        _state.value = RecordingState.Interrupted(reason)
        Log.i(TAG, "interrupted: $reason")
    }

    private fun onWriterError(t: Throwable) {
        Log.e(TAG, "writer thread failed", t)
        native?.pause()
        val free = files.root.usableSpace
        _state.value = if (free < AudioFormat.CRITICAL_STORAGE_BYTES) RecordingState.Interrupted(InterruptionReason.LOW_STORAGE)
        else RecordingState.Failed(CaptureError.Io(t.message ?: t::class.java.simpleName))
    }

    private fun teardown(deleteFile: Boolean) {
        tick?.cancel(false); tick = null
        thread?.let { it.shutdown(); runCatching { it.join(2_000) } }
        runCatching { writer?.close() }
        native?.let { it.closeStream(); it.close() }
        focus.unregisterRouteListeners(); focus.abandon()
        CaptureForegroundService.stop(context)
        CaptureSessionFlag.set(false)
        config?.let { if (deleteFile) files.deleteTakeFiles(it.takeId) }
        native = null; writer = null; reducer = null; thread = null; config = null
        _framesWritten.value = 0
        _state.value = RecordingState.Idle
    }

    companion object { private const val TAG = "OboeRecorder" }
}
