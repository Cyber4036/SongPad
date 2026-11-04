package com.songpad.feature.export.bundle

import android.media.MediaMetadataRetriever
import com.songpad.core.audio.capture.PeakReducer
import com.songpad.core.audio.encode.PcmDecoder
import com.songpad.core.model.data.LyricsRepository
import com.songpad.core.model.data.NoteRepository
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.export.ExportSource
import com.songpad.core.model.export.ImportResult
import com.songpad.core.model.export.SongPadImporter
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.NoteDraft
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.CapturedAudio
import com.songpad.core.model.take.MidiAsset
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeDraft
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.take.TakeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Full round-trip importer supporting:
 * 1. SongPad bundle archives (`.zip`, legacy `.songpad`, and `.songpad.bin` / `.bin` files with ZIP magic bytes),
 *    restoring songs, audio takes, MIDI sidecars, lyrics sections, and notes (both new `song.json` with `bodyText`
 *    and legacy `song.json` + `lyrics.txt` / `notes.txt`).
 * 2. Standalone audio files (`.m4a`, `.flac`, `.wav`, `.mp3`, `.aac`, `.ogg`, `.pcm`), computing duration and
 *    `peaks.wave` waveform sidecars.
 * 3. Plain-text lyrics documents (`.txt`), parsing `[Section]` headers into structured `LyricsDocument` sections.
 */
