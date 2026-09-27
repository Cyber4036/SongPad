package com.songpad.app.navigation

import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.testing.FakeSongRepository
import com.songpad.core.data.testing.FakeTakeRepository
import com.songpad.core.data.testing.InMemoryStore
import com.songpad.core.model.take.AudioFormat
import com.songpad.core.model.take.CapturedAudio
import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakeDraft
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.take.TakeType
import com.songpad.feature.song.detail.SongDetailTab
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Nudge/DeepLinkResolver (§11.3). Exercises the resolver's own logic via the internal Uri-free overload —
 * `resolve(uri: Uri?)` is a one-line scheme-check-then-delegate adapter with nothing else to test, and
 * constructing a real `android.net.Uri` here would need Robolectric, which this project avoids per
 * `:core:model`'s no-Android-dependency convention. `MainActivity.onCreate`/`onNewIntent` both call
 * `DeepLinkResolver.resolve` the same way regardless of cold vs. warm start, so this covers both paths.
 */
class DeepLinkResolverTest {
    private val store = InMemoryStore()
    private val songs = FakeSongRepository(store)
    private val takes = FakeTakeRepository(store)
    private val resolver = DeepLinkResolver(songs, takes)

    @Test
    fun `no id falls back to library`() = runTest {
        assertThat(resolver.resolve(host = "song", id = null)).isEqualTo(LibraryKey)
    }

    @Test
    fun `unknown host falls back to library`() = runTest {
        assertThat(resolver.resolve(host = "nonsense", id = "anything")).isEqualTo(LibraryKey)
    }

    @Test
    fun `song deep link resolves to that song`() = runTest {
        val song = songs.create("Midnight Riff").getOrThrow()
        assertThat(resolver.resolve(host = "song", id = song.id)).isEqualTo(SongDetailKey(song.id))
    }

    @Test
    fun `song deep link to a deleted song falls back to library`() = runTest {
        val song = songs.create("Gone").getOrThrow()
        songs.softDelete(song.id)
        assertThat(resolver.resolve(host = "song", id = song.id)).isEqualTo(LibraryKey)
    }

    @Test
    fun `take deep link resolves into its song's Takes tab, scrolled to that take`() = runTest {
        val song = songs.create("Midnight Riff").getOrThrow()
        val take = takes.commit(
            TakeDraft(
                store.ids.newId(), "Take 1", TakeType.VOICE, song.id, null,
                CapturedAudio("x", TakePaths.pcm("x"), 48_000L * 10, AudioFormat.bytesForFrames(48_000L * 10), null),
                null, QualityMode.STANDARD,
            ),
        ).getOrThrow()
        assertThat(resolver.resolve(host = "take", id = take.id))
            .isEqualTo(SongDetailKey(song.id, SongDetailTab.TAKES.name, take.id))
    }

    @Test
    fun `unassigned take deep link falls back to library`() = runTest {
        val take = takes.commit(
            TakeDraft(
                store.ids.newId(), "Loose take", TakeType.VOICE, null, null,
                CapturedAudio("y", TakePaths.pcm("y"), 48_000L * 5, AudioFormat.bytesForFrames(48_000L * 5), null),
                null, QualityMode.STANDARD,
            ),
        ).getOrThrow()
        assertThat(resolver.resolve(host = "take", id = take.id)).isEqualTo(LibraryKey)
    }

    @Test
    fun `missing take falls back to library`() = runTest {
        assertThat(resolver.resolve(host = "take", id = "does-not-exist")).isEqualTo(LibraryKey)
    }
}
