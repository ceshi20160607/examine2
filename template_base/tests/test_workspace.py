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

from template_base.execution import current_execution_status
from template_base.workspace import load_workspace, validate_workspace, workspace_status


class WorkspaceTest(unittest.TestCase):
    workspace_path = REPO_ROOT / "workspace.json"

    def test_current_workspace_has_clean_active_and_template_boundaries(self) -> None:
        workspace = load_workspace(self.workspace_path)
        status = workspace_status(workspace, self.workspace_path)
        self.assertTrue(status["boundariesReady"])
        self.assertEqual("projects/unexamine", status["activeProject"])

    def test_project_status_reports_current_bounded_step_without_completion_credit(self) -> None:
        status = current_execution_status(self.workspace_path)
        state_path = REPO_ROOT / "projects" / "unexamine" / "ai" / "planning" / "rebuild-state.json"
        state = json.loads(state_path.read_text(encoding="utf-8"))
        self.assertEqual(state["current_step"], status["currentStep"])
        self.assertEqual("not_verified", status["productCompletion"])
        self.assertIsNone(status["overallPercent"])

    def test_workspace_allows_pending_analysis_without_fragments_or_audit(self) -> None:
        issues = validate_workspace(load_workspace(self.workspace_path), self.workspace_path)
        self.assertFalse(any("requirementFragments" in issue.path for issue in issues))
        self.assertFalse(any("requirementAudit" in issue.path for issue in issues))

    def test_workspace_rejects_unknown_bounded_step(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-step-") as directory:
            root = Path(directory)
            shutil.copytree(REPO_ROOT, root, dirs_exist_ok=True, ignore=shutil.ignore_patterns(".git", "target", "node_modules", "dist", "*.log"))
            state_path = root / "projects" / "unexamine" / "ai" / "planning" / "rebuild-state.json"
            state = json.loads(state_path.read_text(encoding="utf-8"))
            state["current_step"] = "R15_UNKNOWN"
            state_path.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
            path = root / "workspace.json"
            issues = validate_workspace(load_workspace(path), path)
            self.assertTrue(any("current step, next step and scope" in issue.message for issue in issues))

    def test_workspace_rejects_unverified_bounded_prerequisite(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-prerequisite-") as directory:
            root = Path(directory)
            shutil.copytree(REPO_ROOT, root, dirs_exist_ok=True, ignore=shutil.ignore_patterns(".git", "target", "node_modules", "dist", "*.log"))
            state_path = root / "projects" / "unexamine" / "ai" / "planning" / "rebuild-state.json"
            state = json.loads(state_path.read_text(encoding="utf-8"))
            state["packages"][0]["steps"]["R15_TEST_UNVERIFIED"] = "unverified"
            state["current_scope"]["prerequisite_steps"] = ["R15_TEST_UNVERIFIED"]
            state_path.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
            path = root / "workspace.json"
            issues = validate_workspace(load_workspace(path), path)
            self.assertTrue(any("prerequisite is not verified" in issue.message for issue in issues))

    def test_workspace_requires_unambiguous_rules_directory(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-rules-") as directory:
            root = Path(directory)
            shutil.copytree(REPO_ROOT, root, dirs_exist_ok=True, ignore=shutil.ignore_patterns(".git", ".tmp", "target", "node_modules", "dist", "*.log"))
            rules = root / "template_base" / "rules"
            rules.rename(root / "template_base" / "contracts")
            issues = validate_workspace(load_workspace(root / "workspace.json"), root / "workspace.json")
            self.assertTrue(any("must contain rules/" in issue.message for issue in issues))
            self.assertTrue(any("obsolete contracts/" in issue.message for issue in issues))

    def test_workspace_rejects_stale_requirement_hash(self) -> None:
        workspace = copy.deepcopy(load_workspace(self.workspace_path))
        workspace["requirements"]["sha256"] = "0" * 64
        issues = validate_workspace(workspace, self.workspace_path)
        self.assertTrue(any("refresh the workspace baseline" in issue.message for issue in issues))

    def test_workspace_rejects_obsolete_stage_layout(self) -> None:
        project = REPO_ROOT / "projects" / "unexamine"
        stale = project / "program-plan.json"
        stale.write_text("{}\n", encoding="utf-8")
        try:
            issues = validate_workspace(load_workspace(self.workspace_path), self.workspace_path)
            self.assertTrue(any("obsolete project control entry" in issue.message for issue in issues))
        finally:
            stale.unlink()


if __name__ == "__main__":
    unittest.main()
