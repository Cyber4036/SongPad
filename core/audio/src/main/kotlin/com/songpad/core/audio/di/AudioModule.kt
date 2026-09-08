package com.songpad.core.audio.di

import android.content.Context
import androidx.work.WorkManager
import com.songpad.core.audio.capture.OboeAudioRecorderEngine
import com.songpad.core.audio.playback.Media3PlaybackController
import com.songpad.core.audio.storage.AndroidStorageProbe
import com.songpad.core.audio.transcode.WorkManagerTranscodeScheduler
import com.songpad.core.model.audio.AudioRecorderEngine
import com.songpad.core.model.audio.PlaybackController
import com.songpad.core.model.usecase.StorageProbe
import com.songpad.core.model.usecase.TranscodeScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioBindingsModule {
    @Binds @Singleton abstract fun recorder(impl: OboeAudioRecorderEngine): AudioRecorderEngine
    @Binds @Singleton abstract fun playback(impl: Media3PlaybackController): PlaybackController
    @Binds abstract fun transcodeScheduler(impl: WorkManagerTranscodeScheduler): TranscodeScheduler
    @Binds abstract fun storageProbe(impl: AndroidStorageProbe): StorageProbe
}

@Module
@InstallIn(SingletonComponent::class)
object AudioProvidersModule {
    @Provides @Singleton fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
