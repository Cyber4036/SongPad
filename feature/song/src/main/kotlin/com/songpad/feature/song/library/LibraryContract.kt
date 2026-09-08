package com.songpad.feature.song.library

import com.songpad.core.model.song.Song
import com.songpad.core.model.song.SongFilter
import com.songpad.core.model.state.QualityDisclosureState
import com.songpad.core.model.take.Take

/**
 * §17 "Songs" states: EmptyLibrary · Populated · Filtered. (SearchResults is owned by :feature:search — the
 * library's search bar hands off to it; features never depend on each other, §5.)
 */
sealed interface LibraryState {
    val filter: SongFilter
    val disclosure: QualityDisclosureState

    data class Loading(override val filter: SongFilter = SongFilter.ALL) : LibraryState {
        override val disclosure get() = QualityDisclosureState.Hidden
    }

    data class EmptyLibrary(override val disclosure: QualityDisclosureState) : LibraryState {
        override val filter get() = SongFilter.ALL
    }

    data class Populated(
        val songs: List<Song>,
        val unassignedTakes: List<Take>,
        override val disclosure: QualityDisclosureState,
    ) : LibraryState {
        override val filter get() = SongFilter.ALL
    }

    /** A non-default pill is active; `songs` is the filtered list (may be empty — the pill still shows). */
    data class Filtered(
        override val filter: SongFilter,
        val songs: List<Song>,
        val unassignedTakes: List<Take>,
        override val disclosure: QualityDisclosureState,
    ) : LibraryState
}

sealed interface LibrarySheet {
    data object None : LibrarySheet
    /** Phase 0 §24.3: "Add to Song" for an unassigned take — existing songs, or a new one. */
    data class AssignTake(val take: Take, val songs: List<Song>) : LibrarySheet
    data class NewSongForTake(val take: Take) : LibrarySheet
    data class ConfirmDeleteTake(val take: Take) : LibrarySheet
}
