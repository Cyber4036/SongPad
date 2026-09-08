package com.songpad.feature.song.detail

import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.note.Note
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongActivity
import com.songpad.core.model.take.Take
import com.songpad.core.model.take.TakeType

enum class SongDetailTab(val label: String) { HOME("Home"), LYRICS("Lyrics"), TAKES("Takes"), NOTES("Notes") }

/** Phase 0 §9 filter pills, 1:1 with [TakeType] plus All. */
enum class TakeFilter(val label: String, val type: TakeType?) {
    ALL("All", null), VOICE("Voice", TakeType.VOICE), PIANO("Piano", TakeType.PIANO), VOICE_PIANO("Voice + Piano", TakeType.VOICE_PIANO),
}

/** §17 "Takes": Empty · Populated · Playing · Transcoding · Unavailable. */
sealed interface TakeListState {
    val filter: TakeFilter

    data class Empty(override val filter: TakeFilter) : TakeListState
    data class Populated(override val filter: TakeFilter, val takes: List<Take>) : TakeListState
    data class Playing(override val filter: TakeFilter, val takes: List<Take>, val playing: NowPlaying) : TakeListState
    /** At least one take is still transcoding — playable from raw PCM, so no spinner (§17). */
    data class Transcoding(override val filter: TakeFilter, val takes: List<Take>, val transcodingIds: Set<String>, val playing: NowPlaying?) : TakeListState
    /** Playback failed or a take's file is missing. */
    data class Unavailable(override val filter: TakeFilter, val takes: List<Take>, val takeId: String, val reason: String) : TakeListState
}

data class NowPlaying(
    val take: Take,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val peaks: FloatArray,
) {
    val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** §17 "Lyrics": Empty · Editing · Autosaved · SectionInserting. */
sealed interface LyricsState {
    data object Empty : LyricsState
    data class Editing(val document: LyricsDocument, val dirtySectionId: String?) : LyricsState
    data class Autosaved(val document: LyricsDocument) : LyricsState
    data class SectionInserting(val document: LyricsDocument?, val atIndex: Int?) : LyricsState
}

/** §17 "Notes": Empty · Composing · MediaAttached · VoiceRecording · Saved · InvalidEmpty. Media/voice attach are V1 UI TODOs; the states exist. */
sealed interface NotesState {
    val notes: List<Note>

    data class Empty(override val notes: List<Note> = emptyList()) : NotesState
    data class Composing(override val notes: List<Note>, val draftText: String, val editingNoteId: String?) : NotesState
    data class MediaAttached(override val notes: List<Note>, val draftText: String, val mediaUri: String) : NotesState
    data class VoiceRecording(override val notes: List<Note>, val draftText: String) : NotesState
    data class Saved(override val notes: List<Note>) : NotesState
    data class InvalidEmpty(override val notes: List<Note>, val draftText: String) : NotesState
}

sealed interface SongDetailState {
    data object Loading : SongDetailState
    data object NotFound : SongDetailState
    data class Loaded(
        val song: Song,
        val tab: SongDetailTab,
        val activity: List<SongActivity>,
        val takes: TakeListState,
        val lyrics: LyricsState,
        val notes: NotesState,
        val nowPlaying: NowPlaying?,
    ) : SongDetailState
}

sealed interface SongDetailDialog {
    data object None : SongDetailDialog
    data object RenameSong : SongDetailDialog
    data class RenameTake(val take: Take) : SongDetailDialog
    data class ConfirmDeleteTake(val take: Take) : SongDetailDialog
    data class ConfirmDeleteSection(val sectionId: String, val label: String) : SongDetailDialog
    data class ConfirmDeleteNote(val noteId: String) : SongDetailDialog
    data object ConfirmDeleteSong : SongDetailDialog
}

/** Reads `peaks.wave` for the ribbon player's seek waveform (§7.1). Bound to PeakReducer in the feature module. */
fun interface PeaksLoader {
    fun load(relativePath: String): FloatArray
}
