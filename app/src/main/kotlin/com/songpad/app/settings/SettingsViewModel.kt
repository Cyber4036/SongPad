package com.songpad.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeFileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val qualityMode: QualityMode = QualityMode.STANDARD,
    val themeVariant: String? = null,
    val nudgesEnabled: Boolean = true,
    val storageUsedBytes: Long = 0,
    val takeDirCount: Int = 0,
)

/** Phase 0 §16 Settings: Appearance · Permissions · Storage & Data · Export · About. The §4.3 quality toggle lives here. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val files: TakeFileStore,
) : ViewModel() {

    private val storage = MutableStateFlow(0L to 0)

    val state: StateFlow<SettingsUiState> = combine(settings.qualityMode, settings.themeVariant, settings.nudgesEnabled, storage) { q, t, n, (bytes, dirs) ->
        SettingsUiState(q, t, n, bytes, dirs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _showLicenses = MutableStateFlow(false)
    val showLicenses: StateFlow<Boolean> = _showLicenses.asStateFlow()

    init { refreshStorage() }

    fun setQualityMode(mode: QualityMode) = viewModelScope.launch { settings.setQualityMode(mode) }
    fun setThemeVariant(name: String?) = viewModelScope.launch { settings.setThemeVariant(name) }
    fun setNudgesEnabled(enabled: Boolean) = viewModelScope.launch { settings.setNudgesEnabled(enabled) }
    fun setShowLicenses(show: Boolean) { _showLicenses.value = show }

    fun refreshStorage() = viewModelScope.launch {
        storage.value = withContext(Dispatchers.IO) {
            val dirs = files.listTakeDirs()
            dirs.sumOf { it.sizeRecursively() } to dirs.size
        }
    }

    private fun File.sizeRecursively(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
