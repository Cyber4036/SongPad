# ADR-0006: UUIDv7 primary keys stored as TEXT

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Every table must be sync-mergeable later (§6.3) while keeping B-tree inserts near-sequential today.

## Decision

`UuidV7` in `:core:model` generates RFC 9562 v7 ids (48-bit ms timestamp + 12-bit sub-ms counter + random tail); ids are stored as 36-char lowercase TEXT. `IdGenerator` is the injectable seam; tests use `SequentialIdGenerator`.

## Rejected alternatives

- UUIDv4 — random inserts fragment the B-tree.
- INTEGER autoincrement — fastest, but not mergeable across devices.

## Consequences

Time ordering means `ORDER BY id` approximates creation order, but code must still order by `created_at` for correctness across clock skew. S-4 measured insert throughput of 2,000 rows well under the 2 s threshold on the desktop build.

## References

- spec §4.1 Primary keys
- core/model/src/main/kotlin/com/songpad/core/model/id/UuidV7.kt
