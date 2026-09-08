# ADR-0026: Research Pass B override: Oboe sampler over SoundPool

**Status:** Accepted (override of Research Pass B)
**Date:** 2026-09-07

## Context

Research Pass B specified SoundPool for the piano.

## Decision

Oboe polyphonic sampler (ADR-0015). SoundPool gives no control over voice stealing, buffer size or velocity layering and adds unmeasurable latency.

## Rejected alternatives

- SoundPool — rejected as above.

## Consequences

The keyboard waits on S-2/S-3; nothing in the data model depends on the engine choice.

## References

- spec §4.1 Piano engine
- spec §8.1
