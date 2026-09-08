# SongPad V1 — Phase 1 Delegation Prompts

*Progressive work assignment, wave by wave. Each block is a self-contained brief to hand to one developer.*

Companion to `SongPad_Phase_1_Development_Specification.md`. Nothing here overrides that document; where this file and the spec disagree, **the spec wins** and this file is wrong and should be corrected.

---

## How to use this file

Six waves. **A wave does not open until the previous gate passes.** Within a wave, all prompts run in parallel — that is the whole point of freezing the §15 contracts.

| Wave | Weeks | Gate that closes it |
| --- | --- | --- |
| 0 — Lock & spikes | 1 | **M0** |
| 1 — Foundation | 1–2 | **Week 2 gate** |
| 2 — Vertical slice | 3–4 | **M1** |
| 3 — Parallel features | 5–8 | **M2** (wk 8), **M3** (wk 10) |
| 4 — Integration | 8–10 | **M3** |
| 5 — Hardening | 10–12 | **M4** |
| 6 — Beta | 12–14 | **M5** |

Role letters follow §19. Wave 0–2 uses **Dev A–E**; Waves 3+ use **D1–D5**. The mapping is:

```
Dev A (lead) ─────────────────────────► D-lead
Dev C (audio) ────────────────────────► D1 (capture flow) + D2 (piano/MIDI)
Dev B (data) ─────────────────────────► D3 (songs/takes/library)
Dev D (design system) ────────────────► D4 (lyrics/notes UI)
Dev E (spikes/devices) ───────────────► D5 (search, nudges, export, settings)
```

On a four-person team, Dev E does not exist: its Wave 0 spikes are absorbed by A/C/D as marked, and D5's Wave 3 stream is split across D1–D4 once their primary streams stabilize (§19).

### Note on device inventory (supersedes the spec's generic four-brand matrix)

Currently available, in place of "one Pixel / one Samsung / one Xiaomi / one Transsion":

| Device | adb / instrumented tests | Role |
| --- | --- | --- |
| **Pixel 4a** | Yes | Clean baseline, same role the spec assigns it |
| **Tecno Spark 8** | Yes | **This is the Transsion device the spec calls for.** Tecno = Transsion. Runs S-1 and S-2 for real. |
| **Galaxy A03** (unconfirmed exact model) | No — apk install only, no adb | Manual/subjective spot-check only: launch, responsiveness, does a recording sound right. Cannot run scripted recovery or latency spikes. Not a substitute for a Samsung in the automated matrix. |
| Xiaomi/HyperOS device | **None available** | **Open gap.** HyperOS has its own aggressive background-kill behavior, distinct from Transsion's, and nothing on hand tests it. |

Wherever a prompt below says "Xiaomi/HyperOS," treat it as **untested pending hardware**, not satisfied by the Tecno result. Every spike or hardening report that touches this must say so explicitly rather than imply full coverage.

---

## Standing preamble — paste this at the top of every prompt below

> **Before you write any code, read, in this order:**
> 1. `SongPad_Phase_1_Development_Specification.md` — §0–§5 (provenance, precedence, scope, locked decisions, modules), §15 (frozen contracts), §19–§21 (work breakdown, milestones, spikes).
> 2. `SongPad_Phase_0_Closure_Specification.docx` — for any product behavior your work touches.
> 3. `Capture.html` — the interactive prototype, for visual and interaction reference only.
> 4. `Rough_Plan_we_re_using_` — background on sequencing and team shape.
>
> **Five standing rules:**
> - **Precedence (§2).** Phase 0 governs product behavior. The Phase 1 spec governs architecture, schema and contracts. The design system governs visual detail. Your judgement governs implementation detail *inside* a locked contract.
> - **Raise, don't implement.** A change to a LOCKED Phase 0 behavior is a product decision — raise it. A change to a LOCKED §4 technical decision is an ADR — it needs the lead's sign-off and an amendment to the spec. Silent adoption of an alternative is not acceptable, even when the alternative is better.
> - **Rejected alternatives get written down.** If you choose between two approaches inside your own contract, record what lost and why, in the PR description. Future code review should not relitigate a settled decision.
> - **Section numbers collide.** "§7" in the Phase 1 spec and "Phase 3" in `Rough_Plan_we_re_using_` are different numbering systems. Always name the document.
> - **No feature module ever depends on another feature module.** Cross-feature navigation goes through route keys declared in `:app` (§5).
>
> If something you need is genuinely undefined by either spec, do not invent it and do not guess at intent. Flag it as an open question and keep moving on the parts that are defined.

