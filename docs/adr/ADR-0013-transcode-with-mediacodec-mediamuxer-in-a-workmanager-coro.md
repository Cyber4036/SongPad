# ADR-0013: Transcode with MediaCodec + MediaMuxer in a WorkManager CoroutineWorker

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Saving must return instantly and survive process death; the transcode is CPU work that belongs off the UI and off the save path.

## Decision

`FinalizeTakeUseCase` commits the row with `status = TRANSCODING` and enqueues `TranscodeWorker` (unique per take, KEEP). `PcmTranscoder` encodes AAC-LC → M4A via `MediaMuxer` or FLAC via the MediaCodec FLAC encoder, verifies the output, then deletes the raw PCM. Failure after three attempts marks the take FAILED but keeps the PCM.

## Rejected alternatives

- Inline transcode on save — blocks the user and dies with the process.

## Consequences

A take is playable from raw PCM the instant it is saved; `Transcoding` is a correctness state, not a spinner (§17).

## References

- spec §4.1 Transcode
- core/audio/src/main/kotlin/com/songpad/core/audio/transcode/
