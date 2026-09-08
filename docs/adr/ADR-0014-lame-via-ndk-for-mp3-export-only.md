# ADR-0014: LAME via NDK for MP3 export only

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Phase 0 §13 requires MP3 export. Android has never shipped an MP3 encoder and MediaCodec offers no route.

## Decision

`LameMp3Encoder` calls a tiny JNI shim (`lame_jni.cpp`) around libmp3lame at 192 kbps mono, 48 kHz (a valid MPEG-1 Layer III rate, so no resampling). The `songpad_lame` target is built only when `src/main/cpp/lame/libmp3lame/lame.c` is vendored; otherwise `isAvailable` is false and the export screen reports `EncoderUnavailable`. LGPL 2.1 obligations: dynamic linking (`.so`), license text in Settings → Open source licenses, and the source archive published per docs/audio/LAME_INTEGRATION.md.

## Rejected alternatives

- Shine — smaller but audibly worse.
- Omitting MP3 — fails Phase 0 §13.

## Consequences

MP3 is a leaf on the export worker and does not gate Phase 1. Patents expired in 2017; there is no royalty question.

## References

- spec §4.1 MP3 export encoder
- spec §12.4
- docs/audio/LAME_INTEGRATION.md
