package com.songpad.app.navigation

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.data.TakeRepository
import com.songpad.feature.song.detail.SongDetailTab
import javax.inject.Inject

/** §11.3: `songpad://song/{id}` / `songpad://take/{id}` → the target, or the library when it is gone. */
class DeepLinkResolver @Inject constructor(private val songs: SongRepository, private val takes: TakeRepository) {
    suspend fun resolve(uri: Uri?): NavKey? {
        if (uri == null || uri.scheme != "songpad") return null
        val id = uri.lastPathSegment ?: return LibraryKey
        return when (uri.host) {
            "song" -> songs.getSong(id)?.let { SongDetailKey(it.id) } ?: LibraryKey
            "take" -> takes.getTake(id)?.let { t -> t.songId?.let { SongDetailKey(it, SongDetailTab.TAKES.name) } } ?: LibraryKey
            else -> LibraryKey
        }
    }
}
