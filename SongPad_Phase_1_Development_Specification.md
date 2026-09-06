**SongPad V1**
**Phase 1 Development Specification**

*Engineering architecture, technical decisions, contracts, work breakdown and test strategy*
*Version 1.0 — Phase 1 entry document*

Status: **READY TO IMPLEMENT**, subject only to the four Stage 0 spikes in §21, which must return before their dependent code is written.

---

# 0. Document control and provenance

This document is written for a **4–5 developer team** (one acting as technical lead), targeting a **10–14 week V1 beta**, per `Rough_Plan_we_re_using_`.

It consolidates four inputs. Where they disagree, the precedence order in §2 applies.

| Input | Role | Authority |
| --- | --- | --- |
| `SongPad_Phase_0_Closure_Specification` | Product behavior, IA, data model contract, V1/V2 boundary | **Authoritative for product behavior** |
| `Rough_Plan_we_re_using_` | Team shape, timeline, dependency graph, Week 1 lock list | Authoritative for sequencing and team structure |
| Technical Research Pass A (Android platform research, Aug 2026) | Platform constraints, API currency, library versions | Authoritative for platform facts |
| Technical Research Pass B (external engineering architecture doc) | Schema detail, binary formats, contracts, test matrix | Contributory; superseded where it conflicts with A or with directives below |

**Explicit provenance notes — read these before treating any number in this document as sourced:**

1. **Team size (4–5 developers) is user-provided**, from `Rough_Plan_we_re_using_`. An earlier conversation contained a Claude-fabricated figure ("2 Android devs") that was incorrectly reported back as user-sourced. That figure is void and does not appear anywhere in this document.
2. **Research Pass B asserts a 4–5 person team, 44.1 kHz sample rate, FTS4, `SoundPool`, and `Dispatchers.IO` for the capture loop.** The team size agrees with the source file. The other four are **overridden** by explicit direction (§4.1) and are recorded as rejected alternatives, not silently dropped.
3. **Two items below are flagged as unresolved product questions, not engineering decisions**, and are listed in §21.3. They must be raised as product decisions per Phase 0 §26 rather than settled in code.

---

# 1. Purpose and Phase 1 objectives

Phase 0 closed product definition. Phase 1 converts it into a buildable system.

Phase 1 succeeds when:

1. Every cross-team technical decision is locked and written down, so no developer invents an assumption another developer must later unpick.
2. The vertical slice (§16) runs end to end on real hardware by Week 4.
3. The interface contracts in §15 are frozen, allowing five developers to work without blocking on each other's implementations.
4. Each of the four Stage 0 spikes (§21.1) has returned a go/no-go result against a stated threshold, before its dependent architecture is committed.
5. The test matrix (§18) is running in CI from Week 2, not retrofitted in Week 11.

Phase 1 explicitly does **not** aim to deliver features. Features are Phase 3 (Weeks 5–8).

---

# 2. Source of truth and precedence

```
Phase 0 Closure Specification        →  product behavior, user-facing states, V1/V2 boundary
        ↓ (defers to)
This document                        →  architecture, libraries, schema, contracts, sequencing
        ↓ (defers to)
Approved design system / prototype   →  visual and interaction detail
        ↓ (defers to)
Developer judgement                  →  implementation detail inside a locked contract
```

Rules:

- A change to a **LOCKED** Phase 0 product behavior is a product decision. It is raised, not implemented.
- A change to a **LOCKED** technical decision in §4 is an ADR. It requires the technical lead's sign-off and an amendment to this document.
- A change inside a contract in §15 (e.g. how `AudioRecorderEngine` buffers internally) is a developer decision requiring no approval.
- Phase 0 marked five things as **ENGINEERING DECISION**: library choices, exact schema, audio codec/file strategy, Android scheduler, implementation modules. This document resolves all five.

---

# 3. Phase 1 scope

| In scope for Phase 1 | Out of scope for Phase 1 |
| --- | --- |
| Module skeleton, DI graph, CI, build variants | Polished feature screens |
| Room schema, DAOs, migrations, migration tests | Search UI, nudge copy, export UI |
| Audio capture engine + foreground service + recovery | Piano polish, landscape piano |
| Playback engine | Notes `@` reference resolution |
| Design system tokens and core components | Settings screens beyond the quality toggle stub |
| Vertical slice (§16) | Anything in the Phase 0 V2 column |
| Stage 0 spikes (§21.1) | |

---

# 4. Technical decisions (locked)

## 4.1 Decision register

Every row is **LOCKED** unless marked otherwise. "Rejected" records what was considered and why it lost, so the decision is not relitigated in a code review.

| Area | Decision | Rationale | Rejected |
| --- | --- | --- | --- |
| Language | Kotlin 2.x, coroutines/Flow | Structured concurrency; native Compose integration | Java/RxJava — boilerplate, no Compose compiler benefit |
| UI | Jetpack Compose, Material 3 base, custom Canvas for waveform and keyboard | Declarative state binding; direct Canvas control | XML Views — imperative state sync, slow theme switching |
| Navigation | **Navigation 3** (androidx.navigation3, stable since Nov 2025; 1.1.x line) | Developer-owned back stack held in Compose state; fits a ViewModel-centric local-first app; avoids a later Nav2→Nav3 migration | Navigation Compose (Nav2) — supported, but starting new in 2026 buys a migration. **Fallback if spike S-5 fails.** |
| DI | **Hilt** (KSP, not KAPT) | Compile-time graph validation; first-class ViewModel/Worker/Service scoping; the team is Android-only | Koin — weaker compile-time safety; Metro — KMP-oriented, no benefit here; manual DI — does not scale to 5 devs |
| Database | **Room 2.7+ with `BundledSQLiteDriver`** | Ships a known SQLite build inside the APK, removing OEM build variance and guaranteeing FTS5 availability | SQLDelight — SQL-first/KMP oriented; raw SQLite — no compile-time checking or reactive queries; Realm/ObjectBox — proprietary binaries, threading constraints |
| Primary keys | **UUIDv7 stored as `TEXT`** | Globally unique (sync-ready) and time-ordered, so B-tree inserts stay near-sequential | UUIDv4 — random inserts fragment the B-tree; `INTEGER` autoincrement — fastest but not sync-mergeable |
| Search | **FTS5**, external-content table, `unicode61` tokenizer, `bm25()` ranking, prefix indexes | Prefix indexing for search-as-you-type; ranking and `snippet()` support | **FTS4 — rejected.** Research Pass B specified FTS4; it is obsolete, has no `bm25()`, and weaker prefix support |
| Audio capture API | **Oboe (C++/NDK)** for the capture stream, wrapped by a thin Kotlin facade | One engine for capture and synthesis; deterministic buffer handling; native thread priority | `MediaRecorder` — black-box, unrecoverable container corruption on process death; `AudioRecord` (Java) — workable but splits the audio stack across two languages when Oboe is already required for the piano |
| Sample rate / format | **48 kHz, 16-bit, mono, linear PCM** | Device-native rate on virtually all Android hardware, so no resampling in the HAL; 96 kB/s = 5.76 MB/min | **44.1 kHz — rejected.** Research Pass B specified it; it forces a HAL resample on most devices and gains nothing for a capture tool |
| Capture thread | **Dedicated thread**, `Process.setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)`, or Oboe's own callback thread | The dispatcher must never be shared or elastic | **`Dispatchers.IO` — forbidden for the capture loop.** Shared, elastic, starvation-prone. Research Pass B specified it; overridden |
| On-disk capture format | Headerless raw PCM (`.pcm`) during capture | A truncated headerless file is still fully decodable to its last synced byte | Writing a container live — header fields cannot be finalized on abrupt kill |
| Stored audio asset | **Two modes, user-selectable in Settings (§4.3).** Standard (default): AAC-LC in M4A, 128 kbps mono, ~0.96 MB/min. Lossless: **FLAC**, ~2.9 MB/min | Standard keeps a scratchpad library cheap; Lossless removes lossy generations from the export chain entirely | Storing MP3 — pays encode loss on every take including the ~95 % never exported, worse than AAC at equal bitrate, and software-only encoding taxes every save. Retaining raw PCM as the "original" — 5.76 MB/min, twice FLAC for no benefit |
| Playback | **Media3 / ExoPlayer** | Accurate seeking, audio focus handling, MediaSession, plays M4A, FLAC and raw PCM | `MediaPlayer` — non-deterministic state machine, poor error propagation; `AudioTrack` — reserved for the piano engine |
| Transcode | `MediaCodec` (AAC-LC **or** FLAC encoder) + `MediaMuxer`, run in a `WorkManager` `CoroutineWorker` | Both encoders are on the same MediaCodec path and are usually hardware-backed, so a 5-minute take transcodes in seconds with negligible battery | Doing it inline on save — blocks the user and dies with the process |
| MP3 export encoder | **LAME via NDK**, export path only | Android ships an MP3 *decoder* but has never shipped an *encoder*; MediaCodec offers no route. MP3 patents expired in 2017, so there is no royalty question | Shine — smaller but audibly worse; omitting MP3 — fails Phase 0 §13 |
| Piano engine | **Oboe (C++/NDK)** polyphonic sampler, mono samples, 3–4 velocity layers, ~15–25 MB | Only path to musically usable touch-to-tone latency | **`SoundPool` — rejected.** Research Pass B specified it; insufficient control over voice management, latency and velocity layering |
| MIDI | Internal event model → **SMF Type 0**, 480 PPQ, written directly as bytes | No external MIDI hardware in V1; SMF writing is ~200 lines and license-clean | `android.media.midi` — solves external/virtual MIDI hardware, which V1 does not have |
| Background scheduling | **`WorkManager` only** | Doze-aware, reboot-persistent, battery-constraint aware | **`AlarmManager` — forbidden.** See §11.1 |
| Foreground service | `microphone`-typed FGS, started only from a visible screen | Mandatory since Android 14; required for capture to survive backgrounding | Background thread without FGS — killed by LMK; `dataSync` type — wrong type, subject to timeout |
| Storage | App-specific storage, no storage permission | Scoped-storage exempt; removed on uninstall | Shared/`MediaStore` — permission cost and no benefit |
| Export | SAF `ACTION_CREATE_DOCUMENT` to save, FileProvider + `ACTION_SEND` to share | Two distinct user intents; both are required | `file://` URIs — throws `FileUriExposedException` since Android 7 |
| Cloud backup | Audio and MIDI **excluded** from Auto Backup | Auto Backup is capped at 25 MB per app; audio blows past it immediately | Backing up everything — silently exceeds quota and backs up nothing |
| Architecture | Layered UDF: Compose UI → ViewModel (`StateFlow`) → use case → repository → data source | Google-standard, testable, matches the team's parallel split | MVI with a formal intent-reducer everywhere — added ceremony; use MVI only for the capture screen (§7.2) |

