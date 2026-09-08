package com.songpad.core.model.usecase

import com.songpad.core.model.audio.AudioRecorderEngine
import com.songpad.core.model.midi.MidiCaptureEngine

/** Discard is confirmed in the UI when loss is possible (Phase 0 §5); the use case just tears down. */
class DiscardRecordingUseCase(
    private val engine: AudioRecorderEngine,
    private val midi: MidiCaptureEngine,
) {
    suspend operator fun invoke(): Result<Unit> {
        midi.reset()
        return engine.discard()
    }
}
