package com.songpad.core.data.testing

import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.take.QualityMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {
    private val _qualityMode = MutableStateFlow(QualityMode.STANDARD)
    override val qualityMode: Flow<QualityMode> = _qualityMode
    override suspend fun setQualityMode(mode: QualityMode) { _qualityMode.value = mode }

    private val _firstSave = MutableStateFlow(false)
    override val firstSaveDisclosureDismissed: Flow<Boolean> = _firstSave
    override suspend fun dismissFirstSaveDisclosure() { _firstSave.value = true }

    private val _exportSeen = MutableStateFlow(false)
    override val exportDisclosureSeen: Flow<Boolean> = _exportSeen
    override suspend fun markExportDisclosureSeen() { _exportSeen.value = true }

    private val _nudges = MutableStateFlow(true)
    override val nudgesEnabled: Flow<Boolean> = _nudges
    override suspend fun setNudgesEnabled(enabled: Boolean) { _nudges.value = enabled }

    private val _lastNudge = MutableStateFlow<Long?>(null)
    override val lastNudgeAtMillis: Flow<Long?> = _lastNudge
    override suspend fun setLastNudgeAt(millis: Long) { _lastNudge.value = millis }

    private val _theme = MutableStateFlow<String?>(null)
    override val themeVariant: Flow<String?> = _theme
    override suspend fun setThemeVariant(name: String?) { _theme.value = name }

    private val _firstTake = MutableStateFlow(false)
    override val hasSavedFirstTake: Flow<Boolean> = _firstTake
    override suspend fun markFirstTakeSaved() { _firstTake.value = true }

    val currentQualityMode: QualityMode get() = _qualityMode.value
    val firstTakeSaved: Boolean get() = _firstTake.value
}
