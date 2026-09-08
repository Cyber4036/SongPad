# SMF Type 0 byte schema (spec §8.4) and `events.log` (spec §8.5)

Written by `core/midi/src/main/kotlin/com/songpad/core/midi/smf/SmfWriter.kt`; byte-exact tests in `SmfWriterTest`
and `VlqTest`.

## Timing constants (`MidiTiming`)

| Constant | Value | Derivation |
| --- | --- | --- |
| Sample rate | 48,000 Hz | `AudioFormat.SAMPLE_RATE` |
| PPQ | 480 | spec §8.4 |
| Tempo | 120 BPM = 500,000 µs per quarter | spec §8.4 |
| Frames per quarter | 24,000 | 48,000 × 60 / 120 |
| **Frames per tick** | **50** | 24,000 / 480 — an integer, so frame→tick never accumulates rounding error |

## File layout

| Offset | Bytes | Meaning |
| --- | --- | --- |
| 0x00 | `4D 54 68 64` | `MThd` |
| 0x04 | `00 00 00 06` | Header length |
| 0x08 | `00 00` | Format 0 |
| 0x0A | `00 01` | 1 track |
| 0x0C | `01 E0` | 480 PPQ |
| 0x0E | `4D 54 72 6B` | `MTrk` |
| 0x12 | 4 bytes BE | Track length (everything after this field to end of file) |
| 0x16 | `00 FF 51 03 07 A1 20` | Tempo meta event, 500,000 µs/quarter |
| … | `[VLQ] 90 kk vv` | Note On, channel 0, key `kk`, velocity `vv` (1–127) |
| … | `[VLQ] 80 kk 00` | Note Off, channel 0 |
| end | `00 FF 2F 00` | End of Track |

Events are sorted by frame index (stable), converted to ticks with integer division, and each delta is the difference
from the previous event's tick. Running status is **not** used — every event carries its status byte, which keeps the
writer trivially verifiable. A Note On with velocity 0 is never emitted; releases are always `0x80`.

## VLQ

7 data bits per byte, most significant group first, bit 7 set on every byte except the last.

```
  1060 = 0b100_0100100
  split → [0001000][0100100] = 0x08, 0x24
  VLQ   → 0x88 0x24
```

`Vlq.encode(0)` is `00`; `Vlq.encode(0x0FFFFFFF)` is `FF FF FF 7F` (the 4-byte maximum).

## `events.log` record (11 bytes, big-endian, fixed width)

| Offset | Size | Field |
| --- | --- | --- |
| 0 | 8 | frameIndex (`Long`, audio frame timeline from `AudioRecorderEngine.frameIndexFor`) |
| 8 | 1 | note (0–127) |
| 9 | 1 | velocity (0–127) |
| 10 | 1 | 1 = Note On, 0 = Note Off |

Appended for every key event and `fsync`ed on the PCM writer's ~1 s cadence via `OboeAudioRecorderEngine.onSyncCadence`.
On a clean stop it is converted to `performance.mid` and deleted. On recovery, `EventsLog.read(file, maxFrameExclusive =
recoveredFrames)` drops events beyond the recovered PCM and `closeDangling` emits Note Offs at the last frame, so audio and
MIDI are truncated to the same point by construction. A trailing partial record (kill mid-write) is ignored.
