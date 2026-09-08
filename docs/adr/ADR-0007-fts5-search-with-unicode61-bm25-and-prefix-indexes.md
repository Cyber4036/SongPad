# ADR-0007: FTS5 search with unicode61, bm25 and prefix indexes

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Search-as-you-type across four entity types with diacritic-insensitive matching, ranking and snippets.

## Decision

One FTS5 table `search_index` (unicode61, `remove_diacritics 2`, `prefix='2 3 4'`) populated by 12 triggers over song/section/note/take; `bm25(4.0, 1.0)` weights titles over bodies; `snippet()` marks hits with `<b>…</b>` which `SnippetHighlight` turns into spans. User input passes through `FtsQueryBuilder`, which escapes it and appends `*` for prefix matching. See ADR-0024 for why the table stores content rather than using external-content mode.

## Rejected alternatives

- FTS4 — obsolete, no `bm25()`, weaker prefix support (Research Pass B override, ADR-0025).
- LIKE '%q%' — full scans, no ranking, no diacritic folding.

## Consequences

The index roughly doubles text storage, which is negligible next to audio. Search stays inside `:core:data`; features only see `SearchEngine`.

## References

- spec §4.1 Search
- spec §10
- core/data/src/main/kotlin/com/songpad/core/data/db/fts/
