package com.songpad.feature.nudges

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.songpad.core.model.data.NudgeRepository
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.logging.Logger
import com.songpad.core.model.time.Clock
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** §11.1 — WorkManager only. AlarmManager is forbidden; a PR that adds it is rejected by default (ADR). */
@HiltWorker
class NudgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val nudges: NudgeRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val notifier: NudgeNotifier,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = runCatching { NudgeRun(nudges, settings, clock).execute(notifier) }
            .getOrElse { t -> logger.w(TAG, "nudge run failed", t); return if (runAttemptCount < 3) Result.retry() else Result.failure() }
        logger.i(TAG, "outcome=$outcome")
        return Result.success()
    }

    companion object {
        const val NAME = "songpad.nudges"
        private const val TAG = "NudgeWorker"
    }
}

/** Exactly the §11.1 configuration. Idempotent (KEEP). */
object NudgeScheduler {
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NudgeWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NudgeWorker>(24, TimeUnit.HOURS, 6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build(),
        )
    }

    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(NudgeWorker.NAME) }
}
