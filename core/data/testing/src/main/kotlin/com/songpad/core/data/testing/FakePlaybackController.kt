package com.songpad.core.data.testing

import com.songpad.core.model.audio.PlaybackController
import com.songpad.core.model.audio.PlaybackSource
import com.songpad.core.model.audio.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlaybackController(var durationMs: Long = 15_000) : PlaybackController {
    override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val played = mutableListOf<PlaybackSource>()

    override fun play(source: PlaybackSource) {
        played += source
        state.value = PlaybackState.Playing(source, 0, durationMs)
    }

    override fun pause() {
        (state.value as? PlaybackState.Playing)?.let { state.value = PlaybackState.Paused(it.source, it.positionMs, it.durationMs) }
    }

    override fun seekTo(positionMs: Long) {
        state.value = when (val s = state.value) {
            is PlaybackState.Playing -> s.copy(positionMs = positionMs)
            is PlaybackState.Paused -> s.copy(positionMs = positionMs)
            else -> s
        }
    }

    override fun stop() { state.value = PlaybackState.Idle }

    fun finish() {
        state.value.sourceOrNull?.let { state.value = PlaybackState.Ended(it, durationMs) }
    }
}
