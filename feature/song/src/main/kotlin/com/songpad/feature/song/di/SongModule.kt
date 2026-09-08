package com.songpad.feature.song.di

import com.songpad.core.audio.capture.PeakReducer
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.model.usecase.AssignTakeToSongUseCase
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
    @Provides fun renameTake(takes: TakeRepository) = RenameTakeUseCase(takes)
    @Provides fun peaksLoader(files: TakeFileStore): PeaksLoader = PeaksLoader { path -> PeakReducer.readPeaksFile(files.file(path)) }
}
