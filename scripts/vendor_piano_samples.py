#!/usr/bin/env python3
"""Converts a FreePats-style SFZ + FLAC region set into the manifest + raw PCM assets
core:audio's Oboe sampler loads directly (no on-device audio decoder needed — see
docs/audio/PIANO_SAMPLER.md). Invoked by scripts/vendor_piano_samples.sh; not meant to run
standalone against arbitrary SFZ files (the parser only understands the handful of opcodes
this specific pack uses).
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REGION_RE = re.compile(r"<region>")
OPCODE_RE = re.compile(r"(\w+)=(\S+)")


def parse_sfz(sfz_path: Path):
    """Returns (default_loop_mode_per_group, [region_dicts]). Only understands the opcodes
    UprightPianoKW's SFZ actually uses: lokey/hikey/pitch_keycenter/loop_start/loop_end/sample
    at <region> scope, and loop_mode at <group> scope."""
    regions = []
    current_group_loop_mode = "no_loop"
    current = None
    for raw_line in sfz_path.read_text().splitlines():
        line = raw_line.split("//")[0].strip()
        if not line:
            continue
        if line.startswith("<group>"):
            current_group_loop_mode = "no_loop"
            continue
        if line.startswith("<region>"):
            if current is not None:
                regions.append(current)
            current = {"loop_mode": current_group_loop_mode}
            continue
        for key, value in OPCODE_RE.findall(line):
            if current is None:
                if key == "loop_mode":
                    current_group_loop_mode = value
            else:
                current[key] = value
    if current is not None:
        regions.append(current)
    return regions


def ffprobe_sample_rate(path: Path) -> int:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "a:0",
         "-show_entries", "stream=sample_rate", "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, check=True,
    )
    return int(out.stdout.strip())


def decode_to_pcm(src: Path, dst: Path, target_rate: int) -> int:
    """Decodes to mono s16le PCM at target_rate. Returns the frame count written."""
    subprocess.run(
        ["ffmpeg", "-y", "-v", "error", "-i", str(src),
         "-ac", "1", "-ar", str(target_rate), "-f", "s16le", str(dst)],
        check=True,
    )
    return dst.stat().st_size // 2  # 2 bytes/frame (mono, 16-bit)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sfz", required=True, type=Path)
    ap.add_argument("--pack-dir", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    ap.add_argument("--sample-rate", required=True, type=int)
    args = ap.parse_args()

    regions = parse_sfz(args.sfz)
    if not regions:
        print("vendor-piano: no <region> blocks parsed — SFZ format changed upstream?", file=sys.stderr)
        sys.exit(1)

    manifest = []
    for r in regions:
        for required in ("lokey", "hikey", "pitch_keycenter", "sample"):
            if required not in r:
                print(f"vendor-piano: region missing {required}: {r}", file=sys.stderr)
                sys.exit(1)
        src = args.pack_dir / r["sample"]
        native_rate = ffprobe_sample_rate(src)
        stem = Path(r["sample"]).stem  # e.g. "C1vH"
        pcm_name = f"{stem}.pcm"
        total_frames = decode_to_pcm(src, args.out_dir / pcm_name, args.sample_rate)

        looped = r.get("loop_mode") == "loop_continuous" and "loop_start" in r and "loop_end" in r
        scale = args.sample_rate / native_rate
        loop_start = round(int(r["loop_start"]) * scale) if looped else 0
        loop_end = round(int(r["loop_end"]) * scale) if looped else 0
        if looped:
            # Clamp — rounding after rate conversion can push loop_end a frame past EOF.
            loop_end = min(loop_end, total_frames)
            loop_start = min(loop_start, loop_end - 1) if loop_end > 0 else 0

        manifest.append({
            "file": pcm_name,
            "lokey": int(r["lokey"]),
            "hikey": int(r["hikey"]),
            "rootNote": int(r["pitch_keycenter"]),
            "sampleRate": args.sample_rate,
            "totalFrames": total_frames,
            "looped": looped,
            "loopStartFrame": loop_start,
            "loopEndFrame": loop_end,
        })

    manifest.sort(key=lambda m: m["rootNote"])
    (args.out_dir / "manifest.json").write_text(json.dumps({"regions": manifest}, indent=2))
    print(f"vendor-piano: wrote {len(manifest)} regions")


if __name__ == "__main__":
    main()
