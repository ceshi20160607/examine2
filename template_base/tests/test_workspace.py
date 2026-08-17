from __future__ import annotations

import copy
import shutil
import sys
import tempfile
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = BASE_ROOT.parent
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.workspace import computed_project_status, load_workspace, validate_workspace, workspace_status


class WorkspaceTest(unittest.TestCase):
    workspace_path = REPO_ROOT / "workspace.json"

    def test_current_workspace_has_clean_active_and_template_boundaries(self) -> None:
        workspace = load_workspace(self.workspace_path)
        status = workspace_status(workspace, self.workspace_path)
        self.assertTrue(status["boundariesReady"])
        self.assertEqual("projects/unexamine", status["activeProject"])

    def test_project_status_is_computed_from_canonical_workflow_and_fragments(self) -> None:
        status = computed_project_status(load_workspace(self.workspace_path), self.workspace_path)
        self.assertEqual(9, status["workflow"]["totalGates"])
        self.assertEqual("G01_REQUIREMENTS", status["workflow"]["currentGate"]["id"])
        fragment_count = len(list((REPO_ROOT / "projects" / "unexamine" / "ai" / "requirements" / "fragments").glob("RWP-*.json")))
        self.assertEqual(fragment_count, status["requirements"]["extractedPackets"])
        self.assertEqual(fragment_count, status["requirements"]["structurallyValidPackets"])
        self.assertNotIn("completedPackets", status["requirements"])

        self.assertEqual(0, status["requirements"]["semanticAudit"]["auditedPackets"])
        self.assertEqual(0, status["requirements"]["semanticAudit"]["acceptedPackets"])

    def test_workspace_allows_pending_analysis_without_fragments_or_audit(self) -> None:
        issues = validate_workspace(load_workspace(self.workspace_path), self.workspace_path)
        self.assertFalse(any("requirementFragments" in issue.path for issue in issues))
        self.assertFalse(any("requirementAudit" in issue.path for issue in issues))

    def test_workspace_rejects_unknown_current_gate(self) -> None:
        project_status = REPO_ROOT / "projects" / "unexamine" / "ai" / "status" / "project-status.json"
        original = project_status.read_text(encoding="utf-8")
        project_status.write_text(original.replace("G01_REQUIREMENTS", "G99_INVENTED"), encoding="utf-8")
        try:
            issues = validate_workspace(load_workspace(self.workspace_path), self.workspace_path)
            self.assertTrue(any("canonical workflow gate" in issue.message for issue in issues))
        finally:
            project_status.write_text(original, encoding="utf-8")

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
