package com.songpad.core.data.testing

import com.songpad.core.model.data.NoteMatch
import com.songpad.core.model.data.SearchEngine
import com.songpad.core.model.data.SearchResults
import com.songpad.core.model.data.SectionMatch
import com.songpad.core.model.data.SongMatch
import com.songpad.core.model.data.TakeMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Substring search over the in-memory store, grouped like the real engine. Snippets carry `<b>` markers. */
class FakeSearchEngine(val store: InMemoryStore = InMemoryStore()) : SearchEngine {

    override fun searchGlobal(query: String): Flow<SearchResults> = search(query, null)
    override fun searchInSong(songId: String, query: String): Flow<SearchResults> = search(query, songId)

    private fun search(query: String, songId: String?): Flow<SearchResults> {
        val q = query.trim()
        if (q.isEmpty()) return flowOf(SearchResults.EMPTY)
        return combine(store.songs, store.takes, store.documents, store.notes) { songs, takes, docs, notes ->
            fun hit(text: String) = text.contains(q, ignoreCase = true)
            fun snip(text: String): String {
                val i = text.indexOf(q, ignoreCase = true)
                return if (i < 0) text else text.substring(0, i) + "<b>" + text.substring(i, i + q.length) + "</b>" + text.substring(i + q.length)
            }
            SearchResults(
                songs = if (songId == null) songs.values.filter { !it.meta.deleted && hit(it.name) }.map { SongMatch(it.id, it.name, snip(it.name), -1.0) } else emptyList(),
                lyrics = docs.values.filter { songId == null || it.songId == songId }.flatMap { d -> d.sections.filter { hit(it.bodyText) || hit(it.displayLabel) }.map { SectionMatch(it.id, d.songId, it.displayLabel, snip(it.bodyText), -1.0) } },
                notes = notes.values.filter { !it.meta.deleted && (songId == null || it.songId == songId) && hit(it.bodyText) }.map { NoteMatch(it.id, it.songId, snip(it.bodyText), -1.0) },
                takes = takes.values.filter { !it.meta.deleted && (songId == null || it.songId == songId) && hit(it.name) }.map { TakeMatch(it.id, it.songId, it.name, snip(it.name), -1.0) },
            )
        }
    }
}
