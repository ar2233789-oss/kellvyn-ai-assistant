#!/usr/bin/env python3
"""Read-only Android animation capture and frame-timing audit.

The tool never installs, launches, taps, or changes app settings. The target package
must already be foreground on an explicitly selected, authorized ADB device.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
SERIAL_RE = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")
PACKAGE_RE = re.compile(r"^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$")
SCENARIO_RE = re.compile(r"^[\w .+:-]{1,80}$", re.UNICODE)


class AuditBlocked(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return round(ordered[lower], 3)
    weight = position - lower
    return round(ordered[lower] * (1.0 - weight) + ordered[upper] * weight, 3)


def parse_frame_stats(text: str, frame_budget_ms: float | None = None) -> dict[str, Any]:
    """Parse Android gfxinfo PROFILEDATA CSV without assuming a fixed column order."""
    lines = text.splitlines()
    header_index = next(
        (index for index, line in enumerate(lines)
         if "IntendedVsync" in line and "FrameCompleted" in line),
        None
    )
    if header_index is None:
        return {
            "status": "NO_FRAMESTATS_CSV",
            "valid_frames": 0,
            "frame_budget_ms": frame_budget_ms,
            "note": "The device did not expose the expected IntendedVsync/FrameCompleted columns."
        }

    reader = csv.DictReader(lines[header_index:])
    durations_ms: list[float] = []
    for row in reader:
        try:
            intended = int((row.get("IntendedVsync") or "").strip())
            completed = int((row.get("FrameCompleted") or "").strip())
        except (TypeError, ValueError):
            continue
        if intended <= 0 or completed <= intended:
            continue
        duration_ms = (completed - intended) / 1_000_000.0
        if math.isfinite(duration_ms) and 0.0 < duration_ms < 10_000.0:
            durations_ms.append(duration_ms)

    over_budget = (
        sum(1 for duration in durations_ms if duration > frame_budget_ms)
        if frame_budget_ms is not None else None
    )
    frozen = sum(1 for duration in durations_ms if duration > 700.0)
    return {
        "status": "PARSED" if durations_ms else "NO_VALID_FRAME_ROWS",
        "valid_frames": len(durations_ms),
        "frame_budget_ms": frame_budget_ms,
        "over_budget_frames": over_budget,
        "over_budget_ratio": (
            round(over_budget / len(durations_ms), 5)
            if over_budget is not None and durations_ms else None
        ),
        "frozen_frames_over_700ms": frozen,
        "mean_frame_ms": round(sum(durations_ms) / len(durations_ms), 3) if durations_ms else None,
        "p50_frame_ms": percentile(durations_ms, 0.50),
        "p90_frame_ms": percentile(durations_ms, 0.90),
        "p95_frame_ms": percentile(durations_ms, 0.95),
        "p99_frame_ms": percentile(durations_ms, 0.99),
        "max_frame_ms": max(durations_ms) if durations_ms else None,
        "note": "Duration is FrameCompleted minus IntendedVsync. It is a gfxinfo estimate, not a Perfetto FrameTimeline trace."
    }


def parse_gfxinfo_summary(text: str) -> dict[str, Any]:
    metrics: dict[str, Any] = {}
    total = re.search(r"(?im)^\s*Total frames rendered:\s*(\d+)", text)
    janky = re.search(r"(?im)^\s*Janky frames:\s*(\d+)\s*\(([\d.]+)%\)", text)
    if total:
        metrics["summary_total_frames"] = int(total.group(1))
    if janky:
        metrics["summary_janky_frames"] = int(janky.group(1))
        metrics["summary_janky_percent"] = float(janky.group(2))
    for percentile_name, value in re.findall(
        r"(?im)^\s*(50|90|95|99)(?:st|nd|th)? percentile:\s*([\d.]+)\s*ms",
        text
    ):
        metrics[f"summary_p{percentile_name}_ms"] = float(value)
    return metrics


def parse_meminfo_pss_kb(text: str) -> int | None:
    match = re.search(r"(?im)^\s*TOTAL PSS:\s*([\d,]+)", text)
    if not match:
        return None
    return int(match.group(1).replace(",", ""))


def parse_battery_info(text: str) -> dict[str, Any]:
    level = re.search(r"(?im)^\s*level:\s*(\d+)", text)
    scale = re.search(r"(?im)^\s*scale:\s*(\d+)", text)
    temperature = re.search(r"(?im)^\s*temperature:\s*(-?\d+)", text)
    return {
        "level_percent": (
            round(int(level.group(1)) * 100 / int(scale.group(1)), 2)
            if level and scale and int(scale.group(1)) > 0
            else int(level.group(1)) if level else None
        ),
        "temperature_c": round(int(temperature.group(1)) / 10.0, 1) if temperature else None
    }


def parse_display_refresh_rates(text: str) -> list[float]:
    values = {
        round(float(value), 3)
        for value in re.findall(r"(?i)(?:refreshRate|refreshRateOverride|fps)\s*[:=]\s*([0-9]+(?:\.[0-9]+)?)", text)
        if 1.0 <= float(value) <= 360.0
    }
    return sorted(values)


def parse_thermal_status(text: str) -> int | None:
    patterns = (
        r"(?im)^\s*Thermal Status:\s*(\d+)",
        r"(?im)^\s*Current thermal status:\s*(\d+)",
        r"(?i)mStatus=(\d+)"
    )
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            return int(match.group(1))
    return None

def classify_capture_status(
    video_extraction: str,
    valid_frames: int,
    app_foreground_after: bool
) -> str:
    """Classify artifact completeness only; deliberately does not grade animation quality."""
    if video_extraction != "PASSED" or valid_frames <= 0 or not app_foreground_after:
        return "PARTIAL"
    return "CAPTURED"


def foreground_package(activity_dump: str, package: str) -> bool:
    """Require an exact package token on Android's resumed/top-resumed activity line."""
    package_token = re.compile(
        r"(?<![A-Za-z0-9_.])" + re.escape(package.casefold()) + r"(?=$|[/\s}:])"
    )
    for line in activity_dump.splitlines():
        lower = line.casefold()
        if any(marker in lower for marker in ("mresumedactivity", "topresumedactivity", "resumedactivity")):
            if package_token.search(lower):
                return True
    return False