## 4.2 Notes on two decisions that deserve elaboration

**FTS5 and the bundled driver.** The direction to use FTS5 is correct and adopted. One qualification, flagged rather than assumed: the claim that Android 11+ (API 30+) *natively* ships SQLite with FTS5 is broadly true but not a guarantee — FTS5 is a compile-time option, and the platform SQLite build has historically varied across OEM images. Because `BundledSQLiteDriver` is already the chosen driver for version-consistency reasons, this is moot: FTS5 is guaranteed by the bundled build regardless of what the device ships. **Spike S-4 confirms this empirically.** Do not rely on platform SQLite for FTS5.

**Oboe for capture as well as playback.** The direction locks Oboe for the keyboard. This spec extends Oboe to the *capture* path too. The reason is that a mixed stack — `AudioRecord` in Kotlin for capture, Oboe in C++ for synthesis — means two clocks, two threading models, and a JNI boundary sitting exactly where the Voice+Piano synchronization problem lives (§8.3). One native audio module removes that seam. If spike S-1 shows Oboe input is less reliable than `AudioRecord` on low-end Transsion hardware, `AudioRecord` on a dedicated `URGENT_AUDIO` thread is the documented fallback, and §8.3's clock strategy still holds.

## 4.3 Recording quality modes

Capture is always 48 kHz · 16-bit · mono raw PCM (§7). The mode decides only what the PCM is transcoded *into* after a clean stop.

| Mode | Stored format | Size | WAV export | MP3 export |
| --- | --- | --- | --- | --- |
| **Standard** (default) | AAC-LC in M4A, 128 kbps | ~0.96 MB/min | Decoded from AAC — labelled "uncompressed, for DAWs", never "lossless" | One lossy generation |
| **Lossless** | FLAC (MediaCodec encoder, API 21+) | ~2.9 MB/min | Bit-exact | Single generation from a lossless source |

Design consequences:

- **Nothing is ever stored twice.** Lossless mode stores FLAC *instead of* M4A, not alongside it. Raw PCM is deleted after a verified transcode in both modes.
- **Mode is a per-take property, not a global one.** `AudioAssetEntity.mime_type` already carries it (§6.1), so the schema needs no change and a library can contain both formats.
- **The setting applies to new recordings only.** It cannot be applied retroactively, which is what drives the disclosure requirement in §4.4.
- Standard is the default because SongPad is a scratchpad: most takes are short fragments that never become songs, and tripling storage for all of them to protect the few is the wrong default on a budget device.
- 128 kbps rather than 96 gives the Standard→MP3 path enough headroom that the second generation is not obviously degraded.

`ExportSourceResolver` (§12.5) reads `mime_type` and hands every encoder the best available source. No encoder branches on mode.

## 4.4 Disclosure of the quality mode

The mode cannot be applied retroactively. A user who discovers it in month three has a library that cannot be upgraded. That asymmetry — not ordinary settings discoverability — is what justifies surfacing it early.

It must **not** appear at first launch. An onboarding screen about audio codecs before the user has recorded anything is exactly what "capture first, organize later" exists to prevent.

Three surfaces, in order of appearance:

1. **After the first save.** Phase 0 §5 already returns the user to the library with the new Take and unobtrusive organize actions. One dismissible line sits under that first Take: *"Recording in Standard quality. Lossless uses ~3× the space and exports without quality loss. Change →"*. Shown once, dismissible, never repeated.
2. **First time Export is opened.** A contextual note stating which format the take is stored in and what that means for the chosen export format. Relevant even if late.
3. **Settings → Storage & Data.** The permanent home. Per-minute cost stated inline, and the copy must state explicitly that the setting **applies to new recordings only**. Anything vaguer and users will flip it expecting their back catalogue to improve.

---

# 5. Module architecture

```
                                    :app
                    (Application, DI root, NavDisplay host)
                                      │
        ┌─────────────┬───────────────┼───────────────┬─────────────┐
        ▼             ▼               ▼               ▼             ▼
 :feature:capture :feature:song  :feature:search :feature:nudges :feature:export
        │             │               │               │             │
        └─────────────┴───────┬───────┴───────────────┴─────────────┘
                              ▼
        ┌──────────┬──────────┼──────────┬──────────┐
        ▼          ▼          ▼          ▼          ▼
   :core:audio :core:midi :core:data :core:ui  :core:model
    (NDK/Oboe)             (Room,FTS5)          (pure Kotlin)
```

| Module | Owns | Depends on | Owner (Phase 1) |
| --- | --- | --- | --- |
| `:core:model` | Domain entities, value types, validation rules. **Pure Kotlin, zero Android deps.** | — | Lead |
| `:core:data` | Room schema, DAOs, FTS5 tables and triggers, migrations, repositories, file store | `:core:model` | Dev B |
| `:core:audio` | Oboe capture + playback engines, C++ ring buffer, waveform extraction, audio focus, FGS | `:core:model` | Dev C |
| `:core:midi` | Sampler voice engine (shares the Oboe stream), MIDI event collector, SMF Type 0 serializer | `:core:model`, `:core:audio` | Dev C / Dev 2 |
| `:core:ui` | Semantic tokens, four themes, typography, spacing, glass-pill, waveform Canvas, shared components | — | Dev D |
| `:feature:*` | ViewModels, screens, navigation entries for one journey each | `:core:*` | Devs 1–5 |

Rules:
- Feature modules **never** depend on each other. Cross-feature navigation goes through route keys declared in `:app`.
- `:core:model` has no Android dependency, so domain logic is JVM-unit-testable with no Robolectric.
- `:core:audio` is the only module with NDK/CMake. Nothing else touches JNI.

---

# 6. Data architecture

## 6.1 Schema

All primary keys are **UUIDv7 strings**. All timestamps are epoch milliseconds (`INTEGER`). Every user-data table carries the sync block described in §6.3.

```
  ┌──────────────────────────────────────────────────────────┐
  │                          SONG                            │
  │  id · name · status · is_favorite · sync block           │
  └──┬─────────────────────┬──────────────────────┬──────────┘
     │ 1                   │ 1                    │ 1
     │ 0..*                │ 0..1                 │ 0..*
  ┌──▼─────────────┐  ┌────▼──────────────┐  ┌────▼──────────────┐
  │      TAKE      │  │ LYRICS_DOCUMENT   │  │       NOTE        │
  │ id             │  │ id                │  │ id                │
  │ song_id (NULL) │  │ song_id (UNIQUE)  │  │ song_id           │
  │ type · status  │  │ sync block        │  │ body_text         │
  │ duration_ms    │  └────┬──────────────┘  │ voice_take_id?    │
  │ sync block     │       │ 1               │ media_uri?        │
  └──┬──────┬──────┘       │ 0..*            └───────────────────┘
     │ 1    │ 0..1    ┌────▼──────────────┐
     │      │         │      SECTION      │      ┌──────────────┐
  ┌──▼───┐ ┌▼──────┐  │ id · document_id  │      │    NUDGE     │
  │AUDIO │ │ MIDI  │  │ tag · sort_order  │      │ id           │
  │ASSET │ │ ASSET │  │ body_text         │      │ song_id?     │
  └──────┘ └───────┘  └───────────────────┘      └──────────────┘
```

