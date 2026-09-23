"""B14.3 待办聚合、今日联系规则和消息中心真实 API 验收。"""
import json
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "todo-message-facts.json"
BASE = "http://127.0.0.1:18080"
SYSTEM_ID, TENANT_ID = 54, 57


def call(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    raw = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    request = urllib.request.Request(BASE + path, method=method, headers=headers, data=raw)
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            data = response.read()
            return response.status, json.loads(data) if data else {}
    except urllib.error.HTTPError as error:
        data = error.read()
        return error.code, json.loads(data) if data else {}


def api(method, path, body=None, token=None):
    status, result = call(method, path, body, token)
    assert 200 <= status < 300, (path, status, result.get("code"), result.get("message"))
    return result["data"]


def sql(statement):
    result = subprocess.run(["docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N unexamine -e "$1"',
        "todo-message-acceptance", statement], capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, result.stderr
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def admin_tokens():
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    system = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {}, login["accessToken"])
    return login["accessToken"], system["tokens"]["accessToken"]


def publish_module(module_id, token):
    check = api("GET", f"/api/admin/module-config/modules/{module_id}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/admin/module-config/modules/{module_id}/publish",
               {"expectedDraftRevision": check["draftRevision"]}, token)


def wait_ready():
    for _ in range(50):
        try:
            with urllib.request.urlopen(BASE + "/actuator/health/readiness", timeout=2) as response:
                if response.status == 200:
                    return
        except Exception:
            time.sleep(1)
    raise AssertionError("backend did not become ready")


def save(facts):
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


stage = sys.argv[1] if len(sys.argv) > 1 else "prepare"

if stage == "prepare":
    suffix = secrets.token_hex(5)
    module_code = "contact_accept_" + suffix
    username = "contact_accept_" + suffix
    password = secrets.token_urlsafe(24)
    facts = {"step": "B14.3", "status": "running", "cases": [], "fixtures": {}, "cleanup": {},
        "preparation_failures": [
            "首次真实撤权刷新暴露运行时拒绝与待办失效写入共用事务，捕获拒绝后外层仍被标记回滚并返回 500；"
            "失败夹具已按精确标识停用，改为隔离运行时读事务后使用全新夹具重跑。",
            "第二次真实撤权已确认待办链修复，但消息列表计算目标可访问状态仍加入原运行时事务并返回 500；"
            "第二批夹具已精确停用，隔离读取器提升为待办与消息共同使用后再以全新夹具重跑。"]}
    platform, admin = admin_tokens()

    module = api("POST", "/api/admin/module-config/modules", {
        "code": module_code, "name": "客户联系验收", "groupName": "待办与消息验收"}, admin)["module"]
    subject = api("POST", f"/api/admin/module-config/modules/{module['id']}/fields", {
        "code": "subject", "name": "客户事项", "fieldType": "TEXT", "required": True,
        "sortOrder": 10, "config": {}}, admin)
    contact = api("POST", f"/api/admin/module-config/modules/{module['id']}/fields", {
        "code": "contact_on", "name": "下次联系日期", "fieldType": "DATE", "required": True,
        "sortOrder": 20, "config": {}}, admin)
    publication = publish_module(module["id"], admin)

    permissions = [
        {"resourceType": "MODULE", "resourceCode": module_code, "actionCode": action, "dataScopeType": "SELF"}
        for action in ["LIST", "DETAIL", "CREATE", "UPDATE"]
    ] + [
        {"resourceType": "TODO", "resourceCode": "SYSTEM", "actionCode": "VIEW", "dataScopeType": "ALL"},
        {"resourceType": "MESSAGE", "resourceCode": "SYSTEM", "actionCode": "VIEW", "dataScopeType": "ALL"},
    ]
    field_policies = [
        {"resourceCode": module_code, "fieldCode": field, "channel": "PAGE",
         "readable": True, "writable": True, "maskStrategy": None}
        for field in ["subject", "contact_on"]
    ]
    role = api("POST", "/api/admin/system/authorization/roles", {
        "name": "联系待办验收角色 " + suffix, "permissions": permissions,
        "fieldPolicies": field_policies}, admin)
    role = api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": role["version"], "reason": "验证今日联系和消息权限回查"}, admin)
    registration = api("POST", "/api/auth/register", {
        "username": username, "password": password, "displayName": "联系待办验收成员",
        "systemName": "联系待办临时注册系统 " + suffix})
    member = api("POST", "/api/admin/system/authorization/members", {
        "account": username, "departmentId": None, "roleIds": [role["id"]]}, admin)
    actor = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]

    today = datetime.now().date().isoformat()
    record = api("POST", f"/api/runtime/modules/{module_code}/records", {
        "title": "联系重点客户确认续约", "status": "ACTIVE",
        "ownerMemberId": member["systemMemberId"], "participantMemberIds": [],
        "fields": {"subject": "续约意向确认", "contact_on": today}}, actor)

    rule = api("POST", "/api/todo-contact-rules", {
        "name": "客户下次联系提醒", "moduleCode": module_code,
        "contactFieldCode": "contact_on", "leadDays": 0, "priority": "HIGH"}, admin)
    assert rule["status"] == "DRAFT" and rule["contactFieldName"] == "下次联系日期"
    rule = api("POST", f"/api/todo-contact-rules/{rule['id']}/publish",
               {"expectedVersion": rule["version"]}, admin)
    assert rule["status"] == "PUBLISHED"

    todos = api("GET", "/api/todos?status=PENDING&type=CONTACT", token=actor)
    matching = [item for item in todos if item["sourceType"] == "RUNTIME_RECORD"
                and item["title"] == record["title"]]
    assert len(matching) == 1, matching
    todo = matching[0]
    assert todo["sourceLabel"] == "今日需联系" and todo["objectName"] == "客户联系验收"
    assert todo["priority"] == "HIGH" and todo["dueAt"].startswith(today)
    repeat = api("GET", "/api/todos?status=PENDING&type=CONTACT", token=actor)
    assert [item["id"] for item in repeat].count(todo["id"]) == 1
    opened = api("POST", f"/api/todos/{todo['id']}/open", {}, actor)
    assert opened["route"] == f"/systems/{SYSTEM_ID}?workspace=runtime&module={module_code}&recordId={record['id']}"
    facts["cases"].append("published-module-date-field-projects-one-deduplicated-owner-todo-and-safe-open")

    preference = api("POST", "/api/messages/preferences", {
        "channel": "WECOM", "enabled": False, "quietStart": None, "quietEnd": None,
        "expectedVersion": None}, actor)
    template_code = "CONTACT_NOTICE_" + suffix.upper()
    template = api("POST", "/api/message-templates", {
        "code": template_code, "name": "联系事项外部提醒", "channel": "WECOM",
        "subjectTemplate": "待联系：{{title}}", "contentTemplate": "请处理 {{title}}。",
        "requiredVariables": ["title"]}, admin)
    template = api("POST", f"/api/message-templates/{template['id']}/publish", {}, admin)
    target_id = f"{module_code}:{record['id']}"
    first_body = {"templateCode": template_code, "sourceType": "SYSTEM", "dedupKey": "contact-1-" + suffix,
        "recipients": [{"type": "TENANT_MEMBER", "id": member["tenantMemberId"]}],
        "variables": {"title": record["title"]}, "targetType": "RUNTIME_RECORD", "targetId": target_id,
        "targetRoute": f"/systems/{SYSTEM_ID}?workspace=runtime&module={module_code}&recordId={record['id']}",
        "sensitivity": "NORMAL"}
    first = api("POST", "/api/messages/events", first_body, admin)
    replay = api("POST", "/api/messages/events", first_body, admin)
    assert replay["id"] == first["id"]
    assert {item["channel"]: item["status"] for item in first["deliveries"]} == {
        "IN_APP": "DELIVERED", "WECOM": "SKIPPED"}

    now = datetime.now()
    preference = api("POST", "/api/messages/preferences", {
        "channel": "WECOM", "enabled": True,
        "quietStart": (now - timedelta(minutes=1)).strftime("%H:%M"),
        "quietEnd": (now + timedelta(hours=1)).strftime("%H:%M"),
        "expectedVersion": preference["version"]}, actor)
    second_body = dict(first_body)
    second_body["dedupKey"] = "contact-2-" + suffix
    second = api("POST", "/api/messages/events", second_body, admin)
    assert {item["channel"]: item["status"] for item in second["deliveries"]}["WECOM"] == "QUIET_PENDING"
    page = api("GET", "/api/messages?status=ACTIVE&sourceType=SYSTEM&page=1&pageSize=1", token=actor)
    assert page["total"] == 2 and page["page"] == 1 and page["pageSize"] == 1 and page["hasNext"]
    message_open = api("POST", f"/api/messages/{second['id']}/open", {}, actor)
    assert message_open["route"] == opened["route"]
    unread = api("POST", "/api/messages/read-all", {}, actor)
    assert unread["count"] == 0
    archived = api("POST", f"/api/messages/{first['id']}/archive", {}, actor)
    assert archived["recipientStatus"] == "ARCHIVED"
    facts["cases"].append("message-preference-disabled-and-quiet-pagination-read-all-archive-dedup-and-deep-link")

    preference = api("POST", "/api/messages/preferences", {
        "channel": "WECOM", "enabled": True, "quietStart": None, "quietEnd": None,
        "expectedVersion": preference["version"]}, actor)
    diagnostics = api("GET", f"/api/messages/diagnostics/{second['id']}/deliveries", token=admin)
    external = next(item for item in diagnostics["deliveries"] if item["channel"] == "WECOM")
    for expected_attempt in [1, 2, 3]:
        retried = api("POST", f"/api/messages/deliveries/{external['id']}/retry", {}, admin)
        current = next(item for item in retried["deliveries"] if item["channel"] == "WECOM")
        assert current["attemptCount"] == expected_attempt
    assert current["status"] == "FAILED"
    facts["cases"].append("quiet-delivery-resumes-through-managed-retry-and-records-terminal-failure")

    subprocess.run(["docker", "restart", "unexamine-golden-backend"], check=True, capture_output=True)
    wait_ready()
    after_restart = api("GET", "/api/todos?status=PENDING&type=CONTACT", token=actor)
    assert any(item["id"] == todo["id"] for item in after_restart)
    inbox_restart = api("GET", "/api/messages?status=ALL&sourceType=SYSTEM&page=1&pageSize=10", token=actor)
    assert inbox_restart["total"] == 2 and {item["id"] for item in inbox_restart["messages"]} == {first["id"], second["id"]}
    facts["cases"].append("same-backend-restart-preserves-todo-message-preference-and-deliveries")

    current_role = next(item for item in api("GET", "/api/admin/system/authorization", token=admin)["roles"]
                        if item["id"] == role["id"])
    revoked_permissions = [item for item in current_role["permissions"]
                           if not (item["resourceType"] == "MODULE" and item["resourceCode"] == module_code
                                   and item["actionCode"] == "DETAIL")]
    draft = api("POST", "/api/admin/system/authorization/roles", {
        "id": current_role["id"], "code": current_role["code"], "name": current_role["name"],
        "description": current_role["description"], "expectedVersion": current_role["version"],
        "permissions": revoked_permissions, "fieldPolicies": current_role["fieldPolicies"]}, admin)
    api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": draft["version"], "reason": "验证待办和消息打开时重新检查目标权限"}, admin)
    all_todos = api("GET", "/api/todos?status=ALL&type=CONTACT", token=actor)
    revoked_todo = next(item for item in all_todos if item["id"] == todo["id"])
    assert revoked_todo["status"] == "INVALID"
    denied_status, denied = call("POST", f"/api/todos/{todo['id']}/open", {}, actor)
    assert denied_status in [403, 404], (denied_status, denied)
    denied_message_status, denied_message = call("POST", f"/api/messages/{second['id']}/open", {}, actor)
    assert denied_message_status == 403, (denied_message_status, denied_message)
    visible_after_revoke = api("GET", "/api/messages?status=ALL&sourceType=SYSTEM&page=1&pageSize=10", token=actor)
    target = next(item for item in visible_after_revoke["messages"] if item["id"] == second["id"])
    assert target["targetCurrentlyAccessible"] is False
    facts["cases"].append("same-session-target-permission-revocation-invalidates-todo-and-denies-message-open")

    readback = sql(
        f"SELECT status,module_code,contact_field_code,lead_days,priority FROM todo_contact_rule WHERE id={rule['id']}; "
        f"SELECT todo_type,source_type,status,priority FROM todo_item WHERE id={todo['id']}; "
        f"SELECT channel,status,attempt_count FROM msg_delivery WHERE message_id IN({first['id']},{second['id']}) ORDER BY message_id,channel")
    assert readback[0] == ["PUBLISHED", module_code, "contact_on", "0", "HIGH"]
    assert readback[1] == ["CONTACT", "RUNTIME_RECORD", "INVALID", "HIGH"]
    assert [row[0:2] for row in readback[2:]] == [
        ["IN_APP", "DELIVERED"], ["WECOM", "SKIPPED"],
        ["IN_APP", "DELIVERED"], ["WECOM", "FAILED"]]
    facts["cases"].append("database-readback-confirms-rule-todo-recipient-and-delivery-state")

    facts["fixtures"] = {
        "module_id": module["id"], "module_group_id": module["groupId"], "module_code": module_code,
        "module_publication_version_id": publication["versionId"], "record_id": record["id"],
        "rule_id": rule["id"], "rule_version": rule["version"], "todo_id": todo["id"],
        "template_id": template["id"], "message_ids": [first["id"], second["id"]],
        "role_id": role["id"], "account_id": registration["accountId"], "username": username,
        "registration_system_id": registration["systemId"], "tenant_member_id": member["tenantMemberId"]}
    facts["status"] = "prepared"
    save(facts)
    print(json.dumps({"status": "prepared", "cases": len(facts["cases"]),
                      "rule": rule["id"], "todo": todo["id"], "messages": 2}, ensure_ascii=False))

