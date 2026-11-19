from __future__ import annotations
import importlib.util
import json
import tempfile
import unittest
from unittest import mock
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MODULE = Path(__file__).resolve().parents[1] / "workbench.py"
spec = importlib.util.spec_from_file_location("kellvyn_workbench", MODULE)
wb = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(wb)

class ToolchainTests(unittest.TestCase):
    def test_node_playwright_is_detected_separately_from_python_module(self):
        import subprocess
        completed = subprocess.CompletedProcess(
            args=["node", "-e", "require.resolve('playwright')"], returncode=1,
            stdout="", stderr="Cannot find module 'playwright'"
        )
        with mock.patch.object(wb.shutil, "which", return_value="/usr/bin/node"), \
             mock.patch.object(wb.subprocess, "run", return_value=completed):
            self.assertEqual("NOT_INSTALLED", wb.node_package_status("playwright"))
        with mock.patch.object(wb.shutil, "which", return_value=None):
            self.assertEqual("BLOCKED_NODE_MISSING", wb.node_package_status("playwright"))


class PolyglotToolchainTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.old_root = wb.ROOT
        wb.ROOT = self.root

    def tearDown(self):
        wb.ROOT = self.old_root
        self.temp.cleanup()

    def test_package_manager_is_selected_from_repository_lockfile(self):
        (self.root / "package.json").write_text('{"scripts":{"test":"vitest run"}}')
        (self.root / "bun.lock").write_text("{}")
        self.assertEqual("bun", wb.detect_package_manager())
        (self.root / "bun.lock").unlink()
        (self.root / "pnpm-lock.yaml").write_text("lockfileVersion: '9.0'\\n")
        self.assertEqual("pnpm", wb.detect_package_manager())

    def test_web_unit_suite_uses_bun_and_local_dependencies(self):
        (self.root / "package.json").write_text(json.dumps({"scripts": {"test": "vitest run"}}))
        (self.root / "bun.lock").write_text("{}")
        modules = self.root / "node_modules"
        (modules / ".bin").mkdir(parents=True)
        (modules / ".bin" / "vitest").write_text("#!/bin/sh\\n")
        with mock.patch.object(wb.shutil, "which", side_effect=lambda name: "/usr/bin/bun" if name == "bun" else None):
            command, blocked = wb.web_suite_command("web-unit")
        self.assertIsNone(blocked)
        self.assertEqual(["/usr/bin/bun", "run", "test"], command)

    def test_web_suite_blocks_instead_of_mislabeling_missing_dependencies_as_test_failure(self):
        (self.root / "package.json").write_text(json.dumps({"scripts": {"test": "vitest run"}}))
        (self.root / "bun.lock").write_text("{}")
        with mock.patch.object(wb.shutil, "which", return_value="/usr/bin/bun"):
            command, blocked = wb.web_suite_command("web-unit")
        self.assertIsNone(command)
        self.assertIn("node_modules", blocked)

    def test_gradle_banner_detects_version_after_welcome_prefix(self):
        self.assertTrue(wb.is_gradle_813_banner("Welcome to Gradle 8.13!\n"))
        self.assertTrue(wb.is_gradle_813_banner("Gradle 8.13"))
        self.assertFalse(wb.is_gradle_813_banner("Welcome to Gradle 8.12!"))

    def test_redaction_covers_quoted_secrets_with_spaces(self):
        value = 'PASSWORD="quoted secret value"'
        redacted = wb.redact_sensitive_text(value)
        self.assertNotIn("quoted secret value", redacted)
        self.assertIn("[REDACTED]", redacted)

    def test_java_major_parser_handles_modern_and_legacy_versions(self):
        self.assertEqual(25, wb.parse_java_major_version('openjdk version "25.0.1" 2026-09-15'))
        self.assertEqual(17, wb.parse_java_major_version('openjdk version "17.0.12"'))
        self.assertEqual(8, wb.parse_java_major_version('java version "1.8.0_402"'))
        self.assertEqual(21, wb.parse_java_major_version('javac 21.0.6'))

    def test_kotlin_lsp_requires_separate_jdk25_and_stays_optional(self):
        fake_home = self.root / "jdk25"
        java = fake_home / "bin" / "java"
        java.parent.mkdir(parents=True)
        java.write_text('#!/bin/sh\necho \'openjdk version "25.0.1"\' >&2\n')
        java.chmod(0o755)
        (self.root / "package.json").write_text(json.dumps({"scripts": {}}))
        old_home = wb.os.environ.get("KOTLIN_LSP_JAVA_HOME")
        try:
            with mock.patch.dict(wb.os.environ, {"KOTLIN_LSP_JAVA_HOME": str(fake_home)}):
                with mock.patch.object(
                    wb.shutil, "which",
                    side_effect=lambda name: "/opt/kotlin-lsp/bin/kotlin-lsp" if name == "kotlin-lsp" else None
                ):
                    matrix = wb.toolchain_matrix({}, {})
            self.assertEqual("READY_UNVALIDATED", matrix["kotlin_semantic_analysis"]["status"])
            self.assertEqual(25, matrix["kotlin_semantic_analysis"]["runtime_jdk_major"])
            self.assertEqual(17, matrix["kotlin_semantic_analysis"]["project_build_jdk_major"])
        finally:
            if old_home is None:
                wb.os.environ.pop("KOTLIN_LSP_JAVA_HOME", None)
            else:
                wb.os.environ["KOTLIN_LSP_JAVA_HOME"] = old_home

    def test_kotlin_lsp_is_blocked_when_its_separate_jdk_is_missing(self):
        (self.root / "package.json").write_text(json.dumps({"scripts": {}}))
        with mock.patch.dict(wb.os.environ, {"KOTLIN_LSP_JAVA_HOME": ""}):
            with mock.patch.object(
                wb.shutil, "which",
                side_effect=lambda name: "/opt/kotlin-lsp/bin/kotlin-lsp" if name == "kotlin-lsp" else None
            ):
                matrix = wb.toolchain_matrix({}, {})
        self.assertEqual(
            "BLOCKED_SEPARATE_JDK25_NOT_CONFIGURED",
            matrix["kotlin_semantic_analysis"]["status"]
        )
        self.assertEqual(25, matrix["kotlin_semantic_analysis"]["required_runtime_jdk_major"])

    def test_kotlin_lsp_is_blocked_when_the_separate_jdk_is_not_25(self):
        fake_home = self.root / "jdk17"
        java = fake_home / "bin" / "java"
        java.parent.mkdir(parents=True)
        java.write_text('#!/bin/sh\\necho \'openjdk version "17.0.12"\' >&2\\n')
        java.chmod(0o755)
        (self.root / "package.json").write_text(json.dumps({"scripts": {}}))
        with mock.patch.dict(wb.os.environ, {"KOTLIN_LSP_JAVA_HOME": str(fake_home)}):
            with mock.patch.object(
                wb.shutil, "which",
                side_effect=lambda name: "/opt/kotlin-lsp/bin/kotlin-lsp" if name == "kotlin-lsp" else None
            ):
                matrix = wb.toolchain_matrix({}, {})
        self.assertEqual("BLOCKED_JDK25_REQUIRED", matrix["kotlin_semantic_analysis"]["status"])
        self.assertEqual(17, matrix["kotlin_semantic_analysis"]["runtime_jdk_major"])

    def test_toolchain_matrix_never_calls_detected_container_tool_a_secure_sandbox(self):
        (self.root / "package.json").write_text(json.dumps({"scripts": {}}))
        with mock.patch.object(wb.shutil, "which", side_effect=lambda name: "/usr/bin/podman" if name == "podman" else None):
            matrix = wb.toolchain_matrix({}, {})
        self.assertEqual("DETECTED_UNVALIDATED", matrix["sandbox"]["status"])
        with mock.patch.object(wb.shutil, "which", return_value=None):
            unavailable = wb.toolchain_matrix({}, {})
        self.assertEqual("NOT_AVAILABLE", unavailable["sandbox"]["status"])
        self.assertEqual("BLOCKED", matrix["android_kotlin_java"]["status"])