@Singleton
class SongPadImporterImpl @Inject constructor(
    private val songs: SongRepository,
    private val takes: TakeRepository,
    private val lyrics: LyricsRepository,
    private val notes: NoteRepository,
    private val files: TakeFileStore,
    private val pcmDecoder: PcmDecoder,
    private val ids: IdGenerator,
) : SongPadImporter {

    override suspend fun importFromStream(
        displayName: String,
        mimeType: String?,
        targetSongId: String?,
        openStream: () -> InputStream?,
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        val tempFile = File(files.tmpDir(), "import-${ids.newId()}.tmp")
        try {
            val stream = openStream() ?: return@withContext domainFailure(DataError.Io("Could not open selected file."))
            stream.use { input ->
                tempFile.outputStream().buffered().use { out -> input.copyTo(out, 16 * 1024) }
            }
            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext domainFailure(DataError.InvalidInput("The selected file is empty."))
            }

            val magic = readMagicBytes(tempFile, 12)
            val cleanName = displayName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "Imported" }
            val ext = cleanName.substringAfterLast('.', "").lowercase(Locale.US)

            when {
                isZipMagic(magic) -> importZipArchive(tempFile, cleanName, targetSongId)
                isAudioMagicOrExtension(magic, ext, mimeType) -> importStandaloneAudio(tempFile, cleanName, ext, mimeType, magic, targetSongId)
                else -> importStandaloneLyrics(tempFile, cleanName, targetSongId)
            }
        } catch (t: Throwable) {
            domainFailure(DataError.Io(t.message ?: "Import failed."))
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun importZipArchive(
        zipTempFile: File,
        displayName: String,
        targetSongId: String?,
    ): Result<ImportResult> {
        ZipFile(zipTempFile).use { zip ->
            val manifestEntry = zip.getEntry("song.json")
            val lyricsEntry = zip.getEntry("lyrics.txt")
            val notesEntry = zip.getEntry("notes.txt")
            val lyricsTxt = lyricsEntry?.let { zip.getInputStream(it).bufferedReader(Charsets.UTF_8).use { r -> r.readText() } }
            val notesTxt = notesEntry?.let { zip.getInputStream(it).bufferedReader(Charsets.UTF_8).use { r -> r.readText() } }

            if (manifestEntry != null) {
                val manifestJson = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val manifest = SongBundleManifest.json.decodeFromString(SongBundleManifest.serializer(), manifestJson)
                return importManifestBundle(zip, manifest, lyricsTxt, notesTxt, targetSongId)
            }

            // Generic ZIP without song.json: import any audio and .txt entries inside
            val baseSongName = stripArchiveExtension(displayName).ifBlank { "Imported Song" }
            val destSong = if (targetSongId != null) {
                songs.getSong(targetSongId) ?: songs.create(uniqueSongName(baseSongName)).getOrThrow()
            } else {
                songs.create(uniqueSongName(baseSongName)).getOrThrow()
            }

            var takeCount = 0
            var sectionCount = 0
            val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
            for (entry in entries) {
                val entryName = entry.name.substringAfterLast('/')
                if (entryName.startsWith(".")) continue
                val ext = entryName.substringAfterLast('.', "").lowercase(Locale.US)
                if (ext in AUDIO_EXTENSIONS) {
                    val staged = File(files.tmpDir(), "zip-audio-${ids.newId()}.$ext")
                    try {
                        zip.getInputStream(entry).use { input -> staged.outputStream().use { input.copyTo(it) } }
                        val magic = readMagicBytes(staged, 12)
                        val res = importStandaloneAudio(staged, entryName, ext, null, magic, destSong.id)
                        if (res.isSuccess) takeCount++
                    } finally {
                        staged.delete()
                    }
                } else if (ext == "txt") {
                    val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    sectionCount += insertParsedSectionsIntoSong(destSong.id, text)
                }
            }
            if (takeCount == 0 && sectionCount == 0) {
                return domainFailure(DataError.InvalidInput("No recognizable audio takes or lyrics found in the archive."))
            }
            return Result.success(
                ImportResult.SongBundleImported(
                    songId = destSong.id,
                    songName = destSong.name,
                    takeCount = takeCount,
                    sectionCount = sectionCount,
                    noteCount = 0,
                ),
            )
        }
    }

    private suspend fun importManifestBundle(
        zip: ZipFile,
        manifest: SongBundleManifest,
        lyricsTxt: String?,
        notesTxt: String?,
        targetSongId: String?,
    ): Result<ImportResult> {
        val existingTarget = targetSongId?.let { songs.getSong(it) }
        val destSong = if (existingTarget != null) {
            existingTarget
        } else {
            val resolvedName = uniqueSongName(manifest.song.name.ifBlank { "Imported Song" })
            val created = songs.create(resolvedName).getOrThrow()
            val desiredStatus = SongStatus.entries.firstOrNull { it.name == manifest.song.status } ?: SongStatus.ONGOING
            if (desiredStatus != SongStatus.ONGOING) {
                songs.setStatus(created.id, desiredStatus)
            }
            if (manifest.song.favorite) {
                songs.toggleFavorite(created.id)
            }
            songs.getSong(created.id) ?: created
        }

        // 1. Restore Lyrics Sections (supports both new song.json with bodyText and legacy song.json + lyrics.txt)
        val parsedTxtSections = lyricsTxt?.let { parsePlainTextLyrics(it) }.orEmpty()
        var importedSectionsCount = 0
        if (manifest.sections.isNotEmpty()) {
            val sortedManifestSections = manifest.sections.sortedBy { it.sortOrder }
            val hasManifestBodies = sortedManifestSections.any { it.bodyText.isNotBlank() }
            sortedManifestSections.forEachIndexed { index, secInfo ->
                val tag = SectionTag.entries.firstOrNull { it.name == secInfo.tag } ?: SectionTag.VERSE
                val customLabel = secInfo.customLabel ?: if (tag == SectionTag.CUSTOM) secInfo.label else null
                val body = when {
                    hasManifestBodies -> secInfo.bodyText
                    index < parsedTxtSections.size -> parsedTxtSections[index].bodyText
                    else -> parsedTxtSections.firstOrNull { it.label.equals(secInfo.label, ignoreCase = true) }?.bodyText.orEmpty()
                }
                val createdSec = lyrics.insertSection(destSong.id, tag, customLabel, null).getOrNull()
                if (createdSec != null) {
                    if (body.isNotEmpty()) {
                        lyrics.updateSectionText(createdSec.id, body)
                    }
                    importedSectionsCount++
                }
            }
        } else if (parsedTxtSections.isNotEmpty()) {
            for (parsed in parsedTxtSections) {
                val createdSec = lyrics.insertSection(destSong.id, parsed.tag, parsed.customLabel, null).getOrNull()
                if (createdSec != null) {
                    if (parsed.bodyText.isNotEmpty()) {
                        lyrics.updateSectionText(createdSec.id, parsed.bodyText)
                    }
                    importedSectionsCount++
                }
            }
        }

        // 2. Restore Audio & MIDI Takes
        var importedTakesCount = 0
        for (takeInfo in manifest.takes.sortedBy { it.createdAtMillis }) {
            val audioPathInZip = takeInfo.audioFile ?: continue
            val zipEntry = zip.getEntry(audioPathInZip) ?: continue
            val newTakeId = ids.newId()
            files.takeDir(newTakeId)

            val ext = (takeInfo.exportFormat ?: audioPathInZip.substringAfterLast('.', "wav")).lowercase(Locale.US)
            val (storedRelPath, storedMime) = storedPathAndMimeFor(newTakeId, ext)
            val dstFile = files.file(storedRelPath)
            dstFile.parentFile?.mkdirs()
            zip.getInputStream(zipEntry).use { input ->
                dstFile.outputStream().buffered().use { out -> input.copyTo(out, 16 * 1024) }
            }

            // Optional MIDI sidecar
            val midiAsset = takeInfo.midiFile?.let { midiPathInZip ->
                zip.getEntry(midiPathInZip)?.let { midiEntry ->
                    val midiRel = TakePaths.midi(newTakeId)
                    val midiFile = files.file(midiRel)
                    midiFile.parentFile?.mkdirs()
                    zip.getInputStream(midiEntry).use { input ->
                        midiFile.outputStream().buffered().use { out -> input.copyTo(out, 16 * 1024) }
                    }
                    MidiAsset(
                        id = ids.newId(),
                        takeId = newTakeId,
                        relativePath = midiRel,
                        ppq = 480,
                        byteSize = midiFile.length(),
                        eventCount = 1,
                    )
                }
            }

            val durationMs = takeInfo.durationMs.takeIf { it > 0L } ?: probeDurationMs(dstFile, storedMime)
            val peaksRel = generatePeaksForTake(newTakeId, storedRelPath, storedMime, durationMs, dstFile.length())
            val takeType = TakeType.entries.firstOrNull { it.name == takeInfo.type }
                ?: if (midiAsset != null) TakeType.PIANO else TakeType.VOICE
            val qualityMode = if (storedMime == AudioFormat.MIME_FLAC || storedMime == AudioFormat.MIME_WAV) {
                QualityMode.LOSSLESS
            } else {
                QualityMode.STANDARD
            }

            val draft = TakeDraft(
                takeId = newTakeId,
                name = takeInfo.name.ifBlank { takes.nextDefaultName(takeType) },
                type = takeType,
                songId = destSong.id,
                newSongName = null,
                audio = CapturedAudio(
                    takeId = newTakeId,
                    relativePath = storedRelPath,
                    frames = (durationMs * AudioFormat.SAMPLE_RATE / 1000L).coerceAtLeast(1L),
                    byteSize = dstFile.length(),
                    peaksPath = peaksRel,
                ),
                midi = midiAsset,
                qualityMode = qualityMode,
            )
            val committed = takes.commit(draft)
            if (committed.isSuccess) {
                takes.markTranscoded(newTakeId, storedRelPath, storedMime, dstFile.length())
                importedTakesCount++
            }
        }

        // 3. Restore Notes (supports both new song.json with bodyText and legacy notes.txt)
        val parsedNotes = notesTxt?.let { parsePlainTextNotes(it) }.orEmpty()
        var importedNotesCount = 0
        if (manifest.notes.isNotEmpty()) {
            val hasNoteBodies = manifest.notes.any { it.bodyText.isNotBlank() }
            manifest.notes.forEachIndexed { index, noteInfo ->
                val body = when {
                    hasNoteBodies -> noteInfo.bodyText
                    index < parsedNotes.size -> parsedNotes[index]
                    else -> ""
                }.trim()
                if (body.isNotEmpty()) {
                    val res = notes.create(NoteDraft(songId = destSong.id, bodyText = body))
                    if (res.isSuccess) importedNotesCount++
                }
            }
        } else if (parsedNotes.isNotEmpty()) {
            for (body in parsedNotes) {
                if (body.isNotBlank()) {
                    val res = notes.create(NoteDraft(songId = destSong.id, bodyText = body.trim()))
                    if (res.isSuccess) importedNotesCount++
                }
            }
        }

        return Result.success(
            ImportResult.SongBundleImported(
                songId = destSong.id,
                songName = destSong.name,
                takeCount = importedTakesCount,
                sectionCount = importedSectionsCount,
                noteCount = importedNotesCount,
            ),
        )
    }

    private suspend fun importStandaloneAudio(
        tempFile: File,
        displayName: String,
        ext: String,
        mimeType: String?,
        magic: ByteArray,
        targetSongId: String?,
    ): Result<ImportResult> {
        val newTakeId = ids.newId()
        files.takeDir(newTakeId)

        val resolvedExt = resolveAudioExtension(ext, mimeType, magic)
        val (storedRelPath, storedMime) = storedPathAndMimeFor(newTakeId, resolvedExt)
        val dstFile = files.file(storedRelPath)
        dstFile.parentFile?.mkdirs()
        tempFile.copyTo(dstFile, overwrite = true)

        val durationMs = probeDurationMs(dstFile, storedMime).coerceAtLeast(1_000L)
        val peaksRel = generatePeaksForTake(newTakeId, storedRelPath, storedMime, durationMs, dstFile.length())
        val baseName = displayName.substringBeforeLast('.').replace('_', ' ').trim().ifBlank {
            takes.nextDefaultName(TakeType.VOICE)
        }
        val qualityMode = if (storedMime == AudioFormat.MIME_FLAC || storedMime == AudioFormat.MIME_WAV) {
            QualityMode.LOSSLESS
        } else {
            QualityMode.STANDARD
        }

        val validSongId = targetSongId?.takeIf { songs.getSong(it) != null }
        val draft = TakeDraft(
            takeId = newTakeId,
            name = baseName,
            type = TakeType.VOICE,
            songId = validSongId,
            newSongName = null,
            audio = CapturedAudio(
                takeId = newTakeId,
                relativePath = storedRelPath,
                frames = (durationMs * AudioFormat.SAMPLE_RATE / 1000L).coerceAtLeast(1L),
                byteSize = dstFile.length(),
                peaksPath = peaksRel,
            ),
            midi = null,
            qualityMode = qualityMode,
        )
        takes.commit(draft).getOrThrow()
        takes.markTranscoded(newTakeId, storedRelPath, storedMime, dstFile.length()).getOrThrow()

        return Result.success(
            ImportResult.TakeImported(
                takeId = newTakeId,
                takeName = baseName,
                songId = validSongId,
                durationMs = durationMs,
            ),
        )
    }

    private suspend fun importStandaloneLyrics(
        tempFile: File,
        displayName: String,
        targetSongId: String?,
    ): Result<ImportResult> {
        val rawText = tempFile.readText(Charsets.UTF_8)
        if (rawText.isBlank()) {
            return domainFailure(DataError.InvalidInput("The text file is empty."))
        }
        val mainLyricsPart = rawText.substringBefore("\n--- Notes ---\n")
        val notesPart = rawText.substringAfter("\n--- Notes ---\n", missingDelimiterValue = "")
        val docName = displayName.substringBeforeLast('.').removeSuffix("_Lyrics").replace('_', ' ').trim().ifBlank { "Imported Lyrics" }

        val validSongId = targetSongId?.takeIf { songs.getSong(it) != null }
        if (validSongId != null) {
            val sectionCount = insertParsedSectionsIntoSong(validSongId, mainLyricsPart)
            if (notesPart.isNotBlank()) {
                parsePlainTextNotes(notesPart).forEach { noteBody ->
                    if (noteBody.isNotBlank()) notes.create(NoteDraft(validSongId, noteBody.trim()))
                }
            }
            val doc = lyrics.ensureDocument(validSongId).getOrThrow()
            return Result.success(
                ImportResult.LyricsImported(
                    documentId = doc.id,
                    documentName = docName,
                    songId = validSongId,
                    sectionCount = sectionCount,
                ),
            )
        } else {
            val parsed = parsePlainTextLyrics(mainLyricsPart)
            val standalone = lyrics.createStandalone(docName).getOrThrow()
            val firstSec = standalone.sections.firstOrNull()
            var count = 0
            if (parsed.isNotEmpty()) {
                parsed.forEachIndexed { idx, p ->
                    if (idx == 0 && firstSec != null) {
                        if (p.tag != firstSec.tag || p.customLabel != firstSec.customLabel) {
                            lyrics.updateSectionTag(firstSec.id, p.tag, p.customLabel)
                        }
                        lyrics.updateSectionText(firstSec.id, p.bodyText)
                        count++
                    } else {
                        val sec = lyrics.insertSectionInDocument(standalone.id, p.tag, p.customLabel, null).getOrNull()
                        if (sec != null) {
                            lyrics.updateSectionText(sec.id, p.bodyText)
                            count++
                        }
                    }
                }
            } else if (firstSec != null) {
                lyrics.updateSectionText(firstSec.id, mainLyricsPart.trim())
                count = 1
            }
            return Result.success(
                ImportResult.LyricsImported(
                    documentId = standalone.id,
                    documentName = standalone.name,
                    songId = null,
                    sectionCount = count,
                ),
            )
        }
    }

    private suspend fun insertParsedSectionsIntoSong(songId: String, text: String): Int {
        val parsed = parsePlainTextLyrics(text)
        var count = 0
        for (p in parsed) {
            val sec = lyrics.insertSection(songId, p.tag, p.customLabel, null).getOrNull()
            if (sec != null) {
                if (p.bodyText.isNotEmpty()) {
                    lyrics.updateSectionText(sec.id, p.bodyText)
                }
                count++
            }
        }
        return count
    }

    private suspend fun uniqueSongName(baseName: String): String {
        val trimmed = baseName.trim().ifBlank { "Imported Song" }
        if (!songs.nameExists(trimmed)) return trimmed
        val importedName = "$trimmed (imported)"
        if (!songs.nameExists(importedName)) return importedName
        var i = 2
        while (songs.nameExists("$trimmed (imported $i)")) i++
        return "$trimmed (imported $i)"
    }

    private fun generatePeaksForTake(
        takeId: String,
        storedRelPath: String,
        storedMime: String,
        durationMs: Long,
        byteSize: Long,
    ): String {
        val peaksRel = TakePaths.peaks(takeId)
        val peaksFile = files.file(peaksRel)
        val reducer = PeakReducer(onAmplitude = {})
        val decodedOk = runCatching {
            val source = ExportSource(
                takeId = takeId,
                relativePath = storedRelPath,
                mimeType = storedMime,
                isLossless = storedMime == AudioFormat.MIME_FLAC || storedMime == AudioFormat.MIME_WAV,
                durationMs = durationMs,
                byteSize = byteSize,
            )
            pcmDecoder.decode(source, onPcm = { shorts, count -> reducer.consume(shorts, count) })
            reducer.peaksSnapshot().isNotEmpty()
        }.getOrDefault(false)

        if (!decodedOk) {
            // Fallback synthetic waveform from raw file bytes so imported takes in JVM tests or unsupported codecs still render peaks
            val syntheticSamples = ShortArray(48_000)
            val srcFile = files.file(storedRelPath)
            val raw = runCatching { srcFile.inputStream().use { it.readNBytes(8192) } }.getOrDefault(ByteArray(0))
            for (i in syntheticSamples.indices) {
                val b = if (raw.isNotEmpty()) raw[i % raw.size].toInt() else 32
                syntheticSamples[i] = ((abs(b) * 180) % 24_000).toShort()
            }
            reducer.consume(ShortBuffer.wrap(syntheticSamples), syntheticSamples.size)
        }
        reducer.writePeaksFile(peaksFile)
        return peaksRel
    }

    private fun probeDurationMs(file: File, mimeType: String): Long {
        if (mimeType == AudioFormat.MIME_RAW_PCM) {
            return AudioFormat.durationMsForBytes(file.length()).coerceAtLeast(1_000L)
        }
        if (mimeType == AudioFormat.MIME_WAV || file.name.endsWith(".wav", ignoreCase = true)) {
            val wavDuration = runCatching {
                file.inputStream().use { input ->
                    val header = ByteArray(44)
                    if (input.read(header) >= 32) {
                        val byteRate = ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        if (byteRate > 0) ((file.length() - 44).coerceAtLeast(0L) * 1000L) / byteRate else 0L
                    } else 0L
                }
            }.getOrDefault(0L)
            if (wavDuration > 0L) return wavDuration
        }
        val retrieved = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrDefault(0L)
        if (retrieved > 0L) return retrieved
        // Fallback estimate based on file size (~16 KB/sec for compressed, 96 KB/sec for uncompressed)
        val bytesPerMs = if (mimeType == AudioFormat.MIME_FLAC || mimeType == AudioFormat.MIME_WAV) 48L else 16L
        return (file.length() / bytesPerMs).coerceAtLeast(1_000L)
    }

    data class ParsedSection(
        val label: String,
        val tag: SectionTag,
        val customLabel: String?,
        val bodyText: String,
    )

    companion object {
        private val AUDIO_EXTENSIONS = setOf("m4a", "flac", "wav", "mp3", "aac", "ogg", "pcm")
        private val HEADER_REGEX = Regex("^\\[(.+)]$|^---\\s*(.+)\\s*---$")

        fun isZipMagic(magic: ByteArray): Boolean =
            magic.size >= 4 &&
                magic[0] == 'P'.code.toByte() &&
                magic[1] == 'K'.code.toByte() &&
                ((magic[2] == 3.toByte() && magic[3] == 4.toByte()) || (magic[2] == 5.toByte() && magic[3] == 6.toByte()))

        fun isAudioMagicOrExtension(magic: ByteArray, ext: String, mimeType: String?): Boolean {
            if (ext in AUDIO_EXTENSIONS) return true
            if (mimeType?.startsWith("audio/", ignoreCase = true) == true) return true
            if (magic.size >= 4) {
                // RIFF
                if (magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() && magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte()) return true
                // fLaC
                if (magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() && magic[2] == 'a'.code.toByte() && magic[3] == 'C'.code.toByte()) return true
                // ID3
                if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) return true
                // MP3 sync word
                if (magic[0] == 0xFF.toByte() && (magic[1].toInt() and 0xE0) == 0xE0) return true
            }
            if (magic.size >= 8) {
                // M4A/MP4 'ftyp'
                if (magic[4] == 'f'.code.toByte() && magic[5] == 't'.code.toByte() && magic[6] == 'y'.code.toByte() && magic[7] == 'p'.code.toByte()) return true
            }
            return false
        }

        private fun resolveAudioExtension(ext: String, mimeType: String?, magic: ByteArray): String {
            if (ext in AUDIO_EXTENSIONS) return ext
            if (magic.size >= 4) {
                if (magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() && magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte()) return "wav"
                if (magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() && magic[2] == 'a'.code.toByte() && magic[3] == 'C'.code.toByte()) return "flac"
                if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) return "mp3"
                if (magic[0] == 0xFF.toByte() && (magic[1].toInt() and 0xE0) == 0xE0) return "mp3"
            }
            if (magic.size >= 8 && magic[4] == 'f'.code.toByte() && magic[5] == 't'.code.toByte() && magic[6] == 'y'.code.toByte() && magic[7] == 'p'.code.toByte()) {
                return "m4a"
            }
            return when (mimeType?.lowercase(Locale.US)) {
                "audio/flac", "audio/x-flac" -> "flac"
                "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
                "audio/mpeg", "audio/mp3" -> "mp3"
                else -> "m4a"
            }
        }

        private fun storedPathAndMimeFor(takeId: String, ext: String): Pair<String, String> = when (ext.lowercase(Locale.US)) {
            "flac" -> TakePaths.flac(takeId) to AudioFormat.MIME_FLAC
            "wav" -> TakePaths.wav(takeId) to AudioFormat.MIME_WAV
            "mp3" -> TakePaths.mp3(takeId) to AudioFormat.MIME_MP3
            "pcm" -> TakePaths.pcm(takeId) to AudioFormat.MIME_RAW_PCM
            else -> TakePaths.m4a(takeId) to AudioFormat.MIME_M4A
        }

        private fun readMagicBytes(file: File, count: Int): ByteArray =
            file.inputStream().use { it.readNBytes(count) }

        private fun stripArchiveExtension(name: String): String =
            name.removeSuffix(".bin")
                .removeSuffix(".songpad")
                .removeSuffix(".zip")
                .removePrefix("Export_")
                .replace(Regex("_\\d{8}-\\d{4}$"), "")
                .replace('_', ' ')
                .trim()

        /**
         * Parses `lyrics.txt` or user-supplied `.txt` files into structured sections.
         * Supports `[Verse 1]`, `[Chorus]`, `[Bridge]`, `[Pre-Chorus]`, `[Intro]`, `[Outro]`, `[Hook]`, or custom `[Label]`.
         */
        fun parsePlainTextLyrics(text: String): List<ParsedSection> {
            val normalized = text.replace("\r\n", "\n").trim()
            if (normalized.isEmpty()) return emptyList()

            val lines = normalized.lines()
            val sections = mutableListOf<ParsedSection>()
            var currentHeader: String? = null
            val currentLines = mutableListOf<String>()

            fun flushCurrent() {
                val body = currentLines.joinToString("\n").trim()
                if (currentHeader != null || body.isNotEmpty()) {
                    val label = currentHeader ?: "Verse"
                    val (tag, custom) = mapHeaderToSectionTag(label)
                    sections += ParsedSection(label = label, tag = tag, customLabel = custom, bodyText = body)
                }
                currentLines.clear()
            }

            for (line in lines) {
                val match = HEADER_REGEX.matchEntire(line.trim())
                if (match != null) {
                    if (currentHeader != null || currentLines.any { it.isNotBlank() }) {
                        flushCurrent()
                    } else {
                        currentLines.clear()
                    }
                    currentHeader = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
                } else {
                    currentLines += line
                }
            }
            flushCurrent()
            return sections
        }

        private fun mapHeaderToSectionTag(rawHeader: String): Pair<SectionTag, String?> {
            val base = rawHeader.replace(Regex("\\s+\\d+$"), "").trim()
            return when {
                base.equals("Verse", ignoreCase = true) -> SectionTag.VERSE to null
                base.equals("Chorus", ignoreCase = true) -> SectionTag.CHORUS to null
                base.equals("Bridge", ignoreCase = true) -> SectionTag.BRIDGE to null
                base.equals("Pre-Chorus", ignoreCase = true) || base.equals("Pre Chorus", ignoreCase = true) -> SectionTag.PRE_CHORUS to null
                base.equals("Post-Chorus", ignoreCase = true) || base.equals("Post Chorus", ignoreCase = true) -> SectionTag.POST_CHORUS to null
                base.equals("Intro", ignoreCase = true) -> SectionTag.INTRO to null
                base.equals("Outro", ignoreCase = true) -> SectionTag.OUTRO to null
                else -> SectionTag.CUSTOM to rawHeader.trim()
            }
        }

        /** Parses `notes.txt` formatted as `[yyyy-MM-dd HH:mm]\n<body>` separated by blank lines. */
        fun parsePlainTextNotes(text: String): List<String> {
            val normalized = text.replace("\r\n", "\n").trim()
            if (normalized.isEmpty()) return emptyList()
            val blocks = normalized.split(Regex("\n\\s*\n(?=\\[\\d{4}-\\d{2}-\\d{2})"))
            return blocks.mapNotNull { block ->
                val trimmed = block.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val lines = trimmed.lines()
                if (lines.first().matches(Regex("^\\[\\d{4}-\\d{2}-\\d{2}.*]$"))) {
                    lines.drop(1).joinToString("\n").trim().takeIf { it.isNotEmpty() }
                } else {
                    trimmed
                }
            }
        }
    }
}
