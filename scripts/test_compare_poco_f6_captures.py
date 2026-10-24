#!/usr/bin/env python3
"""Regression tests for the screenshot comparison utilities used by the Poco F6 lab."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image


SCRIPT = Path(__file__).with_name("compare_poco_f6_captures.py")


def write_image(path: Path, size: tuple[int, int], color: tuple[int, int, int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, color).save(path)


def run_compare(web: Path, native: Path, output: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--web-dir", str(web),
            "--native-dir", str(native),
            "--output-dir", str(output),
            "--status-bar-top-px", "6",
        ],
        text=True,
        capture_output=True,
        check=False,
    )


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="poco-f6-lab-test-") as tmp:
        root = Path(tmp)
        web, native, output = root / "web", root / "native", root / "out"
        # Each web capture is 50x30. Native captures include six rows of status-bar pixels.
        write_image(web / "web-reference.png", (50, 30), (10, 20, 30))
        write_image(web / "web-settings.png", (50, 30), (20, 30, 40))
        write_image(web / "web-after-gold-palette.png", (50, 30), (180, 120, 20))
        write_image(native / "native-screen.png", (50, 36), (10, 20, 30))
        write_image(native / "native-settings.png", (50, 36), (20, 30, 40))
        write_image(native / "native-after-gold-palette.png", (50, 36), (180, 120, 20))

        (web / "web-interaction-audit.json").write_text(
            json.dumps({"status": "passed", "checks": [
                {"name": "settings", "result": "PASS"},
                {"name": "palette", "result": "PASS"},
            ], "pageErrors": []}),
            encoding="utf-8",
        )
        (native / "native-controls.json").write_text(
            json.dumps([{"label": label} for label in [
                "Histórico", "Configurações", "Anexos e Web", "Ditado por voz",
                "Enviar mensagem", "AI", "Navegador", "Editor", "Terminal", "Bots", "Apps",
            ]]),
            encoding="utf-8",
        )
        (native / "native-crashes.txt").write_text(
            "No crash for com.kellvyn.assistant during startup.\n", encoding="utf-8"
        )
        (native / "native-lightning.mp4").write_bytes(b"test recording placeholder")
        (native / "native-response-controls.png").write_bytes(b"test evidence placeholder")

        result = run_compare(web, native, output)
        if result.returncode != 0:
            raise SystemExit("Comparator rejected valid fixtures:\n" + result.stdout + "\n" + result.stderr)

        report = json.loads((output / "parity-metrics.json").read_text(encoding="utf-8"))
        names = [item["name"] for item in report.get("results", [])]
        if names != ["main", "settings", "gold-palette"]:
            raise SystemExit(f"Expected main/settings/gold-palette comparisons, got: {names}")
        if report.get("paletteComparison") != "compared":
            raise SystemExit("The gold palette comparison was not reported as compared.")
        if report.get("evidence", {}).get("webInteractionAudit", {}).get("passedChecks") != 2:
            raise SystemExit("Web interaction audit results were not included in the parity report.")
        if report.get("evidence", {}).get("nativeControlInventory", {}).get("missingExpectedLabels"):
            raise SystemExit("The control inventory incorrectly reported missing fixture controls.")
        for name in names:
            for suffix in ("overlay", "difference-amplified", "side-by-side"):
                if not (output / f"{name}-{suffix}.png").is_file():
                    raise SystemExit(f"Missing visual evidence: {name}-{suffix}.png")

        # Width mismatch must fail loudly instead of silently resizing/interpolating screenshots.
        write_image(native / "native-after-gold-palette.png", (54, 36), (180, 120, 20))
        bad = run_compare(web, native, root / "bad-out")
        if bad.returncode == 0 or "widths differ by more than one pixel" not in (bad.stdout + bad.stderr):
            raise SystemExit("Comparator did not reject a bad screenshot width.")
        print("PASS: main/settings/palette comparisons, evidence report and no-resize guard.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
