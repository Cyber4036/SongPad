# ADR-0003: Navigation 3 (androidx.navigation3 1.1.x)

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

The app is local-first and ViewModel-centric; the back stack is app state that must survive process death and drive the bottom bar.

## Decision

Navigation 3 with a developer-owned `rememberNavBackStack` of `@Serializable` `NavKey`s (`LibraryKey`, `RecordKey`, `SongDetailKey`, `SearchKey`, `NudgesKey`, `ExportKey`, `SettingsKey`). Keys carry ids only, never domain objects. Decorators: `rememberSaveableStateHolderNavEntryDecorator` and `rememberViewModelStoreNavEntryDecorator`. All navigation logic lives in `MainActivity.kt`; features receive lambdas.

## Rejected alternatives

- Navigation Compose (Nav2) — supported, but starting new in 2026 buys a later migration. It remains the documented fallback if spike S-5 fails; the swap cost is ~2 days because navigation is centralized in one file.

## Consequences

Deep links from nudges (`songpad://song/{id}`, `songpad://take/{id}`) are resolved by `DeepLinkResolver` into keys, falling back to the library when the target is gone (§11.3). S-5 (predictive back, restoration with an active recorder) is still pending device validation — see docs/spikes/S-5.md.

## References

- spec §4.1 Navigation
- spec §21.1 S-5
- app/src/main/kotlin/com/songpad/app/navigation/
