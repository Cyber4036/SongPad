# SongPad Engineering & Contribution Guide

This guide governs development practices for the SongPad project. All contributors must follow these conventions to maintain architectural integrity, avoid blocking parallel streams, and ensure deterministic, crash-safe performance.

---

## 🧭 Core Principles

1. **Capture first. Organize later.**  
   Every architectural decision must preserve zero-friction idea capture. Audio safety always takes precedence over metadata operations.
2. **Freeze the Contracts Early.**  
   Per the Phase 1 Specification (§15), core interfaces are frozen at Milestone 0. You can code against interfaces and fakes in `:core:data:testing` without waiting for implementation details.
3. **Feature Modules Never Depend on Each Other.**  
   `:feature:*` modules communicate exclusively via route keys defined in `:app` and domain models in `:core:model`.
4. **Pure Domain Model.**  
   `:core:model` contains zero Android dependencies. Domain business logic must remain 100% JVM unit testable.
5. **Deterministic Audio Threading.**  
   Audio capture loops must run on a dedicated thread with `Process.setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)`. Running the capture read loop on `Dispatchers.IO` or `Dispatchers.Default` is **strictly prohibited**.

---

## 🌿 Git & Branching Strategy

- **`main`**: Production and beta integration branch. Always deployable and passing CI.
- **`feature/<module>-<description>`**: Used for module-scoped feature development (e.g., `feature/capture-mvi-flow`, `feature/song-section-reorder`).
- **`spike/<spike-id>-<description>`**: Used for Stage 0 spikes (e.g., `spike/s1-oboe-recovery`, `spike/s3-midi-alignment`).
- **`fix/<issue-description>`**: Bug fixes and hotfixes.

### Commit Messages

Use conventional commit messages:
- `feat(audio): implement circular ring buffer for Oboe capture`
- `fix(data): correct cascade rule on song deletion for sections`
- `test(model): add unit tests for lyrics document validator`
- `refactor(ui): extract semantic token definitions into theme wrappers`
- `docs(spec): document Stage 0 spike S-4 benchmarks`

---

## 📜 Contract Change Process (§15)

The core interfaces defined in `SongPad_Phase_1_Development_Specification.md` (§15) are:
- `AudioRecorderEngine`
- `PlaybackController`
- `MidiCaptureEngine`
- `TakeRepository`
- `SongRepository`
- `SearchEngine`

**Rule**: Any modification to a frozen method signature, parameter type, or return type requires:
1. Written rationale and approval from the Technical Lead.
2. An update to `SongPad_Phase_1_Development_Specification.md`.
3. Simultaneous updates to corresponding fakes in `:core:data:testing`.

---

## 🧪 Testing Gates & Quality Standards

Every Pull Request must pass the following test gates prior to merging:

| Layer | Requirement | Gate |
| :--- | :--- | :--- |
| **`:core:model` & Use Cases** | JVM unit tests using `runTest` and Turbine | **> 85% branch coverage** |
| **SMF Serializer** | Byte-exact verification against MIDI spec (VLQ boundaries) | 100% pass |
| **Room Migrations** | `MigrationTestHelper` verifying schema & data survival | No migration merges without test |
| **Design System** | Verification across all 4 themes (SongPad Light/Dark & MelodyStudio Light/Dark) | Visual check & no hardcoded colors |
| **Audio & Process Death** | Survival under `adb shell am kill` mid-recording | Uncorrupted partial PCM recovery |

---

## 🎨 Design System Compliance (§14)

1. **Semantic Tokens Only**: Never reference raw hex codes (e.g., `#F5EAD8`) directly in feature composables. Always use semantic roles (`bg`, `surface`, `text`, `accent`, `accent-2`, `record`, `divider`).
2. **Touch Targets**: All interactive elements must maintain a minimum touch target of **48 × 48 dp**.
3. **Canvas Drawing**: Waveform and piano canvas implementations must read accent and record colors dynamically from the active theme token layer.

---

## 🤖 CI & Physical Multi-Device Testing Strategy

Automated cloud device farms (such as nightly Firebase Test Lab matrices) have fundamental blindspots for SongPad's core value proposition:
- **Acoustic feedback & microphone nuance**: In Voice + Piano mode, the microphone acoustically captures speaker output. Remote server racks cannot evaluate whether acoustic capture sounds musical, distorted, or out of sync.
- **Perceived latency**: Only human ears and touch can validate whether touch-to-tone latency is musical.
- **Aggressive OEM behavior**: Aggressive process management on Transsion (Tecno/Infinix) and Xiaomi/HyperOS during phone calls, screen locks, and backgrounding is verified much more reliably on actual hardware in daily use.

### Testing Pipeline
1. **PR Gate (Fast & Free)**: Build, lint, and JVM unit tests (< 10 min gate).
2. **Main Gate**: KVM-accelerated emulator instrumented tests on API 30 and API 35.
3. **Physical Device Packaging**: On merge to `main` (and via manual workflow dispatch), CI packages debug APK artifacts. At the end of major development runs, team members install these builds directly onto physical target devices (Pixel baseline, Samsung, Xiaomi/HyperOS, Transsion) to validate real-world audio, latency, and crash recovery.