elif stage == "cleanup":
    facts = json.loads(FACTS.read_text(encoding="utf-8"))
    assert facts["status"] == "prepared"
    _, admin = admin_tokens()
    fixture = facts["fixtures"]
    rules = api("GET", "/api/todo-contact-rules", token=admin)
    rule = next(item for item in rules if item["id"] == fixture["rule_id"])
    if rule["status"] == "PUBLISHED":
        api("POST", f"/api/todo-contact-rules/{rule['id']}/disable", {
            "expectedVersion": rule["version"]}, admin)
    record = api("GET", f"/api/runtime/modules/{fixture['module_code']}/records/{fixture['record_id']}", token=admin)
    if not record["deleted"]:
        api("POST", f"/api/runtime/modules/{fixture['module_code']}/records/{fixture['record_id']}/delete", {
            "reason": "B14.3 待办消息验收数据清理", "version": record["version"]}, admin)
    account_id = int(fixture["account_id"])
    sql(
        f"DELETE FROM todo_contact_rule WHERE id={int(fixture['rule_id'])} AND status='DISABLED'; "
        f"UPDATE msg_template SET status='DISABLED',version=version+1 WHERE id={int(fixture['template_id'])}; "
        f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={int(fixture['module_id'])} AND code='{fixture['module_code']}'; "
        f"UPDATE cfg_module_group SET status='DISABLED',version=version+1 WHERE id={int(fixture['module_group_id'])}; "
        f"UPDATE sys_role SET status='DISABLED',version=version+1 WHERE id={int(fixture['role_id'])}; "
        "UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id "
        f"SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={account_id} AND tm.status='ACTIVE'; "
        f"UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={account_id} AND status='ACTIVE'; "
        f"UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={account_id} AND status='ACTIVE'; "
        f"UPDATE auth_session SET revoked=1,revoked_reason='todo-message-acceptance-closed' WHERE account_id={account_id} AND revoked=0; "
        f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account_id} AND username='{fixture['username']}'; "
        f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={int(fixture['registration_system_id'])}")
    cleanup = sql("SELECT "
        f"(SELECT COUNT(*) FROM todo_contact_rule WHERE id={int(fixture['rule_id'])}),"
        f"(SELECT COUNT(*) FROM plat_account WHERE id={account_id} AND status='DISABLED'),"
        f"(SELECT COUNT(*) FROM sys_role WHERE id={int(fixture['role_id'])} AND status='DISABLED'),"
        f"(SELECT COUNT(*) FROM auth_session WHERE account_id={account_id} AND revoked=0),"
        f"(SELECT COUNT(*) FROM cfg_module WHERE id={int(fixture['module_id'])} AND status='DISABLED')")[0]
    assert cleanup == ["0", "1", "1", "0", "1"], cleanup
    facts["cleanup"] = {"rule_removed": True, "account_disabled": True, "role_disabled": True,
        "active_sessions": 0, "module_disabled": True, "record_soft_deleted": True,
        "message_and_todo_evidence_retained": True}
    facts["status"] = "passed"
    save(facts)
    print(json.dumps({"status": "passed", "cases": len(facts["cases"]),
                      "cleanup": facts["cleanup"]}, ensure_ascii=False))

else:
    raise SystemExit("stage must be prepare or cleanup")
