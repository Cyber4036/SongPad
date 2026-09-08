# ADR-0010: Headerless raw PCM during capture

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

A process kill mid-recording must lose at most ~1 s of audio, and the surviving file must be playable without repair.

## Decision

Capture writes headerless 48 kHz/16-bit/mono PCM to `takes/{id}/audio.pcm.tmp`, flush + fsync every 48,000 samples (~1 s), then atomic `rename` to `audio.pcm` on a clean stop. `RawPcmDataSource` synthesizes a 44-byte WAV header at play time; recovery (§9.4) needs no metadata because the format is a constant.

## Rejected alternatives

- Writing a container live (M4A/WAV) — header fields cannot be finalized on abrupt kill.

## Consequences

The `.pcm.tmp` → `.pcm` rename is the commit marker used by orphan detection (§9.3) and recovery. Raw PCM is deleted only after a verified transcode.

## References

- spec §4.1 On-disk capture format
- spec §7.3
- core/audio/src/main/kotlin/com/songpad/core/audio/capture/PcmWriter.kt
