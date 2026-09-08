# ADR-0004: Hilt with KSP for dependency injection

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Five modules bind §15 contracts to implementations, plus ViewModels, HiltWorkers and a foreground service. The graph must fail at compile time, not on first launch.

## Decision

Hilt 2.58 via KSP (never KAPT). Contracts are bound in the module that implements them (`DataBindingsModule`, `AudioBindingsModule`, `MidiModule`); features add `ViewModelComponent` modules for use cases and `SingletonComponent` modules for seams (`MidiTakeBinder`, `NudgeNotifier`, `ExportJobRunner`). `SongPadApplication` supplies `HiltWorkerFactory` and the manifest removes WorkManager's default initializer.

## Rejected alternatives

- Koin — runtime resolution, weaker compile-time safety.
- Metro — KMP-oriented, no benefit for an Android-only team.
- Manual DI — does not scale across five parallel streams.

## Consequences

Hilt 2.58 is the last line accepting AGP 8.x; Hilt 2.60+ requires AGP 9. Moving to AGP 9 is a separate, deliberate upgrade (see docs/adr/ADR-0032).

## References

- spec §4.1 DI
