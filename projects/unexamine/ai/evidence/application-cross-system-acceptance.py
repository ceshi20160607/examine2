"""B11.3 系统 A 到系统 B 的发布身份映射、实时权限和拒绝边界验收。"""
import ast
import json
import re
import secrets
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "application-cross-system-facts.json"
TARGET_SYSTEM_ID = 54
TARGET_TENANT_ID = 57
TARGET_GROUP_ID = 47

tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))


def internal_call(token, application_id, body, idempotency_key):
    """使用真实登录令牌和内部幂等头调用现有应用网关。"""
    request = urllib.request.Request(
        f"http://localhost:18080/api/application-internal-access/v1/applications/{application_id}/calls",
        method="POST",
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + token,
                 "X-Idempotency-Key": idempotency_key},
        data=json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode())
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def publish_module(module_id, token):
    check = api("GET", f"/api/admin/module-config/modules/{module_id}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/admin/module-config/modules/{module_id}/publish",
               {"expectedDraftRevision": check["draftRevision"]}, token)


def save_application(application, grant, mappings, token):
    return api("PUT", f"/api/applications/{application['id']}/draft", {
        "expectedVersion": application["version"], "name": application["name"],
        "description": application.get("description"), "applicationType": application["applicationType"],
        "iconCode": application["iconCode"], "callbacks": [], "grants": [grant],
        "identityMappings": mappings}, token)


def publish_application(application, token, summary):
    check = api("GET", f"/api/applications/{application['id']}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/applications/{application['id']}/publish", {
        "expectedDraftRevision": application["draftRevision"], "changeSummary": summary}, token)["application"]


def error_fact(result, expected):
    assert result["code"] == expected, result
    assert result.get("requestId"), result
    return {"code": result["code"], "requestId": result["requestId"]}


def save_facts(facts):
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


