package com.songpad.spikes.s4

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.songpad.core.data.db.fts.FtsQueryBuilder
import com.songpad.core.data.db.fts.SearchIndexDdl
import com.songpad.core.model.id.UuidV7

/**
 * Minimal schema mirror for the spike: the four indexed tables with the columns the triggers touch, created
 * as Room would (TEXT UUIDv7 keys, INTEGER timestamps, the sync block), plus the shared FTS DDL.
 */
class S4Harness(path: String = ":memory:") : AutoCloseable {
    val connection: SQLiteConnection = BundledSQLiteDriver().open(path)
    val ids = UuidV7()

    init {
        connection.execSQL("PRAGMA journal_mode = WAL")
        connection.execSQL("PRAGMA synchronous = NORMAL")
        connection.execSQL("PRAGMA foreign_keys = ON")
        SCHEMA.forEach(connection::execSQL)
        SearchIndexDdl.allStatements.forEach(connection::execSQL)
    }

    fun sqliteVersion(): String = connection.prepare("SELECT sqlite_version()").use { it.step(); it.getText(0) }
    fun fts5Available(): Boolean = connection.prepare("SELECT sqlite_compileoption_used('ENABLE_FTS5')").use { it.step(); it.getLong(0) == 1L }

    fun insertSong(name: String): String = ids.next().also { id ->
        connection.prepare("INSERT INTO song(id,name,status,is_favorite,created_at,updated_at,deleted,deleted_at,sync_state,revision) VALUES(?,?,'ONGOING',0,?,?,0,NULL,'LOCAL_ONLY',1)").use {
            it.bindText(1, id); it.bindText(2, name); it.bindLong(3, now()); it.bindLong(4, now()); it.step()
        }
    }

    /** Inserts [count] takes inside one transaction (the way the repository commits). Returns elapsed ms. */
    fun insertTakes(count: Int, songId: String?): Long {
        val t0 = System.nanoTime()
        connection.execSQL("BEGIN IMMEDIATE")
        connection.prepare("INSERT INTO take(id,song_id,name,type,status,duration_ms,audio_asset_id,midi_asset_id,created_at,updated_at,deleted,deleted_at,sync_state,revision) VALUES(?,?,?,'VOICE','READY',15000,NULL,NULL,?,?,0,NULL,'LOCAL_ONLY',1)").use { st ->
            repeat(count) { i ->
                st.reset()
                st.bindText(1, ids.next()); if (songId == null) st.bindNull(2) else st.bindText(2, songId)
                st.bindText(3, "Take ${i + 1} ${WORDS[i % WORDS.size]}"); st.bindLong(4, now()); st.bindLong(5, now())
                st.step()
            }
        }
        connection.execSQL("COMMIT")
        return (System.nanoTime() - t0) / 1_000_000
    }

    fun insertSection(songId: String, tag: String, number: Int?, body: String): String {
        val docId = connection.prepare("SELECT id FROM lyrics_document WHERE song_id = ?").use { it.bindText(1, songId); if (it.step()) it.getText(0) else null }
            ?: ids.next().also { d ->
                connection.prepare("INSERT INTO lyrics_document(id,song_id,created_at,updated_at,deleted,deleted_at,sync_state,revision) VALUES(?,?,?,?,0,NULL,'LOCAL_ONLY',1)").use {
                    it.bindText(1, d); it.bindText(2, songId); it.bindLong(3, now()); it.bindLong(4, now()); it.step()
                }
            }
        return ids.next().also { id ->
            connection.prepare("INSERT INTO section(id,document_id,tag,custom_label,number,body_text,sort_order,created_at,updated_at,deleted,deleted_at,sync_state,revision) VALUES(?,?,?,NULL,?,?,0,?,?,0,NULL,'LOCAL_ONLY',1)").use {
                it.bindText(1, id); it.bindText(2, docId); it.bindText(3, tag); if (number == null) it.bindNull(4) else it.bindLong(4, number.toLong())
                it.bindText(5, body); it.bindLong(6, now()); it.bindLong(7, now()); it.step()
            }
        }
    }

