from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / "tools" / "dataset_split_auditor.py"
spec = importlib.util.spec_from_file_location("dataset_split_auditor", MODULE)
auditor = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(auditor)


class DatasetSplitAuditorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.train = self.root / "train"
        self.validation = self.root / "validation"
        self.test = self.root / "test"
        for directory in (self.train, self.validation, self.test):
            directory.mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def write(self, root: Path, records: list[str]):
        (root / "samples.jsonl").write_text("\n".join(records) + "\n", encoding="utf-8")

    def test_clean_splits_have_no_overlap(self):
        self.write(self.train, ['{"id":1,"label":"cat"}'])
        self.write(self.validation, ['{"id":2,"label":"dog"}'])
        result = auditor.audit_splits({"train": self.train, "validation": self.validation})
        self.assertEqual("clean", result["status"])
        self.assertEqual(0, result["cross_split_overlap_groups"])

    def test_cross_split_duplicate_detected_even_with_different_key_order(self):
        self.write(self.train, ['{"id":1,"label":"cat"}'])
        self.write(self.validation, ['{"label":"cat","id":1}'])
        result = auditor.audit_splits({"train": self.train, "validation": self.validation})
        self.assertEqual("overlap_detected", result["status"])
        self.assertEqual(1, result["cross_split_overlap_groups"])
        self.assertEqual({"train", "validation"}, {x["split"] for x in result["overlaps"][0]["locations"]})

    def test_within_split_duplicates_reported_separately(self):
        self.write(self.train, ['{"id":1}', '{"id":1}'])
        self.write(self.validation, ['{"id":2}'])
        result = auditor.audit_splits({"train": self.train, "validation": self.validation})
        self.assertEqual("clean", result["status"])
        self.assertEqual(1, result["within_split_duplicate_records_total"])

    def test_invalid_jsonl_marks_data_invalid(self):
        self.write(self.train, ['{"id":1}', 'not-json'])
        self.write(self.validation, ['{"id":2}'])
        result = auditor.audit_splits({"train": self.train, "validation": self.validation})
        self.assertEqual("invalid_data", result["status"])
        self.assertEqual(1, result["invalid_records_total"])

    def test_blank_jsonl_line_is_reported_invalid(self):
        (self.train / "samples.jsonl").write_text('{"id":1}\n\n', encoding="utf-8")
        self.write(self.validation, ['{"id":2}'])
        result = auditor.audit_splits({"train": self.train, "validation": self.validation})
        self.assertEqual(1, result["invalid_records_total"])

    def test_invalid_record_locations_are_reported_without_values(self):
        self.write(self.train, ['{"id":1}', 'not-json', ''])
        self.write(self.validation, ['{"id":2}'])
        result = auditor.audit_splits({"train": self.train, "validation": self.validation}, sample_limit=10)
        locations = result["splits"]["train"]["invalid_record_locations"]
        self.assertEqual([{"path": "samples.jsonl", "line": 2}, {"path": "samples.jsonl", "line": 3}], locations)
        self.assertNotIn("not-json", json.dumps(result))

    def test_symlinks_are_rejected(self):
        self.write(self.train, ['{"id":1}'])
        self.write(self.validation, ['{"id":2}'])
        link = self.train / "linked.jsonl"
        try:
            link.symlink_to(self.train / "samples.jsonl")
        except (OSError, NotImplementedError):
            self.skipTest("symlinks unavailable")
        with self.assertRaises(auditor.SplitAuditError):
            auditor.audit_splits({"train": self.train, "validation": self.validation})

    def test_nested_split_roots_are_rejected(self):
        nested = self.train / "validation"
        nested.mkdir()
        with self.assertRaises(auditor.SplitAuditError):
            auditor.audit_splits({"train": self.train, "validation": nested})

    def test_report_does_not_include_record_values(self):
        self.write(self.train, ['{"secret":"private-value"}'])
        self.write(self.validation, ['{"secret":"private-value"}'])
        result = auditor.audit_splits({"train": self.train, "validation": self.validation})
        rendered = json.dumps(result)
        self.assertNotIn("private-value", rendered)
        self.assertIn("record_sha256", rendered)

    def test_requires_at_least_two_splits(self):
        with self.assertRaises(auditor.SplitAuditError):
            auditor.audit_splits({"train": self.train})

    def test_sample_limit_is_bounded(self):
        self.write(self.train, ['{"id":1}'])
        self.write(self.validation, ['{"id":1}'])
        with self.assertRaises(auditor.SplitAuditError):
            auditor.audit_splits({"train": self.train, "validation": self.validation}, sample_limit=501)

    def test_zero_sample_limit_emits_no_overlap_locations(self):
        self.write(self.train, ['{"id":1}'])
        self.write(self.validation, ['{"id":1}'])
        result = auditor.audit_splits({"train": self.train, "validation": self.validation}, sample_limit=0)
        self.assertEqual([], result["overlaps"][0]["locations"])

    def test_unknown_split_name_is_rejected(self):
        with self.assertRaises(auditor.SplitAuditError):
            auditor.audit_splits({"training": self.train, "validation": self.validation})


if __name__ == "__main__":
    unittest.main()
