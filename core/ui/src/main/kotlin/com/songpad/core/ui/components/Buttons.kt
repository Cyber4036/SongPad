package com.songpad.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leadingIcon: ImageVector? = null) {
    val c = SongPadTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
        shape = RoundedCornerShape(Radii.pill),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent, disabledContainerColor = c.accentSoft, disabledContentColor = c.textTertiary),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Box(Modifier.size(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    val c = SongPadTheme.colors
    val content = if (destructive) c.destructive else c.text
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
        shape = RoundedCornerShape(Radii.pill),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content, disabledContentColor = c.textTertiary),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (destructive) c.destructive.copy(alpha = 0.5f) else c.divider),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Icon action with the 48dp minimum target enforced regardless of icon size. */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = SongPadTheme.colors.text,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val c = SongPadTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .sizeIn(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
            .then(if (filled) Modifier.background(c.surfaceVariant, CircleShape) else Modifier),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else c.textTertiary)
    }
}

/**
 * The centered, protruding Record action (Phase 0 §3). Reads `record` from the token layer; pulses while recording.
 */
@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Dimens.recordFab,
    enabled: Boolean = true,
) {
    val c = SongPadTheme.colors
    val pulse = rememberInfiniteTransition(label = "record-pulse")
    val ring by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ring",
    )
    val fill by animateColorAsState(if (enabled) c.record else c.textTertiary, label = "fill")
    Box(modifier.size(size + 16.dp), contentAlignment = Alignment.Center) {
        if (isRecording) {
            Box(Modifier.size(size).scale(ring).background(c.recordSoft, CircleShape))
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = fill,
            contentColor = c.onAccent,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(size)
                .border(3.dp, c.bg, CircleShape)
                .semantics { contentDescription = if (isRecording) "Stop recording" else "Record" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isRecording) {
                    Box(Modifier.size(size * 0.32f).background(c.onAccent, RoundedCornerShape(4.dp)))
                } else {
                    Icon(SongPadIcons.Mic, contentDescription = null, modifier = Modifier.size(size * 0.42f))
                }
            }
        }
    }
}

/** Library/Takes filter pill and the Record-screen mode pill share one component (Phase 0 §6, §9, §14). */
@Composable
fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = SongPadTheme.colors
    val bg by animateColorAsState(if (selected) c.accent else c.surfaceVariant, label = "pill-bg")
    val fg by animateColorAsState(if (selected) c.onAccent else if (enabled) c.text else c.textTertiary, label = "pill-fg")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radii.pill),
        color = bg,
        contentColor = fg,
        modifier = modifier.height(Dimens.minTouchTarget - 8.dp).defaultMinSize(minWidth = Dimens.minTouchTarget),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Box(Modifier.size(Spacing.xs + 2.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Type tag / status badge (`accent-2` role). */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier, tone: TagTone = TagTone.Accent2) {
    val c = SongPadTheme.colors
    val (bg, fg) = when (tone) {
        TagTone.Accent2 -> c.accent2Soft to c.text
        TagTone.Accent -> c.accentSoft to c.text
        TagTone.Record -> c.recordSoft to c.text
        TagTone.Neutral -> c.surfaceVariant to c.textSecondary
        TagTone.Warning -> c.warning.copy(alpha = 0.2f) to c.text
    }
    Box(modifier.background(bg, RoundedCornerShape(Radii.sm)).padding(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

enum class TagTone { Accent2, Accent, Record, Neutral, Warning }
