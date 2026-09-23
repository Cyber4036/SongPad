package com.songpad.feature.song

import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.testing.FakeAudioRecorderEngine
import com.songpad.core.data.testing.FakeLyricsRepository
import com.songpad.core.data.testing.FakeMidiCaptureEngine
import com.songpad.core.data.testing.FakeNoteRepository
import com.songpad.core.data.testing.FakePlaybackController
import com.songpad.core.data.testing.FakeSettingsRepository
import com.songpad.core.data.testing.FakeSongRepository
import com.songpad.core.data.testing.FakeStorageProbe
import com.songpad.core.data.testing.FakeTakeRepository
import com.songpad.core.data.testing.FakeTranscodeScheduler
import com.songpad.core.data.testing.InMemoryStore
import com.songpad.core.model.audio.PlaybackSource
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.logging.Logger
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.state.QualityDisclosureState
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.CapturedAudio
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeDraft
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.AssignTakeToSongUseCase
import com.songpad.core.model.usecase.CreateSongFromTakeUseCase
import com.songpad.core.model.usecase.DiscardRecordingUseCase
import com.songpad.core.model.usecase.FinalizeTakeUseCase
import com.songpad.core.model.usecase.RenameTakeUseCase
import com.songpad.core.model.usecase.StartRecordingUseCase
import com.songpad.feature.song.detail.LyricsState
import com.songpad.feature.song.detail.NotesState
import com.songpad.feature.song.detail.SongDetailState
import com.songpad.feature.song.detail.SongDetailViewModel
import com.songpad.feature.song.detail.TakeFilter
import com.songpad.feature.song.detail.TakeListState
import com.songpad.feature.song.library.LibraryState
import com.songpad.feature.song.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryStore()
    private val songs = FakeSongRepository(store)
    private val takes = FakeTakeRepository(store)
    private val lyrics = FakeLyricsRepository(store)
    private val notes = FakeNoteRepository(store)
    private val settings = FakeSettingsRepository()
    private val playback = FakePlaybackController()
    private val engine = FakeAudioRecorderEngine()
    private val midi = FakeMidiCaptureEngine()
    private val transcodes = FakeTranscodeScheduler()
    private val storage = FakeStorageProbe()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun library() = LibraryViewModel(songs, takes, settings, CreateSongFromTakeUseCase(songs), AssignTakeToSongUseCase(takes, songs), Logger.Noop)
    private fun detail() = SongDetailViewModel(
        songs, takes, lyrics, notes, playback, RenameTakeUseCase(takes),
        StartRecordingUseCase(engine, settings, store.ids, store.clock, storage),
        FinalizeTakeUseCase(engine, midi, takes, settings, transcodes),
        DiscardRecordingUseCase(engine, midi),
        { FloatArray(0) }, Logger.Noop,
    )

    private suspend fun commitTake(songId: String?, name: String = "Take", newSong: String? = null, type: TakeType = TakeType.VOICE) = takes.commit(
        TakeDraft(store.ids.newId(), name, type, songId, newSong, CapturedAudio("x", TakePaths.pcm("x"), 48_000L * 10, AudioFormat.bytesForFrames(48_000L * 10), null), null, QualityMode.STANDARD),
    ).getOrThrow()

    private fun TestScope.subscribe(vararg flows: kotlinx.coroutines.flow.Flow<*>): List<Job> =
        flows.map { f -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.collect { } } }

    @Test
    fun `library - empty, then populated with unassigned takes and filter pills`() = runTest(dispatcher) {
        val vm = library()
        subscribe(vm.state)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(LibraryState.EmptyLibrary::class.java)

        val song = songs.create("Midnight Riff").getOrThrow()
        songs.setStatus(song.id, SongStatus.FINISHED)
        val orphan = commitTake(null, "Loose idea")
        advanceUntilIdle()
        val populated = vm.state.value as LibraryState.Populated
        assertThat(populated.songs.map { it.name }).containsExactly("Midnight Riff")
        assertThat(populated.unassignedTakes.map { it.id }).containsExactly(orphan.id)

        vm.setFilter(SongFilter.ONGOING); advanceUntilIdle()
        val filtered = vm.state.value as LibraryState.Filtered
        assertThat(filtered.songs).isEmpty()
        vm.setFilter(SongFilter.FINISHED); advanceUntilIdle()
        assertThat((vm.state.value as LibraryState.Filtered).songs.map { it.id }).containsExactly(song.id)
    }

    @Test
    fun `library - first-save disclosure shows once a take exists and hides on dismiss`() = runTest(dispatcher) {
        val vm = library()
        subscribe(vm.state)
        commitTake(null, "Idea"); advanceUntilIdle()
        assertThat(vm.state.value.disclosure).isEqualTo(QualityDisclosureState.Hidden)
        settings.markFirstTakeSaved(); advanceUntilIdle()
        assertThat(vm.state.value.disclosure).isEqualTo(QualityDisclosureState.FirstSaveDisclosure("Standard"))
        vm.dismissDisclosure(); advanceUntilIdle()
        assertThat(vm.state.value.disclosure).isEqualTo(QualityDisclosureState.Hidden)
    }

    @Test
    fun `library - add unassigned take to a new song and to an existing song`() = runTest(dispatcher) {
        val vm = library()
        subscribe(vm.state)
        val a = commitTake(null, "A"); val b = commitTake(null, "B")
        var created: String? = null
        vm.createSongForTake("Fresh", a.id) { created = it }; advanceUntilIdle()
        assertThat(created).isNotNull()
        assertThat(takes.getTake(a.id)!!.songId).isEqualTo(created)
        vm.assignTake(b.id, created!!); advanceUntilIdle()
        assertThat(takes.getTake(b.id)!!.songId).isEqualTo(created)
        assertThat((vm.state.value as LibraryState.Populated).unassignedTakes).isEmpty()
    }

    @Test
    fun `detail - takes tab states, play through the single controller, filter pills and delete releases player`() = runTest(dispatcher) {
        val song = songs.create("Song").getOrThrow()
        val vm = detail()
        subscribe(vm.state)
        vm.bind(song.id); advanceUntilIdle()
        var loaded = vm.state.value as SongDetailState.Loaded
        assertThat(loaded.takes).isInstanceOf(TakeListState.Empty::class.java)

        val take = commitTake(song.id, "Verse idea"); advanceUntilIdle()
        loaded = vm.state.value as SongDetailState.Loaded
        assertThat(loaded.takes).isInstanceOf(TakeListState.Transcoding::class.java) // TRANSCODING but playable
        takes.markTranscoded(take.id, TakePaths.m4a(take.id), AudioFormat.MIME_M4A, 1000); advanceUntilIdle()
        assertThat((vm.state.value as SongDetailState.Loaded).takes).isInstanceOf(TakeListState.Populated::class.java)

        val t = takes.getTake(take.id)!!
        vm.play(t); advanceUntilIdle()
        assertThat(playback.played.single()).isEqualTo(PlaybackSource.CommittedTake(t.id, TakePaths.m4a(t.id), AudioFormat.MIME_M4A))
        loaded = vm.state.value as SongDetailState.Loaded
        assertThat(loaded.takes).isInstanceOf(TakeListState.Playing::class.java)
        assertThat(loaded.nowPlaying!!.isPlaying).isTrue()

        vm.setTakeFilter(TakeFilter.PIANO); advanceUntilIdle()
        assertThat((vm.state.value as SongDetailState.Loaded).takes).isEqualTo(TakeListState.Empty(TakeFilter.PIANO))
        vm.setTakeFilter(TakeFilter.ALL)

        vm.deleteTake(t.id); advanceUntilIdle()
        assertThat(playback.state.value).isEqualTo(com.songpad.core.model.audio.PlaybackState.Idle)
        assertThat((vm.state.value as SongDetailState.Loaded).takes).isInstanceOf(TakeListState.Empty::class.java)
    }

    @Test
    fun `detail - lyrics empty, insert sections with numbering, autosave, notes validation`() = runTest(dispatcher) {
        val song = songs.create("Song").getOrThrow()
        val vm = detail()
        subscribe(vm.state)
        vm.bind(song.id); advanceUntilIdle()
        assertThat((vm.state.value as SongDetailState.Loaded).lyrics).isEqualTo(LyricsState.Empty)

        vm.beginInsertSection(null); advanceUntilIdle()
        assertThat((vm.state.value as SongDetailState.Loaded).lyrics).isInstanceOf(LyricsState.SectionInserting::class.java)
        vm.insertSection(SectionTag.VERSE); advanceUntilIdle()
        vm.insertSection(SectionTag.CHORUS); advanceUntilIdle()
        vm.insertSection(SectionTag.VERSE); advanceUntilIdle()
        val doc = (lyrics.observeDocument(song.id).first())!!
        assertThat(doc.sections.sortedBy { it.sortOrder }.map { it.displayLabel }).containsExactly("Verse 1", "Chorus", "Verse 2").inOrder()

        val verse = doc.sections.first()
        vm.editSection(verse.id, "Under the streetlight"); advanceUntilIdle()
        assertThat((vm.state.value as SongDetailState.Loaded).lyrics).isInstanceOf(LyricsState.Autosaved::class.java)
        assertThat(lyrics.observeDocument(song.id).first()!!.sections.first { it.id == verse.id }.bodyText).isEqualTo("Under the streetlight")

        vm.composeNote("   "); vm.saveNote(); advanceUntilIdle()
        assertThat((vm.state.value as SongDetailState.Loaded).notes).isInstanceOf(NotesState.InvalidEmpty::class.java)
        vm.composeNote("Try @Chorus louder"); vm.saveNote(); advanceUntilIdle()
        val ns = (vm.state.value as SongDetailState.Loaded).notes
        assertThat(ns).isInstanceOf(NotesState.Saved::class.java)
        val savedNote = ns.notes.single()
        assertThat(savedNote.bodyText).isEqualTo("Try @Chorus louder")
        assertThat(savedNote.sectionRefId).isEqualTo(doc.sections.first { it.displayLabel == "Chorus" }.id)
        assertThat((vm.state.value as SongDetailState.Loaded).activity).hasSize(2) // section edit + note, newest first

        // Editing only ever changes text — update() is bodyText-only, so mentioning a *different* section here
        // must NOT move sectionRefId; if it did, editing would be silently re-deriving refs it isn't supposed to.
        vm.composeNote(savedNote.bodyText, savedNote.id)
        vm.updateDraftText("Actually try @Verse 1 instead")
        vm.saveNote(); advanceUntilIdle()
        val edited = (vm.state.value as SongDetailState.Loaded).notes.notes.single()
        assertThat(edited.bodyText).isEqualTo("Actually try @Verse 1 instead")
        assertThat(edited.sectionRefId).isEqualTo(savedNote.sectionRefId) // still Chorus, not re-resolved to Verse 1
    }

    @Test
    fun `detail - notes attach photo and record voice`() = runTest(dispatcher) {
        val song = songs.create("Song").getOrThrow()
        val vm = detail()
        subscribe(vm.state)
        vm.bind(song.id); advanceUntilIdle()

        // "+" media attach: Composing -> MediaAttached, save carries mediaUri through.
        vm.composeNote("A photo of the chords")
        vm.attachMedia("content://media/1"); advanceUntilIdle()
        assertThat((vm.state.value as SongDetailState.Loaded).notes).isInstanceOf(NotesState.MediaAttached::class.java)
        vm.saveNote(); advanceUntilIdle()
        val withPhoto = (vm.state.value as SongDetailState.Loaded).notes.notes.single()
        assertThat(withPhoto.mediaUri).isEqualTo("content://media/1")

        // Microphone action: Composing -> VoiceRecording -> stop commits a VOICE take and auto-saves the note.
        vm.composeNote("Rough idea")
        vm.beginVoiceNote(); advanceUntilIdle()
        assertThat((vm.state.value as SongDetailState.Loaded).notes).isInstanceOf(NotesState.VoiceRecording::class.java)
        engine.simulateFrames(48_000L * 5)
        vm.stopVoiceNote(); advanceUntilIdle()
        val ns = (vm.state.value as SongDetailState.Loaded).notes
        assertThat(ns).isInstanceOf(NotesState.Saved::class.java)
        val voiceNote = ns.notes.first { it.bodyText == "Rough idea" }
        assertThat(voiceNote.voiceTakeId).isNotNull()
        assertThat(takes.observeForSong(song.id).first().any { it.id == voiceNote.voiceTakeId && it.type == TakeType.VOICE }).isTrue()

        // Cancelling a recording discards the take and returns to composing with the typed text intact.
        vm.composeNote("Discard me")
        vm.beginVoiceNote(); advanceUntilIdle()
        vm.cancelVoiceNote(); advanceUntilIdle()
        assertThat((vm.state.value as SongDetailState.Loaded).notes).isInstanceOf(NotesState.Composing::class.java)
    }

    @Test
    fun `detail - finish and archive are reversible and delete navigates to library`() = runTest(dispatcher) {
        val song = songs.create("Song").getOrThrow()
        val vm = detail()
        subscribe(vm.state)
        val navs = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.navigateToLibrary.collect { navs += it } }
        vm.bind(song.id); advanceUntilIdle()
        vm.toggleFinished(); advanceUntilIdle()
        assertThat(songs.getSong(song.id)!!.status).isEqualTo(SongStatus.FINISHED)
        vm.toggleFinished(); advanceUntilIdle()
        assertThat(songs.getSong(song.id)!!.status).isEqualTo(SongStatus.ONGOING)
        vm.toggleArchived(); advanceUntilIdle()
        assertThat(songs.getSong(song.id)!!.status).isEqualTo(SongStatus.ARCHIVED)
        vm.deleteSong(); advanceUntilIdle()
        assertThat(navs).hasSize(1)
        assertThat(vm.state.value).isEqualTo(SongDetailState.NotFound)
    }
}
