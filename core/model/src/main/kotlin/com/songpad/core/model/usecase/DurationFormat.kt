package com.songpad.core.model.usecase

/** "2:41" / "1:02:03". Shared by capture, takes, recovery prompt and export summary. */
object DurationFormat {
    fun mmss(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0) + 500) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
