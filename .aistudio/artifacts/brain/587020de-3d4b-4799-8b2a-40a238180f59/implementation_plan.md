# Galaxy Device Compatibility, Lossless FLAC & Piano Recording Playback Fix

This update resolves the three device-specific failures observed on the Samsung Galaxy (Android 11 / API 30): the `NoSuchMethodError: readNBytes` crash when importing exported songs, the `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` playback failure on recordings made after enabling Lossless (FLAC) mode or recording Piano takes, and the silent playback failure state on the main Songs Library screen.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX decisions incorporate your confirmed answers from Phase 1.

- **Confirmed Decision 1 — Fix Lossless FLAC & Standard M4A Transcoding**:
  - After making your first two working recordings in Standard quality, switching to **Lossless (FLAC)** caused every subsequent recording (`Piano take 2`, `Piano take 4`, `Voice + Piano take 4`, `Voice take 2`) to be encoded via the FLAC `MediaCodec` pipeline, which was stripping buffers flagged with `BUFFER_FLAG_CODEC_CONFIG`—discarding the mandatory 42-byte `fLaC` magic header and `STREAMINFO` block.
  - We will write the `fLaC` + `STREAMINFO` header on every FLAC transcode (preserving codec config buffers and synthesizing a canonical 42-byte `fLaC` + `STREAMINFO` header when the hardware encoder omits it) and harden AAC/M4A muxing so `MediaMuxer.stop()` errors or non-monotonic timestamps never overwrite `audio.pcm`.
- **Confirmed Decision 2 — Auto-Repair Existing Damaged Takes & Fall Back to Raw PCM**:
  - When playing or scanning existing takes on the device, if a `.flac` file is missing its 42-byte `fLaC` + `STREAMINFO` header (because it was transcoded before this fix), the playback and recovery pipeline will automatically prepend the canonical 48 kHz / 16-bit / mono `fLaC` + `STREAMINFO` header in-place and regenerate any missing waveform peaks—restoring your existing Galaxy takes (`Piano take 2`, `Piano take 4`, `Voice + Piano take 4`, `Voice take 2`) without losing your recordings.
  - If a transcoded file is ever unreadable and raw `audio.pcm` (or `.pcm.tmp`) still exists in the take folder, playback automatically falls back to the raw PCM stream.
- **Confirmed Decision 3 — Surface Clear Playback Error Banner on Songs Library Screen**:
  - Previously, `LibraryViewModel` mapped `PlaybackState.Error` to `null`, causing failed takes in the Unfiled list to either silently ignore taps or leave the `RibbonPlayer` frozen at `0:00`. The Library screen will now display a clear, dismissible inline error banner whenever a take cannot be played, matching the Song Detail screen's feedback.

---

### 1. Overview & Core Concept

- **What It Does**:
  1. **Android 8–12 (API 26–32) Import Compatibility**: Replaces Java 11 / Android 13+ (`API 33`) `InputStream.readNBytes(int)` calls in the import engine with a universal bounded stream reader that works across all supported Android versions (`minSdk 26` through `compileSdk 36`).
  2. **Valid Lossless FLAC & Verified M4A Transcoding**: Ensures every Lossless `.flac` recording begins with a valid `fLaC` + `STREAMINFO` header, verifies transcoded output against actual encoded frame timestamps and container extractability before deleting `audio.pcm`, and automatically repairs existing headerless `.flac` recordings on the device.
  3. **Reliable Multi-Session Piano & Mic Capture on Budget HALs**: Fully closes and releases the low-latency Oboe piano output stream on `disarm()` (rather than leaving it in `Stopped` state, which stalls AAudio callback resumption on MediaTek/Samsung HALs after the first recording) and re-verifies stream health when starting capture so Piano-only and Voice + Piano takes always capture audible samples and waveform peaks.
  4. **Transparent Playback Error Feedback**: Surfaces playback errors directly on the Songs Library screen and resets the player cleanly instead of leaving the transport stuck at `0:00`.
