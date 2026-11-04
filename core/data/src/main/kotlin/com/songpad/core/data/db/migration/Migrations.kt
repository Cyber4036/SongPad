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

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `lyrics_document_new` (
                    `id` TEXT NOT NULL,
                    `song_id` TEXT,
                    `name` TEXT NOT NULL DEFAULT '',
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `deleted` INTEGER NOT NULL DEFAULT 0,
                    `deleted_at` INTEGER,
                    `sync_state` TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                    `revision` INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`song_id`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""",
            )
            connection.execSQL(
                """INSERT INTO `lyrics_document_new` (`id`, `song_id`, `name`, `created_at`, `updated_at`, `deleted`, `deleted_at`, `sync_state`, `revision`)
                   SELECT `id`, `song_id`, '', `created_at`, `updated_at`, `deleted`, `deleted_at`, `sync_state`, `revision` FROM `lyrics_document`""",
            )
            connection.execSQL("DROP TABLE `lyrics_document`")
            connection.execSQL("ALTER TABLE `lyrics_document_new` RENAME TO `lyrics_document`")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lyrics_document_song_id` ON `lyrics_document` (`song_id`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_document_updated_at` ON `lyrics_document` (`updated_at`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_document_sync_state` ON `lyrics_document` (`sync_state`)")
            connection.execSQL("PRAGMA foreign_keys = ON")
            rebuildSearchIndex(connection)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
