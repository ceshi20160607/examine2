from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class RuleIssue:
    path: str
    message: str

    def __str__(self) -> str:
        return f"{self.path}: {self.message}"


def rules_root() -> Path:
    return Path(__file__).resolve().parents[2] / "rules"


def load_rule(name: str) -> dict[str, Any]:
    path = rules_root() / name
    try:
        rule = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"cannot load rule {path}: {error}") from error
    if not isinstance(rule, dict):
        raise ValueError(f"rule root must be an object: {path}")
    return rule


def _matches_type(value: Any, expected: str) -> bool:
    return {
        "object": isinstance(value, dict),
        "array": isinstance(value, list),
        "string": isinstance(value, str),
        "integer": isinstance(value, int) and not isinstance(value, bool),
        "number": isinstance(value, (int, float)) and not isinstance(value, bool),
        "boolean": isinstance(value, bool),
        "null": value is None,
    }.get(expected, False)


def _validate(value: Any, rule: dict[str, Any], path: str, issues: list[RuleIssue]) -> None:
    expected_type = rule.get("type")
    if expected_type is not None:
        accepted = [expected_type] if isinstance(expected_type, str) else expected_type
        if not isinstance(accepted, list) or not any(_matches_type(value, item) for item in accepted):
            issues.append(RuleIssue(path, f"must have type {expected_type}"))
            return
    if "const" in rule and value != rule["const"]:
        issues.append(RuleIssue(path, f"must equal {rule['const']!r}"))
    if "enum" in rule and value not in rule["enum"]:
        issues.append(RuleIssue(path, f"must be one of {rule['enum']!r}"))

    if isinstance(value, dict):
        required = rule.get("required", [])
        if isinstance(required, list):
            for key in required:
                if key not in value:
                    issues.append(RuleIssue(f"{path}.{key}", "is required"))
        properties = rule.get("properties", {})
        if isinstance(properties, dict):
            if rule.get("additionalProperties") is False:
                for key in sorted(value.keys() - properties.keys()):
                    issues.append(RuleIssue(f"{path}.{key}", "is not allowed"))
            for key, child_rule in properties.items():
                if key in value and isinstance(child_rule, dict):
                    _validate(value[key], child_rule, f"{path}.{key}", issues)

    if isinstance(value, list):
        minimum = rule.get("minItems")
        if isinstance(minimum, int) and len(value) < minimum:
            issues.append(RuleIssue(path, f"must contain at least {minimum} items"))
        if rule.get("uniqueItems") is True:
            normalized = [json.dumps(item, ensure_ascii=False, sort_keys=True) for item in value]
            if len(normalized) != len(set(normalized)):
                issues.append(RuleIssue(path, "must not contain duplicate items"))
        item_rule = rule.get("items")
        if isinstance(item_rule, dict):
            for index, item in enumerate(value):
                _validate(item, item_rule, f"{path}[{index}]", issues)

    if isinstance(value, str):
        minimum = rule.get("minLength")
        if isinstance(minimum, int) and len(value) < minimum:
            issues.append(RuleIssue(path, f"must contain at least {minimum} characters"))
        pattern = rule.get("pattern")
        if isinstance(pattern, str) and re.search(pattern, value) is None:
            issues.append(RuleIssue(path, f"must match {pattern}"))

    if isinstance(value, (int, float)) and not isinstance(value, bool):
        minimum = rule.get("minimum")
        maximum = rule.get("maximum")
        if isinstance(minimum, (int, float)) and value < minimum:
            issues.append(RuleIssue(path, f"must be at least {minimum}"))
        if isinstance(maximum, (int, float)) and value > maximum:
            issues.append(RuleIssue(path, f"must be at most {maximum}"))


def validate_rule(value: Any, rule_name: str) -> list[RuleIssue]:
    issues: list[RuleIssue] = []
    _validate(value, load_rule(rule_name), "$", issues)
    return issues
