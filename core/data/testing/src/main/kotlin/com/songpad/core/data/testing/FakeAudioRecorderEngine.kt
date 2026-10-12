package com.songpad.core.data.testing

import com.songpad.core.model.audio.AudioRecorderEngine
import com.songpad.core.model.audio.CaptureConfig
import com.songpad.core.model.audio.InterruptionReason
import com.songpad.core.model.audio.RecordingState
import com.songpad.core.model.error.CaptureError
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.CapturedAudio
import com.songpad.core.model.take.TakePaths
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Scripted recorder. Tests drive [emitAmplitude], [simulateFrames] and [interrupt]; `start` can be made
 * to fail with [failStartWith].
 */
class FakeAudioRecorderEngine : AudioRecorderEngine {
    override val amplitudes = MutableSharedFlow<Float>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val framesWritten = MutableStateFlow(0L)

    var failStartWith: DomainError? = null
    var config: CaptureConfig? = null
    val calls = mutableListOf<String>()
    var framesPerNano: Double = AudioFormat.SAMPLE_RATE / 1e9
    var anchorNanos: Long = 0

    override suspend fun start(config: CaptureConfig): Result<Unit> {
        calls += "start"
        failStartWith?.let { return domainFailure(it) }
        if (state.value !is RecordingState.Idle) return domainFailure(CaptureError.AlreadyRecording)
        this.config = config
        framesWritten.value = 0
        state.value = RecordingState.Recording(0)
        return Result.success(Unit)
    }

    override suspend fun pause(): Result<Unit> {
        calls += "pause"
        val s = state.value as? RecordingState.Recording ?: return domainFailure(CaptureError.NotRecording)
        state.value = RecordingState.Paused(s.elapsedMs)
        return Result.success(Unit)
    }

    var failResumeWith: DomainError? = null

    override suspend fun resume(): Result<Unit> {
        calls += "resume"
        failResumeWith?.let { return domainFailure(it) }
        val elapsed = when (val s = state.value) {
            is RecordingState.Paused -> s.elapsedMs
            is RecordingState.Interrupted -> AudioFormat.framesToMs(framesWritten.value)
            else -> return domainFailure(CaptureError.NotRecording)
        }
        state.value = RecordingState.Recording(elapsed)
        return Result.success(Unit)
    }

    override suspend fun stopAndCommit(takeId: String): Result<CapturedAudio> {
        calls += "stopAndCommit"
        if (state.value is RecordingState.Idle) return domainFailure(CaptureError.NotRecording)
        val frames = framesWritten.value
        state.value = RecordingState.Idle
        return Result.success(
            CapturedAudio(
                takeId = takeId,
                relativePath = TakePaths.pcm(takeId),
                frames = frames,
                byteSize = AudioFormat.bytesForFrames(frames),
                peaksPath = TakePaths.peaks(takeId),
            ),
        )
    }

    override suspend fun discard(): Result<Unit> {
        calls += "discard"
        state.value = RecordingState.Idle
        framesWritten.value = 0
        return Result.success(Unit)
    }

    override fun frameIndexFor(eventTimeNanos: Long): Long =
        Math.round((eventTimeNanos - anchorNanos) * framesPerNano).coerceAtLeast(0)

    // ── test controls ──
    fun emitAmplitude(value: Float) { amplitudes.tryEmit(value) }

    fun simulateFrames(frames: Long) {
        framesWritten.value += frames
        (state.value as? RecordingState.Recording)?.let { state.value = RecordingState.Recording(AudioFormat.framesToMs(framesWritten.value)) }
    }

    fun interrupt(reason: InterruptionReason) { state.value = RecordingState.Interrupted(reason) }
    fun fail(error: CaptureError) { state.value = RecordingState.Failed(error) }
}
