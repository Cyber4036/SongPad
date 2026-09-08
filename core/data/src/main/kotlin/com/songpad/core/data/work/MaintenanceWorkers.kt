package com.songpad.core.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.songpad.core.data.db.SongPadDatabase
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.model.state.CaptureSessionFlag
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.time.Clock
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

/** §9.2 step 3: rows soft-deleted longer than the retention window are hard-deleted with their files. */
@HiltWorker
class PurgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: SongPadDatabase,
    private val files: TakeFileStore,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cutoff = clock.nowMillis() - RETENTION_MS
        db.takeDao().purgeCandidates(cutoff).forEach { id ->
            files.deleteTakeFiles(id)
            db.takeDao().hardDelete(id)
        }
        db.noteDao().purgeCandidates(cutoff).forEach { id ->
            files.deleteNoteFiles(id)
            db.noteDao().hardDelete(id)
        }
        db.songDao().purgeCandidates(cutoff).forEach { id ->
            // Children cascade in the DB; their files are reconciled by OrphanCleanupWorker after the 24 h grace.
            db.songDao().hardDelete(id)
        }
        return Result.success()
    }

    companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        const val NAME = "songpad.purge"
    }
}

/**
 * §9.3 — normative safety rule. A file is collected only if ALL hold:
 *  1. no referencing row (soft-deleted rows inside their window are NOT orphans),
 *  2. last-modified older than 24 hours,
 *  3. the take directory holds no `.pcm.tmp` and no `events.log`,
 *  4. no capture session is active — if one is, the whole run is deferred.
 * Logs what it would delete before deleting, at debug level, for the whole of Phase 1–5.
 */
@HiltWorker
class OrphanCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: SongPadDatabase,
    private val files: TakeFileStore,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (CaptureSessionFlag.isActive) {
            Log.d(TAG, "capture active — deferring orphan cleanup entirely (rule 4)")
            return Result.retry()
        }
        val now = clock.nowMillis()
        val referenced = (db.takeDao().allIdsIncludingDeleted() + db.audioAssetDao().allTakeIdsIncludingDeleted() + db.midiAssetDao().allTakeIdsIncludingDeleted()).toSet()
        val candidates = files.listTakeDirs().filter { dir -> isCollectable(dir, referenced, now) }
        candidates.forEach { Log.d(TAG, "would delete orphan take dir ${it.name}") }
        if (CaptureSessionFlag.isActive) return Result.retry() // re-check after the scan: belt and braces
        candidates.forEach { it.deleteRecursively() }

        val noteIds = db.noteDao().allIdsIncludingDeleted().toSet()
        files.notesRoot().listFiles()?.filter { it.isDirectory && it.name !in noteIds && newestMtime(it) < now - GRACE_MS }?.forEach {
            Log.d(TAG, "would delete orphan note dir ${it.name}")
            it.deleteRecursively()
        }
        return Result.success()
    }

    private fun isCollectable(dir: File, referenced: Set<String>, now: Long): Boolean {
        if (dir.name in referenced) return false                                             // rule 1
        if (newestMtime(dir) >= now - GRACE_MS) return false                                 // rule 2
        if (File(dir, TakePaths.AUDIO_PCM_TMP).exists() || File(dir, TakePaths.EVENTS_LOG).exists()) return false // rule 3
        return true
    }

    private fun newestMtime(dir: File): Long = maxOf(dir.lastModified(), dir.listFiles()?.maxOfOrNull { it.lastModified() } ?: 0L)

    companion object {
        const val GRACE_MS = 24L * 60 * 60 * 1000
        const val NAME = "songpad.orphan_cleanup"
        private const val TAG = "OrphanCleanup"
    }
}

/** Schedules both daily maintenance workers (WorkManager only — §4.1). Idempotent. */
object MaintenanceScheduler {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            PurgeWorker.NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            OrphanCleanupWorker.NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OrphanCleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresDeviceIdle(true).build())
                .build(),
        )
    }
}
