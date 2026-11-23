from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / "tools" / "experiment_engine.py"
spec = importlib.util.spec_from_file_location("experiment_engine", MODULE)
engine = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(engine)

HASH_A = "a" * 64
HASH_B = "b" * 64


def make_spec(run_id: str, metrics: dict[str, float], artifact: str = "output.json") -> dict:
    return {
        "run_id": run_id,
        "experiment": "runtime-comparison",
        "kind": "language-model",
        "subject": {"name": "sample-model", "version": run_id, "revision": run_id},
        "comparison_context": {
            "dataset_sha256": HASH_A,
            "evaluation_suite_sha256": HASH_B,
            "device": "Poco F6 virtual profile",
            "runtime": "test-runtime",
            "precision": "int4",
        },
        "parameters": {"temperature": 0.2},
        "metrics": metrics,
        "artifacts": [artifact],
    }


POLICY = {"schema": 1, "metrics": {
    "quality": {"direction": "higher", "min_improvement": 0.01, "max_regression": 0.005},
    "latency_ms": {"direction": "lower", "min_improvement": 2, "max_regression": 1},
}}


class ExperimentEngineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.artifacts = self.root / "artifacts"
        self.artifacts.mkdir()
        (self.artifacts / "output.json").write_text('{"ok":true}\n', encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def write_spec(self, name: str, data: dict) -> Path:
        path = self.root / name
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def record(self, run_id: str, metrics: dict[str, float]) -> tuple[dict, Path]:
        spec_path = self.write_spec(f"{run_id}-spec.json", make_spec(run_id, metrics))
        output = self.root / f"{run_id}.run.json"
        run = engine.record_run(spec_path, self.artifacts, output)
        return run, output

    def test_record_and_verify_hashes_artifacts(self):
        run, path = self.record("baseline-1", {"quality": 0.8, "latency_ms": 100})
        self.assertEqual("sealed", run["status"])
        self.assertEqual(1, len(run["artifacts"]))
        verified = engine.verify_run(path, self.artifacts)
        self.assertEqual("verified", verified["status"])

    def test_verify_detects_changed_artifact(self):
        _, path = self.record("baseline-2", {"quality": 0.8, "latency_ms": 100})
        (self.artifacts / "output.json").write_text('{"ok":false}\n', encoding="utf-8")
        result = engine.verify_run(path, self.artifacts)
        self.assertEqual("integrity_error", result["status"])
        self.assertFalse(result["checks"][0]["ok"])

    def test_path_traversal_is_rejected(self):
        spec_path = self.write_spec("unsafe.json", make_spec("unsafe-1", {"quality": 1.0}, "../outside.txt"))
        with self.assertRaises(engine.ExperimentError):
            engine.record_run(spec_path, self.artifacts, self.root / "unsafe.run.json")

    def test_absolute_artifact_path_is_rejected(self):
        spec_path = self.write_spec("absolute.json", make_spec("unsafe-2", {"quality": 1.0}, str(self.artifacts / "output.json")))
        with self.assertRaises(engine.ExperimentError):
            engine.record_run(spec_path, self.artifacts, self.root / "absolute.run.json")

    def test_symbolic_link_artifact_is_rejected(self):
        link = self.artifacts / "linked.json"
        try:
            link.symlink_to(self.artifacts / "output.json")
        except (OSError, NotImplementedError):
            self.skipTest("symlinks are unavailable in this environment")
        spec_path = self.write_spec("symlink.json", make_spec("unsafe-3", {"quality": 1.0}, "linked.json"))
        with self.assertRaises(engine.ExperimentError):
            engine.record_run(spec_path, self.artifacts, self.root / "symlink.run.json")

    def test_non_finite_metric_is_rejected(self):
        data = make_spec("bad-metric", {"quality": float("nan")})
        with self.assertRaises(engine.ExperimentError):
            engine.validate_spec(data)

    def test_comparison_reports_improvement_without_regression(self):
        baseline, _ = self.record("baseline-3", {"quality": 0.80, "latency_ms": 100})
        candidate, _ = self.record("candidate-3", {"quality": 0.83, "latency_ms": 95})
        result = engine.compare_runs(baseline, candidate, POLICY)
        self.assertEqual("improved", result["status"])

    def test_comparison_blocks_regression_even_when_another_metric_improves(self):
        baseline, _ = self.record("baseline-4", {"quality": 0.80, "latency_ms": 100})
        candidate, _ = self.record("candidate-4", {"quality": 0.90, "latency_ms": 110})
        result = engine.compare_runs(baseline, candidate, POLICY)
        self.assertEqual("regression", result["status"])

    def test_mismatched_evaluation_context_is_inconclusive(self):
        baseline, _ = self.record("baseline-5", {"quality": 0.80, "latency_ms": 100})
        candidate, _ = self.record("candidate-5", {"quality": 0.90, "latency_ms": 90})
        candidate["comparison_context"]["device"] = "different-device"
        candidate["comparison_context_sha256"] = engine.sha256_value(candidate["comparison_context"])
        result = engine.compare_runs(baseline, candidate, POLICY)
        self.assertEqual("inconclusive", result["status"])

    def test_missing_metric_is_inconclusive(self):
        baseline, _ = self.record("baseline-6", {"quality": 0.80, "latency_ms": 100})
        candidate, _ = self.record("candidate-6", {"quality": 0.90})
        result = engine.compare_runs(baseline, candidate, POLICY)
        self.assertEqual("inconclusive", result["status"])

    def test_invalid_policy_is_rejected(self):
        with self.assertRaises(engine.ExperimentError):
            engine.validate_policy({"schema": 1, "metrics": {"quality": {"direction": "guess", "min_improvement": 0, "max_regression": 0}}})

    def test_record_refuses_to_overwrite_existing_run(self):
        spec_path = self.write_spec("immutable.json", make_spec("immutable-1", {"quality": 0.9}))
        output = self.root / "immutable.run.json"
        engine.record_run(spec_path, self.artifacts, output)
        with self.assertRaises(engine.ExperimentError):
            engine.record_run(spec_path, self.artifacts, output)

    def test_verify_detects_tampered_comparison_context(self):
        run, path = self.record("context-integrity", {"quality": 0.9})
        run["comparison_context"]["device"] = "changed-device"
        path.write_text(json.dumps(run), encoding="utf-8")
        with self.assertRaises(engine.ExperimentError):
            engine.verify_run(path, self.artifacts)

    def test_comparison_rejects_different_experiment_groups(self):
        baseline, _ = self.record("baseline-group", {"quality": 0.8, "latency_ms": 100})
        candidate, _ = self.record("candidate-group", {"quality": 0.9, "latency_ms": 90})
        candidate["experiment"] = "different-experiment"
        with self.assertRaises(engine.ExperimentError):
            engine.compare_runs(baseline, candidate, POLICY)


    def test_record_imports_metrics_from_matching_complete_evaluation(self):
        spec_path = self.write_spec("evaluated.json", make_spec("evaluated-run", {"latency_ms": 100}))
        evaluation_path = self.write_spec("evaluation-result.json", {
            "schema": 1, "status": "complete", "evaluation_suite_sha256": HASH_B,
            "metrics": {"exact_match_accuracy": 0.9, "coverage": 1.0, "matched_count": 9,
                        "case_count": 9, "missing_prediction_count": 0, "extra_prediction_count": 0}
        })
        output = self.root / "evaluated.run.json"
        run = engine.record_run(spec_path, self.artifacts, output, evaluation_path)
        self.assertEqual(0.9, run["metrics"]["exact_match_accuracy"])
        self.assertEqual("complete", run["evaluation"]["status"])
        verified = engine.verify_run(output, self.artifacts, evaluation_path)
        self.assertEqual("verified", verified["status"])
        self.assertEqual("verified", verified["evaluation_result_status"])

    def test_record_imports_task_metrics_without_exact_match_metric(self):
        spec_path = self.write_spec("classification.json", make_spec("classification-run", {"latency_ms": 100}))
        evaluation_path = self.write_spec("classification-eval.json", {
            "schema": 1, "status": "complete", "task": "classification", "evaluation_suite_sha256": HASH_B,
            "metrics": {"accuracy": 0.9, "macro_f1": 0.88, "coverage": 1.0, "matched_count": 9,
                        "case_count": 9, "missing_prediction_count": 0, "extra_prediction_count": 0}
        })
        run = engine.record_run(spec_path, self.artifacts, self.root / "classification.run.json", evaluation_path)
        self.assertEqual(0.9, run["metrics"]["accuracy"])
        self.assertEqual(0.88, run["metrics"]["macro_f1"])

    def test_record_rejects_evaluation_from_different_suite(self):
        spec_path = self.write_spec("wrong-suite.json", make_spec("wrong-suite-run", {"latency_ms": 100}))
        evaluation_path = self.write_spec("wrong-suite-eval.json", {
            "schema": 1, "status": "complete", "evaluation_suite_sha256": "c" * 64,
            "metrics": {"exact_match_accuracy": 0.9, "coverage": 1.0, "matched_count": 9,
                        "case_count": 9, "missing_prediction_count": 0, "extra_prediction_count": 0}
        })
        with self.assertRaises(engine.ExperimentError):
            engine.record_run(spec_path, self.artifacts, self.root / "wrong-suite.run.json", evaluation_path)

    def test_record_rejects_partial_evaluation(self):
        spec_path = self.write_spec("partial.json", make_spec("partial-run", {"latency_ms": 100}))
        evaluation_path = self.write_spec("partial-eval.json", {
            "schema": 1, "status": "partial", "evaluation_suite_sha256": HASH_B,
            "metrics": {"exact_match_accuracy": 0.9, "coverage": 0.5, "matched_count": 1,
                        "case_count": 2, "missing_prediction_count": 1, "extra_prediction_count": 0}
        })
        with self.assertRaises(engine.ExperimentError):
            engine.record_run(spec_path, self.artifacts, self.root / "partial.run.json", evaluation_path)


    def test_evaluation_is_not_claimed_verified_without_rechecking_result_file(self):
        spec_path = self.write_spec("no-recheck.json", make_spec("no-recheck-run", {"latency_ms": 100}))
        evaluation_path = self.write_spec("no-recheck-eval.json", {
            "schema": 1, "status": "complete", "evaluation_suite_sha256": HASH_B,
            "metrics": {"exact_match_accuracy": 0.9, "coverage": 1.0, "matched_count": 2,
                        "case_count": 2, "missing_prediction_count": 0, "extra_prediction_count": 0}
        })
        output = self.root / "no-recheck.run.json"
        engine.record_run(spec_path, self.artifacts, output, evaluation_path)
        result = engine.verify_run(output, self.artifacts)
        self.assertEqual("evaluation_not_rechecked", result["status"])

    def test_record_rejects_complete_result_with_inconsistent_counts(self):
        spec_path = self.write_spec("inconsistent.json", make_spec("inconsistent-run", {"latency_ms": 100}))
        evaluation_path = self.write_spec("inconsistent-eval.json", {
            "schema": 1, "status": "complete", "evaluation_suite_sha256": HASH_B,
            "metrics": {"exact_match_accuracy": 0.9, "coverage": 1.0, "matched_count": 1,
                        "case_count": 2, "missing_prediction_count": 0, "extra_prediction_count": 0}
        })
        with self.assertRaises(engine.ExperimentError):
            engine.record_run(spec_path, self.artifacts, self.root / "inconsistent.run.json", evaluation_path)


if __name__ == "__main__":
    unittest.main()
