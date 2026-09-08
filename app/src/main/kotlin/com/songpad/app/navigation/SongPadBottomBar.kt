package com.songpad.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.sizeIn
import androidx.navigation3.runtime.NavKey
import com.songpad.core.ui.components.RecordButton
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

/** Phase 0 §3: Songs · Record (centered, protruding) · Nudges. */
@Composable
fun SongPadBottomBar(current: NavKey, onSongs: () -> Unit, onRecord: () -> Unit, onNudges: () -> Unit) {
    val c = SongPadTheme.colors
    Box(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab("Songs", SongPadIcons.MusicNote, selected = current is LibraryKey, onClick = onSongs, modifier = Modifier.weight(1f))
            Box(Modifier.weight(1f))
            Tab("Nudges", SongPadIcons.Lyrics, selected = current is NudgesKey, onClick = onNudges, modifier = Modifier.weight(1f))
        }
        RecordButton(isRecording = false, onClick = onRecord, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-16).dp), size = Dimens.recordFab)
    }
}

@Composable
private fun Tab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = SongPadTheme.colors
    val tint = if (selected) c.accent else c.textSecondary
    Column(
        modifier.sizeIn(minHeight = Dimens.minTouchTarget).clickable(onClick = onClick).padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
