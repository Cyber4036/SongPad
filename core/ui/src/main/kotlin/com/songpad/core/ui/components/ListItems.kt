package com.songpad.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

@Composable
fun SongListItem(
    name: String,
    statusLabel: String?,
    metaLine: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    Surface(onClick = onClick, shape = RoundedCornerShape(Radii.md), color = c.surface, contentColor = c.text, border = BorderStroke(1.dp, c.divider), modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.defaultMinSize(minHeight = Dimens.listItemMinHeight).padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    if (statusLabel != null) TagChip(statusLabel)
                    Text(metaLine, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconAction(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = onToggleFavorite,
                tint = if (isFavorite) c.record else c.textTertiary,
            )
        }
    }
}

@Composable
fun TakeListItem(
    name: String,
    typeLabel: String,
    typeIcon: ImageVector,
    durationLabel: String,
    metaLine: String,
    statusBadge: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = SongPadTheme.colors
    Surface(onClick = onClick, shape = RoundedCornerShape(Radii.md), color = c.surface, contentColor = c.text, border = BorderStroke(1.dp, c.divider), modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.defaultMinSize(minHeight = Dimens.listItemMinHeight).padding(start = Spacing.sm, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = if (isPlaying) SongPadIcons.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play $name",
                onClick = onPlay,
                tint = c.accent,
                filled = true,
            )
            Column(Modifier.weight(1f).padding(start = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(typeIcon, contentDescription = null, tint = c.accent2, modifier = Modifier.size(14.dp))
                        Box(Modifier.size(Spacing.xs))
                        Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    }
                    Text(durationLabel, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    if (statusBadge != null) TagChip(statusBadge, tone = TagTone.Warning)
                    Text(metaLine, style = MaterialTheme.typography.bodySmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
        }
    }
}

/** A row that opens a sub-surface (Settings entries, tabs' detail rows). */
@Composable
fun SettingRow(title: String, subtitle: String?, onClick: (() -> Unit)?, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = SongPadTheme.colors
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        color = c.surface,
        contentColor = c.text,
        shape = RoundedCornerShape(Radii.md),
        border = BorderStroke(1.dp, c.divider),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.defaultMinSize(minHeight = Dimens.listItemMinHeight).padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            trailing?.invoke()
        }
    }
}
