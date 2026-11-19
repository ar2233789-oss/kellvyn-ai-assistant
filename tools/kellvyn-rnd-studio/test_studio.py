"""Safety tests for the R&D Studio. Uses temporary Git repositories and stdlib only."""
import importlib.util, subprocess, tempfile, unittest, json, os
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
  wrapper_dir=self.root/"android/gradle/wrapper"; wrapper_dir.mkdir(parents=True)
  gradlew=self.root/"android/gradlew"
  gradlew.write_text("#!/bin/sh\nif [ -f ../force-gradle-failure ]; then exit 7; fi\nprintf '%s\n' \"$@\"\n")
  gradlew.chmod(0o755)
  (wrapper_dir/"gradle-wrapper.jar").write_bytes(b"trusted-test-wrapper")
  (wrapper_dir/"gradle-wrapper.properties").write_text("distributionUrl=https://services.gradle.org/distributions/gradle-test.zip\n")
  h=git("hash-object","src/Protected.kt",cwd=self.root); (self.root/".github/interface-lock.sha").write_text(f"{h}  src/Protected.kt\n")
  git("add",".",cwd=self.root); git("commit","-m","base",cwd=self.root)
  self.old=(studio.ROOT,studio.TASKS,studio.LOCK,studio.DOCS); self.old_env={k:os.environ.get(k) for k in ("KELLVYN_GRADLE_EXECUTABLE","KELLVYN_GRADLE_VERSION")}; studio.ROOT=self.root; studio.TASKS=self.root.parent/"rnd-tasks"; studio.LOCK=self.root/".github/interface-lock.sha"; studio.DOCS=self.root/"docs/kellvyn/rnd-studio"
 def tearDown(self):
  studio.ROOT,studio.TASKS,studio.LOCK,studio.DOCS=self.old
  for key,value in self.old_env.items():
   if value is None: os.environ.pop(key,None)
   else: os.environ[key]=value
  self.tmp.cleanup()
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
 def test_track_maps_create_complete_manifests_for_all_scopes(self):
  for scope,definition in studio.TRACKS.items():
   for relative in definition["sources"]:
    p=self.root/relative; p.parent.mkdir(parents=True,exist_ok=True); p.write_text("source stub\n")
   for class_name in definition["tests"]:
    p=self.root/"android/app/src/test/java"/Path(*class_name.split(".")).with_suffix(".kt")
    p.parent.mkdir(parents=True,exist_ok=True); p.write_text("class TestStub\n")
   result=studio.track_map(SimpleNamespace(scope=scope))
   self.assertEqual(0,result)
   manifest=json.loads((studio.DOCS/"maps"/(scope+"-map.json")).read_text())
   self.assertEqual([],manifest["missing_tests"])
   self.assertTrue(all(item["exists"] for item in manifest["sources"]))
   self.assertTrue(all(item["exists"] for item in manifest["tests"]))
 def test_track_map_blocks_when_required_source_or_test_is_missing(self):
  result=studio.track_map(SimpleNamespace(scope="brain"))
  self.assertEqual(2,result)
 def test_task_test_executes_selected_suite_and_records_verifiable_evidence(self):
  work=self.create()
  result=studio.task_test(SimpleNamespace(id="brain-core",suite="brain",timeout=30))
  self.assertEqual(0,result)
  task=json.loads((studio.TASKS/"brain-core/task.json").read_text())
  evidence=task["evidence"][-1]
  self.assertEqual("PASSED",evidence["status"])
  self.assertEqual("PASS",evidence["gradle_wrapper_integrity"])
  log=Path(evidence["log"]).read_text()
  self.assertIn("KellvynTransformerLanguageModelTest",log)
  self.assertIn("KellvynDecisionProtocolTest",log)
  self.assertNotIn("KellvynOrchestratorTest",log)
  report=studio.check_report(SimpleNamespace(id="brain-core",require_test=[]))
  self.assertEqual("PASS",report["gate"])
 def test_task_test_nonzero_exit_is_recorded_as_failed_not_passed(self):
  work=self.create()
  (work/"force-gradle-failure").write_text("fail\n")
  result=studio.task_test(SimpleNamespace(id="brain-core",suite="brain",timeout=30))
  self.assertEqual(2,result)
  task=json.loads((studio.TASKS/"brain-core/task.json").read_text())
  self.assertEqual("FAILED",task["evidence"][-1]["status"])
  report=studio.check_report(SimpleNamespace(id="brain-core",require_test=[]))
  self.assertEqual("BLOCKED",report["gate"])
 def test_task_test_refuses_modified_gradle_wrapper_before_execution(self):
  work=self.create()
  wrapper=work/"android/gradlew"
  wrapper.write_text("#!/bin/sh\ntouch ../tampered-wrapper-executed\n")
  wrapper.chmod(0o755)
  with self.assertRaisesRegex(RuntimeError,"Gradle wrapper integrity"):
   studio.task_test(SimpleNamespace(id="brain-core",suite="brain",timeout=30))
  self.assertFalse((work/"tampered-wrapper-executed").exists())
  report=studio.check_report(SimpleNamespace(id="brain-core",require_test=[]))
  self.assertEqual("FAIL",report["gradle_wrapper"])
  self.assertEqual("BLOCKED",report["gate"])
 def remove_wrapper_and_commit(self):
  git("rm","-r","android",cwd=self.root)
  git("commit","-m","remove wrapper for system Gradle test",cwd=self.root)
 def fake_system_gradle(self, script=None):
  executable=self.root.parent/"trusted-gradle"/"gradle"
  executable.parent.mkdir(parents=True,exist_ok=True)
  executable.write_text(script or "#!/bin/sh\nif [ \"$1\" = \"--version\" ]; then echo 'Gradle 8.13'; exit 0; fi\nprintf '%s\n' \"$@\"\n")
  executable.chmod(0o755)
  os.environ["KELLVYN_GRADLE_EXECUTABLE"]=str(executable)
  os.environ["KELLVYN_GRADLE_VERSION"]="8.13"
  return executable
 def test_task_create_and_test_use_pinned_system_gradle_when_wrapper_is_absent(self):
  self.remove_wrapper_and_commit()
  executable=self.fake_system_gradle()
  work=self.create()
  manifest=json.loads((studio.TASKS/"brain-core/task.json").read_text())
  self.assertEqual("system",manifest["gradle_execution"]["mode"])
  self.assertEqual(studio.digest(executable),manifest["gradle_execution"]["sha256"])
  result=studio.task_test(SimpleNamespace(id="brain-core",suite="brain",timeout=30))
  self.assertEqual(0,result)
  evidence=json.loads((studio.TASKS/"brain-core/task.json").read_text())["evidence"][-1]
  self.assertEqual("PASSED",evidence["status"])
  self.assertEqual("system",evidence["gradle_execution_mode"])
  self.assertEqual("PASS",evidence["gradle_wrapper_integrity"])
  self.assertEqual("PASS",studio.check_report(SimpleNamespace(id="brain-core",require_test=[]))["gate"])
 def test_task_test_refuses_a_replaced_system_gradle_before_executing_it(self):
  self.remove_wrapper_and_commit()
  executable=self.fake_system_gradle()
  work=self.create()
  marker=self.root.parent/"tampered-gradle-ran"
  executable.write_text(f"#!/bin/sh\ntouch '{marker}'\necho 'Gradle 8.13'\n")
  executable.chmod(0o755)
  with self.assertRaisesRegex(RuntimeError,"Gradle wrapper integrity"):
   studio.task_test(SimpleNamespace(id="brain-core",suite="brain",timeout=30))
  self.assertFalse(marker.exists())
  report=studio.check_report(SimpleNamespace(id="brain-core",require_test=[]))
  self.assertEqual("FAIL",report["gradle_wrapper"])
  self.assertEqual("BLOCKED",report["gate"])
 def test_task_create_blocks_when_wrapper_and_trusted_system_gradle_are_absent(self):
  self.remove_wrapper_and_commit()
  os.environ["KELLVYN_GRADLE_EXECUTABLE"]=str(self.root.parent/"missing-gradle")
  with self.assertRaisesRegex(RuntimeError,"configured system Gradle executable is missing"):
   self.create()
 def test_repo_map_matches_test_files_by_referenced_symbols(self):
  main=self.root/"android/app/src/main/java/sample"; main.mkdir(parents=True)
  (main/"AgentCoordinator.kt").write_text("package sample\nclass AgentCoordinator\n")
  test_dir=self.root/"android/app/src/test/java/sample"; test_dir.mkdir(parents=True)
  test_file=test_dir/"ArchitectureContractTest.kt"
  test_file.write_text("package sample\nclass ArchitectureContractTest { fun verifies() { AgentCoordinator() } }\n")
  result=studio.repo_map(SimpleNamespace())
  self.assertEqual(0,result)
  manifest=json.loads((studio.DOCS/"repository-map.json").read_text())
  row=next(item for item in manifest["sources"] if item["file"].endswith("AgentCoordinator.kt"))
  self.assertIn("android/app/src/test/java/sample/ArchitectureContractTest.kt",row["test_candidates"])
 def test_task_records_scope_and_scope_specific_gate(self):
  self.create()
  m=json.loads((studio.TASKS/"brain-core/task.json").read_text())
  self.assertEqual("brain",m["scope"])
  self.assertEqual(["brain"],m["required_tests"])
 def test_track_registry_has_three_separate_lanes(self):
  self.assertEqual({"brain","agents","integration"},set(studio.TRACKS))
  self.assertTrue(set(studio.TRACKS["brain"]["tests"]).isdisjoint(set(studio.TRACKS["agents"]["tests"])))
if __name__=="__main__": unittest.main(verbosity=2)
