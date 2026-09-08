package com.songpad.core.data.repo

import com.songpad.core.data.db.SongPadDatabase
import com.songpad.core.model.data.ExportSourceResolver
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.export.ExportSource
import com.songpad.core.model.take.AudioFormat
import javax.inject.Inject
import javax.inject.Singleton

/** §12.5: reads `mime_type`, hands every encoder the best available source. No encoder branches on mode. */
@Singleton
class ExportSourceResolverImpl @Inject constructor(private val db: SongPadDatabase) : ExportSourceResolver {
    override suspend fun resolve(takeId: String): Result<ExportSource> {
        val take = db.takeDao().get(takeId) ?: return domainFailure(DataError.NotFound("Take", takeId))
        val asset = db.audioAssetDao().forTake(takeId) ?: return domainFailure(DataError.NotFound("AudioAsset", takeId))
        val lossless = asset.mimeType == AudioFormat.MIME_FLAC || asset.mimeType == AudioFormat.MIME_RAW_PCM
        return Result.success(ExportSource(takeId, asset.relativePath, asset.mimeType, lossless, take.durationMs, asset.byteSize))
    }
}
