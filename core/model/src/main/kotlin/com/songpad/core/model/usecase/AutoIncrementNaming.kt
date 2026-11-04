package com.songpad.core.model.usecase

import com.songpad.core.model.take.TakeType

/**
 * Generates collision-free sequential default names for unnamed recordings (takes) and standalone lyrics.
 * Examples:
 * - Voice recordings: "Voice take 1", "Voice take 2", ...
 * - Piano recordings: "Piano take 1", "Piano take 2", ...
 * - Combined recordings: "Voice + Piano take 1", "Voice + Piano take 2", ...
 * - Standalone lyrics: "Lyric 1", "Lyric 2", ...
 */
object AutoIncrementNaming {
    const val LYRIC_PREFIX = "Lyric"

    fun takePrefix(type: TakeType): String = when (type) {
        TakeType.VOICE -> "Voice take"
        TakeType.PIANO -> "Piano take"
        TakeType.VOICE_PIANO -> "Voice + Piano take"
    }

    /**
     * Given a [prefix] (such as `"Voice take"` or `"Lyric"`) and [existingNames], finds the highest
     * integer `N` among names matching `"$prefix"` (counted as 1) or `"$prefix N"` (case-insensitive)
     * and returns `"$prefix ${max + 1}"`.
     */
    fun nextName(prefix: String, existingNames: Iterable<String>): String {
        val trimmedPrefix = prefix.trim()
        val regex = Regex("^${Regex.escape(trimmedPrefix)}(?:\\s+(\\d+))?$", RegexOption.IGNORE_CASE)
        var maxNumber = 0
        for (raw in existingNames) {
            val match = regex.matchEntire(raw.trim()) ?: continue
            val numStr = match.groupValues.getOrNull(1).orEmpty()
            val num = if (numStr.isEmpty()) 1 else numStr.toIntOrNull() ?: continue
            if (num > maxNumber) maxNumber = num
        }
        return "$trimmedPrefix ${maxNumber + 1}"
    }
}
