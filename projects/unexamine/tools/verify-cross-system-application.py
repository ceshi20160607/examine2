"""在两个隔离系统中验证目标系统应用的模块和 Flow 内部调用。"""

import importlib.util
import json
import secrets
import subprocess
import sys
import time
import urllib.request
from pathlib import Path


spec = importlib.util.spec_from_file_location("flow_fixture", Path(__file__).with_name("verify-external-flow.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def main():
    suffix = secrets.token_hex(4)

    def register(side):
        return fixture.expected("/api/auth/register", "POST", {
            "username": f"g5_{side}_{suffix}", "password": "correct-password",
            "displayName": f"G5 {side} 系统所属人",
            "email": f"g5_{side}_{suffix}@example.com",
            "systemName": f"G5 {side} 系统", "systemCode": f"g5_{side}_{suffix}"})

    target = register("target")
    source = register("source")
    target_token = target["tokens"]["accessToken"]
    source_token = source["tokens"]["accessToken"]
    target_system, target_tenant = target["systemId"], target["tenantId"]
    source_system = source["systemId"]
    group = fixture.expected("/api/admin/module-config/groups", "POST", {
        "code": "g5_service", "name": "对外服务", "sortOrder": 10}, target_token)
    module = fixture.expected("/api/admin/module-config/modules", "POST", {
        "groupId": group["id"], "code": "g5_contract", "name": "服务合同"}, target_token)
    module_id = module["module"]["id"]
    module_code = module["module"]["code"]
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/fields", "POST", {
        "code": "contract_note", "name": "合同说明", "fieldType": "TEXT",
        "required": False, "uniqueValue": False, "searchable": True,
        "sortOrder": 10, "config": {}}, target_token)
    draft = fixture.expected(f"/api/admin/module-config/modules/{module_id}/draft", token=target_token)
    check = fixture.expected(f"/api/admin/module-config/modules/{module_id}/publication-check", token=target_token)
    if not check["valid"]:
        raise AssertionError(f"目标模块发布检查失败：{check}")
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/publish", "POST", {
        "expectedDraftRevision": draft["module"]["draftRevision"]}, target_token)
    record = fixture.expected(f"/api/runtime/modules/{module_code}/records", "POST", {
        "title": "G5 目标系统合同", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"contract_note": "仅目标应用授权字段可见"}}, target_token)
    record_id = record["id"]

    flow = fixture.expected("/api/flows", "POST", {
        "code": "g5_review", "name": "目标系统受控 Flow"}, target_token)
    flow_id = flow["id"]
    fixture.expected(f"/api/flows/{flow_id}/draft", "PUT", {
        "expectedVersion": 0,
        "nodes": [fixture.node("start", "START", "开始", 0),
                  fixture.node("end", "END", "结束", 200)],
        "edges": [fixture.edge("to-end", "start", "end", 10)]}, target_token)
    flow_check = fixture.expected(f"/api/flows/{flow_id}/publication-check", token=target_token)
    if not flow_check["valid"]:
        raise AssertionError(f"目标 Flow 发布检查失败：{flow_check}")
    fixture.expected(f"/api/flows/{flow_id}/publish", "POST", {
        "expectedDraftRevision": 2, "changeSummary": "系统间受控发起"}, target_token)

    app = fixture.expected("/api/applications", "POST", {
        "code": "g5_target_app", "name": "G5 目标系统服务应用",
        "applicationType": "SERVICE"}, target_token)
    app_id = app["application"]["id"]
    catalog = fixture.expected(f"/api/applications/{app_id}/scope-catalog", token=target_token)
    owner_id = catalog["members"][0]["id"]
    scope = {"type": "RESPONSIBLE", "memberIds": [owner_id], "departmentIds": []}
    rate = {"accessChannels": ["INTERNAL"], "maxRequests": 100,
            "windowSeconds": 600, "failureDisableThreshold": 100,
            "failureWindowSeconds": 3600}

    def grant(resource_type, resource_id, action, fields=None):
        return {"targetType": "SYSTEM", "targetSystemId": target_system,
                "targetTenantId": target_tenant, "resourceType": resource_type,
                "resourceId": resource_id, "actionCode": action,
                "dataScope": scope, "rateLimit": rate, "fields": fields or []}

    readable = [{"fieldCode": "contract_note", "readable": True,
                 "writable": False, "maskStrategy": "NONE"}]
    grants = [grant("MODULE", module_code, "LIST", readable),
              grant("MODULE", module_code, "DETAIL", readable),
              grant("FLOW", "g5_review", "START")]
    fixture.expected(f"/api/applications/{app_id}/draft", "PUT", {
        "expectedVersion": 0, "name": "G5 目标系统服务应用",
        "applicationType": "SERVICE", "callbacks": [], "grants": grants}, target_token)
    app_check = fixture.expected(f"/api/applications/{app_id}/publication-check", token=target_token)
    if not app_check["valid"]:
        raise AssertionError(f"目标应用发布检查失败：{app_check}")
    fixture.expected(f"/api/applications/{app_id}/publish", "POST", {
        "expectedDraftRevision": 2, "changeSummary": "两个系统之间的模块和 Flow 能力"}, target_token)

    def call(resource_type, resource_id, action, payload, status=200,
             token=source_token, target_id=target_system, tenant_id=target_tenant,
             requested_scope=scope):
        body = {"resourceType": resource_type, "resourceId": resource_id,
                "actionCode": action, "targetSystemId": target_id,
                "targetTenantId": tenant_id, "requestedDataScope": requested_scope,
                "payload": payload}
        actual, result = fixture.request(
            f"/api/application-internal-access/v1/applications/{app_id}/calls",
            "POST", body, token, {"X-Idempotency-Key": "g5-" + secrets.token_hex(12)})
        if actual != status:
            raise AssertionError(f"跨系统 {resource_type}:{action}: HTTP {actual}，预期 {status}：{result}")
        return result

    listed = fixture.business(call("MODULE", module_code, "LIST", {"page": 1, "pageSize": 20}))
    if not any(item["id"] == record_id for item in listed.get("records", listed.get("items", []))):
        raise AssertionError(f"系统乙未读到目标授权记录：{listed}")
    detail = fixture.business(call("MODULE", module_code, "DETAIL", {"recordId": record_id}))
    if detail["id"] != record_id or detail["fields"].get("contract_note") != "仅目标应用授权字段可见":
        raise AssertionError(f"系统乙目标详情或字段裁剪错误：{detail}")
    denied = call("MODULE", module_code, "UPDATE", {"recordId": record_id}, status=403)
    if denied["code"] != "APPLICATION_GRANT_DENIED":
        raise AssertionError(f"来源系统所属人越过目标应用授权：{denied}")
    scope_denied = call("MODULE", module_code, "LIST", {}, status=403,
                        target_id=target_system + 999)
    if scope_denied["code"] != "APPLICATION_TARGET_SCOPE_EXPANSION":
        raise AssertionError(f"目标系统范围被扩大：{scope_denied}")
    data_denied = call("MODULE", module_code, "LIST", {}, status=403,
                       requested_scope={"type": "RESPONSIBLE", "memberIds": [], "departmentIds": []})
    if data_denied["code"] != "APPLICATION_DATA_SCOPE_EXPANSION":
        raise AssertionError(f"负责人范围被更改：{data_denied}")
    started = fixture.business(call("FLOW", "g5_review", "START", {
        "title": "G5 来源系统发起目标 Flow", "variables": {}}))
    instance_id = started["instance"]["id"]
    if started["instance"]["status"] != "COMPLETED":
        raise AssertionError(f"跨系统 Flow 没有按目标系统发布版运行：{started}")
    if "--restart" in sys.argv:
        subprocess.run(["docker", "restart", "unexamine-g1-preview"], check=True, capture_output=True)
        for _ in range(60):
            try:
                with urllib.request.urlopen(fixture.BASE + "/actuator/health", timeout=2) as health:
                    if json.loads(health.read()).get("status") == "UP":
                        break
            except OSError:
                pass
            time.sleep(1)
        else:
            raise AssertionError("重启后应用服务未恢复")
        listed_after_restart = fixture.business(call("MODULE", module_code, "LIST", {"page": 1}))
        if not any(item["id"] == record_id for item in listed_after_restart.get(
                "records", listed_after_restart.get("items", []))):
            raise AssertionError("重启后来源系统未读到目标授权记录")
        flow_after_restart = fixture.expected(f"/api/flow-runtime/instances/{instance_id}", token=target_token)
        if flow_after_restart["id"] != instance_id or flow_after_restart["status"] != "COMPLETED":
            raise AssertionError(f"重启后目标 Flow 实例不一致：{flow_after_restart}")
    platform_token = fixture.expected("/api/auth/login", "POST", {
        "username": f"g5_source_{suffix}", "password": "correct-password"})["accessToken"]
    wrong_context = call("MODULE", module_code, "LIST", {}, token=platform_token, status=403)
    if wrong_context["code"] != "APPLICATION_INTERNAL_CONTEXT_DENIED":
        raise AssertionError(f"平台上下文冒充系统来源未拒绝：{wrong_context}")
    anonymous = call("MODULE", module_code, "LIST", {}, token=None, status=401)
    if anonymous["code"] != "AUTHENTICATION_REQUIRED":
        raise AssertionError(f"未登录来源仍可调用目标应用：{anonymous}")
    fixture.expected(f"/api/applications/{app_id}/disable", "POST", {
        "reason": "G5 目标应用停用测试"}, target_token)
    disabled = call("MODULE", module_code, "LIST", {}, status=403)
    if disabled["code"] != "APPLICATION_DISABLED":
        raise AssertionError(f"目标应用停用后跨系统仍可调用：{disabled}")
    print(json.dumps({"targetSystemId": target_system, "targetTenantId": target_tenant,
        "sourceSystemId": source_system, "applicationId": app_id, "moduleId": module_id,
        "recordId": record_id, "flowId": flow_id, "instanceId": instance_id,
        "finalFlowStatus": started["instance"]["status"],
        "ungrantedDenied": denied["code"], "scopeDenied": scope_denied["code"],
        "dataScopeDenied": data_denied["code"], "wrongContextDenied": wrong_context["code"],
        "anonymousDenied": anonymous["code"], "disabledDenied": disabled["code"],
        "restartVerified": "--restart" in sys.argv}, ensure_ascii=False))


if __name__ == "__main__":
    main()
