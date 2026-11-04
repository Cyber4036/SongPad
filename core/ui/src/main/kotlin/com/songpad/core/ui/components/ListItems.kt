package com.songpad.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadFonts
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongListItem(
    name: String,
    statusLabel: String?,
    metaLine: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    takeLabel: String? = null,
    lyricLabel: String? = null,
    statusTone: TagTone = TagTone.Accent2,
    onMoreClick: (() -> Unit)? = null,
) {
    val c = SongPadTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.lg),
        color = c.surface,
        contentColor = c.text,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SongPadFonts.heading,
                        fontWeight = FontWeight.Normal,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                    ),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) SongPadIcons.StarFilled else SongPadIcons.StarOutline,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) c.accent else c.text.copy(alpha = 0.40f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (onMoreClick != null) {
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = SongPadIcons.MoreHoriz,
                            contentDescription = "More options for $name",
                            tint = c.text.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (takeLabel != null) TagChip(takeLabel, tone = TagTone.Neutral)
                    if (lyricLabel != null) TagChip(lyricLabel, tone = TagTone.Neutral)
                    if (statusLabel != null) TagChip(statusLabel, tone = statusTone)
                }
                Text(
                    metaLine,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = c.text.copy(alpha = 0.50f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Capture.html "Unfiled — nice catch" warm accent container for unassigned takes at the top of the Library.
 */
@Composable
fun UnfiledTakesContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = SongPadTheme.colors
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = c.accent100,
        contentColor = c.text,
        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                "UNFILED — NICE CATCH",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = c.accent800,
            )
            content()
        }
    }
}

@Composable
fun UnfiledTakeRow(
    name: String,
    metaLine: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onAssignToSong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onPlayPause,
            shape = CircleShape,
            color = c.accent,
            contentColor = c.bg,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isPlaying) SongPadIcons.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause $name" else "Play $name",
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = SongPadFonts.heading,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                ),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                metaLine,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = c.text.copy(alpha = 0.60f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PrimaryButton(
            text = "Add to a song",
            onClick = onAssignToSong,
            compact = true,
        )
    }
}

@Composable
fun UnfiledLyricsRow(
    name: String,
    metaLine: String,
    onClick: () -> Unit,
    onAssignToSong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = c.accent2,
            contentColor = c.bg,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = SongPadIcons.Lyrics,
                    contentDescription = "Open $name",
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            contentColor = c.text,
            shape = RoundedCornerShape(Radii.sm),
            modifier = Modifier.weight(1f),
        ) {
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = SongPadFonts.heading,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                    ),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    metaLine,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = c.text.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PrimaryButton(
            text = "Add to a song",
            onClick = onAssignToSong,
            compact = true,
        )
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
    val cardBg = if (isPlaying) c.accent100 else c.surface
    val typeTone = when {
        typeLabel.contains("Voice") && typeLabel.contains("Piano") -> TagTone.Outline
        typeLabel.contains("Piano") -> TagTone.Accent2
        else -> TagTone.Accent
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.card),
        color = cardBg,
        contentColor = c.text,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = c.accent,
                contentColor = c.bg,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon = if (isPlaying) SongPadIcons.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play $name",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$durationLabel · $metaLine",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = c.text.copy(alpha = 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (statusBadge != null) TagChip(statusBadge, tone = TagTone.Warning)
                }
            }
            TagChip(typeLabel, tone = typeTone)
            trailing?.invoke()
        }
    }
}

@Composable
private fun Icon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(imageVector = icon, contentDescription = contentDescription, modifier = modifier)
}

/** Grouped card container for Settings sections matching Capture.html (.card with uppercase accent kicker). */
@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    kicker: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = SongPadTheme.colors
    Surface(
        shape = RoundedCornerShape(Radii.lg),
        color = c.surface,
        contentColor = c.text,
        border = BorderStroke(1.dp, c.divider),
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!kicker.isNullOrBlank()) {
                Text(
                    kicker.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = c.accent,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            content()
        }
    }
}

/** A row that opens a sub-surface (Settings entries, tabs' detail rows). */
@Composable
fun SettingRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showTopDivider: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = SongPadTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTopDivider) {
            HorizontalDivider(color = c.divider, thickness = 1.dp)
        }
        Surface(
            onClick = onClick ?: {},
            enabled = onClick != null,
            color = Color.Transparent,
            contentColor = c.text,
            shape = RoundedCornerShape(Radii.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                if (leadingIcon != null) {
                    androidx.compose.material3.Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp))
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = c.text.copy(alpha = 0.60f),
                        )
                    }
                }
                trailing?.invoke()
            }
        }
    }
}
