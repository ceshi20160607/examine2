"""B10.2c.2c 模块业务校验异常的回滚、脱敏、去重、恢复与权限边界验收。"""
import ast
import json
import re
import secrets
import sys
import time
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))

FACTS = AREA / "module-validation-anomaly-facts.json"
facts = json.loads(FACTS.read_text(encoding="utf-8")) if FACTS.exists() else {
    "step": "B10.2c.2c", "status": "running", "code": "validation_anomaly_" + secrets.token_hex(5),
    "module_ids": [], "flow_ids": [], "binding_ids": [], "event_ids": [], "instance_ids": [], "cases": []}
assert facts["status"] == "running", "验收事实已经封存"

SYSTEM_ID = 54
TENANT_ID = 57
GROUP_ID = 47

java_root = AREA.parents[1] / "backend/src/main/java/com/unique/unexamine"
bootstrap = (java_root / "platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
login = api("POST", "/api/auth/login", {
    "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
    "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
admin = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {}, login["accessToken"])["tokens"]["accessToken"]


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def case(name, result, **extra):
    if not any(item["case"] == name for item in facts["cases"]):
        facts["cases"].append({"case": name, "result": result, **extra})
        save()


def node(key, kind, name, x):
    return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
        "config": {}, "assigneePolicy": {}, "formPolicy": {}, "timeoutPolicy": {}, "exceptionPolicy": {}}


def event_rows(module_id):
    return api("GET", f"/api/flow-bindings/events?moduleId={module_id}&triggerEvent=EXCEPTION", token=admin)


def event(event_id, statuses=("PENDING", "FAILED", "PUBLISHED"), seconds=25):
    deadline = time.time() + seconds
    rows = []
    while time.time() < deadline:
        rows = event_rows(facts["module_id"])
        found = next((row for row in rows if row["id"] == event_id), None)
        if found and found["status"] in statuses:
            return found
        time.sleep(.35)
    raise AssertionError((event_id, statuses, rows))


def validation_events():
    rows = sql("SELECT id,status,retry_count,HEX(payload_json) FROM core_event_outbox "
        "WHERE aggregate_type='FLOW_TRIGGER_EVENT' AND event_type='EXCEPTION' "
        "AND payload_json->>'$.sourceType'='MODULE_VALIDATION' "
        f"AND payload_json->>'$.moduleId'='{int(facts['module_id'])}' ORDER BY id")
    return [{"id": int(row[0]), "status": row[1], "retry": int(row[2]),
             "payload": json.loads(bytes.fromhex(row[3]).decode("utf-8"))} for row in rows]


def new_event(excluded=(), seconds=20):
    deadline = time.time() + seconds
    while time.time() < deadline:
        found = next((row for row in reversed(validation_events()) if row["id"] not in excluded), None)
        if found:
            return found
        time.sleep(.25)
    raise AssertionError(("new module validation event missing", excluded, validation_events()))


def instance_count(source_id):
    safe = source_id.replace("'", "''")
    return int(sql("SELECT COUNT(*) FROM flow_instance WHERE business_snapshot_json->>'$.sourceType'='MODULE_VALIDATION' "
        f"AND business_snapshot_json->>'$.sourceId'='{safe}'")[0][0])


def record_count():
    return int(sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={int(facts['module_id'])} AND deleted=0")[0][0])


def bad_create(title):
    return {"title": title, "status": "ACTIVE", "participantMemberIds": [],
            "fields": {"subject": title, "amount": 1500}}


stage = sys.argv[1]
if stage == "prepare":
    assert not facts.get("prepared")
    created = api("POST", "/api/admin/module-config/modules", {
        "groupId": GROUP_ID, "code": facts["code"], "name": "模块校验异常验收"}, admin)
    module_id = created["module"]["id"]
    facts["module_id"] = module_id
    facts["module_ids"].append(module_id)
    save()
    api("POST", f"/api/admin/module-config/modules/{module_id}/fields", {
        "code": "subject", "name": "主题", "fieldType": "TEXT", "required": True,
        "sortOrder": 10, "config": {"placeholder": "请输入主题"}}, admin)
    api("POST", f"/api/admin/module-config/modules/{module_id}/fields", {
        "code": "amount", "name": "金额", "fieldType": "NUMBER", "required": False,
        "sortOrder": 20, "config": {"precision": 2}}, admin)
    definition = {"mode": "ALL", "conditions": [{"field": "amount", "operator": "GT", "value": 1000}],
                  "effect": {"type": "BLOCK"}}
    rule = api("POST", f"/api/admin/module-config/modules/{module_id}/rules-indexes/rules", {
        "code": "amount_limit", "name": "金额上限", "ruleType": "VALIDATION", "triggerEvent": "ALWAYS",
        "definition": definition, "message": "金额不能超过 1000", "sortOrder": 10}, admin)
    tested = api("POST", f"/api/admin/module-config/modules/{module_id}/rules-indexes/rules/{rule['id']}/test",
                 {"sampleFields": {"subject": "预演", "amount": 1500}}, admin)
    assert tested["matched"] and tested["effectType"] == "BLOCK"
    check = api("GET", f"/api/admin/module-config/modules/{module_id}/publication-check", token=admin)
    assert check["valid"], check["issues"]
    api("POST", f"/api/admin/module-config/modules/{module_id}/publish",
        {"expectedDraftRevision": check["draftRevision"]}, admin)

    valid_record = api("POST", f"/api/runtime/modules/{facts['code']}/records", {
        "title": "有效基线记录", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"subject": "有效基线记录", "amount": 500}}, admin)
    facts["record_id"] = valid_record["id"]
    save()

    before_count = record_count()
    no_binding_key = "validation-no-binding-" + secrets.token_hex(8)
    status, response = call("POST", f"/api/runtime/modules/{facts['code']}/records",
        bad_create("无绑定校验拒绝"), admin, no_binding_key)
    assert status == 422 and response["code"] == "BUSINESS_RULE_REJECTED", response
    no_binding = new_event()
    facts["event_ids"].append(no_binding["id"]); save()
    delivered = event(no_binding["id"], ("PUBLISHED",))
    payload = validation_events()[-1]["payload"]
    assert delivered.get("deliveryResult") == "NO_MATCHING_BINDING"
    assert record_count() == before_count
    assert payload["variables"]["actionCode"] == "CREATE"
    assert payload["variables"]["ruleName"] == "金额上限"
    assert payload["variables"]["errorCategory"] == "BUSINESS_RULE_REJECTED"
    assert set(payload["actor"]) <= {"accountId", "platformId", "systemId", "tenantId", "memberId", "tenantMemberId"}
    serialized = json.dumps(payload, ensure_ascii=False)
    assert no_binding_key not in serialized and "无绑定校验拒绝" not in serialized and "1500" not in serialized
    case("no-binding-preserves-original-rejection", "无绑定时原操作返回原业务规则错误且无新增记录，脱敏事件安全结束。",
         http=status, code=response["code"], outboxId=no_binding["id"])

    flow = api("POST", "/api/flows", {"code": "validation_flow_" + secrets.token_hex(5),
        "name": "模块校验异常处理", "description": "B10.2c.2c 临时验收流程"}, admin)
    facts["flow_ids"].append(flow["id"]); save()
    api("PUT", f"/api/flows/{flow['id']}/draft", {"expectedVersion": flow["version"],
        "nodes": [node("start", "START", "开始", 0), node("end", "END", "结束", 240)],
        "edges": [{"edgeKey": "e1", "sourceNodeKey": "start", "targetNodeKey": "end",
                   "priorityOrder": 0, "config": {}}]}, admin)
    flow_check = api("GET", f"/api/flows/{flow['id']}/publication-check", token=admin)
    assert flow_check["valid"], flow_check["issues"]
    api("POST", f"/api/flows/{flow['id']}/publish", {
        "expectedDraftRevision": flow_check["draftRevision"], "changeSummary": "模块校验异常验收",
        "simulationVariables": {}}, admin)
    condition = {"mode": "ALL", "conditions": [
        {"field": "exceptionSource", "operator": "EQ", "value": "MODULE_VALIDATION"}]}
    binding = api("POST", "/api/flow-bindings/publish", {"moduleId": module_id,
        "triggerEvent": "EXCEPTION", "flowId": flow["id"], "executionMode": "FLOW_ONLY",
        "priorityOrder": 0, "condition": condition, "mutuallyExclusive": True,
        "replaceExisting": False}, admin)
    facts["binding_ids"].append(binding["id"]); save()
    assert binding["condition"] == condition and binding["executionMode"] == "FLOW_ONLY"

    sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(flow['id'])}")
    recovery_key = "validation-recovery-" + secrets.token_hex(8)
    facts["recovery_key"] = recovery_key
    facts["recovery_title"] = "流程恢复校验拒绝"
    before_count = record_count()
    status, response = call("POST", f"/api/runtime/modules/{facts['code']}/records",
        bad_create(facts["recovery_title"]), admin, recovery_key)
    assert status == 422 and response["code"] == "BUSINESS_RULE_REJECTED"
    recovery = new_event(set(facts["event_ids"]))
    facts["event_ids"].append(recovery["id"]); save()
    failed = event(recovery["id"], ("FAILED",), 25)
    assert record_count() == before_count and failed["flowInstanceId"] is None
    recovery_payload = next(row["payload"] for row in validation_events() if row["id"] == recovery["id"])
    facts["restart"] = {"flow_id": flow["id"], "event_id": recovery["id"],
        "source_id": recovery_payload["sourceId"], "original_http": status, "original_code": response["code"]}
    facts["prepared"] = True
    save()
    case("flow-unavailable-does-not-change-business-error", "流程停用时异常事实进入可靠重试，原新建仍返回业务规则错误且完整回滚。",
         outboxId=recovery["id"], retryCount=failed["retryCount"])
    print(json.dumps({"prepare": "PASS", "restart": facts["restart"]}, ensure_ascii=False))

