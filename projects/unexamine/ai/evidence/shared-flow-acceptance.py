"""现有环境的共享 Flow 身份验收；只复用 HTTP/SQL 工具，不重跑已通过的权限矩阵。"""
import ast
import base64
import hashlib
import json
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
JAVA = AREA.parents[1] / "backend/src/main/java/com/unique/unexamine"
SYSTEM, OWNER_TENANT, RECIPIENT_TENANT = 54, 56, 57
SOURCE, TARGET = "spark_shared_business", "spark_shared_target"
FACTS = AREA / "shared-flow-facts.json"
# 只加载无副作用的四个工具函数；不执行旧脚本的身份注册/业务写入。
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
helpers = ast.Module(body=[node for node in tree.body if isinstance(node, ast.FunctionDef)
    and node.name in {"call", "api", "sql", "enter"}], type_ignores=[])
exec(compile(helpers, "shared-http-helpers", "exec"))


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def binding_body(view):
    return {**{key: view[key] for key in ["moduleId", "actionId", "triggerEvent", "flowId",
        "executionMode", "priorityOrder", "conditionExpression", "condition", "mutuallyExclusive"]
        if view.get(key) is not None},
        "replaceExisting": True}


def publish_case(case):
    """在既有合成模块上发布验收流程；结束时恢复原来源绑定，不触碰真实业务模块。"""
    flow = api("POST", "/api/flows", {"name": "共享身份验收·" + case}, owner_admin)
    facts["fixture_flow_ids"].append(flow["id"])
    def node(key, kind, **extra):
        return {"nodeKey": key, "nodeType": kind, "name": key, "positionX": 0, "positionY": 0,
                "exceptionPolicy": {"action": "MANUAL"}, **extra}
    nodes = [node("start", "START"), node("approve", "APPROVAL",
        assigneePolicy={"type": "INITIATOR"} if case == "missing_initiator" else
                       {"type": "PERSON", "tenantMemberIds": [owner_entry["tenantMemberId"]]})]
    if case == "original_actor_writeback_denied":
        nodes.append(node("write", "UPDATE_FIELD", config={"businessAction": "UPDATE",
            "fieldUpdates": {"subject": "不应借审批人权限写入"}}))
    nodes.append(node("end", "END"))
    edges = [{"edgeKey": f"edge{i}", "sourceNodeKey": left["nodeKey"], "targetNodeKey": right["nodeKey"]}
             for i, (left, right) in enumerate(zip(nodes, nodes[1:]))]
    draft = api("PUT", f"/api/flows/{flow['id']}/draft", {
        "expectedVersion": flow["version"], "nodes": nodes, "edges": edges}, owner_admin)
    api("POST", f"/api/flows/{flow['id']}/publish", {
        "expectedDraftRevision": draft["draftRevision"], "changeSummary": "共享身份真实验收"}, owner_admin)
    body = binding_body(original_binding)
    body.update(flowId=flow["id"], executionMode="AFTER_EXECUTION")
    bound = api("POST", "/api/flow-bindings/publish", body, owner_admin)
    facts["binding_fixture"] = {"flow_id": flow["id"], "flow_version_id": bound["flowVersionId"]}
    save()


def read_flow(record_id):
    rows = sql("SELECT id,tenant_id,started_by_account_id,started_by_tenant_member_id,status,error_code "
        f"FROM flow_instance WHERE system_id={SYSTEM} AND business_type='MODULE:{SOURCE}' "
        f"AND business_id='{int(record_id)}' ORDER BY id")
    return rows


