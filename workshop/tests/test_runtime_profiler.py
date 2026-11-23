from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / "tools" / "runtime_profiler.py"
spec = importlib.util.spec_from_file_location("runtime_profiler", MODULE)
profiler = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(profiler)


class RuntimeProfilerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.suite_root = self.root / "suite"
        self.suite_root.mkdir()
        self.suite_file = self.suite_root / "suite.json"
        self.suite_file.write_text(json.dumps({"cases": [
            {"id": "q1", "input": "first", "expected": "a"},
            {"id": "q2", "input": "second", "expected": "b"},
            {"id": "q3", "input": "third", "expected": "c"},
        ]}), encoding="utf-8")
        self.measurements = self.root / "measurements.jsonl"

    def tearDown(self):
        self.temp.cleanup()

    def successful_rows(self):
        return [
            {"case_id": "q1", "status": "success", "latency_ms": 1000, "time_to_first_token_ms": 200,
             "input_tokens": 100, "output_tokens": 40, "peak_memory_mb": 1200},
            {"case_id": "q2", "status": "success", "latency_ms": 1200, "time_to_first_token_ms": 300,
             "input_tokens": 150, "output_tokens": 60, "peak_memory_mb": 1400},
            {"case_id": "q3", "status": "success", "latency_ms": 1400, "time_to_first_token_ms": 400,
             "input_tokens": 200, "output_tokens": 80, "peak_memory_mb": 1300},
        ]

    def write_measurements(self, rows):
        self.measurements.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")

    def test_complete_profile_reports_latency_ttft_throughput_and_memory(self):
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        result = profiler.profile(ids, fingerprint, self.successful_rows())
        self.assertEqual("complete", result["status"])
        self.assertEqual(1.0, result["metrics"]["coverage"])
        self.assertAlmostEqual(1200, result["metrics"]["latency_ms"])
        self.assertAlmostEqual(1200, result["metrics"]["latency_p50_ms"])
        self.assertAlmostEqual(1380, result["metrics"]["latency_p95_ms"])
        self.assertAlmostEqual(300, result["metrics"]["time_to_first_token_ms"])
        self.assertEqual(1400, result["metrics"]["peak_memory_mb"])
        self.assertGreater(result["metrics"]["decode_tokens_per_second"], 0)
        self.assertGreater(result["metrics"]["end_to_end_output_tokens_per_second"], 0)

    def test_missing_case_marks_profile_partial(self):
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        result = profiler.profile(ids, fingerprint, self.successful_rows()[:2])
        self.assertEqual("partial", result["status"])
        self.assertAlmostEqual(2 / 3, result["metrics"]["coverage"])
        self.assertEqual(["q3"], result["missing_case_ids"])

    def test_failed_case_marks_profile_partial(self):
        rows = self.successful_rows()
        rows[1] = {"case_id": "q2", "status": "error", "error": "secret prompt content"}
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        result = profiler.profile(ids, fingerprint, rows)
        self.assertEqual("partial", result["status"])
        self.assertEqual(["q2"], result["failed_case_ids"])
        self.assertNotIn("secret prompt content", json.dumps(result))

    def test_extra_case_id_marks_profile_partial(self):
        rows = self.successful_rows() + [{"case_id": "unexpected", "status": "error"}]
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        result = profiler.profile(ids, fingerprint, rows)
        self.assertEqual("partial", result["status"])
        self.assertEqual(["unexpected"], result["extra_case_ids"])

    def test_duplicate_measurement_ids_are_rejected(self):
        rows = self.successful_rows() + [self.successful_rows()[0]]
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        with self.assertRaises(profiler.RuntimeProfileError):
            profiler.profile(ids, fingerprint, rows)

    def test_duplicate_suite_ids_are_rejected(self):
        self.suite_file.write_text(json.dumps({"cases": [
            {"id": "same", "input": "a", "expected": 1},
            {"id": "same", "input": "b", "expected": 2},
        ]}), encoding="utf-8")
        with self.assertRaises(profiler.RuntimeProfileError):
            profiler.load_suite(self.suite_root, self.suite_file)

    def test_invalid_latency_is_rejected(self):
        rows = self.successful_rows()
        rows[0]["latency_ms"] = float("nan")
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        with self.assertRaises(profiler.RuntimeProfileError):
            profiler.profile(ids, fingerprint, rows)

    def test_ttft_cannot_exceed_total_latency(self):
        rows = self.successful_rows()
        rows[0]["time_to_first_token_ms"] = 2000
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        with self.assertRaises(profiler.RuntimeProfileError):
            profiler.profile(ids, fingerprint, rows)

    def test_output_tokens_require_ttft(self):
        rows = self.successful_rows()
        del rows[0]["time_to_first_token_ms"]
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        with self.assertRaises(profiler.RuntimeProfileError):
            profiler.profile(ids, fingerprint, rows)

    def test_zero_output_tokens_do_not_require_ttft(self):
        rows = self.successful_rows()
        for row in rows:
            row["output_tokens"] = 0
            row.pop("time_to_first_token_ms")
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        result = profiler.profile(ids, fingerprint, rows)
        self.assertEqual("complete", result["status"])
        self.assertNotIn("time_to_first_token_ms", result["metrics"])
        self.assertNotIn("decode_tokens_per_second", result["metrics"])

    def test_optional_memory_reports_coverage(self):
        rows = self.successful_rows()
        rows[0].pop("peak_memory_mb")
        ids, fingerprint = profiler.load_suite(self.suite_root, self.suite_file)
        result = profiler.profile(ids, fingerprint, rows)
        self.assertAlmostEqual(2 / 3, result["metrics"]["peak_memory_coverage"])

    def test_profile_command_writes_complete_result(self):
        self.write_measurements(self.successful_rows())
        result = profiler.run_profile(self.suite_root, self.suite_file, self.measurements,
                                      self.root / "result.json")
        self.assertEqual("complete", result["status"])
        self.assertTrue((self.root / "result.json").is_file())

    def test_measurement_file_inside_reference_suite_is_rejected(self):
        inside = self.suite_root / "measurements.jsonl"
        inside.write_text(json.dumps(self.successful_rows()[0]) + "\n", encoding="utf-8")
        with self.assertRaises(profiler.RuntimeProfileError):
            profiler.run_profile(self.suite_root, self.suite_file, inside, self.root / "result.json")

    def test_percentile_interpolates_deterministically(self):
        self.assertEqual(2.0, profiler.percentile([1, 2, 3], 50))
        self.assertAlmostEqual(2.9, profiler.percentile([1, 2, 3], 95))


if __name__ == "__main__":
    unittest.main()
