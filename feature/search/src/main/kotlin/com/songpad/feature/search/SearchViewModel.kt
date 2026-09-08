package com.songpad.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.model.data.SearchEngine
import com.songpad.core.model.data.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** §17 "Songs.SearchResults" — the one place search results are a state. Global or scoped to a song (§10.2). */
sealed interface SearchState {
    val query: String

    data class Idle(override val query: String = "") : SearchState
    data class Searching(override val query: String) : SearchState
    data class NoResults(override val query: String) : SearchState
    data class Results(override val query: String, val results: SearchResults) : SearchState
    data class Failed(override val query: String, val message: String) : SearchState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val engine: SearchEngine) : ViewModel() {

    private val songScope = MutableStateFlow<String?>(null)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Set once by the screen: null for the global search, a song id for "search in song". */
    fun bind(songId: String?) { songScope.value = songId }

    fun setQuery(q: String) { _query.value = q }
    fun clear() { _query.value = "" }

    val state: StateFlow<SearchState> = combine(_query, songScope) { q, s -> q.trim() to s }
        .distinctUntilChanged()
        .debounce { (q, _) -> if (q.isEmpty()) 0L else DEBOUNCE_MS }
        .flatMapLatest { (q, scope) ->
            if (q.length < MIN_QUERY_LENGTH) flowOf(SearchState.Idle(q))
            else (if (scope == null) engine.searchGlobal(q) else engine.searchInSong(scope, q))
                .map<SearchResults, SearchState> { r -> if (r.isEmpty) SearchState.NoResults(q) else SearchState.Results(q, r) }
                .onStart { emit(SearchState.Searching(q)) }
                .catch { emit(SearchState.Failed(q, it.message ?: "Search failed")) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle())

    companion object {
        const val DEBOUNCE_MS = 200L
        /** FTS5 prefix indexes start at 2 characters (§10.1 `prefix='2 3 4'`); a single letter would scan. */
        const val MIN_QUERY_LENGTH = 2
    }
}