| Entity | Key columns | FK / cascade | Notes |
| --- | --- | --- | --- |
| `SongEntity` | `id`, `name`, `status` (`ongoing`\|`finished`\|`archived`), `is_favorite` | root | Deleting cascades to lyrics, sections, notes, takes |
| `TakeEntity` | `id`, `song_id` **nullable**, `name`, `type` (`voice`\|`piano`\|`voice_piano`), `status`, `duration_ms`, `audio_asset_id`, `midi_asset_id?` | `song_id → SongEntity.id ON DELETE CASCADE` | `song_id = NULL` is the first-class independent Take (Phase 0 §4) |
| `AudioAssetEntity` | `id`, `take_id`, `relative_path`, `mime_type`, `sample_rate`, `bit_depth`, `byte_size`, `peaks_path` | `take_id → TakeEntity.id ON DELETE CASCADE` | |
| `MidiAssetEntity` | `id`, `take_id`, `relative_path`, `ppq`, `byte_size` | `take_id → TakeEntity.id ON DELETE CASCADE` | Only for `piano` / `voice_piano` takes |
| `LyricsDocumentEntity` | `id`, `song_id` **UNIQUE** | `ON DELETE CASCADE` | Exactly one per Song |
| `SectionEntity` | `id`, `document_id`, `tag`, `body_text`, `sort_order` | `ON DELETE CASCADE` | `body_text` may be empty (Phase 0 §8) |
| `NoteEntity` | `id`, `song_id`, `body_text`, `voice_take_id?`, `media_uri?` | `song_id ON DELETE CASCADE`; `voice_take_id ON DELETE SET NULL` | Empty notes are rejected in the domain layer, not the DB |
| `NudgeEntity` | `id`, `target_song_id?`, `title`, `body`, `is_read` | `ON DELETE SET NULL` | In-app history; nudge survives target deletion as a read item |

Indexes: `take(song_id)`, `take(status)`, `note(song_id)`, `section(document_id, sort_order)`, and on every table `(updated_at)` and `(sync_state)`.

Do **not** use `WITHOUT ROWID` — the FTS5 external-content joins and secondary indexes need a rowid.

## 6.2 Take lifecycle status

`TakeEntity.status` is separate from `sync_state` and drives recovery:

```
RECORDING ──clean stop──► TRANSCODING ──worker success──► READY
    │                          │
    │ process death            │ worker exhausted retries
    ▼                          ▼
ORPHANED ──user recovers──► TRANSCODING          FAILED
    │
    └── user discards ──► hard delete (row + files)
```

A row in `RECORDING` at cold start is by definition orphaned (§9.4). Nothing else may write that status.

## 6.3 Sync-ready block

Every user-data table carries these five columns from v1 of the schema. Collaboration and cloud sync are out of V1 scope (Phase 0 §22), but retrofitting these columns into a populated database later is the single most expensive migration this project could face.

| Column | Type | Purpose |
| --- | --- | --- |
| `created_at` | INTEGER | Epoch ms, set once |
| `updated_at` | INTEGER | Epoch ms, written on **every** mutation. Indexed |
| `deleted` | INTEGER (0/1) | Soft delete. All read queries filter `deleted = 0` |
| `deleted_at` | INTEGER? | When the soft delete happened; drives the purge window |
| `sync_state` | TEXT | `LOCAL_ONLY` \| `PENDING_UPLOAD` \| `SYNCED` \| `PENDING_DELETE`. In V1 everything is `LOCAL_ONLY` |
| `revision` | INTEGER | Monotonic per-row counter, incremented on every mutation |

In V1, `deleted = 1` is written by the repository, and a purge worker hard-deletes rows and their files after the retention window (§9.3). The UI never sees soft-deleted rows.

**This is the highest-consequence decision in the schema.** It costs almost nothing now and is close to unaffordable later.

## 6.4 Migrations

- Export schema JSON to VCS from commit one (`room.schemaLocation`).
- Auto-migrations for mechanical changes; hand-written `Migration` for anything with a data transform.
- Every migration path ships with a `MigrationTestHelper` instrumented test: create at vN, migrate, assert schema **and** assert data survived. A migration without a test does not merge.
- FTS5 virtual tables and their triggers are declared in hand-written DDL inside migrations — Room's `@Fts4` annotation is not used, and FTS4 is not used at all.

---

# 7. Audio capture architecture

Audio capture is the highest technical risk in the project. Everything here is written to satisfy one invariant:

> **A user must never lose audio they believe they recorded — including when the OS kills the process without warning.**

## 7.1 Capture pipeline

```
 [ Record FAB (visible screen) ]
             │
             ▼
 ┌───────────────────────────┐   denied    ┌──────────────────────────┐
 │ RECORD_AUDIO granted?     ├────────────►│ Rationale → Settings     │
 └───────────┬───────────────┘             └──────────────────────────┘
             │ granted
             ▼
 ┌───────────────────────────┐   not granted   ┌──────────────────────┐
 │ Request AudioFocus        ├────────────────►│ Surface error, abort │
 └───────────┬───────────────┘                 └──────────────────────┘
             │ granted
             ▼
 ┌───────────────────────────────────────────────────────────────────┐
 │ startForeground(microphone)  ← MUST originate from a visible UI   │
 └───────────┬───────────────────────────────────────────────────────┘
             ▼
 ┌───────────────────────────────────────────────────────────────────┐
 │ Oboe input stream: 48 kHz · mono · I16 · low-latency               │
 │ Native callback thread (URGENT_AUDIO priority)                     │
 └───────────┬───────────────────────────────┬───────────────────────┘
             │                               │
             ▼                               ▼
 ┌───────────────────────┐         ┌─────────────────────────────────┐
 │ Lock-free ring buffer │         │ Peak/RMS reducer (per block)    │
 │        ↓              │         │        ↓                        │
 │ Writer thread         │         │ SharedFlow<Float> @ 10 Hz       │
 │  append → take.pcm    │         │        ↓                        │
 │  flush+fsync @ ~1 s   │         │ Compose Canvas waveform         │
 └───────────┬───────────┘         └─────────────────────────────────┘
             │ user taps Save
             ▼
 ┌───────────────────────────────────────────────────────────────────┐
 │ Finalize: close stream → rename .pcm.tmp → .pcm → DB row          │
 │           → enqueue TranscodeWorker → status = TRANSCODING        │
 └───────────────────────────────────────────────────────────────────┘
```

## 7.2 Threading — normative

This section is normative because it is the most commonly violated part of an Android audio pipeline.

1. **The capture read loop MUST NOT run on `Dispatchers.IO`, `Dispatchers.Default`, or any shared dispatcher.** These pools are elastic and shared with unrelated I/O; under load the capture loop is starved and drops samples. Research Pass B specified `Dispatchers.IO` for this loop; that is overridden.
2. The capture loop runs either on **Oboe's own high-priority callback thread** (preferred) or, in the `AudioRecord` fallback, on a **dedicated `Thread`** whose first statement is `android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)`.
3. The capture thread **must not allocate, block, lock, log, or touch JNI object creation**. It copies bytes into a preallocated lock-free ring buffer and returns.
4. A separate **writer thread** (`THREAD_PRIORITY_AUDIO`, one level below) drains the ring buffer to disk. Disk I/O never happens on the capture thread.
5. Waveform reduction (peak/RMS per block) happens on the writer thread, not the capture thread, and is published to Kotlin as a `SharedFlow<Float>` at **10 Hz** with `extraBufferCapacity` and `BufferOverflow.DROP_OLDEST` so a slow UI can never back-pressure audio.
6. Ring buffer capacity is sized for **≥ 2 seconds** of audio, so a transient writer stall (page cache flush, GC pause) cannot drop samples.

The capture screen is the one place in the app that uses a strict **MVI** loop — a single `CaptureIntent` sealed interface and one `CaptureState` — because the recording state machine has enough legal transitions that ad-hoc `StateFlow` mutation from several call sites will produce bugs.

## 7.3 Crash-resilient file strategy

- Capture writes **headerless raw PCM** to `takes/{takeId}/audio.pcm.tmp`.
- `BufferedOutputStream.flush()` followed by `FileDescriptor.sync()` runs **approximately every 1 second of audio**, not on every buffer. Every-buffer fsync destroys battery and throughput; never fsyncing loses up to the page-cache window.
- Because the file has no header, a truncated file is not corrupt — it is a shorter valid recording. The recovery path (§9.4) synthesizes the container from known parameters, since sample rate, channel count and bit depth are fixed constants, not per-file metadata.
- On a clean stop, the file is `rename()`d from `.pcm.tmp` to `.pcm`. `rename()` within the same filesystem is atomic; the presence of `audio.pcm` is therefore the commit marker.

Spike **S-1** validates this end to end.

## 7.4 Interruption matrix