---

# Wave 0 — Week 1: Lock and spikes

Nothing in this wave is feature work. The output is contracts, skeletons and go/no-go verdicts.

## Prompt 0-A — Dev A (Technical lead): architecture skeleton and contract freeze

> [standing preamble]
>
> **Your objective this week:** make it possible for four other people to write code on Monday of Week 2 without talking to each other.
>
> **Deliverables:**
> 1. Gradle module skeleton exactly as §5: `:app`, `:feature:capture|song|search|nudges|export`, `:core:audio|midi|data|ui|model`. Enforce the dependency rules in the build files themselves — a feature-to-feature dependency should fail the build, not a code review.
> 2. `:core:model` populated with the domain entities and value types from §6.1 and Phase 0 §20. Pure Kotlin, zero Android dependencies, JVM-unit-testable without Robolectric.
> 3. Hilt graph and DI root.
> 4. CI on GitHub Actions, tier 1 only this week: build + lint + JVM unit tests on every PR, under ~10 minutes (§18). If it is slower than that people will route around it.
> 5. `:core:data:testing` module containing fakes for every interface in §15, so feature work can start against fakes before implementations land.
> 6. ADR file for each LOCKED row in §4.1, each recording the rejected alternative and why it lost. The four Research Pass B overrides (FTS5 over FTS4, Oboe over SoundPool, 48 kHz over 44.1 kHz, dedicated `THREAD_PRIORITY_URGENT_AUDIO` thread over `Dispatchers.IO`) each get their own ADR — they are overrides of a named source and must not read as if nobody considered the alternative.
> 7. **Freeze the §15 interfaces at end of week.** Publish the freeze. After the freeze, a signature change needs your sign-off because someone is already coding against it.
>
> **Done when:** CI is green on an empty skeleton, the fakes compile, and the frozen contracts are published to the team.
>
> **Do not:** build screens, wire navigation destinations to real content, or start any feature module.

## Prompt 0-B — Dev B (Data): schema, DAO signatures, spike S-4

> [standing preamble] Also read §6 (data architecture) and §10 (search) in full.
>
> **Deliverables:**
> 1. Room entities for the full §6.1 schema: Song, Take, AudioAsset, MidiAsset, LyricsDocument, Section, Note, Nudge. UUIDv7 string primary keys, epoch-millisecond `INTEGER` timestamps, foreign keys as drawn.
> 2. The §6.3 sync block on every user-data table. **These columns are written in V1 and read by nothing.** They stay inert (`LOCAL_ONLY`). That is deliberate — cheap now, expensive to retrofit. Do not build sync behavior on top of them.
> 3. DAO signatures only this week, not full implementations.
> 4. **Spike S-4 — this blocks the schema and all search work.** Two measurements under `BundledSQLiteDriver`:
>    - Insert 2,000 takes with UUIDv7 keys. **Threshold: < 2 s.**
>    - FTS5 external-content table plus sync triggers. **Threshold: query < 15 ms**, with `bm25`, `snippet` and prefix matching all functional.
>
>    Report a written go/no-go against those numbers, not an impression. A failing spike is the spike working; the fallback is named in the spec.
>
> **Done when:** entities compile, DAO signatures are frozen alongside §15, and S-4 has returned a verdict in writing.
>
> **Do not:** implement repositories yet, and do not build the search UI.

