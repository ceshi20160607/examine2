from __future__ import annotations

import copy
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = BASE_ROOT.parent
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.architecture import ArchitectureError, load_architecture, require_architecture_ready, validate_architecture
from template_base.workspace import load_workspace, validate_workspace, workspace_status


class ArchitectureAndWorkspaceTest(unittest.TestCase):
    architecture_path = REPO_ROOT / "projects" / "unexamine" / "architecture-baseline.json"
    workspace_path = REPO_ROOT / "workspace.json"

    def test_runtime_platform_requires_runtime_metadata(self) -> None:
        baseline = load_architecture(self.architecture_path)
        self.assertEqual([], validate_architecture(baseline, self.architecture_path))
        invalid = copy.deepcopy(baseline)
        invalid["application"]["businessModuleRealization"] = "compile-time-generated"
        issues = validate_architecture(invalid, self.architecture_path)
        self.assertTrue(any("runtime metadata" in issue.message for issue in issues))

    def test_unresolved_architecture_decision_blocks_development(self) -> None:
        baseline = copy.deepcopy(load_architecture(self.architecture_path))
        baseline["unresolvedDecisions"] = [{"id": "DEC-X", "question": "未决问题", "blocking": True}]
        with self.assertRaises(ArchitectureError):
            require_architecture_ready(baseline, self.architecture_path)

    def test_current_workspace_has_separate_active_template_and_legacy_roots(self) -> None:
        workspace = load_workspace(self.workspace_path)
        status = workspace_status(workspace, self.workspace_path)
        self.assertTrue(status["boundariesReady"])
        self.assertEqual("projects/unexamine", status["activeProject"])
        self.assertGreaterEqual(status["legacyReferenceCount"], 5)

    def test_workspace_rejects_active_project_inside_legacy(self) -> None:
        workspace = copy.deepcopy(load_workspace(self.workspace_path))
        workspace["legacyRoot"]["path"] = "projects"
        issues = validate_workspace(workspace, self.workspace_path)
        self.assertTrue(any("must not overlap the active project" in issue.message for issue in issues))

    def test_workspace_rejects_reference_outside_hidden_legacy_root(self) -> None:
        workspace = copy.deepcopy(load_workspace(self.workspace_path))
        workspace["legacyReferences"][0]["path"] = "docs"
        issues = validate_workspace(workspace, self.workspace_path)
        self.assertTrue(any("must be inside legacyRoot" in issue.message for issue in issues))

    def test_workspace_rejects_missing_legacy_search_exclusion(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-ignore-") as directory:
            root = Path(directory)
            shutil.copytree(REPO_ROOT / "projects", root / "projects")
            shutil.copytree(REPO_ROOT / "docs", root / "docs")
            (root / "template_base").mkdir()
            shutil.copyfile(BASE_ROOT / "VERSION", root / "template_base" / "VERSION")
            legacy_root = root / ".tmp" / "legacy_reference"
            for relative in (
                ".base",
                ".cursor",
                "backend",
                "frontend",
                "sql",
                "delivery",
                "performance",
                "docs/design",
            ):
                (legacy_root / relative).mkdir(parents=True, exist_ok=True)
            for relative in ("docs/temp_flow.md", "docs/temp_flow_persion.html", "docs/user_setting.md"):
                target = legacy_root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text("legacy fixture\n", encoding="utf-8")
            workspace = copy.deepcopy(load_workspace(self.workspace_path))
            (root / ".ignore").write_text("template_base/examples/*/generated/\n", encoding="utf-8")
            temp_workspace_path = root / "workspace.json"
            temp_workspace_path.write_text(json.dumps(workspace, ensure_ascii=False), encoding="utf-8")
            issues = validate_workspace(workspace, temp_workspace_path)
            self.assertTrue(any("must exclude .tmp/legacy_reference/" in issue.message for issue in issues))

    def test_workspace_rejects_stale_requirement_hash(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-workspace-") as directory:
            root = Path(directory)
            shutil.copytree(REPO_ROOT / "projects", root / "projects")
            shutil.copytree(REPO_ROOT / "docs", root / "docs")
            shutil.copytree(BASE_ROOT, root / "template_base")
            workspace = copy.deepcopy(load_workspace(self.workspace_path))
            workspace["requirements"]["sha256"] = "0" * 64
            temp_workspace_path = root / "workspace.json"
            temp_workspace_path.write_text(json.dumps(workspace, ensure_ascii=False), encoding="utf-8")
            issues = validate_workspace(workspace, temp_workspace_path)
            self.assertTrue(any("refresh the workspace baseline" in issue.message for issue in issues))


if __name__ == "__main__":
    unittest.main()
