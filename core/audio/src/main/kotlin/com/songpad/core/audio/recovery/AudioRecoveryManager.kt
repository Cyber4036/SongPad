package com.songpad.core.audio.recovery

import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.take.OrphanedTake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §9.4: runs once on app start, off the main thread. Finds RECORDING rows and rowless `.pcm.tmp` dirs,
 * discards noise, and exposes the §17 Recovery state for the capture feature to render as ONE non-blocking
 * prompt. Keep/Discard go through `RecoverTakeUseCase`; MIDI sidecars are converted by :core:midi (step 6).
 *
 * Open product question §21.3 #3 (prompt on launch vs silent library item) is NOT settled here: the
 * manager only produces the state; the feature decides the surface.
 */
@Singleton
class AudioRecoveryManager @Inject constructor(private val takes: TakeRepository) {

    sealed interface RecoveryState {
        data object None : RecoveryState
        data class Prompt(val orphans: List<OrphanedTake>) : RecoveryState
        data object Restoring : RecoveryState
        data class Restored(val takeIds: List<String>) : RecoveryState
        data class DiscardConfirm(val orphans: List<OrphanedTake>) : RecoveryState
    }

    private val _state = MutableStateFlow<RecoveryState>(RecoveryState.None)
    val state: StateFlow<RecoveryState> = _state.asStateFlow()
    private var scanned = false

    suspend fun scanOnce() {
        if (scanned) return
        scanned = true
        val orphans = withContext(Dispatchers.IO) {
            val found = takes.findOrphaned()
            found.filter { it.hasDbRow }.forEach { takes.markOrphaned(it.takeId) }
            found
        }
        _state.value = if (orphans.isEmpty()) RecoveryState.None else RecoveryState.Prompt(orphans)
    }

    fun set(state: RecoveryState) { _state.value = state }
    fun clear() { _state.value = RecoveryState.None }
}
