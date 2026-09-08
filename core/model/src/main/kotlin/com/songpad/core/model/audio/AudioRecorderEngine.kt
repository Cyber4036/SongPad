package com.songpad.core.model.audio

import com.songpad.core.model.error.CaptureError
import com.songpad.core.model.take.CapturedAudio
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// ── §15 FROZEN CONTRACT — implemented in :core:audio. Signature changes need the lead's sign-off. ──

interface AudioRecorderEngine {
    /** 10 Hz, normalized 0f..1f, DROP_OLDEST so a slow UI can never back-pressure audio (§7.2). */
    val amplitudes: SharedFlow<Float>
    val state: StateFlow<RecordingState>
    /** Ground-truth timeline: how much audio exists on the ring/disk path (§8.3). */
    val framesWritten: StateFlow<Long>

    suspend fun start(config: CaptureConfig): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stopAndCommit(takeId: String): Result<CapturedAudio>
    suspend fun discard(): Result<Unit>

    /** Maps a UI-thread event time onto the audio frame timeline. See §8.3. */
    fun frameIndexFor(eventTimeNanos: Long): Long
}

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val elapsedMs: Long) : RecordingState
    data class Paused(val elapsedMs: Long) : RecordingState
    data class Interrupted(val reason: InterruptionReason) : RecordingState
    data class Failed(val error: CaptureError) : RecordingState
}

/** §7.4 interruption matrix. Never auto-resume after a transient loss. */
enum class InterruptionReason {
    FOCUS_LOSS_TRANSIENT,
    FOCUS_LOSS_PERMANENT,
    HEADPHONES_UNPLUGGED,
    BLUETOOTH_DISCONNECTED,
    LOW_STORAGE,
}

data class CaptureConfig(
    val takeId: String,
    val takeType: TakeType,
    val qualityMode: QualityMode,
    val songId: String?,
)
