#!/usr/bin/env python3
"""Audit exact JSONL record overlap across train/validation/test splits.

This passive offline tool is invoked directly by the single Kellvyn main
intelligence. It does not move, rewrite, label, or expose dataset records.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sqlite3
import sys
import tempfile
from pathlib import Path
from typing import Any

MAX_FILES_PER_SPLIT = 100_000


class SplitAuditError(ValueError):
    """Raised when a split cannot be audited safely."""


def canonical_record(raw_line: bytes, path: Path, line_number: int) -> str | None:
    try:
        value = json.loads(raw_line.decode("utf-8"))
        canonical = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, TypeError):
        return None
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def safe_jsonl_files(root: Path) -> tuple[Path, list[Path]]:
    if root.is_symlink():
        raise SplitAuditError(f"A raiz da divisão não pode ser link simbólico: {root}")
    try:
        resolved = root.resolve(strict=True)
    except OSError as exc:
        raise SplitAuditError(f"Raiz da divisão inexistente: {root}: {exc}") from exc
    if not resolved.is_dir():
        raise SplitAuditError(f"A raiz da divisão não é diretório: {root}")
    result: list[Path] = []
    stack = [resolved]
    while stack:
        current = stack.pop()
        try:
            entries = sorted(os.scandir(current), key=lambda item: item.name.casefold(), reverse=True)
        except OSError as exc:
            raise SplitAuditError(f"Não foi possível ler {current}: {exc}") from exc
        for entry in entries:
            item = Path(entry.path)
            if entry.is_symlink():
                raise SplitAuditError(f"Link simbólico não permitido na divisão {root.name}: {item.relative_to(resolved)}")
            if entry.is_dir(follow_symlinks=False):
                stack.append(item)
            elif entry.is_file(follow_symlinks=False) and item.suffix.lower() in {".jsonl", ".ndjson"}:
                result.append(item)
                if len(result) > MAX_FILES_PER_SPLIT:
                    raise SplitAuditError(f"Limite excedido: mais de {MAX_FILES_PER_SPLIT} arquivos em {root.name}")
            elif entry.is_file(follow_symlinks=False):
                continue
            else:
                raise SplitAuditError(f"Tipo de arquivo especial não permitido: {item.relative_to(resolved)}")
    return resolved, sorted(result)


def validate_roots(splits: dict[str, Path]) -> dict[str, tuple[Path, list[Path]]]:
    allowed = {"train", "validation", "test"}
    if set(splits) - allowed:
        raise SplitAuditError("Nomes de divisão permitidos: train, validation, test")
    if len(splits) < 2:
        raise SplitAuditError("Informe pelo menos duas divisões entre train, validation e test")
    prepared = {name: safe_jsonl_files(root) for name, root in splits.items() if root is not None}
    roots = [(name, data[0]) for name, data in prepared.items()]
    for index, (name_a, root_a) in enumerate(roots):
        for name_b, root_b in roots[index + 1:]:
            if root_a == root_b or root_a in root_b.parents or root_b in root_a.parents:
                raise SplitAuditError(f"As raízes {name_a} e {name_b} são iguais ou aninhadas; a auditoria seria ambígua")
    return prepared


def audit_splits(splits: dict[str, Path], sample_limit: int = 50) -> dict[str, Any]:
    if sample_limit < 0 or sample_limit > 500:
        raise SplitAuditError("sample_limit precisa estar entre 0 e 500")
    prepared = validate_roots(splits)
    with tempfile.TemporaryDirectory(prefix="kellvyn-split-audit-") as temp_dir:
        db = sqlite3.connect(str(Path(temp_dir) / "fingerprints.sqlite3"))
        try:
            db.execute("PRAGMA journal_mode=OFF")
            db.execute("PRAGMA synchronous=OFF")
            db.execute("PRAGMA temp_store=FILE")
            db.execute("CREATE TABLE records (digest TEXT NOT NULL, split TEXT NOT NULL, relative_path TEXT NOT NULL, line_number INTEGER NOT NULL)")
            db.execute("CREATE INDEX records_digest_idx ON records(digest, split)")
            split_summary: dict[str, dict[str, Any]] = {}
            batch = []
            for split_name, (root, files) in prepared.items():
                summary = {"files": len(files), "records": 0, "duplicate_records_within_split": 0,
                           "invalid_records": 0, "invalid_record_locations": []}
                for path in files:
                    relative = path.relative_to(root).as_posix()
                    try:
                        with path.open("rb") as stream:
                            for line_number, raw_line in enumerate(stream, 1):
                                if not raw_line.strip():
                                    summary["invalid_records"] += 1
                                    if len(summary["invalid_record_locations"]) < sample_limit:
                                        summary["invalid_record_locations"].append({"path": relative, "line": line_number})
                                    continue
                                digest = canonical_record(raw_line, path, line_number)
                                if digest is None:
                                    summary["invalid_records"] += 1
                                    if len(summary["invalid_record_locations"]) < sample_limit:
                                        summary["invalid_record_locations"].append({"path": relative, "line": line_number})
                                    continue
                                summary["records"] += 1
                                batch.append((digest, split_name, relative, line_number))
                                if len(batch) >= 5000:
                                    db.executemany("INSERT INTO records VALUES (?, ?, ?, ?)", batch)
                                    batch.clear()
                    except OSError as exc:
                        raise SplitAuditError(f"Falha ao ler {relative}: {exc}") from exc
                split_summary[split_name] = summary
            if batch:
                db.executemany("INSERT INTO records VALUES (?, ?, ?, ?)", batch)
            db.commit()
            duplicate_rows = db.execute(
                "SELECT split, SUM(occurrences - 1) FROM ("
                "SELECT split, digest, COUNT(*) AS occurrences FROM records "
                "GROUP BY split, digest HAVING occurrences > 1) GROUP BY split"
            ).fetchall()
            for split_name, duplicate_count in duplicate_rows:
                split_summary[split_name]["duplicate_records_within_split"] = int(duplicate_count)
            overlap_rows = db.execute(
                "SELECT digest, COUNT(DISTINCT split) AS split_count FROM records GROUP BY digest HAVING split_count > 1 ORDER BY digest"
            ).fetchall()
            overlaps = []
            for digest, split_count in overlap_rows:
                samples = [] if sample_limit == 0 else db.execute(
                    "SELECT split, relative_path, line_number FROM records WHERE digest=? ORDER BY split, relative_path, line_number LIMIT ?",
                    (digest, sample_limit),
                ).fetchall()
                overlaps.append({"record_sha256": digest, "split_count": split_count,
                                 "locations": [{"split": row[0], "path": row[1], "line": row[2]} for row in samples]})
            invalid_total = sum(x["invalid_records"] for x in split_summary.values())
            duplicate_total = sum(x["duplicate_records_within_split"] for x in split_summary.values())
            status = "invalid_data" if invalid_total else "overlap_detected" if overlaps else "clean"
            return {
                "schema": 1,
                "status": status,
                "splits": split_summary,
                "cross_split_overlap_groups": len(overlaps),
                "invalid_records_total": invalid_total,
                "within_split_duplicate_records_total": duplicate_total,
                "overlaps": overlaps,
                "privacy_note": "Record values are not copied into the report; only SHA-256 fingerprints and locations are emitted. Hashes of low-entropy records may be guessed, so reports still require access control.",
                "scope_note": "Detects exact duplicate JSON/JSONL records after canonical JSON key ordering; it does not detect paraphrases, near-duplicates, or semantic leakage.",
            }
        finally:
            db.close()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--train-root", type=Path)
    parser.add_argument("--validation-root", type=Path)
    parser.add_argument("--test-root", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sample-limit", type=int, default=50)
    args = parser.parse_args(argv)
    roots = {name: root for name, root in (("train", args.train_root), ("validation", args.validation_root), ("test", args.test_root)) if root is not None}
    try:
        resolved_output = args.output.resolve(strict=False)
        for root in roots.values():
            resolved_root = root.resolve(strict=True)
            try:
                resolved_output.relative_to(resolved_root)
            except ValueError:
                continue
            raise SplitAuditError("O relatório precisa ficar fora das raízes dos datasets para preservar os dados originais")
        result = audit_splits(roots, args.sample_limit)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        rendered = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
        fd, temporary = tempfile.mkstemp(prefix=f".{args.output.name}.", suffix=".tmp", dir=str(args.output.parent))
        try:
            with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
                stream.write(rendered)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, args.output)
        except Exception:
            try:
                os.unlink(temporary)
            except OSError:
                pass
            raise
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        return 0 if result["status"] == "clean" else 3
    except (SplitAuditError, OSError, ValueError) as exc:
        print(f"dataset_split_auditor: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
