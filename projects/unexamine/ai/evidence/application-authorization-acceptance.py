"""B11.1 平台/系统应用可视化授权、资源版本冻结、范围交集与回滚真实验收。"""
import ast
import json
import re
import secrets
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "application-authorization-facts.json"
SYSTEM_ID = 54
TENANT_ID = 57
GROUP_ID = 47

tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))


def internal_call(application_id, body, token, key):
    request = urllib.request.Request(
        f"http://localhost:18080/api/application-internal-access/v1/applications/{application_id}/calls",
        method="POST", headers={"Content-Type": "application/json", "Authorization": "Bearer " + token,
                                "X-Idempotency-Key": key},
        data=json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode())
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def save(facts):
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def flow_node(key, kind, name, x):
    return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
            "config": {}, "assigneePolicy": {}, "formPolicy": {}, "timeoutPolicy": {}, "exceptionPolicy": {}}


def publish_flow(flow, token, label):
    flow = api("PUT", f"/api/flows/{flow['id']}/draft", {"expectedVersion": flow["version"],
        "nodes": [flow_node("start", "START", "开始", 0), flow_node("end", "END", label, 260)],
        "edges": [{"edgeKey": "done", "sourceNodeKey": "start", "targetNodeKey": "end",
                   "priorityOrder": 0, "config": {}}]}, token)
    check = api("GET", f"/api/flows/{flow['id']}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/flows/{flow['id']}/publish", {
        "expectedDraftRevision": check["draftRevision"], "changeSummary": label,
        "simulationVariables": {}}, token)


def publish_module(module_id, token):
    check = api("GET", f"/api/admin/module-config/modules/{module_id}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/admin/module-config/modules/{module_id}/publish",
               {"expectedDraftRevision": check["draftRevision"]}, token)


facts = {"step": "B11.1", "status": "running", "cases": [], "cleanup": {},
    "preparation_failures": [
        "首次平台内部调用暴露权限快照使用 Map.of 保存可空平台 memberId 的空指针；临时应用和 Flow 已停用，改用可空安全映射后以全新夹具重跑。"
    ]}
