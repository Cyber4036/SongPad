package com.songpad.core.model.song

import com.songpad.core.model.sync.SyncMeta

/** Phase 0 §4: the user's project container. Statuses are reversible. */
enum class SongStatus { ONGOING, FINISHED, ARCHIVED }

data class Song(
    val id: String,
    val name: String,
    val status: SongStatus,
    val isFavorite: Boolean,
    val meta: SyncMeta,
) {
    val createdAt: Long get() = meta.createdAt
    val updatedAt: Long get() = meta.updatedAt
}

/** Library filter pills (Phase 0 §14). */
enum class SongFilter { ALL, FAVORITES, ONGOING, FINISHED, ARCHIVED }

/** Reversible status transitions (Phase 0 §4, §15). The action label is the reverse operation. */
object SongStatusTransitions {
    fun toggleFinished(current: SongStatus): SongStatus =
        if (current == SongStatus.FINISHED) SongStatus.ONGOING else SongStatus.FINISHED

    fun toggleArchived(current: SongStatus): SongStatus =
        if (current == SongStatus.ARCHIVED) SongStatus.ONGOING else SongStatus.ARCHIVED

    fun finishActionLabel(current: SongStatus): String =
        if (current == SongStatus.FINISHED) "Mark ongoing" else "Mark finished"

    fun archiveActionLabel(current: SongStatus): String =
        if (current == SongStatus.ARCHIVED) "Unarchive" else "Archive"

    fun matches(status: SongStatus, favorite: Boolean, filter: SongFilter): Boolean = when (filter) {
        SongFilter.ALL -> status != SongStatus.ARCHIVED
        SongFilter.FAVORITES -> favorite
        SongFilter.ONGOING -> status == SongStatus.ONGOING
        SongFilter.FINISHED -> status == SongStatus.FINISHED
        SongFilter.ARCHIVED -> status == SongStatus.ARCHIVED
    }
}
