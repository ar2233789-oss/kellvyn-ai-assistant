"""Safety tests for the R&D Studio. Uses temporary Git repositories and stdlib only."""
import importlib.util, subprocess, tempfile, unittest, json
from pathlib import Path
from types import SimpleNamespace

src=Path(__file__).with_name("studio.py")
spec=importlib.util.spec_from_file_location("rnd_studio",src); studio=importlib.util.module_from_spec(spec); spec.loader.exec_module(studio)
def git(*args,cwd): return subprocess.run(["git",*args],cwd=cwd,text=True,capture_output=True,check=True).stdout.strip()

class StudioTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory(); self.root=Path(self.tmp.name)/"repo"; self.root.mkdir()
  git("init",cwd=self.root); git("config","user.name","test",cwd=self.root); git("config","user.email","test@example.invalid",cwd=self.root)
  (self.root/".github").mkdir(); (self.root/"src").mkdir(); (self.root/"src/Protected.kt").write_text("class Protected\n")
  h=git("hash-object","src/Protected.kt",cwd=self.root); (self.root/".github/interface-lock.sha").write_text(f"{h}  src/Protected.kt\n")
  git("add",".",cwd=self.root); git("commit","-m","base",cwd=self.root)
  self.old=(studio.ROOT,studio.TASKS,studio.LOCK); studio.ROOT=self.root; studio.TASKS=self.root.parent/"rnd-tasks"; studio.LOCK=self.root/".github/interface-lock.sha"
 def tearDown(self): studio.ROOT,studio.TASKS,studio.LOCK=self.old; self.tmp.cleanup()
 def create(self):
  studio.task_create(SimpleNamespace(id="brain-core",title="Brain test",objective="Isolate work",scope="brain",base="HEAD",require_test=[]))
  return studio.TASKS/"brain-core/checkout"
 def test_worktree_is_separate(self):
  p=self.create(); self.assertTrue(p.is_dir()); self.assertNotEqual(p,self.root); self.assertEqual("kellvyn-rnd/brain-core",git("branch","--show-current",cwd=p))
 def test_protected_change_blocks(self):
  p=self.create(); (p/"src/Protected.kt").write_text("class Changed\n")
  r=studio.check_report(SimpleNamespace(id="brain-core",require_test=[])); self.assertEqual("FAIL",r["protected"]); self.assertEqual("BLOCKED",r["gate"])
 def test_untracked_whitespace_blocks(self):
  p=self.create(); (p/"src/New.kt").write_text("class New  \n")
  r=studio.check_report(SimpleNamespace(id="brain-core",require_test=[])); self.assertEqual("FAIL",r["diff_check"])
 def test_missing_test_blocks(self):
  self.create(); r=studio.check_report(SimpleNamespace(id="brain-core",require_test=["unit"])); self.assertEqual(["unit"],r["missing_or_stale_tests"])
 def test_stale_test_evidence_blocks_after_source_change(self):
  p=self.create(); m=json.loads((studio.TASKS/"brain-core/task.json").read_text()); log=studio.TASKS/"brain-core/evidence/unit.log"; log.parent.mkdir(exist_ok=True); log.write_text("actual test output\n")
  m["evidence"].append({"suite":"unit","exit_code":0,"head":studio.head(p),"fingerprint":studio.fingerprint(p,m["base"]),"log":str(log),"log_sha256":studio.digest(log)})
  (studio.TASKS/"brain-core/task.json").write_text(json.dumps(m)); self.assertEqual("PASS",studio.check_report(SimpleNamespace(id="brain-core",require_test=["unit"]))["gate"])
  (p/"src/New.kt").write_text("class New\n"); self.assertEqual("BLOCKED",studio.check_report(SimpleNamespace(id="brain-core",require_test=["unit"]))["gate"])
 def test_only_three_gradle_suites_are_exposed(self): self.assertEqual({"unit","assemble","instrumented"},set(studio.TESTS))
 def test_brain_suite_filters_only_neural_and_protocol_classes(self):
  cmd=studio.suite_command("brain")
  selected=[cmd[i+1] for i,x in enumerate(cmd[:-1]) if x=="--tests"]
  self.assertTrue(any(".neural.KellvynTransformerLanguageModelTest" in x for x in selected))
  self.assertIn("com.kellvyn.assistant.KellvynDecisionProtocolTest",selected)
  self.assertFalse(any("Orchestrator" in x or "AgentTest" in x for x in selected))
 def test_agents_suite_filters_orchestrator_and_specialist_contracts(self):
  cmd=studio.suite_command("agents")
  selected=[cmd[i+1] for i,x in enumerate(cmd[:-1]) if x=="--tests"]
  self.assertIn("com.kellvyn.assistant.KellvynOrchestratorTest",selected)
  self.assertIn("com.kellvyn.assistant.KellvynAgentDelegationTest",selected)
  self.assertIn("com.kellvyn.assistant.KellvynSpecialistToolPolicyTest",selected)
  self.assertFalse(any(".neural." in x for x in selected))
 def test_integration_suite_contains_brain_and_agent_boundaries_once(self):
  cmd=studio.suite_command("integration")
  selected=[cmd[i+1] for i,x in enumerate(cmd[:-1]) if x=="--tests"]
  self.assertIn("com.kellvyn.assistant.KellvynTransformerInferenceEngineTest",selected)
  self.assertIn("com.kellvyn.assistant.KellvynOrchestratorTest",selected)
  self.assertIn("com.kellvyn.assistant.KellvynBrainAgentIntegrationContractTest",selected)
  self.assertEqual(len(selected),len(set(selected)))
 def test_task_records_scope_and_scope_specific_gate(self):
  self.create()
  m=json.loads((studio.TASKS/"brain-core/task.json").read_text())
  self.assertEqual("brain",m["scope"])
  self.assertEqual(["brain"],m["required_tests"])
 def test_track_registry_has_three_separate_lanes(self):
  self.assertEqual({"brain","agents","integration"},set(studio.TRACKS))
  self.assertTrue(set(studio.TRACKS["brain"]["tests"]).isdisjoint(set(studio.TRACKS["agents"]["tests"])))
if __name__=="__main__": unittest.main(verbosity=2)
