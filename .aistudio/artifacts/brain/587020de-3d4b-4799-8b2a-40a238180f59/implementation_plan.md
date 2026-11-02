# Instant Voice Capture & Quick-Launch Piano/Lyrics Actions

Streamline SongPad's capture entry points so tapping the main **Record** button immediately begins a Voice recording, while persistent floating **Lyrics** and **Piano** quick-launch buttons in the lower-right corner open the capture screen in their respective modes (Ready state) for immediate practice, reading, or multi-layer recording.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following product and UX decisions incorporate your specifications and clarifications:

- **Main Record Action Starts Recording Immediately**: Tapping the main Record button (on the bottom bar of Songs/Nudges, on the Song Detail screen, or in empty-state Record actions) opens the Record screen with **Voice** selected and automatically starts recording (`autoStart = true`). If microphone permission is not yet granted, the system permission prompt is triggered first and recording starts immediately upon approval.
- **Floating Piano & Lyrics Quick-Launch Stack (Lower-Right)**: Two floating circular action buttons—**Lyrics** (stacked higher) and **Piano** (stacked below it)—float in the lower-right corner above the bottom bar on top-level screens (**Songs** and **Nudges**) and above the main Record FAB on the **Song Detail** screen.
- **Context Awareness Across Screens**: Just like the main Record button, the Piano and Lyrics quick-launch icons automatically inherit the current context:
  - From **Songs** or **Nudges**: launches as an unfiled *Loose idea* (and if Lyrics is launched on a loose idea, prompts the *"Which song?"* picker or new song dialog so lyrics have a home).
  - From **Song Detail**: automatically binds to that specific song (`songId`), loading its lyrics and saving takes directly into that song.
- **Playable Piano in Ready State Before Recording**: Opening the Record screen via the **Piano** icon sets **Piano** as the only active layer (`TakeType.PIANO`) in the `Ready` state. The Oboe piano sampler is armed whenever the Piano layer is active on the capture screen (`Ready`, `Recording`, `Paused`), allowing the user to freely play/practice on the keyboard before pressing Record, and start recording at any time.

---

### 1. Overview & Core Concept

- **What It Does**: Eliminates the extra tap when catching fleeting vocal ideas while providing dedicated 1-tap entry points for Piano-only practice/capture and Lyrics-assisted songwriting.
- **Target Audience / Persona**: Songwriters capturing sudden melodies (who need zero-latency 1-tap voice recording) as well as writers working out chord progressions on the built-in keyboard or singing over drafted lyrics.
- **Key Value**: Fast, predictable, context-aware capture from anywhere in the core songwriting workflow without cluttering the interface.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Instant Voice Capture (`Record` Button)**:
     - User taps the main crimson Record button on Songs, Nudges, or Song Detail.
     - App navigates to the Record screen with `Voice` toggled on and immediately transitions into `Recording` (with live waveform and running timer).
  2. **Piano Quick-Launch (`Piano` Icon)**:
     - User taps the floating Piano icon in the lower-right corner.
     - App opens the Record screen in `Ready` state with **Piano** as the only active pill (`TakeType.PIANO`) and the docked multi-octave keyboard visible.
     - User can immediately play notes on the keyboard (audible through the Oboe sampler) and tap the pulsing Record button whenever ready to record MIDI/audio, or toggle `Voice` / `Lyrics` on at any time.
  3. **Lyrics Quick-Launch (`Lyrics` Icon)**:
     - User taps the floating Lyrics icon in the lower-right corner.
     - App opens the Record screen in `Ready` state with **Voice** + **Lyrics** toggled on.
     - If launched from inside a **Song Detail** screen, that song's lyrics sections are immediately displayed on stage for reading/editing before or during recording.
     - If launched from **Songs** or **Nudges** (loose idea), the *"Which song?"* bottom sheet appears so the user can pick an existing song or create a new one for their lyrics.

- **Visual Identity & Theme**:
  - **Aesthetic Direction**: Warm editorial studio aesthetic matching SongPad's existing OKLCH semantic token system (`SongPadTheme.colors`).
  - **Floating Quick-Launch Stack**:
    - Styled as elevated circular surfaces (`44.dp` visual circle with `48.dp` minimum interactive touch target) using `c.surface` background, `1.dp` `c.divider` border, subtle shadow elevation, and `c.text` / `c.accent` iconography (`SongPadIcons.Lyrics` on top, `SongPadIcons.Piano` below, spaced `10.dp` apart).
    - On **Top-Level Screens (Songs, Nudges)**: Positioned in the lower-right corner (`padding(end = 18.dp, bottom = 16.dp)` above the bottom navigation bar, automatically shifting upward when the floating `RibbonPlayer` is visible).
    - On **Song Detail Screen**: Positioned in a vertical column aligned to the lower-right corner (`Alignment.BottomEnd`), stacked directly above the `62.dp` crimson Record FAB so Lyrics, Piano, and Record form a cohesive vertical capture cluster.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified `RecordKey` Navigation Parameters**
  - *Chosen Approach*: Extend `RecordKey` with `initialType: TakeType = TakeType.VOICE`, `autoStart: Boolean = false`, and `openLyrics: Boolean = false`.
  - *Why*: Every capture entry point routes to the exact same `RecordScreen` composable while declaratively configuring its starting mode and auto-start behavior. Back-stack serialization in Navigation 3 remains clean and process-death safe.

