package com.songpad.feature.song.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.audio.recovery.AudioRecoveryManager
import com.songpad.core.model.data.SearchResults
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.song.SongStatusTransitions
import com.songpad.core.model.state.QualityDisclosureState
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.DurationFormat
import com.songpad.core.ui.components.ConfirmDialog
import com.songpad.core.ui.components.EditorialHeader
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.FilterPill
import com.songpad.core.ui.components.IconAction
import com.songpad.core.ui.components.ImportBottomSheet
import com.songpad.core.ui.components.InlineDisclosure
import com.songpad.core.ui.components.PillSearchBar
import com.songpad.core.ui.components.PrimaryButton
import com.songpad.core.ui.components.RibbonPlayer
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.components.SectionHeader
import com.songpad.core.ui.components.SheetOptionRow
import com.songpad.core.ui.components.SongListItem
import com.songpad.core.ui.components.SongPadBottomSheet
import com.songpad.core.ui.components.TagChip
import com.songpad.core.ui.components.TagTone
import com.songpad.core.ui.components.TextInputDialog
import com.songpad.core.ui.components.UnfiledLyricsRow
import com.songpad.core.ui.components.UnfiledTakeRow
import com.songpad.core.ui.components.UnfiledTakesContainer
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.song.detail.NowPlaying
import java.text.DateFormat
import java.util.Date

