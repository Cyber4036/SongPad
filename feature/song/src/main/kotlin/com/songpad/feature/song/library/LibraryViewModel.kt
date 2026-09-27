package com.songpad.feature.song.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.audio.recovery.AudioRecoveryManager
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.error.domainError
import com.songpad.core.model.logging.Logger
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.state.QualityDisclosureState
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.Take
import com.songpad.core.model.usecase.AssignTakeToSongUseCase
import com.songpad.core.model.usecase.CreateSongFromTakeUseCase
import com.songpad.core.model.usecase.RecoverTakeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val logger: Logger,
) : ViewModel() {

    private val filter = MutableStateFlow(SongFilter.ALL)

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
        filter.flatMapLatest { f -> combine(songs.observeSongs(f), takes.observeUnassigned()) { s, t -> Triple(f, s, t) } },
        disclosure,
    ) { (f, songList, unassigned), disc ->
        when {
            f != SongFilter.ALL -> LibraryState.Filtered(f, songList, unassigned, disc)
            songList.isEmpty() && unassigned.isEmpty() -> LibraryState.EmptyLibrary(disc)
            else -> LibraryState.Populated(songList, unassigned, disc)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState.Loading())

    fun setFilter(f: SongFilter) { filter.value = f }

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
        assignTakeToSong(takeId, songId).report(); closeSheet()
    }

    fun createSongForTake(name: String, takeId: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        createSongFromTake(name, takeId).report().onSuccess { onCreated(it.id) }; closeSheet()
    }

    fun deleteTake(takeId: String) = viewModelScope.launch { takes.softDelete(takeId).report(); closeSheet() }

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
