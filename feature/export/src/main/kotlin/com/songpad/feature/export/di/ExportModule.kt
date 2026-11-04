package com.songpad.feature.export.di

import com.songpad.core.model.export.SongPadImporter
import com.songpad.feature.export.bundle.SongPadImporterImpl
import com.songpad.feature.export.work.ExportJobRunner
import com.songpad.feature.export.work.WorkManagerExportRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    @Binds abstract fun runner(impl: WorkManagerExportRunner): ExportJobRunner
    @Binds abstract fun importer(impl: SongPadImporterImpl): SongPadImporter
}
