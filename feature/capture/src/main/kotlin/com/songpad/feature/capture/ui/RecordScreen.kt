package com.songpad.feature.capture.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.audio.InterruptionReason
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.song.Song
import com.songpad.core.model.state.PermissionState
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.DurationFormat
import com.songpad.core.ui.components.ConfirmDialog
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.GlassPill
import com.songpad.core.ui.components.IconAction
import com.songpad.core.ui.components.LayerTogglePill
import com.songpad.core.ui.components.LiveWaveform
import com.songpad.core.ui.components.PrimaryButton
import com.songpad.core.ui.components.RecordButton
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.components.SeekableWaveform
import com.songpad.core.ui.components.SheetOptionRow
import com.songpad.core.ui.components.SongPadBottomSheet
import com.songpad.core.ui.components.TagChip
import com.songpad.core.ui.components.TagTone
import com.songpad.core.ui.components.TextInputDialog
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadFonts
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.capture.CaptureEffect
import com.songpad.feature.capture.CaptureIntent
import com.songpad.feature.capture.CaptureState
import com.songpad.feature.capture.CaptureViewModel
import com.songpad.feature.capture.permission.MicrophonePermission
import com.songpad.feature.capture.permission.rememberMicrophonePermissionRequester
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Phase 0 §6 recording screen redesigned to match Capture.html:
 * - Independent Voice, Piano, and Lyrics layer toggles
 * - Editorial context header ("READY WHEN YOU ARE" / "RECORDING" / "PAUSED — TAP WAVEFORM TO SCRUB")
 * - Live Lyrics section editor while recording (with "Which song?" picker sheet for loose ideas)
 * - Docked Piano with Key Size zoom (Small / Medium / Large) and Octave stepper
 * - Compact floating Glass-Pill transport bar with inline waveform and tabular timer
 */
