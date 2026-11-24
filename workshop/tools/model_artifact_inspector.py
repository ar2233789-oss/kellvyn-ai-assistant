#!/usr/bin/env python3
"""Inspect model weight metadata without loading tensors or executing model code.

Supported formats: GGUF v2/v3 and Safetensors single-file checkpoints.
Only the Python standard library is required.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import struct
import sys
from collections import Counter
from pathlib import Path
from typing import Any, BinaryIO

MAX_HEADER_BYTES = 64 * 1024 * 1024
MAX_TENSORS = 500_000
MAX_METADATA_ITEMS = 1_000_000
MAX_STRING_BYTES = 16 * 1024 * 1024
SAFETENSORS_DTYPE_BYTES = {
    "BOOL": 1, "U8": 1, "I8": 1, "I16": 2, "I32": 4,
    "I64": 8, "F8_E4M3": 1,
    "F16": 2, "BF16": 2, "F32": 4, "F64": 8,
}
GGUF_VALUE_TYPES = {
    0: ("UINT8", "<B", 1), 1: ("INT8", "<b", 1),
    2: ("UINT16", "<H", 2), 3: ("INT16", "<h", 2),
    4: ("UINT32", "<I", 4), 5: ("INT32", "<i", 4),
    6: ("FLOAT32", "<f", 4), 7: ("BOOL", "<?", 1),
    8: ("STRING", None, None), 9: ("ARRAY", None, None),
    10: ("UINT64", "<Q", 8), 11: ("INT64", "<q", 8),
    12: ("FLOAT64", "<d", 8),
}
GGML_TENSOR_TYPES = {
    0: "F32", 1: "F16", 2: "Q4_0", 3: "Q4_1", 6: "Q5_0",
    7: "Q5_1", 8: "Q8_0", 9: "Q8_1", 10: "Q2_K", 11: "Q3_K",
    12: "Q4_K", 13: "Q5_K", 14: "Q6_K", 15: "Q8_K",
    16: "IQ2_XXS", 17: "IQ2_XS", 18: "IQ3_XXS", 19: "IQ1_S",
    20: "IQ4_NL", 21: "IQ3_S", 22: "IQ2_S", 23: "IQ4_XS",
    24: "I8", 25: "I16", 26: "I32", 27: "I64", 28: "F64",
    29: "IQ1_M", 30: "BF16", 34: "TQ1_0", 35: "TQ2_0",
    39: "MXFP4", 40: "NVFP4", 41: "Q1_0", 42: "Q2_0",
    43: "F8_E4M3",
}


class ModelArtifactError(ValueError):
    """Raised when a model artifact is malformed or exceeds inspection limits."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def checked_file(path: Path) -> tuple[Path, int]:
    if path.is_symlink():
        raise ModelArtifactError("O arquivo do modelo não pode ser link simbólico")
    try:
        resolved = path.resolve(strict=True)
        info = resolved.stat()
    except OSError as exc:
        raise ModelArtifactError(f"Não foi possível abrir o arquivo: {exc}") from exc
    if not resolved.is_file():
        raise ModelArtifactError("O caminho informado não é um arquivo regular")
    return resolved, info.st_size


def json_no_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ModelArtifactError(f"Chave JSON duplicada: {key}")
        result[key] = value
    return result


