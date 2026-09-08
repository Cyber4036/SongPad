package com.songpad.core.model.lyrics

import com.google.common.truth.Truth.assertThat
import com.songpad.core.model.sync.SyncMeta
import org.junit.Test

class SectionOrderingTest {
    private fun s(id: String, tag: SectionTag, order: Int, custom: String? = null) =
        Section(id, "doc", tag, custom, null, "", order, SyncMeta.create(0))

    @Test
    fun `insert renumbers contiguous sort orders`() {
        val list = listOf(s("a", SectionTag.VERSE, 0), s("b", SectionTag.CHORUS, 1))
        val out = SectionOrdering.insert(list, s("c", SectionTag.BRIDGE, 99), 1)
        assertThat(out.map { it.id }).containsExactly("a", "c", "b").inOrder()
        assertThat(out.map { it.sortOrder }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `move clamps and keeps orders contiguous`() {
        val list = listOf(s("a", SectionTag.VERSE, 0), s("b", SectionTag.CHORUS, 1), s("c", SectionTag.VERSE, 2))
        val out = SectionOrdering.move(list, "c", 0)
        assertThat(out.map { it.id }).containsExactly("c", "a", "b").inOrder()
        assertThat(SectionOrdering.move(list, "a", 50).map { it.id }).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun `renumber gives Verse 1 and Verse 2 but a lone Chorus stays unnumbered`() {
        val list = listOf(s("a", SectionTag.VERSE, 0), s("b", SectionTag.CHORUS, 1), s("c", SectionTag.VERSE, 2), s("d", SectionTag.CUSTOM, 3, "Drop"))
        val out = SectionOrdering.renumber(list)
        assertThat(out.map { it.displayLabel }).containsExactly("Verse 1", "Chorus", "Verse 2", "Drop").inOrder()
    }

    @Test
    fun `plain text render lists sections in order with labels`() {
        val doc = LyricsDocument("doc", "song", SectionOrdering.renumber(listOf(s("a", SectionTag.VERSE, 1).copy(bodyText = "second"), s("b", SectionTag.INTRO, 0).copy(bodyText = "first"))), SyncMeta.create(0))
        assertThat(doc.plainText).isEqualTo("[Intro]\nfirst\n\n[Verse]\nsecond")
    }
}