    fun insertNote(songId: String, body: String): String = ids.next().also { id ->
        connection.prepare("INSERT INTO note(id,song_id,body_text,voice_take_id,media_uri,section_ref_id,take_ref_id,created_at,updated_at,deleted,deleted_at,sync_state,revision) VALUES(?,?,?,NULL,NULL,NULL,NULL,?,?,0,NULL,'LOCAL_ONLY',1)").use {
            it.bindText(1, id); it.bindText(2, songId); it.bindText(3, body); it.bindLong(4, now()); it.bindLong(5, now()); it.step()
        }
    }

    fun softDeleteTake(id: String) { connection.prepare("UPDATE take SET deleted = 1, deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE id = ?").use { it.bindLong(1, now()); it.bindLong(2, now()); it.bindText(3, id); it.step() } }

    data class Row(val entityId: String, val songId: String?, val type: String, val title: String, val snippet: String, val rank: Double)

    /** Runs the §10.1 query. Returns rows and elapsed microseconds. */
    fun search(raw: String, songId: String? = null): Pair<List<Row>, Long> {
        val match = FtsQueryBuilder.build(raw) ?: return emptyList<Row>() to 0L
        val t0 = System.nanoTime()
        val rows = connection.prepare(SearchIndexDdl.searchSql).use { st ->
            st.bindText(1, match); if (songId == null) st.bindNull(2) else st.bindText(2, songId)
            buildList { while (st.step()) add(Row(st.getText(0), if (st.isNull(1)) null else st.getText(1), st.getText(2), st.getText(3), st.getText(4), st.getDouble(5))) }
        }
        return rows to (System.nanoTime() - t0) / 1_000
    }

    fun indexCount(): Long = connection.prepare("SELECT COUNT(*) FROM ${SearchIndexDdl.TABLE}").use { it.step(); it.getLong(0) }

    override fun close() = connection.close()

    private fun now() = System.currentTimeMillis()

    companion object {
        val WORDS = listOf("midnight", "riff", "neon", "rain", "chorus idea", "bridge", "hook", "verse sketch", "outro", "café")

        private const val SYNC = "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, deleted_at INTEGER, sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY', revision INTEGER NOT NULL DEFAULT 1"
        val SCHEMA = listOf(
            "CREATE TABLE song(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, status TEXT NOT NULL, is_favorite INTEGER NOT NULL DEFAULT 0, $SYNC)",
            "CREATE INDEX idx_song_updated ON song(updated_at)",
            "CREATE TABLE take(id TEXT NOT NULL PRIMARY KEY, song_id TEXT REFERENCES song(id) ON DELETE CASCADE, name TEXT NOT NULL, type TEXT NOT NULL, status TEXT NOT NULL, duration_ms INTEGER NOT NULL, audio_asset_id TEXT, midi_asset_id TEXT, $SYNC)",
            "CREATE INDEX idx_take_song ON take(song_id)", "CREATE INDEX idx_take_status ON take(status)", "CREATE INDEX idx_take_updated ON take(updated_at)", "CREATE INDEX idx_take_sync ON take(sync_state)",
            "CREATE TABLE lyrics_document(id TEXT NOT NULL PRIMARY KEY, song_id TEXT NOT NULL UNIQUE REFERENCES song(id) ON DELETE CASCADE, $SYNC)",
            "CREATE TABLE section(id TEXT NOT NULL PRIMARY KEY, document_id TEXT NOT NULL REFERENCES lyrics_document(id) ON DELETE CASCADE, tag TEXT NOT NULL, custom_label TEXT, number INTEGER, body_text TEXT NOT NULL, sort_order INTEGER NOT NULL, $SYNC)",
            "CREATE TABLE note(id TEXT NOT NULL PRIMARY KEY, song_id TEXT NOT NULL REFERENCES song(id) ON DELETE CASCADE, body_text TEXT NOT NULL, voice_take_id TEXT, media_uri TEXT, section_ref_id TEXT, take_ref_id TEXT, $SYNC)",
        )
    }
}
