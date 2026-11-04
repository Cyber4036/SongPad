package com.songpad.core.model.lyrics

import com.google.common.truth.Truth.assertThat
import com.songpad.core.model.sync.SyncMeta
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsDraftStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `FileLyricsDraftStore persists drafts immediately to disk and recovers after process restart`() {
        val dir = tempFolder.newFolder("drafts")
        val store1 = FileLyricsDraftStore(dir)

        store1.putDraft("sec-1", "First line typed", 1_000L)
        store1.putDraft("sec-1", "First line typed\nSecond line in flight", 1_050L)
        store1.putDraft("sec-2", "Chorus hook draft", 1_100L)

        // Simulate abrupt process death and cold start by instantiating a new FileLyricsDraftStore on the same directory
        val recoveredStore = FileLyricsDraftStore(dir)
        assertThat(recoveredStore.getDraft("sec-1")?.bodyText)
            .isEqualTo("First line typed\nSecond line in flight")
        assertThat(recoveredStore.getDraft("sec-1")?.updatedAtMillis).isEqualTo(1_050L)
        assertThat(recoveredStore.getDraft("sec-2")?.bodyText)
            .isEqualTo("Chorus hook draft")

        // Removing a committed draft updates disk state
        recoveredStore.removeDraft("sec-1")
        val afterRemoveStore = FileLyricsDraftStore(dir)
        assertThat(afterRemoveStore.getDraft("sec-1")).isNull()
        assertThat(afterRemoveStore.getDraft("sec-2")?.bodyText).isEqualTo("Chorus hook draft")

        afterRemoveStore.clear()
        val afterClearStore = FileLyricsDraftStore(dir)
        assertThat(afterClearStore.drafts.value).isEmpty()
    }

    @Test
    fun `LyricsDocument withDrafts overlays uncommitted local drafts onto sections`() {
        val baseMeta = SyncMeta.create(500L)
        val doc = LyricsDocument(
            id = "doc-1",
            songId = "song-1",
            sections = listOf(
                Section("sec-1", "doc-1", SectionTag.VERSE, null, 1, "Old saved text", 0, baseMeta),
                Section("sec-2", "doc-1", SectionTag.CHORUS, null, null, "Saved chorus", 1, baseMeta),
            ),
            meta = baseMeta,
            name = "Song",
        )
        val drafts = mapOf(
            "sec-1" to SectionDraft("sec-1", "New unsaved draft text", 900L),
        )
        val merged = doc.withDrafts(drafts)
        assertThat(merged.sections[0].bodyText).isEqualTo("New unsaved draft text")
        assertThat(merged.sections[0].meta.updatedAt).isEqualTo(900L)
        assertThat(merged.sections[1].bodyText).isEqualTo("Saved chorus")
        assertThat(merged.meta.updatedAt).isEqualTo(900L)
    }
}
