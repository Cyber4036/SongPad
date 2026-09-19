# ADR-0015: Oboe polyphonic sampler for the piano engine

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Touch-to-tone latency is the whole product for the piano; only native audio with exclusive-mode output gets there.

## Decision

An Oboe output stream driving a C++ polyphonic sampler with mono samples and 3–4 velocity layers (~15–25 MB of assets). Gated on spikes S-2 (latency) and S-3 (alignment) returning go verdicts — both PASSED on the Galaxy A02 (docs/spikes/S-2.md, docs/spikes/S-3.md, 2026-09-20). Built: `piano_engine.{h,cpp}` (32-voice pool, oldest-then-quietest stealing, pitch-shift resampling, looped sustain, unit-tested on the host under ASan/UBSan in `core/audio/src/test/cpp/piano_engine_test.cpp`), `piano_oboe_stream.{h,cpp}` (the Oboe output stream), `piano_jni.cpp`, and the Kotlin `PianoSamplerEngine` contract/impl in `core:model`/`core:audio`. See ADR-0033 for the asset-sourcing deviations (velocity layer count, PCM vs. compressed shipping, key range) this required.

## Rejected alternatives

- `SoundPool` — Research Pass B specified it; insufficient control over voice management, latency and velocity layers (ADR-0026).

## Consequences

The MIDI capture path (`MidiCaptureEngineImpl`, `events.log`, SMF writer) is complete and JVM-tested so the keyboard can plug in without touching the data model. `PianoKeyboard` (feature:capture) is the Canvas keyboard §8.2 calls for, replacing the temporary `PianoTapPad` (`ToneGenerator`) stub.

## References

- spec §4.1 Piano engine
- spec §8.1
- spec §21.1 S-2/S-3