- **Decision 2: Live Piano Sampler in `Ready` State**
  - *Chosen Approach*: Arm the Oboe `PianoSamplerEngine` whenever the Piano layer is active (`TakeType.PIANO` or `TakeType.VOICE_PIANO`) in `Ready`, `Recording`, or `Paused` states, and route `KeyDown`/`KeyUp` events to `piano.noteOn`/`noteOff` whenever Piano is active, while only writing MIDI events to `MidiCaptureEngine` when `CaptureState.Recording` is active.
  - *Why*: Enables the user to warm up, test chords, and find their key on the piano before hitting Record, matching real-world instrument workflows.

- **Decision 3: Independent Layer Toggles (Voice, Piano, Lyrics)**
  - *Chosen Approach*: Allow toggling `Voice`, `Piano`, and `Lyrics` freely on the Record screen. Toggling `Lyrics` on at any time (Ready or Recording) reveals the live lyrics editor stage while keeping the transport and/or docked piano accessible below.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        App Shell & Navigation Host                         │
│                                                                            │
│  ┌──────────────────────────────┐        ┌──────────────────────────────┐  │
│  │  Top-Level (Songs / Nudges)  │        │      Song Detail Screen      │  │
│  │  • Centered Record Button    │        │  • Lower-Right Capture Stack │  │
│  │  • Lower-Right Floating      │        │    [Lyrics] (Ready + Lyrics) │  │
│  │    Stack: [Lyrics] / [Piano] │        │    [Piano]  (Ready + Piano)  │  │
│  │    (songId = null)           │        │    [Record] (Auto Voice Rec) │  │
│  └──────────────┬───────────────┘        └──────────────┬───────────────┘  │
│                 │                                       │                  │
│                 └───────────────────┬───────────────────┘                  │
│                                     ▼                                      │
│         RecordKey(songId, initialType, autoStart, openLyrics)              │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                       RecordScreen & CaptureViewModel                      │
│                                                                            │
│  1. bind(songId, initialType, autoStart)                                   │
│     ├─ Sets initial TakeType (VOICE / PIANO / VOICE_PIANO)                 │
│     ├─ If openLyrics == true: opens Lyrics stage (or Song Picker if loose) │
│     └─ If autoStart == true: dispatches Start once permission is resolved  │
│                                                                            │
│  2. Piano Sampler Lifecycle                                                │
│     ├─ Armed whenever Piano layer is selected (Ready / Recording)          │
│     ├─ KeyDown/KeyUp → PianoSamplerEngine (audible in Ready & Recording)   │
│     └─ KeyDown/KeyUp → MidiCaptureEngine  (recorded only during Recording) │
└────────────────────────────────────────────────────────────────────────────┘
```

#### Interactive Component & State Mapping

- **Quick-Launch Floating Cluster (`CaptureQuickActions`)**:
  - Reusable composable rendering the vertical `[Lyrics]` + `[Piano]` floating icon buttons with `Modifier.testTag("quick_lyrics_button")` and `Modifier.testTag("quick_piano_button")`.
  - On Top-Level screens (`Songs`, `Nudges`): anchored at `Alignment.BottomEnd` above the `SongPadBottomBar` (and above `RibbonPlayer` when active).
  - On `SongDetailScreen`: anchored at `Alignment.BottomEnd` directly above the main `RecordButton` (hidden on the `Notes` tab or when `RibbonPlayer` is active, matching existing visibility rules).
- **Auto-Start Lifecycle Guard (`CaptureViewModel`)**:
  - Tracks whether `autoStart` has already been consumed for the current `RecordKey` session so rotating the screen or pausing/discarding a take does not inadvertently re-trigger recording.
  - When `autoStart = true`:
    - If `MicrophonePermission` is already `Granted`, dispatches `CaptureIntent.Start` immediately upon binding.
    - If `MicrophonePermission` is not yet granted, requests microphone permission first and automatically transitions into `CaptureIntent.Start` as soon as the user grants permission.
- **Layer Toggle & Sampler State (`CaptureViewModel` & `RecordScreen`)**:
  - `CaptureViewModel` arms `PianoSamplerEngine` whenever `state.type != TakeType.VOICE` and the screen is in `Ready`, `Recording`, or `Paused` state (with `CaptureMix` enabled during active recording).
  - `KeyDown` and `KeyUp` intents trigger `piano.noteOn` / `piano.noteOff` whenever `state.type != TakeType.VOICE`, and additionally record into `midi.onKeyDown` / `midi.onKeyUp` when `state is CaptureState.Recording`.