def convert(case):
    existing = next((record for record in facts["records"] if record["case"] == case), None)
    if existing:
        assert existing.get("flow_id"), "接手未完成的动作投递，不再新建记录"
        return existing
    record = api("POST", f"/api/runtime/modules/{SOURCE}/records", {
        "title": "共享身份验收·" + case, "status": "ACTIVE", "ownerMemberId": owner_entry["systemMemberId"],
        "participantMemberIds": [], "fields": {"subject": "共享身份验收·" + case}}, owner_admin)
    fixture = {"source_id": record["id"], "case": case}
    facts["records"].append(fixture)
    save()
    share = api("POST", f"/api/runtime/modules/{SOURCE}/records/{record['id']}/shares", {
        "targetTenantId": RECIPIENT_TENANT, "allowedActions": ["LIST", "DETAIL", "CONVERT"]}, owner_admin)
    fixture["share_id"] = share["id"]
    path = f"/api/runtime/modules/{SOURCE}/records/{record['id']}/convert"
    body = {"targetModuleCode": TARGET, "version": record["version"]}
    key = secrets.token_hex(16)
    result = api("POST", path, body, actor_token, key)
    fixture.update(action_id=result["idempotencyRecordId"], target_id=result["value"]["targetRecord"]["id"])
    save()
    for attempt in range(30):
        delivery = api("GET", f"/api/runtime/modules/{SOURCE}/records/action-results/{fixture['action_id']}", token=actor_token)
        rows = read_flow(record["id"])
        if delivery.get("flowInstanceId") and rows:
            break
        time.sleep(.5)
    assert len(rows) == 1, (case, "flow instance count", len(rows), delivery.get("status"))
    assert int(rows[0][1]) == OWNER_TENANT and int(rows[0][2]) == registration["accountId"]
    assert rows[0][3] == "NULL", "不能凭空制造来源租户成员"
    replay = api("POST", path, body, actor_token, key)
    assert replay["replayed"] and replay["value"]["targetRecord"]["id"] == fixture["target_id"]
    assert len(read_flow(record["id"])) == 1
    fixture.update(flow_id=int(rows[0][0]), flow_readback=rows[0], replay_same_flow=True,
                   delivery_status=delivery["status"])
    save()
    return fixture


def read_messages(fixture, expect_status):
    row = read_flow(fixture["source_id"])[0]
    assert row[4] == expect_status, (fixture["case"], row[4], row[5])
    fixture["flow_readback"] = row
    messages = sql("SELECT m.id,m.tenant_id,r.account_id,HEX(m.subject) FROM msg_message m "
        "JOIN msg_recipient r ON r.message_id=m.id "
        f"WHERE m.source_type='FLOW' AND m.target_id='{fixture['flow_id']}' "
        f"AND r.account_id={registration['accountId']}")
    assert len(messages) == 1 and int(messages[0][1]) == RECIPIENT_TENANT
    fixture["starter_message"] = messages[0]
    save()


def approve(fixture):
    tasks = sql(f"SELECT id,assignee_account_id,assignee_tenant_member_id FROM flow_task "
        f"WHERE instance_id={fixture['flow_id']} AND status='PENDING'")
    assert len(tasks) == 1 and int(tasks[0][2]) == owner_entry["tenantMemberId"]
    fixture["source_approval_task"] = tasks[0]
    result = api("POST", f"/api/flow-runtime/tasks/{tasks[0][0]}/actions", {
        "actionCode": "APPROVE", "comment": "来源租户正常审批验收", "variables": {},
        "idempotencyKey": secrets.token_hex(16)}, owner_admin)
    return result


