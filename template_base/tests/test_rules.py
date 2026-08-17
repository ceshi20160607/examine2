from __future__ import annotations

import sys
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
                "project-starter.schema.json",
                "project-status.schema.json",
                "requirement-analysis.schema.json",
                "requirement-fragment-audit.schema.json",
                "requirement-fragment-index.schema.json",
                "requirement-fragment.schema.json",
                "workspace.schema.json",
            ],
            names,
        )
        for name in names:
            self.assertIsInstance(load_rule(name), dict)

    def test_rule_rejects_unknown_fields(self) -> None:
        issues = validate_rule({"schemaVersion": 1, "unexpected": True}, "project-starter.schema.json")
        self.assertTrue(any(issue.path == "$.unexpected" for issue in issues))


if __name__ == "__main__":
    unittest.main()
