package com.songpad.core.data.db.fts

/**
 * §10.1: the user's raw input is escaped and tokenized BEFORE it reaches `MATCH`. Text containing `"`,
 * `*`, `-`, `NEAR`, `OR`, `AND`, `NOT`, parentheses or colons must never be interpreted as FTS5 syntax.
 * Each token becomes a quoted phrase; the final token gets a trailing `*` for search-as-you-type prefix
 * matching. Tokens are implicitly ANDed. This is the only place in the app that builds a MATCH string.
 */
object FtsQueryBuilder {
    private val separators = Regex("[\\s\"*():^\\-+|,;.!?/\\\\\\[\\]{}<>=~`'“”‘’]+")

    /** Returns null when the input has no searchable token (caller shows the idle state, not an error). */
    fun build(rawInput: String): String? {
        val tokens = tokenize(rawInput)
        if (tokens.isEmpty()) return null
        return tokens.mapIndexed { i, t ->
            val quoted = "\"" + t.replace("\"", "\"\"") + "\""
            if (i == tokens.lastIndex) "$quoted*" else quoted
        }.joinToString(" ")
    }

    fun tokenize(rawInput: String): List<String> =
        rawInput.split(separators).map { it.trim() }.filter { it.isNotEmpty() }.take(16)
}
