package com.songpad.core.audio.transcode

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.usecase.TranscodeScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * §7.1 finalize step: TRANSCODING → READY. Runs in WorkManager so it survives the app being closed. The take
 * is already playable from raw PCM, so this never blocks the user (§17). On exhausted retries the take is
 * marked FAILED and the raw PCM is kept — it is still the user's recording.
 */
@HiltWorker
class TranscodeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val takes: TakeRepository,
    private val files: TakeFileStore,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val takeId = inputData.getString(KEY_TAKE_ID) ?: return Result.failure()
        val take = takes.getTake(takeId) ?: return Result.failure()
        if (take.status == TakeStatus.READY) return Result.success()
        val pcm = files.file(TakePaths.pcm(takeId))
        if (!pcm.exists()) { takes.markFailed(takeId); return Result.failure() }

        // Mode was decided at record time (§4.3); the draft's mode is carried through the worker input, defaulting to the current setting.
        val mode = inputData.getString(KEY_MODE)?.let { runCatching { QualityMode.valueOf(it) }.getOrNull() } ?: settings.qualityMode.first()
        val target = files.file(TakePaths.stored(takeId, mode))
        return try {
            val out = PcmTranscoder().transcode(pcm, mode, target)
            if (!PcmTranscoder.verify(out, pcm.length())) {
                Log.w(TAG, "verification failed for $takeId (${out.durationUs} us)")
                target.delete()
                return retryOrFail(takeId)
            }
            takes.markTranscoded(takeId, TakePaths.stored(takeId, mode), out.mimeType, out.byteSize).getOrThrow()
            pcm.delete() // §4.3: only after a verified transcode
            Result.success()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Log.e(TAG, "transcode failed for $takeId", t)
            target.delete()
            retryOrFail(takeId)
        }
    }

    private suspend fun retryOrFail(takeId: String): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else { takes.markFailed(takeId); Result.failure() }

    companion object {
        const val KEY_TAKE_ID = "take_id"
        const val KEY_MODE = "quality_mode"
        private const val MAX_ATTEMPTS = 3
        private const val TAG = "TranscodeWorker"
    }
}

/** Enqueues one unique transcode per take; safe to call twice (KEEP). */
class WorkManagerTranscodeScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val settings: SettingsRepository,
) : TranscodeScheduler {
    override fun enqueue(takeId: String) {
        val request = OneTimeWorkRequestBuilder<TranscodeWorker>()
            .setInputData(workDataOf(TranscodeWorker.KEY_TAKE_ID to takeId))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("transcode")
            .build()
        workManager.enqueueUniqueWork("transcode:$takeId", ExistingWorkPolicy.KEEP, request)
    }

    /** Variant used when the mode is known at enqueue time (the finalize path). */
    fun enqueue(takeId: String, mode: QualityMode) {
        val request = OneTimeWorkRequestBuilder<TranscodeWorker>()
            .setInputData(workDataOf(TranscodeWorker.KEY_TAKE_ID to takeId, TranscodeWorker.KEY_MODE to mode.name))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("transcode")
            .build()
        workManager.enqueueUniqueWork("transcode:$takeId", ExistingWorkPolicy.KEEP, request)
    }

    @Suppress("unused")
    private val settingsRef = settings
}
