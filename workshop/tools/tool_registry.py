#!/usr/bin/env python3
"""Validate and inspect the declarative tool catalog for the single main intelligence.

This module never invokes registered tools. It only reads and validates contracts.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path, PurePosixPath
from typing import Any

SCHEMA_VERSION = 1
REQUIRED_TOOL_FIELDS = {
    "id", "title", "script", "command", "purpose", "inputs", "outputs",
    "side_effects", "network", "executes_project_code", "arguments",
}
OPTIONAL_LIST_FIELDS = {"optional_inputs", "constraints"}


class RegistryError(ValueError):
    """Raised when the tool registry violates its contract."""


def read_registry(path: Path) -> dict[str, Any]:
    if path.is_symlink():
        raise RegistryError("O arquivo de registro não pode ser link simbólico")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RegistryError(f"Não foi possível ler o registro JSON: {exc}") from exc
    if not isinstance(data, dict):
        raise RegistryError("A raiz do registro precisa ser um objeto JSON")
    return data


def validate_registry(data: Any, repo_root: Path | None = None) -> dict[str, Any]:
    if not isinstance(data, dict) or data.get("schema") != SCHEMA_VERSION:
        raise RegistryError("Versão de esquema do registro não reconhecida")
    if data.get("registry_id") != "kellvyn-workshop-tools":
        raise RegistryError("registry_id inesperado")
    policy = data.get("policy")
    if not isinstance(policy, dict):
        raise RegistryError("O registro precisa declarar policy")
    required_policy = {
        "single_intelligence": True,
        "tools_are_not_agents": True,
        "tools_must_not_plan_or_decide": True,
        "network_access": False,
        "execute_project_code": False,
        "third_party_runtime_dependencies": False,
        "brain_scope": "excluded",
    }
    if policy != required_policy:
        raise RegistryError("A política precisa manter uma inteligência única e excluir o cérebro do escopo")
    tools = data.get("tools")
    if not isinstance(tools, list) or not tools:
        raise RegistryError("O registro precisa conter ao menos uma ferramenta")
    seen_ids: set[str] = set()
    seen_scripts: set[tuple[str, tuple[str, ...]]] = set()
    normalized = []
    for index, tool in enumerate(tools):
        if not isinstance(tool, dict):
            raise RegistryError(f"Ferramenta {index} precisa ser um objeto")
        missing = REQUIRED_TOOL_FIELDS - set(tool)
        if missing:
            raise RegistryError(f"Ferramenta {index} sem campos: {', '.join(sorted(missing))}")
        tool_id = tool["id"]
        if not isinstance(tool_id, str) or not tool_id.strip() or tool_id in seen_ids:
            raise RegistryError(f"ID vazio ou duplicado na ferramenta {index}: {tool_id!r}")
        seen_ids.add(tool_id)
        for field in ("title", "purpose", "script"):
            if not isinstance(tool[field], str) or not tool[field].strip():
                raise RegistryError(f"{tool_id}.{field} precisa ser texto não vazio")
        script = PurePosixPath(tool["script"])
        if script.is_absolute() or any(part in ("", ".", "..") for part in script.parts) or "\\" in tool["script"]:
            raise RegistryError(f"Caminho de script inseguro em {tool_id}")
        command = tool["command"]
        if not isinstance(command, list) or any(not isinstance(item, str) or not item for item in command):
            raise RegistryError(f"{tool_id}.command precisa ser lista de argumentos textuais")
        identity = (tool["script"], tuple(command))
        if identity in seen_scripts:
            raise RegistryError(f"Script/comando duplicado: {tool_id}")
        seen_scripts.add(identity)
        for field in ("inputs", "outputs", "side_effects"):
            values = tool[field]
            if not isinstance(values, list) or any(not isinstance(item, str) or not item for item in values):
                raise RegistryError(f"{tool_id}.{field} precisa ser lista de textos não vazios")
            if len(values) != len(set(values)):
                raise RegistryError(f"{tool_id}.{field} contém valores duplicados")
        for field in OPTIONAL_LIST_FIELDS:
            if field in tool and (
                not isinstance(tool[field], list)
                or any(not isinstance(item, str) or not item for item in tool[field])
            ):
                raise RegistryError(f"{tool_id}.{field} precisa ser lista de textos")
        for field in ("network", "executes_project_code"):
            if tool[field] is not False:
                raise RegistryError(f"{tool_id}.{field} precisa ser false nesta oficina")
        all_flags = tool["inputs"] + tool.get("optional_inputs", [])
        if len(all_flags) != len(set(all_flags)):
            raise RegistryError(f"{tool_id} repete argumentos obrigatórios/opcionais")
        if any(not flag.startswith("--") for flag in all_flags):
            raise RegistryError(f"{tool_id} contém argumento que não é uma opção CLI")
        arguments = tool["arguments"]
        if not isinstance(arguments, dict) or set(arguments) != set(all_flags):
            raise RegistryError(f"{tool_id}.arguments precisa descrever exatamente todas as opções CLI")
        allowed_types = {"directory", "input_file", "output_file", "integer", "boolean"}
        for flag, contract in arguments.items():
            if not isinstance(contract, dict) or set(contract) != {"type", "required"}:
                raise RegistryError(f"Contrato inválido para {tool_id} {flag}")
            if contract["type"] not in allowed_types or not isinstance(contract["required"], bool):
                raise RegistryError(f"Tipo/obrigatoriedade inválido para {tool_id} {flag}")
            if contract["required"] != (flag in tool["inputs"]):
                raise RegistryError(f"Obrigatoriedade divergente entre inputs e arguments em {tool_id} {flag}")
        if repo_root is not None:
            candidate = repo_root
            for part in script.parts:
                candidate = candidate / part
                if candidate.is_symlink():
                    raise RegistryError(f"{tool_id} aponta para caminho com link simbólico")
            try:
                resolved = candidate.resolve(strict=True)
                resolved.relative_to(repo_root.resolve(strict=True))
            except (OSError, ValueError) as exc:
                raise RegistryError(f"Script ausente ou fora do repositório: {tool['script']}") from exc
            if not resolved.is_file():
                raise RegistryError(f"Script não é arquivo regular: {tool['script']}")
        normalized.append(tool)
    return {
        "schema": SCHEMA_VERSION,
        "status": "valid",
        "registry_id": data["registry_id"],
        "tool_count": len(normalized),
        "single_intelligence": True,
        "tools_are_agents": False,
        "tools": normalized,
    }


def main(argv: list[str] | None = None) -> int:
    default_registry = Path(__file__).resolve().parents[1] / "tool_registry.json"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry", type=Path, default=default_registry)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("validate", help="Validate tool contracts and script paths")
    commands.add_parser("list", help="List registered tools without executing them")
    describe = commands.add_parser("describe", help="Show one tool contract")
    describe.add_argument("tool_id")
    args = parser.parse_args(argv)
    try:
        registry = read_registry(args.registry)
        result = validate_registry(registry, args.repo_root)
        if args.command == "list":
            result = {key: value for key, value in result.items() if key != "tools"}
            result["tools"] = [
                {"id": item["id"], "title": item["title"], "purpose": item["purpose"]}
                for item in registry["tools"]
            ]
        elif args.command == "describe":
            matches = [item for item in registry["tools"] if item["id"] == args.tool_id]
            if not matches:
                raise RegistryError(f"Ferramenta não encontrada: {args.tool_id}")
            result = matches[0]
        print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        return 0
    except (RegistryError, OSError, ValueError) as exc:
        print(f"tool_registry: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
