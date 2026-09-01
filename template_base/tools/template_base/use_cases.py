from __future__ import annotations

import json
from collections import Counter
from pathlib import Path
from typing import Any

from template_base.architecture import validate_architecture
from template_base.common import sha256_bytes
from template_base.requirements import load_analysis, validate_analysis
from template_base.rules import validate_rule


class UseCaseError(ValueError):
    pass


def _read_catalog(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise UseCaseError(f"cannot read use-case catalog {path}: {error}") from error
    if not isinstance(value, dict):
        raise UseCaseError(f"use-case catalog root must be an object: {path}")
    issues = validate_rule(value, "use-case-catalog.schema.json")
    if issues:
        raise UseCaseError("\n".join(str(issue) for issue in issues))
    return value


def _resolve_bound_source(catalog_path: Path, path_value: str, expected_hash: str, label: str) -> Path:
    source_path = (catalog_path.parent / path_value).resolve()
    try:
        actual_hash = sha256_bytes(source_path.read_bytes())
    except OSError as error:
        raise UseCaseError(f"cannot read {label} {source_path}: {error}") from error
    if actual_hash != expected_hash:
        raise UseCaseError(f"{label} hash mismatch: declared {expected_hash}, actual {actual_hash}")
    return source_path


def validate_use_case_catalog(path: Path) -> dict[str, object]:
    catalog_path = path.resolve()
    catalog = _read_catalog(catalog_path)
    source = catalog["source"]
    analysis_path = _resolve_bound_source(
        catalog_path,
        source["requirementAnalysisPath"],
        source["requirementAnalysisSha256"],
        "requirement analysis",
    )
    architecture_path = _resolve_bound_source(
        catalog_path,
        source["architecturePath"],
        source["architectureSha256"],
        "architecture",
    )
    analysis = load_analysis(analysis_path)
    analysis_summary = validate_analysis(analysis, analysis_path)
    validate_architecture(architecture_path)
    architecture = json.loads(architecture_path.read_text(encoding="utf-8-sig"))

    requirement_owner: dict[str, str] = {}
    for capability in architecture["capabilities"]:
        for requirement_id in capability["requirementIds"]:
            requirement_owner[requirement_id] = capability["id"]
    known_requirements = set(requirement_owner)
    known_capabilities = {item["id"] for item in architecture["capabilities"]}

    use_case_ids = [item["id"] for item in catalog["useCases"]]
    duplicate_use_cases = sorted(item for item, count in Counter(use_case_ids).items() if count > 1)
    if duplicate_use_cases:
        raise UseCaseError(f"duplicate use-case IDs: {', '.join(duplicate_use_cases)}")

    covered_requirement_ids: list[str] = []
    for use_case in catalog["useCases"]:
        use_case_id = use_case["id"]
        capability_id = use_case["capabilityId"]
        if capability_id not in known_capabilities:
            raise UseCaseError(f"{use_case_id} references unknown capability {capability_id}")
        unknown_requirements = sorted(set(use_case["requirementIds"]) - known_requirements)
        if unknown_requirements:
            raise UseCaseError(
                f"{use_case_id} references unknown requirements: {', '.join(unknown_requirements)}"
            )
        wrong_owner = sorted(
            requirement_id
            for requirement_id in use_case["requirementIds"]
            if requirement_owner[requirement_id] != capability_id
        )
        if wrong_owner:
            details = ", ".join(
                f"{requirement_id} owned by {requirement_owner[requirement_id]}"
                for requirement_id in wrong_owner
            )
            raise UseCaseError(f"{use_case_id} crosses exclusive capability ownership: {details}")
        expected_orders = list(range(1, len(use_case["steps"]) + 1))
        actual_orders = [step["order"] for step in use_case["steps"]]
        if actual_orders != expected_orders:
            raise UseCaseError(
                f"{use_case_id} step orders must be contiguous from 1: {actual_orders}"
            )
        covered_requirement_ids.extend(use_case["requirementIds"])

    missing_requirements = sorted(known_requirements - set(covered_requirement_ids))
    if missing_requirements:
        raise UseCaseError("requirements lack use-case coverage: " + ", ".join(missing_requirements))
    return {
        "valid": True,
        "catalog": catalog_path.as_posix(),
        "requirementCount": analysis_summary["requirementCount"],
        "coveredRequirementCount": len(set(covered_requirement_ids)),
        "useCaseCount": len(catalog["useCases"]),
        "capabilityCount": len({item["capabilityId"] for item in catalog["useCases"]}),
    }
