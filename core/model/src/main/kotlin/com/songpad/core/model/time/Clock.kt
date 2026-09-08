package com.songpad.core.model.time

/** Wall-clock source. Injected so every timestamp in the domain is testable. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }
    }
}
