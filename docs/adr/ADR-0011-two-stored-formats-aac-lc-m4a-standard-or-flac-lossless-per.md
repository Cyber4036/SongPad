# ADR-0011: Two stored formats: AAC-LC M4A (Standard) or FLAC (Lossless), per take

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

A scratchpad library is mostly fragments that never become songs; storing everything lossless triples space on budget devices, yet lossy generations on export are a real cost for the takes that matter.

## Decision

`QualityMode` is a Settings toggle snapshotted at `StartRecordingUseCase` time and carried in `AudioAsset.mimeType` (`audio/mp4` or `audio/flac`). Nothing is stored twice; Lossless stores FLAC instead of M4A. The setting applies to new recordings only and is disclosed on three surfaces (§4.4): first-save line in the library, export screen, Settings → Storage & Data.

## Rejected alternatives

- Storing MP3 — encode loss on every take, worse than AAC at equal bitrate, software-only encoding.
- Keeping raw PCM as the original — 5.76 MB/min, twice FLAC, for no benefit.

## Consequences

`ExportSourceResolver` reads the mime type and encoders never branch on mode. The product copy for the disclosure surfaces is still an open product decision (§21.3 #1); the code ships the surfaces with draft copy.

## References

- spec §4.1 Stored audio asset
- spec §4.3
- spec §4.4
