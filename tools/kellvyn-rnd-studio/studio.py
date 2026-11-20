#!/usr/bin/env python3
"""Kellvyn R&D Studio: isolated source study, experiments and evidence gates."""
from __future__ import annotations
import argparse, hashlib, json, os, re, shutil, subprocess, sys, time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs/kellvyn/rnd-studio"
TASKS = ROOT.parent / f".{ROOT.name}-rnd-studio" / "tasks"
LOCK = ROOT / ".github/interface-lock.sha"
GRADLE_WRAPPER_FILES = (
    "android/gradlew",
    "android/gradlew.bat",
    "android/gradle/wrapper/gradle-wrapper.jar",
    "android/gradle/wrapper/gradle-wrapper.properties",
)
TESTS = {
    "unit": ["testDebugUnitTest", "--no-daemon"],
    "assemble": ["assembleDebug", "--no-daemon"],
    "instrumented": ["connectedDebugAndroidTest", "--no-daemon"],
}
TRACKS = {
 "kn": {
  "title": "KN R&D",
  "sources": [
   "android/app/src/main/java/com/kellvyn/assistant/KellvynRuntime.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynLocalNeuralInferenceEngine.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynTransformerInferenceEngine.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynDecisionProtocol.kt",
   "android/app/src/main/java/com/kellvyn/assistant/neural/KellvynBpeTokenizer.kt",
   "android/app/src/main/java/com/kellvyn/assistant/neural/KellvynAttentionLanguageModel.kt",
   "android/app/src/main/java/com/kellvyn/assistant/neural/KellvynTransformerLanguageModel.kt",
   "android/app/src/main/java/com/kellvyn/assistant/neural/KellvynTransformerTrainingPipeline.kt",
   "android/app/src/main/java/com/kellvyn/assistant/neural/KellvynTransformerTrainingQualityGate.kt",
   "android/app/src/main/java/com/kellvyn/assistant/neural/KellvynNeuralModelStore.kt"
  ],
  "tests": [
   "com.kellvyn.assistant.KellvynDecisionProtocolTest",
   "com.kellvyn.assistant.KellvynTransformerInferenceEngineTest",
   "com.kellvyn.assistant.neural.KellvynAttentionLanguageModelTest",
   "com.kellvyn.assistant.neural.KellvynBpeTokenizerTest",
   "com.kellvyn.assistant.neural.KellvynByteModelTest",
   "com.kellvyn.assistant.neural.KellvynCompletionTrainingTest",
   "com.kellvyn.assistant.neural.KellvynNeuralModelStoreTest",
   "com.kellvyn.assistant.neural.KellvynTrainingPipelineTest",
   "com.kellvyn.assistant.neural.KellvynTransformerLanguageModelTest",
   "com.kellvyn.assistant.neural.KellvynTransformerTrainingPipelineTest",
   "com.kellvyn.assistant.neural.KellvynTransformerTrainingQualityGateTest"
  ],
  "metrics": ["tokenizer_round_trip","gradient_error_per_group","kv_cache_probability_error","protocol_validity","held_out_loss","latency_ram_thermal_battery"]
 },
 "agents": {
  "title": "Agent R&D",
  "sources": [
   "android/app/src/main/java/com/kellvyn/assistant/KellvynAgent.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynOrchestrator.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynSpecialistToolPolicy.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynSpecialistEvaluationHarness.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynSpecialistAgentBenchmarkExecutor.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynSpecialistBenchmarkRegistry.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynSpecialistProfileCompiler.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynSpecialistProfileRegistry.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynAgentLabTrace.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynTaskPlanner.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynAgentLimits.kt"
  ],
  "tests": [
   "com.kellvyn.assistant.KellvynAgentDelegationTest",
   "com.kellvyn.assistant.KellvynAgentLabTraceStoreTest",
   "com.kellvyn.assistant.KellvynAgentLimitsTest",
   "com.kellvyn.assistant.KellvynAgentPlanTest",
   "com.kellvyn.assistant.KellvynAgentSpecialistEvaluationTest",
   "com.kellvyn.assistant.KellvynAgentTest",
   "com.kellvyn.assistant.KellvynOrchestratorTest",
   "com.kellvyn.assistant.KellvynSpecialistAgentBenchmarkExecutorTest",
   "com.kellvyn.assistant.KellvynSpecialistEvaluationHarnessTest",
   "com.kellvyn.assistant.KellvynSpecialistProfileRegistryTest",
   "com.kellvyn.assistant.KellvynSpecialistProfileStoreTest",
   "com.kellvyn.assistant.KellvynSpecialistToolPolicyTest"
  ],
  "metrics": ["routing_accuracy","evidence_coverage","policy_violation_rate","context_leakage","timeout_cancellation_budget","tool_success_rate","delegation_duration_ms","pass_k_reliability","resource_cost"]
 },
 "integration": {
  "title": "KN + Agent Integration",
  "sources": [
   "android/app/src/main/java/com/kellvyn/assistant/KellvynRuntime.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynAgent.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynOrchestrator.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynLocalNeuralInferenceEngine.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynTransformerInferenceEngine.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynDecisionProtocol.kt",
   "android/app/src/main/java/com/kellvyn/assistant/KellvynAgentLabTrace.kt"
  ],
  "tests": [
   "com.kellvyn.assistant.KellvynDecisionProtocolTest",
   "com.kellvyn.assistant.KellvynTransformerInferenceEngineTest",
   "com.kellvyn.assistant.KellvynAgentDelegationTest",
   "com.kellvyn.assistant.KellvynAgentSpecialistEvaluationTest",
   "com.kellvyn.assistant.KellvynOrchestratorTest",
   "com.kellvyn.assistant.KellvynSpecialistAgentBenchmarkExecutorTest",
   "com.kellvyn.assistant.KellvynSpecialistEvaluationHarnessTest",
   "com.kellvyn.assistant.KellvynSpecialistToolPolicyTest",
   "com.kellvyn.assistant.KellvynKNAgentIntegrationContractTest"
  ],
  "metrics": ["valid_decision_rate","inference_to_agent_success","verified_tool_success","verified_delegation_success","untrusted_result_propagation","runtime_connection_status"]
 }
}
SUITE_NAMES = set(TESTS) | set(TRACKS)
ID = re.compile(r"^[a-z0-9][a-z0-9-]{1,39}$")
SYMBOL = re.compile(r"^\s*(?:(?:public|internal|private|protected|open|abstract|data|sealed|enum|fun|suspend|inline|override|const)\s+)*(?:class|interface|object|typealias|fun)\s+([A-Za-z_]\w*)", re.M)

