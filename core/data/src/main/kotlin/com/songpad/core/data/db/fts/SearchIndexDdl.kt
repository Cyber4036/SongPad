package com.songpad.core.data.db.fts

/**
 * §10.1 FTS5 index and its maintenance triggers, as hand-written DDL (§6.4: Room's `@Fts4` is not used,
 * FTS4 is not used at all). Executed in `RoomDatabase.Callback.onCreate` for fresh installs and replayed
 * by any migration that needs to rebuild it.
 *
 * Design note (recorded per the "rejected alternatives" rule): the spec calls this an *external-content*
 * table, but FTS5 external content binds to exactly ONE content table and this index spans four
 * (song, section, note, take). The spec's own DDL carries no `content=` option, so this is a standard
 * content-storing FTS5 table fed by triggers. The alternative — a unified `search_source` content table
 * plus an external-content index — doubles the trigger surface for a storage saving of a few kilobytes of
 * lyric text. Rejected.
 *
 * This file must stay free of Android imports: the S-4 spike compiles it on the JVM.
 */
object SearchIndexDdl {
    const val TABLE = "search_index"

    const val TYPE_SONG = "song"
    const val TYPE_SECTION = "section"
    const val TYPE_NOTE = "note"
    const val TYPE_TAKE = "take"

    val createTable = """
        CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts5(
            search_title,
            search_content,
            entity_id      UNINDEXED,
            song_id        UNINDEXED,
            entity_type    UNINDEXED,
            tokenize = "unicode61 remove_diacritics 2",
            prefix = '2 3 4'
        )
    """.trimIndent()

    /** Human label for a section row, computed in SQL so the index holds "Pre-Chorus 2", not "PRE_CHORUS". */
    private const val SECTION_LABEL = """
        CASE new.tag
            WHEN 'CUSTOM' THEN COALESCE(NULLIF(new.custom_label, ''), 'Custom')
            ELSE (CASE new.tag
                    WHEN 'INTRO' THEN 'Intro' WHEN 'VERSE' THEN 'Verse' WHEN 'PRE_CHORUS' THEN 'Pre-Chorus'
                    WHEN 'CHORUS' THEN 'Chorus' WHEN 'POST_CHORUS' THEN 'Post-Chorus' WHEN 'BRIDGE' THEN 'Bridge'
                    WHEN 'OUTRO' THEN 'Outro' ELSE new.tag END)
                 || COALESCE(' ' || new.number, '')
        END
    """

    private const val SECTION_SONG = "(SELECT song_id FROM lyrics_document WHERE id = new.document_id)"

    private fun deleteByEntity(idExpr: String) = "DELETE FROM $TABLE WHERE entity_id = $idExpr"

    val triggers: List<String> = listOf(
        // ── song ──
        """CREATE TRIGGER IF NOT EXISTS song_ai AFTER INSERT ON song WHEN new.deleted = 0 BEGIN
             INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type)
             VALUES (new.name, '', new.id, new.id, '$TYPE_SONG');
           END""",
        """CREATE TRIGGER IF NOT EXISTS song_au AFTER UPDATE ON song BEGIN
             ${deleteByEntity("old.id")};
             INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type)
             SELECT new.name, '', new.id, new.id, '$TYPE_SONG' WHERE new.deleted = 0;
           END""",
        """CREATE TRIGGER IF NOT EXISTS song_ad AFTER DELETE ON song BEGIN ${deleteByEntity("old.id")}; END""",
        // ── section ──
        """CREATE TRIGGER IF NOT EXISTS section_ai AFTER INSERT ON section WHEN new.deleted = 0 BEGIN
             INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type)
             VALUES ($SECTION_LABEL, new.body_text, new.id, $SECTION_SONG, '$TYPE_SECTION');
           END""",
        """CREATE TRIGGER IF NOT EXISTS section_au AFTER UPDATE ON section BEGIN
             ${deleteByEntity("old.id")};
             INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type)
             SELECT $SECTION_LABEL, new.body_text, new.id, $SECTION_SONG, '$TYPE_SECTION' WHERE new.deleted = 0;
           END""",
        """CREATE TRIGGER IF NOT EXISTS section_ad AFTER DELETE ON section BEGIN ${deleteByEntity("old.id")}; END""",
        // ── note ──
        """CREATE TRIGGER IF NOT EXISTS note_ai AFTER INSERT ON note WHEN new.deleted = 0 BEGIN
             INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type)
             VALUES ('', new.body_text, new.id, new.song_id, '$TYPE_NOTE');
           END""",
        """CREATE TRIGGER IF NOT EXISTS note_au AFTER UPDATE ON note BEGIN
             ${deleteByEntity("old.id")};
             INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type)
             SELECT '', new.body_text, new.id, new.song_id, '$TYPE_NOTE' WHERE new.deleted = 0;
           END""",
        """CREATE TRIGGER IF NOT EXISTS note_ad AFTER DELETE ON note BEGIN ${deleteByEntity("old.id")}; END""",
        // ── take ──
        """CREATE TRIGGER IF NOT EXISTS take_ai AFTER INSERT ON take WHEN new.deleted = 0 BEGIN
             INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type)
             VALUES (new.name, '', new.id, new.song_id, '$TYPE_TAKE');
           END""",
        """CREATE TRIGGER IF NOT EXISTS take_au AFTER UPDATE ON take BEGIN
             ${deleteByEntity("old.id")};
             INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type)
             SELECT new.name, '', new.id, new.song_id, '$TYPE_TAKE' WHERE new.deleted = 0;
           END""",
        """CREATE TRIGGER IF NOT EXISTS take_ad AFTER DELETE ON take BEGIN ${deleteByEntity("old.id")}; END""",
    )

    /** Everything needed on a fresh database, in order. */
    val allStatements: List<String> = listOf(createTable) + triggers

    /** Rebuild from source rows (§10.2: the index is a derived artifact). */
    val rebuild: List<String> = listOf(
        "DELETE FROM $TABLE",
        "INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type) SELECT name, '', id, id, '$TYPE_SONG' FROM song WHERE deleted = 0",
        "INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type) SELECT name, '', id, song_id, '$TYPE_TAKE' FROM take WHERE deleted = 0",
        "INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type) SELECT '', body_text, id, song_id, '$TYPE_NOTE' FROM note WHERE deleted = 0",
        """INSERT INTO $TABLE(search_title, search_content, entity_id, song_id, entity_type)
           SELECT ${SECTION_LABEL.replace("new.", "s.")}, s.body_text, s.id, d.song_id, '$TYPE_SECTION'
           FROM section s JOIN lyrics_document d ON d.id = s.document_id WHERE s.deleted = 0""",
    )

    /**
     * §10.1 query shape. Bind 1 = MATCH expression, bind 2 = song_id filter or NULL.
     * `snippet(..., -1, ...)` lets FTS5 pick the best-matching column, so a hit in a take or song *title* still yields a
     * highlighted snippet instead of an empty content column (found by the S-4 spike).
     */
    val searchSql = """
        SELECT entity_id, song_id, entity_type, search_title,
               snippet($TABLE, -1, '<b>', '</b>', '…', 24) AS snippet_text,
               bm25($TABLE, 4.0, 1.0) AS rank
        FROM $TABLE
        WHERE $TABLE MATCH ?1 AND (?2 IS NULL OR song_id = ?2)
        ORDER BY rank
        LIMIT 50
    """.trimIndent()
}
