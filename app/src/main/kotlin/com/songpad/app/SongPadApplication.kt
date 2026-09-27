package com.songpad.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.songpad.core.audio.recovery.AudioRecoveryManager
import com.songpad.core.data.work.MaintenanceScheduler
import com.songpad.core.model.logging.Logger
import com.songpad.feature.nudges.NudgeScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt root + WorkManager configuration (the default initializer is removed in the manifest so HiltWorkerFactory
 * is used). Cold-start duties: the §9.4 recovery scan (off the main thread), daily maintenance (§9.2/§9.3) and the
 * §11.1 nudge worker — all WorkManager, never AlarmManager.
 */
@HiltAndroidApp
class SongPadApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var recovery: AudioRecoveryManager
    @Inject lateinit var logger: Logger

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).setMinimumLoggingLevel(android.util.Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        appScope.launch {
            runCatching { recovery.scanOnce() }.onFailure { logger.w(TAG, "recovery scan failed", it) }
        }
        MaintenanceScheduler.schedule(this)
        NudgeScheduler.schedule(this)
    }

    /**
     * Wave 5 hardening: debug builds log main-thread disk/network access and leaked closeables, cursors,
     * registrations and activities to logcat (tag StrictMode), so device sessions surface ANR and leak
     * candidates without a profiler attached. Log-only — never kills the process — and absent from release.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects().detectLeakedSqlLiteObjects().detectLeakedRegistrationObjects()
                .detectActivityLeaks().detectFileUriExposure().penaltyLog().build(),
        )
    }

    companion object { private const val TAG = "SongPadApplication" }
}