facts = {"step": "B11.3", "status": "running", "cases": [], "cleanup": {}}
application = module = None
record_ids = []
disabled_target_member = None
suffix = secrets.token_hex(5)
module_code = "cross_bridge_" + suffix
try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]
    target_token = api("POST", f"/api/systems/{TARGET_SYSTEM_ID}/tenants/{TARGET_TENANT_ID}/enter",
                       {}, platform)["tokens"]["accessToken"]

    # 选择同平台另一套真实系统/租户成员作为来源，并准备同账号下的另一未映射上下文。
    source = sql("SELECT s.id,t.id,tm.id,m.id FROM sys_system s JOIN sys_tenant t ON t.system_id=s.id "
                 "JOIN sys_tenant_member tm ON tm.system_id=s.id AND tm.tenant_id=t.id "
                 "JOIN sys_member m ON m.id=tm.system_member_id AND m.system_id=s.id "
                 "WHERE s.platform_id=1 AND s.id<>54 AND s.status='ACTIVE' AND s.deleted=0 "
                 "AND t.status='ACTIVE' AND tm.status='ACTIVE' AND m.status='ACTIVE' AND m.account_id=1 "
                 "ORDER BY s.id,t.id LIMIT 1")[0]
    source_system_id, source_tenant_id, source_tenant_member_id = map(int, source[:3])
    unmapped_rows = sql(f"SELECT s.id,t.id,tm.id FROM sys_system s JOIN sys_tenant t ON t.system_id=s.id "
                        f"JOIN sys_tenant_member tm ON tm.system_id=s.id AND tm.tenant_id=t.id "
                        f"JOIN sys_member m ON m.id=tm.system_member_id AND m.system_id=s.id "
                        f"WHERE s.platform_id=1 AND s.status='ACTIVE' AND s.deleted=0 AND t.status='ACTIVE' "
                        f"AND tm.status='ACTIVE' AND m.status='ACTIVE' AND m.account_id=1 "
                        f"AND tm.id<>{source_tenant_member_id} ORDER BY (s.id={source_system_id}) DESC,s.id,t.id LIMIT 1")
    assert unmapped_rows
    unmapped_system_id, unmapped_tenant_id, unmapped_tenant_member_id = map(int, unmapped_rows[0])
    source_token = api("POST", f"/api/systems/{source_system_id}/tenants/{source_tenant_id}/enter",
                       {}, platform)["tokens"]["accessToken"]
    unmapped_token = api("POST", f"/api/systems/{unmapped_system_id}/tenants/{unmapped_tenant_id}/enter",
                         {}, platform)["tokens"]["accessToken"]

    target_member = sql("SELECT tm.id,m.id FROM sys_tenant_member tm JOIN sys_member m "
                        "ON m.id=tm.system_member_id AND m.system_id=tm.system_id "
                        "WHERE tm.system_id=54 AND tm.tenant_id=57 AND tm.status='ACTIVE' "
                        "AND m.status='ACTIVE' AND m.account_id=1 LIMIT 1")[0]
    target_tenant_member_id, target_system_member_id = map(int, target_member)

    module = api("POST", "/api/admin/module-config/modules", {
        "groupId": TARGET_GROUP_ID, "code": module_code, "name": "跨系统应用桥验收模块"}, target_token)
    module_id = module["module"]["id"]
    api("POST", f"/api/admin/module-config/modules/{module_id}/fields", {
        "code": "subject", "name": "主题", "fieldType": "TEXT", "required": True,
        "sortOrder": 10, "config": {"placeholder": "请输入主题"}}, target_token)
    publish_module(module_id, target_token)

    created = api("POST", "/api/applications", {
        "code": "cross_bridge_app_" + suffix, "name": "系统 A 到系统 B 应用桥验收",
        "description": "发布身份映射与当前权限交集", "applicationType": "SERVICE",
        "iconCode": "link", "credentialValidityDays": 30}, target_token)
    application = created["application"]
    catalog = api("GET", f"/api/applications/{application['id']}/identity-catalog", token=target_token)
    source_option = next(item for item in catalog["sourceMembers"]
                         if item["tenantMemberId"] == source_tenant_member_id)
    target_option = next(item for item in catalog["targetMembers"]
                         if item["tenantMemberId"] == target_tenant_member_id)
    assert source_option["systemName"] and source_option["tenantName"] and source_option["memberName"]
    assert target_option["systemName"] and target_option["tenantName"] and target_option["memberName"]

    grant = {"targetType": "SYSTEM", "targetSystemId": TARGET_SYSTEM_ID,
             "targetTenantId": TARGET_TENANT_ID, "resourceType": "MODULE",
             "resourceId": module_code, "actionCode": "CREATE", "dataScope": {"type": "ALL"},
             "rateLimit": {"accessChannels": ["INTERNAL"], "maxRequests": 1000,
                           "windowSeconds": 3600, "failureDisableThreshold": 10,
                           "failureWindowSeconds": 300},
             "fields": [{"fieldCode": "subject", "readable": True, "writable": True,
                         "maskStrategy": "NONE"}]}
    mapping = {"sourceTenantMemberId": source_tenant_member_id,
               "targetTenantMemberId": target_tenant_member_id, "enabled": True}
    application = save_application(application, grant, [mapping], target_token)
    mapping = {"id": application["identityMappings"][0]["id"], **mapping}
    application = publish_application(application, target_token, "发布 A 到 B 的成员执行映射")
    published = application["publishedIdentityMappings"][0]
    assert published["sourceMemberName"] == source_option["memberName"]
    assert published["targetMemberName"] == target_option["memberName"] and published["status"] == "ACTIVE"
    facts["cases"].append({"case": "named-catalog-and-published-identity", "result": "PASS",
        "source": f"{source_option['systemName']} / {source_option['tenantName']} / {source_option['memberName']}",
        "target": f"{target_option['systemName']} / {target_option['tenantName']} / {target_option['memberName']}",
        "mappingId": mapping["id"], "applicationVersionId": application["currentVersionId"]})

    body = {"resourceType": "MODULE", "resourceId": module_code, "actionCode": "CREATE",
            "targetSystemId": TARGET_SYSTEM_ID, "targetTenantId": TARGET_TENANT_ID,
            "requestedDataScope": {"type": "ALL"},
            "payload": {"title": "A 到 B 真实新建", "fields": {"subject": "来自系统 A"}}}
    idem = "cross-" + secrets.token_hex(8)
    status, success = internal_call(source_token, application["id"], body, idem)
    assert status == 200, success
    result = success["data"]
    assert result["source"]["type"] == "APPLICATION_CALL" and result["source"]["accessChannel"] == "INTERNAL"
    record_id = int(result["targetReference"].split(":")[-1])
    record_ids.append(record_id)
    record = sql(f"SELECT system_id,tenant_id,created_by_member_id FROM biz_record WHERE id={record_id}")[0]
    assert list(map(int, record)) == [TARGET_SYSTEM_ID, TARGET_TENANT_ID, target_system_member_id]
    status, replay = internal_call(source_token, application["id"], body, idem)
    assert status == 200 and replay["data"]["replayed"] is True
    logs = api("GET", f"/api/applications/{application['id']}/calls", token=target_token)
    success_log = next(item for item in logs if item["requestId"] == result["requestId"])
    assert success_log["permissionSnapshot"]["sourceIdentity"]["tenantMemberId"] == source_tenant_member_id
    assert success_log["permissionSnapshot"]["targetIdentity"]["tenantMemberId"] == target_tenant_member_id
    facts["cases"].append({"case": "a-to-b-create-current-target-permission", "result": "PASS",
        "requestId": result["requestId"], "recordId": record_id, "createdByTargetMemberId": target_system_member_id,
        "idempotentReplay": True, "sourceType": result["source"]["type"]})

    # 未映射身份和请求扩大目标租户均在业务写入前拒绝。
    status, unmapped = internal_call(unmapped_token, application["id"], body, "unmapped-" + secrets.token_hex(6))
    assert status == 403
    unmapped_fact = error_fact(unmapped, "APPLICATION_SOURCE_IDENTITY_UNMAPPED")
    tampered = dict(body) | {"targetTenantId": source_tenant_id}
    status, expanded = internal_call(source_token, application["id"], tampered,
                                     "expanded-" + secrets.token_hex(6))
    assert status == 403
    expanded_fact = error_fact(expanded, "APPLICATION_TARGET_SCOPE_EXPANSION")
    facts["cases"].append({"case": "unmapped-and-target-tamper-rejected", "result": "PASS",
        "unmappedTenantMemberId": unmapped_tenant_member_id, "unmapped": unmapped_fact,
        "targetTamper": expanded_fact})

    # 草稿修改目标成员不能改变当前发布身份；只要未重新发布，仍由旧目标成员执行。
    alternatives = [item for item in catalog["targetMembers"]
                    if item["tenantMemberId"] != target_tenant_member_id]
    if alternatives:
        application = api("GET", f"/api/applications/{application['id']}", token=target_token)
        draft_changed = dict(mapping) | {"targetTenantMemberId": alternatives[0]["tenantMemberId"]}
        application = save_application(application, grant, [draft_changed], target_token)
        status, frozen = internal_call(source_token, application["id"], body,
                                       "frozen-" + secrets.token_hex(6))
        assert status == 200, frozen
        frozen_record_id = int(frozen["data"]["targetReference"].split(":")[-1])
        record_ids.append(frozen_record_id)
        frozen_creator = int(sql(f"SELECT created_by_member_id FROM biz_record WHERE id={frozen_record_id}")[0][0])
        assert frozen_creator == target_system_member_id
        application = save_application(application, grant, [mapping], target_token)
        facts["cases"].append({"case": "draft-change-does-not-drift-published-target", "result": "PASS",
            "draftTargetMember": alternatives[0]["memberName"], "publishedTargetMember": target_option["memberName"],
            "createdByPublishedMemberId": frozen_creator})

    # 停用映射立即撤销已发布调用，重新启用无需篡改发布快照即可恢复。
    application = api("GET", f"/api/applications/{application['id']}", token=target_token)
    disabled_mapping = dict(mapping) | {"enabled": False}
    application = save_application(application, grant, [disabled_mapping], target_token)
    status, disabled = internal_call(source_token, application["id"], body,
                                     "disabled-map-" + secrets.token_hex(6))
    assert status == 403
    disabled_fact = error_fact(disabled, "APPLICATION_IDENTITY_MAPPING_DISABLED")
    application = save_application(application, grant, [mapping], target_token)
    status, restored = internal_call(source_token, application["id"], body,
                                     "restored-map-" + secrets.token_hex(6))
    assert status == 200, restored
    restored_record_id = int(restored["data"]["targetReference"].split(":")[-1])
    record_ids.append(restored_record_id)
    facts["cases"].append({"case": "mapping-revocation-and-reenable", "result": "PASS",
        "disabled": disabled_fact, "restoredRequestId": restored["data"]["requestId"]})

    # 目标成员实时停用后，不依赖重新发布即可拒绝；恢复后同系统内部调用仍按原路径工作。
    disabled_target_member = target_tenant_member_id
    sql(f"UPDATE sys_tenant_member SET status='DISABLED',version=version+1 WHERE id={target_tenant_member_id}")
    status, inactive = internal_call(source_token, application["id"], body,
                                     "inactive-target-" + secrets.token_hex(6))
    assert status == 403
    inactive_fact = error_fact(inactive, "APPLICATION_TARGET_IDENTITY_INACTIVE")
    sql(f"UPDATE sys_tenant_member SET status='ACTIVE',version=version+1 WHERE id={target_tenant_member_id}")
    disabled_target_member = None
    status, same_system = internal_call(target_token, application["id"], body,
                                        "same-system-" + secrets.token_hex(6))
    assert status == 200, same_system
    same_record_id = int(same_system["data"]["targetReference"].split(":")[-1])
    record_ids.append(same_record_id)
    facts["cases"].append({"case": "live-target-disable-and-same-system-regression", "result": "PASS",
        "inactiveTarget": inactive_fact, "sameSystemRequestId": same_system["data"]["requestId"]})

    logs = api("GET", f"/api/applications/{application['id']}/calls", token=target_token)
    pending = int(sql(f"SELECT COUNT(*) FROM app_call WHERE application_id={int(application['id'])} AND status='PENDING'")[0][0])
    assert pending == 0 and any(item["status"] == "SUCCESS" for item in logs)
    assert any(item["status"] == "FAILED" for item in logs)
    facts["cases"].append({"case": "safe-auditable-call-history", "result": "PASS",
        "logCount": len(logs), "pendingCalls": pending,
        "requestIdsPresent": all(item.get("requestId") for item in logs)})
    facts.update({"status": "passed", "application_id": application["id"], "module_id": module_id,
                  "module_code": module_code, "record_ids": record_ids,
                  "source_system_id": source_system_id, "source_tenant_id": source_tenant_id,
                  "source_tenant_member_id": source_tenant_member_id,
                  "target_tenant_member_id": target_tenant_member_id})
