#!/usr/bin/env python3
"""Pixel-aligned visual comparison. Requires Pillow; never silently resizes inputs."""
from __future__ import annotations
import argparse, json
from pathlib import Path
from datetime import datetime, timezone


def compare(before: Path, after: Path, output: Path, threshold: int = 18) -> dict:
    try:
        from PIL import Image, ImageChops, ImageEnhance
    except ImportError as exc:
        raise RuntimeError("Pillow is required: install tools/kellvyn-workbench/requirements-visual.txt") from exc
    a = Image.open(before).convert("RGBA")
    b = Image.open(after).convert("RGBA")
    if a.size != b.size:
        raise ValueError(f"images must have identical dimensions; got {a.size} and {b.size}; crop/alignment first")
    output.mkdir(parents=True, exist_ok=True)
    diff = ImageChops.difference(a, b)
    gray = diff.convert("L")
    hist = gray.histogram()
    total = a.width * a.height
    changed = sum(hist[threshold + 1:])
    mae = sum(i * count for i, count in enumerate(hist)) / max(total, 1) / 255.0
    overlay = Image.blend(a, b, 0.5)
    amplified = ImageEnhance.Contrast(diff).enhance(4.0)
    a.save(output / "before.png")
    b.save(output / "after.png")
    overlay.save(output / "overlay.png")
    amplified.save(output / "difference.png")
    side = Image.new("RGBA", (a.width * 2, a.height), (24, 24, 24, 255))
    side.alpha_composite(a, (0, 0)); side.alpha_composite(b, (a.width, 0)); side.save(output / "side-by-side.png")
    result = {
        "schema": "kellvyn-visual-diff/v1", "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "dimensions": list(a.size), "threshold": threshold, "changed_pixels": changed,
        "changed_pixel_ratio": changed / max(total, 1), "mean_absolute_error": mae,
        "artifacts": ["before.png", "after.png", "overlay.png", "difference.png", "side-by-side.png"],
        "interpretation": "Metrics are pixel differences only; visual correctness still needs requirement-aware review.",
    }
    (output / "visual-diff.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--before", type=Path, required=True); p.add_argument("--after", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True); p.add_argument("--threshold", type=int, default=18)
    a = p.parse_args()
    if not 0 <= a.threshold <= 254: p.error("threshold must be 0..254")
    print(json.dumps(compare(a.before, a.after, a.output, a.threshold), indent=2))

if __name__ == "__main__": main()
