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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.songpad.core.audio.recovery.AudioRecoveryManager
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
import com.songpad.core.ui.components.PrimaryButton
import com.songpad.core.ui.components.SecondaryButton
import com.songpad.core.ui.components.SectionHeader
import com.songpad.core.ui.components.SongListItem
import com.songpad.core.ui.components.SongPadBottomSheet
import com.songpad.core.ui.components.RibbonPlayer
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.components.TakeListItem
import com.songpad.core.ui.components.TextInputDialog
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing
import com.songpad.feature.song.detail.NowPlaying
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
    val recovery by viewModel.recoveryState.collectAsStateWithLifecycle()
    val c = SongPadTheme.colors

    Column(Modifier.fillMaxSize()) {
        SongPadTopBar(title = "Songs", onBack = null) {
            IconAction(Icons.Filled.Search, "Search", onClick = onOpenSearch)
            IconAction(Icons.Filled.Settings, "Settings", onClick = onOpenSettings)
        }
        RecoveryBanner(recovery, viewModel)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            SongFilter.entries.forEach { f ->
                FilterPill(f.label(), selected = state.filter == f, onClick = { viewModel.setFilter(f) })
                Spacer(Modifier.width(Spacing.sm))
            }
        }
        (state.disclosure as? QualityDisclosureState.FirstSaveDisclosure)?.let {
            InlineDisclosure(
                text = "Recording in Standard quality. Lossless uses about 3× the space and applies to new recordings only.",
                actionLabel = "Change →", onAction = onOpenSettings, onDismiss = viewModel::dismissDisclosure,
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
            is LibraryState.Populated -> SongList(s.songs, s.unassignedTakes, s.nowPlaying, s.filter, onOpenSong, viewModel, Modifier.weight(1f))
            is LibraryState.Filtered -> SongList(s.songs, s.unassignedTakes, s.nowPlaying, s.filter, onOpenSong, viewModel, Modifier.weight(1f))
        }
        state.nowPlaying?.let { np ->
            RibbonPlayer(
                title = np.take.name, subtitle = np.take.type.label(), isPlaying = np.isPlaying, progress = np.progress,
                positionLabel = "${DurationFormat.mmss(np.positionMs)} / ${DurationFormat.mmss(np.durationMs)}", peaks = np.peaks,
                onPlayPause = { viewModel.togglePlayPause(np.take) }, onSeek = viewModel::seekTo, onDismiss = viewModel::dismissPlayer,
            )
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

/**
 * §9.4 / §21.3 #3 final decision: ONE non-blocking banner at the top of the Library — no modal, and never
 * shown on the Record screen. Discard still confirms first (Phase 0 destructive-action rule), via a real
 * dialog; that's a separate concern from the banner itself being non-blocking.
 */
@Composable
private fun RecoveryBanner(state: AudioRecoveryManager.RecoveryState, vm: LibraryViewModel) {
    val c = SongPadTheme.colors
    when (state) {
        AudioRecoveryManager.RecoveryState.None -> Unit
        is AudioRecoveryManager.RecoveryState.Prompt -> {
            val count = state.orphans.count { !it.isNoise }
            val totalMs = state.orphans.sumOf { it.durationMs }
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
                Text(
                    if (count == 1) "We recovered ${DurationFormat.mmss(totalMs)} of an unfinished recording. Keep it?"
                    else "We recovered $count unfinished recordings (${DurationFormat.mmss(totalMs)} total). Keep them?",
                    style = MaterialTheme.typography.bodyMedium, color = c.text,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row {
                    PrimaryButton("Keep", onClick = vm::keepRecovered)
                    Spacer(Modifier.width(Spacing.sm))
                    SecondaryButton("Discard", onClick = vm::requestDiscardRecovered, destructive = true)
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
                Modifier.weight(1f), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium,
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
private fun SongList(songs: List<Song>, unassigned: List<Take>, nowPlaying: NowPlaying?, filter: SongFilter, onOpenSong: (String) -> Unit, vm: LibraryViewModel, modifier: Modifier) {
    val c = SongPadTheme.colors
    LazyColumn(modifier.fillMaxWidth()) {
        if (unassigned.isNotEmpty() && filter == SongFilter.ALL) {
            item { SectionHeader("Unassigned takes") }
            items(unassigned, key = { "take-" + it.id }) { take ->
                TakeListItem(
                    name = take.name, typeLabel = take.type.label(), typeIcon = take.type.icon(),
                    durationLabel = DurationFormat.mmss(take.durationMs), metaLine = take.meta.createdAt.dateLabel(),
                    statusBadge = take.statusBadge(), isPlaying = nowPlaying?.take?.id == take.id && nowPlaying.isPlaying,
                    onClick = { vm.openAssignSheet(take) }, onPlay = { vm.togglePlayPause(take) },
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
