from __future__ import annotations
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).parents[1] / "tools" / "tool_registry.py"
spec = importlib.util.spec_from_file_location("tool_registry", MODULE)
registry_module = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(registry_module)

REGISTRY_PATH = Path(__file__).parents[1] / "tool_registry.json"


class ToolRegistryTests(unittest.TestCase):
    def setUp(self):
        self.registry = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
        self.root = Path(__file__).parents[2]

    def test_current_registry_is_valid_and_single_intelligence(self):
        result = registry_module.validate_registry(self.registry, self.root)
        self.assertEqual("valid", result["status"])
        self.assertEqual(10, result["tool_count"])
        self.assertTrue(result["single_intelligence"])
        self.assertFalse(result["tools_are_agents"])

    def test_model_inspector_is_registered_as_passive_metadata_tool(self):
        result = registry_module.validate_registry(self.registry, self.root)
        tool = next(item for item in result["tools"] if item["id"] == "model.inspect")
        self.assertEqual(["--model-file"], tool["inputs"])
        self.assertFalse(tool["network"])
        self.assertFalse(tool["executes_project_code"])
        self.assertEqual([], tool["side_effects"])

    def test_memory_estimator_is_registered_with_numeric_budget_contracts(self):
        result = registry_module.validate_registry(self.registry, self.root)
        tool = next(item for item in result["tools"] if item["id"] == "model.estimate_memory")
        self.assertEqual("number", tool["arguments"]["--usable-memory-mb"]["type"])
        self.assertEqual("number", tool["arguments"]["--runtime-overhead-mb"]["type"])
        self.assertFalse(tool["network"])
        self.assertFalse(tool["executes_project_code"])

    def test_all_scripts_resolve_inside_repository(self):
        result = registry_module.validate_registry(self.registry, self.root)
        for tool in result["tools"]:
            self.assertTrue((self.root / tool["script"]).is_file(), tool["id"])

    def test_duplicate_tool_id_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"].append(copy.deepcopy(changed["tools"][0]))
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_missing_contract_field_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        del changed["tools"][0]["purpose"]
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_network_access_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"][0]["network"] = True
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_project_code_execution_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"][0]["executes_project_code"] = True
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_agent_orchestrator_policy_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["policy"]["tools_are_not_agents"] = False
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_brain_scope_must_remain_excluded(self):
        changed = copy.deepcopy(self.registry)
        changed["policy"]["brain_scope"] = "included"
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_parent_traversal_in_script_path_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"][0]["script"] = "../../outside.py"
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_missing_script_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"][0]["script"] = "workshop/tools/does-not-exist.py"
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_duplicate_cli_flags_are_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"][0]["optional_inputs"] = ["--root"]
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_duplicate_script_command_pair_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"].append(copy.deepcopy(changed["tools"][0]))
        changed["tools"][-1]["id"] = "dataset.inspect.copy"
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_argument_contract_must_cover_every_cli_flag(self):
        changed = copy.deepcopy(self.registry)
        del changed["tools"][0]["arguments"]["--root"]
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_unknown_argument_type_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"][0]["arguments"]["--root"]["type"] = "shell_command"
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_argument_required_flag_must_match_contract(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"][0]["arguments"]["--root"]["required"] = False
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_invalid_registry_schema_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["schema"] = 99
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_non_object_tool_is_rejected(self):
        changed = copy.deepcopy(self.registry)
        changed["tools"][0] = "not-a-tool"
        with self.assertRaises(registry_module.RegistryError):
            registry_module.validate_registry(changed, self.root)

    def test_read_registry_rejects_invalid_json(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "broken.json"
            path.write_text("{broken", encoding="utf-8")
            with self.assertRaises(registry_module.RegistryError):
                registry_module.read_registry(path)


if __name__ == "__main__":
    unittest.main()