def inspect_safetensors(path: Path) -> dict[str, Any]:
    resolved, file_size = checked_file(path)
    if file_size < 8:
        raise ModelArtifactError("Arquivo Safetensors menor que o cabeçalho mínimo")
    with resolved.open("rb") as stream:
        header_length = struct.unpack("<Q", stream.read(8))[0]
        if header_length < 2 or header_length > MAX_HEADER_BYTES or header_length > file_size - 8:
            raise ModelArtifactError("Tamanho de cabeçalho Safetensors inválido ou acima do limite")
        header_bytes = stream.read(header_length)
    if not header_bytes.startswith(b"{"):
        raise ModelArtifactError("Cabeçalho Safetensors precisa começar com objeto JSON")
    try:
        header = json.loads(header_bytes.decode("utf-8"), object_pairs_hook=json_no_duplicate_keys)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ModelArtifactError(f"Cabeçalho Safetensors inválido: {exc}") from exc
    if not isinstance(header, dict):
        raise ModelArtifactError("Cabeçalho Safetensors precisa ser um objeto JSON")
    metadata = header.get("__metadata__", {})
    if not isinstance(metadata, dict) or any(not isinstance(k, str) or not isinstance(v, str) for k, v in metadata.items()):
        raise ModelArtifactError("__metadata__ precisa ser um mapa de texto para texto")
    data_size = file_size - 8 - header_length
    tensor_rows = []
    ranges = []
    dtype_counts: Counter[str] = Counter()
    parameter_count = 0
    for name, info in header.items():
        if name == "__metadata__":
            continue
        if not isinstance(name, str) or not isinstance(info, dict):
            raise ModelArtifactError("Entrada de tensor Safetensors inválida")
        dtype = info.get("dtype")
        if not isinstance(dtype, str):
            raise ModelArtifactError(f"Tipo Safetensors inválido no tensor {name}")
        shape = info.get("shape")
        offsets = info.get("data_offsets")
        if dtype not in SAFETENSORS_DTYPE_BYTES:
            raise ModelArtifactError(f"Tipo Safetensors não suportado: {dtype}")
        if not isinstance(shape, list) or any(isinstance(x, bool) or not isinstance(x, int) or x < 0 for x in shape):
            raise ModelArtifactError(f"Dimensões inválidas no tensor {name}")
        if not isinstance(offsets, list) or len(offsets) != 2 or any(isinstance(x, bool) or not isinstance(x, int) for x in offsets):
            raise ModelArtifactError(f"Offsets inválidos no tensor {name}")
        begin, end = offsets
        if begin < 0 or end < begin or end > data_size:
            raise ModelArtifactError(f"Offsets fora do buffer de dados no tensor {name}")
        elements = math.prod(shape) if shape else 1
        expected_bytes = elements * SAFETENSORS_DTYPE_BYTES[dtype]
        if end - begin != expected_bytes:
            raise ModelArtifactError(f"Tamanho incompatível entre shape/dtype e offsets no tensor {name}")
        if end > begin:
            ranges.append((begin, end, name))
        parameter_count += elements
        dtype_counts[dtype] += 1
        if len(tensor_rows) < 20:
            tensor_rows.append({"name": name, "dtype": dtype, "shape": shape, "parameters": elements})
        if len(dtype_counts) > 64 or len(tensor_rows) > MAX_TENSORS:
            raise ModelArtifactError("Limite de tensores excedido")
    if len(header) - (1 if "__metadata__" in header else 0) > MAX_TENSORS:
        raise ModelArtifactError("Quantidade de tensores acima do limite")
    ranges.sort()
    cursor = 0
    for begin, end, name in ranges:
        if begin < cursor:
            raise ModelArtifactError(f"Regiões de dados sobrepostas: {name}")
        if begin != cursor:
            raise ModelArtifactError(f"Buffer Safetensors contém lacuna antes do tensor {name}")
        cursor = end
    if cursor != data_size:
        raise ModelArtifactError("O buffer Safetensors contém bytes não indexados pelos tensores")
    return {
        "schema": 1, "status": "inspected", "format": "safetensors",
        "file": resolved.name, "file_bytes": file_size, "file_sha256": sha256_file(resolved),
        "header_bytes": header_length, "tensor_data_bytes": data_size,
        "tensor_count": sum(dtype_counts.values()), "parameter_count": parameter_count,
        "dtype_tensor_counts": dict(sorted(dtype_counts.items())),
        "metadata": metadata, "tensor_samples": tensor_rows,
        "inspection_scope": "Header and tensor layout only; tensor weights were not loaded.",
    }


