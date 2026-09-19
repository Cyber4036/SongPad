# ADR-0033: Piano sampler implementation choices (asset format, velocity layers, key range)

**Status:** Accepted (implementation detail inside the LOCKED ADR-0015 architecture, raised)
**Date:** 2026-09-20

## Context

ADR-0015 (LOCKED) specifies an Oboe polyphonic sampler with mono samples, 3–4 velocity layers,
~15–25 MB of assets, shipped compressed (OGG/FLAC) and decoded once to PCM at load. Building it
required sourcing real sample audio with a license SongPad can ship. No licensed piano sample pack
was available in the development environment; three deviations from the literal spec text follow
from what a verifiably CC0 pack actually contains.

## Decision

Vendors FreePats "Upright Piano KW (small)" (CC0 1.0, `scripts/vendor_piano_samples.sh` fetches it
by checksum, same pattern as `vendor_lame.sh`) — 26 regions spanning the full 88-key range
(MIDI 21–108), decoded once at vendor time to raw 48 kHz mono PCM (~9.9 MB total, committed to
`core/audio/src/main/assets/piano/` since CC0 carries no LAME-style "don't commit" obligation).

Three deviations from ADR-0015's literal text, each a size/availability tradeoff rather than a
product decision:

1. **One velocity layer, not 3–4.** The pack's dynamics come from a single recorded velocity;
   `PianoEngine::velocityToGain()` derives dynamics from touch velocity via amplitude only
   (`pow(normalized, 0.6)`), not layer switching. A 3–4 layer CC0 pack at this size didn't exist;
   the alternative (VCSL) had 4 layers but an undocumented, self-contradicting loudness ordering
   (peak amplitude and decay duration disagreed on which end was soft) — not something to guess at
   for a real-time audio path.
2. **Raw PCM shipped directly, not OGG/FLAC.** Avoids needing an on-device audio decoder (another
   vendoring problem) for a 9.9 MB payload already well under budget; the "decode once at load"
   behavior ADR-0015 actually cares about is unchanged — `PianoSamplerEngineImpl.loadAsync()` reads
   the manifest and PCM assets into native memory exactly once, asynchronously, at screen-open.
3. **No horizontal scroll; a fixed 2-octave key range (C3–C5, MIDI 48–72) in `PianoKeyboard`**,
   not the full 88 keys the samples cover. Landscape piano — the natural place for a wider keyboard
   — is explicitly out of scope for Phase 1 (spec §3); a portrait keyboard wide enough for a
   scrollable 88 keys without gesture conflicts between scrolling and glissando was not attempted.

## Rejected alternatives

- Procedurally-synthesized placeholder tones instead of real samples — rejected in favor of sourcing
  a real CC0 pack (user decision).
- VCSL Kawai Grand (4 velocity layers, matches ADR-0015's layer count) — rejected: plain WAV with no
  archive-tool blocker, but the v1–v4 ordering is undocumented and two independent measurements
  (onset peak amplitude vs. decay duration) disagreed on which layer was louder.
- Vendoring LAME-style (gitignored, fetched by CI) instead of committing the PCM assets — rejected:
  CC0 carries no legal reason to keep it out of the repository, unlike LAME's LGPL "don't commit
  source" requirement, and committing means the app never silently ships a piano with no sound.

## Consequences

The sampler engine (`piano_engine.h/.cpp`) is architecturally exactly what ADR-0015 specifies —
Oboe output stream, 32-voice pool, oldest-then-quietest stealing, pitch-shift resampling, looped
sustain — and is unit-tested on the host under ASan/UBSan
(`core/audio/src/test/cpp/piano_engine_test.cpp`) independent of any Android device. Swapping in a
licensed multi-velocity-layer pack later is a `vendor_piano_samples.sh` + manifest change, not an
engine rewrite: `PianoRegion`/`loadRegion` already carry a `lokey`/`hikey` range per region, so
multiple regions per key range (one per velocity layer) drop in without touching `PianoEngine`'s
voice logic — only `findRegionForNote` would need a velocity parameter.

## References

- ADR-0015 (Oboe polyphonic sampler for the piano engine)
- spec §8.1 (Sampler engine), §8.2 (Keyboard UI), §3 (Phase 1 scope — landscape piano out of scope)
- docs/audio/LAME_INTEGRATION.md (the analogous vendoring pattern this reuses)
