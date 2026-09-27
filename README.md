# SongPad

> **Capture first. Organize later.**  
> A fast, local-first music-idea capture application for Android 11+ (API 30+).

---

## 🎵 Overview

**SongPad** is built for songwriters and musicians who need to capture ideas immediately without getting bogged down in menus, project configuration, or DAW complexity. A musician struck by inspiration can open the app, capture a voice memo or keyboard sketch in seconds, and organize it into a structured Song project when ready.

SongPad is **not** a digital audio workstation (DAW), multi-track production suite, or score writer. It is a purpose-built musical scratchpad designed for maximum speed, zero cognitive friction, deterministic latency, and uncompromised crash resilience.

---

## ✨ Core Features (V1 Scope)

- **Frictionless Capture**: Global, instant one-tap audio capture. The app launches directly into capture readiness.
- **On-Screen Piano & Piano + Voice**: Low-latency polyphonic sampled keyboard (Oboe C++/NDK). In *Piano + Voice* mode, voice audio and keyboard performance are captured simultaneously with frame-accurate synchronization and an SMF Type 0 MIDI sidecar.
- **Crash-Resilient Audio Engine**: Audio is streamed as headerless raw linear PCM (48 kHz, 16-bit, mono) with periodic `fsync`. If the process is abruptly terminated, the recording is preserved up to the last synced byte and recoverable on relaunch.
- **Recording Quality Modes**:
  - **Standard** (default): Transcoded asynchronously to AAC-LC in M4A (128 kbps mono, ~0.96 MB/min).
  - **Lossless**: Transcoded asynchronously to FLAC (~2.9 MB/min) for bit-exact DAW export.
- **Song Projects & Workspaces**: Group takes, lyrics, and notes into Songs with statuses (`ongoing`, `finished`, `archived`) and favorites.
- **Modular Lyrics & Tagged Sections**: Section-based lyric editor (Verse, Chorus, Bridge, Pre-Chorus, Outro, etc.) with drag-and-drop reordering and autosave.
- **Contextual Notes**: Rich text and scratch notes associated with songs, with support for quick voice notes.
- **Fast Full-Text Search**: Powered by SQLite FTS5 with `bm25` ranking and prefix matching, querying songs, lyrics, notes, and take names instantaneously.
- **Gentle Nudges**: Coarse-cadence, non-guilt-inducing local notifications (`WorkManager`) prompting the songwriter to revisit unfinished ideas.
- **Comprehensive Export**: Standalone audio export (M4A, FLAC, WAV, and MP3 via embedded NDK LAME encoder) and complete Project Zip bundles containing audio stems, MIDI files, lyrics, and machine-readable `song.json` metadata.
- **Dual-Personality Theme System**:
  - **SongPad**: Organic, warm, tactile (Light: `#F5EAD8` / Dark: `#1B1815`)
  - **MelodyStudio**: Electric, vibrant, sleek (Light: `#E9E0FA` / Dark: `#0D0718`)

---

## 🏛️ System Architecture

SongPad is structured as a modular, unidirectional data flow (UDF) Android application strictly separating pure domain models, data persistence, native audio processing, design system components, and isolated feature flows.

```
                                    :app
                    (Application, DI Root, NavHost)
                                      │
        ┌─────────────┬───────────────┼───────────────┬─────────────┐
        ▼             ▼               ▼               ▼             ▼
 :feature:capture :feature:song  :feature:search :feature:nudges :feature:export
        │             │               │               │             │
        └─────────────┴───────┬───────┴───────────────┴─────────────┘
                              ▼
        ┌──────────┬──────────┼──────────┬──────────┐
        ▼          ▼          ▼          ▼          ▼
   :core:audio :core:midi :core:data  :core:ui  :core:model
    (NDK/Oboe)             (Room/FTS5) (Tokens)  (Pure Kotlin)
```

### Module Responsibilities

