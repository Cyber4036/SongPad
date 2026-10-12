package com.songpad.app

import com.google.common.truth.Truth.assertThat
import com.songpad.app.navigation.DeepLinkResolver
import com.songpad.app.navigation.LibraryKey
import com.songpad.app.navigation.SongDetailKey
import com.songpad.core.data.testing.FakeSettingsRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Wave 4: an in-app nudge tap on a take (`MainViewModel.openTake`) must resolve the same way the
 * `songpad://take/{id}` notification deep link does — both go through [DeepLinkResolver]. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryStore()
    private val songs = FakeSongRepository(store)
    private val takes = FakeTakeRepository(store)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm() = MainViewModel(FakeSettingsRepository(), DeepLinkResolver(songs, takes))

    @Test
    fun `opening a take nudge lands on its song's Takes tab, scrolled to that take`() = runTest(dispatcher) {
        val song = songs.create("Midnight Riff").getOrThrow()
        val take = takes.commit(
            TakeDraft(
                store.ids.newId(), "Take 1", TakeType.VOICE, song.id, null,
                CapturedAudio("x", TakePaths.pcm("x"), 48_000L * 10, AudioFormat.bytesForFrames(48_000L * 10), null),
                null, QualityMode.STANDARD,
            ),
        ).getOrThrow()
        val vm = vm()
        val targets = mutableListOf<Any>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.inAppTargets.collect { targets += it } }

        vm.openTake(take.id)
        advanceUntilIdle()

        assertThat(targets).containsExactly(SongDetailKey(song.id, SongDetailTab.TAKES.name, take.id))
    }

    @Test
    fun `opening a take nudge for a take that no longer exists falls back to Library`() = runTest(dispatcher) {
        val vm = vm()
        val targets = mutableListOf<Any>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.inAppTargets.collect { targets += it } }

        vm.openTake("does-not-exist")
        advanceUntilIdle()

        assertThat(targets).containsExactly(LibraryKey)
    }

    @Test
    fun `themeVariant is seeded with persisted value on creation`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setThemeVariant("MELODY_DARK")
        val vm = MainViewModel(settings, DeepLinkResolver(songs, takes))
        assertThat(vm.themeVariant.value).isEqualTo("MELODY_DARK")
    }
}
