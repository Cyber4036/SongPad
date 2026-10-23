package com.songpad.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadFonts
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPadBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = SongPadTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.bg,
        contentColor = c.text,
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(c.divider, RoundedCornerShape(Radii.pill)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.screen, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = SongPadFonts.heading,
                        fontSize = 19.sp,
                    ),
                    color = c.text,
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = c.text.copy(alpha = 0.60f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            } else if (title != null) {
                Spacer(Modifier.height(4.dp))
            }
            content()
        }
    }
}

/** Pill option row inside a SongPadBottomSheet matching Capture.html sheetItems. */
@Composable
fun SheetOptionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    destructive: Boolean = false,
    accent: Boolean = false,
) {
    val c = SongPadTheme.colors
    val bg = when {
        destructive -> c.accent100
        accent -> c.accent100
        else -> c.surface
    }
    val fg = when {
        destructive -> c.accent800
        accent -> c.accent800
        else -> c.text
    }
    val bd = if (destructive || accent) c.accent.copy(alpha = 0.35f) else c.divider
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, bd),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    fontWeight = if (destructive) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = fg,
                modifier = Modifier.weight(1f),
            )
            if (!meta.isNullOrBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = fg.copy(alpha = 0.60f),
                )
            }
        }
    }
}

/** Destructive actions require confirmation with clear wording (Phase 0 §21). */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    val c = SongPadTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.text.copy(alpha = 0.85f),
        shape = RoundedCornerShape(Radii.sheet),
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = SongPadFonts.heading,
                    fontSize = 20.sp,
                ),
            )
        },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)) },
        confirmButton = {
            PrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                compact = true,
            )
        },
        dismissButton = {
            SecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                compact = true,
            )
        },
    )
}

/** Lightweight single-field dialog: name a Song, rename a Take (Phase 0 §14 "no multi-step wizard"). */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    validate: (String) -> String? = { if (it.isBlank()) "Enter a name" else null },
) {
    val c = SongPadTheme.colors
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val error = validate(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.text,
        shape = RoundedCornerShape(Radii.sheet),
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = SongPadFonts.heading,
                    fontSize = 20.sp,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(Radii.pill),
                isError = error != null && value.isNotEmpty(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.divider,
                    cursorColor = c.accent,
                    focusedLabelColor = c.accent,
                    focusedContainerColor = c.bg,
                    unfocusedContainerColor = c.bg,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            PrimaryButton(
                text = confirmLabel,
                onClick = { if (error == null) onConfirm(value.trim()) },
                enabled = error == null,
                compact = true,
            )
        },
        dismissButton = {
            SecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                compact = true,
            )
        },
    )
}
