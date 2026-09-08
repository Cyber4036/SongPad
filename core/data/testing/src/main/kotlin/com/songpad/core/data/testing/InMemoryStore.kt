package com.songpad.core.data.testing

import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.lyrics.Section
import com.songpad.core.model.note.Note
import com.songpad.core.model.nudge.Nudge
import com.songpad.core.model.song.Song
import com.songpad.core.model.take.Take
import kotlinx.coroutines.flow.MutableStateFlow

/** Shared in-memory tables behind the fake repositories, so one test can wire several fakes to the same data. */
class InMemoryStore(val clock: FakeClock = FakeClock(), val ids: SequentialIdGenerator = SequentialIdGenerator()) {
    val songs = MutableStateFlow<Map<String, Song>>(emptyMap())
    val takes = MutableStateFlow<Map<String, Take>>(emptyMap())
    val documents = MutableStateFlow<Map<String, LyricsDocument>>(emptyMap()) // keyed by songId
    val notes = MutableStateFlow<Map<String, Note>>(emptyMap())
    val nudges = MutableStateFlow<Map<String, Nudge>>(emptyMap())

    fun sectionsOf(songId: String): List<Section> = documents.value[songId]?.sections.orEmpty()
}
