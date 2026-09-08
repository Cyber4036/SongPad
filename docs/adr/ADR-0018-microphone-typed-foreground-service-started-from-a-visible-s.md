# ADR-0018: Microphone-typed foreground service started from a visible screen

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Capture must survive backgrounding; since Android 14 a microphone FGS must be started while the app is visible.

## Decision

`CaptureForegroundService` (`foregroundServiceType="microphone"`, channel `songpad_recording`, LOW importance minimum) is started by `OboeAudioRecorderEngine.start()`, which only runs from the record screen, and stopped on every teardown path. `stopWithTask=false` so a swiped task does not kill an in-flight recording.

## Rejected alternatives

- Background thread without FGS — killed by the low-memory killer.
- `dataSync` type — wrong type, subject to timeouts.

## Consequences

Play Console requires the FGS type declaration and a video demonstration; see docs/play/FOREGROUND_SERVICE.md.

## References

- spec §4.1 Foreground service
- spec §7.5
- spec §13
