from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / "tools" / "labctl.py"
spec = importlib.util.spec_from_file_location("labctl", MODULE)
labctl = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(labctl)

class LabCtlTests(unittest.TestCase):
    def test_guard_allows_only_workshop_and_lab_workflow(self):
        for path in ["workshop/README.md", "workshop/tools/x.py", ".github/workflows/super-lab.yml", "./workshop" + chr(92) + "tools" + chr(92) + "x.py"]:
            self.assertTrue(labctl.is_allowed_changed_path(path), path)
        for path in ["android/app/src/main/Activity.java", "src/App.tsx", "scripts/build.py", ".github/interface-lock.sha", "README.md", ".github/workflows/build-apk.yml"]:
            self.assertFalse(labctl.is_allowed_changed_path(path), path)

    def test_report_keeps_real_artifacts_and_escapes_html(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            source = root / "source"
            source.mkdir()
            unsafe_name = "<script>alert(1).json"
            (source / unsafe_name).write_text('{"msg":"diagnostic"}', encoding="utf-8")
            (source / "note.txt").write_text("diagnostic", encoding="utf-8")
            output = root / "report"
            self.assertEqual(0, labctl.report(source, output, ["web=success", "android=failure"]))
            manifest = json.loads((output / "report.json").read_text(encoding="utf-8"))
            self.assertEqual(2, manifest["artifactCount"])
            html = (output / "index.html").read_text(encoding="utf-8")
            self.assertNotIn(unsafe_name, html)
            self.assertIn("&lt;script&gt;alert(1).json", html)
            self.assertIn("android", html)
            self.assertTrue((output / "artifacts" / unsafe_name).is_file())

    def test_report_does_not_invent_artifacts_when_input_is_missing(self):
        with tempfile.TemporaryDirectory() as td:
            output = Path(td) / "report"
            labctl.report(Path(td) / "missing", output, ["studio=skipped"])
            manifest = json.loads((output / "report.json").read_text(encoding="utf-8"))
            self.assertEqual(0, manifest["artifactCount"])
            self.assertEqual([], manifest["images"])

if __name__ == "__main__":
    unittest.main()
