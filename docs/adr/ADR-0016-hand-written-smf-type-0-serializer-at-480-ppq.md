# ADR-0016: Hand-written SMF Type 0 serializer at 480 PPQ

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

V1 has no external MIDI hardware; a performance is a list of key events on the audio frame timeline.

## Decision

`SmfWriter` in `:core:midi` writes the bytes in docs/midi/SMF_BYTE_SCHEMA.md: MThd, format 0, one track, 480 PPQ, tempo 120 BPM, Note On/Off on channel 0 with VLQ deltas. At 48 kHz / 480 PPQ / 120 BPM one tick is exactly 50 frames, so frame→tick is integer division with no drift.

## Rejected alternatives

- `android.media.midi` — solves external/virtual hardware V1 does not have.
- `ktmidi` (MIT) — the only approved dependency if a library is ever preferred; ~200 lines did not justify it.

## Consequences

Byte-exact JVM tests (`SmfWriterTest`, `VlqTest`) are the cheapest high-confidence tests in the project.

## References

- spec §4.1 MIDI
- spec §8.4
- docs/midi/SMF_BYTE_SCHEMA.md
