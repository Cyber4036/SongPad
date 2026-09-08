# ADR-0027: Research Pass B override: URGENT_AUDIO thread over Dispatchers.IO

**Status:** Accepted (override of Research Pass B)
**Date:** 2026-09-07

## Context

Research Pass B specified running the capture loop on `Dispatchers.IO`.

## Decision

Oboe's callback thread for capture and a dedicated `THREAD_PRIORITY_AUDIO` writer thread (ADR-0009). Shared dispatchers are elastic and starvation-prone.

## Rejected alternatives

- `Dispatchers.IO` — rejected as above.

## Consequences

Code review rule: no coroutine on the audio path.

## References

- spec §7.2
