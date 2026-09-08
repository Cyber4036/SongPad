package com.songpad.core.data.db.fts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtsQueryBuilderTest {
    @Test
    fun `plain words become quoted phrases with a trailing prefix star`() {
        assertThat(FtsQueryBuilder.build("midnight ri")).isEqualTo("\"midnight\" \"ri\"*")
        assertThat(FtsQueryBuilder.build("chorus")).isEqualTo("\"chorus\"*")
    }

    @Test
    fun `fts5 syntax in user input is neutralised`() {
        assertThat(FtsQueryBuilder.build("a OR b")).isEqualTo("\"a\" \"OR\" \"b\"*")
        assertThat(FtsQueryBuilder.build("NEAR(x y)")).isEqualTo("\"NEAR\" \"x\" \"y\"*")
        assertThat(FtsQueryBuilder.build("\"quoted\" -minus col:val *")).isEqualTo("\"quoted\" \"minus\" \"col\" \"val\"*")
        assertThat(FtsQueryBuilder.build("don't")).isEqualTo("\"don\" \"t\"*")
    }

    @Test
    fun `blank or punctuation-only input yields null`() {
        assertThat(FtsQueryBuilder.build("")).isNull()
        assertThat(FtsQueryBuilder.build("   ")).isNull()
        assertThat(FtsQueryBuilder.build("*\"()-")).isNull()
    }

    @Test
    fun `token count is capped`() {
        val many = (1..40).joinToString(" ") { "w$it" }
        assertThat(FtsQueryBuilder.tokenize(many)).hasSize(16)
    }

    @Test
    fun `ddl is well formed and complete`() {
        assertThat(SearchIndexDdl.allStatements).hasSize(13)
        assertThat(SearchIndexDdl.createTable).contains("prefix = '2 3 4'")
        assertThat(SearchIndexDdl.createTable).contains("remove_diacritics 2")
        assertThat(SearchIndexDdl.searchSql).contains("bm25(search_index, 4.0, 1.0)")
        assertThat(SearchIndexDdl.searchSql).contains("snippet(search_index, -1")
    }
}
