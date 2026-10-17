"""用隔离测试系统验证应用逐项配置 Flow、归属隔离及发布后外部审批。"""

import importlib.util
import json
import secrets
import subprocess
import sys
import time
import urllib.request
from pathlib import Path


spec = importlib.util.spec_from_file_location("flow_runtime_fixture", Path(__file__).with_name("verify-external-flow.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def main():
    suffix = secrets.token_hex(4)
    account = fixture.expected("/api/auth/register", "POST", {
        "username": "g4_config_" + suffix, "password": "correct-password",
        "displayName": "G4 外部配置验收", "email": "g4_config_" + suffix + "@example.com",
        "systemName": "G4 外部配置验收", "systemCode": "g4_config_" + suffix})
    token = account["tokens"]["accessToken"]
    system_id, tenant_id = account["systemId"], account["tenantId"]
    app = fixture.expected("/api/applications", "POST", {
        "code": "g4_flow_builder", "name": "G4 流程配置应用", "applicationType": "SERVICE"}, token)
    app_id = app["application"]["id"]
    secret = app["issuedCredential"]
    peer = fixture.expected("/api/applications", "POST", {
        "code": "g4_other_builder", "name": "G4 其他配置应用", "applicationType": "SERVICE"}, token)
    peer_id = peer["application"]["id"]
    peer_secret = peer["issuedCredential"]
    catalog = fixture.expected(f"/api/applications/{app_id}/scope-catalog", token=token)
    member_id = catalog["members"][0]["id"]
    data_scope = {"type": "RESPONSIBLE", "memberIds": [member_id], "departmentIds": []}
    scope = {"systemId": system_id, "tenantId": tenant_id, "dataScope": data_scope}
    rate = {"accessChannels": ["EXTERNAL"], "maxRequests": 200, "windowSeconds": 600,
            "allowedIps": ["172.17.0.1", "127.0.0.1"],
            "failureDisableThreshold": 100, "failureWindowSeconds": 3600}

    def grant(resource, action):
        return {"targetType": "SYSTEM", "targetSystemId": system_id, "targetTenantId": tenant_id,
                "resourceType": "FLOW", "resourceId": resource, "actionCode": action,
                "dataScope": data_scope, "rateLimit": rate, "fields": []}

    def publish_app(identifier, name, grants):
        current = fixture.expected(f"/api/applications/{identifier}", token=token)
        saved = fixture.expected(f"/api/applications/{identifier}/draft", "PUT", {
            "expectedVersion": current["version"], "name": name, "applicationType": "SERVICE",
            "callbacks": [], "grants": grants}, token)
        check = fixture.expected(f"/api/applications/{identifier}/publication-check", token=token)
        if not check["valid"]:
            raise AssertionError(f"应用授权检查失败：{check}")
        return fixture.expected(f"/api/applications/{identifier}/publish", "POST", {
            "expectedDraftRevision": saved["draftRevision"], "changeSummary": "外部 Flow 配置逐项授权"}, token)

    def call(action, payload, resource="__flow_design__", credential=secret, target_scope=scope, status=200):
        actual, response = fixture.signed_call(credential["clientId"], credential["clientSecret"],
                                               target_scope, action, payload, resource)
        if actual != status:
            raise AssertionError(f"{action} HTTP {actual}，预期 {status}：{response}")
        return response

    all_design = [grant("__flow_design__", action) for action in
                  ("CREATE_DRAFT", "VIEW_DRAFT", "SAVE_DRAFT", "VALIDATE", "PUBLISH")]
    publish_app(app_id, "G4 流程配置应用", all_design[:1])
    denied = call("VIEW_DRAFT", {"flowId": 1}, status=403)
    if denied["code"] != "APPLICATION_GRANT_DENIED":
        raise AssertionError(f"未授权草稿读取未拒绝：{denied}")
    publish_denied = call("PUBLISH", {"flowId": 1, "expectedDraftRevision": 1,
                                       "changeSummary": "未获权"}, status=403)
    if publish_denied["code"] != "APPLICATION_GRANT_DENIED":
        raise AssertionError(f"未授权发布未拒绝：{publish_denied}")
    created = fixture.business(call("CREATE_DRAFT", {
        "code": "g4_external_config", "name": "G4 应用配置的外部审批"}))
    flow_id = created["id"]
    if created["version"] != 0:
        raise AssertionError(f"草稿初始版本异常：{created}")
    publish_app(peer_id, "G4 其他配置应用", [grant("__flow_design__", "VIEW_DRAFT")])
    peer_denied = call("VIEW_DRAFT", {"flowId": flow_id}, credential=peer_secret, status=404)
    if peer_denied["code"] != "FLOW_NOT_FOUND":
        raise AssertionError(f"其他应用读取了当前应用草稿：{peer_denied}")
    scope_denied = call("CREATE_DRAFT", {"name": "越界流程"},
        target_scope={**scope, "systemId": system_id + 999}, status=403)
    if scope_denied["code"] != "APPLICATION_TARGET_SCOPE_EXPANSION":
        raise AssertionError(f"应用请求扩大系统范围未拒绝：{scope_denied}")
    publish_app(app_id, "G4 流程配置应用", all_design)
    empty_check = fixture.business(call("VALIDATE", {"flowId": flow_id}))
    if empty_check["valid"]:
        raise AssertionError("空草稿错误通过发布检查")
    wrong_version = call("SAVE_DRAFT", {"flowId": flow_id, "expectedVersion": 99,
        "nodes": [fixture.node("start", "START", "开始", 0)], "edges": []}, status=409)
    if wrong_version["code"] != "FLOW_DRAFT_VERSION_CONFLICT":
        raise AssertionError(f"草稿乐观锁拒绝码不符：{wrong_version}")
    principal = "outside-approver-001"
    approval = {"approvalMode": "OR_SIGN", "manualOperationPolicy": {
        "allowedActions": ["APPROVE", "REJECT"], "opinionRequiredActions": ["REJECT"],
        "requireClaim": False}}
    saved = fixture.business(call("SAVE_DRAFT", {"flowId": flow_id, "expectedVersion": 0,
        "nodes": [fixture.node("start", "START", "开始", 0),
                  fixture.node("approval", "APPROVAL", "外部审批", 200,
                               {"type": "EXTERNAL_PERSON", "applicationId": app_id,
                                "externalApproverId": principal}, approval),
                  fixture.node("end", "END", "结束", 400)],
        "edges": [fixture.edge("to-approval", "start", "approval", 10),
                  fixture.edge("to-end", "approval", "end", 20)]}))
    if saved["draftRevision"] != 2:
        raise AssertionError(f"保存后的草稿修订号异常：{saved}")
    own_read = fixture.business(call("VIEW_DRAFT", {"flowId": flow_id}))
    if len(own_read["nodes"]) != 3 or own_read["version"] != saved["version"]:
        raise AssertionError("应用读取草稿与保存结果不一致")
    checked = fixture.business(call("VALIDATE", {"flowId": flow_id}))
    if not checked["valid"]:
        raise AssertionError(f"有效草稿发布检查失败：{checked}")
    stale_publish = call("PUBLISH", {"flowId": flow_id, "expectedDraftRevision": 1,
                                     "changeSummary": "错误修订号"}, status=409)
    if stale_publish["code"] != "FLOW_DRAFT_REVISION_CONFLICT":
        raise AssertionError(f"错误发布修订号未拒绝：{stale_publish}")
    published = fixture.business(call("PUBLISH", {"flowId": flow_id,
        "expectedDraftRevision": 2, "changeSummary": "外部应用发布流程"}))
    flow_version_id = published["versionId"]
    admin_read = fixture.expected(f"/api/flows/{flow_id}", token=token)
    if admin_read["currentVersionId"] != flow_version_id or admin_read["code"] != created["code"]:
        raise AssertionError("后台 Flow 设计器读取的发布版本与应用接口不一致")
    run_grants = [grant("g4_external_config", action) for action in
                  ("START", "VIEW_INSTANCE", "LIST_TASKS", "HANDLE")]
    publish_app(app_id, "G4 流程配置应用", all_design + run_grants)
    invalid_saved = fixture.business(call("SAVE_DRAFT", {"flowId": flow_id,
        "expectedVersion": admin_read["version"],
        "nodes": [fixture.node("start", "START", "开始", 0),
                  fixture.node("unknown", "UNSUPPORTED", "无效节点", 200),
                  fixture.node("end", "END", "结束", 400)],
        "edges": [fixture.edge("to-unknown", "start", "unknown", 10),
                  fixture.edge("to-end", "unknown", "end", 20)]}))
    invalid_check = fixture.business(call("VALIDATE", {"flowId": flow_id}))
    if invalid_check["valid"] or not any(issue["code"] == "NODE_TYPE_INVALID"
                                      for issue in invalid_check["issues"]):
        raise AssertionError(f"无效节点未被发布检查识别：{invalid_check}")
    invalid_publish = call("PUBLISH", {"flowId": flow_id,
        "expectedDraftRevision": invalid_saved["draftRevision"],
        "changeSummary": "不得发布的节点"}, status=422)
    if invalid_publish["code"] != "FLOW_PUBLICATION_INVALID":
        raise AssertionError(f"无效草稿错误发布：{invalid_publish}")
    after_failed_publish = fixture.expected(f"/api/flows/{flow_id}", token=token)
    if after_failed_publish["currentVersionId"] != flow_version_id or after_failed_publish["status"] != "PUBLISHED":
        raise AssertionError("发布检查失败改变了原发布指针或可用状态")
    started = fixture.business(call("START", {"externalPrincipalId": principal,
        "title": "G4 外部配置后发起", "variables": {}}, resource="g4_external_config"))
    instance_id = started["instance"]["id"]
    tasks = fixture.business(call("LIST_TASKS", {"externalPrincipalId": principal},
                                  resource="g4_external_config"))
    if tasks["total"] != 1 or tasks["items"][0]["instanceId"] != instance_id:
        raise AssertionError(f"发布后外部待办不一致：{tasks}")
    task_id = tasks["items"][0]["taskId"]
    wrong_tasks = fixture.business(call("LIST_TASKS", {"externalPrincipalId": "wrong-person"},
                                        resource="g4_external_config"))
    if wrong_tasks["total"] != 0:
        raise AssertionError(f"其他审批人看到了待办：{wrong_tasks}")
    wrong_handle = call("HANDLE", {"externalPrincipalId": "wrong-person",
        "taskId": task_id, "decision": "APPROVE"}, resource="g4_external_config", status=403)
    if wrong_handle["code"] != "FLOW_EXTERNAL_APPROVER_DENIED":
        raise AssertionError(f"其他审批人办理未拒绝：{wrong_handle}")
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
            raise AssertionError("重启后 Flow 服务未恢复")
        tasks = fixture.business(call("LIST_TASKS", {"externalPrincipalId": principal},
                                      resource="g4_external_config"))
        if tasks["total"] != 1 or tasks["items"][0]["taskId"] != task_id:
            raise AssertionError("重启后外部待办未恢复")
    handled = fixture.business(call("HANDLE", {"externalPrincipalId": principal,
        "taskId": task_id, "decision": "APPROVE"}, resource="g4_external_config"))
    if handled["status"] != "COMPLETED":
        raise AssertionError(f"外部审批未完成：{handled}")
    print(json.dumps({"systemId": system_id, "tenantId": tenant_id, "applicationId": app_id,
        "otherApplicationId": peer_id, "flowId": flow_id, "flowVersionId": flow_version_id,
        "instanceId": instance_id, "taskId": task_id, "finalStatus": handled["status"],
        "noGrantDenied": denied["code"], "publishGrantDenied": publish_denied["code"],
        "otherAppDenied": peer_denied["code"], "wrongApproverDenied": wrong_handle["code"],
        "scopeDenied": scope_denied["code"], "invalidNodeDenied": invalid_publish["code"],
        "previousVersionRetained": after_failed_publish["currentVersionId"] == flow_version_id,
        "wrongVersionDenied": wrong_version["code"], "stalePublishDenied": stale_publish["code"],
        "restartVerified": "--restart" in sys.argv}, ensure_ascii=False))


if __name__ == "__main__":
    main()
