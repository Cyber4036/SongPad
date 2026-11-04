package com.songpad.feature.export.bundle

import com.songpad.core.model.data.ExportSourceResolver
import com.songpad.core.model.export.ExportAudioFormat
import com.songpad.core.model.export.ExportMode
import com.songpad.core.model.export.ExportRequest
import com.songpad.core.model.export.ExportSource
import com.songpad.core.model.export.ExportSummary
import com.songpad.core.model.song.SongDetail
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.Take
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns a [SongDetail] plus [ExportSourceResolver] answers into the summary the screen shows before confirmation
 * (Phase 0 §13) and the entry plan the worker executes. Pure Kotlin — tested with fakes.
 */
class ExportPlanner(private val resolver: ExportSourceResolver) {

    data class PlannedTake(val take: Take, val source: ExportSource, val audioEntryPath: String, val midiEntryPath: String?)

    data class Plan(
        val request: ExportRequest,
        val detail: SongDetail,
        val takes: List<PlannedTake>,
        val includeText: Boolean,
        val summary: ExportSummary,
    )

    suspend fun plan(detail: SongDetail, request: ExportRequest): Plan {
        val includeAudio = request.mode != ExportMode.LYRICS_ONLY
        val includeText = request.mode != ExportMode.AUDIO_TAKES && request.takeId == null
        val exportable = if (includeAudio) {
            detail.takes
                .filter { it.isPlayable && it.audio != null && (request.takeId == null || it.id == request.takeId) }
                .sortedBy { it.meta.createdAt }
        } else {
            emptyList()
        }
        val planned = exportable.mapIndexedNotNull { i, take ->
            val source = resolver.resolve(take.id).getOrNull() ?: return@mapIndexedNotNull null
            val ext = extensionFor(request.audioFormat, source)
            val base = "%02d %s".format(i + 1, SongBundleWriter.safeEntryName(take.name))
            PlannedTake(
                take = take, source = source,
                audioEntryPath = "audio/$base.$ext",
                midiEntryPath = if (request.mode == ExportMode.EVERYTHING && take.midi != null) "midi/$base.mid" else null,
            )
        }
        val sections = if (includeText) detail.lyrics?.sections.orEmpty() else emptyList()
        val notes = if (includeText) detail.notes else emptyList()

        val isDirectLyrics = request.mode == ExportMode.LYRICS_ONLY
        val isDirectSingleAudio = !isDirectLyrics &&
            (request.takeId != null || (request.mode == ExportMode.AUDIO_TAKES && planned.size == 1 && planned[0].midiEntryPath == null)) &&
            planned.size == 1

        val (outputFileName, outputMime) = when {
            isDirectLyrics -> SongBundleWriter.lyricsFileName(detail.song.name) to "text/plain"
            isDirectSingleAudio -> {
                val single = planned[0]
                val ext = extensionFor(request.audioFormat, single.source)
                SongBundleWriter.singleTakeFileName(detail.song.name, single.take.name, ext) to mimeTypeForExtension(ext)
            }
            else -> SongBundleWriter.bundleFileName(detail.song.name, System.currentTimeMillis()) to "application/zip"
        }

        val estimatedBytes = when {
            isDirectLyrics -> (sections.sumOf { it.bodyText.length.toLong() + 24L } + notes.sumOf { it.bodyText.length.toLong() + 32L }).coerceAtLeast(128L)
            isDirectSingleAudio -> estimateBytes(request.audioFormat, planned[0].source)
            else -> planned.sumOf { estimateBytes(request.audioFormat, it.source) } + planned.sumOf { it.take.midi?.byteSize ?: 0L } +
                sections.sumOf { it.bodyText.length.toLong() } + notes.sumOf { it.bodyText.length.toLong() } + 2_048
        }

        val selectedTakeName = request.takeId?.let { id -> detail.takes.firstOrNull { it.id == id }?.name }
        val summary = ExportSummary(
            songName = detail.song.name,
            takeCount = planned.size,
            midiCount = planned.count { it.midiEntryPath != null },
            sectionCount = sections.size,
            noteCount = notes.size,
            losslessTakeCount = planned.count { it.source.isLossless },
            estimatedBytes = estimatedBytes,
            outputFileName = outputFileName,
            outputMimeType = outputMime,
            selectedTakeName = selectedTakeName,
        )
        return Plan(request, detail, planned, includeText, summary)
    }

