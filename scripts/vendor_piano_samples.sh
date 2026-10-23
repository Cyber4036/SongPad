#!/usr/bin/env bash
# Fetches the FreePats "Upright Piano KW (small)" CC0 sample pack and converts it into the
# manifest + raw PCM assets the Oboe sampler (core:audio) loads at runtime. Mirrors
# scripts/vendor_lame.sh: gitignored output, safe to re-run, wiped and rebuilt each time.
#
# Source: https://freepats.zenvoid.org/Piano/acoustic-grand-piano.html#UprightKW
# License: CC0 1.0 (public domain) — see the vendored readme.txt copied alongside the manifest.
# Requires: curl, 7z (p7zip), ffmpeg, python3.
set -euo pipefail

PACK_URL="https://freepats.zenvoid.org/Piano/UprightPianoKW/UprightPianoKW-small-SFZ+FLAC-20190703.7z"
# Computed from this exact download (no independent mirror/checksum exists for this file, unlike
# LAME's). Re-verify by hand if this script ever starts failing the check after a genuine upstream
# change — don't just delete the check.
PACK_SHA256="e346ef198fcda0694fc66ddb80c2db2a1c700e96e3a1801930e3cebfed37047f"
TARGET_SAMPLE_RATE=48000

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS_DIR="${REPO_ROOT}/core/audio/src/main/assets/piano"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

echo "vendor-piano: downloading UprightPianoKW (small, CC0)"
curl -sL --retry 3 -o "${WORK}/pack.7z" "${PACK_URL}"

echo "vendor-piano: verifying checksum"
echo "${PACK_SHA256}  ${WORK}/pack.7z" | sha256sum -c -

echo "vendor-piano: extracting"
7z x -y -o"${WORK}/extracted" "${WORK}/pack.7z" > /dev/null
PACK_DIR="$(find "${WORK}/extracted" -maxdepth 1 -mindepth 1 -type d | head -1)"
SFZ_FILE="$(find "${PACK_DIR}" -maxdepth 1 -name '*.sfz')"

rm -rf "${ASSETS_DIR}"
mkdir -p "${ASSETS_DIR}"
cp "${PACK_DIR}/readme.txt" "${ASSETS_DIR}/LICENSE-CC0.txt"

echo "vendor-piano: decoding regions from ${SFZ_FILE##*/}"
python3 "$(dirname "${BASH_SOURCE[0]}")/vendor_piano_samples.py" \
  --sfz "${SFZ_FILE}" \
  --pack-dir "${PACK_DIR}" \
  --out-dir "${ASSETS_DIR}" \
  --sample-rate "${TARGET_SAMPLE_RATE}"

echo "vendor-piano: done — $(find "${ASSETS_DIR}" -name '*.pcm' | wc -l) regions in ${ASSETS_DIR}"
du -sh "${ASSETS_DIR}"
