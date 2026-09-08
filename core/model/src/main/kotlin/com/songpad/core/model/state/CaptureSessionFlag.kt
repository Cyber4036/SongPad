package com.songpad.core.model.state

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-level "a capture is active" flag (§9.3 rule 4). Set by the recorder engine, read by
 * `OrphanCleanupWorker`, which defers the whole run if it is set. Pure Kotlin so both sides can share it.
 */
object CaptureSessionFlag {
    private val active = AtomicBoolean(false)
    val isActive: Boolean get() = active.get()
    fun set(value: Boolean) = active.set(value)
}
