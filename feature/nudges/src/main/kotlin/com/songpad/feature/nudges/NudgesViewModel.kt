package com.songpad.feature.nudges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.model.data.NudgeRepository
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.nudge.Nudge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** §17 "Nudges": Empty · Active · Disabled · TargetUnavailable. */
sealed interface NudgesState {
    data object Loading : NudgesState
    data object Empty : NudgesState
    data class Active(val history: List<Nudge>, val notificationsGranted: Boolean) : NudgesState
    /** The toggle is off; history stays visible so nothing is lost. */
    data class Disabled(val history: List<Nudge>) : NudgesState
    /** The user tapped a nudge whose Song/Take is gone (§11.3) — the screen offers the library instead. */
    data class TargetUnavailable(val history: List<Nudge>, val nudge: Nudge) : NudgesState
}

sealed interface NudgeNavigation {
    data class Song(val songId: String) : NudgeNavigation
    data class Take(val takeId: String) : NudgeNavigation
    data object Library : NudgeNavigation
    /** §11.3: POST_NOTIFICATIONS is requested in context — when the user enables nudges. */
    data object RequestNotificationPermission : NudgeNavigation
}

@HiltViewModel
class NudgesViewModel @Inject constructor(
    private val nudges: NudgeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val unavailable = MutableSharedFlow<Nudge?>(replay = 1).apply { tryEmit(null) }
    private val permissionGranted = MutableSharedFlow<Boolean>(replay = 1).apply { tryEmit(true) }

    private val _navigation = MutableSharedFlow<NudgeNavigation>(extraBufferCapacity = 4)
    val navigation: SharedFlow<NudgeNavigation> = _navigation.asSharedFlow()

    val state: StateFlow<NudgesState> = combine(nudges.observeHistory(), settings.nudgesEnabled, unavailable, permissionGranted) { history, enabled, missing, granted ->
        when {
            missing != null -> NudgesState.TargetUnavailable(history, missing)
            !enabled -> NudgesState.Disabled(history)
            history.isEmpty() -> NudgesState.Empty
            else -> NudgesState.Active(history, granted)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NudgesState.Loading)

    fun setNotificationPermission(granted: Boolean) { permissionGranted.tryEmit(granted) }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setNudgesEnabled(enabled)
        if (enabled) _navigation.tryEmit(NudgeNavigation.RequestNotificationPermission)
    }

    fun open(nudge: Nudge) = viewModelScope.launch {
        nudges.markRead(nudge.id)
        val songId = nudge.targetSongId
        val takeId = nudge.targetTakeId
        val exists = nudges.targetExists(songId, takeId)
        when {
            !exists -> unavailable.tryEmit(nudge)
            songId != null -> _navigation.tryEmit(NudgeNavigation.Song(songId))
            takeId != null -> _navigation.tryEmit(NudgeNavigation.Take(takeId))
            else -> _navigation.tryEmit(NudgeNavigation.Library)
        }
    }

    fun dismissUnavailable(goToLibrary: Boolean) {
        unavailable.tryEmit(null)
        if (goToLibrary) _navigation.tryEmit(NudgeNavigation.Library)
    }
}