@Composable
fun RecordScreen(
    songId: String?,
    onNavigateToSong: (String) -> Unit,
    onClose: () -> Unit,
    initialType: TakeType = TakeType.VOICE,
    autoStart: Boolean = false,
    openLyrics: Boolean = false,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reviewPeaks by viewModel.reviewPeaks.collectAsStateWithLifecycle()
    val boundSongId by viewModel.boundSongId.collectAsStateWithLifecycle()
    val activeSongName by viewModel.activeSongName.collectAsStateWithLifecycle()
    val activeLyrics by viewModel.activeLyrics.collectAsStateWithLifecycle()
    val availableSongs by viewModel.availableSongs.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val requester = rememberMicrophonePermissionRequester { viewModel.dispatch(CaptureIntent.PermissionResult(it)) }
    val currentOnNavigateToSong by rememberUpdatedState(onNavigateToSong)
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(songId, initialType, autoStart) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is CaptureEffect.NavigateToSong -> currentOnNavigateToSong(effect.songId)
                    CaptureEffect.Finished -> currentOnClose()
                    CaptureEffect.RequestMicrophonePermission -> requester.request()
                    CaptureEffect.OpenAppSettings -> MicrophonePermission.openAppSettings(context)
                    is CaptureEffect.Message -> android.widget.Toast.makeText(context, effect.text, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        viewModel.bind(songId = songId, initialType = initialType, autoStart = autoStart)
        viewModel.dispatch(CaptureIntent.PermissionResult(MicrophonePermission.current(context)))
    }

    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state is CaptureState.PermissionDenied) {
                if (MicrophonePermission.current(context) == PermissionState.Granted) {
                    viewModel.dispatch(CaptureIntent.PermissionResult(PermissionState.Granted))
                }
            }
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushLyricsDrafts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushLyricsDrafts()
        }
    }

    val takeOpen = state.holdsOpenTake()
    val close = {
        when {
            state is CaptureState.Saving -> Unit
            state is CaptureState.Failed -> {
                viewModel.dispatch(CaptureIntent.Reset)
                viewModel.finishStandaloneLyricsSession { onClose() }
            }
            takeOpen -> viewModel.dispatch(CaptureIntent.RequestDiscard)
            else -> viewModel.finishStandaloneLyricsSession { onClose() }
        }
    }
    BackHandler(enabled = takeOpen, onBack = close)

    RecordContent(
        state = state,
        reviewPeaks = reviewPeaks,
        amplitudes = viewModel.amplitudes,
        boundSongId = boundSongId,
        activeSongName = activeSongName,
        activeLyrics = activeLyrics,
        availableSongs = availableSongs,
        openLyricsInitially = openLyrics,
        onEnsureStandaloneLyrics = viewModel::ensureStandaloneLyricsForCapture,
        onSelectSongForLyrics = viewModel::selectSongForCapture,
        onCreateSongForLyrics = { name, after -> viewModel.createSongForCapture(name, after) },
        onEditLyricsSection = viewModel::editLyricsSection,
        onInsertLyricsSection = viewModel::insertLyricsSection,
        onRetagLyricsSection = viewModel::retagLyricsSection,
        onDeleteLyricsSection = viewModel::deleteLyricsSection,
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
    amplitudes: SharedFlow<Float>,
    dispatch: (CaptureIntent) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    boundSongId: String? = state.songId,
    activeSongName: String? = null,
    activeLyrics: LyricsDocument? = null,
    availableSongs: List<Song> = emptyList(),
    openLyricsInitially: Boolean = false,
    onEnsureStandaloneLyrics: () -> Unit = {},
    onSelectSongForLyrics: (String) -> Unit = {},
    onCreateSongForLyrics: (String, (String) -> Unit) -> Unit = { _, _ -> },
    onEditLyricsSection: (String, String) -> Unit = { _, _ -> },
    onInsertLyricsSection: (SectionTag, String?) -> Unit = { _, _ -> },
    onRetagLyricsSection: (String, SectionTag, String?) -> Unit = { _, _, _ -> },
    onDeleteLyricsSection: (String) -> Unit = {},
) {
    val c = SongPadTheme.colors
    var octaveShift by rememberSaveable { mutableIntStateOf(0) }
    var keySizeOrd by rememberSaveable { mutableIntStateOf(PianoKeySize.MEDIUM.ordinal) }
    val keySize = PianoKeySize.entries[keySizeOrd.coerceIn(0, PianoKeySize.entries.lastIndex)]

    var lyricsLayer by rememberSaveable { mutableStateOf(openLyricsInitially) }
    var initialLyricsHandled by rememberSaveable { mutableStateOf(false) }
    var showPickSongSheet by remember { mutableStateOf(false) }
    var showNewSongDialog by remember { mutableStateOf(false) }
    var retaggingSection by remember { mutableStateOf<Section?>(null) }
    var showAddSectionSheet by remember { mutableStateOf(false) }

    LaunchedEffect(openLyricsInitially, boundSongId) {
        if (openLyricsInitially && !initialLyricsHandled) {
            initialLyricsHandled = true
            lyricsLayer = true
            if (boundSongId == null) {
                onEnsureStandaloneLyrics()
            }
        }
    }

    LaunchedEffect(lyricsLayer, boundSongId, activeLyrics) {
        if (lyricsLayer) {
            if (boundSongId == null && activeLyrics == null) {
                onEnsureStandaloneLyrics()
            } else if (activeLyrics != null && activeLyrics.sections.isEmpty()) {
                onInsertLyricsSection(SectionTag.VERSE, null)
            }
        }
    }

    val voiceActive = state.type == TakeType.VOICE || state.type == TakeType.VOICE_PIANO
    val pianoActive = state.type == TakeType.PIANO || state.type == TakeType.VOICE_PIANO

    fun toggleVoice() {
        val nextType = when (state.type) {
            TakeType.VOICE -> TakeType.PIANO
            TakeType.PIANO -> TakeType.VOICE_PIANO
            TakeType.VOICE_PIANO -> TakeType.PIANO
        }
        dispatch(CaptureIntent.SelectType(nextType))
    }

    fun togglePiano() {
        val nextType = when (state.type) {
            TakeType.VOICE -> TakeType.VOICE_PIANO
            TakeType.PIANO -> TakeType.VOICE
            TakeType.VOICE_PIANO -> TakeType.VOICE
        }
        dispatch(CaptureIntent.SelectType(nextType))
    }

    fun toggleLyrics() {
        if (lyricsLayer) {
            lyricsLayer = false
        } else {
            lyricsLayer = true
            if (boundSongId == null && activeLyrics == null) {
                onEnsureStandaloneLyrics()
            } else if (activeLyrics == null || activeLyrics.sections.isEmpty()) {
                onInsertLyricsSection(SectionTag.VERSE, null)
            }
        }
    }

    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val compactForLyricsKeyboard = isImeVisible && lyricsLayer

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // Top Bar: [✕ Close] + [Voice] [Piano] [Lyrics] layer pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = if (compactForLyricsKeyboard) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconAction(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                outlined = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LayerTogglePill(
                    label = "Voice",
                    active = voiceActive,
                    onClick = { toggleVoice() },
                )
                LayerTogglePill(
                    label = "Piano",
                    active = pianoActive,
                    onClick = { togglePiano() },
                )
                LayerTogglePill(
                    label = "Lyrics",
                    active = lyricsLayer,
                    onClick = { toggleLyrics() },
                )
            }
        }

        // Context Header ("READY WHEN YOU ARE" / "RECORDING" + Song name or "Loose idea")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = if (compactForLyricsKeyboard) 2.dp else 4.dp),
        ) {
            if (!compactForLyricsKeyboard) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (state is CaptureState.Recording) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(c.record),
                        )
                    }
                    Text(
                        text = when (state) {
                            is CaptureState.Recording -> "RECORDING"
                            is CaptureState.Paused, is CaptureState.Reviewing -> "PAUSED — TAP WAVEFORM TO SCRUB"
                            is CaptureState.Interrupted -> "INTERRUPTED"
                            else -> "READY WHEN YOU ARE"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            fontSize = 11.sp,
                        ),
                        color = when (state) {
                            is CaptureState.Recording -> c.record
                            else -> c.textTertiary
                        },
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = activeSongName
                        ?: activeLyrics?.name?.takeIf { lyricsLayer }
                        ?: if (boundSongId != null) "Song take" else "Loose idea",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = SongPadFonts.heading,
                        fontSize = if (compactForLyricsKeyboard) 18.sp else 22.sp,
                    ),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (lyricsLayer && (state is CaptureState.Recording || state is CaptureState.Paused || state is CaptureState.Reviewing)) {
                    val elapsed = when (state) {
                        is CaptureState.Recording -> state.elapsedMs
                        is CaptureState.Paused -> state.review.positionMs.takeIf { it > 0 } ?: state.elapsedMs
                        is CaptureState.Reviewing -> state.review.positionMs
                        else -> 0L
                    }
                    Surface(
                        shape = RoundedCornerShape(Radii.pill),
                        color = if (state is CaptureState.Recording) c.record.copy(alpha = 0.14f) else c.surface,
                        border = BorderStroke(1.dp, if (state is CaptureState.Recording) c.record.copy(alpha = 0.4f) else c.divider),
                    ) {
                        Text(
                            text = DurationFormat.mmss(elapsed),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = SongPadFonts.heading,
                                fontSize = 14.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            color = if (state is CaptureState.Recording) c.record else c.text,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else if (lyricsLayer && state is CaptureState.Ready && activeLyrics?.hasNonEmptyText == true) {
                    PrimaryButton(
                        text = "Done",
                        onClick = onClose,
                        compact = true,
                    )
                }
            }
        }

        // Main stage: either terminal/error state, live Lyrics layer, or Waveform + Timer stage
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (state) {
                is CaptureState.Saving -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Saving…", style = MaterialTheme.typography.titleMedium, color = c.textSecondary)
                }
                is CaptureState.Saved -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Saved", style = MaterialTheme.typography.titleMedium, color = c.success)
                }
                is CaptureState.Discarded -> EmptyState(
                    icon = SongPadIcons.Mic,
                    title = "Recording discarded",
                    body = "Nothing was kept.",
                    actionLabel = "Record again",
                    onAction = { dispatch(CaptureIntent.Reset) },
                )
                is CaptureState.Failed -> EmptyState(
                    icon = SongPadIcons.Mic,
                    title = "Recording failed",
                    body = state.error.message,
                    actionLabel = "Try again",
                    onAction = { dispatch(CaptureIntent.Reset) },
                )
                is CaptureState.PermissionDenied -> EmptyState(
                    icon = SongPadIcons.Mic,
                    title = "SongPad needs the microphone",
                    body = "Recording captures audio only while this screen is open. Nothing leaves your device.",
                    actionLabel = if (state.permanent) "Open settings" else "Allow microphone",
                    onAction = { dispatch(CaptureIntent.Start) },
                )
                is CaptureState.LowStorage -> EmptyState(
                    icon = SongPadIcons.Mic,
                    title = "Not enough free space",
                    body = "About ${state.freeBytes / (1024 * 1024)} MB is free. Recording needs roughly 6 MB per minute; free up space and try again.",
                    actionLabel = "Try again",
                    onAction = { dispatch(CaptureIntent.Reset) },
                )
                else -> {
                    if (lyricsLayer) {
                        LiveLyricsStage(
                            lyrics = activeLyrics,
                            onEditSection = onEditLyricsSection,
                            onRetagSection = { retaggingSection = it },
                            onDeleteSection = onDeleteLyricsSection,
                            onAddSection = { showAddSectionSheet = true },
                        )
                    } else {
                        WaveformTimerStage(
                            state = state,
                            amplitudes = amplitudes,
                            reviewPeaks = reviewPeaks,
                            compact = pianoActive,
                            onScrub = { dispatch(CaptureIntent.Scrub(it)) },
                        )
                    }
                }
            }
        }

        // Docked Piano Keyboard when Piano layer is active (hidden while software keyboard is open)
        val showPiano = !isImeVisible && pianoActive && (state is CaptureState.Ready || state is CaptureState.Recording || state is CaptureState.Paused || state is CaptureState.Reviewing)
        if (showPiano) {
            HorizontalDivider(color = c.divider, thickness = 1.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(top = 8.dp, bottom = 4.dp),
            ) {
                PianoControlsBar(
                    keySize = keySize,
                    onKeySizeSmaller = { if (keySizeOrd > 0) keySizeOrd-- },
                    onKeySizeLarger = { if (keySizeOrd < PianoKeySize.entries.lastIndex) keySizeOrd++ },
                    octaveShift = octaveShift,
                    onOctaveDown = { if (octaveShift > -2) octaveShift-- },
                    onOctaveUp = { if (octaveShift < 2) octaveShift++ },
                )
                Spacer(Modifier.height(6.dp))
                PianoKeyboard(
                    dispatch = dispatch,
                    startNote = 48 + 12 * octaveShift,
                    octaves = 2,
                    keySize = keySize,
                )
            }
        }

        // Bottom Transport (80.dp pulsing Record FAB when Ready; Floating Glass-Pill when Recording/Paused/Reviewing).
        // Hide the idle 80.dp Record FAB while typing lyrics with the software keyboard open so the active section has full room.
        val showTransport = !(compactForLyricsKeyboard && (state is CaptureState.Ready || state is CaptureState.PermissionDenied))
        if (showTransport) {
            Transport(
                state = state,
                amplitudes = amplitudes,
                reviewPeaks = reviewPeaks,
                dispatch = dispatch,
                onSave = {
                    dispatch(CaptureIntent.Save(takeName = "", newSongName = null))
                },
            )
        }
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

    if (showPickSongSheet) {
        SongPadBottomSheet(
            onDismiss = { showPickSongSheet = false },
            title = "Which song?",
            subtitle = "Lyrics belong to a song. Pick one or start a new one.",
        ) {
            SheetOptionRow(
                label = "+ New song…",
                accent = true,
                onClick = {
                    showPickSongSheet = false
                    showNewSongDialog = true
                },
            )
            availableSongs.forEach { s ->
                SheetOptionRow(
                    label = s.name,
                    meta = "${s.sectionCount} section${if (s.sectionCount == 1) "" else "s"}",
                    onClick = {
                        onSelectSongForLyrics(s.id)
                        showPickSongSheet = false
                        lyricsLayer = true
                    },
                )
            }
        }
    }

    if (showNewSongDialog) {
        TextInputDialog(
            title = "New song",
            label = "Song name",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                showNewSongDialog = false
                onCreateSongForLyrics(name) {
                    lyricsLayer = true
                }
            },
            onDismiss = { showNewSongDialog = false },
        )
    }

    retaggingSection?.let { sec ->
        RecordSectionTagSheet(
            title = "Section tag",
            activeTag = sec.tag,
            onSelect = { tag, custom ->
                onRetagLyricsSection(sec.id, tag, custom)
                retaggingSection = null
            },
            onDismiss = { retaggingSection = null },
        )
    }

    if (showAddSectionSheet) {
        RecordSectionTagSheet(
            title = "Add section",
            activeTag = null,
            onSelect = { tag, custom ->
                onInsertLyricsSection(tag, custom)
                showAddSectionSheet = false
            },
            onDismiss = { showAddSectionSheet = false },
        )
    }
}

