## Summary
<!-- Provide a concise summary of the changes and the feature or bug addressed. -->

## Associated Milestone & Module
- **Module(s)**: `[:core:model | :core:data | :core:audio | :core:midi | :core:ui | :feature:capture | :feature:song | :feature:search | :feature:nudges | :feature:export | :app]`
- **Milestone / Spike**: `[e.g., M0, M1 Vertical Slice, Spike S-1]`

## Architectural Compliance Checklist
- [ ] **Interface Contracts**: Did NOT modify any frozen interface signature in §15 without prior Technical Lead approval.
- [ ] **Module Boundaries**: No feature module depends directly on another feature module.
- [ ] **Pure Domain Model**: `:core:model` remains free of Android dependencies (`android.*`).
- [ ] **Audio Threading**: Audio capture logic does NOT use `Dispatchers.IO` or `Dispatchers.Default`; runs on dedicated thread with `THREAD_PRIORITY_URGENT_AUDIO`.
- [ ] **Design System**: Uses semantic tokens exclusively (`bg`, `surface`, `text`, `accent`, etc.) rather than hardcoded hex colors.
- [ ] **Themes**: Verified UI against all four themes (SongPad Light/Dark, MelodyStudio Light/Dark).
- [ ] **Touch Targets**: All interactive elements satisfy the 48×48 dp minimum.
- [ ] **Error Handling**: Use cases return typed domain errors in `Result`, never uncaught raw exceptions.

## Test Verification
- [ ] JVM Unit Tests added or updated (branch coverage $\ge 85\%$ on business logic).
- [ ] Room migrations tested (if schema was altered).
- [ ] Survives process recreation / configuration change.