| Trigger | System event | Engine action | UI state | Recovery |
| --- | --- | --- | --- | --- |
| Incoming call / VoIP | `AUDIOFOCUS_LOSS_TRANSIENT` | Stop reads, drain ring buffer, flush + fsync, hold file handle | Recording → Paused | On `AUDIOFOCUS_GAIN`, **stay paused**; show Resume. Never auto-resume — silent auto-resume records audio the user did not intend |
| Permanent focus loss | `AUDIOFOCUS_LOSS` | Stop, flush, finalize as a saved take | Recording → Saving → Saved | Take is committed, not discarded |
| Headphone unplugged | `ACTION_AUDIO_BECOMING_NOISY` | Pause immediately | Recording → Paused | Prevents mic/speaker feedback. User resumes explicitly |
| Bluetooth disconnect | `AudioDeviceCallback.onAudioDevicesRemoved` | Close stream, rebind to built-in mic, resize buffers | Recording → Paused | Explicit resume; buffer size recomputed for the new route |
| Notification ping | `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` | **Ignore** — continue capturing at full gain | Recording (unchanged) | Ducking a *recording* would gate the user's own performance |
| Low storage | `< 100 MB` free | Stop read loop, flush, finalize what exists | Recording → Saving → Storage warning | Block new recordings until space is freed. **100 MB ≈ 17 min of headroom at 5.76 MB/min** |
| Screen lock / background | — | Continue; FGS keeps the process alive | Recording (unchanged) | Notification shows elapsed time |
| Process death | LMK / OEM kill | Nothing — bytes are already on disk | App terminated | Cold start finds `RECORDING` row + `.pcm.tmp` → recovery prompt (§9.4) |

Route changes use `AudioDeviceCallback` and, from Android 13, `AudioManager.setCommunicationDevice()`. `startBluetoothSco()` is deprecated and must not be used.

## 7.5 Foreground service constraints

The `microphone` FGS is not optional and is more constrained than older Android guidance suggests:

- Targeting Android 14+, the service **must** declare `android:foregroundServiceType="microphone"` and hold `FOREGROUND_SERVICE_MICROPHONE` plus `RECORD_AUDIO`. Starting it without `RECORD_AUDIO` throws `SecurityException`.
- `RECORD_AUDIO` is a while-in-use permission: the microphone FGS **cannot be started while the app is in the background**. Every recording therefore begins from a visible screen. This is compatible with the Phase 0 capture model (both entry points are user taps on a visible Record control) but it forecloses any future "start recording from a widget/notification" feature without a product-level rethink.
- Android's ongoing background-audio hardening (through Android 17) continues in this direction. The design assumption is: **capture is always user-initiated from a visible surface.**
- Play Console requires a foreground-service-type declaration for `microphone`: use-case description, user impact if interrupted, and a demo video. Dev 5 owns this; it must be prepared before the beta submission in Week 12, not on the day of submission.

## 7.6 Playback

Media3/ExoPlayer, one shared player instance owned by a `PlaybackController` in `:core:audio`, exposed as a singleton through Hilt. One instance app-wide prevents two surfaces (review player, takes ribbon) fighting over audio focus.

- Plays the stored `.m4a`; also plays raw `.pcm` (via the raw/PCM extractor with the fixed format) so a recovered-but-not-yet-transcoded take is immediately playable.
- Scrub events map to `seekTo(positionMs)` against the precomputed peaks file, so the waveform and the transport agree.
- The reviewing-before-save case plays the in-progress `.pcm.tmp` directly.

---

# 8. Piano and MIDI

## 8.1 Sampler engine

- Oboe output stream, 48 kHz mono, exclusive sharing mode, buffer sized to ~2 bursts.
- Voice pool of 32, with oldest-then-quietest voice stealing.
- Samples: ~15–25 MB mono, 3–4 velocity layers, shipped compressed (OGG/FLAC) and **decoded once to PCM in memory at load**, not decoded per note. Decoding per note adds unpredictable jitter to exactly the path that must be deterministic.
- Loading is asynchronous and begins when the capture screen opens, not when Piano mode is toggled, so the first key press is never gated on I/O.
- In Voice+Piano mode the sampler output goes to the speaker and is picked up acoustically by the microphone — that is the Phase 0 model (the mic recording is the audible performance capture). The sampler stream is **not** mixed into the capture file.

## 8.2 Keyboard UI

Custom Compose `Canvas` with raw `pointerInput` handling, **not** one composable per key. Per-key composables put 20+ recomposing nodes in the hot path of a musical instrument. Multi-touch pointer tracking supports glissando and polyphonic chords. Key-down events are dispatched across JNI with no allocation on the touch path.

## 8.3 Clock synchronization — normative

This resolves the question directly: **the audio hardware frame clock is the master timeline. `SystemClock.elapsedRealtimeNanos` is used only as a capture-time stamp that is then converted into audio frames.**

Neither clock alone is correct:

- The MIDI event *originates* on the UI thread, where the only available timestamp is `elapsedRealtimeNanos` (from `MotionEvent.getEventTimeNanos()`).
- The audio timeline is defined by the audio DMA clock, which **drifts relative to the system monotonic clock** — typically tens of ppm, which over a 5-minute take is tens of milliseconds. Anchoring MIDI to `t0` on the system clock and counting forward, as Research Pass B proposes, accumulates exactly this drift.

The correct construction:

