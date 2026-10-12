package com.songpad.feature.song.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.model.audio.PlaybackController
import com.songpad.core.model.audio.PlaybackSource
import com.songpad.core.model.audio.PlaybackState
import com.songpad.core.model.data.LyricsRepository
import com.songpad.core.model.data.NoteRepository
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.error.domainError
import com.songpad.core.model.logging.Logger
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.NoteDraft
import com.songpad.core.model.note.NoteValidation
import com.songpad.core.model.song.SongDetail
import com.songpad.core.model.song.SongStatusTransitions
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.AssignTakeToSongUseCase
import com.songpad.core.model.usecase.CaptureSession
import com.songpad.core.model.usecase.DiscardRecordingUseCase
import com.songpad.core.model.usecase.FinalizeTakeUseCase
import com.songpad.core.model.usecase.RenameTakeUseCase
import com.songpad.core.model.usecase.StartRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Song Home / Lyrics / Takes / Notes (Phase 0 §15, §8, §9, §10). One reactive [SongDetail] in, typed states out.
 * Playback goes through the single app-wide [PlaybackController] (§7.6); this ViewModel only maps its state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SongDetailViewModel @Inject constructor(
    private val songs: SongRepository,
    private val takes: TakeRepository,
    private val lyricsRepo: LyricsRepository,
    private val notesRepo: NoteRepository,
    private val playback: PlaybackController,
    private val renameTakeUseCase: RenameTakeUseCase,
    private val startRecording: StartRecordingUseCase,
    private val finalizeTake: FinalizeTakeUseCase,
    private val discardRecording: DiscardRecordingUseCase,
    private val peaksLoader: PeaksLoader,
    private val logger: Logger,
    private val assignTakeToSong: AssignTakeToSongUseCase = AssignTakeToSongUseCase(takes, songs),
) : ViewModel() {

    private val songId = MutableStateFlow<String?>(null)
    private val tab = MutableStateFlow(SongDetailTab.HOME)
    private val takeFilter = MutableStateFlow(TakeFilter.ALL)
    private val lyricsUi = MutableStateFlow<LyricsUi>(LyricsUi.Idle)
    private val notesUi = MutableStateFlow<NotesUi>(NotesUi.Idle)

    /** True while a voice note is capturing — the screen must not be left then (see [SongDetailScreen]). */
    val recordingVoiceNote: StateFlow<Boolean> = notesUi.map { it is NotesUi.Recording }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val unavailable = MutableStateFlow<Pair<String, String>?>(null)
    private val peaksCache = HashMap<String, FloatArray>()
    private val peaksVersion = MutableStateFlow(0)

    private val _dialog = MutableStateFlow<SongDetailDialog>(SongDetailDialog.None)
    val dialog: StateFlow<SongDetailDialog> = _dialog.asStateFlow()

    private val _errors = MutableSharedFlow<DomainError>(extraBufferCapacity = 4)
    val errors: SharedFlow<DomainError> = _errors.asSharedFlow()

    private val _navigateToLibrary = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToLibrary: SharedFlow<Unit> = _navigateToLibrary.asSharedFlow()

    private sealed interface LyricsUi {
        data object Idle : LyricsUi
        data class Dirty(val sectionId: String) : LyricsUi
        data object Autosaved : LyricsUi
        data class Inserting(val atIndex: Int?) : LyricsUi
    }

    private sealed interface NotesUi {
        data object Idle : NotesUi
        /** [mediaUri] set means the published state is [NotesState.MediaAttached] instead of [NotesState.Composing]. */
        data class Composing(val text: String, val editingNoteId: String?, val mediaUri: String? = null) : NotesUi
        /** Only entered for a *new* note — attaching audio to a note already being edited isn't supported because
         * [NoteRepository.update] is bodyText-only (§15 frozen contract); the UI hides attach/voice while editing. */
        data class Recording(val text: String, val session: CaptureSession) : NotesUi
        data class Invalid(val text: String) : NotesUi
        data object Saved : NotesUi
    }

    /** `stateIn`, not a plain cold flow: [resolveMentions] needs a synchronous last-known read, not a fresh
     * re-subscribe-and-query per note save. */
    private val detail = songId.filterNotNull().flatMapLatest { songs.observeSong(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<SongDetailState> = combine(
        combine(detail, tab, takeFilter, playback.state, peaksVersion) { d, t, f, p, _ -> Quad(d, t, f, p) },
        lyricsUi, notesUi, unavailable,
    ) { (d, t, f, p), lu, nu, un ->
        if (d == null) SongDetailState.NotFound else build(d, t, f, p, lu, nu, un)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongDetailState.Loading)

    private data class Quad(val detail: SongDetail?, val tab: SongDetailTab, val filter: TakeFilter, val playback: PlaybackState)

    fun bind(id: String, initialTab: SongDetailTab = SongDetailTab.HOME) {
        if (songId.value == id) return
        songId.value = id
        tab.value = initialTab
    }

    fun selectTab(t: SongDetailTab) { tab.value = t }
    fun setTakeFilter(f: TakeFilter) { takeFilter.value = f }
    fun showDialog(d: SongDetailDialog) { _dialog.value = d }
    fun closeDialog() { _dialog.value = SongDetailDialog.None }

    fun openMoveTakeDialog(take: Take) {
        viewModelScope.launch {
            val currentId = songId.value
            val allSongs = songs.observeSongs(SongFilter.ALL).first().filter { it.id != currentId }
            _dialog.value = SongDetailDialog.MoveTake(take, allSongs)
        }
    }

    fun moveTake(takeId: String, targetSongId: String) {
        viewModelScope.launch {
            assignTakeToSong(takeId, targetSongId).report().onSuccess { closeDialog() }
        }
    }

    // ── Song ──
    fun toggleFinished() = mutateSong { songs.setStatus(it.song.id, SongStatusTransitions.toggleFinished(it.song.status)) }
    fun toggleArchived() = mutateSong { songs.setStatus(it.song.id, SongStatusTransitions.toggleArchived(it.song.status)) }
    fun toggleFavorite() = mutateSong { songs.toggleFavorite(it.song.id) }
    fun renameSong(name: String) = mutateSong { songs.rename(it.song.id, name.trim()) }.also { closeDialog() }
    fun deleteSong() = viewModelScope.launch {
        val id = songId.value ?: return@launch
        stopIfPlayingSong()
        songs.softDelete(id).report().onSuccess { _navigateToLibrary.tryEmit(Unit) }
        closeDialog()
    }

    // ── Takes ──
    fun play(take: Take) {
        val audio = take.audio ?: run { unavailable.value = take.id to "No audio file for this take."; return }
        if (!take.isPlayable) { unavailable.value = take.id to "This take isn't available."; return }
        unavailable.value = null
        val current = playback.state.value
        if (current.sourceOrNull?.takeId == take.id && current is PlaybackState.Paused) { playback.play(current.source); return }
        loadPeaks(take)
        playback.play(PlaybackSource.CommittedTake(take.id, audio.relativePath, audio.mimeType))
    }

    fun togglePlayPause(take: Take) {
        val current = playback.state.value
        if (current is PlaybackState.Playing && current.source.takeId == take.id) playback.pause() else play(take)
    }

    fun pause() = playback.pause()
    fun seekTo(fraction: Float) {
        val s = playback.state.value
        val duration = when (s) { is PlaybackState.Playing -> s.durationMs; is PlaybackState.Paused -> s.durationMs; else -> return }
        playback.seekTo((fraction.coerceIn(0f, 1f) * duration).toLong())
    }
    fun dismissPlayer() = playback.stop()

    fun renameTake(takeId: String, name: String) = viewModelScope.launch { renameTakeUseCase(takeId, name).report(); closeDialog() }

    /** §9.2 two-phase deletion, step 1: release the player before the row is soft-deleted. */
    fun deleteTake(takeId: String) = viewModelScope.launch {
        if (playback.state.value.sourceOrNull?.takeId == takeId) playback.stop()
        takes.softDelete(takeId).report()
        closeDialog()
    }

    // ── Lyrics (Phase 0 §8) ──
    fun beginInsertSection(atIndex: Int?) { lyricsUi.value = LyricsUi.Inserting(atIndex) }
    fun cancelInsertSection() { lyricsUi.value = LyricsUi.Idle }
    fun insertSection(tag: SectionTag, customLabel: String? = null) = viewModelScope.launch {
        val id = songId.value ?: return@launch
        val at = (lyricsUi.value as? LyricsUi.Inserting)?.atIndex
        lyricsRepo.insertSection(id, tag, customLabel, at).report()
        lyricsUi.value = LyricsUi.Idle
    }

    private var autosave: Job? = null
    /** Autosave with a short debounce; the state shows Editing until the write lands, then Autosaved. */
    fun editSection(sectionId: String, text: String) {
        lyricsUi.value = LyricsUi.Dirty(sectionId)
        autosave?.cancel()
        autosave = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            lyricsRepo.updateSectionText(sectionId, text).report().onSuccess { lyricsUi.value = LyricsUi.Autosaved }
        }
    }
    fun flushSectionEdit(sectionId: String, text: String) = viewModelScope.launch {
        autosave?.cancel()
        lyricsRepo.updateSectionText(sectionId, text).report().onSuccess { lyricsUi.value = LyricsUi.Autosaved }
    }
    fun moveSection(sectionId: String, toIndex: Int) = viewModelScope.launch { lyricsRepo.moveSection(sectionId, toIndex).report() }
    fun deleteSection(sectionId: String) = viewModelScope.launch { lyricsRepo.deleteSection(sectionId).report(); closeDialog() }

    // ── Notes (Phase 0 §10) ──
    /** Begins composing (blank) or editing (existing text) a note. Resets any attached media/recording. */
    fun composeNote(text: String, editingNoteId: String? = null) { notesUi.value = NotesUi.Composing(text, editingNoteId) }
    /** Text-field edits while composing/recording, preserving whatever else is attached. */
    fun updateDraftText(text: String) {
        notesUi.value = when (val ui = notesUi.value) {
            is NotesUi.Composing -> ui.copy(text = text)
            is NotesUi.Recording -> ui.copy(text = text)
            is NotesUi.Invalid, NotesUi.Idle, NotesUi.Saved -> NotesUi.Composing(text, null)
        }
    }
    fun cancelNote() { notesUi.value = NotesUi.Idle }

    /** Phase 0 §10 "+" action — photo picker only (no camera permission, per §13). New notes only. */
    fun attachMedia(uri: String) {
        val ui = notesUi.value as? NotesUi.Composing ?: return
        if (ui.editingNoteId != null) return
        notesUi.value = ui.copy(mediaUri = uri)
    }
    fun removeMedia() { (notesUi.value as? NotesUi.Composing)?.let { notesUi.value = it.copy(mediaUri = null) } }

    /** Phase 0 §10 "microphone action". New notes only — reuses the same capture use cases as the main
     * Record flow (§16), since a voice note is just a VOICE-type Take referenced by [Note.voiceTakeId]. */
    fun beginVoiceNote() = viewModelScope.launch {
        val id = songId.value ?: return@launch
        val ui = notesUi.value as? NotesUi.Composing ?: return@launch
        if (ui.editingNoteId != null) return@launch
        startRecording(TakeType.VOICE, id).report().onSuccess { session -> notesUi.value = NotesUi.Recording(ui.text, session) }
    }
    fun cancelVoiceNote() {
        val ui = notesUi.value as? NotesUi.Recording ?: return
        if (voiceNoteBusy) return
        voiceNoteBusy = true
        viewModelScope.launch {
            try {
                discardRecording().report()
                notesUi.value = NotesUi.Composing(ui.text, null)
            } finally { voiceNoteBusy = false }
        }
    }
    /** Stopping IS sending, chat-message style — there is no separate "voice attached, not yet saved" state
     * in §17's Notes hierarchy, so the recorded take commits straight into a saved Note. [notesUi] stays
     * Recording until the note is committed, so back stays swallowed (see [recordingVoiceNote]); the busy
     * flag, set synchronously, makes a repeated back or tap during the commit a no-op instead of finalizing
     * the same session twice. */
    fun stopVoiceNote() {
        val ui = notesUi.value as? NotesUi.Recording ?: return
        if (voiceNoteBusy) return
        voiceNoteBusy = true
        viewModelScope.launch {
            try {
                finalizeTake(ui.session, "Voice note", null).report()
                    .onSuccess { take -> createNote(ui.text, mediaUri = null, voiceTakeId = take.id) }
                    // Don't trap the user in Recording (back is swallowed there). The .pcm.tmp stays on disk,
                    // so the §9.4 recovery scan offers it back on next launch.
                    .onFailure { notesUi.value = NotesUi.Composing(ui.text, null) }
            } finally { voiceNoteBusy = false }
        }
    }
    private var voiceNoteBusy = false

    /** New note vs. editing an existing one are genuinely different operations, not the same call with some
     * fields zeroed out: [NoteRepository.update] is bodyText-only (§15 frozen contract), so there is no way to
     * carry mediaUri/voiceTakeId/refs through an edit — keeping them as separate functions makes that a type-level
     * fact instead of a value silently dropped on one branch. */
    fun saveNote() = viewModelScope.launch {
        val ui = notesUi.value as? NotesUi.Composing ?: return@launch
        val editingId = ui.editingNoteId
        if (editingId != null) updateNoteText(editingId, ui.text) else createNote(ui.text, ui.mediaUri, voiceTakeId = null)
    }
    fun deleteNote(noteId: String) = viewModelScope.launch { notesRepo.softDelete(noteId).report(); closeDialog() }

    private suspend fun createNote(text: String, mediaUri: String?, voiceTakeId: String?) {
        val id = songId.value ?: return
        val (sectionRefId, takeRefId) = resolveMentions(text)
        val draft = NoteDraft(songId = id, bodyText = text.trim(), voiceTakeId = voiceTakeId, mediaUri = mediaUri, sectionRefId = sectionRefId, takeRefId = takeRefId)
        NoteValidation.validate(draft)?.let { notesUi.value = NotesUi.Invalid(text); return }
        notesRepo.create(draft).report().onSuccess { notesUi.value = NotesUi.Saved }
    }

    /** No local pre-check here (unlike [createNote]): the repo validates against the *existing* note's own
     * voiceTakeId/mediaUri, which this function doesn't have — asking it to re-derive them would just be
     * `notesRepo.get` for no reason when the repo already does the right check on the real row. */
    private suspend fun updateNoteText(noteId: String, text: String) {
        notesRepo.update(noteId, text.trim()).report()
            .onSuccess { notesUi.value = NotesUi.Saved }
            .onFailure { notesUi.value = NotesUi.Invalid(text) }
    }

    /** First `@` mention that names a real Section wins the section ref; first that names a real Take wins the
     * take ref (independently — a note can reference one of each). Match is case-insensitive against
     * [Section.displayLabel] / [Take.name]; an unmatched mention is left as plain text, not an error — the
     * composer doesn't guess what the user meant. */
    private fun resolveMentions(text: String): Pair<String?, String?> {
        val d = detail.value ?: return null to null
        val tokens = NoteValidation.mentions(text)
        val sectionId = tokens.firstNotNullOfOrNull { token -> d.lyrics?.sections?.firstOrNull { it.displayLabel.equals(token, ignoreCase = true) } }?.id
        val takeId = tokens.firstNotNullOfOrNull { token -> d.takes.firstOrNull { it.name.equals(token, ignoreCase = true) } }?.id
        return sectionId to takeId
    }

    // ── internals ──

    private fun build(d: SongDetail, t: SongDetailTab, f: TakeFilter, p: PlaybackState, lu: LyricsUi, nu: NotesUi, un: Pair<String, String>?): SongDetailState.Loaded {
        val filtered = d.takes.filter { f.type == null || it.type == f.type }
        val nowPlaying = nowPlayingFrom(d, p)
        val transcoding = filtered.filter { it.status == TakeStatus.TRANSCODING }.map { it.id }.toSet()
        val takeState: TakeListState = when {
            un != null && filtered.any { it.id == un.first } -> TakeListState.Unavailable(f, filtered, un.first, un.second)
            filtered.isEmpty() -> TakeListState.Empty(f)
            transcoding.isNotEmpty() -> TakeListState.Transcoding(f, filtered, transcoding, nowPlaying)
            nowPlaying != null && filtered.any { it.id == nowPlaying.take.id } -> TakeListState.Playing(f, filtered, nowPlaying)
            else -> TakeListState.Populated(f, filtered)
        }
        val doc = d.lyrics
        val lyricsState: LyricsState = when (lu) {
            is LyricsUi.Inserting -> LyricsState.SectionInserting(doc, lu.atIndex)
            else -> if (doc == null || doc.sections.isEmpty()) LyricsState.Empty else when (lu) {
                is LyricsUi.Dirty -> LyricsState.Editing(doc, lu.sectionId)
                LyricsUi.Autosaved -> LyricsState.Autosaved(doc)
                else -> LyricsState.Editing(doc, null)
            }
        }
        val notesState: NotesState = when (nu) {
            is NotesUi.Composing -> if (nu.mediaUri != null) NotesState.MediaAttached(d.notes, nu.text, nu.mediaUri)
                else NotesState.Composing(d.notes, nu.text, nu.editingNoteId)
            is NotesUi.Recording -> NotesState.VoiceRecording(d.notes, nu.text)
            is NotesUi.Invalid -> NotesState.InvalidEmpty(d.notes, nu.text)
            NotesUi.Saved -> NotesState.Saved(d.notes)
            NotesUi.Idle -> if (d.notes.isEmpty()) NotesState.Empty() else NotesState.Saved(d.notes)
        }
        return SongDetailState.Loaded(d.song, t, d.recentActivity, takeState, lyricsState, notesState, nowPlaying)
    }

    private fun nowPlayingFrom(d: SongDetail, p: PlaybackState): NowPlaying? {
        val source = p.sourceOrNull as? PlaybackSource.CommittedTake ?: return null
        val take = d.takes.firstOrNull { it.id == source.takeId } ?: return null
        val peaks = peaksCache[take.id] ?: FloatArray(0)
        return when (p) {
            is PlaybackState.Playing -> NowPlaying(take, true, p.positionMs, p.durationMs.orTake(take), peaks)
            is PlaybackState.Paused -> NowPlaying(take, false, p.positionMs, p.durationMs.orTake(take), peaks)
            is PlaybackState.Loading -> NowPlaying(take, true, 0, take.durationMs, peaks)
            is PlaybackState.Ended -> NowPlaying(take, false, 0, p.durationMs.orTake(take), peaks)
            is PlaybackState.Error -> { unavailable.value = take.id to p.error.message; null }
            PlaybackState.Idle -> null
        }
    }

    private fun Long.orTake(take: Take) = if (this > 0) this else take.durationMs

    private fun loadPeaks(take: Take) {
        val path = take.audio?.peaksPath ?: return
        if (peaksCache.containsKey(take.id)) return
        viewModelScope.launch {
            val peaks = withContext(Dispatchers.IO) { runCatching { peaksLoader.load(path) }.getOrDefault(FloatArray(0)) }
            peaksCache[take.id] = peaks
            peaksVersion.value++
        }
    }

    private fun stopIfPlayingSong() {
        val playingId = playback.state.value.sourceOrNull?.takeId ?: return
        val loaded = state.value as? SongDetailState.Loaded ?: return
        if (loaded.takes.let { s -> (s as? TakeListState.Playing)?.takes ?: (s as? TakeListState.Transcoding)?.takes ?: (s as? TakeListState.Populated)?.takes }?.any { it.id == playingId } == true) playback.stop()
    }

    private fun mutateSong(block: suspend (SongDetailState.Loaded) -> Result<Unit>) = viewModelScope.launch {
        val loaded = state.value as? SongDetailState.Loaded ?: return@launch
        block(loaded).report()
    }

    private fun <T> Result<T>.report(): Result<T> = onFailure { t -> t.domainError.let { logger.error(TAG, it); _errors.tryEmit(it) } }

    companion object {
        private const val TAG = "SongDetailViewModel"
        const val AUTOSAVE_DEBOUNCE_MS = 400L
    }
}
