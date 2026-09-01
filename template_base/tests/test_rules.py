from __future__ import annotations

import sys
import json
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.rules import load_rule, validate_rule


class MachineRulesTest(unittest.TestCase):
    def test_every_rule_is_loadable(self) -> None:
        names = sorted(path.name for path in (BASE_ROOT / "rules").glob("*.json"))
        self.assertEqual(
            [
                "agent-assignments.schema.json",
                "agent-workflow.schema.json",
                "architecture-design.schema.json",
                "cycle-evidence.schema.json",
                "project-starter.schema.json",
                "project-status.schema.json",
                "requirement-analysis.schema.json",
                "requirement-consolidation.schema.json",
                "requirement-fragment-audit.schema.json",
                "requirement-fragment-index.schema.json",
                "requirement-fragment.schema.json",
                "schedule-plan.schema.json",
                "task-catalog.schema.json",
                "task-graph.schema.json",
                "task-layering.schema.json",
                "use-case-catalog.schema.json",
                "workspace.schema.json",
            ],
            names,
        )
        for name in names:
            self.assertIsInstance(load_rule(name), dict)

    def test_rule_rejects_unknown_fields(self) -> None:
        issues = validate_rule({"schemaVersion": 1, "unexpected": True}, "project-starter.schema.json")
        self.assertTrue(any(issue.path == "$.unexpected" for issue in issues))

    def test_agent_workflow_requires_continuous_execution_loop(self) -> None:
        workflow_path = BASE_ROOT / "agents" / "workflow.json"
        workflow = json.loads(workflow_path.read_text(encoding="utf-8"))
        self.assertEqual([], validate_rule(workflow, "agent-workflow.schema.json"))

        workflow.pop("executionLoop")
        issues = validate_rule(workflow, "agent-workflow.schema.json")
        self.assertTrue(any(issue.path == "$.executionLoop" for issue in issues))


if __name__ == "__main__":
    unittest.main()
