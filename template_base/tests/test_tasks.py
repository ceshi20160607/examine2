from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.common import canonical_json, sha256_bytes
from template_base.tasks import TaskCatalogError, generate_task_catalog, validate_task_catalog
import test_use_cases


class TaskCatalogTest(unittest.TestCase):
    def _catalog(self, root: Path) -> Path:
        use_case_path = test_use_cases.UseCaseValidationTest()._artifacts(root)
        architecture_path = root / "design.json"
        plan_path = root / "task-layering.json"
        plan_path.write_text(canonical_json({
                "schemaVersion": 1,
                "source": {
                    "architecturePath": "design.json",
                    "architectureSha256": sha256_bytes(architecture_path.read_bytes()),
                    "useCaseCatalogPath": "use-cases.json",
                    "useCaseCatalogSha256": sha256_bytes(use_case_path.read_bytes()),
                },
                "capabilityPlans": [{
                    "capabilityId": "CAP-DATA",
                    "bootstrapKinds": ["database", "base-generation"],
                    "defaultImplementationKinds": ["backend-manage", "frontend"],
                    "useCaseOverrides": [],
                }],
        }), encoding="utf-8")
        output_path = root / "task-catalog.json"
        generate_task_catalog(plan_path, output_path, Path("projects/example"))
        return output_path

    def test_generates_owned_implementation_and_real_entry_verification(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-tasks-") as directory:
            output_path = self._catalog(Path(directory))
            catalog = json.loads(output_path.read_text(encoding="utf-8"))
            self.assertEqual(5, len(catalog["tasks"]))
            self.assertNotIn("estimateMinutes", catalog["tasks"][0])
            self.assertEqual(
                {"database", "base-generation", "backend-manage", "frontend", "integration-test"},
                {item["kind"] for item in catalog["tasks"]},
            )

            catalog["tasks"] = [
                item for item in catalog["tasks"] if item["kind"] != "integration-test"
            ]
            output_path.write_text(canonical_json(catalog), encoding="utf-8")
            with self.assertRaisesRegex(TaskCatalogError, "lack integration-test"):
                validate_task_catalog(output_path)


if __name__ == "__main__":
    unittest.main()