/** Phase 0 §14 library redesigned to match Capture.html: Editorial header, inline PillSearchBar with FTS5 snippets, filter pills, Unfiled card, and Song cards with take/section counts and `•••` sheet. */
@Composable
fun LibraryScreen(
    onOpenSong: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecord: () -> Unit,
    onLaunchPiano: () -> Unit = {},
    onLaunchLyrics: () -> Unit = {},
    onOpenLyrics: (songId: String, sectionId: String) -> Unit = { songId, _ -> onOpenSong(songId) },
    onOpenNotes: (songId: String, noteId: String) -> Unit = { songId, _ -> onOpenSong(songId) },
    onOpenTakes: (songId: String, takeId: String) -> Unit = { songId, _ -> onOpenSong(songId) },
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val recovery by viewModel.recoveryState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = context.resolveDisplayName(uri) ?: "imported_file"
            val mime = context.contentResolver.getType(uri)
            viewModel.importFile(name, mime) { context.contentResolver.openInputStream(uri) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            EditorialHeader(
                kicker = "Caught so far",
                title = "Songs",
                actions = {
                    IconAction(
                        icon = SongPadIcons.Upload,
                        contentDescription = "Import",
                        onClick = viewModel::openImportSheet,
                        outlined = true,
                    )
                    IconAction(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings,
                        outlined = true,
                    )
                },
            )

            PillSearchBar(
                query = query,
                onQueryChange = viewModel::setSearchQuery,
                placeholder = "Search songs, lyrics, notes",
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            )

            if (query.trim().isNotEmpty()) {
                InlineSearchResultsList(
                    query = query.trim(),
                    results = searchResults,
                    onOpenSong = onOpenSong,
                    onOpenLyrics = onOpenLyrics,
                    onOpenStandaloneLyrics = { docId -> viewModel.openStandaloneLyrics(docId) },
                    onOpenNotes = onOpenNotes,
                    onOpenTakes = onOpenTakes,
                    modifier = Modifier.weight(1f),
                )
            } else {
                RecoveryBanner(recovery, viewModel)
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screen, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SongFilter.entries.forEach { f ->
                        FilterPill(text = f.label(), selected = state.filter == f, onClick = { viewModel.setFilter(f) })
                    }
                }
                (state.disclosure as? QualityDisclosureState.FirstSaveDisclosure)?.let {
                    InlineDisclosure(
                        text = "Recording in Standard quality. Lossless uses about 3× the space and applies to new recordings only.",
                        actionLabel = "Change →",
                        onAction = onOpenSettings,
                        onDismiss = viewModel::dismissDisclosure,
                        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                    )
                }
                when (val s = state) {
                    is LibraryState.Loading -> Unit
                    is LibraryState.EmptyLibrary -> EmptyState(
                        icon = SongPadIcons.MusicNote,
                        title = "No songs yet",
                        body = "Tap Record below to catch an idea. You don't even have to name it.",
                        actionLabel = "Record",
                        onAction = onRecord,
                        modifier = Modifier.weight(1f),
                    )
                    is LibraryState.Populated -> SongList(s.songs, s.unassignedTakes, s.unassignedLyrics, s.nowPlaying, s.filter, onOpenSong, viewModel, Modifier.weight(1f))
                    is LibraryState.Filtered -> SongList(s.songs, s.unassignedTakes, s.unassignedLyrics, s.nowPlaying, s.filter, onOpenSong, viewModel, Modifier.weight(1f))
                }
            }

            state.nowPlaying?.let { np ->
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

        // Floating Lyrics (higher) and Piano (lower) quick-launch icons in the lower-right corner above the bottom bar
        com.songpad.core.ui.components.CaptureQuickActions(
            onLaunchLyrics = onLaunchLyrics,
            onLaunchPiano = onLaunchPiano,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 18.dp,
                    bottom = if (state.nowPlaying != null) 76.dp else 16.dp,
                ),
        )
    }

    when (val sh = sheet) {
        LibrarySheet.None -> Unit
        is LibrarySheet.AssignTake -> SongPadBottomSheet(
            onDismiss = viewModel::closeSheet,
            title = "Add to song",
            subtitle = "${sh.take.name} · ${DurationFormat.mmss(sh.take.durationMs)} · ${sh.take.type.label()}",
        ) {
            SheetOptionRow(
                label = "+ Create a new song from this take",
                onClick = { viewModel.openNewSongForTake(sh.take) },
                accent = true,
            )
            sh.songs.forEach { song ->
                SheetOptionRow(
                    label = song.name,
                    meta = "${song.takeCount} take${if (song.takeCount == 1) "" else "s"}",
                    onClick = { viewModel.assignTake(sh.take.id, song.id) },
                )
            }
        }
        is LibrarySheet.NewSongForTake -> TextInputDialog(
            title = "New song",
            label = "Song name",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { name -> viewModel.createSongForTake(name, sh.take.id, onOpenSong) },
            onDismiss = viewModel::closeSheet,
        )
        is LibrarySheet.ConfirmDeleteTake -> ConfirmDialog(
            title = "Delete \"${sh.take.name}\"?",
            body = "The take is removed from your library. Its file is purged after 7 days.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteTake(sh.take.id) },
            onDismiss = viewModel::closeSheet,
        )
        is LibrarySheet.AssignLyrics -> SongPadBottomSheet(
            onDismiss = viewModel::closeSheet,
            title = "Add to song",
            subtitle = "${sh.document.name} · ${sh.document.nonEmptySectionCount} section${if (sh.document.nonEmptySectionCount == 1) "" else "s"}",
        ) {
            SheetOptionRow(
                label = "+ Create a new song from these lyrics",
                onClick = { viewModel.openNewSongForLyrics(sh.document) },
                accent = true,
            )
            sh.songs.forEach { song ->
                SheetOptionRow(
                    label = song.name,
                    meta = "${song.sectionCount} section${if (song.sectionCount == 1) "" else "s"}",
                    onClick = { viewModel.assignLyrics(sh.document.id, song.id) },
                )
            }
        }
        is LibrarySheet.NewSongForLyrics -> TextInputDialog(
            title = "New song",
            label = "Song name",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { name -> viewModel.createSongForLyrics(name, sh.document.id, onOpenSong) },
            onDismiss = viewModel::closeSheet,
        )
        is LibrarySheet.EditStandaloneLyrics -> {
            val doc by viewModel.observeStandaloneLyrics(sh.documentId).collectAsStateWithLifecycle(initialValue = null)
            doc?.let { currentDoc ->
                StandaloneLyricsEditorSheet(
                    document = currentDoc,
                    onEditSection = viewModel::editStandaloneSection,
                    onAddSection = { viewModel.insertStandaloneSection(currentDoc.id, SectionTag.VERSE, null) },
                    onDeleteSection = viewModel::deleteStandaloneSection,
                    onAssignToSong = { viewModel.openAssignLyricsSheet(currentDoc) },
                    onRename = { viewModel.openRenameStandaloneLyrics(currentDoc) },
                    onDelete = { viewModel.requestDeleteStandaloneLyrics(currentDoc) },
                    onDismiss = viewModel::closeSheet,
                )
            }
        }
        is LibrarySheet.RenameStandaloneLyrics -> TextInputDialog(
            title = "Rename lyrics",
            label = "Lyrics name",
            initialValue = sh.document.name,
            confirmLabel = "Rename",
            onConfirm = { name -> viewModel.renameStandaloneLyrics(sh.document.id, name) },
            onDismiss = viewModel::closeSheet,
        )
        is LibrarySheet.ConfirmDeleteStandaloneLyrics -> ConfirmDialog(
            title = "Delete \"${sh.document.name}\"?",
            body = "These standalone lyrics will be removed from your library.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteStandaloneLyrics(sh.document.id) },
            onDismiss = viewModel::closeSheet,
        )
        is LibrarySheet.SongOptions -> SongPadBottomSheet(
            onDismiss = viewModel::closeSheet,
            title = sh.song.name,
        ) {
            SheetOptionRow(
                label = "Rename",
                onClick = { viewModel.openRenameSong(sh.song) },
            )
            SheetOptionRow(
                label = if (sh.song.isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = { viewModel.toggleFavorite(sh.song.id); viewModel.closeSheet() },
            )
            SheetOptionRow(
                label = if (sh.song.status == SongStatus.FINISHED) "Reopen as Ongoing" else "Mark as Finished",
                onClick = { viewModel.toggleFinished(sh.song) },
            )
            SheetOptionRow(
                label = SongStatusTransitions.archiveActionLabel(sh.song.status),
                onClick = { viewModel.toggleArchived(sh.song) },
            )
            SheetOptionRow(
                label = "Delete song",
                destructive = true,
                onClick = { viewModel.requestDeleteSong(sh.song) },
            )
        }
        is LibrarySheet.RenameSong -> TextInputDialog(
            title = "Rename song",
            label = "Song name",
            initialValue = sh.song.name,
            confirmLabel = "Rename",
            onConfirm = { name -> viewModel.renameSong(sh.song.id, name) },
            onDismiss = viewModel::closeSheet,
        )
        is LibrarySheet.ConfirmDeleteSong -> ConfirmDialog(
            title = "Delete \"${sh.song.name}\"?",
            body = "Takes, lyrics and notes go with it. Files are purged after 7 days.",
            confirmLabel = "Delete song",
            onConfirm = { viewModel.deleteSong(sh.song.id) },
            onDismiss = viewModel::closeSheet,
        )
        is LibrarySheet.Import -> ImportBottomSheet(
            title = "Import to SongPad",
            subtitle = "Restore exported songs (.zip, .songpad, .bin), or bring in audio recordings and .txt lyrics.",
            allowBundle = true,
            isBusy = sh.isBusy,
            statusTitle = sh.statusTitle,
            statusBody = sh.statusBody,
            isError = sh.isError,
            onPickBundleOrAny = { importPicker.launch(arrayOf("*/*")) },
            onPickAudio = { importPicker.launch(arrayOf("audio/*", "application/ogg", "application/flac", "*/*")) },
            onPickLyrics = { importPicker.launch(arrayOf("text/*", "*/*")) },
            primaryActionLabel = if (sh.importedSongId != null) "Open imported song" else null,
            onPrimaryAction = if (sh.importedSongId != null) {
                {
                    val id = sh.importedSongId
                    viewModel.closeSheet()
                    onOpenSong(id)
                }
            } else null,
            onDismiss = viewModel::closeSheet,
        )
    }
}

