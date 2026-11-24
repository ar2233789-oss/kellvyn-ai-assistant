from __future__ import annotations
import importlib.util
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / "tools" / "model_memory_estimator.py"
spec = importlib.util.spec_from_file_location("model_memory_estimator", MODULE)
estimator = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(estimator)


class ModelMemoryEstimatorTests(unittest.TestCase):
    def setUp(self):
        self.inspection = {
            "schema": 1, "status": "inspected", "format": "gguf",
            "file": "model.gguf", "file_sha256": "a" * 64,
            "tensor_data_bytes": 100 * 1024 * 1024, "parameter_count": 1_000_000_000,
            "model_dimensions": {
                "block_count": 32, "embedding_length": 4096,
                "attention.head_count": 32, "attention.head_count_kv": 8, "context_length": 8192,
            },
        }

    def test_standard_kv_cache_formula_and_memory_total(self):
        result = estimator.estimate_memory(
            self.inspection, context_tokens=4096, usable_memory_mb=4096,
            runtime_overhead_mb=500, safety_margin_percent=25,
        )
        self.assertEqual("within_budget_estimate", result["status"])
        self.assertAlmostEqual(512, result["estimates_mb"]["kv_cache"])
        self.assertAlmostEqual(100, result["estimates_mb"]["weights"])
        self.assertAlmostEqual(1390, result["estimates_mb"]["total"])

    def test_budget_exceeded_is_reported(self):
        result = estimator.estimate_memory(
            self.inspection, context_tokens=8192, usable_memory_mb=512,
            runtime_overhead_mb=512, safety_margin_percent=25,
        )
        self.assertEqual("exceeds_budget_estimate", result["status"])
        self.assertLess(result["estimates_mb"]["headroom"], 0)

    def test_manual_kv_cache_estimate_can_replace_architecture_formula(self):
        result = estimator.estimate_memory(
            self.inspection, context_tokens=2048, usable_memory_mb=2048,
            runtime_overhead_mb=256, explicit_kv_cache_mb=128,
        )
        self.assertEqual("explicit_user_estimate", result["kv_cache_method"])
        self.assertEqual(128, result["estimates_mb"]["kv_cache"])

    def test_missing_architecture_dimensions_requires_manual_inputs(self):
        incomplete = dict(self.inspection)
        incomplete["model_dimensions"] = {}
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(incomplete, 2048, 2048, 256)

    def test_explicit_layer_and_head_dimensions_are_accepted(self):
        result = estimator.estimate_memory(
            self.inspection, 1024, 4096, 256,
            layers=16, kv_heads=4, head_dim=64,
        )
        self.assertGreater(result["estimates_mb"]["kv_cache"], 0)

    def test_invalid_model_tensor_size_is_rejected(self):
        invalid = dict(self.inspection)
        invalid["tensor_data_bytes"] = 0
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(invalid, 1024, 4096, 256)

    def test_invalid_context_is_rejected(self):
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(self.inspection, 0, 4096, 256)

    def test_boolean_context_is_rejected(self):
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(self.inspection, True, 4096, 256)

    def test_zero_usable_memory_is_rejected(self):
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(self.inspection, 1024, 0, 256)

    def test_negative_runtime_overhead_is_rejected(self):
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(self.inspection, 1024, 4096, -1)

    def test_excessive_safety_margin_is_rejected(self):
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(self.inspection, 1024, 4096, 256, 201)

    def test_nonpositive_kv_dimensions_are_rejected(self):
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(self.inspection, 1024, 4096, 256, layers=0, kv_heads=8, head_dim=128)

    def test_invalid_explicit_kv_cache_is_rejected(self):
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(self.inspection, 1024, 4096, 256, explicit_kv_cache_mb=-1)

    def test_context_above_model_declared_limit_is_rejected(self):
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(self.inspection, 8193, 4096, 256)

    def test_kv_element_size_is_bounded(self):
        with self.assertRaises(estimator.MemoryEstimateError):
            estimator.estimate_memory(self.inspection, 1024, 4096, 256, kv_bytes_per_element=3)

    def test_read_inspection_rejects_unrecognized_schema(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "inspection.json"
            path.write_text('{"schema":99}', encoding="utf-8")
            with self.assertRaises(estimator.MemoryEstimateError):
                estimator.read_inspection(path)

    def test_estimate_reports_limitations(self):
        result = estimator.estimate_memory(self.inspection, 1024, 4096, 256)
        self.assertTrue(any("estimate only" in x.lower() for x in result["limitations"]))


if __name__ == "__main__":
    unittest.main()
