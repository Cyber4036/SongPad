package com.songpad.feature.search

import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.testing.FakeLyricsRepository
import com.songpad.core.data.testing.FakeSearchEngine
import com.songpad.core.data.testing.FakeSongRepository
import com.songpad.core.data.testing.InMemoryStore
import com.songpad.core.model.lyrics.SectionTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryStore()
    private val songs = FakeSongRepository(store)
    private val lyrics = FakeLyricsRepository(store)
    private val engine = FakeSearchEngine(store)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `debounces, requires two characters, groups results, scopes to a song`() = runTest(dispatcher) {
        val a = songs.create("Neon Rain").getOrThrow()
        val b = songs.create("Other").getOrThrow()
        val s = lyrics.insertSection(a.id, SectionTag.CHORUS, null, null).getOrThrow()
        lyrics.updateSectionText(s.id, "neon rain on the window")
        val s2 = lyrics.insertSection(b.id, SectionTag.VERSE, null, null).getOrThrow()
        lyrics.updateSectionText(s2.id, "the neon sign hums")

        val vm = SearchViewModel(engine)
        val seen = mutableListOf<SearchState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }

        vm.setQuery("n"); advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(SearchState.Idle("n"))

        vm.setQuery("ne"); advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 50)
        assertThat(vm.state.value).isEqualTo(SearchState.Idle("n")) // still debouncing
        vm.setQuery("neon"); advanceUntilIdle()
        val r = vm.state.value as SearchState.Results
        assertThat(r.results.songs.map { it.songId }).containsExactly(a.id)
        assertThat(r.results.lyrics.map { it.sectionId }).containsExactly(s.id, s2.id)
        assertThat(seen.filterIsInstance<SearchState.Searching>()).isNotEmpty()

        vm.bind(b.id); advanceUntilIdle()
        val scoped = vm.state.value as SearchState.Results
        assertThat(scoped.results.songs).isEmpty()
        assertThat(scoped.results.lyrics.map { it.sectionId }).containsExactly(s2.id)

        vm.setQuery("zzzz"); advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(SearchState.NoResults("zzzz"))
        vm.clear(); advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(SearchState.Idle(""))
    }

    @Test
    fun `snippet markers become highlight runs and nothing else is parsed`() {
        assertThat(SnippetHighlight.runs("rain on the <b>window</b> tonight"))
            .containsExactly("rain on the " to false, "window" to true, " tonight" to false).inOrder()
        assertThat(SnippetHighlight.runs("<b>neon</b> <b>rain</b>"))
            .containsExactly("neon" to true, " " to false, "rain" to true).inOrder()
        assertThat(SnippetHighlight.runs("a <i>literal</i> <b>unterminated"))
            .containsExactly("a <i>literal</i> " to false, "unterminated" to true).inOrder()
        assertThat(SnippetHighlight.runs("")).isEmpty()
    }
}
