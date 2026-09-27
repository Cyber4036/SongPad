package com.songpad.feature.capture

import com.songpad.core.model.audio.InterruptionReason
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.state.PermissionState
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.CaptureSession

/**
 * §17 "Recording" states, one sealed hierarchy. Every branch is a real screen state, not a toast:
 * Ready · Recording · Paused · Reviewing · Saving · Saved · Discarded · Interrupted · Failed ·
 * PermissionDenied · LowStorage.
 */
sealed interface CaptureState {
    val type: TakeType
    val songId: String?

    data class Ready(
        override val type: TakeType,
        override val songId: String?,
        val permission: PermissionState,
        val qualityMode: QualityMode,
    ) : CaptureState

    data class Recording(val session: CaptureSession, val elapsedMs: Long) : CaptureState {
        override val type get() = session.type
        override val songId get() = session.songId
    }

    /** Engine paused; the in-flight PCM is scrubbable (Phase 0 §6). */
    data class Paused(
        val session: CaptureSession,
        val elapsedMs: Long,
        val review: ReviewPlayback = ReviewPlayback(0, elapsedMs, false),
        val confirmDiscard: Boolean = false,
    ) : CaptureState {
        override val type get() = session.type
        override val songId get() = session.songId
    }

    /** Paused, and the in-flight PCM is playing back through the single PlaybackController (§7.6). */
    data class Reviewing(
        val session: CaptureSession,
        val elapsedMs: Long,
        val review: ReviewPlayback,
        val confirmDiscard: Boolean = false,
    ) : CaptureState {
        override val type get() = session.type
        override val songId get() = session.songId
    }

    data class Saving(val session: CaptureSession) : CaptureState {
        override val type get() = session.type
        override val songId get() = session.songId
    }

    data class Saved(val take: Take) : CaptureState {
        override val type get() = take.type
        override val songId get() = take.songId
    }

    data class Discarded(override val type: TakeType, override val songId: String?) : CaptureState

    /** §7.4: never auto-resumed. The user chooses Resume, Save or Discard. */
    data class Interrupted(
        val session: CaptureSession,
        val reason: InterruptionReason,
        val elapsedMs: Long,
        val confirmDiscard: Boolean = false,
    ) : CaptureState {
        override val type get() = session.type
        override val songId get() = session.songId
    }

    data class Failed(val error: DomainError, override val type: TakeType, override val songId: String?) : CaptureState

    data class PermissionDenied(override val type: TakeType, override val songId: String?, val permanent: Boolean) : CaptureState

    data class LowStorage(override val type: TakeType, override val songId: String?, val freeBytes: Long) : CaptureState
}

data class ReviewPlayback(val positionMs: Long, val durationMs: Long, val isPlaying: Boolean) {
    val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

sealed interface CaptureIntent {
    data class SelectType(val type: TakeType) : CaptureIntent
    data class PermissionResult(val state: PermissionState) : CaptureIntent
    data object Start : CaptureIntent
    data object Pause : CaptureIntent
    data object Resume : CaptureIntent
    data object PlayReview : CaptureIntent
    data object PauseReview : CaptureIntent
    data class Scrub(val fraction: Float) : CaptureIntent
    data class Save(val takeName: String, val newSongName: String?) : CaptureIntent
    data object RequestDiscard : CaptureIntent
    data object ConfirmDiscard : CaptureIntent
    data object CancelDiscard : CaptureIntent
    /** Leaves a terminal state (Saved / Discarded / Failed / LowStorage) for Ready. */
    data object Reset : CaptureIntent

    /** Piano keyboard events (§8). The keyboard itself is gated on S-2/S-3; the intents exist so the wiring is testable. */
    data class KeyDown(val note: Int, val velocity: Int, val eventTimeNanos: Long) : CaptureIntent
    data class KeyUp(val note: Int, val eventTimeNanos: Long) : CaptureIntent

    /** Internal: engine state changes are folded through the same serial reducer. */
    data class EngineChanged(val state: com.songpad.core.model.audio.RecordingState) : CaptureIntent
    data class PlaybackChanged(val state: com.songpad.core.model.audio.PlaybackState) : CaptureIntent
}

sealed interface CaptureEffect {
    data class NavigateToSong(val songId: String) : CaptureEffect
    data object RequestMicrophonePermission : CaptureEffect
    data object OpenAppSettings : CaptureEffect
    data class Message(val text: String) : CaptureEffect
}

