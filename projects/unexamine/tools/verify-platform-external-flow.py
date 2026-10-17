"""验证平台应用在平台范围配置、发布并运行外部 Flow。"""

import importlib.util
import json
import os
import secrets
from pathlib import Path


spec = importlib.util.spec_from_file_location("flow_fixture", Path(__file__).with_name("verify-external-flow.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def main():
    suffix = secrets.token_hex(4)
    admin_password = os.getenv("UNEXAMINE_TEST_ADMIN_PASSWORD")
    if not admin_password:
        raise SystemExit("缺少 UNEXAMINE_TEST_ADMIN_PASSWORD 测试环境变量")
    admin = fixture.expected("/api/auth/login", "POST", {
        "username": os.getenv("UNEXAMINE_TEST_ADMIN_USERNAME", "admin"),
        "password": admin_password})["accessToken"]
    application = fixture.expected("/api/applications", "POST", {
        "code": "g4_platform_" + suffix, "name": "G4 平台外部流程应用",
        "applicationType": "SERVICE"}, admin)
    app_id = application["application"]["id"]
    credential = application["issuedCredential"]
    catalog = fixture.expected(f"/api/applications/{app_id}/scope-catalog", token=admin)
    admin_member = next(item["id"] for item in catalog["members"] if item["status"] == "ACTIVE")
    data_scope = {"type": "RESPONSIBLE", "memberIds": [admin_member], "departmentIds": []}
    scope = {"systemId": None, "tenantId": None, "dataScope": data_scope}
    rate = {"accessChannels": ["EXTERNAL"], "maxRequests": 100, "windowSeconds": 600,
            "allowedIps": ["172.17.0.1", "127.0.0.1"],
            "failureDisableThreshold": 100, "failureWindowSeconds": 3600}

    def grant(resource, action):
        return {"targetType": "PLATFORM", "targetSystemId": None, "targetTenantId": None,
                "resourceType": "FLOW", "resourceId": resource, "actionCode": action,
                "dataScope": data_scope, "rateLimit": rate, "fields": []}

    def publish_app(grants):
        current = fixture.expected(f"/api/applications/{app_id}", token=admin)
        saved = fixture.expected(f"/api/applications/{app_id}/draft", "PUT", {
            "expectedVersion": current["version"], "name": "G4 平台外部流程应用",
            "applicationType": "SERVICE", "callbacks": [], "grants": grants}, admin)
        check = fixture.expected(f"/api/applications/{app_id}/publication-check", token=admin)
        if not check["valid"]:
            raise AssertionError(f"平台应用发布检查失败：{check}")
        fixture.expected(f"/api/applications/{app_id}/publish", "POST", {
            "expectedDraftRevision": saved["draftRevision"],
            "changeSummary": "平台 Flow 对外授权"}, admin)

    def call(action, payload, resource="__flow_design__"):
        status, response = fixture.signed_call(credential["clientId"], credential["clientSecret"],
                                                scope, action, payload, resource)
        if status != 200:
            raise AssertionError(f"平台 Flow {action}: HTTP {status} {response}")
        return fixture.business(response)

    design = [grant("__flow_design__", action) for action in
              ("CREATE_DRAFT", "VIEW_DRAFT", "SAVE_DRAFT", "VALIDATE", "PUBLISH")]
    publish_app(design)
    code = "g4_platform_flow_" + suffix
    flow = call("CREATE_DRAFT", {"code": code, "name": "G4 平台外部审批"})
    flow_id = flow["id"]
    if flow["contextType"] != "PLATFORM" or flow["systemId"] is not None:
        raise AssertionError(f"平台流程作用域错误：{flow}")
    person = "platform-external-approver"
    approval = {"approvalMode": "OR_SIGN", "manualOperationPolicy": {
        "allowedActions": ["APPROVE", "REJECT"], "opinionRequiredActions": ["REJECT"],
        "requireClaim": False}}
    call("SAVE_DRAFT", {"flowId": flow_id, "expectedVersion": 0,
        "nodes": [fixture.node("start", "START", "开始", 0),
                  fixture.node("approve", "APPROVAL", "平台外部审批", 200,
                               {"type": "EXTERNAL_PERSON", "applicationId": app_id,
                                "externalApproverId": person}, approval),
                  fixture.node("end", "END", "结束", 400)],
        "edges": [fixture.edge("to-approve", "start", "approve", 10),
                  fixture.edge("to-end", "approve", "end", 20)]})
    if not call("VALIDATE", {"flowId": flow_id})["valid"]:
        raise AssertionError("平台外部审批流程校验失败")
    published = call("PUBLISH", {"flowId": flow_id, "expectedDraftRevision": 2,
                                 "changeSummary": "平台外部审批首版"})
    flow_version_id = published["versionId"]
    run = [grant(code, action) for action in ("START", "VIEW_INSTANCE", "LIST_TASKS", "HANDLE")]
    publish_app(design + run)
    started = call("START", {"externalPrincipalId": person, "title": "平台外部审批调用",
                             "variables": {}}, code)
    instance_id = started["instance"]["id"]
    tasks = call("LIST_TASKS", {"externalPrincipalId": person}, code)
    if tasks["total"] != 1 or tasks["items"][0]["instanceId"] != instance_id:
        raise AssertionError(f"平台应用外部待办不一致：{tasks}")
    task_id = tasks["items"][0]["taskId"]
    handled = call("HANDLE", {"externalPrincipalId": person, "taskId": task_id,
                               "decision": "APPROVE"}, code)
    if handled["status"] != "COMPLETED":
        raise AssertionError(f"平台应用外部审批未完成：{handled}")
    admin_view = fixture.expected(f"/api/flows/{flow_id}", token=admin)
    if admin_view["currentVersionId"] != flow_version_id:
        raise AssertionError("平台 Flow 设计服务发布读回与应用不一致")
    print(json.dumps({"applicationId": app_id, "flowId": flow_id,
                      "flowVersionId": flow_version_id, "instanceId": instance_id,
                      "taskId": task_id, "status": handled["status"],
                      "contextType": flow["contextType"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
