package com.songpad.feature.capture.di

import com.songpad.core.audio.capture.OboeAudioRecorderEngine
import com.songpad.core.midi.capture.MidiCaptureEngineImpl
import com.songpad.core.model.audio.AudioRecorderEngine
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.midi.MidiCaptureEngine
import com.songpad.core.model.time.Clock
import com.songpad.core.model.usecase.DiscardRecordingUseCase
import com.songpad.core.model.usecase.FinalizeTakeUseCase
import com.songpad.core.model.usecase.RecoverTakeUseCase
import com.songpad.core.model.usecase.StartRecordingUseCase
import com.songpad.core.model.usecase.StorageProbe
import com.songpad.core.model.usecase.TranscodeScheduler
import com.songpad.feature.capture.MidiTakeBinder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Use cases are plain classes in :core:model (JVM-testable); the feature assembles them from the bound contracts. */
@Module
@InstallIn(ViewModelComponent::class)
object CaptureUseCaseModule {
    @Provides fun start(engine: AudioRecorderEngine, settings: SettingsRepository, ids: IdGenerator, clock: Clock, storage: StorageProbe) =
        StartRecordingUseCase(engine, settings, ids, clock, storage)

    @Provides fun finalize(engine: AudioRecorderEngine, midi: MidiCaptureEngine, takes: TakeRepository, settings: SettingsRepository, transcodes: TranscodeScheduler) =
        FinalizeTakeUseCase(engine, midi, takes, settings, transcodes)

    @Provides fun discard(engine: AudioRecorderEngine, midi: MidiCaptureEngine) = DiscardRecordingUseCase(engine, midi)

    @Provides fun recover(takes: TakeRepository, transcodes: TranscodeScheduler, clock: Clock) = RecoverTakeUseCase(takes, transcodes, clock)
}

@Module
@InstallIn(SingletonComponent::class)
object CaptureWiringModule {
    /**
     * Also hooks the recorder's fsync cadence to the MIDI log (§8.5: "events.log is fsynced on the same cadence
     * as the PCM writer"). Done here, once, because this module is the only place that sees both concrete engines.
     */
    @Provides
    @Singleton
    fun midiTakeBinder(recorder: OboeAudioRecorderEngine, midi: MidiCaptureEngineImpl): MidiTakeBinder {
        recorder.onSyncCadence = { midi.flushLog() }
        return MidiTakeBinder { takeId -> midi.beginTake(takeId) }
    }
}
