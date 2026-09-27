# Wave 5 (Hardening) — status against Gate M4

*As of 2026-09-27. Single-developer pass: code and docs only. Tests and polish were out of scope for this pass,
and no physical device was attached.*

Gate M4 (spec §20): *available devices green; Xiaomi/HyperOS status explicitly resolved; migration tests pass;
no leaks or ANRs under profiling; orphan cleanup proven safe.*

**M4 is not closed.** The code-side hardening below is done. Every "proven on device" item still needs the Pixel
4a and Tecno Spark 8, plus by-ear spot checks on the Galaxy A03.

## Done in code

| Area | What changed | Where |
| --- | --- | --- |
| Back navigation — capture | System back and the top-bar Close no longer pop the Record route while a take is open (Recording / Paused / Reviewing / Interrupted). Popping cleared the `CaptureViewModel` and stranded a live recording with no UI, recoverable only after the next cold start. It now goes through the existing "Discard this recording?" confirmation (Phase 0 §5: preserve the recording, and confirm when loss is possible). Back is ignored while Saving, because popping would cancel the commit mid-transaction. | `feature/capture/.../ui/RecordScreen.kt` |
| Back navigation — voice notes | Same hazard on Song → Notes while a voice note is recording. Back now stops the voice note and sends it (stopping is sending, chat-style, as already decided for the composer) and keeps the user on the screen. The next back leaves. | `feature/song/.../detail/SongDetailScreen.kt`, `SongDetailViewModel.recordingVoiceNote` |
| Back navigation — nudges | An in-app Nudges → take tap used to reset the back stack to Library, while Nudges → song pushed. Both now push, so back returns to Nudges. External `songpad://` deep links still rebuild the stack as Library → target. | `app/.../MainActivity.kt` (`inAppTargets` vs `deepLinkTargets`) |
| Piano on headphones | §8.1 amended (ADR-0035): on a headphone route, the sampler is mixed into the capture file. | `core/audio/src/main/cpp/sampler_tap.h`, `PianoSamplerEngineImpl` |
| Leaks / ANRs | Debug builds enable `StrictMode` (log-only): main-thread disk and network access, leaked closeables, cursors, registrations and activities, and file-URI exposure. Profiling sessions on device now log findings without a profiler attached. | `SongPadApplication.enableStrictMode` |
| ANR audit | The ~9.9 MB piano sample load is already off the main thread (`Dispatchers.IO` in `PianoSamplerEngineImpl.loadAsync`). The recovery scan runs on the app scope's `Dispatchers.Default`. | — |
| R8 rules | Checked against the minified `internal` build's mapping file and DEX rather than guessed. All WorkManager workers (`PurgeWorker`, `OrphanCleanupWorker`, `TranscodeWorker`, `ExportWorker`, `NudgeWorker`) keep their class names, which matters because WorkManager persists `workerClassName` across app updates. `NativeCapture` / `NativePiano` keep their names and `native` members (JNI symbol binding). The Nav3 route keys are obfuscated, but they keep `Companion`, `serializer()` and `INSTANCE` through kotlinx-serialization's bundled rules, which is what Nav3's reflective `NavKeySerializer` needs to restore the back stack after process death. **No rule changes were needed.** | `app/proguard-rules.pro` (unchanged) |

## Audited, no change needed

- **Orphan cleanup (§9.3).** All four rules are implemented in `OrphanCleanupWorker`:
  1. referenced rows, including soft-deleted ones, are never collected;
  2. 24 h newest-mtime grace;
  3. `.pcm.tmp` or `events.log` present means skip;
  4. an active capture session defers the whole run, re-checked after the scan and before any delete.
  
  A recording started after the second check creates a fresh directory, which rule 2 protects. Would-delete
  paths are logged at debug level before deletion, as §9.3 requires for Phases 1–5. **Not yet proven** by the
  instrumented and device tests §18 asks for.
- **Interruption matrix (§7.4).** Focus loss, telephony (via `AUDIOFOCUS_LOSS_TRANSIENT`) and Bluetooth
  disconnect were already handled, and the reasoning is documented in a doc comment on `AudioFocusGuard`. The 45-minute sessions and
  battery-saver runs are device work.
- **Process death.** Nav3 back stack: serializable keys, verified under R8 (above). In-flight takes: the §9.4
  recovery path to the Library banner. Lyrics autosave on a 400 ms debounce. Not preserved: an unsent
  note-composer draft and the search query live only in ViewModel memory and are lost on process death. They
  survive rotation, because the ViewModel outlives configuration changes. Accepted as polish, which was out of
  scope for this pass.
- **Low storage.** `StartRecordingUseCase` maps free space to `StorageState` and refuses to start below the Low
  threshold, and `CaptureState.LowStorage` renders a real state (§17).

## Data

- **Migrations v1→v2: vacuous at this gate.** The schema is still at `version = 1` (`core/data/schemas/.../1.json`),
  and no v2 was invented to satisfy the gate. The `MigrationTestHelper` harness exists
  (`core/data/src/androidTest/.../MigrationTest.kt`) with the v1 baseline. Per §18, the first real schema change
  must land with its migration test.
- **1,000+ take library scale: open.** `SeededPerformanceTest` seeds 200 takes. §21.2 asks for 1,000+, and there
  is no paging: the Library and Takes lists load full `Flow<List<…>>`. This needs a device measurement before
  deciding whether Paging is warranted.

## Xiaomi / HyperOS — resolved: carried into beta, explicitly

Product owner decision, 2026-09-27. No HyperOS device was available at any point in Phase 1. Beta ships with
HyperOS **untested**. Known risks, none of which the Tecno (Transsion) results cover:

- HyperOS kills background processes more aggressively than stock Android and differently from Transsion's
  HiOS. The foreground service should protect an active take, but that is unverified on this OEM.
- Nudges (WorkManager periodic work) may be delayed or never delivered without the user granting "Autostart" or
  "No restrictions". This is already "unfixable in code" per §21.2.
- The interaction between battery saver and a 45-minute session is unknown.

Mitigation: recruit at least one beta tester on a Xiaomi/Redmi/POCO device running HyperOS, and treat their first
week of vitals and feedback as the HyperOS verification.

## Still open for M4 (device work)

- Audio lifecycle matrix on Pixel 4a and Tecno Spark 8 (§18 row "Audio lifecycle"): calls, BT disconnect,
  headphone unplug, 45-minute session under battery saver.
- ADR-0035 by-ear check: the piano is audible in headphone takes and not doubled on the speaker route.
- Orphan cleanup: worker fired during an active recording, and < 24 h files are never collected.
- Profiling: StrictMode logcat review plus a memory profile across a record → save → playback loop.
- The UI pass for rotation, large type, TalkBack, and small and large screens was out of scope for this pass
  (polish). Back navigation was fixed as above.
- Still unrendered from earlier sessions: the voice-note UI, photo picker and Library recovery banner.
