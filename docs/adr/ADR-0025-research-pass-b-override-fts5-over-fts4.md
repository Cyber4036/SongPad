# ADR-0025: Research Pass B override: FTS5 over FTS4

**Status:** Accepted (override of Research Pass B)
**Date:** 2026-09-07

## Context

Research Pass B specified FTS4.

## Decision

FTS5. FTS4 is obsolete: no `bm25()`, weaker prefix indexes, no `remove_diacritics 2`.

## Rejected alternatives

- FTS4 — rejected as above.

## Consequences

None beyond requiring the bundled SQLite build (ADR-0005), which is already chosen.

## References

- spec §4.1 Search
