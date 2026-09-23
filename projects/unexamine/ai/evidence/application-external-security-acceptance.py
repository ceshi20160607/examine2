"""B11.2 应用凭证生命周期、外部签名、安全边界和失败策略真实验收。"""
import ast
import hashlib
import hmac
import json
import re
import secrets
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "application-external-security-facts.json"
SYSTEM_ID = 54
TENANT_ID = 57
GROUP_ID = 47

tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))


def external_call(credential, body, idempotency_key, nonce=None, timestamp=None, signing_body=None):
    """按产品公开协议生成签名；可单独指定签名内容以验收篡改拒绝。"""
    raw = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
    signed_raw = raw if signing_body is None else json.dumps(signing_body, ensure_ascii=False, separators=(",", ":"))
    nonce = nonce or secrets.token_hex(16)
    timestamp = str(timestamp if timestamp is not None else int(time.time() * 1000))
    request_hash = hashlib.sha256(signed_raw.encode()).hexdigest()
    canonical = "\n".join([credential["clientId"], timestamp, nonce, idempotency_key, request_hash])
    signature = hmac.new(credential["clientSecret"].encode(), canonical.encode(), hashlib.sha256).hexdigest()
    request = urllib.request.Request("http://localhost:18080/api/application-access/v1/calls", method="POST",
        headers={"Content-Type": "application/json", "X-App-Client-Id": credential["clientId"],
                 "X-App-Timestamp": timestamp, "X-App-Nonce": nonce,
                 "X-App-Idempotency-Key": idempotency_key, "X-App-Signature": signature},
        data=raw.encode())
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.load(response), nonce
    except urllib.error.HTTPError as error:
        return error.code, json.load(error), nonce


def flow_node(key, kind, name, x):
    return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
            "config": {}, "assigneePolicy": {}, "formPolicy": {}, "timeoutPolicy": {}, "exceptionPolicy": {}}


def publish_flow(flow, token):
    flow = api("PUT", f"/api/flows/{flow['id']}/draft", {"expectedVersion": flow["version"],
        "nodes": [flow_node("start", "START", "开始", 0), flow_node("end", "END", "完成", 260)],
        "edges": [{"edgeKey": "done", "sourceNodeKey": "start", "targetNodeKey": "end",
                   "priorityOrder": 0, "config": {}}]}, token)
    check = api("GET", f"/api/flows/{flow['id']}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/flows/{flow['id']}/publish", {
        "expectedDraftRevision": check["draftRevision"], "changeSummary": "B11.2 外部调用资源",
        "simulationVariables": {}}, token)


def publish_module(module_id, token):
    check = api("GET", f"/api/admin/module-config/modules/{module_id}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/admin/module-config/modules/{module_id}/publish",
               {"expectedDraftRevision": check["draftRevision"]}, token)


def save_application(application, grant, token, icon):
    return api("PUT", f"/api/applications/{application['id']}/draft", {
        "expectedVersion": application["version"], "name": application["name"],
        "description": application.get("description"), "applicationType": application["applicationType"],
        "iconCode": icon, "callbacks": [], "grants": [grant]}, token)


def publish_application(application, token, summary):
    return api("POST", f"/api/applications/{application['id']}/publish", {
        "expectedDraftRevision": application["draftRevision"], "changeSummary": summary}, token)["application"]


def require_error(result, code):
    assert result["code"] == code, result
    assert result.get("requestId"), result
    return {"code": result["code"], "requestId": result["requestId"]}


def save_facts(facts):
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


facts = {"step": "B11.2", "status": "running", "cases": [], "cleanup": {},
         "credential_expiry_test_setup": "仅对本次随机临时凭证通过数据库将 expires_at 调整到过去，用于触发不可伪造的过期边界；产品签发、轮换、停用和重新启用均通过真实接口。"}