1. The capture engine maintains a running `framesWritten` counter — the ground truth for "how much audio exists".
2. Periodically (~1 Hz) the engine samples an `AudioTimestamp` (`AudioRecord.getTimestamp()` / Oboe's `getTimestamp()`), giving a pair `(framePosition, presentationTimeNanos)` that ties the audio DMA clock to the monotonic clock.
3. The engine maintains a linear mapping from these anchor pairs — a slope (frames per nanosecond, which is *not* exactly 48000/1e9) and an offset — refreshed as new anchors arrive.
4. A key event arriving with `eventTimeNanos` is converted at ingest: `frameIndex = round(slope × (eventTimeNanos − anchorNanos) + anchorFrame)`.
5. MIDI ticks are derived from `frameIndex`, never from wall time.

At 48 kHz, 480 PPQ and the default 120 BPM tempo, a quarter note is 24,000 frames and **one tick is exactly 50 frames** (≈1.042 ms). The integer relationship is deliberate: it makes the frame→tick conversion exact and removes a rounding-drift source.

```
  frames_per_tick = (60 / BPM) × f_s / PPQ = (60/120) × 48000 / 480 = 50
  tick = frameIndex / 50
```

If the sampler's output latency is measured in spike S-2, subtract it from the note's frame index so the MIDI note lands where the user *heard* it, not where the touch was registered.

**Spike S-3 must validate this before the piano work in Weeks 5–8 begins.** Acceptance: over a 5-minute take, a metronomic performance shows < 10 ms cumulative divergence between MIDI note positions and the audio transient positions. A MIDI sidecar that drifts is not "slightly wrong" — it is musically unusable, which would silently void the whole Phase 0 MIDI-retention decision.

## 8.4 SMF Type 0 serializer

Hand-written byte serializer in `:core:midi`. No external dependency; if one is preferred, `ktmidi` (MIT) is the only approved option.

| Offset | Bytes | Meaning |
| --- | --- | --- |
| 0x00 | `4D 54 68 64` | `MThd` |
| 0x04 | `00 00 00 06` | Header length |
| 0x08 | `00 00` | Format 0 |
| 0x0A | `00 01` | 1 track |
| 0x0C | `01 E0` | 480 PPQ |
| 0x0E | `4D 54 72 6B` | `MTrk` |
| 0x12 | 4 bytes BE | Track length |
| 0x16 | `00 FF 51 03 07 A1 20` | Tempo, 500,000 µs/quarter = 120 BPM |
| … | `[VLQ] 90 kk vv` | Note On, channel 0 |
| … | `[VLQ] 80 kk 00` | Note Off, channel 0 |
| end | `00 FF 2F 00` | End of Track |

Delta times use variable-length quantity encoding: 7 data bits per byte, bit 7 set on all but the terminal byte.

```
  1060 = 0b100_0100100
  split → [0001000][0100100] = 0x08, 0x24
  VLQ   → 0x88 0x24
```

The serializer is byte-exact-testable on the JVM with no device, which makes it the cheapest high-confidence test in the project (§18).

## 8.5 MIDI crash safety

MIDI events are buffered in memory **and** appended to `takes/{takeId}/events.log` (a trivial fixed-width binary record: frameIndex, note, velocity, on/off) on the same ~1 s cadence as the PCM fsync. On a clean stop the log is converted to `performance.mid` and deleted. On recovery, a surviving `events.log` is converted to a MIDI file covering exactly the frames that the recovered PCM contains — audio and MIDI are truncated to the same point by construction, because both are indexed by frame.

---

# 9. Storage and file lifecycle

## 9.1 Layout

```
/data/user/0/com.songpad.app/
 ├── databases/
 │   ├── songpad.db  ·  songpad.db-wal  ·  songpad.db-shm
 └── files/
     ├── takes/{takeUuid}/
     │     ├── audio.pcm.tmp      ← in-flight capture (never an orphan while fresh)
     │     ├── audio.pcm          ← committed raw capture
     │     ├── audio.m4a          ← stored asset, Standard mode
     │     ├── audio.flac         ← stored asset, Lossless mode (never both)
     │     ├── peaks.wave         ← precomputed waveform
     │     ├── events.log         ← in-flight MIDI (transient)
     │     └── performance.mid    ← MIDI sidecar
     ├── notes/{noteUuid}/image.jpg
     └── tmp/                     ← export staging, transcode scratch
```

Files are addressed **by the same UUIDv7 as the database row**. Paths are derived by a single shared `TakePaths` helper in `:core:model`; absolute paths are never stored in the database. Only `relative_path` is persisted, and only where a file could deviate from the derived name.

## 9.2 Deletion, two-phase

```
User deletes Take
      │
      ▼
1. Domain: close playback file descriptors for that take
2. DB transaction: deleted = 1, deleted_at = now, revision++
      │  (UI updates immediately — row is now invisible)
      ▼
3. PurgeWorker (daily): rows with deleted = 1 AND deleted_at < now − 7 days
      │     → delete files → hard-delete rows
      ▼
4. OrphanCleanupWorker (daily): reconcile disk against DB
```

Soft delete is what makes the schema sync-ready; hard deletion happens on a schedule, not on the user's tap. The 7-day retention window is an engineering default and is **not** exposed as an undo feature in V1 — Phase 0 specifies confirmation dialogs, not a trash can.

## 9.3 Orphan cleanup — normative safety rule

`OrphanCleanupWorker` (WorkManager, daily, `requiresBatteryNotLow` + `requiresDeviceIdle` where available) lists `files/takes/` and `files/notes/` and compares against valid `AudioAssetEntity` / `MidiAssetEntity` / `NoteEntity` rows.

**A file may only be collected if ALL of the following hold:**

1. It has no referencing row in the database (including soft-deleted rows still inside their retention window — those are *not* orphans), **and**
2. Its **last-modified timestamp is older than 24 hours**, **and**
3. Its take directory contains no `.pcm.tmp` and no `events.log`, **and**
4. No capture session is currently active (the worker checks a process-level recording flag and, if set, defers the run entirely).

Rule 2 is the critical one. A user recording a 40-minute idea has a `audio.pcm.tmp` on disk with **no database row yet** — it is indistinguishable from an orphan by reference-counting alone. Without the 24-hour modification-time grace window, a cleanup job firing mid-session would delete the user's live recording. Rules 3 and 4 are belt-and-braces on top of it.

The worker logs what it would delete before deleting, at debug level, for the whole of Phase 1–5. A cleanup bug that silently eats takes is the single worst possible failure mode in this app.

## 9.4 Recovery on cold start

`AudioRecoveryManager` runs once on app start, off the main thread:

1. Query takes with `status = RECORDING`. Each is orphaned by definition — nothing writes that status except an in-flight capture.
2. Also scan `files/takes/*/audio.pcm.tmp` for directories with no database row at all (a kill between file creation and the first DB write).
3. For each, compute duration from file length: `durationMs = byteLength / 96 ` (96 bytes per ms at 48 kHz · 16-bit · mono). No header parsing, because there is no header.
4. Discard candidates under ~1 second as noise.
5. Present a single non-blocking prompt: *"We recovered an unfinished recording (2:41). Keep it?"* Keep → rename `.tmp` → `.pcm`, write/repair rows, enqueue transcode. Discard → delete the directory.
6. Convert any surviving `events.log` to `performance.mid` truncated to the same frame count.

## 9.5 Backup

`android:dataExtractionRules` excludes `files/takes/` and `files/tmp/` from `<cloud-backup>`. Auto Backup is capped at 25 MB per app; a single 5-minute take exceeds it, and exceeding the quota means **nothing** gets backed up, not just the audio. The database and small text are backed up. Device-to-device transfer has no such cap and may include audio.

Settings → Storage & Data must state plainly that recordings are stored on the device only and are removed if the app is uninstalled.

---

# 10. Search

## 10.1 Engine

**FTS5**, external-content table, over the entities Phase 0 §11 requires: Song names, Lyrics (Sections), Notes, Take names, Sections.

```sql
CREATE VIRTUAL TABLE search_index USING fts5(
    search_title,
    search_content,
    entity_id      UNINDEXED,
    song_id        UNINDEXED,
    entity_type    UNINDEXED,
    tokenize = "unicode61 remove_diacritics 2",
    prefix = '2 3 4'
);
```

- `unicode61` with `remove_diacritics 2` handles non-Latin and accented input, which matters for a songwriting app used in any language.
- `prefix = '2 3 4'` builds prefix indexes so search-as-you-type is an index lookup rather than a scan. This is the concrete reason FTS5 is required and FTS4 is not adequate.
- Ranking uses `bm25()` with column weights favouring `search_title`. FTS4 has no `bm25()`.
- `snippet()` produces the highlighted match fragment the grouped results UI needs.

Index maintenance is by **SQLite triggers** on insert/update/delete of `SongEntity`, `SectionEntity`, `NoteEntity`, `TakeEntity`, declared in hand-written migration DDL. Triggers must also respond to `deleted` flipping to 1, so soft-deleted content leaves the index.

Query shape:

```sql
SELECT entity_id, song_id, entity_type,
       snippet(search_index, 1, '<b>', '</b>', '…', 24) AS snippet_text,
       bm25(search_index, 4.0, 1.0) AS rank
FROM search_index
WHERE search_index MATCH :ftsQuery
ORDER BY rank
LIMIT 50;
```

The user's raw input is **escaped and tokenized before** being placed in `MATCH` — user text containing `"`, `*`, `-`, `NEAR`, or `OR` must not be interpreted as FTS5 syntax. Trailing `*` is appended to the final token for prefix matching. A shared `FtsQueryBuilder` in `:core:data` owns this; no feature module builds a MATCH string.

## 10.2 Boundaries

- The search index is a **local derived artifact**: `sync_state` does not apply, and it is rebuilt from source rows rather than synced.
- Search operates on the data model, never on UI state (Phase 0 §19). `SearchEngine` (§15) lives in `:core:data` and returns domain objects.
- Global and Song-scoped search are the same query with an added `song_id` filter.

---

# 11. Nudges

## 11.1 Scheduler — locked and constrained

**`WorkManager` is the only permitted scheduler. `AlarmManager` is forbidden.**

Rationale, and the exact boundary of the ban:

- Nudges are a coarse cadence model by product decision (Phase 0 §12: *"avoid promising exact alarm-style scheduling"*). Nothing in V1 needs minute-accurate, let alone sub-minute, delivery.
- `setExactAndAllowWhileIdle` and `SCHEDULE_EXACT_ALARM` carry Play policy scrutiny that a re-engagement nudge cannot justify.
- `WorkManager` is Doze-aware, reboot-persistent without `RECEIVE_BOOT_COMPLETED`, and honours battery constraints.

The ban is lifted **only** if a future feature genuinely requires sub-minute exact delivery (e.g. a metronome-style scheduled event). No such feature exists in V1 or in the Phase 0 V2 column. A PR introducing `AlarmManager` is rejected by default and requires an ADR.

Configuration:

```
PeriodicWorkRequestBuilder<NudgeWorker>(
    repeatInterval = 24, HOURS,
    flexTimeInterval = 6, HOURS)
  .setConstraints(requiresBatteryNotLow = true)
  .setBackoffCriteria(EXPONENTIAL, 30, MINUTES)
```

Plus jitter inside the worker so all users are not nudged at the same clock position: `nextEligible = now + interval ± jitter`.

## 11.2 Selection

`NudgeWorker` evaluates deterministic, testable rules and emits at most one nudge per run:

- An `ongoing` Song with at least one Section whose `body_text` is empty.
- An unassigned Take (`song_id IS NULL`) older than 48 hours.
- A Song untouched for N days that has both lyrics and takes (i.e. real investment).

Copy is gentle and never guilt-inducing (Phase 0 §12). Copy strings are reviewed as a product artifact, not written ad hoc in the worker.

## 11.3 Delivery

- Dedicated notification channel `songpad_nudges`, DEFAULT importance, user-controllable separately from the `songpad_recording` channel (which is tied to the capture FGS and must be at least LOW).
- `POST_NOTIFICATIONS` is requested **in context** — when the user enables nudges, or on first save (so the recording FGS notification can display) — never at cold start. Denial is handled with a rationale and a Settings deep link, and nudges degrade to in-app history only.
- Tapping a nudge deep-links to the target Song/Take; if the target is gone, it opens the library (Phase 0 §12).
- Every dispatched nudge writes a `NudgeEntity` row so in-app history is complete even when notifications are disabled.

## 11.4 Honest expectations

Aggressive OEM battery managers (Xiaomi/HyperOS, Oppo/ColorOS, Vivo, Transsion, some Samsung configurations) will defer or drop deferred work regardless of correct `WorkManager` use. This is not fixable in code. Mitigations: engagement keeps the app in a healthier standby bucket; Settings copy sets expectations; a one-time, dismissible battery-optimization hint may be shown on known-aggressive OEMs. Do not promise delivery times in UI copy.

---

# 12. Export

## 12.1 Bundle

Per Phase 0 §13, three modes: **Everything**, **Audio takes**, **Lyrics only**.

```
Export_<SongName>_<yyyyMMdd-HHmm>.songpad   (zip)
 ├── song.json        ← versioned metadata + relationships
 ├── lyrics.txt       ← sections in sort_order, human readable
 ├── notes.txt        ← notes with timestamps
 ├── audio/           ← one file per take
 └── midi/            ← SMF Type 0 where present
```

`song.json` carries a `schemaVersion` field from the first release, so future imports can migrate.

## 12.2 Mechanics

- `java.util.zip.ZipOutputStream` streaming **directly into the destination `OutputStream`**, copying 16 KB at a time. The archive is never fully buffered, so peak memory is flat regardless of project size.
- Audio and MIDI entries use `ZipEntry.STORED` — M4A, FLAC and MP3 are all already compressed and re-deflating them costs CPU for no gain. Only the text/JSON entries are `DEFLATED`. WAV entries are the one exception and may be `DEFLATED`, since uncompressed PCM zips well.
- Runs in a `WorkManager` worker with `setProgress`; the UI observes progress and can cancel. Cancellation deletes the partial archive.
- Staging in `files/tmp/`, cleaned on success, failure and cancellation.

## 12.3 Delivery — two distinct paths

| User intent | Mechanism |
| --- | --- |
| "Save this to my files / Drive" | SAF `ActivityResultContracts.CreateDocument("application/zip")`, write via `contentResolver.openOutputStream(uri)`. Survives uninstall, no permission |
| "Send this to someone" | `FileProvider.getUriForFile()` → `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION` |

Both are offered. `file://` URIs throw `FileUriExposedException` and must never be used.

Large bundles are fine: the Intent carries a URI string, and the receiver streams bytes through `ContentResolver`. The ~1 MB limit sometimes cited is the process-wide Binder transaction buffer, which only applies to data placed **directly** in Intent extras — which this design does not do. Individual receiving apps may impose their own attachment caps; that is their limit to surface, not ours.

## 12.4 Audio format on export — resolved

Phase 0 §13 requires WAV and MP3. Both are supported, alongside the stored format. **This closes the conflict; it is no longer an open product question.**

| Export format | Produced by | Quality note | User-facing label |
| --- | --- | --- | --- |
| **M4A** (stored, Standard mode) | Direct copy | No re-encode | "Compressed — small files" |
| **FLAC** (stored, Lossless mode) | Direct copy | Lossless | "Lossless" |
| **WAV** | `MediaCodec` decode → 44-byte RIFF header | Bit-exact from FLAC; decoded from AAC in Standard mode | **"Uncompressed — for DAWs."** Never "lossless" unless the source is FLAC |
| **MP3** | LAME (NDK), 192 kbps mono | Single generation from FLAC; second generation from AAC | "MP3 — universal compatibility" |

MP3 specifics:

- Android ships an MP3 decoder but has never shipped an encoder, and MediaCodec offers no route. LAME is the only realistic option.
- **Patents expired in 2017**, so there is no royalty question. LGPL 2.1 obligations reduce to shipping the license text (an "Open source licenses" entry in Settings, which is wanted regardless) and publishing the LAME source built from. Android links `.so` files dynamically by definition, which is the compliance path LGPL asks for.
- 48 kHz is a valid MPEG-1 Layer III rate, so no resampling.
- ~400 KB per ABI; App Bundle splits mean a user downloads one.
- Estimated **3–5 dev-days** including CI and tests. Lands in Weeks 5–8 with the rest of the export pipeline (Dev 5). It is a leaf on the export worker and **does not gate Phase 1**.

## 12.5 `ExportSourceResolver`

A single interface in `:core:data` decides what bytes an encoder receives:

```kotlin
interface ExportSourceResolver {
    /** Best available source for a take: FLAC if stored lossless, else M4A. */
    suspend fun resolve(takeId: String): ExportSource
}
```

It reads `AudioAssetEntity.mime_type`. Encoders consume `ExportSource` and never branch on quality mode. This ships in Phase 1 even though it is trivial at first, because it is what keeps the generational-loss question answerable rather than hard-coded.

The honesty rule is normative: **a WAV decoded from AAC is never labelled "lossless."** The export screen states the source format before confirmation, per Phase 0 §13's requirement that export summarize what will be included.

# 13. Permissions

| Permission | Type | When requested | On denial |
| --- | --- | --- | --- |
| `RECORD_AUDIO` | Runtime, dangerous | First tap of a Record action | Rationale sheet explaining capture; Settings deep link; Record disabled with an explanatory state |
| `POST_NOTIFICATIONS` | Runtime (API 33+) | On enabling nudges, or first save | Nudges degrade to in-app history; capture still works |
| `FOREGROUND_SERVICE` | Normal | manifest | — |
| `FOREGROUND_SERVICE_MICROPHONE` | Normal (API 34+) | manifest | — |

No storage permission (app-specific storage). No `SCHEDULE_EXACT_ALARM` (§11.1). No camera permission — image attachments use the photo picker, which requires none.

Phase 0 §18 requires Granted / Denied / Explanation / Retry states for every permission; these are UI states owned by `:feature:capture` and Settings, not ad-hoc dialogs.

Play Console obligations owned by Dev 5: foreground-service-type declaration with demo video; Data Safety form stating audio never leaves the device; privacy policy consistent with that claim.

---

# 14. Design system

Four themes from two personalities (Phase 0 §17): SongPad (organic/warm) and MelodyStudio (electric/vibrant), each Light and Dark. Components reference **semantic tokens only** and never a raw palette value.

```
  OKLCH primitive ramps
          ↓
  Semantic roles (bg, surface, text, accent, accent-2, record, divider)
          ↓
  Component styles (glass pill, tag, input, canvas, FAB)
```

| Token | SongPad Light | SongPad Dark | MelodyStudio Light | MelodyStudio Dark | Used by |
| --- | --- | --- | --- | --- | --- |
| `bg` | `#F5EAD8` | `#1B1815` | `#E9E0FA` | `#0D0718` | Scaffold |
| `surface` | `#EBDDC5` | `#2A2621` | `#FDFAFF` | `#1A0F2E` | Cards, sheets |
| `text` | `#201E1D` | `#F4EBDD` | `#1C1233` | `#F3EBFF` | Type, icons |
| `accent` | `#C67139` | `#E08A4C` | `#7B2FF7` | `#B46BFF` | Actions, active waveform |
| `accent-2` | `#7A8A5E` | `#2B3122` | `#00B3C8` | `#2EE6F5` | Tags, status badges |
| `record` | `#C67139` | `#E08A4C` | `#FF3355` | `#FF5A7A` | Record FAB, live pulse |
| `divider` | `rgba(32,30,29,.16)` | `rgba(244,235,221,.16)` | `rgba(74,42,138,.20)` | `rgba(201,166,255,.22)` | Borders, rules |

Radii: `sm 8dp`, `md 16dp`, `lg 28dp`, `pill 999dp`. Minimum touch target 48×48dp. Glass treatment is a restrained component treatment (glass pill, ribbon player), not a global decoration.

The waveform Canvas and the piano Canvas both read `accent` and `record` from the token layer, so all four themes work with no per-theme drawing code.

---

# 15. Core interfaces (frozen Week 1)

These are the contracts that make parallel development possible. They are frozen at the end of Week 1. Changing a signature after that requires the technical lead's sign-off, because someone is already coding against it.

```kotlin
// ── :core:audio ──────────────────────────────────────────────────────────
interface AudioRecorderEngine {
    val amplitudes: SharedFlow<Float>          // 10 Hz, normalized 0f..1f
    val state: StateFlow<RecordingState>
    val framesWritten: StateFlow<Long>         // ground truth timeline

    suspend fun start(config: CaptureConfig): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stopAndCommit(takeId: String): Result<CapturedAudio>
    suspend fun discard(): Result<Unit>

    /** Maps a UI-thread event time onto the audio frame timeline. See §8.3. */
    fun frameIndexFor(eventTimeNanos: Long): Long
}

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val elapsedMs: Long) : RecordingState
    data class Paused(val elapsedMs: Long) : RecordingState
    data class Interrupted(val reason: InterruptionReason) : RecordingState
    data class Failed(val error: CaptureError) : RecordingState
}

interface PlaybackController {
    val state: StateFlow<PlaybackState>
    fun play(source: PlaybackSource)           // committed take, or in-flight .pcm
    fun pause(); fun seekTo(positionMs: Long); fun stop()
}

// ── :core:midi ───────────────────────────────────────────────────────────
interface MidiCaptureEngine {
    /** Frame indices come from AudioRecorderEngine.frameIndexFor(). */
    fun onKeyDown(note: Int, velocity: Int, eventTimeNanos: Long)
    fun onKeyUp(note: Int, eventTimeNanos: Long)
    suspend fun commit(takeId: String): Result<MidiAsset?>
    fun reset()
}

// ── :core:data ───────────────────────────────────────────────────────────
interface TakeRepository {
    fun observeForSong(songId: String): Flow<List<Take>>
    fun observeUnassigned(): Flow<List<Take>>
    suspend fun commit(draft: TakeDraft): Result<Take>
    suspend fun assignToSong(takeId: String, songId: String): Result<Unit>
    suspend fun rename(takeId: String, name: String): Result<Unit>
    suspend fun softDelete(takeId: String): Result<Unit>
    suspend fun findOrphaned(): List<OrphanedTake>
}

interface SongRepository {
    fun observeSongs(filter: SongFilter): Flow<List<Song>>
    fun observeSong(songId: String): Flow<SongDetail?>
    suspend fun create(name: String, adoptTakeId: String? = null): Result<Song>
    suspend fun setStatus(songId: String, status: SongStatus): Result<Unit>
    suspend fun toggleFavorite(songId: String): Result<Unit>
    suspend fun softDelete(songId: String): Result<Unit>
}

interface SearchEngine {
    fun searchGlobal(query: String): Flow<SearchResults>
    fun searchInSong(songId: String, query: String): Flow<SearchResults>
}

data class SearchResults(
    val songs: List<SongMatch>,
    val lyrics: List<SectionMatch>,
    val notes: List<NoteMatch>,
    val takes: List<TakeMatch>,
)
```

Every `Result` failure carries a typed domain error, never a raw exception. Feature modules code against these interfaces and, before the implementations land, against fakes in `:core:data:testing`.

---

# 16. Vertical slice (Milestone 1, Weeks 3–4)

One journey, all layers, on real hardware:

> Open app → tap Record → record 15 s of voice with live waveform → pause → review by scrubbing → Save → name Song "Midnight Riff" → land in Song Home → play the Take back.

```
 RecordScreen ──CaptureIntent.Start──► CaptureViewModel
                                            │
                                     StartRecordingUseCase
                                            │
              ┌─────────────────────────────┴───────────────────┐
              ▼                                                 ▼
     AudioRecorderEngine                                  CaptureSession
     (FGS + Oboe + ring buffer)                           (takeId, paths)
              │
   48 kHz PCM → audio.pcm.tmp        amplitudes → SharedFlow(10 Hz) → Canvas
              │
    CaptureIntent.Save("Midnight Riff")
              ▼
     FinalizeTakeUseCase
       ├─ stop, flush, fsync, rename .tmp → .pcm
       ├─ Room transaction: SongEntity + TakeEntity(status=TRANSCODING) + AudioAssetEntity
       └─ enqueue TranscodeWorker
              ▼
     Navigate → SongDetailScreen(Home)
              ▼
     TakeRepository.observeForSong() emits → tap Play → PlaybackController
```

The slice is **done** when it survives all of: airplane-mode, screen lock mid-recording, an incoming call mid-recording, `adb shell am kill` mid-recording followed by recovery on relaunch, and a cold start with 200 seeded takes.

Failing the slice in Week 4 is a good outcome. Failing it in Week 9 is not.

---

# 17. Error and lifecycle states

Phase 0 §18 mandates these. Each is a real state in a sealed hierarchy, not a toast.

| Area | States |
| --- | --- |
| Recording | Ready · Recording · Paused · Reviewing · Saving · Saved · Discarded · Interrupted · Failed · PermissionDenied · LowStorage |
| Recovery | None · Prompt(duration) · Restoring · Restored · DiscardConfirm |
| Lyrics | Empty · Editing · Autosaved · SectionInserting |
| Takes | Empty · Populated · Playing · Transcoding · Unavailable |
| Notes | Empty · Composing · MediaAttached · VoiceRecording · Saved · InvalidEmpty |
| Songs | EmptyLibrary · Populated · SearchResults · Filtered |
| Nudges | Empty · Active · Disabled · TargetUnavailable |
| Export | Selecting(mode + audio format) · Preparing(progress) · Success · Failure · Cancelled |
| Quality mode | Standard · Lossless · FirstSaveDisclosure(dismissible) |
| Permissions | Granted · Denied · NeedsRationale · PermanentlyDenied |
| Storage | Normal · Low · Critical |

`Transcoding` deserves emphasis: a take is playable from raw PCM the instant it is saved, so the user is never blocked waiting for a transcode. The state exists for correctness, not for a spinner. The raw PCM is deleted only once the M4A or FLAC has been written and verified (§4.3).

---

# 18. Testing strategy

Testing is written inside each phase, not appended in Week 11.

| Layer | Where it runs | What it proves | Gate |
| --- | --- | --- | --- |
| Domain / use cases | JVM (`runTest`, Turbine, fakes) | Take assignment, section ordering, empty-note rejection, status transitions, nudge selection rules | > 85 % branch coverage on `:core:model` + use cases |
| SMF serializer | JVM | Known event sequence → byte-exact SMF output; VLQ boundaries at 127/128/16383/16384 | Byte-for-byte match |
| Clock mapping | JVM | Synthetic anchor pairs with injected drift → frame indices within tolerance over 5 simulated minutes | < 10 ms cumulative error |
| Room + FTS5 | Instrumented (in-memory DB) | Cascades, soft-delete filtering, trigger sync, prefix queries, `bm25` ordering, `snippet` output | Query < 15 ms at 2,000 rows |
| Migrations | Instrumented (`MigrationTestHelper`) | Every vN→vN+1 path; schema **and** data survival | No migration merges without a test |
| Compose UI | Instrumented / Robolectric | Capture flow, section insertion, note validation, `@` composer | Asserted state < 500 ms, no recomposition loops |
| Audio lifecycle | **Physical devices** | start/pause/resume/save; call interruption; BT disconnect; headphone unplug; 45-min session under battery saver | Zero dropped frames; duration metadata accurate to ±50 ms |
| Process death | Physical devices (`adb shell am kill`) | Partial PCM recoverable; MIDI truncated to the same frame | 100 % recovery of synced bytes |
| Orphan cleanup | Instrumented + device | Worker run during an active recording does not touch `.pcm.tmp`; files < 24 h old are never collected | Zero false positives |
| Export | Emulators, API 30/33/34/35 | 25-take project → valid zip; `song.json` schema-valid; audio plays in a system player | Bundle integrity |
| Export formats | Instrumented + JVM | FLAC→WAV round-trip is **bit-exact**; M4A→WAV produces a valid RIFF header; LAME MP3 decodes at the expected duration; `ExportSourceResolver` picks FLAC over M4A | Bit-exact where the source is lossless |
| Screenshot | JVM (Paparazzi) | Design-system components across all four themes | Optional; scoped to `:core:ui` |

**What cannot be automated**, and must be a manual device checklist owned by Dev C: perceived touch-to-tone latency, recording quality on each device's mic, and whether Voice+Piano *sounds* in sync. Automation can prove the numbers; only a person can confirm the result is musical.

Device matrix: one Pixel (clean baseline), one Samsung (share), one **Xiaomi/HyperOS** and one **Transsion (Tecno/Infinix)** (aggressive process management and low-end audio hardware — this is where reliability breaks first, and given the target market they are not optional).

CI (GitHub Actions):
1. **Every PR** — build, lint, JVM unit tests (fast & free). Must be under ~10 minutes or people route around it.
2. **Every merge to main** — instrumented tests on KVM-accelerated Linux runners (`reactivecircus/android-emulator-runner`), API 30 and a current API.
3. **Physical device milestone validation (replaces Nightly Firebase Matrix)** — Automated cloud test farms cannot evaluate acoustic capture (e.g. mic picking up speaker output in Voice+Piano mode), real perceived touch-to-tone latency, or aggressive OEM behavioral kills. Automated Nightly Firebase matrix is dropped; instead, CI packages APK artifacts on `main` and milestone ends for direct validation on physical devices (Pixel, Samsung, Xiaomi/HyperOS, Transsion).

---

# 19. Work breakdown and parallelization

Team: **4–5 developers**, one acting as technical lead (source: `Rough_Plan_we_re_using_`). Where a task is written "Dev 5 / else split", it assumes the five-person shape and degrades to the four-person shape by distributing that work once the primary streams stabilize.

```
                      WEEK 1 — PRODUCT & TECH LOCK
                    (this document + frozen contracts)
                                  │
        ┌─────────────┬───────────┼───────────┬─────────────┐
        ▼             ▼           ▼           ▼             ▼
     Dev A         Dev B       Dev C       Dev D        Dev E*
   Architecture     Data       Audio      Design       Spikes /
    CI / DI        Room/FTS5   Oboe/FGS   Tokens       devices
        │             │           │           │             │
        └─────────────┴─────┬─────┴───────────┴─────────────┘
                            ▼
                   WEEKS 3–4 — VERTICAL SLICE
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
  Stream A: Capture   Stream B: Workspace  Stream C: Platform
  Dev 1 capture flow  Dev 3 songs/takes    Dev 5 search (FTS5)
  Dev 2 piano/MIDI    Dev 4 lyrics/notes   Dev 5 nudges, export
        │                   │                   │
        └───────────────────┼───────────────────┘
                            ▼
                  WEEKS 8–10 — INTEGRATION
                            ▼
                  WEEKS 10–12 — HARDENING
                            ▼
                   WEEKS 12–14 — BETA
```

| Phase | Dev A (lead) | Dev C / Dev 2 (audio) | Dev B / Dev 3 (data + workspace) | Dev D / Dev 4 / Dev 5 (UI + platform) |
| --- | --- | --- | --- | --- |
| **Wk 1 — Lock** | ADRs, module skeleton, Hilt graph, CI, freeze §15 contracts | Oboe/CMake skeleton, buffer sizing, SMF byte schema, **spikes S-1/S-2/S-3** | Room entities, FKs, DAO signatures, **spike S-4** (FTS5 + UUIDv7) | Token architecture across four themes; **spike S-5** (Nav 3) |
| **Wk 1–2 — Foundation** | Nav 3 host, MVI base, error/logging, build variants | Capture engine, ring buffer, `URGENT_AUDIO` thread, FGS, audio focus | Room + `BundledSQLiteDriver`, DAOs, in-memory test DB, migration harness | Glass pill, waveform Canvas, theme wrappers, empty states |
| **Wk 3–4 — Vertical slice** | Wire UI→VM→UseCase→Repo→DB→Audio | PCM→commit→transcode→review player | Song+Take transaction, reactive queries | RecordScreen, SongDetailScreen, ribbon player |
| **Wk 5–8 — Parallel** | Review, integration, architectural integrity | D1: global/project capture, recovery UX. D2: piano Canvas, sampler, MIDI, Voice+Piano | D3: library, filters, favorites, rename, take detail. D4: section editor, notes timeline, `@` refs | D5: FTS5 search UI, nudges, export pipeline (incl. LAME/MP3, ~3–5 d), quality toggle + disclosure, settings, permissions |
| **Wk 8–10 — Integration** | Cross-module nav, unified playback | Voice+Piano sync validation, latency profiling, routing | Loose-take filing, note refs, section reorder | Search filtering, nudge deep links, share sheet |
| **Wk 10–12 — Hardening** | Leaks, ANRs, LMK survival, R8 rules | Focus loss, telephony, BT, 45-min sessions, device matrix | Migrations v1→v2, orphan GC, 1,000+ take performance | Back nav, rotation, large type, accessibility, small screens |
| **Wk 12–14 — Beta** | Release variants, crash reporting | Device compatibility sign-off | Library-scale validation | Play declarations, dogfooding, polish |

Non-negotiable sequencing:
- §15 contracts freeze at the end of Week 1. Nobody waits for an implementation.
- Spikes S-1..S-5 return **before** the code that depends on them (§21.1).
- No feature module depends on another feature module, ever.

---

# 20. Milestones and Definition of Done

| Milestone | Week | Definition of Done |
| --- | --- | --- |
| M0 Lock | 1 | This document approved; contracts frozen; CI green on an empty skeleton; five spikes assigned |
| M1 Vertical slice | 4 | The §16 journey passes all five stress conditions on a physical device |
| M2 Capture complete | 8 | Voice, Piano, Voice+Piano all commit correct audio + MIDI; recovery works; interruption matrix (§7.4) fully handled |
| M3 Feature complete | 10 | Every Phase 0 V1 feature reachable; all §17 states implemented |
| M4 Hardened | 12 | Device matrix green; migration tests pass; no leaks or ANRs under profiling; orphan cleanup proven safe |
| M5 Beta | 14 | Signed build, Play declarations complete, external testers recording daily |

A feature is **Done** when: it satisfies Phase 0 behavior; all its §17 states are implemented; it has unit tests; it survives rotation, process death and back navigation; it works in all four themes; and it has been used on a physical low-end device.

---

# 21. Risks, spikes and open decisions

## 21.1 Stage 0 spikes — must return before dependent work

| ID | Spike | Threshold (go/no-go) | Blocks | Owner |
| --- | --- | --- | --- | --- |
| **S-1** | Recorder crash recovery: 30-min continuous capture on Xiaomi/HyperOS and a Transsion device, then `adb shell am kill` | Partial `.pcm` is fully decodable to the last synced byte; ≤ 1 s of audio lost | All capture work (§7) | Dev C |
| **S-2** | Piano touch-to-tone latency via Oboe on 3 devices (flagship, mid-range, low-end Transsion) | Median ≤ 40 ms on mid-range. Above ~60 ms, revisit sample loading and buffer strategy before building the keyboard | Piano engine (§8.1) | Dev C / Dev 2 |
| **S-3** | MIDI/audio alignment: metronomic 5-minute Voice+Piano take; compare MIDI note frames against audio transients | < 10 ms cumulative divergence at 5 minutes | Voice+Piano (§8.3) — **the highest-consequence spike** | Dev 2 |
| **S-4** | UUIDv7 insert throughput at 2,000 rows **and** FTS5 external-content + triggers under `BundledSQLiteDriver` | Insert of 2,000 takes < 2 s; FTS5 query < 15 ms; `bm25`/`snippet`/prefix all functional | Schema and search (§6, §10) | Dev B |
| **S-5** | Navigation 3 two-screen prototype (library → take detail with an active recorder) | No state-restoration or back-stack defects; predictive back works | Navigation (§4.1) — fallback is Nav2 | Dev D |

A spike that fails is not a crisis; it is the spike doing its job. Each has a named fallback in this document.

## 21.2 Standing risks

| Risk | Mitigation | Residual |
| --- | --- | --- |
| Audio file corruption on kill | Headerless PCM + ~1 s fsync + atomic rename + recovery manager (§7.3, §9.4) | Up to ~1 s of audio between fsyncs |
| MIDI/audio drift | Frame-clock master with `AudioTimestamp` anchors (§8.3) | Depends entirely on S-3 |
| **Orphan cleanup deleting live recordings** | 24 h mtime grace + `.tmp` presence check + active-session deferral (§9.3) | Low, but the worst failure mode in the app — hence three independent guards |
| OEM process killing | FGS + crash-safe files + expectation-setting copy | **Unfixable in code.** Cannot be engineered away |
| Device-dependent audio latency | Oboe, exclusive mode, native buffers | **Unfixable in code.** Set expectations |
| Nudge non-delivery on aggressive OEMs | WorkManager + jitter + honest copy | Unfixable in code |
| Navigation 3 immaturity | S-5; thin, centralized navigation logic | Nav2 fallback costs ~2 days |
| Contract churn after Week 1 | Freeze + lead sign-off | Rising cost with each week |
| Generational loss on MP3 export in Standard mode | `ExportSourceResolver` prefers FLAC; 128 kbps AAC source gives headroom; 192 kbps MP3 target | Present by design in Standard mode; Lossless removes it |
| User discovers the quality mode too late to matter | Three-surface disclosure (§4.4), first at first-save | Cannot be fully removed — the setting is inherently non-retroactive |
| Library-scale performance | Indexes, FTS5, paging; test at 1,000+ takes from Week 10 | |

## 21.3 Open decisions — product, not engineering

Per Phase 0 §26, these are raised rather than implemented:

1. **Recording quality mode as a product surface.** §4.3 introduces a user-visible Standard/Lossless setting with a ~3× storage consequence that **cannot be applied retroactively**. The engineering design is settled; the product surface is not. **Questions: is the first-save disclosure line (§4.4) acceptable in that surface, and what is the exact copy?** Phase 0 §16 defines a Storage & Data section but not its contents. Does not block Phase 1 — the toggle ships as a Settings stub and the disclosure line is added in Weeks 5–8.
2. **Storage honesty in Settings.** Audio is excluded from cloud backup (§9.5), so uninstalling loses recordings. Phase 0 §16 has a Storage & Data section but does not state the copy. **Question: what does Settings say, and is there a V1 manual-backup affordance beyond Export?**
3. **Recovery prompt wording and timing.** §9.4 recovers unfinished takes, a user-facing behavior not described in Phase 0. **Question: prompt on launch, or surface silently in the library as a recovered item?**

None of these blocks Week 1–4 work.

---

# 22. Deferred to V2

Restating the Phase 0 §22 boundary as an engineering commitment, so nothing is built speculatively:

- MIDI editing, transformation, transcription, arrangement — the sidecar is **written and never read** in V1.
- Multi-track, layering, overdub, destructive audio editing.
- Cloud sync, accounts, collaboration — the §6.3 sync columns exist but are inert (`LOCAL_ONLY`).
- Section-to-Take references.
- Moments.
- Advanced tagging and semantic discovery.
- Stems and project interchange.
- Database encryption (data is app-private with no PII beyond the user's own lyrics; SQLCipher would change the file format and complicate any future sync).
- Retroactive re-encoding of an existing library when the quality mode changes. The setting applies to new recordings only (§4.3); bulk re-encode is a V2 question, and in Standard mode it could not recover quality anyway.

Writing the sync columns and the MIDI sidecar in V1 while reading neither is deliberate. Both are cheap now and expensive to retrofit.

---

# 23. Phase 1 starting rule

Phase 1 implementation may begin.

Developers implement against **this document**, the **Phase 0 Closure Specification**, and the approved design system. A proposed change to a LOCKED product behavior is raised as a product decision. A proposed change to a LOCKED technical decision is raised as an ADR and amended here.

The first commit is the module skeleton and CI. The first meaningful milestone is the vertical slice in Week 4. Everything before it exists to make it possible; everything after it depends on it having worked.
