# Stage 0 spikes (spec §21.1)

| ID | Spike | Threshold | Status |
| --- | --- | --- | --- |
| S-1 | Recorder crash recovery on Xiaomi/HyperOS + Transsion, `am kill` after 30 min | ≤ 1 s lost, partial `.pcm` decodable | **Pass on Samsung Galaxy A02** (`SM-A022F`, API 30) & **Tecno Spark 8** (`TECNO KG6`, API 30, Transsion); Xiaomi pending |
| S-2 | Piano touch-to-tone latency via Oboe on 3 devices | median ≤ 40 ms mid-range (≤ 60 ms budget) | **Pass on Samsung Galaxy A02** (avg 52 ms, min 30 ms, xRun 0); other tiers pending |
| S-3 | MIDI/audio alignment over a 5-minute Voice+Piano take | < 10 ms cumulative divergence | **Pass on Samsung Galaxy A02** (1.12 ms drift over 5 min, +3.73 ppm, 0 xruns; Voice+Piano gate cleared) |
| S-4 | UUIDv7 insert throughput + FTS5 under BundledSQLiteDriver | 2,000 inserts < 2 s; query < 15 ms; bm25/snippet/prefix functional | **Pass on desktop, Samsung Galaxy A02, and Tecno Spark 8** — see S-4-report.md |
| S-5 | Navigation 3 two-screen prototype with an active recorder | no restoration/back-stack defects; predictive back works | **Pass on Samsung Galaxy A02 & Tecno Spark 8** (M1 slice verified) |

The desktop-runnable part of S-4 is `./gradlew :spikes:s4-fts5:run`, which rewrites `S-4-report.md`.
The physical hardware benchmarks are executed via `./gradlew :core:data:connectedDebugAndroidTest` and `:core:audio:connectedDebugAndroidTest`.
Hardware verification completed on Samsung Galaxy A02 (`SM-A022F`, Android 11 / API 30, ARMv7a) and Tecno Spark 8 (`TECNO KG6`, Android 11 / API 30, ARMv7a).


**Known gap:** no Xiaomi/HyperOS device was available in this pass. S-1 and the §11.4 nudge-delivery check are
incomplete until one is on the bench; HyperOS's battery manager is the most aggressive of the target set.
