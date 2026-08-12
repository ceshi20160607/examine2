from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .architecture import load_architecture, require_architecture_ready
from .contract import sha256_bytes
from .requirements import load_intake, require_current_intake


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


def validate_workspace(data: dict[str, Any], workspace_path: Path) -> list[WorkspaceIssue]:
    issues: list[WorkspaceIssue] = []
    root = _object(
        data,
        "$",
        {"schemaVersion", "workspaceId", "requirements", "template", "legacyRoot", "activeProject", "legacyReferences"},
        {"schemaVersion", "workspaceId", "requirements", "template", "legacyRoot", "activeProject", "legacyReferences"},
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
    if template_path is not None and not template_path.is_dir():
        issues.append(WorkspaceIssue("$.template.path", f"directory does not exist: {template_path}"))
    if version_path is not None and not version_path.is_file():
        issues.append(WorkspaceIssue("$.template.versionFile", f"file does not exist: {version_path}"))
    if template_path is not None and version_path is not None and not version_path.is_relative_to(template_path):
        issues.append(WorkspaceIssue("$.template.versionFile", "must be inside the template directory"))

    legacy_root_config = _object(
        root.get("legacyRoot"),
        "$.legacyRoot",
        {"path", "searchPolicy", "ignoreFile"},
        {"path", "searchPolicy", "ignoreFile"},
        issues,
    )
    legacy_root = _relative(legacy_root_config.get("path"), "$.legacyRoot.path", workspace_root, issues)
    if legacy_root_config.get("searchPolicy") != "explicit-only":
        issues.append(WorkspaceIssue("$.legacyRoot.searchPolicy", "must equal explicit-only"))
    ignore_file = _relative(legacy_root_config.get("ignoreFile"), "$.legacyRoot.ignoreFile", workspace_root, issues)
    if legacy_root is not None:
        if not legacy_root.is_dir():
            issues.append(WorkspaceIssue("$.legacyRoot.path", f"directory does not exist: {legacy_root}"))
        relative_legacy_root = legacy_root.relative_to(workspace_root)
        if not relative_legacy_root.parts or relative_legacy_root.parts[0] != ".tmp":
            issues.append(WorkspaceIssue("$.legacyRoot.path", "must be under the hidden .tmp directory"))
        if ignore_file is not None:
            if not ignore_file.is_file():
                issues.append(WorkspaceIssue("$.legacyRoot.ignoreFile", f"file does not exist: {ignore_file}"))
            else:
                ignored_paths = {
                    line.strip().rstrip("/").replace("\\", "/")
                    for line in ignore_file.read_text(encoding="utf-8-sig").splitlines()
                    if line.strip() and not line.lstrip().startswith("#")
                }
                required_ignore = relative_legacy_root.as_posix()
                if required_ignore not in ignored_paths:
                    issues.append(WorkspaceIssue("$.legacyRoot.ignoreFile", f"must exclude {required_ignore}/"))

    active = _object(
        root.get("activeProject"),
        "$.activeProject",
        {"path", "architectureBaseline", "implementationPolicy"},
        {"path", "architectureBaseline", "implementationPolicy"},
        issues,
    )
    active_path = _relative(active.get("path"), "$.activeProject.path", workspace_root, issues)
    architecture_path = _relative(active.get("architectureBaseline"), "$.activeProject.architectureBaseline", workspace_root, issues)
    if active.get("implementationPolicy") != "new-code-only":
        issues.append(WorkspaceIssue("$.activeProject.implementationPolicy", "must equal new-code-only"))
    if active_path is not None and not active_path.is_dir():
        issues.append(WorkspaceIssue("$.activeProject.path", f"directory does not exist: {active_path}"))
    if active_path is not None and architecture_path is not None and not architecture_path.is_relative_to(active_path):
        issues.append(WorkspaceIssue("$.activeProject.architectureBaseline", "must be inside the active project"))
    if architecture_path is not None:
        if not architecture_path.is_file():
            issues.append(WorkspaceIssue("$.activeProject.architectureBaseline", f"file does not exist: {architecture_path}"))
        else:
            try:
                architecture = load_architecture(architecture_path)
                require_architecture_ready(architecture, architecture_path)
                architecture_source = (architecture_path.parent / architecture["source"]["path"]).resolve()
                if requirement_path is not None and architecture_source != requirement_path:
                    issues.append(WorkspaceIssue("$.activeProject.architectureBaseline", "must bind to the canonical requirements"))
            except ValueError as error:
                issues.append(WorkspaceIssue("$.activeProject.architectureBaseline", str(error)))

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

    legacy = root.get("legacyReferences")
    legacy_paths: list[Path] = []
    if not isinstance(legacy, list) or not legacy:
        issues.append(WorkspaceIssue("$.legacyReferences", "must contain at least one reference"))
    else:
        seen: set[Path] = set()
        for index, raw_reference in enumerate(legacy):
            path = f"$.legacyReferences[{index}]"
            reference = _object(raw_reference, path, {"path", "purpose", "writePolicy"}, {"path", "purpose", "writePolicy"}, issues)
            resolved = _relative(reference.get("path"), f"{path}.path", workspace_root, issues)
            if not isinstance(reference.get("purpose"), str) or not reference.get("purpose", "").strip():
                issues.append(WorkspaceIssue(f"{path}.purpose", "must be a non-blank string"))
            if reference.get("writePolicy") != "reference-only":
                issues.append(WorkspaceIssue(f"{path}.writePolicy", "must equal reference-only"))
            if resolved is not None:
                if not resolved.exists():
                    issues.append(WorkspaceIssue(f"{path}.path", f"path does not exist: {resolved}"))
                if resolved in seen:
                    issues.append(WorkspaceIssue(f"{path}.path", "duplicate legacy reference"))
                seen.add(resolved)
                legacy_paths.append(resolved)

    if legacy_root is not None:
        for index, legacy_path in enumerate(legacy_paths):
            if not legacy_path.is_relative_to(legacy_root):
                issues.append(WorkspaceIssue(f"$.legacyReferences[{index}].path", "must be inside legacyRoot"))

    if active_path is not None and template_path is not None and _overlap(active_path, template_path):
        issues.append(WorkspaceIssue("$.activeProject.path", "must not overlap the template directory"))
    if legacy_root is not None:
        if active_path is not None and _overlap(active_path, legacy_root):
            issues.append(WorkspaceIssue("$.legacyRoot.path", "must not overlap the active project"))
        if template_path is not None and _overlap(template_path, legacy_root):
            issues.append(WorkspaceIssue("$.legacyRoot.path", "must not overlap the template directory"))
    for index, legacy_path in enumerate(legacy_paths):
        if active_path is not None and _overlap(active_path, legacy_path):
            issues.append(WorkspaceIssue(f"$.legacyReferences[{index}].path", "must not overlap the active project"))
        if template_path is not None and _overlap(template_path, legacy_path):
            issues.append(WorkspaceIssue(f"$.legacyReferences[{index}].path", "must not overlap the template directory"))
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
        "legacyReferenceCount": len(data["legacyReferences"]),
        "boundariesReady": True,
    }
