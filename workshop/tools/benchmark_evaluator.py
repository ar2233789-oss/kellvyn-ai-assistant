#!/usr/bin/env python3
"""Deterministic, offline evaluator for exact-match, classification, regression and WER.

This measurement tool is called by the single Kellvyn main intelligence. It does
not generate predictions, call a model, execute user code, or use a network.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
import tempfile
import unicodedata
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dataset_catalog import build_manifest  # noqa: E402

SCHEMA_VERSION = 1
SUPPORTED_TASKS = {"exact_match", "classification", "regression", "word_error_rate"}


class EvaluationError(ValueError):
    """Raised when a benchmark suite or prediction file is invalid."""


def read_json(path: Path) -> Any:
    if path.is_symlink():
        raise EvaluationError(f"Links simbólicos não são aceitos: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise EvaluationError(f"Não foi possível ler JSON válido em {path}: {exc}") from exc


def canonical(value: Any) -> str:
    try:
        return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)
    except (TypeError, ValueError) as exc:
        raise EvaluationError(f"Valor não pode ser comparado como JSON: {exc}") from exc


def normalize_text(value: str, casefold: bool, collapse_whitespace: bool) -> str:
    normalized = unicodedata.normalize("NFKC", value).strip()
    if collapse_whitespace:
        normalized = " ".join(normalized.split())
    if casefold:
        normalized = normalized.casefold()
    return normalized


def normalize(value: Any, casefold: bool, collapse_whitespace: bool) -> Any:
    if isinstance(value, str):
        return normalize_text(value, casefold, collapse_whitespace)
    if isinstance(value, list):
        return [normalize(item, casefold, collapse_whitespace) for item in value]
    if isinstance(value, dict):
        return {key: normalize(item, casefold, collapse_whitespace) for key, item in value.items()}
    return value


def load_suite(suite_path: Path, suite_root: Path) -> tuple[dict[str, Any], str]:
    if suite_root.is_symlink() or suite_path.is_symlink():
        raise EvaluationError("A raiz e o arquivo da suíte não podem ser links simbólicos")
    try:
        resolved_root = suite_root.resolve(strict=True)
        resolved_suite = suite_path.resolve(strict=True)
        resolved_suite.relative_to(resolved_root)
    except (OSError, ValueError) as exc:
        raise EvaluationError("O arquivo da suíte precisa existir dentro da raiz informada") from exc
    if not resolved_root.is_dir() or not resolved_suite.is_file():
        raise EvaluationError("Raiz e arquivo da suíte precisam ser diretório e arquivo regular")
    suite = read_json(resolved_suite)
    if not isinstance(suite, dict) or not isinstance(suite.get("cases"), list) or not suite["cases"]:
        raise EvaluationError("A suíte precisa conter uma lista não vazia chamada cases")
    task = suite.get("task", "exact_match")
    if task not in SUPPORTED_TASKS:
        raise EvaluationError(f"Tarefa não suportada: {task}; opções: {', '.join(sorted(SUPPORTED_TASKS))}")
    seen = set()
    for index, case in enumerate(suite["cases"]):
        if not isinstance(case, dict) or not isinstance(case.get("id"), str) or not case["id"].strip():
            raise EvaluationError(f"Caso {index} precisa ter um id textual não vazio")
        if case["id"] in seen:
            raise EvaluationError(f"ID duplicado na suíte: {case['id']}")
        if "expected" not in case or "input" not in case:
            raise EvaluationError(f"Caso {case['id']} precisa conter input e expected")
        canonical(case["expected"])
        canonical(case["input"])
        seen.add(case["id"])
    fingerprint = build_manifest(resolved_root)["dataset_sha256"]
    return suite, fingerprint


def load_predictions(path: Path) -> dict[str, Any]:
    data = read_json(path)
    predictions = data.get("predictions") if isinstance(data, dict) else None
    if not isinstance(predictions, list):
        raise EvaluationError("O arquivo de previsões precisa conter uma lista chamada predictions")
    result = {}
    for index, item in enumerate(predictions):
        if not isinstance(item, dict) or not isinstance(item.get("id"), str) or not item["id"].strip():
            raise EvaluationError(f"Previsão {index} precisa ter um id textual não vazio")
        if "output" not in item:
            raise EvaluationError(f"Previsão {item['id']} não contém output")
        if item["id"] in result:
            raise EvaluationError(f"ID duplicado nas previsões: {item['id']}")
        canonical(item["output"])
        result[item["id"]] = item["output"]
    return result


def exact_match_metrics(cases: list[dict[str, Any]], predictions: dict[str, Any],
                        casefold: bool, collapse_whitespace: bool) -> tuple[dict[str, float], list[dict[str, Any]]]:
    matches = []
    for case in cases:
        case_id = case["id"]
        if case_id not in predictions:
            continue
        expected = normalize(case["expected"], casefold, collapse_whitespace)
        actual = normalize(predictions[case_id], casefold, collapse_whitespace)
        matches.append({"id": case_id, "match": canonical(expected) == canonical(actual)})
    return {"exact_match_accuracy": sum(bool(x["match"]) for x in matches) / len(cases)}, matches


def classification_metrics(cases: list[dict[str, Any]], predictions: dict[str, Any]) -> tuple[dict[str, float], dict[str, Any]]:
    paired = []
    labels: dict[str, Any] = {}
    for case in cases:
        if case["id"] not in predictions:
            continue
        expected, actual = case["expected"], predictions[case["id"]]
        if isinstance(expected, (dict, list)) or isinstance(actual, (dict, list)):
            raise EvaluationError("classification exige rótulos escalares (texto, número ou booleano)")
        expected_key, actual_key = canonical(expected), canonical(actual)
        labels[expected_key] = expected
        labels[actual_key] = actual
        paired.append((expected_key, actual_key))
    if not paired:
        raise EvaluationError("Não há previsões disponíveis para calcular métricas de classificação")
    per_class = {}
    tp_total = fp_total = fn_total = 0
    f1_values = []
    for key in sorted(labels):
        tp = sum(1 for truth, pred in paired if truth == key and pred == key)
        fp = sum(1 for truth, pred in paired if truth != key and pred == key)
        fn = sum(1 for truth, pred in paired if truth == key and pred != key)
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        per_class[key] = {"label": labels[key], "support": tp + fn, "precision": precision,
                          "recall": recall, "f1": f1, "true_positive": tp,
                          "false_positive": fp, "false_negative": fn}
        tp_total += tp
        fp_total += fp
        fn_total += fn
        f1_values.append(f1)
    accuracy = sum(1 for truth, pred in paired if truth == pred) / len(cases)
    macro_precision = sum(x["precision"] for x in per_class.values()) / len(per_class)
    macro_recall = sum(x["recall"] for x in per_class.values()) / len(per_class)
    micro_precision = tp_total / (tp_total + fp_total) if tp_total + fp_total else 0.0
    micro_recall = tp_total / (tp_total + fn_total) if tp_total + fn_total else 0.0
    micro_f1 = (2 * micro_precision * micro_recall / (micro_precision + micro_recall)
                if micro_precision + micro_recall else 0.0)
    return {"accuracy": accuracy, "macro_precision": macro_precision, "macro_recall": macro_recall,
            "macro_f1": sum(f1_values) / len(f1_values), "micro_precision": micro_precision,
            "micro_recall": micro_recall, "micro_f1": micro_f1}, {"per_class": per_class}


def regression_metrics(cases: list[dict[str, Any]], predictions: dict[str, Any]) -> tuple[dict[str, float], dict[str, Any]]:
    pairs = []
    for case in cases:
        if case["id"] not in predictions:
            continue
        expected, actual = case["expected"], predictions[case["id"]]
        if isinstance(expected, bool) or not isinstance(expected, (int, float)) or not math.isfinite(float(expected)):
            raise EvaluationError(f"Valor esperado de regressão inválido no caso {case['id']}")
        if isinstance(actual, bool) or not isinstance(actual, (int, float)) or not math.isfinite(float(actual)):
            raise EvaluationError(f"Previsão de regressão inválida no caso {case['id']}")
        pairs.append((float(expected), float(actual)))
    if not pairs:
        raise EvaluationError("Não há previsões disponíveis para calcular métricas de regressão")
    errors = [pred - truth for truth, pred in pairs]
    mae = sum(abs(error) for error in errors) / len(errors)
    mse = sum(error * error for error in errors) / len(errors)
    mean_truth = sum(truth for truth, _ in pairs) / len(pairs)
    total = sum((truth - mean_truth) ** 2 for truth, _ in pairs)
    residual = sum(error * error for error in errors)
    r2 = 1.0 if residual == 0 else 0.0 if total == 0 else 1.0 - residual / total
    return {"mae": mae, "mse": mse, "rmse": math.sqrt(mse), "r2": r2}, {
        "evaluated_pairs": len(pairs), "target_mean": mean_truth,
        "r2_note": "For a constant target, R2 is 1 for perfect predictions and 0 otherwise."}


def word_edit_counts(reference: list[str], hypothesis: list[str]) -> tuple[int, int, int]:
    rows, cols = len(reference) + 1, len(hypothesis) + 1
    dp = [[0] * cols for _ in range(rows)]
    back = [[""] * cols for _ in range(rows)]
    for i in range(1, rows):
        dp[i][0], back[i][0] = i, "D"
    for j in range(1, cols):
        dp[0][j], back[0][j] = j, "I"
    for i in range(1, rows):
        for j in range(1, cols):
            if reference[i - 1] == hypothesis[j - 1]:
                dp[i][j], back[i][j] = dp[i - 1][j - 1], "M"
            else:
                choices = [(dp[i - 1][j - 1] + 1, "S"),
                           (dp[i - 1][j] + 1, "D"), (dp[i][j - 1] + 1, "I")]
                dp[i][j], back[i][j] = min(choices, key=lambda item: item[0])
    substitutions = deletions = insertions = 0
    i, j = len(reference), len(hypothesis)
    while i or j:
        operation = back[i][j]
        if operation == "S": substitutions += 1; i -= 1; j -= 1
        elif operation == "D": deletions += 1; i -= 1
        elif operation == "I": insertions += 1; j -= 1
        else: i -= 1; j -= 1
    return substitutions, deletions, insertions


def wer_metrics(cases: list[dict[str, Any]], predictions: dict[str, Any],
                casefold: bool, collapse_whitespace: bool) -> tuple[dict[str, float], dict[str, Any]]:
    substitutions = deletions = insertions = reference_words = 0
    per_case = []
    for case in cases:
        if case["id"] not in predictions:
            continue
        reference, hypothesis = case["expected"], predictions[case["id"]]
        if not isinstance(reference, str) or not isinstance(hypothesis, str):
            raise EvaluationError("word_error_rate exige expected e output como texto")
        reference_words_list = normalize_text(reference, casefold, collapse_whitespace).split()
        hypothesis_words_list = normalize_text(hypothesis, casefold, collapse_whitespace).split()
        sub, delete, insert = word_edit_counts(reference_words_list, hypothesis_words_list)
        substitutions += sub
        deletions += delete
        insertions += insert
        reference_words += len(reference_words_list)
        per_case.append({"id": case["id"], "substitutions": sub, "deletions": delete, "insertions": insert,
                         "reference_words": len(reference_words_list)})
    if reference_words == 0:
        raise EvaluationError("WER não pode ser calculado: referências disponíveis sem palavras")
    wer = (substitutions + deletions + insertions) / reference_words
    return {"word_error_rate": wer, "word_substitutions": float(substitutions),
            "word_deletions": float(deletions), "word_insertions": float(insertions)}, {
        "reference_words": reference_words, "per_case": per_case,
        "metric_note": "WER pode exceder 1.0 quando há muitas inserções."}


def evaluate(suite: dict[str, Any], suite_sha256: str, predictions: dict[str, Any],
             casefold: bool = False, collapse_whitespace: bool = False) -> dict[str, Any]:
    cases = suite["cases"]
    expected_ids = {case["id"] for case in cases}
    prediction_ids = set(predictions)
    missing = sorted(expected_ids - prediction_ids)
    extra = sorted(prediction_ids - expected_ids)
    coverage = (len(cases) - len(missing)) / len(cases)
    task = suite.get("task", "exact_match")
    details: dict[str, Any] = {}
    if task == "exact_match":
        metrics, case_results = exact_match_metrics(cases, predictions, casefold, collapse_whitespace)
        details["case_results"] = case_results
        metric_note = "Exact match is deterministic lexical/structured comparison, not semantic quality."
    elif task == "classification":
        metrics, details = classification_metrics(cases, predictions)
        metric_note = "Macro metrics expose per-class performance; accuracy alone can hide class imbalance."
    elif task == "regression":
        metrics, details = regression_metrics(cases, predictions)
        metric_note = "Regression metrics describe numeric error; scale and target distribution matter."
    elif task == "word_error_rate":
        metrics, details = wer_metrics(cases, predictions, casefold, collapse_whitespace)
        metric_note = "WER is substitutions + deletions + insertions divided by reference words; it can exceed 1."
    else:
        raise EvaluationError(f"Tarefa não suportada: {task}")
    metrics["coverage"] = coverage
    metrics.update({"matched_count": sum(1 for case in cases if case["id"] in predictions),
                    "case_count": len(cases), "missing_prediction_count": len(missing),
                    "extra_prediction_count": len(extra)})
    return {"schema": SCHEMA_VERSION, "status": "complete" if not missing and not extra else "partial",
            "task": task, "suite_id": suite.get("suite_id"), "evaluation_suite_sha256": suite_sha256,
            "metrics": metrics,
            "normalization": {"unicode": "NFKC", "trim": True, "casefold": casefold,
                              "collapse_whitespace": collapse_whitespace},
            "missing_ids": missing, "extra_ids": extra, **details, "metric_note": metric_note}


def write_json_atomic(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rendered = json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    fd, temp_path = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(rendered); stream.flush(); os.fsync(stream.fileno())
        os.replace(temp_path, path)
    except Exception:
        try: os.unlink(temp_path)
        except OSError: pass
        raise


def run_evaluation(suite_root: Path, suite_file: Path, predictions_file: Path,
                   output: Path, casefold: bool, collapse_whitespace: bool) -> dict[str, Any]:
    resolved_root = suite_root.resolve(strict=True)
    resolved_output = output.resolve(strict=False)
    try: resolved_output.relative_to(resolved_root)
    except ValueError: pass
    else: raise EvaluationError("O relatório precisa ficar fora da raiz da suíte para preservar sua impressão digital")
    suite, suite_hash = load_suite(suite_file, suite_root)
    predictions = load_predictions(predictions_file)
    result = evaluate(suite, suite_hash, predictions, casefold, collapse_whitespace)
    write_json_atomic(output, result)
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite-root", type=Path, required=True)
    parser.add_argument("--suite-file", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--casefold", action="store_true")
    parser.add_argument("--collapse-whitespace", action="store_true")
    args = parser.parse_args(argv)
    try:
        result = run_evaluation(args.suite_root, args.suite_file, args.predictions, args.output,
                                args.casefold, args.collapse_whitespace)
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        return 0
    except (EvaluationError, OSError, ValueError) as exc:
        print(f"benchmark_evaluator: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
