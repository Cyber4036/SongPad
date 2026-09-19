package com.songpad.core.model.piano

import kotlinx.coroutines.flow.StateFlow

// ── §15-style contract (added post-Week-1: the sampler wasn't started until S-2/S-3 returned go
// verdicts — see docs/adr/ADR-0015). Implemented in :core:audio. ──

/**
 * The Oboe polyphonic sampler (§8.1). Loading is asynchronous and begins when the capture screen
 * opens, not when Piano mode is toggled, so the first key press is never gated on I/O — callers
 * should invoke [loadAsync] once at screen entry and just let [noteOn] arrive whenever the user
 * taps a key, loaded or not (calls before loading finishes are simply not audible).
 */
interface PianoSamplerEngine {
    /** True once every sample region has been decoded into native memory. */
    val loaded: StateFlow<Boolean>

    /** Idempotent — safe to call more than once (e.g. on process/config changes reusing the instance). */
    fun loadAsync()

    /** Opens and starts the output stream. Call when Piano / Voice+Piano mode is armed. */
    fun arm(): Result<Unit>

    /** Stops the output stream but keeps it open, so re-arming within the same screen is cheap. */
    fun disarm()

    fun noteOn(note: Int, velocity: Int)
    fun noteOff(note: Int)

    /** Full teardown — native resources and loaded samples are released. Call from onCleared(). */
    fun release()
}
