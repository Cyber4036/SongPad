# SongPad — Organic Design System & Feature Parity Plan

Transform SongPad's Android interface to match the tactile, editorial warmth of the `Capture.html` design prototype while completing all missing songwriting workflows—live multi-layer capture (`Voice`, `Piano`, `Lyrics`), inline FTS5 library search, song/take metadata cards, chat-bubble notes with `@mention` autocomplete, section retagging, take duplication, and unread nudge badges.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All scope questions from Phase 1 have been confirmed and are reflected throughout this plan:

- **Confirmed Decision 1 — Full Prototype Feature Parity**: Implement all four missing prototype feature groups:
  1. **Record Screen**: Independent `Voice`, `Piano`, and `Lyrics` layer toggles (with live lyrics editing during capture and song assignment prompt for loose takes), compact floating glass-pill transport with inline waveform, and Piano key-size zoom (`Small / Medium / Large`) with labeled rounded keys.
  2. **Songs Library**: Inline search bar, `"Unfiled — nice catch"` callout card for loose takes, song card badges (`take count`, `section count`, `status`), and the `•••` song action sheet.
  3. **Song Detail & Chat-Bubble Notes**: Top-bar `Finish`/`Archive` pills, inline song rename, `VOX`/`PNO`/`V+P`/`LYR`/`NTE` activity badges, clickable lyric section tag pills for retagging, take `Duplicate` and single-take `Export`, and right-aligned chat-bubble Notes with a pinned bottom composer and live `@` autocomplete popup.
  4. **Nudges & Settings**: Unread nudge badge on the bottom navigation bar, bell-icon nudge cards with unread indicator dots, and the 3-swatch theme family selector (`SongPad` vs `Melody Studio`) with `Light / Dark` mode pills.
- **Confirmed Decision 2 — Inline FTS5 Search on Songs Screen**: Typing into the rounded pill search bar on the Songs screen runs live FTS5 full-text queries across songs, lyrics, notes, and takes with highlighted `<b>…</b>` snippet matches right in place, without requiring a separate screen hop.
- **Confirmed Decision 3 — Preserve Native Android Capabilities**: Retain photo attachments on notes, lossless FLAC recording toggles, storage usage metrics, and crash-recovery banners, restyled as cohesive organic cards matching the prototype.

---

## 1. Overview & Core Concept

- **What It Does**: SongPad is a local-first songwriting studio for capturing fleeting musical ideas—voice memos, polyphonic piano sketches, structured lyrics, and timestamped/cross-referenced notes—before they vanish.
- **Target Audience / Persona**: Songwriters, vocalists, and instrumentalists who need zero-friction capture in one hand, simultaneous lyric drafting while humming or playing chords, and gentle reminders (`Nudges`) to finish half-written ideas.
- **Key Value**: Bridges raw voice-memo apps and complex DAWs by combining low-latency audio/MIDI capture with structured lyric sections, `@mention` note linking, and an inviting, non-judgmental studio aesthetic.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Songs Library & Inline FTS5 Search**:
   - Opens with the editorial kicker `"CAUGHT SO FAR"` above the `Caprasimo` `"Songs"` heading and a circular bordered Settings icon button.
   - Below sits a pill-shaped search input (`"Search songs, lyrics, notes"`). When empty, horizontal status filter pills (`All`, `Favorites`, `Ongoing`, `Finished`, `Archived`) filter the song cards. When a query is typed, the view seamlessly displays grouped FTS5 snippet results (`Songs`, `Lyrics`, `Notes`, `Takes`) with highlighted matches that deep-link straight to the matching section, note, or take.
   - If unassigned takes exist, a warm accent container (`"UNFILED — NICE CATCH"`) appears at the top with play controls and an `"Add to a song"` primary pill button.
   - Each Song card (`28dp`–`32dp` radius, soft elevation, `18dp` horizontal screen margin, `11dp` vertical gap) displays the song title in `Caprasimo`, a star favorite toggle, a `•••` quick-menu button, and pill tags for `X takes`, `Y sections`, `Ongoing / Finished / Archived` status, and relative update time.

