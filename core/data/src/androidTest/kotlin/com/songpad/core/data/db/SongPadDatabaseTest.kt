package com.songpad.core.data.db

import androidx.room.RoomRawQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.db.entity.LyricsDocumentEntity
import com.songpad.core.data.db.entity.NoteEntity
import com.songpad.core.data.db.entity.SectionEntity
import com.songpad.core.data.db.entity.SongEntity
import com.songpad.core.data.db.entity.SyncColumns
import com.songpad.core.data.db.entity.TakeEntity
import com.songpad.core.data.db.fts.FtsQueryBuilder
import com.songpad.core.data.db.fts.SearchIndexDdl
import com.songpad.core.model.id.UuidV7
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** §18 "Room + FTS5": cascades, soft-delete filtering, trigger sync, prefix queries, bm25 ordering, snippet output. */
@RunWith(AndroidJUnit4::class)
class SongPadDatabaseTest {
    private lateinit var db: SongPadDatabase
    private val ids = UuidV7()
    private fun sync(now: Long = 1_000) = SyncColumns(now, now)

    @Before fun setUp() { db = SongPadDatabase.buildInMemory(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { db.close() }

    private suspend fun search(q: String, songId: String? = null) = db.searchDao().searchOnce(
        RoomRawQuery(SearchIndexDdl.searchSql) { s -> s.bindText(1, FtsQueryBuilder.build(q)!!); if (songId == null) s.bindNull(2) else s.bindText(2, songId) },
    )

    @Test
    fun triggers_keep_the_index_in_sync_and_soft_delete_removes_rows() = runTest {
        val song = SongEntity(ids.next(), "Midnight Riff", "ONGOING", 0, sync())
        db.songDao().insert(song)
        assertThat(search("midn").map { it.entity_type }).containsExactly(SearchIndexDdl.TYPE_SONG)

        db.songDao().update(song.copy(name = "Morning Riff"))
        assertThat(search("midn")).isEmpty()
        assertThat(search("morn").single().search_title).isEqualTo("Morning Riff")

        db.songDao().update(song.copy(name = "Morning Riff", sync = sync().copy(deleted = 1, deletedAt = 5)))
        assertThat(search("morn")).isEmpty()
    }

    @Test
    fun sections_index_the_human_label_and_snippet_highlights() = runTest {
        val song = SongEntity(ids.next(), "S", "ONGOING", 0, sync())
        db.songDao().insert(song)
        val doc = LyricsDocumentEntity(ids.next(), song.id, sync())
        db.lyricsDao().insertDocument(doc)
        db.lyricsDao().insertSection(SectionEntity(ids.next(), doc.id, "PRE_CHORUS", null, 2, "hold the line under neon light", 0, sync()))
        val rows = search("neon")
        assertThat(rows.single().search_title).isEqualTo("Pre-Chorus 2")
        assertThat(rows.single().snippet_text).contains("<b>neon</b>")
        assertThat(search("pre").single().entity_type).isEqualTo(SearchIndexDdl.TYPE_SECTION)
        assertThat(search("neon", songId = song.id)).hasSize(1)
        assertThat(search("neon", songId = "other")).isEmpty()
    }

    @Test
    fun bm25_ranks_title_matches_above_content_matches() = runTest {
        val song = SongEntity(ids.next(), "Rain", "ONGOING", 0, sync())
        db.songDao().insert(song)
        db.noteDao().insert(NoteEntity(ids.next(), song.id, "a note that mentions rain once among many other words here", null, null, null, null, sync()))
        val rows = search("rain")
        assertThat(rows.first().entity_type).isEqualTo(SearchIndexDdl.TYPE_SONG)
        assertThat(rows.first().rank).isLessThan(rows.last().rank) // bm25 is negative; lower is better
    }

    @Test
    fun cascades_and_set_null_follow_the_schema() = runTest {
        val song = SongEntity(ids.next(), "S", "ONGOING", 0, sync())
        db.songDao().insert(song)
        val take = TakeEntity(ids.next(), song.id, "T", "VOICE", "READY", 1000, null, null, sync())
        db.takeDao().insert(take)
        val note = NoteEntity(ids.next(), song.id, "n", take.id, null, null, null, sync())
        db.noteDao().insert(note)
        db.takeDao().hardDelete(take.id)
        assertThat(db.noteDao().get(note.id)!!.voiceTakeId).isNull()
        db.songDao().hardDelete(song.id)
        assertThat(db.noteDao().get(note.id)).isNull()
        assertThat(search("n")).isEmpty()
    }

    @Test
    fun unassigned_takes_are_first_class() = runTest {
        db.takeDao().insert(TakeEntity(ids.next(), null, "Loose", "VOICE", "READY", 1000, null, null, sync()))
        assertThat(db.takeDao().unassigned()).hasSize(1)
        assertThat(search("loo").single().song_id).isNull()
    }
}
