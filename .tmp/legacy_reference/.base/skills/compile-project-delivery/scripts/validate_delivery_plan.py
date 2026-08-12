#!/usr/bin/env python3
"""Deterministically validate the compile-project-delivery JSON contract."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import sys
from pathlib import Path
from typing import Any


ROOT_KEYS = {
    "schemaVersion", "projectId", "source", "decisions", "artifacts",
    "qualityGates", "phases", "requirements", "acceptanceCases", "tasks", "cycles",
}
REQUIREMENT_STATUS = {"candidate", "accepted", "blocked", "rejected"}
CASE_STATUS = {"candidate", "accepted", "blocked", "rejected"}
CASE_KIND = {"journey", "success", "permission", "validation", "failure", "recovery"}
TASK_STATUS = {"planned", "ready", "blocked", "deferred", "passed"}
QUALITY_STAGE = {"functional", "hardening"}
VERIFY_KIND = {"build", "unit", "api", "data", "permission", "ui_smoke", "integration", "package", "security", "a11y"}
PLANNING_STATUS = {"outline", "detailed"}
BASE_BINDING_MODE = {"reuse", "configure", "extend", "override", "project_only"}


class ValidationError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def exact_keys(value: dict[str, Any], keys: set[str], label: str) -> None:
    require(isinstance(value, dict), f"{label} must be an object")
    missing = sorted(keys - set(value))
    extra = sorted(set(value) - keys)
    require(not missing and not extra, f"{label} keys mismatch; missing={missing}, extra={extra}")


def nonempty(value: Any, label: str) -> None:
    require(isinstance(value, str) and bool(value.strip()), f"{label} must be a non-empty string")


def nonempty_strings(value: Any, label: str) -> None:
    require(isinstance(value, list) and value, f"{label} must be a non-empty array")
    for index, item in enumerate(value):
        nonempty(item, f"{label}[{index}]")


def unique_ids(items: Any, label: str) -> dict[str, dict[str, Any]]:
    require(isinstance(items, list), f"{label} must be an array")
    result: dict[str, dict[str, Any]] = {}
    for index, item in enumerate(items):
        require(isinstance(item, dict), f"{label}[{index}] must be an object")
        nonempty(item.get("id"), f"{label}[{index}].id")
        require(item["id"] not in result, f"duplicate {label} id {item['id']}")
        result[item["id"]] = item
    return result


def exact_inverse(left_ids: list[str], expected_ids: list[str], label: str) -> None:
    require(len(left_ids) == len(set(left_ids)), f"{label} contains duplicate ids")
    require(set(left_ids) == set(expected_ids), f"{label} inverse mismatch: declared={sorted(left_ids)}, actual={sorted(expected_ids)}")


def validate_refs(ids: Any, index: dict[str, Any], label: str, allow_empty: bool = True) -> list[str]:
    require(isinstance(ids, list), f"{label} must be an array")
    require(allow_empty or bool(ids), f"{label} must not be empty")
    require(len(ids) == len(set(ids)), f"{label} contains duplicate ids")
    unknown = sorted(set(ids) - set(index))
    require(not unknown, f"{label} has unknown ids {unknown}")
    return ids


def validate_dag(index: dict[str, dict[str, Any]], dependency_key: str, label: str) -> dict[str, set[str]]:
    closure: dict[str, set[str]] = {}
    visiting: set[str] = set()

    def visit(item_id: str) -> set[str]:
        if item_id in closure:
            return closure[item_id]
        require(item_id not in visiting, f"{label} dependency cycle at {item_id}")
        visiting.add(item_id)
        deps = set(validate_refs(index[item_id][dependency_key], index, f"{label} {item_id}.{dependency_key}"))
        expanded = set(deps)
        for dep in sorted(deps):
            expanded.update(visit(dep))
        visiting.remove(item_id)
        closure[item_id] = expanded
        return expanded

    for item_id in sorted(index):
        visit(item_id)
    return closure


def normalized_scope(scope: str) -> str:
    return scope.replace("\\", "/").strip().rstrip("/").lower()


def scopes_overlap(first: list[str], second: list[str]) -> bool:
    for raw_a in first:
        a = normalized_scope(raw_a)
        for raw_b in second:
            b = normalized_scope(raw_b)
            if a == b or a.startswith(b + "/") or b.startswith(a + "/"):
                return True
    return False


def validate(plan: dict[str, Any], source_root: Path | None = None) -> dict[str, Any]:
    exact_keys(plan, ROOT_KEYS, "root")
    require(plan["schemaVersion"] == 1, "schemaVersion must be 1")
    nonempty(plan["projectId"], "projectId")

    exact_keys(plan["source"], {"id", "path", "sha256"}, "source")
    nonempty(plan["source"]["id"], "source.id")
    nonempty(plan["source"]["path"], "source.path")
    require(isinstance(plan["source"]["sha256"], str) and len(plan["source"]["sha256"]) == 64 and all(c in "0123456789abcdef" for c in plan["source"]["sha256"]), "source.sha256 must be lowercase 64-hex")
    if source_root is not None:
        source_path = (source_root / plan["source"]["path"]).resolve()
        require(source_path.is_file(), f"source.path does not exist under source root: {plan['source']['path']}")
        actual_source_hash = hashlib.sha256(source_path.read_bytes()).hexdigest()
        require(actual_source_hash == plan["source"]["sha256"], f"source.sha256 mismatch for {plan['source']['path']}")

    decisions = unique_ids(plan["decisions"], "decisions")
    artifacts = unique_ids(plan["artifacts"], "artifacts")
    gates = unique_ids(plan["qualityGates"], "qualityGates")
    phases = unique_ids(plan["phases"], "phases")
    requirements = unique_ids(plan["requirements"], "requirements")
    cases = unique_ids(plan["acceptanceCases"], "acceptanceCases")
    tasks = unique_ids(plan["tasks"], "tasks")
    cycles = unique_ids(plan["cycles"], "cycles")
    require(phases and requirements and cases and tasks and cycles, "phases, requirements, acceptanceCases, tasks, and cycles must not be empty")

    for decision in decisions.values():
        exact_keys(decision, {"id", "question", "status", "selected", "authority", "evidence"}, f"decision {decision['id']}")
        nonempty(decision["question"], f"decision {decision['id']}.question")
        require(decision["status"] in {"unresolved", "resolved", "rejected"}, f"decision {decision['id']} has invalid status")
        if decision["status"] == "resolved":
            for field in ("selected", "authority", "evidence"):
                nonempty(decision[field], f"decision {decision['id']}.{field}")
        else:
            require(all(decision[field] is None for field in ("selected", "authority", "evidence")), f"decision {decision['id']} unresolved/rejected fields must be null")

    for artifact in artifacts.values():
        exact_keys(artifact, {"id", "status", "sha256", "path"}, f"artifact {artifact['id']}")
        require(artifact["status"] in {"draft", "accepted", "superseded"}, f"artifact {artifact['id']} has invalid status")
        nonempty(artifact["path"], f"artifact {artifact['id']}.path")
        require(isinstance(artifact["sha256"], str) and len(artifact["sha256"]) == 64 and all(c in "0123456789abcdef" for c in artifact["sha256"]), f"artifact {artifact['id']}.sha256 must be lowercase 64-hex")
        if source_root is not None:
            artifact_path = (source_root / artifact["path"]).resolve()
            require(artifact_path.is_file(), f"artifact {artifact['id']} path does not exist under source root: {artifact['path']}")
            actual_artifact_hash = hashlib.sha256(artifact_path.read_bytes()).hexdigest()
            require(actual_artifact_hash == artifact["sha256"], f"artifact {artifact['id']} sha256 mismatch for {artifact['path']}")

    for gate in gates.values():
        exact_keys(gate, {"id", "type", "status", "evidence"}, f"qualityGate {gate['id']}")
        require(gate["type"] == "feature_complete", f"qualityGate {gate['id']} type must be feature_complete")
        require(gate["status"] in {"open", "passed", "failed"}, f"qualityGate {gate['id']} has invalid status")
        if gate["status"] == "passed":
            nonempty(gate["evidence"], f"qualityGate {gate['id']}.evidence")
        else:
            require(gate["evidence"] is None, f"qualityGate {gate['id']}.evidence must be null unless passed")

    phase_sequences: dict[int, str] = {}
    for phase in phases.values():
        exact_keys(phase, {"id", "sequence", "planningStatus", "outcome", "dependsOnPhaseIds", "requirementIds", "taskIds", "cycleIds"}, f"phase {phase['id']}")
        require(isinstance(phase["sequence"], int) and phase["sequence"] > 0, f"phase {phase['id']}.sequence must be positive")
        require(phase["sequence"] not in phase_sequences, f"duplicate phase sequence {phase['sequence']}")
        phase_sequences[phase["sequence"]] = phase["id"]
        require(phase["planningStatus"] in PLANNING_STATUS, f"phase {phase['id']} has invalid planningStatus")
        nonempty(phase["outcome"], f"phase {phase['id']}.outcome")
        validate_refs(phase["dependsOnPhaseIds"], phases, f"phase {phase['id']}.dependsOnPhaseIds")
        validate_refs(phase["requirementIds"], requirements, f"phase {phase['id']}.requirementIds", False)
        validate_refs(phase["taskIds"], tasks, f"phase {phase['id']}.taskIds", phase["planningStatus"] == "outline")
        validate_refs(phase["cycleIds"], cycles, f"phase {phase['id']}.cycleIds", phase["planningStatus"] == "outline")
        if phase["planningStatus"] == "outline":
            require(not phase["taskIds"] and not phase["cycleIds"], f"outline phase {phase['id']} cannot pre-create tasks or cycles")
    phase_closure = validate_dag(phases, "dependsOnPhaseIds", "phase")
    for phase in phases.values():
        for dep_id in phase_closure[phase["id"]]:
            require(phases[dep_id]["sequence"] < phase["sequence"], f"phase {phase['id']} depends on non-earlier phase {dep_id}")

    for requirement in requirements.values():
        exact_keys(requirement, {"id", "phaseId", "sourceRefs", "actor", "trigger", "action", "outcome", "placement", "constraints", "exclusions", "status", "decisionIds", "dependsOnRequirementIds", "acceptanceCaseIds", "taskIds"}, f"requirement {requirement['id']}")
        require(requirement["phaseId"] in phases, f"requirement {requirement['id']} has unknown phaseId")
        require(requirement["status"] in REQUIREMENT_STATUS, f"requirement {requirement['id']} has invalid status")
        require(isinstance(requirement["sourceRefs"], list) and requirement["sourceRefs"], f"requirement {requirement['id']}.sourceRefs must not be empty")
        for index, source_ref in enumerate(requirement["sourceRefs"]):
            exact_keys(source_ref, {"locator", "quote"}, f"requirement {requirement['id']}.sourceRefs[{index}]")
            nonempty(source_ref["locator"], f"requirement {requirement['id']}.sourceRefs[{index}].locator")
            nonempty(source_ref["quote"], f"requirement {requirement['id']}.sourceRefs[{index}].quote")
        nonempty(requirement["actor"], f"requirement {requirement['id']}.actor")
        nonempty(requirement["trigger"], f"requirement {requirement['id']}.trigger")
        exact_keys(requirement["action"], {"verb", "object"}, f"requirement {requirement['id']}.action")
        nonempty(requirement["action"]["verb"], f"requirement {requirement['id']}.action.verb")
        nonempty(requirement["action"]["object"], f"requirement {requirement['id']}.action.object")
        nonempty(requirement["outcome"], f"requirement {requirement['id']}.outcome")
        exact_keys(requirement["placement"], {"surface", "location", "defaultVisible"}, f"requirement {requirement['id']}.placement")
        nonempty(requirement["placement"]["surface"], f"requirement {requirement['id']}.placement.surface")
        nonempty(requirement["placement"]["location"], f"requirement {requirement['id']}.placement.location")
        require(isinstance(requirement["placement"]["defaultVisible"], bool), f"requirement {requirement['id']}.placement.defaultVisible must be boolean")
        require(isinstance(requirement["constraints"], list), f"requirement {requirement['id']}.constraints must be an array")
        nonempty_strings(requirement["exclusions"], f"requirement {requirement['id']}.exclusions")
        validate_refs(requirement["decisionIds"], decisions, f"requirement {requirement['id']}.decisionIds")
        validate_refs(requirement["dependsOnRequirementIds"], requirements, f"requirement {requirement['id']}.dependsOnRequirementIds")
        validate_refs(requirement["acceptanceCaseIds"], cases, f"requirement {requirement['id']}.acceptanceCaseIds", requirement["status"] != "accepted")
        validate_refs(requirement["taskIds"], tasks, f"requirement {requirement['id']}.taskIds", requirement["status"] != "accepted")
        if requirement["status"] == "accepted":
            require(phases[requirement["phaseId"]]["planningStatus"] == "detailed", f"accepted requirement {requirement['id']} belongs to an outline phase")
            unresolved = [item for item in requirement["decisionIds"] if decisions[item]["status"] != "resolved"]
            require(not unresolved, f"accepted requirement {requirement['id']} uses unresolved decisions {unresolved}")
    validate_dag(requirements, "dependsOnRequirementIds", "requirement")

    for case in cases.values():
        exact_keys(case, {"id", "kind", "status", "required", "expectedBusinessOutcome", "requirementIds", "given", "when", "then", "taskIds"}, f"acceptanceCase {case['id']}")
        require(case["kind"] in CASE_KIND, f"acceptanceCase {case['id']} has invalid kind")
        require(case["status"] in CASE_STATUS, f"acceptanceCase {case['id']} has invalid status")
        require(isinstance(case["required"], bool), f"acceptanceCase {case['id']}.required must be boolean")
        require(case["expectedBusinessOutcome"] in {"achieved", "denied", "recovered"}, f"acceptanceCase {case['id']} has invalid expectedBusinessOutcome")
        if case["kind"] in {"journey", "success"}:
            require(case["expectedBusinessOutcome"] == "achieved", f"positive case {case['id']} must achieve the business outcome")
        if case["kind"] == "permission":
            require(case["expectedBusinessOutcome"] == "denied", f"permission case {case['id']} must describe denial")
        validate_refs(case["requirementIds"], requirements, f"acceptanceCase {case['id']}.requirementIds", False)
        nonempty_strings(case["given"], f"acceptanceCase {case['id']}.given")
        nonempty(case["when"], f"acceptanceCase {case['id']}.when")
        nonempty_strings(case["then"], f"acceptanceCase {case['id']}.then")
        validate_refs(case["taskIds"], tasks, f"acceptanceCase {case['id']}.taskIds", case["status"] != "accepted")
        if case["status"] == "accepted":
            bad = [item for item in case["requirementIds"] if requirements[item]["status"] != "accepted"]
            require(not bad, f"accepted case {case['id']} uses non-accepted requirements {bad}")

    for requirement in requirements.values():
        if requirement["status"] != "accepted":
            continue
        positive_required = [
            case for case in cases.values()
            if requirement["id"] in case["requirementIds"]
            and case["status"] == "accepted"
            and case["required"] is True
            and case["kind"] in {"journey", "success"}
            and case["expectedBusinessOutcome"] == "achieved"
        ]
        require(positive_required, f"accepted requirement {requirement['id']} has no required positive business case")

    for task in tasks.values():
        exact_keys(task, {"id", "phaseId", "result", "requirementIds", "acceptanceCaseIds", "inputArtifactIds", "dependsOnTaskIds", "estimateMinutes", "moduleScope", "writeScope", "resourceScope", "scheduling", "serialReason", "allowedChanges", "forbiddenChanges", "baseBindings", "newSemanticsAllowed", "unresolvedDecisionIds", "qualityStage", "verificationKinds", "acceptanceCommand", "status", "cycleId", "activationGateId"}, f"task {task['id']}")
        require(task["phaseId"] in phases, f"task {task['id']} has unknown phaseId")
        nonempty(task["result"], f"task {task['id']}.result")
        validate_refs(task["requirementIds"], requirements, f"task {task['id']}.requirementIds", False)
        validate_refs(task["acceptanceCaseIds"], cases, f"task {task['id']}.acceptanceCaseIds", False)
        validate_refs(task["inputArtifactIds"], artifacts, f"task {task['id']}.inputArtifactIds", False)
        validate_refs(task["dependsOnTaskIds"], tasks, f"task {task['id']}.dependsOnTaskIds")
        require(isinstance(task["estimateMinutes"], int) and 0 < task["estimateMinutes"] <= 120, f"task {task['id']}.estimateMinutes must be 1..120")
        nonempty(task["moduleScope"], f"task {task['id']}.moduleScope")
        nonempty_strings(task["writeScope"], f"task {task['id']}.writeScope")
        require(isinstance(task["resourceScope"], list), f"task {task['id']}.resourceScope must be an array")
        nonempty_strings(task["allowedChanges"], f"task {task['id']}.allowedChanges")
        nonempty_strings(task["forbiddenChanges"], f"task {task['id']}.forbiddenChanges")
        require(isinstance(task["baseBindings"], list) and task["baseBindings"], f"task {task['id']}.baseBindings must not be empty")
        for index, binding in enumerate(task["baseBindings"]):
            exact_keys(binding, {"assetId", "mode", "reason"}, f"task {task['id']}.baseBindings[{index}]")
            nonempty(binding["assetId"], f"task {task['id']}.baseBindings[{index}].assetId")
            require(binding["mode"] in BASE_BINDING_MODE, f"task {task['id']}.baseBindings[{index}] has invalid mode")
            nonempty(binding["reason"], f"task {task['id']}.baseBindings[{index}].reason")
        require(task["newSemanticsAllowed"] is False, f"task {task['id']} cannot invent new product semantics")
        require(task["unresolvedDecisionIds"] == [], f"task {task['id']} cannot start with unresolved decisions")
        require(task["scheduling"] in {"ready_parallel", "forced_serial"}, f"task {task['id']} has invalid scheduling")
        if task["scheduling"] == "forced_serial":
            nonempty(task["serialReason"], f"task {task['id']}.serialReason")
        else:
            require(task["serialReason"] is None, f"task {task['id']}.serialReason must be null for ready_parallel")
        require(task["qualityStage"] in QUALITY_STAGE, f"task {task['id']} has invalid qualityStage")
        nonempty_strings(task["verificationKinds"], f"task {task['id']}.verificationKinds")
        require(not (set(task["verificationKinds"]) - VERIFY_KIND), f"task {task['id']} has invalid verificationKinds")
        nonempty(task["acceptanceCommand"], f"task {task['id']}.acceptanceCommand")
        require(task["status"] in TASK_STATUS, f"task {task['id']} has invalid status")
        require(all(requirements[item]["status"] == "accepted" for item in task["requirementIds"]), f"task {task['id']} references non-accepted requirement")
        require(all(cases[item]["status"] == "accepted" for item in task["acceptanceCaseIds"]), f"task {task['id']} references non-accepted case")
        require(all(artifacts[item]["status"] == "accepted" for item in task["inputArtifactIds"]), f"task {task['id']} consumes non-accepted artifact")
        case_requirement_ids = {
            requirement_id
            for case_id in task["acceptanceCaseIds"]
            for requirement_id in cases[case_id]["requirementIds"]
        }
        require(set(task["requirementIds"]) == case_requirement_ids, f"task {task['id']} requirementIds must exactly match its acceptance cases")
        require(task["activationGateId"] is None, f"core delivery task {task['id']} cannot use a performance activation gate")
        require(task["cycleId"] is not None and task["cycleId"] in cycles, f"task {task['id']} must belong to one core delivery cycle")

    task_closure = validate_dag(tasks, "dependsOnTaskIds", "task")

    cycle_sequences: dict[int, str] = {}
    for cycle in cycles.values():
        exact_keys(cycle, {"id", "phaseId", "sequence", "timeboxMinutes", "outcome", "demo", "taskIds"}, f"cycle {cycle['id']}")
        require(cycle["phaseId"] in phases, f"cycle {cycle['id']} has unknown phaseId")
        require(isinstance(cycle["sequence"], int) and cycle["sequence"] > 0, f"cycle {cycle['id']}.sequence must be positive")
        require(cycle["sequence"] not in cycle_sequences, f"duplicate cycle sequence {cycle['sequence']}")
        cycle_sequences[cycle["sequence"]] = cycle["id"]
        require(isinstance(cycle["timeboxMinutes"], int) and 0 < cycle["timeboxMinutes"] <= 240, f"cycle {cycle['id']}.timeboxMinutes must be 1..240")
        nonempty(cycle["outcome"], f"cycle {cycle['id']}.outcome")
        exact_keys(cycle["demo"], {"entryPoint", "acceptanceCaseId"}, f"cycle {cycle['id']}.demo")
        nonempty(cycle["demo"]["entryPoint"], f"cycle {cycle['id']}.demo.entryPoint")
        require(cycle["demo"]["acceptanceCaseId"] in cases, f"cycle {cycle['id']} has unknown demo case")
        demo_case = cases[cycle["demo"]["acceptanceCaseId"]]
        require(demo_case["status"] == "accepted" and demo_case["kind"] == "journey", f"cycle {cycle['id']} demo must be an accepted journey case")
        validate_refs(cycle["taskIds"], tasks, f"cycle {cycle['id']}.taskIds", False)
        require(set(demo_case["taskIds"]) & set(cycle["taskIds"]), f"cycle {cycle['id']} demo case has no task in the cycle")
        require(all(tasks[item]["cycleId"] == cycle["id"] for item in cycle["taskIds"]), f"cycle {cycle['id']} task inverse mismatch")
        require(all(tasks[item]["phaseId"] == cycle["phaseId"] for item in cycle["taskIds"]), f"cycle {cycle['id']} contains a task from another phase")

        longest: dict[str, int] = {}
        for task_id in sorted(cycle["taskIds"], key=lambda item: len(task_closure[item])):
            local_deps = [item for item in tasks[task_id]["dependsOnTaskIds"] if item in cycle["taskIds"]]
            longest[task_id] = tasks[task_id]["estimateMinutes"] + max((longest[item] for item in local_deps), default=0)
        critical_path = max(longest.values())
        require(critical_path <= cycle["timeboxMinutes"], f"cycle {cycle['id']} critical path {critical_path} exceeds timebox {cycle['timeboxMinutes']}")

    for task in tasks.values():
        if task["cycleId"] is None:
            continue
        cycle = cycles[task["cycleId"]]
        for dependency_id in task_closure[task["id"]]:
            dependency_cycle_id = tasks[dependency_id]["cycleId"]
            if dependency_cycle_id is not None:
                require(cycles[dependency_cycle_id]["sequence"] <= cycle["sequence"], f"task {task['id']} depends on later-cycle task {dependency_id}")

    cycle_list = list(cycles.values())
    for cycle in cycle_list:
        cycle_tasks = [tasks[item] for item in cycle["taskIds"]]
        for task in cycle_tasks:
            if task["scheduling"] == "forced_serial":
                has_conflict = any(
                    scopes_overlap(task["writeScope"] + task["resourceScope"], other["writeScope"] + other["resourceScope"])
                    for other in cycle_tasks
                    if other["id"] != task["id"]
                )
                require(bool(task["dependsOnTaskIds"]) or has_conflict, f"forced-serial task {task['id']} has no dependency or write conflict")
        for index, first in enumerate(cycle_tasks):
            for second in cycle_tasks[index + 1:]:
                dependent = second["id"] in task_closure[first["id"]] or first["id"] in task_closure[second["id"]]
                overlap = scopes_overlap(first["writeScope"] + first["resourceScope"], second["writeScope"] + second["resourceScope"])
                if not dependent and not overlap:
                    require(first["scheduling"] == second["scheduling"] == "ready_parallel", f"independent disjoint tasks {first['id']} and {second['id']} must default to ready_parallel")

    for phase in phases.values():
        exact_inverse(phase["requirementIds"], [item["id"] for item in requirements.values() if item["phaseId"] == phase["id"]], f"phase {phase['id']}.requirementIds")
        exact_inverse(phase["taskIds"], [item["id"] for item in tasks.values() if item["phaseId"] == phase["id"]], f"phase {phase['id']}.taskIds")
        exact_inverse(phase["cycleIds"], [item["id"] for item in cycles.values() if item["phaseId"] == phase["id"]], f"phase {phase['id']}.cycleIds")
    for requirement in requirements.values():
        exact_inverse(requirement["acceptanceCaseIds"], [item["id"] for item in cases.values() if requirement["id"] in item["requirementIds"]], f"requirement {requirement['id']}.acceptanceCaseIds")
        exact_inverse(requirement["taskIds"], [item["id"] for item in tasks.values() if requirement["id"] in item["requirementIds"]], f"requirement {requirement['id']}.taskIds")
    for case in cases.values():
        exact_inverse(case["taskIds"], [item["id"] for item in tasks.values() if case["id"] in item["acceptanceCaseIds"]], f"acceptanceCase {case['id']}.taskIds")
    for cycle in cycles.values():
        exact_inverse(cycle["taskIds"], [item["id"] for item in tasks.values() if item["cycleId"] == cycle["id"]], f"cycle {cycle['id']}.taskIds")

    canonical = json.dumps(plan, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return {
        "projectId": plan["projectId"],
        "phases": len(phases),
        "acceptedRequirements": sum(item["status"] == "accepted" for item in requirements.values()),
        "acceptanceCases": len(cases),
        "tasks": len(tasks),
        "cycles": len(cycles),
        "canonicalSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
    }


def run_self_test() -> None:
    example_path = Path(__file__).resolve().parent.parent / "references" / "example.json"
    base = json.loads(example_path.read_text(encoding="utf-8"))
    validate(base)

    failures: list[tuple[str, Any]] = []

    candidate = copy.deepcopy(base)
    candidate["requirements"][0]["status"] = "candidate"
    failures.append(("candidate requirement promotion", candidate))

    unresolved = copy.deepcopy(base)
    unresolved["decisions"].append({"id": "DEC-X", "question": "Choose", "status": "unresolved", "selected": None, "authority": None, "evidence": None})
    unresolved["requirements"][0]["decisionIds"] = ["DEC-X"]
    failures.append(("unresolved decision promotion", unresolved))

    oversized = copy.deepcopy(base)
    oversized["tasks"][0]["estimateMinutes"] = 121
    failures.append(("oversized task", oversized))

    slow_cycle = copy.deepcopy(base)
    slow_cycle["cycles"][0]["timeboxMinutes"] = 60
    failures.append(("cycle critical path", slow_cycle))

    trace = copy.deepcopy(base)
    trace["requirements"][0]["taskIds"].pop()
    failures.append(("one-way trace", trace))

    serial = copy.deepcopy(base)
    serial["tasks"][0]["scheduling"] = "forced_serial"
    serial["tasks"][0]["serialReason"] = "preference"
    failures.append(("unnecessary serial scheduling", serial))

    premature_perf = copy.deepcopy(base)
    premature_perf["tasks"][0]["qualityStage"] = "performance"
    premature_perf["tasks"][0]["verificationKinds"] = ["performance"]
    premature_perf["tasks"][0]["activationGateId"] = "GATE-FEATURE-COMPLETE"
    failures.append(("premature performance", premature_perf))

    for label, fixture in failures:
        try:
            validate(fixture)
        except ValidationError:
            continue
        raise AssertionError(f"self-test fixture unexpectedly passed: {label}")
    print(f"SELF_TEST_PASS valid=1 rejected={len(failures)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("plan", nargs="?", type=Path)
    parser.add_argument("--canonical-output", type=Path)
    parser.add_argument("--source-root", type=Path, default=Path.cwd())
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    try:
        if args.self_test:
            require(args.plan is None and args.canonical_output is None, "--self-test cannot be combined with plan arguments")
            run_self_test()
            return 0
        require(args.plan is not None, "plan path is required unless --self-test is used")
        plan = json.loads(args.plan.read_text(encoding="utf-8"))
        summary = validate(plan, args.source_root.resolve())
        if args.canonical_output:
            args.canonical_output.write_text(json.dumps(plan, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        print("VALIDATION_PASS " + json.dumps(summary, ensure_ascii=False, sort_keys=True))
        return 0
    except (OSError, json.JSONDecodeError, ValidationError, AssertionError) as exc:
        print(f"VALIDATION_FAIL {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
