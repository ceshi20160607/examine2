"""在 S19 同一候选库中验收客户记录的发布审批和不同成员办理。"""

import json
import os
import runpy
import sys
from pathlib import Path


ROOT = Path(os.environ.get("S19_WORK_ROOT", str(Path(__file__).resolve().parents[3])))
EVIDENCE = ROOT / "projects/unexamine/ai/evidence/s19-approval-readback.json"
MEMBER = ROOT / "projects/unexamine/ai/evidence/s19-member-record-readback.json"
api = runpy.run_path(str(Path(__file__).with_name("verify-product-release-member.py")))
success = api["success"]
request = api["request"]
login = api["login"]
enter = api["enter"]


def sessions():
    password = os.environ["S19_USER_PASSWORD"]
    admin_password = os.environ["S19_ADMIN_PASSWORD"]
    return {
        "admin": enter(1, login("admin", admin_password)),
        "operator": enter(1, login("s19_member", password)),
        "approver": enter(1, login("s19_approver", password)),
    }


def grant_roles(admin, module_code):
    roles = success("/api/admin/system/authorization", token=admin)["roles"]
    results = {}
    for code in ("s19_operator", "s19_approver"):
        role = next(item for item in roles if item["code"] == code)
        permissions = [dict(item) for item in role["permissions"]]
        if code == "s19_operator":
            required = [("MODULE", module_code, "APPROVAL", "SELF"),
                        ("FLOW", "SYSTEM", "VIEW_RUNTIME", "ALL")]
        else:
            # 审批员必须能查看其他成员提交的客户记录，但仍没有修改记录的权限。
            for permission in permissions:
                if permission["resourceType"] == "MODULE" and permission["resourceCode"] == module_code \
                        and permission["actionCode"] in ("LIST", "DETAIL"):
                    permission["dataScopeType"] = "ALL"
            required = [("FLOW", "SYSTEM", "VIEW_RUNTIME", "ALL"),
                        ("FLOW", "SYSTEM", "HANDLE", "ALL"),
                        ("TODO", "SYSTEM", "VIEW", "ALL"),
                        ("TODO", "SYSTEM", "HANDLE", "ALL")]
        for resource_type, resource_code, action, scope in required:
            if not any(item["resourceType"] == resource_type and item["resourceCode"] == resource_code
                       and item["actionCode"] == action for item in permissions):
                permissions.append({"resourceType": resource_type, "resourceCode": resource_code,
                                    "actionCode": action, "dataScopeType": scope, "dataScopeJson": "{}"})
        if permissions != role["permissions"]:
            draft = success("/api/admin/system/authorization/roles", "POST", {
                "id": role["id"], "code": code, "name": role["name"],
                "description": role.get("description"), "permissions": permissions,
                "fieldPolicies": role["fieldPolicies"], "expectedVersion": role["version"]}, admin)
            role = success(f"/api/admin/system/authorization/roles/{role['id']}/publish", "POST", {
                "reason": "S19 同版客户审批角色授权", "expectedVersion": draft["version"]}, admin)
        results[code] = role["id"]
    return results


