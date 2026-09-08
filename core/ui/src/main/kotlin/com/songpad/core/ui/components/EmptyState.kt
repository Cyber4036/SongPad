package com.songpad.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

/** Empty / error / unavailable states as real components, not toasts (§17). */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = SongPadTheme.colors
    Column(
        modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(64.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = c.text)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = c.textSecondary)
        if (actionLabel != null && onAction != null) PrimaryButton(actionLabel, onAction)
    }
}

/**
 * One dismissible inline line — the §4.4 first-save disclosure surface and the OEM battery hint (§11.4).
 * Shown once, dismissible, never repeated: the caller owns that bookkeeping.
 */
@Composable
fun InlineDisclosure(text: String, actionLabel: String?, onAction: (() -> Unit)?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = SongPadTheme.colors
    Surface(color = c.accentSoft, contentColor = c.text, shape = RoundedCornerShape(Radii.md), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                androidx.compose.material3.TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = c.accent) }
            }
            IconAction(Icons.Default.Close, contentDescription = "Dismiss", onClick = onDismiss, tint = c.textSecondary)
        }
    }
}

/** Grouped section heading used by search results, settings and Song Home. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = SongPadTheme.colors
    Row(modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
