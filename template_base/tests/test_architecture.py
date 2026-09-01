from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.architecture import ArchitectureError, validate_architecture
from template_base.common import canonical_json, sha256_bytes
from template_base.requirements import build_intake


class ArchitectureValidationTest(unittest.TestCase):
    def _artifacts(self, root: Path) -> Path:
        requirement_path = root / "user_requirement.md"
        intake_path = root / "requirement-intake.json"
        analysis_path = root / "requirement-analysis.json"
        design_path = root / "design.json"
        requirement_path.write_text("# 数据\n\n- 用户可以新建数据。\n", encoding="utf-8")
        build_intake(requirement_path, intake_path)
        intake = json.loads(intake_path.read_text(encoding="utf-8"))
        block_id = next(item["id"] for item in intake["blocks"] if item["kind"] != "heading")
        analysis = {
            "schemaVersion": 1,
            "source": {
                "intakePath": "requirement-intake.json",
                "intakeSha256": sha256_bytes(intake_path.read_bytes()),
                "requirementsSha256": intake["source"]["sha256"],
            },
            "requirements": [{
                "id": "REQ-DATA-CREATE",
                "title": "新建数据",
                "statement": "用户可以新建数据。",
                "type": "functional",
                "priority": "must",
                "origin": "explicit",
                "status": "confirmed",
                "sourceBlocks": [block_id],
                "acceptanceCriteria": ["保存后可以读回数据。"],
                "decisionIds": [],
            }],
            "decisions": [],
            "coverage": [{
                "sourceBlock": block_id,
                "classification": "requirement",
                "requirementIds": ["REQ-DATA-CREATE"],
                "rationale": "直接需求",
            }],
        }
        analysis_path.write_text(canonical_json(analysis), encoding="utf-8")
        design = {
            "schemaVersion": 1,
            "source": {
                "requirementAnalysisPath": "requirement-analysis.json",
                "requirementAnalysisSha256": sha256_bytes(analysis_path.read_bytes()),
            },
            "contexts": [{
                "id": "CTX-SYSTEM",
                "name": "系统上下文",
                "isolationKeys": ["system_id", "tenant_id"],
                "entryRules": ["先解析系统和租户。"],
            }],
            "capabilities": [{
                "id": "CAP-DATA",
                "name": "数据",
                "ownerModule": "data",
                "contextIds": ["CTX-SYSTEM"],
                "storageModel": "physical-tables",
                "requirementIds": ["REQ-DATA-CREATE"],
                "responsibilities": ["新建数据。"],
                "dependsOnCapabilityIds": [],
                "transactionBoundaries": ["保存数据在一个事务中完成。"],
                "dataBoundaries": ["数据按系统和租户隔离。"],
                "authorizationBoundaries": ["写入前检查新建权限。"],
                "boundaries": ["不负责身份认证。"],
            }],
            "decisions": [],
        }
        design_path.write_text(canonical_json(design), encoding="utf-8")
        return design_path

    def test_requires_exactly_one_capability_owner_per_requirement(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-architecture-") as directory:
            design_path = self._artifacts(Path(directory))
            summary = validate_architecture(design_path)
            self.assertEqual(1, summary["ownedRequirementCount"])

            design = json.loads(design_path.read_text(encoding="utf-8"))
            duplicate = dict(design["capabilities"][0])
            duplicate["id"] = "CAP-DATA-SECOND"
            duplicate["ownerModule"] = "data-second"
            design["capabilities"].append(duplicate)
            design_path.write_text(canonical_json(design), encoding="utf-8")
            with self.assertRaisesRegex(ArchitectureError, "more than one capability"):
                validate_architecture(design_path)

    def test_rejects_stale_requirement_analysis(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-architecture-source-") as directory:
            root = Path(directory)
            design_path = self._artifacts(root)
            (root / "requirement-analysis.json").write_text("{}\n", encoding="utf-8")
            with self.assertRaisesRegex(ArchitectureError, "hash mismatch"):
                validate_architecture(design_path)


if __name__ == "__main__":
    unittest.main()
