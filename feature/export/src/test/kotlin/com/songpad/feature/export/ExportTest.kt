package com.songpad.feature.export

import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.testing.FakeExportSourceResolver
import com.songpad.core.data.testing.FakeLyricsRepository
import com.songpad.core.data.testing.FakeNoteRepository
import com.songpad.core.data.testing.FakeSettingsRepository
import com.songpad.core.data.testing.FakeSongRepository
import com.songpad.core.data.testing.FakeTakeRepository
import com.songpad.core.data.testing.InMemoryStore
import com.songpad.core.model.error.ExportError
import com.songpad.core.model.export.ExportAudioFormat
import com.songpad.core.model.export.ExportMode
import com.songpad.core.model.export.ExportRequest
import com.songpad.core.model.export.ExportState
import com.songpad.core.model.logging.Logger
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.NoteDraft
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.CapturedAudio
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeDraft
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.take.TakeType
import com.songpad.core.audio.encode.PcmDecoder
import com.songpad.core.model.export.ImportResult
import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.take.TakeFileStore
import com.songpad.feature.export.bundle.ExportPlanner
import com.songpad.feature.export.bundle.SongBundleManifest
import com.songpad.feature.export.bundle.SongBundleWriter
import com.songpad.feature.export.bundle.SongPadImporterImpl
import com.songpad.feature.export.work.ExportJobRunner
import com.songpad.feature.export.work.ExportJobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ExportTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryStore()
    private val songs = FakeSongRepository(store)
    private val takes = FakeTakeRepository(store)
    private val lyrics = FakeLyricsRepository(store)
    private val notes = FakeNoteRepository(store)
    private val resolver = FakeExportSourceResolver(store)
    private val settings = FakeSettingsRepository()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private suspend fun seed(): String {
        val song = songs.create("Neon Rain").getOrThrow()
        takes.commit(TakeDraft("t1", "Verse idea", TakeType.VOICE, song.id, null, CapturedAudio("t1", TakePaths.pcm("t1"), 48_000L * 10, AudioFormat.bytesForFrames(48_000L * 10), null), null, QualityMode.STANDARD)).getOrThrow()
        takes.markTranscoded("t1", TakePaths.m4a("t1"), AudioFormat.MIME_M4A, 160_000)
        takes.commit(TakeDraft("t2", "Piano sketch", TakeType.PIANO, song.id, null, CapturedAudio("t2", TakePaths.pcm("t2"), 48_000L * 5, AudioFormat.bytesForFrames(48_000L * 5), null), com.songpad.core.model.take.MidiAsset("m2", "t2", TakePaths.midi("t2"), 480, 300, 12), QualityMode.LOSSLESS)).getOrThrow()
        takes.markTranscoded("t2", TakePaths.flac("t2"), AudioFormat.MIME_FLAC, 900_000)
        val s = lyrics.insertSection(song.id, SectionTag.CHORUS, null, null).getOrThrow()
        lyrics.updateSectionText(s.id, "neon rain on the window")
        notes.create(NoteDraft(song.id, "Try @Chorus louder"))
        return song.id
    }

    @Test
    fun `planner - summary states source formats honestly, resolves direct files vs zip bundles, and scopes single takes`() = runTest {
        val songId = seed()
        val detail = songs.observeSong(songId).first()!!
        val planner = ExportPlanner(resolver)

        val all = planner.plan(detail, ExportRequest(songId, ExportMode.EVERYTHING, ExportAudioFormat.WAV))
        assertThat(all.summary.takeCount).isEqualTo(2)
        assertThat(all.summary.losslessTakeCount).isEqualTo(1)
        assertThat(all.summary.hasLossyTakes).isTrue()
        assertThat(all.summary.midiCount).isEqualTo(1)
        assertThat(all.summary.sectionCount).isEqualTo(1)
        assertThat(all.summary.noteCount).isEqualTo(1)
        assertThat(all.summary.outputMimeType).isEqualTo("application/zip")
        assertThat(all.summary.outputFileName).endsWith(".zip")
        assertThat(all.takes.map { it.audioEntryPath }).containsExactly("audio/01 Verse idea.wav", "audio/02 Piano sketch.wav").inOrder()
        assertThat(all.takes[1].midiEntryPath).isEqualTo("midi/02 Piano sketch.mid")

        val stored = planner.plan(detail, ExportRequest(songId, ExportMode.AUDIO_TAKES, ExportAudioFormat.STORED))
        assertThat(stored.takes.map { it.audioEntryPath }).containsExactly("audio/01 Verse idea.m4a", "audio/02 Piano sketch.flac").inOrder()
        assertThat(stored.summary.sectionCount).isEqualTo(0)
        assertThat(stored.summary.midiCount).isEqualTo(0)
        assertThat(stored.summary.outputMimeType).isEqualTo("application/zip")

        // Single-take export produces a direct audio file (.wav, .mp3, .m4a) instead of a bundle archive
        val singleWav = planner.plan(detail, ExportRequest(songId, ExportMode.AUDIO_TAKES, ExportAudioFormat.WAV, takeId = "t1"))
        assertThat(singleWav.takes).hasSize(1)
        assertThat(singleWav.summary.selectedTakeName).isEqualTo("Verse idea")
        assertThat(singleWav.summary.outputMimeType).isEqualTo("audio/wav")
        assertThat(singleWav.summary.outputFileName).endsWith(".wav")

        // Lyrics-only export produces a direct .txt file with text/plain MIME type
        val lyricsOnly = planner.plan(detail, ExportRequest(songId, ExportMode.LYRICS_ONLY, ExportAudioFormat.STORED))
        assertThat(lyricsOnly.takes).isEmpty()
        assertThat(lyricsOnly.summary.sectionCount).isEqualTo(1)
        assertThat(lyricsOnly.summary.outputMimeType).isEqualTo("text/plain")
        assertThat(lyricsOnly.summary.outputFileName).endsWith(".txt")

        val manifest = planner.manifest(all, 1_700_000_000_000, "test")
        assertThat(manifest.schemaVersion).isEqualTo(SongBundleManifest.SCHEMA_VERSION)
        assertThat(manifest.takes.map { it.sourceFormat }).containsExactly("m4a", "flac").inOrder()
        assertThat(manifest.sections.single().bodyText).isEqualTo("neon rain on the window")
        assertThat(manifest.notes.single().bodyText).isEqualTo("Try @Chorus louder")
        val json = SongBundleManifest.json.encodeToString(SongBundleManifest.serializer(), manifest)
        assertThat(json).contains("\"schemaVersion\": 1")
        assertThat(SongBundleManifest.json.decodeFromString(SongBundleManifest.serializer(), json)).isEqualTo(manifest)
        assertThat(planner.lyricsText(all)).isEqualTo("[Chorus]\nneon rain on the window")
        assertThat(planner.notesText(all)).contains("Try @Chorus louder")
    }

    @Test
    fun `bundle writer - stored media, deflated text, streamed entries, abort deletes nothing it did not write`() {
        val media = tmp.newFile("a.m4a").apply { writeBytes(ByteArray(50_000) { (it % 251).toByte() }) }
        val out = ByteArrayOutputStream()
        val entries = listOf(
            SongBundleWriter.Entry.Text("song.json", "{\"schemaVersion\":1}"),
            SongBundleWriter.Entry.FromFile("audio/01 a.m4a", media, stored = true),
            SongBundleWriter.Entry.Streamed("audio/02 b.wav") { o, p -> o.write(ByteArray(1000)); p(1f) },
        )
        val seen = mutableListOf<String>()
        SongBundleWriter().write(entries, out) { _, _, path, _ -> seen += path; true }
        assertThat(seen).containsAtLeast("song.json", "audio/01 a.m4a", "audio/02 b.wav")

        val read = mutableMapOf<String, Pair<Int, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                read[e.name] = e.method to zip.readBytes()
            }
        }
        assertThat(read.keys).containsExactly("song.json", "audio/01 a.m4a", "audio/02 b.wav")
        assertThat(read["audio/01 a.m4a"]!!.first).isEqualTo(ZipEntry.STORED)
        assertThat(read["audio/01 a.m4a"]!!.second).isEqualTo(media.readBytes())
        assertThat(read["song.json"]!!.first).isEqualTo(ZipEntry.DEFLATED)
        assertThat(read["audio/02 b.wav"]!!.second).hasLength(1000)

        val aborted = ByteArrayOutputStream()
        val thrown = runCatching { SongBundleWriter().write(entries, aborted) { i, _, _, _ -> i < 1 } }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SongBundleWriter.AbortedException::class.java)

        assertThat(SongBundleWriter.bundleFileName("Neon/Rain: v2", 0)).matches("Export_Neon_Rain_ v2_\\d{8}-\\d{4}\\.zip")
        assertThat(SongBundleWriter.lyricsFileName("Neon/Rain: v2", 0)).matches("Lyrics_Neon_Rain_ v2_\\d{8}-\\d{4}\\.txt")
        assertThat(SongBundleWriter.singleTakeFileName("Neon Rain", "Verse 1", "mp3", 0)).matches("Neon Rain - Verse 1_\\d{8}-\\d{4}\\.mp3")
    }

    @Test
    fun `importer - round-trip import of legacy songpad/bin bundle, standalone wav take, and txt lyrics`() = runTest {
        val rootDir = tmp.newFolder("files")
        val fileStore = TakeFileStore(rootDir)
        var nextId = 100
        val ids = IdGenerator { "id-${nextId++}" }
        val importer = SongPadImporterImpl(
            songs = songs,
            takes = takes,
            lyrics = lyrics,
            notes = notes,
            files = fileStore,
            pcmDecoder = PcmDecoder(fileStore),
            ids = ids,
        )

        // 1. Build a legacy .songpad.bin archive (where sections in song.json had no bodyText, only lyrics.txt + notes.txt + audio)
        val fakeM4aBytes = ByteArray(4096) { (it % 127).toByte() }
        val legacyManifestJson = """
            {
              "schemaVersion": 1,
              "generator": "SongPad 0.1.0",
              "exportedAtMillis": 1700000000000,
              "mode": "EVERYTHING",
              "song": {
                "id": "orig-song",
                "name": "Recovered Midnight",
                "status": "ONGOING",
                "favorite": true,
                "createdAtMillis": 1700000000000,
                "updatedAtMillis": 1700000000000
              },
              "takes": [
                {
                  "id": "orig-t1",
                  "name": "Chorus vocal",
                  "type": "VOICE",
                  "durationMs": 8000,
                  "createdAtMillis": 1700000000000,
                  "sourceFormat": "m4a",
                  "exportFormat": "m4a",
                  "audioFile": "audio/01 Chorus vocal.m4a",
                  "midiFile": null
                }
              ],
              "sections": [
                { "id": "s1", "tag": "VERSE", "label": "Verse 1", "sortOrder": 0 },
                { "id": "s2", "tag": "CHORUS", "label": "Chorus", "sortOrder": 1 }
              ],
              "notes": [
                { "id": "n1", "createdAtMillis": 1700000000000, "sectionRefId": null, "takeRefId": null }
              ]
            }
        """.trimIndent()
        val legacyLyricsTxt = "[Verse 1]\nWalking down the empty street\n\n[Chorus]\nSing it back to me"
        val legacyNotesTxt = "[2023-11-14 22:13]\nCapo on 3rd fret"

        val bundleBytes = ByteArrayOutputStream().also { out ->
            SongBundleWriter().write(
                listOf(
                    SongBundleWriter.Entry.Text("song.json", legacyManifestJson),
                    SongBundleWriter.Entry.Text("lyrics.txt", legacyLyricsTxt),
                    SongBundleWriter.Entry.Text("notes.txt", legacyNotesTxt),
                    SongBundleWriter.Entry.Streamed("audio/01 Chorus vocal.m4a") { o, p -> o.write(fakeM4aBytes); p(1f) },
                ),
                out,
            ) { _, _, _, _ -> true }
        }.toByteArray()

        // Even if Android named the legacy export `.songpad.bin` with application/octet-stream, importer restores it!
        val bundleRes = importer.importFromStream(
            displayName = "Export_Recovered Midnight_20250101-1200.songpad.bin",
            mimeType = "application/octet-stream",
            targetSongId = null,
            openStream = { ByteArrayInputStream(bundleBytes) },
        ).getOrThrow() as ImportResult.SongBundleImported

        assertThat(bundleRes.songName).isEqualTo("Recovered Midnight")
        assertThat(bundleRes.takeCount).isEqualTo(1)
        assertThat(bundleRes.sectionCount).isEqualTo(2)
        assertThat(bundleRes.noteCount).isEqualTo(1)

        val importedDetail = songs.observeSong(bundleRes.songId).first()!!
        assertThat(importedDetail.takes.single().name).isEqualTo("Chorus vocal")
        assertThat(importedDetail.lyrics!!.sections.map { it.bodyText })
            .containsExactly("Walking down the empty street", "Sing it back to me").inOrder()
        assertThat(importedDetail.notes.single().bodyText).isEqualTo("Capo on 3rd fret")

        // 2. Import standalone .txt lyrics into the imported song
        val extraLyrics = "[Bridge]\nHold the line until dawn"
        val lyricsRes = importer.importFromStream(
            displayName = "Bridge idea.txt",
            mimeType = "text/plain",
            targetSongId = bundleRes.songId,
            openStream = { ByteArrayInputStream(extraLyrics.toByteArray()) },
        ).getOrThrow() as ImportResult.LyricsImported
        assertThat(lyricsRes.sectionCount).isEqualTo(1)
        assertThat(songs.observeSong(bundleRes.songId).first()!!.lyrics!!.sections.map { it.bodyText })
            .contains("Hold the line until dawn")

        // 3. Import standalone .wav audio into the imported song
        val wavOut = ByteArrayOutputStream()
        com.songpad.core.audio.encode.WavEncoder().use { enc ->
            val pcmFile = tmp.newFile("sample.pcm").apply { writeBytes(ByteArray(9600)) } // 100ms @ 48kHz mono s16le
            enc.encode(pcmFile, 48_000, wavOut) {}
        }
        val wavRes = importer.importFromStream(
            displayName = "Guitar Hook.wav",
            mimeType = "audio/wav",
            targetSongId = bundleRes.songId,
            openStream = { ByteArrayInputStream(wavOut.toByteArray()) },
        ).getOrThrow() as ImportResult.TakeImported
        assertThat(wavRes.takeName).isEqualTo("Guitar Hook")
        assertThat(songs.observeSong(bundleRes.songId).first()!!.takes.map { it.name })
            .contains("Guitar Hook")
    }

    private class ScriptedRunner(val statuses: List<ExportJobStatus>) : ExportJobRunner {
        var cancelled = 0
        override fun start(request: ExportRequest): Flow<ExportJobStatus> = flow { statuses.forEach { emit(it) } }
        override fun cancel(request: ExportRequest) { cancelled++ }
    }

    @Test
    fun `view model - selecting, preparing with progress, success, and the nothing-to-export guard`() = runTest(dispatcher) {
        val songId = seed()
        val runner = ScriptedRunner(listOf(ExportJobStatus.Queued, ExportJobStatus.Running(0.5f, "01 Verse idea.m4a"), ExportJobStatus.Done("/tmp/x.zip", "x.zip", 12345, "application/zip")))
        val vm = ExportViewModel(songs, resolver, settings, runner, Logger.Noop)
        vm.bind(songId); advanceUntilIdle()
        val selecting = vm.state.value as ExportState.Selecting
        assertThat(selecting.summary!!.takeCount).isEqualTo(2)

        vm.setMode(ExportMode.LYRICS_ONLY); advanceUntilIdle()
        assertThat((vm.state.value as ExportState.Selecting).summary!!.takeCount).isEqualTo(0)
        vm.setMode(ExportMode.EVERYTHING); vm.setFormat(ExportAudioFormat.WAV); advanceUntilIdle()
        assertThat((vm.state.value as ExportState.Selecting).request.audioFormat).isEqualTo(ExportAudioFormat.WAV)

        vm.start(); advanceUntilIdle()
        val success = vm.state.value as ExportState.Success
        assertThat(success.bundleName).isEqualTo("x.zip")
        assertThat(success.mimeType).isEqualTo("application/zip")
        assertThat(vm.bundlePath).isEqualTo("/tmp/x.zip")

        vm.reset(); advanceUntilIdle()
        assertThat((vm.state.value as ExportState.Selecting).request.audioFormat).isEqualTo(ExportAudioFormat.WAV)

        // An empty song: nothing to export is a Failure state, and the runner is never started.
        val empty = songs.create("Empty").getOrThrow()
        val vm2 = ExportViewModel(songs, resolver, settings, ScriptedRunner(emptyList()), Logger.Noop)
        vm2.bind(empty.id); advanceUntilIdle()
        vm2.start(); advanceUntilIdle()
        assertThat((vm2.state.value as ExportState.Failure).error).isEqualTo(ExportError.NothingToExport)
    }

    @Test
    fun `view model - cancel`() = runTest(dispatcher) {
        val songId = seed()
        val runner = ScriptedRunner(listOf(ExportJobStatus.Queued, ExportJobStatus.Running(0.1f, null)))
        val vm = ExportViewModel(songs, resolver, settings, runner, Logger.Noop)
        vm.bind(songId); advanceUntilIdle()
        vm.start(); advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(ExportState.Preparing::class.java)
        vm.cancel(); advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(ExportState.Cancelled)
        assertThat(runner.cancelled).isEqualTo(1)
    }
}