def write_failure_manifest(
    output: Path,
    *,
    status: str,
    serial: str,
    package: str,
    scenario: str,
    error: Exception
) -> Path | None:
    """Persist a minimal failure checkpoint only when device preflight created the run folder."""
    if not output.is_dir():
        return None
    path = output / "animation-audit.json"
    report = {
        "schema": "kellvyn-android-animation-audit/v1",
        "status": status,
        "quality_assessment": "NOT_EVALUATED",
        "serial": serial,
        "package": package,
        "scenario": scenario,
        "finished_at_utc": datetime.now(timezone.utc).isoformat(),
        "error_type": type(error).__name__,
        "error": "Capture did not complete; inspect the Workbench stderr artifact for the failure details.",
        "limitations": [
            "Capture did not complete; this manifest is a failure checkpoint, not quality evidence.",
            "Any partial video/log artifacts must be inspected independently."
        ]
    }
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return path


def safe_output_path(value: str) -> Path:
    candidate = Path(value)
    resolved = (candidate if candidate.is_absolute() else ROOT / candidate).resolve()
    if resolved != ROOT and ROOT not in resolved.parents:
        raise ValueError("output must stay inside the repository")
    return resolved


def child_environment() -> dict[str, str]:
    import os
    allowed = {
        "PATH", "HOME", "TMPDIR", "TMP", "TEMP", "LANG", "LC_ALL", "TZ",
        "ANDROID_HOME", "ANDROID_SDK_ROOT", "ANDROID_USER_HOME"
    }
    return {key: value for key, value in os.environ.items() if key in allowed}


def run_command(argv: list[str], timeout_s: int, *, allow_failure: bool = False) -> subprocess.CompletedProcess[str]:
    try:
        result = subprocess.run(
            argv, cwd=ROOT, text=True, capture_output=True, timeout=timeout_s,
            check=False, env=child_environment()
        )
    except FileNotFoundError as exc:
        raise AuditBlocked(f"executable not installed: {exc.filename}") from exc
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError(f"command timed out after {timeout_s}s: {argv[0]}") from exc
    if result.returncode and not allow_failure:
        message = (result.stderr or result.stdout or "").strip()[-3000:]
        raise RuntimeError(f"command failed ({result.returncode}): {argv[0]}: {message}")
    return result


def adb_command(adb: str, serial: str, *args: str, timeout_s: int = 30,
                allow_failure: bool = False) -> subprocess.CompletedProcess[str]:
    return run_command([adb, "-s", serial, *args], timeout_s, allow_failure=allow_failure)


