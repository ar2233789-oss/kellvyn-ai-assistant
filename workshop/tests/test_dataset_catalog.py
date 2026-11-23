from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / "tools" / "dataset_catalog.py"
spec = importlib.util.spec_from_file_location("dataset_catalog", MODULE)
catalog = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(catalog)


class DatasetCatalogTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.data = self.root / "data"
        self.data.mkdir()
        (self.data / "a.txt").write_text("alpha\n", encoding="utf-8")
        (self.data / "b.txt").write_text("beta\n", encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def test_fingerprint_is_stable_and_independent_of_creation_order(self):
        first = catalog.build_manifest(self.data)
        other = self.root / "other"
        other.mkdir()
        (other / "b.txt").write_text("beta\n", encoding="utf-8")
        (other / "a.txt").write_text("alpha\n", encoding="utf-8")
        second = catalog.build_manifest(other)
        self.assertEqual(first["dataset_sha256"], second["dataset_sha256"])

    def test_verify_detects_changed_data(self):
        manifest_path = self.root / "data-manifest.json"
        catalog.inspect_dataset(self.data, manifest_path, 100, 1024 * 1024, True)
        (self.data / "a.txt").write_text("changed\n", encoding="utf-8")
        result = catalog.verify_dataset(self.data, manifest_path)
        self.assertEqual("changed", result["status"])
        self.assertEqual(["a.txt"], result["changed"])

    def test_duplicate_contents_are_reported(self):
        (self.data / "copy.txt").write_text("alpha\n", encoding="utf-8")
        manifest = catalog.build_manifest(self.data)
        self.assertIn(["a.txt", "copy.txt"], manifest["duplicate_content_groups"])

    def test_symlinks_are_rejected(self):
        link = self.data / "link.txt"
        try:
            link.symlink_to(self.data / "a.txt")
        except (OSError, NotImplementedError):
            self.skipTest("symlinks are unavailable")
        with self.assertRaises(catalog.DatasetError):
            catalog.build_manifest(self.data)

    def test_jsonl_records_and_invalid_rows_are_counted(self):
        (self.data / "items.jsonl").write_text('{"x":1}\nnot-json\n{"x":2}\n', encoding="utf-8")
        manifest = catalog.build_manifest(self.data)
        item = next(row for row in manifest["files"] if row["path"] == "items.jsonl")
        self.assertEqual(2, item["jsonl"]["valid_records"])
        self.assertEqual(1, item["jsonl"]["invalid_lines"])
        self.assertIn("invalid_jsonl_rows", manifest["warnings"])

    def test_output_inside_dataset_is_rejected(self):
        with self.assertRaises(catalog.DatasetError):
            catalog.inspect_dataset(self.data, self.data / "manifest.json", 100, 1024 * 1024, True)

    def test_file_limit_is_enforced(self):
        with self.assertRaises(catalog.DatasetError):
            catalog.build_manifest(self.data, max_files=1)

    def test_empty_dataset_is_reported_as_warning(self):
        empty = self.root / "empty"
        empty.mkdir()
        manifest = catalog.build_manifest(empty)
        self.assertEqual(0, manifest["file_count"])
        self.assertIn("empty_dataset", manifest["warnings"])

    def test_verify_rejects_manifest_with_tampered_file_inventory(self):
        manifest_path = self.root / "data-manifest.json"
        catalog.inspect_dataset(self.data, manifest_path, 100, 1024 * 1024, True)
        saved = json.loads(manifest_path.read_text(encoding="utf-8"))
        saved["files"].pop()
        manifest_path.write_text(json.dumps(saved), encoding="utf-8")
        with self.assertRaises(catalog.DatasetError):
            catalog.verify_dataset(self.data, manifest_path)


if __name__ == "__main__":
    unittest.main()