## Prompt 0-C — Dev C (Audio): NDK skeleton, spikes S-1, S-2, S-3

> [standing preamble] Also read §7 (capture), §8 (piano and clock sync — §8.3 is normative), §9 (file storage) in full.
>
> This is the heaviest Week 1 load on the team. Three of the five spikes are yours, and S-3 is the highest-consequence spike in the project.
>
> **Deliverables:**
> 1. `:core:audio` with Oboe wired through CMake, and the C++ ring buffer skeleton. This is the only module that touches NDK/JNI — keep it that way.
> 2. Buffer sizing strategy at 48 kHz, documented.
> 3. The SMF Type 0 byte schema for `:core:midi`, written down before any serializer code. Hand-written serializer, no dependency; `ktmidi` (MIT) is the only approved alternative if one is wanted.
> 4. **Spike S-1 — blocks all capture work (§7).** 30-minute continuous capture on the **Tecno Spark 8** (this is the Transsion device the spec calls for — Tecno = Transsion), then `adb shell am kill`. **Threshold: the partial `.pcm` is fully decodable to the last synced byte, ≤ 1 s of audio lost.** Run it on the **Pixel 4a** as well for a clean-baseline comparison point. No Xiaomi/HyperOS device is currently available — record this explicitly in the spike report as an untested gap, not a pass by proxy; HyperOS's background-kill behavior is its own thing and the Tecno result does not stand in for it.
> 5. **Spike S-2 — blocks the piano engine (§8.1).** Touch-to-tone latency via Oboe on the **Pixel 4a** (baseline) and the **Tecno Spark 8** (low-end target — this is the "low-end Transsion" device named in the spec). **Threshold: median ≤ 40 ms on the low-end device.** Above ~60 ms, stop and revisit sample loading and buffer strategy before anyone builds a keyboard. Record the measured output latency — §8.3 needs it to offset MIDI note frame indices. The Galaxy A03 can be used for a subjective by-ear check if it becomes available, but has no adb access and cannot produce a scripted measurement.
> 6. **Spike S-3 — blocks Voice+Piano (§8.3), highest consequence.** Metronomic 5-minute Voice+Piano take; compare MIDI note frame positions against audio transients. **Threshold: < 10 ms cumulative divergence at 5 minutes.** Validate the §8.3 construction specifically: audio hardware frame clock as master, `AudioTimestamp` anchor pairs refreshing a linear mapping, `eventTimeNanos` converted at ingest. Do **not** validate the rejected approach of anchoring to `SystemClock.elapsedRealtimeNanos` at t0 and counting forward — the DMA clock drifts against it by tens of ppm and that is exactly what the spike exists to catch.
>
> **Done when:** all three spikes have returned written verdicts against their stated thresholds.
>
> **Do not:** build the recording screen, the piano keyboard, or any polished UI. Not this week and not in Wave 1.

## Prompt 0-D — Dev D (Design system): token architecture, spike S-5

> [standing preamble] Also read §14 (design system) in full, and open `Capture.html`.
>
> **Deliverables:**
> 1. Token architecture in `:core:ui`, three layers as §14: OKLCH primitive ramps → semantic roles (`bg`, `surface`, `text`, `accent`, `accent-2`, `record`, `divider`) → component styles. Components reference **semantic tokens only** and never a raw palette value. All four themes (SongPad Light/Dark, MelodyStudio Light/Dark) from the §14 table.
> 2. Radii (`sm 8`, `md 16`, `lg 28`, `pill 999`dp), typography, spacing, 48×48dp minimum touch target.
> 3. Treat the four themes as **one theme architecture**, not four designs. The waveform Canvas and piano Canvas will read `accent` and `record` from the token layer, so there must be no per-theme drawing code anywhere.
> 4. **Spike S-5 — blocks navigation (§4.1).** Navigation 3 two-screen prototype: library → take detail, with an active recorder running. **Threshold: no state-restoration or back-stack defects, predictive back works.** Fallback is Navigation 2; report a clear verdict either way.
>
> **Done when:** tokens compile and theme-switch cleanly in a preview harness, and S-5 has a verdict.
>
> **Do not:** build feature screens. Glass treatment is a restrained component treatment (glass pill, ribbon player), not a global decoration.

