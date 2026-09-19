# LAME (MP3 export) integration (spec §12.4, ADR-0014)

MP3 export uses libmp3lame through a ~100-line JNI shim (`core/audio/src/main/cpp/lame_jni.cpp`). The encoder is an
export-time leaf: nothing else in the app depends on it, and the build works without it.

## Vendoring

1. Run `scripts/vendor_lame.sh`. It downloads the LAME 3.100 source tarball, verifies its sha256
   (`ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e`), and copies `libmp3lame/`,
   `include/`, and `mpglib/` into `core/audio/src/main/cpp/lame/` so that
   `core/audio/src/main/cpp/lame/libmp3lame/lame.c` exists. Safe to re-run.
2. The tarball ships only `config.h.in` (autoconf generates the real `config.h` on a host build);
   the script writes a hand-verified `config.h` for bionic/Clang instead — confirmed by compiling
   and linking all 20 `libmp3lame/*.c` translation units + the JNI shim for every ABI. Two things
   in it aren't obvious from upstream:
   - `ieee754_float32_t` isn't typedef'd anywhere in the tarball (glibc's `<ieee754.h>` normally
     supplies it; bionic has no such header) even though `util.h` declares `fast_log2()` in terms
     of it unconditionally, so the generated `config.h` typedefs it to `float` directly.
   - `HAVE_MPGLIB` is deliberately left **unset**. Setting it pulls `libmp3lame/mpglib_interface.c`'s
     MP3 *decode* path (`InitMP3`/`decodeMP3`/...) into the link, which needs `mpglib/*.c` — not
     vendored, since we only ever encode. Left unset, that file's body (guarded by one
     `#ifdef HAVE_MPGLIB`) compiles to an empty translation unit.
3. `CMakeLists.txt` detects `lame/libmp3lame/lame.c` and adds the `songpad_lame` shared library
   target (the top-level `project()` call declares both `C` and `CXX` — CMake silently drops `.c`
   sources from the generated Ninja build with no diagnostic if only `CXX` is enabled, so don't
   drop the `C` language back off that line). `LameMp3Encoder.libraryLoaded` becomes true at
   runtime and the export screen offers MP3.
4. Do **not** commit the LAME sources to this repository (LGPL text and attribution are already in
   `core/ui/licenses/`); `/core/audio/src/main/cpp/lame/` is gitignored and `scripts/vendor_lame.sh`
   runs as the `vendor-lame` CI step before every native build. Without it, locally,
   `ExportError.EncoderUnavailable("MP3")` is a real, tested state.

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
