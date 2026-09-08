# ADR-0008: Oboe (C++/NDK) for the capture stream

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Voice+Piano synchronization (§8.3) lives exactly at the boundary between capture and synthesis. A mixed `AudioRecord`/Oboe stack would put two clocks and a JNI seam there.

## Decision

Oboe input stream (48 kHz, mono, I16, LowLatency, Exclusive with Shared/VoiceRecognition fallback) in `capture_engine.cpp`, a lock-free SPSC ring buffer (≥ 2 s), and a Kotlin writer thread. `OboeAudioRecorderEngine` is the thin facade implementing the frozen `AudioRecorderEngine` contract.

## Rejected alternatives

- `MediaRecorder` — black box; container corruption on process death is unrecoverable.
- `AudioRecord` (Java) — workable, but splits the audio stack across two languages when Oboe is already required for the piano. It remains the documented fallback on a dedicated URGENT_AUDIO thread if S-1 shows Oboe input is unreliable on low-end Transsion hardware.

## Consequences

`:core:audio` is the only module allowed a CMake build (enforced in root build.gradle.kts). S-1 (30-minute capture + `am kill` on Xiaomi/HyperOS and Transsion) is pending — see docs/spikes/S-1.md.

## References

- spec §4.1 Audio capture API
- spec §4.2
- spec §7.1
