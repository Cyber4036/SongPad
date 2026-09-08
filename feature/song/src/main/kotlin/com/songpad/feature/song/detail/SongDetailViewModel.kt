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
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.usecase.RenameTakeUseCase
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
import kotlinx.coroutines.flow.flatMapLatest
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
    private val peaksLoader: PeaksLoader,
    private val logger: Logger,
) : ViewModel() {

    private val songId = MutableStateFlow<String?>(null)
    private val tab = MutableStateFlow(SongDetailTab.HOME)
    private val takeFilter = MutableStateFlow(TakeFilter.ALL)
    private val lyricsUi = MutableStateFlow<LyricsUi>(LyricsUi.Idle)
    private val notesUi = MutableStateFlow<NotesUi>(NotesUi.Idle)
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
        data class Composing(val text: String, val editingNoteId: String?) : NotesUi
        data class Invalid(val text: String) : NotesUi
        data object Saved : NotesUi
    }

    private val detail = songId.filterNotNull().flatMapLatest { songs.observeSong(it) }

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
    fun composeNote(text: String, editingNoteId: String? = null) { notesUi.value = NotesUi.Composing(text, editingNoteId) }
    fun cancelNote() { notesUi.value = NotesUi.Idle }
    fun saveNote() = viewModelScope.launch {
        val id = songId.value ?: return@launch
        val ui = notesUi.value as? NotesUi.Composing ?: return@launch
        val draft = NoteDraft(songId = id, bodyText = ui.text.trim())
        NoteValidation.validate(draft)?.let { notesUi.value = NotesUi.Invalid(ui.text); return@launch }
        val result = if (ui.editingNoteId != null) notesRepo.update(ui.editingNoteId, draft.bodyText).map { } else notesRepo.create(draft).map { }
        result.report().onSuccess { notesUi.value = NotesUi.Saved }
    }
    fun deleteNote(noteId: String) = viewModelScope.launch { notesRepo.softDelete(noteId).report(); closeDialog() }

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
            is NotesUi.Composing -> NotesState.Composing(d.notes, nu.text, nu.editingNoteId)
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
