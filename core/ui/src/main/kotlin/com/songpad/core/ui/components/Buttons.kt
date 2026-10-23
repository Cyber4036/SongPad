package com.songpad.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadFonts
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
) {
    val c = SongPadTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = if (compact) 36.dp else Dimens.minTouchTarget),
        shape = RoundedCornerShape(Radii.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.bg,
            disabledContainerColor = c.accentSoft,
            disabledContentColor = c.textTertiary,
        ),
        contentPadding = if (compact) PaddingValues(horizontal = 14.dp, vertical = 7.dp) else PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
            Box(Modifier.size(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = SongPadFonts.heading,
                fontWeight = FontWeight.Normal,
                fontSize = if (compact) 12.5.sp else 14.sp,
            ),
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
) {
    val c = SongPadTheme.colors
    val content = if (destructive) c.destructive else c.text
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = if (compact) 34.dp else Dimens.minTouchTarget),
        shape = RoundedCornerShape(Radii.pill),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content, disabledContentColor = c.textTertiary),
        border = BorderStroke(1.dp, if (destructive) c.destructive.copy(alpha = 0.5f) else c.divider),
        contentPadding = if (compact) PaddingValues(horizontal = 13.dp, vertical = 6.dp) else PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(15.dp))
            Box(Modifier.size(5.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = SongPadFonts.heading,
                fontWeight = FontWeight.Normal,
                fontSize = if (compact) 12.5.sp else 14.sp,
            ),
        )
    }
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
    outlined: Boolean = false,
    accentFilled: Boolean = false,
) {
    val c = SongPadTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget),
    ) {
        val innerModifier = when {
            accentFilled -> Modifier.size(36.dp).background(c.accent, CircleShape)
            filled -> Modifier.size(36.dp).background(c.surface, CircleShape).border(1.dp, c.divider, CircleShape)
            outlined -> Modifier.size(36.dp).border(1.dp, c.divider, CircleShape)
            else -> Modifier.size(36.dp)
        }
        val iconTint = when {
            !enabled -> c.textTertiary
            accentFilled -> c.bg
            else -> tint
        }
        Box(innerModifier, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(19.dp))
        }
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
    size: Dp = Dimens.recordFab,
    enabled: Boolean = true,
    pulseWhenIdle: Boolean = false,
    haloWidth: Dp = 5.dp,
) {
    val c = SongPadTheme.colors
    val pulse = rememberInfiniteTransition(label = "record-pulse")
    val ring by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ring",
    )
    val fill by animateColorAsState(if (enabled) c.record else c.textTertiary, label = "fill")
    Box(modifier.size(size + 16.dp), contentAlignment = Alignment.Center) {
        if (isRecording || pulseWhenIdle) {
            Box(Modifier.size(size).scale(ring).background(c.recordSoft, CircleShape))
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = fill,
            contentColor = c.bg,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(size)
                .then(if (haloWidth > 0.dp) Modifier.border(haloWidth, c.bg, CircleShape) else Modifier)
                .semantics { contentDescription = if (isRecording) "Stop recording" else "Record" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isRecording) {
                    Icon(SongPadIcons.Pause, contentDescription = null, modifier = Modifier.size(size * 0.36f))
                } else {
                    Icon(SongPadIcons.Mic, contentDescription = null, modifier = Modifier.size(size * 0.40f))
                }
            }
        }
    }
}

/** Library/Takes/Tabs filter pill matching Capture.html (.tag / pill buttons). */
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
    val bg by animateColorAsState(if (selected) c.accent else Color.Transparent, label = "pill-bg")
    val fg by animateColorAsState(
        when {
            selected -> c.bg
            enabled -> c.text.copy(alpha = 0.72f)
            else -> c.textTertiary
        },
        label = "pill-fg",
    )
    val bd by animateColorAsState(if (selected) c.accent else c.divider, label = "pill-bd")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radii.pill),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, bd),
        modifier = modifier.defaultMinSize(minHeight = 34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Box(Modifier.size(5.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1,
            )
        }
    }
}

/** Record screen top-right layer toggle pill with status dot (Voice · Piano · Lyrics in Capture.html). */
@Composable
fun LayerTogglePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    val bg by animateColorAsState(if (active) c.accent else Color.Transparent, label = "layer-bg")
    val fg by animateColorAsState(if (active) c.bg else c.text.copy(alpha = 0.72f), label = "layer-fg")
    val bd by animateColorAsState(if (active) c.accent else c.divider, label = "layer-bd")
    val dot by animateColorAsState(if (active) c.bg else c.text.copy(alpha = 0.30f), label = "layer-dot")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.pill),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, bd),
        modifier = modifier.defaultMinSize(minHeight = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp))
        }
    }
}

/** Type tag / status badge matching .tag in Capture.html. */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: TagTone = TagTone.Accent2,
    onClick: (() -> Unit)? = null,
) {
    val c = SongPadTheme.colors
    val (bg, fg, border) = when (tone) {
        TagTone.Accent2 -> Triple(c.accent2_100, c.accent2_800, null)
        TagTone.Accent -> Triple(c.accent100, c.accent800, null)
        TagTone.Record -> Triple(c.recordSoft, c.text, null)
        TagTone.Neutral -> Triple(c.neutral100, c.neutral800, null)
        TagTone.Outline -> Triple(Color.Transparent, c.accent, BorderStroke(1.dp, c.accent))
        TagTone.Warning -> Triple(c.warning.copy(alpha = 0.2f), c.text, null)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(Radii.tag),
            color = bg,
            contentColor = fg,
            border = border,
            modifier = modifier,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = fg,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    } else {
        val boxMod = modifier
            .background(bg, RoundedCornerShape(Radii.tag))
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(Radii.tag)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp)
        Box(boxMod, contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = fg, maxLines = 1)
        }
    }
}

enum class TagTone { Accent2, Accent, Record, Neutral, Outline, Warning }
