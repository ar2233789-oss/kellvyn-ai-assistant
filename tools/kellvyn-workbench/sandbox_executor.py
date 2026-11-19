#!/usr/bin/env python3
"""Rootless, offline, disposable Podman executor for untrusted engineering tasks.

Only a filtered workspace copy is mounted into the container. The source checkout,
user home, credentials, SDK caches and host sockets are never mounted.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
RUNS = ROOT / "artifacts" / "workbench" / "sandbox-runs"
DIGEST_IMAGE = re.compile(r"^[A-Za-z0-9._/:+-]+@sha256:[0-9a-f]{64}$")
TASK_ID = re.compile(r"^[A-Za-z0-9._-]{1,80}$")
IGNORE_PATTERNS = (
    ".git", ".gradle", "build", "node_modules", ".idea", ".kotlin",
    "__pycache__", ".pytest_cache", ".mypy_cache", ".ruff_cache",
    ".venv", "venv", "artifacts", ".env", ".env.*", ".npmrc", ".pypirc",
    "*.jks", "*.keystore", "*.pem", "*.p12", "*.pfx", "id_rsa", "id_ed25519"
)


class SandboxBlocked(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_repo_path(value: str) -> Path:
    path = Path(value).expanduser()
    resolved = (path if path.is_absolute() else ROOT / path).resolve()
    if resolved == ROOT or ROOT not in resolved.parents:
        raise ValueError("sandbox source must be a subdirectory inside the repository; repository root is not accepted")
    if not resolved.is_dir():
        raise FileNotFoundError(resolved)
    return resolved


def validate_image_ref(image: str) -> None:
    if not DIGEST_IMAGE.fullmatch(image):
        raise ValueError("sandbox image must be pinned by immutable sha256 digest (name@sha256:<64 hex>)")


def validate_limits(memory_mb: int, cpus: float, pids_limit: int, timeout_s: int) -> None:
    if not 512 <= memory_mb <= 16384:
        raise ValueError("memory limit must be 512..16384 MiB")
    if not 0.25 <= cpus <= 8.0:
        raise ValueError("CPU limit must be 0.25..8.0")
    if not 16 <= pids_limit <= 512:
        raise ValueError("PID limit must be 16..512")
    if not 1 <= timeout_s <= 7200:
        raise ValueError("timeout must be 1..7200 seconds")


def run_host(argv: list[str], timeout_s: int = 20, allow_failure: bool = False) -> subprocess.CompletedProcess[str]:
    try:
        result = subprocess.run(
            argv,
            cwd=ROOT,
            env={
                key: value for key, value in os.environ.items()
                if key in {"PATH", "HOME", "TMPDIR", "TMP", "TEMP", "LANG", "LC_ALL", "TZ"}
            },
            text=True,
            capture_output=True,
            timeout=timeout_s,
            check=False
        )
    except FileNotFoundError as exc:
        raise SandboxBlocked(f"required executable is missing: {exc.filename}") from exc
    except subprocess.TimeoutExpired as exc:
        raise SandboxBlocked(f"preflight command timed out: {argv[0]}") from exc
    if result.returncode and not allow_failure:
        detail = (result.stderr or result.stdout or "").strip()[-2000:]
        raise SandboxBlocked(f"preflight command failed ({result.returncode}): {detail}")
    return result


def podman_rootless_info(podman: str) -> dict[str, Any]:
    info = run_host([podman, "info", "--format", "json"], timeout_s=30)
    try:
        data = json.loads(info.stdout)
    except json.JSONDecodeError as exc:
        raise SandboxBlocked("Podman info did not return valid JSON") from exc
    host = data.get("host", {}) if isinstance(data, dict) else {}
    security = host.get("security", {}) if isinstance(host, dict) else {}
    rootless = security.get("rootless")
    if rootless is not True:
        raise SandboxBlocked("Podman is not running rootless; refusing host-level container execution")
    return {
        "rootless": True,
        "podman_version": data.get("version", {}).get("Version") if isinstance(data.get("version"), dict) else None
    }


def source_inventory(
    source: Path,
    *,
    max_files: int = 20000,
    max_bytes: int = 2 * 1024 * 1024 * 1024,
    reject_symlinks: bool = True
) -> list[dict[str, Any]]:
    inventory: list[dict[str, Any]] = []
    total_bytes = 0
    for current, dirs, files in os.walk(source, topdown=True, followlinks=False):
        current_path = Path(current)
        kept_dirs = []
        for name in sorted(dirs):
            path = current_path / name
            if name in IGNORE_PATTERNS or name.startswith(".env."):
                continue
            if path.is_symlink():
                if reject_symlinks:
                    raise SandboxBlocked(f"workspace contains a symlink; refusing ambiguous host path: {path.relative_to(source)}")
                target = os.readlink(path)
                inventory.append({
                    "path": path.relative_to(source).as_posix(),
                    "type": "symlink",
                    "target": target,
                    "sha256": hashlib.sha256(target.encode("utf-8", errors="surrogateescape")).hexdigest()
                })
                continue
            kept_dirs.append(name)
        dirs[:] = kept_dirs
        for name in sorted(files):
            path = current_path / name
            relative = path.relative_to(source).as_posix()
            if name in IGNORE_PATTERNS or name.startswith(".env.") or path.suffix.lower() in {
                ".jks", ".keystore", ".pem", ".p12", ".pfx", ".key"
            }:
                continue
            if path.is_symlink():
                if reject_symlinks:
                    raise SandboxBlocked(f"workspace contains a symlink; refusing ambiguous host path: {relative}")
                target = os.readlink(path)
                inventory.append({
                    "path": relative,
                    "type": "symlink",
                    "target": target,
                    "sha256": hashlib.sha256(target.encode("utf-8", errors="surrogateescape")).hexdigest()
                })
                continue
            if not path.is_file():
                continue
            size = path.stat().st_size
            total_bytes += size
            if len(inventory) >= max_files:
                raise SandboxBlocked(f"workspace exceeds the {max_files} file safety limit")
            if total_bytes > max_bytes:
                raise SandboxBlocked(f"workspace exceeds the {max_bytes} byte safety limit")
            inventory.append({
                "path": relative,
                "type": "file",
                "size_bytes": size,
                "sha256": sha256_file(path)
            })
    return sorted(inventory, key=lambda item: item["path"])

def copy_workspace(source: Path, target: Path) -> list[dict[str, Any]]:
    target.mkdir(parents=True, exist_ok=False)
    shutil.copytree(
        source,
        target,
        dirs_exist_ok=True,
        symlinks=True,
        ignore=shutil.ignore_patterns(*IGNORE_PATTERNS)
    )
    # Re-scan the actual copy and reject any symlink that appeared during staging.
    return source_inventory(target, reject_symlinks=True)


def workspace_diff(before: list[dict[str, Any]], after: list[dict[str, Any]]) -> dict[str, Any]:
    before_map = {item["path"]: item["sha256"] for item in before}
    after_map = {item["path"]: item["sha256"] for item in after}
    added = sorted(set(after_map) - set(before_map))
    deleted = sorted(set(before_map) - set(after_map))
    changed = sorted(path for path in set(before_map) & set(after_map) if before_map[path] != after_map[path])
    return {"added": added, "deleted": deleted, "modified": changed,
            "changed_count": len(added) + len(deleted) + len(changed)}


def verify_local_image(podman: str, image: str) -> None:
    result = run_host([podman, "image", "exists", image], timeout_s=20, allow_failure=True)
    if result.returncode != 0:
        raise SandboxBlocked("digest-pinned image is not present locally; sandbox will never pull images implicitly")


def execute_rootless(
    *,
    source: Path,
    image: str,
    argv: list[str],
    task_id: str,
    memory_mb: int = 4096,
    cpus: float = 2.0,
    pids_limit: int = 128,
    timeout_s: int = 1800
) -> dict[str, Any]:
    if not TASK_ID.fullmatch(task_id):
        raise ValueError("invalid task id")
    validate_image_ref(image)
    validate_limits(memory_mb, cpus, pids_limit, timeout_s)
    if not argv or not argv[0] or sum(len(arg) for arg in argv) > 16000:
        raise ValueError("a non-empty argv with at most 16000 total argument characters is required")
    source = source.resolve()
    if source == ROOT or ROOT not in source.parents or not source.is_dir():
        raise ValueError("sandbox source must be an existing subdirectory inside the repository")
    podman = shutil.which("podman")
    if not podman:
        raise SandboxBlocked("Podman is not installed; Docker fallback is intentionally disabled until rootless mode can be proven")
    runtime = podman_rootless_info(podman)
    verify_local_image(podman, image)

    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:10]
    run_dir = RUNS / run_id
    if "," in str(run_dir):
        raise SandboxBlocked("sandbox host path contains a comma, which cannot be represented safely in Podman mount syntax")
    run_dir.mkdir(parents=True, exist_ok=False)
    workspace = run_dir / "workspace"
    output = run_dir / "output"
    output.mkdir()
    before_source = source_inventory(source)
    before = copy_workspace(source, workspace)
    source_head = run_host(["git", "-C", str(source), "rev-parse", "HEAD"], timeout_s=10, allow_failure=True).stdout.strip() or None
    source_status = run_host(["git", "-C", str(source), "status", "--porcelain"], timeout_s=10, allow_failure=True).stdout
    name = "kellvyn-sbx-" + uuid.uuid4().hex[:12]
    stdout_path = run_dir / "stdout.log"
    stderr_path = run_dir / "stderr.log"

    uid = os.getuid() if hasattr(os, "getuid") else 1000
    gid = os.getgid() if hasattr(os, "getgid") else 1000
    command = [
        podman, "run",
        "--name", name,
        "--pull=never",
        "--network=none",
        "--read-only",
        "--cap-drop=ALL",
        "--security-opt=no-new-privileges",
        "--userns=keep-id",
        "--user", f"{uid}:{gid}",
        "--pids-limit", str(pids_limit),
        "--memory", f"{memory_mb}m",
        "--cpus", str(cpus),
        "--ipc=private",
        "--tmpfs", "/tmp:rw,nosuid,nodev,size=512m",
        "--mount", f"type=bind,src={run_dir},dst=/sandbox,rw",
        "--workdir", "/sandbox/workspace",
        image,
        *argv
    ]

    started = time.monotonic()
    status = "FAILED"
    exit_code = None
    error = None
    try:
        with stdout_path.open("w", encoding="utf-8") as stdout, stderr_path.open("w", encoding="utf-8") as stderr:
            process = subprocess.Popen(
                command,
                cwd=ROOT,
                env={key: value for key, value in os.environ.items()
                     if key in {"PATH", "HOME", "TMPDIR", "TMP", "TEMP", "LANG", "LC_ALL", "TZ"}},
                stdout=stdout,
                stderr=stderr,
                start_new_session=True
            )
            try:
                exit_code = process.wait(timeout=timeout_s)
                status = "COMPLETED" if exit_code == 0 else "COMMAND_FAILED"
            except subprocess.TimeoutExpired:
                status = "TIMED_OUT"
                error = f"sandbox task exceeded {timeout_s}s"
                run_host([podman, "kill", name], timeout_s=15, allow_failure=True)
                run_host([podman, "rm", "-f", name], timeout_s=20, allow_failure=True)
                process.kill()
                process.wait()
                exit_code = 124
    except OSError as exc:
        status = "BLOCKED"
        error = f"{type(exc).__name__}: {exc}"
        stdout_path.touch(exist_ok=True)
        stderr_path.write_text(error + "\n", encoding="utf-8")

    # Remove any container left behind after a client error/timeout.
    run_host([podman, "rm", "-f", name], timeout_s=20, allow_failure=True)
    after = source_inventory(workspace, reject_symlinks=False)
    changes = workspace_diff(before, after)
    manifest = {
        "schema": "kellvyn-rootless-sandbox-run/v1",
        "run_id": run_id,
        "task_id": task_id,
        "status": status,
        "exit_code": exit_code,
        "error": error,
        "started_at_utc": datetime.now(timezone.utc).isoformat(),
        "duration_seconds": round(time.monotonic() - started, 3),
        "engine": "podman",
        "runtime": runtime,
        "image": image,
        "network": "none",
        "filesystem_root": "read-only",
        "workspace_mount": "/sandbox/workspace (writable copy only)",
        "output_mount": "/sandbox/output",
        "limits": {"memory_mb": memory_mb, "cpus": cpus, "pids": pids_limit, "timeout_seconds": timeout_s},
        "source": str(source.relative_to(ROOT)),
        "source_head": source_head,
        "source_dirty_before": bool(source_status.strip()),
        "command": argv,
        "workspace_changes": changes,
        "workspace_before_files": len(before),
        "workspace_after_files": len(after),
        "stdout": str(stdout_path.relative_to(ROOT)),
        "stderr": str(stderr_path.relative_to(ROOT)),
        "output_directory": str(output.relative_to(ROOT)),
        "quality_assessment": "NOT_EVALUATED"
    }
    manifest_path = run_dir / "sandbox-run.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    manifest["manifest"] = str(manifest_path.relative_to(ROOT))
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run trusted or untrusted engineering code in a rootless offline Podman sandbox.")
    parser.add_argument("--source", required=True)
    parser.add_argument("--image", required=True, help="Preloaded immutable image reference: name@sha256:<64 hex>")
    parser.add_argument("--task-id", required=True)
    parser.add_argument("--memory-mb", type=int, default=4096)
    parser.add_argument("--cpus", type=float, default=2.0)
    parser.add_argument("--pids-limit", type=int, default=128)
    parser.add_argument("--timeout", type=int, default=1800)
    parser.add_argument("command", nargs=argparse.REMAINDER, help="Exact argv after --; no shell is invoked")
    args = parser.parse_args(argv)
    command = list(args.command)
    if command and command[0] == "--":
        command = command[1:]
    try:
        source = safe_repo_path(args.source)
        result = execute_rootless(
            source=source, image=args.image, argv=command, task_id=args.task_id,
            memory_mb=args.memory_mb, cpus=args.cpus, pids_limit=args.pids_limit,
            timeout_s=args.timeout
        )
        return 0 if result["status"] == "COMPLETED" else 4 if result["status"] == "TIMED_OUT" else 2
    except SandboxBlocked as exc:
        print(json.dumps({"schema":"kellvyn-rootless-sandbox-run/v1","status":"BLOCKED","reason":str(exc)}, indent=2), file=sys.stderr)
        return 3
    except (OSError, ValueError, RuntimeError) as exc:
        print(f"SANDBOX_EXECUTOR_ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
