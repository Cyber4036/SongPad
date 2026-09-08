package com.songpad.core.model.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A minimal, pure-Kotlin MVI loop (§4.1 "MVI only for the capture screen", §7.2). One sealed Intent, one
 * State, optional one-shot Effects. Intents are processed one at a time in order, so the recording state
 * machine's legal transitions are enforced in a single reducer rather than from several call sites.
 *
 * Feature ViewModels embed a store rather than extend it, which keeps this module Android-free.
 */
class MviStore<S : Any, I : Any, E : Any>(
    initialState: S,
    private val scope: CoroutineScope,
    private val handler: suspend MviStore<S, I, E>.(intent: I) -> Unit,
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<E>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects: SharedFlow<E> = _effects.asSharedFlow()

    private val mutex = Mutex()

    val current: S get() = _state.value

    fun dispatch(intent: I) {
        scope.launch { mutex.withLock { handler(intent) } }
    }

    fun reduce(transform: (S) -> S) = _state.update(transform)

    fun emit(effect: E) { _effects.tryEmit(effect) }
}
