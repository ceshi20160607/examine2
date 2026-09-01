from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.common import canonical_json, sha256_bytes
from template_base.requirements import (
    RequirementError,
    build_analysis_plan,
    build_intake,
    extract_markdown_blocks,
    extract_literal_fragments,
    load_analysis,
    collect_analysis_fragments,
    consolidate_analysis_fragments,
    validate_analysis,
)


class RequirementPipelineTest(unittest.TestCase):
    def _workspace(self, root: Path) -> tuple[Path, Path]:
        requirements_path = root / "user_requirement.md"
        intake_path = root / "requirement-intake.json"
        analysis_path = root / "requirement-analysis.json"
        requirements_path.write_text("# 数据管理\n\n- 支持新建数据并校验必填字段。\n", encoding="utf-8")
        build_intake(requirements_path, intake_path)
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
                "statement": "用户可以新建数据，系统校验必填字段。",
                "type": "functional",
                "priority": "must",
                "origin": "explicit",
                "status": "confirmed",
                "sourceBlocks": [block_id],
                "acceptanceCriteria": ["缺少必填字段时拒绝保存。"],
                "decisionIds": [],
            }],
            "decisions": [],
            "coverage": [{
                "sourceBlock": block_id,
                "classification": "requirement",
                "requirementIds": ["REQ-DATA-CREATE"],
                "rationale": "",
            }],
        }
        analysis_path.write_text(canonical_json(analysis), encoding="utf-8")
        return intake_path, analysis_path

    def test_markdown_intake_is_deterministic_and_groups_lists(self) -> None:
        content = "# 范围\n\n- 第一项\n- 第二项\n  - 子项\n\n最后说明。\n"
        first = extract_markdown_blocks(content)
        self.assertEqual(first, extract_markdown_blocks(content))
        self.assertEqual(["heading", "list", "paragraph"], [item["kind"] for item in first])

    def test_analysis_plan_covers_the_full_requirement_source(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-full-analysis-") as directory:
            root = Path(directory)
            requirements_path = root / "user_requirement.md"
            intake_path = root / "requirement-intake.json"
            plan_path = root / "analysis-plan.json"
            requirements_path.write_text("# 产品\n\n## 登录\n\n- 完成真实登录\n\n## 流程\n\n- 完成工作流\n", encoding="utf-8")
            build_intake(requirements_path, intake_path)
            result = build_analysis_plan(intake_path, plan_path, max_blocks=1)
            self.assertEqual(2, result["blockCount"])
            self.assertEqual(0, result["deferredBlockCount"])
            plan = json.loads(plan_path.read_text(encoding="utf-8"))
            self.assertNotIn("scope", plan)
            self.assertEqual(2, sum(len(packet["blockIds"]) for packet in plan["packets"]))

    def test_literal_extraction_is_lossless_but_explicitly_unreviewed(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-literal-candidates-") as directory:
            root = Path(directory)
            requirements_path = root / "user_requirement.md"
            intake_path = root / "requirement-intake.json"
            plan_path = root / "analysis-plan.json"
            fragments = root / "fragments"
            requirements_path.write_text("# 产品\n\n## 登录\n\n- 完成真实登录\n\n## 流程\n\n- 完成工作流\n", encoding="utf-8")
            build_intake(requirements_path, intake_path)
            build_analysis_plan(intake_path, plan_path, max_blocks=1)
            result = extract_literal_fragments(plan_path, fragments)
            self.assertEqual(2, result["packetCount"])
            self.assertEqual("unreviewed", result["semanticStatus"])
            first = json.loads((fragments / "RWP-0001.json").read_text(encoding="utf-8"))
            self.assertEqual("source-literal", first["requirements"][0]["type"])
            self.assertEqual("unreviewed", first["coverage"][0]["classification"])

    def test_source_change_invalidates_existing_analysis(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-source-change-") as directory:
            root = Path(directory)
            _, analysis_path = self._workspace(root)
            (root / "user_requirement.md").write_text("需求已改变\n", encoding="utf-8")
            with self.assertRaisesRegex(RequirementError, "source changed"):
                validate_analysis(load_analysis(analysis_path), analysis_path)

    def test_missing_source_coverage_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-coverage-") as directory:
            root = Path(directory)
            _, analysis_path = self._workspace(root)
            analysis = load_analysis(analysis_path)
            analysis["coverage"] = []
            analysis_path.write_text(canonical_json(analysis), encoding="utf-8")
            with self.assertRaisesRegex(RequirementError, "lack coverage"):
                validate_analysis(load_analysis(analysis_path), analysis_path)

    def test_open_decision_remains_visible(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-open-decision-") as directory:
            root = Path(directory)
            _, analysis_path = self._workspace(root)
            analysis = load_analysis(analysis_path)
            analysis["requirements"][0]["status"] = "pending"
            analysis["requirements"][0]["decisionIds"] = ["DEC-DATA-OWNER"]
            analysis["decisions"] = [{
                "id": "DEC-DATA-OWNER",
                "question": "由谁负责数据？",
                "sourceBlocks": analysis["requirements"][0]["sourceBlocks"],
                "options": ["创建人", "指定负责人"],
                "status": "open",
                "resolution": None,
                "resolvedBy": None,
            }]
            analysis_path.write_text(canonical_json(analysis), encoding="utf-8")
            summary = validate_analysis(load_analysis(analysis_path), analysis_path)
            self.assertEqual(1, summary["openDecisionCount"])
            self.assertEqual(1, summary["pendingRequirementCount"])

    def test_parallel_fragments_are_collected_without_becoming_final_requirements(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-fragments-") as directory:
            root = Path(directory)
            intake_path, analysis_path = self._workspace(root)
            plan_path = root / "requirement-plan.json"
            build_analysis_plan(intake_path, plan_path)
            analysis = load_analysis(analysis_path)
            fragment_path = root / "RWP-0001.json"
            fragment_path.write_text(canonical_json({
                "schemaVersion": 1,
                "source": {"planPath": "requirement-plan.json", "planSha256": hashlib.sha256(plan_path.read_bytes()).hexdigest()},
                "packetId": "RWP-0001",
                "requirements": analysis["requirements"],
                "decisions": analysis["decisions"],
                "coverage": analysis["coverage"],
            }), encoding="utf-8")
            output_path = root / "fragment-index.json"
            result = collect_analysis_fragments(plan_path, [fragment_path], output_path)
            self.assertEqual(1, result["packetCount"])
            self.assertEqual(1, result["candidateRequirementCount"])
            self.assertNotIn("requirements", json.loads(output_path.read_text(encoding="utf-8")))
            with self.assertRaisesRegex(RequirementError, "more than once"):
                collect_analysis_fragments(plan_path, [fragment_path, fragment_path], root / "duplicate.json")

    def test_proposal_requires_user_or_pm_resolution(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-proposal-") as directory:
            root = Path(directory)
            _, analysis_path = self._workspace(root)
            analysis = load_analysis(analysis_path)
            block_id = analysis["requirements"][0]["sourceBlocks"][0]
            analysis["requirements"].append({
                "id": "REQ-PROPOSED-AUTO-ASSIGN",
                "title": "自动分派建议",
                "statement": "系统自动分派数据。",
                "type": "functional",
                "priority": "could",
                "origin": "proposed",
                "status": "confirmed",
                "sourceBlocks": [block_id],
                "acceptanceCriteria": ["数据被自动分派。"],
                "decisionIds": ["DEC-AUTO-ASSIGN"],
            })
            analysis["decisions"].append({
                "id": "DEC-AUTO-ASSIGN",
                "question": "是否增加自动分派？",
                "sourceBlocks": [block_id],
                "options": ["增加", "不增加"],
                "status": "resolved",
                "resolution": "增加",
                "resolvedBy": "explicit-source",
            })
            analysis["coverage"][0]["requirementIds"].append("REQ-PROPOSED-AUTO-ASSIGN")
            analysis_path.write_text(canonical_json(analysis), encoding="utf-8")
            with self.assertRaisesRegex(RequirementError, "requires a user- or PM-resolved decision"):
                validate_analysis(load_analysis(analysis_path), analysis_path)
            analysis["decisions"][0]["resolvedBy"] = "pm"
            analysis_path.write_text(canonical_json(analysis), encoding="utf-8")
            self.assertEqual(2, validate_analysis(load_analysis(analysis_path), analysis_path)["requirementCount"])

    def test_consolidation_merges_alias_sources_and_criteria(self) -> None:
        with tempfile.TemporaryDirectory(prefix="template-base-consolidation-") as directory:
            root = Path(directory)
            intake_path, analysis_path = self._workspace(root)
            plan_path = root / "analysis-plan.json"
            build_analysis_plan(intake_path, plan_path)
            analysis = load_analysis(analysis_path)
            primary = analysis["requirements"][0]
            alias = dict(primary)
            alias["id"] = "REQ-DATA-CREATE-ALIAS"
            alias["title"] = "重复的新建数据"
            alias["acceptanceCriteria"] = ["保存成功后能够读回。"]
            fragment_path = root / "RWP-0001.json"
            fragment_path.write_text(canonical_json({
                "schemaVersion": 1,
                "source": {"planPath": "analysis-plan.json", "planSha256": sha256_bytes(plan_path.read_bytes())},
                "packetId": "RWP-0001",
                "requirements": [primary, alias],
                "decisions": [],
                "coverage": [{
                    "sourceBlock": primary["sourceBlocks"][0],
                    "classification": "requirement",
                    "requirementIds": [primary["id"], alias["id"]],
                    "rationale": "same atomic behavior",
                }],
            }), encoding="utf-8")
            index_path = root / "fragment-index.json"
            collect_analysis_fragments(plan_path, [fragment_path], index_path)
            aliases_path = root / "requirement-consolidation.json"
            aliases_path.write_text(canonical_json({
                "schemaVersion": 1,
                "source": {
                    "fragmentIndexPath": "fragment-index.json",
                    "fragmentIndexSha256": sha256_bytes(index_path.read_bytes()),
                },
                "aliases": [{
                    "candidateId": alias["id"],
                    "finalId": primary["id"],
                    "rationale": "same behavior",
                }],
            }), encoding="utf-8")
            output_path = root / "final-analysis.json"
            result = consolidate_analysis_fragments(index_path, aliases_path, output_path)
            self.assertEqual(2, result["candidateRequirementCount"])
            self.assertEqual(1, result["finalRequirementCount"])
            final = load_analysis(output_path)
            self.assertEqual(["缺少必填字段时拒绝保存。", "保存成功后能够读回。"], final["requirements"][0]["acceptanceCriteria"])


if __name__ == "__main__":
    unittest.main()
