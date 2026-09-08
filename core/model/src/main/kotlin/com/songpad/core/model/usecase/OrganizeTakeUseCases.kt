package com.songpad.core.model.usecase

import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.song.Song

/** Phase 0 §24.3: "Add to new Song" — lightweight, collects only the name, adopts the take. */
class CreateSongFromTakeUseCase(private val songs: SongRepository) {
    suspend operator fun invoke(name: String, takeId: String?): Result<Song> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return domainFailure(DataError.InvalidInput("Give the song a name."))
        if (trimmed.length > MAX_NAME) return domainFailure(DataError.InvalidInput("Song names are limited to $MAX_NAME characters."))
        return songs.create(trimmed, takeId)
    }

    companion object { const val MAX_NAME = 120 }
}

/** Phase 0 §24.2/§24.3: "Add to existing Song". */
class AssignTakeToSongUseCase(private val takes: TakeRepository, private val songs: SongRepository) {
    suspend operator fun invoke(takeId: String, songId: String): Result<Unit> {
        songs.getSong(songId) ?: return domainFailure(DataError.NotFound("Song", songId))
        return takes.assignToSong(takeId, songId)
    }
}

class RenameTakeUseCase(private val takes: TakeRepository) {
    suspend operator fun invoke(takeId: String, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return domainFailure(DataError.InvalidInput("A take needs a name."))
        return takes.rename(takeId, trimmed)
    }
}