2. **Multi-Layer Record Screen (`Voice` · `Piano` · `Lyrics`)**:
   - Top bar features a circular close button (`✕`) on the left and three independent layer toggle pills (`Voice`, `Piano`, `Lyrics`) with active status dots on the right.
   - Editorial kicker (`"READY WHEN YOU ARE"` → `"RECORDING"` → `"TAKE IN HAND"`) sits above the context title (the target Song name or `"Loose idea"`).
   - **Lyrics Layer**: When `Lyrics` is toggled off, the center area displays the large `52sp` `Caprasimo` tabular timer, live/scrubbable waveform, and encouraging hint copy. When `Lyrics` is toggled on (prompting `"Which song?"` via a bottom sheet if started as a loose idea), the center area becomes a live, autosaving lyric section editor with a compact timer and `+ Section` button so the songwriter can read and write verses while recording.
   - **Piano Layer**: When `Piano` is toggled on, the polyphonic keyboard docks above the transport controls with two pill steppers: Key Width (`[−] Small / Medium / Large [+]`) and Octave range (`[<] C3–B4 [>]`), rendering rounded ivory white keys (`#F4F1EC`) with note labels and dark ebony keys with multi-touch glissando support.
   - **Floating Glass-Pill Transport**: In `Ready` state, a `80dp` pulsing circular Record button anchors the bottom. Once recording or reviewing, it morphs into a translucent floating glass pill containing `[✕ Discard]`, `[✓ Save]` (accent pill), `[⏸ / ▶ Pause or Play]`, an inline scrubbable waveform with playhead, and the tabular elapsed timer.

3. **Song Detail (`Home` · `Lyrics` · `Takes` · `Notes`)**:
   - Header row has a circular Back button, quick-toggle `Finish / Reopen` and `Archive / Unarchive` pills, and a circular Export icon button, followed by the Song title with an inline pencil rename button and horizontal pill tabs.
   - **Home Tab**: Chronological activity cards (`24dp` radius) with circular color-coded badges (`VOX`, `PNO`, `V+P`, `LYR`, `NTE`). Tapping a take opens the floating bottom **Ribbon Player**; tapping a lyric or note switches to and scrolls to that item.
   - **Lyrics Tab**: Shows autosave status (`"All changes saved"` / `"Saving…"` / `"Autosaved just now"`) and a `+ Section` pill button. Each section is a `24dp` surface card with a clickable accent tag pill (`Verse 1`, `Chorus`, etc.) that opens a `"Section tag"` bottom sheet to retag and auto-renumber sections, reorder arrows, a delete `✕` button, and a borderless auto-growing text editor.
   - **Takes Tab**: Filter pills (`All`, `Voice`, `Piano`, `Voice + Piano`) above `24dp` take cards with a `34dp` circular accent play button, title, duration/timestamp metadata, type tag chip, and a `⋮` options button opening a bottom sheet (`Play`, `Rename`, `Duplicate`, `Export take`, `Move to another song…`, `Delete take`).
   - **Notes Tab (Chat-Bubble Composer)**: Notes render as right-aligned chat bubbles (`22dp` top/left corners, `6dp` bottom-right tail) tinted with `accentSoft` for text/photo notes and `accent2Soft` for playable voice notes, with compact `Edit` and `Delete` actions underneath. A translucent bottom composer bar stays pinned above the keyboard with a borderless input, photo attach icon, one-tap voice note mic button, and checkmark save button. Typing `@` opens a floating `"REFERENCE A SECTION"` autocomplete card listing the song's lyric sections and takes.

4. **Nudges & Settings**:
   - **Bottom Bar**: Protruding `70dp` center Record button with a `5dp` background halo, flanked by `Songs` and `Nudges` (with an unread count pill badge).
   - **Nudges Screen**: Editorial kicker `"GENTLE REMINDERS"` above `"Nudges"`, followed by `26dp` rounded nudge cards with a `34dp` circular bell badge, `Caprasimo` title, body copy, timestamp, and an accent unread dot.
   - **Settings Screen**: Grouped `28dp` organic cards for **Appearance** (interactive 3-swatch cards for `SongPad` and `Melody Studio` + `Light / Dark` mode pills + follow-system option), **Permissions** (`Microphone`, `Notifications`), **Storage & Data** (`Lossless recording` toggle, take count & byte usage, local-storage disclosure), **Export**, and **About**.

---

### Visual Identity & Theme

