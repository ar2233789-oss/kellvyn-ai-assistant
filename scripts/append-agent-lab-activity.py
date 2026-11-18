#!/usr/bin/env python3
"""Append one immutable Git commit summary to the Kellvyn development activity ledger."""

from __future__ import annotations

import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs" / "kellvyn" / "DEVELOPMENT_ACTIVITY.md"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def git(*args: str) -> str:
    return subprocess.check_output(
        ["git", *args],
        cwd=ROOT,
        text=True,
        stderr=subprocess.STDOUT,
    ).strip()


def main() -> None:
    sha = os.environ.get("GITHUB_SHA", "").lower()
    if not SHA_RE.fullmatch(sha):
        raise SystemExit("GITHUB_SHA is missing or invalid.")

    if not LEDGER.exists():
        LEDGER.parent.mkdir(parents=True, exist_ok=True)
        LEDGER.write_text(
            "# Kellvyn — atividade de desenvolvimento\n\n"
            "Registro automático de commits enviados à branch de laboratório. "
            "Não armazena prompts, conversas, argumentos de ferramentas ou dados privados.\n\n",
            encoding="utf-8",
        )

    previous = LEDGER.read_text(encoding="utf-8")
    if sha in previous:
        print(f"Commit {sha[:12]} já consta no livro; nada a fazer.")
        return

    committed_at = git("show", "-s", "--format=%cI", sha)
    author = git("show", "-s", "--format=%an", sha).replace("\n", " ").strip()
    subject = git("show", "-s", "--format=%s", sha).replace("\n", " ").strip()
    paths = sorted(
        set(
            line.strip()
            for line in git(
                "diff-tree", "--root", "-m", "--no-commit-id",
                "--name-only", "-r", sha
            ).splitlines()
            if line.strip()
        )
    )

    timestamp = datetime.fromisoformat(committed_at.replace("Z", "+00:00"))
    timestamp = timestamp.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    def md(value: str) -> str:
        return value.replace("`", "\\`").replace("|", "\\|")

    shown_paths = paths[:80]
    path_text = ", ".join(f"`{md(path)}`" for path in shown_paths) or "nenhum arquivo detectado"
    if len(paths) > len(shown_paths):
        path_text += f" e mais {len(paths) - len(shown_paths)} arquivos"

    entry = (
        f"## {timestamp}\n\n"
        f"- Commit: `{sha}`\n"
        f"- Autor: {md(author)}\n"
        f"- Alteração: {md(subject)}\n"
        f"- Arquivos: {path_text}\n\n"
    )
    with LEDGER.open("a", encoding="utf-8") as stream:
        stream.write(entry)

    print(f"Registrado {sha[:12]} ({len(paths)} caminhos).")


if __name__ == "__main__":
    main()
