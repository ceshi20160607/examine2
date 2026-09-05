from __future__ import annotations

import heapq
import json
import math
import os
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from template_base.common import canonical_json, sha256_bytes
from template_base.rules import validate_rule
from template_base.tasks import validate_task_catalog


class ScheduleError(ValueError):
    pass


def _read_json_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise ScheduleError(f"cannot read {label} {path}: {error}") from error
    if not isinstance(value, dict):
        raise ScheduleError(f"{label} root must be an object: {path}")
    return value


def _resolve_bound_source(source_owner: Path, path_value: str, expected_hash: str, label: str) -> Path:
    source_path = (source_owner.parent / path_value).resolve()
    try:
        actual_hash = sha256_bytes(source_path.read_bytes())
    except OSError as error:
        raise ScheduleError(f"cannot read {label} {source_path}: {error}") from error
    if actual_hash != expected_hash:
        raise ScheduleError(f"{label} hash mismatch: declared {expected_hash}, actual {actual_hash}")
    return source_path


def _relative(from_directory: Path, target: Path) -> str:
    return Path(os.path.relpath(target, from_directory)).as_posix()


def _duplicates(values: list[str]) -> list[str]:
    return sorted(value for value, count in Counter(values).items() if count > 1)


def _require_acyclic(dependencies: dict[str, list[str]], label: str) -> None:
    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(item_id: str, trail: list[str]) -> None:
        if item_id in visiting:
            start = trail.index(item_id)
            raise ScheduleError(f"{label} dependency cycle: {' -> '.join(trail[start:] + [item_id])}")
        if item_id in visited:
            return
        visiting.add(item_id)
        for dependency_id in dependencies[item_id]:
            visit(dependency_id, trail + [item_id])
        visiting.remove(item_id)
        visited.add(item_id)

    for item_id in dependencies:
        visit(item_id, [])


def _bound_catalog_sources(catalog_path: Path) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    catalog = _read_json_object(catalog_path, "task catalog")
    source = catalog["source"]
    architecture_path = _resolve_bound_source(
        catalog_path, source["architecturePath"], source["architectureSha256"], "architecture"
    )
    use_case_path = _resolve_bound_source(
        catalog_path, source["useCaseCatalogPath"], source["useCaseCatalogSha256"], "use-case catalog"
    )
    return catalog, _read_json_object(architecture_path, "architecture"), _read_json_object(use_case_path, "use-case catalog")


