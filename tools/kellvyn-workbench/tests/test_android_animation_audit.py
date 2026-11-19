from __future__ import annotations
import importlib.util
import subprocess
import tempfile
import unittest
from unittest import mock
from pathlib import Path

MODULE = Path(__file__).resolve().parents[1] / "android_animation_audit.py"
spec = importlib.util.spec_from_file_location("android_animation_audit", MODULE)
audit = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(audit)


class FrameStatsTests(unittest.TestCase):
    def test_parses_frame_duration_percentiles_and_budget_overruns(self):
        sample = """---PROFILEDATA---
Flags,IntendedVsync,FrameCompleted
0,1000000000,1010000000
0,1016666667,1046666667
0,1050000000,1760000000
"""
        report = audit.parse_frame_stats(sample, frame_budget_ms=16.67)
        self.assertEqual("PARSED", report["status"])
        self.assertEqual(3, report["valid_frames"])
        self.assertEqual(2, report["over_budget_frames"])
        self.assertEqual(1, report["frozen_frames_over_700ms"])
        self.assertEqual(710.0, report["max_frame_ms"])

    def test_ignores_invalid_and_incomplete_frame_rows(self):
        sample = """Flags,IntendedVsync,FrameCompleted
0,1000000000,1010000000
0,1010000000,0
0,1020000000,not-a-number
0,-1,1030000000
"""
        report = audit.parse_frame_stats(sample)
        self.assertEqual("PARSED", report["status"])
        self.assertEqual(1, report["valid_frames"])
        self.assertIsNone(report["over_budget_frames"])

    def test_reports_missing_framestats_csv_without_fake_zero_metrics(self):
        report = audit.parse_frame_stats("No data available")
        self.assertEqual("NO_FRAMESTATS_CSV", report["status"])
        self.assertEqual(0, report["valid_frames"])
        self.assertIsNone(report["p95_frame_ms"])

    def test_parses_gfxinfo_summary_percentiles(self):
        sample = """Total frames rendered: 240
Janky frames: 12 (5.00%)
50th percentile: 8ms
90th percentile: 18ms
95th percentile: 24ms
99th percentile: 35ms
"""
        report = audit.parse_gfxinfo_summary(sample)
        self.assertEqual(240, report["summary_total_frames"])
        self.assertEqual(12, report["summary_janky_frames"])
        self.assertEqual(5.0, report["summary_janky_percent"])
        self.assertEqual(24.0, report["summary_p95_ms"])

    def test_parses_total_pss_memory(self):
        self.assertEqual(123456, audit.parse_meminfo_pss_kb("TOTAL PSS: 123,456 kB"))
        self.assertIsNone(audit.parse_meminfo_pss_kb("No memory summary"))

    def test_parses_battery_level_and_temperature(self):
        report = audit.parse_battery_info("  level: 73\n  scale: 100\n  temperature: 354\n")
        self.assertEqual(73.0, report["level_percent"])
        self.assertEqual(35.4, report["temperature_c"])

    def test_parses_display_refresh_rates_without_assuming_one(self):
        rates = audit.parse_display_refresh_rates("refreshRate=120.0\nmode fps: 60.0\nrefreshRate=120.0")
        self.assertEqual([60.0, 120.0], rates)
        self.assertEqual([], audit.parse_display_refresh_rates("no refresh field"))

    def test_parses_thermal_status_when_available(self):
        self.assertEqual(3, audit.parse_thermal_status("Thermal Status: 3"))
        self.assertIsNone(audit.parse_thermal_status("thermal data unavailable"))

    def test_foreground_check_requires_resumed_activity_line(self):
        self.assertTrue(audit.foreground_package(
            "mResumedActivity: ActivityRecord{abc com.kellvyn.assistant/.MainActivity}",
            "com.kellvyn.assistant"
        ))
        self.assertTrue(audit.foreground_package(
            "topResumedActivity=ActivityRecord{abc com.kellvyn.assistant/.MainActivity}",
            "com.kellvyn.assistant"
        ))
        self.assertFalse(audit.foreground_package(
            "Historians: com.kellvyn.assistant/.MainActivity",
            "com.kellvyn.assistant"
        ))

    def test_video_and_frame_sha256_helper(self):
        with tempfile.TemporaryDirectory() as td:
            sample = Path(td) / "sample.bin"
            sample.write_bytes(b"abc")
            self.assertEqual(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                audit.sha256_file(sample)
            )

    def test_foreground_preflight_blocks_before_creating_output_directory(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td) / "repo"
            root.mkdir()
            output = root / "artifacts" / "animation-run"
            old_root = audit.ROOT
            audit.ROOT = root

            def fake_run(argv, **kwargs):
                return subprocess.CompletedProcess(
                    argv, 0, stdout="List of devices attached\nemulator-5554 device product:test\n", stderr=""
                )

            def fake_adb(adb, serial, *args, **kwargs):
                if args == ("shell", "getprop", "sys.boot_completed"):
                    stdout = "1\n"
                elif args[:3] == ("shell", "pm", "path"):
                    stdout = "package:/data/app/base.apk\n"
                elif args[:4] == ("shell", "dumpsys", "activity", "activities"):
                    stdout = "Historians: com.other.app/.MainActivity\n"
                else:
                    stdout = ""
                return subprocess.CompletedProcess([adb, *args], 0, stdout=stdout, stderr="")

            try:
                with mock.patch.object(audit.shutil, "which", side_effect=lambda name: "/usr/bin/adb" if name == "adb" else None):
                    with mock.patch.object(audit, "run_command", side_effect=fake_run):
                        with mock.patch.object(audit, "adb_command", side_effect=fake_adb):
                            with self.assertRaises(audit.AuditBlocked):
                                audit.capture_android_animation(
                                    serial="emulator-5554",
                                    package="com.kellvyn.assistant",
                                    output=output,
                                    duration_s=10
                                )
                self.assertFalse(output.exists(), "blocked foreground preflight must not leave an empty run folder")
            finally:
                audit.ROOT = old_root

    def test_foreground_check_rejects_package_prefix_collision(self):
        self.assertFalse(audit.foreground_package(
            "mResumedActivity: ActivityRecord{abc com.kellvyn.assistant.debug/.MainActivity}",
            "com.kellvyn.assistant"
        ))
        self.assertTrue(audit.foreground_package(
            "mResumedActivity: ActivityRecord{abc com.kellvyn.assistant/.MainActivity}",
            "com.kellvyn.assistant"
        ))

    def test_failure_manifest_records_checkpoint_without_raw_exception_text(self):
        with tempfile.TemporaryDirectory() as td:
            output = Path(td) / "run"
            output.mkdir()
            manifest = audit.write_failure_manifest(
                output,
                status="FAILED",
                serial="emulator-5554",
                package="com.kellvyn.assistant",
                error=RuntimeError("API_KEY=should-not-be-written")
            )
            self.assertEqual(output / "animation-audit.json", manifest)
            import json
            report = json.loads(manifest.read_text(encoding="utf-8"))
            self.assertEqual("FAILED", report["status"])
            self.assertEqual("NOT_EVALUATED", report["quality_assessment"])
            self.assertNotIn("should-not-be-written", manifest.read_text(encoding="utf-8"))

    def test_rejects_output_outside_repository(self):
        with tempfile.TemporaryDirectory() as td:
            old_root = audit.ROOT
            audit.ROOT = Path(td) / "repo"
            audit.ROOT.mkdir()
            try:
                with self.assertRaisesRegex(ValueError, "inside the repository"):
                    audit.safe_output_path(str(Path(td) / "outside"))
            finally:
                audit.ROOT = old_root

    def test_capture_status_is_not_an_animation_quality_verdict(self):
        self.assertEqual("CAPTURED", audit.classify_capture_status("PASSED", 42, True))
        self.assertEqual("PARTIAL", audit.classify_capture_status("BLOCKED_FFMPEG_NOT_INSTALLED", 42, True))
        self.assertEqual("PARTIAL", audit.classify_capture_status("PASSED", 0, True))
        self.assertEqual("PARTIAL", audit.classify_capture_status("FAILED", 42, True))
        self.assertEqual("PARTIAL", audit.classify_capture_status("PASSED", 42, False))

    def test_validates_frame_budget_and_recording_duration(self):
        with self.assertRaisesRegex(ValueError, "duration"):
            audit.capture_android_animation(
                serial="emulator-5554",
                package="com.kellvyn.assistant",
                output=Path(audit.ROOT) / "unused",
                duration_s=180
            )
        with self.assertRaisesRegex(ValueError, "frame budget"):
            audit.capture_android_animation(
                serial="emulator-5554",
                package="com.kellvyn.assistant",
                output=Path(audit.ROOT) / "unused",
                duration_s=10,
                frame_budget_ms=1
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