def now(): return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()

def gradle_wrapper_snapshot(cwd):
    result = {}
    for relative in GRADLE_WRAPPER_FILES:
        path = cwd / relative
        result[relative] = digest(path) if path.is_file() and not path.is_symlink() else None
    return result

def gradle_wrapper_baseline(revision):
    result = {}
    for relative in GRADLE_WRAPPER_FILES:
        blob = subprocess.run(
            ["git", "show", f"{revision}:{relative}"],
            cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE
        )
        result[relative] = hashlib.sha256(blob.stdout).hexdigest() if blob.returncode == 0 else None
    return result

def gradle_version(executable):
    result = subprocess.run(
        [str(executable), "--version"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=30
    )
    if result.returncode:
        raise RuntimeError("trusted Gradle --version check failed: " + (result.stdout or "")[-1000:])
    match = re.search(r"(?m)^\s*Gradle\s+([0-9][A-Za-z0-9.+-]*)\s*$", result.stdout or "")
    if not match:
        raise RuntimeError("could not identify the Gradle version from --version output")
    return match.group(1)

def system_gradle_baseline():
    configured = os.environ.get("KELLVYN_GRADLE_EXECUTABLE", "").strip()
    candidate = configured or shutil.which("gradle")
    if not candidate:
        return None
    executable = Path(candidate).resolve()
    if not executable.is_file() or not os.access(executable, os.X_OK):
        raise RuntimeError("configured system Gradle executable is missing or not executable")
    if executable == ROOT or ROOT in executable.parents:
        raise RuntimeError("system Gradle executable must be outside the repository/worktree")
    version = gradle_version(executable)
    expected = os.environ.get("KELLVYN_GRADLE_VERSION", "8.13").strip()
    if expected and version != expected:
        raise RuntimeError(f"system Gradle version {version} does not match the pinned version {expected}")
    return {
        "mode": "system",
        "executable": str(executable),
        "sha256": digest(executable),
        "version": version
    }

def gradle_execution_baseline(revision):
    wrapper_hashes = gradle_wrapper_baseline(revision)
    required = ("android/gradlew", "android/gradle/wrapper/gradle-wrapper.jar", "android/gradle/wrapper/gradle-wrapper.properties")
    if all(wrapper_hashes.get(path) is not None for path in required):
        return {"mode": "wrapper", "files_sha256": wrapper_hashes}
    system = system_gradle_baseline()
    if system:
        return system
    raise RuntimeError(
        "base has no complete trusted Gradle wrapper and no pinned system Gradle 8.13 is available; "
        "install Gradle 8.13 or set KELLVYN_GRADLE_EXECUTABLE and KELLVYN_GRADLE_VERSION"
    )

def gradle_wrapper_integrity(work, record):
    execution = record.get("gradle_execution")
    if isinstance(execution, dict):
        mode = execution.get("mode")
        if mode == "wrapper":
            expected = execution.get("files_sha256")
            if not isinstance(expected, dict) or set(expected) != set(GRADLE_WRAPPER_FILES):
                return {"status": "FAIL", "changed": ["wrapper_baseline_missing"], "sha256": None, "mode": mode}
            current = gradle_wrapper_snapshot(work)
            changed = [relative for relative in GRADLE_WRAPPER_FILES if current[relative] != expected.get(relative)]
            return {"status": "PASS" if not changed else "FAIL", "changed": changed, "sha256": current, "mode": mode}
        if mode == "system":
            executable_text = execution.get("executable")
            expected_sha = execution.get("sha256")
            expected_version = execution.get("version")
            if not executable_text or not expected_sha or not expected_version:
                return {"status": "FAIL", "changed": ["system_gradle_baseline_missing"], "sha256": None, "mode": mode}
            executable = Path(executable_text).resolve()
            if not executable.is_file() or not os.access(executable, os.X_OK):
                return {"status": "FAIL", "changed": ["system_gradle_missing"], "sha256": None, "mode": mode}
            current_sha = digest(executable)
            if current_sha != expected_sha:
                # Do not execute a binary whose digest changed after the baseline.
                return {"status": "FAIL", "changed": ["system_gradle_sha256"], "sha256": current_sha, "mode": mode}
            try:
                current_version = gradle_version(executable)
            except (OSError, RuntimeError, subprocess.TimeoutExpired):
                return {"status": "FAIL", "changed": ["system_gradle_version_unavailable"], "sha256": current_sha, "mode": mode}
            changed = [] if current_version == expected_version else ["system_gradle_version"]
            return {"status": "PASS" if not changed else "FAIL", "changed": changed, "sha256": current_sha, "mode": mode}
        return {"status": "FAIL", "changed": ["unknown_gradle_execution_mode"], "sha256": None, "mode": mode}

    # Backward compatibility for task manifests created before the execution-mode contract.
    expected = record.get("gradle_wrapper_sha256")
    if not isinstance(expected, dict) or set(expected) != set(GRADLE_WRAPPER_FILES):
        return {"status": "FAIL", "changed": ["baseline_missing"], "sha256": None, "mode": "legacy-wrapper"}
    current = gradle_wrapper_snapshot(work)
    changed = [relative for relative in GRADLE_WRAPPER_FILES if current[relative] != expected.get(relative)]
    return {"status": "PASS" if not changed else "FAIL", "changed": changed, "sha256": current, "mode": "legacy-wrapper"}
def suite_command(name):
    if name in TESTS:
        return list(TESTS[name])
    if name not in TRACKS:
        raise ValueError("unknown test suite: " + name)
    command = ["testDebugUnitTest", "--no-daemon"]
    for test_class in TRACKS[name]["tests"]:
        command.extend(["--tests", test_class])
    return command

def git(*args, cwd=None, ok=True):
    r = subprocess.run(["git", *args], cwd=cwd or ROOT, text=True, capture_output=True)
    if ok and r.returncode: raise RuntimeError(r.stderr.strip() or r.stdout.strip() or "git failed")
    return r
def head(cwd=None): return git("rev-parse", "HEAD", cwd=cwd).stdout.strip()
def snapshot(cwd=None):
    cwd = cwd or ROOT; result = {}
    if LOCK.is_file():
        for line in LOCK.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^[0-9a-f]{40}\s+(.+)$", line.strip())
            if m:
                p = cwd / m.group(1); result[m.group(1)] = digest(p) if p.is_file() else "MISSING"
    p = cwd / ".github/interface-lock.sha"
    result[".github/interface-lock.sha"] = digest(p) if p.is_file() else "MISSING"
    return result
def task(id):
    if not ID.fullmatch(id): raise ValueError("invalid task id")
    home = TASKS / id; f = home / "task.json"
    if not f.is_file(): raise FileNotFoundError(id)
    m = json.loads(f.read_text(encoding="utf-8")); work = Path(m["worktree"]).resolve()
    if not work.is_dir() or work == ROOT or ROOT in work.parents: raise ValueError("worktree is not isolated")
    return work, m
def fingerprint(work, base):
    names = set(git("diff", "--name-only", base, cwd=work).stdout.splitlines())
    names.update(x[3:] for x in git("status", "--porcelain", "--untracked-files=all", cwd=work).stdout.splitlines() if x.startswith("?? "))
    items = [(n, digest(work/n) if (work/n).is_file() else "DELETED") for n in sorted(names)]
    return hashlib.sha256(json.dumps(items, separators=(",", ":")).encode()).hexdigest()

def repo_map(_):
    src = ROOT / "android/app/src/main"; roots = [ROOT/"android/app/src/test", ROOT/"android/app/src/androidTest"]
    tests = sorted(p for r in roots if r.exists() for p in r.rglob("*.kt"))
    test_refs = []
    for test_path in tests:
        test_text = test_path.read_text(encoding="utf-8", errors="replace")
        referenced = set(re.findall(r"\b[A-Za-z_][A-Za-z0-9_]*\b", test_text))
        test_refs.append((test_path, referenced))
    rows=[]
    for p in sorted(src.rglob("*.kt")) if src.exists() else []:
        source_text = p.read_text(encoding="utf-8", errors="replace")
        declared = [{"name":m.group(1),"line":source_text[:m.start()].count("\n")+1}
                    for m in SYMBOL.finditer(source_text)]
        symbol_names = {item["name"] for item in declared}
        related_tests = [
            test_path.relative_to(ROOT).as_posix()
            for test_path, referenced in test_refs
            if symbol_names.intersection(referenced)
        ]
        rows.append({"file":p.relative_to(ROOT).as_posix(), "sha256":digest(p),
          "symbols":declared,
          "imports":sorted(set(re.findall(r"^\s*import\s+([\w.*]+)",source_text,re.M))),
          "test_candidates":related_tests})
    DOCS.mkdir(parents=True,exist_ok=True); data={"generated_at_utc":now(),"branch":git("branch","--show-current").stdout.strip(),"head":head(),"scope":"static index only","sources":rows,"test_files":[p.relative_to(ROOT).as_posix() for p in tests]}
    (DOCS/"repository-map.json").write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    (DOCS/"repository-map.md").write_text("# Kotlin source map\n\n"+"\n".join(f"## {x['file']}\nSymbols: {', '.join(s['name'] for s in x['symbols'])}\nTests: {', '.join(x['test_candidates']) or 'not matched'}" for x in rows),encoding="utf-8")
    print(f"sources={len(rows)} tests={len(tests)} map={DOCS/'repository-map.md'}"); return 0

def track_map(a):
    definition = TRACKS[a.scope]
    sources = []
    for relative in definition["sources"]:
        path = ROOT / relative
        sources.append({"path": relative, "exists": path.is_file(),
                        "sha256": digest(path) if path.is_file() else None})
    found, missing = [], []
    for class_name in definition["tests"]:
        path = ROOT / "android/app/src/test/java" / Path(*class_name.split(".")).with_suffix(".kt")
        item = {"class": class_name, "exists": path.is_file()}
        (found if path.is_file() else missing).append(item)
    report = {"generated_at_utc": now(), "head": head(), "scope": a.scope,
              "title": definition["title"], "sources": sources, "tests": found,
              "missing_tests": missing, "metrics": definition["metrics"],
              "note": "Static map only; it does not prove execution or cognitive competence."}
    target = DOCS / "maps"
    target.mkdir(parents=True, exist_ok=True)
    (target / (a.scope + "-map.json")).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# " + definition["title"], "", "Generated UTC: " + report["generated_at_utc"], "HEAD: " + report["head"], "", "## Sources"]
    lines += [("- OK: " if x["exists"] else "- MISSING: ") + x["path"] for x in sources]
    lines += ["", "## Tests"]
    lines += [("- OK: " if x["exists"] else "- MISSING: ") + x["class"] for x in found]
    lines += ["- MISSING: " + x["class"] for x in missing]
    lines += ["", "## Metrics"] + ["- " + x for x in definition["metrics"]]
    lines += ["", report["note"]]
    (target / (a.scope + "-map.md")).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({"scope": a.scope, "sources_present": sum(x["exists"] for x in sources),
                      "sources_total": len(sources), "tests_present": len(found),
                      "tests_total": len(definition["tests"]), "missing_tests": missing}, ensure_ascii=False, indent=2))
    return 0 if not missing and all(x["exists"] for x in sources) else 2


