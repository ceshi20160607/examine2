from __future__ import annotations

import hashlib
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.requirements import (
    RequirementError,
    build_analysis_plan,
    build_intake,
    extract_markdown_blocks,
    load_analysis,
    merge_analysis_fragments,
    promote_contract,
    validate_analysis,
)
from template_base.contract import load_contract
from template_base.generator import generate_project


class RequirementPipelineTest(unittest.TestCase):
    example_root = BASE_ROOT / "examples" / "work-order"

    def _workspace(self, root: Path) -> tuple[Path, Path, Path]:
        requirements_path = root / "user_require.md"
        intake_path = root / "requirement-intake.json"
        analysis_path = root / "requirement-analysis.json"
        candidate_path = root / "project.contract.candidate.json"
        shutil.copyfile(self.example_root / "user_require.md", requirements_path)
        build_intake(requirements_path, intake_path)
        shutil.copyfile(self.example_root / "requirement-analysis.json", analysis_path)
        shutil.copyfile(self.example_root / "project.contract.json", candidate_path)
        return intake_path, analysis_path, candidate_path

    def test_markdown_intake_is_deterministic_and_groups_lists(self) -> None:
        content = "# 范围\n\n- 第一项\n- 第二项\n  - 子项\n\n最后说明。\n"
        first = extract_markdown_blocks(content)
        second = extract_markdown_blocks(content)
        self.assertEqual(first, second)
        self.assertEqual(["heading", "list", "paragraph"], [item["kind"] for item in first])

    def test_scoped_plan_keeps_deferred_blocks_visible_and_mergeable(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-scope-") as directory:
            root = Path(directory)
            requirements_path = root / "user_require.md"
            intake_path = root / "requirement-intake.json"
            scope_path = root / "slice.scope.json"
            plan_path = root / "slice.plan.json"
            fragment_path = root / "fragment.json"
            analysis_path = root / "analysis.json"
            requirements_path.write_text(
                "# 产品\n\n## 当前切片\n\n- 完成真实登录\n\n## 后续切片\n\n- 完成复杂流程\n",
                encoding="utf-8",
            )
            build_intake(requirements_path, intake_path)
            intake = json.loads(intake_path.read_text(encoding="utf-8"))
            scope_path.write_text(json.dumps({
                "schemaVersion": 1,
                "id": "slice-01",
                "intake": {
                    "path": "requirement-intake.json",
                    "sha256": hashlib.sha256(intake_path.read_bytes()).hexdigest(),
                    "requirementsSha256": intake["source"]["sha256"],
                },
                "selectors": {
                    "includeHeadingPaths": [["产品", "当前切片"]],
                    "includeBlocks": [],
                    "excludeBlocks": [],
                },
                "deferredRationale": "复杂流程在后续切片分析",
            }, ensure_ascii=False), encoding="utf-8")
            result = build_analysis_plan(intake_path, plan_path, scope_path=scope_path)
            self.assertEqual(1, result["blockCount"])
            self.assertEqual(1, result["deferredBlockCount"])
            plan = json.loads(plan_path.read_text(encoding="utf-8"))
            self.assertEqual(2, plan["schemaVersion"])
            selected_block = plan["packets"][0]["blockIds"][0]
            fragment_path.write_text(json.dumps({
                "schemaVersion": 1,
                "source": {
                    "planPath": "slice.plan.json",
                    "planSha256": hashlib.sha256(plan_path.read_bytes()).hexdigest(),
                },
                "packetId": plan["packets"][0]["id"],
                "requirements": [{
                    "id": "REQ-AUTH-LOGIN",
                    "title": "真实登录",
                    "statement": "用户可以通过真实后端完成登录。",
                    "type": "functional",
                    "priority": "must",
                    "origin": "explicit",
                    "status": "confirmed",
                    "sourceBlocks": [selected_block],
                    "acceptanceCriteria": ["浏览器登录后可从数据库读回会话主体。"],
                    "decisionIds": [],
                }],
                "decisions": [],
                "coverage": [{
                    "sourceBlock": selected_block,
                    "classification": "requirement",
                    "requirementIds": ["REQ-AUTH-LOGIN"],
                    "rationale": "",
                }],
            }, ensure_ascii=False), encoding="utf-8")
            merged = merge_analysis_fragments(plan_path, [fragment_path], analysis_path)
            self.assertEqual(1, merged["coveredBlockCount"])
            self.assertEqual(1, merged["deferredBlockCount"])
            self.assertEqual(2, load_analysis(analysis_path)["schemaVersion"])
    def test_source_change_invalidates_existing_analysis(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-source-change-") as directory:
            root = Path(directory)
            _, analysis_path, _ = self._workspace(root)
            (root / "user_require.md").write_text("需求已改变\n", encoding="utf-8")
            with self.assertRaisesRegex(RequirementError, "source changed"):
                validate_analysis(load_analysis(analysis_path), analysis_path)

    def test_missing_source_coverage_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-coverage-") as directory:
            root = Path(directory)
            _, analysis_path, _ = self._workspace(root)
            analysis = load_analysis(analysis_path)
            analysis["coverage"] = analysis["coverage"][:-1]
            analysis_path.write_text(json.dumps(analysis, ensure_ascii=False), encoding="utf-8")
            with self.assertRaisesRegex(RequirementError, "lack coverage"):
                validate_analysis(load_analysis(analysis_path), analysis_path)

    def test_open_decision_blocks_contract_promotion(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-open-decision-") as directory:
            root = Path(directory)
            _, analysis_path, candidate_path = self._workspace(root)
            analysis = load_analysis(analysis_path)
            analysis["decisions"].append({
                "id": "DEC-DELETE-PERMISSION",
                "question": "谁可以删除工单？",
                "sourceBlocks": ["B0003"],
                "options": ["仅管理员", "工单创建者和管理员"],
                "status": "open",
                "resolution": None,
                "resolvedBy": None,
            })
            analysis_path.write_text(json.dumps(analysis, ensure_ascii=False), encoding="utf-8")
            with self.assertRaisesRegex(RequirementError, "1 open decisions"):
                promote_contract(analysis_path, candidate_path, root / "project.contract.json")

    def test_missing_contract_binding_blocks_promotion(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-binding-") as directory:
            root = Path(directory)
            _, analysis_path, candidate_path = self._workspace(root)
            analysis = load_analysis(analysis_path)
            analysis["contractBindings"] = [
                item for item in analysis["contractBindings"]
                if item["target"] != "action:work-order:delete"
            ]
            analysis_path.write_text(json.dumps(analysis, ensure_ascii=False), encoding="utf-8")
            with self.assertRaisesRegex(RequirementError, "missing contract bindings"):
                promote_contract(analysis_path, candidate_path, root / "project.contract.json")

    def test_fully_traced_contract_is_promoted(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-promote-") as directory:
            root = Path(directory)
            _, analysis_path, candidate_path = self._workspace(root)
            output_path = root / "promoted" / "project.contract.json"
            result = promote_contract(analysis_path, candidate_path, output_path)
            self.assertEqual("work-order-sample", result["projectId"])
            self.assertEqual(14, result["boundTargetCount"])
            self.assertTrue(output_path.is_file())
            self.assertTrue((root / "promoted" / "project.contract.promotion.json").is_file())
            promoted = json.loads(output_path.read_text(encoding="utf-8"))
            self.assertEqual("../user_require.md", promoted["source"]["path"])
            generate_project(load_contract(output_path), output_path, root / "generated")
            analysis_path.write_text(analysis_path.read_text(encoding="utf-8") + "\n", encoding="utf-8")
            with self.assertRaisesRegex(RequirementError, "analysis changed after contract promotion"):
                generate_project(load_contract(output_path), output_path, root / "generated-after-analysis-change")

    def test_parallel_fragments_merge_only_when_every_packet_is_submitted_once(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-fragments-") as directory:
            root = Path(directory)
            intake_path, analysis_path, _ = self._workspace(root)
            plan_path = root / "requirement-plan.json"
            build_analysis_plan(intake_path, plan_path)
            analysis = load_analysis(analysis_path)
            fragment_path = root / "RWP-0001.json"
            fragment_path.write_text(json.dumps({
                "schemaVersion": 1,
                "source": {
                    "planPath": "requirement-plan.json",
                    "planSha256": hashlib.sha256(plan_path.read_bytes()).hexdigest(),
                },
                "packetId": "RWP-0001",
                "requirements": analysis["requirements"],
                "decisions": analysis["decisions"],
                "coverage": analysis["coverage"],
            }, ensure_ascii=False), encoding="utf-8")
            output_path = root / "merged-analysis.json"
            result = merge_analysis_fragments(plan_path, [fragment_path], output_path)
            self.assertEqual(1, result["packetCount"])
            self.assertEqual(7, result["requirementCount"])
            self.assertEqual([], json.loads(output_path.read_text(encoding="utf-8"))["contractBindings"])
            with self.assertRaisesRegex(RequirementError, "more than once"):
                merge_analysis_fragments(plan_path, [fragment_path, fragment_path], root / "duplicate.json")

    def test_agent_proposal_cannot_be_confirmed_without_user_resolution(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-proposal-") as directory:
            root = Path(directory)
            _, analysis_path, _ = self._workspace(root)
            analysis = load_analysis(analysis_path)
            analysis["requirements"].append({
                "id": "REQ-PROPOSED-AUTO-ASSIGN",
                "title": "自动分派建议",
                "statement": "系统自动把工单分派给空闲成员。",
                "type": "functional",
                "priority": "could",
                "origin": "proposed",
                "status": "confirmed",
                "sourceBlocks": ["B0002"],
                "acceptanceCriteria": ["新工单自动分派给符合规则的成员。"],
                "decisionIds": ["DEC-AUTO-ASSIGN"],
            })
            analysis["decisions"].append({
                "id": "DEC-AUTO-ASSIGN",
                "question": "是否增加自动分派？",
                "sourceBlocks": ["B0002"],
                "options": ["增加", "不增加"],
                "status": "resolved",
                "resolution": "增加",
                "resolvedBy": "explicit-source",
            })
            analysis["coverage"][0]["requirementIds"].append("REQ-PROPOSED-AUTO-ASSIGN")
            analysis_path.write_text(json.dumps(analysis, ensure_ascii=False), encoding="utf-8")
            with self.assertRaisesRegex(RequirementError, "requires a user-resolved decision"):
                validate_analysis(load_analysis(analysis_path), analysis_path)


if __name__ == "__main__":
    unittest.main()
