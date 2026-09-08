package com.songpad.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

/**
 * The restrained glass treatment (§14): a translucent pill with a hairline stroke, used by the recording
 * control group (Phase 0 §6) and the ribbon player (Phase 0 §9). Not a global decoration.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
    content: @Composable RowScope.() -> Unit,
) {
    val c = SongPadTheme.colors
    Surface(
        modifier = modifier.defaultMinSize(minHeight = Dimens.glassPillHeight),
        shape = RoundedCornerShape(Radii.pill),
        color = c.glassFill,
        contentColor = c.text,
        border = BorderStroke(1.dp, c.glassStroke),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}
