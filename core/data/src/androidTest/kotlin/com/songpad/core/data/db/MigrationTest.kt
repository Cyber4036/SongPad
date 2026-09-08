package com.songpad.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.db.fts.SearchIndexDdl
import com.songpad.core.data.db.migration.Migrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * §6.4 migration harness. v1 is the baseline, so this executes the no-op v1 path (Prompt 1-B) and proves
 * the FTS objects are recreated by the shared DDL after a migration. Every future vN→vN+1 adds a case here
 * that seeds data at vN, migrates, and asserts the data survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SongPadDatabase::class.java,
        emptyList(),
        BundledSQLiteDriver(),
    )

    @Test
    fun v1_baseline_opens_and_validates_with_all_migrations_applied() {
        val db: File = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(dbName)
        db.delete()
        helper.createDatabase(dbName, SongPadDatabase.VERSION).use { connection ->
            SearchIndexDdl.allStatements.forEach(connection::execSQL)
            connection.execSQL("INSERT INTO song(id, name, status, is_favorite, created_at, updated_at, deleted, deleted_at, sync_state, revision) VALUES ('s1', 'Seed', 'ONGOING', 0, 1, 1, 0, NULL, 'LOCAL_ONLY', 1)")
        }
        val migrated: SQLiteConnection = helper.runMigrationsAndValidate(dbName, SongPadDatabase.VERSION, true, *Migrations.ALL)
        migrated.prepare("SELECT COUNT(*) FROM search_index WHERE entity_id = 's1'").use { stmt ->
            assertThat(stmt.step()).isTrue()
            assertThat(stmt.getLong(0)).isEqualTo(1)
        }
        migrated.close()
    }
}
