package com.songpad.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.take.QualityMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val store: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val quality = stringPreferencesKey("quality_mode")
        val firstSaveDisclosure = booleanPreferencesKey("first_save_disclosure_dismissed")
        val exportDisclosure = booleanPreferencesKey("export_disclosure_seen")
        val nudges = booleanPreferencesKey("nudges_enabled")
        val lastNudge = longPreferencesKey("last_nudge_at")
        val theme = stringPreferencesKey("theme_variant")
        val firstTake = booleanPreferencesKey("first_take_saved")
    }

    override val qualityMode: Flow<QualityMode> = store.data.map { p -> p[Keys.quality]?.let { runCatching { QualityMode.valueOf(it) }.getOrNull() } ?: QualityMode.STANDARD }
    override suspend fun setQualityMode(mode: QualityMode) { store.edit { it[Keys.quality] = mode.name } }

    override val firstSaveDisclosureDismissed: Flow<Boolean> = store.data.map { it[Keys.firstSaveDisclosure] ?: false }
    override suspend fun dismissFirstSaveDisclosure() { store.edit { it[Keys.firstSaveDisclosure] = true } }
    override val exportDisclosureSeen: Flow<Boolean> = store.data.map { it[Keys.exportDisclosure] ?: false }
    override suspend fun markExportDisclosureSeen() { store.edit { it[Keys.exportDisclosure] = true } }

    override val nudgesEnabled: Flow<Boolean> = store.data.map { it[Keys.nudges] ?: true }
    override suspend fun setNudgesEnabled(enabled: Boolean) { store.edit { it[Keys.nudges] = enabled } }
    override val lastNudgeAtMillis: Flow<Long?> = store.data.map { it[Keys.lastNudge] }
    override suspend fun setLastNudgeAt(millis: Long) { store.edit { it[Keys.lastNudge] = millis } }

    override val themeVariant: Flow<String?> = store.data.map { it[Keys.theme] }
    override suspend fun setThemeVariant(name: String?) { store.edit { if (name == null) it.remove(Keys.theme) else it[Keys.theme] = name } }
    override val hasSavedFirstTake: Flow<Boolean> = store.data.map { it[Keys.firstTake] ?: false }
    override suspend fun markFirstTakeSaved() { store.edit { it[Keys.firstTake] = true } }
}
