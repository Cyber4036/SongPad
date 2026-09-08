package com.songpad.core.data.repo

import androidx.room.RoomRawQuery
import com.songpad.core.data.db.SongPadDatabase
import com.songpad.core.data.db.dao.SearchRow
import com.songpad.core.data.db.fts.FtsQueryBuilder
import com.songpad.core.data.db.fts.SearchIndexDdl
import com.songpad.core.model.data.NoteMatch
import com.songpad.core.model.data.SearchEngine
import com.songpad.core.model.data.SearchResults
import com.songpad.core.model.data.SectionMatch
import com.songpad.core.model.data.SongMatch
import com.songpad.core.model.data.TakeMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** §10: FTS5 with bm25 ranking and snippet highlighting, over the data model, never UI state. Global and Song-scoped are one query plus a filter. */
@Singleton
class SearchEngineImpl @Inject constructor(private val db: SongPadDatabase) : SearchEngine {

    override fun searchGlobal(query: String): Flow<SearchResults> = run(query, null)
    override fun searchInSong(songId: String, query: String): Flow<SearchResults> = run(query, songId)

    private fun run(query: String, songId: String?): Flow<SearchResults> {
        val match = FtsQueryBuilder.build(query) ?: return flowOf(SearchResults.EMPTY)
        val raw = RoomRawQuery(SearchIndexDdl.searchSql) { stmt ->
            stmt.bindText(1, match)
            if (songId == null) stmt.bindNull(2) else stmt.bindText(2, songId)
        }
        return db.searchDao().search(raw).map { rows -> group(rows) }
    }

    private fun group(rows: List<SearchRow>): SearchResults = SearchResults(
        songs = rows.filter { it.entity_type == SearchIndexDdl.TYPE_SONG }.map { SongMatch(it.entity_id, it.search_title, it.snippet_text.ifBlank { it.search_title }, it.rank) },
        lyrics = rows.filter { it.entity_type == SearchIndexDdl.TYPE_SECTION }.map { SectionMatch(it.entity_id, it.song_id ?: "", it.search_title, it.snippet_text, it.rank) },
        notes = rows.filter { it.entity_type == SearchIndexDdl.TYPE_NOTE }.map { NoteMatch(it.entity_id, it.song_id ?: "", it.snippet_text, it.rank) },
        takes = rows.filter { it.entity_type == SearchIndexDdl.TYPE_TAKE }.map { TakeMatch(it.entity_id, it.song_id, it.search_title, it.snippet_text.ifBlank { it.search_title }, it.rank) },
    )
}