def finish_fixtures():
    """先验收普通手动流程，再收尾开放异常及测试数据；永久转化和审计历史保留。"""
    if not facts.get("manual_flow_regression"):
        owner_account = sql(f"SELECT account_id FROM sys_member WHERE id={owner_entry['systemMemberId']}")
        existing = sql("SELECT id,tenant_id,started_by_account_id,started_by_tenant_member_id,status FROM flow_instance "
            f"WHERE system_id={SYSTEM} AND flow_id={facts['fixture_flow_ids'][1]} AND title='普通来源成员流程回归'")
        if existing:
            assert len(existing) == 1
            instance_id, rows = int(existing[0][0]), [existing[0][1:]]
        else:
            result = api("POST", "/api/flow-runtime/instances", {
                "flowId": facts["fixture_flow_ids"][1], "title": "普通来源成员流程回归",
                "businessType": "MODULE:" + SOURCE, "businessSnapshot": {
                    "actionId": facts["records"][0]["action_id"], "actorTenantId": RECIPIENT_TENANT},
                "variables": {}, "idempotencyKey": "manual:" + secrets.token_hex(16)}, owner_admin)
            instance_id = result["instance"]["id"]
            tasks = sql(f"SELECT id FROM flow_task WHERE instance_id={instance_id} AND status='PENDING'")
            assert len(tasks) == 1
            api("POST", f"/api/flow-runtime/tasks/{tasks[0][0]}/actions", {
                "actionCode": "APPROVE", "comment": "普通手动流程不信任伪造快照身份",
                "variables": {}, "idempotencyKey": secrets.token_hex(16)}, owner_admin)
            rows = sql(f"SELECT tenant_id,started_by_account_id,started_by_tenant_member_id,status "
                f"FROM flow_instance WHERE id={instance_id}")
        assert rows == [[str(OWNER_TENANT), owner_account[0][0], str(owner_entry["tenantMemberId"]), "COMPLETED"]]
        facts["manual_flow_regression"] = {"id": instance_id, "readback": rows[0],
            "client_actor_snapshot_ignored": True}
        save()
    for fixture in facts["records"]:
        status = read_flow(fixture["source_id"])[0][4]
        if status == "EXCEPTION":
            api("POST", f"/api/flow-runtime/instances/{fixture['flow_id']}/actions", {
                "actionCode": "TERMINATE", "comment": "合成夹具验收完成，保留异常和审计历史",
                "idempotencyKey": secrets.token_hex(16)}, owner_admin)
        fixture["cleanup_flow_status"] = read_flow(fixture["source_id"])[0][4]
    actor_facts = json.loads((AREA / "shared-actor-facts.json").read_text(encoding="utf-8"))
    records = facts["records"] + [{"source_id": record["id"], "share_id": record["share_id"],
        "target_id": actor_facts["target_record_id"]} for record in actor_facts["records"]]
    for fixture in records:
        if not fixture.get("share_revoked"):
            shares = api("GET", f"/api/runtime/modules/{SOURCE}/records/{fixture['source_id']}/shares", token=owner_admin)
            share = next(value for value in shares if value["id"] == fixture["share_id"])
            if share["status"] == "ACTIVE":
                api("POST", f"/api/runtime/modules/{SOURCE}/records/{fixture['source_id']}/shares/{fixture['share_id']}/revoke",
                    {"version": share["version"], "reason": "合成验收结束，撤销测试共享"}, owner_admin)
            fixture["share_revoked"] = True
        for module, identifier, token in [(SOURCE, fixture["source_id"], owner_admin),
                                          (TARGET, fixture["target_id"], recipient_admin)]:
            record = api("GET", f"/api/runtime/modules/{module}/records/{identifier}", token=token)
            if record["status"] != "ARCHIVED":
                api("POST", f"/api/runtime/modules/{module}/records/{identifier}/archive", {
                    "version": record["version"], "reason": "合成验收结束，归档保留永久审计"}, token)
        fixture["records_archived"] = True
        save()
    facts["cleanup"]["all_test_records_archived"] = True
    actor_facts["cleanup"].update(records_archived=True, shares_revoked=True)
    (AREA / "shared-actor-facts.json").write_text(json.dumps(actor_facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Manual Flow forged-snapshot regression and public termination/revoke/archive cleanup passed.")


resuming = FACTS.exists()
cleanup_only = len(sys.argv) > 1 and sys.argv[1] == "cleanup"
facts = json.loads(FACTS.read_text(encoding="utf-8")) if resuming else {
    "system_id": SYSTEM, "actor_tenant_id": RECIPIENT_TENANT, "data_tenant_id": OWNER_TENANT,
    "fixture_flow_ids": [], "records": [], "verification": "in_progress"}
assert cleanup_only or facts["verification"] != "passed", "成功验收不重跑"
registration = member = original_binding = None
username, password = "shared_actor_" + secrets.token_hex(6), secrets.token_urlsafe(24)
owner_admin = recipient_admin = None
try:
    assert json.load(urllib.request.urlopen("http://localhost:18080/actuator/health", timeout=10))["status"] == "UP"
    bootstrap = (JAVA / "platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    owner_entry = enter(login["accessToken"], OWNER_TENANT)
    owner_admin = owner_entry["tokens"]["accessToken"]
    recipient_admin = enter(login["accessToken"], RECIPIENT_TENANT)["tokens"]["accessToken"]
    # 上次验收进程在服务启动时断开；先完成旧身份收尾，成功业务事实保留。
    previous = json.loads((AREA / "shared-actor-facts.json").read_text(encoding="utf-8"))
    catalog = api("GET", "/api/admin/system/authorization", token=recipient_admin)
    old = next(value for value in catalog["members"] if value["tenantMemberId"] == previous["tenant_member_id"])
    api("POST", f"/api/admin/system/authorization/members/{old['tenantMemberId']}/assignment", {
        "departmentId": old["departmentId"], "roleIds": [], "expectedVersion": old["version"]}, recipient_admin)
    sql(f"UPDATE auth_session SET revoked=1,revoked_reason='shared-actor-acceptance-finished' WHERE account_id={int(previous['account_id'])}; "
        f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={int(previous['account_id'])} AND username LIKE 'shared_actor_%'; "
        f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={int(previous['registration_system_id'])} AND creator_account_id={int(previous['account_id'])}")
    previous["cleanup"] = {"account_disabled": True, "sessions_revoked": True, "registration_system_disabled": True,
                           "roles_unassigned": True, "audit_preserved": True}
    (AREA / "shared-actor-facts.json").write_text(json.dumps(previous, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if resuming:
        # 仅恢复公开注册过的随机验收身份，凭证重置仅供夹具续接，随后正常登录/进入租户。
        identity = sql(f"SELECT username FROM plat_account WHERE id={int(facts['account_id'])} AND username LIKE 'shared_actor_%'")
        assert len(identity) == 1
        username = identity[0][0]
        salt = secrets.token_bytes(16)
        encode = lambda value: base64.urlsafe_b64encode(value).decode().rstrip("=")
        hashed = "pbkdf2-sha256$210000$" + encode(salt) + "$" + encode(hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 210000))
        sql(f"UPDATE plat_account_credential SET password_hash='{hashed}' WHERE account_id={int(facts['account_id'])}; "
            f"UPDATE plat_account SET status='ACTIVE',version=version+1 WHERE id={int(facts['account_id'])} AND username='{username}'")
        system_code = sql(f"SELECT code FROM sys_system WHERE id={int(facts['registration_system_id'])} AND creator_account_id={int(facts['account_id'])}")[0][0]
        token = api("POST", "/api/auth/login", {"username": username, "password": password})
        registration = {"accountId": facts["account_id"], "systemId": facts["registration_system_id"],
            "systemCode": system_code, "platformTokens": {"accessToken": token["accessToken"]}}
        facts["fixture_credential_recovery"] = "existing synthetic account only; normal login; no new source membership"
    else:
        registration = api("POST", "/api/auth/register", {"username": username, "password": password,
            "displayName": "独立共享Flow验收", "systemName": "共享Flow临时空系统"})
    facts.update(account_id=registration["accountId"], registration_system_id=registration["systemId"], email_or_sms_used=False)
    if resuming:
        member = next(value for value in catalog["members"] if value["tenantMemberId"] == facts["tenant_member_id"])
        member = api("POST", f"/api/admin/system/authorization/members/{member['tenantMemberId']}/assignment", {
            "departmentId": member["departmentId"], "roleIds": [previous["role_ids"][-1]],
            "expectedVersion": member["version"]}, recipient_admin)
    else:
        member = api("POST", "/api/admin/system/authorization/members", {
            "account": username, "departmentId": None, "roleIds": [previous["role_ids"][-1]]}, recipient_admin)
    facts["tenant_member_id"] = member["tenantMemberId"]
    actor_token = enter(registration["platformTokens"]["accessToken"], RECIPIENT_TENANT)["tokens"]["accessToken"]
    bindings = api("GET", "/api/flow-bindings?moduleId=33&triggerEvent=CONVERT", token=owner_admin)
    original_binding = facts.get("original_binding") or next(value for value in bindings["currentTenant"] if value["status"] == "ACTIVE")
    facts["original_binding"] = binding_body(original_binding)
    save()
    if cleanup_only:
        finish_fixtures()
    else:
        done = convert("source_end")
        read_messages(done, "COMPLETED")
        status, denial = call("POST", "/api/flow-runtime/instances", {"flowId": original_binding["flowId"],
            "title": "不能伪造动作来源", "businessSnapshot": {"actionId": done["action_id"], "actorTenantId": RECIPIENT_TENANT},
            "variables": {}, "idempotencyKey": "business-action:" + str(done["action_id"])}, owner_admin)
        assert status == 422 and denial["code"] == "FLOW_START_KEY_RESERVED"
        facts["manual_origin_forgery_denied"] = {"http": status, "code": denial["code"]}
        for case in ["source_person_approval", "missing_initiator", "original_actor_writeback_denied"]:
            if not any(record["case"] == case for record in facts["records"]):
                publish_case(case)
            fixture = convert(case)
            if case != "missing_initiator":
                approve(fixture)
            read_messages(fixture, "COMPLETED" if case == "source_person_approval" else "EXCEPTION")
            if case != "source_person_approval":
                errors = sql(f"SELECT error_code FROM flow_exception WHERE instance_id={fixture['flow_id']} AND status='OPEN'")
                assert len(errors) == 1
                expected = "FLOW_RUNTIME_ASSIGNEE_UNRESOLVED" if case == "missing_initiator" else "PERMISSION_DENIED"
                assert errors[0][0] == expected, errors[0][0]
                fixture["persisted_exception"] = expected
            if case == "original_actor_writeback_denied":
                source = api("GET", f"/api/runtime/modules/{SOURCE}/records/{fixture['source_id']}", token=owner_admin)
                assert source["fields"]["subject"] == "共享身份验收·" + case
                fixture["no_approver_authority_borrowed"] = True
        count = int(sql("SELECT COUNT(*) FROM sys_tenant_member tm JOIN sys_member m ON m.id=tm.system_member_id "
            f"WHERE m.account_id={registration['accountId']} AND tm.tenant_id={OWNER_TENANT}")[0][0])
        assert count == 0
        facts.update(source_membership_count=count, verification="passed")
        print("Shared Flow: source end/person approval, recipient starter notifications, missing initiator exception, original-actor writeback denial and replay passed.")
finally:
    # 各收尾动作独立记录，不能因其中一项失败跳过身份禁用和绑定恢复。
    cleanup = facts.setdefault("cleanup", {})
    if original_binding and owner_admin:
        try:
            api("POST", "/api/flow-bindings/publish", binding_body(original_binding), owner_admin)
            cleanup["source_binding_restored"] = True
        except Exception as error:
            cleanup["source_binding_restored"] = False
            cleanup["binding_error_type"] = type(error).__name__
            if isinstance(error, AssertionError): cleanup["binding_error"] = str(error)
    if member and recipient_admin:
        try:
            api("POST", f"/api/admin/system/authorization/members/{member['tenantMemberId']}/assignment", {
                "departmentId": member["departmentId"], "roleIds": [], "expectedVersion": member["version"]}, recipient_admin)
            cleanup["roles_unassigned"] = True
        except Exception as error:
            cleanup["roles_unassigned"] = False
            cleanup["member_error_type"] = type(error).__name__
    if registration:
        sql(f"UPDATE auth_session SET revoked=1,revoked_reason='shared-flow-acceptance-finished' WHERE account_id={registration['accountId']}; "
            f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={registration['accountId']} AND username='{username}'; "
            f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={registration['systemId']} AND code='{registration['systemCode']}'")
        cleanup.update(account_disabled=True, sessions_revoked=True, registration_system_disabled=True, audit_preserved=True)
    save()
