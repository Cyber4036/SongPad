package com.songpad.feature.song.detail

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.Note
import com.songpad.core.model.note.NoteValidation
import com.songpad.core.model.song.SongActivity
import com.songpad.core.model.song.SongStatusTransitions
import com.songpad.core.model.take.Take
import com.songpad.core.model.usecase.DurationFormat
import com.songpad.core.ui.components.ConfirmDialog
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.FilterPill
import com.songpad.core.ui.components.IconAction
import com.songpad.core.ui.components.PrimaryButton
import com.songpad.core.ui.components.RibbonPlayer
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.components.SectionHeader
import com.songpad.core.ui.components.SongPadBottomSheet
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.components.TagChip
import com.songpad.core.ui.components.TakeListItem
import com.songpad.core.ui.components.TextInputDialog
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.song.library.dateLabel
import com.songpad.feature.song.library.icon
import com.songpad.feature.song.library.label
import com.songpad.feature.song.library.statusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Phase 0 §15 Song Home with Lyrics / Takes / Notes tabs. */
@Composable
fun SongDetailScreen(
    songId: String,
    initialTab: SongDetailTab,
    onBack: () -> Unit,
    onRecord: (String) -> Unit,
    onExport: (String) -> Unit,
    onSearchInSong: (String) -> Unit,
    targetId: String? = null,
    viewModel: SongDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(songId) { viewModel.bind(songId, initialTab) }
    // Leaving mid voice note would clear the ViewModel and strand the live recording. Stopping a voice note
    // is sending it (chat-style), so back first stops-and-saves it and keeps the user here; the next back leaves.
    val recordingVoiceNote by viewModel.recordingVoiceNote.collectAsStateWithLifecycle()
    BackHandler(enabled = recordingVoiceNote) { viewModel.stopVoiceNote() }
    val guardedBack = { if (recordingVoiceNote) viewModel.stopVoiceNote() else onBack() }
    LaunchedEffect(Unit) { viewModel.navigateToLibrary.collect { onBack() } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val c = SongPadTheme.colors
    // Landing target from search, or a @mention tap that switched tabs — consumed once the row is found and scrolled to.
    var scrollTarget by remember(songId) { mutableStateOf(targetId) }
    fun navigateToRef(tab: SongDetailTab, id: String) { viewModel.selectTab(tab); scrollTarget = id }

    when (val s = state) {
        SongDetailState.Loading -> Column(Modifier.fillMaxSize()) { SongPadTopBar(title = null, onBack = onBack) }
        SongDetailState.NotFound -> Column(Modifier.fillMaxSize()) {
            SongPadTopBar(title = null, onBack = onBack)
            EmptyState(SongPadIcons.MusicNote, "This song is gone", "It may have been deleted.", actionLabel = "Back to library", onAction = onBack)
        }
        is SongDetailState.Loaded -> Column(Modifier.fillMaxSize()) {
            var menu by remember { mutableStateOf(false) }
            SongPadTopBar(title = s.song.name, onBack = { guardedBack() }) {
                IconAction(if (s.song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", onClick = viewModel::toggleFavorite, tint = if (s.song.isFavorite) c.record else c.text)
                IconAction(Icons.Filled.Share, "Export", onClick = { onExport(s.song.id) })
                Box {
                    IconAction(Icons.Filled.MoreVert, "More", onClick = { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; viewModel.showDialog(SongDetailDialog.RenameSong) })
                        DropdownMenuItem(text = { Text(SongStatusTransitions.finishActionLabel(s.song.status)) }, onClick = { menu = false; viewModel.toggleFinished() })
                        DropdownMenuItem(text = { Text(SongStatusTransitions.archiveActionLabel(s.song.status)) }, onClick = { menu = false; viewModel.toggleArchived() })
                        DropdownMenuItem(text = { Text("Search in song") }, onClick = { menu = false; onSearchInSong(s.song.id) })
                        DropdownMenuItem(text = { Text("Delete song", color = c.destructive) }, onClick = { menu = false; viewModel.showDialog(SongDetailDialog.ConfirmDeleteSong) })
                    }
                }
            }
            TabRow(selectedTabIndex = s.tab.ordinal, containerColor = c.bg, contentColor = c.accent) {
                SongDetailTab.entries.forEach { t -> Tab(selected = s.tab == t, onClick = { viewModel.selectTab(t) }, text = { Text(t.label) }) }
            }
            Box(Modifier.weight(1f)) {
                when (s.tab) {
                    SongDetailTab.HOME -> HomeTab(s, onRecord = { onRecord(s.song.id) }, viewModel)
                    SongDetailTab.LYRICS -> LyricsTab(s.lyrics, viewModel, scrollTarget) { scrollTarget = null }
                    SongDetailTab.TAKES -> TakesTab(s.takes, viewModel, onRecord = { onRecord(s.song.id) }, scrollTarget = scrollTarget, onScrolled = { scrollTarget = null })
                    SongDetailTab.NOTES -> NotesTab(
                        s.notes, viewModel, sections = s.lyrics.documentOrNull()?.sections.orEmpty(), takes = s.takes.takesOrEmpty(),
                        scrollTarget = scrollTarget, onScrolled = { scrollTarget = null }, onNavigateToRef = ::navigateToRef,
                    )
                }
            }
            s.nowPlaying?.let { np ->
                RibbonPlayer(
                    title = np.take.name, subtitle = np.take.type.label(), isPlaying = np.isPlaying, progress = np.progress,
                    positionLabel = "${DurationFormat.mmss(np.positionMs)} / ${DurationFormat.mmss(np.durationMs)}", peaks = np.peaks,
                    onPlayPause = { viewModel.togglePlayPause(np.take) }, onSeek = viewModel::seekTo, onDismiss = viewModel::dismissPlayer,
                )
            }
        }
    }

    when (val d = dialog) {
        SongDetailDialog.None -> Unit
        SongDetailDialog.RenameSong -> (state as? SongDetailState.Loaded)?.let { s ->
            TextInputDialog("Rename song", "Song name", s.song.name, "Rename", onConfirm = viewModel::renameSong, onDismiss = viewModel::closeDialog)
        }
        is SongDetailDialog.RenameTake -> TextInputDialog("Rename take", "Take name", d.take.name, "Rename", onConfirm = { viewModel.renameTake(d.take.id, it) }, onDismiss = viewModel::closeDialog)
        is SongDetailDialog.ConfirmDeleteTake -> ConfirmDialog("Delete \"${d.take.name}\"?", "The take is removed from this song. Its file is purged after 7 days.", "Delete", onConfirm = { viewModel.deleteTake(d.take.id) }, onDismiss = viewModel::closeDialog)
        is SongDetailDialog.ConfirmDeleteSection -> ConfirmDialog("Delete ${d.label}?", "Its lyrics are removed. This can't be undone.", "Delete", onConfirm = { viewModel.deleteSection(d.sectionId) }, onDismiss = viewModel::closeDialog)
        is SongDetailDialog.ConfirmDeleteNote -> ConfirmDialog("Delete note?", "This can't be undone.", "Delete", onConfirm = { viewModel.deleteNote(d.noteId) }, onDismiss = viewModel::closeDialog)
        SongDetailDialog.ConfirmDeleteSong -> ConfirmDialog("Delete this song?", "Takes, lyrics and notes go with it. Files are purged after 7 days.", "Delete song", onConfirm = viewModel::deleteSong, onDismiss = viewModel::closeDialog)
    }
}

@Composable
private fun HomeTab(s: SongDetailState.Loaded, onRecord: () -> Unit, vm: SongDetailViewModel) {
    val c = SongPadTheme.colors
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryButton("Record", onClick = onRecord, leadingIcon = SongPadIcons.Mic)
                SecondaryButton("Add section", onClick = { vm.selectTab(SongDetailTab.LYRICS); vm.beginInsertSection(null) })
                SecondaryButton("Note", onClick = { vm.selectTab(SongDetailTab.NOTES); vm.composeNote("") })
            }
        }
        if (s.activity.isEmpty()) item {
            EmptyState(SongPadIcons.MusicNote, "Nothing here yet", "Record a take, add a lyric section, or jot a note. Everything shows up here, newest first.")
        } else {
            item { SectionHeader("Recent") }
            items(s.activity, key = { it.key() }) { a ->
                when (a) {
                    is SongActivity.TakeSaved -> TakeRow(a.take, s.nowPlaying, vm)
                    is SongActivity.NoteAdded -> Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
                        TagChip("Note"); Text(a.note.bodyText, color = c.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    is SongActivity.SectionEdited -> Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
                        TagChip(a.section.displayLabel); Text(a.section.bodyText.lineSequence().take(2).joinToString("\n"), color = c.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun SongActivity.key() = when (this) { is SongActivity.TakeSaved -> "t-" + take.id; is SongActivity.NoteAdded -> "n-" + note.id; is SongActivity.SectionEdited -> "s-" + section.id }

@Composable
private fun TakeRow(take: Take, nowPlaying: NowPlaying?, vm: SongDetailViewModel) {
    TakeListItem(
        name = take.name, typeLabel = take.type.label(), typeIcon = take.type.icon(),
        durationLabel = DurationFormat.mmss(take.durationMs), metaLine = take.meta.createdAt.dateLabel(), statusBadge = take.statusBadge(),
        isPlaying = nowPlaying?.take?.id == take.id && nowPlaying.isPlaying,
        onClick = { vm.showDialog(SongDetailDialog.RenameTake(take)) }, onPlay = { vm.togglePlayPause(take) },
        trailing = { IconAction(Icons.Filled.Delete, "Delete take", onClick = { vm.showDialog(SongDetailDialog.ConfirmDeleteTake(take)) }) },
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
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
            TakeFilter.entries.forEach { f -> FilterPill(f.label, selected = state.filter == f, onClick = { vm.setTakeFilter(f) }); Spacer(Modifier.width(Spacing.sm)) }
        }
        when (state) {
            is TakeListState.Empty -> EmptyState(SongPadIcons.Mic, "No takes", if (state.filter == TakeFilter.ALL) "Record your first take for this song." else "No ${state.filter.label} takes yet.", actionLabel = "Record", onAction = onRecord)
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
    LazyColumn(Modifier.fillMaxSize(), state = listState) { items(takes, key = { it.id }) { TakeRow(it, np, vm) }; item { Spacer(Modifier.height(Spacing.xxxl)) } }
}

private fun LyricsState.documentOrNull(): LyricsDocument? = when (this) {
    is LyricsState.Editing -> document; is LyricsState.Autosaved -> document; is LyricsState.SectionInserting -> document; LyricsState.Empty -> null
}

@Composable
private fun LyricsTab(state: LyricsState, vm: SongDetailViewModel, scrollTarget: String?, onScrolled: () -> Unit) {
    val c = SongPadTheme.colors
    val doc = state.documentOrNull()
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("Add section", onClick = { vm.beginInsertSection(null) })
            Spacer(Modifier.weight(1f))
            Text(
                when (state) { is LyricsState.Editing -> if (state.dirtySectionId != null) "Saving…" else ""; is LyricsState.Autosaved -> "Saved"; else -> "" },
                style = MaterialTheme.typography.labelSmall, color = c.textTertiary,
            )
        }
        if (doc == null || doc.sections.isEmpty()) {
            EmptyState(SongPadIcons.Lyrics, "No lyrics yet", "Add a section — Verse, Chorus, Bridge — and write inside it. Sections can stay empty.", actionLabel = "Add section", onAction = { vm.beginInsertSection(null) })
        } else {
            val sorted = doc.sections.sortedBy { it.sortOrder }
            LaunchedEffect(scrollTarget, sorted) {
                if (scrollTarget == null) return@LaunchedEffect
                val index = sorted.indexOfFirst { it.id == scrollTarget }
                if (index >= 0) { listState.animateScrollToItem(index); onScrolled() }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(sorted, key = { _, it -> it.id }) { index, section ->
                    SectionEditor(
                        section, vm, canMoveUp = index > 0, canMoveDown = index < sorted.lastIndex,
                        onMoveUp = { vm.moveSection(section.id, index - 1) }, onMoveDown = { vm.moveSection(section.id, index + 1) },
                    )
                }
                item { Spacer(Modifier.height(Spacing.xxxl)) }
            }
        }
    }
    if (state is LyricsState.SectionInserting) {
        SongPadBottomSheet(onDismiss = vm::cancelInsertSection, title = "Add section") {
            SectionTag.entries.filter { it != SectionTag.CUSTOM }.forEach { tag ->
                Text(tag.label, Modifier.fillMaxWidth().clickable { vm.insertSection(tag) }.padding(Spacing.lg), color = c.text, style = MaterialTheme.typography.bodyLarge)
            }
            var custom by remember { mutableStateOf("") }
            Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(custom, { custom = it }, Modifier.weight(1f), label = { Text("Custom label") }, singleLine = true)
                Spacer(Modifier.width(Spacing.sm))
                SecondaryButton("Add", enabled = custom.isNotBlank(), onClick = { vm.insertSection(SectionTag.CUSTOM, custom.trim()) })
            }
        }
    }
}

@Composable
private fun SectionEditor(section: Section, vm: SongDetailViewModel, canMoveUp: Boolean, canMoveDown: Boolean, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    val c = SongPadTheme.colors
    var text by remember(section.id) { mutableStateOf(section.bodyText) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(section.displayLabel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.accent2)
            Spacer(Modifier.weight(1f))
            IconAction(Icons.Filled.KeyboardArrowUp, "Move up", enabled = canMoveUp, onClick = onMoveUp)
            IconAction(Icons.Filled.KeyboardArrowDown, "Move down", enabled = canMoveDown, onClick = onMoveDown)
            IconAction(Icons.Filled.Delete, "Delete section", onClick = { vm.showDialog(SongDetailDialog.ConfirmDeleteSection(section.id, section.displayLabel)) })
        }
        OutlinedTextField(
            value = text, onValueChange = { text = it; vm.editSection(section.id, it) },
            modifier = Modifier.fillMaxWidth(), placeholder = { Text("Write here…") }, minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
        )
    }
}

@Composable
private fun NotesTab(
    state: NotesState, vm: SongDetailViewModel, sections: List<Section>, takes: List<Take>,
    scrollTarget: String?, onScrolled: () -> Unit, onNavigateToRef: (SongDetailTab, String) -> Unit,
) {
    val c = SongPadTheme.colors
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.beginVoiceNote() }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.attachMedia(it.toString()) } }

    Column(Modifier.fillMaxSize()) {
        val draft = when (state) { is NotesState.Composing -> state.draftText; is NotesState.InvalidEmpty -> state.draftText; is NotesState.MediaAttached -> state.draftText; is NotesState.VoiceRecording -> state.draftText; else -> null }
        // §15 NoteRepository.update is bodyText-only — attach/voice controls only ever show for a *new* note.
        val canAttach = (state as? NotesState.Composing)?.editingNoteId == null
        if (draft != null) {
            Column(Modifier.padding(Spacing.screen)) {
                if (state is NotesState.MediaAttached) {
                    Box(Modifier.padding(bottom = Spacing.sm)) {
                        NotePhotoThumbnail(state.mediaUri)
                        IconAction(Icons.Filled.Close, "Remove photo", onClick = vm::removeMedia, modifier = Modifier.align(Alignment.TopEnd), filled = true)
                    }
                }
                if (state is NotesState.VoiceRecording) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.sm)) {
                        Icon(SongPadIcons.Mic, null, tint = c.accent)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Recording voice note…", color = c.text)
                    }
                }
                OutlinedTextField(
                    value = draft, onValueChange = vm::updateDraftText,
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("Note… use @Chorus or @Take name to reference") }, minLines = 3,
                    isError = state is NotesState.InvalidEmpty,
                    supportingText = if (state is NotesState.InvalidEmpty) ({ Text("A note needs text, a voice note, or an attachment.") }) else null,
                )
                Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    if (state is NotesState.VoiceRecording) {
                        IconAction(SongPadIcons.Stop, "Stop recording", onClick = vm::stopVoiceNote, filled = true)
                        SecondaryButton("Cancel", onClick = vm::cancelVoiceNote)
                    } else {
                        PrimaryButton("Save", onClick = vm::saveNote, enabled = draft.isNotBlank() || state is NotesState.MediaAttached)
                        SecondaryButton("Cancel", onClick = vm::cancelNote)
                        if (canAttach) {
                            Spacer(Modifier.weight(1f))
                            IconAction(
                                Icons.Filled.AddPhotoAlternate, "Attach photo",
                                onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            )
                            IconAction(SongPadIcons.Mic, "Record voice note", onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (granted) vm.beginVoiceNote() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            })
                        }
                    }
                }
            }
        } else {
            Row(Modifier.padding(Spacing.screen)) { PrimaryButton("New note", onClick = { vm.composeNote("") }) }
        }
        if (state.notes.isEmpty() && draft == null) {
            EmptyState(SongPadIcons.Lyrics, "No notes", "Capture a thought about this song. Reference a section or take with @.")
        } else {
            LaunchedEffect(scrollTarget, state.notes) {
                if (scrollTarget == null) return@LaunchedEffect
                val index = state.notes.indexOfFirst { it.id == scrollTarget }
                if (index >= 0) { listState.animateScrollToItem(index); onScrolled() }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(state.notes, key = { it.id }) { note -> NoteRow(note, vm, sections, takes, onNavigateToRef) }
                item { Spacer(Modifier.height(Spacing.xxxl)) }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, vm: SongDetailViewModel, sections: List<Section>, takes: List<Take>, onNavigateToRef: (SongDetailTab, String) -> Unit) {
    val c = SongPadTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(note.meta.updatedAt.dateLabel(), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            Spacer(Modifier.weight(1f))
            IconAction(Icons.Filled.Edit, "Edit note", onClick = { vm.composeNote(note.bodyText, note.id) })
            IconAction(Icons.Filled.Delete, "Delete note", onClick = { vm.showDialog(SongDetailDialog.ConfirmDeleteNote(note.id)) })
        }
        note.mediaUri?.let { uri -> NotePhotoThumbnail(uri, Modifier.padding(bottom = Spacing.sm)) }
        if (note.voiceTakeId != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.sm)) {
                Icon(SongPadIcons.Mic, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Voice note — play from the Takes tab", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }
        if (note.bodyText.isNotBlank()) NoteBody(note.bodyText, sections, takes, c.text, c.accent, onNavigateToRef)
    }
}

/** Renders `@mention` spans as tappable text, resolved live against this song's current sections/takes —
 * the same case-insensitive displayLabel/name match [SongDetailViewModel.resolveMentions] uses at save time.
 * An unmatched mention (typo, deleted section) just renders as plain text, same as it does unresolved in the model. */
@Composable
private fun NoteBody(text: String, sections: List<Section>, takes: List<Take>, textColor: Color, mentionColor: Color, onNavigateToRef: (SongDetailTab, String) -> Unit) {
    data class Ref(val tab: SongDetailTab, val id: String)
    val refs = mutableListOf<Ref?>()
    val annotated = buildAnnotatedString {
        var cursor = 0
        for (match in NoteValidation.mentionMatches(text)) {
            append(text.substring(cursor, match.range.first))
            val token = match.groupValues[1].trim()
            val section = sections.firstOrNull { it.displayLabel.equals(token, ignoreCase = true) }
            val take = takes.firstOrNull { it.name.equals(token, ignoreCase = true) }
            val ref = when { section != null -> Ref(SongDetailTab.LYRICS, section.id); take != null -> Ref(SongDetailTab.TAKES, take.id); else -> null }
            if (ref != null) {
                withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.SemiBold)) { append(match.value) }
            } else {
                append(match.value)
            }
            refs += ref
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    ClickableText(annotated, style = MaterialTheme.typography.bodyMedium.copy(color = textColor)) { offset ->
        NoteValidation.mentionMatches(text).forEachIndexed { i, match ->
            if (offset in match.range.first..match.range.last) refs.getOrNull(i)?.let { onNavigateToRef(it.tab, it.id) }
        }
    }
}

/** Decoded off the main thread and downsampled — this is a note attachment thumbnail, not a full viewer. */
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

