package com.songpad.feature.song.library

import com.songpad.core.model.lyrics.LyricsDocument
import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.state.QualityDisclosureState
import com.songpad.core.model.take.Take
import com.songpad.feature.song.detail.NowPlaying

/**
 * §17 "Songs" states: EmptyLibrary · Populated · Filtered.
 */
sealed interface LibraryState {
    val filter: SongFilter
    val disclosure: QualityDisclosureState
    val nowPlaying: NowPlaying?
    val playbackError: String?

    data class Loading(override val filter: SongFilter = SongFilter.ALL) : LibraryState {
        override val disclosure get() = QualityDisclosureState.Hidden
        override val nowPlaying get(): NowPlaying? = null
        override val playbackError get(): String? = null
    }

    data class EmptyLibrary(
        override val disclosure: QualityDisclosureState,
        override val playbackError: String? = null,
    ) : LibraryState {
        override val filter get() = SongFilter.ALL
        override val nowPlaying get(): NowPlaying? = null
    }

    /** Unassigned takes and standalone lyrics are accessible here at the top of the Library. */
    data class Populated(
        val songs: List<Song>,
        val unassignedTakes: List<Take>,
        override val disclosure: QualityDisclosureState,
        override val nowPlaying: NowPlaying?,
        val unassignedLyrics: List<LyricsDocument> = emptyList(),
        override val playbackError: String? = null,
    ) : LibraryState {
        override val filter get() = SongFilter.ALL
    }

    /** A non-default pill is active; `songs` is the filtered list (may be empty — the pill still shows). */
    data class Filtered(
        override val filter: SongFilter,
        val songs: List<Song>,
        val unassignedTakes: List<Take>,
        override val disclosure: QualityDisclosureState,
        override val nowPlaying: NowPlaying?,
        val unassignedLyrics: List<LyricsDocument> = emptyList(),
        override val playbackError: String? = null,
    ) : LibraryState
}

sealed interface LibrarySheet {
    data object None : LibrarySheet
    /** Phase 0 §24.3: "Add to Song" for an unassigned take — existing songs, or a new one. */
    data class AssignTake(val take: Take, val songs: List<Song>) : LibrarySheet
    data class NewSongForTake(val take: Take) : LibrarySheet
    data class ConfirmDeleteTake(val take: Take) : LibrarySheet
    /** Standalone lyrics sheets: assign to song, create new song, edit standalone lyrics, rename, or delete. */
    data class AssignLyrics(val document: LyricsDocument, val songs: List<Song>) : LibrarySheet
    data class NewSongForLyrics(val document: LyricsDocument) : LibrarySheet
    data class EditStandaloneLyrics(val documentId: String) : LibrarySheet
    data class RenameStandaloneLyrics(val document: LyricsDocument) : LibrarySheet
    data class ConfirmDeleteStandaloneLyrics(val document: LyricsDocument) : LibrarySheet
    /** Song card `•••` options sheet (Capture.html §Songs). */
    data class SongOptions(val song: Song) : LibrarySheet
    data class RenameSong(val song: Song) : LibrarySheet
    data class ConfirmDeleteSong(val song: Song) : LibrarySheet
    /** Import sheet for bundles (.songpad, .zip), standalone audio files, or .txt lyrics. */
    data class Import(
        val isBusy: Boolean = false,
        val statusTitle: String? = null,
        val statusBody: String? = null,
        val isError: Boolean = false,
        val importedSongId: String? = null,
    ) : LibrarySheet
}
