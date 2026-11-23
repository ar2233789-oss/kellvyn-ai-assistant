#!/usr/bin/env python3
"""Offline experiment recorder, artifact verifier, and deterministic comparator.

This is a tool invoked by the single Kellvyn main intelligence. It is not an
agent, does not make plans, and never executes project-supplied commands.
Only Python's standard library is required.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any

SCHEMA_VERSION = 1
RUN_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")
SHA256_RE = re.compile(r"^[a-f0-9]{64}$")
ALLOWED_KINDS = {
    "language-model", "vision-model", "audio-model", "multimodal-model",
    "traditional-ml", "application", "workflow", "dataset", "other",
}
REQUIRED_CONTEXT_FIELDS = ("dataset_sha256", "evaluation_suite_sha256", "device", "runtime", "precision")


class ExperimentError(ValueError):
    """Raised when an experiment record or artifact is invalid."""


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_value(value: Any) -> str:
    return sha256_bytes(canonical_json(value).encode("utf-8"))


def read_json(path: Path) -> Any:
    if path.is_symlink():
        raise ExperimentError(f"Links simbólicos não são aceitos: {path}")
    try:
        with path.open("r", encoding="utf-8") as stream:
            return json.load(stream)
    except (OSError, json.JSONDecodeError) as exc:
        raise ExperimentError(f"Não foi possível ler JSON válido em {path}: {exc}") from exc


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


def validate_sha256(value: Any, field: str) -> None:
    if not isinstance(value, str) or not SHA256_RE.fullmatch(value):
        raise ExperimentError(f"{field} precisa ser um SHA-256 hexadecimal de 64 caracteres")


def validate_metric_map(metrics: Any) -> dict[str, float]:
    if not isinstance(metrics, dict) or not metrics:
        raise ExperimentError("metrics precisa ser um objeto JSON não vazio")
    normalized: dict[str, float] = {}
    for name, value in metrics.items():
        if not isinstance(name, str) or not name.strip():
            raise ExperimentError("Toda métrica precisa ter um nome não vazio")
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(float(value)):
            raise ExperimentError(f"A métrica '{name}' precisa ser um número finito")
        normalized[name] = float(value)
    return normalized


def resolve_artifact(root: Path, relative: Any) -> tuple[str, Path]:
    if not isinstance(relative, str) or not relative.strip():
        raise ExperimentError("O caminho de cada artefato precisa ser uma string não vazia")
    if "\\" in relative:
        raise ExperimentError(f"Use caminhos relativos com '/' em vez de barras invertidas: {relative}")
    rel = PurePosixPath(relative)
    if rel.is_absolute() or any(part in ("", ".", "..") for part in rel.parts):
        raise ExperimentError(f"Caminho de artefato inseguro: {relative}")
    root_resolved = root.resolve(strict=True)
    candidate = root
    for part in rel.parts:
        candidate = candidate / part
        if candidate.is_symlink():
            raise ExperimentError(f"Links simbólicos não são aceitos como artefatos: {relative}")
    try:
        resolved = candidate.resolve(strict=True)
        resolved.relative_to(root_resolved)
    except (OSError, ValueError) as exc:
        raise ExperimentError(f"Artefato fora da pasta ou inexistente: {relative}") from exc
    if not resolved.is_file():
        raise ExperimentError(f"Artefato não é um arquivo regular: {relative}")
    return rel.as_posix(), resolved


def validate_comparison_context(context: Any) -> dict[str, Any]:
    if not isinstance(context, dict):
        raise ExperimentError("comparison_context precisa ser um objeto JSON")
    try:
        canonical_json(context)
    except (TypeError, ValueError) as exc:
        raise ExperimentError(f"comparison_context não é JSON canônico válido: {exc}") from exc
    missing = [field for field in REQUIRED_CONTEXT_FIELDS if field not in context]
    if missing:
        raise ExperimentError("comparison_context incompleto: " + ", ".join(missing))
    validate_sha256(context["dataset_sha256"], "comparison_context.dataset_sha256")
    validate_sha256(context["evaluation_suite_sha256"], "comparison_context.evaluation_suite_sha256")
    for field in ("device", "runtime", "precision"):
        if not isinstance(context[field], str) or not context[field].strip():
            raise ExperimentError(f"comparison_context.{field} precisa ser uma string não vazia")
    return context


def validate_spec(spec: Any) -> dict[str, Any]:
    if not isinstance(spec, dict):
        raise ExperimentError("A especificação precisa ser um objeto JSON")
    run_id = spec.get("run_id")
    if not isinstance(run_id, str) or not RUN_ID_RE.fullmatch(run_id):
        raise ExperimentError("run_id inválido; use 1–80 caracteres: letras, números, ponto, '_' ou '-'")
    experiment = spec.get("experiment")
    if not isinstance(experiment, str) or not experiment.strip():
        raise ExperimentError("experiment precisa identificar o experimento")
    kind = spec.get("kind")
    if not isinstance(kind, str) or kind not in ALLOWED_KINDS:
        raise ExperimentError(f"kind precisa ser um destes valores: {', '.join(sorted(ALLOWED_KINDS))}")
    subject = spec.get("subject")
    if not isinstance(subject, dict) or not isinstance(subject.get("name"), str) or not subject["name"].strip():
        raise ExperimentError("subject.name é obrigatório")
    context = validate_comparison_context(spec.get("comparison_context"))
    parameters = spec.get("parameters", {})
    if not isinstance(parameters, dict):
        raise ExperimentError("parameters precisa ser um objeto JSON")
    try:
        canonical_json(subject)
        canonical_json(parameters)
    except (TypeError, ValueError) as exc:
        raise ExperimentError(f"subject/parameters não são JSON canônico válido: {exc}") from exc
    metrics = validate_metric_map(spec.get("metrics"))
    artifacts = spec.get("artifacts", [])
    if not isinstance(artifacts, list) or any(not isinstance(item, str) for item in artifacts):
        raise ExperimentError("artifacts precisa ser uma lista de caminhos relativos em texto")
    if len(set(artifacts)) != len(artifacts):
        raise ExperimentError("A lista de artefatos contém caminhos duplicados")
    return {
        "run_id": run_id,
        "experiment": experiment.strip(),
        "kind": kind,
        "subject": subject,
        "comparison_context": context,
        "parameters": parameters,
        "metrics": metrics,
        "artifacts": artifacts,
    }



def load_evaluation_result(path: Path, expected_suite_hash: str) -> tuple[dict[str, float], dict[str, Any]]:
    result = read_json(path)
    if not isinstance(result, dict) or result.get("schema") != 1 or result.get("status") != "complete":
        raise ExperimentError("A avaliação precisa ser um resultado completo do benchmark_evaluator")
    suite_hash = result.get("evaluation_suite_sha256")
    validate_sha256(suite_hash, "evaluation_result.evaluation_suite_sha256")
    if suite_hash != expected_suite_hash:
        raise ExperimentError("O hash da suíte avaliada não corresponde ao comparison_context do experimento")
    metrics = validate_metric_map(result.get("metrics"))
    if "coverage" not in metrics or not 0.0 <= metrics["coverage"] <= 1.0:
        raise ExperimentError("Resultado de avaliação sem coverage válida")
    count_names = ("matched_count", "case_count", "missing_prediction_count", "extra_prediction_count")
    counts = {name: result.get("metrics", {}).get(name) for name in count_names}
    if any(isinstance(value, bool) or not isinstance(value, int) or value < 0 for value in counts.values()):
        raise ExperimentError("Resultado de avaliação contém contagens ausentes ou inválidas")
    if counts["case_count"] < 1 or counts["matched_count"] != counts["case_count"] or counts["missing_prediction_count"] != 0 or counts["extra_prediction_count"] != 0 or metrics["coverage"] != 1.0:
        raise ExperimentError("Resultado marcado como complete, mas as contagens/cobertura não confirmam uma suíte integral")
    score_metrics = {name: value for name, value in metrics.items() if name not in count_names and name != "coverage"}
    if not score_metrics:
        raise ExperimentError("Resultado de avaliação não contém métrica específica da tarefa")
    bounded = ("exact_match_accuracy", "accuracy", "macro_precision", "macro_recall", "macro_f1",
               "micro_precision", "micro_recall", "micro_f1", "coverage")
    for name in bounded:
        if name in metrics and not 0.0 <= metrics[name] <= 1.0:
            raise ExperimentError(f"A métrica {name} precisa estar entre 0 e 1")
    return {"coverage": metrics["coverage"], **score_metrics}, {
        "evaluation_suite_sha256": suite_hash,
        "evaluation_result_sha256": sha256_file(path),
        "status": result["status"],
        "task": result.get("task", "exact_match"),
        "counts": counts,
    }


def record_run(spec_path: Path, artifact_root: Path, output_path: Path, evaluation_path: Path | None = None) -> dict[str, Any]:
    spec = validate_spec(read_json(spec_path))
    evaluated_metrics: dict[str, float] = {}
    evaluation_provenance = None
    if evaluation_path is not None:
        evaluated_metrics, evaluation_provenance = load_evaluation_result(
            evaluation_path, spec["comparison_context"]["evaluation_suite_sha256"])
        for name, value in evaluated_metrics.items():
            if name in spec["metrics"] and float(spec["metrics"][name]) != value:
                raise ExperimentError(f"A métrica {name} diverge do resultado de avaliação; não será sobrescrita")
        spec["metrics"].update(evaluated_metrics)
    if output_path.exists():
        raise ExperimentError(f"O registro já existe e não será sobrescrito: {output_path}")
    if not artifact_root.is_dir():
        raise ExperimentError(f"Pasta de artefatos inexistente: {artifact_root}")
    artifact_records = []
    for relative in spec["artifacts"]:
        rel, path = resolve_artifact(artifact_root, relative)
        artifact_records.append({"path": rel, "bytes": path.stat().st_size, "sha256": sha256_file(path)})
    run = {
        "schema": SCHEMA_VERSION,
        "status": "sealed",
        "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "run_id": spec["run_id"],
        "experiment": spec["experiment"],
        "kind": spec["kind"],
        "subject": spec["subject"],
        "comparison_context": spec["comparison_context"],
        "comparison_context_sha256": sha256_value(spec["comparison_context"]),
        "parameters": spec["parameters"],
        "metrics": spec["metrics"],
        "artifacts": artifact_records,
        "evaluation": evaluation_provenance,
        "integrity_note": "Hashes detect divergence from recorded artifacts; they do not prove who created or approved this manifest.",
    }
    write_json_atomic(output_path, run)
    return run


def verify_run(run_path: Path, artifact_root: Path, evaluation_path: Path | None = None) -> dict[str, Any]:
    run = read_json(run_path)
    if not isinstance(run, dict) or run.get("schema") != SCHEMA_VERSION or run.get("status") != "sealed":
        raise ExperimentError("Manifesto não reconhecido ou não selado")
    if not isinstance(run.get("artifacts"), list):
        raise ExperimentError("Manifesto sem lista de artefatos")
    context = validate_comparison_context(run.get("comparison_context"))
    expected_context_hash = run.get("comparison_context_sha256")
    validate_sha256(expected_context_hash, "comparison_context_sha256")
    if sha256_value(context) != expected_context_hash:
        raise ExperimentError("comparison_context diverge do hash registrado")
    evaluation_status = "not_present"
    evaluation = run.get("evaluation")
    if evaluation is not None:
        if not isinstance(evaluation, dict) or evaluation.get("status") != "complete":
            raise ExperimentError("Proveniência de avaliação inválida no manifesto")
        validate_sha256(evaluation.get("evaluation_suite_sha256"), "evaluation.evaluation_suite_sha256")
        validate_sha256(evaluation.get("evaluation_result_sha256"), "evaluation.evaluation_result_sha256")
        if evaluation["evaluation_suite_sha256"] != context["evaluation_suite_sha256"]:
            raise ExperimentError("A suíte registrada na avaliação diverge do contexto")
        if evaluation_path is not None:
            if evaluation_path.is_symlink():
                raise ExperimentError("O resultado da avaliação não pode ser um link simbólico")
            evaluation_status = "verified" if sha256_file(evaluation_path) == evaluation["evaluation_result_sha256"] else "integrity_error"
        else:
            evaluation_status = "not_rechecked"
    checks = []
    for item in run["artifacts"]:
        if not isinstance(item, dict):
            raise ExperimentError("Registro de artefato inválido")
        rel, path = resolve_artifact(artifact_root, item.get("path"))
        actual_hash = sha256_file(path)
        actual_bytes = path.stat().st_size
        expected_hash = item.get("sha256")
        validate_sha256(expected_hash, f"artifacts[{rel}].sha256")
        expected_bytes = item.get("bytes")
        if isinstance(expected_bytes, bool) or not isinstance(expected_bytes, int) or expected_bytes < 0:
            raise ExperimentError(f"Tamanho inválido registrado para {rel}")
        ok = actual_hash == expected_hash and actual_bytes == expected_bytes
        checks.append({"path": rel, "ok": ok, "expected_sha256": expected_hash, "actual_sha256": actual_hash,
                       "expected_bytes": item.get("bytes"), "actual_bytes": actual_bytes})
    artifacts_ok = all(x["ok"] for x in checks)
    if not artifacts_ok or evaluation_status == "integrity_error":
        status = "integrity_error"
    elif evaluation_status == "not_rechecked":
        status = "evaluation_not_rechecked"
    else:
        status = "verified"
    return {"run_id": run.get("run_id"), "status": status,
            "artifact_count": len(checks), "evaluation_result_status": evaluation_status, "checks": checks}


def validate_policy(policy: Any) -> dict[str, Any]:
    if not isinstance(policy, dict) or policy.get("schema") != SCHEMA_VERSION:
        raise ExperimentError("Política de comparação precisa ter schema=1")
    metrics = policy.get("metrics")
    if not isinstance(metrics, dict) or not metrics:
        raise ExperimentError("A política precisa declarar ao menos uma métrica")
    normalized = {}
    for name, rule in metrics.items():
        if not isinstance(name, str) or not isinstance(rule, dict):
            raise ExperimentError("Regra de métrica inválida")
        direction = rule.get("direction")
        if direction not in ("higher", "lower"):
            raise ExperimentError(f"direction inválida para {name}: use higher ou lower")
        values = {}
        for key in ("min_improvement", "max_regression"):
            value = rule.get(key)
            if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(float(value)) or value < 0:
                raise ExperimentError(f"{name}.{key} precisa ser um número finito maior ou igual a zero")
            values[key] = float(value)
        normalized[name] = {"direction": direction, **values}
    return normalized


def compare_runs(baseline: Any, candidate: Any, policy: Any) -> dict[str, Any]:
    for label, run in (("baseline", baseline), ("candidate", candidate)):
        if not isinstance(run, dict) or run.get("schema") != SCHEMA_VERSION or run.get("status") != "sealed":
            raise ExperimentError(f"{label} não é um manifesto selado válido")
        validate_metric_map(run.get("metrics"))
        context = validate_comparison_context(run.get("comparison_context"))
        expected_context_hash = run.get("comparison_context_sha256")
        validate_sha256(expected_context_hash, f"{label}.comparison_context_sha256")
        if sha256_value(context) != expected_context_hash:
            raise ExperimentError(f"{label} possui comparison_context alterado")
    if baseline.get("experiment") != candidate.get("experiment") or baseline.get("kind") != candidate.get("kind"):
        raise ExperimentError("As execuções precisam pertencer ao mesmo experimento e tipo de projeto")
    rules = validate_policy(policy)
    baseline_context = sha256_value(baseline["comparison_context"])
    candidate_context = sha256_value(candidate["comparison_context"])
    if baseline_context != candidate_context:
        return {"schema": 1, "status": "inconclusive", "reason": "comparison_context_mismatch",
                "baseline_run": baseline.get("run_id"), "candidate_run": candidate.get("run_id"),
                "baseline_context_sha256": baseline_context, "candidate_context_sha256": candidate_context,
                "metrics": []}
    rows = []
    missing = []
    regression = False
    improved = False
    for name, rule in rules.items():
        if name not in baseline["metrics"] or name not in candidate["metrics"]:
            missing.append(name)
            continue
        before = float(baseline["metrics"][name])
        after = float(candidate["metrics"][name])
        gain = after - before if rule["direction"] == "higher" else before - after
        is_regression = gain < -rule["max_regression"]
        is_improvement = gain > rule["min_improvement"]
        regression = regression or is_regression
        improved = improved or is_improvement
        rows.append({"metric": name, "direction": rule["direction"], "baseline": before, "candidate": after,
                     "oriented_gain": gain, "max_regression": rule["max_regression"],
                     "min_improvement": rule["min_improvement"],
                     "result": "regression" if is_regression else "improved" if is_improvement else "within_tolerance"})
    if missing:
        status = "inconclusive"
        reason = "missing_metrics: " + ", ".join(missing)
    elif regression:
        status = "regression"
        reason = "one_or_more_metrics_exceeded_regression_tolerance"
    elif improved:
        status = "improved"
        reason = "at_least_one_metric_improved_without_disallowed_regression"
    else:
        status = "no_material_improvement"
        reason = "no_metric_exceeded_its_minimum_improvement_threshold"
    return {"schema": 1, "status": status, "reason": reason,
            "baseline_run": baseline.get("run_id"), "candidate_run": candidate.get("run_id"),
            "comparison_context_sha256": baseline_context, "metrics": rows}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    record = commands.add_parser("record", help="Seal a run specification and hash its artifacts")
    record.add_argument("--spec", type=Path, required=True)
    record.add_argument("--artifact-root", type=Path, required=True)
    record.add_argument("--output", type=Path, required=True)
    record.add_argument("--evaluation-result", type=Path,
                        help="Optional complete benchmark result; evaluation suite fingerprint must match")
    verify = commands.add_parser("verify", help="Recalculate artifact hashes from a sealed run")
    verify.add_argument("--run", type=Path, required=True)
    verify.add_argument("--artifact-root", type=Path, required=True)
    verify.add_argument("--evaluation-result", type=Path,
                        help="Optional evaluation JSON to recheck its recorded hash")
    compare = commands.add_parser("compare", help="Compare two sealed runs against a policy")
    compare.add_argument("--baseline", type=Path, required=True)
    compare.add_argument("--candidate", type=Path, required=True)
    compare.add_argument("--policy", type=Path, required=True)
    compare.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.command == "record":
            result = record_run(args.spec, args.artifact_root, args.output, args.evaluation_result)
        elif args.command == "verify":
            result = verify_run(args.run, args.artifact_root, args.evaluation_result)
        else:
            result = compare_runs(read_json(args.baseline), read_json(args.candidate), read_json(args.policy))
            if args.output:
                write_json_atomic(args.output, result)
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        if args.command == "verify" and result["status"] != "verified":
            return 3
        if args.command == "compare" and result["status"] in ("regression", "inconclusive"):
            return 4
        return 0
    except (ExperimentError, OSError) as exc:
        print(f"experiment_engine: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