def capture_android_animation(
    *,
    serial: str,
    package: str,
    output: Path,
    scenario: str = "manual-unspecified",
    duration_s: int = 10,
    frame_sample_fps: float = 2.0,
    frame_budget_ms: float | None = None,
    bit_rate: int = 6_000_000,
) -> dict[str, Any]:
    if not SERIAL_RE.fullmatch(serial):
        raise ValueError("invalid ADB serial")
    if not PACKAGE_RE.fullmatch(package):
        raise ValueError("invalid Android package name")
    if not SCENARIO_RE.fullmatch(scenario):
        raise ValueError("scenario must be 1..80 safe letters, numbers, spaces or . _ + : - characters")
    if not 5 <= duration_s <= 60:
        raise ValueError("duration must be between 5 and 60 seconds")
    if not 0.5 <= frame_sample_fps <= 10.0:
        raise ValueError("frame sample fps must be between 0.5 and 10")
    if frame_budget_ms is not None and not 4.0 <= frame_budget_ms <= 50.0:
        raise ValueError("frame budget must be between 4 and 50 ms")
    if not 1_000_000 <= bit_rate <= 20_000_000:
        raise ValueError("screenrecord bit rate must be between 1 and 20 Mbps")

    adb = shutil.which("adb")
    if not adb:
        raise AuditBlocked("adb is not installed on this host")
    if shutil.which("ffmpeg") is None:
        ffmpeg_path = None
    else:
        ffmpeg_path = shutil.which("ffmpeg")

    output = output.resolve()
    if output != ROOT and ROOT not in output.parents:
        raise ValueError("output must stay inside the repository")
    def save_text(name: str, value: str) -> None:
        (output / name).write_text(value or "(empty)\n", encoding="utf-8", errors="replace")

    report: dict[str, Any] = {
        "schema": "kellvyn-android-animation-audit/v1",
        "run_id": uuid.uuid4().hex,
        "started_at_utc": datetime.now(timezone.utc).isoformat(),
        "status": "RUNNING",
        "serial": serial,
        "package": package,
        "scenario": scenario,
        "duration_limit_seconds": duration_s,
        "frame_sample_fps": frame_sample_fps,
        "frame_budget_ms": frame_budget_ms,
        "screenrecord_bit_rate": bit_rate,
        "actions": [
            "No APK install or launch",
            "No UI taps, swipes, setting changes or source edits",
            "Temporary screen recording on the selected device, pulled to this output folder, then removed from /sdcard"
        ],
        "quality_assessment": "NOT_EVALUATED",
        "privacy_note": "The video captures the visible device display. Keep the target app foreground and close private notifications or content before starting."
    }

    devices = run_command([adb, "devices", "-l"], timeout_s=10)
    matching = [
        line for line in devices.stdout.splitlines()[1:]
        if line.split() and line.split()[0] == serial and len(line.split()) > 1 and line.split()[1] == "device"
    ]
    if not matching:
        raise AuditBlocked(f"ADB serial is not authorized and online: {serial}")

    boot = adb_command(adb, serial, "shell", "getprop", "sys.boot_completed", timeout_s=10)
    if boot.stdout.strip() != "1":
        raise AuditBlocked("selected Android device is not fully booted")

    package_path = adb_command(adb, serial, "shell", "pm", "path", package, timeout_s=10, allow_failure=True)
    if "package:" not in package_path.stdout:
        raise AuditBlocked(f"package is not installed on the selected device: {package}")

    activities = adb_command(adb, serial, "shell", "dumpsys", "activity", "activities", timeout_s=20)
    if not foreground_package(activities.stdout, package):
        raise AuditBlocked(
            f"{package} is not confirmed as the resumed/top-resumed app; bring it to the foreground before recording"
        )

    # Create output only after ADB/device/app preflight succeeds.
    output.mkdir(parents=True, exist_ok=False)
    frames_dir = output / "frames"
    frames_dir.mkdir(parents=True, exist_ok=True)
    save_text("activities-before.txt", activities.stdout)

    device_properties = []
    for prop in ("ro.product.model", "ro.product.manufacturer", "ro.build.version.release", "ro.build.version.sdk"):
        value = adb_command(adb, serial, "shell", "getprop", prop, timeout_s=10, allow_failure=True)
        device_properties.append(f"{prop}={value.stdout.strip()}")
    save_text("device-info.txt", "\n".join(device_properties))
    before_snapshots: dict[str, str] = {}
    for key, name, args, timeout in (
        ("display", "display-before.txt", ("shell", "dumpsys", "display"), 20),
        ("battery", "battery-before.txt", ("shell", "dumpsys", "battery"), 15),
        ("thermal", "thermal-before.txt", ("shell", "dumpsys", "thermalservice"), 15),
        ("memory", "memory-before.txt", ("shell", "dumpsys", "meminfo", package), 20),
    ):
        result = adb_command(adb, serial, *args, timeout_s=timeout, allow_failure=True)
        before_snapshots[key] = result.stdout + result.stderr
        save_text(name, before_snapshots[key])

    # Reset only the Android gfxinfo counters for this package. No app settings or data are changed.
    gfx_reset = adb_command(adb, serial, "shell", "dumpsys", "gfxinfo", package, "reset", timeout_s=15, allow_failure=True)
    save_text("gfxinfo-reset.txt", gfx_reset.stdout + gfx_reset.stderr)

    remote_video = f"/sdcard/kellvyn-animation-{report['run_id']}.mp4"
    video = output / "screen-recording.mp4"
    try:
        recording = adb_command(
            adb, serial, "shell", "screenrecord", "--time-limit", str(duration_s),
            "--bit-rate", str(bit_rate), remote_video,
            timeout_s=duration_s + 30
        )
        save_text("screenrecord.txt", recording.stdout + recording.stderr)
        adb_command(adb, serial, "pull", remote_video, str(video), timeout_s=45)
    finally:
        adb_command(adb, serial, "shell", "rm", "-f", remote_video, timeout_s=10, allow_failure=True)

    if not video.is_file() or video.stat().st_size < 1024:
        raise RuntimeError("screenrecord did not produce a valid MP4 artifact")

    gfx = adb_command(adb, serial, "shell", "dumpsys", "gfxinfo", package, "framestats", timeout_s=25, allow_failure=True)
    save_text("gfxinfo-framestats.txt", gfx.stdout + gfx.stderr)
    after_snapshots: dict[str, str] = {}
    for key, name, args, timeout in (
        ("activities", "activities-after.txt", ("shell", "dumpsys", "activity", "activities"), 20),
        ("display", "display-after.txt", ("shell", "dumpsys", "display"), 20),
        ("battery", "battery-after.txt", ("shell", "dumpsys", "battery"), 15),
        ("thermal", "thermal-after.txt", ("shell", "dumpsys", "thermalservice"), 15),
        ("memory", "memory-after.txt", ("shell", "dumpsys", "meminfo", package), 20),
    ):
        result = adb_command(adb, serial, *args, timeout_s=timeout, allow_failure=True)
        after_snapshots[key] = result.stdout + result.stderr
        save_text(name, after_snapshots[key])

    frame_metrics = parse_frame_stats(gfx.stdout + "\n" + gfx.stderr, frame_budget_ms)
    frame_metrics.update(parse_gfxinfo_summary(gfx.stdout + "\n" + gfx.stderr))
    report["frame_metrics"] = frame_metrics
    pss_before = parse_meminfo_pss_kb(before_snapshots.get("memory", ""))
    pss_after = parse_meminfo_pss_kb(after_snapshots.get("memory", ""))
    app_foreground_after = foreground_package(
        after_snapshots.get("activities", ""), package
    )
    report["app_foreground_before_capture"] = True
    report["app_foreground_after_capture"] = app_foreground_after
    report["resource_metrics"] = {
        "memory_total_pss_kb_before": pss_before,
        "memory_total_pss_kb_after": pss_after,
        "memory_total_pss_delta_kb": pss_after - pss_before if pss_before is not None and pss_after is not None else None,
        "battery_before": parse_battery_info(before_snapshots.get("battery", "")),
        "battery_after": parse_battery_info(after_snapshots.get("battery", "")),
        "thermal_status_before": parse_thermal_status(before_snapshots.get("thermal", "")),
        "thermal_status_after": parse_thermal_status(after_snapshots.get("thermal", "")),
        "display_refresh_rates_before": parse_display_refresh_rates(before_snapshots.get("display", "")),
        "display_refresh_rates_after": parse_display_refresh_rates(after_snapshots.get("display", ""))
    }
    report["artifacts"] = {
        "video": video.relative_to(ROOT).as_posix(),
        "gfxinfo": "gfxinfo-framestats.txt",
        "frames_directory": frames_dir.relative_to(ROOT).as_posix()
    }
    report["video_bytes"] = video.stat().st_size
    report["video_sha256"] = sha256_file(video)
    report["video_extraction"] = "NOT_RUN"

    if ffmpeg_path:
        extraction = run_command(
            [
                ffmpeg_path, "-hide_banner", "-loglevel", "error", "-i", str(video),
                "-vf", f"fps={frame_sample_fps}", "-vsync", "0",
                str(frames_dir / "frame_%05d.png")
            ],
            timeout_s=max(30, duration_s * 4),
            allow_failure=True
        )
        save_text("ffmpeg-frames.txt", extraction.stdout + extraction.stderr)
        frame_count = len(list(frames_dir.glob("frame_*.png")))
        report["video_extraction"] = "PASSED" if extraction.returncode == 0 and frame_count else "FAILED"
        report["frame_count"] = frame_count
        if frame_count:
            report["frame_hashes"] = [
                {"file": frame.name, "sha256": sha256_file(frame)}
                for frame in sorted(frames_dir.glob("frame_*.png"))
            ]
    else:
        report["video_extraction"] = "BLOCKED_FFMPEG_NOT_INSTALLED"
        report["frame_count"] = 0

    report["finished_at_utc"] = datetime.now(timezone.utc).isoformat()
    report["status"] = classify_capture_status(
        report["video_extraction"],
        frame_metrics.get("valid_frames", 0),
        app_foreground_after
    )
    # CAPTURED means the artifacts are complete; it is not an animation-quality pass.
    report["quality_assessment"] = "NOT_EVALUATED"
    report["limitations"] = [
        "gfxinfo framestats is an app-level frame timing source and does not replace Perfetto FrameTimeline.",
        "The capture measures only the current visible scenario; run each animation/effect combination separately.",
        "Foreground is checked immediately before and after the recording; temporary focus loss between those checks is not continuously monitored.",
        "Capture is marked PARTIAL if the app is not foreground at the end of the recording.",
        "screenrecord adds measurement overhead; treat gfxinfo timing as diagnostic and confirm suspected jank with Perfetto or Macrobenchmark."
        "frame_budget_ms is optional and must be selected for the target refresh rate; no refresh rate is assumed.",
        "A video and frame-timing report do not prove visual correctness; compare sampled frames with a requirement-aware baseline."
    ]
    (output / "animation-audit.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Capture Android animation video, gfxinfo and resource snapshots.")
    parser.add_argument("--serial", required=True, help="Exact authorized ADB serial; no device auto-selection.")
    parser.add_argument("--package", default="com.kellvyn.assistant")
    parser.add_argument("--scenario", default="manual-unspecified", help="Short label such as chuva, raios or chuva+raios.")
    parser.add_argument("--duration", type=int, default=10)
    parser.add_argument("--frame-sample-fps", type=float, default=2.0)
    parser.add_argument("--frame-budget-ms", type=float, default=None)
    parser.add_argument("--bit-rate", type=int, default=6_000_000)
    parser.add_argument("--output", default="artifacts/workbench/android-animation/latest")
    args = parser.parse_args(argv)
    run_dir: Path | None = None
    try:
        output = safe_output_path(args.output)
        run_dir = output / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8])
        result = capture_android_animation(
            serial=args.serial,
            package=args.package,
            output=run_dir,
            scenario=args.scenario,
            duration_s=args.duration,
            frame_sample_fps=args.frame_sample_fps,
            frame_budget_ms=args.frame_budget_ms,
            bit_rate=args.bit_rate
        )
        result["output_directory"] = run_dir.relative_to(ROOT).as_posix()
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result["status"] == "CAPTURED" else (4 if result["status"] == "PARTIAL" else 2)
    except AuditBlocked as exc:
        if run_dir is not None:
            write_failure_manifest(
                run_dir, status="BLOCKED", serial=args.serial, package=args.package, scenario=args.scenario, error=exc
            )
        report = {"schema": "kellvyn-android-animation-audit/v1", "status": "BLOCKED", "reason": str(exc)}
        print(json.dumps(report, ensure_ascii=False, indent=2), file=sys.stderr)
        return 3
    except (OSError, ValueError, RuntimeError) as exc:
        if run_dir is not None:
            write_failure_manifest(
                run_dir, status="FAILED", serial=args.serial, package=args.package, scenario=args.scenario, error=exc
            )
        report = {"schema": "kellvyn-android-animation-audit/v1", "status": "FAILED", "reason": f"{type(exc).__name__}: {exc}"}
        print(json.dumps(report, ensure_ascii=False, indent=2), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
