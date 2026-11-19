from __future__ import annotations
import importlib.util
import tempfile
import unittest
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