platform_flow = platform_application = system_module = system_application = copied_application = None
system_record_ids = []
suffix = secrets.token_hex(5)
flow_code = "external_flow_" + suffix
module_code = "external_module_" + suffix
try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]
    system = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {}, platform)["tokens"]["accessToken"]

    # 平台应用：签名、时间窗、Nonce、幂等、限流与完整凭证生命周期。
    platform_flow = api("POST", "/api/flows", {"code": flow_code, "name": "外部签名验收 Flow",
        "description": "B11.2 临时平台资源"}, platform)
    publish_flow(platform_flow, platform)
    created = api("POST", "/api/applications", {"code": "external_platform_" + suffix,
        "name": "平台外部调用验收", "description": "签名与凭证生命周期", "applicationType": "SERVICE",
        "iconCode": "workflow", "credentialValidityDays": 30}, platform)
    platform_application, first = created["application"], created["issuedCredential"]
    assert platform_application["iconCode"] == "workflow" and first["shownOnce"] is True
    expiry_days = (datetime.fromisoformat(first["expiresAt"]) - datetime.fromisoformat(first["validFrom"])).days
    assert expiry_days == 30
    grant = {"targetType": "PLATFORM", "resourceType": "FLOW", "resourceId": flow_code,
        "actionCode": "START", "dataScope": {"type": "PLATFORM"},
        "rateLimit": {"accessChannels": ["EXTERNAL"], "maxRequests": 100, "windowSeconds": 600,
                      "allowedIps": ["*"], "failureDisableThreshold": 5, "failureWindowSeconds": 300},
        "fields": [{"fieldCode": "title", "readable": True, "writable": True, "maskStrategy": "NONE"}]}
    platform_application = publish_application(save_application(platform_application, grant, platform, "workflow"),
                                               platform, "开放平台外部签名调用")
    body = {"resourceType": "FLOW", "resourceId": flow_code, "actionCode": "START",
            "requestedDataScope": {"type": "PLATFORM"}, "payload": {"title": "平台外部真实调用"}}
    idem = "platform-" + secrets.token_hex(8)
    status, success, used_nonce = external_call(first, body, idem)
    assert status == 200 and success["data"]["replayed"] is False, success
    status, replay_error, _ = external_call(first, body, idem, nonce=used_nonce)
    replay_fact = require_error(replay_error, "APPLICATION_REPLAY_DETECTED")
    status, replayed, _ = external_call(first, body, idem)
    assert status == 200 and replayed["data"]["replayed"] is True, replayed
    changed = dict(body) | {"payload": {"title": "篡改幂等内容"}}
    status, conflict, _ = external_call(first, changed, idem)
    conflict_fact = require_error(conflict, "APPLICATION_IDEMPOTENCY_CONFLICT")
    status, expired_time, _ = external_call(first, body, "old-time-" + secrets.token_hex(5),
                                              timestamp=int(time.time() * 1000) - 301000)
    time_fact = require_error(expired_time, "APPLICATION_TIMESTAMP_EXPIRED")
    status, tampered, _ = external_call(first, changed, "tamper-" + secrets.token_hex(5), signing_body=body)
    signature_fact = require_error(tampered, "APPLICATION_SIGNATURE_INVALID")
    facts["cases"].append({"case": "external-signature-replay-idempotency", "result": "PASS",
        "successRequestId": success["data"]["requestId"], "replay": replay_fact,
        "idempotencyConflict": conflict_fact, "expiredTimestamp": time_fact, "tamperedPayload": signature_fact})

    # 将额度调到 1，历史成功调用立即进入限流；恢复额度后继续生命周期验收。
    platform_application = api("GET", f"/api/applications/{platform_application['id']}", token=platform)
    grant["rateLimit"]["maxRequests"] = 1
    platform_application = publish_application(save_application(platform_application, grant, platform, "workflow"),
                                               platform, "验证外部调用限流")
    status, limited, _ = external_call(first, body, "limited-" + secrets.token_hex(5))
    assert status == 429
    limit_fact = require_error(limited, "APPLICATION_RATE_LIMITED")
    platform_application = api("GET", f"/api/applications/{platform_application['id']}", token=platform)
    grant["rateLimit"]["maxRequests"] = 100
    platform_application = publish_application(save_application(platform_application, grant, platform, "workflow"),
                                               platform, "恢复正常调用额度")

    rotated = api("POST", f"/api/applications/{platform_application['id']}/credentials",
                  {"validityDays": 10}, platform)
    second = rotated["issuedCredential"]
    assert (datetime.fromisoformat(second["expiresAt"]) - datetime.fromisoformat(second["validFrom"])).days == 10
    status, revoked, _ = external_call(first, body, "revoked-" + secrets.token_hex(5))
    revoked_fact = require_error(revoked, "APPLICATION_CREDENTIAL_INACTIVE")
    status, second_success, _ = external_call(second, body, "second-" + secrets.token_hex(5))
    assert status == 200, second_success
    sql(f"UPDATE app_credential SET expires_at=DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE id={int(second['credentialId'])}")
    status, expired_credential, _ = external_call(second, body, "expired-" + secrets.token_hex(5))
    expired_fact = require_error(expired_credential, "APPLICATION_CREDENTIAL_INACTIVE")
    third = api("POST", f"/api/applications/{platform_application['id']}/credentials",
                {"validityDays": 7}, platform)["issuedCredential"]
    api("POST", f"/api/applications/{platform_application['id']}/disable", {"reason": "验证停用立即拒绝"}, platform)
    status, disabled, _ = external_call(third, body, "disabled-" + secrets.token_hex(5))
    disabled_fact = require_error(disabled, "APPLICATION_DISABLED")
    enabled = api("POST", f"/api/applications/{platform_application['id']}/enable",
                  {"validityDays": 5}, platform)
    fourth = enabled["issuedCredential"]
    assert fourth["credentialVersion"] > third["credentialVersion"]
    status, enabled_success, _ = external_call(fourth, body, "enabled-" + secrets.token_hex(5))
    assert status == 200, enabled_success
    platform_application = enabled["application"]
    facts["cases"].append({"case": "limit-rotate-expire-disable-enable", "result": "PASS",
        "rateLimit": limit_fact, "revokedCredential": revoked_fact, "expiredCredential": expired_fact,
        "disabledApplication": disabled_fact, "newCredentialVersions": [second["credentialVersion"],
        third["credentialVersion"], fourth["credentialVersion"]]})

    # 系统应用：来源 IP、数据范围、字段白名单和连续目标失败自动停用。
    system_module = api("POST", "/api/admin/module-config/modules", {
        "groupId": GROUP_ID, "code": module_code, "name": "外部系统写入验收模块"}, system)
    module_id = system_module["module"]["id"]
    api("POST", f"/api/admin/module-config/modules/{module_id}/fields", {
        "code": "subject", "name": "主题", "fieldType": "TEXT", "required": True,
        "sortOrder": 10, "config": {"placeholder": "请输入主题"}}, system)
    publish_module(module_id, system)
    created = api("POST", "/api/applications", {"code": "external_system_" + suffix,
        "name": "系统外部调用验收", "description": "字段、范围与失败策略", "applicationType": "SERVICE",
        "iconCode": "database", "credentialValidityDays": 15}, system)
    system_application, system_credential = created["application"], created["issuedCredential"]
    system_grant = {"targetType": "SYSTEM", "targetSystemId": SYSTEM_ID, "targetTenantId": TENANT_ID,
        "resourceType": "MODULE", "resourceId": module_code, "actionCode": "CREATE",
        "dataScope": {"type": "ALL"}, "rateLimit": {"accessChannels": ["EXTERNAL"],
        "maxRequests": 100, "windowSeconds": 600, "allowedIps": ["203.0.113.10"],
        "failureDisableThreshold": 2, "failureWindowSeconds": 300},
        "fields": [{"fieldCode": "subject", "readable": True, "writable": True, "maskStrategy": "NONE"}]}
    system_application = publish_application(save_application(system_application, system_grant, system, "database"),
                                             system, "验证来源 IP 白名单")
    system_body = {"resourceType": "MODULE", "resourceId": module_code, "actionCode": "CREATE",
        "targetSystemId": SYSTEM_ID, "targetTenantId": TENANT_ID, "requestedDataScope": {"type": "ALL"},
        "payload": {"title": "外部系统新建", "fields": {"subject": "真实外部写入"}}}
    status, ip_denied, _ = external_call(system_credential, system_body, "ip-" + secrets.token_hex(5))
    ip_fact = require_error(ip_denied, "APPLICATION_SOURCE_IP_DENIED")
    system_application = api("GET", f"/api/applications/{system_application['id']}", token=system)
    system_grant["rateLimit"]["allowedIps"] = ["*"]
    system_application = publish_application(save_application(system_application, system_grant, system, "database"),
                                             system, "开放验收来源并固定字段范围")
    status, system_success, _ = external_call(system_credential, system_body, "system-" + secrets.token_hex(5))
    assert status == 200, system_success
    system_record_ids.append(int(system_success["data"]["targetReference"].split(":")[-1]))
    expanded = dict(system_body) | {"requestedDataScope": {"type": "PLATFORM"}}
    status, scope_denied, _ = external_call(system_credential, expanded, "scope-" + secrets.token_hex(5))
    scope_fact = require_error(scope_denied, "APPLICATION_DATA_SCOPE_EXPANSION")
    unauthorized = dict(system_body) | {"payload": {"title": "越权字段", "fields": {"hidden": "禁止"}}}
    field_facts = []
    for index in range(2):
        status, denied, _ = external_call(system_credential, unauthorized, f"field-{index}-" + secrets.token_hex(5))
        field_facts.append(require_error(denied, "APPLICATION_FIELD_WRITE_DENIED"))
    system_application = api("GET", f"/api/applications/{system_application['id']}", token=system)
    assert system_application["status"] == "DISABLED"
    assert all(item["status"] != "ACTIVE" for item in system_application["credentials"])
    reenabled = api("POST", f"/api/applications/{system_application['id']}/enable", {"validityDays": 9}, system)
    system_credential = reenabled["issuedCredential"]
    status, recovered, _ = external_call(system_credential, system_body, "recovered-" + secrets.token_hex(5))
    assert status == 200, recovered
    system_record_ids.append(int(recovered["data"]["targetReference"].split(":")[-1]))
    system_application = reenabled["application"]
    facts["cases"].append({"case": "system-ip-scope-field-failure-policy", "result": "PASS",
        "ipDenied": ip_fact, "scopeDenied": scope_fact, "fieldDenied": field_facts,
        "autoDisabled": True, "reEnabledCredentialVersion": system_credential["credentialVersion"],
        "createdRecordIds": system_record_ids})

    # 复制产生独立草稿和凭证；编辑后停用、删除仍保留授权、凭证和审计历史。
    copied = api("POST", f"/api/applications/{system_application['id']}/copy", {
        "code": "external_copy_" + suffix, "name": "系统应用生命周期副本",
        "credentialValidityDays": 3}, system)
    copied_application, copied_credential = copied["application"], copied["issuedCredential"]
    assert copied_application["status"] == "DRAFT" and copied_application["iconCode"] == "database"
    assert copied_application["versions"] == [] and copied_credential["clientId"] != system_credential["clientId"]
    copied_application = save_application(copied_application, system_grant, system, "message")
    assert copied_application["iconCode"] == "message" and copied_application["name"] == "系统应用生命周期副本"
    copied_application = api("POST", f"/api/applications/{copied_application['id']}/disable",
                             {"reason": "验证停用保留配置"}, system)
    grant_count = int(sql(f"SELECT COUNT(*) FROM app_grant WHERE application_id={int(copied_application['id'])}")[0][0])
    credential_count = int(sql(f"SELECT COUNT(*) FROM app_credential WHERE application_id={int(copied_application['id'])}")[0][0])
    api("DELETE", f"/api/applications/{copied_application['id']}", {"reason": "验证删除保留安全历史"}, system)
    assert all(item["id"] != copied_application["id"] for item in api("GET", "/api/applications", token=system))
    deleted = sql(f"SELECT status,(SELECT COUNT(*) FROM app_grant WHERE application_id={int(copied_application['id'])}),"
                  f"(SELECT COUNT(*) FROM app_credential WHERE application_id={int(copied_application['id'])}),"
                  f"(SELECT COUNT(*) FROM audit_event WHERE object_type='APPLICATION' AND object_id='{int(copied_application['id'])}' AND event_code='APPLICATION_DELETED') "
                  f"FROM app_definition WHERE id={int(copied_application['id'])}")[0]
    assert deleted[0] == "DELETED" and int(deleted[1]) == grant_count > 0
    assert int(deleted[2]) == credential_count > 0 and int(deleted[3]) == 1
    facts["cases"].append({"case": "create-copy-edit-disable-delete-history", "result": "PASS",
        "copiedApplicationId": copied_application["id"], "independentCredential": True,
        "editedPresetIcon": "message", "deletedHiddenFromList": True,
        "preservedGrantCount": grant_count, "preservedCredentialCount": credential_count,
        "deleteAuditCount": int(deleted[3])})

    platform_logs = api("GET", f"/api/applications/{platform_application['id']}/calls", token=platform)
    system_logs = api("GET", f"/api/applications/{system_application['id']}/calls", token=system)
    logs_text = json.dumps([platform_logs, system_logs], ensure_ascii=False)
    for secret_value in [first["clientSecret"], second["clientSecret"], third["clientSecret"],
                         fourth["clientSecret"], system_credential["clientSecret"]]:
        assert secret_value not in logs_text
    assert any(item["status"] == "SUCCESS" for item in platform_logs)
    assert any(item["status"] == "FAILED" for item in system_logs)
    facts["cases"].append({"case": "safe-call-history", "result": "PASS",
        "platformLogCount": len(platform_logs), "systemLogCount": len(system_logs),
        "secretOrRawCredentialExposed": False})
    facts.update({"status": "passed", "platform_application_id": platform_application["id"],
        "platform_flow_id": platform_flow["id"], "system_application_id": system_application["id"],
        "module_id": module_id, "module_code": module_code, "record_ids": system_record_ids})
