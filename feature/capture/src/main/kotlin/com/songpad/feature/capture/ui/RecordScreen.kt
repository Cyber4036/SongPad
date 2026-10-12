package com.songpad.feature.capture.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.audio.InterruptionReason
import com.songpad.core.model.state.PermissionState
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.DurationFormat
import com.songpad.core.ui.components.ConfirmDialog
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.FilterPill
import com.songpad.core.ui.components.GlassPill
import com.songpad.core.ui.components.IconAction
import com.songpad.core.ui.components.LiveWaveform
import com.songpad.core.ui.components.PrimaryButton
import com.songpad.core.ui.components.RecordButton
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.components.SeekableWaveform
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.components.TextInputDialog
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.capture.CaptureEffect
import com.songpad.feature.capture.CaptureIntent
import com.songpad.feature.capture.CaptureState
import com.songpad.feature.capture.CaptureViewModel
import com.songpad.feature.capture.permission.MicrophonePermission
import com.songpad.feature.capture.permission.rememberMicrophonePermissionRequester

/**
 * Phase 0 §6 recording screen: mode pills, live waveform, glass-pill transport, review scrubber, save/discard.
 * The screen renders state and emits intents; every rule lives in [CaptureViewModel].
 */
@Composable
fun RecordScreen(
    songId: String?,
    onNavigateToSong: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reviewPeaks by viewModel.reviewPeaks.collectAsStateWithLifecycle()
    val requester = rememberMicrophonePermissionRequester { viewModel.dispatch(CaptureIntent.PermissionResult(it)) }

    LaunchedEffect(songId) {
        viewModel.bind(songId)
        viewModel.dispatch(CaptureIntent.PermissionResult(MicrophonePermission.current(context)))
    }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CaptureEffect.NavigateToSong -> onNavigateToSong(effect.songId)
                CaptureEffect.RequestMicrophonePermission -> requester.request()
                CaptureEffect.OpenAppSettings -> MicrophonePermission.openAppSettings(context)
                is CaptureEffect.Message -> android.widget.Toast.makeText(context, effect.text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Leaving pops this route and clears the ViewModel, which would strand a live take (or cancel a save
    // mid-commit). Phase 0 §5: preserve the recording; discarding needs confirmation when loss is possible.
    // So while a take is open, back/close goes through the same discard confirmation as the Discard button.
    val takeOpen = state.holdsOpenTake()
    val close = {
        when {
            state is CaptureState.Saving -> Unit
            state is CaptureState.Failed -> {
                viewModel.dispatch(CaptureIntent.Reset)
                onClose()
            }
            takeOpen -> viewModel.dispatch(CaptureIntent.RequestDiscard)
            else -> onClose()
        }
    }
    BackHandler(enabled = takeOpen, onBack = close)

    RecordContent(
        state = state,
        reviewPeaks = reviewPeaks,
        amplitudes = viewModel.amplitudes,
        dispatch = viewModel::dispatch,
        onClose = close,
        onOpenSettings = { MicrophonePermission.openAppSettings(context) },
        onRequestPermission = { requester.request() },
    )
}

private fun CaptureState.holdsOpenTake(): Boolean = this is CaptureState.Recording || this is CaptureState.Paused ||
    this is CaptureState.Reviewing || this is CaptureState.Interrupted || this is CaptureState.Saving

@Composable
fun RecordContent(
    state: CaptureState,
    reviewPeaks: FloatArray,
    amplitudes: kotlinx.coroutines.flow.SharedFlow<Float>,
    dispatch: (CaptureIntent) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val c = SongPadTheme.colors
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SongPadTopBar(
            title = when (state) {
                is CaptureState.Recording -> "Recording"
                is CaptureState.Paused, is CaptureState.Reviewing -> "Review"
                is CaptureState.Interrupted -> "Interrupted"
                else -> "Record"
            },
            onBack = onClose,
            backContentDescription = "Close",
        )

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state) {
                is CaptureState.Ready -> ReadyBody(state, dispatch)
                is CaptureState.Recording -> {
                    LiveWaveform(amplitudes, isRecording = true, Modifier.fillMaxWidth().height(Dimens.waveformHeight))
                    Elapsed(state.elapsedMs, recording = true)
                    if (state.type != TakeType.VOICE) {
                        Spacer(Modifier.height(Spacing.md))
                        PianoKeyboard(dispatch)
                    }
                }
                is CaptureState.Paused -> {
                    SeekableWaveform(reviewPeaks, state.review.progress, { dispatch(CaptureIntent.Scrub(it)) }, Modifier.fillMaxWidth().height(Dimens.waveformHeight))
                    Elapsed(state.review.positionMs, recording = false, total = state.elapsedMs)
                }
                is CaptureState.Reviewing -> {
                    SeekableWaveform(reviewPeaks, state.review.progress, { dispatch(CaptureIntent.Scrub(it)) }, Modifier.fillMaxWidth().height(Dimens.waveformHeight))
                    Elapsed(state.review.positionMs, recording = false, total = state.elapsedMs)
                }
                is CaptureState.Interrupted -> {
                    Text(interruptionCopy(state.reason), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                    Spacer(Modifier.height(Spacing.md))
                    Elapsed(state.elapsedMs, recording = false)
                }
                is CaptureState.Saving -> Text("Saving…", style = MaterialTheme.typography.titleMedium, color = c.textSecondary)
                is CaptureState.Saved -> Text("Saved", style = MaterialTheme.typography.titleMedium, color = c.success)
                is CaptureState.Discarded -> EmptyState(
                    icon = SongPadIcons.Mic, title = "Recording discarded", body = "Nothing was kept.",
                    actionLabel = "Record again", onAction = { dispatch(CaptureIntent.Reset) },
                )
                is CaptureState.Failed -> EmptyState(
                    icon = SongPadIcons.Mic, title = "Recording failed", body = state.error.message,
                    actionLabel = "Try again", onAction = { dispatch(CaptureIntent.Reset) },
                )
                is CaptureState.PermissionDenied -> EmptyState(
                    icon = SongPadIcons.Mic,
                    title = "SongPad needs the microphone",
                    body = "Recording captures audio only while this screen is open. Nothing leaves your device.",
                    actionLabel = if (state.permanent) "Open settings" else "Allow microphone",
                    onAction = if (state.permanent) onOpenSettings else onRequestPermission,
                )
                is CaptureState.LowStorage -> EmptyState(
                    icon = SongPadIcons.Mic,
                    title = "Not enough free space",
                    body = "About ${state.freeBytes / (1024 * 1024)} MB is free. Recording needs roughly 6 MB per minute; free up space and try again.",
                    actionLabel = "Try again", onAction = { dispatch(CaptureIntent.Reset) },
                )
            }
        }

        Transport(state, dispatch, onSave = {
            if (state.songId == null) showSaveDialog = true
            else dispatch(CaptureIntent.Save(takeName = "", newSongName = null))
        })
    }

    if (showSaveDialog) {
        TextInputDialog(
            title = "Name your song",
            label = "Song name",
            initialValue = "",
            confirmLabel = "Save",
            onConfirm = { name -> showSaveDialog = false; dispatch(CaptureIntent.Save(takeName = "", newSongName = name)) },
            onDismiss = { showSaveDialog = false },
        )
    }
    val confirmDiscard = when (state) {
        is CaptureState.Paused -> state.confirmDiscard
        is CaptureState.Reviewing -> state.confirmDiscard
        is CaptureState.Interrupted -> state.confirmDiscard
        else -> false
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard this recording?",
            body = "The audio you just recorded will be deleted. This can't be undone.",
            confirmLabel = "Discard",
            onConfirm = { dispatch(CaptureIntent.ConfirmDiscard) },
            onDismiss = { dispatch(CaptureIntent.CancelDiscard) },
        )
    }
}

