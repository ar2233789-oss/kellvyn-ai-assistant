#!/usr/bin/env python3
"""Kellvyn Engineering Lab: repository map, static audit and session log.

Static inspection only: this tool never claims to compile, execute, install or
validate the Android application or neural model.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs" / "kellvyn"
AUDITS = DOCS / "audits"
LEDGER = DOCS / "DEVELOPMENT_ACTIVITY.md"
LOCK = ROOT / ".github" / "interface-lock.sha"
SYMBOL_RE = re.compile(
    r"^\s*(?:(?:public|internal|private|protected|open|abstract|data|sealed|enum|fun|suspend|inline|override|lateinit|const|expect|actual)\s+)*"
    r"(?:class|interface|object|typealias|fun)\s+([A-Za-z_][A-Za-z0-9_]*)"
)
TEST_RE = re.compile(r"^\s*@Test\b")


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def git(*args: str, required: bool = True) -> str:
    try:
        return subprocess.check_output(
            ["git", *args], cwd=ROOT, text=True, stderr=subprocess.STDOUT
        ).strip()
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        if required:
            raise RuntimeError(f"git {' '.join(args)}: {exc}") from exc
        return ""


def git_blob(path: Path) -> str:
    return git("hash-object", str(path.relative_to(ROOT)), required=False)


def interface_lock_audit() -> list[dict[str, str]]:
    entries: list[dict[str, str]] = []
    if not LOCK.exists():
        return entries
    for line in LOCK.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^([0-9a-f]{40})\s+(.+)$", line.strip())
        if not match:
            continue
        expected, relative = match.groups()
        target = ROOT / relative
        actual = git_blob(target) if target.is_file() else "MISSING"
        entries.append({
            "path": relative,
            "expected_git_blob": expected,
            "actual_git_blob": actual,
            "status": "MATCH" if actual == expected else "MISMATCH",
        })
    return entries


def inventory():
    main = ROOT / "android" / "app" / "src" / "main"
    test_roots = [
        ROOT / "android" / "app" / "src" / "test",
        ROOT / "android" / "app" / "src" / "androidTest",
    ]
    source_files = sorted(str(p.relative_to(ROOT)) for p in main.rglob("*.kt")) if main.exists() else []
    test_files = sorted(
        str(p.relative_to(ROOT))
        for base in test_roots if base.exists()
        for p in base.rglob("*.kt")
    )
    counts = {"kotlin_sources": len(source_files), "kotlin_test_files": len(test_files), "test_annotations": 0}
    symbols = []
    test_set = set(test_files)
    for relative in source_files + test_files:
        try:
            lines = (ROOT / relative).read_text(encoding="utf-8").splitlines()
        except (OSError, UnicodeError):
            continue
        for number, line in enumerate(lines, 1):
            if relative in test_set and TEST_RE.match(line):
                counts["test_annotations"] += 1
            match = SYMBOL_RE.match(line)
            if match:
                symbols.append({"file": relative, "line": number, "symbol": match.group(1)})
    return source_files, test_files, counts, symbols


def workflows():
    result = []
    base = ROOT / ".github" / "workflows"
    if not base.exists():
        return result
    for path in sorted(list(base.glob("*.yml")) + list(base.glob("*.yaml"))):
        body = path.read_text(encoding="utf-8", errors="replace")
        labels = sorted(set(re.findall(r"^\s*runs-on:\s*([^\s#]+)", body, re.M)))
        result.append({"path": str(path.relative_to(ROOT)), "runner_labels": ", ".join(labels) or "not-declared"})
    return result


def build_report():
    stamp = utc_now().isoformat(timespec="seconds")
    source_files, test_files, counts, symbols = inventory()
    lock = interface_lock_audit()
    return {
        "schema": "kellvyn-engineering-audit/v1",
        "generated_at_utc": stamp,
        "repository": "ar2233789-oss/kellvyn-ai-assistant",
        "branch": git("branch", "--show-current", required=False) or "DETACHED_OR_UNKNOWN",
        "head": git("rev-parse", "HEAD", required=False) or "UNKNOWN",
        "scope": "static repository inventory; not a build or runtime test",
        "android": {
            "gradle_wrapper_present": (ROOT / "android" / "gradlew").is_file(),
            "gradle_build_file_present": (ROOT / "android" / "app" / "build.gradle").is_file(),
            "main_activity_present": (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "kellvyn" / "assistant" / "MainActivity.java").is_file(),
        },
        "counts": counts,
        "source_files": source_files,
        "test_files": test_files,
        "symbols": symbols,
        "interface_lock": lock,
        "interface_lock_mismatches": sum(item["status"] != "MATCH" for item in lock),
        "workflows": workflows(),
        "execution_status": "NOT_RUN_BY_STATIC_AUDIT",
        "limitations": [
            "Does not invoke Gradle, JUnit, Android Emulator, ADB or neural inference.",
            "The symbol map is navigation metadata, not semantic proof of integration.",
            "Interface lock mismatches are reported and never auto-fixed.",
        ],
    }


def render_map(report):
    lines = [
        "# Kellvyn — mapa estrutural do repositório", "",
        f"- Gerado UTC: {report['generated_at_utc']}",
        f"- Branch: {report['branch']}", f"- HEAD: {report['head']}",
        "- Índice estático para navegação; não substitui leitura do código.", "",
        "## Símbolos Kotlin", "",
    ]
    by_file = {}
    for item in report["symbols"]:
        by_file.setdefault(item["file"], []).append(item)
    for file, items in sorted(by_file.items()):
        lines.extend([f"### {file}", ""])
        lines.extend(f"- Linha {item['line']}: {item['symbol']}" for item in items[:80])
        lines.append("")
    lines.extend([
        "## Inventário", "",
        f"- Fontes Kotlin: {report['counts']['kotlin_sources']}",
        f"- Arquivos de teste Kotlin: {report['counts']['kotlin_test_files']}",
        f"- Anotações @Test: {report['counts']['test_annotations']}", "",
    ])
    return "\n".join(lines)


def render_audit(report):
    lines = [
        "# Kellvyn — auditoria estrutural", "",
        f"- Data/hora UTC: {report['generated_at_utc']}",
        f"- Repositório: {report['repository']}",
        f"- Branch: {report['branch']}", f"- HEAD: {report['head']}",
        "- Estado: INVENTÁRIO ESTÁTICO — NÃO É TESTE DE EXECUÇÃO", "",
        "## Inventário", "",
        f"- Fontes Kotlin: {report['counts']['kotlin_sources']}",
        f"- Arquivos de teste Kotlin: {report['counts']['kotlin_test_files']}",
        f"- Anotações @Test: {report['counts']['test_annotations']}",
        f"- Gradle Wrapper: {'presente' if report['android']['gradle_wrapper_present'] else 'ausente'}", "",
        "## Proteção da interface", "",
        "| Arquivo | Estado | Hash esperado | Hash encontrado |",
        "|---|---|---|---|",
    ]
    for item in report["interface_lock"]:
        lines.append(
            f"| {item['path']} | {item['status']} | "
            f"{item['expected_git_blob']} | {item['actual_git_blob']} |"
        )
    lines.extend([
        "", f"Divergências: {report['interface_lock_mismatches']}. Nenhum hash foi alterado automaticamente.",
        "", "## Workflows e runners declarados", "",
        "| Workflow | Runner |", "|---|---|",
    ])
    lines.extend(f"| {item['path']} | {item['runner_labels']} |" for item in report["workflows"])
    lines.extend([
        "", "## Limites", "",
        *[f"- {item}" for item in report["limitations]],
        "", "## Próximo gate", "",
        "Executar Gradle/JUnit e Android instrumentado em runner real, anexar logs e relatórios; "
        "depois validar inferência neural real e medir no dispositivo-alvo. Não promover perfis "
        "com base apenas neste inventário.", "",
    ])
    return "\n".join(lines)


def command_audit(_args):
    report = build_report()
    AUDITS.mkdir(parents=True, exist_ok=True)
    stamp = utc_now().strftime("%Y%m%d-%H%M%S")
    json_path = AUDITS / f"agent-lab-audit-{stamp}Z.json"
    md_path = AUDITS / f"agent-lab-audit-{stamp}Z.md"
    map_path = AUDITS / "REPOSITORY_MAP.md"
    latest_path = AUDITS / "LATEST.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    md_path.write_text(render_audit(report), encoding="utf-8")
    map_path.write_text(render_map(report), encoding="utf-8")
    latest_path.write_text(
        "# Última auditoria do Kellvyn Engineering Lab\n\n"
        f"- Auditoria: {md_path.name}\n- Dados: {json_path.name}\n"
        f"- Mapa: {map_path.name}\n- Gerado UTC: {report['generated_at_utc']}\n"
        f"- HEAD inspecionado: {report['head']}\n"
        "- Execução/compilação: NÃO EXECUTADA POR ESTE SCRIPT\n",
        encoding="utf-8",
    )
    print(f"AUDIT_WRITTEN={md_path.relative_to(ROOT)}")
    print(f"JSON_WRITTEN={json_path.relative_to(ROOT)}")
    print(f"MAP_WRITTEN={map_path.relative_to(ROOT)}")
    print(f"INTERFACE_LOCK_MISMATCHES={report['interface_lock_mismatches']}")
    print("EXECUTION_STATUS=NOT_RUN_BY_STATIC_AUDIT")


def command_log(args):
    if not args.summary.strip():
        raise SystemExit("--summary não pode ficar vazio.")
    DOCS.mkdir(parents=True, exist_ok=True)
    stamp = utc_now().strftime("%Y-%m-%d %H:%M:%S UTC")
    evidence = ", ".join(args.evidence) if args.evidence else "não anexada"
    entry = (
        f"\n## {stamp} — {args.kind.upper()}\n\n"
        f"- Resumo: {args.summary.strip()}\n"
        f"- Estado: {args.status.upper()}\n"
        f"- Evidências: {evidence}\n"
        f"- Próximo passo: {args.next_step.strip() or 'não definido'}\n"
    )
    with LEDGER.open("a", encoding="utf-8") as stream:
        stream.write(entry)
    print(f"SESSION_EVENT_RECORDED={stamp}")


def main():
    parser = argparse.ArgumentParser(description="Ferramentas do Kellvyn Engineering Lab.")
    commands = parser.add_subparsers(required=True)
    audit = commands.add_parser("audit", help="gera auditoria, mapa e snapshots UTC")
    audit.set_defaults(func=command_audit)
    log = commands.add_parser("log", help="registra evento de trabalho no diário")
    log.add_argument("--kind", choices=["research", "decision", "implementation", "test", "incident", "handoff"], required=True)
    log.add_argument("--summary", required=True)
    log.add_argument("--status", choices=["implemented", "not-run", "passed", "failed", "blocked", "decision"], default="implemented")
    log.add_argument("--evidence", action="append", default=[])
    log.add_argument("--next-step", default="")
    log.set_defaults(func=command_log)
    args = parser.parse_args()
    try:
        args.func(args)
    except (RuntimeError, OSError, ValueError) as exc:
        print(f"ENGINEERING_LAB_ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)


if __name__ == "__main__":
    main()
