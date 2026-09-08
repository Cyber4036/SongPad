package com.songpad.feature.export.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.songpad.core.model.data.ExportSourceResolver
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.error.ExportError
import com.songpad.core.model.export.ExportAudioFormat
import com.songpad.core.model.export.ExportMode
import com.songpad.core.model.export.ExportRequest
import com.songpad.core.model.logging.Logger
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.model.time.Clock
import com.songpad.feature.export.bundle.ExportPlanner
import com.songpad.feature.export.bundle.SongBundleManifest
import com.songpad.feature.export.bundle.SongBundleWriter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * §12.2: builds the bundle in `files/tmp/`, reports `setProgress`, honours cancellation (partial archive deleted),
 * cleans staging on every exit. Output is the staged bundle path; delivery (SAF / share) is the screen's job (§12.3).
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val songs: SongRepository,
    private val resolver: ExportSourceResolver,
    private val files: TakeFileStore,
    private val encoders: ExportEncoders,
    private val clock: Clock,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val songId = inputData.getString(KEY_SONG_ID) ?: return fail(ExportError.Io("missing song id"))
        val request = ExportRequest(
            songId = songId,
            mode = ExportMode.valueOf(inputData.getString(KEY_MODE) ?: ExportMode.EVERYTHING.name),
            audioFormat = ExportAudioFormat.valueOf(inputData.getString(KEY_FORMAT) ?: ExportAudioFormat.STORED.name),
        )
        encoders.availability(request.audioFormat)?.let { return fail(it) }
        val detail = songs.observeSong(songId).first() ?: return fail(ExportError.Io("song not found"))
        val planner = ExportPlanner(resolver)
        val plan = planner.plan(detail, request)
        if (plan.takes.isEmpty() && (!plan.includeText || (detail.lyrics?.sections.isNullOrEmpty() && detail.notes.isEmpty()))) return fail(ExportError.NothingToExport)

        val now = clock.nowMillis()
        val staging = File(files.tmpDir(), "export-$id").also { it.mkdirs() }
        val bundle = File(files.tmpDir(), SongBundleWriter.bundleFileName(detail.song.name, now))
        val progress = MutableStateFlow(0f to "")
        return try {
            coroutineScope {
                // The writer's callback runs on an IO thread; progress is published from a coroutine that can suspend.
                val publisher = launch { progress.collect { (f, item) -> setProgress(workDataOf(KEY_PROGRESS to f, KEY_ITEM to item)) } }
                withContext(Dispatchers.IO) {
                    val entries = buildEntries(plan, planner, staging, now)
                    bundle.outputStream().use { out ->
                        SongBundleWriter().write(entries, out) { i, n, path, f ->
                            if (isStopped) return@write false
                            val overall = if (n == 0) 1f else (i + f.coerceIn(0f, 1f)) / n
                            progress.value = overall to path.substringAfterLast('/')
                            true
                        }
                    }
                }
                publisher.cancel()
            }
            Result.success(workDataOf(KEY_PATH to bundle.absolutePath, KEY_BYTES to bundle.length(), KEY_NAME to bundle.name))
        } catch (e: SongBundleWriter.AbortedException) {
            bundle.delete(); Result.failure(workDataOf(KEY_ERROR to ERROR_CANCELLED))
        } catch (e: CancellationException) {
            bundle.delete(); throw e
        } catch (t: Throwable) {
            logger.e(TAG, "export failed", t); bundle.delete(); fail(ExportError.Io(t.message ?: t::class.java.simpleName))
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun buildEntries(plan: ExportPlanner.Plan, planner: ExportPlanner, staging: File, now: Long): List<SongBundleWriter.Entry> = buildList {
        add(SongBundleWriter.Entry.Text("song.json", SongBundleManifest.json.encodeToString(SongBundleManifest.serializer(), planner.manifest(plan, now, GENERATOR))))
        if (plan.includeText) {
            add(SongBundleWriter.Entry.Text("lyrics.txt", planner.lyricsText(plan)))
            add(SongBundleWriter.Entry.Text("notes.txt", planner.notesText(plan)))
        }
        plan.takes.forEach { p ->
            val encoder = encoders.forSource(plan.request.audioFormat, p.source)
            if (encoders.isStreamedDeflated(plan.request.audioFormat, p.source)) {
                add(SongBundleWriter.Entry.Streamed(p.audioEntryPath) { out, onProgress -> encoder.encode(p.source, out, onProgress) })
            } else if (plan.request.audioFormat == ExportAudioFormat.STORED) {
                add(SongBundleWriter.Entry.FromFile(p.audioEntryPath, files.file(p.source.relativePath), stored = true))
            } else {
                // MP3: encode to staging first so the STORED entry knows its size and CRC.
                val staged = File(staging, p.audioEntryPath.substringAfterLast('/'))
                staged.outputStream().use { encoder.encode(p.source, it) }
                add(SongBundleWriter.Entry.FromFile(p.audioEntryPath, staged, stored = true))
            }
            val midi = p.take.midi
            if (p.midiEntryPath != null && midi != null) add(SongBundleWriter.Entry.FromFile(p.midiEntryPath, files.file(midi.relativePath), stored = true))
        }
    }

    private fun fail(error: ExportError): Result = Result.failure(workDataOf(KEY_ERROR to error.message, KEY_ERROR_KIND to error::class.java.simpleName))

    companion object {
        const val KEY_SONG_ID = "song_id"
        const val KEY_MODE = "mode"
        const val KEY_FORMAT = "format"
        const val KEY_PROGRESS = "progress"
        const val KEY_ITEM = "item"
        const val KEY_PATH = "path"
        const val KEY_BYTES = "bytes"
        const val KEY_NAME = "name"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_KIND = "error_kind"
        const val ERROR_CANCELLED = "cancelled"
        const val GENERATOR = "SongPad/1.0"
        private const val TAG = "ExportWorker"

        fun input(request: ExportRequest): Data = workDataOf(KEY_SONG_ID to request.songId, KEY_MODE to request.mode.name, KEY_FORMAT to request.audioFormat.name)
    }
}
