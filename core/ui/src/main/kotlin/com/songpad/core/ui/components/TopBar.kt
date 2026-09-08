package com.songpad.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

/** Top-left back/close, title, upper-right actions (Phase 0 §6, §14, §15). */
@Composable
fun SongPadTopBar(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backContentDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = SongPadTheme.colors
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconAction(Icons.AutoMirrored.Filled.ArrowBack, backContentDescription, onBack)
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = if (onBack == null) Spacing.lg else Spacing.xs),
            )
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
        actions()
    }
}