## Prompt 0-E — Dev E (five-person shape only): device matrix and test infrastructure

> [standing preamble] Also read §18 (test strategy) in full.
>
> **Deliverables:**
> 1. Confirm and set up the physical devices actually on hand: **Pixel 4a** (adb, clean baseline) and **Tecno Spark 8** (adb — this is the Transsion device the spec requires; Tecno = Transsion, aggressive process management and low-end audio hardware are exactly why the spec insists on it). Set up the **Galaxy A03** for apk-install manual checks only — no adb available, so it cannot run scripted or instrumented tests. **No Xiaomi/HyperOS device is currently available; this is a known gap**, not something to substitute around. Flag it in the Week 1 report and, before Wave 5 hardening, escalate whether to acquire one — HyperOS's background-kill behavior doesn't transfer from the Tecno result.
> 2. CI tier 2: instrumented tests on KVM-accelerated Linux runners (`reactivecircus/android-emulator-runner`), API 30 and a current API, on merge to main.
> 3. CI tier 3: Firebase Test Lab nightly, **scoped to what it can actually validate**: OS-level lifecycle behavior, thread/process handling, ANRs and crashes, rotation, backgrounding, and broad compatibility sweep across device shapes. **FTL does not validate anything acoustic** — no real speaker-to-mic path, no representative DAC behavior — so it is not a stand-in for S-1, S-2, or S-3, and its nightly run should not be read as covering those. **Non-blocking regardless** — FTL has had capacity incidents and must never be able to block a merge.
> 4. Support Dev C on the S-1/S-2 device runs on the Pixel 4a and Tecno Spark 8; they cannot run three spikes across the available hardware alone in a week.
>
> **If there is no Dev E:** item 1 goes to Dev C, items 2–3 go to Dev A in Wave 1, and Dev C's spike load must be acknowledged as the Week 1 critical path.

### Gate M0 (end of Week 1)

Passes when: the spec is approved; §15 contracts are **frozen and published**; CI is green on an empty skeleton; all five spikes are assigned and have returned or have a named return date.

---

# Wave 1 — Weeks 1–2: Foundation

Runs into Week 1 where it does not depend on the freeze. Everyone codes against frozen interfaces and fakes.

## Prompt 1-A — Dev A: app shell

> [standing preamble]
>
> Build: Navigation 3 host per the S-5 verdict (Nav 2 if S-5 failed), MVI base classes, error and logging infrastructure, build variants. Typed domain errors throughout — every `Result` failure in §15 carries a domain error, never a raw exception.
>
> **Done when:** an empty app boots, navigates between two placeholder destinations, survives rotation and process death, and logs a typed error end to end.

## Prompt 1-B — Dev B: persistence layer

> [standing preamble]
>
> Build: Room on `BundledSQLiteDriver`, DAO implementations, in-memory test database, migration harness. Reactive queries returning `Flow`. Implement `TakeRepository` and `SongRepository` against the frozen §15 signatures.
>
> Include the FTS5 external-content tables and triggers now — per the S-4 verdict — even though no search UI exists until Wave 3. Retrofitting FTS triggers onto a populated database later is avoidable pain.
>
> **Done when:** Room runs, the migration harness executes a no-op v1 migration, and repository fakes can be swapped for real implementations without a signature change.

## Prompt 1-C — Dev C: capture engine