| Module | Purpose & Scope | Dependencies |
| :--- | :--- | :--- |
| **`:core:model`** | Domain entities, value types, validation, the frozen §15 contracts, use cases, `MviStore`, `TakeFileStore`. **Pure Kotlin, zero Android dependencies.** | *None* |
| **`:core:data`** | Room database, DAOs, SQLite FTS5 table & triggers, migrations, repository implementations, DataStore settings, maintenance workers. | `:core:model` |
| **`:core:data:testing`** | JVM fakes for every contract over one `InMemoryStore`; used by every feature test. | `:core:model` |
| **`:core:audio`** | Oboe C++/NDK capture & synthesis engines, circular ring buffer, waveform extraction, audio focus, foreground service. | `:core:model` |
| **`:core:midi`** | Sampler voice coordinator (shares Oboe stream), MIDI event collector, SMF Type 0 serializer. | `:core:model`, `:core:audio` |
| **`:core:ui`** | Semantic token architecture, four theme definitions, typography, spacing, glass pill, custom Canvas components. | *None* |
| **`:feature:capture`** | Global capture flow, recording screen, pause/resume, audio recovery, MVI capture controller. | `:core:*` |
| **`:feature:song`** | Songs library, song workspace, lyrics section editor, notes timeline, take management. | `:core:*` |
| **`:feature:search`** | Global and in-song instant search UI with FTS5 highlighting and prefix filtering. | `:core:*` |
| **`:feature:nudges`** | In-app nudge inbox, WorkManager scheduling logic, nudge settings. | `:core:*` |
| **`:feature:export`** | Audio conversion (M4A, FLAC, WAV, LAME MP3), SAF file creation, and project zip bundle packaging. | `:core:*` |
| **`:app`** | Application entry point, Hilt root, Navigation 3 host and route keys, Settings screen, deep links. | All modules |
| **`:spikes:s4-fts5`** | Desktop-runnable S-4 spike against the same bundled SQLite (`./gradlew :spikes:s4-fts5:run`). | `:core:model` |

### Non-Negotiable Architecture Rules

1. **Strict Feature Isolation**: Feature modules **never** depend on one another. Cross-feature navigation is coordinated through route keys declared in `:app`.
2. **Pure Domain Model**: `:core:model` contains **no** Android dependencies, enabling instant JVM unit testing with zero Robolectric overhead.
3. **NDK Encapsulation**: `:core:audio` is the **only** module containing NDK/CMake/JNI code. No other module interacts directly with native binaries.
4. **Interface Contracts**: All feature modules develop against the frozen core interfaces (§15), declared in `:core:model` (see [`docs/CONTRACTS_FROZEN.md`](docs/CONTRACTS_FROZEN.md) and ADR-0030), or against the fakes in `:core:data:testing`.
5. **Module boundaries are enforced at configuration time**: `./gradlew checkModuleBoundaries` (also run by the CI gate) fails the build on a forbidden project dependency; only `:core:audio` may contain a CMake build.

### Phase 1 status (single-developer pass, 2026-09-27)

Waves 0–4 are built: every Phase 0 V1 feature is reachable, and every §17 state is implemented. That includes
the Oboe piano sampler and keyboard, and LAME MP3 export. S-1, S-2 and S-3 passed on physical devices
(`docs/spikes/`, `docs/STAGE_1_DEVICE_VERIFICATION_REPORT.md`).

The code-side work for Wave 5 (hardening) and Wave 6 (beta) is done:
- back-navigation guards around live recordings;
- §8.1 piano-on-headphones mix (ADR-0035);
- debug StrictMode;
- R8 verified against the minified build;
- release signing wiring;
- crash reporting via Play vitals (ADR-0036).

Gates M4 and M5 are **not closed**. They need device-matrix runs, Play Console setup and testers. See
[`docs/release/HARDENING_STATUS.md`](docs/release/HARDENING_STATUS.md) and
[`docs/release/BETA_RELEASE.md`](docs/release/BETA_RELEASE.md). Xiaomi/HyperOS is carried into beta untested,
by explicit decision.

---

## 🔒 Locked Technical Decisions

As specified in the **Phase 1 Development Specification (§4.1)**:

