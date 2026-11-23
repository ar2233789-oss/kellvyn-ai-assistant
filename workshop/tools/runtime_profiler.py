#!/usr/bin/env python3
"""Aggregate offline runtime measurements against a fixed evaluation suite.

The profiler is a passive calculator invoked by the single Kellvyn main
intelligence. It never runs a model, executes commands, or records prompts.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
import tempfile
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dataset_catalog import build_manifest  # noqa: E402

SCHEMA_VERSION = 1


class RuntimeProfileError(ValueError):
    """Raised when the measurement file or benchmark suite is invalid."""


def read_json(path: Path) -> Any:
    if path.is_symlink():
        raise RuntimeProfileError(f"Links simbólicos não são aceitos: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RuntimeProfileError(f"Não foi possível ler JSON válido em {path}: {exc}") from exc


def read_measurements(path: Path) -> list[dict[str, Any]]:
    if path.is_symlink():
        raise RuntimeProfileError("O arquivo de medições não pode ser link simbólico")
    items = []
    try:
        with path.open("rb") as stream:
            for line_number, raw in enumerate(stream, 1):
                if not raw.strip():
                    raise RuntimeProfileError(f"Linha vazia no arquivo de medições: {line_number}")
                try:
                    item = json.loads(raw.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise RuntimeProfileError(f"JSONL inválido na linha {line_number}: {exc}") from exc
                if not isinstance(item, dict):
                    raise RuntimeProfileError(f"A linha {line_number} precisa conter um objeto JSON")
                items.append(item)
    except OSError as exc:
        raise RuntimeProfileError(f"Não foi possível ler as medições: {exc}") from exc
    if not items:
        raise RuntimeProfileError("O arquivo de medições está vazio")
    return items


def percentile(values: list[float], percentile_value: float) -> float:
    if not values:
        raise RuntimeProfileError("Não é possível calcular percentil sem medições")
    if not 0 <= percentile_value <= 100:
        raise RuntimeProfileError("Percentil precisa estar entre 0 e 100")
    ordered = sorted(values)
    position = (len(ordered) - 1) * percentile_value / 100.0
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    fraction = position - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * fraction


def finite_number(value: Any, field: str, *, minimum: float = 0.0, strict: bool = False) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(float(value)):
        raise RuntimeProfileError(f"{field} precisa ser um número finito")
    number = float(value)
    if number < minimum or (strict and number == minimum):
        relation = "maior que" if strict else "maior ou igual a"
        raise RuntimeProfileError(f"{field} precisa ser {relation} {minimum}")
    return number


def nonnegative_int(value: Any, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise RuntimeProfileError(f"{field} precisa ser um inteiro maior ou igual a zero")
    return value


def load_suite(suite_root: Path, suite_file: Path) -> tuple[list[str], str]:
    if suite_root.is_symlink() or suite_file.is_symlink():
        raise RuntimeProfileError("Raiz e arquivo da suíte não podem ser links simbólicos")
    try:
        root = suite_root.resolve(strict=True)
        file_path = suite_file.resolve(strict=True)
        file_path.relative_to(root)
    except (OSError, ValueError) as exc:
        raise RuntimeProfileError("Arquivo da suíte precisa existir dentro da raiz de referência") from exc
    if not root.is_dir() or not file_path.is_file():
        raise RuntimeProfileError("Raiz da suíte e arquivo precisam ser diretório e arquivo regular")
    suite = read_json(file_path)
    cases = suite.get("cases") if isinstance(suite, dict) else None
    if not isinstance(cases, list) or not cases:
        raise RuntimeProfileError("A suíte precisa conter uma lista não vazia chamada cases")
    ids = []
    for index, case in enumerate(cases):
        if not isinstance(case, dict) or not isinstance(case.get("id"), str) or not case["id"].strip():
            raise RuntimeProfileError(f"Caso {index} precisa ter id textual não vazio")
        ids.append(case["id"])
    if len(set(ids)) != len(ids):
        raise RuntimeProfileError("A suíte contém IDs de caso duplicados")
    suite_hash = build_manifest(root)["dataset_sha256"]
    return ids, suite_hash


def validate_measurement(item: dict[str, Any], line_number: int) -> dict[str, Any]:
    case_id = item.get("case_id")
    if not isinstance(case_id, str) or not case_id.strip():
        raise RuntimeProfileError(f"case_id ausente/inválido na linha {line_number}")
    status = item.get("status")
    if status not in {"success", "error"}:
        raise RuntimeProfileError(f"status precisa ser success ou error na linha {line_number}")
    output = {"case_id": case_id, "status": status}
    if status == "error":
        # Do not copy exception messages: they may contain prompts or sensitive payloads.
        return output
    latency = finite_number(item.get("latency_ms"), f"latency_ms na linha {line_number}", strict=True)
    input_tokens = nonnegative_int(item.get("input_tokens"), f"input_tokens na linha {line_number}")
    output_tokens = nonnegative_int(item.get("output_tokens"), f"output_tokens na linha {line_number}")
    output.update({"latency_ms": latency, "input_tokens": input_tokens, "output_tokens": output_tokens})
    ttft = item.get("time_to_first_token_ms")
    if ttft is not None:
        ttft = finite_number(ttft, f"time_to_first_token_ms na linha {line_number}")
        if ttft > latency:
            raise RuntimeProfileError(f"TTFT maior que a latência total na linha {line_number}")
        if output_tokens == 0:
            raise RuntimeProfileError(f"TTFT informado sem tokens de saída na linha {line_number}")
        output["time_to_first_token_ms"] = ttft
    elif output_tokens > 0:
        raise RuntimeProfileError(f"TTFT precisa ser informado quando há tokens de saída (linha {line_number})")
    memory = item.get("peak_memory_mb")
    if memory is not None:
        output["peak_memory_mb"] = finite_number(memory, f"peak_memory_mb na linha {line_number}")
    return output


def profile(suite_ids: list[str], suite_sha256: str, measurements: list[dict[str, Any]]) -> dict[str, Any]:
    expected = set(suite_ids)
    seen: dict[str, dict[str, Any]] = {}
    for index, item in enumerate(measurements, 1):
        row = validate_measurement(item, index)
        case_id = row["case_id"]
        if case_id in seen:
            raise RuntimeProfileError(f"Medição duplicada para case_id: {case_id}")
        seen[case_id] = row
    extra = sorted(set(seen) - expected)
    missing = sorted(expected - set(seen))
    failed = sorted(case_id for case_id, row in seen.items() if row["status"] != "success")
    successful = [seen[case_id] for case_id in suite_ids if case_id in seen and seen[case_id]["status"] == "success"]
    completed_ids = {row["case_id"] for row in successful}
    missing_or_failed = sorted(expected - completed_ids)
    coverage = len(completed_ids) / len(suite_ids)
    metrics: dict[str, float | int] = {
        "coverage": coverage,
        "matched_count": len(completed_ids),
        "case_count": len(suite_ids),
        "missing_prediction_count": len(missing_or_failed),
        "extra_prediction_count": len(extra),
    }
    details: dict[str, Any] = {
        "missing_case_ids": sorted(set(missing) | set(failed)),
        "failed_case_ids": failed,
        "extra_case_ids": extra,
    }
    if successful:
        latencies = [row["latency_ms"] for row in successful]
        metrics.update({
            "latency_ms": sum(latencies) / len(latencies),
            "latency_p50_ms": percentile(latencies, 50),
            "latency_p95_ms": percentile(latencies, 95),
        })
        ttft_values = [row["time_to_first_token_ms"] for row in successful if "time_to_first_token_ms" in row]
        if ttft_values:
            metrics["time_to_first_token_ms"] = sum(ttft_values) / len(ttft_values)
            metrics["time_to_first_token_p95_ms"] = percentile(ttft_values, 95)
            metrics["ttft_coverage"] = len(ttft_values) / len(successful)
        total_output_tokens = sum(row["output_tokens"] for row in successful)
        total_latency = sum(latencies)
        if total_latency > 0 and total_output_tokens > 0:
            metrics["end_to_end_output_tokens_per_second"] = total_output_tokens * 1000.0 / total_latency
        decode_tokens = 0
        decode_ms = 0.0
        for row in successful:
            if row["output_tokens"] > 0 and "time_to_first_token_ms" in row:
                decode_tokens += max(0, row["output_tokens"] - 1)
                decode_ms += max(0.0, row["latency_ms"] - row["time_to_first_token_ms"])
        if decode_tokens > 0 and decode_ms > 0:
            metrics["decode_tokens_per_second"] = decode_tokens * 1000.0 / decode_ms
        memory_values = [row["peak_memory_mb"] for row in successful if "peak_memory_mb" in row]
        if memory_values:
            metrics["peak_memory_mb"] = max(memory_values)
            metrics["peak_memory_mean_mb"] = sum(memory_values) / len(memory_values)
            metrics["peak_memory_coverage"] = len(memory_values) / len(successful)
        metrics["input_tokens_total"] = sum(row["input_tokens"] for row in successful)
        metrics["output_tokens_total"] = total_output_tokens
    status = "complete" if not missing_or_failed and not extra and len(successful) == len(suite_ids) else "partial"
    return {
        "schema": SCHEMA_VERSION,
        "status": status,
        "task": "runtime",
        "evaluation_suite_sha256": suite_sha256,
        "metrics": metrics,
        "measurements_received": len(measurements),
        "successful_measurements": len(successful),
        **details,
        "metric_notes": {
            "latency_ms": "Arithmetic mean total request latency across successful cases.",
            "latency_p50_ms": "Linear-interpolated 50th percentile of total request latency.",
            "latency_p95_ms": "Linear-interpolated 95th percentile of total request latency.",
            "time_to_first_token_ms": "Mean TTFT only among successful measurements with TTFT supplied.",
            "decode_tokens_per_second": "Aggregate (output_tokens - 1) divided by time after first token; do not compare if tokenization/runtime differs.",
            "end_to_end_output_tokens_per_second": "Total output tokens divided by total request latency; includes prefill and time to first token.",
            "peak_memory_mb": "Maximum supplied per-request peak memory; measurement method must be held constant.",
        },
        "privacy_note": "No prompts, completions, or error messages are copied; only case IDs and numerical measurements are reported.",
    }


def write_json_atomic(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rendered = json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(rendered)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    except Exception:
        try:
            os.unlink(temporary)
        except OSError:
            pass
        raise


def run_profile(suite_root: Path, suite_file: Path, measurements_file: Path,
                output: Path) -> dict[str, Any]:
    resolved_root = suite_root.resolve(strict=True)
    for candidate, label in ((measurements_file, "medições"), (output, "relatório")):
        if candidate.is_symlink():
            raise RuntimeProfileError(f"O arquivo de {label} não pode ser link simbólico")
        resolved_candidate = candidate.resolve(strict=False)
        try:
            resolved_candidate.relative_to(resolved_root)
        except ValueError:
            continue
        raise RuntimeProfileError(f"O arquivo de {label} precisa ficar fora da raiz da suíte de referência")
    suite_ids, suite_hash = load_suite(suite_root, suite_file)
    rows = read_measurements(measurements_file)
    result = profile(suite_ids, suite_hash, rows)
    write_json_atomic(output, result)
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite-root", type=Path, required=True)
    parser.add_argument("--suite-file", type=Path, required=True)
    parser.add_argument("--measurements", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        result = run_profile(args.suite_root, args.suite_file, args.measurements, args.output)
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        return 0 if result["status"] == "complete" else 3
    except (RuntimeProfileError, OSError, ValueError) as exc:
        print(f"runtime_profiler: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
