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
        return resolve(uri.host, uri.lastPathSegment)
    }

    /** Pure-Kotlin core once the scheme is validated — no `android.net.Uri` in the signature, so this is
     * directly JVM-unit-testable without Robolectric (matches `:core:model`'s no-Robolectric convention). */
    internal suspend fun resolve(host: String?, id: String?): NavKey {
        val target = id ?: return LibraryKey
        return when (host) {
            "song" -> songs.getSong(target)?.let { SongDetailKey(it.id) } ?: LibraryKey
            "take" -> takes.getTake(target)?.let { t -> t.songId?.let { SongDetailKey(it, SongDetailTab.TAKES.name, t.id) } } ?: LibraryKey
            else -> LibraryKey
        }
    }
}