| Area | Decision | Rationale | Rejected Alternatives |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin 2.x, Coroutines & Flow | Structured concurrency, first-class Compose performance. | Java / RxJava |
| **UI Framework** | Jetpack Compose (Material 3 base) | Declarative state binding; custom Canvas for waveform and piano. | Traditional XML Views |
| **Navigation** | Navigation 3 (`androidx.navigation3`) | State-driven backstack suited for local-first apps. | Nav2 (kept as verified fallback) |
| **Dependency Injection** | Dagger Hilt (with KSP) | Compile-time validation, native ViewModel/Worker/Service scoping. | Koin, manual DI |
| **Database** | Room 2.7+ with `BundledSQLiteDriver` | Bundled SQLite guarantees FTS5 availability across all OEMs. | SQLDelight, raw SQLite, Realm |
| **Primary Keys** | UUIDv7 (`TEXT`) | Time-ordered, sequential B-tree inserts, sync-ready without collisions. | UUIDv4 (random fragmentation), auto-inc IDs |
| **Search Engine** | SQLite FTS5 (`unicode61`, `bm25()`) | Search-as-you-type prefix indexing, relevance ranking, and snippets. | FTS4 (obsolete, rejected) |
| **Audio Capture** | Oboe (C++/NDK) stream | Sub-millisecond latency control, unified capture and synthesis clocks. | `MediaRecorder` (unrecoverable), `AudioRecord` |
| **Audio Format** | 48 kHz, 16-bit, mono linear PCM | Hardware-native sample rate on Android, zero HAL resampling penalty. | 44.1 kHz (forces resampling on Android HAL) |
| **Capture Thread** | Dedicated `URGENT_AUDIO` thread | Deterministic buffer scheduling without thread starvation. | `Dispatchers.IO` (**strictly forbidden**) |
| **On-Disk Capture** | Headerless raw PCM (`.pcm`) | Truncated files remain fully playable up to the last synced byte. | Live container writing (corrupts on crash) |
| **Playback** | Media3 / ExoPlayer | Accurate scrubbing, audio focus handling, MediaSession integration. | Android `MediaPlayer` |
| **Transcoding** | `MediaCodec` + `MediaMuxer` (in WorkManager) | Asynchronous hardware-accelerated transcoding to M4A or FLAC. | Inline main-thread encoding |
| **MP3 Export** | LAME via NDK | High-quality MP3 encoding on export (patents expired 2017). | Shine (inferior quality) |
| **Piano Engine** | Oboe polyphonic sampler | Real-time touch-to-tone latency under 40 ms. | `SoundPool` (unpredictable latency) |
| **Background Tasks** | Android `WorkManager` only | Doze-compliant, battery-friendly, persistent across reboots. | `AlarmManager` (**forbidden** for V1) |
| **Foreground Service** | `microphone`-typed FGS | Required since Android 14 to preserve active capture during backgrounding. | Background thread without FGS |

---

## 🗄️ Data Model & Take Lifecycle

### Entity Hierarchy

- **`SongEntity`**: Root project entity (`id`, `name`, `status`, `is_favorite`, `created_at`, `updated_at`, `sync_state`).
- **`TakeEntity`**: Audio capture unit (`id`, `song_id?`, `name`, `type`, `status`, `duration_ms`). When `song_id = NULL`, the Take is an independent capture.
- **`AudioAssetEntity`**: File metadata (`take_id`, `relative_path`, `mime_type`, `sample_rate`, `bit_depth`, `byte_size`, `peaks_path`).
- **`MidiAssetEntity`**: Piano MIDI sidecar (`take_id`, `relative_path`, `ppq`, `byte_size`).
- **`LyricsDocumentEntity`**: Exactly one document per Song (`song_id` unique).
- **`SectionEntity`**: Individual lyric sections (`document_id`, `tag`, `body_text`, `sort_order`).
- **`NoteEntity`**: Unstructured thought notes (`song_id`, `body_text`, `voice_take_id?`, `media_uri?`).
- **`NudgeEntity`**: In-app reminder notifications (`target_song_id?`, `title`, `body`, `is_read`).

### Take Lifecycle State Machine

```
RECORDING ──clean stop──► TRANSCODING ──worker success──► READY
    │                          │
    │ process death            │ worker exhausted retries
    ▼                          ▼
ORPHANED ──user recovers──► TRANSCODING          FAILED
    │
    └── user discards ──► hard delete (row + files)
```

---

## 📅 Development Roadmap & Milestones

The project is structured across a **10–14 week timeline** for a 4–5 developer team:

| Milestone | Target | Description & Deliverables |
| :--- | :---: | :--- |
| **M0: Lock** | Week 1 | Frozen contracts (§15), module skeleton, CI pipeline, five spikes assigned. |
| **M1: Vertical Slice** | Week 4 | End-to-end user journey on physical hardware: *Open → Record → Waveform → Save → Song Home → Playback*. |
| **M2: Capture Complete** | Week 8 | Voice, Piano, and Piano + Voice capture complete; MIDI alignment verified; crash recovery operational. |
| **M3: Feature Complete** | Week 10 | Library, lyrics editor, notes, FTS5 search, nudges, and multi-format export fully implemented. |
| **M4: Hardened** | Week 12 | Device matrix testing (Pixel, Samsung, Xiaomi, Transsion), leak detection, orphan cleanup, migration tests. |
| **M5: Beta** | Week 14 | Signed release build, Google Play declarations, daily dogfooding. |

