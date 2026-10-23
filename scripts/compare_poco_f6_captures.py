#!/usr/bin/env python3
"""Compare the actual web and Android screenshots captured by the Poco F6 lab.

No scaling or interpolation is used. The Android status bar is cropped from the top;
the crop height is fixed to the web capture height, and a one-pixel width difference
from fractional DPR rounding is trimmed at the right edge only.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageEnhance


def compare_pair(web_path: Path, native_path: Path, out: Path, name: str, status_top_px: int) -> dict:
    web = Image.open(web_path).convert("RGB")
    native_full = Image.open(native_path).convert("RGB")

    # 444 CSS px at DPR 2.75 rounds to 1221 px; Android is set to 1220 px wide.
    web_width, target_height = web.size
    if abs(native_full.width - web_width) > 1:
        raise ValueError(f"{name}: widths differ by more than one pixel: web={web.size}, native={native_full.size}")
    if native_full.height < status_top_px + target_height:
        raise ValueError(
            f"{name}: cannot make equal-height crops without scaling; "
            f"web={web.size}, native={native_full.size}, status_top={status_top_px}"
        )
    target_width = min(web_width, native_full.width)

    # Never resize: trim only DPR-rounding pixels from the right edge and crop the OS status bar.
    reference = web.crop((0, 0, target_width, target_height))
    candidate = native_full.crop((0, status_top_px, target_width, status_top_px + target_height))
    if reference.size != candidate.size:
        raise ValueError(f"{name}: crop dimensions differ: web={reference.size}, native={candidate.size}")

    ref_arr = np.asarray(reference, dtype=np.float32)
    can_arr = np.asarray(candidate, dtype=np.float32)
    abs_diff = np.abs(ref_arr - can_arr)
    changed = np.any(abs_diff > 16, axis=2)
    metrics = {
        "name": name,
        "webSource": str(web_path),
        "nativeSource": str(native_path),
        "webOriginalSize": list(web.size),
        "nativeOriginalSize": list(native_full.size),
        "statusBarCropTopPx": status_top_px,
        "comparisonSize": list(reference.size),
        "meanAbsoluteError": round(float(abs_diff.mean()), 4),
        "rootMeanSquareError": round(float(np.sqrt(np.mean(np.square(abs_diff)))), 4),
        "changedPixelsPercentAtThreshold16": round(float(changed.mean() * 100.0), 3),
        "note": "Diagnostic metrics, not an automatic pass/fail score. Inspect the overlay and amplified difference image; Android/web font rasterization and animated particles naturally differ."
    }

    out.mkdir(parents=True, exist_ok=True)
    reference.save(out / f"{name}-web-cropped.png")
    candidate.save(out / f"{name}-android-cropped.png")
    Image.blend(reference, candidate, 0.5).save(out / f"{name}-overlay.png")
    diff = ImageChops.difference(reference, candidate)
    ImageEnhance.Contrast(diff).enhance(4.0).save(out / f"{name}-difference-amplified.png")
    side = Image.new("RGB", (reference.width * 2, reference.height))
    side.paste(reference, (0, 0))
    side.paste(candidate, (reference.width, 0))
    side.save(out / f"{name}-side-by-side.png")
    return metrics


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--web-dir", type=Path, required=True)
    parser.add_argument("--native-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--status-bar-top-px", type=int, default=66)
    args = parser.parse_args()

    required = [
        args.web_dir / "web-reference.png",
        args.web_dir / "web-settings.png",
        args.native_dir / "native-screen.png",
        args.native_dir / "native-settings.png",
    ]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        parser.error("missing required screenshots: " + ", ".join(missing))

    results = [
        compare_pair(args.web_dir / "web-reference.png", args.native_dir / "native-screen.png",
                     args.output_dir, "main", args.status_bar_top_px),
        compare_pair(args.web_dir / "web-settings.png", args.native_dir / "native-settings.png",
                     args.output_dir, "settings", args.status_bar_top_px),
    ]
    report = {
        "status": "captures_compared",
        "method": "pixel-aligned crops only; no rescale/interpolation",
        "results": results,
    }
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "parity-metrics.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
