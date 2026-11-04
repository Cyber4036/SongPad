package com.songpad.feature.song.detail

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.Note
import com.songpad.core.model.note.NoteValidation
import com.songpad.core.model.song.SongActivity
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.take.Take
import com.songpad.core.model.usecase.DurationFormat
import com.songpad.core.ui.components.ConfirmDialog
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.FilterPill
import com.songpad.core.ui.components.IconAction
import com.songpad.core.ui.components.ImportBottomSheet
import com.songpad.core.ui.components.RecordButton
import com.songpad.core.ui.components.RibbonPlayer
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.components.SheetOptionRow
import com.songpad.core.ui.components.SongPadBottomSheet
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.components.TagChip
import com.songpad.core.ui.components.TagTone
import com.songpad.core.ui.components.TakeListItem
import com.songpad.core.ui.components.TextInputDialog
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadFonts
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.song.library.icon
import com.songpad.feature.song.library.label
import com.songpad.feature.song.library.relativeTimeLabel
import com.songpad.feature.song.library.resolveDisplayName
import com.songpad.feature.song.library.shortBadge
import com.songpad.feature.song.library.statusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Phase 0 §15 Song Home with Lyrics / Takes / Notes tabs, redesigned to match Capture.html. */
@Composable
fun SongDetailScreen(
    songId: String,
    initialTab: SongDetailTab,
    onBack: () -> Unit,
    onRecord: (String) -> Unit,
    onExport: (String) -> Unit,
    onSearchInSong: (String) -> Unit,
    onExportTake: (songId: String, takeId: String) -> Unit = { sId, _ -> onExport(sId) },
    onLaunchPiano: (String) -> Unit = {},
    onLaunchLyrics: (String) -> Unit = {},
    targetId: String? = null,
    viewModel: SongDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(songId) { viewModel.bind(songId, initialTab) }
    val context = LocalContext.current
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.resolveDisplayName(uri) ?: "imported_file"
            val mime = context.contentResolver.getType(uri)
            viewModel.importIntoSong(name, mime) { context.contentResolver.openInputStream(uri) }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushPendingLyricsEdits()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushPendingLyricsEdits()
        }
    }
    val recordingVoiceNote by viewModel.recordingVoiceNote.collectAsStateWithLifecycle()
    BackHandler(enabled = recordingVoiceNote) { viewModel.stopVoiceNote() }
    val guardedBack = {
        viewModel.flushPendingLyricsEdits()
        if (recordingVoiceNote) viewModel.stopVoiceNote() else onBack()
    }
    LaunchedEffect(Unit) { viewModel.navigateToLibrary.collect { onBack() } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val c = SongPadTheme.colors
    var scrollTarget by remember(songId) { mutableStateOf(targetId) }
    fun navigateToRef(tab: SongDetailTab, id: String) { viewModel.selectTab(tab); scrollTarget = id }

    when (val s = state) {
        SongDetailState.Loading -> Column(Modifier.fillMaxSize()) { SongPadTopBar(title = null, onBack = onBack) }
        SongDetailState.NotFound -> Column(Modifier.fillMaxSize()) {
            SongPadTopBar(title = null, onBack = onBack)
            EmptyState(SongPadIcons.MusicNote, "This song is gone", "It may have been deleted.", actionLabel = "Back to library", onAction = onBack)
        }
        is SongDetailState.Loaded -> {
            val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val compactHeaderForKeyboard = isImeVisible && (s.tab == SongDetailTab.LYRICS || s.tab == SongDetailTab.NOTES)
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    if (!compactHeaderForKeyboard) {
                        // Header action row matching Capture.html: [< Back] ... [Finish/Reopen] [Archive/Unarchive] [Import] [Export]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconAction(
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                onClick = { guardedBack() },
                                outlined = true,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val isFinished = s.song.status == SongStatus.FINISHED
                                val isArchived = s.song.status == SongStatus.ARCHIVED
                                StatusHeaderPill(
                                    label = if (isFinished) "Reopen" else "Finish",
                                    active = isFinished,
                                    activeBg = c.accent,
                                    activeFg = c.onAccent,
                                    onClick = viewModel::toggleFinished,
                                )
                                StatusHeaderPill(
                                    label = if (isArchived) "Unarchive" else "Archive",
                                    active = isArchived,
                                    activeBg = c.neutral800,
                                    activeFg = c.bg,
                                    onClick = viewModel::toggleArchived,
                                )
                                IconAction(
                                    icon = SongPadIcons.Upload,
                                    contentDescription = "Import into song",
                                    onClick = { viewModel.openImportDialog() },
                                    outlined = true,
                                )
                                IconAction(
                                    icon = Icons.Filled.Share,
                                    contentDescription = "Export",
                                    onClick = { onExport(s.song.id) },
                                    outlined = true,
                                )
                            }
                        }

                        // Song title + inline pencil edit button + favorite star
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = Spacing.screen, end = Spacing.screen, top = 4.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = s.song.name,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFamily = SongPadFonts.heading,
                                    fontSize = 26.sp,
                                    lineHeight = 29.sp,
                                ),
                                color = c.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clickable { viewModel.showDialog(SongDetailDialog.RenameSong) },
                            )
                            Surface(
                                onClick = { viewModel.showDialog(SongDetailDialog.RenameSong) },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = SongPadIcons.EditPencil,
                                        contentDescription = "Rename song",
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Surface(
                                onClick = viewModel::toggleFavorite,
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (s.song.isFavorite) SongPadIcons.StarFilled else SongPadIcons.StarOutline,
                                        contentDescription = "Favorite",
                                        tint = if (s.song.isFavorite) c.accent else c.textTertiary,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }

                    // Pill sub-tabs (Home, Lyrics, Takes, Notes) matching Capture.html
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = Spacing.screen, end = Spacing.screen, bottom = if (compactHeaderForKeyboard) 6.dp else 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SongDetailTab.entries.forEach { t ->
                            FilterPill(
                                text = t.label,
                                selected = s.tab == t,
                                onClick = { viewModel.selectTab(t) },
                            )
                        }
                    }

                    Box(Modifier.weight(1f)) {
                    when (s.tab) {
                        SongDetailTab.HOME -> HomeTab(
                            s = s,
                            vm = viewModel,
                            onSelectTab = viewModel::selectTab,
                        )
                        SongDetailTab.LYRICS -> LyricsTab(
                            state = s.lyrics,
                            vm = viewModel,
                            scrollTarget = scrollTarget,
                            onScrolled = { scrollTarget = null },
                        )
                        SongDetailTab.TAKES -> TakesTab(
                            state = s.takes,
                            vm = viewModel,
                            onRecord = { onRecord(s.song.id) },
                            scrollTarget = scrollTarget,
                            onScrolled = { scrollTarget = null },
                        )
                        SongDetailTab.NOTES -> NotesTab(
                            state = s.notes,
                            vm = viewModel,
                            sections = s.lyrics.documentOrNull()?.sections.orEmpty(),
                            takes = s.takes.takesOrEmpty(),
                            nowPlaying = s.nowPlaying,
                            scrollTarget = scrollTarget,
                            onScrolled = { scrollTarget = null },
                            onNavigateToRef = ::navigateToRef,
                        )
                    }
                }

                s.nowPlaying?.let { np ->
                    RibbonPlayer(
                        title = np.take.name,
                        subtitle = np.take.type.label(),
                        isPlaying = np.isPlaying,
                        progress = np.progress,
                        positionLabel = "${DurationFormat.mmss(np.positionMs)} / ${DurationFormat.mmss(np.durationMs)}",
                        peaks = np.peaks,
                        onPlayPause = { viewModel.togglePlayPause(np.take) },
                        onSeek = viewModel::seekTo,
                        onDismiss = viewModel::dismissPlayer,
                    )
                }
            }

                // Persistent Record FAB + Quick-Launch Lyrics/Piano stack on Song Detail (hidden on Notes tab, when RibbonPlayer is open, or when keyboard is open)
                val fabVisible = s.tab != SongDetailTab.NOTES && s.nowPlaying == null && !isImeVisible
                if (fabVisible) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        com.songpad.core.ui.components.CaptureQuickActions(
                            onLaunchLyrics = { onLaunchLyrics(s.song.id) },
                            onLaunchPiano = { onLaunchPiano(s.song.id) },
                        )
                        RecordButton(
                            isRecording = false,
                            onClick = { onRecord(s.song.id) },
                            size = Dimens.recordFabDetail,
                            haloWidth = 5.dp,
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        SongDetailDialog.None -> Unit
        SongDetailDialog.RenameSong -> (state as? SongDetailState.Loaded)?.let { s ->
            TextInputDialog("Rename song", "Song name", s.song.name, "Rename", onConfirm = viewModel::renameSong, onDismiss = viewModel::closeDialog)
        }
        is SongDetailDialog.TakeOptions -> (state as? SongDetailState.Loaded)?.let { s ->
            val isPlaying = s.nowPlaying?.take?.id == d.take.id && s.nowPlaying.isPlaying
            SongPadBottomSheet(
                onDismiss = viewModel::closeDialog,
                title = d.take.name,
                subtitle = "${d.take.type.label()} · ${DurationFormat.mmss(d.take.durationMs)} · ${d.take.meta.createdAt.relativeTimeLabel()}",
            ) {
                SheetOptionRow(
                    label = if (isPlaying) "Pause" else "Play",
                    onClick = { viewModel.togglePlayPause(d.take); viewModel.closeDialog() },
                )
                SheetOptionRow(
                    label = "Rename",
                    onClick = { viewModel.showDialog(SongDetailDialog.RenameTake(d.take)) },
                )
                SheetOptionRow(
                    label = "Duplicate",
                    onClick = { viewModel.duplicateTake(d.take.id) },
                )
                SheetOptionRow(
                    label = "Export take",
                    onClick = { viewModel.closeDialog(); onExportTake(s.song.id, d.take.id) },
                )
                SheetOptionRow(
                    label = "Move to another song…",
                    onClick = { viewModel.openMoveTakeDialog(d.take) },
                )
                SheetOptionRow(
                    label = "Delete take",
                    destructive = true,
                    onClick = { viewModel.showDialog(SongDetailDialog.ConfirmDeleteTake(d.take)) },
                )
            }
        }
        is SongDetailDialog.RenameTake -> TextInputDialog("Rename take", "Take name", d.take.name, "Rename", onConfirm = { viewModel.renameTake(d.take.id, it) }, onDismiss = viewModel::closeDialog)
        is SongDetailDialog.MoveTake -> SongPadBottomSheet(
            onDismiss = viewModel::closeDialog,
            title = "Move \"${d.take.name}\" to…",
        ) {
            d.songs.forEach { song ->
                SheetOptionRow(
                    label = song.name,
                    meta = "${song.takeCount} take${if (song.takeCount == 1) "" else "s"}",
                    onClick = { viewModel.moveTake(d.take.id, song.id) },
                )
            }
            if (d.songs.isEmpty()) {
                Text("No other songs available.", Modifier.padding(Spacing.lg), color = c.textSecondary)
            }
        }
        is SongDetailDialog.RetagSection -> SectionTagBottomSheet(
            title = "Section tag",
            activeTag = d.section.tag,
            onSelectTag = { tag, custom -> viewModel.retagSection(d.section.id, tag, custom) },
            onDismiss = viewModel::closeDialog,
        )
        is SongDetailDialog.ConfirmDeleteTake -> ConfirmDialog("Delete \"${d.take.name}\"?", "The take is removed from this song. Its file is purged after 7 days.", "Delete", onConfirm = { viewModel.deleteTake(d.take.id) }, onDismiss = viewModel::closeDialog)
        is SongDetailDialog.ConfirmDeleteSection -> ConfirmDialog("Delete ${d.label}?", "Its lyrics are removed. This can't be undone.", "Delete", onConfirm = { viewModel.deleteSection(d.sectionId) }, onDismiss = viewModel::closeDialog)
        is SongDetailDialog.ConfirmDeleteNote -> ConfirmDialog("Delete note?", "This can't be undone.", "Delete", onConfirm = { viewModel.deleteNote(d.noteId) }, onDismiss = viewModel::closeDialog)
        SongDetailDialog.ConfirmDeleteSong -> ConfirmDialog("Delete this song?", "Takes, lyrics and notes go with it. Files are purged after 7 days.", "Delete song", onConfirm = viewModel::deleteSong, onDismiss = viewModel::closeDialog)
        is SongDetailDialog.Import -> ImportBottomSheet(
            title = "Import into song",
            subtitle = "Add an audio recording (.mp3, .wav, .m4a, .flac) as a take, or append lyrics from a .txt file.",
            allowBundle = false,
            isBusy = d.isBusy,
            statusTitle = d.statusTitle,
            statusBody = d.statusBody,
            isError = d.isError,
            onPickBundleOrAny = { importPicker.launch(arrayOf("*/*")) },
            onPickAudio = { importPicker.launch(arrayOf("audio/*", "application/ogg", "application/flac", "*/*")) },
            onPickLyrics = { importPicker.launch(arrayOf("text/*", "*/*")) },
            onDismiss = viewModel::closeDialog,
        )
    }
}

