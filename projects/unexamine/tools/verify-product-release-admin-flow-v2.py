"""验证页面发布的审批流程 V2 用于新实例，并保留已完成的 V1 实例。"""

import json
import os
import runpy
import sys
from pathlib import Path


ROOT = Path(os.environ.get("S19_WORK_ROOT", str(Path(__file__).resolve().parents[3])))
EVIDENCE = ROOT / "projects/unexamine/ai/evidence/s19-admin-flow-v2-readback.json"
APPROVAL = ROOT / "projects/unexamine/ai/evidence/s19-approval-readback.json"
MEMBER = ROOT / "projects/unexamine/ai/evidence/s19-member-record-readback.json"
api = runpy.run_path(str(Path(__file__).with_name("verify-product-release-member.py")))
success = api["success"]
request = api["request"]
login = api["login"]
enter = api["enter"]


def sessions():
    password = os.environ["S19_USER_PASSWORD"]
    return {
        "admin": enter(1, login("admin", os.environ["S19_ADMIN_PASSWORD"])),
        "operator": enter(1, login("s19_member", password)),
        "approver": enter(1, login("s19_approver", password)),
    }


def start():
    tokens = sessions()
    record = json.loads(MEMBER.read_text(encoding="utf-8"))["records"]["客户记录"]
    flow = success("/api/flows/1", token=tokens["admin"])
    if not any(version["id"] == flow["currentVersionId"] and version["versionNumber"] == 2
               for version in flow["versions"]):
        raise AssertionError("管理员页面发布的流程 V2 尚未成为当前版本")
    old = success("/api/flow-runtime/instances/1", token=tokens["admin"])
    if old["flowVersionId"] == flow["currentVersionId"] or old["status"] != "COMPLETED":
        raise AssertionError("旧审批实例的固定版本或完成状态不符合预期")
    started = success("/api/flow-runtime/module-approvals", "POST", {
        "moduleCode": record["moduleCode"], "recordId": record["recordId"],
        "idempotencyKey": "s19-ui-flow-v2-20261016"}, tokens["operator"])
    instance = started["instance"]
    pending = [task for task in instance["tasks"] if task["status"] == "PENDING"]
    if instance["flowVersionId"] != flow["currentVersionId"] or len(pending) != 1:
        raise AssertionError("新审批实例未使用页面发布的流程 V2 或待办数量错误")
    evidence = {
        "step": "S19.11", "candidate": "0.1.0-s19.11-dev3", "status": "v2_pending",
        "recordId": record["recordId"], "moduleCode": record["moduleCode"],
        "flowId": flow["id"], "oldInstanceId": old["id"],
        "oldVersionId": old["flowVersionId"], "oldStatus": old["status"],
        "newInstanceId": instance["id"], "newVersionId": instance["flowVersionId"],
        "taskId": pending[0]["id"],
    }
    EVIDENCE.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    APPROVAL.write_text(json.dumps({
        "step": "S19.11", "status": "approval_pending", "moduleCode": record["moduleCode"],
        "recordId": record["recordId"], "instanceId": instance["id"],
        "taskId": pending[0]["id"], "flowVersionId": instance["flowVersionId"],
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(evidence, ensure_ascii=False))


def finish():
    evidence = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    tokens = sessions()
    path = f"/api/flow-runtime/tasks/{evidence['taskId']}/actions"
    status, denied = request(path, "POST", {
        "actionCode": "APPROVE", "idempotencyKey": "s19-ui-flow-v2-denied-20261016",
        "variables": {}}, tokens["operator"])
    if status != 403:
        raise AssertionError(f"非审批人处理未被拒绝：{status} {denied.get('code')}")
    new = success(f"/api/flow-runtime/instances/{evidence['newInstanceId']}",
                  token=tokens["approver"])
    old = success(f"/api/flow-runtime/instances/{evidence['oldInstanceId']}",
                  token=tokens["admin"])
    if new["status"] != "COMPLETED" or new["flowVersionId"] != evidence["newVersionId"]:
        raise AssertionError("V2 审批未由页面完成或版本发生变化")
    if old["status"] != evidence["oldStatus"] or old["flowVersionId"] != evidence["oldVersionId"]:
        raise AssertionError("旧审批实例被新版本改变")
    evidence.update({"status": "v2_browser_completed_old_v1_preserved",
                     "nonAssigneeDenied": denied.get("code"), "newStatus": new["status"]})
    EVIDENCE.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(evidence, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("start", "finish"):
        raise SystemExit("usage: verify-product-release-admin-flow-v2.py start|finish")
    {"start": start, "finish": finish}[sys.argv[1]]()