finally:
    try:
        if system_application is not None:
            try:
                api("POST", f"/api/applications/{system_application['id']}/disable",
                    {"reason": "B11.2 系统外部调用验收完成"}, system)
            except Exception:
                pass
            facts["cleanup"]["system_application_disabled"] = True
        if platform_application is not None:
            try:
                api("POST", f"/api/applications/{platform_application['id']}/disable",
                    {"reason": "B11.2 平台外部调用验收完成"}, platform)
            except Exception:
                pass
            facts["cleanup"]["platform_application_disabled"] = True
        if system_module is not None:
            sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={int(system_module['module']['id'])} AND code='{module_code}'")
            facts["cleanup"]["module_disabled"] = True
        if platform_flow is not None:
            sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(platform_flow['id'])} AND code='{flow_code}'")
            facts["cleanup"]["platform_flow_disabled"] = True
        if system_record_ids:
            sql("UPDATE biz_record SET deleted=1,deleted_at=NOW(),version=version+1 WHERE id IN ("
                + ",".join(str(value) for value in system_record_ids) + ")")
            facts["cleanup"]["records_deleted"] = True
    finally:
        save_facts(facts)

assert facts["status"] == "passed", facts
print(json.dumps({"status": "PASS", "cases": len(facts["cases"]),
                  "platformApplicationId": facts["platform_application_id"],
                  "systemApplicationId": facts["system_application_id"],
                  "moduleId": facts["module_id"]}, ensure_ascii=False))