- **Aesthetic Direction**: Warm Organic Studio (`SongPad` default) & Neon Late-Night Studio (`Melody Studio` alternate)—tactile, rounded, and editorial, avoiding sterile Material defaults or sharp-cornered spreadsheets.
- **Color Palette & Tokens**:
  - Preserves the 7 core contract tokens (`bg`, `surface`, `text`, `accent`, `accent2`, `record`, `divider`) across all 4 theme variants while exposing tonal ramp tokens (`accentSoftContainer` / `accent100`, `accentOnSoft` / `accent800`, `accent2SoftContainer` / `accent2_100`, `accent2OnSoft` / `accent2_800`, `neutralSoftContainer` / `neutral100`, `neutralOnSoft` / `neutral800`) to match the prototype's exact tag and card contrast:
    - **SongPad Light**: `--bg: #F5EAD8`, `--surface: #EBDDC5`, `--text: #201E1D`, `--accent: #C67139`, `--accent-2: #7A8A5E`, `--accent-100: #FFF2EB`, `--accent-800: #643312`, `--accent-2-100: #F0FAE1`, `--accent-2-800: #3D472B`.
    - **SongPad Dark**: `--bg: #1B1815`, `--surface: #38322B`, `--text: #F4EBDD`, `--accent: #E08A4C`, `--accent-2: #2B3122`, `--accent-100: #3A2517`, `--accent-800: #F7CBA4`, `--accent-2-100: #2B3122`, `--accent-2-800: #D3DFBA`.
    - **Melody Light**: `--bg: #E9E0FA`, `--surface: #FDFAFF`, `--text: #1C1233`, `--accent: #7B2FF7`, `--accent-2: #00B3C8`, `--record: #FF3355`.
    - **Melody Dark**: `--bg: #0D0718`, `--surface: #2A1D47`, `--text: #F3EBFF`, `--accent: #B46BFF`, `--accent-2: #2EE6F5`, `--record: #FF5A7A`.
- **Typography & Hierarchy**:
  - **Display & Card Titles (`Caprasimo`)**: Screen titles (`25sp–32sp`), timer readouts (`52sp` tabular), song card titles (`17sp`), nudge/unfiled titles (`15sp`), and sheet titles (`19sp`).
  - **Body & Controls (`Figtree`)**: Kickers (`10.5sp`, uppercase, `0.11em` tracking, `accent` color), body copy (`14sp–14.5sp`, `1.55` line height), filter pills (`12.5sp–13px`), and metadata tags (`11sp`).
- **Spatial Rhythm & Radii**:
  - Screen horizontal padding: `18.dp`; card-to-card vertical gap: `10.dp–11.dp`; card internal padding: `14.dp–16.dp`.
  - Card corner radius: `24.dp–28.dp`; bottom sheets: `32.dp` top corners with `42dp × 4dp` drag handle; buttons, tags, inputs, and transport bars: `999.dp` full pill.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Independent Layer State (`Voice`, `Piano`, `Lyrics`) on the Capture Screen**
  - *Chosen Approach*: Represent `voiceEnabled`, `pianoEnabled`, and `lyricsEnabled` as independent boolean flags in the capture UI/ViewModel that derive the underlying `TakeType` (`VOICE`, `PIANO`, `VOICE_PIANO`, with at least one audio/MIDI source active) while allowing `Lyrics` and `Piano` to be toggled on or off both before and during recording.
  - *Why*: Matches the prototype's fluid songwriting experience where a user starts humming a voice take and mid-thought taps `Lyrics` to jot down a line or `Piano` to check a chord.
  - *Alternatives Considered*: Keeping `TakeType` locked to a pre-recording radio button, which prevented viewing lyrics or opening the piano once recording started.

- **Decision 2: Unified Inline Search on Songs Screen + In-Song Search**
  - *Chosen Approach*: Embed the pill search bar directly in `LibraryScreen` and wire it to `SearchEngine.searchGlobal(query)` so typing immediately displays FTS5 snippet matches (with category filter pills), while keeping `SearchScreen` available for scoped `"Search in song"` and deep-link navigation.
  - *Why*: Eliminates an unnecessary navigation hop from the main library while retaining full SQLite FTS5 `bm25` ranking and `<b>…</b>` snippet highlighting.

