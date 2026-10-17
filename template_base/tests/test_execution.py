from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))

from template_base.execution import ExecutionError, execution_context, verify_execution_receipt
from template_base.workspace import computed_project_status


class ExecutionTest(unittest.TestCase):
    """使用小型隔离工程验证入口失配与陈旧证据，避免复制或重跑整个产品。"""

    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(prefix="tb-execution-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.workspace_path = self.root / "workspace.json"
        self.source = self.root / "docs/requirements.md"
        self.source.parent.mkdir()
        self.source.write_text("审批前不生效\n失败不产生目标记录\n", encoding="utf-8")
        self.code = self.root / "projects/demo/backend/Action.java"
        self.code.parent.mkdir(parents=True)
        self.code.write_text("class Action {}", encoding="utf-8")
        self.workflow = {"executionLoop": {"continuous": True}}
        self.write("template/agents/workflow.json", self.workflow)
        self.workspace = {"activeProject": {"path": "projects/demo", "executionControl": "projects/demo/ai/control.json"},
                          "requirements": {"path": "docs/requirements.md", "sha256": self.hash(self.source)},
                          "template": {"path": "template"}}
        self.control = {"schemaVersion": 1, "mode": "bounded_steps", "state": "projects/demo/ai/state.json",
                        "contextRoots": ["projects/demo/backend"], "maximumContextCharacters": 8000,
                        "policy": {"loop": "立即进入下一步骤"}}
        self.state = {
            "phase": "implementation_in_progress", "requirement_source": "docs/requirements.md",
            "requirement_sha256": self.hash(self.source), "plan": "projects/demo/ai/plan.md", "ledger": "projects/demo/ai/ledger.json",
            "current_step": "B1", "next_step": "B1",
            "packages": [{"steps": {"B0": "verified", "B1": "in_progress", "B2": "unverified"}}],
            "current_scope": {"step": "B1", "prerequisite_steps": ["B0"], "source_rows": ["R1"],
                              "entry_files": ["Action.java"], "acceptance": ["审批前读回正式记录未变"]},
            "pending_substeps": {"B0": {"status": "verified", "evidence": "projects/demo/ai/previous.json"}},
            "step_record_contract": {"required": ["step", "source_rows", "changed_files", "checks_actual_results", "readback",
                                                 "permission_or_failure_result", "tested_file_hashes", "remaining", "next_step"]},
        }
        self.ledger = {"source": "docs/requirements.md", "source_sha256": self.hash(self.source), "rows": [
            {"id": f"R{index}", "line": index, "excerpt": line, "source_sha256": hashlib.sha256(line.encode()).hexdigest(), "status": "unverified"}
            for index, line in enumerate(self.source.read_text("utf-8").splitlines(), 1)]}
        self.write("projects/demo/ai/previous.json", {"step": "B0", "status": "verified_contribution"})
        self.write("projects/demo/ai/plan.md", {})
        self.save()

    def hash(self, path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def write(self, relative: str, value: dict) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")

    def save(self) -> None:
        self.write("workspace.json", self.workspace)
        self.write("projects/demo/ai/control.json", self.control)
        self.write("projects/demo/ai/state.json", self.state)
        self.write("projects/demo/ai/ledger.json", self.ledger)

    def receipt(self) -> Path:
        self.write("projects/demo/ai/readback.json", {"effectiveBeforeApproval": False})
        self.write("projects/demo/ai/receipt.json", {
            "step": "B1", "status": "verified_contribution", "source_rows": ["R1"],
            "changed_files": ["projects/demo/backend/Action.java"], "checks_actual_results": ["真实 API 读回未变"],
            "readback": ["projects/demo/ai/readback.json"], "permission_or_failure_result": "拒绝未授权提交",
            "tested_file_hashes": [{"path": "projects/demo/backend/Action.java", "sha256": self.hash(self.code)}],
            "remaining": ["B2"], "next_step": "B2"})
        return self.root / "projects/demo/ai/receipt.json"

    def test_context_is_bounded_readonly_and_history_is_not_completion(self) -> None:
        before = (self.root / "projects/demo/ai/state.json").read_bytes()
        self.write("projects/demo/ai/status/project-status.json", {"scheduling": {"overallPercent": 100}})
        context = execution_context(self.workspace_path)
        self.assertEqual(["R1"], [row["id"] for row in context["requirements"]])
        self.assertEqual("projects/demo/backend/Action.java", context["entryFiles"][0]["path"])
        self.assertNotIn("sourceCode", context)
        status = computed_project_status(self.workspace, self.workspace_path)
        self.assertIsNone(status["overallPercent"])
        self.assertEqual("not_verified", status["productCompletion"])
        self.assertFalse(status["historicalCyclesUsedForCompletion"])
        self.assertEqual(before, (self.root / "projects/demo/ai/state.json").read_bytes())

    def test_changed_requirement_and_forged_row_are_rejected(self) -> None:
        self.source.write_text("改动后的要求\n", encoding="utf-8")
        with self.assertRaisesRegex(ExecutionError, "requirement binding changed"):
            execution_context(self.workspace_path)
        new_hash = self.hash(self.source)
        self.workspace["requirements"]["sha256"] = new_hash
        self.state["requirement_sha256"] = new_hash
        self.ledger["source_sha256"] = new_hash
        self.save()
        with self.assertRaisesRegex(ExecutionError, "row binding changed"):
            execution_context(self.workspace_path)

    def test_missing_dependency_and_mismatched_evidence_are_rejected(self) -> None:
        self.state["packages"][0]["steps"]["B0"] = "unverified"
        self.save()
        with self.assertRaisesRegex(ExecutionError, "prerequisite is not verified"):
            execution_context(self.workspace_path)
        self.state["packages"][0]["steps"]["B0"] = "verified"
        self.save()
        self.write("projects/demo/ai/previous.json", {"step": "OTHER", "status": "verified_contribution"})
        with self.assertRaisesRegex(ExecutionError, "evidence does not match"):
            execution_context(self.workspace_path)

    def test_scope_mismatch_is_rejected(self) -> None:
        self.state["next_step"] = "B2"
        self.save()
        with self.assertRaisesRegex(ExecutionError, "same declared unfinished step"):
            execution_context(self.workspace_path)

    def test_parallel_packet_needs_explicit_admission_and_keeps_main_checkpoint(self) -> None:
        with self.assertRaisesRegex(ExecutionError, "not an admitted unfinished packet"):
            execution_context(self.workspace_path, "B2")
        self.state["parallel_task_packets"] = {"B2": {
            "step": "B2", "prerequisite_steps": ["B0"], "source_rows": ["R2"],
            "entry_files": ["Action.java"], "acceptance": ["失败不产生目标记录"]}}
        self.save()
        branch = execution_context(self.workspace_path, "B2")
        self.assertEqual("B2", branch["step"])
        self.assertTrue(branch["parallelBranch"])
        self.assertEqual(["R2"], [row["id"] for row in branch["requirements"]])
        self.assertEqual("B1", execution_context(self.workspace_path)["step"])
        self.state["parallel_task_packets"]["B2"]["prerequisite_steps"] = ["B1"]
        self.save()
        with self.assertRaisesRegex(ExecutionError, "prerequisite is not verified"):
            execution_context(self.workspace_path, "B2")

    def test_integration_gate_is_visible_and_cannot_be_an_empty_label(self) -> None:
        self.state["current_scope"]["integration_gate"] = "G1"
        self.save()
        with self.assertRaisesRegex(ExecutionError, "not bound to a business journey"):
            execution_context(self.workspace_path)
        self.state["integration_gates"] = {"G1": {
            "status": "unverified", "business_journey": "同一业务记录配置、执行并读回",
            "required_evidence": ["真实页面", "数据库读回"], "next_exact": "执行连续验收"}}
        self.save()
        context = execution_context(self.workspace_path)
        self.assertEqual("G1", context["integrationGate"]["id"])
        self.assertFalse(context["completionCredit"])

    def test_main_scope_cannot_leave_first_unfinished_business_gate(self) -> None:
        self.control["enforceIntegrationGateOrder"] = True
        self.state["integration_gate_order"] = ["G1", "G2"]
        self.state["integration_gates"] = {
            gate: {"status": status, "business_journey": "同一业务对象的连续旅程",
                   "required_evidence": ["页面和数据库读回"], "next_exact": "继续当前旅程"}
            for gate, status in (("G1", "verified"), ("G2", "unverified"))
        }
        self.state["integration_gates"]["G1"]["evidence"] = "projects/demo/ai/gate-g1.json"
        self.write("projects/demo/ai/gate-g1.json", {
            "integration_gate": "G1", "status": "verified_contribution"})
        self.state["current_scope"]["integration_gate"] = "G1"
        self.save()
        with self.assertRaisesRegex(ExecutionError, "first unfinished integration gate: G2"):
            execution_context(self.workspace_path)
        self.state["current_scope"]["integration_gate"] = "G2"
        self.save()
        self.assertEqual("G2", execution_context(self.workspace_path)["integrationGate"]["id"])
        self.write("projects/demo/ai/gate-g1.json", {
            "integration_gate": "G2", "status": "verified_contribution"})
        with self.assertRaisesRegex(ExecutionError, "verified integration gate evidence does not match"):
            execution_context(self.workspace_path)
        del self.state["integration_gate_order"]
        self.save()
        with self.assertRaisesRegex(ExecutionError, "integration gate order is required"):
            execution_context(self.workspace_path)

    def test_path_escape_ambiguous_files_and_oversized_context_are_rejected(self) -> None:
        self.control["contextRoots"] = ["../outside"]
        self.save()
        with self.assertRaisesRegex(ExecutionError, "leaves its root"):
            execution_context(self.workspace_path)
        self.control["contextRoots"] = ["projects/demo/backend"]
        self.save()
        duplicate = self.root / "projects/demo/Action.java"
        duplicate.write_text("duplicate", encoding="utf-8")
        with self.assertRaisesRegex(ExecutionError, "ambiguous"):
            execution_context(self.workspace_path)
        duplicate.unlink()
        self.state["current_scope"]["acceptance"] = ["详细验收" * 3000]
        self.save()
        with self.assertRaisesRegex(ExecutionError, "exceeds"):
            execution_context(self.workspace_path)

    def test_receipt_is_readonly_and_stale_file_fails(self) -> None:
        receipt = self.receipt()
        before = (self.root / "projects/demo/ai/state.json").read_bytes()
        result = verify_execution_receipt(self.workspace_path, receipt)
        self.assertEqual("passed", result["artifactIntegrity"])
        self.assertFalse(result["completionCredit"])
        self.assertEqual(before, (self.root / "projects/demo/ai/state.json").read_bytes())
        self.code.write_text("changed after verification", encoding="utf-8")
        with self.assertRaisesRegex(ExecutionError, "changed after verification"):
            verify_execution_receipt(self.workspace_path, receipt)

    def test_receipt_wrong_scope_successor_and_missing_readback_fail(self) -> None:
        receipt = self.receipt()
        value = json.loads(receipt.read_text("utf-8"))
        value["source_rows"] = ["R2"]
        self.write(receipt.relative_to(self.root).as_posix(), value)
        with self.assertRaisesRegex(ExecutionError, "source rows must match"):
            verify_execution_receipt(self.workspace_path, receipt)
        value["source_rows"] = ["R1"]
        value["next_step"] = "INVENTED"
        self.write(receipt.relative_to(self.root).as_posix(), value)
        with self.assertRaisesRegex(ExecutionError, "declared unfinished successor"):
            verify_execution_receipt(self.workspace_path, receipt)
        value["next_step"] = "B2"
        self.write(receipt.relative_to(self.root).as_posix(), value)
        (self.root / "projects/demo/ai/readback.json").unlink()
        with self.assertRaisesRegex(ExecutionError, "must exist inside active project"):
            verify_execution_receipt(self.workspace_path, receipt)


if __name__ == "__main__":
    unittest.main()
