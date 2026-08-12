from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .contract import sha256_bytes


APPLICATION_KINDS = {"fixed-domain-system", "runtime-configurable-platform"}
MODULE_REALIZATIONS = {"compile-time-generated", "runtime-metadata"}
IDENTITY_STRATEGIES = {"application-local", "platform-unified"}
TENANCY_STRATEGIES = {"single-tenant", "fixed-multi-tenant", "runtime-switchable-multi-tenant"}


@dataclass(frozen=True)
class ArchitectureIssue:
    path: str
    message: str

    def __str__(self) -> str:
        return f"{self.path}: {self.message}"


class ArchitectureError(ValueError):
    def __init__(self, issues: list[ArchitectureIssue]):
        super().__init__("\n".join(str(issue) for issue in issues))
        self.issues = issues


def load_architecture(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise ArchitectureError([ArchitectureIssue("$", f"cannot read architecture baseline: {error}")]) from error
    if not isinstance(value, dict):
        raise ArchitectureError([ArchitectureIssue("$", "architecture baseline must be an object")])
    return value


def _object(
    value: Any,
    path: str,
    required: set[str],
    allowed: set[str],
    issues: list[ArchitectureIssue],
) -> dict[str, Any]:
    if not isinstance(value, dict):
        issues.append(ArchitectureIssue(path, "must be an object"))
        return {}
    for key in sorted(required - value.keys()):
        issues.append(ArchitectureIssue(f"{path}.{key}", "is required"))
    for key in sorted(value.keys() - allowed):
        issues.append(ArchitectureIssue(f"{path}.{key}", "is not allowed"))
    return value


def _non_blank(value: Any, path: str, issues: list[ArchitectureIssue]) -> str:
    if not isinstance(value, str) or not value.strip():
        issues.append(ArchitectureIssue(path, "must be a non-blank string"))
        return ""
    return value


def _unique_strings(value: Any, path: str, issues: list[ArchitectureIssue], minimum: int = 1) -> list[str]:
    if not isinstance(value, list) or len(value) < minimum:
        issues.append(ArchitectureIssue(path, f"must contain at least {minimum} item(s)"))
        return []
    result: list[str] = []
    for index, item in enumerate(value):
        text = _non_blank(item, f"{path}[{index}]", issues)
        if text:
            result.append(text)
    if len(result) != len(set(result)):
        issues.append(ArchitectureIssue(path, "must not contain duplicates"))
    return result


def validate_architecture(data: dict[str, Any], baseline_path: Path | None = None) -> list[ArchitectureIssue]:
    issues: list[ArchitectureIssue] = []
    root = _object(
        data,
        "$",
        {"schemaVersion", "source", "application", "boundaries", "firstVerticalSlice", "unresolvedDecisions"},
        {"schemaVersion", "source", "application", "boundaries", "firstVerticalSlice", "unresolvedDecisions"},
        issues,
    )
    if root.get("schemaVersion") != 1:
        issues.append(ArchitectureIssue("$.schemaVersion", "must equal 1"))

    source = _object(root.get("source"), "$.source", {"path", "sha256"}, {"path", "sha256"}, issues)
    source_path = _non_blank(source.get("path"), "$.source.path", issues)
    source_hash = _non_blank(source.get("sha256"), "$.source.sha256", issues)
    if source_hash and (len(source_hash) != 64 or any(character not in "0123456789abcdef" for character in source_hash)):
        issues.append(ArchitectureIssue("$.source.sha256", "must be a lower-case SHA-256"))
    if baseline_path is not None and source_path and len(source_hash) == 64:
        resolved = (baseline_path.parent / source_path).resolve()
        if not resolved.is_file():
            issues.append(ArchitectureIssue("$.source.path", f"source file does not exist: {resolved}"))
        elif sha256_bytes(resolved.read_bytes()) != source_hash:
            issues.append(ArchitectureIssue("$.source.sha256", "requirements changed; rebuild the architecture baseline"))

    application = _object(
        root.get("application"),
        "$.application",
        {"kind", "businessModuleRealization", "identityStrategy", "tenancyStrategy"},
        {"kind", "businessModuleRealization", "identityStrategy", "tenancyStrategy"},
        issues,
    )
    kind = application.get("kind")
    realization = application.get("businessModuleRealization")
    identity = application.get("identityStrategy")
    tenancy = application.get("tenancyStrategy")
    if kind not in APPLICATION_KINDS:
        issues.append(ArchitectureIssue("$.application.kind", f"must be one of {sorted(APPLICATION_KINDS)}"))
    if realization not in MODULE_REALIZATIONS:
        issues.append(ArchitectureIssue("$.application.businessModuleRealization", f"must be one of {sorted(MODULE_REALIZATIONS)}"))
    if identity not in IDENTITY_STRATEGIES:
        issues.append(ArchitectureIssue("$.application.identityStrategy", f"must be one of {sorted(IDENTITY_STRATEGIES)}"))
    if tenancy not in TENANCY_STRATEGIES:
        issues.append(ArchitectureIssue("$.application.tenancyStrategy", f"must be one of {sorted(TENANCY_STRATEGIES)}"))
    if kind == "runtime-configurable-platform" and realization != "runtime-metadata":
        issues.append(ArchitectureIssue(
            "$.application.businessModuleRealization",
            "runtime-configurable platforms must realize administrator-created modules from runtime metadata",
        ))

    boundaries = _object(
        root.get("boundaries"),
        "$.boundaries",
        {"fixedCore", "runtimeConfigured", "forbiddenImplementations"},
        {"fixedCore", "runtimeConfigured", "forbiddenImplementations"},
        issues,
    )
    fixed_core = _unique_strings(boundaries.get("fixedCore"), "$.boundaries.fixedCore", issues)
    runtime_configured = _unique_strings(boundaries.get("runtimeConfigured"), "$.boundaries.runtimeConfigured", issues)
    _unique_strings(boundaries.get("forbiddenImplementations"), "$.boundaries.forbiddenImplementations", issues)
    overlap = sorted(set(fixed_core) & set(runtime_configured))
    if overlap:
        issues.append(ArchitectureIssue("$.boundaries", f"fixed and runtime-configured capabilities overlap: {overlap}"))

    first_slice = _object(
        root.get("firstVerticalSlice"),
        "$.firstVerticalSlice",
        {"name", "orderedOutcomes", "deferredCapabilities"},
        {"name", "orderedOutcomes", "deferredCapabilities"},
        issues,
    )
    _non_blank(first_slice.get("name"), "$.firstVerticalSlice.name", issues)
    _unique_strings(first_slice.get("orderedOutcomes"), "$.firstVerticalSlice.orderedOutcomes", issues, minimum=2)
    _unique_strings(first_slice.get("deferredCapabilities"), "$.firstVerticalSlice.deferredCapabilities", issues, minimum=0)

    decisions = root.get("unresolvedDecisions")
    if not isinstance(decisions, list):
        issues.append(ArchitectureIssue("$.unresolvedDecisions", "must be an array"))
    else:
        seen: set[str] = set()
        for index, raw_decision in enumerate(decisions):
            path = f"$.unresolvedDecisions[{index}]"
            decision = _object(raw_decision, path, {"id", "question", "blocking"}, {"id", "question", "blocking"}, issues)
            decision_id = _non_blank(decision.get("id"), f"{path}.id", issues)
            _non_blank(decision.get("question"), f"{path}.question", issues)
            if decision.get("blocking") is not True:
                issues.append(ArchitectureIssue(f"{path}.blocking", "must be true"))
            if decision_id in seen:
                issues.append(ArchitectureIssue(f"{path}.id", f"duplicate value: {decision_id}"))
            seen.add(decision_id)
    return issues


def require_architecture_ready(data: dict[str, Any], baseline_path: Path) -> None:
    issues = validate_architecture(data, baseline_path)
    if issues:
        raise ArchitectureError(issues)
    decisions = data.get("unresolvedDecisions", [])
    if decisions:
        raise ArchitectureError([
            ArchitectureIssue(
                "$.unresolvedDecisions",
                f"development is blocked by {len(decisions)} unresolved architecture decision(s)",
            )
        ])