internal fun Context.resolveDisplayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment

@Composable
private fun StandaloneLyricsEditorSheet(
    document: LyricsDocument,
    onEditSection: (String, String) -> Unit,
    onAddSection: () -> Unit,
    onDeleteSection: (String) -> Unit,
    onAssignToSong: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SongPadTheme.colors
    val sorted = remember(document) { document.sections.sortedBy { it.sortOrder } }
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    var focusedSectionId by remember { mutableStateOf<String?>(null) }
    var imeWasVisible by remember { mutableStateOf(false) }
    var knownSectionIds by remember { mutableStateOf<Set<String>?>(null) }
    var autoFocusSectionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isImeVisible) {
        if (imeWasVisible && !isImeVisible) {
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

    val displaySections = remember(sorted, focusedSectionId) {
        val active = sorted.firstOrNull { it.id == focusedSectionId }
        if (active != null) {
            listOf(active) + sorted.filterNot { it.id == active.id }
        } else {
            sorted
        }
    }

    SongPadBottomSheet(
        onDismiss = onDismiss,
        title = document.name,
        subtitle = "Unfiled lyrics · ${document.nonEmptySectionCount} section${if (document.nonEmptySectionCount == 1) "" else "s"}",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                        shape = RoundedCornerShape(16.dp),
                        color = c.bg,
                        border = BorderStroke(if (isLevitated) 1.5.dp else 1.dp, if (isLevitated) c.accent else c.divider),
                        shadowElevation = if (isLevitated) 4.dp else 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isDimmed) 0.45f else 1f),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    TagChip(text = sec.displayLabel, tone = TagTone.Accent)
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
                                    if (sorted.size > 1) {
                                        Surface(
                                            onClick = { onDeleteSection(sec.id) },
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Filled.Close, "Delete section", tint = c.textTertiary, modifier = Modifier.size(14.dp))
                                            }
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
                                    lineHeight = 20.sp,
                                ),
                                cursorBrush = SolidColor(c.accent),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                minLines = if (isLevitated) 3 else 2,
                                maxLines = if (isLevitated) 6 else Int.MAX_VALUE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp)
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
                                                text = "Write lyrics…",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(
                    text = "+ Section",
                    onClick = {
                        focusManager.clearFocus()
                        focusedSectionId = null
                        onAddSection()
                    },
                )
                PrimaryButton(text = "Add to a song", onClick = onAssignToSong, compact = true)
            }
            SheetOptionRow(label = "Rename lyrics", onClick = onRename)
            SheetOptionRow(label = "Delete lyrics", destructive = true, onClick = onDelete)
        }
    }
}