def task_create(a):
    if not ID.fullmatch(a.id): raise ValueError("invalid task id")
    base=git("rev-parse","--verify",f"{a.base}^{{commit}}").stdout.strip(); home=TASKS/a.id; work=home/"checkout"; branch=f"kellvyn-rnd/{a.id}"
    gradle_execution = gradle_execution_baseline(base)
    wrapper_hashes = gradle_execution.get("files_sha256") if gradle_execution.get("mode") == "wrapper" else None
    if home.exists() or git("show-ref","--verify",f"refs/heads/{branch}",ok=False).returncode==0: raise FileExistsError(a.id)
    TASKS.mkdir(parents=True,exist_ok=True); home.mkdir(parents=True)
    try: git("worktree","add","-b",branch,str(work),base)
    except Exception: home.rmdir(); raise
    record={"id":a.id,"title":a.title,"objective":a.objective,"scope":a.scope,"created_at_utc":now(),"base":base,"branch":branch,"worktree":str(work),"protected":snapshot(),"gradle_execution":gradle_execution,"gradle_wrapper_sha256":wrapper_hashes,"required_tests":a.require_test or [a.scope],"evidence":[]}
    (home/"task.json").write_text(json.dumps(record,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    (home/"TASK.md").write_text(f"# {a.title}\n\n{a.objective}\n\nBase: {base}\nBranch: {branch}\n",encoding="utf-8")
    print(f"WORKTREE={work}\nBRANCH={branch}\nBASE={base}"); return 0

def task_test(a):
    work,m=task(a.id)
    before = gradle_wrapper_integrity(work, m)
    if before["status"] != "PASS":
        raise RuntimeError("Gradle wrapper integrity check failed: " + ", ".join(before["changed"]))
    execution = m.get("gradle_execution", {})
    mode = execution.get("mode", "wrapper" if m.get("gradle_wrapper_sha256") else None)
    if mode == "wrapper":
        wrapper=work/"android"/("gradlew.bat" if os.name=="nt" else "gradlew")
        if not wrapper.is_file(): raise FileNotFoundError(wrapper)
        if os.name!="nt" and not os.access(wrapper,os.X_OK): raise PermissionError("gradlew not executable")
        executable = str(wrapper)
    elif mode == "system":
        executable = execution.get("executable")
        if not executable: raise RuntimeError("system Gradle executable is missing from the task manifest")
    else:
        raise RuntimeError("task has no trusted Gradle execution contract")
    start=time.monotonic(); stamp=datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"); evidence=TASKS/a.id/"evidence"; evidence.mkdir(exist_ok=True)
    command=[executable,*suite_command(a.suite)]
    try:
        r=subprocess.run(command,cwd=work/"android",text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=a.timeout)
        code=r.returncode; output=r.stdout; status="PASSED" if code==0 else "FAILED"
    except subprocess.TimeoutExpired as e:
        code=124; output=(e.stdout.decode(errors="replace") if isinstance(e.stdout,bytes) else e.stdout or "")+f"\nTIMEOUT {a.timeout}s"; status="TIMEOUT"
    except OSError as e: code=127; output=str(e); status="NOT_RUN"
    after = gradle_wrapper_integrity(work, m)
    if after["status"] != "PASS":
        code = 126
        status = "INTEGRITY_FAILURE"
        output = (output or "") + "\nGRADLE_WRAPPER_INTEGRITY_FAILURE: " + ", ".join(after["changed"])
    log=evidence/f"{a.suite}-{stamp}.log"; log.write_text(output or "(no output)\n",encoding="utf-8",errors="replace")
    run={"suite":a.suite,"status":status,"exit_code":code,"head":head(work),"fingerprint":fingerprint(work,m["base"]),"gradle_wrapper_integrity":after["status"],"gradle_execution_mode":after.get("mode"),"duration_seconds":round(time.monotonic()-start,2),"log":str(log),"log_sha256":digest(log)}
    m.setdefault("evidence",[]).append(run); (TASKS/a.id/"task.json").write_text(json.dumps(m,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(run,ensure_ascii=False,indent=2)); return 0 if code==0 else 2

def check_report(a):
    work,m=task(a.id); base=m["base"]; status=git("status","--porcelain","--untracked-files=all",cwd=work).stdout.splitlines()
    untracked=[x[3:] for x in status if x.startswith("?? ")]; changes=sorted(set(git("diff","--name-only",base,cwd=work).stdout.splitlines()+untracked))
    diff=git("diff","--check",base,cwd=work,ok=False); staged=git("diff","--cached","--check",cwd=work,ok=False)
    white=[]
    for n in untracked:
        p=work/n
        if p.is_file():
            try: white += [f"{n}:{i}" for i,line in enumerate(p.read_text(encoding="utf-8").splitlines(),1) if line.endswith((" ","\t"))]
            except UnicodeError: pass
    protected=snapshot(work); protected_bad=[n for n,h in m["protected"].items() if protected.get(n)!=h]
    wrapper = gradle_wrapper_integrity(work, m)
    fp=fingerprint(work,base); suites=a.require_test or m.get("required_tests",["unit"]); passed={}
    for e in m.get("evidence",[]):
        log=Path(e.get("log", ""))
        passed[e.get("suite")]=(e.get("exit_code")==0 and e.get("head")==head(work)
          and e.get("fingerprint")==fp and log.is_file() and digest(log)==e.get("log_sha256"))
    missing=[x for x in suites if not passed.get(x,False)]; diff_ok=diff.returncode==0 and staged.returncode==0 and not white
    report={"checked_at_utc":now(),"task":a.id,"head":head(work),"changed_files":changes,"diff_check":"PASS" if diff_ok else "FAIL","diff_output":(diff.stdout+diff.stderr+staged.stdout+staged.stderr+"\n".join(white))[:3000],"protected":"PASS" if not protected_bad else "FAIL","protected_changes":protected_bad,"gradle_wrapper":wrapper["status"],"gradle_wrapper_changes":wrapper["changed"],"gradle_execution_mode":wrapper.get("mode"),"missing_or_stale_tests":missing,"gate":"PASS" if diff_ok and not protected_bad and wrapper["status"] == "PASS" and not missing else "BLOCKED"}
    return report

def task_check(a):
    report=check_report(a)
    ev=TASKS/a.id/"evidence"; ev.mkdir(exist_ok=True); (ev/f"gate-{int(time.time())}.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(report,ensure_ascii=False,indent=2)); return 0 if report["gate"]=="PASS" else 2

def research_add(a):
    if not a.title.strip() or not a.source.strip() or not a.finding.strip(): raise ValueError("title/source/finding required")
    DOCS.mkdir(parents=True,exist_ok=True); p=DOCS/"research-ledger.jsonl"; row={"recorded_at_utc":now(),"topic":a.topic,"title":a.title,"source":a.source,"finding":a.finding,"decision":a.decision,"confidence":a.confidence}
    with p.open("a",encoding="utf-8") as f: f.write(json.dumps(row,ensure_ascii=False)+"\n")
    print(f"RECORDED={row['recorded_at_utc']}"); return 0

def experiment_create(a):
    p=DOCS/"experiments"/a.id
    if not ID.fullmatch(a.id): raise ValueError("invalid experiment id")
    p.mkdir(parents=True,exist_ok=False); row={"id":a.id,"title":a.title,"hypothesis":a.hypothesis,"scope":a.scope,"created_at_utc":now(),"baseline_commit":head(),"state":"PLANNED","controls":[],"metrics":[],"runs":[],"acceptance_criteria":[]}
    (p/"experiment.json").write_text(json.dumps(row,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    (p/"README.md").write_text(f"# {a.title}\n\n## Hypothesis\n{a.hypothesis}\n\nRecord baseline/candidate, controls, metrics, repetitions, acceptance criteria and evidence before drawing conclusions.\n",encoding="utf-8"); print(f"CREATED={p}"); return 0

def experiment_record(a):
    p=DOCS/"experiments"/a.id/"experiment.json"
    if not p.is_file(): raise FileNotFoundError(a.id)
    row=json.loads(p.read_text(encoding="utf-8")); row["runs"].append({"recorded_at_utc":now(),"variant":a.variant,"metric":a.metric,"value":a.value,"unit":a.unit,"evidence":a.evidence,"notes":a.notes,"commit":head()}); row["state"]="IN_PROGRESS"
    p.write_text(json.dumps(row,ensure_ascii=False,indent=2)+"\n",encoding="utf-8"); print("RUN_RECORDED"); return 0

def main():
    p=argparse.ArgumentParser(description="Kellvyn R&D Studio"); s=p.add_subparsers(dest="cmd",required=True)
    x=s.add_parser("map"); x.set_defaults(fn=repo_map)
    x=s.add_parser("track-map"); x.add_argument("--scope",choices=sorted(TRACKS),required=True); x.set_defaults(fn=track_map)
    t=s.add_parser("task"); q=t.add_subparsers(dest="action",required=True)
    c=q.add_parser("create"); c.add_argument("--id",required=True); c.add_argument("--title",required=True); c.add_argument("--objective",required=True); c.add_argument("--scope",choices=sorted(TRACKS),default="integration"); c.add_argument("--base",default="HEAD"); c.add_argument("--require-test",action="append",choices=sorted(SUITE_NAMES)); c.set_defaults(fn=task_create)
    for action,fn in (("test",task_test),("check",task_check)):
        z=q.add_parser(action); z.add_argument("--id",required=True); z.set_defaults(fn=fn)
        if action=="test": z.add_argument("--suite",required=True,choices=sorted(SUITE_NAMES)); z.add_argument("--timeout",type=int,default=1800)
        else: z.add_argument("--require-test",action="append",choices=sorted(SUITE_NAMES))
    r=s.add_parser("research"); r.add_argument("--topic",default="general"); r.add_argument("--title",required=True); r.add_argument("--source",required=True); r.add_argument("--finding",required=True); r.add_argument("--decision",default="pending"); r.add_argument("--confidence",choices=["low","medium","high"],default="medium"); r.set_defaults(fn=research_add)
    e=s.add_parser("experiment"); v=e.add_subparsers(dest="action",required=True); n=v.add_parser("create"); n.add_argument("--id",required=True); n.add_argument("--title",required=True); n.add_argument("--hypothesis",required=True); n.add_argument("--scope",choices=sorted(TRACKS),default="integration"); n.set_defaults(fn=experiment_create)
    n=v.add_parser("record"); n.add_argument("--id",required=True); n.add_argument("--variant",required=True,choices=["baseline","candidate"]); n.add_argument("--metric",required=True); n.add_argument("--value",type=float,required=True); n.add_argument("--unit",default="unspecified"); n.add_argument("--evidence",required=True); n.add_argument("--notes",default=""); n.set_defaults(fn=experiment_record)
    a=p.parse_args()
    if getattr(a,"timeout",1800)<1 or getattr(a,"timeout",1800)>3600: raise ValueError("timeout must be 1..3600s")
    try: return a.fn(a)
    except (OSError,ValueError,RuntimeError,KeyError,json.JSONDecodeError) as ex: print(f"STUDIO_ERROR: {ex}",file=sys.stderr); return 2
if __name__=="__main__": raise SystemExit(main())
