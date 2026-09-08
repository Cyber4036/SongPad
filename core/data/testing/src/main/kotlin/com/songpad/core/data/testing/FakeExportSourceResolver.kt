package com.songpad.core.data.testing

import com.songpad.core.model.data.ExportSourceResolver
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.export.ExportSource
import com.songpad.core.model.take.AudioFormat

/** Mirrors §12.5: FLAC if stored lossless, else M4A, else raw PCM while transcoding. */
class FakeExportSourceResolver(val store: InMemoryStore = InMemoryStore()) : ExportSourceResolver {
    override suspend fun resolve(takeId: String): Result<ExportSource> {
        val take = store.takes.value[takeId] ?: return domainFailure(DataError.NotFound("Take", takeId))
        val audio = take.audio ?: return domainFailure(DataError.NotFound("AudioAsset", takeId))
        return Result.success(
            ExportSource(
                takeId = takeId,
                relativePath = audio.relativePath,
                mimeType = audio.mimeType,
                isLossless = audio.mimeType == AudioFormat.MIME_FLAC || audio.mimeType == AudioFormat.MIME_RAW_PCM,
                durationMs = take.durationMs,
                byteSize = audio.byteSize,
            ),
        )
    }
}
