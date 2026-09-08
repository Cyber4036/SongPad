# Stage 0 spikes (spec §21.1)

| ID | Spike | Threshold | Status |
| --- | --- | --- | --- |
| S-1 | Recorder crash recovery on Xiaomi/HyperOS + Transsion, `am kill` after 30 min | ≤ 1 s lost, partial `.pcm` decodable | **Pending — physical devices** |
| S-2 | Piano touch-to-tone latency via Oboe on 3 devices | median ≤ 40 ms mid-range | **Pending — physical devices**; gates the sampler |
| S-3 | MIDI/audio alignment over a 5-minute Voice+Piano take | < 10 ms cumulative divergence | **Pending — physical devices**; gates Voice+Piano |
| S-4 | UUIDv7 insert throughput + FTS5 under BundledSQLiteDriver | 2,000 inserts < 2 s; query < 15 ms; bm25/snippet/prefix functional | **Functional pass on desktop** — see S-4-report.md; device timing pending |
| S-5 | Navigation 3 two-screen prototype with an active recorder | no restoration/back-stack defects; predictive back works | **Pending — physical devices** (the shell is built; the test protocol is below) |

The desktop-runnable part of S-4 is `./gradlew :spikes:s4-fts5:run`, which rewrites `S-4-report.md`.
The other four need hardware the single-developer pass did not have; each template below records exactly what to run
and what "go" means so a device day can close them without re-deriving anything.

**Known gap:** no Xiaomi/HyperOS device was available in this pass. S-1 and the §11.4 nudge-delivery check are
incomplete until one is on the bench; HyperOS's battery manager is the most aggressive of the target set.
