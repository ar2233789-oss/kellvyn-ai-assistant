#!/usr/bin/env python3
"""Scope guard, toolchain inventory, and offline HTML report for the Super Lab."""
from __future__ import annotations
import argparse
import html
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ALLOWED_EXACT = {".github/workflows/super-lab.yml"}
ALLOWED_PREFIXES = ("workshop/",)

def is_allowed_changed_path(path: str) -> bool:
    clean = path.replace("\\", "/")
    while clean.startswith("./"):
        clean = clean[2:]
    return clean in ALLOWED_EXACT or any(clean.startswith(prefix) for prefix in ALLOWED_PREFIXES)

def run(args: list[str], cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=False)

def guard(base: str, head: str) -> int:
    diff = run(["git", "diff", "--name-only", f"{base}...{head}"])
    if diff.returncode:
        print(diff.stderr or "git diff failed", file=sys.stderr)
        return diff.returncode or 2
    changed = [line for line in diff.stdout.splitlines() if line.strip()]
    denied = [path for path in changed if not is_allowed_changed_path(path)]
    print(json.dumps({"base": base, "head": head, "changed": changed, "denied": denied}, indent=2))
    if denied:
        print("Super Lab rejected changes outside tooling paths.", file=sys.stderr)
        return 1
    if not changed:
        print("No changed paths found; nothing to guard.", file=sys.stderr)
        return 2
    return 0

def doctor(output: Path | None) -> int:
    names = ["python3", "git", "node", "npm", "bun", "java", "gradle", "adb", "blender"]
    tools = {}
    for name in names:
        path = shutil.which(name)
        if not path:
            tools[name] = {"available": False}
            continue
        result = run([name, "--version"] if name not in {"adb"} else [name, "version"])
        line = next((x.strip() for x in (result.stdout + "\n" + result.stderr).splitlines() if x.strip()), "")
        tools[name] = {"available": True, "path": path, "version": line[:240], "versionExit": result.returncode}
    report = {"schema": 1, "androidHome": os.environ.get("ANDROID_HOME"), "tools": tools}
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 0

def report(input_dir: Path, output_dir: Path, statuses: list[str]) -> int:
    output_dir.mkdir(parents=True, exist_ok=True)
    copied = []
    if input_dir.exists():
        for src in input_dir.rglob("*"):
            if not src.is_file():
                continue
            rel = src.relative_to(input_dir)
            dst = output_dir / "artifacts" / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            if src.resolve() != dst.resolve():
                shutil.copy2(src, dst)
            copied.append({"path": rel.as_posix(), "bytes": dst.stat().st_size})
    status_obj = {}
    for item in statuses:
        key, sep, value = item.partition("=")
        if sep and key.strip():
            status_obj[key.strip()] = value.strip()
    images = [x for x in copied if x["path"].lower().endswith((".png", ".jpg", ".jpeg", ".webp"))]
    cards = []
    for img in images:
        safe_path = html.escape("artifacts/" + img["path"], quote=True)
        alt = html.escape(img["path"])
        cards.append(f'<figure><img loading="lazy" src="{safe_path}" alt="{alt}"><figcaption>{alt} · {img["bytes"]:,} bytes</figcaption></figure>')
    status_rows = "".join(
        f"<tr><th>{html.escape(k)}</th><td><span class=\"status\">{html.escape(v)}</span></td></tr>"
        for k, v in sorted(status_obj.items())
    )
    files_rows = "".join(
        f"<tr><td>{html.escape(x['path'])}</td><td>{x['bytes']:,}</td></tr>"
        for x in sorted(copied, key=lambda row: row["path"])
    )
    dashboard = """<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Super Lab · Relatório</title><style>
body{font:16px system-ui;margin:0 auto;padding:24px;max-width:1100px;background:#101514;color:#e4eeeb}
h1,h2{color:#6ee7c8}p{color:#b7c6c1}table{width:100%;border-collapse:collapse;margin:16px 0}
th,td{text-align:left;padding:9px;border-bottom:1px solid #31413c;overflow-wrap:anywhere}
figure{margin:0;background:#19231f;padding:10px;border-radius:9px}img{width:100%;height:auto;display:block}
figcaption{font-size:12px;color:#b7c6c1;margin-top:7px} .gallery{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:12px}
.status{font-family:ui-monospace,monospace}.note{border-left:3px solid #6ee7c8;padding:10px 14px;background:#18231f}
</style><h1>Super Lab — relatório</h1><p>Resultados baseados apenas nos artefatos realmente encontrados. Comparação visual não equivale a aprovação automática.</p>
<div class="note">Este laboratório inspeciona ferramentas e builds em um runner/AVD. Ele não altera a interface do app, nem prova desempenho térmico ou autonomia em um Poco F6 físico.</div>
<h2>Estados dos jobs</h2><table>STATUS_ROWS</table><h2>Capturas disponíveis</h2><div class="gallery">GALLERY</div><h2>Arquivos e diagnósticos</h2><table><tr><th>Arquivo</th><th>Bytes</th></tr>FILES_ROWS</table></html>"""
    dashboard = dashboard.replace("STATUS_ROWS", status_rows or "<tr><td colspan=2>Sem estados fornecidos</td></tr>")
    dashboard = dashboard.replace("GALLERY", "".join(cards) or "<p>Nenhuma captura foi anexada.</p>")
    dashboard = dashboard.replace("FILES_ROWS", files_rows or "<tr><td colspan=2>Nenhum artefato encontrado</td></tr>")
    (output_dir / "index.html").write_text(dashboard, encoding="utf-8")
    manifest = {"schema": 1, "statuses": status_obj, "artifactCount": len(copied), "files": sorted(copied, key=lambda row: row["path"]), "images": [x["path"] for x in images]}
    (output_dir / "report.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "report_created", "path": str(output_dir / "index.html"), "artifactCount": len(copied)}, ensure_ascii=False))
    return 0

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p_guard = sub.add_parser("guard", help="Reject changes outside lab-only paths")
    p_guard.add_argument("--base", required=True); p_guard.add_argument("--head", default="HEAD")
    p_doctor = sub.add_parser("doctor", help="Inventory tools available in the current environment")
    p_doctor.add_argument("--output", type=Path)
    p_report = sub.add_parser("report", help="Create HTML/JSON report from collected artifacts")
    p_report.add_argument("--input-dir", type=Path, required=True); p_report.add_argument("--output-dir", type=Path, required=True)
    p_report.add_argument("--status", action="append", default=[])
    args = parser.parse_args()
    if args.command == "guard": return guard(args.base, args.head)
    if args.command == "doctor": return doctor(args.output)
    return report(args.input_dir, args.output_dir, args.status)

if __name__ == "__main__":
    raise SystemExit(main())
