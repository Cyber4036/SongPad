# ADR-0019: App-specific storage without a storage permission

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Recordings are private working files; the app must not ask for storage permissions.

## Decision

`TakeFileStore(context.filesDir)` owns `takes/{id}/`, `notes/{id}/` and `tmp/` (§9.1). No `MediaStore`, no external storage. Sharing goes through a `FileProvider` scoped to `tmp/` only.

## Rejected alternatives

- Shared storage / `MediaStore` — permission cost and no benefit for private drafts.

## Consequences

Uninstall deletes everything; Settings says so explicitly and points to Export (§21.3 #2).

## References

- spec §4.1 Storage
- spec §9.1
