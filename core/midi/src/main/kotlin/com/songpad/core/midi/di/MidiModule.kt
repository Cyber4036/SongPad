package com.songpad.core.midi.di

import com.songpad.core.midi.capture.MidiCaptureEngineImpl
import com.songpad.core.model.midi.MidiCaptureEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MidiModule {
    @Binds abstract fun midiCaptureEngine(impl: MidiCaptureEngineImpl): MidiCaptureEngine
}
