from __future__ import annotations

import json
from collections import Counter
from pathlib import Path
from typing import Any

from template_base.common import sha256_bytes
from template_base.requirements import load_analysis, validate_analysis
from template_base.rules import validate_rule


class ArchitectureError(ValueError):
    pass


def _read_design(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise ArchitectureError(f"cannot read architecture design {path}: {error}") from error
    if not isinstance(value, dict):
        raise ArchitectureError(f"architecture design root must be an object: {path}")
    issues = validate_rule(value, "architecture-design.schema.json")
    if issues:
        raise ArchitectureError("\n".join(str(issue) for issue in issues))
    return value


def _require_unique(values: list[str], label: str) -> None:
    duplicates = sorted(value for value, count in Counter(values).items() if count > 1)
    if duplicates:
        raise ArchitectureError(f"duplicate {label}: {', '.join(duplicates)}")


def _require_acyclic(dependencies: dict[str, list[str]]) -> None:
    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(capability_id: str, path: list[str]) -> None:
        if capability_id in visiting:
            start = path.index(capability_id)
            cycle = path[start:] + [capability_id]
            raise ArchitectureError(f"capability dependency cycle: {' -> '.join(cycle)}")
        if capability_id in visited:
            return
        visiting.add(capability_id)
        for dependency_id in dependencies[capability_id]:
            visit(dependency_id, path + [capability_id])
        visiting.remove(capability_id)
        visited.add(capability_id)

    for capability_id in dependencies:
        visit(capability_id, [])


def validate_architecture(path: Path) -> dict[str, object]:
    design_path = path.resolve()
    design = _read_design(design_path)
    source = design["source"]
    analysis_path = (design_path.parent / source["requirementAnalysisPath"]).resolve()
    try:
        actual_hash = sha256_bytes(analysis_path.read_bytes())
    except OSError as error:
        raise ArchitectureError(f"cannot read requirement analysis {analysis_path}: {error}") from error
    if actual_hash != source["requirementAnalysisSha256"]:
        raise ArchitectureError(
            "requirement analysis hash mismatch: "
            f"declared {source['requirementAnalysisSha256']}, actual {actual_hash}"
        )

    analysis = load_analysis(analysis_path)
    analysis_summary = validate_analysis(analysis, analysis_path)
    expected_requirement_ids = {item["id"] for item in analysis["requirements"]}

    contexts = design["contexts"]
    context_ids = [item["id"] for item in contexts]
    _require_unique(context_ids, "context IDs")
    known_context_ids = set(context_ids)

    capabilities = design["capabilities"]
    capability_ids = [item["id"] for item in capabilities]
    _require_unique(capability_ids, "capability IDs")
    known_capability_ids = set(capability_ids)

    assigned_requirement_ids: list[str] = []
    dependencies: dict[str, list[str]] = {}
    for capability in capabilities:
        capability_id = capability["id"]
        unknown_contexts = sorted(set(capability["contextIds"]) - known_context_ids)
        if unknown_contexts:
            raise ArchitectureError(
                f"{capability_id} references unknown contexts: {', '.join(unknown_contexts)}"
            )
        dependency_ids = capability["dependsOnCapabilityIds"]
        unknown_dependencies = sorted(set(dependency_ids) - known_capability_ids)
        if unknown_dependencies:
            raise ArchitectureError(
                f"{capability_id} references unknown capabilities: {', '.join(unknown_dependencies)}"
            )
        if capability_id in dependency_ids:
            raise ArchitectureError(f"{capability_id} cannot depend on itself")
        dependencies[capability_id] = dependency_ids
        assigned_requirement_ids.extend(capability["requirementIds"])

    _require_acyclic(dependencies)
    assignment_counts = Counter(assigned_requirement_ids)
    duplicate_assignments = sorted(
        requirement_id for requirement_id, count in assignment_counts.items() if count > 1
    )
    unknown_assignments = sorted(set(assigned_requirement_ids) - expected_requirement_ids)
    missing_assignments = sorted(expected_requirement_ids - set(assigned_requirement_ids))
    if duplicate_assignments:
        raise ArchitectureError(
            "requirements assigned to more than one capability: " + ", ".join(duplicate_assignments)
        )
    if unknown_assignments:
        raise ArchitectureError(
            "architecture references unknown requirements: " + ", ".join(unknown_assignments)
        )
    if missing_assignments:
        raise ArchitectureError(
            "requirements lack capability ownership: " + ", ".join(missing_assignments)
        )

    decision_ids = [item["id"] for item in design["decisions"]]
    _require_unique(decision_ids, "architecture decision IDs")
    return {
        "valid": True,
        "architecture": design_path.as_posix(),
        "requirementCount": analysis_summary["requirementCount"],
        "ownedRequirementCount": len(assigned_requirement_ids),
        "capabilityCount": len(capabilities),
        "contextCount": len(contexts),
        "decisionCount": len(decision_ids),
    }
