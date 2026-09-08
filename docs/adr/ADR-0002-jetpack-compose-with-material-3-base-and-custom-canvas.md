# ADR-0002: Jetpack Compose with Material 3 base and custom Canvas

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

The UI needs four themes from one token layer (§14), a live waveform at 10 Hz and, later, a piano keyboard — all of which are hostile to XML Views.

## Decision

Compose + Material 3 for every screen. Waveforms are `Canvas` composables in `:core:ui` (`LiveWaveform`, `SeekableWaveform`). Components read `SongPadTheme.colors` (semantic tokens), never Material color roles directly; Material 3's `ColorScheme` is derived from the tokens, not the other way round.

## Rejected alternatives

- XML Views — imperative state sync, slow theme switching, no cheap Canvas.

## Consequences

Feature modules are Compose-only and depend on `:core:ui` for every visual primitive. Previews use `FourThemePreview` so a component is reviewed in all four variants at once.

## References

- spec §4.1 UI
- spec §14
- core/ui/src/main/kotlin/com/songpad/core/ui/theme/
