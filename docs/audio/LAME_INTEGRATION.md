# LAME (MP3 export) integration (spec §12.4, ADR-0014)

MP3 export uses libmp3lame through a ~100-line JNI shim (`core/audio/src/main/cpp/lame_jni.cpp`). The encoder is an
export-time leaf: nothing else in the app depends on it, and the build works without it.

## Vendoring

1. Download the LAME 3.100 source tarball (https://lame.sourceforge.io/) and verify its checksum.
2. Copy `libmp3lame/`, `include/`, and `mpglib/` (only the headers mpglib needs) into
   `core/audio/src/main/cpp/lame/` so that `core/audio/src/main/cpp/lame/libmp3lame/lame.c` exists.
3. `CMakeLists.txt` detects that file and adds the `songpad_lame` shared library target; `LameMp3Encoder.libraryLoaded`
   becomes true at runtime and the export screen offers MP3.
4. Do **not** commit the LAME sources to this repository (LGPL text and attribution are already in
   `core/ui/licenses/`); CI fetches them in the `vendor-lame` step. Without them, `ExportError.EncoderUnavailable("MP3")`
   is a real, tested state.

## Build settings

- Compiled with `-DSTDC_HEADERS -DHAVE_STRCHR -DHAVE_MEMCPY -DHAVE_INTTYPES_H -O2`, per ABI (arm64-v8a, armeabi-v7a,
  x86_64). ~400 KB per ABI; App Bundle splits mean a device downloads one.
- Encoder settings: 48 kHz in / 48 kHz out (no resampling — 48 kHz is a valid MPEG-1 Layer III rate), mono, 192 kbps CBR,
  quality 2. `lame_set_num_channels(1)`, `lame_set_mode(MONO)`, `lame_set_brate(192)`, `lame_set_quality(2)`.
- JNI surface: `nativeInit(sampleRate, channels, kbps) → handle`, `nativeEncode(handle, ShortArray, samples, ByteArray) → bytes`,
  `nativeFlush(handle, ByteArray) → bytes`, `nativeClose(handle)`.

## Licensing obligations (LGPL 2.1)

- Dynamic linking (`libsongpad_lame.so`) is the compliance path LGPL asks for on Android.
- Ship the license text: Settings → About → Open source licenses lists LAME with LGPL 2.1 (`core/ui/licenses/LAME-LGPL-2.1.txt`).
- Publish the exact source archive and patches used for the build alongside each release
  (`docs/play/OPEN_SOURCE.md` records the URL). No app source needs to be published.
- MP3 patents expired in 2017; there is no royalty question.

## Quality note (normative)

A Standard-mode take (AAC-LC 128 kbps) exported to MP3 is a second lossy generation. `ExportSourceResolver` prefers
FLAC when the take was recorded in Lossless mode, giving a single generation. The export screen states the source
format before confirmation; nothing labels an MP3 or a WAV from AAC as "lossless".
