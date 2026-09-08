# ADR-0012: Media3 / ExoPlayer for playback, one instance app-wide

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Playback must seek accurately, respect audio focus, and play M4A, FLAC and headerless raw PCM (for takes still transcoding).

## Decision

A single `ExoPlayer` behind `Media3PlaybackController` (`@Singleton`). Raw PCM plays via `RawPcmDataSource` (`songpad-pcm://` scheme, synthesized WAV header) and the in-flight `.pcm.tmp` during review-before-save. Every surface (record review, ribbon player) drives the same instance, so two takes can never play at once.

## Rejected alternatives

- `MediaPlayer` — non-deterministic state machine, poor error propagation.
- `AudioTrack` — reserved for the piano engine.

## Consequences

Deletion is two-phase: the controller releases the file before the row is soft-deleted (§9.2). Ducking is ignored and playback never auto-resumes after focus loss.

## References

- spec §4.1 Playback
- spec §7.6