def setup():
    tokens = sessions()
    admin = tokens["admin"]
    record = json.loads(MEMBER.read_text(encoding="utf-8"))["records"]["客户记录"]
    module_code, record_id = record["moduleCode"], record["recordId"]
    module = next(item for item in success("/api/admin/module-config", token=admin)["modules"]
                  if item["code"] == module_code)
    draft = success(f"/api/admin/module-config/modules/{module['id']}/draft", token=admin)
    action = next((item for item in draft["actions"] if item["code"] == "APPROVAL"), None)
    if action is None:
        action = success(f"/api/admin/module-config/modules/{module['id']}/actions/approval", "POST", {}, admin)
        revision = success(f"/api/admin/module-config/modules/{module['id']}/draft", token=admin)["module"]["draftRevision"]
        success(f"/api/admin/module-config/modules/{module['id']}/publish", "POST",
                {"expectedDraftRevision": revision}, admin)
    roles = grant_roles(admin, module_code)
    flows = success("/api/flows", token=admin)
    flow = next((item for item in flows if item["code"] == "s19_customer_review"), None)
    if flow is None:
        flow = success("/api/flows", "POST", {"code": "s19_customer_review", "name": "客户记录审批"}, admin)
        people = success("/api/flow-runtime/participants", token=admin)["people"]
        approver_id = next(item["tenantMemberId"] for item in people
                           if item["displayName"] == "s19_approver")
        saved = success(f"/api/flows/{flow['id']}/draft", "PUT", {
            "expectedVersion": 0,
            "nodes": [
                {"nodeKey": "start", "nodeType": "START", "name": "开始", "positionX": 0,
                 "positionY": 0, "config": {}},
                {"nodeKey": "review", "nodeType": "APPROVAL", "name": "客户审批", "positionX": 200,
                 "positionY": 0, "assigneePolicy": {"type": "PERSON", "tenantMemberIds": [approver_id]},
                 "config": {"approvalMode": "OR_SIGN", "manualOperationPolicy": {
                     "allowedActions": ["APPROVE", "REJECT"], "opinionRequiredActions": ["REJECT"],
                     "requireClaim": False}}},
                {"nodeKey": "end", "nodeType": "END", "name": "结束", "positionX": 400,
                 "positionY": 0, "config": {}},
            ],
            "edges": [
                {"edgeKey": "to-review", "sourceNodeKey": "start", "targetNodeKey": "review",
                 "priorityOrder": 0, "config": {}},
                {"edgeKey": "to-end", "sourceNodeKey": "review", "targetNodeKey": "end",
                 "priorityOrder": 0, "config": {}},
            ],
        }, admin)
        check = success(f"/api/flows/{flow['id']}/publication-check", token=admin)
        if not check["valid"]:
            raise AssertionError(f"Flow publication check failed: {check}")
        success(f"/api/flows/{flow['id']}/publish", "POST", {
            "expectedDraftRevision": saved["draftRevision"], "changeSummary": "S19 客户审批"}, admin)
    bindings = success(f"/api/flow-bindings?moduleId={module['id']}&triggerEvent=MANUAL", token=admin)
    binding = next((item for item in bindings["currentTenant"] if item["status"] == "ACTIVE"
                    and item["actionId"] == action["id"]), None)
    if binding is None:
        binding = success("/api/flow-bindings/publish", "POST", {
            "moduleId": module["id"], "actionId": action["id"], "flowId": flow["id"],
            "triggerEvent": "MANUAL", "executionMode": "FLOW_ONLY", "priorityOrder": 0,
            "mutuallyExclusive": True, "replaceExisting": False}, admin)
    started = success("/api/flow-runtime/module-approvals", "POST", {
        "moduleCode": module_code, "recordId": record_id,
        "idempotencyKey": os.environ.get("S19_APPROVAL_KEY", "s19-customer-review-1")}, tokens["operator"])
    instance = started["instance"]
    pending = [item for item in instance["tasks"] if item["status"] == "PENDING"]
    if len(pending) != 1:
        raise AssertionError(f"Expected one approver task: {instance}")
    evidence = {"step": "S19.1", "status": "approval_pending", "moduleId": module["id"],
                "moduleCode": module_code, "recordId": record_id, "actionId": action["id"],
                "flowId": flow["id"], "bindingId": binding["id"], "roles": roles,
                "instanceId": instance["id"], "taskId": pending[0]["id"],
                "flowVersionId": instance["flowVersionId"]}
    EVIDENCE.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(evidence, ensure_ascii=False))


def deny():
    evidence = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    tokens = sessions()
    path = f"/api/flow-runtime/tasks/{evidence['taskId']}/actions"
    body = {"actionCode": "APPROVE", "idempotencyKey": "s19-approver-decision-1", "variables": {}}
    status, response = request(path, "POST", body, tokens["operator"])
    if status != 403 or response.get("code") == "OK":
        raise AssertionError(f"Non-assignee handled approval: {status} {response}")
    evidence["nonAssigneeDenied"] = response.get("code")
    EVIDENCE.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"nonAssigneeDenied": response.get("code")}, ensure_ascii=False))


def complete():
    evidence = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    tokens = sessions()
    approved = success(f"/api/flow-runtime/instances/{evidence['instanceId']}",
                       token=tokens["approver"])
    if approved["status"] != "COMPLETED" or "nonAssigneeDenied" not in evidence:
        raise AssertionError(f"Approval or negative path incomplete: {approved['status']}")
    detail = success(f"/api/runtime/modules/{evidence['moduleCode']}/records/{evidence['recordId']}",
                     token=tokens["operator"])
    evidence.update({"status": "approval_browser_api_verified",
                     "instanceStatus": approved["status"], "recordTitle": detail["title"]})
    EVIDENCE.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(evidence, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("setup", "deny", "complete"):
        raise SystemExit("usage: verify-product-release-approval.py setup|deny|complete")
    {"setup": setup, "deny": deny, "complete": complete}[sys.argv[1]]()
