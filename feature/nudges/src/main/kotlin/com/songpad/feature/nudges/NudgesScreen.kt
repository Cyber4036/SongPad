package com.songpad.feature.nudges

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.nudge.Nudge
import com.songpad.core.ui.components.ConfirmDialog
import com.songpad.core.ui.components.EditorialHeader
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.SettingRow
import com.songpad.core.ui.components.SettingsGroupCard
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadFonts
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

@Composable
fun NudgesScreen(
    onOpenSong: (String) -> Unit,
    onOpenTake: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onLaunchPiano: () -> Unit = {},
    onLaunchLyrics: () -> Unit = {},
    viewModel: NudgesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = SongPadTheme.colors
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.setNotificationPermission(it) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            viewModel.setNotificationPermission(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        }
        viewModel.navigation.collect { nav ->
            when (nav) {
                is NudgeNavigation.Song -> onOpenSong(nav.songId)
                is NudgeNavigation.Take -> onOpenTake(nav.takeId)
                NudgeNavigation.Library -> onOpenLibrary()
                NudgeNavigation.RequestNotificationPermission -> if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            EditorialHeader(
                kicker = "Gentle reminders",
                title = "Nudges",
                kickerColor = c.accent2_800,
            )
            Text(
                text = "Quiet prompts when an unfinished idea has been sitting a while. At most one a day — never pushy.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = c.textSecondary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )

            val enabled = state !is NudgesState.Disabled
            SettingsGroupCard(modifier = Modifier.padding(bottom = 12.dp)) {
                SettingRow(
                    title = "Gentle nudges",
                    subtitle = if (enabled) "On · at most one per day" else "Off · history stays below",
                    onClick = { viewModel.setEnabled(!enabled) },
                    trailing = { Switch(checked = enabled, onCheckedChange = { viewModel.setEnabled(it) }) },
                )
            }

            when (val s = state) {
                NudgesState.Loading -> Unit
                NudgesState.Empty -> EmptyState(
                    icon = SongPadIcons.Bell,
                    title = "Nothing yet",
                    body = "When a section sits empty or a take has no home, a quiet reminder shows up here.",
                )
                is NudgesState.Active -> {
                    if (!s.notificationsGranted) {
                        Text(
                            text = "Notifications are off, so nudges only appear here. Turn them on in system settings to get them on the lock screen.",
                            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                    History(s.history, viewModel::open)
                }
                is NudgesState.Disabled -> if (s.history.isEmpty()) {
                    EmptyState(SongPadIcons.Bell, "Nudges are off", "Turn them on to get an occasional reminder.")
                } else {
                    History(s.history, viewModel::open)
                }
                is NudgesState.TargetUnavailable -> {
                    History(s.history, viewModel::open)
                    ConfirmDialog(
                        title = "That one's gone",
                        body = "The song or take this nudge pointed to was deleted.",
                        confirmLabel = "Open library",
                        destructive = false,
                        onConfirm = { viewModel.dismissUnavailable(goToLibrary = true) },
                        onDismiss = { viewModel.dismissUnavailable(goToLibrary = false) },
                    )
                }
            }
        }

        // Floating Lyrics (higher) and Piano (lower) quick-launch icons in the lower-right corner above the bottom bar
        com.songpad.core.ui.components.CaptureQuickActions(
            onLaunchLyrics = onLaunchLyrics,
            onLaunchPiano = onLaunchPiano,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun History(history: List<Nudge>, onOpen: (Nudge) -> Unit) {
    val c = SongPadTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        items(history, key = { it.id }) { n ->
            Surface(
                onClick = { onOpen(n) },
                shape = RoundedCornerShape(26.dp),
                color = c.surface,
                border = BorderStroke(1.dp, c.divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(c.accent100),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = SongPadIcons.Bell,
                            contentDescription = null,
                            tint = c.accent800,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = n.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = SongPadFonts.heading,
                                fontSize = 15.sp,
                            ),
                            color = c.text,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = n.body,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = n.meta.createdAt.nudgeTimeLabel(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = c.textTertiary,
                        )
                    }
                    if (!n.isRead) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(c.accent),
                        )
                    }
                }
            }
        }
    }
}

private fun Long.nudgeTimeLabel(): String {
    val diffMs = (System.currentTimeMillis() - this).coerceAtLeast(0L)
    val hours = diffMs / 3_600_000L
    val days = hours / 24L
    return when {
        hours < 1 -> "Today"
        hours < 24 -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
    }
}
