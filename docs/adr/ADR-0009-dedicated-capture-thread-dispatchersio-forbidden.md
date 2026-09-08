# ADR-0009: Dedicated capture thread — Dispatchers.IO forbidden

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Coroutine dispatchers are shared and elastic; under load a capture loop on them is starved and drops samples.

## Decision

The capture read loop runs on Oboe's callback thread and never allocates, blocks, locks or logs. A separate writer `Thread` at `THREAD_PRIORITY_AUDIO` drains the ring buffer to disk and computes peaks. No coroutine dispatcher touches audio data until it is a `SharedFlow<Float>` at 10 Hz with `DROP_OLDEST`.

## Rejected alternatives

- `Dispatchers.IO` for the capture loop — Research Pass B specified it; overridden (ADR-0027).
- `Dispatchers.Default` — same problem, plus CPU contention with Compose.

## Consequences

Reviewers reject any coroutine on the audio path. The only coroutine near capture is the Hilt-scoped state machine in `OboeAudioRecorderEngine`, which talks to the threads via atomics and the ring buffer.

## References

- spec §4.1 Capture thread
- spec §7.2
