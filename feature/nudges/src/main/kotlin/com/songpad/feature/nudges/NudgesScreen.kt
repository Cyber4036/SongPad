package com.songpad.feature.nudges

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.nudge.Nudge
import com.songpad.core.ui.components.ConfirmDialog
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.SettingRow
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

@Composable
fun NudgesScreen(
    onOpenSong: (String) -> Unit,
    onOpenTake: (String) -> Unit,
    onOpenLibrary: () -> Unit,
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

    Column(Modifier.fillMaxSize()) {
        SongPadTopBar(title = "Nudges", onBack = null)
        val enabled = state !is NudgesState.Disabled
        SettingRow(
            title = "Gentle nudges", subtitle = "At most one a day, about ideas you started. Timing is approximate — the system decides when.",
            onClick = { viewModel.setEnabled(!enabled) },
            trailing = { Switch(checked = enabled, onCheckedChange = { viewModel.setEnabled(it) }) },
        )
        when (val s = state) {
            NudgesState.Loading -> Unit
            NudgesState.Empty -> EmptyState(SongPadIcons.MusicNote, "Nothing yet", "When a section sits empty or a take has no home, a quiet reminder shows up here.")
            is NudgesState.Active -> {
                if (!s.notificationsGranted) Text(
                    "Notifications are off, so nudges only appear here. Turn them on in system settings to get them on the lock screen.",
                    Modifier.padding(Spacing.screen), style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                History(s.history, viewModel::open)
            }
            is NudgesState.Disabled -> if (s.history.isEmpty()) EmptyState(SongPadIcons.MusicNote, "Nudges are off", "Turn them on to get an occasional reminder.") else History(s.history, viewModel::open)
            is NudgesState.TargetUnavailable -> {
                History(s.history, viewModel::open)
                ConfirmDialog(
                    title = "That one's gone", body = "The song or take this nudge pointed to was deleted.",
                    confirmLabel = "Open library", destructive = false,
                    onConfirm = { viewModel.dismissUnavailable(goToLibrary = true) }, onDismiss = { viewModel.dismissUnavailable(goToLibrary = false) },
                )
            }
        }
    }
}

@Composable
private fun History(history: List<Nudge>, onOpen: (Nudge) -> Unit) {
    val c = SongPadTheme.colors
    LazyColumn(Modifier.fillMaxSize()) {
        items(history, key = { it.id }) { n ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(n) }.padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                Text(n.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.Bold), color = c.text)
                Text(n.body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(n.meta.createdAt)), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }
    }
}