- **Decision 3: Non-Breaking Repository Extensions for Counts, Retagging & Duplication**
  - *Chosen Approach*: Add reactive song summary counts (`takeCount`, `sectionCount`), section tag updating (`updateSectionTag`), and take duplication (`duplicateTake`) as additive methods on the existing repositories and DAOs (plus test fakes in `:core:data:testing`) without altering any frozen §15 method signatures or database table schemas.
  - *Why*: Preserves 100% compatibility with existing unit tests, Room schema `1.json`, and module boundary checks in root `build.gradle.kts`.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        :app (MainActivity & Navigation 3)                    │
│  ┌─────────────────────────┐  ┌──────────────────────┐  ┌─────────────────┐  │
│  │    SongPadBottomBar     │  │     NavDisplay       │  │ SettingsScreen  │  │
│  │ (Songs · Record · Nudge │  │ (Library · SongDetail│  │ (2-Family Swatch│  │
│  │  with Unread Badge)     │  │  Record · Nudges)    │  │  + Light/Dark)  │  │
│  └───────────┬─────────────┘  └──────────┬───────────┘  └────────┬────────┘  │
└──────────────┼───────────────────────────┼───────────────────────┼───────────┘
               │                           │                       │
      ┌────────┴───────────────────────────┴──────────┐            │
      ▼                                               ▼            ▼
┌───────────────────────────────────────────┐  ┌───────────────────────────────┐
│              :feature:song                │  │       :feature:capture        │
│ ┌───────────────────────────────────────┐ │  │ ┌───────────────────────────┐ │
│ │ LibraryScreen & LibraryViewModel      │ │  │ │ RecordScreen & ViewModel  │ │
│ │ • Editorial Kicker + Pill Search Bar  │ │  │ │ • Voice/Piano/Lyrics Pills│ │
│ │ • Inline FTS5 Snippet Results         │ │  │ │ • Live Lyrics Editor Pad  │ │
│ │ • "Unfiled — nice catch" Accent Card  │ │  │ │ • Song Picker BottomSheet │ │
│ │ • Song Cards (Counts + ••• Sheet)     │ │  │ │ • GlassPill Inline Wave   │ │
│ └───────────────────────────────────────┘ │  │ │ • Piano Zoom & Octave Bar │ │
│ ┌───────────────────────────────────────┐ │  │ └───────────────────────────┘ │
│ │ SongDetailScreen & SongDetailViewModel│ │  └───────────────┬───────────────┘
│ │ • Finish/Archive Pills & Inline Rename│ │                  │
│ │ • Home Feed (VOX/PNO/LYR/NTE Badges)  │ │  ┌───────────────┴───────────────┐
│ │ • Lyrics Cards (Retag Sheet + Editor) │ │  │        :feature:nudges        │
│ │ • Takes Cards (Duplicate + Export)    │ │  │ • Editorial Header & Switch   │
│ │ • Chat-Bubble Notes + @Mention Popup  │ │  │ • Bell-Badge Cards + Unread   │
│ └───────────────────┬───────────────────┘ │  └───────────────┬───────────────┘
└─────────────────────┼─────────────────────┘                  │
                      ▼                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│              :core:ui (Organic Design System & Shared Components)            │
│  • SemanticColors & Palette (Tonal Ramps 100..800 for SongPad & Melody)      │
│  • Typography (Caprasimo headings + Figtree body) & Dimens (18dp/24dp/999dp) │
│  • EditorialHeader · PillSearchBar · SongCard · TakeCard · GlassPillTransport│
│  • RibbonPlayer · SongPadBottomSheet (Pill Option Rows) · TagChip            │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                 :core:data & :core:model (Repositories & Room)               │
│  • SongRepository: observeSongSummaries (take & section counts per song)     │
│  • LyricsRepository: updateSectionTag (retag + auto-renumber Verse/Chorus)   │
│  • TakeRepository: duplicateTake (copies audio/peaks/MIDI files + new row)   │
│  • NudgeRepository: observeHistory (drives unread count badge & Nudge cards) │
│  • SearchEngine: searchGlobal / searchInSong (FTS5 snippets)                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State Extensions

