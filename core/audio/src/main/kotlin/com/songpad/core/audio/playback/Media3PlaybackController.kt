package com.songpad.core.audio.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.wav.WavExtractor
import com.songpad.core.model.audio.PlaybackController
import com.songpad.core.model.audio.PlaybackSource
import com.songpad.core.model.audio.PlaybackState
import com.songpad.core.model.error.PlaybackError
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.TakeFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §7.6: ONE Media3/ExoPlayer instance app-wide, owned here, so the review player and the takes ribbon never
 * fight over audio focus. Plays `.m4a`, `.flac`, committed `.pcm` and the in-flight `.pcm.tmp` (raw PCM is
 * wrapped in a synthesized WAV header by [RawPcmDataSource], since the format is a fixed constant §7.3).
 */
@Singleton
class Media3PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: TakeFileStore,
) : PlaybackController {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var current: PlaybackSource? = null
    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            val src = current
            if (src != null && p.isPlaying) {
                _state.value = PlaybackState.Playing(src, p.currentPosition, p.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
                main.postDelayed(this, 100)
            }
        }
    }

    private val player: ExoPlayer by lazy {
        val extractors = ExtractorsFactory { arrayOf<Extractor>(WavExtractor(), *DefaultExtractorsFactory().createExtractors()) }
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(RawPcmDataSource.Factory(DefaultDataSource.Factory(context), files), extractors))
            .build().also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val src = current ?: return
                        when (playbackState) {
                            Player.STATE_BUFFERING -> _state.value = PlaybackState.Loading(src)
                            Player.STATE_READY -> if (p.playWhenReady) { _state.value = PlaybackState.Playing(src, p.currentPosition, p.duration.coerceAtLeast(0)); main.post(ticker) } else _state.value = PlaybackState.Paused(src, p.currentPosition, p.duration.coerceAtLeast(0))
                            Player.STATE_ENDED -> _state.value = PlaybackState.Ended(src, p.duration.coerceAtLeast(0))
                            Player.STATE_IDLE -> Unit
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val src = current ?: return
                        if (isPlaying) main.post(ticker) else if (p.playbackState == Player.STATE_READY) _state.value = PlaybackState.Paused(src, p.currentPosition, p.duration.coerceAtLeast(0))
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _state.value = PlaybackState.Error(current, PlaybackError.Decoder(error.errorCodeName))
                    }
                })
            }
    }

    override fun play(source: PlaybackSource) {
        main.post {
            val file = files.file(source.relativePath)
            if (!file.exists()) { _state.value = PlaybackState.Error(source, PlaybackError.SourceUnavailable("file missing")); return@post }
            if (current?.relativePath == source.relativePath && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
                player.play(); return@post
            }
            current = source
            _state.value = PlaybackState.Loading(source)
            val uri = when (source) {
                is PlaybackSource.CommittedTake -> if (source.mimeType == AudioFormat.MIME_RAW_PCM) RawPcmDataSource.uriFor(source.relativePath) else Uri.fromFile(file)
                is PlaybackSource.RawPcm, is PlaybackSource.InFlightPcm -> RawPcmDataSource.uriFor(source.relativePath)
            }
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun pause() { main.post { player.pause() } }

    override fun seekTo(positionMs: Long) {
        main.post {
            player.seekTo(positionMs)
            val src = current ?: return@post
            _state.value = if (player.isPlaying) PlaybackState.Playing(src, positionMs, player.duration.coerceAtLeast(0)) else PlaybackState.Paused(src, positionMs, player.duration.coerceAtLeast(0))
        }
    }

    override fun stop() {
        main.post { player.stop(); player.clearMediaItems(); current = null; _state.value = PlaybackState.Idle }
    }

    /** Release file handles for a take before deletion (§9.2 step 1). */
    fun releaseIfPlaying(takeId: String) { if (current?.takeId == takeId) stop() }

}
