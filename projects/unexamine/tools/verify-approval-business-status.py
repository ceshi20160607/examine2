"""在隔离系统验证已发布审批绑定的业务状态和待审批编辑限制。"""

import json
import runpy
import secrets
import sys
from pathlib import Path


health = runpy.run_path(str(Path(__file__).with_name("verify-operations-health.py")))
fixture = health["fixture"]
enter = health["token"]
SYSTEM_ID = 151
MODULE_ID = 73
MODULE = "g8_contract"
FLOW_CODE = "g12_status_review"
ROLE_CODE = "g8_metric_viewer"


def tokens():
    return enter("g8_owner_75ca9359", SYSTEM_ID), enter("g8_viewer_75ca9359", SYSTEM_ID)


def setup():
    owner, viewer = tokens()
    roles = fixture.expected("/api/admin/system/authorization", token=owner)["roles"]
    role = next(item for item in roles if item["code"] == ROLE_CODE)
    permissions = list(role["permissions"])
    if not any(item["resourceType"] == "MODULE" and item["resourceCode"] == MODULE
               and item["actionCode"] == "UPDATE" for item in permissions):
        permissions.append({"resourceType": "MODULE", "resourceCode": MODULE,
                            "actionCode": "UPDATE", "dataScopeType": "SELF", "dataScopeJson": "{}"})
    for action in ("VIEW_RUNTIME", "HANDLE"):
        if not any(item["resourceType"] == "FLOW" and item["actionCode"] == action
                   for item in permissions):
            permissions.append({"resourceType": "FLOW", "resourceCode": "SYSTEM",
                                "actionCode": action, "dataScopeType": "ALL", "dataScopeJson": "{}"})
    field_policies = [dict(item) for item in role["fieldPolicies"]]
    for item in field_policies:
        if item["resourceCode"] == MODULE and item["fieldCode"] == "amount" and item["channel"] == "PAGE":
            item["writable"] = True
    if permissions != role["permissions"] or field_policies != role["fieldPolicies"]:
        draft = fixture.expected("/api/admin/system/authorization/roles", "POST", {
            "id": role["id"], "code": role["code"], "name": role["name"],
            "description": role.get("description"), "permissions": permissions,
            "fieldPolicies": field_policies, "expectedVersion": role["version"]}, owner)
        fixture.expected(f"/api/admin/system/authorization/roles/{role['id']}/publish", "POST", {
            "reason": "G12 验证本人负责记录的审批后编辑", "expectedVersion": draft["version"]}, owner)

    flows = fixture.expected("/api/flows", token=owner)
    flow = next((item for item in flows if item["code"] == FLOW_CODE), None)
    if flow is None:
        flow = fixture.expected("/api/flows", "POST", {
            "code": FLOW_CODE, "name": "G12 合同状态审批"}, owner)
        flow_id = flow["id"]
        nodes = [fixture.node("start", "START", "开始", 0),
                 fixture.node("approve", "APPROVAL", "负责人审批", 200,
                              {"type": "PERSON", "tenantMemberIds": [226]},
                              {"approvalMode": "OR_SIGN", "manualOperationPolicy": {
                                  "allowedActions": ["APPROVE", "REJECT"],
                                  "opinionRequiredActions": ["REJECT"], "requireClaim": False}}),
                 fixture.node("end", "END", "结束", 400)]
        saved = fixture.expected(f"/api/flows/{flow_id}/draft", "PUT", {
            "expectedVersion": 0, "nodes": nodes,
            "edges": [fixture.edge("to-approve", "start", "approve", 10),
                      fixture.edge("to-end", "approve", "end", 20)]}, owner)
        check = fixture.expected(f"/api/flows/{flow_id}/publication-check", token=owner)
        if not check["valid"]:
            raise AssertionError(f"审批流程发布检查失败：{check}")
        fixture.expected(f"/api/flows/{flow_id}/publish", "POST", {
            "expectedDraftRevision": saved["draftRevision"], "changeSummary": "G12 状态联动流程"}, owner)
    else:
        flow_id = flow["id"]

    bindings = fixture.expected(f"/api/flow-bindings?moduleId={MODULE_ID}&triggerEvent=UPDATE", token=owner)
    active = next((item for item in bindings["currentTenant"] if item["status"] == "ACTIVE"), None)
    mapping = {"COMPLETED": "APPROVED", "REJECTED": "REJECTED", "WITHDRAWN": "DRAFT"}
    if active is None or active["executionMode"] != "AFTER_APPROVAL" or active.get("approvalStatusMapping") != mapping:
        active = fixture.expected("/api/flow-bindings/publish", "POST", {
            "moduleId": MODULE_ID, "triggerEvent": "UPDATE", "flowId": flow_id,
            "executionMode": "AFTER_APPROVAL", "priorityOrder": 0, "mutuallyExclusive": True,
            "replaceExisting": active is not None, "approvalStatusMapping": mapping}, owner)
    records = []
    for name, amount in (("通过", 611), ("拒绝", 622), ("撤回", 633)):
        record = fixture.expected(f"/api/runtime/modules/{MODULE}/records", "POST", {
            "title": f"G12 {name}前合同", "status": "DRAFT", "ownerMemberId": 227,
            "participantMemberIds": [], "fields": {"amount": amount}}, owner)
        records.append({"id": record["id"], "version": record["version"],
                        "title": record["title"], "amount": amount})
    print(json.dumps({"systemId": SYSTEM_ID, "flowId": flow_id,
                      "bindingId": active["id"], "records": records}, ensure_ascii=False))