elif stage == "resume":
    assert facts.get("prepared") and not facts.get("resumed")
    restart = facts["restart"]
    completed_cases = {item["case"] for item in facts["cases"]}
    if "durable-event-recovers-after-backend-restart" not in completed_cases:
        retained = event(restart["event_id"], ("FAILED", "PENDING"))
        assert retained["flowInstanceId"] is None and instance_count(restart["source_id"]) == 0
        sql(f"UPDATE flow_definition SET status='ACTIVE',version=version+1 WHERE id={int(restart['flow_id'])}; "
            f"UPDATE core_event_outbox SET available_at=NOW(),version=version+1 WHERE id={int(restart['event_id'])}")
        recovered = event(restart["event_id"], ("PUBLISHED",), 25)
        assert recovered.get("flowInstanceId") and instance_count(restart["source_id"]) == 1
        facts["instance_ids"].append(recovered["flowInstanceId"]); save()
        case("durable-event-recovers-after-backend-restart", "后端重启后保留失败事件；流程恢复后只启动一个实例。",
             outboxId=restart["event_id"], instanceId=recovered["flowInstanceId"])
        completed_cases.add("durable-event-recovers-after-backend-restart")
    else:
        recovered = event(restart["event_id"], ("PUBLISHED",))
        assert recovered.get("flowInstanceId") and instance_count(restart["source_id"]) == 1

    if "same-action-and-rule-start-at-most-once" not in completed_cases:
        before_events = len(validation_events())
        status, response = call("POST", f"/api/runtime/modules/{facts['code']}/records",
            bad_create(facts["recovery_title"]), admin, facts["recovery_key"])
        assert status == restart["original_http"] and response["code"] == restart["original_code"]
        time.sleep(1.5)
        assert len(validation_events()) == before_events and instance_count(restart["source_id"]) == 1
        case("same-action-and-rule-start-at-most-once", "同一请求标识与规则重复失败仍复用原异常事实和流程实例。",
             outboxCount=before_events, instanceCount=1)
        completed_cases.add("same-action-and-rule-start-at-most-once")

    current = api("GET", f"/api/runtime/modules/{facts['code']}/records/{facts['record_id']}", token=admin)
    update_body = {"title": current["title"], "recordNumber": current.get("recordNumber"),
        "status": current["status"], "ownerMemberId": current.get("ownerMemberId"),
        "departmentId": current.get("departmentId"), "participantMemberIds": current["participantMemberIds"],
        "fields": {"subject": "编辑失败不应保存", "amount": 1500}, "version": current["version"]}
    if "update-validation-rolls-back-and-starts-exception" not in completed_cases:
        update_key = "validation-update-" + secrets.token_hex(8)
        status, response = call("PUT", f"/api/runtime/modules/{facts['code']}/records/{facts['record_id']}",
            update_body, admin, update_key)
        assert status == 422 and response["code"] == "BUSINESS_RULE_REJECTED"
        update_event = new_event(set(facts["event_ids"]))
        facts["event_ids"].append(update_event["id"]); save()
        update_delivery = event(update_event["id"], ("PUBLISHED",), 25)
        update_payload = next(row["payload"] for row in validation_events() if row["id"] == update_event["id"])
        assert update_payload["variables"]["actionCode"] == "UPDATE"
        assert update_payload["variables"]["recordId"] == facts["record_id"]
        assert update_delivery.get("flowInstanceId") and instance_count(update_payload["sourceId"]) == 1
        facts["instance_ids"].append(update_delivery["flowInstanceId"]); save()
        unchanged = api("GET", f"/api/runtime/modules/{facts['code']}/records/{facts['record_id']}", token=admin)
        assert unchanged["fields"]["amount"] == 500 and unchanged["fields"]["subject"] == "有效基线记录"
        case("update-validation-rolls-back-and-starts-exception", "编辑校验拒绝后记录值和版本不变，异常流程只携带记录引用和规则身份。",
             outboxId=update_event["id"], instanceId=update_delivery["flowInstanceId"])
        completed_cases.add("update-validation-rolls-back-and-starts-exception")
    else:
        unchanged = current
        assert unchanged["fields"]["amount"] == 500 and unchanged["fields"]["subject"] == "有效基线记录"

    before_events = len(validation_events())
    stale = dict(update_body)
    stale["fields"] = {"subject": "并发错误", "amount": 400}
    stale["version"] = unchanged["version"] + 99
    status, response = call("PUT", f"/api/runtime/modules/{facts['code']}/records/{facts['record_id']}",
        stale, admin, "validation-conflict-" + secrets.token_hex(8))
    assert status == 409 and response["code"] == "RECORD_VERSION_CONFLICT"
    time.sleep(1)
    assert len(validation_events()) == before_events
    status, response = call("POST", f"/api/runtime/modules/{facts['code']}/records",
        {"title": "", "participantMemberIds": [], "fields": {}}, admin, "validation-input-" + secrets.token_hex(8))
    assert status == 400 and response["code"] == "VALIDATION_FAILED"
    assert len(validation_events()) == before_events
    case("concurrency-and-input-errors-are-excluded", "版本冲突和普通输入错误保持原响应，均未产生 EXCEPTION 事实。",
         eventCount=before_events)

    username = "validation_denied_" + secrets.token_hex(6)
    password = secrets.token_urlsafe(24)
    registration = api("POST", "/api/auth/register", {"username": username, "password": password,
        "displayName": "校验异常权限边界", "systemName": "校验异常临时注册系统"})
    role = api("POST", "/api/admin/system/authorization/roles", {"name": "校验异常禁止新建",
        "permissions": [{"resourceType": "MODULE", "resourceCode": facts["code"],
                         "actionCode": "LIST", "dataScopeType": "ALL"}], "fieldPolicies": []}, admin)
    api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": role["version"], "reason": "验证权限拒绝不触发异常流程"}, admin)
    member = api("POST", "/api/admin/system/authorization/members", {
        "account": username, "departmentId": None, "roleIds": [role["id"]]}, admin)
    denied_token = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                       registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]
    status, response = call("POST", f"/api/runtime/modules/{facts['code']}/records",
        bad_create("权限拒绝不触发"), denied_token, "validation-denied-" + secrets.token_hex(8))
    assert status == 403 and len(validation_events()) == before_events
    facts["permission_fixture"] = {"role_id": role["id"], "tenant_member_id": member["tenantMemberId"],
        "member_version": member["version"], "account_id": registration["accountId"],
        "registration_system_id": registration["systemId"], "registration_system_code": registration["systemCode"],
        "username": username}
    save()
    case("permission-errors-are-excluded", "缺少模块新建权限返回 403，未产生 EXCEPTION 事实。", http=status)

    fields = api("GET", f"/api/flow-bindings/condition-fields?moduleId={facts['module_id']}&triggerEvent=EXCEPTION", token=admin)
    codes = {field["code"] for field in fields}
    assert {"exceptionSource", "actionCode", "errorCategory", "ruleCode", "ruleName", "recordId"} <= codes
    facts["condition_fields"] = sorted(codes)
    facts["resumed"] = True
    save()
    print(json.dumps({"resume": "PASS", "cases": len(facts["cases"]),
                      "moduleCode": facts["code"], "moduleId": facts["module_id"]}, ensure_ascii=False))

