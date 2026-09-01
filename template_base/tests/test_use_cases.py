from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.common import canonical_json, sha256_bytes
from template_base.requirements import build_intake
from template_base.use_cases import UseCaseError, validate_use_case_catalog


class UseCaseValidationTest(unittest.TestCase):
    def _artifacts(self, root: Path) -> Path:
        requirement_path = root / "user_requirement.md"
        intake_path = root / "requirement-intake.json"
        analysis_path = root / "requirement-analysis.json"
        architecture_path = root / "design.json"
        catalog_path = root / "use-cases.json"
        requirement_path.write_text("# 数据\n\n- 用户可以新建数据。\n", encoding="utf-8")
        build_intake(requirement_path, intake_path)
        intake = json.loads(intake_path.read_text(encoding="utf-8"))
        block_id = next(item["id"] for item in intake["blocks"] if item["kind"] != "heading")
        analysis = {
            "schemaVersion": 1,
            "source": {"intakePath": "requirement-intake.json", "intakeSha256": sha256_bytes(intake_path.read_bytes()), "requirementsSha256": intake["source"]["sha256"]},
            "requirements": [{"id": "REQ-DATA-CREATE", "title": "新建数据", "statement": "用户可以新建数据。", "type": "functional", "priority": "must", "origin": "explicit", "status": "confirmed", "sourceBlocks": [block_id], "acceptanceCriteria": ["保存后可以读回。"], "decisionIds": []}],
            "decisions": [],
            "coverage": [{"sourceBlock": block_id, "classification": "requirement", "requirementIds": ["REQ-DATA-CREATE"], "rationale": "直接需求"}],
        }
        analysis_path.write_text(canonical_json(analysis), encoding="utf-8")
        architecture = {
            "schemaVersion": 1,
            "source": {"requirementAnalysisPath": "requirement-analysis.json", "requirementAnalysisSha256": sha256_bytes(analysis_path.read_bytes())},
            "contexts": [{"id": "CTX-SYSTEM", "name": "系统", "isolationKeys": ["system_id", "tenant_id"], "entryRules": ["解析上下文。"]}],
            "capabilities": [{"id": "CAP-DATA", "name": "数据", "ownerModule": "data", "contextIds": ["CTX-SYSTEM"], "storageModel": "physical-tables", "requirementIds": ["REQ-DATA-CREATE"], "responsibilities": ["新建数据。"], "dependsOnCapabilityIds": [], "transactionBoundaries": ["一次提交。"], "dataBoundaries": ["租户隔离。"], "authorizationBoundaries": ["检查新建权限。"], "boundaries": ["不认证账号。"]}],
            "decisions": [],
        }
        architecture_path.write_text(canonical_json(architecture), encoding="utf-8")
        catalog = {
            "schemaVersion": 1,
            "source": {"requirementAnalysisPath": "requirement-analysis.json", "requirementAnalysisSha256": sha256_bytes(analysis_path.read_bytes()), "architecturePath": "design.json", "architectureSha256": sha256_bytes(architecture_path.read_bytes())},
            "useCases": [{
                "id": "UC-DATA-CREATE", "title": "新建数据", "capabilityId": "CAP-DATA", "requirementIds": ["REQ-DATA-CREATE"], "actor": "有新建权限的用户", "entry": "数据列表的新建按钮", "preconditions": ["已进入系统租户。"],
                "steps": [{"order": 1, "action": "填写并保存数据。", "input": "有效字段值", "visibleResult": "列表刷新且新数据出现在第一行。"}],
                "persistentResults": ["保存数据及审计。"], "authorizationResults": ["只允许有新建权限的用户。"], "failureResults": ["必填缺失时停留表单并标出字段。"]
            }],
        }
        catalog_path.write_text(canonical_json(catalog), encoding="utf-8")
        return catalog_path

    def test_validates_full_owned_use_case_coverage(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-use-cases-") as directory:
            catalog_path = self._artifacts(Path(directory))
            summary = validate_use_case_catalog(catalog_path)
            self.assertEqual(1, summary["coveredRequirementCount"])

            catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
            catalog["useCases"][0]["capabilityId"] = "CAP-OTHER"
            catalog_path.write_text(canonical_json(catalog), encoding="utf-8")
            with self.assertRaisesRegex(UseCaseError, "unknown capability"):
                validate_use_case_catalog(catalog_path)

    def test_requires_contiguous_steps(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-use-case-steps-") as directory:
            catalog_path = self._artifacts(Path(directory))
            catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
            catalog["useCases"][0]["steps"][0]["order"] = 2
            catalog_path.write_text(canonical_json(catalog), encoding="utf-8")
            with self.assertRaisesRegex(UseCaseError, "contiguous"):
                validate_use_case_catalog(catalog_path)


if __name__ == "__main__":
    unittest.main()
