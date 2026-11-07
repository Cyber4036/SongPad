package com.songpad.feature.song.di

import com.songpad.core.audio.capture.PeakReducer
import com.songpad.core.audio.encode.PcmDecoder
import com.songpad.core.audio.transcode.FlacHeader
import com.songpad.core.model.data.LyricsRepository
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.export.ExportSource
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.usecase.AssignLyricsToSongUseCase
import com.songpad.core.model.usecase.AssignTakeToSongUseCase
import com.songpad.core.model.usecase.CreateSongFromLyricsUseCase
import com.songpad.core.model.usecase.CreateSongFromTakeUseCase
import com.songpad.core.model.usecase.RenameTakeUseCase
import com.songpad.feature.song.detail.PeaksLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object SongModule {
    @Provides fun createSong(songs: SongRepository) = CreateSongFromTakeUseCase(songs)
    @Provides fun assignTake(takes: TakeRepository, songs: SongRepository) = AssignTakeToSongUseCase(takes, songs)
    @Provides fun createSongFromLyrics(songs: SongRepository, lyrics: LyricsRepository) = CreateSongFromLyricsUseCase(songs, lyrics)
    @Provides fun assignLyrics(lyrics: LyricsRepository, songs: SongRepository) = AssignLyricsToSongUseCase(lyrics, songs)
    @Provides fun renameTake(takes: TakeRepository) = RenameTakeUseCase(takes)

    @Provides
    fun peaksLoader(files: TakeFileStore, pcmDecoder: PcmDecoder): PeaksLoader = PeaksLoader { path ->
        val peaksFile = files.file(path)
        val existing = PeakReducer.readPeaksFile(peaksFile)
        if (existing.isNotEmpty() && existing.any { it > 0.0005f }) {
            return@PeaksLoader existing
        }
        val takeDir = peaksFile.parentFile ?: return@PeaksLoader existing
        val takeId = takeDir.name
        val candidates = listOf(
            TakePaths.flac(takeId) to AudioFormat.MIME_FLAC,
            TakePaths.m4a(takeId) to AudioFormat.MIME_M4A,
            TakePaths.wav(takeId) to AudioFormat.MIME_WAV,
            TakePaths.mp3(takeId) to AudioFormat.MIME_MP3,
            TakePaths.pcm(takeId) to AudioFormat.MIME_RAW_PCM,
        )
        val (audioRel, mime) = candidates.firstOrNull { (rel, _) ->
            val f = files.file(rel)
            f.exists() && f.length() > 0L
        } ?: return@PeaksLoader existing

        val audioFile = files.file(audioRel)
        if (mime == AudioFormat.MIME_FLAC) {
            FlacHeader.ensureValidFlacFile(audioFile)
        }
        val reducer = PeakReducer(onAmplitude = {})
        val rebuilt = runCatching {
            val source = ExportSource(
                takeId = takeId,
                relativePath = audioRel,
                mimeType = mime,
                isLossless = mime == AudioFormat.MIME_FLAC || mime == AudioFormat.MIME_WAV || mime == AudioFormat.MIME_RAW_PCM,
                durationMs = 0L,
                byteSize = audioFile.length(),
            )
            pcmDecoder.decode(source, onPcm = { shorts, count -> reducer.consume(shorts, count) })
            val snapshot = reducer.peaksSnapshot()
            if (snapshot.isNotEmpty() && snapshot.any { it > 0.0005f }) {
                reducer.writePeaksFile(peaksFile)
                snapshot
            } else {
                existing
            }
        }.getOrDefault(existing)
        rebuilt
    }
}
