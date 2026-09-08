package com.songpad.core.model.audio

import com.songpad.core.model.error.PlaybackError
import kotlinx.coroutines.flow.StateFlow

// ── §15 FROZEN CONTRACT — implemented in :core:audio (one Media3 instance app-wide, §7.6). ──

interface PlaybackController {
    val state: StateFlow<PlaybackState>
    fun play(source: PlaybackSource)
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
}

/** A committed take, or the in-flight `.pcm.tmp` during review-before-save (§7.6). */
sealed interface PlaybackSource {
    val takeId: String
    val relativePath: String

    data class CommittedTake(override val takeId: String, override val relativePath: String, val mimeType: String) : PlaybackSource
    data class RawPcm(override val takeId: String, override val relativePath: String) : PlaybackSource
    data class InFlightPcm(override val takeId: String, override val relativePath: String, val byteLength: Long) : PlaybackSource
}

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Loading(val source: PlaybackSource) : PlaybackState
    data class Playing(val source: PlaybackSource, val positionMs: Long, val durationMs: Long) : PlaybackState
    data class Paused(val source: PlaybackSource, val positionMs: Long, val durationMs: Long) : PlaybackState
    data class Ended(val source: PlaybackSource, val durationMs: Long) : PlaybackState
    data class Error(val source: PlaybackSource?, val error: PlaybackError) : PlaybackState

    val sourceOrNull: PlaybackSource?
        get() = when (this) {
            is Idle -> null
            is Loading -> source
            is Playing -> source
            is Paused -> source
            is Ended -> source
            is Error -> source
        }
}
