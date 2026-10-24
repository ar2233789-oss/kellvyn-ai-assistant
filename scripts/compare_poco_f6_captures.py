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
    parser.add_argument("--status-bar-top-px", type=int, default=136)
    args = parser.parse_args()

    required = [
        args.web_dir / "web-reference.png",
        args.web_dir / "web-settings.png",
        args.web_dir / "web-after-gold-palette.png",
        args.native_dir / "native-screen.png",
        args.native_dir / "native-settings.png",
        args.native_dir / "native-after-gold-palette.png",
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

    # Palette switching is a separate parity case: compare both implementations after
    # applying the same gold preset, not just the untouched default screenshots.
    web_palette = args.web_dir / "web-after-gold-palette.png"
    native_palette = args.native_dir / "native-after-gold-palette.png"
    palette_comparison = "not_captured"
    if web_palette.is_file() and native_palette.is_file():
        results.append(compare_pair(web_palette, native_palette, args.output_dir,
                                    "gold-palette", args.status_bar_top_px))
        palette_comparison = "compared"
    elif web_palette.is_file() != native_palette.is_file():
        palette_comparison = "incomplete_pair"
    else:
        palette_comparison = "not_captured"

    evidence = {}
    web_audit = args.web_dir / "web-interaction-audit.json"
    if web_audit.is_file():
        try:
            audit = json.loads(web_audit.read_text(encoding="utf-8"))
            checks = audit.get("checks", [])
            evidence["webInteractionAudit"] = {
                "status": audit.get("status", "unknown"),
                "totalChecks": len(checks),
                "passedChecks": sum(1 for item in checks if item.get("result") == "PASS"),
                "failedChecks": sum(1 for item in checks if item.get("result") != "PASS"),
                "pageErrors": audit.get("pageErrors", []),
            }
        except (json.JSONDecodeError, OSError) as error:
            evidence["webInteractionAudit"] = {"status": "unreadable", "error": str(error)}

    native_controls = args.native_dir / "native-controls.json"
    if native_controls.is_file():
        try:
            controls = json.loads(native_controls.read_text(encoding="utf-8"))
            labels = [item.get("label", "") for item in controls]
            expected_labels = [
                "Histórico", "Configurações", "Anexos e Web", "Ditado por voz",
                "Enviar mensagem", "AI", "Navegador", "Editor", "Terminal", "Bots", "Apps",
            ]
            evidence["nativeControlInventory"] = {
                "count": len(controls),
                "labels": labels,
                "missingExpectedLabels": [label for label in expected_labels if label not in labels],
            }
        except (json.JSONDecodeError, OSError) as error:
            evidence["nativeControlInventory"] = {"status": "unreadable", "error": str(error)}

    crash_report = args.native_dir / "native-crashes.txt"
    if crash_report.is_file():
        crash_text = crash_report.read_text(encoding="utf-8", errors="replace")
        evidence["startupCrashScan"] = {
            "status": "clean" if "No crash for com.kellvyn.assistant" in crash_text else "inspect",
            "report": crash_text[:2000],
        }

    evidence["nativeVisualEvidence"] = {
        "paletteScreenshot": native_palette.is_file(),
        "lightningVideo": (args.native_dir / "native-lightning.mp4").is_file(),
        "responseControlScreenshot": (args.native_dir / "native-response-controls.png").is_file(),
        "failureSummary": (args.native_dir / "lab-failure-summary.txt").is_file(),
    }

    report = {
        "status": "captures_compared",
        "method": "pixel-aligned crops only; no rescale/interpolation",
        "paletteComparison": palette_comparison,
        "evidence": evidence,
        "results": results,
    }
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "parity-metrics.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
