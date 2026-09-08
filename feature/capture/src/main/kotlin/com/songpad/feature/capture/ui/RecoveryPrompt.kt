package com.songpad.feature.capture.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.songpad.core.model.usecase.DurationFormat
import com.songpad.core.ui.components.ConfirmDialog
import com.songpad.core.ui.components.PrimaryButton
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.capture.CaptureIntent
import com.songpad.feature.capture.RecoveryUiState

/**
 * §9.4: ONE non-blocking prompt on cold start. Rendered inline at the top of the record screen, not as a modal,
 * so it never blocks a new recording. Open product question §21.3 #3 (prompt vs silent library item) stays open;
 * this is the "prompt" rendering and can be swapped without touching the ViewModel.
 */
@Composable
fun RecoveryPrompt(state: RecoveryUiState, dispatch: (CaptureIntent) -> Unit, modifier: Modifier = Modifier) {
    val c = SongPadTheme.colors
    when (state) {
        RecoveryUiState.None -> Unit
        is RecoveryUiState.Prompt -> Column(modifier.fillMaxWidth().padding(Spacing.lg)) {
            val count = state.orphans.count { !it.isNoise }
            Text(
                if (count == 1) "We found an unsaved recording" else "We found $count unsaved recordings",
                style = MaterialTheme.typography.titleMedium, color = c.text,
            )
            Text(
                "About ${DurationFormat.mmss(state.totalDurationMs)} of audio survived the last session. Keep it as a take?",
                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            )
            Spacer(Modifier.height(Spacing.md))
            Row {
                PrimaryButton("Keep", onClick = { dispatch(CaptureIntent.KeepRecovered) })
                Spacer(Modifier.width(Spacing.sm))
                SecondaryButton("Discard", onClick = { dispatch(CaptureIntent.DiscardRecovered) }, destructive = true)
            }
        }
        RecoveryUiState.Restoring -> Text("Restoring…", Modifier.padding(Spacing.lg), color = c.textSecondary)
        is RecoveryUiState.Restored -> Row(modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text(
                if (state.takeIds.isEmpty()) "Nothing worth keeping was found." else "Recovered as unassigned takes — find them in your library.",
                Modifier.weight(1f), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium,
            )
            SecondaryButton("OK", onClick = { dispatch(CaptureIntent.DismissRecovered) })
        }
        is RecoveryUiState.DiscardConfirm -> ConfirmDialog(
            title = "Discard unsaved recording?",
            body = "This deletes ${DurationFormat.mmss(state.orphans.sumOf { it.durationMs })} of audio permanently.",
            confirmLabel = "Discard",
            onConfirm = { dispatch(CaptureIntent.ConfirmDiscardRecovered) },
            onDismiss = { dispatch(CaptureIntent.CancelDiscardRecovered) },
        )
    }
}
