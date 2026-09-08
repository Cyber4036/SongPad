package com.songpad.core.model.usecase

import com.songpad.core.model.audio.AudioRecorderEngine
import com.songpad.core.model.audio.CaptureConfig
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.error.CaptureError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.state.StorageState
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.time.Clock
import kotlinx.coroutines.flow.first

/** Reads free storage. Implemented with StatFs in :core:audio; a fake in tests. */
fun interface StorageProbe {
    fun freeBytes(): Long
}

/**
 * §16 slice, step one. Allocates the take id, refuses on low storage (§7.4) and starts the engine.
 * The quality mode is snapshotted here — the setting applies to new recordings only (§4.3).
 */
class StartRecordingUseCase(
    private val engine: AudioRecorderEngine,
    private val settings: SettingsRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val storage: StorageProbe,
) {
    suspend operator fun invoke(type: TakeType, songId: String?): Result<CaptureSession> {
        val storageState = StorageState.of(storage.freeBytes(), AudioFormat.LOW_STORAGE_BYTES, AudioFormat.CRITICAL_STORAGE_BYTES)
        if (storageState !is StorageState.Normal) return domainFailure(CaptureError.LowStorage(storageState.freeBytes))

        val mode = settings.qualityMode.first()
        val session = CaptureSession(
            takeId = ids.newId(),
            type = type,
            songId = songId,
            qualityMode = mode,
            startedAtMillis = clock.nowMillis(),
        )
        return engine.start(CaptureConfig(session.takeId, type, mode, songId)).map { session }
    }
}
