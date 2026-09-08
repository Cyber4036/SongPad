package com.songpad.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.songpad.core.data.db.dao.AudioAssetDao
import com.songpad.core.data.db.dao.LyricsDao
import com.songpad.core.data.db.dao.MidiAssetDao
import com.songpad.core.data.db.dao.NoteDao
import com.songpad.core.data.db.dao.NudgeDao
import com.songpad.core.data.db.dao.SearchDao
import com.songpad.core.data.db.dao.SongDao
import com.songpad.core.data.db.dao.TakeDao
import com.songpad.core.data.db.entity.AudioAssetEntity
import com.songpad.core.data.db.entity.LyricsDocumentEntity
import com.songpad.core.data.db.entity.MidiAssetEntity
import com.songpad.core.data.db.entity.NoteEntity
import com.songpad.core.data.db.entity.NudgeEntity
import com.songpad.core.data.db.entity.SectionEntity
import com.songpad.core.data.db.entity.SongEntity
import com.songpad.core.data.db.entity.TakeEntity
import com.songpad.core.data.db.fts.SearchIndexDdl
import com.songpad.core.data.db.migration.Migrations
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        SongEntity::class, TakeEntity::class, AudioAssetEntity::class, MidiAssetEntity::class,
        LyricsDocumentEntity::class, SectionEntity::class, NoteEntity::class, NudgeEntity::class,
    ],
    version = SongPadDatabase.VERSION,
    exportSchema = true,
)
abstract class SongPadDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun takeDao(): TakeDao
    abstract fun audioAssetDao(): AudioAssetDao
    abstract fun midiAssetDao(): MidiAssetDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun noteDao(): NoteDao
    abstract fun nudgeDao(): NudgeDao
    abstract fun searchDao(): SearchDao

    companion object {
        const val VERSION = 1
        const val NAME = "songpad.db"

        /** The FTS index and triggers live outside Room's entity model (§6.4), so they are created here and in migrations. */
        val ftsCallback = object : Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                SearchIndexDdl.allStatements.forEach(connection::execSQL)
            }

            override fun onOpen(connection: SQLiteConnection) {
                connection.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        fun build(context: Context): SongPadDatabase =
            Room.databaseBuilder(context, SongPadDatabase::class.java, NAME)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(ftsCallback)
                .addMigrations(*Migrations.ALL)
                .build()

        /** In-memory database for instrumented tests and the migration harness — same driver, same callback. */
        fun buildInMemory(context: Context): SongPadDatabase =
            Room.inMemoryDatabaseBuilder(context, SongPadDatabase::class.java)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(ftsCallback)
                .build()
    }
}
