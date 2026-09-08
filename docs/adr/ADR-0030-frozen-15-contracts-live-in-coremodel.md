# ADR-0030: Frozen §15 contracts live in :core:model

**Status:** Accepted (deviation from spec placement, raised)
**Date:** 2026-09-07

## Context

The spec places contracts in the module that implements them (`:core:audio`, `:core:midi`, `:core:data`). That makes use cases and fakes depend on Android modules, so the vertical slice cannot be tested on the JVM.

## Decision

All §15 interfaces (`AudioRecorderEngine`, `PlaybackController`, `MidiCaptureEngine`, `TakeRepository`, `SongRepository`, `SearchEngine`) and the non-frozen repository interfaces are declared in the pure-Kotlin `:core:model`. Implementations stay exactly where §15 says (`OboeAudioRecorderEngine` in `:core:audio`, etc.) and are bound there with Hilt. The freeze discipline (lead sign-off for signature changes) applies to the files in `core/model/.../audio`, `.../midi`, `.../data/Repositories.kt`.

## Rejected alternatives

- Contracts in implementing modules — every ViewModel test would need Android; fakes could not live in a `kotlin-jvm` module.

## Consequences

`:core:data:testing` (fakes) and `UseCaseTest` run in seconds without an emulator. docs/CONTRACTS_FROZEN.md lists the exact files.

## References

- spec §15
- docs/CONTRACTS_FROZEN.md
