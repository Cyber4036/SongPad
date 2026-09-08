# ADR-0005: Room 2.8 with BundledSQLiteDriver

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

FTS5 is a compile-time SQLite option and OEM platform builds vary. The schema also needs reactive queries and compile-time SQL checking.

## Decision

Room 2.8.4 on `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`), `setQueryCoroutineContext(Dispatchers.IO)`. The bundled build guarantees FTS5 (§4.2) regardless of the device. Schemas are exported to `core/data/schemas/` and every migration is tested with `MigrationTestHelper` on the same driver.

## Rejected alternatives

- SQLDelight — SQL-first/KMP oriented, no reactive Room ergonomics.
- Raw SQLite — no compile-time checking, no reactive queries.
- Realm / ObjectBox — proprietary binaries and threading constraints.

## Consequences

The APK carries its own SQLite (~1 MB per ABI). The S-4 spike runs against the identical bundled library on the JVM (`sqlite-bundled-jvm`), so desktop numbers are representative of the engine, though not of device I/O.

## References

- spec §4.1 Database
- spec §4.2
- spec §6.4