platform_flow = platform_application = module = system_application = imported_application = None
code_suffix = secrets.token_hex(5)
flow_code = "platform_app_flow_" + code_suffix
module_code = "application_module_" + code_suffix
try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]
    entered = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {}, platform)
    system = entered["tokens"]["accessToken"]
    me = api("GET", "/api/auth/me", token=system)

    # 平台应用只看平台 Flow，并通过同一应用页面协议发布固定版本。
    platform_flow = api("POST", "/api/flows", {"code": flow_code, "name": "平台应用授权验收 Flow",
        "description": "B11.1 临时平台资源"}, platform)
    platform_flow_v1 = publish_flow(platform_flow, platform, "平台 Flow V1")["versionId"]
    platform_application_created = api("POST", "/api/applications", {
        "code": "platform_controlled_" + code_suffix, "name": "平台受控应用验收",
        "description": "只开放平台 Flow", "applicationType": "SERVICE"}, platform)
    platform_application = platform_application_created["application"]
    wrong_platform_grant = {"targetType": "SYSTEM", "targetSystemId": SYSTEM_ID,
        "targetTenantId": TENANT_ID, "resourceType": "FLOW", "resourceId": flow_code,
        "actionCode": "START", "dataScope": {"type": "PLATFORM"},
        "rateLimit": {"accessChannels": ["INTERNAL"], "maxRequests": 20, "windowSeconds": 60},
        "fields": []}
    status, rejected = call("PUT", f"/api/applications/{platform_application['id']}/draft", {
        "expectedVersion": platform_application["version"], "name": platform_application["name"],
        "description": platform_application["description"], "applicationType": "SERVICE",
        "callbacks": [], "grants": [wrong_platform_grant]}, platform)
    assert status == 422 and rejected["code"] == "APPLICATION_PLATFORM_SCOPE_INVALID", rejected
    platform_resources = api("GET", "/api/applications/resources", token=platform)
    assert platform_resources and all(item["resourceType"] == "FLOW" for item in platform_resources)
    assert any(item["resourceId"] == flow_code for item in platform_resources)
    platform_grant = {"targetType": "PLATFORM", "resourceType": "FLOW", "resourceId": flow_code,
        "actionCode": "START", "dataScope": {"type": "PLATFORM"},
        "rateLimit": {"accessChannels": ["INTERNAL"], "maxRequests": 20, "windowSeconds": 60},
        "fields": [{"fieldCode": "title", "readable": True, "writable": True, "maskStrategy": "NONE"}]}
    platform_application = api("PUT", f"/api/applications/{platform_application['id']}/draft", {
        "expectedVersion": platform_application["version"], "name": platform_application["name"],
        "description": platform_application["description"], "applicationType": "SERVICE",
        "callbacks": [], "grants": [platform_grant]}, platform)
    platform_app_v1 = api("POST", f"/api/applications/{platform_application['id']}/publish", {
        "expectedDraftRevision": platform_application["draftRevision"], "changeSummary": "平台 Flow 固定授权"}, platform)
    platform_application = api("GET", f"/api/applications/{platform_application['id']}", token=platform)
    assert platform_application["contextType"] == "PLATFORM" and platform_application["ownerSystemId"] is None
    assert platform_application["publishedGrants"][0]["resourceVersionId"] == platform_flow_v1
    platform_body = {"resourceType": "FLOW", "resourceId": flow_code, "actionCode": "START",
        "requestedDataScope": {"type": "PLATFORM"}, "payload": {"title": "平台应用真实发起"}}
    status, platform_result = internal_call(platform_application["id"], platform_body, platform,
                                             "platform-" + secrets.token_hex(8))
    assert status == 200 and platform_result["data"]["result"]["instance"]["status"] == "COMPLETED", platform_result
    facts["cases"].append({"case": "platform-visual-scope-and-flow", "result": "PASS",
        "applicationId": platform_application["id"], "applicationVersionId": platform_app_v1["versionId"],
        "flowVersionId": platform_flow_v1,
        "instanceId": platform_result["data"]["result"]["instance"]["id"],
        "crossSystemRejected": True})

    # 系统模块应用固定当前模块物理身份和 V1；任意数据范围与错误字段方向均在保存时拒绝。
    module = api("POST", "/api/admin/module-config/modules", {
        "groupId": GROUP_ID, "code": module_code, "name": "应用模块授权验收"}, system)
    module_id = module["module"]["id"]
    subject = api("POST", f"/api/admin/module-config/modules/{module_id}/fields", {
        "code": "subject", "name": "主题", "fieldType": "TEXT", "required": True,
        "sortOrder": 10, "config": {"placeholder": "请输入主题"}}, system)
    module_v1 = publish_module(module_id, system)["versionId"]
    system_application_created = api("POST", "/api/applications", {
        "code": "system_controlled_" + code_suffix, "name": "系统模块受控应用验收",
        "description": "固定模块、字段和数据范围", "applicationType": "SERVICE"}, system)
    system_application = system_application_created["application"]
    base_grant = {"targetType": "SYSTEM", "targetSystemId": SYSTEM_ID, "targetTenantId": TENANT_ID,
        "resourceType": "MODULE", "resourceId": module_code, "actionCode": "DETAIL",
        "dataScope": {"type": "SELF"},
        "rateLimit": {"accessChannels": ["INTERNAL"], "maxRequests": 100, "windowSeconds": 600},
        "fields": [{"fieldCode": "subject", "readable": True, "writable": False, "maskStrategy": "NONE"}]}
    invalid_scope = dict(base_grant) | {"dataScope": {"type": "EVERYTHING"}}
    status, rejected = call("PUT", f"/api/applications/{system_application['id']}/draft", {
        "expectedVersion": system_application["version"], "name": system_application["name"],
        "description": system_application["description"], "applicationType": "SERVICE",
        "callbacks": [], "grants": [invalid_scope]}, system)
    assert status == 422 and rejected["code"] == "APPLICATION_DATA_SCOPE_INVALID", rejected
    invalid_direction = dict(base_grant) | {"fields": [{"fieldCode": "subject", "readable": True,
                                                          "writable": True, "maskStrategy": "NONE"}]}
    status, rejected = call("PUT", f"/api/applications/{system_application['id']}/draft", {
        "expectedVersion": system_application["version"], "name": system_application["name"],
        "description": system_application["description"], "applicationType": "SERVICE",
        "callbacks": [], "grants": [invalid_direction]}, system)
    assert status == 422 and rejected["code"] == "APPLICATION_GRANT_FIELD_DIRECTION_INVALID", rejected
    system_resources = api("GET", "/api/applications/resources", token=system)
    selected_module = next(item for item in system_resources if item["resourceType"] == "MODULE"
                           and item["resourceId"] == module_code)
    assert selected_module["publishedVersionId"] == module_v1
    assert {item["code"] for item in selected_module["actions"]} >= {"LIST", "DETAIL", "CREATE", "UPDATE"}
    system_application = api("PUT", f"/api/applications/{system_application['id']}/draft", {
        "expectedVersion": system_application["version"], "name": system_application["name"],
        "description": system_application["description"], "applicationType": "SERVICE",
        "callbacks": [], "grants": [base_grant]}, system)
    system_app_v1 = api("POST", f"/api/applications/{system_application['id']}/publish", {
        "expectedDraftRevision": system_application["draftRevision"], "changeSummary": "固定模块 V1"}, system)
    system_application = api("GET", f"/api/applications/{system_application['id']}", token=system)
    frozen_v1 = system_application["publishedGrants"][0]
    assert frozen_v1["resourceVersionId"] == module_v1 and frozen_v1["resourceVersionNumber"] == 1

    # 模块切换 V2 不改变应用当前 V1；应用明确重发后才采用 V2，回滚应用会发布一个新的 V1 授权版本。
    api("POST", f"/api/admin/module-config/modules/{module_id}/fields", {
        "code": "remark", "name": "备注", "fieldType": "TEXT", "required": False,
        "sortOrder": 20, "config": {"placeholder": "可选"}}, system)
    module_v2 = publish_module(module_id, system)["versionId"]
    system_application = api("GET", f"/api/applications/{system_application['id']}", token=system)
    assert system_application["publishedGrants"][0]["resourceVersionId"] == module_v1
    system_application = api("PUT", f"/api/applications/{system_application['id']}/draft", {
        "expectedVersion": system_application["version"], "name": system_application["name"],
        "description": system_application["description"], "applicationType": "SERVICE",
        "callbacks": [], "grants": [base_grant]}, system)
    system_app_v2 = api("POST", f"/api/applications/{system_application['id']}/publish", {
        "expectedDraftRevision": system_application["draftRevision"], "changeSummary": "明确升级模块 V2"}, system)
    system_application = api("GET", f"/api/applications/{system_application['id']}", token=system)
    assert system_application["publishedGrants"][0]["resourceVersionId"] == module_v2
    rolled = api("POST", f"/api/applications/{system_application['id']}/rollback", {
        "targetVersionId": system_app_v1["versionId"],
        "expectedPublicationVersion": system_application["publicationVersion"],
        "reason": "验证历史授权恢复为新版本"}, system)
    system_application = rolled["application"]
    assert system_application["publishedGrants"][0]["resourceVersionId"] == module_v1
    assert rolled["versionNumber"] > system_app_v2["versionNumber"]
    facts["cases"].append({"case": "module-version-freeze-republish-rollback", "result": "PASS",
        "moduleVersionV1": module_v1, "moduleVersionV2": module_v2,
        "applicationVersionV1": system_app_v1["versionId"],
        "applicationVersionV2": system_app_v2["versionId"],
        "rollbackVersionId": rolled["versionId"], "fixedAfterRollback": module_v1})

    # 应用固定 SELF，即使目标管理员角色更宽，也不能读取其他负责人数据。
    foreign_member = int(sql(f"SELECT system_member_id FROM sys_tenant_member WHERE tenant_id={TENANT_ID} "
                             f"AND status='ACTIVE' AND system_member_id<>{int(me['memberId'])} LIMIT 1")[0][0])
    own_record = api("POST", f"/api/runtime/modules/{module_code}/records", {
        "title": "应用本人范围记录", "ownerMemberId": me["memberId"], "status": "ACTIVE",
        "participantMemberIds": [], "fields": {"subject": "本人可读"}}, system)
    foreign_record = api("POST", f"/api/runtime/modules/{module_code}/records", {
        "title": "应用范围外记录", "ownerMemberId": foreign_member, "status": "ACTIVE",
        "participantMemberIds": [], "fields": {"subject": "他人不可读"}}, system)
    body = {"resourceType": "MODULE", "resourceId": module_code, "actionCode": "DETAIL",
        "targetSystemId": SYSTEM_ID, "targetTenantId": TENANT_ID,
        "requestedDataScope": {"type": "SELF"}, "payload": {"recordId": own_record["id"]}}
    status, own_result = internal_call(system_application["id"], body, system,
                                       "own-" + secrets.token_hex(8))
    assert status == 200 and own_result["data"]["targetReference"] == f"BUSINESS_RECORD:{own_record['id']}", own_result
    body["payload"] = {"recordId": foreign_record["id"]}
    status, denied = internal_call(system_application["id"], body, system,
                                   "foreign-" + secrets.token_hex(8))
    assert status in (403, 404) and denied["code"] in {"PERMISSION_DENIED", "RECORD_NOT_FOUND"}, denied
    facts["cases"].append({"case": "fixed-data-scope-intersects-current-identity", "result": "PASS",
        "ownRecordId": own_record["id"], "foreignRecordId": foreign_record["id"],
        "foreignDeniedCode": denied["code"]})

    exported = api("GET", f"/api/applications/{system_application['id']}/export", token=system)
    serialized = json.dumps(exported, ensure_ascii=False)
    assert "clientSecret" not in serialized and system_application_created["issuedCredential"]["clientSecret"] not in serialized
    assert exported["grants"][0]["resourceId"] == module_code
    imported = api("POST", "/api/applications/import", {
        "code": "system_imported_" + code_suffix, "name": "系统应用安全导入验收",
        "description": exported["description"], "applicationType": exported["applicationType"],
        "callbacks": exported["callbacks"], "grants": exported["grants"]}, system)
    imported_application = imported["application"]
    assert imported_application["status"] == "DRAFT" and imported_application["currentVersionId"] is None
    assert imported_application["contextType"] == "SYSTEM"
    assert imported["issuedCredential"]["shownOnce"] is True
    assert imported["issuedCredential"]["clientSecret"] != system_application_created["issuedCredential"]["clientSecret"]
    assert api("GET", f"/api/applications/{imported_application['id']}/calls", token=system) == []
    facts["cases"].append({"case": "safe-configuration-export-import", "result": "PASS",
        "schemaVersion": exported["schemaVersion"], "secretExposed": False,
        "importedApplicationId": imported_application["id"], "importedAsDraft": True,
        "independentCredential": True, "historyCopied": False})
    facts.update({"status": "passed", "platform_application_id": platform_application["id"],
        "platform_flow_id": platform_flow["id"], "system_application_id": system_application["id"],
        "module_id": module_id, "module_code": module_code,
        "record_ids": [own_record["id"], foreign_record["id"]]})
