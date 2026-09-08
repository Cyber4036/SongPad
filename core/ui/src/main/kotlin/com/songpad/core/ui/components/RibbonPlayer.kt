package com.songpad.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

/**
 * Compact bottom ribbon / glass-pill player (Phase 0 §9): pause/play and waveform seeking. Pure
 * presentation — the feature passes state in and gets intents out, so one `PlaybackController`
 * instance can drive every surface (§7.6).
 */
@Composable
fun RibbonPlayer(
    title: String,
    subtitle: String?,
    isPlaying: Boolean,
    progress: Float,
    positionLabel: String,
    peaks: FloatArray,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    GlassPill(modifier = modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm)) {
        IconAction(
            icon = if (isPlaying) SongPadIcons.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            tint = c.accent,
            filled = true,
        )
        Column(Modifier.weight(1f).padding(horizontal = Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(positionLabel, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            SeekableWaveform(peaks = peaks, progress = progress, onSeek = onSeek, modifier = Modifier.height(36.dp), active = true)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconAction(icon = Icons.Default.Close, contentDescription = "Close player", onClick = onDismiss, tint = c.textSecondary)
    }
}
