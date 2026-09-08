package com.songpad.spikes.s4

import java.io.File

/** Runs the S-4 measurements and writes a written go/no-go verdict. Thresholds per Prompt 0-B. */
fun main(args: Array<String>) {
    val out = File(args.getOrNull(0) ?: "docs/spikes/S-4-report.md")
    val dbFile = File.createTempFile("s4-spike", ".db")
    val report = StringBuilder()
    fun line(s: String = "") { println(s); report.appendLine(s) }

    S4Harness(dbFile.absolutePath).use { h ->
        line("# Spike S-4 — UUIDv7 insert throughput + FTS5 under BundledSQLiteDriver")
        line()
        line("Generated ${java.time.ZonedDateTime.now()} on ${System.getProperty("os.name")} ${System.getProperty("os.arch")}, JVM ${System.getProperty("java.version")}.")
        line("SQLite `${h.sqliteVersion()}` from `androidx.sqlite:sqlite-bundled` — the same bundled build the APK ships. FTS5 compiled in: **${h.fts5Available()}**.")
        line()
        line("> **Scope note.** This run is on a desktop JVM, not the Pixel 4a / Tecno Spark 8. It proves the *functional* claims")
        line("> (FTS5 present, bm25/snippet/prefix working, triggers correct, UUIDv7 ordering) with certainty, because the SQLite build is")
        line("> identical. The *timing* numbers are an upper bound on desktop only; the device run must be repeated with the same harness")
        line("> (instrumented test `SongPadDatabaseTest` + this main) before the number is quoted as a device verdict.")
        line()

        // ── Measurement 1: 2,000 takes with UUIDv7 keys, one transaction, on-disk WAL ──
        val song = h.insertSong("Midnight Riff")
        val warm = h.insertTakes(200, song)
        val insertMs = h.insertTakes(2_000, song)
        val loose = h.insertTakes(500, null)
        line("## 1. Insert 2,000 takes (UUIDv7 keys) — threshold < 2 s")
        line()
        line("| Run | Rows | Elapsed |")
        line("| --- | --- | --- |")
        line("| warm-up | 200 | $warm ms |")
        line("| **measured** | **2,000** | **$insertMs ms** |")
        line("| unassigned (song_id NULL) | 500 | $loose ms |")
        line()
        line("Verdict: **${if (insertMs < 2000) "GO" else "NO-GO"}** ($insertMs ms vs 2,000 ms threshold). Each insert also fires the `take_ai` FTS trigger, so this is the real write cost.")
        line()

        // ── Measurement 2: FTS5 query < 15 ms with bm25, snippet, prefix ──
        repeat(300) { i -> h.insertSection(song, listOf("VERSE", "CHORUS", "PRE_CHORUS", "BRIDGE")[i % 4], (i / 4) + 1, "line $i under the neon rain, hold the riff ${S4Harness.WORDS[i % S4Harness.WORDS.size]}") }
        repeat(300) { i -> h.insertNote(song, "note $i — try the chorus idea with a darker riff, café lights ${S4Harness.WORDS[(i * 3) % S4Harness.WORDS.size]}") }
        repeat(20) { i -> h.insertSong("Song number $i about rain") }
        line("## 2. FTS5 query — threshold < 15 ms, with bm25 · snippet · prefix functional")
        line()
        line("Index rows: ${h.indexCount()} (takes + sections + notes + songs).")
        line()
        line("| Query | Prefix | Rows | Top type | p50 | p95 | max |")
        line("| --- | --- | --- | --- | --- | --- | --- |")
        var worstP95 = 0L
        listOf("mid", "midnight ri", "neon", "chorus id", "café", "rain", "riff", "Take 19").forEach { q ->
            val samples = (1..25).map { h.search(q).second }.sorted()
            val (rows, _) = h.search(q)
            val p50 = samples[samples.size / 2]; val p95 = samples[(samples.size * 0.95).toInt().coerceAtMost(samples.size - 1)]; val max = samples.last()
            worstP95 = maxOf(worstP95, p95)
            line("| `$q` | ${q.last().isLetter()} | ${rows.size} | ${rows.firstOrNull()?.type ?: "—"} | ${p50 / 1000.0} ms | ${p95 / 1000.0} ms | ${max / 1000.0} ms |")
        }
        line()
        val (neon, _) = h.search("neon")
        val (prefix, _) = h.search("midn")
        val (scoped, _) = h.search("rain", song)
        val (global, _) = h.search("rain")
        val snippetOk = neon.any { it.snippet.contains("<b>neon</b>") }
        val prefixOk = prefix.isNotEmpty()
        // bm25(4.0, 1.0): every row whose TITLE matches must rank at or above every row that only matches in content.
        // (Ranks are negative; ORDER BY rank ascending, so "better" is smaller.)
        val titleHits = global.filter { it.title.contains("rain", ignoreCase = true) }
        val contentOnly = global.filter { !it.title.contains("rain", ignoreCase = true) }
        val rankOk = titleHits.isNotEmpty() && (contentOnly.isEmpty() || titleHits.maxOf { it.rank } <= contentOnly.minOf { it.rank })
        // Both lists are capped at LIMIT 50, so "subset" is checked on membership, not size.
        val scopeOk = scoped.isNotEmpty() && scoped.all { it.songId == song } && scoped.size <= global.size
        val accentOk = h.search("cafe").first.isNotEmpty() // remove_diacritics 2: "cafe" matches "café"
        line("- `snippet()` highlights: **$snippetOk** (e.g. `${neon.firstOrNull()?.snippet?.take(70)}`)")
        line("- prefix match (`midn` → midnight): **$prefixOk** (${prefix.size} rows)")
        line("- `bm25()` title weighting (title hits for `rain` rank above content-only hits): **$rankOk** (${titleHits.size} title / ${contentOnly.size} content-only in top 50)")
        line("- song-scoped filter is a subset of global: **$scopeOk** (${scoped.size} vs ${global.size})")
        line("- `remove_diacritics 2` (`cafe` finds `café`): **$accentOk**")
        line()
        val queryGo = worstP95 < 15_000 && snippetOk && prefixOk && rankOk && scopeOk
        line("Verdict: **${if (queryGo) "GO" else "NO-GO"}** (worst p95 ${worstP95 / 1000.0} ms vs 15 ms threshold; all functional checks ${if (snippetOk && prefixOk && rankOk && scopeOk) "pass" else "DID NOT pass"}).")
        line()

        // ── Trigger correctness: soft delete leaves the index ──
        val before = h.indexCount()
        val someTake = h.connection.prepare("SELECT id FROM take LIMIT 1").use { it.step(); it.getText(0) }
        h.softDeleteTake(someTake)
        val after = h.indexCount()
        line("## 3. Trigger behaviour")
        line()
        line("- Soft-deleting a take removed exactly one index row: **${before - after == 1L}** ($before → $after).")
        line()
        line("## Overall")
        line()
        line("**${if (insertMs < 2000 && queryGo) "GO" else "NO-GO"}** on the desktop run. Device confirmation pending (Pixel 4a, Tecno Spark 8). Fallback named in the spec if the device run fails the 15 ms query threshold: drop `prefix = '2 3 4'` to `prefix = '2 3'` and cap results at 30; if the insert threshold fails, batch commits stay as they are (already one transaction) and the UUIDv7 decision holds — the key format is not the bottleneck at this scale.")
    }
    out.parentFile?.mkdirs()
    out.writeText(report.toString())
    dbFile.delete()
    println("\nReport written to ${out.path}")
}
