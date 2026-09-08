package com.songpad.core.model.id

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UuidV7Test {
    @Test
    fun `ids are valid version 7 and carry the timestamp`() {
        var now = 1_725_000_000_123L
        val gen = UuidV7(clock = { now }, seed = 42)
        val id = gen.next()
        assertThat(UuidV7.isValid(id)).isTrue()
        assertThat(UuidV7.timestampOf(id)).isEqualTo(now)
        now += 5
        assertThat(UuidV7.timestampOf(gen.next())).isEqualTo(now)
    }

    @Test
    fun `ids generated in the same millisecond are strictly increasing`() {
        val gen = UuidV7(clock = { 1_725_000_000_000L }, seed = 1)
        val ids = List(5000) { gen.next() }
        for (i in 1 until ids.size) assertThat(ids[i]).isGreaterThan(ids[i - 1])
        assertThat(ids.toSet()).hasSize(ids.size)
    }

    @Test
    fun `a clock that goes backwards never breaks ordering`() {
        val times = ArrayDeque(listOf(2000L, 1000L, 1500L, 2000L, 3000L))
        val gen = UuidV7(clock = { times.removeFirst() }, seed = 7)
        val ids = List(5) { gen.next() }
        for (i in 1 until ids.size) assertThat(ids[i]).isGreaterThan(ids[i - 1])
    }

    @Test
    fun `format renders known words byte exactly`() {
        assertThat(UuidV7.format(0x0190_0000_0000_7abcL, -0x7fff_ffff_ffff_0001L or (0x2L shl 62)))
            .matches("^0190[0-9a-f]{4}-0000-7abc-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