@Composable
private fun StatusHeaderPill(
    label: String,
    active: Boolean,
    activeBg: Color,
    activeFg: Color,
    onClick: () -> Unit,
) {
    val c = SongPadTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.pill),
        color = if (active) activeBg else c.surface,
        contentColor = if (active) activeFg else c.textSecondary,
        border = BorderStroke(1.dp, if (active) activeBg else c.divider),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HomeTab(
    s: SongDetailState.Loaded,
    vm: SongDetailViewModel,
    onSelectTab: (SongDetailTab) -> Unit,
) {
    val c = SongPadTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 2.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Text(
                text = "RECENT ACTIVITY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp,
                ),
                color = c.textTertiary,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
        if (s.activity.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Empty song — hit Record or add a lyric section.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
            }
        } else {
            items(s.activity, key = { it.key() }) { a ->
                when (a) {
                    is SongActivity.TakeSaved -> ActivityFeedCard(
                        badgeText = a.take.type.shortBadge(),
                        badgeBg = c.accent100,
                        badgeFg = c.accent800,
                        title = "${a.take.name} · ${DurationFormat.mmss(a.take.durationMs)}",
                        subtitle = "${a.take.type.label()} · ${a.take.meta.createdAt.relativeTimeLabel()}",
                        onClick = { vm.togglePlayPause(a.take) },
                    )
                    is SongActivity.SectionEdited -> ActivityFeedCard(
                        badgeText = "LYR",
                        badgeBg = c.accent2_100,
                        badgeFg = c.accent2_800,
                        title = a.section.displayLabel,
                        subtitle = a.section.bodyText.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: "(empty)",
                        onClick = { onSelectTab(SongDetailTab.LYRICS) },
                    )
                    is SongActivity.NoteAdded -> ActivityFeedCard(
                        badgeText = "NTE",
                        badgeBg = c.neutral100,
                        badgeFg = c.neutral800,
                        title = a.note.bodyText.ifBlank { if (a.note.voiceTakeId != null) "Voice note" else "Photo note" },
                        subtitle = "Note · ${a.note.meta.updatedAt.relativeTimeLabel()}",
                        onClick = { onSelectTab(SongDetailTab.NOTES) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityFeedCard(
    badgeText: String,
    badgeBg: Color,
    badgeFg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = SongPadTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.card),
        color = c.surface,
        border = BorderStroke(1.dp, c.divider),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    color = badgeFg,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun SongActivity.key() = when (this) {
    is SongActivity.TakeSaved -> "t-" + take.id
    is SongActivity.NoteAdded -> "n-" + note.id
    is SongActivity.SectionEdited -> "s-" + section.id
}

@Composable
private fun TakeRow(take: Take, nowPlaying: NowPlaying?, vm: SongDetailViewModel) {
    val c = SongPadTheme.colors
    TakeListItem(
        name = take.name,
        typeLabel = take.type.label(),
        typeIcon = take.type.icon(),
        durationLabel = DurationFormat.mmss(take.durationMs),
        metaLine = take.meta.createdAt.relativeTimeLabel(),
        statusBadge = take.statusBadge(),
        isPlaying = nowPlaying?.take?.id == take.id && nowPlaying.isPlaying,
        onClick = { vm.togglePlayPause(take) },
        onPlay = { vm.togglePlayPause(take) },
        trailing = {
            Surface(
                onClick = { vm.showDialog(SongDetailDialog.TakeOptions(take)) },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Take options",
                        tint = c.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}

private fun TakeListState.takesOrEmpty(): List<Take> = when (this) {
    is TakeListState.Empty -> emptyList()
    is TakeListState.Populated -> takes
    is TakeListState.Playing -> takes
    is TakeListState.Transcoding -> takes
    is TakeListState.Unavailable -> takes
}

@Composable
private fun TakesTab(state: TakeListState, vm: SongDetailViewModel, onRecord: () -> Unit, scrollTarget: String?, onScrolled: () -> Unit) {
    val c = SongPadTheme.colors
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TakeFilter.entries.forEach { f ->
                FilterPill(text = f.label, selected = state.filter == f, onClick = { vm.setTakeFilter(f) })
            }
        }
        when (state) {
            is TakeListState.Empty -> EmptyState(
                icon = SongPadIcons.Mic,
                title = "No takes",
                body = if (state.filter == TakeFilter.ALL) "Record your first take for this song." else "No ${state.filter.label} takes yet.",
                actionLabel = "Record",
                onAction = onRecord,
            )
            is TakeListState.Populated -> TakeList(state.takes, null, vm, listState, scrollTarget, onScrolled)
            is TakeListState.Playing -> TakeList(state.takes, state.playing, vm, listState, scrollTarget, onScrolled)
            is TakeListState.Transcoding -> TakeList(state.takes, state.playing, vm, listState, scrollTarget, onScrolled)
            is TakeListState.Unavailable -> Column {
                Text(state.reason, Modifier.padding(Spacing.screen), color = c.warning, style = MaterialTheme.typography.bodyMedium)
                TakeList(state.takes, null, vm, listState, scrollTarget, onScrolled)
            }
        }
    }
}

@Composable
private fun TakeList(takes: List<Take>, np: NowPlaying?, vm: SongDetailViewModel, listState: LazyListState, scrollTarget: String?, onScrolled: () -> Unit) {
    LaunchedEffect(scrollTarget, takes) {
        if (scrollTarget == null) return@LaunchedEffect
        val index = takes.indexOfFirst { it.id == scrollTarget }
        if (index >= 0) { listState.animateScrollToItem(index); onScrolled() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(takes, key = { it.id }) { TakeRow(it, np, vm) }
    }
}

private fun LyricsState.documentOrNull(): LyricsDocument? = when (this) {
    is LyricsState.Editing -> document
    is LyricsState.Autosaved -> document
    is LyricsState.SectionInserting -> document
    LyricsState.Empty -> null
}

@Composable
private fun LyricsTab(state: LyricsState, vm: SongDetailViewModel, scrollTarget: String?, onScrolled: () -> Unit) {
    val c = SongPadTheme.colors
    val doc = state.documentOrNull()
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

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.screen, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = when {
                    state is LyricsState.Editing && state.dirtySectionId != null -> "Draft saved locally…"
                    focusedSectionId != null -> "All changes saved · tap Done or hide keyboard to return to list"
                    else -> "All changes saved"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (focusedSectionId != null) c.accent else c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Surface(
                onClick = {
                    focusManager.clearFocus()
                    focusedSectionId = null
                    vm.beginInsertSection(null)
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
        if (doc == null || doc.sections.isEmpty()) {
            EmptyState(
                icon = SongPadIcons.Lyrics,
                title = "No lyrics yet",
                body = "Add a section — Verse, Chorus, Bridge — and write inside it. Sections can stay empty.",
                actionLabel = "Add section",
                onAction = { vm.beginInsertSection(null) },
            )
        } else {
            val sorted = doc.sections.sortedBy { it.sortOrder }

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

            LaunchedEffect(scrollTarget, sorted) {
                if (scrollTarget == null) return@LaunchedEffect
                val index = sorted.indexOfFirst { it.id == scrollTarget }
                if (index >= 0) {
                    onScrolled()
                }
            }

            // When a section is focused, levitate it to the top of the list and lock outer scroll at 0.
            // When unfocused, return it to its canonical sortOrder slot (and scroll down if it's at the bottom).
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
                    .padding(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        bottom = if (focusedSectionId != null) 12.dp else 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                displaySections.forEach { section ->
                    key(section.id) {
                        val canonicalIndex = sorted.indexOfFirst { it.id == section.id }.coerceAtLeast(0)
                        val isLevitated = focusedSectionId == section.id
                        val isDimmed = focusedSectionId != null && !isLevitated
                        SectionEditorCard(
                            section = section,
                            vm = vm,
                            isLevitated = isLevitated,
                            dimmed = isDimmed,
                            autoFocus = autoFocusSectionId == section.id,
                            onAutoFocused = { if (autoFocusSectionId == section.id) autoFocusSectionId = null },
                            onFocused = { focusedSectionId = section.id },
                            onDoneEditing = {
                                lastReturnedSectionId = section.id
                                focusedSectionId = null
                                focusManager.clearFocus()
                            },
                            canMoveUp = focusedSectionId == null && canonicalIndex > 0,
                            canMoveDown = focusedSectionId == null && canonicalIndex < sorted.lastIndex,
                            onMoveUp = { vm.moveSection(section.id, canonicalIndex - 1) },
                            onMoveDown = { vm.moveSection(section.id, canonicalIndex + 1) },
                        )
                    }
                }
            }
        }
    }
    if (state is LyricsState.SectionInserting) {
        SectionTagBottomSheet(
            title = "Add section",
            activeTag = null,
            onSelectTag = { tag, custom -> vm.insertSection(tag, custom) },
            onDismiss = vm::cancelInsertSection,
        )
    }
}

@Composable
internal fun SectionTagBottomSheet(
    title: String,
    activeTag: SectionTag?,
    onSelectTag: (SectionTag, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    SongPadBottomSheet(onDismiss = onDismiss, title = title) {
        SectionTag.entries.filter { it != SectionTag.CUSTOM }.forEach { tag ->
            SheetOptionRow(
                label = tag.label,
                meta = if (activeTag == tag) "✓" else null,
                accent = activeTag == tag,
                onClick = { onSelectTag(tag, null) },
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
                onClick = { onSelectTag(SectionTag.CUSTOM, custom.trim()) },
            )
        }
    }
}

@Composable
private fun SectionEditorCard(
    section: Section,
    vm: SongDetailViewModel,
    isLevitated: Boolean,
    dimmed: Boolean,
    autoFocus: Boolean,
    onAutoFocused: () -> Unit,
    onFocused: () -> Unit,
    onDoneEditing: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val c = SongPadTheme.colors
    var text by remember(section.id) { mutableStateOf(section.bodyText) }
    LaunchedEffect(section.bodyText) {
        if (!isLevitated && text != section.bodyText) {
            text = section.bodyText
        }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            runCatching { focusRequester.requestFocus() }
            onAutoFocused()
        }
    }
    Surface(
        shape = RoundedCornerShape(Radii.card),
        color = c.surface,
        border = BorderStroke(if (isLevitated) 1.5.dp else 1.dp, if (isLevitated) c.accent else c.divider),
        shadowElevation = if (isLevitated) 6.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.45f else 1f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TagChip(
                        text = section.displayLabel,
                        tone = TagTone.Accent,
                        onClick = { vm.showDialog(SongDetailDialog.RetagSection(section)) },
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
                            onClick = onDoneEditing,
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
                    if (canMoveUp) {
                        Surface(
                            onClick = onMoveUp,
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = c.textTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (canMoveDown) {
                        Surface(
                            onClick = onMoveDown,
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Move down", tint = c.textTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Surface(
                        onClick = { vm.showDialog(SongDetailDialog.ConfirmDeleteSection(section.id, section.displayLabel)) },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Delete section", tint = c.textTertiary, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it; vm.editSection(section.id, it) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = c.text,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                ),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = if (isLevitated) 4 else 2,
                maxLines = if (isLevitated) 7 else Int.MAX_VALUE,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onFocused()
                        }
                    },
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth()) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Type what you hear…",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
                                color = c.textTertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

private val MentionTriggerRegex = Regex("""@([\w\s]*)$""")

@Composable
private fun NotesTab(
    state: NotesState,
    vm: SongDetailViewModel,
    sections: List<Section>,
    takes: List<Take>,
    nowPlaying: NowPlaying?,
    scrollTarget: String?,
    onScrolled: () -> Unit,
    onNavigateToRef: (SongDetailTab, String) -> Unit,
) {
    val c = SongPadTheme.colors
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (state !is NotesState.Composing) vm.composeNote("")
            vm.beginVoiceNote()
        }
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            if (state !is NotesState.Composing) vm.composeNote("")
            vm.attachMedia(it.toString())
        }
    }

    val draft = when (state) {
        is NotesState.Composing -> state.draftText
        is NotesState.InvalidEmpty -> state.draftText
        is NotesState.MediaAttached -> state.draftText
        is NotesState.VoiceRecording -> state.draftText
        else -> ""
    }
    val editingNoteId = (state as? NotesState.Composing)?.editingNoteId
    val canAttach = editingNoteId == null
    val mentionMatch = remember(draft) { MentionTriggerRegex.find(draft) }
    val mentionQuery = mentionMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()

    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isImeVisible, state.notes.size) {
        if (state.notes.isNotEmpty() && isImeVisible && scrollTarget == null) {
            listState.animateScrollToItem(state.notes.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Chat-bubble message stream
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.notes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(horizontal = Spacing.screen, vertical = 24.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "No notes yet. Jot a thought below — type @ to tag a section or take.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
            } else {
                LaunchedEffect(scrollTarget, state.notes) {
                    if (scrollTarget == null) return@LaunchedEffect
                    val index = state.notes.indexOfFirst { it.id == scrollTarget }
                    if (index >= 0) { listState.animateScrollToItem(index); onScrolled() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteBubbleRow(
                            note = note,
                            vm = vm,
                            sections = sections,
                            takes = takes,
                            nowPlaying = nowPlaying,
                            onNavigateToRef = onNavigateToRef,
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            // Live @mention autocomplete popup pinned at the bottom of the scroll area above composer
            if (mentionMatch != null && (sections.isNotEmpty() || takes.isNotEmpty())) {
                val matchingSections = sections.filter { mentionQuery.isEmpty() || it.displayLabel.contains(mentionQuery, ignoreCase = true) }
                val matchingTakes = takes.filter { mentionQuery.isEmpty() || it.name.contains(mentionQuery, ignoreCase = true) }
                if (matchingSections.isNotEmpty() || matchingTakes.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, c.divider),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screen, vertical = 6.dp),
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text(
                                text = "REFERENCE A SECTION OR TAKE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = c.textTertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            matchingSections.take(4).forEach { sec ->
                                Surface(
                                    onClick = {
                                        val replaced = draft.replace(MentionTriggerRegex, "@${sec.displayLabel} ")
                                        vm.updateDraftText(replaced)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "@${sec.displayLabel}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                        color = c.accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                            }
                            matchingTakes.take(3).forEach { take ->
                                Surface(
                                    onClick = {
                                        val replaced = draft.replace(MentionTriggerRegex, "@${take.name} ")
                                        vm.updateDraftText(replaced)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "@${take.name}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                        color = c.accent2,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pinned bottom composer bar matching Capture.html
        HorizontalDivider(color = c.divider, thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg.copy(alpha = 0.96f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (editingNoteId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Editing note", style = MaterialTheme.typography.labelSmall, color = c.accent)
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textSecondary,
                        modifier = Modifier.clickable { vm.cancelNote() },
                    )
                }
            }
            if (state is NotesState.MediaAttached) {
                Box(Modifier.padding(start = 4.dp)) {
                    NotePhotoThumbnail(state.mediaUri)
                    IconAction(Icons.Filled.Close, "Remove photo", onClick = vm::removeMedia, modifier = Modifier.align(Alignment.TopEnd), filled = true)
                }
            }
            if (state is NotesState.VoiceRecording) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(SongPadIcons.Mic, null, tint = c.record, modifier = Modifier.size(18.dp))
                        Text("Recording voice note…", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = c.record)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SecondaryButton("Cancel", onClick = vm::cancelVoiceNote)
                        IconAction(SongPadIcons.Check, "Save voice note", onClick = vm::stopVoiceNote, accentFilled = true)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (canAttach) {
                        Surface(
                            onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            shape = CircleShape,
                            color = c.surface,
                            border = BorderStroke(1.dp, c.divider),
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.AddPhotoAlternate, "Attach photo", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Surface(
                            onClick = {
                                if (state !is NotesState.Composing) vm.composeNote(draft)
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (granted) vm.beginVoiceNote() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            shape = CircleShape,
                            color = c.surface,
                            border = BorderStroke(1.dp, c.divider),
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(SongPadIcons.Mic, "Record voice note", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Pill input field with multi-line internal scrolling so text is never buried
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, if (state is NotesState.InvalidEmpty) c.destructive else c.divider),
                        modifier = Modifier.weight(1f),
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { vm.updateDraftText(it) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = c.text,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (draft.isEmpty()) {
                                        Text(
                                            text = "Jot a note… (@section to link)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                            color = c.textTertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }

                    val canSend = draft.isNotBlank() || state is NotesState.MediaAttached
                    Surface(
                        onClick = {
                            if (state !is NotesState.Composing && state !is NotesState.MediaAttached) {
                                vm.composeNote(draft)
                            }
                            vm.saveNote()
                        },
                        enabled = canSend,
                        shape = CircleShape,
                        color = if (canSend) c.accent else c.surface,
                        contentColor = if (canSend) c.onAccent else c.textTertiary,
                        border = if (canSend) null else BorderStroke(1.dp, c.divider),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(SongPadIcons.Check, "Save note", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteBubbleRow(
    note: Note,
    vm: SongDetailViewModel,
    sections: List<Section>,
    takes: List<Take>,
    nowPlaying: NowPlaying?,
    onNavigateToRef: (SongDetailTab, String) -> Unit,
) {
    val c = SongPadTheme.colors
    val isVoice = note.voiceTakeId != null
    val voiceTake = remember(note.voiceTakeId, takes) { takes.firstOrNull { it.id == note.voiceTakeId } }
    val isPlayingVoice = voiceTake != null && nowPlaying?.take?.id == voiceTake.id && nowPlaying.isPlaying

    val bubbleShape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomEnd = 6.dp,
        bottomStart = 22.dp,
    )
    val bubbleBg = if (isVoice) c.accent2_100 else c.accent100
    val bubbleText = if (isVoice) c.accent2_800 else c.accent800
    val bubbleBorder = if (isVoice) c.accent2.copy(alpha = 0.35f) else c.accent200

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleBg,
            border = BorderStroke(1.dp, bubbleBorder),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .then(
                    if (voiceTake != null) Modifier.clickable { vm.togglePlayPause(voiceTake) } else Modifier
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                note.mediaUri?.let { uri ->
                    NotePhotoThumbnail(uri, Modifier.padding(bottom = 8.dp))
                }
                if (isVoice) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = if (note.bodyText.isNotBlank()) 6.dp else 0.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(c.accent2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isPlayingVoice) SongPadIcons.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlayingVoice) "Pause voice note" else "Play voice note",
                                tint = c.onAccent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = if (voiceTake != null) "Voice note · ${DurationFormat.mmss(voiceTake.durationMs)}" else "Voice note",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                            color = bubbleText,
                        )
                    }
                }
                if (note.bodyText.isNotBlank()) {
                    NoteBody(
                        text = note.bodyText,
                        sections = sections,
                        takes = takes,
                        textColor = bubbleText,
                        mentionColor = c.accent,
                        onNavigateToRef = onNavigateToRef,
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = note.meta.updatedAt.relativeTimeLabel(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = c.textTertiary,
            )
            if (!isVoice) {
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = c.textTertiary,
                    modifier = Modifier.clickable { vm.composeNote(note.bodyText, note.id) },
                )
            }
            Text(
                text = "Delete",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = c.textTertiary,
                modifier = Modifier.clickable { vm.showDialog(SongDetailDialog.ConfirmDeleteNote(note.id)) },
            )
        }
    }
}

@Composable
private fun NoteBody(
    text: String,
    sections: List<Section>,
    takes: List<Take>,
    textColor: Color,
    mentionColor: Color,
    onNavigateToRef: (SongDetailTab, String) -> Unit,
) {
    data class Ref(val tab: SongDetailTab, val id: String)
    val refs = mutableListOf<Ref?>()
    val annotated = buildAnnotatedString {
        var cursor = 0
        for (match in NoteValidation.mentionMatches(text)) {
            append(text.substring(cursor, match.range.first))
            val token = match.groupValues[1].trim()
            val section = sections.firstOrNull { it.displayLabel.equals(token, ignoreCase = true) }
            val take = takes.firstOrNull { it.name.equals(token, ignoreCase = true) }
            val ref = when {
                section != null -> Ref(SongDetailTab.LYRICS, section.id)
                take != null -> Ref(SongDetailTab.TAKES, take.id)
                else -> null
            }
            if (ref != null) {
                withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold)) { append(match.value) }
            } else {
                append(match.value)
            }
            refs += ref
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = textColor, fontSize = 14.sp, lineHeight = 20.sp),
    ) { offset ->
        NoteValidation.mentionMatches(text).forEachIndexed { i, match ->
            if (offset in match.range.first..match.range.last) refs.getOrNull(i)?.let { onNavigateToRef(it.tab, it.id) }
        }
    }
}

@Composable
private fun NotePhotoThumbnail(uriString: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                }
            }.getOrNull()
        }
    }
    Box(modifier.size(96.dp).clip(RoundedCornerShape(Radii.sm)).background(SongPadTheme.colors.surfaceVariant)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Attached photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
