package com.songpad.core.model.usecase

import com.songpad.core.model.audio.AudioRecorderEngine
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.error.CaptureError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.midi.MidiCaptureEngine
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeDraft
import com.songpad.core.model.take.TakeType

/** Enqueues the post-commit transcode (§7.1). WorkManager in :core:audio; a fake in tests. */
fun interface TranscodeScheduler {
    fun enqueue(takeId: String)
}

/**
 * §16: stop, flush, fsync, rename `.tmp` → `.pcm` (all inside the engine), commit MIDI if any, then ONE
 * Room transaction for Song + Take + AudioAsset with `status = TRANSCODING`, then enqueue the worker.
 * The take is playable from raw PCM the instant this returns (§17 "Transcoding").
 */
class FinalizeTakeUseCase(
    private val engine: AudioRecorderEngine,
    private val midi: MidiCaptureEngine,
    private val takes: TakeRepository,
    private val settings: SettingsRepository,
    private val transcodes: TranscodeScheduler,
) {
    suspend operator fun invoke(session: CaptureSession, takeName: String, newSongName: String?): Result<Take> {
        val audio = engine.stopAndCommit(session.takeId).getOrElse { return Result.failure(it) }
        if (audio.frames <= 0) return domainFailure(CaptureError.NothingCaptured)

        val midiAsset = if (session.type == TakeType.VOICE) {
            midi.reset(); null
        } else {
            midi.commit(session.takeId).getOrElse { return Result.failure(it) }
        }

        val draft = TakeDraft(
            takeId = session.takeId,
            name = takeName.ifBlank { defaultTakeName(session) },
            type = session.type,
            songId = session.songId,
            newSongName = newSongName?.takeIf { session.songId == null && it.isNotBlank() },
            audio = audio,
            midi = midiAsset,
            qualityMode = session.qualityMode,
        )
        return takes.commit(draft).onSuccess {
            settings.markFirstTakeSaved()
            transcodes.enqueue(it.id)
        }
    }

    private fun defaultTakeName(session: CaptureSession): String = when (session.type) {
        TakeType.VOICE -> "Voice take"
        TakeType.PIANO -> "Piano take"
        TakeType.VOICE_PIANO -> "Voice + Piano take"
    }
}