def validate_task_graph(path: Path) -> dict[str, object]:
    graph_path = path.resolve()
    graph = _read_json_object(graph_path, "task graph")
    issues = validate_rule(graph, "task-graph.schema.json")
    if issues:
        raise ScheduleError("\n".join(str(issue) for issue in issues))
    source = graph["source"]
    catalog_path = _resolve_bound_source(
        graph_path, source["taskCatalogPath"], source["taskCatalogSha256"], "task catalog"
    )
    validate_task_catalog(catalog_path)
    catalog, _, use_case_catalog = _bound_catalog_sources(catalog_path)
    tasks = {item["id"]: item for item in catalog["tasks"]}
    use_case_ids = {item["id"] for item in use_case_catalog["useCases"]}

    phases = graph["phases"]
    phase_ids = [item["id"] for item in phases]
    if _duplicates(phase_ids):
        raise ScheduleError(f"duplicate phase IDs: {', '.join(_duplicates(phase_ids))}")
    if [item["order"] for item in phases] != list(range(1, len(phases) + 1)):
        raise ScheduleError("phase order must be contiguous from 1")
    known_phases = set(phase_ids)
    phase_order = {item["id"]: item["order"] for item in phases}
    for phase in phases:
        unknown = sorted(set(phase["dependsOn"]) - known_phases)
        if unknown:
            raise ScheduleError(f"{phase['id']} references unknown phases: {', '.join(unknown)}")
        if any(phase_order[item] >= phase["order"] for item in phase["dependsOn"]):
            raise ScheduleError(f"{phase['id']} can only depend on earlier phases")

    schedules = graph["taskSchedules"]
    scheduled_task_ids = [item["taskId"] for item in schedules]
    if _duplicates(scheduled_task_ids):
        raise ScheduleError(f"duplicate task schedules: {', '.join(_duplicates(scheduled_task_ids))}")
    missing_tasks = sorted(tasks.keys() - set(scheduled_task_ids))
    unknown_tasks = sorted(set(scheduled_task_ids) - tasks.keys())
    if missing_tasks or unknown_tasks:
        raise ScheduleError(f"task schedules must exactly cover catalog; missing={missing_tasks}, unknown={unknown_tasks}")
    schedule_by_task = {item["taskId"]: item for item in schedules}
    dependencies: dict[str, list[str]] = {}
    for schedule in schedules:
        task_id = schedule["taskId"]
        if schedule["phaseId"] not in known_phases:
            raise ScheduleError(f"{task_id} references unknown phase {schedule['phaseId']}")
        unknown = sorted(set(schedule["dependsOn"]) - tasks.keys())
        if unknown:
            raise ScheduleError(f"{task_id} references unknown dependencies: {', '.join(unknown)}")
        if task_id in schedule["dependsOn"]:
            raise ScheduleError(f"{task_id} cannot depend on itself")
        for dependency_id in schedule["dependsOn"]:
            dependency_phase = schedule_by_task[dependency_id]["phaseId"]
            if phase_order[dependency_phase] > phase_order[schedule["phaseId"]]:
                raise ScheduleError(f"{task_id} depends on later-phase task {dependency_id}")
        dependencies[task_id] = schedule["dependsOn"]
    _require_acyclic(dependencies, "task")

    cycles = graph["cycles"]
    cycle_ids = [item["id"] for item in cycles]
    if _duplicates(cycle_ids):
        raise ScheduleError(f"duplicate cycle IDs: {', '.join(_duplicates(cycle_ids))}")
    if [item["order"] for item in cycles] != list(range(1, len(cycles) + 1)):
        raise ScheduleError("cycle order must be contiguous from 1")
    placed: list[str] = []
    task_cycle_order: dict[str, int] = {}
    for cycle in cycles:
        if cycle["phaseId"] not in known_phases:
            raise ScheduleError(f"{cycle['id']} references unknown phase {cycle['phaseId']}")
        unknown = sorted(set(cycle["taskIds"]) - tasks.keys())
        if unknown:
            raise ScheduleError(f"{cycle['id']} references unknown tasks: {', '.join(unknown)}")
        wrong_phase = sorted(
            task_id for task_id in cycle["taskIds"]
            if schedule_by_task[task_id]["phaseId"] != cycle["phaseId"]
        )
        if wrong_phase:
            raise ScheduleError(f"{cycle['id']} contains tasks from another phase: {', '.join(wrong_phase)}")
        estimate = sum(schedule_by_task[task_id]["estimateMinutes"] for task_id in cycle["taskIds"])
        if estimate > cycle["maximumMinutes"]:
            raise ScheduleError(f"{cycle['id']} totals {estimate} minutes, above {cycle['maximumMinutes']}")
        expected_journeys = {
            use_case_id
            for task_id in cycle["taskIds"]
            if tasks[task_id]["kind"] == "integration-test"
            for use_case_id in tasks[task_id]["useCaseIds"]
        }
        if set(cycle["journeyUseCaseIds"]) != expected_journeys:
            raise ScheduleError(f"{cycle['id']} journeyUseCaseIds must exactly match its integration-test tasks")
        if not set(cycle["journeyUseCaseIds"]) <= use_case_ids:
            raise ScheduleError(f"{cycle['id']} references unknown journey use cases")
        for task_id in cycle["taskIds"]:
            if task_id in task_cycle_order:
                raise ScheduleError(f"{task_id} is assigned to more than one cycle")
            task_cycle_order[task_id] = cycle["order"]
        placed.extend(cycle["taskIds"])
    if set(placed) != tasks.keys():
        raise ScheduleError("cycles must place every catalog task exactly once")
    for task_id, dependency_ids in dependencies.items():
        if any(task_cycle_order[item] > task_cycle_order[task_id] for item in dependency_ids):
            raise ScheduleError(f"{task_id} has a dependency in a later cycle")

    assessments = graph["existingImplementationAssessment"]
    assessment_paths = [item["path"] for item in assessments]
    if _duplicates(assessment_paths):
        raise ScheduleError(f"duplicate assessment paths: {', '.join(_duplicates(assessment_paths))}")
    for item in assessments:
        unknown = sorted(set(item["taskIds"]) - tasks.keys())
        if unknown:
            raise ScheduleError(f"assessment {item['path']} references unknown tasks: {', '.join(unknown)}")

    total_minutes = sum(item["estimateMinutes"] for item in schedules)
    return {
        "valid": True,
        "graph": graph_path.as_posix(),
        "phaseCount": len(phases),
        "cycleCount": len(cycles),
        "taskCount": len(schedules),
        "totalEstimatedMinutes": total_minutes,
        "totalEstimatedHours": round(total_minutes / 60, 1),
        "assessmentCount": len(assessments),
    }