> [standing preamble] §7 and §9 are normative here.
>
> Build: the capture engine writing 48 kHz PCM to disk, C++ ring buffer, dedicated capture thread at `THREAD_PRIORITY_URGENT_AUDIO` (**not** `Dispatchers.IO` — see the ADR), foreground service with the correct FGS type, audio focus handling.
>
> Durability rules from §7.3/§9.4, non-negotiable: headerless PCM, ~1 s fsync cadence, atomic `.tmp` → `.pcm` rename, recovery manager on relaunch. S-1 proved this works; now make it the actual implementation.
>
> **Done when:** the engine records to disk, emits amplitudes at 10 Hz on the §15 `SharedFlow`, exposes `framesWritten` as ground truth, and a killed process leaves a recoverable file.

## Prompt 1-D — Dev D: core components

> [standing preamble]
>
> Build: glass pill, waveform Canvas (reading `accent`/`record` from tokens, no per-theme code), theme wrappers, empty states, list items, sheets, dialogs, buttons.
>
> The waveform Canvas consumes the 10 Hz normalized `SharedFlow<Float>` from `AudioRecorderEngine` (§15). Build against the fake until Dev C's engine lands — that is what the fakes are for.
>
> **Done when:** every component renders correctly in all four themes in a preview harness, at default and large type.

### Week 2 gate

**Hard blockers — all must be true:**
- §15 contracts frozen and stable
- CI green
- Room running
- Capture engine writing to disk
- Media3 playback working
- **S-1 and S-4 verdicts returned**

**Stop signals — if either is true, do not open Wave 2:**
- PCM is unrecoverable after process kill
- Contracts are still in flux

A stop signal is not a schedule problem to be pushed through. Opening Wave 2 on an unstable contract converts a two-day fix into a Week 9 rewrite.

---

# Wave 2 — Weeks 3–4: Vertical slice

**One shared objective, four people.** Not four parallel objectives. Nobody starts feature work until this journey runs on real hardware.

> **The journey (§16):** Open app → tap Record → record 15 s of voice with live waveform → pause → review by scrubbing → Save → name Song "Midnight Riff" → land in Song Home → play the Take back.

## Prompt 2-A — Dev A

> Wire the full chain: `RecordScreen` → `CaptureIntent` → `CaptureViewModel` → `StartRecordingUseCase` / `FinalizeTakeUseCase` → repository → Room + file store → audio engine → `PlaybackController`. Own the seams. Where a layer boundary is awkward, that is architectural information — surface it now, not in Week 9.

## Prompt 2-C — Dev C

> PCM → commit → transcode → review player. `FinalizeTakeUseCase` stops, flushes, fsyncs, renames `.tmp` → `.pcm`, then enqueues `TranscodeWorker`.
>
> **The take is playable from raw PCM the instant it is saved.** The `Transcoding` state exists for correctness, never as a wait. Raw PCM is deleted only after the M4A (Standard) or FLAC (Lossless) is written **and verified** (§4.3).

## Prompt 2-B — Dev B

> The Song + Take commit as a single Room transaction, including `AudioAssetEntity` and `TakeEntity(status = TRANSCODING)`. Reactive queries so `TakeRepository.observeForSong()` emits the new take without a manual refresh.

## Prompt 2-D — Dev D

> `RecordScreen`, `SongDetailScreen` and the ribbon player, built from Wave 1 components. Enough fidelity to prove the interaction; polish is Wave 3.

### Gate M1 (end of Week 4)

The slice is done when it survives **all five** stress conditions on a physical device:
1. Airplane mode
2. Screen lock mid-recording
3. Incoming call mid-recording
4. `adb shell am kill` mid-recording, then recovery on relaunch
5. Cold start with 200 seeded takes

**Failing this in Week 4 is a good outcome. Failing it in Week 9 is not.** Do not open Wave 3 on a slice that passes four of five.

---

# Wave 3 — Weeks 5–8: Parallel feature development

Now the team splits properly. Three streams, no cross-stream dependencies.

## Prompt 3-D1 — Capture flow (Stream A)