class GGUFReader:
    def __init__(self, stream: BinaryIO, file_size: int):
        self.stream = stream
        self.file_size = file_size

    def read(self, size: int) -> bytes:
        if size < 0 or size > MAX_STRING_BYTES or self.stream.tell() + size > self.file_size:
            raise ModelArtifactError("Leitura GGUF fora dos limites do arquivo")
        data = self.stream.read(size)
        if len(data) != size:
            raise ModelArtifactError("Arquivo GGUF truncado")
        return data

    def unpack(self, fmt: str) -> Any:
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.read(size))[0]

    def string(self) -> str:
        length = self.unpack("<Q")
        if length > MAX_STRING_BYTES:
            raise ModelArtifactError("String GGUF excede o limite de inspeção")
        try:
            return self.read(length).decode("utf-8")
        except UnicodeDecodeError as exc:
            raise ModelArtifactError("String GGUF não é UTF-8 válido") from exc

    def value(self, value_type: int, keep: bool = True) -> Any:
        if value_type not in GGUF_VALUE_TYPES:
            raise ModelArtifactError(f"Tipo de metadado GGUF desconhecido: {value_type}")
        name, fmt, size = GGUF_VALUE_TYPES[value_type]
        if name == "STRING":
            value = self.string()
            return value if keep else None
        if name == "ARRAY":
            element_type = self.unpack("<I")
            count = self.unpack("<Q")
            if count > MAX_METADATA_ITEMS or element_type == 9:
                raise ModelArtifactError("Array GGUF excede o limite ou contém array aninhado")
            if element_type not in GGUF_VALUE_TYPES:
                raise ModelArtifactError("Tipo de elemento de array GGUF inválido")
            element_name, element_fmt, element_size = GGUF_VALUE_TYPES[element_type]
            if element_name == "STRING":
                values = []
                for _ in range(count):
                    item = self.string()
                    if keep and len(values) < 32:
                        values.append(item)
                return {"type": element_name, "count": count, "sample": values} if keep else None
            if element_name == "ARRAY" or element_size is None:
                raise ModelArtifactError("Tipo de array GGUF não suportado")
            total = count * element_size
            if total > MAX_HEADER_BYTES:
                raise ModelArtifactError("Array numérico GGUF excede o limite de inspeção")
            raw = self.read(total)
            if not keep:
                return None
            if element_fmt is None:
                raise ModelArtifactError("Formato numérico GGUF inválido")
            values = list(struct.unpack("<" + element_fmt[1:] * count, raw)) if count else []
            return {"type": element_name, "count": count, "sample": values[:32]}
        if fmt is None:
            raise ModelArtifactError(f"Tipo GGUF não suportado: {name}")
        value = self.unpack(fmt)
        if name == "BOOL" and value not in (False, True):
            raise ModelArtifactError("Booleano GGUF inválido")
        if isinstance(value, float) and not math.isfinite(value):
            raise ModelArtifactError("Metadado numérico GGUF não finito")
        return value if keep else None


