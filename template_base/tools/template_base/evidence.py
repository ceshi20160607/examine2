from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

from .rules import validate_rule


@dataclass(frozen=True)
class CycleEvidenceIssue:
    path: str
    message: str

    def __str__(self) -> str:
        return f"{self.path}: {self.message}"


def _read_object(path: Path, issue_path: str, issues: list[CycleEvidenceIssue]) -> dict[str, Any] | None:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        issues.append(CycleEvidenceIssue(issue_path, f"cannot read JSON: {path}: {error}"))
        return None
    if not isinstance(value, dict):
        issues.append(CycleEvidenceIssue(issue_path, "must contain a JSON object"))
        return None
    return value


def evaluate_cycle_evidence(
    workspace_root: Path,
    active_project_path: Path,
    task_graph_path: Path,
    task_catalog_path: Path,
) -> tuple[dict[str, Any], list[CycleEvidenceIssue]]:
    """Validate cycle evidence and compute the contiguous completed-cycle prefix."""
    issues: list[CycleEvidenceIssue] = []
    graph = _read_object(task_graph_path, "$.activeProject.taskGraph", issues)
    catalog = _read_object(task_catalog_path, "$.activeProject.tasks", issues)
    if graph is None or catalog is None:
        return {
            "totalCycles": 0,
            "completedCycles": 0,
            "overallPercent": 0.0,
            "completedCycleIds": [],
            "latestExecutedDate": None,
        }, issues

    cycles = sorted(
        (cycle for cycle in graph.get("cycles", []) if isinstance(cycle, dict)),
        key=lambda cycle: cycle.get("order", 0),
    )
    task_kinds = {
        task.get("id"): task.get("kind")
        for task in catalog.get("tasks", [])
        if isinstance(task, dict)
    }
    evidence_root = active_project_path / "ai" / "evidence"
    scheduled_ids = {cycle.get("id") for cycle in cycles}
    evidence_paths = {
        path.stem: path
        for path in evidence_root.glob("CYCLE-*.json")
        if path.is_file()
    }
    for unknown_id in sorted(evidence_paths.keys() - scheduled_ids):
        issues.append(CycleEvidenceIssue(
            f"$.activeProject.cycleEvidence.{unknown_id}",
            "does not reference a scheduled cycle",
        ))

    completed_ids: list[str] = []
    executed_dates: list[str] = []
    prefix_open = True
    for cycle in cycles:
        cycle_id = cycle.get("id")
        if not isinstance(cycle_id, str):
            continue
        evidence_path = evidence_paths.get(cycle_id)
        if evidence_path is None:
            prefix_open = False
            continue
        cycle_path = f"$.activeProject.cycleEvidence.{cycle_id}"
        evidence = _read_object(evidence_path, cycle_path, issues)
        cycle_issue_count = len(issues)
        if evidence is None:
            prefix_open = False
            continue

        issues.extend(
            CycleEvidenceIssue(f"{cycle_path}{issue.path[1:]}", issue.message)
            for issue in validate_rule(evidence, "cycle-evidence.schema.json")
        )
        if evidence.get("cycleId") != cycle_id:
            issues.append(CycleEvidenceIssue(f"{cycle_path}.cycleId", f"must equal {cycle_id}"))

        expected_tasks = cycle.get("taskIds", [])
        actual_tasks = evidence.get("taskIds", [])
        if not isinstance(actual_tasks, list) or set(actual_tasks) != set(expected_tasks) or len(actual_tasks) != len(expected_tasks):
            issues.append(CycleEvidenceIssue(
                f"{cycle_path}.taskIds",
                f"must exactly cover scheduled tasks {expected_tasks}",
            ))

        checks = evidence.get("checks", [])
        journeys = evidence.get("journeys", [])
        if any(isinstance(check, dict) and check.get("result") != "passed" for check in checks if isinstance(checks, list)):
            issues.append(CycleEvidenceIssue(f"{cycle_path}.checks", "all cycle checks must pass before completion"))
        if any(isinstance(journey, dict) and journey.get("result") != "passed" for journey in journeys if isinstance(journeys, list)):
            issues.append(CycleEvidenceIssue(f"{cycle_path}.journeys", "all recorded journeys must pass before completion"))

        expected_journeys = set(cycle.get("journeyUseCaseIds", []))
        actual_journeys = {
            journey.get("useCaseId")
            for journey in journeys
            if isinstance(journeys, list) and isinstance(journey, dict)
        }
        unknown_journeys = actual_journeys - expected_journeys
        if unknown_journeys:
            issues.append(CycleEvidenceIssue(
                f"{cycle_path}.journeys",
                f"references use cases outside the scheduled cycle: {sorted(unknown_journeys)}",
            ))
        has_frontend_task = any(task_kinds.get(task_id) == "frontend" for task_id in expected_tasks)
        if has_frontend_task and expected_journeys and not actual_journeys:
            issues.append(CycleEvidenceIssue(
                f"{cycle_path}.journeys",
                "a cycle with frontend work must contain at least one real-entry journey",
            ))

        if isinstance(journeys, list):
            for journey_index, journey in enumerate(journeys):
                if not isinstance(journey, dict):
                    continue
                for path_index, path_value in enumerate(journey.get("evidencePaths", [])):
                    evidence_item_path = f"{cycle_path}.journeys[{journey_index}].evidencePaths[{path_index}]"
                    if not isinstance(path_value, str):
                        continue
                    relative_path = Path(path_value)
                    resolved = (workspace_root / relative_path).resolve()
                    if relative_path.is_absolute() or ".." in relative_path.parts or not resolved.is_relative_to(workspace_root):
                        issues.append(CycleEvidenceIssue(evidence_item_path, "must stay inside the workspace"))
                    elif not resolved.exists():
                        issues.append(CycleEvidenceIssue(evidence_item_path, f"does not exist: {path_value}"))

        executed_date: str | None = None
        executed_at = evidence.get("executedAt")
        if isinstance(executed_at, str):
            try:
                executed_date = datetime.fromisoformat(executed_at).date().isoformat()
            except ValueError:
                issues.append(CycleEvidenceIssue(f"{cycle_path}.executedAt", "must be an ISO-8601 timestamp"))

        cycle_valid = len(issues) == cycle_issue_count
        if prefix_open and cycle_valid:
            completed_ids.append(cycle_id)
            if executed_date is not None:
                executed_dates.append(executed_date)
        else:
            if prefix_open:
                prefix_open = False
            elif cycle_valid:
                issues.append(CycleEvidenceIssue(cycle_path, "cannot complete a cycle after an incomplete earlier cycle"))

    total_cycles = len(cycles)
    completed_cycles = len(completed_ids)
    return {
        "totalCycles": total_cycles,
        "completedCycles": completed_cycles,
        "overallPercent": round(completed_cycles * 100 / total_cycles, 1) if total_cycles else 100.0,
        "completedCycleIds": completed_ids,
        "latestExecutedDate": max(executed_dates) if executed_dates else None,
    }, issues
