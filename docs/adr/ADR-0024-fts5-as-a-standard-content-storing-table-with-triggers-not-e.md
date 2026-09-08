# ADR-0024: FTS5 as a standard content-storing table with triggers, not external content

**Status:** Accepted (deviation from spec wording, raised)
**Date:** 2026-09-07

## Context

The spec says "external-content table", but FTS5 external content binds to exactly one content table, and `search_index` aggregates four (song, section, note, take).

## Decision

`search_index` is a standard FTS5 table that stores its own copy of the text and is kept in sync by 12 AFTER INSERT/UPDATE/DELETE triggers. `rebuildSearchIndex` exists for migrations. The trade is roughly 2× text storage, which is negligible beside audio.

## Rejected alternatives

- External-content FTS5 — cannot span four source tables; a UNION view is not a valid content table.
- Four separate FTS tables — four queries per keystroke and no cross-type ranking.
- Contentless FTS5 — no `snippet()`.

## Consequences

The spec's wording is treated as intent ("keep FTS in sync with the entities via triggers") rather than the literal `content=` option. Raised as a spec clarification.

## References

- spec §10.1
- core/data/src/main/kotlin/com/songpad/core/data/db/fts/SearchIndexDdl.kt
