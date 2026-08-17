from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .common import sha256_bytes
from .requirements import RequirementError, load_intake, require_current_intake, validate_analysis_fragment
from .rules import validate_rule


CANONICAL_GATE_IDS = [
    "G01_REQUIREMENTS",
    "G02_CAPABILITY_OWNERSHIP",
    "G03_USE_CASES_TASKS",
    "G04_DEPENDENCY_CODE_ASSESSMENT",
    "G05_DATABASE_DESIGN",
    "G06_BASE_GENERATION",
    "G07_MANAGE_IMPLEMENTATION",
    "G08_FRONTEND_IMPLEMENTATION",
    "G09_CYCLE_VERIFICATION",
]


@dataclass(frozen=True)
class WorkspaceIssue:
    path: str
    message: str

    def __str__(self) -> str:
        return f"{self.path}: {self.message}"


class WorkspaceError(ValueError):
    def __init__(self, issues: list[WorkspaceIssue]):
        super().__init__("\n".join(str(issue) for issue in issues))
        self.issues = issues


def load_workspace(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise WorkspaceError([WorkspaceIssue("$", f"cannot read workspace definition: {error}")]) from error
    if not isinstance(value, dict):
        raise WorkspaceError([WorkspaceIssue("$", "workspace definition must be an object")])
    return value


def _object(
    value: Any,
    path: str,
    required: set[str],
    allowed: set[str],
    issues: list[WorkspaceIssue],
) -> dict[str, Any]:
    if not isinstance(value, dict):
        issues.append(WorkspaceIssue(path, "must be an object"))
        return {}
    for key in sorted(required - value.keys()):
        issues.append(WorkspaceIssue(f"{path}.{key}", "is required"))
    for key in sorted(value.keys() - allowed):
        issues.append(WorkspaceIssue(f"{path}.{key}", "is not allowed"))
    return value


def _relative(value: Any, path: str, root: Path, issues: list[WorkspaceIssue]) -> Path | None:
    if not isinstance(value, str) or not value.strip():
        issues.append(WorkspaceIssue(path, "must be a non-blank relative path"))
        return None
    candidate = Path(value)
    if candidate.is_absolute() or ".." in candidate.parts:
        issues.append(WorkspaceIssue(path, "must stay inside the workspace"))
        return None
    return (root / candidate).resolve()


def _overlap(first: Path, second: Path) -> bool:
    return first == second or first.is_relative_to(second) or second.is_relative_to(first)


def _json_file(path: Path, issue_path: str, issues: list[WorkspaceIssue]) -> dict[str, Any] | None:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        issues.append(WorkspaceIssue(issue_path, f"cannot read JSON: {path}: {error}"))
        return None
    if not isinstance(value, dict):
        issues.append(WorkspaceIssue(issue_path, "must contain a JSON object"))
        return None
    return value


def _validate_project_control(
    active_path: Path,
    workflow: dict[str, Any] | None,
    issues: list[WorkspaceIssue],
) -> None:
    status_path = active_path / "ai" / "status" / "project-status.json"
    assignment_path = active_path / "ai" / "agents" / "assignments.json"
    intake_path = active_path / "ai" / "requirements" / "requirement-intake.json"
    plan_path = active_path / "ai" / "requirements" / "analysis-plan.json"
    audit_path = active_path / "ai" / "evidence" / "requirement-fragment-audit.json"
    for path, rule_name, issue_path in (
        (status_path, "project-status.schema.json", "$.activeProject.status"),
        (assignment_path, "agent-assignments.schema.json", "$.activeProject.assignments"),
    ):
        value = _json_file(path, issue_path, issues)
        if value is not None:
            issues.extend(WorkspaceIssue(issue.path, issue.message) for issue in validate_rule(value, rule_name))

    status = _json_file(status_path, "$.activeProject.status", issues)
    intake = _json_file(intake_path, "$.requirements.intake", issues)
    plan = _json_file(plan_path, "$.activeProject.analysisPlan", issues)
    if status is not None and workflow is not None:
        gates = workflow.get("gates", [])
        gate_ids = [gate.get("id") for gate in gates if isinstance(gate, dict)]
        if gate_ids != CANONICAL_GATE_IDS:
            issues.append(WorkspaceIssue("$.template.workflow.gates", "must equal the canonical nine-gate sequence"))
        ordinals = [gate.get("ordinal") for gate in gates if isinstance(gate, dict)]
        if ordinals != list(range(1, len(gates) + 1)):
            issues.append(WorkspaceIssue("$.template.workflow.gates", "gate ordinals must be continuous and ordered"))
        if status.get("currentGateId") not in gate_ids:
            issues.append(WorkspaceIssue("$.activeProject.status.currentGateId", "must reference a canonical workflow gate"))
    if status is not None and intake is not None and plan is not None:
        packets = plan.get("packets", [])
        planned_ids = {packet.get("id") for packet in packets if isinstance(packet, dict)}
        fragment_root = active_path / "ai" / "requirements" / "fragments"
        extracted_ids = {path.stem for path in fragment_root.glob("RWP-*.json")} if fragment_root.is_dir() else set()
        if fragment_root.is_dir():
            for fragment_path in sorted(fragment_root.glob("RWP-*.json")):
                try:
                    validate_analysis_fragment(fragment_path)
                except RequirementError as error:
                    issues.append(WorkspaceIssue("$.activeProject.requirementFragments", str(error)))
        if not extracted_ids <= planned_ids:
            issues.append(WorkspaceIssue("$.activeProject.requirementFragments", "fragment directory contains unknown packet ids"))

        if audit_path.is_file():
            audit = _json_file(audit_path, "$.activeProject.requirementAudit", issues)
            if audit is not None:
                issues.extend(
                    WorkspaceIssue(issue.path, issue.message)
                    for issue in validate_rule(audit, "requirement-fragment-audit.schema.json")
                )
                if audit.get("source", {}).get("planPath") != "../requirements/analysis-plan.json":
                    issues.append(WorkspaceIssue("$.activeProject.requirementAudit.source.planPath", "must point to the active project's analysis plan"))
                if audit.get("source", {}).get("planSha256") != sha256_bytes(plan_path.read_bytes()):
                    issues.append(WorkspaceIssue("$.activeProject.requirementAudit.source.planSha256", "must bind to the current analysis plan"))
                audit_items = audit.get("audits", [])
                audit_ids = [item.get("packetId") for item in audit_items if isinstance(item, dict)]
                if len(audit_ids) != len(set(audit_ids)):
                    issues.append(WorkspaceIssue("$.activeProject.requirementAudit.audits", "packet ids must be unique"))
                if not set(audit_ids) <= extracted_ids:
                    issues.append(WorkspaceIssue("$.activeProject.requirementAudit.audits", "can only audit extracted fragments"))
                for index, item in enumerate(audit_items):
                    if not isinstance(item, dict):
                        continue
                    outcome = item.get("outcome")
                    audit_issues = item.get("issues")
                    if outcome == "accepted" and audit_issues:
                        issues.append(WorkspaceIssue(f"$.activeProject.requirementAudit.audits[{index}]", "accepted packet cannot contain issues"))
                    if outcome == "revise" and not audit_issues:
                        issues.append(WorkspaceIssue(f"$.activeProject.requirementAudit.audits[{index}]", "revise packet must contain at least one issue"))
                    fragment_path = fragment_root / f"{item.get('packetId')}.json"
                    fragment = _json_file(fragment_path, f"$.activeProject.requirementAudit.audits[{index}].packetId", issues)
                    if fragment_path.is_file() and item.get("fragmentSha256") != sha256_bytes(fragment_path.read_bytes()):
                        issues.append(WorkspaceIssue(
                            f"$.activeProject.requirementAudit.audits[{index}].fragmentSha256",
                            "must bind to the current fragment content",
                        ))
                    requirement_ids = {
                        requirement.get("id")
                        for requirement in fragment.get("requirements", [])
                        if isinstance(requirement, dict)
                    } if fragment is not None else set()
                    for issue_index, audit_issue in enumerate(audit_issues if isinstance(audit_issues, list) else []):
                        if not isinstance(audit_issue, dict):
                            continue
                        unknown = set(audit_issue.get("requirementIds", [])) - requirement_ids
                        if unknown:
                            issues.append(WorkspaceIssue(
                                f"$.activeProject.requirementAudit.audits[{index}].issues[{issue_index}].requirementIds",
                                f"references unknown requirement ids: {sorted(unknown)}",
                            ))

    assignments = _json_file(assignment_path, "$.activeProject.assignments", issues)
    if assignments is not None:
        items = assignments.get("assignments", [])
        ids = {item.get("id") for item in items if isinstance(item, dict)}
        for index, item in enumerate(items):
            if not isinstance(item, dict):
                continue
            unknown = set(item.get("blockedBy", [])) - ids
            if unknown:
                issues.append(WorkspaceIssue(f"$.activeProject.assignments[{index}].blockedBy", f"references unknown assignments: {sorted(unknown)}"))


def validate_workspace(data: dict[str, Any], workspace_path: Path) -> list[WorkspaceIssue]:
    issues = [WorkspaceIssue(issue.path, issue.message) for issue in validate_rule(data, "workspace.schema.json")]
    root = _object(
        data,
        "$",
        {"schemaVersion", "workspaceId", "requirements", "template", "activeProject"},
        {"schemaVersion", "workspaceId", "requirements", "template", "activeProject"},
        issues,
    )
    if root.get("schemaVersion") != 1:
        issues.append(WorkspaceIssue("$.schemaVersion", "must equal 1"))
    if not isinstance(root.get("workspaceId"), str) or not root.get("workspaceId", "").strip():
        issues.append(WorkspaceIssue("$.workspaceId", "must be a non-blank string"))

    workspace_root = workspace_path.resolve().parent
    requirements = _object(
        root.get("requirements"),
        "$.requirements",
        {"path", "sha256", "intake"},
        {"path", "sha256", "intake"},
        issues,
    )
    requirement_path = _relative(requirements.get("path"), "$.requirements.path", workspace_root, issues)
    intake_path = _relative(requirements.get("intake"), "$.requirements.intake", workspace_root, issues)
    requirement_hash = requirements.get("sha256")
    if not isinstance(requirement_hash, str) or len(requirement_hash) != 64:
        issues.append(WorkspaceIssue("$.requirements.sha256", "must be a SHA-256"))
    elif requirement_path is not None:
        if not requirement_path.is_file():
            issues.append(WorkspaceIssue("$.requirements.path", f"file does not exist: {requirement_path}"))
        elif sha256_bytes(requirement_path.read_bytes()) != requirement_hash:
            issues.append(WorkspaceIssue("$.requirements.sha256", "requirements changed; refresh the workspace baseline"))

    template = _object(root.get("template"), "$.template", {"path", "versionFile"}, {"path", "versionFile"}, issues)
    template_path = _relative(template.get("path"), "$.template.path", workspace_root, issues)
    version_path = _relative(template.get("versionFile"), "$.template.versionFile", workspace_root, issues)
    workflow: dict[str, Any] | None = None
    if template_path is not None and not template_path.is_dir():
        issues.append(WorkspaceIssue("$.template.path", f"directory does not exist: {template_path}"))
    if template_path is not None and template_path.is_dir():
        if not (template_path / "rules").is_dir():
            issues.append(WorkspaceIssue("$.template.path", "template must contain rules/ for machine validation rules"))
        if (template_path / "contracts").exists():
            issues.append(WorkspaceIssue("$.template.path", "obsolete contracts/ directory must not exist; use rules/"))
        workflow_path = template_path / "agents" / "workflow.json"
        workflow = _json_file(workflow_path, "$.template.workflow", issues)
        if workflow is not None:
            issues.extend(
                WorkspaceIssue(issue.path, issue.message)
                for issue in validate_rule(workflow, "agent-workflow.schema.json")
            )
            for role in workflow.get("roles", []):
                role_id = role.get("id") if isinstance(role, dict) else None
                if isinstance(role_id, str) and not (template_path / "agents" / f"{role_id}.md").is_file():
                    issues.append(WorkspaceIssue("$.template.workflow", f"role instruction is missing: agents/{role_id}.md"))
    if version_path is not None and not version_path.is_file():
        issues.append(WorkspaceIssue("$.template.versionFile", f"file does not exist: {version_path}"))
    if template_path is not None and version_path is not None and not version_path.is_relative_to(template_path):
        issues.append(WorkspaceIssue("$.template.versionFile", "must be inside the template directory"))

    active = _object(
        root.get("activeProject"),
        "$.activeProject",
        {"path"},
        {"path"},
        issues,
    )
    active_path = _relative(active.get("path"), "$.activeProject.path", workspace_root, issues)
    if active_path is not None and not active_path.is_dir():
        issues.append(WorkspaceIssue("$.activeProject.path", f"directory does not exist: {active_path}"))
    if active_path is not None and active_path.is_dir():
        required_directories = ("backend", "frontend", "db", "deploy", "tools", "ai")
        for directory in required_directories:
            if not (active_path / directory).is_dir():
                issues.append(WorkspaceIssue(
                    "$.activeProject.path",
                    f"active project must contain the top-level {directory}/ directory",
                ))
        forbidden_obsolete_entries = (
            "app",
            "stages",
            "cycles",
            "program-plan.json",
            "architecture-baseline.json",
            "project-starter.json",
            "base-codegen.json",
        )
        for entry in forbidden_obsolete_entries:
            if (active_path / entry).exists():
                issues.append(WorkspaceIssue(
                    "$.activeProject.path",
                    f"obsolete project control entry must not exist: {entry}",
                ))
        requirement_control_root = active_path / "ai" / "requirements"
        obsolete_partial_files = (
            list(requirement_control_root.glob("slice-*.scope.json"))
            + list(requirement_control_root.glob("slice-*.plan.json"))
        )
        for stale in sorted(obsolete_partial_files):
            issues.append(WorkspaceIssue(
                "$.activeProject.path",
                f"obsolete partial requirement slice must not exist: {stale.relative_to(active_path).as_posix()}",
            ))
        _validate_project_control(active_path, workflow, issues)
    if intake_path is not None:
        if not intake_path.is_file():
            issues.append(WorkspaceIssue("$.requirements.intake", f"file does not exist: {intake_path}"))
        else:
            try:
                intake_source = require_current_intake(load_intake(intake_path), intake_path)
                if requirement_path is not None and intake_source != requirement_path:
                    issues.append(WorkspaceIssue("$.requirements.intake", "must index the canonical requirements"))
            except ValueError as error:
                issues.append(WorkspaceIssue("$.requirements.intake", str(error)))
    if active_path is not None and intake_path is not None and not intake_path.is_relative_to(active_path):
        issues.append(WorkspaceIssue("$.requirements.intake", "must be stored inside the active project"))

    if active_path is not None and template_path is not None and _overlap(active_path, template_path):
        issues.append(WorkspaceIssue("$.activeProject.path", "must not overlap the template directory"))
    return issues


def require_valid_workspace(data: dict[str, Any], workspace_path: Path) -> None:
    issues = validate_workspace(data, workspace_path)
    if issues:
        raise WorkspaceError(issues)


def workspace_status(data: dict[str, Any], workspace_path: Path) -> dict[str, Any]:
    require_valid_workspace(data, workspace_path)
    return {
        "workspaceId": data["workspaceId"],
        "requirementsSha256": data["requirements"]["sha256"],
        "activeProject": data["activeProject"]["path"],
        "boundariesReady": True,
    }


def computed_project_status(data: dict[str, Any], workspace_path: Path) -> dict[str, Any]:
    require_valid_workspace(data, workspace_path)
    workspace_root = workspace_path.resolve().parent
    active_path = (workspace_root / data["activeProject"]["path"]).resolve()
    template_path = (workspace_root / data["template"]["path"]).resolve()
    status = json.loads((active_path / "ai" / "status" / "project-status.json").read_text(encoding="utf-8-sig"))
    workflow = json.loads((template_path / "agents" / "workflow.json").read_text(encoding="utf-8-sig"))
    intake = json.loads((active_path / "ai" / "requirements" / "requirement-intake.json").read_text(encoding="utf-8-sig"))
    plan = json.loads((active_path / "ai" / "requirements" / "analysis-plan.json").read_text(encoding="utf-8-sig"))

    gates = workflow["gates"]
    current_gate = next(gate for gate in gates if gate["id"] == status["currentGateId"])
    fragment_paths = sorted((active_path / "ai" / "requirements" / "fragments").glob("RWP-*.json"))
    candidate_requirements = 0
    decisions = 0
    covered_blocks = 0
    for fragment_path in fragment_paths:
        fragment = json.loads(fragment_path.read_text(encoding="utf-8-sig"))
        candidate_requirements += len(fragment.get("requirements", []))
        decisions += len(fragment.get("decisions", []))
        covered_blocks += len(fragment.get("coverage", []))

    audit_path = active_path / "ai" / "evidence" / "requirement-fragment-audit.json"
    audits: list[dict[str, Any]] = []
    if audit_path.is_file():
        audit = json.loads(audit_path.read_text(encoding="utf-8-sig"))
        audits = [item for item in audit.get("audits", []) if isinstance(item, dict)]
    accepted = sum(1 for item in audits if item.get("outcome") == "accepted")
    revise = sum(1 for item in audits if item.get("outcome") == "revise")
    packet_count = plan["packetCount"]
    coverable_blocks = sum(
        1 for block in intake.get("blocks", [])
        if isinstance(block, dict) and block.get("kind") != "heading"
    )

    return {
        "asOf": status["asOf"],
        "workflow": {
            "source": "template_base/agents/workflow.json",
            "totalGates": len(gates),
            "currentGate": {
                **current_gate,
                "status": status["currentGateStatus"],
            },
        },
        "requirements": {
            "source": data["requirements"]["path"],
            "sourceBlocks": intake["blockCount"],
            "coverableBlocks": coverable_blocks,
            "analysisPackets": packet_count,
            "extractedPackets": len(fragment_paths),
            "structurallyValidPackets": len(fragment_paths),
            "remainingExtractionPackets": packet_count - len(fragment_paths),
            "coveredBlocks": covered_blocks,
            "candidateRequirements": candidate_requirements,
            "decisions": decisions,
            "semanticAudit": {
                "auditedPackets": len(audits),
                "acceptedPackets": accepted,
                "revisePackets": revise,
                "unauditedExtractedPackets": len(fragment_paths) - len(audits),
            },
            "extractionPercent": round(len(fragment_paths) * 100 / packet_count, 1) if packet_count else 0.0,
            "sourceCoveragePercent": round(covered_blocks * 100 / coverable_blocks, 1) if coverable_blocks else 0.0,
        },
        "scheduling": {
            "maximumCycleMinutes": workflow["cycle"]["maximumMinutes"],
            **status["scheduling"],
        },
        "existingImplementation": status["existingImplementation"],
    }
