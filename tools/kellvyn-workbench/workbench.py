#!/usr/bin/env python3
"""Kellvyn Workbench: the assistant's reproducible engineering and research bench.

This tool coordinates audits, tamper-evident session records, isolated R&D tasks,
test execution, diagnostics, visual comparisons and Blender production runs.
It never claims a test passed unless the process actually exited successfully.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time
import uuid
from threading import Thread
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    from zoneinfo import ZoneInfo
except ImportError:  # pragma: no cover - Python < 3.9 fallback
    ZoneInfo = None  # type: ignore[assignment]

ROOT = Path(__file__).resolve().parents[2]
HOME = ROOT / "artifacts" / "workbench"
RUNS = HOME / "runs"
AUDITS = HOME / "audits"
LEDGER = ROOT / "docs" / "kellvyn" / "workbench-ledger.jsonl"
STATE_JSON = ROOT / "docs" / "kellvyn" / "WORKBENCH_STATE.json"
STATE_MD = ROOT / "docs" / "kellvyn" / "WORKBENCH_STATE.md"
LOCK_FILE = ROOT / ".github" / "interface-lock.sha"
STUDIO = ROOT / "tools" / "kellvyn-rnd-studio" / "studio.py"
BLENDER_PIPELINE = Path(__file__).resolve().parent / "creative" / "blender_pipeline.py"
GENESIS = "0" * 64


def timestamp() -> tuple[str, str]:
    now = datetime.now(timezone.utc)
    utc = now.isoformat(timespec="milliseconds").replace("+00:00", "Z")
    if ZoneInfo:
        local = now.astimezone(ZoneInfo("America/Belem")).isoformat(timespec="seconds")
    else:  # pragma: no cover
        local = now.astimezone().isoformat(timespec="seconds")
    return utc, local


def git(*args: str, required: bool = False) -> str:
    try:
        return subprocess.check_output(
            ["git", *args], cwd=ROOT, text=True, stderr=subprocess.STDOUT,
            timeout=15,
        ).strip()
    except (OSError, subprocess.SubprocessError):
        if required:
            raise
        return "UNKNOWN"


def repo_identity() -> dict[str, str]:
    return {
        "branch": git("branch", "--show-current"),
        "head": git("rev-parse", "HEAD"),
        "short_head": git("rev-parse", "--short", "HEAD"),
        "dirty": "true" if git("status", "--porcelain") else "false",
    }


def canonical_json(value: dict[str, Any]) -> bytes:
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def verify_ledger(path: Path | None = None) -> dict[str, Any]:
    path = path or LEDGER
    if not path.exists():
        return {"valid": True, "records": 0, "last_hash": GENESIS, "message": "ledger not created yet"}
    previous = GENESIS
    count = 0
    with path.open("r", encoding="utf-8") as stream:
        for line_no, raw in enumerate(stream, 1):
            if not raw.strip():
                continue
            try:
                row = json.loads(raw)
            except json.JSONDecodeError as exc:
                return {"valid": False, "records": count, "line": line_no, "message": f"invalid JSON: {exc}"}
            stored_hash = row.pop("event_hash", None)
            if row.get("sequence") != count + 1:
                return {"valid": False, "records": count, "line": line_no, "message": "sequence discontinuity"}
            if row.get("previous_hash") != previous:
                return {"valid": False, "records": count, "line": line_no, "message": "previous hash mismatch"}
            computed = hashlib.sha256(canonical_json(row)).hexdigest()
            if stored_hash != computed:
                return {"valid": False, "records": count, "line": line_no, "message": "event hash mismatch"}
            previous = computed
            count += 1
    return {"valid": True, "records": count, "last_hash": previous, "message": "hash chain verified"}


def append_event(kind: str, status: str, summary: str, *, session_id: str = "", paths: list[str] | None = None,
                 evidence: list[str] | None = None, metadata: dict[str, Any] | None = None) -> dict[str, Any]:
    LEDGER.parent.mkdir(parents=True, exist_ok=True)
    audit = verify_ledger()
    if not audit["valid"]:
        raise RuntimeError(f"workbench ledger integrity failure at line {audit.get('line')}: {audit['message']}")
    utc, local = timestamp()
    prior = audit["last_hash"]
    body: dict[str, Any] = {
        "schema": "kellvyn-workbench-event/v1",
        "sequence": audit["records"] + 1,
        "recorded_at_utc": utc,
        "recorded_at_local": local,
        "timezone": "America/Belem",
        "session_id": session_id or "unspecified",
        "kind": kind,
        "status": status,
        "summary": summary[:2000],
        "branch": repo_identity()["branch"],
        "head": repo_identity()["head"],
        "paths": sorted(set(paths or []))[:100],
        "evidence": (evidence or [])[:50],
        "metadata": metadata or {},
        "previous_hash": prior,
    }
    digest = hashlib.sha256(canonical_json(body)).hexdigest()
    record = dict(body, event_hash=digest)
    line = json.dumps(record, sort_keys=True, ensure_ascii=False, separators=(",", ":")) + "\n"
    # Single append write; flock prevents concurrent sessions from reusing one sequence.
    with LEDGER.open("a", encoding="utf-8") as stream:
        try:
            import fcntl
            fcntl.flock(stream.fileno(), fcntl.LOCK_EX)
            # Recheck after acquiring the process lock.
            current = verify_ledger()
            if not current["valid"] or current["records"] + 1 != body["sequence"]:
                raise RuntimeError("ledger changed concurrently; retry after re-reading it")
            stream.write(line)
            stream.flush()
            os.fsync(stream.fileno())
            fcntl.flock(stream.fileno(), fcntl.LOCK_UN)
        except ImportError:  # Windows fallback: append mode plus one line write.
            stream.write(line)
            stream.flush()
            os.fsync(stream.fileno())
    return record


def within_repo(path: Path) -> Path:
    resolved = path.expanduser().resolve()
    if resolved != ROOT and ROOT not in resolved.parents:
        raise ValueError(f"path must stay inside repository/worktree: {path}")
    return resolved


def command_version(binary: str, args: list[str]) -> str:
    exe = shutil.which(binary)
    if not exe:
        return "NOT_INSTALLED"
    try:
        result = subprocess.run([exe, *args], text=True, capture_output=True, timeout=8, check=False)
        output = (result.stdout + "\n" + result.stderr).strip().splitlines()
        return output[0][:180] if output else f"FOUND (exit={result.returncode})"
    except Exception as exc:
        return f"FOUND_BUT_VERSION_FAILED: {type(exc).__name__}"


def doctor() -> dict[str, Any]:
    tools = {
        "git": ("git", ["--version"]),
        "python": (sys.executable, ["--version"]),
        "java": ("java", ["-version"]),
        "gradle": ("gradle", ["--version"]),
        "adb": ("adb", ["version"]),
        "blender": ("blender", ["--version"]),
        "node": ("node", ["--version"]),
        "npm": ("npm", ["--version"]),
        "ffmpeg": ("ffmpeg", ["-version"]),
    }
    found = {name: command_version(binary, args) for name, (binary, args) in tools.items()}
    optional = {}
    for module in ("PIL", "playwright", "pytest"):
        try:
            __import__(module)
            optional[module] = "AVAILABLE"
        except Exception:
            optional[module] = "NOT_INSTALLED"
    return {
        "generated_at_utc": timestamp()[0],
        "platform": platform.platform(),
        "architecture": platform.machine(),
        "python": platform.python_version(),
        "tools": found,
        "optional_modules": optional,
        "repository": repo_identity(),
        "notes": [
            "FOUND means the executable exists; it does not prove that a project build succeeds.",
            "ADB device access is checked separately by android-smoke or debug --adb.",
            "Blender is a host-side creative tool, not a dependency to embed in the Android APK.",
        ],
    }


def audit() -> dict[str, Any]:
    main = ROOT / "android/app/src/main"
    test_roots = [ROOT / "android/app/src/test", ROOT / "android/app/src/androidTest"]
    src = sorted(str(p.relative_to(ROOT)) for p in main.rglob("*.kt")) if main.exists() else []
    tests = sorted(str(p.relative_to(ROOT)) for r in test_roots if r.exists() for p in r.rglob("*.kt"))
    lock = []
    if LOCK_FILE.exists():
        for line in LOCK_FILE.read_text(encoding="utf-8").splitlines():
            match = re.match(r"^([0-9a-f]{40})\s+(.+)$", line.strip())
            if not match:
                continue
            expected, rel = match.groups()
            target = ROOT / rel
            actual = git("hash-object", rel) if target.is_file() else "MISSING"
            lock.append({"path": rel, "expected": expected, "actual": actual, "status": "MATCH" if actual == expected else "MISMATCH"})
    report = {
        "schema": "kellvyn-workbench-audit/v1",
        "generated_at_utc": timestamp()[0],
        "repository": repo_identity(),
        "source_kotlin_files": len(src),
        "test_kotlin_files": len(tests),
        "source_paths": src,
        "test_paths": tests,
        "interface_lock": lock,
        "interface_lock_mismatches": sum(item["status"] != "MATCH" for item in lock),
        "toolchain": doctor()["tools"],
        "execution_status": "NOT_RUN_BY_STATIC_AUDIT",
        "limits": ["Static inventory is not compilation.", "A present test file is not a test execution.", "An AVD run is not a physical Poco F6 validation."],
    }
    AUDITS.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    json_path = AUDITS / f"audit-{stamp}Z.json"
    md_path = AUDITS / f"audit-{stamp}Z.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    md_path.write_text(render_audit(report), encoding="utf-8")
    append_event("AUDIT", "RECORDED", "Repository inventory and protected-file audit generated.", paths=[str(json_path.relative_to(ROOT)), str(md_path.relative_to(ROOT))], evidence=[report["repository"]["head"]])
    report["artifacts"] = [str(json_path.relative_to(ROOT)), str(md_path.relative_to(ROOT))]
    return report


def render_audit(report: dict[str, Any]) -> str:
    r = report["repository"]
    lines = ["# Workbench audit", "", f"- UTC: {report['generated_at_utc']}", f"- Branch: `{r['branch']}`", f"- HEAD: `{r['head']}`", "", "## Inventory", "", f"- Kotlin sources: {report['source_kotlin_files']}", f"- Kotlin test files: {report['test_kotlin_files']}", f"- Interface-lock mismatches: {report['interface_lock_mismatches']}", "", "## Protected files", "", "| Path | Status | Expected | Actual |", "|---|---|---|---|"]
    lines += [f"| `{x['path']}` | **{x['status']}** | `{x['expected']}` | `{x['actual']}` |" for x in report["interface_lock"]]
    lines += ["", "## Execution tools", "", "| Tool | Detection |", "|---|---|"]
    lines += [f"| {name} | `{value}` |" for name, value in report["toolchain"].items()]
    lines += ["", "**Execution status:** `NOT_RUN_BY_STATIC_AUDIT`. No compilation or runtime success is implied.", ""]
    return "\n".join(lines)


def write_checkpoint(summary: str, next_step: str, *, done: list[str], blocked: list[str], session_id: str) -> None:
    state = {
        "schema": "kellvyn-workbench-state/v1",
        "updated_at_utc": timestamp()[0],
        "updated_at_local": timestamp()[1],
        "session_id": session_id,
        "repository": repo_identity(),
        "summary": summary,
        "completed": done,
        "blocked_or_unverified": blocked,
        "next_step": next_step,
        "ledger": str(LEDGER.relative_to(ROOT)),
        "ledger_integrity": verify_ledger(),
    }
    STATE_JSON.parent.mkdir(parents=True, exist_ok=True)
    temp = STATE_JSON.with_suffix(".json.tmp")
    temp.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temp.replace(STATE_JSON)
    lines = ["# Workbench — checkpoint de continuidade", "", f"- Atualizado UTC: {state['updated_at_utc']}", f"- Horário local: {state['updated_at_local']}", f"- Branch: `{state['repository']['branch']}`", f"- HEAD: `{state['repository']['head']}`", f"- Sessão: `{session_id}`", "", "## Resumo", "", summary, "", "## Concluído", ""]
    lines += [f"- {x}" for x in done] or ["- Nenhum item marcado como concluído."]
    lines += ["", "## Bloqueado / não verificado", ""]
    lines += [f"- {x}" for x in blocked] or ["- Nenhum bloqueio registrado."]
    lines += ["", "## Próximo passo", "", next_step, "", f"Ledger integrity: **{'VALID' if state['ledger_integrity']['valid'] else 'INVALID'}** ({state['ledger_integrity']['records']} records).", ""]
    STATE_MD.write_text("\n".join(lines), encoding="utf-8")
    append_event("CHECKPOINT", "RECORDED", summary, session_id=session_id, paths=[str(STATE_JSON.relative_to(ROOT)), str(STATE_MD.relative_to(ROOT))], evidence=[state["repository"]["head"]], metadata={"next_step": next_step})


def run_capture(name: str, command: list[str], *, cwd: Path = ROOT, timeout_s: int = 1800, session_id: str = "", env: dict[str, str] | None = None) -> dict[str, Any]:
    if timeout_s < 1 or timeout_s > 7200:
        raise ValueError("timeout must be between 1 and 7200 seconds")
    run_id = f"{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}-{uuid.uuid4().hex[:8]}"
    out = RUNS / run_id
    out.mkdir(parents=True, exist_ok=False)
    stdout_path, stderr_path = out / "stdout.log", out / "stderr.log"
    started_utc = timestamp()[0]
    started = time.monotonic()
    status = "FAILED"
    exit_code = None
    error = None
    process = None

    def pump(pipe, destination: Path, echo_progress: bool = False):
        with destination.open("w", encoding="utf-8", errors="replace") as target:
            for line in iter(pipe.readline, ""):
                target.write(line)
                target.flush()
                if echo_progress and line.startswith("WORKBENCH_PROGRESS "):
                    # Creative tools publish structured stage events; stream them live.
                    print(line.rstrip(), flush=True)
        pipe.close()

    try:
        process = subprocess.Popen(command, cwd=cwd, text=True, encoding="utf-8", errors="replace",
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=1, env={**os.environ, **(env or {})})
        assert process.stdout is not None and process.stderr is not None
        stdout_thread = Thread(target=pump, args=(process.stdout, stdout_path, True), daemon=True)
        stderr_thread = Thread(target=pump, args=(process.stderr, stderr_path, False), daemon=True)
        stdout_thread.start(); stderr_thread.start()
        try:
            exit_code = process.wait(timeout=timeout_s)
        except subprocess.TimeoutExpired:
            process.kill()
            exit_code = process.wait()
            status, error = "TIMED_OUT", f"timeout after {timeout_s}s; process terminated"
        stdout_thread.join(timeout=10); stderr_thread.join(timeout=10)
        if status != "TIMED_OUT":
            status = "PASSED" if exit_code == 0 else "FAILED"
    except FileNotFoundError as exc:
        status, error = "BLOCKED", f"executable missing: {exc.filename}"
        stderr_path.write_text(error + "\n", encoding="utf-8")
        stdout_path.touch(exist_ok=True)
    except Exception as exc:
        status, error = "FAILED", f"{type(exc).__name__}: {exc}"
        stderr_path.write_text(error + "\n", encoding="utf-8")
        stdout_path.touch(exist_ok=True)
        if process and process.poll() is None:
            process.kill(); process.wait()
    elapsed = round(time.monotonic() - started, 3)
    meta = {
        "schema": "kellvyn-workbench-run/v1", "run_id": run_id, "name": name,
        "status": status, "exit_code": exit_code, "error": error,
        "started_at_utc": started_utc, "duration_seconds": elapsed,
        "command": command, "cwd": str(cwd), "repository": repo_identity(),
        "stdout": str(stdout_path.relative_to(ROOT)), "stderr": str(stderr_path.relative_to(ROOT)),
    }
    (out / "run.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    append_event("EXECUTION", status, f"{name}: {status}; duration={elapsed}s", session_id=session_id,
                 paths=[meta["stdout"], meta["stderr"], str((out / "run.json").relative_to(ROOT))],
                 evidence=[f"exit_code={exit_code}", f"run_id={run_id}"])
    return meta


def debug_log(log_path: Path) -> dict[str, Any]:
    log_path = within_repo(log_path)
    if not log_path.is_file():
        raise FileNotFoundError(log_path)
    raw = log_path.read_text(encoding="utf-8", errors="replace")[-2_000_000:]
    patterns = [
        ("KOTLIN_COMPILER", re.compile(r"(?m)^e:\s+(.+?):\s*\((\d+),\s*(\d+)\):\s*(.+)$")),
        ("JAVA_COMPILER", re.compile(r"(?m)^(.+?\.java):(\d+):\s*error:\s*(.+)$")),
        ("STACK_FRAME", re.compile(r"(?m)^\s*at\s+[\w.$<>]+\(([^():]+):(\d+)\)")),
        ("PYTHON_TRACEBACK", re.compile(r"(?m)^\s*File \"([^\"]+)\", line (\d+), in (.+)$")),
        ("ANR", re.compile(r"(?i)(Application Not Responding|ANR in [^\s]+|am_anr)")),
        ("FATAL", re.compile(r"(?i)(FATAL EXCEPTION|Fatal signal|SIGSEGV|OutOfMemoryError|StackOverflowError)")),
        ("EXCEPTION_SUMMARY", re.compile(r"(?m)^(?:Caused by:\s*)?([A-Za-z_$][\w.$]*(?:Exception|Error))(?::\s*(.*))?$")),
        ("GRADLE_FAILURE", re.compile(r"(?ms)^\* What went wrong:\s*\n(.*?)(?=^\* Try:|^\* Get more help|^BUILD (?:FAILED|SUCCESSFUL)|\Z)")),
    ]
    findings: list[dict[str, Any]] = []
    for kind, regex in patterns:
        for match in list(regex.finditer(raw))[-80:]:
            if kind == "KOTLIN_COMPILER":
                path, line, col, message = match.groups()
                findings.append({"kind": kind, "path": path, "line": int(line), "column": int(col), "message": message[:500]})
            elif kind == "JAVA_COMPILER":
                path, line, message = match.groups()
                findings.append({"kind": kind, "path": path, "line": int(line), "message": message[:500]})
            elif kind in ("STACK_FRAME", "PYTHON_TRACEBACK"):
                findings.append({"kind": kind, "path": match.group(1), "line": int(match.group(2)), "message": match.group(0).strip()[:500]})
            elif kind == "EXCEPTION_SUMMARY":
                findings.append({"kind": kind, "exception": match.group(1), "message": (match.group(2) or "").strip()[:500]})
            else:
                findings.append({"kind": kind, "message": match.group(1).strip()[:1000] if match.groups() else match.group(0).strip()[:1000]})
    # Preserve chronological appearance while de-duplicating exact findings.
    unique = []
    seen = set()
    for item in sorted(findings, key=lambda x: (x.get("path", ""), x.get("line", 0), x["kind"])):
        key = json.dumps(item, sort_keys=True)
        if key not in seen:
            seen.add(key)
            unique.append(item)
    likely = next((x for x in unique if x.get("path") and x.get("line")), None)
    return {"schema": "kellvyn-workbench-debug/v1", "generated_at_utc": timestamp()[0], "source_log": str(log_path.relative_to(ROOT)),
            "finding_count": len(unique), "primary_location": likely, "findings": unique,
            "note": "Automated triage only. Confirm root cause by reproducing the error and rerunning the relevant test."}


def cmd_doctor(_args: argparse.Namespace) -> int:
    result = doctor()
    print(json.dumps(result, ensure_ascii=False, indent=2))
    append_event("DOCTOR", "RECORDED", "Workbench toolchain inventory generated.", metadata={"tools": result["tools"], "optional_modules": result["optional_modules"]})
    return 0


def cmd_audit(_args: argparse.Namespace) -> int:
    report = audit()
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


def cmd_log(args: argparse.Namespace) -> int:
    event = append_event(args.kind.upper(), args.status.upper(), args.summary, session_id=args.session,
                         paths=args.path, evidence=args.evidence, metadata={"next_step": args.next_step})
    print(json.dumps({"sequence": event["sequence"], "event_hash": event["event_hash"], "recorded_at_utc": event["recorded_at_utc"]}, indent=2))
    return 0


def cmd_verify(_args: argparse.Namespace) -> int:
    result = verify_ledger()
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["valid"] else 2


def cmd_checkpoint(args: argparse.Namespace) -> int:
    write_checkpoint(args.summary, args.next, done=args.done, blocked=args.blocked, session_id=args.session)
    print(f"Checkpoint written: {STATE_MD.relative_to(ROOT)}")
    return 0


def cmd_session(args: argparse.Namespace) -> int:
    active_file = HOME / "active-session.json"
    active_file.parent.mkdir(parents=True, exist_ok=True)
    if args.action == "start":
        session_id = args.id or str(uuid.uuid4())
        if active_file.exists():
            old = json.loads(active_file.read_text(encoding="utf-8"))
            append_event("SESSION_RECOVERY", "REVIEW_REQUIRED", "A prior session remained open; preserving its record rather than overwriting it.",
                         session_id=old.get("session_id", "unknown"), evidence=[str(active_file.relative_to(ROOT))])
        record = {"session_id": session_id, "started_at_utc": timestamp()[0], "summary": args.summary, "status": "OPEN"}
        active_file.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        append_event("SESSION_START", "OPEN", args.summary, session_id=session_id, paths=[str(active_file.relative_to(ROOT))])
        print(session_id)
        return 0
    append_event("SESSION_END", args.status.upper(), args.summary, session_id=args.id)
    if active_file.exists():
        old = json.loads(active_file.read_text(encoding="utf-8"))
        if old.get("session_id") == args.id:
            old["status"] = args.status.upper(); old["ended_at_utc"] = timestamp()[0]
            active_file.write_text(json.dumps(old, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0


def cmd_run(args: argparse.Namespace) -> int:
    cwd = within_repo(ROOT / args.cwd)
    argv = list(args.argv)
    if argv and argv[0] == "--": argv = argv[1:]
    if not argv: raise ValueError("run requires an explicit executable and arguments after --")
    result = run_capture(args.name, argv, cwd=cwd, timeout_s=args.timeout, session_id=args.session)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "PASSED" else (3 if result["status"] == "BLOCKED" else 2)


def cmd_debug(args: argparse.Namespace) -> int:
    if args.adb:
        adb = shutil.which("adb")
        if not adb:
            print("BLOCKED: adb is not installed on this host.", file=sys.stderr)
            return 3
        devices = subprocess.run([adb, "devices"], text=True, capture_output=True, timeout=10, check=False)
        rows = [line.split()[0] for line in devices.stdout.splitlines()[1:] if len(line.split()) >= 2 and line.split()[1] == "device"]
        serial = args.serial or (rows[0] if rows else "")
        if not serial:
            print("BLOCKED: no authorized Android device/emulator is connected.", file=sys.stderr)
            return 3
        raw = subprocess.run([adb, "-s", serial, "logcat", "-d", "-t", "1600", "-v", "threadtime"], text=True, capture_output=True, timeout=30, check=False)
        if raw.returncode:
            print(raw.stderr[-3000:], file=sys.stderr)
            return 2
        adb_dir = RUNS / f"adb-debug-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}"
        adb_dir.mkdir(parents=True, exist_ok=True)
        log_file = adb_dir / "logcat.txt"
        # Keep raw logs as evidence, but redact common token-shaped values before persistence.
        log_text = re.sub(r"(?i)(token|authorization|bearer)(\s*[:=]\s*)[^\s,;]+", r"\1\2[REDACTED]", raw.stdout)
        crash = subprocess.run([adb, "-s", serial, "logcat", "-b", "crash", "-d", "-t", "300"], text=True, capture_output=True, timeout=20, check=False)
        anr = subprocess.run([adb, "-s", serial, "shell", "dumpsys", "activity", "lastanr"], text=True, capture_output=True, timeout=20, check=False)
        crash_text = re.sub(r"(?i)(token|authorization|bearer)(\s*[:=]\s*)[^\s,;]+", r"\1\2[REDACTED]", crash.stdout or crash.stderr)
        anr_text = re.sub(r"(?i)(token|authorization|bearer)(\s*[:=]\s*)[^\s,;]+", r"\1\2[REDACTED]", anr.stdout or anr.stderr)
        log_file.write_text(log_text[-2_000_000:] + "\n\n--- CRASH BUFFER ---\n" + crash_text[-250_000:] + "\n\n--- LAST ANR ---\n" + anr_text[-250_000:], encoding="utf-8")
        args.log = str(log_file)
    result = debug_log(Path(args.log) if Path(args.log).is_absolute() else ROOT / args.log)
    out = Path(args.output) if args.output else RUNS / f"debug-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}.json"
    if not out.is_absolute(): out = ROOT / out
    out = within_repo(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    append_event("DEBUG_TRIAGE", "RECORDED", f"Parsed {result['finding_count']} diagnostic findings.", paths=[result["source_log"], str(out.relative_to(ROOT))])
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


def cmd_test(args: argparse.Namespace) -> int:
    py = sys.executable
    commands = {
        "workbench": [py, "-m", "unittest", "discover", "-s", "tools/kellvyn-workbench/tests", "-v"],
        "rnd-studio": [py, "-m", "unittest", "discover", "-s", "tools/kellvyn-rnd-studio", "-p", "test_studio.py", "-v"],
        "syntax": [py, "-m", "py_compile", "tools/kellvyn-workbench/workbench.py", "tools/kellvyn-workbench/creative/blender_pipeline.py", "tools/kellvyn-workbench/visual_diff.py"],
        "android-syntax": ["bash", "-n", "tools/kellvyn-workbench/android_smoke.sh"],
        "web-syntax": ["node", "--check", "tools/kellvyn-workbench/web_audit.mjs"],
    }
    command = commands[args.suite]
    result = run_capture(f"test:{args.suite}", command, timeout_s=args.timeout, session_id=args.session)
    stdout = (ROOT / result["stdout"]).read_text(encoding="utf-8", errors="replace")
    stderr = (ROOT / result["stderr"]).read_text(encoding="utf-8", errors="replace")
    print(stdout[-12000:])
    if stderr: print(stderr[-6000:], file=sys.stderr)
    print(f"\nRESULT={result['status']} RUN={result['run_id']} ELAPSED={result['duration_seconds']}s")
    return 0 if result["status"] == "PASSED" else 2


def cmd_worktree(args: argparse.Namespace) -> int:
    if not STUDIO.is_file():
        print("R&D Studio CLI is missing; cannot create isolated task.", file=sys.stderr)
        return 3
    cmd = [sys.executable, str(STUDIO), "task", "create", "--id", args.id, "--title", args.title,
           "--objective", args.objective, "--scope", args.scope, "--base", args.base]
    for suite in args.require_test: cmd += ["--require-test", suite]
    result = run_capture("isolated-worktree-create", cmd, timeout_s=120, session_id=args.session)
    print((ROOT / result["stdout"]).read_text(encoding="utf-8", errors="replace"))
    if result["status"] != "PASSED":
        print((ROOT / result["stderr"]).read_text(encoding="utf-8", errors="replace"), file=sys.stderr)
        return 2
    return 0


def cmd_studio_task(args: argparse.Namespace) -> int:
    if not STUDIO.is_file():
        print("BLOCKED: R&D Studio CLI is missing.", file=sys.stderr)
        return 3
    argv = [sys.executable, str(STUDIO), "task", args.action, "--id", args.id]
    if args.action == "test": argv += ["--suite", args.suite, "--timeout", str(args.timeout)]
    if args.action == "check":
        for suite in args.require_test: argv += ["--require-test", suite]
    result = run_capture(f"rnd-task-{args.action}", argv, timeout_s=args.timeout if args.action == "test" else 120, session_id=args.session)
    print((ROOT / result["stdout"]).read_text(encoding="utf-8", errors="replace")[-10000:])
    if result["status"] != "PASSED":
        print((ROOT / result["stderr"]).read_text(encoding="utf-8", errors="replace")[-6000:], file=sys.stderr)
        return 2
    return 0


def cmd_rnd(args: argparse.Namespace) -> int:
    argv = list(args.argv)
    if argv and argv[0] == "--": argv = argv[1:]
    if not argv: raise ValueError("rnd requires a R&D Studio subcommand")
    if not STUDIO.is_file(): raise FileNotFoundError(STUDIO)
    result = run_capture("rnd-studio:" + " ".join(argv[:4]), [sys.executable, str(STUDIO), *argv], timeout_s=args.timeout, session_id=args.session)
    print((ROOT / result["stdout"]).read_text(encoding="utf-8", errors="replace")[-12000:])
    if result["status"] != "PASSED":
        print((ROOT / result["stderr"]).read_text(encoding="utf-8", errors="replace")[-6000:], file=sys.stderr)
        return 2
    return 0


def cmd_android_smoke(args: argparse.Namespace) -> int:
    script = Path(__file__).resolve().parent / "android_smoke.sh"
    if not shutil.which("adb"):
        print("BLOCKED: adb is not installed.", file=sys.stderr); return 3
    apk = Path(args.apk); apk = apk if apk.is_absolute() else ROOT / apk
    out = Path(args.output); out = out if out.is_absolute() else ROOT / out
    apk = within_repo(apk); out = within_repo(out)
    if not apk.is_file(): raise FileNotFoundError(apk)
    out.mkdir(parents=True, exist_ok=True)
    result = run_capture("android-virtual-smoke", ["bash", str(script), str(apk), str(out)], timeout_s=args.timeout, session_id=args.session)
    print((ROOT / result["stdout"]).read_text(encoding="utf-8", errors="replace")[-8000:])
    print(f"ANDROID_SMOKE={result['status']} RUN={result['run_id']}")
    return 0 if result["status"] == "PASSED" else (3 if result["status"] == "BLOCKED" else 2)


def cmd_web_audit(args: argparse.Namespace) -> int:
    script = Path(__file__).resolve().parent / "web_audit.mjs"
    if not shutil.which("node"):
        print("BLOCKED: Node.js is not installed.", file=sys.stderr); return 3
    out = Path(args.output); out = out if out.is_absolute() else ROOT / out
    out = within_repo(out); out.mkdir(parents=True, exist_ok=True)
    env = {"KELLVYN_LAB_URL": args.url, "LAB_OUTPUT_DIR": str(out)}
    result = run_capture("web-visual-audit", ["node", str(script)], timeout_s=args.timeout, session_id=args.session, env=env)
    print((ROOT / result["stdout"]).read_text(encoding="utf-8", errors="replace")[-10000:])
    print(f"WEB_AUDIT={result['status']} RUN={result['run_id']}")
    return 0 if result["status"] == "PASSED" else 2


def build_dashboard() -> Path:
    dashboard_dir = HOME / "dashboard"
    dashboard_dir.mkdir(parents=True, exist_ok=True)
    events = []
    integrity = verify_ledger()
    if LEDGER.exists() and integrity["valid"]:
        for line in LEDGER.read_text(encoding="utf-8").splitlines():
            if line.strip(): events.append(json.loads(line))
    runs = []
    for manifest in sorted(RUNS.glob("*/run.json"), key=lambda p: p.stat().st_mtime, reverse=True)[:40]:
        try: runs.append(json.loads(manifest.read_text(encoding="utf-8")))
        except (OSError, json.JSONDecodeError): pass
    creative = sorted(HOME.glob("creative/**/manifest.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    creative_cards = []
    for path in creative[:12]:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            preview = path.parent / "preview.png"
            preview_rel = os.path.relpath(preview, dashboard_dir).replace(os.sep, "/") if preview.is_file() else ""
            progress_file = path.parent / "progress.jsonl"
            progress = json.loads(progress_file.read_text(encoding="utf-8").splitlines()[-1]) if progress_file.is_file() and progress_file.stat().st_size else {}
            creative_cards.append((data, str(path.relative_to(ROOT)), preview_rel, progress))
        except (OSError, json.JSONDecodeError): continue
    def esc(value): return html.escape(str(value), quote=True)
    rows = "".join(f"<tr><td>{esc(e.get('recorded_at_local',''))}</td><td>{esc(e.get('kind',''))}</td><td>{esc(e.get('status',''))}</td><td>{esc(e.get('summary',''))}</td><td><code>{esc(str(e.get('head',''))[:12])}</code></td></tr>" for e in reversed(events[-80:]))
    run_rows = "".join(f"<tr><td>{esc(r.get('name'))}</td><td>{esc(r.get('status'))}</td><td>{esc(r.get('duration_seconds'))}s</td><td><code>{esc(r.get('run_id'))}</code></td><td><a href='{esc(os.path.relpath(ROOT / r.get('stdout', ''), dashboard_dir).replace(os.sep, '/'))}'>stdout</a> · <a href='{esc(os.path.relpath(ROOT / r.get('stderr', ''), dashboard_dir).replace(os.sep, '/'))}'>stderr</a></td></tr>" for r in runs)
    cards = "".join(f"<article><h3>{esc(d.get('mode','creative')).upper()} · {esc(d.get('lighting',''))}</h3>{('<img src="'+esc(img)+'" alt="render preview">') if img else ''}<p>Engine: {esc(d.get('engine'))} · Shadows: {esc(d.get('shadow_quality'))} · Lights: {esc(', '.join(d.get('light_rig',[])))}</p><p><code>{esc(p)}</code></p></article>" for d,p,img in creative_cards)
    state = STATE_MD.read_text(encoding="utf-8") if STATE_MD.exists() else "No checkpoint recorded yet."
    (dashboard_dir / "index.html").write_text("""<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Kellvyn Workbench</title><style>body{font:15px system-ui;background:#10161a;color:#e8f0ef;margin:0;padding:24px}main{max-width:1200px;margin:auto}section,article{background:#182329;border:1px solid #34504d;border-radius:12px;padding:16px;margin:14px 0}h1,h2{color:#72e6c2}table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:8px;border-bottom:1px solid #34504d;vertical-align:top}code{font-size:12px;overflow-wrap:anywhere}img{max-width:100%;max-height:360px;object-fit:contain;background:#0a0e10;border-radius:8px}article{display:inline-block;width:min(420px,100%);vertical-align:top;box-sizing:border-box}pre{white-space:pre-wrap;overflow-wrap:anywhere}a{color:#7dd3fc}</style></head><body><main><h1>Kellvyn Workbench</h1><p>Auditoria operacional local — não é status de build até haver logs reais.</p><section><h2>Integridade do diário</h2><p>""" + f"<strong>{'VALID' if integrity['valid'] else 'INVALID'}</strong> · {integrity['records']} eventos · {esc(integrity.get('message',''))}" + """</p></section><section><h2>Checkpoint</h2><pre>""" + esc(state) + """</pre></section><section><h2>Criações recentes</h2>""" + (cards or "<p>No creative outputs recorded.</p>") + """</section><section><h2>Execuções</h2><table><thead><tr><th>Nome</th><th>Estado</th><th>Duração</th><th>Run</th><th>Logs</th></tr></thead><tbody>""" + (run_rows or "<tr><td colspan=5>Nenhuma execução registrada.</td></tr>") + """</tbody></table></section><section><h2>Auditoria cronológica</h2><table><thead><tr><th>Horário local</th><th>Tipo</th><th>Estado</th><th>Resumo</th><th>HEAD</th></tr></thead><tbody>""" + (rows or "<tr><td colspan=5>Nenhum evento registrado.</td></tr>") + """</tbody></table></section></main></body></html>""", encoding="utf-8")
    return dashboard_dir / "index.html"


def cmd_report(_args: argparse.Namespace) -> int:
    page = build_dashboard()
    append_event("DASHBOARD", "RECORDED", "Local Workbench dashboard generated.", paths=[str(page.relative_to(ROOT))])
    print(str(page.relative_to(ROOT)))
    return 0


def cmd_visual_diff(args: argparse.Namespace) -> int:
    script = Path(__file__).resolve().parent / "visual_diff.py"
    command = [sys.executable, str(script), "--before", args.before, "--after", args.after,
               "--output", args.output, "--threshold", str(args.threshold)]
    result = run_capture("visual-diff", command, timeout_s=120)
    print((ROOT / result["stdout"]).read_text(encoding="utf-8", errors="replace"))
    if result["status"] != "PASSED":
        print((ROOT / result["stderr"]).read_text(encoding="utf-8", errors="replace"), file=sys.stderr)
        return 3 if result["status"] == "BLOCKED" else 2
    return 0


def cmd_creative(args: argparse.Namespace) -> int:
    binary = shutil.which("blender")
    if not binary:
        print("BLOCKED: Blender executable is not installed on this host. The pipeline is available, but no render was executed.", file=sys.stderr)
        append_event("CREATIVE_RENDER", "BLOCKED", "Blender executable not installed; render not run.")
        return 3
    output = Path(args.output)
    if not output.is_absolute(): output = ROOT / output
    output = within_repo(output)
    output.mkdir(parents=True, exist_ok=True)
    progress_file = output / "progress.jsonl"
    cmd = [binary, "--background", "--factory-startup", "--python", str(BLENDER_PIPELINE), "--",
           "--mode", args.mode, "--output-dir", str(output), "--progress-file", str(progress_file), "--lighting", args.lighting,
           "--key-light-type", args.key_light_type, "--key-energy-scale", str(args.key_energy_scale),
           "--key-size", str(args.key_size), "--spot-angle", str(args.spot_angle), "--sun-angle", str(args.sun_angle),
           "--engine", args.engine, "--samples", str(args.samples), "--shadow-quality", args.shadow_quality,
           "--resolution", str(args.resolution), "--palette", args.palette]
    result = run_capture("creative-blender", cmd, timeout_s=args.timeout, session_id=args.session)
    print((ROOT / result["stdout"]).read_text(encoding="utf-8", errors="replace")[-12000:])
    if result["status"] == "PASSED":
        artifacts = sorted(p for p in output.rglob("*") if p.is_file())
        evidence = [f"{p.relative_to(ROOT)}#sha256={hashlib.sha256(p.read_bytes()).hexdigest()}#bytes={p.stat().st_size}" for p in artifacts]
        append_event("CREATIVE_ARTIFACTS", "RECORDED", f"Rendered {len(artifacts)} creative artifacts.", session_id=args.session,
                     paths=[str(p.relative_to(ROOT)) for p in artifacts], evidence=evidence)
        print("ARTIFACTS:")
        for item in evidence: print("- " + item)
    print(f"RESULT={result['status']} RUN={result['run_id']}")
    return 0 if result["status"] == "PASSED" else 2


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Kellvyn Workbench — research, build, debug, visual and creative tools.")
    sub = parser.add_subparsers(dest="command", required=True)
    for name, fn in (("doctor", cmd_doctor), ("audit", cmd_audit), ("verify-ledger", cmd_verify)):
        p = sub.add_parser(name); p.set_defaults(func=fn)
    p = sub.add_parser("log"); p.add_argument("--kind", choices=["research", "decision", "implementation", "test", "debug", "creative", "incident", "handoff"], required=True)
    p.add_argument("--status", choices=["planned", "implemented", "passed", "failed", "blocked", "not-run", "review-required"], required=True)
    p.add_argument("--summary", required=True); p.add_argument("--session", default="")
    p.add_argument("--path", action="append", default=[]); p.add_argument("--evidence", action="append", default=[]); p.add_argument("--next-step", default="")
    p.set_defaults(func=cmd_log)
    p = sub.add_parser("checkpoint"); p.add_argument("--summary", required=True); p.add_argument("--next", required=True); p.add_argument("--session", default="")
    p.add_argument("--done", action="append", default=[]); p.add_argument("--blocked", action="append", default=[]); p.set_defaults(func=cmd_checkpoint)
    p = sub.add_parser("session"); q = p.add_subparsers(dest="action", required=True)
    a = q.add_parser("start"); a.add_argument("--id", default=""); a.add_argument("--summary", required=True); a.set_defaults(func=cmd_session)
    a = q.add_parser("end"); a.add_argument("--id", required=True); a.add_argument("--summary", required=True); a.add_argument("--status", choices=["completed", "interrupted", "blocked"], default="completed"); a.set_defaults(func=cmd_session)
    p = sub.add_parser("run"); p.add_argument("--name", required=True); p.add_argument("--cwd", default="."); p.add_argument("--timeout", type=int, default=1800); p.add_argument("--session", default=""); p.add_argument("argv", nargs=argparse.REMAINDER, help="explicit executable and arguments; no shell is used"); p.set_defaults(func=cmd_run)
    p = sub.add_parser("debug"); p.add_argument("--log", default=""); p.add_argument("--adb", action="store_true"); p.add_argument("--serial", default=""); p.add_argument("--output", default=""); p.set_defaults(func=cmd_debug)
    p = sub.add_parser("test"); p.add_argument("--suite", choices=["workbench", "rnd-studio", "syntax", "android-syntax", "web-syntax"], required=True); p.add_argument("--timeout", type=int, default=1200); p.add_argument("--session", default=""); p.set_defaults(func=cmd_test)
    p = sub.add_parser("rnd"); p.add_argument("--timeout", type=int, default=1800); p.add_argument("--session", default=""); p.add_argument("argv", nargs=argparse.REMAINDER); p.set_defaults(func=cmd_rnd)
    p = sub.add_parser("task"); q = p.add_subparsers(dest="action", required=True)
    z = q.add_parser("test"); z.add_argument("--id", required=True); z.add_argument("--suite", required=True, choices=["brain", "agents", "integration", "unit", "assemble", "instrumented"]); z.add_argument("--timeout", type=int, default=1800); z.add_argument("--session", default=""); z.set_defaults(func=cmd_studio_task)
    z = q.add_parser("check"); z.add_argument("--id", required=True); z.add_argument("--require-test", action="append", choices=["brain", "agents", "integration", "unit", "assemble", "instrumented"], default=[]); z.add_argument("--session", default=""); z.set_defaults(func=cmd_studio_task)
    p = sub.add_parser("android-smoke"); p.add_argument("--apk", required=True); p.add_argument("--output", default="artifacts/workbench/android/latest"); p.add_argument("--timeout", type=int, default=1200); p.add_argument("--session", default=""); p.set_defaults(func=cmd_android_smoke)
    p = sub.add_parser("web-audit"); p.add_argument("--url", default="http://127.0.0.1:4173/"); p.add_argument("--output", default="artifacts/workbench/web/latest"); p.add_argument("--timeout", type=int, default=300); p.add_argument("--session", default=""); p.set_defaults(func=cmd_web_audit)
    p = sub.add_parser("report"); p.set_defaults(func=cmd_report)
    p = sub.add_parser("worktree"); p.add_argument("--id", required=True); p.add_argument("--title", required=True); p.add_argument("--objective", required=True); p.add_argument("--scope", choices=["brain", "agents", "integration"], default="agents"); p.add_argument("--base", default="HEAD"); p.add_argument("--require-test", action="append", default=[]); p.add_argument("--session", default=""); p.set_defaults(func=cmd_worktree)
    p = sub.add_parser("visual-diff"); p.add_argument("--before", required=True); p.add_argument("--after", required=True); p.add_argument("--output", required=True); p.add_argument("--threshold", type=int, default=18); p.set_defaults(func=cmd_visual_diff)
    p = sub.add_parser("creative"); p.add_argument("--mode", choices=["2d", "3d", "4d"], default="3d"); p.add_argument("--output", default="artifacts/workbench/creative/latest"); p.add_argument("--lighting", choices=["studio", "dramatic", "soft", "sunset"], default="studio"); p.add_argument("--key-light-type", choices=["AREA", "SUN", "SPOT"], default="AREA"); p.add_argument("--key-energy-scale", type=float, default=1.0); p.add_argument("--key-size", type=float, default=5.0); p.add_argument("--spot-angle", type=float, default=55.0); p.add_argument("--sun-angle", type=float, default=8.0); p.add_argument("--engine", choices=["eevee", "cycles"], default="eevee"); p.add_argument("--samples", type=int, default=32); p.add_argument("--shadow-quality", choices=["low", "medium", "high"], default="medium"); p.add_argument("--resolution", type=int, default=75); p.add_argument("--palette", choices=["emerald", "ocean", "violet", "sunset", "graphite"], default="emerald"); p.add_argument("--timeout", type=int, default=1800); p.add_argument("--session", default=""); p.set_defaults(func=cmd_creative)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if hasattr(args, "timeout") and not (1 <= args.timeout <= 7200):
        raise SystemExit("timeout must be between 1 and 7200 seconds")
    if args.command == "debug" and not args.log and not args.adb:
        raise SystemExit("debug requires --log PATH or --adb")
    if args.command == "visual-diff" and not 0 <= args.threshold <= 254:
        raise SystemExit("visual-diff threshold must be 0..254")
    try:
        return int(args.func(args))
    except (OSError, ValueError, RuntimeError, KeyError, json.JSONDecodeError) as exc:
        print(f"WORKBENCH_ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