@Composable
private fun InlineSearchResultsList(
    query: String,
    results: SearchResults?,
    onOpenSong: (String) -> Unit,
    onOpenLyrics: (String, String) -> Unit,
    onOpenStandaloneLyrics: (String) -> Unit = {},
    onOpenNotes: (String, String) -> Unit,
    onOpenTakes: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    if (results == null) {
        Box(modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.TopCenter) {
            Text("Searching…", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (results.isEmpty) {
        EmptyState(
            icon = Icons.Filled.Search,
            title = "Nothing matched \"$query\"",
            body = "Try another word — search checks song names, lyrics, notes, and takes.",
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (results.songs.isNotEmpty()) {
            item { SectionHeader("Songs") }
            items(results.songs, key = { "s-" + it.songId }) { m ->
                SearchResultCard(
                    badge = "Song",
                    badgeTone = TagTone.Accent,
                    title = m.name,
                    snippet = highlightSnippet(m.snippet, c.accent),
                    onClick = { onOpenSong(m.songId) },
                )
            }
        }
        if (results.lyrics.isNotEmpty()) {
            item { SectionHeader("Lyrics") }
            items(results.lyrics, key = { "l-" + it.sectionId }) { m ->
                SearchResultCard(
                    badge = m.label,
                    badgeTone = TagTone.Accent2,
                    title = if (m.songId.isEmpty()) "Unfiled lyrics" else "Lyric section",
                    snippet = highlightSnippet(m.snippet, c.accent),
                    onClick = {
                        if (m.songId.isNotEmpty()) {
                            onOpenLyrics(m.songId, m.sectionId)
                        } else if (m.documentId.isNotEmpty()) {
                            onOpenStandaloneLyrics(m.documentId)
                        }
                    },
                )
            }
        }
        if (results.notes.isNotEmpty()) {
            item { SectionHeader("Notes") }
            items(results.notes, key = { "n-" + it.noteId }) { m ->
                SearchResultCard(
                    badge = "Note",
                    badgeTone = TagTone.Neutral,
                    title = "Song note",
                    snippet = highlightSnippet(m.snippet, c.accent),
                    onClick = { onOpenNotes(m.songId, m.noteId) },
                )
            }
        }
        if (results.takes.isNotEmpty()) {
            item { SectionHeader("Takes") }
            items(results.takes, key = { "t-" + it.takeId }) { m ->
                SearchResultCard(
                    badge = "Take",
                    badgeTone = TagTone.Accent,
                    title = m.name,
                    snippet = highlightSnippet(m.snippet, c.accent),
                    onClick = { val sid = m.songId; if (sid != null) onOpenTakes(sid, m.takeId) },
                )
            }
        }
        item { Spacer(Modifier.height(Spacing.xxxl)) }
    }
}

@Composable
private fun SearchResultCard(
    badge: String,
    badgeTone: TagTone,
    title: String,
    snippet: AnnotatedString,
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagChip(text = badge, tone = badgeTone)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                )
            }
            if (snippet.text.isNotBlank() && !snippet.text.equals(title, ignoreCase = true)) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = c.textSecondary,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun highlightSnippet(snippet: String, highlight: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val openTag = "<b>"
    val closeTag = "</b>"
    var i = 0
    while (i < snippet.length) {
        val open = snippet.indexOf(openTag, i)
        if (open < 0) {
            append(snippet.substring(i))
            break
        }
        if (open > i) append(snippet.substring(i, open))
        val close = snippet.indexOf(closeTag, open + openTag.length)
        if (close < 0) {
            pushStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold))
            append(snippet.substring(open + openTag.length))
            pop()
            break
        }
        pushStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold))
        append(snippet.substring(open + openTag.length, close))
        pop()
        i = close + closeTag.length
    }
}

