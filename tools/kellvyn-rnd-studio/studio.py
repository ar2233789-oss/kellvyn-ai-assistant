#!/usr/bin/env python3
"""Kellvyn R&D Studio: isolated source study, experiments and evidence gates."""
from __future__ import annotations
import argparse, hashlib, json, os, re, subprocess, sys, time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs/kellvyn/rnd-studio"
TASKS = ROOT.parent / f".{ROOT.name}-rnd-studio" / "tasks"
LOCK = ROOT / ".github/interface-lock.sha"
TESTS = {
    "unit": ["testDebugUnitTest", "--no-daemon"],
    "assemble": ["assembleDebug", "--no-daemon"],
    "instrumented": ["connectedDebugAndroidTest", "--no-daemon"],
}
TRACKS = {
 "brain": {
  "title": "Brain R&D",
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
  "title": "Brain + Agent Integration",
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
   "com.kellvyn.assistant.KellvynSpecialistToolPolicyTest"
  ],
  "metrics": ["valid_decision_rate","inference_to_agent_success","verified_tool_success","verified_delegation_success","untrusted_result_propagation","runtime_connection_status"]
 }
}
SUITE_NAMES = set(TESTS) | set(TRACKS)
ID = re.compile(r"^[a-z0-9][a-z0-9-]{1,39}$")
SYMBOL = re.compile(r"^\s*(?:(?:public|internal|private|protected|open|abstract|data|sealed|enum|fun|suspend|inline|override|const)\s+)*(?:class|interface|object|typealias|fun)\s+([A-Za-z_]\w*)", re.M)

def now(): return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
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
    tests = sorted(p for r in roots if r.exists() for p in r.rglob("*.kt")); names = {p.stem for p in tests}; rows=[]
    for p in sorted(src.rglob("*.kt")) if src.exists() else []:
        text = p.read_text(encoding="utf-8", errors="replace"); stem=p.stem
        rows.append({"file":p.relative_to(ROOT).as_posix(), "sha256":digest(p),
          "symbols":[{"name":m.group(1),"line":text[:m.start()].count("\n")+1} for m in SYMBOL.finditer(text)],
          "imports":sorted(set(re.findall(r"^\s*import\s+([\w.*]+)",text,re.M))),
          "test_candidates":sorted(n for n in names if n in {stem+"Test",stem+"Tests",stem+"InstrumentedTest"})})
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
    if home.exists() or git("show-ref","--verify",f"refs/heads/{branch}",ok=False).returncode==0: raise FileExistsError(a.id)
    TASKS.mkdir(parents=True,exist_ok=True); home.mkdir(parents=True)
    try: git("worktree","add","-b",branch,str(work),base)
    except Exception: home.rmdir(); raise
    record={"id":a.id,"title":a.title,"objective":a.objective,"scope":a.scope,"created_at_utc":now(),"base":base,"branch":branch,"worktree":str(work),"protected":snapshot(),"required_tests":a.require_test or [a.scope],"evidence":[]}
    (home/"task.json").write_text(json.dumps(record,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    (home/"TASK.md").write_text(f"# {a.title}\n\n{a.objective}\n\nBase: {base}\nBranch: {branch}\n",encoding="utf-8")
    print(f"WORKTREE={work}\nBRANCH={branch}\nBASE={base}"); return 0

def task_test(a):
    work,m=task(a.id); wrapper=work/"android"/("gradlew.bat" if os.name=="nt" else "gradlew")
    if not wrapper.is_file(): raise FileNotFoundError(wrapper)
    if os.name!="nt" and not os.access(wrapper,os.X_OK): raise PermissionError("gradlew not executable")
    start=time.monotonic(); stamp=datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"); evidence=TASKS/a.id/"evidence"; evidence.mkdir(exist_ok=True)
    command=[str(wrapper),*suite_command(a.suite)]
    try:
        r=subprocess.run(command,cwd=work/"android",text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=a.timeout)
        code=r.returncode; output=r.stdout; status="PASSED" if code==0 else "FAILED"
    except subprocess.TimeoutExpired as e:
        code=124; output=(e.stdout.decode(errors="replace") if isinstance(e.stdout,bytes) else e.stdout or "")+f"\nTIMEOUT {a.timeout}s"; status="TIMEOUT"
    except OSError as e: code=127; output=str(e); status="NOT_RUN"
    log=evidence/f"{a.suite}-{stamp}.log"; log.write_text(output or "(no output)\n",encoding="utf-8",errors="replace")
    run={"suite":a.suite,"status":status,"exit_code":code,"head":head(work),"fingerprint":fingerprint(work,m["base"]),"duration_seconds":round(time.monotonic()-start,2),"log":str(log),"log_sha256":digest(log)}
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
    fp=fingerprint(work,base); suites=a.require_test or m.get("required_tests",["unit"]); passed={}
    for e in m.get("evidence",[]):
        log=Path(e.get("log", ""))
        passed[e.get("suite")]=(e.get("exit_code")==0 and e.get("head")==head(work)
          and e.get("fingerprint")==fp and log.is_file() and digest(log)==e.get("log_sha256"))
    missing=[x for x in suites if not passed.get(x,False)]; diff_ok=diff.returncode==0 and staged.returncode==0 and not white
    report={"checked_at_utc":now(),"task":a.id,"head":head(work),"changed_files":changes,"diff_check":"PASS" if diff_ok else "FAIL","diff_output":(diff.stdout+diff.stderr+staged.stdout+staged.stderr+"\n".join(white))[:3000],"protected":"PASS" if not protected_bad else "FAIL","protected_changes":protected_bad,"missing_or_stale_tests":missing,"gate":"PASS" if diff_ok and not protected_bad and not missing else "BLOCKED"}
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