def readback():
    owner, viewer = tokens()
    outsider = enter("g8_outsider_75ca9359", SYSTEM_ID)
    binding = fixture.expected(f"/api/flow-bindings?moduleId={MODULE_ID}&triggerEvent=UPDATE", token=owner)
    active = next(item for item in binding["currentTenant"] if item["status"] == "ACTIVE")
    expected_mapping = {"COMPLETED": "APPROVED", "REJECTED": "REJECTED", "WITHDRAWN": "DRAFT"}
    if active["executionMode"] != "AFTER_APPROVAL" or active["approvalStatusMapping"] != expected_mapping:
        raise AssertionError("审批状态映射没有按已发布绑定读回")
    expected_records = ((138, "APPROVED", "G12 审批通过合同", 711),
                        (139, "REJECTED", "G12 拒绝前合同", 622),
                        (140, "DRAFT", "G12 撤回前合同", 633))
    records = []
    for record_id, status, title, amount in expected_records:
        record = fixture.expected(f"/api/runtime/modules/{MODULE}/records/{record_id}", token=viewer)
        if record["status"] != status or record["title"] != title or record["fields"]["amount"] != amount:
            raise AssertionError(f"审批结果与业务记录不一致：{record_id}/{record}")
        records.append({"id": record_id, "status": record["status"], "version": record["version"]})
    for action_id, status in ((178, "EXECUTED"), (179, "REJECTED"), (180, "WITHDRAWN")):
        progress = fixture.expected(f"/api/flow-runtime/business-actions/{MODULE}/{action_id}", token=viewer)
        if progress["businessStatus"] != status:
            raise AssertionError(f"审批动作结果与记录不一致：{action_id}/{progress['businessStatus']}")
    denied, _ = fixture.request(f"/api/runtime/modules/{MODULE}/records/138", token=outsider)
    if denied != 403:
        raise AssertionError(f"无权成员读取审批记录未被拒绝：{denied}")
    print(json.dumps({"systemId": SYSTEM_ID, "bindingId": active["id"],
                      "records": records, "actionStatuses": ["EXECUTED", "REJECTED", "WITHDRAWN"],
                      "outsiderDenied": True}, ensure_ascii=False))


def negative_configuration():
    """无效状态编码及非审批绑定须在发布前拒绝，原生效配置保持不变。"""
    owner, _ = tokens()
    path = f"/api/flow-bindings?moduleId={MODULE_ID}&triggerEvent=UPDATE"
    before = next(item for item in fixture.expected(path, token=owner)["currentTenant"]
                  if item["status"] == "ACTIVE")
    cases = (
        ({"executionMode": "DIRECT", "approvalStatusMapping": {"COMPLETED": "APPROVED"}},
         "FLOW_APPROVAL_STATUS_MAPPING_MODE_INVALID"),
        ({"executionMode": "AFTER_APPROVAL", "approvalStatusMapping": {"COMPLETED": "bad status"}},
         "FLOW_APPROVAL_STATUS_MAPPING_INVALID"),
    )
    for invalid, code in cases:
        payload = {"moduleId": MODULE_ID, "triggerEvent": "UPDATE", "flowId": before["flowId"],
                   "priorityOrder": 0, "mutuallyExclusive": True, "replaceExisting": True,
                   **invalid}
        status, result = fixture.request("/api/flow-bindings/publish", "POST", payload, owner)
        if status != 422 or result.get("code") != code:
            raise AssertionError(f"错误绑定未按预期拒绝：{status}/{result}")
    after = next(item for item in fixture.expected(path, token=owner)["currentTenant"]
                 if item["status"] == "ACTIVE")
    if after["id"] != before["id"] or after["approvalStatusMapping"] != before["approvalStatusMapping"]:
        raise AssertionError("非法发布影响了生效绑定")
    print(json.dumps({"invalidModeDenied": True, "invalidStatusDenied": True,
                      "activeBindingUnchanged": before["id"]}, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("setup", "readback", "negative"):
        raise SystemExit("usage: verify-approval-business-status.py setup|readback|negative")
    if sys.argv[1] == "setup":
        setup()
    elif sys.argv[1] == "readback":
        readback()
    else:
        negative_configuration()