elif stage == "cleanup":
    assert facts.get("resumed") and not facts.get("cleanup")
    fixture = facts["permission_fixture"]
    api("POST", f"/api/admin/system/authorization/members/{fixture['tenant_member_id']}/assignment", {
        "departmentId": None, "roleIds": [], "expectedVersion": fixture["member_version"]}, admin)
    status, result = call("POST", f"/api/runtime/modules/{facts['code']}/records/{facts['record_id']}/delete", {
        "reason": "B10.2c.2c 验收收尾", "version": api("GET",
            f"/api/runtime/modules/{facts['code']}/records/{facts['record_id']}", token=admin)["version"]},
        admin, "validation-cleanup-" + secrets.token_hex(8))
    assert 200 <= status < 300, result
    binding_ids = ",".join(str(int(value)) for value in facts["binding_ids"])
    flow_ids = ",".join(str(int(value)) for value in facts["flow_ids"])
    module_ids = ",".join(str(int(value)) for value in facts["module_ids"])
    system_entity = (java_root / "system/base/entity/SystemDefinition.java").read_text(encoding="utf-8")
    system_table = re.search(r'@TableName\("([a-z_]+)"\)', system_entity).group(1)
    sql("START TRANSACTION; "
        f"UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE id IN ({binding_ids}); "
        f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN ({flow_ids}); "
        f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id IN ({module_ids}); "
        f"UPDATE sys_role SET status='DISABLED',version=version+1 WHERE id={int(fixture['role_id'])}; "
        f"UPDATE auth_session SET revoked=1,revoked_reason='validation-anomaly-acceptance-finished' WHERE account_id={int(fixture['account_id'])}; "
        f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={int(fixture['account_id'])} AND username='{fixture['username']}'; "
        f"UPDATE {system_table} SET status='DISABLED',version=version+1 WHERE id={int(fixture['registration_system_id'])} "
        f"AND code='{fixture['registration_system_code']}'; COMMIT;")
    unfinished = sql("SELECT id FROM flow_instance WHERE id IN (" + ",".join(
        str(int(value)) for value in facts["instance_ids"]) + ") AND status NOT IN ('COMPLETED','TERMINATED')")
    assert not unfinished
    assert not sql(f"SELECT id FROM core_event_outbox WHERE id IN ({','.join(str(int(value)) for value in facts['event_ids'])}) "
                   "AND status IN ('PENDING','FAILED')")
    facts["cleanup"] = {"binding_disabled": True, "flow_disabled": True, "module_disabled": True,
        "record_deleted": True, "temporary_account_disabled": True, "events_settled": True,
        "unfinished_instances": 0}
    facts["status"] = "passed"
    save()
    print(json.dumps({"cleanup": "PASS", "cases": len(facts["cases"])}, ensure_ascii=False))

else:
    raise SystemExit("stage must be prepare, resume or cleanup")