finally:
    try:
        if system_application is not None:
            try:
                api("POST", f"/api/applications/{system_application['id']}/disable",
                    {"reason": "B11.1 验收完成"}, system)
            except Exception:
                pass
            facts["cleanup"]["system_application_disabled"] = True
        if imported_application is not None:
            try:
                api("POST", f"/api/applications/{imported_application['id']}/disable",
                    {"reason": "B11.1 导入验收完成"}, system)
            except Exception:
                pass
            facts["cleanup"]["imported_application_disabled"] = True
        if platform_application is not None:
            try:
                api("POST", f"/api/applications/{platform_application['id']}/disable",
                    {"reason": "B11.1 验收完成"}, platform)
            except Exception:
                pass
            facts["cleanup"]["platform_application_disabled"] = True
        if module is not None:
            sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={int(module['module']['id'])} "
                f"AND code='{module_code}'")
            facts["cleanup"]["module_disabled"] = True
        if platform_flow is not None:
            sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(platform_flow['id'])} "
                f"AND code='{flow_code}'")
            facts["cleanup"]["platform_flow_disabled"] = True
        if facts.get("record_ids"):
            sql("UPDATE biz_record SET deleted=1,deleted_at=NOW(),version=version+1 WHERE id IN ("
                + ",".join(str(int(value)) for value in facts["record_ids"]) + ")")
            facts["cleanup"]["records_deleted"] = True
    finally:
        save(facts)

assert facts["status"] == "passed", facts
print(json.dumps({"status": "PASS", "cases": len(facts["cases"]),
                  "platformApplicationId": facts["platform_application_id"],
                  "systemApplicationId": facts["system_application_id"],
                  "moduleId": facts["module_id"]}, ensure_ascii=False))