@Composable
private fun WaveformTimerStage(
    state: CaptureState,
    amplitudes: SharedFlow<Float>,
    reviewPeaks: FloatArray,
    compact: Boolean,
    onScrub: (Float) -> Unit,
) {
    val c = SongPadTheme.colors
    val elapsedMs = when (state) {
        is CaptureState.Recording -> state.elapsedMs
        is CaptureState.Paused -> state.review.positionMs.takeIf { it > 0 } ?: state.elapsedMs
        is CaptureState.Reviewing -> state.review.positionMs
        is CaptureState.Interrupted -> state.elapsedMs
        else -> 0L
    }
    val totalMs = when (state) {
        is CaptureState.Paused -> state.elapsedMs
        is CaptureState.Reviewing -> state.elapsedMs
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = DurationFormat.mmss(elapsedMs),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = SongPadFonts.heading,
                fontSize = if (compact) 40.sp else 52.sp,
                lineHeight = if (compact) 44.sp else 56.sp,
                fontFeatureSettings = "tnum",
            ),
            color = if (state is CaptureState.Recording) c.record else c.text,
        )
        if (totalMs != null && (state is CaptureState.Paused || state is CaptureState.Reviewing)) {
            Text(
                text = "of ${DurationFormat.mmss(totalMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
            )
        }
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

        when (state) {
            is CaptureState.Paused -> SeekableWaveform(
                peaks = reviewPeaks,
                progress = state.review.progress,
                onSeek = onScrub,
                modifier = Modifier.fillMaxWidth().height(if (compact) 56.dp else Dimens.waveformHeight),
            )
            is CaptureState.Reviewing -> SeekableWaveform(
                peaks = reviewPeaks,
                progress = state.review.progress,
                onSeek = onScrub,
                modifier = Modifier.fillMaxWidth().height(if (compact) 56.dp else Dimens.waveformHeight),
            )
            is CaptureState.Interrupted -> Text(
                text = interruptionCopy(state.reason),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            else -> LiveWaveform(
                amplitudes = amplitudes,
                isRecording = state is CaptureState.Recording,
                modifier = Modifier.fillMaxWidth().height(if (compact) 56.dp else Dimens.waveformHeight),
            )
        }

        if (state is CaptureState.Ready) {
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Text(
                text = when {
                    state.permission != PermissionState.Granted -> "Tap Record to allow the microphone."
                    state.type == TakeType.VOICE_PIANO -> "Use headphones for best Voice + Piano quality"
                    state.qualityMode == QualityMode.LOSSLESS -> "Lossless FLAC · tap record and play"
                    else -> "Tap record and play — name it later, or don't"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LiveLyricsStage(
    lyrics: LyricsDocument?,
    onEditSection: (String, String) -> Unit,
    onRetagSection: (Section) -> Unit,
    onDeleteSection: (String) -> Unit,
    onAddSection: () -> Unit,
) {
    val c = SongPadTheme.colors
    val sorted = remember(lyrics) { lyrics?.sections?.sortedBy { it.sortOrder }.orEmpty() }
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    var focusedSectionId by remember { mutableStateOf<String?>(null) }
    var lastReturnedSectionId by remember { mutableStateOf<String?>(null) }
    var imeWasVisible by remember { mutableStateOf(false) }
    var knownSectionIds by remember { mutableStateOf<Set<String>?>(null) }
    var autoFocusSectionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isImeVisible) {
        if (imeWasVisible && !isImeVisible) {
            if (focusedSectionId != null) {
                lastReturnedSectionId = focusedSectionId
            }
            focusedSectionId = null
            focusManager.clearFocus()
        }
        imeWasVisible = isImeVisible
    }

    LaunchedEffect(sorted.map { it.id }) {
        val currentIds = sorted.map { it.id }.toSet()
        val prev = knownSectionIds
        if (prev != null && currentIds.size > prev.size) {
            val addedId = sorted.lastOrNull { it.id !in prev }?.id
            if (addedId != null) {
                focusedSectionId = addedId
                autoFocusSectionId = addedId
            }
        }
        knownSectionIds = currentIds
    }

    LaunchedEffect(focusedSectionId) {
        if (focusedSectionId != null) {
            scrollState.scrollTo(0)
        } else {
            val returnedId = lastReturnedSectionId
            if (returnedId != null) {
                lastReturnedSectionId = null
                val canonicalIndex = sorted.indexOfFirst { it.id == returnedId }
                if (canonicalIndex >= sorted.lastIndex && sorted.size > 1) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
        }
    }

    val displaySections = remember(sorted, focusedSectionId) {
        val active = sorted.firstOrNull { it.id == focusedSectionId }
        if (active != null) {
            listOf(active) + sorted.filterNot { it.id == active.id }
        } else {
            sorted
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState, enabled = focusedSectionId == null)
            .padding(horizontal = Spacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        displaySections.forEach { sec ->
            key(sec.id) {
                val isLevitated = focusedSectionId == sec.id
                val isDimmed = focusedSectionId != null && !isLevitated
                var text by remember(sec.id) { mutableStateOf(sec.bodyText) }
                LaunchedEffect(sec.bodyText) {
                    if (!isLevitated && text != sec.bodyText) {
                        text = sec.bodyText
                    }
                }
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(autoFocusSectionId == sec.id) {
                    if (autoFocusSectionId == sec.id) {
                        runCatching { focusRequester.requestFocus() }
                        autoFocusSectionId = null
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = c.surface,
                    border = BorderStroke(if (isLevitated) 1.5.dp else 1.dp, if (isLevitated) c.accent else c.divider),
                    shadowElevation = if (isLevitated) 6.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isDimmed) 0.45f else 1f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TagChip(
                                    text = sec.displayLabel,
                                    tone = TagTone.Accent,
                                    onClick = { onRetagSection(sec) },
                                )
                                if (isLevitated) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.8.sp,
                                        ),
                                        color = c.accent,
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (isLevitated) {
                                    Surface(
                                        onClick = {
                                            lastReturnedSectionId = sec.id
                                            focusedSectionId = null
                                            focusManager.clearFocus()
                                        },
                                        shape = RoundedCornerShape(Radii.pill),
                                        color = c.accent,
                                        contentColor = c.onAccent,
                                    ) {
                                        Text(
                                            text = "Done",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                            ),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { onDeleteSection(sec.id) },
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Close, "Delete section", tint = c.textTertiary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it; onEditSection(sec.id, it) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = c.text,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                            ),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            minLines = if (isLevitated) 4 else 2,
                            maxLines = if (isLevitated) 6 else Int.MAX_VALUE,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        focusedSectionId = sec.id
                                    }
                                },
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth()) {
                                    if (text.isEmpty()) {
                                        Text(
                                            text = "Write while you play…",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                            color = c.textTertiary,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }
        }
        Surface(
            onClick = {
                focusManager.clearFocus()
                focusedSectionId = null
                onAddSection()
            },
            shape = RoundedCornerShape(Radii.pill),
            color = c.accent100,
            contentColor = c.accent800,
        ) {
            Text(
                text = "+ Section",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PianoControlsBar(
    keySize: PianoKeySize,
    onKeySizeSmaller: () -> Unit,
    onKeySizeLarger: () -> Unit,
    octaveShift: Int,
    onOctaveDown: () -> Unit,
    onOctaveUp: () -> Unit,
) {
    val c = SongPadTheme.colors
    val baseOct = 3 + octaveShift
    val rangeLabel = "C$baseOct–B${baseOct + 1}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Key size zoom pill stepper
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "KEYS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp),
                color = c.textSecondary,
            )
            PillStepper(
                value = keySize.label,
                canDecrement = keySize != PianoKeySize.SMALL,
                canIncrement = keySize != PianoKeySize.LARGE,
                onDecrement = onKeySizeSmaller,
                onIncrement = onKeySizeLarger,
            )
        }

        // Octave pill stepper
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "OCTAVE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp),
                color = c.textSecondary,
            )
            PillStepper(
                value = rangeLabel,
                canDecrement = octaveShift > -2,
                canIncrement = octaveShift < 2,
                onDecrement = onOctaveDown,
                onIncrement = onOctaveUp,
            )
        }
    }
}

@Composable
private fun PillStepper(
    value: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val c = SongPadTheme.colors
    Surface(
        shape = RoundedCornerShape(Radii.pill),
        color = c.bg,
        border = BorderStroke(1.dp, c.divider),
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                onClick = onDecrement,
                enabled = canDecrement,
                shape = CircleShape,
                color = c.surface,
                modifier = Modifier.size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "−",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (canDecrement) c.text else c.textTertiary,
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = c.text,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Surface(
                onClick = onIncrement,
                enabled = canIncrement,
                shape = CircleShape,
                color = c.surface,
                modifier = Modifier.size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (canIncrement) c.text else c.textTertiary,
                    )
                }
            }
        }
    }
}

/** Phase 0 §6: 80.dp pulsing Record FAB when idle; floating translucent Glass-Pill when recording/paused/reviewing. */
@Composable
private fun Transport(
    state: CaptureState,
    amplitudes: SharedFlow<Float>,
    reviewPeaks: FloatArray,
    dispatch: (CaptureIntent) -> Unit,
    onSave: () -> Unit,
) {
    val c = SongPadTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is CaptureState.Ready, is CaptureState.PermissionDenied -> RecordButton(
                isRecording = false,
                onClick = { dispatch(CaptureIntent.Start) },
                size = Dimens.recordFabLarge,
                pulseWhenIdle = state is CaptureState.Ready,
                haloWidth = 6.dp,
            )
            is CaptureState.Recording -> GlassPill(Modifier.fillMaxWidth()) {
                // ✕ Discard
                Surface(
                    onClick = { dispatch(CaptureIntent.RequestDiscard) },
                    shape = CircleShape,
                    color = c.surface,
                    border = BorderStroke(1.dp, c.divider),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, "Discard", tint = c.destructive, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                // ✓ Save pill
                Surface(
                    onClick = onSave,
                    shape = RoundedCornerShape(Radii.pill),
                    color = c.accent,
                    contentColor = c.onAccent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(SongPadIcons.Check, null, modifier = Modifier.size(14.dp))
                        Text("Save", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                // ⏸ Pause circle
                Surface(
                    onClick = { dispatch(CaptureIntent.Pause) },
                    shape = CircleShape,
                    color = c.record,
                    contentColor = c.onAccent,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(SongPadIcons.Pause, "Pause", modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Inline live waveform
                LiveWaveform(
                    amplitudes = amplitudes,
                    isRecording = true,
                    modifier = Modifier.weight(1f).height(30.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = DurationFormat.mmss(state.elapsedMs),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = c.text,
                )
            }
            is CaptureState.Paused, is CaptureState.Reviewing -> {
                val playing = state is CaptureState.Reviewing && state.review.isPlaying
                val review = when (state) {
                    is CaptureState.Paused -> state.review
                    is CaptureState.Reviewing -> state.review
                    else -> null
                }
                val elapsed = when (state) {
                    is CaptureState.Paused -> state.elapsedMs
                    is CaptureState.Reviewing -> state.elapsedMs
                    else -> 0L
                }
                GlassPill(Modifier.fillMaxWidth()) {
                    // ✕ Discard
                    Surface(
                        onClick = { dispatch(CaptureIntent.RequestDiscard) },
                        shape = CircleShape,
                        color = c.surface,
                        border = BorderStroke(1.dp, c.divider),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Discard", tint = c.destructive, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // ✓ Save pill
                    Surface(
                        onClick = onSave,
                        shape = RoundedCornerShape(Radii.pill),
                        color = c.accent,
                        contentColor = c.onAccent,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(SongPadIcons.Check, null, modifier = Modifier.size(14.dp))
                            Text("Save", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // ▶ / ⏸ Review playback button
                    Surface(
                        onClick = { dispatch(if (playing) CaptureIntent.PauseReview else CaptureIntent.PlayReview) },
                        shape = CircleShape,
                        color = c.surface,
                        border = BorderStroke(1.dp, c.divider),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playing) SongPadIcons.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Pause review" else "Play review",
                                tint = c.text,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // Resume Record button
                    RecordButton(
                        isRecording = false,
                        onClick = { dispatch(CaptureIntent.Resume) },
                        size = 38.dp,
                        haloWidth = 3.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    // Scrubbable inline waveform
                    SeekableWaveform(
                        peaks = reviewPeaks,
                        progress = review?.progress ?: 0f,
                        onSeek = { dispatch(CaptureIntent.Scrub(it)) },
                        modifier = Modifier.weight(1f).height(30.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = DurationFormat.mmss(review?.positionMs?.takeIf { it > 0 } ?: elapsed),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = c.text,
                    )
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

@Composable
private fun RecordSectionTagSheet(
    title: String,
    activeTag: SectionTag?,
    onSelect: (SectionTag, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    SongPadBottomSheet(onDismiss = onDismiss, title = title) {
        SectionTag.entries.filter { it != SectionTag.CUSTOM }.forEach { tag ->
            SheetOptionRow(
                label = tag.label,
                meta = if (activeTag == tag) "✓" else null,
                accent = activeTag == tag,
                onClick = { onSelect(tag, null) },
            )
        }
        var custom by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                modifier = Modifier.weight(1f),
                label = { Text("Custom label") },
                singleLine = true,
            )
            SecondaryButton(
                text = "Add",
                enabled = custom.isNotBlank(),
                onClick = { onSelect(SectionTag.CUSTOM, custom.trim()) },
            )
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
