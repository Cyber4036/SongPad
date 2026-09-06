# SongPad Architecture & Technical Reference

This document complements the [Master Phase 1 Development Specification](SongPad_Phase_1_Development_Specification.md), providing architectural patterns, lifecycle state models, threading constraints, and file storage blueprints.

---

## 1. Unidirectional Data Flow (UDF) & MVI

The application standardizes on a Google-recommended UDF architecture across most features and a formal MVI (Model-View-Intent) state pattern for the high-concurrency Capture flow.

```
       User / Device Action
                │
                ▼
       Intent / UI Event
                │
                ▼
        [ ViewModel ]  ◄────── StateFlow / SharedFlow ────── [ Repository / Engine ]
                │                                                    │
                ▼                                                    ▼
         State Reduction                                    Data Source / Audio HAL
                │
                ▼
       Immutable UI State
                │
                ▼
          Compose UI
```

### Layer Hierarchy

1. **Compose UI Layer (`:feature:*`, `:core:ui`)**: Stateless Composables listening to `StateFlow<UiState>` and emitting user intents via lambdas.
2. **ViewModel Layer (`:feature:*`)**: Manages UI state, handles navigation intents, and orchestrates domain use cases.
3. **Domain Use Case Layer (`:core:model`)**: Encapsulates single-responsibility business workflows (e.g., `StartRecordingUseCase`, `FinalizeTakeUseCase`). Contains pure Kotlin logic.
4. **Data Repository Layer (`:core:data`)**: Exposes reactive `Flow` queries to Room DAOs and coordinates file operations.
5. **Hardware / Audio HAL (`:core:audio`)**: Encapsulates Oboe C++ audio streams, hardware sample buffers, and audio focus listeners.

---

## 2. Audio Capture Pipeline & Threading

```
      Microphone (Hardware HAL)
                  │
                  ▼ 48 kHz, 16-bit mono
          Oboe Audio Stream (C++)
                  │
                  ▼
        Oboe Callback / Audio Thread (URGENT_AUDIO priority)
                  │
                  ▼ Native Ring Buffer (≥ 2.0s audio capacity)
                  │
        Writer Thread (Disk IO)
          ├─ Write to takes/{takeId}/audio.pcm.tmp
          ├─ fsync() every ~1.0 s of audio
          └─ Extract peaks / RMS
                  │
                  ▼ SharedFlow<Float> (10 Hz, DROP_OLDEST)
             Canvas Waveform
```

### Threading Invariants (§7.2)

- **Strict Ban on Elastic Dispatchers**: Capture read operations **must never** execute on `Dispatchers.IO`, `Dispatchers.Default`, or thread pools shared with network or Room operations.
- **Dedicated Capture Thread**: The capture read thread must be assigned `Process.THREAD_PRIORITY_URGENT_AUDIO`.
- **Ring Buffer Decoupling**: A minimum 2-second in-memory ring buffer isolates the native audio callback from flash storage write stalls or garbage collection pauses.
- **Waveform Throttling**: Waveform amplitude extraction runs on the disk writer thread (not the audio callback thread) and is pushed via `SharedFlow` with `DROP_OLDEST` at 10 Hz.

---

## 3. Storage Layout & Crash Resilience

All application data resides in app-specific internal storage (`Context.filesDir`), exempt from runtime storage permissions and scoped storage restrictions.

```
/data/user/0/com.songpad.app/files/
├── takes/
│   └── {takeId}/
│       ├── audio.pcm.tmp       # In-flight raw PCM during active recording
│       ├── audio.pcm           # Committed raw PCM (prior to transcode)
│       ├── audio.m4a           # Final Standard quality asset (AAC-LC, 128 kbps)
│       ├── audio.flac          # Final Lossless quality asset (FLAC)
│       ├── waveform.peaks      # Downsampled 100 Hz binary float peaks
│       ├── events.log          # In-flight binary MIDI log (Piano mode)
│       └── performance.mid     # Final SMF Type 0 MIDI file
├── samples/
│   └── piano/                  # Bundled multi-velocity piano sample banks
└── exports/                    # Temporary staged ZIP bundles for SAF sharing
```

### Crash Recovery Strategy (§7.3, §9.4)

1. **Headerless PCM**: Because `.pcm` contains no container headers, a truncated file remains fully decodable up to its final byte on disk.
2. **Periodic fsync**: `BufferedOutputStream.flush()` and `FileDescriptor.sync()` run at ~1-second intervals. At most ~1 second of audio is lost during sudden battery removal or OS kill.
3. **Atomic Rename**: On clean capture completion, `audio.pcm.tmp` is atomically renamed to `audio.pcm`.
4. **Cold Start Recovery**: At cold start, the database is queried for takes in `RECORDING` status. Unfinished `.pcm.tmp` files are detected and surfaced for immediate user recovery or clean discard.
5. **Two-Phase Deletion**: Soft deletion sets `sync_state = DELETED` and records `deleted_at`. Hard file cleanup is scheduled via daily `WorkManager` orphan cleanup with a 7-day safety retention grace period.

---

## 4. Clock Synchronization (Voice + Piano)

In **Piano + Voice** mode, both the microphone audio and the on-screen keyboard synthesis share a single hardware timeline.

- **Audio Frame Master Clock**: The audio frame index serves as the absolute timeline.
- **Anchor Pairing**: When a key is tapped on the UI thread, the event timestamp `System.nanoTime()` is correlated against the active audio stream frame index via `AudioRecorderEngine.frameIndexFor(eventTimeNanos)`.
- **MIDI Alignment**: Key events are recorded in `events.log` with precise frame offsets, ensuring the resulting `.mid` file stays locked to vocal transients within $< 10$ ms over a 5-minute take.
