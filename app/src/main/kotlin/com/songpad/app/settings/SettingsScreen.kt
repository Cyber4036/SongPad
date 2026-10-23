package com.songpad.app.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.app.BuildConfig
import com.songpad.core.model.take.QualityMode
import com.songpad.core.ui.components.SectionHeader
import com.songpad.core.ui.components.SettingRow
import com.songpad.core.ui.components.SettingsGroupCard
import com.songpad.core.ui.components.SongPadBottomSheet
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadFonts
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.core.ui.theme.ThemeVariant

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenNudges: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showLicenses by viewModel.showLicenses.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = SongPadTheme.colors
    val sysDark = isSystemInDarkTheme()
    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val notifGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val openAppSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    val currentVariant = ThemeVariant.fromName(state.themeVariant)
    val isMelodyFamily = currentVariant == ThemeVariant.MELODY_LIGHT || currentVariant == ThemeVariant.MELODY_DARK
    val isDarkActive = currentVariant?.isDark ?: sysDark
    val followSystem = state.themeVariant == null

    fun selectFamily(melody: Boolean) {
        val target = when {
            melody && isDarkActive -> ThemeVariant.MELODY_DARK
            melody && !isDarkActive -> ThemeVariant.MELODY_LIGHT
            !melody && isDarkActive -> ThemeVariant.SONGPAD_DARK
            else -> ThemeVariant.SONGPAD_LIGHT
        }
        viewModel.setThemeVariant(target.name)
    }

    fun selectMode(dark: Boolean?) {
        if (dark == null) {
            viewModel.setThemeVariant(null)
        } else {
            val target = when {
                isMelodyFamily && dark -> ThemeVariant.MELODY_DARK
                isMelodyFamily && !dark -> ThemeVariant.MELODY_LIGHT
                !isMelodyFamily && dark -> ThemeVariant.SONGPAD_DARK
                else -> ThemeVariant.SONGPAD_LIGHT
            }
            viewModel.setThemeVariant(target.name)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SongPadTopBar(title = "Settings", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Appearance")
            SettingsGroupCard {
                Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeFamilyCard(
                            title = "SongPad",
                            swatches = listOf(Color(0xFF1D1B1A), Color(0xFFE05A47), Color(0xFFE8A838)),
                            selected = !isMelodyFamily,
                            onClick = { selectFamily(melody = false) },
                            modifier = Modifier.weight(1f),
                        )
                        ThemeFamilyCard(
                            title = "Melody Studio",
                            swatches = listOf(Color(0xFF181A1F), Color(0xFF5B7FFF), Color(0xFF3ECFA0)),
                            selected = isMelodyFamily,
                            onClick = { selectFamily(melody = true) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModeOptionPill(
                            label = "Light",
                            selected = !followSystem && !isDarkActive,
                            onClick = { selectMode(false) },
                            modifier = Modifier.weight(1f),
                        )
                        ModeOptionPill(
                            label = "Dark",
                            selected = !followSystem && isDarkActive,
                            onClick = { selectMode(true) },
                            modifier = Modifier.weight(1f),
                        )
                        ModeOptionPill(
                            label = "System",
                            selected = followSystem,
                            onClick = { selectMode(null) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (isMelodyFamily) {
                            "Cool slate and electric indigo — feels like a studio"
                        } else {
                            "Warm paper and terracotta — feels like a notebook"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }

            SectionHeader("Permissions")
            SettingsGroupCard {
                SettingRow("Microphone", if (micGranted) "Allowed — used only while recording" else "Not allowed — recording needs it", onClick = openAppSettings)
                HorizontalDivider(color = c.divider, thickness = 1.dp)
                SettingRow("Notifications", if (notifGranted) "Allowed — used for gentle nudges only" else "Off — nudges show only inside the app", onClick = openAppSettings)
            }

            SectionHeader("Storage & data")
            val lossless = state.qualityMode == QualityMode.LOSSLESS
            SettingsGroupCard {
                SettingRow(
                    title = "Lossless recording",
                    subtitle = if (lossless) "On · FLAC, about 4 MB per minute" else "Off · Standard quality, about 1 MB per minute",
                    onClick = { viewModel.setQualityMode(if (lossless) QualityMode.STANDARD else QualityMode.LOSSLESS) },
                    trailing = { Switch(checked = lossless, onCheckedChange = { viewModel.setQualityMode(if (it) QualityMode.LOSSLESS else QualityMode.STANDARD) }) },
                )
                HorizontalDivider(color = c.divider, thickness = 1.dp)
                SettingRow(
                    title = "Recordings on this device",
                    subtitle = "${state.takeDirCount} take${if (state.takeDirCount == 1) "" else "s"} · ${state.storageUsedBytes.mb()}",
                    onClick = viewModel::refreshStorage,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Recordings stay on this phone and aren't in cloud backup. Export songs or takes to keep a copy elsewhere.",
                modifier = Modifier.padding(horizontal = 22.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = c.textSecondary,
            )

            SectionHeader("Nudges")
            SettingsGroupCard {
                SettingRow(
                    title = "Gentle nudges",
                    subtitle = "At most one a day; timing is approximate",
                    onClick = onOpenNudges,
                    trailing = { Switch(checked = state.nudgesEnabled, onCheckedChange = viewModel::setNudgesEnabled) },
                )
            }

            SectionHeader("Export")
            SettingsGroupCard {
                SettingRow(
                    title = "Formats",
                    subtitle = "Stored copy (M4A / FLAC), WAV for DAWs, MP3 where available.",
                    onClick = null,
                )
            }

            SectionHeader("About")
            SettingsGroupCard {
                SettingRow("SongPad", "Version ${BuildConfig.VERSION_NAME}", onClick = null)
                HorizontalDivider(color = c.divider, thickness = 1.dp)
                SettingRow("Open source licenses", "Oboe, Media3, LAME, Figtree, Caprasimo and others", onClick = { viewModel.setShowLicenses(true) })
                HorizontalDivider(color = c.divider, thickness = 1.dp)
                SettingRow("Privacy", "Audio never leaves the device. No accounts, no analytics, no network.", onClick = null)
            }
            Spacer(Modifier.height(Spacing.xxxl))
        }
    }

    if (showLicenses) {
        SongPadBottomSheet(onDismiss = { viewModel.setShowLicenses(false) }, title = "Open source licenses") {
            Text(LICENSES, Modifier.padding(Spacing.screen).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun ThemeFamilyCard(
    title: String,
    swatches: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = c.bg,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.divider),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                swatches.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = SongPadFonts.heading,
                    fontSize = 14.sp,
                ),
                color = c.text,
            )
        }
    }
}

@Composable
private fun ModeOptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.pill),
        color = if (selected) c.neutral900 else c.bg,
        contentColor = if (selected) c.bg else c.textSecondary,
        border = BorderStroke(1.dp, if (selected) c.neutral900 else c.divider),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

private fun Long.mb(): String = if (this < 1024 * 1024) "${this / 1024} KB" else "%.1f MB".format(this / (1024.0 * 1024.0))

private const val LICENSES = """
Oboe — Apache License 2.0 — https://github.com/google/oboe
AndroidX Media3 — Apache License 2.0
AndroidX (Room, WorkManager, Compose, Navigation 3, Hilt) — Apache License 2.0
Kotlin and kotlinx libraries — Apache License 2.0
LAME MP3 encoder — LGPL 2.1 — linked dynamically as libsongpad_lame.so; source and build notes: docs/audio/LAME_INTEGRATION.md
Figtree — SIL Open Font License 1.1 — Erik Kennedy
Caprasimo — SIL Open Font License 1.1 — Jeremy Tribby
"""
