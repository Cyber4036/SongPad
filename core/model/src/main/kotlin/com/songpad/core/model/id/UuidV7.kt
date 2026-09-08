package com.songpad.core.model.id

import java.security.SecureRandom

/**
 * RFC 9562 UUID version 7: 48-bit Unix epoch milliseconds, 4-bit version, 12 bits of
 * sub-millisecond monotonic counter, 2-bit variant, 62 random bits.
 *
 * Time-ordered so B-tree inserts stay near-sequential (§4.1 "Primary keys"), globally unique so
 * rows are sync-mergeable later (§6.3). Rendered as the canonical 36-char lowercase string.
 *
 * Monotonicity: within one process, two ids generated in the same millisecond are ordered by the
 * 12-bit counter; if the clock goes backwards the last timestamp is reused so ordering never breaks.
 */
class UuidV7(private val clock: () -> Long = System::currentTimeMillis, seed: Long? = null) {

    private val random = if (seed == null) SecureRandom() else java.util.Random(seed)
    private var lastMillis = -1L
    private var counter = 0

    @Synchronized
    fun next(): String {
        var now = clock()
        if (now <= lastMillis) {
            now = lastMillis
            counter++
            if (counter > 0xFFF) { // counter exhausted: borrow a millisecond
                now = lastMillis + 1
                counter = 0
            }
        } else {
            counter = random.nextInt(0x800) // start counter at a random point below half range
        }
        lastMillis = now

        val hi = (now shl 16) or (0x7L shl 12) or counter.toLong()
        val lo = (random.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or (0x2L shl 62)
        return format(hi, lo)
    }

    companion object {
        private const val HEX = "0123456789abcdef"

        fun format(hi: Long, lo: Long): String {
            val sb = StringBuilder(36)
            appendHex(sb, hi ushr 32, 8); sb.append('-')
            appendHex(sb, (hi ushr 16) and 0xFFFF, 4); sb.append('-')
            appendHex(sb, hi and 0xFFFF, 4); sb.append('-')
            appendHex(sb, (lo ushr 48) and 0xFFFF, 4); sb.append('-')
            appendHex(sb, lo and 0xFFFF_FFFF_FFFFL, 12)
            return sb.toString()
        }

        private fun appendHex(sb: StringBuilder, value: Long, digits: Int) {
            for (i in digits - 1 downTo 0) sb.append(HEX[((value ushr (i * 4)) and 0xF).toInt()])
        }

        /** Extracts the embedded epoch-millisecond timestamp. */
        fun timestampOf(id: String): Long = id.substring(0, 8).toLong(16) shl 16 or id.substring(9, 13).toLong(16)

        private val PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        fun isValid(id: String): Boolean = PATTERN.matches(id)
    }
}
