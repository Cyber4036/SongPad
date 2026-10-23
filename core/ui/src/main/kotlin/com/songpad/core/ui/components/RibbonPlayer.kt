package com.songpad.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

/**
 * Compact floating bottom ribbon / glass-pill player matching Capture.html:
 * [38dp circular accent Play/Pause] [Inline Scrubbable Waveform] [Tabular Time] [✕ Close].
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
    GlassPill(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Surface(
            onClick = onPlayPause,
            shape = CircleShape,
            color = c.accent,
            contentColor = c.bg,
            modifier = Modifier.size(38.dp),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) SongPadIcons.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause $title" else "Play $title",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        SeekableWaveform(
            peaks = peaks,
            progress = progress,
            onSeek = onSeek,
            modifier = Modifier.weight(1f).height(32.dp),
            active = true,
            showPlayhead = false,
        )
        Text(
            positionLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = c.text.copy(alpha = 0.72f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close player",
                tint = c.text.copy(alpha = 0.55f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
