package com.songpad.spikes.s4

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The S-4 functional half, run in CI tier 1 on every PR: FTS5 present; bm25, snippet, prefix, diacritics, triggers all work. */
class S4FunctionalTest {
    @Test
    fun fts5_is_compiled_into_the_bundled_driver() {
        S4Harness().use { assertThat(it.fts5Available()).isTrue() }
    }

    @Test
    fun bm25_snippet_prefix_and_diacritics_work() {
        S4Harness().use { h ->
            val song = h.insertSong("Rain Song")
            h.insertSection(song, "CHORUS", null, "dancing under the neon rain tonight")
            h.insertNote(song, "the café by the station")
            h.insertTakes(10, song)

            val (neon, _) = h.search("neo")
            val sections = neon.filter { it.type == "section" }
            assertThat(sections).hasSize(1)
            assertThat(sections[0].snippet).contains("<b>neon</b>")
            assertThat(sections[0].title).isEqualTo("Chorus")
            assertThat(neon.filter { it.type == "take" }.map { it.title }).contains("Take 3 neon")

            val (rain, _) = h.search("rain")
            assertThat(rain.map { it.type }).containsAtLeast("song", "section")
            assertThat(rain[0].type).isEqualTo("song") // title weight 4.0 vs content 1.0: "Rain Song" outranks the lyric line

            assertThat(h.search("cafe").first.filter { it.type == "note" }).hasSize(1) // remove_diacritics 2
            assertThat(h.search("Take 3").first.map { it.title }).contains("Take 3 neon")
        }
    }

    @Test
    fun triggers_track_update_and_soft_delete() {
        S4Harness().use { h ->
            val song = h.insertSong("Alpha")
            h.insertTakes(5, song)
            assertThat(h.indexCount()).isEqualTo(6)
            val id = h.connection.prepare("SELECT id FROM take LIMIT 1").use { it.step(); it.getText(0) }
            h.softDeleteTake(id)
            assertThat(h.indexCount()).isEqualTo(5)
            h.connection.prepare("UPDATE take SET name = 'renamed zulu' WHERE deleted = 0 AND id = (SELECT id FROM take WHERE deleted = 0 LIMIT 1)").use { it.step() }
            assertThat(h.search("zulu").first).hasSize(1)
        }
    }

    @Test
    fun uuidv7_keys_arrive_in_ascending_order() {
        S4Harness().use { h ->
            h.insertTakes(500, null)
            val ids = h.connection.prepare("SELECT id FROM take ORDER BY rowid").use { st -> buildList { while (st.step()) add(st.getText(0)) } }
            assertThat(ids).isInOrder()
        }
    }

    @Test
    fun user_input_with_fts_syntax_does_not_error() {
        S4Harness().use { h ->
            h.insertSong("plain")
            listOf("\"", "*", "NEAR(a b)", "a OR b", "-x", "col:val", "(", "don't stop").forEach { q -> h.search(q) }
        }
    }
}
