from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.common import canonical_json, sha256_bytes
from template_base.scheduling import ScheduleError, generate_task_graph, validate_task_graph
import test_tasks


class SchedulingTest(unittest.TestCase):
    def test_generates_dependency_ordered_four_hour_cycles(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-schedule-") as directory:
            root = Path(directory)
            catalog_path = test_tasks.TaskCatalogTest()._catalog(root)
            plan_path = root / "schedule-plan.json"
            plan_path.write_text(canonical_json({
                "schemaVersion": 1,
                "source": {
                    "taskCatalogPath": "task-catalog.json",
                    "taskCatalogSha256": sha256_bytes(catalog_path.read_bytes()),
                },
                "foundationPhaseId": "PHASE-01",
                "phases": [{
                    "id": "PHASE-01",
                    "order": 1,
                    "name": "可用数据闭环",
                    "outcome": "真实页面可以新建并读回数据。",
                    "dependsOn": [],
                    "capabilityIds": ["CAP-DATA"],
                }],
                "defaultEstimates": {
                    "design": 30,
                    "database": 60,
                    "base-generation": 15,
                    "backend-manage": 60,
                    "frontend": 45,
                    "integration-test": 15,
                    "operations": 90,
                },
                "capabilityEstimateFactors": [],
                "taskEstimateOverrides": [],
                "taskPhaseOverrides": [],
                "existingImplementationAssessment": [{
                    "path": "projects/example/src",
                    "treatment": "replace",
                    "taskIds": ["TASK-0003"],
                    "rationale": "示例实现不满足真实持久化。",
                }],
            }), encoding="utf-8")
            graph_path = root / "task-graph.json"
            result = generate_task_graph(plan_path, graph_path)
            self.assertEqual(1, result["cycleCount"])
            self.assertEqual(5, result["taskCount"])

            graph = json.loads(graph_path.read_text(encoding="utf-8"))
            self.assertEqual(["UC-DATA-CREATE"], graph["cycles"][0]["journeyUseCaseIds"])
            graph["cycles"][0]["taskIds"].pop()
            graph["cycles"][0]["journeyUseCaseIds"] = []
            graph_path.write_text(canonical_json(graph), encoding="utf-8")
            with self.assertRaisesRegex(ScheduleError, "place every catalog task"):
                validate_task_graph(graph_path)


if __name__ == "__main__":
    unittest.main()