class LedgerTests(unittest.TestCase):
    def test_empty_ledger_is_valid(self):
        with tempfile.TemporaryDirectory() as td:
            report = wb.verify_ledger(Path(td) / "ledger.jsonl")
            self.assertTrue(report["valid"])
            self.assertEqual(0, report["records"])

    def test_hash_chain_accepts_appended_records(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "ledger.jsonl"
            original = wb.LEDGER
            wb.LEDGER = path
            try:
                wb.append_event("RESEARCH", "RECORDED", "first event")
                wb.append_event("TEST", "PASSED", "second event")
            finally:
                wb.LEDGER = original
            report = wb.verify_ledger(path)
            self.assertTrue(report["valid"], report)
            self.assertEqual(2, report["records"])


    def test_run_capture_records_exit_status_and_raw_logs(self):
        import sys
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            old = (wb.ROOT, wb.RUNS, wb.LEDGER)
            wb.ROOT = root
            wb.RUNS = root / "artifacts" / "runs"
            wb.LEDGER = root / "docs" / "ledger.jsonl"
            try:
                result = wb.run_capture("unit-probe", [sys.executable, "-c", "print('unit-probe-ok')"], cwd=root, timeout_s=10)
                self.assertEqual("PASSED", result["status"])
                self.assertEqual(0, result["exit_code"])
                self.assertIn("unit-probe-ok", (root / result["stdout"]).read_text())
                self.assertTrue(wb.verify_ledger()["valid"])
            finally:
                wb.ROOT, wb.RUNS, wb.LEDGER = old

    def test_run_capture_marks_timeout_not_passed(self):
        import sys
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            old = (wb.ROOT, wb.RUNS, wb.LEDGER)
            wb.ROOT = root
            wb.RUNS = root / "artifacts" / "runs"
            wb.LEDGER = root / "docs" / "ledger.jsonl"
            try:
                result = wb.run_capture("timeout-probe", [sys.executable, "-c", "import time; time.sleep(2)"], cwd=root, timeout_s=1)
                self.assertEqual("TIMED_OUT", result["status"])
            finally:
                wb.ROOT, wb.RUNS, wb.LEDGER = old

    def test_run_capture_drops_secret_environment_and_redacts_outputs_and_manifest(self):
        import sys
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            old = (wb.ROOT, wb.RUNS, wb.LEDGER)
            wb.ROOT = root
            wb.RUNS = root / "artifacts" / "runs"
            wb.LEDGER = root / "docs" / "ledger.jsonl"
            code = "import os; print('API_KEY=sample-secret-value'); print('PASSWORD="quoted secret value"'); print('Authorization: Bearer abcdefghijklmnop'); print(os.getenv('KELLVYN_WORKBENCH_TEST_TOKEN', 'not-inherited'))"
            try:
                with mock.patch.dict(wb.os.environ, {"KELLVYN_WORKBENCH_TEST_TOKEN": "host-secret-value"}):
                    result = wb.run_capture("secret-redaction-probe", [sys.executable, "-c", code], cwd=root, timeout_s=10)
                self.assertEqual("PASSED", result["status"])
                stdout = (root / result["stdout"]).read_text()
                manifest = root / Path(result["stdout"]).parent / "run.json"
                # Run outputs must not contain inherited host credentials or printed key values.
                self.assertIn("not-inherited", stdout)
                self.assertIn("[REDACTED]", stdout)
                self.assertNotIn("host-secret-value", stdout)
                self.assertNotIn("sample-secret-value", stdout)
                self.assertNotIn("quoted secret value", stdout)
                self.assertNotIn("abcdefghijklmnop", stdout)
                self.assertNotIn("sample-secret-value", manifest.read_text())
                self.assertNotIn("quoted secret value", manifest.read_text())
                self.assertNotIn("abcdefghijklmnop", manifest.read_text())
                self.assertNotIn("host-secret-value", manifest.read_text())
            finally:
                wb.ROOT, wb.RUNS, wb.LEDGER = old

    def test_sanitized_child_environment_rejects_explicit_secret_overrides(self):
        with self.assertRaisesRegex(ValueError, "sensitive environment variable"):
            wb.sanitized_child_environment({"SERVICE_API_TOKEN": "must-not-pass"})

    @unittest.skipIf(__import__("os").name == "nt", "POSIX process-group termination only")
    def test_timeout_terminates_descendant_processes(self):
        import sys
        import time
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            old = (wb.ROOT, wb.RUNS, wb.LEDGER)
            wb.ROOT = root
            wb.RUNS = root / "artifacts" / "runs"
            wb.LEDGER = root / "docs" / "ledger.jsonl"
            sentinel = root / "descendant-survived.txt"
            child_code = "import time,pathlib; time.sleep(1.5); pathlib.Path(" + repr(str(sentinel)) + ").write_text('survived')"
            parent_code = "import subprocess,sys,time; subprocess.Popen([sys.executable,'-c'," + repr(child_code) + "]); time.sleep(30)"
            try:
                result = wb.run_capture("process-tree-timeout", [sys.executable, "-c", parent_code], cwd=root, timeout_s=1)
                self.assertEqual("TIMED_OUT", result["status"])
                time.sleep(1.8)
                self.assertFalse(sentinel.exists(), "descendant process survived timeout termination")
            finally:
                wb.ROOT, wb.RUNS, wb.LEDGER = old

    def test_tampering_is_detected(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "ledger.jsonl"
            original = wb.LEDGER
            wb.LEDGER = path
            try:
                wb.append_event("RESEARCH", "RECORDED", "original")
            finally:
                wb.LEDGER = original
            row = json.loads(path.read_text())
            row["summary"] = "silently changed"
            path.write_text(json.dumps(row) + "\n")
            report = wb.verify_ledger(path)
            self.assertFalse(report["valid"])
            self.assertIn("hash mismatch", report["message"])

    def test_sequence_reordering_is_detected(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "ledger.jsonl"
            original = wb.LEDGER
            wb.LEDGER = path
            try:
                wb.append_event("A", "RECORDED", "first")
                wb.append_event("B", "RECORDED", "second")
            finally:
                wb.LEDGER = original
            rows = path.read_text().splitlines()
            path.write_text(rows[1] + "\n" + rows[0] + "\n")
            self.assertFalse(wb.verify_ledger(path)["valid"])

class DiagnosticTests(unittest.TestCase):
    def test_kotlin_and_stack_trace_locations_are_extracted(self):
        with tempfile.TemporaryDirectory() as td:
            log = Path(td) / "build.log"
            log.write_text("e: app/src/main/Kellvyn.kt: (42, 9): Unresolved reference: missing\n at com.example.Main.run(MainActivity.kt:88)\n")
            original = wb.ROOT
            # within_repo uses ROOT; put fixture under its temporary override.
            wb.ROOT = Path(td)
            try:
                report = wb.debug_log(log)
            finally:
                wb.ROOT = original
            self.assertGreaterEqual(report["finding_count"], 2)
            self.assertTrue(any(x.get("line") == 42 for x in report["findings"]))
            self.assertTrue(any(x.get("line") == 88 for x in report["findings"]))

    def test_outside_repository_path_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            original = wb.ROOT
            wb.ROOT = Path(td)
            try:
                with self.assertRaises(ValueError):
                    wb.within_repo(Path(td).parent / "outside.log")
            finally:
                wb.ROOT = original

class CliTests(unittest.TestCase):
    def test_timeout_range_is_enforced(self):
        with self.assertRaises(SystemExit):
            wb.main(["test", "--suite", "workbench", "--timeout", "0"])

    def test_parser_exposes_core_workbench_domains(self):
        parser = wb.build_parser()
        self.assertIsNotNone(parser)
        # Existing studio is delegated to rather than recreated.
        self.assertTrue(wb.STUDIO.name == "studio.py")
        self.assertEqual("blender_pipeline.py", wb.BLENDER_PIPELINE.name)


    def test_dashboard_is_a_static_report_with_timeline_and_run_status(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            old = (wb.ROOT, wb.HOME, wb.RUNS, wb.AUDITS, wb.LEDGER, wb.STATE_MD)
            wb.ROOT = root; wb.HOME = root / "artifacts" / "workbench"; wb.RUNS = wb.HOME / "runs"
            wb.AUDITS = wb.HOME / "audits"; wb.LEDGER = root / "docs" / "ledger.jsonl"; wb.STATE_MD = root / "docs" / "STATE.md"
            try:
                wb.append_event("TEST", "PASSED", "dashboard test event")
                run = wb.RUNS / "r1"; run.mkdir(parents=True)
                (run / "stdout.log").write_text("test output")
                (run / "stderr.log").write_text("")
                (run / "run.json").write_text(json.dumps({"name":"fake-suite","status":"PASSED","duration_seconds":1.2,"run_id":"r1","stdout":"artifacts/workbench/runs/r1/stdout.log","stderr":"artifacts/workbench/runs/r1/stderr.log"}))
                wb.STATE_MD.write_text("# checkpoint")
                page = wb.build_dashboard()
                content = page.read_text()
                self.assertIn("dashboard test event", content)
                self.assertIn("fake-suite", content)
                self.assertIn("checkpoint", content)
            finally:
                wb.ROOT, wb.HOME, wb.RUNS, wb.AUDITS, wb.LEDGER, wb.STATE_MD = old

class VisualDiffTests(unittest.TestCase):
    def test_visual_diff_outputs_overlay_and_metrics_when_pillow_exists(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow optional dependency is not installed")
        from importlib.util import spec_from_file_location, module_from_spec
        visual_path = MODULE.with_name("visual_diff.py")
        visual_spec = spec_from_file_location("visual_diff", visual_path)
        visual = module_from_spec(visual_spec)
        visual_spec.loader.exec_module(visual)
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            before, after, out = root / "before.png", root / "after.png", root / "out"
            Image.new("RGBA", (8, 8), (255, 0, 0, 255)).save(before)
            Image.new("RGBA", (8, 8), (0, 255, 0, 255)).save(after)
            report = visual.compare(before, after, out)
            self.assertGreater(report["changed_pixel_ratio"], 0.9)
            self.assertTrue((out / "overlay.png").is_file())
            self.assertTrue((out / "difference.png").is_file())

    def test_visual_diff_rejects_dimension_mismatch(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow optional dependency is not installed")
        from importlib.util import spec_from_file_location, module_from_spec
        visual_spec = spec_from_file_location("visual_diff", MODULE.with_name("visual_diff.py"))
        visual = module_from_spec(visual_spec); visual_spec.loader.exec_module(visual)
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); a = root / "a.png"; b = root / "b.png"
            Image.new("RGB", (8, 8)).save(a); Image.new("RGB", (9, 8)).save(b)
            with self.assertRaises(ValueError): visual.compare(a, b, root / "out")

if __name__ == "__main__": unittest.main(verbosity=2)
