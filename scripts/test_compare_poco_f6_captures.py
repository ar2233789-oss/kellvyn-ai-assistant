#!/usr/bin/env python3
"""Regression tests for the Poco F6 screenshot parity acceptance gate."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image


SCRIPT = Path(__file__).with_name("compare_poco_f6_captures.py")
EXPECTED_CONTROLS = [
    "Histórico", "Configurações", "Anexos e Web", "Ditado por voz",
    "Enviar mensagem", "AI", "Navegador", "Editor", "Terminal", "Bots", "Apps",
]


def write_image(path: Path, size: tuple[int, int], color: tuple[int, int, int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, color).save(path)


def write_valid_evidence(web: Path, native: Path) -> None:
    web.mkdir(parents=True, exist_ok=True)
    native.mkdir(parents=True, exist_ok=True)
    (web / "web-interaction-audit.json").write_text(
        json.dumps({
            "status": "passed",
            "checks": [
                {"name": "settings", "result": "PASS"},
                {"name": "palette", "result": "PASS"},
            ],
            "pageErrors": [],
        }),
        encoding="utf-8",
    )
    (native / "native-controls.json").write_text(
        json.dumps([{"label": label} for label in EXPECTED_CONTROLS]),
        encoding="utf-8",
    )
    (native / "native-crashes.txt").write_text(
        "No crash for com.kellvyn.assistant during startup.\n",
        encoding="utf-8",
    )
    (native / "native-lightning.mp4").write_bytes(b"non-empty video evidence fixture")
    (native / "native-response-controls.png").write_bytes(b"non-empty screenshot fixture")


def run_compare(web: Path, native: Path, output: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--web-dir", str(web),
            "--native-dir", str(native),
            "--output-dir", str(output),
            "--status-bar-top-px", "6",
            "--channel-threshold", "16",
            "--max-changed-pixels-percent", "1.0",
        ],
        text=True,
        capture_output=True,
        check=False,
    )


def read_report(output: Path) -> dict:
    path = output / "parity-metrics.json"
    if not path.is_file():
        raise SystemExit("Comparator did not write parity-metrics.json.")
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="poco-f6-lab-test-") as tmp:
        root = Path(tmp)
        web, native = root / "web", root / "native"

        # Each web capture is 50x30. Native screenshots include six status-bar rows.
        write_image(web / "web-reference.png", (50, 30), (10, 20, 30))
        write_image(web / "web-settings.png", (50, 30), (20, 30, 40))
        write_image(web / "web-after-gold-palette.png", (50, 30), (180, 120, 20))
        write_image(native / "native-screen.png", (50, 36), (10, 20, 30))
        write_image(native / "native-settings.png", (50, 36), (20, 30, 40))
        write_image(native / "native-after-gold-palette.png", (50, 36), (180, 120, 20))
        write_valid_evidence(web, native)

        # Exact image fixtures plus complete functional evidence must pass.
        pass_out = root / "pass-out"
        passed = run_compare(web, native, pass_out)
        if passed.returncode != 0:
            raise SystemExit("Comparator rejected valid exact fixtures:\n" + passed.stdout + passed.stderr)
        pass_report = read_report(pass_out)
        if pass_report.get("status") != "passed" or not pass_report.get("passed"):
            raise SystemExit("Exact fixtures did not receive an overall pass.")
        names = [item["name"] for item in pass_report.get("results", [])]
        if names != ["main", "settings", "gold-palette"]:
            raise SystemExit(f"Expected three screenshot pairs, got: {names}")
        if any(item["changedPixelsPercent"] != 0 for item in pass_report["results"]):
            raise SystemExit("Identical fixtures should have zero changed pixels.")
        for name in names:
            for suffix in ("overlay", "difference-amplified", "side-by-side"):
                if not (pass_out / f"{name}-{suffix}.png").is_file():
                    raise SystemExit(f"Missing visual evidence: {name}-{suffix}.png")

        # A large visual mismatch must fail even when image dimensions are valid.
        write_image(native / "native-settings.png", (50, 36), (220, 10, 180))
        fail_out = root / "fail-out"
        failed = run_compare(web, native, fail_out)
        fail_report = read_report(fail_out)
        if failed.returncode == 0:
            raise SystemExit("Comparator returned success for a large settings mismatch.")
        if fail_report.get("status") != "failed" or fail_report.get("passed") is not False:
            raise SystemExit("Large visual mismatch did not set overall status to failed.")
        if not any("settings" in reason and "changed pixels" in reason for reason in fail_report.get("failures", [])):
            raise SystemExit("Failure report did not identify the settings screenshot mismatch.")

        # A missing required video must also fail the overall evidence gate.
        (native / "native-lightning.mp4").unlink()
        evidence_out = root / "evidence-out"
        evidence_failed = run_compare(web, native, evidence_out)
        evidence_report = read_report(evidence_out)
        if evidence_failed.returncode == 0 or evidence_report.get("status") != "failed":
            raise SystemExit("Missing animation evidence was incorrectly accepted.")
        if not any("native-lightning.mp4" in reason for reason in evidence_report.get("failures", [])):
            raise SystemExit("Failure report did not identify missing lightning video evidence.")

        # Width mismatch must fail loudly rather than silently resizing/interpolating.
        write_image(native / "native-after-gold-palette.png", (54, 36), (180, 120, 20))
        bad_width = run_compare(web, native, root / "bad-width-out")
        if bad_width.returncode == 0 or "widths differ by more than one pixel" not in (bad_width.stdout + bad_width.stderr):
            raise SystemExit("Comparator did not reject a bad screenshot width.")

        print("PASS: exact screenshots pass; visual mismatch fails; missing animation evidence fails; no-resize guard passes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