- **Target Audience / Persona**: Songwriters capturing vocal and piano ideas across both older/budget Android phones (Android 8–12, Samsung Galaxy A-series, MediaTek/Exynos HALs) and modern flagships (Pixel, Android 13–16).
- **Key Value**: Guarantees that every recording—whether Standard M4A or Lossless FLAC, Voice or Piano-only—plays back reliably and can be exported and re-imported on any Android device.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Importing an Exported Song or Audio File on Android 11/12**:
     - Selecting an exported `.zip` / `.songpad` / `.bin` bundle, audio file, or `.txt` lyric sheet in the **Import to SongPad** bottom sheet reads magic bytes and waveform samples using a universal byte-buffer loop, completing the import and showing the *"Imported '<SongName>'"* confirmation card with the **Open imported song** action.
  2. **Playing Existing & Newly Recorded Lossless (FLAC) or Piano Takes**:
     - Tapping **Play** on any existing take that previously failed with `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (such as `Piano take 2`, `Piano take 4`, `Voice + Piano take 4`, or `Voice take 2`) triggers an automatic header inspection: if the `.flac` file lacks its `fLaC` magic header, SongPad repairs the 42-byte `fLaC` + `STREAMINFO` header and rebuilds `peaks.wave` on the fly before handing the URI to ExoPlayer.
     - Newly recorded Piano-only, Voice + Piano, and Voice takes in both **Standard** and **Lossless** modes play immediately with full scrubbable waveforms in the `RibbonPlayer`.
  3. **Playback Error Banner on Songs Library Screen**:
     - If a take's audio file is genuinely missing or corrupted beyond repair, tapping Play on the Songs Library screen displays a clear, dismissible inline alert banner above the Unfiled / Songs list (e.g., *"Playback failed: <reason>"*) and resets the play button state cleanly.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Retains SongPad's warm editorial notebook (`SongPad`) and cool studio (`Melody Studio`) theme families.
  - *Color Palette & Mood*: Uses `SongPadTheme.colors` semantic tokens (`c.surface`, `c.accent`, `c.danger`, `c.text`, `c.textSecondary`, `c.divider`) for the Library playback error banner, matching Song Detail's error presentation.
  - *Typography & Hierarchy*: `Caprasimo` display headers paired with `Figtree` body and tabular-numeral (`tnum`) transport timers.
  - *Interactive Feedback & Motion*: Instant play/pause state transitions in `UnfiledTakeRow`, `TakeListItem`, and `RibbonPlayer`, with automatic waveform peak regeneration when repairing legacy takes.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Universal Pre-API-33 Byte Reader Instead of `InputStream.readNBytes`**
  - *Chosen Approach*: Implement a dedicated `readUpToNBytes(count: Int): ByteArray` extension on `InputStream` using standard `read(buffer, offset, remaining)` loops.
  - *Why*: `InputStream.readNBytes(int)` was added to Android's `core-oj.jar` in API 33 (Android 13). Even though it compiles against SDK 36, calling it on Android 11 (API 30) throws `NoSuchMethodError` (`java.lang.Error`). A standard loop works identically on API 26–36.
- **Decision 2: Self-Healing FLAC Header Repair + Strict Transcode Verification**
  - *Chosen Approach*:
    1. During FLAC transcoding, write `BUFFER_FLAG_CODEC_CONFIG` output buffers and ensure the file starts with the 42-byte `fLaC` + `STREAMINFO` header (48 kHz, 1 channel, 16-bit, total sample count).
    2. Before `TranscodeWorker` deletes `audio.pcm`, verify that `MediaExtractor` can actually parse at least one audio track from the transcoded file and that actual encoded output timestamps (`lastPtsUs`) match the input PCM duration.
    3. Before playing any `.flac` take (and during startup recovery), inspect the first 4 bytes: if `"fLaC"` is missing, atomically prepend the 42-byte `fLaC` + `STREAMINFO` header and regenerate `peaks.wave` if needed.
  - *Why*: Fixes both future recordings and the takes already recorded on your Galaxy without requiring you to re-record them.
- **Decision 3: Full Close-and-Reopen Lifecycle for the Oboe Piano Output Stream**
  - *Chosen Approach*: When `PianoSamplerEngine.disarm()` is called (or when capture starts and detects a stalled piano stream), fully close the native `PianoOutputStream` (`closeLocked()`) rather than leaving it in `Stopped` state via `requestStop()`, and reset `SamplerTap` when recording starts.
  - *Why*: On Android 11 MediaTek/Samsung AAudio HALs, calling `requestStart()` on a previously stopped low-latency stream or opening a concurrent input stream can leave the output callback thread dormant, causing subsequent Piano recordings to capture digital silence and interfering with ExoPlayer's audio track.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        Import & API 26–32 Compatibility                      │
│                                                                              │
│  ImportBottomSheet ──► SongPadImporterImpl                                   │
│                         ├─ readUpToNBytes(12)  [Replaces API 33 readNBytes]  │
│                         ├─ Zip / Audio / Lyrics Inspector                    │
│                         └─ PeakReducer + PcmDecoder Waveform Generator       │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                 Capture, Transcode & Self-Healing Playback                   │
│                                                                              │
│  ┌──────────────────────────────┐      ┌──────────────────────────────────┐  │
│  │  Oboe Capture & Piano Engine │      │   TranscodeWorker & PcmTranscoder│  │
│  │  • PianoOutputStream closes  │      │   • FLAC writes fLaC + STREAMINFO│  │
│  │    cleanly on disarm()       │─────►│   • AAC enforces monotonic PTS   │  │
│  │  • Re-verifies stream state  │      │     & checks MediaMuxer.stop()   │  │
│  │    when CaptureEngine starts │      │   • Container sniff verification │  │
│  │  • SamplerTap primes cleanly │      │     before deleting audio.pcm    │  │
│  └──────────────────────────────┘      └─────────────────┬────────────────┘  │
│                                                          │                   │
│                                                          ▼                   │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │               Media3PlaybackController & FlacHeaderRepair              │  │
│  │  1. Inspects .flac takes: if "fLaC" magic is missing, prepends the     │  │
│  │     42-byte fLaC + STREAMINFO header in-place & rebuilds peaks.wave    │  │
│  │  2. If transcoded container is unreadable & audio.pcm exists, falls    │  │
│  │     back to RawPcmDataSource (synthesized 44-byte WAV header)          │  │
│  │  3. Resets ExoPlayer cleanly on error & emits PlaybackState.Error      │  │
│  └──────────────────┬────────────────────────────────────┬────────────────┘  │
│                     │                                    │                   │
│                     ▼                                    ▼                   │
│  ┌──────────────────────────────────┐  ┌──────────────────────────────────┐  │
│  │          LibraryViewModel        │  │        SongDetailViewModel       │  │
│  │  • Clears stuck Loading/Playing  │  │  • Displays inline error banner  │  │
│  │  • Surfaces dismissible playback │  │  • Reloads repaired waveform     │  │
│  │    error banner in LibraryScreen │  │    peaks automatically           │  │
│  └──────────────────────────────────┘  └──────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - **FLAC 42-Byte Stream Header Synthesis & Repair**:
    - Standard FLAC stream marker: `4 bytes` (`"fLaC"`, `0x66 0x4C 0x61 0x43`), followed by a `4-byte` metadata block header (`0x80 0x00 0x00 0x22` — last-metadata-block flag `1`, type `0` = `STREAMINFO`, length `34` bytes) and `34-byte` `STREAMINFO` payload configured for `48,000 Hz`, `1 channel`, `16 bits per sample`, and total sample count derived from the source PCM length.
  - **`LibraryState` Playback Error State**:
    - Expose `playbackError: String?` (with `dismissPlaybackError()`) in `LibraryViewModel` so `LibraryScreen` renders an inline dismissible error banner whenever `PlaybackState.Error` occurs, rather than silently dropping the error or leaving `NowPlaying` stuck at `0:00`.
  - **Playable Fallback for `FAILED` Transcodes**:
    - Update `Take.isPlayable` and `Media3PlaybackController` so takes whose background transcode failed (leaving valid raw `audio.pcm` on disk) remain playable via `RawPcmDataSource`.

- **Interactive Component & State Mapping**:
  - **Import Flow (`SongPadImporterImpl`)**:
    - Replaces all `readNBytes` calls with `InputStream.readUpToNBytes(maxBytes)` so magic-byte detection and synthetic waveform fallback succeed on Android 8–12 (API 26–32).
    - Also runs the FLAC header check when importing `.flac` takes from bundles so any bundle exported from a previously affected session repairs cleanly on import.
  - **Capture & Piano Lifecycle (`PianoOutputStream`, `PianoSamplerEngineImpl`, `CaptureViewModel`)**:
    - `PianoOutputStream::stop()` closes and resets the native Oboe stream (`closeLocked()`) instead of leaving an idle low-latency stream open on the HAL.
    - When transitioning from `CaptureState.Ready` to `CaptureState.Recording` in `PIANO` or `VOICE_PIANO` mode, `CaptureViewModel` and `PianoSamplerEngineImpl` ensure the piano stream is actively running (`StreamState::Started`) and `SamplerTap` is freshly reset so no stale backlog or dead stream occurs.
  - **Playback & Repair Flow (`Media3PlaybackController`, `LibraryViewModel`, `SongDetailViewModel`)**:
    - When `play(source)` is invoked on a `.flac` file, `FlacHeaderRepair.ensureValidHeader(file)` checks the first 4 bytes and prepends the 42-byte `fLaC` + `STREAMINFO` header if missing.
    - When `PeaksLoader` loads `peaks.wave` for a repaired take whose peaks were empty or all-zero, it decodes and writes `peaks.wave` so the `RibbonPlayer` displays the real waveform.