def inspect_gguf(path: Path) -> dict[str, Any]:
    resolved, file_size = checked_file(path)
    with resolved.open("rb") as stream:
        reader = GGUFReader(stream, file_size)
        if reader.read(4) != b"GGUF":
            raise ModelArtifactError("Assinatura GGUF inválida")
        version = reader.unpack("<I")
        if version not in (2, 3):
            raise ModelArtifactError(f"Versão GGUF não suportada: {version}")
        tensor_count = reader.unpack("<Q")
        metadata_count = reader.unpack("<Q")
        if tensor_count > MAX_TENSORS or metadata_count > MAX_METADATA_ITEMS:
            raise ModelArtifactError("Cabeçalho GGUF excede os limites de inspeção")
        metadata = {}
        for _ in range(metadata_count):
            key = reader.string()
            if not key or key in metadata:
                raise ModelArtifactError("Chave GGUF vazia ou duplicada")
            value_type = reader.unpack("<I")
            metadata[key] = reader.value(value_type, keep=key in {
                "general.architecture", "general.name", "general.basename",
                "general.file_type", "general.quantization_version", "general.alignment",
                "tokenizer.ggml.model", "tokenizer.ggml.pre",
            } or key.endswith(".context_length") or key.endswith(".embedding_length"))
        tensor_types: Counter[str] = Counter()
        tensor_parameters = 0
        tensor_offsets = []
        tensor_samples = []
        tensor_names = set()
        for index in range(tensor_count):
            name = reader.string()
            if not name or name in tensor_names:
                raise ModelArtifactError("Nome de tensor GGUF vazio ou duplicado")
            tensor_names.add(name)
            dimensions = reader.unpack("<I")
            if dimensions > 4:
                raise ModelArtifactError(f"Tensor GGUF com dimensões demais: {name}")
            shape = [reader.unpack("<Q") for _ in range(dimensions)]
            if any(dimension == 0 for dimension in shape):
                raise ModelArtifactError(f"Tensor GGUF com dimensão zero: {name}")
            type_id = reader.unpack("<I")
            offset = reader.unpack("<Q")
            if type_id not in GGML_TENSOR_TYPES:
                raise ModelArtifactError(f"Tipo de tensor GGUF desconhecido: {type_id}")
            tensor_types[GGML_TENSOR_TYPES[type_id]] += 1
            parameters = math.prod(shape) if shape else 1
            tensor_parameters += parameters
            tensor_offsets.append((offset, name))
            if len(tensor_samples) < 20:
                tensor_samples.append({"name": name, "shape": shape, "type": GGML_TENSOR_TYPES[type_id]})
        if stream.tell() > MAX_HEADER_BYTES:
            raise ModelArtifactError("Cabeçalho GGUF excede o limite de 64 MiB")
        alignment = metadata.get("general.alignment", 32)
        if isinstance(alignment, bool) or not isinstance(alignment, int) or alignment <= 0 or alignment > 4096:
            raise ModelArtifactError("general.alignment GGUF inválido")
        data_start = ((stream.tell() + alignment - 1) // alignment) * alignment if tensor_count else stream.tell()
        data_size = file_size - data_start
        for offset, name in tensor_offsets:
            if data_size <= 0 or offset >= data_size:
                raise ModelArtifactError(f"Offset GGUF fora do buffer de tensores: {name}")
            if offset % alignment:
                raise ModelArtifactError(f"Offset GGUF desalinhado: {name}")
    architecture = metadata.get("general.architecture")
    model_fields = {}
    if isinstance(architecture, str):
        for suffix in ("context_length", "embedding_length", "block_count", "feed_forward_length",
                       "attention.head_count", "attention.head_count_kv", "vocab_size"):
            key = f"{architecture}.{suffix}"
            if key in metadata:
                model_fields[suffix] = metadata[key]
    return {
        "schema": 1, "status": "inspected", "format": "gguf", "gguf_version": version,
        "file": resolved.name, "file_bytes": file_size, "file_sha256": sha256_file(resolved),
        "architecture": architecture, "model_name": metadata.get("general.name"),
        "file_type": metadata.get("general.file_type"),
        "quantization_version": metadata.get("general.quantization_version"),
        "tokenizer_model": metadata.get("tokenizer.ggml.model"),
        "model_dimensions": model_fields, "tensor_count": tensor_count,
        "parameter_count": tensor_parameters, "tensor_data_bytes": data_size,
        "tensor_type_counts": dict(sorted(tensor_types.items())),
        "tensor_samples": tensor_samples,
        "inspection_scope": "GGUF metadata and tensor descriptors only; tensor weights were not loaded.",
        "compatibility_note": "Recognized format does not prove that a particular runtime/device supports every architecture or quantization.",
    }


def inspect_model(path: Path) -> dict[str, Any]:
    suffix = path.suffix.lower()
    if suffix == ".gguf":
        return inspect_gguf(path)
    if suffix == ".safetensors":
        return inspect_safetensors(path)
    raise ModelArtifactError("Formato não suportado; use um arquivo .gguf ou .safetensors")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-file", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        result = inspect_model(args.model_file)
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        return 0
    except (ModelArtifactError, OSError, ValueError) as exc:
        print(f"model_artifact_inspector: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