finally:
    try:
        if disabled_target_member is not None:
            sql(f"UPDATE sys_tenant_member SET status='ACTIVE',version=version+1 WHERE id={disabled_target_member}")
            facts["cleanup"]["target_member_restored"] = True
        if application is not None:
            try:
                current = api("GET", f"/api/applications/{application['id']}", token=target_token)
                if current["status"] != "DISABLED":
                    api("POST", f"/api/applications/{application['id']}/disable",
                        {"reason": "B11.3 跨系统应用桥验收完成"}, target_token)
            except Exception:
                pass
            facts["cleanup"]["application_disabled"] = True
        if module is not None:
            sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={int(module['module']['id'])} "
                f"AND code='{module_code}'")
            facts["cleanup"]["module_disabled"] = True
        if record_ids:
            sql("UPDATE biz_record SET deleted=1,deleted_at=NOW(),version=version+1 WHERE id IN ("
                + ",".join(str(value) for value in record_ids) + ")")
            facts["cleanup"]["records_deleted"] = True
    finally:
        save_facts(facts)

assert facts["status"] == "passed", facts
print(json.dumps({"status": "PASS", "cases": len(facts["cases"]),
                  "applicationId": facts["application_id"], "moduleId": facts["module_id"],
                  "records": len(facts["record_ids"])}, ensure_ascii=False))
