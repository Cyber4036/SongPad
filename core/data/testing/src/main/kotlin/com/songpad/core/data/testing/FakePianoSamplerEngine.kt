package com.songpad.core.data.testing

import com.songpad.core.model.piano.PianoSamplerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePianoSamplerEngine : PianoSamplerEngine {
    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded

    var loadAsyncCallCount = 0
    var armCallCount = 0
    var disarmCallCount = 0
    var releaseCallCount = 0
    var armResult: Result<Unit> = Result.success(Unit)
    val noteOnCalls = mutableListOf<Pair<Int, Int>>()
    val noteOffCalls = mutableListOf<Int>()

    /** Test hook — call after [loadAsync] to simulate the async load completing. */
    fun completeLoad() { _loaded.value = true }

    override fun loadAsync() { loadAsyncCallCount++ }
    override fun arm(): Result<Unit> { armCallCount++; return armResult }
    override fun disarm() { disarmCallCount++ }
    override fun noteOn(note: Int, velocity: Int) { noteOnCalls += note to velocity }
    override fun noteOff(note: Int) { noteOffCalls += note }
    override fun release() { releaseCallCount++ }
}
