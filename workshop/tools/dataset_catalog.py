#!/usr/bin/env python3
"""Offline dataset inventory, content fingerprinting, and integrity verifier.

This deterministic utility is called by the single Kellvyn main intelligence.
It never trains a model, executes dataset code, modifies source data, or uses
network services. Only Python's standard library is required.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import tempfile
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1
DEFAULT_MAX_FILES = 100_000
DEFAULT_MAX_BYTES = 20 * 1024 * 1024 * 1024
IMAGE_EXT = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".tif", ".tiff", ".heic"}
AUDIO_EXT = {".wav", ".mp3", ".flac", ".ogg", ".m4a", ".aac", ".opus"}
VIDEO_EXT = {".mp4", ".mkv", ".mov", ".webm", ".avi", ".m4v"}
TABULAR_EXT = {".csv", ".tsv", ".parquet", ".feather", ".arrow", ".xls", ".xlsx"}
DOCUMENT_EXT = {".pdf", ".doc", ".docx", ".ppt", ".pptx", ".odt", ".rtf"}
ARCHIVE_EXT = {".zip", ".tar", ".gz", ".7z", ".rar", ".xz", ".zst"}
TEXT_EXT = {".txt", ".md", ".json", ".jsonl", ".ndjson", ".yaml", ".yml", ".xml", ".html", ".htm"}


class DatasetError(ValueError):
    """Raised when a dataset cannot be inventoried safely."""


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


def sha256_value(value: Any) -> str:
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def category_for(path: Path) -> str:
    ext = path.suffix.lower()
    if ext in IMAGE_EXT:
        return "image"
    if ext in AUDIO_EXT:
        return "audio"
    if ext in VIDEO_EXT:
        return "video"
    if ext in TABULAR_EXT:
        return "tabular"
    if ext in DOCUMENT_EXT:
        return "document"
    if ext in ARCHIVE_EXT:
        return "archive"
    if ext in TEXT_EXT:
        return "text"
    return "other"


def inspect_file(path: Path, validate_jsonl: bool) -> dict[str, Any]:
    before = path.stat()
    digest = hashlib.sha256()
    jsonl = path.suffix.lower() in {".jsonl", ".ndjson"} and validate_jsonl
    records = 0
    invalid_lines: list[int] = []
    invalid_count = 0
    with path.open("rb") as stream:
        if jsonl:
            for line_number, raw_line in enumerate(stream, 1):
                digest.update(raw_line)
                try:
                    text = raw_line.decode("utf-8").strip()
                    if not text:
                        raise ValueError("blank JSONL row")
                    json.loads(text)
                    records += 1
                except (UnicodeDecodeError, json.JSONDecodeError, ValueError):
                    invalid_count += 1
                    if len(invalid_lines) < 20:
                        invalid_lines.append(line_number)
        else:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    after = path.stat()
    stable_before = (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
    stable_after = (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
    if stable_before != stable_after:
        raise DatasetError(f"Arquivo mudou durante a leitura; repita a inspeção: {path.name}")
    result: dict[str, Any] = {
        "bytes": after.st_size,
        "sha256": digest.hexdigest(),
        "category": category_for(path),
    }
    if jsonl:
        result["jsonl"] = {"valid_records": records, "invalid_lines": invalid_count, "invalid_line_numbers_sample": invalid_lines}
    return result


def walk_files(root: Path, max_files: int):
    if root.is_symlink():
        raise DatasetError("A raiz do dataset não pode ser um link simbólico")
    try:
        root = root.resolve(strict=True)
    except OSError as exc:
        raise DatasetError(f"Raiz do dataset inválida: {exc}") from exc
    if not root.is_dir():
        raise DatasetError(f"A raiz não é uma pasta: {root}")
    stack = [root]
    found = []
    while stack:
        current = stack.pop()
        try:
            entries = sorted(os.scandir(current), key=lambda entry: entry.name.casefold(), reverse=True)
        except OSError as exc:
            raise DatasetError(f"Não foi possível ler a pasta {current}: {exc}") from exc
        for entry in entries:
            entry_path = Path(entry.path)
            if entry.is_symlink():
                raise DatasetError(f"Links simbólicos não são aceitos no dataset: {entry_path.relative_to(root)}")
            if entry.is_dir(follow_symlinks=False):
                stack.append(entry_path)
            elif entry.is_file(follow_symlinks=False):
                relative = entry_path.relative_to(root).as_posix()
                found.append((relative, entry_path))
                if len(found) > max_files:
                    raise DatasetError(f"Limite excedido: mais de {max_files} arquivos")
            else:
                raise DatasetError(f"Tipo de arquivo especial não suportado: {entry_path.relative_to(root)}")
    return root, sorted(found, key=lambda item: item[0])


def build_manifest(root: Path, max_files: int = DEFAULT_MAX_FILES,
                   max_bytes: int = DEFAULT_MAX_BYTES, validate_jsonl: bool = True) -> dict[str, Any]:
    if max_files < 1 or max_bytes < 1:
        raise DatasetError("Os limites de arquivos e bytes precisam ser positivos")
    resolved_root, entries = walk_files(root, max_files)
    files = []
    total_bytes = 0
    categories: dict[str, int] = defaultdict(int)
    hashes: dict[str, list[str]] = defaultdict(list)
    total_records = 0
    invalid_jsonl_rows = 0
    for relative, path in entries:
        info = inspect_file(path, validate_jsonl)
        total_bytes += info["bytes"]
        if total_bytes > max_bytes:
            raise DatasetError(f"Limite de tamanho excedido: mais de {max_bytes} bytes")
        categories[info["category"]] += 1
        hashes[info["sha256"]].append(relative)
        if "jsonl" in info:
            total_records += info["jsonl"]["valid_records"]
            invalid_jsonl_rows += info["jsonl"]["invalid_lines"]
        files.append({"path": relative, **info})
    fingerprint_payload = [{"path": item["path"], "bytes": item["bytes"], "sha256": item["sha256"]} for item in files]
    duplicates = [sorted(paths) for paths in hashes.values() if len(paths) > 1]
    warnings = []
    if not files:
        warnings.append("empty_dataset")
    if invalid_jsonl_rows:
        warnings.append("invalid_jsonl_rows")
    return {
        "schema": SCHEMA_VERSION,
        "status": "warnings" if warnings else "inspected",
        "root_label": resolved_root.name,
        "scanned_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "scan_options": {"max_files": max_files, "max_bytes": max_bytes, "validate_jsonl": validate_jsonl},
        "file_count": len(files),
        "total_bytes": total_bytes,
        "categories": dict(sorted(categories.items())),
        "duplicate_content_groups": duplicates,
        "jsonl_valid_records": total_records,
        "jsonl_invalid_rows": invalid_jsonl_rows,
        "warnings": warnings,
        "files": files,
        "dataset_sha256": sha256_value(fingerprint_payload),
        "fingerprint_definition": "SHA-256 of sorted relative paths, byte sizes, and per-file SHA-256 values",
    }


def write_json_atomic(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rendered = json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    fd, temp_path = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(rendered)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp_path, path)
    except Exception:
        try:
            os.unlink(temp_path)
        except OSError:
            pass
        raise


def inspect_dataset(root: Path, output: Path, max_files: int, max_bytes: int, validate_jsonl: bool) -> dict[str, Any]:
    resolved_root = root.resolve(strict=True)
    resolved_output = output.resolve(strict=False)
    try:
        resolved_output.relative_to(resolved_root)
    except ValueError:
        pass
    else:
        raise DatasetError("O manifesto precisa ficar fora da pasta do dataset para não alterar sua própria impressão digital")
    manifest = build_manifest(root, max_files, max_bytes, validate_jsonl)
    write_json_atomic(output, manifest)
    return manifest


def verify_dataset(root: Path, manifest_path: Path) -> dict[str, Any]:
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise DatasetError(f"Não foi possível ler o manifesto: {exc}") from exc
    if not isinstance(manifest, dict) or manifest.get("schema") != SCHEMA_VERSION:
        raise DatasetError("Manifesto de dataset não reconhecido")
    options = manifest.get("scan_options")
    if not isinstance(options, dict):
        raise DatasetError("Manifesto sem opções de inspeção")
    recorded_files = manifest.get("files")
    if not isinstance(recorded_files, list):
        raise DatasetError("Manifesto sem inventário de arquivos")
    try:
        fingerprint_payload = []
        for item in recorded_files:
            if not isinstance(item, dict) or not isinstance(item.get("path"), str):
                raise DatasetError("Manifesto contém entrada de arquivo inválida")
            if isinstance(item.get("bytes"), bool) or not isinstance(item.get("bytes"), int) or item["bytes"] < 0:
                raise DatasetError("Manifesto contém tamanho de arquivo inválido")
            digest = item.get("sha256")
            if not isinstance(digest, str) or len(digest) != 64 or any(ch not in "0123456789abcdef" for ch in digest):
                raise DatasetError("Manifesto contém hash de arquivo inválido")
            fingerprint_payload.append({"path": item["path"], "bytes": item["bytes"], "sha256": digest})
        if len({item["path"] for item in fingerprint_payload}) != len(fingerprint_payload):
            raise DatasetError("Manifesto contém caminhos duplicados")
        recorded_fingerprint = sha256_value(sorted(fingerprint_payload, key=lambda item: item["path"]))
    except (TypeError, KeyError) as exc:
        raise DatasetError(f"Manifesto inconsistente: {exc}") from exc
    if recorded_fingerprint != manifest.get("dataset_sha256"):
        raise DatasetError("O inventário do manifesto não corresponde à sua impressão digital")
    current = build_manifest(root, int(options.get("max_files", DEFAULT_MAX_FILES)),
                             int(options.get("max_bytes", DEFAULT_MAX_BYTES)),
                             bool(options.get("validate_jsonl", True)))
    expected = {item["path"]: item for item in recorded_files}
    actual = {item["path"]: item for item in current["files"]}
    added = sorted(set(actual) - set(expected))
    removed = sorted(set(expected) - set(actual))
    changed = sorted(path for path in set(actual) & set(expected)
                     if actual[path].get("sha256") != expected[path].get("sha256")
                     or actual[path].get("bytes") != expected[path].get("bytes"))
    verified = current["dataset_sha256"] == manifest.get("dataset_sha256")
    return {"status": "verified" if verified else "changed", "expected_dataset_sha256": manifest.get("dataset_sha256"),
            "actual_dataset_sha256": current["dataset_sha256"], "file_count": current["file_count"],
            "added": added, "removed": removed, "changed": changed,
            "warnings": current["warnings"]}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    inspect = commands.add_parser("inspect", help="Inventory and fingerprint a local dataset")
    inspect.add_argument("--root", type=Path, required=True)
    inspect.add_argument("--output", type=Path, required=True)
    inspect.add_argument("--max-files", type=int, default=DEFAULT_MAX_FILES)
    inspect.add_argument("--max-bytes", type=int, default=DEFAULT_MAX_BYTES)
    inspect.add_argument("--skip-jsonl-validation", action="store_true")
    verify = commands.add_parser("verify", help="Check a dataset against a saved fingerprint")
    verify.add_argument("--root", type=Path, required=True)
    verify.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "inspect":
            result = inspect_dataset(args.root, args.output, args.max_files, args.max_bytes,
                                     not args.skip_jsonl_validation)
        else:
            result = verify_dataset(args.root, args.manifest)
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        if args.command == "verify" and result["status"] != "verified":
            return 3
        return 0
    except (DatasetError, OSError, ValueError) as exc:
        print(f"dataset_catalog: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
