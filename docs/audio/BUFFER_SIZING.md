# Capture buffer sizing (spec §7.1, §7.2)

Three buffers sit between the microphone and the file. Each has one job and one owner.

| Buffer | Owner | Size | Why |
| --- | --- | --- | --- |
| Oboe stream buffer | Oboe / HAL | `2 × framesPerBurst` (`setBufferSizeInFrames`), falling back to whatever the device grants | Two bursts is the low-latency default; capture does not need lower and a larger stream buffer only adds latency to the waveform, not safety |
| Ring buffer (`ring_buffer.h`) | native callback → Kotlin writer | **≥ 2 s** of audio = 96,000 frames = 192 kB (power-of-two capacity) | §7.2 rule 6: a transient writer stall (page-cache flush, GC pause, `fsync`) must not drop samples. 2 s covers the worst observed `fsync` on eMMC budget devices with margin |
| Writer chunk | `CaptureWriterThread` | drains whatever is available, writes through a `BufferedOutputStream`, `flush` + `FileDescriptor.sync()` every **48,000 samples (~1 s)** | §7.3: every-buffer fsync destroys battery; never fsyncing loses the page-cache window. ~1 s bounds loss on `am kill` to ≤ 1 s (S-1 threshold) |

## Threads

- **Oboe callback thread** — copies I16 samples into the ring buffer and returns. No allocation, lock, log or JNI object
  creation (§7.2 rule 3). Overruns are counted (`getXRunCount`) and logged from the writer, never from the callback.
- **Writer thread** — a plain `Thread` whose first statement is `Process.setThreadPriority(THREAD_PRIORITY_AUDIO)`.
  Drains the ring, appends to `audio.pcm.tmp`, computes peak/RMS per block (`PeakReducer`), publishes 10 Hz amplitudes
  with `DROP_OLDEST`, and calls the `onSyncCadence` hook so the MIDI `events.log` fsyncs on the same beat (§8.5).
- **Nothing else** touches audio data. `Dispatchers.IO` is forbidden on this path (ADR-0009 / ADR-0027).

## Clock anchors

Every sync cadence also samples `AudioStream::getTimestamp()` and feeds `ClockMapping` (§8.3): a least-squares slope
between `CLOCK_MONOTONIC` and frame position, clamped to ±500 ppm, with `outputLatencyFrames` subtracted so a UI-thread
key event maps to the frame the user actually heard. `ClockMappingTest` simulates drift and shows the rejected
"single t0 anchor" approach diverging by ≥ 11 ms at 5 minutes, above the S-3 threshold.

## Numbers to re-measure on hardware (S-1)

- Actual `framesPerBurst` and granted buffer capacity per device (logged at stream open).
- XRun count over 30 minutes.
- Longest single `fsync` duration (add a timer around `sync()` in `PcmWriter` for the spike build).
