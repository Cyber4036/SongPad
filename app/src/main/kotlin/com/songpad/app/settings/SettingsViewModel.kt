package com.songpad.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.export.ImportResult
import com.songpad.core.model.export.SongPadImporter
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
import java.io.InputStream
import javax.inject.Inject

data class SettingsUiState(
    val qualityMode: QualityMode = QualityMode.STANDARD,
    val themeVariant: String? = null,
    val nudgesEnabled: Boolean = true,
    val storageUsedBytes: Long = 0,
    val takeDirCount: Int = 0,
)

data class SettingsImportSheetState(
    val isBusy: Boolean = false,
    val statusTitle: String? = null,
    val statusBody: String? = null,
    val isError: Boolean = false,
    val importedSongId: String? = null,
)

/** Phase 0 §16 Settings: Appearance · Permissions · Storage & Data · Export & Import · About. The §4.3 quality toggle lives here. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val files: TakeFileStore,
    private val importer: SongPadImporter? = null,
) : ViewModel() {

    private val storage = MutableStateFlow(0L to 0)

    val state: StateFlow<SettingsUiState> = combine(settings.qualityMode, settings.themeVariant, settings.nudgesEnabled, storage) { q, t, n, (bytes, dirs) ->
        SettingsUiState(q, t, n, bytes, dirs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _showLicenses = MutableStateFlow(false)
    val showLicenses: StateFlow<Boolean> = _showLicenses.asStateFlow()

    private val _importSheet = MutableStateFlow<SettingsImportSheetState?>(null)
    val importSheet: StateFlow<SettingsImportSheetState?> = _importSheet.asStateFlow()

    init { refreshStorage() }

    fun setQualityMode(mode: QualityMode) = viewModelScope.launch { settings.setQualityMode(mode) }
    fun setThemeVariant(name: String?) = viewModelScope.launch { settings.setThemeVariant(name) }
    fun setNudgesEnabled(enabled: Boolean) = viewModelScope.launch { settings.setNudgesEnabled(enabled) }
    fun setShowLicenses(show: Boolean) { _showLicenses.value = show }
    fun openImportSheet() { _importSheet.value = SettingsImportSheetState() }
    fun closeImportSheet() { _importSheet.value = null }

    fun importFile(displayName: String, mimeType: String?, openStream: () -> InputStream?) = viewModelScope.launch {
        val imp = importer ?: return@launch
        _importSheet.value = SettingsImportSheetState(isBusy = true, statusTitle = "Importing…", statusBody = displayName)
        val result = imp.importFromStream(displayName = displayName, mimeType = mimeType, targetSongId = null, openStream = openStream)
        result.fold(
            onSuccess = { imported ->
                refreshStorage()
                _importSheet.value = when (imported) {
                    is ImportResult.SongBundleImported -> SettingsImportSheetState(
                        isBusy = false,
                        statusTitle = "Imported \"${imported.songName}\"",
                        statusBody = "${imported.takeCount} take${if (imported.takeCount == 1) "" else "s"} · ${imported.sectionCount} lyric section${if (imported.sectionCount == 1) "" else "s"}",
                        importedSongId = imported.songId,
                    )
                    is ImportResult.TakeImported -> SettingsImportSheetState(
                        isBusy = false,
                        statusTitle = "Imported take \"${imported.takeName}\"",
                        statusBody = "Saved to Unfiled takes in your Songs library.",
                    )
                    is ImportResult.LyricsImported -> SettingsImportSheetState(
                        isBusy = false,
                        statusTitle = "Imported lyrics \"${imported.documentName}\"",
                        statusBody = "${imported.sectionCount} section${if (imported.sectionCount == 1) "" else "s"} saved to Unfiled lyrics.",
                    )
                }
            },
            onFailure = { err ->
                _importSheet.value = SettingsImportSheetState(
                    isBusy = false,
                    statusTitle = "Import failed",
                    statusBody = err.message ?: "Could not read \"$displayName\".",
                    isError = true,
                )
            },
        )
    }

    fun refreshStorage() = viewModelScope.launch {
        storage.value = withContext(Dispatchers.IO) {
            val dirs = files.listTakeDirs()
            dirs.sumOf { it.sizeRecursively() } to dirs.size
        }
    }

    private fun File.sizeRecursively(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
