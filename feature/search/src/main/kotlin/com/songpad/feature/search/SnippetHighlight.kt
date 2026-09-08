package com.songpad.feature.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * FTS5 `snippet()` marks hits with `<b>…</b>` (§10.1). Only that one marker pair is honoured; anything else
 * in the text — including user-typed angle brackets — is rendered literally.
 */
object SnippetHighlight {
    private const val OPEN = "<b>"
    private const val CLOSE = "</b>"

    /** Pure split for tests: alternating (text, isHighlighted) runs. */
    fun runs(snippet: String): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>()
        var i = 0
        while (i < snippet.length) {
            val open = snippet.indexOf(OPEN, i)
            if (open < 0) { out += snippet.substring(i) to false; break }
            if (open > i) out += snippet.substring(i, open) to false
            val close = snippet.indexOf(CLOSE, open + OPEN.length)
            if (close < 0) { out += snippet.substring(open + OPEN.length) to true; break }
            out += snippet.substring(open + OPEN.length, close) to true
            i = close + CLOSE.length
        }
        return out.filter { it.first.isNotEmpty() }
    }

    fun annotate(snippet: String, highlight: Color): AnnotatedString = buildAnnotatedString {
        runs(snippet).forEach { (text, hit) ->
            if (hit) pushStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold))
            append(text)
            if (hit) pop()
        }
    }
}
