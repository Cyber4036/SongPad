# ADR-0035: Mix the piano sampler into the capture file when the output route is headphones (amends §8.1)

**Status:** Accepted (amendment to a LOCKED spec clause, decided by the product owner)
**Date:** 2026-09-27

## Context

Spec §8.1 (LOCKED) said: *"In Voice+Piano mode the sampler output goes to the speaker and is picked up
acoustically by the microphone — that is the Phase 0 model… The sampler stream is **not** mixed into the
capture file."* Phase 0 §7 says the same thing in product terms: "The microphone recording is the audible
performance capture."

The Wave 4 audit found the hole in that model: with wired, USB or Bluetooth headphones connected there is no
acoustic path from the sampler to the mic, so a Piano or Voice+Piano take has **no piano in its audio file**.
Only the MIDI sidecar (written, never read in V1, §22) holds it. The user hears the piano while playing and
gets a take without it on playback. The Wave 4 pass raised this rather than fixing it. On 2026-09-27 the
product owner chose to amend §8.1 so the sampler is mixed into the file, over two alternatives: a headphone
warning, or leaving it open.

## Decision

While the piano sampler is armed (the capture screen is Recording with a non-VOICE take), `PianoSamplerEngineImpl`
watches the output route with an `AudioDeviceCallback`:

- **Headphone route** (wired headphones/headset, USB headset, Bluetooth A2DP/SCO/LE, hearing aid): the sampler's
  rendered output is mixed digitally into the capture stream.
- **Speaker route**: unchanged from the original §8.1. The mic picks the piano up acoustically, and nothing is
  mixed.

The switch is live. Plugging headphones in or out mid-take changes the behaviour from that buffer on.

Mechanics (`core/audio/src/main/cpp/sampler_tap.h`): a process-wide lock-free SPSC ring (`SamplerTap`). The
piano output callback writes each rendered buffer, and the capture input callback saturating-adds it onto the
mic samples **before** they enter the capture ring buffer. Everything downstream therefore sees one mono
signal: the PCM file, the waveform peaks, transcode and recovery. Both sides are real-time callbacks with no
allocation, locks, logging or JNI.

The two callbacks run on independent schedules with different burst sizes. An Exclusive piano stream may
deliver ~96 frames per call against a Shared capture fallback's 960, and Bluetooth A2DP output commonly
renders 1024–2048 frames at a time. The tap is therefore a small jitter buffer:

- The consumer waits for a target fill before mixing. The target is 2× the largest burst observed on either
  side, clamped to 10–170 ms. It is based on observed callback sizes, not `getFramesPerBurst()`, which doesn't
  reflect A2DP chunking.
- It re-primes after an underrun.
- It trims any backlog beyond target plus two bursts, so the piano never drifts late in the file.
- The tap is cleared, and the burst statistics reset, each time mixing is enabled.

A host simulation under ASan/UBSan (scratch harness, not committed, because tests were out of scope for this
pass) covered producer bursts of 96–1920 and consumer bursts of 96–960 with up to 5 ms of callback jitter and
±50 ppm clock skew. Over 120 s, the piano ramp came out sample-contiguous in every case after priming. Priming
takes 12–100 ms. The piano sits in the file 12–40 ms behind render time on wired routes and up to ~125 ms behind
on A2DP-sized bursts. Only an extreme 200 ppm skew caused brief re-primes.

## Why only on headphones

Mixing on the speaker route as well would record every note twice: once digitally at render time and once
acoustically, 20–100 ms later depending on the device's output latency. That produces an audible flam or
comb-filtered piano. On the speaker route the original model is already correct, so it is kept.

## Consequences

- A headphone take's piano is clean and uncoloured by the room. A speaker take's piano sounds as it did in the
  room. The two sound different, which follows from the route.
- **Bluetooth latency.** On A2DP the user hears the piano 150–250 ms after the key press and tends to sing
  against that late sound. The file carries the piano at render time, so the voice may sit slightly behind it
  on playback. That is inherent in Bluetooth monitoring. Wired headphones don't have it.
- **Bluetooth speakers** (car audio, portable speakers) report the same A2DP device type as Bluetooth headphones,
  so on those routes the piano will be doubled (mixed plus heard acoustically). The device type can't tell them
  apart. Accepted as a residual.
- The jitter buffer adds 12–40 ms (wired) to ~125 ms (Bluetooth bursts) of lag to the piano in the file,
  relative to render time. On Bluetooth that partly offsets the voice lag described above.
- Voice-only takes are unaffected: the sampler is never armed for `TakeType.VOICE`.
- The MIDI sidecar is unchanged and still the frame-clock-aligned record of the performance (§8.3).

## Rejected alternatives

- **Warn in Piano mode when headphones are connected** (no spec change). Rejected by the product owner: it
  tells the user about the hole instead of closing it.
- **Always mix, on every route.** Rejected: it doubles the piano on the speaker route (above).
- **Resynthesize the piano from MIDI at playback or export.** Rejected: MIDI is written-never-read in V1 (§22),
  and the result would not match what the user heard on the speaker route.
- **Mix in Kotlin on the writer thread instead of in the capture callback.** Rejected: the writer thread drains
  in bursts, so aligning the piano there would need timestamps on both streams. Mixing callback-to-callback
  keeps both on the audio clock with a bounded lag.

## Verification status

The build compiles and links for all ABIs. It has **not been run on a device**. Before beta it needs a manual
check on the Pixel 4a and Tecno Spark 8 with wired and Bluetooth headphones: the piano is audible in the saved
take, is not doubled on the speaker route, the voice-to-piano alignment is acceptable by ear, and the level
balance works: the mix is digital full-scale against an `Unprocessed` mic, so the piano may sit too loud over the
voice (spec §18,
"only a person can confirm the result is musical").
