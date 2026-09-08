# ADR-0022: Layered UDF, with MVI only on the capture screen

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Five parallel streams need a shared, predictable architecture; only the recorder has a state machine complex enough to justify a reducer.

## Decision

Compose UI → ViewModel (`StateFlow`) → use case → repository → data source. Every §17 state set is a sealed hierarchy. The capture screen alone uses `MviStore<CaptureState, CaptureIntent, CaptureEffect>` — one serial reducer enforcing the recorder's legal transitions.

## Rejected alternatives

- MVI everywhere — added ceremony with no matching complexity outside capture.

## Consequences

ViewModels are constructed from contracts and pure use cases, so every feature has JVM tests against `:core:data:testing` fakes.

## References

- spec §4.1 Architecture
- spec §7.2
- core/model/src/main/kotlin/com/songpad/core/model/mvi/MviStore.kt
