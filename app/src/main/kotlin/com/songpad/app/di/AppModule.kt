package com.songpad.app.di

import android.util.Log
import com.songpad.core.model.logging.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The only android.util.Log import in the codebase: core modules log through the seam. */
class AndroidLogger : Logger {
    override fun d(tag: String, message: String) { Log.d(tag, message) }
    override fun i(tag: String, message: String) { Log.i(tag, message) }
    override fun w(tag: String, message: String, t: Throwable?) { Log.w(tag, message, t) }
    override fun e(tag: String, message: String, t: Throwable?) { Log.e(tag, message, t) }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun logger(): Logger = AndroidLogger()
}
