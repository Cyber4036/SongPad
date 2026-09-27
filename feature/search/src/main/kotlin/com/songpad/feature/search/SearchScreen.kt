package com.songpad.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.songpad.core.ui.components.EmptyState
import com.songpad.core.ui.components.FilterPill
import com.songpad.core.ui.components.IconAction
import com.songpad.core.ui.components.SectionHeader
import com.songpad.core.ui.components.SongPadTopBar
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.Spacing

/** Phase 0 §14 / spec §10: grouped results — Songs, Lyrics, Notes, Takes — each with a highlighted snippet. */
@Composable
fun SearchScreen(
    songId: String?,
    onBack: () -> Unit,
    onOpenSong: (songId: String) -> Unit,
    onOpenLyrics: (songId: String, sectionId: String) -> Unit,
    onOpenNotes: (songId: String, noteId: String) -> Unit,
    onOpenTakes: (songId: String, takeId: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    LaunchedEffect(songId) { viewModel.bind(songId) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val c = SongPadTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        SongPadTopBar(title = if (songId == null) "Search" else "Search in song", onBack = onBack)
        OutlinedTextField(
            value = query, onValueChange = viewModel::setQuery, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).focusRequester(focus),
            placeholder = { Text("Lyrics, notes, takes, songs…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconAction(Icons.Filled.Close, "Clear", onClick = viewModel::clear) },
        )
        when (val s = state) {
            is SearchState.Idle -> if (s.query.isEmpty()) EmptyState(SongPadIcons.Lyrics, "Search everything", "Words in lyrics, notes, take names and song names. Diacritics and case don't matter.")
            is SearchState.Searching -> Text("Searching…", Modifier.padding(Spacing.screen), color = c.textSecondary)
            is SearchState.NoResults -> EmptyState(SongPadIcons.Lyrics, "No matches for \"${s.query}\"", "Try a shorter word or a different spelling.")
            is SearchState.Failed -> EmptyState(SongPadIcons.Lyrics, "Search hit a snag", s.message)
            is SearchState.Results -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
                    SearchFilter.entries.forEach { f -> FilterPill(f.label, selected = filter == f, onClick = { viewModel.setFilter(f) }); Spacer(Modifier.width(Spacing.sm)) }
                }
                val r = s.results
                LazyColumn(Modifier.fillMaxSize()) {
                    if (r.songs.isNotEmpty() && (filter == SearchFilter.ALL || filter == SearchFilter.SONGS)) {
                        item { SectionHeader("Songs") }
                        items(r.songs, key = { "s" + it.songId }) { m -> ResultRow(m.name, SnippetHighlight.annotate(m.snippet, c.accent)) { onOpenSong(m.songId) } }
                    }
                    if (r.lyrics.isNotEmpty() && (filter == SearchFilter.ALL || filter == SearchFilter.LYRICS)) {
                        item { SectionHeader("Lyrics") }
                        items(r.lyrics, key = { "l" + it.sectionId }) { m -> ResultRow(m.label, SnippetHighlight.annotate(m.snippet, c.accent)) { onOpenLyrics(m.songId, m.sectionId) } }
                    }
                    if (r.notes.isNotEmpty() && (filter == SearchFilter.ALL || filter == SearchFilter.NOTES)) {
                        item { SectionHeader("Notes") }
                        items(r.notes, key = { "n" + it.noteId }) { m -> ResultRow("Note", SnippetHighlight.annotate(m.snippet, c.accent)) { onOpenNotes(m.songId, m.noteId) } }
                    }
                    if (r.takes.isNotEmpty() && (filter == SearchFilter.ALL || filter == SearchFilter.TAKES)) {
                        item { SectionHeader("Takes") }
                        items(r.takes, key = { "t" + it.takeId }) { m ->
                            ResultRow(m.name, SnippetHighlight.annotate(m.snippet, c.accent)) { m.songId?.let { onOpenTakes(it, m.takeId) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, snippet: AnnotatedString, onClick: () -> Unit) {
    val c = SongPadTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = c.text)
        Text(snippet, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2)
    }
}
