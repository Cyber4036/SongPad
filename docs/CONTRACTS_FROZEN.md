# Frozen contracts (spec §15)

These interfaces were frozen at the end of Week 1. **Changing a signature requires the technical lead's sign-off**, because
other modules and every fake in `:core:data:testing` are coded against them. Additions below the "non-frozen" markers in
the same files are allowed with a normal review.

All contracts are declared in the pure-Kotlin module `:core:model` (see ADR-0030) and implemented in the module §15 names.

| Contract | Declared in | Implemented by | Bound in |
| --- | --- | --- | --- |
| `AudioRecorderEngine`, `RecordingState`, `InterruptionReason`, `CaptureConfig` | `core/model/src/main/kotlin/com/songpad/core/model/audio/AudioRecorderEngine.kt` | `OboeAudioRecorderEngine` (`:core:audio`) | `AudioBindingsModule` |
| `PlaybackController`, `PlaybackSource`, `PlaybackState` | `core/model/.../audio/PlaybackController.kt` | `Media3PlaybackController` (`:core:audio`) | `AudioBindingsModule` |
| `MidiCaptureEngine`, `MidiEvent` | `core/model/.../midi/MidiCaptureEngine.kt` | `MidiCaptureEngineImpl` (`:core:midi`) | `MidiModule` |
| `TakeRepository` (frozen block) | `core/model/.../data/Repositories.kt` | `TakeRepositoryImpl` (`:core:data`) | `DataBindingsModule` |
| `SongRepository` (frozen block) | same file | `SongRepositoryImpl` | `DataBindingsModule` |
| `SearchEngine`, `SearchResults`, `SongMatch`, `SectionMatch`, `NoteMatch`, `TakeMatch` | same file | `SearchEngineImpl` | `DataBindingsModule` |

Non-frozen but under the same review discipline (they are what Phase 0 §8/§10/§12 need):
`LyricsRepository`, `NoteRepository`, `NudgeRepository`, `ExportSourceResolver`, `SettingsRepository`, and the
non-frozen additions to `TakeRepository`/`SongRepository` (`observeTake`, `markTranscoded`, `recoverOrphan`, `rename`, …).

## Error contract

Every `Result` failure carries a `DomainError` inside `DomainException` (`core/model/.../error/DomainError.kt`).
Nothing above the data layer sees a stack trace; ViewModels map errors to §17 states and log through `Logger`.

## Fakes

`:core:data:testing` ships a fake for every contract (`FakeAudioRecorderEngine`, `FakePlaybackController`,
`FakeMidiCaptureEngine`, `FakeTakeRepository`, `FakeSongRepository`, `FakeSearchEngine`, `FakeLyricsRepository`,
`FakeNoteRepository`, `FakeNudgeRepository`, `FakeSettingsRepository`, `FakeExportSourceResolver`,
`FakeTranscodeScheduler`, `FakeStorageProbe`) over one `InMemoryStore`. A contract change must update its fake in the
same PR.

## Change log

| Date | Change | Sign-off |
| --- | --- | --- |
| 2026-09-07 | Initial freeze. `PlaybackSource.InFlightPcm` added before the freeze so review-before-save (§7.6) is representable. | lead |
