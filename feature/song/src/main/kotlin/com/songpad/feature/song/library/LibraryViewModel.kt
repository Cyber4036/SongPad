package com.songpad.feature.song.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.audio.recovery.AudioRecoveryManager
import com.songpad.core.model.audio.PlaybackController
import com.songpad.core.model.audio.PlaybackSource
import com.songpad.core.model.audio.PlaybackState
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.error.domainError
import com.songpad.core.model.logging.Logger
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.state.QualityDisclosureState
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.Take
import com.songpad.core.model.usecase.AssignTakeToSongUseCase
import com.songpad.core.model.usecase.CreateSongFromTakeUseCase
import com.songpad.core.model.usecase.RecoverTakeUseCase
import com.songpad.feature.song.detail.NowPlaying
import com.songpad.feature.song.detail.PeaksLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
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
) : ViewModel() {

    private val filter = MutableStateFlow(SongFilter.ALL)
    private val peaksCache = HashMap<String, FloatArray>()
    private val peaksVersion = MutableStateFlow(0)

    private val _sheet = MutableStateFlow<LibrarySheet>(LibrarySheet.None)
    val sheet: StateFlow<LibrarySheet> = _sheet.asStateFlow()

    private val _errors = MutableSharedFlow<DomainError>(extraBufferCapacity = 4)
    val errors: SharedFlow<DomainError> = _errors.asSharedFlow()

    /**
     * §4.4 surface 1: the one-time, dismissible first-save disclosure — shown after the first take exists and
     * until dismissed. §21.3 #1 final decision: only ever shown for Standard — a user who already chose
     * Lossless has nothing to be told to switch to.
     */
    private val disclosure = combine(settings.hasSavedFirstTake, settings.firstSaveDisclosureDismissed, settings.qualityMode) { saved, dismissed, mode ->
        if (saved && !dismissed && mode == QualityMode.STANDARD) QualityDisclosureState.FirstSaveDisclosure(mode.label()) else QualityDisclosureState.Hidden
    }

    /**
     * §9.4 / §21.3 #3 final decision: a non-blocking banner at the top of the Library, never on the Record
     * screen. Detection itself already runs once on app launch ([AudioRecoveryManager.scanOnce] from
     * `SongPadApplication`); this ViewModel only renders and drives Keep/Discard.
     */
    val recoveryState: StateFlow<AudioRecoveryManager.RecoveryState> = recoveryManager.state

    val state: StateFlow<LibraryState> = combine(
        combine(
            filter.flatMapLatest { f -> combine(songs.observeSongs(f), takes.observeUnassigned()) { s, t -> Triple(f, s, t) } },
            playback.state, peaksVersion,
        ) { (f, s, t), p, _ -> Quad(f, s, t, p) },
        disclosure,
    ) { (f, songList, unassigned, p), disc ->
        val nowPlaying = nowPlayingFrom(unassigned, p)
        when {
            f != SongFilter.ALL -> LibraryState.Filtered(f, songList, unassigned, disc, nowPlaying)
            songList.isEmpty() && unassigned.isEmpty() -> LibraryState.EmptyLibrary(disc)
            else -> LibraryState.Populated(songList, unassigned, disc, nowPlaying)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState.Loading())

    private data class Quad(val filter: SongFilter, val songs: List<Song>, val unassigned: List<Take>, val playback: PlaybackState)

    fun setFilter(f: SongFilter) { filter.value = f }

    // ── Playback of unassigned takes (§7.6 unified playback — Library is their only playback context) ──
    fun play(take: Take) {
        val audio = take.audio ?: return
        if (!take.isPlayable) return
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

    private fun nowPlayingFrom(unassigned: List<Take>, p: PlaybackState): NowPlaying? {
        val source = p.sourceOrNull as? PlaybackSource.CommittedTake ?: return null
        val take = unassigned.firstOrNull { it.id == source.takeId } ?: return null
        val peaks = peaksCache[take.id] ?: FloatArray(0)
        return when (p) {
            is PlaybackState.Playing -> NowPlaying(take, true, p.positionMs, p.durationMs.orTake(take), peaks)
            is PlaybackState.Paused -> NowPlaying(take, false, p.positionMs, p.durationMs.orTake(take), peaks)
            is PlaybackState.Loading -> NowPlaying(take, true, 0, take.durationMs, peaks)
            is PlaybackState.Ended -> NowPlaying(take, false, 0, p.durationMs.orTake(take), peaks)
            is PlaybackState.Error -> null
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

    fun toggleFavorite(songId: String) = viewModelScope.launch { songs.toggleFavorite(songId).report() }

    fun dismissDisclosure() = viewModelScope.launch { settings.dismissFirstSaveDisclosure() }

    fun openAssignSheet(take: Take) = viewModelScope.launch {
        val candidates = songs.observeSongs(SongFilter.ALL).first()
        _sheet.value = LibrarySheet.AssignTake(take, candidates)
    }

    fun openNewSongForTake(take: Take) { _sheet.value = LibrarySheet.NewSongForTake(take) }
    fun requestDeleteTake(take: Take) { _sheet.value = LibrarySheet.ConfirmDeleteTake(take) }
    fun closeSheet() { _sheet.value = LibrarySheet.None }

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

    /** A take leaving the unassigned list (assigned to a song, or deleted) can no longer be resolved by
     * [nowPlayingFrom], which would otherwise leave [playback] silently running with no ribbon to control it. */
    private fun stopIfPlaying(takeId: String) { if (playback.state.value.sourceOrNull?.takeId == takeId) playback.stop() }

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

    /** Discard asks for confirmation first (Phase 0 destructive-action rule) — this moves Prompt -> DiscardConfirm. */
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

    private fun <T> Result<T>.report(): Result<T> = onFailure { t -> t.domainError.let { logger.error(TAG, it); _errors.tryEmit(it) } }

    companion object {
        private const val TAG = "LibraryViewModel"
        fun QualityMode.label() = when (this) { QualityMode.STANDARD -> "Standard"; QualityMode.LOSSLESS -> "Lossless" }
    }
}
