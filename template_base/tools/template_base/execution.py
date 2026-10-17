"""有界步骤执行入口：读取项目断点，不从历史周期推断当前完成情况。"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .common import sha256_bytes


class ExecutionError(ValueError):
    pass


def _read(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, ValueError) as error:
        raise ExecutionError(f"cannot read execution artifact {path}: {error}") from error
    if not isinstance(value, dict):
        raise ExecutionError(f"execution artifact must be an object: {path}")
    return value


def _inside(root: Path, value: Any) -> Path:
    if not isinstance(value, str) or not value.strip():
        raise ExecutionError("execution paths must be non-blank relative paths")
    relative = Path(value)
    result = (root / relative).resolve()
    if relative.is_absolute() or ".." in relative.parts or not result.is_relative_to(root):
        raise ExecutionError(f"execution path leaves its root: {value}")
    return result


def _project_file(root: Path, project: Path, value: Any) -> Path:
    path = _inside(root, value)
    if not path.is_relative_to(project) or not path.is_file():
        raise ExecutionError(f"execution artifact must exist inside active project: {value}")
    return path


def _steps(state: dict[str, Any]) -> dict[str, str]:
    steps = {}
    for package in state["packages"]:
        for step, status in package["steps"].items():
            if step in steps:
                raise ExecutionError(f"duplicate execution step: {step}")
            steps[step] = status
    return steps


def _load(workspace_path: Path) -> tuple:
    root = workspace_path.resolve().parent
    workspace = _read(workspace_path)
    project = _inside(root, workspace["activeProject"]["path"])
    control_path = _project_file(root, project, workspace["activeProject"]["executionControl"])
    control = _read(control_path)
    if control.get("schemaVersion") != 1 or control.get("mode") != "bounded_steps":
        raise ExecutionError("unsupported execution control schema or mode")
    state_path = _project_file(root, project, control["state"])
    state = _read(state_path)
    plan_path = _project_file(root, project, state["plan"])
    ledger_path = _project_file(root, project, state["ledger"])
    ledger = _read(ledger_path)
    requirement_path = _inside(root, workspace["requirements"]["path"])
    actual_hash = sha256_bytes(requirement_path.read_bytes())
    if (state["requirement_source"] != workspace["requirements"]["path"]
            or ledger["source"] != state["requirement_source"]
            or any(expected != actual_hash for expected in (
                workspace["requirements"]["sha256"], state["requirement_sha256"], ledger["source_sha256"]))):
        raise ExecutionError("current requirement binding changed; review affected requirements and evidence before rebinding")
    workflow_path = _inside(root, workspace["template"]["path"]) / "agents" / "workflow.json"
    workflow = _read(workflow_path)
    return root, project, control, state, ledger, requirement_path, plan_path, state_path, control_path, workflow_path, workflow


def _context(workspace_path: Path, selected_step: str | None = None) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    root, project, control, state, ledger, source, plan, state_path, control_path, workflow_path, workflow = _load(workspace_path)
    steps = _steps(state)
    if selected_step is None:
        scope = state["current_scope"]
        step = state["next_step"]
        if state["current_step"] != step or scope["step"] != step or steps.get(step) not in {"unverified", "in_progress"}:
            raise ExecutionError("current step, next step and scope must name the same declared unfinished step")
    else:
        scope = state.get("parallel_task_packets", {}).get(selected_step)
        step = selected_step
        if not isinstance(scope, dict) or scope.get("step") != step or steps.get(step) not in {"unverified", "in_progress"}:
            raise ExecutionError(f"parallel step is not an admitted unfinished packet: {step}")
    prerequisites = scope["prerequisite_steps"]
    if not isinstance(prerequisites, list) or len(set(prerequisites)) != len(prerequisites):
        raise ExecutionError("prerequisite_steps must be an explicit unique list")
    for dependency in prerequisites:
        if dependency == step or steps.get(dependency) != "verified":
            raise ExecutionError(f"prerequisite is not verified: {dependency}")
        record = state.get("pending_substeps", {}).get(dependency, {})
        evidence = _read(_project_file(root, project, record.get("evidence")))
        # 旧项目回执使用 verified；两种回执都仅证明该前置切片，不授予当前产品完成信用。
        if record.get("status") != "verified" or evidence.get("step") != dependency or evidence.get("status") not in {"verified_contribution", "verified"}:
            raise ExecutionError(f"prerequisite evidence does not match: {dependency}")
    ids = scope["source_rows"]
    if not isinstance(ids, list) or not ids or len(set(ids)) != len(ids):
        raise ExecutionError("current source_rows must be a nonempty unique list")
    rows = {row["id"]: row for row in ledger["rows"]}
    if len(rows) != len(ledger["rows"]):
        raise ExecutionError("requirement ledger contains duplicate row ids")
    lines = source.read_text(encoding="utf-8-sig").splitlines()
    selected = []
    for row_id in ids:
        row = rows.get(row_id)
        if not row or not isinstance(row.get("line"), int) or not 1 <= row["line"] <= len(lines):
            raise ExecutionError(f"unknown or out-of-range requirement row: {row_id}")
        text = lines[row["line"] - 1]
        if row["excerpt"] != text or row["source_sha256"] != sha256_bytes(text.encode("utf-8")):
            raise ExecutionError(f"requirement row binding changed: {row_id}")
        selected.append({"id": row_id, "line": row["line"], "section": row.get("section"), "text": text, "status": row["status"]})
    context_roots = [_inside(root, value) for value in control["contextRoots"]]
    if not context_roots or any(not path.is_dir() or not path.is_relative_to(project) for path in context_roots):
        raise ExecutionError("context roots must exist inside active project")
    if not scope.get("entry_files") or not scope.get("acceptance"):
        raise ExecutionError("current scope needs source entry files and acceptance criteria")
    gate_id = scope.get("integration_gate")
    gate = state.get("integration_gates", {}).get(gate_id) if gate_id else None
    if gate_id and (not isinstance(gate, dict) or not gate.get("business_journey")
                    or not gate.get("required_evidence") or not gate.get("next_exact")):
        raise ExecutionError(f"integration gate is not bound to a business journey: {gate_id}")
    gate_order = state.get("integration_gate_order")
    if control.get("enforceIntegrationGateOrder") and gate_order is None:
        raise ExecutionError("integration gate order is required by execution control")
    if gate_order is not None:
        gates = state.get("integration_gates", {})
        if (not isinstance(gate_order, list) or len(gate_order) != len(set(gate_order))
                or set(gate_order) != set(gates)):
            raise ExecutionError("integration gate order must list every gate exactly once")
        for prior_id in gate_order:
            prior = gates[prior_id]
            if prior.get("status") != "verified":
                break
            evidence = _read(_project_file(root, project, prior.get("evidence")))
            if (evidence.get("integration_gate") != prior_id
                    or evidence.get("status") not in {"verified", "verified_contribution"}):
                raise ExecutionError(f"verified integration gate evidence does not match: {prior_id}")
        first_unfinished = next((item for item in gate_order if gates[item].get("status") != "verified"), None)
        if selected_step is None and first_unfinished and gate_id != first_unfinished:
            raise ExecutionError(f"current scope must serve first unfinished integration gate: {first_unfinished}")
    files = []
    for value in scope["entry_files"]:
        candidates = {_inside(base, value) for base in [root, project, *context_roots]}
        candidates = {path for path in candidates if path.is_file() and path.is_relative_to(project)}
        if len(candidates) != 1:
            raise ExecutionError(f"entry file is missing or ambiguous: {value}")
        path = candidates.pop()
        files.append({"path": path.relative_to(root).as_posix(), "sha256": sha256_bytes(path.read_bytes())})
    context = {
        "mode": "bounded_steps", "activeProject": project.relative_to(root).as_posix(),
        "controlSource": control_path.relative_to(root).as_posix(),
        "stateSource": state_path.relative_to(root).as_posix(), "planSource": plan.relative_to(root).as_posix(),
        "requirementSource": source.relative_to(root).as_posix(), "requirementSha256": state["requirement_sha256"],
        "step": step, "parallelBranch": selected_step is not None, "reason": scope.get("reason"),
        "prerequisiteSteps": prerequisites,
        "requirements": selected, "entryFiles": files, "acceptance": scope["acceptance"],
        "integrationGate": {"id": gate_id, **gate} if gate_id else None,
        "environment": state.get("environment_note"), "policy": control["policy"],
        "continuous": workflow["executionLoop"]["continuous"],
        "stoppingPolicySource": workflow_path.relative_to(root).as_posix() + "#executionLoop",
        "openBlockers": [item for item in state.get("blockers", []) if item.get("status") != "resolved"],
        "completionCredit": False,
        "note": "Context and artifact checks do not prove actual business behavior or whole-product completion.",
    }
    maximum = control["maximumContextCharacters"]
    if not isinstance(maximum, int) or maximum < 1000 or len(json.dumps(context, ensure_ascii=False)) > maximum:
        raise ExecutionError("current context exceeds its project-defined bound; split the current substep")
    return context, state, ledger


def execution_context(workspace_path: Path, step: str | None = None) -> dict[str, Any]:
    """只输出当前原文、入口与验收；源码内容按直接依赖另行读取。"""
    try:
        return _context(workspace_path, step)[0]
    except (OSError, KeyError, TypeError, AttributeError) as error:
        raise ExecutionError(f"invalid execution control: {error}") from error


def verify_execution_receipt(workspace_path: Path, receipt_path: Path, step: str | None = None) -> dict[str, Any]:
    """校验当前检查点的完整性，不改写状态、不替代真实页面/API/持久化验证。"""
    try:
        context, state, _ = _context(workspace_path, step)
        root = workspace_path.resolve().parent
        project = _inside(root, context["activeProject"])
        resolved = receipt_path.resolve()
        if not resolved.is_relative_to(project):
            raise ExecutionError("receipt must stay inside active project")
        receipt = _read(resolved)
        missing = set(state["step_record_contract"]["required"]) - receipt.keys()
        if missing or receipt.get("step") != context["step"] or receipt.get("status") != "verified_contribution":
            raise ExecutionError(f"receipt must describe current verified contribution; missing fields: {sorted(missing)}")
        if set(receipt["source_rows"]) != {row["id"] for row in context["requirements"]}:
            raise ExecutionError("receipt source rows must match current scope")
        for key in ["checks_actual_results", "readback", "permission_or_failure_result", "tested_file_hashes", "next_step"]:
            if not receipt[key]:
                raise ExecutionError(f"receipt field cannot be empty: {key}")
        successor = receipt["next_step"]
        if successor == context["step"] or _steps(state).get(successor) not in {"unverified", "in_progress"}:
            raise ExecutionError("receipt next_step must be a declared unfinished successor")
        tested = set()
        for item in receipt["tested_file_hashes"]:
            path = _project_file(root, project, item["path"])
            if item["sha256"] != sha256_bytes(path.read_bytes()):
                raise ExecutionError(f"tested file changed after verification: {item['path']}")
            tested.add(path)
        for value in receipt["readback"]:
            _project_file(root, project, value)
        for value in receipt["changed_files"]:
            if _project_file(root, project, value) not in tested:
                raise ExecutionError(f"changed file has no tested hash: {value}")
        return {"step": context["step"], "nextStep": successor, "artifactIntegrity": "passed", "completionCredit": False,
                "note": "Receipt structure and file freshness passed; actual acceptance still requires current business results."}
    except (OSError, KeyError, TypeError, AttributeError) as error:
        raise ExecutionError(f"invalid execution receipt: {error}") from error


def current_execution_status(workspace_path: Path) -> dict[str, Any]:
    """当前状态不使用旧周期比例；源行数量也不是功能完成百分比。"""
    context = execution_context(workspace_path)
    root = workspace_path.resolve().parent
    state = _read(_inside(root, context["stateSource"]))
    last = state.get("last_result", {})
    stale = []
    for item in last.get("tested_file_hashes", []):
        path = _inside(root, item["path"])
        if not path.is_file() or sha256_bytes(path.read_bytes()) != item["sha256"]:
            stale.append(item["path"])
    return {"mode": context["mode"], "activeProject": context["activeProject"], "stateSource": context["stateSource"],
            "phase": state["phase"], "currentStep": context["step"], "nextStep": state["next_step"],
            "lastContribution": {"step": last.get("step"), "status": last.get("status"), "staleFiles": stale},
            "overallPercent": None, "productCompletion": "not_verified", "continuous": context["continuous"],
            "historicalCyclesUsedForCompletion": False,
            "note": "A verified substep is a bounded contribution. Whole-product completion requires final actual acceptance."}
