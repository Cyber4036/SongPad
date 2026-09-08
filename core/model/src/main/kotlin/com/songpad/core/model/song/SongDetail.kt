package com.songpad.core.model.song

import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.note.Note
import com.songpad.core.model.take.Take

/** Everything Song Home needs, in one reactive object (§15 `SongRepository.observeSong`). */
data class SongDetail(
    val song: Song,
    val takes: List<Take>,
    val lyrics: LyricsDocument?,
    val notes: List<Note>,
) {
    /** Song Home lists all material in recency order (Phase 0 §15). */
    val recentActivity: List<SongActivity>
        get() = buildList {
            takes.forEach { add(SongActivity.TakeSaved(it)) }
            notes.forEach { add(SongActivity.NoteAdded(it)) }
            lyrics?.sections?.filter { it.bodyText.isNotBlank() }?.forEach { add(SongActivity.SectionEdited(it)) }
        }.sortedByDescending { it.at }
}

sealed interface SongActivity {
    val at: Long

    data class TakeSaved(val take: Take) : SongActivity { override val at get() = take.meta.updatedAt }
    data class NoteAdded(val note: Note) : SongActivity { override val at get() = note.meta.updatedAt }
    data class SectionEdited(val section: com.songpad.core.model.lyrics.Section) : SongActivity { override val at get() = section.meta.updatedAt }
}
