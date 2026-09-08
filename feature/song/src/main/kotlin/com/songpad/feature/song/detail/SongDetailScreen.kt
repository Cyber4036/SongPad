package com.songpad.feature.song.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.lyrics.SectionTag
import com.songpad.core.model.note.Note
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
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.song.library.dateLabel
import com.songpad.feature.song.library.icon
import com.songpad.feature.song.library.label
import com.songpad.feature.song.library.statusBadge

/** Phase 0 §15 Song Home with Lyrics / Takes / Notes tabs. */
@Composable
fun SongDetailScreen(
    songId: String,
    initialTab: SongDetailTab,
    onBack: () -> Unit,
    onRecord: (String) -> Unit,
    onExport: (String) -> Unit,
    onSearchInSong: (String) -> Unit,
    viewModel: SongDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(songId) { viewModel.bind(songId, initialTab) }
    LaunchedEffect(Unit) { viewModel.navigateToLibrary.collect { onBack() } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val c = SongPadTheme.colors

    when (val s = state) {
        SongDetailState.Loading -> Column(Modifier.fillMaxSize()) { SongPadTopBar(title = null, onBack = onBack) }
        SongDetailState.NotFound -> Column(Modifier.fillMaxSize()) {
            SongPadTopBar(title = null, onBack = onBack)
            EmptyState(SongPadIcons.MusicNote, "This song is gone", "It may have been deleted.", actionLabel = "Back to library", onAction = onBack)
        }
        is SongDetailState.Loaded -> Column(Modifier.fillMaxSize()) {
            var menu by remember { mutableStateOf(false) }
            SongPadTopBar(title = s.song.name, onBack = onBack) {
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
                    SongDetailTab.LYRICS -> LyricsTab(s.lyrics, viewModel)
                    SongDetailTab.TAKES -> TakesTab(s.takes, viewModel, onRecord = { onRecord(s.song.id) })
                    SongDetailTab.NOTES -> NotesTab(s.notes, viewModel)
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

@Composable
private fun TakesTab(state: TakeListState, vm: SongDetailViewModel, onRecord: () -> Unit) {
    val c = SongPadTheme.colors
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
            TakeFilter.entries.forEach { f -> FilterPill(f.label, selected = state.filter == f, onClick = { vm.setTakeFilter(f) }); Spacer(Modifier.width(Spacing.sm)) }
        }
        when (state) {
            is TakeListState.Empty -> EmptyState(SongPadIcons.Mic, "No takes", if (state.filter == TakeFilter.ALL) "Record your first take for this song." else "No ${state.filter.label} takes yet.", actionLabel = "Record", onAction = onRecord)
            is TakeListState.Populated -> TakeList(state.takes, null, vm)
            is TakeListState.Playing -> TakeList(state.takes, state.playing, vm)
            is TakeListState.Transcoding -> TakeList(state.takes, state.playing, vm)
            is TakeListState.Unavailable -> Column {
                Text(state.reason, Modifier.padding(Spacing.screen), color = c.warning, style = MaterialTheme.typography.bodyMedium)
                TakeList(state.takes, null, vm)
            }
        }
    }
}

@Composable
private fun TakeList(takes: List<Take>, np: NowPlaying?, vm: SongDetailViewModel) {
    LazyColumn(Modifier.fillMaxSize()) { items(takes, key = { it.id }) { TakeRow(it, np, vm) }; item { Spacer(Modifier.height(Spacing.xxxl)) } }
}

@Composable
private fun LyricsTab(state: LyricsState, vm: SongDetailViewModel) {
    val c = SongPadTheme.colors
    val doc = when (state) { is LyricsState.Editing -> state.document; is LyricsState.Autosaved -> state.document; is LyricsState.SectionInserting -> state.document; LyricsState.Empty -> null }
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
            LazyColumn(Modifier.fillMaxSize()) {
                items(doc.sections.sortedBy { it.sortOrder }, key = { it.id }) { section -> SectionEditor(section, vm) }
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
private fun SectionEditor(section: Section, vm: SongDetailViewModel) {
    val c = SongPadTheme.colors
    var text by remember(section.id) { mutableStateOf(section.bodyText) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(section.displayLabel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.accent2)
            Spacer(Modifier.weight(1f))
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
private fun NotesTab(state: NotesState, vm: SongDetailViewModel) {
    val c = SongPadTheme.colors
    Column(Modifier.fillMaxSize()) {
        val draft = when (state) { is NotesState.Composing -> state.draftText; is NotesState.InvalidEmpty -> state.draftText; is NotesState.MediaAttached -> state.draftText; is NotesState.VoiceRecording -> state.draftText; else -> null }
        if (draft != null) {
            Column(Modifier.padding(Spacing.screen)) {
                OutlinedTextField(
                    value = draft, onValueChange = { vm.composeNote(it, (state as? NotesState.Composing)?.editingNoteId) },
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("Note… use @Chorus or @Take name to reference") }, minLines = 3,
                    isError = state is NotesState.InvalidEmpty,
                    supportingText = if (state is NotesState.InvalidEmpty) ({ Text("A note needs text, a voice note, or an attachment.") }) else null,
                )
                Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryButton("Save", onClick = vm::saveNote, enabled = draft.isNotBlank())
                    SecondaryButton("Cancel", onClick = vm::cancelNote)
                }
            }
        } else {
            Row(Modifier.padding(Spacing.screen)) { PrimaryButton("New note", onClick = { vm.composeNote("") }) }
        }
        if (state.notes.isEmpty() && draft == null) {
            EmptyState(SongPadIcons.Lyrics, "No notes", "Capture a thought about this song. Reference a section or take with @.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.notes, key = { it.id }) { note -> NoteRow(note, vm) }
                item { Spacer(Modifier.height(Spacing.xxxl)) }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, vm: SongDetailViewModel) {
    val c = SongPadTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(note.meta.updatedAt.dateLabel(), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            Spacer(Modifier.weight(1f))
            IconAction(Icons.Filled.Edit, "Edit note", onClick = { vm.composeNote(note.bodyText, note.id) })
            IconAction(Icons.Filled.Delete, "Delete note", onClick = { vm.showDialog(SongDetailDialog.ConfirmDeleteNote(note.id)) })
        }
        Text(note.bodyText, color = c.text, style = MaterialTheme.typography.bodyMedium)
    }
}

