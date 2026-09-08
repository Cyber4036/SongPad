package com.songpad.core.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.songpad.core.data.db.fts.SearchIndexDdl

/**
 * §6.4 migration harness. Auto-migrations for mechanical changes; hand-written `Migration` for anything
 * with a data transform. Every path ships with a MigrationTestHelper test (androidTest/MigrationTest).
 * FTS DDL is replayed here, never via Room annotations.
 *
 * v1 is the baseline. The harness executes a no-op v1 migration in tests (Prompt 1-B "done when").
 */
object Migrations {
    /** Helper for future migrations that touch indexed tables: recreate the FTS objects and rebuild from source rows. */
    fun rebuildSearchIndex(connection: SQLiteConnection) {
        SearchIndexDdl.allStatements.forEach(connection::execSQL)
        SearchIndexDdl.rebuild.forEach(connection::execSQL)
    }

    val ALL: Array<Migration> = emptyArray()
}
