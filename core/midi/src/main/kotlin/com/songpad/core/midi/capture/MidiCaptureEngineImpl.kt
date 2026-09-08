package com.songpad.core.midi.capture

import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.midi.smf.SmfWriter
import com.songpad.core.model.audio.AudioRecorderEngine
import com.songpad.core.model.error.MidiError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.midi.MidiCaptureEngine
import com.songpad.core.model.midi.MidiEvent
import com.songpad.core.model.midi.MidiTiming
import com.songpad.core.model.take.MidiAsset
import com.songpad.core.model.take.TakePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §15 `MidiCaptureEngine`. Key events arrive on the UI thread with `eventTimeNanos`; the frame index is
 * computed AT INGEST via `AudioRecorderEngine.frameIndexFor()` (§8.3) and never revisited. Events are
 * buffered in memory and appended to `events.log`; [flushLog] is called by the recorder's writer cadence.
 * `commit()` serializes SMF Type 0 to `performance.mid` and deletes the log.
 */
@Singleton
class MidiCaptureEngineImpl @Inject constructor(
    private val recorder: AudioRecorderEngine,
    private val files: TakeFileStore,
    private val ids: IdGenerator,
) : MidiCaptureEngine {

    private val events = ConcurrentLinkedQueue<MidiEvent>()
    @Volatile private var log: EventsLog? = null
    @Volatile private var activeTakeId: String? = null

    /** Called by the capture use case when a piano-capable take starts, so the log is bound to the right directory. */
    fun beginTake(takeId: String) {
        reset()
        activeTakeId = takeId
        log = EventsLog(files.file(TakePaths.eventsLog(takeId))).also { it.open() }
    }

    override fun onKeyDown(note: Int, velocity: Int, eventTimeNanos: Long) {
        val e = MidiEvent(recorder.frameIndexFor(eventTimeNanos), note.coerceIn(0, 127), velocity.coerceIn(1, 127), true)
        events += e
        log?.append(e)
    }

    override fun onKeyUp(note: Int, eventTimeNanos: Long) {
        val e = MidiEvent(recorder.frameIndexFor(eventTimeNanos), note.coerceIn(0, 127), 0, false)
        events += e
        log?.append(e)
    }

    /** ~1 s cadence, invoked from the recorder's writer thread — never the touch path. */
    fun flushLog() { log?.sync() }

    override suspend fun commit(takeId: String): Result<MidiAsset?> = withContext(Dispatchers.IO) {
        val snapshot = events.toList()
        val currentLog = log
        log = null
        activeTakeId = null
        events.clear()
        currentLog?.close()
        if (snapshot.isEmpty()) {
            currentLog?.delete()
            return@withContext Result.success(null)
        }
        try {
            val asset = writeMidi(takeId, EventsLog.closeDangling(snapshot, Long.MAX_VALUE))
            currentLog?.delete()
            Result.success(asset)
        } catch (e: java.io.IOException) {
            domainFailure(MidiError.Io(e.message ?: "write failed"))
        }
    }

    override fun reset() {
        events.clear()
        log?.close(); log?.delete(); log = null
        activeTakeId = null
    }

    /** §9.4 step 6: convert a surviving `events.log` to `performance.mid`, truncated to [frameCount]. Returns null when the log has no events. */
    fun recover(takeId: String, frameCount: Long): MidiAsset? {
        val logFile = files.file(TakePaths.eventsLog(takeId))
        val recovered = EventsLog.read(logFile, maxFrameExclusive = frameCount)
        val asset = if (recovered.isEmpty()) null else writeMidi(takeId, recovered)
        logFile.delete()
        return asset
    }

    private fun writeMidi(takeId: String, events: List<MidiEvent>): MidiAsset {
        val bytes = SmfWriter.write(events)
        val target: File = files.file(TakePaths.midi(takeId))
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.outputStream().use { it.write(bytes); it.fd.sync() }
        if (!tmp.renameTo(target)) throw java.io.IOException("rename failed for ${target.name}")
        return MidiAsset(ids.newId(), takeId, TakePaths.midi(takeId), MidiTiming.PPQ, bytes.size.toLong(), events.size)
    }
}
