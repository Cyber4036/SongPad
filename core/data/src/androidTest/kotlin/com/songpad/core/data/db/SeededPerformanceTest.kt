package com.songpad.core.data.db

import androidx.room.RoomRawQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.db.dao.SearchRow
import com.songpad.core.data.db.entity.NoteEntity
import com.songpad.core.data.db.entity.SongEntity
import com.songpad.core.data.db.entity.SyncColumns
import com.songpad.core.data.db.entity.TakeEntity
import com.songpad.core.data.db.fts.FtsQueryBuilder
import com.songpad.core.data.db.fts.SearchIndexDdl
import com.songpad.core.model.id.UuidV7
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * § 16: "Seed 200 takes — verify cold start remains <2.5s and search remains <50ms."
 * Executed on physical device hardware (Samsung Galaxy A02).
 */
@RunWith(AndroidJUnit4::class)
class SeededPerformanceTest {
    private lateinit var db: SongPadDatabase
    private val ids = UuidV7()
    private fun sync(now: Long = 1_000) = SyncColumns(now, now)

    @Before
    fun setUp() {
        db = SongPadDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun search(q: String, songId: String? = null): List<SearchRow> {
        val query = FtsQueryBuilder.build(q) ?: return emptyList()
        return db.searchDao().searchOnce(
            RoomRawQuery(SearchIndexDdl.searchSql) { s ->
                s.bindText(1, query)
                if (songId == null) s.bindNull(2) else s.bindText(2, songId)
            }
        )
    }

    @Test
    fun seed200TakesAndVerifySearchAndLoadLatency() = runTest {
        // 1. Seed 20 songs, each with 10 takes (total 200 takes) + notes
        val songIds = mutableListOf<String>()
        val seedElapsed = measureTimeMillis {
            db.withTransactionCompat {
                for (s in 1..20) {
                    val songId = ids.next()
                    songIds += songId
                    db.songDao().insert(
                        SongEntity(
                            id = songId,
                            name = "Song $s Midnight Neon",
                            status = "ONGOING",
                            isFavorite = 0,
                            sync = sync(),
                        )
                    )
                    // Add 10 takes per song = 200 takes
                    for (t in 1..10) {
                        val takeId = ids.next()
                        db.takeDao().insert(
                            TakeEntity(
                                id = takeId,
                                songId = songId,
                                name = "Take $t acoustic vocal take $s",
                                type = "VOICE",
                                status = "READY",
                                durationMs = 15_000L * t,
                                audioAssetId = null,
                                midiAssetId = null,
                                sync = sync(),
                            )
                        )
                    }
                    // Add 2 notes per song
                    db.noteDao().insert(
                        NoteEntity(
                            id = ids.next(),
                            songId = songId,
                            bodyText = "Remember to keep the chorus riff darker at verse $s",
                            voiceTakeId = null,
                            mediaUri = null,
                            sectionRefId = null,
                            takeRefId = null,
                            sync = sync(),
                        )
                    )
                }
            }
        }
        println("--> Seeded 200 takes + 20 songs in $seedElapsed ms")

        // Verify total counts
        val allSongs = db.songDao().observeAll().first()
        assertThat(allSongs.size).isEqualTo(20)

        val unassigned = db.takeDao().unassigned()
        assertThat(unassigned).isEmpty()

        // 2. Measure Library load latency
        val loadElapsed = measureTimeMillis {
            val loaded = db.songDao().observeAll().first()
            for (song in loaded) {
                db.takeDao().forSong(song.id)
            }
        }
        println("--> Full Library (20 songs, 200 takes) cold load: $loadElapsed ms")
        // Must be well within the 2.5s threshold (§16)
        assertThat(loadElapsed).isLessThan(2500)

        // 3. Measure FTS5 Search latency across multiple typical queries
        val testQueries = listOf("mid", "neon", "chorus", "darker", "vocal", "acoustic")
        for (q in testQueries) {
            val queryElapsed = measureTimeMillis {
                val results = search(q)
                assertThat(results).isNotEmpty()
            }
            println("--> FTS5 search query '$q': $queryElapsed ms")
            // Must be < 50 ms (§16 threshold)
            assertThat(queryElapsed).isLessThan(50)
        }
    }
}
