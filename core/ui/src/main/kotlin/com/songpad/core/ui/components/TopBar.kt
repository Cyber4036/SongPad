package com.songpad.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadFonts
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

/** Top-left back/close, title, upper-right actions (Phase 0 §6, §14, §15). */
@Composable
fun SongPadTopBar(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backContentDescription: String = "Back",
    useCloseIcon: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = SongPadTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen - 6.dp, end = Spacing.screen - 6.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(
                icon = if (useCloseIcon) Icons.Default.Close else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = backContentDescription,
                onClick = onBack,
                outlined = true,
            )
        }
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SongPadFonts.heading, fontSize = 20.sp),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = if (onBack == null) 6.dp else Spacing.xs),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * Editorial screen header from Capture.html: uppercase tracked kicker ("CAUGHT SO FAR", "GENTLE REMINDERS")
 * above a Caprasimo display heading, with optional right-aligned action buttons.
 */
@Composable
fun EditorialHeader(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    kickerColor: Color = SongPadTheme.colors.accent,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = SongPadTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.screen, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                kicker.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = kickerColor,
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = SongPadFonts.heading,
                    fontSize = 25.sp,
                    lineHeight = 29.sp,
                ),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = actions,
        )
    }
}

/** Inline pill search input from Capture.html ("Search songs, lyrics, notes"). */
@Composable
fun PillSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search songs, lyrics, notes",
    onSearchAction: (() -> Unit)? = null,
) {
    val c = SongPadTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.pill),
        color = c.surface,
        contentColor = c.text,
        border = BorderStroke(1.dp, c.divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = c.text.copy(alpha = 0.5f),
                modifier = Modifier.size(17.dp),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = c.text.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchAction?.invoke() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = c.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
