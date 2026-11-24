#!/usr/bin/env python3
"""Estimate a model's memory budget from an inspected artifact and KV-cache inputs.

This is a conservative planning calculator, not a runtime measurement or a
promise that a model will load successfully on a device.
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


class MemoryEstimateError(ValueError):
    """Raised when model memory estimate inputs are invalid."""


def read_inspection(path: Path) -> dict[str, Any]:
    if path.is_symlink():
        raise MemoryEstimateError("O relatório de inspeção não pode ser link simbólico")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise MemoryEstimateError(f"Relatório de inspeção inválido: {exc}") from exc
    if not isinstance(data, dict) or data.get("schema") != 1 or data.get("status") != "inspected":
        raise MemoryEstimateError("Relatório de inspeção não reconhecido")
    if data.get("format") not in {"gguf", "safetensors"}:
        raise MemoryEstimateError("Formato do relatório não suportado")
    digest = data.get("file_sha256")
    if not isinstance(digest, str) or len(digest) != 64 or any(ch not in "0123456789abcdef" for ch in digest):
        raise MemoryEstimateError("Relatório sem SHA-256 válido do modelo")
    return data


def positive_int(value: Any, field: str, allow_zero: bool = False) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise MemoryEstimateError(f"{field} precisa ser inteiro")
    if value < (0 if allow_zero else 1):
        raise MemoryEstimateError(f"{field} precisa ser {'maior ou igual a zero' if allow_zero else 'maior que zero'}")
    return value


def estimate_memory(
    inspection: dict[str, Any],
    context_tokens: int,
    usable_memory_mb: float,
    runtime_overhead_mb: float,
    safety_margin_percent: float = 25.0,
    layers: int | None = None,
    kv_heads: int | None = None,
    head_dim: int | None = None,
    kv_bytes_per_element: int = 2,
    explicit_kv_cache_mb: float | None = None,
) -> dict[str, Any]:
    context_tokens = positive_int(context_tokens, "context_tokens")
    kv_bytes_per_element = positive_int(kv_bytes_per_element, "kv_bytes_per_element")
    if kv_bytes_per_element not in {1, 2, 4, 8}:
        raise MemoryEstimateError("kv_bytes_per_element precisa ser 1, 2, 4 ou 8")
    if context_tokens > 1_048_576:
        raise MemoryEstimateError("context_tokens excede o limite de estimativa de 1.048.576")
    model_context = inspection.get("model_dimensions", {}).get("context_length") if isinstance(inspection.get("model_dimensions"), dict) else None
    if isinstance(model_context, int) and not isinstance(model_context, bool) and model_context > 0 and context_tokens > model_context:
        raise MemoryEstimateError(f"context_tokens excede o contexto declarado pelo modelo ({model_context})")
    for value, field in ((usable_memory_mb, "usable_memory_mb"),
                         (runtime_overhead_mb, "runtime_overhead_mb"),
                         (safety_margin_percent, "safety_margin_percent")):
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(float(value)):
            raise MemoryEstimateError(f"{field} precisa ser número finito")
        if value < 0:
            raise MemoryEstimateError(f"{field} não pode ser negativo")
    if usable_memory_mb <= 0:
        raise MemoryEstimateError("usable_memory_mb precisa ser maior que zero")
    if safety_margin_percent > 200:
        raise MemoryEstimateError("safety_margin_percent não pode exceder 200")
    weight_bytes = inspection.get("tensor_data_bytes")
    if isinstance(weight_bytes, bool) or not isinstance(weight_bytes, int) or weight_bytes <= 0:
        raise MemoryEstimateError("Relatório sem tamanho válido do buffer de tensores")
    if explicit_kv_cache_mb is not None:
        if isinstance(explicit_kv_cache_mb, bool) or not isinstance(explicit_kv_cache_mb, (int, float)):
            raise MemoryEstimateError("explicit_kv_cache_mb precisa ser numérico")
        if not math.isfinite(float(explicit_kv_cache_mb)) or explicit_kv_cache_mb < 0:
            raise MemoryEstimateError("explicit_kv_cache_mb precisa ser finito e não negativo")
        kv_cache_mb = float(explicit_kv_cache_mb)
        kv_method = "explicit_user_estimate"
        kv_details = None
    else:
        dims = inspection.get("model_dimensions", {})
        if not isinstance(dims, dict):
            dims = {}
        layers = layers if layers is not None else dims.get("block_count")
        kv_heads = kv_heads if kv_heads is not None else dims.get("attention.head_count_kv")
        embedding = dims.get("embedding_length")
        attention_heads = dims.get("attention.head_count")
        if head_dim is None and isinstance(embedding, int) and isinstance(attention_heads, int) and attention_heads > 0 and embedding % attention_heads == 0:
            head_dim = embedding // attention_heads
        missing = [name for name, value in (("layers", layers), ("kv_heads", kv_heads), ("head_dim", head_dim)) if value is None]
        if missing:
            raise MemoryEstimateError(
                "Dimensões KV incompletas; informe " + ", ".join("--" + x.replace("_", "-") for x in missing)
                + " ou forneça --kv-cache-mb"
            )
        layers = positive_int(layers, "layers")
        kv_heads = positive_int(kv_heads, "kv_heads")
        head_dim = positive_int(head_dim, "head_dim")
        kv_cache_bytes = 2 * layers * kv_heads * head_dim * context_tokens * kv_bytes_per_element
        kv_cache_mb = kv_cache_bytes / (1024 * 1024)
        kv_method = "standard_attention_kv_formula"
        kv_details = {
            "layers": layers, "kv_heads": kv_heads, "head_dim": head_dim,
            "context_tokens": context_tokens, "bytes_per_element": kv_bytes_per_element,
            "formula": "2 * layers * kv_heads * head_dim * context_tokens * bytes_per_element",
        }
    weight_mb = weight_bytes / (1024 * 1024)
    base_mb = weight_mb + kv_cache_mb + float(runtime_overhead_mb)
    margin_mb = base_mb * float(safety_margin_percent) / 100.0
    estimated_mb = base_mb + margin_mb
    headroom_mb = float(usable_memory_mb) - estimated_mb
    return {
        "schema": 1,
        "status": "within_budget_estimate" if headroom_mb >= 0 else "exceeds_budget_estimate",
        "model": {
            "file": inspection.get("file"),
            "format": inspection.get("format"),
            "file_sha256": inspection["file_sha256"],
            "tensor_data_bytes": weight_bytes,
            "parameter_count": inspection.get("parameter_count"),
        },
        "inputs": {
            "context_tokens": context_tokens,
            "usable_memory_mb": float(usable_memory_mb),
            "runtime_overhead_mb": float(runtime_overhead_mb),
            "safety_margin_percent": float(safety_margin_percent),
            "kv_bytes_per_element": kv_bytes_per_element,
        },
        "estimates_mb": {
            "weights": weight_mb,
            "kv_cache": kv_cache_mb,
            "runtime_overhead": float(runtime_overhead_mb),
            "safety_margin": margin_mb,
            "total": estimated_mb,
            "headroom": headroom_mb,
        },
        "kv_cache_method": kv_method,
        "kv_cache_details": kv_details,
        "limitations": [
            "Estimate only; does not load the model or measure process RSS.",
            "GGUF/Safetensors tensor bytes are not guaranteed to be fully resident in RAM by every runtime.",
            "Actual memory also depends on activations, compute buffers, allocator fragmentation, GPU/NPU copies, tokenizer, context implementation, and other apps.",
            "KV formula assumes standard attention with K and V caches; hybrid, MLA, recurrent, sliding-window, or architecture-specific caches may differ.",
            "usable_memory_mb must represent memory actually available to the model, not total physical RAM.",
        ],
    }


def write_json_atomic(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rendered = json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
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


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inspection", type=Path, required=True)
    parser.add_argument("--context-tokens", type=int, required=True)
    parser.add_argument("--usable-memory-mb", type=float, required=True)
    parser.add_argument("--runtime-overhead-mb", type=float, required=True)
    parser.add_argument("--safety-margin-percent", type=float, default=25.0)
    parser.add_argument("--layers", type=int)
    parser.add_argument("--kv-heads", type=int)
    parser.add_argument("--head-dim", type=int)
    parser.add_argument("--kv-bytes-per-element", type=int, default=2)
    parser.add_argument("--kv-cache-mb", type=float)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        inspection = read_inspection(args.inspection)
        result = estimate_memory(
            inspection, args.context_tokens, args.usable_memory_mb,
            args.runtime_overhead_mb, args.safety_margin_percent,
            args.layers, args.kv_heads, args.head_dim,
            args.kv_bytes_per_element, args.kv_cache_mb,
        )
        write_json_atomic(args.output, result)
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        return 0 if result["status"] == "within_budget_estimate" else 3
    except (MemoryEstimateError, OSError, ValueError) as exc:
        print(f"model_memory_estimator: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
