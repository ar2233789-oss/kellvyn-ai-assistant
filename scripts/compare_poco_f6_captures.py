#!/usr/bin/env python3
"""Compare web and Android captures and fail when visual parity exceeds the gate.

Images are cropped to corresponding pixel coordinates only. No resize or
interpolation is used. A report and visual evidence are written even on failure.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageEnhance


def compare_pair(web_path: Path, native_path: Path, out: Path, name: str,
                 status_top_px: int, channel_threshold: int,
                 max_changed_percent: float) -> dict:
    web = Image.open(web_path).convert("RGB")
    native_full = Image.open(native_path).convert("RGB")

    web_width, target_height = web.size
    if abs(native_full.width - web_width) > 1:
        raise ValueError(
            f"{name}: widths differ by more than one pixel: "
            f"web={web.size}, native={native_full.size}"
        )
    if native_full.height < status_top_px + target_height:
        raise ValueError(
            f"{name}: cannot make equal-height crops without scaling; "
            f"web={web.size}, native={native_full.size}, status_top={status_top_px}"
        )

    target_width = min(web_width, native_full.width)
    reference = web.crop((0, 0, target_width, target_height))
    candidate = native_full.crop(
        (0, status_top_px, target_width, status_top_px + target_height)
    )
    if reference.size != candidate.size:
        raise ValueError(
            f"{name}: crop dimensions differ: "
            f"web={reference.size}, native={candidate.size}"
        )

    ref_arr = np.asarray(reference, dtype=np.int16)
    can_arr = np.asarray(candidate, dtype=np.int16)
    abs_diff = np.abs(ref_arr - can_arr)
    changed = np.any(abs_diff > channel_threshold, axis=2)
    changed_percent = float(changed.mean() * 100.0)
    mean_absolute_error = float(abs_diff.mean())
    rmse = float(np.sqrt(np.mean(np.square(abs_diff.astype(np.float64)))))
    passed = changed_percent <= max_changed_percent

    metrics = {
        "name": name,
        "status": "passed" if passed else "failed",
        "passed": passed,
        "webSource": str(web_path),
        "nativeSource": str(native_path),
        "webOriginalSize": list(web.size),
        "nativeOriginalSize": list(native_full.size),
        "statusBarCropTopPx": status_top_px,
        "comparisonSize": list(reference.size),
        "channelThreshold": channel_threshold,
        "maxChangedPixelsPercent": max_changed_percent,
        "changedPixelsPercent": round(changed_percent, 4),
        "meanAbsoluteError": round(mean_absolute_error, 4),
        "rootMeanSquareError": round(rmse, 4),
        "note": (
            "Passing means the measured changed-pixel percentage is within the "
            "configured gate; it is literal pixel equality only when the changed "
            "percentage is zero."
        ),
    }

    out.mkdir(parents=True, exist_ok=True)
    reference.save(out / f"{name}-web-cropped.png")
    candidate.save(out / f"{name}-android-cropped.png")
    Image.blend(reference, candidate, 0.5).save(out / f"{name}-overlay.png")
    diff = ImageChops.difference(reference, candidate)
    ImageEnhance.Contrast(diff).enhance(4.0).save(
        out / f"{name}-difference-amplified.png"
    )
    side = Image.new("RGB", (reference.width * 2, reference.height))
    side.paste(reference, (0, 0))
    side.paste(candidate, (reference.width, 0))
    side.save(out / f"{name}-side-by-side.png")
    return metrics


def read_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8")), None
    except FileNotFoundError:
        return None, f"missing required evidence file: {path.name}"
    except (json.JSONDecodeError, OSError) as error:
        return None, f"could not read {path.name}: {error}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--web-dir", type=Path, required=True)
    parser.add_argument("--native-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--status-bar-top-px", type=int, default=136)
    parser.add_argument("--channel-threshold", type=int, default=16)
    parser.add_argument("--max-changed-pixels-percent", type=float, default=1.0)
    args = parser.parse_args()

    if not 0 <= args.channel_threshold <= 255:
        parser.error("--channel-threshold must be from 0 to 255")
    if not 0 <= args.max_changed_pixels_percent <= 100:
        parser.error("--max-changed-pixels-percent must be from 0 to 100")

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

    args.output_dir.mkdir(parents=True, exist_ok=True)
    results = []
    pairs = [
        ("web-reference.png", "native-screen.png", "main"),
        ("web-settings.png", "native-settings.png", "settings"),
        ("web-after-gold-palette.png", "native-after-gold-palette.png", "gold-palette"),
    ]
    for web_name, native_name, result_name in pairs:
        results.append(compare_pair(
            args.web_dir / web_name,
            args.native_dir / native_name,
            args.output_dir,
            result_name,
            args.status_bar_top_px,
            args.channel_threshold,
            args.max_changed_pixels_percent,
        ))

    failures = []
    for item in results:
        if not item["passed"]:
            failures.append(
                f"{item['name']}: {item['changedPixelsPercent']}% changed pixels "
                f"exceeds {args.max_changed_pixels_percent}%"
            )

    evidence = {}
    web_audit, web_error = read_json(args.web_dir / "web-interaction-audit.json")
    if web_error:
        evidence["webInteractionAudit"] = {"status": "missing_or_unreadable", "error": web_error}
        failures.append(web_error)
    elif isinstance(web_audit, dict):
        checks = web_audit.get("checks", [])
        page_errors = web_audit.get("pageErrors", [])
        passed_checks = sum(1 for item in checks if item.get("result") == "PASS")
        failed_checks = sum(1 for item in checks if item.get("result") != "PASS")
        audit_ok = (
            web_audit.get("status") == "passed"
            and bool(checks)
            and failed_checks == 0
            and not page_errors
        )
        evidence["webInteractionAudit"] = {
            "status": web_audit.get("status", "unknown"),
            "totalChecks": len(checks),
            "passedChecks": passed_checks,
            "failedChecks": failed_checks,
            "pageErrors": page_errors,
            "passed": audit_ok,
        }
        if not audit_ok:
            failures.append("web interaction audit is not fully passed")
    else:
        evidence["webInteractionAudit"] = {"status": "invalid_shape"}
        failures.append("web interaction audit has an invalid JSON shape")

    native_controls, controls_error = read_json(args.native_dir / "native-controls.json")
    expected_labels = [
        "Histórico", "Configurações", "Anexos e Web", "Ditado por voz",
        "Enviar mensagem", "AI", "Navegador", "Editor", "Terminal", "Bots", "Apps",
    ]
    if controls_error:
        evidence["nativeControlInventory"] = {
            "status": "missing_or_unreadable", "error": controls_error
        }
        failures.append(controls_error)
    elif isinstance(native_controls, list):
        labels = [item.get("label", "") for item in native_controls if isinstance(item, dict)]
        missing_labels = [label for label in expected_labels if label not in labels]
        controls_ok = not missing_labels
        evidence["nativeControlInventory"] = {
            "status": "passed" if controls_ok else "failed",
            "count": len(native_controls),
            "labels": labels,
            "missingExpectedLabels": missing_labels,
            "passed": controls_ok,
        }
        if not controls_ok:
            failures.append("native control inventory is missing: " + ", ".join(missing_labels))
    else:
        evidence["nativeControlInventory"] = {"status": "invalid_shape"}
        failures.append("native control inventory has an invalid JSON shape")

    crash_path = args.native_dir / "native-crashes.txt"
    if crash_path.is_file():
        crash_text = crash_path.read_text(encoding="utf-8", errors="replace")
        crash_ok = "No crash for com.kellvyn.assistant" in crash_text
        evidence["startupCrashScan"] = {
            "status": "clean" if crash_ok else "inspect",
            "report": crash_text[:2000],
            "passed": crash_ok,
        }
        if not crash_ok:
            failures.append("native startup crash scan is not clean")
    else:
        evidence["startupCrashScan"] = {"status": "missing", "passed": False}
        failures.append("missing required evidence file: native-crashes.txt")

    visual_files = {
        "paletteScreenshot": "native-after-gold-palette.png",
        "lightningVideo": "native-lightning.mp4",
        "responseControlScreenshot": "native-response-controls.png",
    }
    visual_status = {
        key: (args.native_dir / filename).is_file()
        and (args.native_dir / filename).stat().st_size > 0
        for key, filename in visual_files.items()
    }
    evidence["nativeVisualEvidence"] = {
        **visual_status,
        "failureSummary": (args.native_dir / "lab-failure-summary.txt").is_file(),
        "passed": all(visual_status.values()),
    }
    for key, present in visual_status.items():
        if not present:
            failures.append(f"missing required native visual evidence: {visual_files[key]}")

    overall_passed = not failures
    report = {
        "schema": "poco-f6-parity-v2",
        "status": "passed" if overall_passed else "failed",
        "passed": overall_passed,
        "method": "pixel-aligned crops only; no rescale or interpolation",
        "criteria": {
            "channelThreshold": args.channel_threshold,
            "maxChangedPixelsPercent": args.max_changed_pixels_percent,
            "requiredScreenshotPairs": ["main", "settings", "gold-palette"],
            "requiredEvidence": [
                "webInteractionAudit", "nativeControlInventory", "startupCrashScan",
                "native-after-gold-palette.png", "native-lightning.mp4",
                "native-response-controls.png",
            ],
        },
        "paletteComparison": "compared",
        "failures": failures,
        "evidence": evidence,
        "results": results,
    }
    (args.output_dir / "parity-metrics.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, indent=2))
    if not overall_passed:
        print(f"FAIL: visual/functional parity gate rejected {len(failures)} condition(s).")
        return 1
    print("PASS: all required screenshots, interactions and visual thresholds passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
