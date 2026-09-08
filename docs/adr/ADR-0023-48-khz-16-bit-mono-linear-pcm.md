# ADR-0023: 48 kHz · 16-bit · mono linear PCM

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

The HAL on virtually all Android hardware runs at 48 kHz; any other capture rate is resampled on the way in.

## Decision

`AudioFormat` fixes 48 kHz, 16-bit, mono everywhere: capture, ring buffer, `.pcm` files, WAV header synthesis, transcode input, MIDI tick maths (50 frames per tick). 96 kB/s = 5.76 MB/min raw.

## Rejected alternatives

- 44.1 kHz — Research Pass B specified it; forces a HAL resample and gains nothing for a capture tool (ADR-0029).

## Consequences

Every constant derives from `AudioFormat.SAMPLE_RATE`; changing it would invalidate `MidiTiming`'s integer tick and is not a one-line edit.

## References

- spec §4.1 Sample rate / format
