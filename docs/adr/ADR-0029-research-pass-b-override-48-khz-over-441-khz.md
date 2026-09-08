# ADR-0029: Research Pass B override: 48 kHz over 44.1 kHz

**Status:** Accepted (override of Research Pass B)
**Date:** 2026-09-07

## Context

Research Pass B specified 44.1 kHz.

## Decision

48 kHz (ADR-0023). 44.1 kHz forces a HAL resample on most devices and is not a valid MPEG-1 rate advantage for our MP3 path either (48 kHz is).

## Rejected alternatives

- 44.1 kHz — rejected as above.

## Consequences

None.

## References

- spec §4.1 Sample rate / format
