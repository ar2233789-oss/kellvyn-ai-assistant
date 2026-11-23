from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / "tools" / "benchmark_evaluator.py"
spec = importlib.util.spec_from_file_location("benchmark_evaluator", MODULE)
evaluator = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(evaluator)


class BenchmarkEvaluatorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.suite_root = self.root / "suite"
        self.suite_root.mkdir()
        self.suite_file = self.suite_root / "suite.json"
        self.suite_file.write_text(json.dumps({
            "suite_id": "test-v1",
            "cases": [
                {"id": "one", "input": "A", "expected": "Hello"},
                {"id": "two", "input": "B", "expected": {"value": 2}},
            ],
        }), encoding="utf-8")
        self.predictions = self.root / "predictions.json"

    def tearDown(self):
        self.temp.cleanup()

    def write_predictions(self, items):
        self.predictions.write_text(json.dumps({"predictions": items}), encoding="utf-8")

    def load_suite(self):
        return evaluator.load_suite(self.suite_file, self.suite_root)

    def test_exact_match_scores_strings_and_structured_values(self):
        suite, fingerprint = self.load_suite()
        result = evaluator.evaluate(suite, fingerprint, {"one": "Hello", "two": {"value": 2}})
        self.assertEqual(1.0, result["metrics"]["exact_match_accuracy"])
        self.assertEqual("complete", result["status"])

    def test_missing_prediction_reduces_accuracy_and_coverage(self):
        suite, fingerprint = self.load_suite()
        result = evaluator.evaluate(suite, fingerprint, {"one": "Hello"})
        self.assertEqual(0.5, result["metrics"]["exact_match_accuracy"])
        self.assertEqual(0.5, result["metrics"]["coverage"])
        self.assertEqual(["two"], result["missing_ids"])
        self.assertEqual("partial", result["status"])

    def test_extra_prediction_is_reported(self):
        suite, fingerprint = self.load_suite()
        result = evaluator.evaluate(suite, fingerprint, {"one": "Hello", "two": {"value": 2}, "extra": "x"})
        self.assertEqual(["extra"], result["extra_ids"])
        self.assertEqual("partial", result["status"])

    def test_casefold_and_whitespace_options_are_explicit(self):
        suite, fingerprint = self.load_suite()
        result = evaluator.evaluate(suite, fingerprint, {"one": "  HELLO  ", "two": {"value": 2}}, casefold=True)
        self.assertEqual(1.0, result["metrics"]["exact_match_accuracy"])
        self.assertTrue(result["normalization"]["casefold"])

    def test_duplicate_prediction_ids_are_rejected(self):
        self.write_predictions([{"id": "one", "output": "Hello"}, {"id": "one", "output": "Again"}])
        with self.assertRaises(evaluator.EvaluationError):
            evaluator.load_predictions(self.predictions)

    def test_duplicate_suite_ids_are_rejected(self):
        data = json.loads(self.suite_file.read_text(encoding="utf-8"))
        data["cases"].append({"id": "one", "input": "duplicate", "expected": "x"})
        self.suite_file.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaises(evaluator.EvaluationError):
            self.load_suite()

    def test_output_inside_suite_root_is_rejected(self):
        self.write_predictions([{"id": "one", "output": "Hello"}])
        with self.assertRaises(evaluator.EvaluationError):
            evaluator.run_evaluation(self.suite_root, self.suite_file, self.predictions,
                                     self.suite_root / "report.json", False, False)

    def test_result_carries_same_suite_fingerprint_as_dataset_catalog(self):
        suite, fingerprint = self.load_suite()
        import sys
        sys.path.insert(0, str(MODULE.parent))
        import dataset_catalog
        self.assertEqual(dataset_catalog.build_manifest(self.suite_root)["dataset_sha256"], fingerprint)

    def test_classification_reports_accuracy_and_macro_micro_scores(self):
        suite = {"task": "classification", "cases": [
            {"id": "a", "input": "a", "expected": "positive"},
            {"id": "b", "input": "b", "expected": "positive"},
            {"id": "c", "input": "c", "expected": "negative"},
        ]}
        result = evaluator.evaluate(suite, "a" * 64,
                                    {"a": "positive", "b": "negative", "c": "negative"})
        self.assertAlmostEqual(2 / 3, result["metrics"]["accuracy"])
        self.assertAlmostEqual(2 / 3, result["metrics"]["macro_f1"])
        self.assertAlmostEqual(2 / 3, result["metrics"]["micro_f1"])
        self.assertEqual(2, len(result["per_class"]))

    def test_regression_reports_mae_rmse_and_r2(self):
        suite = {"task": "regression", "cases": [
            {"id": "a", "input": "a", "expected": 1},
            {"id": "b", "input": "b", "expected": 2},
            {"id": "c", "input": "c", "expected": 3},
        ]}
        result = evaluator.evaluate(suite, "b" * 64, {"a": 1, "b": 2, "c": 4})
        self.assertAlmostEqual(1 / 3, result["metrics"]["mae"])
        self.assertAlmostEqual((1 / 3) ** 0.5, result["metrics"]["rmse"])
        self.assertAlmostEqual(0.5, result["metrics"]["r2"])

    def test_word_error_rate_counts_insertions(self):
        suite = {"task": "word_error_rate", "cases": [
            {"id": "speech-1", "input": "audio", "expected": "hello world"},
        ]}
        result = evaluator.evaluate(suite, "c" * 64, {"speech-1": "hello there world"})
        self.assertAlmostEqual(0.5, result["metrics"]["word_error_rate"])
        self.assertEqual(1.0, result["metrics"]["word_insertions"])

    def test_word_error_rate_counts_substitutions_and_deletions(self):
        suite = {"task": "word_error_rate", "cases": [
            {"id": "speech-1", "input": "audio", "expected": "hello brave world"},
        ]}
        result = evaluator.evaluate(suite, "d" * 64, {"speech-1": "hello there"})
        self.assertEqual(1.0, result["metrics"]["word_substitutions"])
        self.assertEqual(1.0, result["metrics"]["word_deletions"])
        self.assertAlmostEqual(2 / 3, result["metrics"]["word_error_rate"])

    def test_regression_rejects_non_numeric_predictions(self):
        suite = {"task": "regression", "cases": [
            {"id": "a", "input": "a", "expected": 1.0},
        ]}
        with self.assertRaises(evaluator.EvaluationError):
            evaluator.evaluate(suite, "e" * 64, {"a": "one"})

    def test_word_error_rate_rejects_empty_reference_corpus(self):
        suite = {"task": "word_error_rate", "cases": [
            {"id": "empty", "input": "audio", "expected": ""},
        ]}
        with self.assertRaises(evaluator.EvaluationError):
            evaluator.evaluate(suite, "f" * 64, {"empty": "hello"})


    def test_word_error_rate_alignment_has_bounded_memory(self):
        words = ["word"] * 1001
        with self.assertRaises(evaluator.EvaluationError):
            evaluator.word_edit_counts(words, words)


if __name__ == "__main__":
    unittest.main()
