# Architecture Decision Records

One record per LOCKED row of spec §4.1 (ADR-0001…0023), plus the four Research Pass B overrides, the AlarmManager ban, and the placement decisions made while building Phase 1. A LOCKED decision is changed only by a superseding ADR signed off by the technical lead.

| ADR | Title | Status |
| --- | --- | --- |
| ADR-0001 | Kotlin 2.x with coroutines and Flow | Accepted (LOCKED, spec §4.1) |
| ADR-0002 | Jetpack Compose with Material 3 base and custom Canvas | Accepted (LOCKED, spec §4.1) |
| ADR-0003 | Navigation 3 (androidx.navigation3 1.1.x) | Accepted (LOCKED, spec §4.1) |
| ADR-0004 | Hilt with KSP for dependency injection | Accepted (LOCKED, spec §4.1) |
| ADR-0005 | Room 2.8 with BundledSQLiteDriver | Accepted (LOCKED, spec §4.1) |
| ADR-0006 | UUIDv7 primary keys stored as TEXT | Accepted (LOCKED, spec §4.1) |
| ADR-0007 | FTS5 search with unicode61, bm25 and prefix indexes | Accepted (LOCKED, spec §4.1) |
| ADR-0008 | Oboe (C++/NDK) for the capture stream | Accepted (LOCKED, spec §4.1) |
| ADR-0009 | Dedicated capture thread — Dispatchers.IO forbidden | Accepted (LOCKED, spec §4.1) |
| ADR-0010 | Headerless raw PCM during capture | Accepted (LOCKED, spec §4.1) |
| ADR-0011 | Two stored formats: AAC-LC M4A (Standard) or FLAC (Lossless), per take | Accepted (LOCKED, spec §4.1) |
| ADR-0012 | Media3 / ExoPlayer for playback, one instance app-wide | Accepted (LOCKED, spec §4.1) |
| ADR-0013 | Transcode with MediaCodec + MediaMuxer in a WorkManager CoroutineWorker | Accepted (LOCKED, spec §4.1) |
| ADR-0014 | LAME via NDK for MP3 export only | Accepted (LOCKED, spec §4.1) |
| ADR-0015 | Oboe polyphonic sampler for the piano engine | Accepted (LOCKED, spec §4.1) |
| ADR-0016 | Hand-written SMF Type 0 serializer at 480 PPQ | Accepted (LOCKED, spec §4.1) |
| ADR-0017 | WorkManager only — AlarmManager forbidden | Accepted (LOCKED, spec §4.1) |
| ADR-0018 | Microphone-typed foreground service started from a visible screen | Accepted (LOCKED, spec §4.1) |
| ADR-0019 | App-specific storage without a storage permission | Accepted (LOCKED, spec §4.1) |
| ADR-0020 | Export via SAF CreateDocument and FileProvider + ACTION_SEND | Accepted (LOCKED, spec §4.1) |
| ADR-0021 | Audio and MIDI excluded from Auto Backup | Accepted (LOCKED, spec §4.1) |
| ADR-0022 | Layered UDF, with MVI only on the capture screen | Accepted (LOCKED, spec §4.1) |
| ADR-0023 | 48 kHz · 16-bit · mono linear PCM | Accepted (LOCKED, spec §4.1) |
| ADR-0024 | FTS5 as a standard content-storing table with triggers, not external content | Accepted (deviation from spec wording, raised) |
| ADR-0025 | Research Pass B override: FTS5 over FTS4 | Accepted (override of Research Pass B) |
| ADR-0026 | Research Pass B override: Oboe sampler over SoundPool | Accepted (override of Research Pass B) |
| ADR-0027 | Research Pass B override: URGENT_AUDIO thread over Dispatchers.IO | Accepted (override of Research Pass B) |
| ADR-0028 | AlarmManager is banned | Accepted (LOCKED) |
| ADR-0029 | Research Pass B override: 48 kHz over 44.1 kHz | Accepted (override of Research Pass B) |
| ADR-0030 | Frozen §15 contracts live in :core:model | Accepted (deviation from spec placement, raised) |
| ADR-0031 | TakeFileStore lives in :core:model | Accepted (placement decision, raised) |
| ADR-0032 | Toolchain pins: AGP 8.13 / Kotlin 2.3 / compileSdk 36 and the AndroidX ceiling that implies | Accepted (engineering, revisit after Milestone 1) |
| ADR-0033 | Piano sampler implementation choices (asset format, velocity layers, key range) | Accepted (implementation detail inside LOCKED ADR-0015, raised) |
