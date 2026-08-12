from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


FIELD_TYPES = {"id", "string", "text", "integer", "decimal", "boolean", "date", "datetime"}
ACTIONS = {"list", "detail", "create", "update", "delete"}
CAMEL = re.compile(r"^[a-z][A-Za-z0-9]*$")
KEBAB = re.compile(r"^[a-z][a-z0-9-]*$")
SNAKE = re.compile(r"^[a-z][a-z0-9_]*$")
PACKAGE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


@dataclass(frozen=True)
class ContractIssue:
    path: str
    message: str

    def __str__(self) -> str:
        return f"{self.path}: {self.message}"


class ContractError(ValueError):
    def __init__(self, issues: list[ContractIssue]):
        super().__init__("\n".join(str(issue) for issue in issues))
        self.issues = issues


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def canonical_json(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def load_contract(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ContractError([ContractIssue("$", f"cannot read JSON contract: {error}")]) from error
    if not isinstance(data, dict):
        raise ContractError([ContractIssue("$", "contract root must be an object")])
    return data


def _object(
    value: Any,
    path: str,
    required: set[str],
    allowed: set[str],
    issues: list[ContractIssue],
) -> dict[str, Any]:
    if not isinstance(value, dict):
        issues.append(ContractIssue(path, "must be an object"))
        return {}
    missing = sorted(required - value.keys())
    unknown = sorted(value.keys() - allowed)
    for key in missing:
        issues.append(ContractIssue(f"{path}.{key}", "is required"))
    for key in unknown:
        issues.append(ContractIssue(f"{path}.{key}", "is not allowed"))
    return value


def _non_blank(value: Any, path: str, issues: list[ContractIssue]) -> str:
    if not isinstance(value, str) or not value.strip():
        issues.append(ContractIssue(path, "must be a non-blank string"))
        return ""
    return value


def _matches(value: Any, pattern: re.Pattern[str], path: str, issues: list[ContractIssue]) -> str:
    text = _non_blank(value, path, issues)
    if text and not pattern.fullmatch(text):
        issues.append(ContractIssue(path, f"has invalid format: {text}"))
    return text


def _boolean(value: Any, path: str, issues: list[ContractIssue]) -> bool:
    if not isinstance(value, bool):
        issues.append(ContractIssue(path, "must be boolean"))
        return False
    return value


def validate_contract(data: dict[str, Any], contract_path: Path | None = None) -> list[ContractIssue]:
    issues: list[ContractIssue] = []
    root = _object(
        data,
        "$",
        {"schemaVersion", "source", "project", "runtime", "ui", "modules", "unresolvedDecisions"},
        {"schemaVersion", "source", "project", "runtime", "ui", "modules", "unresolvedDecisions"},
        issues,
    )
    if root.get("schemaVersion") != 1:
        issues.append(ContractIssue("$.schemaVersion", "must equal 1"))

    source = _object(root.get("source"), "$.source", {"path", "sha256"}, {"path", "sha256"}, issues)
    source_path = _non_blank(source.get("path"), "$.source.path", issues)
    source_hash = _matches(source.get("sha256"), SHA256, "$.source.sha256", issues)
    if contract_path is not None and source_path and source_hash:
        resolved = (contract_path.parent / source_path).resolve()
        if not resolved.is_file():
            issues.append(ContractIssue("$.source.path", f"source file does not exist: {resolved}"))
        else:
            actual = sha256_bytes(resolved.read_bytes())
            if actual != source_hash:
                issues.append(ContractIssue("$.source.sha256", f"source hash mismatch; actual is {actual}"))

    project = _object(
        root.get("project"),
        "$.project",
        {"id", "name", "groupId", "artifactId", "basePackage"},
        {"id", "name", "groupId", "artifactId", "basePackage"},
        issues,
    )
    _matches(project.get("id"), KEBAB, "$.project.id", issues)
    _non_blank(project.get("name"), "$.project.name", issues)
    _matches(project.get("artifactId"), KEBAB, "$.project.artifactId", issues)
    _matches(project.get("basePackage"), PACKAGE, "$.project.basePackage", issues)
    group_id = _non_blank(project.get("groupId"), "$.project.groupId", issues)
    if group_id and not PACKAGE.fullmatch(group_id):
        issues.append(ContractIssue("$.project.groupId", "must be a lower-case Java package"))

    runtime = _object(
        root.get("runtime"),
        "$.runtime",
        {"java", "springBoot", "database", "redis", "serverPort"},
        {"java", "springBoot", "database", "redis", "serverPort"},
        issues,
    )
    if runtime.get("java") != 21:
        issues.append(ContractIssue("$.runtime.java", "must equal 21"))
    _non_blank(runtime.get("springBoot"), "$.runtime.springBoot", issues)
    if runtime.get("database") != "mysql":
        issues.append(ContractIssue("$.runtime.database", "v1 supports only mysql"))
    if runtime.get("redis") is not True:
        issues.append(ContractIssue("$.runtime.redis", "must be true in the standard starter"))
    port = runtime.get("serverPort")
    if not isinstance(port, int) or isinstance(port, bool) or not 1024 <= port <= 65535:
        issues.append(ContractIssue("$.runtime.serverPort", "must be an integer from 1024 to 65535"))

    ui = _object(
        root.get("ui"),
        "$.ui",
        {"library", "brand", "density", "referenceViewport"},
        {"library", "brand", "density", "referenceViewport"},
        issues,
    )
    if ui.get("library") != "ant-design-vue":
        issues.append(ContractIssue("$.ui.library", "v1 fixes the UI library to ant-design-vue"))
    _non_blank(ui.get("brand"), "$.ui.brand", issues)
    if ui.get("density") not in {"compact", "comfortable"}:
        issues.append(ContractIssue("$.ui.density", "must be compact or comfortable"))
    if ui.get("referenceViewport") != "1440x900":
        issues.append(ContractIssue("$.ui.referenceViewport", "v1 reference viewport must be 1440x900"))

    modules = root.get("modules")
    if not isinstance(modules, list) or not modules:
        issues.append(ContractIssue("$.modules", "must contain at least one module"))
        modules = []
    seen_module_ids: set[str] = set()
    seen_routes: set[str] = set()
    seen_tables: set[str] = set()
    for module_index, raw_module in enumerate(modules):
        module_path = f"$.modules[{module_index}]"
        module = _object(
            raw_module,
            module_path,
            {"id", "name", "route", "table", "actions", "fields", "businessRules"},
            {"id", "name", "route", "table", "actions", "fields", "businessRules"},
            issues,
        )
        module_id = _matches(module.get("id"), KEBAB, f"{module_path}.id", issues)
        route = _non_blank(module.get("route"), f"{module_path}.route", issues)
        table = _matches(module.get("table"), SNAKE, f"{module_path}.table", issues)
        _non_blank(module.get("name"), f"{module_path}.name", issues)
        if route and (not route.startswith("/") or route.endswith("/") or "//" in route):
            issues.append(ContractIssue(f"{module_path}.route", "must be a normalized absolute route"))
        for value, seen, path in (
            (module_id, seen_module_ids, f"{module_path}.id"),
            (route, seen_routes, f"{module_path}.route"),
            (table, seen_tables, f"{module_path}.table"),
        ):
            if value in seen:
                issues.append(ContractIssue(path, f"duplicate value: {value}"))
            seen.add(value)

        actions = module.get("actions")
        if not isinstance(actions, list) or not actions:
            issues.append(ContractIssue(f"{module_path}.actions", "must contain actions"))
        else:
            if len(actions) != len(set(actions)):
                issues.append(ContractIssue(f"{module_path}.actions", "must not contain duplicates"))
            for action_index, action in enumerate(actions):
                if action not in ACTIONS:
                    issues.append(ContractIssue(f"{module_path}.actions[{action_index}]", f"unsupported action: {action}"))

        fields = module.get("fields")
        if not isinstance(fields, list) or len(fields) < 2:
            issues.append(ContractIssue(f"{module_path}.fields", "must contain at least id plus one business field"))
            fields = []
        seen_codes: set[str] = set()
        seen_columns: set[str] = set()
        id_fields = 0
        for field_index, raw_field in enumerate(fields):
            field_path = f"{module_path}.fields[{field_index}]"
            field = _object(
                raw_field,
                field_path,
                {"code", "column", "label", "type", "required", "list", "form", "filter"},
                {"code", "column", "label", "type", "required", "list", "form", "filter", "length", "unique"},
                issues,
            )
            code = _matches(field.get("code"), CAMEL, f"{field_path}.code", issues)
            column = _matches(field.get("column"), SNAKE, f"{field_path}.column", issues)
            _non_blank(field.get("label"), f"{field_path}.label", issues)
            field_type = field.get("type")
            if field_type not in FIELD_TYPES:
                issues.append(ContractIssue(f"{field_path}.type", f"unsupported field type: {field_type}"))
            for flag in ("required", "list", "form", "filter"):
                _boolean(field.get(flag), f"{field_path}.{flag}", issues)
            if "unique" in field:
                _boolean(field.get("unique"), f"{field_path}.unique", issues)
            length = field.get("length")
            if length is not None and (not isinstance(length, int) or isinstance(length, bool) or not 1 <= length <= 4000):
                issues.append(ContractIssue(f"{field_path}.length", "must be an integer from 1 to 4000"))
            if field_type == "string" and length is None:
                issues.append(ContractIssue(f"{field_path}.length", "string fields require length"))
            if field_type not in {"string"} and length is not None:
                issues.append(ContractIssue(f"{field_path}.length", "length is allowed only for string fields"))
            if field_type == "id":
                id_fields += 1
                if code != "id" or column != "id" or not field.get("required"):
                    issues.append(ContractIssue(field_path, "id field must use code/column id and be required"))
                if field.get("form"):
                    issues.append(ContractIssue(f"{field_path}.form", "id field cannot be edited"))
            for value, seen, path in (
                (code, seen_codes, f"{field_path}.code"),
                (column, seen_columns, f"{field_path}.column"),
            ):
                if value in seen:
                    issues.append(ContractIssue(path, f"duplicate value: {value}"))
                seen.add(value)
        if id_fields != 1:
            issues.append(ContractIssue(f"{module_path}.fields", "must contain exactly one id field"))
        if fields and not any(field.get("list") for field in fields if isinstance(field, dict)):
            issues.append(ContractIssue(f"{module_path}.fields", "at least one field must be visible in the list"))

        rules = module.get("businessRules")
        if not isinstance(rules, list):
            issues.append(ContractIssue(f"{module_path}.businessRules", "must be an array"))
        else:
            seen_rules: set[str] = set()
            for rule_index, raw_rule in enumerate(rules):
                rule_path = f"{module_path}.businessRules[{rule_index}]"
                rule = _object(
                    raw_rule,
                    rule_path,
                    {"id", "description", "implementation"},
                    {"id", "description", "implementation"},
                    issues,
                )
                rule_id = _non_blank(rule.get("id"), f"{rule_path}.id", issues)
                _non_blank(rule.get("description"), f"{rule_path}.description", issues)
                if rule.get("implementation") not in {"generated", "handwritten"}:
                    issues.append(ContractIssue(f"{rule_path}.implementation", "must be generated or handwritten"))
                if rule_id in seen_rules:
                    issues.append(ContractIssue(f"{rule_path}.id", f"duplicate value: {rule_id}"))
                seen_rules.add(rule_id)

    decisions = root.get("unresolvedDecisions")
    if not isinstance(decisions, list):
        issues.append(ContractIssue("$.unresolvedDecisions", "must be an array"))
    else:
        for index, raw_decision in enumerate(decisions):
            path = f"$.unresolvedDecisions[{index}]"
            decision = _object(
                raw_decision,
                path,
                {"id", "question", "blocking"},
                {"id", "question", "blocking"},
                issues,
            )
            _non_blank(decision.get("id"), f"{path}.id", issues)
            _non_blank(decision.get("question"), f"{path}.question", issues)
            if decision.get("blocking") is not True:
                issues.append(ContractIssue(f"{path}.blocking", "unresolved decisions are always blocking"))

    return issues


def require_valid_contract(data: dict[str, Any], contract_path: Path) -> None:
    issues = validate_contract(data, contract_path)
    if issues:
        raise ContractError(issues)


def require_generation_ready(data: dict[str, Any], contract_path: Path) -> None:
    require_valid_contract(data, contract_path)
    decisions = data.get("unresolvedDecisions", [])
    if decisions:
        raise ContractError([
            ContractIssue(
                "$.unresolvedDecisions",
                f"generation is blocked by {len(decisions)} unresolved product decision(s)",
            )
        ])
