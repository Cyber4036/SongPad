# SongPad Export Overhaul & Full Round-Trip Import

This update resolves the issue where every export creates an unreadable `.songpad` / `.bin` file, enables native LAME MP3 encoding so MP3 exports succeed reliably, and introduces a complete round-trip Import workflow for song bundles (`.zip` / `.songpad`), standalone audio recordings (`.m4a`, `.flac`, `.wav`, `.mp3`), and plain-text lyrics (`.txt`).

### User Review & Critical Decisions

> [!IMPORTANT]
> The following product and architecture choices reflect your confirmed preferences and resolve all three export/import failure modes.

- **Confirmed Decision 1 — Direct Files + Standard `.zip` Bundles**:
  - **Lyrics Only** exports produce a direct human-readable `.txt` file (`<SongName>_Lyrics.txt`, `text/plain`) rather than a compressed archive.
  - **Single-Take** exports (either when a song has 1 take in *Audio takes* mode without MIDI, or when choosing **"Export take"** from a specific take's `•••` menu) export the direct audio file (`<SongName> - <TakeName>.m4a` / `.flac` / `.wav` / `.mp3`) with its true audio MIME type (`audio/mp4`, `audio/flac`, `audio/wav`, `audio/mpeg`).
  - **Multi-Item / Everything** exports produce a standard `.zip` archive (`Export_<SongName>_<yyyyMMdd-HHmm>.zip`, `application/zip`) so Android SAF, file managers, and desktop DAWs never rename or treat the output as an unknown `.bin` binary.
- **Confirmed Decision 2 — Native LAME MP3 Encoding Fixed**:
  - Vendor and compile LAME 3.100 into the native NDK library (`libsongpad_lame.so`) so `LameMp3Encoder` is always available at runtime (48 kHz mono, 192 kbps CBR).
- **Confirmed Decision 3 — Universal Import Scope (`.zip`, `.songpad`, Audio, `.txt`)**:
  - Users can import full song archives (both legacy `.songpad` files that may have been saved as `.bin` and new `.zip` bundles), standalone audio recordings (`.m4a`, `.flac`, `.wav`, `.mp3`, `.aac`, `.ogg`), and `.txt` lyrics documents.
- **Confirmed Decision 4 — Import Entry Points Across the App**:
  - Accessible from the **Library header** (import button next to Settings), **Settings → Storage & data** ("Import songs, takes or lyrics"), and **inside any Song Detail screen** (to import an audio take or lyrics `.txt` directly into that song, plus direct single-take export from any take's `•••` menu).

---

### 1. Overview & Core Concept

- **What It Does**:
  1. **True-Format Export**: Delivers the exact file type the user expects—plain `.txt` for lyrics, direct `.mp3` / `.wav` / `.m4a` / `.flac` for single takes, and standard `.zip` archives for multi-track song bundles—with dynamic MIME registration so Android's "Save to files" and "Send to…" never append `.bin`.
  2. **Working MP3 Export**: Bundles the LAME 3.100 C encoder into the native build so MP3 encoding works out-of-the-box for both single-take and full-bundle exports.
  3. **Lossless Round-Trip Import**: Restores exported song bundles (including section body text, notes, audio takes, waveform peaks, and MIDI sidecars) and lets songwriters bring in external audio files or `.txt` lyric sheets into their library or directly into an open song.
- **Target Audience / Persona**: Songwriters, vocalists, and musicians who rely on SongPad to safeguard their recorded ideas, move takes into DAWs or messaging apps, and restore backups without losing a single recording or lyric line.
- **Key Value**: Eliminates data lock-in and export failures—every recording and lyric sheet can leave the app in a universally playable format and return intact whenever needed.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Exporting from Song Detail (`Share / Export` button)**:
     - The user chooses **Everything**, **Audio takes**, or **Lyrics only**, plus the audio format (**Stored M4A/FLAC**, **WAV**, or **MP3**).
     - The summary card clearly states the output format and file extension before confirmation (e.g., `Export_Midnight_20261103.zip`, `Midnight_Lyrics.txt`, or `01 Verse idea.mp3`).
     - Tapping **Save to files** or **Send to…** uses the exact MIME type (`text/plain`, `audio/mpeg`, `audio/wav`, `audio/mp4`, `audio/flac`, or `application/zip`) so the OS file picker and share sheet preserve the real extension.
  2. **Exporting a Single Take (`•••` → `Export take`)**:
     - Opens the Export screen pre-scoped to that specific take (with a chip showing the selected take name and an option to switch to all takes). Exporting produces the standalone `.mp3`, `.wav`, `.m4a`, or `.flac` audio file directly.
  3. **Importing from Library or Settings**:
     - Tapping the **Import** action in the Library top bar or in **Settings → Storage & data** opens the system document picker (`OpenDocument` accepting `*/*` so even previously exported `.songpad` or `.bin` files can be selected alongside `.zip`, `audio/*`, and `text/plain`).
     - An **Import Bottom Sheet** displays instant progress and a completion summary (e.g., *"Imported 'Midnight' (3 takes, 4 lyric sections, 2 notes)"* with an **Open Song** button, or *"Imported audio take into Unfiled"* / *"Imported lyrics into Unfiled"*).
  4. **Importing Inside an Open Song**:
     - On the **Song Detail** screen, the top action row or tab actions let the user import an external audio file directly as a take in that song, or import a `.txt` file / bundle's lyrics directly into the song's Lyrics tab.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Editorial warm-paper notebook (`SongPad` theme) and cool-slate studio (`Melody Studio` theme), matching the existing tactile studio aesthetic.
  - *Color Palette & Mood*: Uses `SongPadTheme.colors` semantic tokens (`c.bg`, `c.surface`, `c.accent`, `c.accent100`, `c.accent800`, `c.text`, `c.textSecondary`, `c.divider`, `c.success`) with zero hardcoded hex values in feature screens.
  - *Typography & Hierarchy*: `Caprasimo` display headings (`SongPadFonts.heading`) paired with `Figtree` body and label typography, maintaining crisp pill badges (`TagChip`) and `SettingsGroupCard` containers.
  - *Interactive Feedback & Motion*: Smooth progress indicators during bundle extraction/waveform peak generation, clear confirmation cards with file size and format badges, and non-destructive conflict resolution (automatically suffixing `(imported)` if a song with the same name already exists).

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Smart Output Container (Direct File vs. `.zip` Bundle)**
  - *Chosen Approach*: Output a direct `.txt` file for *Lyrics only*, a direct `.mp3`/`.wav`/`.m4a`/`.flac` file when exporting a single take without extra bundle files, and a `.zip` archive when exporting multiple takes or *Everything* (audio + lyrics + notes + `song.json`).
  - *Why*: Android's `CreateDocument` and share targets map unknown extensions like `.songpad` to `.bin` (`application/octet-stream`) or append `.zip`. Using real `.txt`, `.mp3`, `.wav`, `.m4a`, `.flac`, and `.zip` extensions with matching MIME types guarantees every exported file opens immediately in music players, text editors, and file managers.
  - *Alternatives Considered*: Always wrapping single takes or lyrics in a `.zip`—rejected because users exporting a single MP3 or lyric sheet want to play or read it immediately without unzipping first.
- **Decision 2: Enriching `song.json` Manifest While Supporting Legacy Exports**
  - *Chosen Approach*: Include `bodyText` and `customLabel` inside `SongBundleManifest.SectionInfo` and `bodyText` inside `SongBundleManifest.NoteInfo` for lossless JSON restoration, while also adding a fallback parser for `lyrics.txt` and `notes.txt` when importing older `.songpad` / `.bin` archives that only stored section/note IDs in `song.json`.
  - *Why*: Ensures users who already exported `.songpad` or `.bin` files before this update can recover 100% of their takes, lyrics, and notes.
- **Decision 3: Waveform Peaks & Audio Metadata Extraction on Import**
  - *Chosen Approach*: When importing audio files (from a bundle or standalone), extract duration and sample rate via Android `MediaMetadataRetriever` / `PcmDecoder` and compute a `.peaks` sidecar file (`PeakReducer`) so imported takes display full interactive waveforms in the `RibbonPlayer` just like native recordings.
  - *Why*: Prevents blank waveforms or zero-duration takes after import.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                UI & Screens                                  │
│                                                                              │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────────┐  │
│  │    LibraryScreen     │  │   SongDetailScreen   │  │   SettingsScreen   │  │
│  │  • Header Import Btn │  │  • Export Song / Take│  │  • Storage & Data  │  │
│  │  • Import Sheet      │  │  • Import Take/Lyrics│  │    Import Action   │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └─────────┬──────────┘  │
│             │                         │                        │             │
│             ▼                         ▼                        ▼             │
│  ┌────────────────────────────────────┐  ┌────────────────────────────────┐  │
│  │            ExportScreen            │  │       ImportBottomSheet        │  │
│  │  • Mode: Everything / Takes / Text │  │  • Auto-detects .zip/.songpad/ │  │
│  │  • Scope: Full Song or Single Take │  │    .bin, audio/*, or .txt      │  │
│  │  • Dynamic MIME SAF & Share Intent │  │  • Progress + Open Song CTA    │  │
│  └─────────────────┬──────────────────┘  └────────────────┬───────────────┘  │
└────────────────────┼──────────────────────────────────────┼──────────────────┘
                     │                                      │
                     ▼                                      ▼
┌────────────────────────────────────────┐ ┌───────────────────────────────────┐
│          Export Pipeline               │ │          Import Engine            │
│                                        │ │                                   │
│  ExportViewModel ──► ExportJobRunner   │ │  SongPadImporter (UseCase/Repo)   │
│         │                              │ │    ├─ Zip/Bundle Inspector        │
│         ▼                              │ │    │   (song.json + lyrics.txt)   │
│    ExportWorker                        │ │    ├─ Standalone Audio Importer   │
│    ├─ Direct .txt (Lyrics Only)        │ │    │   (M4A/FLAC/WAV/MP3 + Peaks) │
│    ├─ Direct .mp3/.wav/.m4a/.flac      │ │    └─ Standalone .txt Parser      │
│    │   (Single Take Export)            │ │        ([Section] tag detection)  │
│    └─ Standard .zip (Multi-item Bundle)│ └────────────────┬──────────────────┘
│         │                              │                  │
│         ▼                              │                  ▼
│  Native LAME JNI (libsongpad_lame.so)  │ ┌───────────────────────────────────┐
│  • Vendored LAME 3.100 C sources       │ │   Room DB & TakeFileStore (IO)    │
│  • 48 kHz mono, 192 kbps CBR MP3       │ │  • Song + Take + Audio/Midi Asset │
└────────────────────────────────────────┘ │  • LyricsDocument + Sections      │
                                           └───────────────────────────────────┘
```

- **Data Model & State**:
  - **`ExportRequest` & `ExportState.Success`**:
    - Add optional `takeId: String? = null` to `ExportRequest` so exporting from a take's `•••` menu scopes the export to that single take (while allowing the user to toggle between "Selected take" and "All takes" on the Export screen).
    - Add `mimeType: String` to `ExportState.Success` and `ExportJobStatus.Done` so `ExportScreen` launches `CreateDocument(mimeType)` and `Intent.ACTION_SEND` with the exact MIME type (`text/plain`, `audio/mpeg`, `audio/wav`, `audio/mp4`, `audio/flac`, or `application/zip`).
  - **`SongBundleManifest` (`song.json`)**:
    - Extend `SectionInfo` with `bodyText: String = ""` and `customLabel: String? = null`.
    - Extend `NoteInfo` with `bodyText: String = ""`.
    - Maintain backward compatibility (`ignoreUnknownKeys = true` and default values) so archives from earlier builds parse cleanly and fall back to `lyrics.txt` / `notes.txt` when `bodyText` in JSON is blank.
  - **`ImportResult` & `SongPadImporter`**:
    - `ImportResult.SongImported(songId, songName, takeCount, sectionCount, noteCount)`
    - `ImportResult.TakeImported(takeId, takeName, songId)`
    - `ImportResult.LyricsImported(documentId, songId, sectionCount)`

- **Interactive Component & State Mapping**:
  - **Direct vs. Zip Export Resolution**:
    - `ExportMode.LYRICS_ONLY` → writes `<SongName>_Lyrics.txt` (`text/plain`) directly to `files/tmp/` without wrapping in a zip.
    - Single take export (either `request.takeId != null` or `ExportMode.AUDIO_TAKES` with 1 take and no MIDI) → encodes/copies directly to `<SongName> - <TakeName>.<ext>` (`audio/mpeg`, `audio/wav`, `audio/mp4`, or `audio/flac`).
    - `ExportMode.EVERYTHING` or multi-take `ExportMode.AUDIO_TAKES` → writes `Export_<SongName>_<yyyyMMdd-HHmm>.zip` (`application/zip`).
  - **Universal Import Auto-Detection**:
    - Inspects the first 4 magic bytes of the selected `Uri` stream: if it starts with `PK\x03\x04` (ZIP header), it processes it as a SongPad bundle regardless of whether the filename ends in `.zip`, `.songpad`, or `.bin`.
    - If the MIME type or extension is audio (`.m4a`, `.mp3`, `.wav`, `.flac`, `.aac`, `.ogg`) or `RIFF` / `fLaC` / `ID3` / `ftyp` magic bytes are detected, it copies the audio into a new `takes/<takeId>/` directory, decodes/extracts duration and waveform `.peaks`, and commits a `READY` take (either unassigned or attached to the target `songId`).
    - Otherwise, if valid UTF-8 text is detected (`.txt`), it parses section headers like `[Verse 1]`, `[Chorus]`, `[Bridge]` (or imports as a Verse section) into either the target song's `LyricsDocument` or a new standalone `LyricsDocument`.
