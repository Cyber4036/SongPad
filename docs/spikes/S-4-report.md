# Spike S-4 — UUIDv7 insert throughput + FTS5 under BundledSQLiteDriver

Generated 2026-09-07T20:04:14.613079054+02:00[Africa/Kigali] on Linux amd64, JVM 17.0.20.1.
SQLite `3.50.1` from `androidx.sqlite:sqlite-bundled` — the same bundled build the APK ships. FTS5 compiled in: **true**.

> **Scope note.** This run is on a desktop JVM, not the Pixel 4a / Tecno Spark 8. It proves the *functional* claims
> (FTS5 present, bm25/snippet/prefix working, triggers correct, UUIDv7 ordering) with certainty, because the SQLite build is
> identical. The *timing* numbers are an upper bound on desktop only; the device run must be repeated with the same harness
> (instrumented test `SongPadDatabaseTest` + this main) before the number is quoted as a device verdict.

## 1. Insert 2,000 takes (UUIDv7 keys) — threshold < 2 s

| Run | Rows | Elapsed |
| --- | --- | --- |
| warm-up | 200 | 12 ms |
| **measured** | **2,000** | **76 ms** |
| unassigned (song_id NULL) | 500 | 19 ms |

Verdict: **GO** (76 ms vs 2,000 ms threshold). Each insert also fires the `take_ai` FTS trigger, so this is the real write cost.

## 2. FTS5 query — threshold < 15 ms, with bm25 · snippet · prefix functional

Index rows: 3321 (takes + sections + notes + songs).

| Query | Prefix | Rows | Top type | p50 | p95 | max |
| --- | --- | --- | --- | --- | --- | --- |
| `mid` | true | 50 | song | 0.686 ms | 0.987 ms | 6.264 ms |
| `midnight ri` | true | 50 | song | 0.595 ms | 0.719 ms | 0.916 ms |
| `neon` | true | 50 | take | 0.704 ms | 0.744 ms | 0.774 ms |
| `chorus id` | true | 50 | take | 0.84 ms | 0.945 ms | 1.185 ms |
| `café` | true | 50 | take | 0.451 ms | 0.712 ms | 0.719 ms |
| `rain` | true | 50 | take | 0.378 ms | 0.397 ms | 0.399 ms |
| `riff` | true | 50 | song | 0.471 ms | 0.768 ms | 0.889 ms |
| `Take 19` | false | 50 | take | 0.633 ms | 0.739 ms | 0.899 ms |

- `snippet()` highlights: **true** (e.g. `Take 3 <b>neon</b>`)
- prefix match (`midn` → midnight): **true** (50 rows)
- `bm25()` title weighting (title hits for `rain` rank above content-only hits): **true** (50 title / 0 content-only in top 50)
- song-scoped filter is a subset of global: **true** (50 vs 50)
- `remove_diacritics 2` (`cafe` finds `café`): **true**

Verdict: **GO** (worst p95 0.987 ms vs 15 ms threshold; all functional checks pass).

## 3. Trigger behaviour

- Soft-deleting a take removed exactly one index row: **true** (3321 → 3320).

## Overall

**GO** on the desktop run. Device confirmation pending (Pixel 4a, Tecno Spark 8). Fallback named in the spec if the device run fails the 15 ms query threshold: drop `prefix = '2 3 4'` to `prefix = '2 3'` and cap results at 30; if the insert threshold fails, batch commits stay as they are (already one transaction) and the UUIDv7 decision holds — the key format is not the bottleneck at this scale.
