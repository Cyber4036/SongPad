package com.songpad.feature.nudges.di

import com.songpad.feature.nudges.AndroidNudgeNotifier
import com.songpad.feature.nudges.NudgeNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NudgesModule {
    @Binds abstract fun notifier(impl: AndroidNudgeNotifier): NudgeNotifier
}