> [standing preamble] §7 and Phase 0 §5 govern.
>
> Build: global capture and project capture, recording state machine, pause/resume, save/discard, post-capture flow, recovery UX.
>
> The full recording state set from §17 — Ready · Recording · Paused · Reviewing · Saving · Saved · Discarded · Interrupted · Failed · PermissionDenied · LowStorage — is a **sealed hierarchy, not a set of toasts**. Same for Recovery: None · Prompt(duration) · Restoring · Restored · DiscardConfirm.
>
> Handle the §7.4 interruption matrix completely: focus loss, telephony, Bluetooth connect/disconnect, headphone routing.

## Prompt 3-D2 — Piano and MIDI (Stream A)

> [standing preamble] §8 governs. **Do not start until S-2 and S-3 have both returned go verdicts.**
>
> Build: piano keyboard as a single Compose `Canvas` with raw `pointerInput` multi-touch — **not** one composable per key, which puts 20+ recomposing nodes in the hot path of a musical instrument. Key-down dispatch across JNI with no allocation on the touch path. Sampler voice engine sharing the Oboe stream. MIDI event collector. SMF Type 0 serializer.
>
> Voice+Piano is **one Take** with audio plus an optional MIDI asset — not two captures (Phase 0 §20).
>
> Clock sync follows §8.3 exactly: audio frame clock is master, `AudioTimestamp` anchors, `frameIndexFor(eventTimeNanos)` at ingest, ticks derived from frame index and never from wall time. At 48 kHz / 480 PPQ / 120 BPM, one tick is exactly 50 frames — that integer relationship is deliberate, preserve it. Subtract the S-2 measured output latency so the note lands where the user *heard* it.
>
> Landscape piano experience and performance optimization also sit here.

## Prompt 3-D3 — Songs, takes, library (Stream B)

> [standing preamble] Phase 0 §20–§21 govern behavior.
>
> Build: library, filters, favorites, rename, take detail, song status transitions, loose/unassigned take handling. An unassigned Take stays valid and discoverable without a Song.
>
> States: EmptyLibrary · Populated · SearchResults · Filtered; Takes: Empty · Populated · Playing · Transcoding · Unavailable.
>
> Editing rules: rename and re-organize takes, but **no destructive audio editing in V1**. Deletion of an audio or MIDI asset follows the Take lifecycle and must never orphan a file.

## Prompt 3-D4 — Lyrics and notes (Stream B)

> [standing preamble] Phase 0 governs the lyrics/section and note models.
>
> Build: section editor, notes timeline, `@` references, autosave.
>
> States — Lyrics: Empty · Editing · Autosaved · SectionInserting; Notes: Empty · Composing · MediaAttached · VoiceRecording · Saved · InvalidEmpty.
>
> A Song has one primary LyricsDocument; a LyricsDocument has many Sections; a Note belongs to one Song with optional Section/Take references. Section-to-Take references are **V2** — do not build them.

## Prompt 3-D5 — Platform: search, nudges, export, settings (Stream C)

> [standing preamble] §10, §11, §12, §13, §4.3 govern.
>
> Build:
> 1. **FTS5 search UI** over the tables Dev B built, with `bm25` ranking and `snippet` highlighting. `searchGlobal` and `searchInSong` per §15. Search queries the data model, never scraped UI state.
> 2. **Nudges** on WorkManager. **`AlarmManager` is forbidden** — see the ADR. Deep links into the target. States: Empty · Active · Disabled · TargetUnavailable.
> 3. **Export pipeline**, including MP3 via LAME/NDK (~3–5 dev-days; patents expired, LGPL 2.1 obligations are routine for Android dynamic linking). Encoders stay format-agnostic behind `ExportSourceResolver` — do not let an encoder learn whether the source is M4A or FLAC.
> 4. **Quality toggle and its three disclosure surfaces** (§4.3), in this order: after first save (one dismissible line, shown once, never repeated), first time Export is opened, and permanently in Settings → Storage & Data. It must **not** appear at first launch — an onboarding screen about audio codecs before the user has recorded anything is exactly what "capture first, organize later" exists to prevent.
>
>    **The copy must state explicitly that the setting applies to new recordings only.** This constraint survives every rewrite of the wording. Anything vaguer and users flip it expecting their back catalogue to improve.
> 5. Permissions (Granted · Denied · NeedsRationale · PermanentlyDenied) and storage states (Normal · Low · Critical).
> 6. Play Console obligations: FGS-type declaration with demo video, Data Safety form stating audio never leaves the device, privacy policy consistent with that claim.
>
> **On a four-person team** this stream is split across D1–D4 once their primary streams stabilize. Export and the LAME work should go to whoever finishes first, not be spread thin.

