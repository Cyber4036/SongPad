package com.songpad.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.songpad.core.data.db.SongPadDatabase
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.data.repo.ExportSourceResolverImpl
import com.songpad.core.data.repo.LyricsRepositoryImpl
import com.songpad.core.data.repo.NoteRepositoryImpl
import com.songpad.core.data.repo.NudgeRepositoryImpl
import com.songpad.core.data.repo.SearchEngineImpl
import com.songpad.core.data.repo.SongRepositoryImpl
import com.songpad.core.data.repo.TakeRepositoryImpl
import com.songpad.core.data.settings.SettingsRepositoryImpl
import com.songpad.core.model.data.ExportSourceResolver
import com.songpad.core.model.data.LyricsRepository
import com.songpad.core.model.data.NoteRepository
import com.songpad.core.model.data.NudgeRepository
import com.songpad.core.model.data.SearchEngine
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.id.UuidV7
import com.songpad.core.model.time.Clock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): SongPadDatabase = SongPadDatabase.build(context)
    @Provides @Singleton fun fileStore(@ApplicationContext context: Context): TakeFileStore = TakeFileStore(context.filesDir)
    @Provides @Singleton fun clock(): Clock = Clock.SYSTEM
    @Provides @Singleton fun idGenerator(): IdGenerator { val gen = UuidV7(); return IdGenerator { gen.next() } }
    @Provides @Singleton fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("songpad_settings") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds abstract fun takeRepository(impl: TakeRepositoryImpl): TakeRepository
    @Binds abstract fun songRepository(impl: SongRepositoryImpl): SongRepository
    @Binds abstract fun lyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository
    @Binds abstract fun noteRepository(impl: NoteRepositoryImpl): NoteRepository
    @Binds abstract fun nudgeRepository(impl: NudgeRepositoryImpl): NudgeRepository
    @Binds abstract fun searchEngine(impl: SearchEngineImpl): SearchEngine
    @Binds abstract fun exportSourceResolver(impl: ExportSourceResolverImpl): ExportSourceResolver
    @Binds abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
