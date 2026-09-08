package com.songpad.feature.song.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.song.SongStatus
import com.songpad.core.model.state.QualityDisclosureState
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeStatus
import com.songpad.core.model.take.TakeType
import com.songpad.core.model.usecase.DurationFormat
import com.songpad.core.ui.components.ConfirmDialog
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.FilterPill
import com.songpad.core.ui.components.IconAction
import com.songpad.core.ui.components.InlineDisclosure
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.components.SectionHeader
import com.songpad.core.ui.components.SongListItem
import com.songpad.core.ui.components.SongPadBottomSheet
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.components.TakeListItem
import com.songpad.core.ui.components.TextInputDialog
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

/** Phase 0 §14 library: search bar hand-off, filter pills, songs, unassigned takes with "Add to Song". */
@Composable
fun LibraryScreen(
    onOpenSong: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecord: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val c = SongPadTheme.colors

    Column(Modifier.fillMaxSize()) {
        SongPadTopBar(title = "Songs", onBack = null) {
            IconAction(Icons.Filled.Search, "Search", onClick = onOpenSearch)
            IconAction(Icons.Filled.Settings, "Settings", onClick = onOpenSettings)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            SongFilter.entries.forEach { f ->
                FilterPill(f.label(), selected = state.filter == f, onClick = { viewModel.setFilter(f) })
                Spacer(Modifier.width(Spacing.sm))
            }
        }
        (state.disclosure as? QualityDisclosureState.FirstSaveDisclosure)?.let { d ->
            InlineDisclosure(
                text = "Recordings are saved in ${d.currentModeLabel} quality. You can change this in Settings — it applies to new recordings only.",
                actionLabel = "Settings", onAction = onOpenSettings, onDismiss = viewModel::dismissDisclosure,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
        }
        when (val s = state) {
            is LibraryState.Loading -> Unit
            is LibraryState.EmptyLibrary -> EmptyState(
                icon = SongPadIcons.MusicNote, title = "No songs yet",
                body = "Tap Record to capture an idea. Name it when you save and it becomes your first song.",
                actionLabel = "Record", onAction = onRecord, modifier = Modifier.weight(1f),
            )
            is LibraryState.Populated -> SongList(s.songs, s.unassignedTakes, s.filter, onOpenSong, viewModel, Modifier.weight(1f))
            is LibraryState.Filtered -> SongList(s.songs, s.unassignedTakes, s.filter, onOpenSong, viewModel, Modifier.weight(1f))
        }
    }

    when (val sh = sheet) {
        LibrarySheet.None -> Unit
        is LibrarySheet.AssignTake -> SongPadBottomSheet(onDismiss = viewModel::closeSheet, title = "Add \"${sh.take.name}\" to…") {
            Text("New song", Modifier.fillMaxWidth().clickable { viewModel.openNewSongForTake(sh.take) }.padding(Spacing.lg),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            sh.songs.forEach { song ->
                Text(song.name, Modifier.fillMaxWidth().clickable { viewModel.assignTake(sh.take.id, song.id) }.padding(Spacing.lg),
                    style = MaterialTheme.typography.bodyLarge, color = c.text)
            }
            if (sh.songs.isEmpty()) Text("No songs yet — create one above.", Modifier.padding(Spacing.lg), color = c.textSecondary)
        }
        is LibrarySheet.NewSongForTake -> TextInputDialog(
            title = "New song", label = "Song name", initialValue = "", confirmLabel = "Create",
            onConfirm = { name -> viewModel.createSongForTake(name, sh.take.id, onOpenSong) },
            onDismiss = viewModel::closeSheet,
        )
        is LibrarySheet.ConfirmDeleteTake -> ConfirmDialog(
            title = "Delete \"${sh.take.name}\"?", body = "The take is removed from your library. Its file is purged after 7 days.",
            confirmLabel = "Delete", onConfirm = { viewModel.deleteTake(sh.take.id) }, onDismiss = viewModel::closeSheet,
        )
    }
}

@Composable
private fun SongList(songs: List<Song>, unassigned: List<Take>, filter: SongFilter, onOpenSong: (String) -> Unit, vm: LibraryViewModel, modifier: Modifier) {
    val c = SongPadTheme.colors
    LazyColumn(modifier.fillMaxWidth()) {
        if (unassigned.isNotEmpty() && filter == SongFilter.ALL) {
            item { SectionHeader("Unassigned takes") }
            items(unassigned, key = { "take-" + it.id }) { take ->
                TakeListItem(
                    name = take.name, typeLabel = take.type.label(), typeIcon = take.type.icon(),
                    durationLabel = DurationFormat.mmss(take.durationMs), metaLine = take.meta.createdAt.dateLabel(),
                    statusBadge = take.statusBadge(), isPlaying = false,
                    onClick = { vm.openAssignSheet(take) }, onPlay = { vm.openAssignSheet(take) },
                    trailing = { SecondaryButton("Add to Song", onClick = { vm.openAssignSheet(take) }) },
                )
            }
            item { SectionHeader("Songs") }
        }
        if (songs.isEmpty()) item {
            Text("Nothing here with this filter.", Modifier.padding(Spacing.screen), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        items(songs, key = { it.id }) { song ->
            SongListItem(
                name = song.name, statusLabel = song.status.label(), metaLine = "Updated " + song.updatedAt.dateLabel(),
                isFavorite = song.isFavorite, onClick = { onOpenSong(song.id) }, onToggleFavorite = { vm.toggleFavorite(song.id) },
            )
        }
        item { Spacer(Modifier.height(Spacing.xxxl)) }
    }
}

internal fun SongFilter.label() = when (this) {
    SongFilter.ALL -> "All"; SongFilter.FAVORITES -> "Favorites"; SongFilter.ONGOING -> "Ongoing"; SongFilter.FINISHED -> "Finished"; SongFilter.ARCHIVED -> "Archived"
}
internal fun SongStatus.label(): String? = when (this) { SongStatus.ONGOING -> null; SongStatus.FINISHED -> "Finished"; SongStatus.ARCHIVED -> "Archived" }
internal fun TakeType.label() = when (this) { TakeType.VOICE -> "Voice"; TakeType.PIANO -> "Piano"; TakeType.VOICE_PIANO -> "Voice + Piano" }
internal fun TakeType.icon() = when (this) { TakeType.VOICE -> SongPadIcons.Mic; TakeType.PIANO -> SongPadIcons.Piano; TakeType.VOICE_PIANO -> SongPadIcons.Waveform }
internal fun Take.statusBadge(): String? = when (status) {
    TakeStatus.TRANSCODING -> "Processing"; TakeStatus.FAILED -> "Failed"; TakeStatus.ORPHANED -> "Recovered"; else -> null
}
internal fun Long.dateLabel(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
