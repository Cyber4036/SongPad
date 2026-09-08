package com.songpad.feature.export.work

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.error.ExportError
import com.songpad.core.model.export.ExportRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** What the ViewModel sees of the worker: a status stream and a cancel. The WorkManager binding lives below; tests use a fake. */
sealed interface ExportJobStatus {
    data object Queued : ExportJobStatus
    data class Running(val progress: Float, val item: String?) : ExportJobStatus
    data class Done(val path: String, val name: String, val bytes: Long) : ExportJobStatus
    data class Failed(val error: DomainError) : ExportJobStatus
    data object Cancelled : ExportJobStatus
}

interface ExportJobRunner {
    fun start(request: ExportRequest): Flow<ExportJobStatus>
    fun cancel(request: ExportRequest)
}

class WorkManagerExportRunner @Inject constructor(private val workManager: WorkManager) : ExportJobRunner {
    override fun start(request: ExportRequest): Flow<ExportJobStatus> {
        val work = OneTimeWorkRequestBuilder<ExportWorker>().setInputData(ExportWorker.input(request)).addTag(TAG).build()
        workManager.enqueueUniqueWork(name(request), ExistingWorkPolicy.REPLACE, work)
        return workManager.getWorkInfoByIdFlow(work.id).map { it.toStatus() }
    }

    override fun cancel(request: ExportRequest) { workManager.cancelUniqueWork(name(request)) }

    private fun WorkInfo?.toStatus(): ExportJobStatus = when (this?.state) {
        null, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportJobStatus.Queued
        WorkInfo.State.RUNNING -> ExportJobStatus.Running(progress.getFloat(ExportWorker.KEY_PROGRESS, 0f), progress.getString(ExportWorker.KEY_ITEM))
        WorkInfo.State.SUCCEEDED -> ExportJobStatus.Done(
            outputData.getString(ExportWorker.KEY_PATH).orEmpty(), outputData.getString(ExportWorker.KEY_NAME).orEmpty(), outputData.getLong(ExportWorker.KEY_BYTES, 0),
        )
        WorkInfo.State.FAILED -> {
            val msg = outputData.getString(ExportWorker.KEY_ERROR)
            if (msg == ExportWorker.ERROR_CANCELLED) ExportJobStatus.Cancelled
            else ExportJobStatus.Failed(
                when (outputData.getString(ExportWorker.KEY_ERROR_KIND)) {
                    "NothingToExport" -> ExportError.NothingToExport
                    "EncoderUnavailable" -> ExportError.EncoderUnavailable(msg?.substringBefore(" export").orEmpty())
                    else -> ExportError.Io(msg ?: "unknown")
                },
            )
        }
        WorkInfo.State.CANCELLED -> ExportJobStatus.Cancelled
    }

    companion object {
        const val TAG = "export"
        fun name(request: ExportRequest) = "export:${request.songId}"
    }
}
