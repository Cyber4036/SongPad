# ADR-0015: Oboe polyphonic sampler for the piano engine

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Touch-to-tone latency is the whole product for the piano; only native audio with exclusive-mode output gets there.

## Decision

An Oboe output stream driving a C++ polyphonic sampler with mono samples and 3–4 velocity layers (~15–25 MB of assets). NOT STARTED: the delegation prompt gates all sampler work on spikes S-2 (latency) and S-3 (alignment) returning go verdicts. `PianoCaptureGate.ENABLED = false` keeps the Piano pills disabled until then.

## Rejected alternatives

- `SoundPool` — Research Pass B specified it; insufficient control over voice management, latency and velocity layers (ADR-0026).

## Consequences

The MIDI capture path (`MidiCaptureEngineImpl`, `events.log`, SMF writer) is complete and JVM-tested so the keyboard can plug in without touching the data model.

## References

- spec §4.1 Piano engine
- spec §8.1
- spec §21.1 S-2/S-3