### Stage 0 Technical Spikes

Before feature code is written, five spikes validate critical assumptions:

1. **S-1 (Recorder Recovery)**: 30-minute continuous capture killed via `adb shell am kill`; verifies PCM integrity up to last synced byte.
2. **S-2 (Piano Latency)**: Oboe touch-to-tone latency benchmarked across low-end and mid-range devices (target: $\le 40$ ms).
3. **S-3 (MIDI/Audio Alignment)**: Metronomic 5-minute Voice + Piano take; proves $< 10$ ms cumulative drift between MIDI and audio transients.
4. **S-4 (Room FTS5 & UUIDv7)**: Benchmarks 2,000 UUIDv7 inserts ($< 2$ s) and FTS5 search queries ($< 15$ ms) using `BundledSQLiteDriver`.
5. **S-5 (Navigation 3)**: Validates state preservation and predictive back on Android 11–15.

---

## 📂 Project Document Index

The repository contains authoritative design, product, and engineering specifications:

| Document | Format | Description | Authority |
| :--- | :--- | :--- | :--- |
| **[`SongPad_Phase_1_Development_Specification.md`](SongPad_Phase_1_Development_Specification.md)** | Markdown | Master engineering architecture, module structure, frozen contracts, threading, testing strategy. | **Authoritative for architecture & code** |
| **[`SongPad_Phase_0_Closure_Specification.docx`](SongPad_Phase_0_Closure_Specification.docx)** | Word Doc | Product requirements, UX states, information architecture, critical user flows, V1/V2 boundaries. | **Authoritative for product behavior** |
| **[`Rough_Plan_we_re_using.md`](Rough_Plan_we_re_using.md)** | Markdown | Team distribution, dependency graph, and overall 10–14 week delivery roadmap. | **Authoritative for sequencing** |
| **[`Capture.html`](Capture.html)** | Interactive HTML | Design prototype and UI exploration bundle (SongPad & MelodyStudio design system). | Visual & interaction reference |
| **[`CONTRIBUTING.md`](CONTRIBUTING.md)** | Markdown | Developer guidelines, PR checklist, branching strategy, and code conventions. | Process & conventions |
| **[`ARCHITECTURE.md`](ARCHITECTURE.md)** | Markdown | Deep dive into audio pipelines, threading, storage layout, and crash-safety rules. | Technical reference |
| **[`docs/adr/`](docs/adr/README.md)** | Markdown | One ADR per locked decision, the Research Pass B overrides, and placement decisions with rejected alternatives. | Decision record |
| **[`docs/CONTRACTS_FROZEN.md`](docs/CONTRACTS_FROZEN.md)** | Markdown | Where each frozen contract lives and the change process. | Contract register |
| **[`docs/spikes/`](docs/spikes/README.md)** | Markdown | Spike protocols, thresholds and results (S-4 generated; S-1/2/3/5 pending hardware). | Spike record |

---

## 🛠️ Getting Started

### Prerequisites

- **Java Development Kit**: JDK 17 (recommended: OpenJDK 17)
- **Android SDK**: API 36 compile/target SDK, API 30 minimum SDK (Android 11+)
- **Android NDK**: 28.2.13676358 (from the version catalog; installed automatically by the SDK manager)
- **CMake**: 3.22.1+
- **Gradle**: 8.14.3 via the wrapper; AGP 8.13.2, Kotlin 2.3.21, KSP 2.3.11, Hilt 2.58 (see ADR-0032 for why these are pinned together)

### Environment Setup

Ensure `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) is configured in your shell environment:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
```

Clone the repository and inspect the branch:

```bash
git clone <repo-url> SongPad
cd SongPad
git status
```

### Build and test

```bash
./gradlew checkModuleBoundaries            # module dependency rules
./gradlew :core:model:test :core:data:testing:test :spikes:s4-fts5:test   # JVM tier, seconds
./gradlew testDebugUnitTest                # every Android module's JVM tests (fakes, no emulator)
./gradlew assembleDebug                    # APK incl. the Oboe native library for arm64-v8a / armeabi-v7a / x86_64
./gradlew :spikes:s4-fts5:run              # regenerates docs/spikes/S-4-report.md
./gradlew connectedDebugAndroidTest        # Room + migration tests; needs a device or emulator
```

---

## 🛡️ License

Confidential & Proprietary. All rights reserved.