    fun manifest(plan: Plan, exportedAtMillis: Long, generator: String): SongBundleManifest = SongBundleManifest(
        generator = generator,
        exportedAtMillis = exportedAtMillis,
        mode = plan.request.mode.name,
        song = plan.detail.song.let { SongBundleManifest.SongInfo(it.id, it.name, it.status.name, it.isFavorite, it.createdAt, it.updatedAt) },
        takes = plan.takes.map { p ->
            SongBundleManifest.TakeInfo(
                id = p.take.id, name = p.take.name, type = p.take.type.name, durationMs = p.take.durationMs, createdAtMillis = p.take.meta.createdAt,
                audioFile = p.audioEntryPath, sourceFormat = sourceLabel(p.source.mimeType),
                exportFormat = extensionFor(plan.request.audioFormat, p.source), midiFile = p.midiEntryPath,
            )
        },
        sections = if (plan.includeText) plan.detail.lyrics?.sections.orEmpty().sortedBy { it.sortOrder }.map {
            SongBundleManifest.SectionInfo(
                id = it.id,
                label = it.displayLabel,
                tag = it.tag.name,
                sortOrder = it.sortOrder,
                bodyText = it.bodyText,
                customLabel = it.customLabel,
            )
        } else emptyList(),
        notes = if (plan.includeText) plan.detail.notes.map {
            SongBundleManifest.NoteInfo(
                id = it.id,
                createdAtMillis = it.meta.createdAt,
                sectionRefId = it.sectionRefId,
                takeRefId = it.takeRefId,
                bodyText = it.bodyText,
            )
        } else emptyList(),
    )

    /** `lyrics.txt`: sections in sort order, human readable (§12.1). */
    fun lyricsText(plan: Plan): String = plan.detail.lyrics?.plainText.orEmpty()

    /** `notes.txt`: notes with timestamps. */
    fun notesText(plan: Plan): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return plan.detail.notes.sortedBy { it.meta.createdAt }.joinToString("\n\n") { n -> "[${fmt.format(Date(n.meta.createdAt))}]\n${n.bodyText}" }
    }

    /** Standalone `.txt` file content for `ExportMode.LYRICS_ONLY` so users get a readable text document. */
    fun standaloneLyricsDocumentText(plan: Plan): String = buildString {
        val lyrics = lyricsText(plan).trim()
        val notes = notesText(plan).trim()
        if (lyrics.isNotEmpty()) {
            append(lyrics)
        }
        if (notes.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n--- Notes ---\n\n")
            append(notes)
        }
    }

    companion object {
        fun extensionFor(format: ExportAudioFormat, source: ExportSource): String = when (format) {
            ExportAudioFormat.STORED -> when (source.mimeType) {
                AudioFormat.MIME_FLAC -> "flac"
                AudioFormat.MIME_M4A -> "m4a"
                AudioFormat.MIME_MP3 -> "mp3"
                AudioFormat.MIME_WAV -> "wav"
                else -> "wav"
            }
            ExportAudioFormat.WAV -> "wav"
            ExportAudioFormat.MP3 -> "mp3"
        }

        fun mimeTypeForExtension(ext: String): String = when (ext.lowercase(Locale.US)) {
            "flac" -> AudioFormat.MIME_FLAC
            "m4a" -> AudioFormat.MIME_M4A
            "mp3" -> AudioFormat.MIME_MP3
            "wav" -> AudioFormat.MIME_WAV
            "txt" -> "text/plain"
            else -> "application/zip"
        }

        fun sourceLabel(mimeType: String): String = when (mimeType) {
            AudioFormat.MIME_FLAC -> "flac"
            AudioFormat.MIME_M4A -> "m4a"
            AudioFormat.MIME_MP3 -> "mp3"
            AudioFormat.MIME_WAV -> "wav"
            AudioFormat.MIME_RAW_PCM -> "pcm"
            else -> mimeType
        }

        /** Rough size for the confirmation screen; the worker reports the true size at the end. */
        fun estimateBytes(format: ExportAudioFormat, source: ExportSource): Long = when (format) {
            ExportAudioFormat.STORED -> if (source.mimeType == AudioFormat.MIME_RAW_PCM) source.durationMs * AudioFormat.BYTES_PER_MS + 44 else source.byteSize
            ExportAudioFormat.WAV -> source.durationMs * AudioFormat.BYTES_PER_MS + 44
            ExportAudioFormat.MP3 -> source.durationMs * AudioFormat.EXPORT_MP3_BITRATE_KBPS * 1000 / 8 / 1000
        }
    }
}
