package com.songpad.feature.song.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.audio.recovery.AudioRecoveryManager
import com.songpad.core.model.audio.PlaybackController
import com.songpad.core.model.audio.PlaybackSource
import com.songpad.core.model.audio.PlaybackState
import com.songpad.core.model.data.LyricsRepository
import com.songpad.core.model.data.SearchEngine
import com.songpad.core.model.data.SearchResults
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.error.domainError
import com.songpad.core.model.export.ImportResult
import com.songpad.core.model.export.SongPadImporter
import com.songpad.core.model.logging.Logger
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.song.SongStatusTransitions
import com.songpad.core.model.state.QualityDisclosureState
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.Take
import com.songpad.core.model.usecase.AssignLyricsToSongUseCase
import com.songpad.core.model.usecase.AssignTakeToSongUseCase
import com.songpad.core.model.usecase.CreateSongFromLyricsUseCase
import com.songpad.core.model.usecase.CreateSongFromTakeUseCase
import com.songpad.core.model.usecase.RecoverTakeUseCase
import com.songpad.feature.song.detail.NowPlaying
import com.songpad.feature.song.detail.PeaksLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songs: SongRepository,
    private val takes: TakeRepository,
    private val settings: SettingsRepository,
    private val createSongFromTake: CreateSongFromTakeUseCase,
    private val assignTakeToSong: AssignTakeToSongUseCase,
    private val recoveryManager: AudioRecoveryManager,
    private val recoverTake: RecoverTakeUseCase,
    private val playback: PlaybackController,
    private val peaksLoader: PeaksLoader,
    private val logger: Logger,
    private val searchEngine: SearchEngine,
    private val lyrics: LyricsRepository,
    private val createSongFromLyrics: CreateSongFromLyricsUseCase,
    private val assignLyricsToSong: AssignLyricsToSongUseCase,
    private val importer: SongPadImporter? = null,
) : ViewModel() {

    private val filter = MutableStateFlow(SongFilter.ALL)
    private val peaksCache = HashMap<String, FloatArray>()
    private val peaksVersion = MutableStateFlow(0)
    private val playbackErrorMsg = MutableStateFlow<String?>(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<SearchResults?> = _searchQuery
        .debounce(150L)
        .flatMapLatest { q ->
            val trimmed = q.trim()
            if (trimmed.isEmpty()) {
                flowOf(null)
            } else {
                searchEngine.searchGlobal(trimmed).map<SearchResults, SearchResults?> { it }.catch { emit(SearchResults.EMPTY) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sheet = MutableStateFlow<LibrarySheet>(LibrarySheet.None)
    val sheet: StateFlow<LibrarySheet> = _sheet.asStateFlow()

    private val _errors = MutableSharedFlow<DomainError>(extraBufferCapacity = 4)
    val errors: SharedFlow<DomainError> = _errors.asSharedFlow()

    private val disclosure = combine(settings.hasSavedFirstTake, settings.firstSaveDisclosureDismissed, settings.qualityMode) { saved, dismissed, mode ->
        if (saved && !dismissed && mode == QualityMode.STANDARD) QualityDisclosureState.FirstSaveDisclosure(mode.label()) else QualityDisclosureState.Hidden
    }

    val recoveryState: StateFlow<AudioRecoveryManager.RecoveryState> = recoveryManager.state

    val state: StateFlow<LibraryState> = combine(
        combine(
            filter.flatMapLatest { f ->
                combine(songs.observeSongs(f), takes.observeUnassigned(), lyrics.observeUnassigned()) { s, t, l ->
                    FilterSlice(f, s, t, l)
                }
            },
            playback.state, peaksVersion,
        ) { slice, p, _ -> slice to p },
        disclosure,
        playbackErrorMsg,
    ) { (slice, p), disc, errMsg ->
        val nowPlaying = nowPlayingFrom(slice.unassignedTakes, p)
        val resolvedError = errMsg ?: (p as? PlaybackState.Error)?.let { errState ->
            val failedTake = slice.unassignedTakes.firstOrNull { it.id == errState.source?.takeId }
            if (failedTake != null) {
                "Playback failed for \"${failedTake.name}\": ${errState.error.message}"
            } else null
        }
        when {
            slice.filter != SongFilter.ALL -> LibraryState.Filtered(slice.filter, slice.songs, slice.unassignedTakes, disc, nowPlaying, slice.unassignedLyrics, resolvedError)
            slice.songs.isEmpty() && slice.unassignedTakes.isEmpty() && slice.unassignedLyrics.isEmpty() -> LibraryState.EmptyLibrary(disc, resolvedError)
            else -> LibraryState.Populated(slice.songs, slice.unassignedTakes, disc, nowPlaying, slice.unassignedLyrics, resolvedError)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState.Loading())

    private data class FilterSlice(
        val filter: SongFilter,
        val songs: List<Song>,
        val unassignedTakes: List<Take>,
        val unassignedLyrics: List<LyricsDocument>,
    )

    fun setFilter(f: SongFilter) { filter.value = f }
    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }

    // ── Playback of unassigned takes (§7.6 unified playback — Library is their only playback context) ──
    fun play(take: Take) {
        val audio = take.audio ?: run {
            playbackErrorMsg.value = "Playback failed for \"${take.name}\": audio file is missing."
            return
        }
        if (!take.isPlayable) {
            playbackErrorMsg.value = "Playback failed for \"${take.name}\": take is not available."
            return
        }
        playbackErrorMsg.value = null
        val current = playback.state.value
        if (current.sourceOrNull?.takeId == take.id && current is PlaybackState.Paused) { playback.play(current.source); return }
        loadPeaks(take)
        playback.play(PlaybackSource.CommittedTake(take.id, audio.relativePath, audio.mimeType))
    }

    fun togglePlayPause(take: Take) {
        val current = playback.state.value
        if (current is PlaybackState.Playing && current.source.takeId == take.id) playback.pause() else play(take)
    }

    fun seekTo(fraction: Float) {
        val s = playback.state.value
        val duration = when (s) { is PlaybackState.Playing -> s.durationMs; is PlaybackState.Paused -> s.durationMs; else -> return }
        playback.seekTo((fraction.coerceIn(0f, 1f) * duration).toLong())
    }

    fun dismissPlayer() = playback.stop()

    fun dismissPlaybackError() {
        playbackErrorMsg.value = null
        if (playback.state.value is PlaybackState.Error) {
            playback.stop()
        }
    }

    private fun nowPlayingFrom(unassigned: List<Take>, p: PlaybackState): NowPlaying? {
        val source = p.sourceOrNull as? PlaybackSource.CommittedTake ?: return null
        val take = unassigned.firstOrNull { it.id == source.takeId } ?: return null
        val peaks = peaksCache[take.id] ?: FloatArray(0)
        return when (p) {
            is PlaybackState.Playing -> NowPlaying(take, true, p.positionMs, p.durationMs.orTake(take), peaks)
            is PlaybackState.Paused -> NowPlaying(take, false, p.positionMs, p.durationMs.orTake(take), peaks)
            is PlaybackState.Loading -> NowPlaying(take, true, 0, take.durationMs, peaks)
            is PlaybackState.Ended -> NowPlaying(take, false, 0, p.durationMs.orTake(take), peaks)
            is PlaybackState.Error -> {
                playbackErrorMsg.value = "Playback failed for \"${take.name}\": ${p.error.message}"
                null
            }
            PlaybackState.Idle -> null
        }
    }

    private fun Long.orTake(take: Take) = if (this > 0) this else take.durationMs

    private fun loadPeaks(take: Take) {
        val path = take.audio?.peaksPath ?: return
        val cached = peaksCache[take.id]
        if (cached != null && cached.isNotEmpty() && cached.any { it > 0.0005f }) return
        viewModelScope.launch {
            val peaks = withContext(Dispatchers.IO) { runCatching { peaksLoader.load(path) }.getOrDefault(FloatArray(0)) }
            peaksCache[take.id] = peaks
            peaksVersion.value++
        }
    }

    fun toggleFavorite(songId: String) = viewModelScope.launch { songs.toggleFavorite(songId).report() }

    fun dismissDisclosure() = viewModelScope.launch { settings.dismissFirstSaveDisclosure() }

    // ── Song options sheet actions ──
    fun openSongOptions(song: Song) { _sheet.value = LibrarySheet.SongOptions(song) }
    fun openRenameSong(song: Song) { _sheet.value = LibrarySheet.RenameSong(song) }
    fun requestDeleteSong(song: Song) { _sheet.value = LibrarySheet.ConfirmDeleteSong(song) }

    fun renameSong(songId: String, name: String) = viewModelScope.launch {
        songs.rename(songId, name.trim()).report()
        closeSheet()
    }

    fun toggleFinished(song: Song) = viewModelScope.launch {
        songs.setStatus(song.id, SongStatusTransitions.toggleFinished(song.status)).report()
        closeSheet()
    }

    fun toggleArchived(song: Song) = viewModelScope.launch {
        songs.setStatus(song.id, SongStatusTransitions.toggleArchived(song.status)).report()
        closeSheet()
    }

    fun deleteSong(songId: String) = viewModelScope.launch {
        songs.softDelete(songId).report()
        closeSheet()
    }

    // ── Unassigned take sheet actions ──
    fun openAssignSheet(take: Take) = viewModelScope.launch {
        val candidates = songs.observeSongs(SongFilter.ALL).first()
        _sheet.value = LibrarySheet.AssignTake(take, candidates)
    }

    fun openNewSongForTake(take: Take) { _sheet.value = LibrarySheet.NewSongForTake(take) }
    fun requestDeleteTake(take: Take) { _sheet.value = LibrarySheet.ConfirmDeleteTake(take) }
    fun closeSheet() {
        flushStandaloneLyricsEdits()
        _sheet.value = LibrarySheet.None
    }

    fun assignTake(takeId: String, songId: String) = viewModelScope.launch {
        stopIfPlaying(takeId)
        assignTakeToSong(takeId, songId).report(); closeSheet()
    }

    fun createSongForTake(name: String, takeId: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        stopIfPlaying(takeId)
        createSongFromTake(name, takeId).report().onSuccess { onCreated(it.id) }; closeSheet()
    }

    /** §9.2 two-phase deletion, step 1: release the player before the row is soft-deleted. */
    fun deleteTake(takeId: String) = viewModelScope.launch { stopIfPlaying(takeId); takes.softDelete(takeId).report(); closeSheet() }

    private fun stopIfPlaying(takeId: String) { if (playback.state.value.sourceOrNull?.takeId == takeId) playback.stop() }

    // ── Unassigned standalone lyrics sheet & editor actions ──
    private var standaloneLyricsAutosaveJob: Job? = null

    init {
        viewModelScope.launch {
            lyrics?.flushDrafts()
        }
    }

    fun observeStandaloneLyrics(documentId: String): Flow<LyricsDocument?> =
        lyrics?.observeDocumentById(documentId) ?: flowOf(null)

    fun openStandaloneLyrics(documentId: String) {
        _sheet.value = LibrarySheet.EditStandaloneLyrics(documentId)
    }

    fun openAssignLyricsSheet(doc: LyricsDocument) = viewModelScope.launch {
        lyrics?.flushDrafts()
        val candidates = songs.observeSongs(SongFilter.ALL).first()
        _sheet.value = LibrarySheet.AssignLyrics(doc, candidates)
    }

    fun openNewSongForLyrics(doc: LyricsDocument) {
        flushStandaloneLyricsEdits()
        _sheet.value = LibrarySheet.NewSongForLyrics(doc)
    }

    fun openRenameStandaloneLyrics(doc: LyricsDocument) {
        flushStandaloneLyricsEdits()
        _sheet.value = LibrarySheet.RenameStandaloneLyrics(doc)
    }

    fun requestDeleteStandaloneLyrics(doc: LyricsDocument) {
        _sheet.value = LibrarySheet.ConfirmDeleteStandaloneLyrics(doc)
    }

    fun assignLyrics(documentId: String, songId: String) = viewModelScope.launch {
        lyrics?.flushDrafts()
        assignLyricsToSong?.invoke(documentId, songId)?.report()
        closeSheet()
    }

    fun createSongForLyrics(name: String, documentId: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        lyrics?.flushDrafts()
        createSongFromLyrics?.invoke(name, documentId)?.report()?.onSuccess { onCreated(it.id) }
        closeSheet()
    }

    fun renameStandaloneLyrics(documentId: String, name: String) = viewModelScope.launch {
        lyrics?.renameDocument(documentId, name.trim())?.report()
        _sheet.value = LibrarySheet.EditStandaloneLyrics(documentId)
    }

    fun deleteStandaloneLyrics(documentId: String) = viewModelScope.launch {
        lyrics?.deleteDocument(documentId)?.report()
        closeSheet()
    }

    /**
     * Immediately persists [text] to local draft state on every keystroke so no lyrics are lost if
     * the app process is interrupted, then commits to the database after a brief debounce.
     */
    fun editStandaloneSection(sectionId: String, text: String) {
        val repo = lyrics ?: return
        repo.saveSectionDraft(sectionId, text)
        standaloneLyricsAutosaveJob?.cancel()
        standaloneLyricsAutosaveJob = viewModelScope.launch {
            delay(250L)
            repo.updateSectionText(sectionId, text).report()
        }
    }

    fun flushStandaloneLyricsEdits() {
        val repo = lyrics ?: return
        standaloneLyricsAutosaveJob?.cancel()
        standaloneLyricsAutosaveJob = null
        viewModelScope.launch {
            repo.flushDrafts().report()
        }
    }

    fun insertStandaloneSection(documentId: String, tag: SectionTag = SectionTag.VERSE, customLabel: String? = null) = viewModelScope.launch {
        lyrics?.flushDrafts()
        lyrics?.insertSectionInDocument(documentId, tag, customLabel, null)?.report()
    }

    fun retagStandaloneSection(sectionId: String, tag: SectionTag, customLabel: String? = null) = viewModelScope.launch {
        lyrics?.flushDrafts()
        lyrics?.updateSectionTag(sectionId, tag, customLabel)?.report()
    }

    fun deleteStandaloneSection(sectionId: String) = viewModelScope.launch {
        lyrics?.deleteSection(sectionId)?.report()
    }

    // ── recovery banner (§9.4 step 5) ──

    fun keepRecovered() = viewModelScope.launch {
        val prompt = recoveryManager.state.value as? AudioRecoveryManager.RecoveryState.Prompt ?: return@launch
        recoveryManager.set(AudioRecoveryManager.RecoveryState.Restoring)
        val kept = mutableListOf<String>()
        prompt.orphans.forEach { orphan ->
            if (orphan.isNoise) recoverTake.discard(orphan)
            else recoverTake.keep(orphan).onSuccess { kept += it.id }.onFailure { logger.error(TAG, it.domainError) }
        }
        recoveryManager.set(AudioRecoveryManager.RecoveryState.Restored(kept))
    }

    fun requestDiscardRecovered() {
        (recoveryManager.state.value as? AudioRecoveryManager.RecoveryState.Prompt)?.let {
            recoveryManager.set(AudioRecoveryManager.RecoveryState.DiscardConfirm(it.orphans))
        }
    }

    fun cancelDiscardRecovered() {
        (recoveryManager.state.value as? AudioRecoveryManager.RecoveryState.DiscardConfirm)?.let {
            recoveryManager.set(AudioRecoveryManager.RecoveryState.Prompt(it.orphans))
        }
    }

    fun confirmDiscardRecovered() = viewModelScope.launch {
        val confirm = recoveryManager.state.value as? AudioRecoveryManager.RecoveryState.DiscardConfirm ?: return@launch
        confirm.orphans.forEach { recoverTake.discard(it).onFailure { t -> logger.error(TAG, t.domainError) } }
        recoveryManager.clear()
    }

    fun dismissRecovered() { recoveryManager.clear() }

    // ── Import (bundles, standalone audio takes, .txt lyrics) ──
    fun openImportSheet() {
        _sheet.value = LibrarySheet.Import()
    }

    fun importFile(displayName: String, mimeType: String?, openStream: () -> InputStream?) = viewModelScope.launch {
        val imp = importer ?: return@launch
        _sheet.value = LibrarySheet.Import(isBusy = true, statusTitle = "Importing…", statusBody = displayName)
        val result = imp.importFromStream(displayName = displayName, mimeType = mimeType, targetSongId = null, openStream = openStream)
        result.fold(
            onSuccess = { imported ->
                _sheet.value = when (imported) {
                    is ImportResult.SongBundleImported -> LibrarySheet.Import(
                        isBusy = false,
                        statusTitle = "Imported \"${imported.songName}\"",
                        statusBody = buildString {
                            append("${imported.takeCount} take${if (imported.takeCount == 1) "" else "s"}")
                            if (imported.sectionCount > 0) append(" · ${imported.sectionCount} lyric section${if (imported.sectionCount == 1) "" else "s"}")
                            if (imported.noteCount > 0) append(" · ${imported.noteCount} note${if (imported.noteCount == 1) "" else "s"}")
                        },
                        importedSongId = imported.songId,
                    )
                    is ImportResult.TakeImported -> LibrarySheet.Import(
                        isBusy = false,
                        statusTitle = "Imported take \"${imported.takeName}\"",
                        statusBody = "Saved to Unfiled takes at the top of your library.",
                    )
                    is ImportResult.LyricsImported -> LibrarySheet.Import(
                        isBusy = false,
                        statusTitle = "Imported lyrics \"${imported.documentName}\"",
                        statusBody = "${imported.sectionCount} section${if (imported.sectionCount == 1) "" else "s"} saved to Unfiled lyrics.",
                    )
                }
            },
            onFailure = { err ->
                _sheet.value = LibrarySheet.Import(
                    isBusy = false,
                    statusTitle = "Import failed",
                    statusBody = err.message ?: "Could not read \"$displayName\".",
                    isError = true,
                )
            },
        )
    }

    private fun <T> Result<T>.report(): Result<T> = onFailure { t -> t.domainError.let { logger.error(TAG, it); _errors.tryEmit(it) } }

    companion object {
        private const val TAG = "LibraryViewModel"
        fun QualityMode.label() = when (this) { QualityMode.STANDARD -> "Standard"; QualityMode.LOSSLESS -> "Lossless" }
    }
}
