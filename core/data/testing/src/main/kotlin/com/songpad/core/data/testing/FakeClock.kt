package com.songpad.core.data.testing

import com.songpad.core.model.id.IdGenerator
import com.songpad.core.model.time.Clock

class FakeClock(var now: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = now
    fun advance(ms: Long) { now += ms }
}

class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var n = 0
    override fun newId(): String = "$prefix-${++n}"
}
