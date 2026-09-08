# ADR-0028: AlarmManager is banned

**Status:** Accepted (LOCKED)
**Date:** 2026-09-07

## Context

Exact alarms carry Play policy scrutiny and Doze exemptions that a re-engagement nudge cannot justify; nothing in V1 or the Phase 0 V2 column needs sub-minute delivery.

## Decision

`AlarmManager` in any form (`set*`, `setExactAndAllowWhileIdle`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`) is forbidden. WorkManager is the only scheduler (ADR-0017). The ban lifts only for a future feature that genuinely needs exact delivery, via a superseding ADR.

## Rejected alternatives

- AlarmManager with inexact alarms — still a second scheduler with no persistence story.

## Consequences

A PR adding it is rejected by default. The nudge copy never promises delivery times (§11.4).

## References

- spec §11.1