@Composable
private fun RecoveryBanner(state: AudioRecoveryManager.RecoveryState, vm: LibraryViewModel) {
    val c = SongPadTheme.colors
    when (state) {
        AudioRecoveryManager.RecoveryState.None -> Unit
        is AudioRecoveryManager.RecoveryState.Prompt -> {
            val count = state.orphans.count { !it.isNoise }
            val totalMs = state.orphans.sumOf { it.durationMs }
            Surface(
                shape = RoundedCornerShape(Radii.card),
                color = c.surface,
                border = BorderStroke(1.dp, c.divider),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (count == 1) "We recovered ${DurationFormat.mmss(totalMs)} of an unfinished recording. Keep it?"
                        else "We recovered $count unfinished recordings (${DurationFormat.mmss(totalMs)} total). Keep them?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row {
                        PrimaryButton("Keep", onClick = vm::keepRecovered)
                        Spacer(Modifier.width(Spacing.sm))
                        SecondaryButton("Discard", onClick = vm::requestDiscardRecovered, destructive = true)
                    }
                }
            }
        }
        AudioRecoveryManager.RecoveryState.Restoring -> Text("Restoring…", Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm), color = c.textSecondary)
        is AudioRecoveryManager.RecoveryState.Restored -> Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.takeIds.isEmpty()) "Nothing worth keeping was found." else "Recovered as unassigned takes.",
                Modifier.weight(1f),
                color = c.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            SecondaryButton("OK", onClick = vm::dismissRecovered)
        }
        is AudioRecoveryManager.RecoveryState.DiscardConfirm -> ConfirmDialog(
            title = "Discard unsaved recording?",
            body = "This deletes ${DurationFormat.mmss(state.orphans.sumOf { it.durationMs })} of audio permanently.",
            confirmLabel = "Discard",
            onConfirm = vm::confirmDiscardRecovered,
            onDismiss = vm::cancelDiscardRecovered,
        )
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    unassigned: List<Take>,
    unassignedLyrics: List<LyricsDocument>,
    nowPlaying: NowPlaying?,
    filter: SongFilter,
    onOpenSong: (String) -> Unit,
    vm: LibraryViewModel,
    modifier: Modifier,
) {
    val c = SongPadTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if ((unassigned.isNotEmpty() || unassignedLyrics.isNotEmpty()) && filter == SongFilter.ALL) {
            item(key = "unassigned-container") {
                UnfiledTakesContainer {
                    unassigned.forEach { take ->
                        UnfiledTakeRow(
                            name = take.name,
                            metaLine = "${DurationFormat.mmss(take.durationMs)} · ${take.type.label()} · ${take.meta.createdAt.relativeTimeLabel()}",
                            isPlaying = nowPlaying?.take?.id == take.id && nowPlaying.isPlaying,
                            onPlayPause = { vm.togglePlayPause(take) },
                            onAssignToSong = { vm.openAssignSheet(take) },
                        )
                    }
                    unassignedLyrics.forEach { doc ->
                        val sectionCount = doc.nonEmptySectionCount.coerceAtLeast(1)
                        val sectionLabel = "$sectionCount section${if (sectionCount == 1) "" else "s"}"
                        val snippet = doc.previewSnippet
                        val metaLine = buildString {
                            append(sectionLabel)
                            if (snippet.isNotEmpty()) append(" · \"$snippet\"")
                            append(" · ")
                            append(doc.meta.updatedAt.relativeTimeLabel())
                        }
                        UnfiledLyricsRow(
                            name = doc.name,
                            metaLine = metaLine,
                            onClick = { vm.openStandaloneLyrics(doc.id) },
                            onAssignToSong = { vm.openAssignLyricsSheet(doc) },
                        )
                    }
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No ${filter.label().lowercase()} songs yet.",
                        color = c.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(songs, key = { it.id }) { song ->
            SongListItem(
                name = song.name,
                statusLabel = song.status.chipLabel(),
                metaLine = song.updatedAt.relativeTimeLabel(),
                isFavorite = song.isFavorite,
                onClick = { onOpenSong(song.id) },
                onToggleFavorite = { vm.toggleFavorite(song.id) },
                takeLabel = "${song.takeCount} take${if (song.takeCount == 1) "" else "s"}",
                lyricLabel = if (song.sectionCount > 0) "${song.sectionCount} section${if (song.sectionCount == 1) "" else "s"}" else "No lyrics yet",
                statusTone = song.status.chipTone(),
                onMoreClick = { vm.openSongOptions(song) },
            )
        }
        item { Spacer(Modifier.height(Spacing.xxxl)) }
    }
}

internal fun SongFilter.label() = when (this) {
    SongFilter.ALL -> "All"; SongFilter.FAVORITES -> "Favorites"; SongFilter.ONGOING -> "Ongoing"; SongFilter.FINISHED -> "Finished"; SongFilter.ARCHIVED -> "Archived"
}
internal fun SongStatus.label(): String? = when (this) { SongStatus.ONGOING -> null; SongStatus.FINISHED -> "Finished"; SongStatus.ARCHIVED -> "Archived" }
internal fun SongStatus.chipLabel(): String = when (this) { SongStatus.ONGOING -> "Ongoing"; SongStatus.FINISHED -> "Finished"; SongStatus.ARCHIVED -> "Archived" }
internal fun SongStatus.chipTone(): TagTone = when (this) { SongStatus.ONGOING -> TagTone.Accent2; SongStatus.FINISHED -> TagTone.Accent; SongStatus.ARCHIVED -> TagTone.Neutral }
internal fun TakeType.label() = when (this) { TakeType.VOICE -> "Voice"; TakeType.PIANO -> "Piano"; TakeType.VOICE_PIANO -> "Voice + Piano" }
internal fun TakeType.shortBadge() = when (this) { TakeType.VOICE -> "VOX"; TakeType.PIANO -> "PNO"; TakeType.VOICE_PIANO -> "V+P" }
internal fun TakeType.icon() = when (this) { TakeType.VOICE -> SongPadIcons.Mic; TakeType.PIANO -> SongPadIcons.Piano; TakeType.VOICE_PIANO -> SongPadIcons.Waveform }
internal fun Take.statusBadge(): String? = when (status) {
    TakeStatus.TRANSCODING -> "Processing"; TakeStatus.FAILED -> "Failed"; TakeStatus.ORPHANED -> "Recovered"; else -> null
}
internal fun Long.dateLabel(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
internal fun Long.relativeTimeLabel(): String {
    val diffMs = (System.currentTimeMillis() - this).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
    }
}