@Composable
private fun ReadyBody(state: CaptureState.Ready, dispatch: (CaptureIntent) -> Unit) {
    val c = SongPadTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterPill("Voice", selected = state.type == TakeType.VOICE, onClick = { dispatch(CaptureIntent.SelectType(TakeType.VOICE)) }, icon = SongPadIcons.Mic)
        FilterPill("Piano", selected = state.type == TakeType.PIANO, onClick = { dispatch(CaptureIntent.SelectType(TakeType.PIANO)) }, icon = SongPadIcons.Piano)
        FilterPill("Voice + Piano", selected = state.type == TakeType.VOICE_PIANO, onClick = { dispatch(CaptureIntent.SelectType(TakeType.VOICE_PIANO)) })
    }
    Spacer(Modifier.height(Spacing.xl))
    Text(
        when (state.qualityMode) { QualityMode.STANDARD -> "Standard quality · ~1 MB per minute"; QualityMode.LOSSLESS -> "Lossless quality · ~4 MB per minute" },
        style = MaterialTheme.typography.bodySmall, color = c.textTertiary,
    )
    if (state.permission != PermissionState.Granted) {
        Spacer(Modifier.height(Spacing.sm))
        Text("Tap Record to allow the microphone.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun Elapsed(ms: Long, recording: Boolean, total: Long? = null) {
    val c = SongPadTheme.colors
    Spacer(Modifier.height(Spacing.lg))
    Text(
        if (total != null) "${DurationFormat.mmss(ms)} / ${DurationFormat.mmss(total)}" else DurationFormat.mmss(ms),
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        color = if (recording) c.record else c.text,
    )
}

/** Phase 0 §6: the glass pill holds the transport; Record protrudes at the centre. */
@Composable
private fun Transport(state: CaptureState, dispatch: (CaptureIntent) -> Unit, onSave: () -> Unit) {
    val c = SongPadTheme.colors
    Box(Modifier.fillMaxWidth().padding(Spacing.screen), contentAlignment = Alignment.Center) {
        when (state) {
            is CaptureState.Ready -> RecordButton(isRecording = false, onClick = { dispatch(CaptureIntent.Start) })
            is CaptureState.Recording -> GlassPill {
                IconAction(Icons.Filled.Delete, "Discard", onClick = { dispatch(CaptureIntent.RequestDiscard) }, tint = c.destructive)
                Spacer(Modifier.width(Spacing.md))
                RecordButton(isRecording = true, onClick = { dispatch(CaptureIntent.Pause) }, size = Dimens.recordFabCompact)
                Spacer(Modifier.width(Spacing.md))
                PrimaryButton("Save", onClick = onSave)
            }
            is CaptureState.Paused, is CaptureState.Reviewing -> {
                val playing = state is CaptureState.Reviewing && state.review.isPlaying
                GlassPill {
                    IconAction(Icons.Filled.Delete, "Discard", onClick = { dispatch(CaptureIntent.RequestDiscard) }, tint = c.destructive)
                    Spacer(Modifier.width(Spacing.sm))
                    IconAction(
                        if (playing) SongPadIcons.Pause else Icons.Filled.PlayArrow,
                        if (playing) "Pause review" else "Play review",
                        onClick = { dispatch(if (playing) CaptureIntent.PauseReview else CaptureIntent.PlayReview) },
                        filled = true,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    RecordButton(isRecording = false, onClick = { dispatch(CaptureIntent.Resume) }, size = Dimens.recordFabCompact)
                    Spacer(Modifier.width(Spacing.md))
                    PrimaryButton("Save", onClick = onSave)
                }
            }
            is CaptureState.Interrupted -> GlassPill {
                SecondaryButton("Discard", onClick = { dispatch(CaptureIntent.RequestDiscard) }, destructive = true)
                Spacer(Modifier.width(Spacing.sm))
                SecondaryButton("Resume", onClick = { dispatch(CaptureIntent.Resume) })
                Spacer(Modifier.width(Spacing.sm))
                PrimaryButton("Save", onClick = onSave)
            }
            else -> Spacer(Modifier.height(Dimens.recordFab))
        }
    }
}

private fun interruptionCopy(reason: InterruptionReason): String = when (reason) {
    InterruptionReason.FOCUS_LOSS_TRANSIENT -> "Recording paused — another app took the audio. Resume when you're ready."
    InterruptionReason.FOCUS_LOSS_PERMANENT -> "Recording stopped — another app took over audio. Your take so far is safe."
    InterruptionReason.HEADPHONES_UNPLUGGED -> "Recording paused — headphones were unplugged."
    InterruptionReason.BLUETOOTH_DISCONNECTED -> "Recording paused — a Bluetooth device disconnected."
    InterruptionReason.LOW_STORAGE -> "Recording paused — the device is almost out of storage."
}