## Prompt 3-lead — Dev A during Wave 3

> Review, integration, architectural integrity. Your job is to catch the feature-to-feature dependency, the raw palette value, the toast standing in for a state, and the silently-adopted alternative — in review, while they are cheap.

---

# Wave 4 — Weeks 8–10: Integration

Parallel development stops paying here. Integration becomes the priority.

- **Lead:** cross-module navigation, unified playback across every surface.
- **Audio:** Voice+Piano sync validation on real hardware, latency profiling, routing.
- **Data/workspace:** loose-take filing, note references, section reorder.
- **Platform:** search filtering, nudge deep links, share sheet.

Test these cross-feature journeys specifically — this is where architectural inconsistency surfaces:

```
Library → Capture → Voice → Record → Pause → Resume → Save → Song → Take → Playback
Song → Lyrics → Chorus → quick capture → Voice → Save → auto-associated with Chorus
Song → Piano → Piano+Voice → Record → Save → ONE Take → Playback
Search → Lyric/Take/Note → open original context
```

**Gate M3 (Week 10):** every Phase 0 V1 feature reachable; all §17 states implemented.

---

# Wave 5 — Weeks 10–12: Hardening

**Stop adding functionality.**

- **Lead:** leaks, ANRs, low-memory-killer survival, R8 rules.
- **Audio:** focus loss, telephony, Bluetooth, 45-minute sessions, across the **Pixel 4a** and **Tecno Spark 8** at minimum, with the **Galaxy A03** for manual by-ear spot checks. Revisit the Xiaomi/HyperOS gap before this gate — hardening is the last reasonable point to acquire that device and get real coverage rather than carry the gap into beta.
- **Data:** v1→v2 migration tests, orphan GC, 1,000+ take performance.
- **UI:** back navigation, rotation, large type, accessibility, small and large screens.

**Orphan cleanup gets specific attention.** It is the worst failure mode in the app, which is why it has three independent guards (§9.3): 24-hour modification-time grace window, `.tmp` presence check, active-session deferral. Prove all three. A cleanup pass that deletes a live recording is unrecoverable and unforgivable.

**Gate M4 (Week 12):** available devices green (Pixel 4a, Tecno Spark 8, plus Galaxy A03 spot checks); Xiaomi/HyperOS status explicitly resolved one way or the other, not silently carried forward; migration tests pass; no leaks or ANRs under profiling; orphan cleanup proven safe.

---

# Wave 6 — Weeks 12–14: Beta

Signed build, crash reporting, Play declarations, external testers recording daily. Not more features.

The questions beta answers are qualitative:
- How quickly can someone start recording?
- Do they understand what happens after saving?
- Can they find an old idea?
- Do they understand Songs?
- Does Piano + Voice make sense?
- **Do they ever lose a recording?**
- Do they understand the difference between Notes and Takes?

**Gate M5:** external testers are recording daily on their own devices.

---

# Definition of Done (applies to every feature, every wave)

A feature is Done when **all** of these hold:

- It satisfies the Phase 0 behavior
- All its §17 states are implemented as real states
- It has unit tests
- It survives rotation, process death and back navigation
- It works in all four themes
- It has been used on a physical low-end device

---

# Two open product questions

§21.3 flags two items as **unresolved product decisions, not engineering decisions**. They are raised through the Phase 0 §26 process and settled by the product owner. If your work runs into one of them, stop and raise it — do not settle it in code, and do not let a default implementation quietly become the decision.
