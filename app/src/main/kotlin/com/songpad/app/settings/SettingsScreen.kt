package com.songpad.app.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.take.QualityMode
import com.songpad.core.ui.components.SectionHeader
import com.songpad.core.ui.components.SettingRow
import com.songpad.core.ui.components.SongPadBottomSheet
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.core.ui.theme.ThemeVariant

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenNudges: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showLicenses by viewModel.showLicenses.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = SongPadTheme.colors
    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val notifGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val openAppSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(Modifier.fillMaxSize()) {
        SongPadTopBar(title = "Settings", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Appearance")
            SettingRow("Follow system", "SongPad look, light or dark with the system", onClick = { viewModel.setThemeVariant(null) },
                trailing = { Switch(checked = state.themeVariant == null, onCheckedChange = { if (it) viewModel.setThemeVariant(null) }) })
            ThemeVariant.entries.forEach { v ->
                SettingRow(v.displayName, null, onClick = { viewModel.setThemeVariant(v.name) },
                    trailing = { Switch(checked = state.themeVariant == v.name, onCheckedChange = { if (it) viewModel.setThemeVariant(v.name) }) })
            }

            SectionHeader("Permissions")
            SettingRow("Microphone", if (micGranted) "Allowed — used only while a recording screen is open" else "Not allowed — recording needs it", onClick = openAppSettings)
            SettingRow("Notifications", if (notifGranted) "Allowed — used for gentle nudges only" else "Off — nudges show only inside the app", onClick = openAppSettings)

            SectionHeader("Storage & data")
            val lossless = state.qualityMode == QualityMode.LOSSLESS
            SettingRow(
                title = "Lossless recording",
                subtitle = if (lossless) "On · FLAC, about 4 MB per minute" else "Off · Standard quality, about 1 MB per minute",
                onClick = { viewModel.setQualityMode(if (lossless) QualityMode.STANDARD else QualityMode.LOSSLESS) },
                trailing = { Switch(checked = lossless, onCheckedChange = { viewModel.setQualityMode(if (it) QualityMode.LOSSLESS else QualityMode.STANDARD) }) },
            )
            Text(
                "Applies to new recordings only. Existing takes keep their format.",
                Modifier.padding(horizontal = Spacing.screen), style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
            Spacer(Modifier.height(Spacing.sm))
            SettingRow("Recordings on this device", "${state.takeDirCount} take${if (state.takeDirCount == 1) "" else "s"} · ${state.storageUsedBytes.mb()}", onClick = viewModel::refreshStorage)
            Text(
                "Recordings are stored only on this phone and aren't included in cloud backup. Uninstalling SongPad or losing the phone deletes them. To keep a copy, export your songs or takes.",
                Modifier.padding(horizontal = Spacing.screen), style = MaterialTheme.typography.bodySmall, color = c.warning,
            )

            SectionHeader("Nudges")
            SettingRow("Gentle nudges", "At most one a day; timing is approximate", onClick = onOpenNudges,
                trailing = { Switch(checked = state.nudgesEnabled, onCheckedChange = viewModel::setNudgesEnabled) })

            SectionHeader("Export")
            SettingRow("Formats", "Stored copy (M4A / FLAC), WAV for DAWs, MP3 where available. Takes recorded in Standard quality export from a compressed source.", onClick = null)

            SectionHeader("About")
            SettingRow("SongPad", "Version 1.0.0 · Phase 1", onClick = null)
            SettingRow("Open source licenses", "Oboe, Media3, LAME, Figtree, Caprasimo and others", onClick = { viewModel.setShowLicenses(true) })
            SettingRow("Privacy", "Audio never leaves the device. No accounts, no analytics, no network.", onClick = null)
            Spacer(Modifier.height(Spacing.xxxl))
        }
    }

    if (showLicenses) {
        SongPadBottomSheet(onDismiss = { viewModel.setShowLicenses(false) }, title = "Open source licenses") {
            Text(LICENSES, Modifier.padding(Spacing.screen).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

private fun Long.mb(): String = if (this < 1024 * 1024) "${this / 1024} KB" else "%.1f MB".format(this / (1024.0 * 1024.0))

/** Full texts live under core/ui/licenses and docs/audio/LAME_INTEGRATION.md; this is the in-app summary the LGPL asks for. */
private const val LICENSES = """
Oboe — Apache License 2.0 — https://github.com/google/oboe
AndroidX Media3 — Apache License 2.0
AndroidX (Room, WorkManager, Compose, Navigation 3, Hilt) — Apache License 2.0
Kotlin and kotlinx libraries — Apache License 2.0
LAME MP3 encoder — LGPL 2.1 — linked dynamically as libsongpad_lame.so; source and build notes: docs/audio/LAME_INTEGRATION.md
Figtree — SIL Open Font License 1.1 — Erik Kennedy
Caprasimo — SIL Open Font License 1.1 — Jeremy Tribby
"""