1. **Tonal Ramp Tokens (`:core:ui`)**:
   - Extend `SemanticColors` with `accentContainer` (`c100`), `onAccentContainer` (`c800`), `accent2Container` (`c100`), `onAccent2Container` (`c800`), `neutralContainer` (`c100`), and `onNeutralContainer` (`c800`) derived directly from `Palette.SongPadLight`, `SongPadDark`, `MelodyLight`, and `MelodyDark` while keeping all existing `SemanticColors` properties intact so `SemanticColorsTest` passes unchanged.
2. **Song Card Counts (`:core:model`, `:core:data`, `:core:data:testing`)**:
   - Add `takeCount: Int = 0` and `sectionCount: Int = 0` to `Song` (with default values `0` so all existing callers and unit tests compile without modification) and populate them reactively in `SongRepositoryImpl.observeSongs()` by combining song rows with take and lyric section counts.
3. **Lyric Section Retagging (`:core:model`, `:core:data`, `:core:data:testing`)**:
   - Add `suspend fun updateSectionTag(sectionId: String, tag: SectionTag, customLabel: String? = null): Result<Unit>` to `LyricsRepository`, implemented in `LyricsRepositoryImpl` and `FakeLyricsRepository`. Updating a tag re-runs `SectionOrdering.renumber` across the document's sections so `Verse 1`, `Verse 2`, and `Chorus 1` automatically renumber.
4. **Take Duplication (`:core:model`, `:core:data`, `:core:data:testing`)**:
   - Add `suspend fun duplicate(takeId: String): Result<Take>` to `TakeRepository`, implemented in `TakeRepositoryImpl` and `FakeTakeRepository`. Copies the underlying audio file, peaks file, and optional MIDI `.mid` file into a new `UuidV7` take directory and inserts the new `TakeEntity` (`"<name> (copy)"`).

### Interactive Component & State Mapping

| Surface | User Interaction | State / Handler Flow |
| :--- | :--- | :--- |
| **Bottom Navigation (`SongPadBottomBar`)** | Tap `Songs`, center `Record`, or `Nudges` (shows unread badge) | Observes unread count from `NudgeRepository`; switches top-level stack or pushes `RecordKey`. |
| **Songs Library (`LibraryScreen`)** | Type in inline search pill; tap filter pills; tap `"Add to a song"` on loose take; tap `•••` on Song card; tap star | Typing triggers `SearchEngine.searchGlobal` and renders FTS5 snippet rows (`Songs`, `Lyrics`, `Notes`, `Takes`). `•••` opens `LibrarySheet.SongOptions` (`Rename`, `Favorite`, `Finish/Reopen`, `Archive/Unarchive`, `Delete`). |
| **Record Screen (`RecordScreen`)** | Toggle `Voice`, `Piano`, `Lyrics` pills; adjust Piano key size (`Small/Medium/Large`) or Octave (`C1–B7`); edit lyrics while recording; scrub inline waveform in Glass Pill | Toggling `Lyrics` on a loose take opens the `"Which song?"` bottom sheet (pick existing song or create `"New song…"`). Toggling `Piano` docks the zoomable `PianoKeyboard` and arms `PianoSamplerEngine` when recording. Glass Pill handles `Discard`, `Save`, `Pause/Play`, and `Scrub`. |
| **Song Detail (`SongDetailScreen`)** | Tap top-bar `Finish` / `Archive` pills or inline rename pencil; tap `Home` feed badges; tap section tag pill in `Lyrics`; tap `⋮` on `Takes`; type `@` or tap Mic/Photo in `Notes` | Section tag pill opens `RetagSection` sheet → `viewModel.updateSectionTag`. Take `⋮` sheet triggers `Play`, `Rename`, `Duplicate` (`viewModel.duplicateTake`), `Export take` (`onExport`), `Move`, or `Delete`. Notes bottom bar detects `/@[\w ]*$/` before cursor to show `@section` / `@take` autocomplete popup, and supports photo attachments + one-tap voice notes. |
| **Settings (`SettingsScreen`)** | Tap `SongPad` or `Melody Studio` swatch card; tap `Light` / `Dark` / `System` mode pills; toggle `Lossless recording` or `Gentle nudges` | Updates `SettingsRepository.setThemeVariant` using the selected family + mode combination, toggles `QualityMode`, and opens system permission settings. |
