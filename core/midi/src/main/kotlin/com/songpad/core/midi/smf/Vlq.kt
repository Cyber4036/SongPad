package com.songpad.core.midi.smf

import java.io.OutputStream

/**
 * §8.4 variable-length quantity: 7 data bits per byte, bit 7 set on all but the terminal byte, big-endian.
 *   1060 = 0b100_0100100 → [0001000][0100100] = 0x08 0x24 → 0x88 0x24
 */
object Vlq {
    fun encode(value: Long): ByteArray {
        require(value >= 0 && value <= 0x0FFF_FFFFL) { "VLQ out of range: $value" }
        var v = value
        val out = ArrayList<Byte>(4)
        out.add((v and 0x7F).toByte())
        v = v ushr 7
        while (v > 0) {
            out.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        return ByteArray(out.size) { out[out.size - 1 - it] }
    }

    fun write(out: OutputStream, value: Long) = out.write(encode(value))

    /** Decodes one VLQ starting at [offset]; returns value and the number of bytes consumed. */
    fun decode(bytes: ByteArray, offset: Int): Pair<Long, Int> {
        var value = 0L
        var i = offset
        while (true) {
            val b = bytes[i].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F).toLong()
            i++
            if (b and 0x80 == 0) break
        }
        return value to (i - offset)
    }
}