def generate_task_graph(plan_path: Path, output_path: Path) -> dict[str, object]:
    schedule_plan_path = plan_path.resolve()
    plan = _read_json_object(schedule_plan_path, "schedule plan")
    issues = validate_rule(plan, "schedule-plan.schema.json")
    if issues:
        raise ScheduleError("\n".join(str(issue) for issue in issues))
    source = plan["source"]
    catalog_path = _resolve_bound_source(
        schedule_plan_path, source["taskCatalogPath"], source["taskCatalogSha256"], "task catalog"
    )
    validate_task_catalog(catalog_path)
    catalog, architecture, _ = _bound_catalog_sources(catalog_path)
    tasks = catalog["tasks"]
    task_by_id = {item["id"]: item for item in tasks}
    capability_ids = {item["id"] for item in architecture["capabilities"]}
    rework_task_stages = {task_id: 1 for task_id in plan.get("reworkTaskIds", [])}
    stage_overrides = plan.get("reworkTaskStages", [])
    stage_override_ids = [item["taskId"] for item in stage_overrides]
    if _duplicates(stage_override_ids):
        raise ScheduleError(f"duplicate reworkTaskStages: {', '.join(_duplicates(stage_override_ids))}")
    rework_task_stages.update({item["taskId"]: item["stage"] for item in stage_overrides})
    rework_task_ids = set(rework_task_stages)
    unknown_rework_tasks = sorted(rework_task_ids - task_by_id.keys())
    if unknown_rework_tasks:
        raise ScheduleError(f"reworkTaskIds references unknown tasks: {', '.join(unknown_rework_tasks)}")

    def dependency_candidate_visible(task_id: str, candidate_id: str) -> bool:
        task_stage = rework_task_stages.get(task_id)
        candidate_stage = rework_task_stages.get(candidate_id)
        if task_stage is None:
            return candidate_stage is None
        return candidate_stage is None or candidate_stage <= task_stage

    phases = plan["phases"]
    phase_ids = [item["id"] for item in phases]
    if _duplicates(phase_ids):
        raise ScheduleError(f"duplicate phase IDs: {', '.join(_duplicates(phase_ids))}")
    if [item["order"] for item in phases] != list(range(1, len(phases) + 1)):
        raise ScheduleError("phase order must be contiguous from 1")
    phase_order = {item["id"]: item["order"] for item in phases}
    if plan["foundationPhaseId"] not in phase_order:
        raise ScheduleError("foundationPhaseId must reference a declared phase")
    capability_phase: dict[str, str] = {}
    for phase in phases:
        for dependency_id in phase["dependsOn"]:
            if dependency_id not in phase_order or phase_order[dependency_id] >= phase["order"]:
                raise ScheduleError(f"{phase['id']} can only depend on an earlier declared phase")
        for capability_id in phase["capabilityIds"]:
            if capability_id in capability_phase:
                raise ScheduleError(f"{capability_id} has more than one primary delivery phase")
            capability_phase[capability_id] = phase["id"]
    missing_capabilities = sorted(capability_ids - capability_phase.keys())
    unknown_capabilities = sorted(capability_phase.keys() - capability_ids)
    if missing_capabilities or unknown_capabilities:
        raise ScheduleError(
            f"phase capability ownership must exactly match architecture; missing={missing_capabilities}, unknown={unknown_capabilities}"
        )

    factors = plan["capabilityEstimateFactors"]
    factor_ids = [item["capabilityId"] for item in factors]
    if _duplicates(factor_ids) or not set(factor_ids) <= capability_ids:
        raise ScheduleError("capability estimate factors must be unique known capabilities")
    factor_by_capability = {item["capabilityId"]: item["percent"] for item in factors}

    estimate_overrides = plan["taskEstimateOverrides"]
    estimate_override_ids = [item["taskId"] for item in estimate_overrides]
    if _duplicates(estimate_override_ids) or not set(estimate_override_ids) <= task_by_id.keys():
        raise ScheduleError("task estimate overrides must be unique known tasks")
    estimate_override_by_task = {item["taskId"]: item["estimateMinutes"] for item in estimate_overrides}

    phase_overrides = plan["taskPhaseOverrides"]
    phase_override_ids = [item["taskId"] for item in phase_overrides]
    if _duplicates(phase_override_ids) or not set(phase_override_ids) <= task_by_id.keys():
        raise ScheduleError("task phase overrides must be unique known tasks")
    if any(item["phaseId"] not in phase_order for item in phase_overrides):
        raise ScheduleError("task phase overrides must reference declared phases")
    phase_override_by_task = {item["taskId"]: item["phaseId"] for item in phase_overrides}

    task_phase: dict[str, str] = {}
    task_estimate: dict[str, int] = {}
    scaled_kinds = {"database", "backend-manage", "frontend", "operations"}
    for task in tasks:
        task_id = task["id"]
        if task_id in phase_override_by_task:
            task_phase[task_id] = phase_override_by_task[task_id]
        elif task["kind"] in {"database", "base-generation", "design"}:
            task_phase[task_id] = plan["foundationPhaseId"]
        else:
            task_phase[task_id] = capability_phase[task["capabilityId"]]
        base = plan["defaultEstimates"][task["kind"]]
        factor = factor_by_capability.get(task["capabilityId"], 100) if task["kind"] in scaled_kinds else 100
        calculated = math.ceil((base * factor / 100) / 5) * 5
        estimate = estimate_override_by_task.get(task_id, calculated)
        if estimate < 1 or estimate > 240:
            raise ScheduleError(f"{task_id} estimate must stay within 1..240 minutes")
        task_estimate[task_id] = estimate

    task_ids_by_use_case: dict[str, list[str]] = defaultdict(list)
    for task in tasks:
        for use_case_id in task["useCaseIds"]:
            task_ids_by_use_case[use_case_id].append(task["id"])
    database_task_ids = [item["id"] for item in tasks if item["kind"] == "database"]
    base_task_ids = [item["id"] for item in tasks if item["kind"] == "base-generation"]
    if len(base_task_ids) != 1:
        raise ScheduleError(f"schedule requires exactly one full-project base-generation task, found {len(base_task_ids)}")
    base_task_id = base_task_ids[0]

    dependencies: dict[str, list[str]] = {}
    for task in tasks:
        task_id = task["id"]
        dependency_ids: set[str] = set()
        if task["kind"] == "base-generation":
            dependency_ids.update(database_task_ids)
            dependency_ids.update(
                item["id"] for item in tasks
                if item["kind"] == "design"
                and item["id"] not in rework_task_ids
                and set(item["useCaseIds"]) & set(task["useCaseIds"])
            )
        elif task["kind"] in {"backend-manage", "operations"}:
            dependency_ids.add(base_task_id)
        elif task["kind"] == "frontend":
            for use_case_id in task["useCaseIds"]:
                dependency_ids.update(
                    candidate_id for candidate_id in task_ids_by_use_case[use_case_id]
                    if task_by_id[candidate_id]["kind"] in {"backend-manage", "operations", "design"}
                    and dependency_candidate_visible(task_id, candidate_id)
                )
            if not dependency_ids:
                dependency_ids.add(base_task_id)
        elif task["kind"] == "integration-test":
            for use_case_id in task["useCaseIds"]:
                dependency_ids.update(
                    candidate_id for candidate_id in task_ids_by_use_case[use_case_id]
                    if task_by_id[candidate_id]["kind"] != "integration-test"
                    and dependency_candidate_visible(task_id, candidate_id)
                )
        dependencies[task_id] = sorted(dependency_ids)
        for dependency_id in dependencies[task_id]:
            if phase_order[task_phase[dependency_id]] > phase_order[task_phase[task_id]]:
                raise ScheduleError(f"{task_id} depends on later-phase task {dependency_id}")
    _require_acyclic(dependencies, "task")

    task_index = {item["id"]: index for index, item in enumerate(tasks)}
    dependents: dict[str, list[str]] = defaultdict(list)
    indegree = {task_id: len(values) for task_id, values in dependencies.items()}
    for task_id, dependency_ids in dependencies.items():
        for dependency_id in dependency_ids:
            dependents[dependency_id].append(task_id)
    ready: list[tuple[int, int, str]] = []
    for task_id, degree in indegree.items():
        if degree == 0:
            heapq.heappush(ready, (phase_order[task_phase[task_id]], task_index[task_id], task_id))
    ordered_task_ids: list[str] = []
    while ready:
        _, _, task_id = heapq.heappop(ready)
        ordered_task_ids.append(task_id)
        for dependent_id in dependents[task_id]:
            indegree[dependent_id] -= 1
            if indegree[dependent_id] == 0:
                heapq.heappush(
                    ready,
                    (phase_order[task_phase[dependent_id]], task_index[dependent_id], dependent_id),
                )
    if len(ordered_task_ids) != len(tasks):
        raise ScheduleError("cannot topologically order all tasks")

    cycles: list[dict[str, Any]] = []
    placed_cycle_order: dict[str, int] = {}
    for task_id in ordered_task_ids:
        phase_id = task_phase[task_id]
        estimate = task_estimate[task_id]
        earliest_order = max(
            (placed_cycle_order[dependency_id] for dependency_id in dependencies[task_id]),
            default=0,
        )
        cycle = next(
            (
                candidate for candidate in cycles
                if candidate["phaseId"] == phase_id
                and candidate["order"] >= earliest_order
                and candidate["estimatedMinutes"] + estimate <= 240
            ),
            None,
        )
        if cycle is None:
            cycle = {
                "id": f"CYCLE-{len(cycles) + 1:03d}",
                "phaseId": phase_id,
                "order": len(cycles) + 1,
                "maximumMinutes": 240,
                "taskIds": [],
                "journeyUseCaseIds": [],
                "estimatedMinutes": 0,
            }
            cycles.append(cycle)
        cycle["taskIds"].append(task_id)
        cycle["estimatedMinutes"] += estimate
        placed_cycle_order[task_id] = cycle["order"]
        if task_by_id[task_id]["kind"] == "integration-test":
            for use_case_id in task_by_id[task_id]["useCaseIds"]:
                if use_case_id not in cycle["journeyUseCaseIds"]:
                    cycle["journeyUseCaseIds"].append(use_case_id)
    output_cycles = [
        {key: value for key, value in cycle.items() if key != "estimatedMinutes"}
        for cycle in cycles
    ]

    graph_path = output_path.resolve()
    graph = {
        "schemaVersion": 1,
        "source": {
            "taskCatalogPath": _relative(graph_path.parent, catalog_path),
            "taskCatalogSha256": sha256_bytes(catalog_path.read_bytes()),
        },
        "phases": [{key: phase[key] for key in ("id", "order", "name", "outcome", "dependsOn")} for phase in phases],
        "cycles": output_cycles,
        "taskSchedules": [
            {
                "taskId": task_id,
                "phaseId": task_phase[task_id],
                "estimateMinutes": task_estimate[task_id],
                "dependsOn": dependencies[task_id],
            }
            for task_id in ordered_task_ids
        ],
        "existingImplementationAssessment": plan["existingImplementationAssessment"],
    }
    graph_path.parent.mkdir(parents=True, exist_ok=True)
    graph_path.write_text(canonical_json(graph), encoding="utf-8")
    summary = validate_task_graph(graph_path)
    return {**summary, "schedulePlan": schedule_plan_path.as_posix(), "output": graph_path.as_posix()}
