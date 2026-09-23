"""共享公共身份链的真实接口验收；凭证仅在本进程内存，使用现有服务。"""
import json
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
JAVA = AREA.parents[1] / "backend/src/main/java/com/unique/unexamine"
SOURCE, TARGET = "spark_shared_business", "spark_shared_target"
SYSTEM, OWNER_TENANT, RECIPIENT_TENANT = 54, 56, 57
FACTS = AREA / "shared-actor-facts.json"


def call(method, path, body=None, token=None, action_key=None):
    """只调用本地项目接口；断言和事实文件不输出凭证或完整鉴权响应。"""
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if action_key:
        headers.update({"Prefer": "return=action", "Idempotency-Key": action_key})
    request = urllib.request.Request("http://localhost:18080" + path, method=method,
        headers=headers, data=None if body is None else json.dumps(body, ensure_ascii=False).encode())
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def api(method, path, body=None, token=None, action_key=None):
    status, result = call(method, path, body, token, action_key)
    assert 200 <= status < 300, (path, status, result.get("code"), result.get("message"))
    return result["data"]


def sql(statement):
    """读取本次验收结果；唯一写入用途是结束时禁用随机测试身份和其空系统。"""
    result = subprocess.run(["docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"', "shared-actor", statement],
        capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, "本地读回/测试身份收尾失败，不输出包含环境凭据的命令"
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def enter(platform_token, tenant):
    return api("POST", f"/api/systems/{SYSTEM}/tenants/{tenant}/enter", {}, platform_token)


def role_for(case, convert=True, create=True, scope="ALL"):
    permissions = [
        {"resourceType": "MODULE", "resourceCode": SOURCE, "actionCode": action, "dataScopeType": "ALL"}
        for action in ["LIST", "DETAIL"]]
    permissions.append({"resourceType": "MODULE", "resourceCode": TARGET,
                        "actionCode": "DETAIL", "dataScopeType": "ALL"})
    if convert:
        permissions.append({"resourceType": "MODULE", "resourceCode": SOURCE,
                            "actionCode": "CONVERT", "dataScopeType": scope})
    if create:
        permissions.append({"resourceType": "MODULE", "resourceCode": TARGET,
                            "actionCode": "CREATE", "dataScopeType": "ALL"})
    role = api("POST", "/api/admin/system/authorization/roles", {
        "name": "公共身份链验收·" + case, "permissions": permissions,
        "fieldPolicies": [{"resourceCode": module, "fieldCode": "subject", "channel": channel,
                           "readable": True, "writable": True}
                          for module in [SOURCE, TARGET] for channel in ["PAGE", "FLOW"]]}, recipient_admin)
    api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": role["version"], "reason": "真实独立共享操作者验收"}, recipient_admin)
    facts["role_ids"].append(role["id"])
    return role["id"]


def assign(role_id):
    global member
    member = api("POST", f"/api/admin/system/authorization/members/{member['tenantMemberId']}/assignment", {
        "departmentId": None, "roleIds": [role_id], "expectedVersion": member["version"]}, recipient_admin)


def new_shared_record(label):
    record = api("POST", f"/api/runtime/modules/{SOURCE}/records", {
        "title": "公共身份链验收·" + label, "status": "ACTIVE", "ownerMemberId": owner_entry["systemMemberId"],
        "participantMemberIds": [], "fields": {"subject": "公共身份链验收·" + label}}, owner_admin)
    share = api("POST", f"/api/runtime/modules/{SOURCE}/records/{record['id']}/shares", {
        "targetTenantId": RECIPIENT_TENANT, "allowedActions": ["LIST", "DETAIL", "CONVERT"]}, owner_admin)
    facts["records"].append({"id": record["id"], "share_id": share["id"]})
    save()
    return record


def marker_count(record_id):
    return int(sql(f"SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id={int(record_id)}")[0][0])


def verify_flow(record_id, action):
    """校验实际动作派生的来源 Flow；不以 outbox 或成功响应代替实例读回。"""
    rows = sql("SELECT id,tenant_id,started_by_account_id,started_by_tenant_member_id,"
        f"HEX(business_snapshot_json) FROM flow_instance WHERE business_type='MODULE:{SOURCE}' "
        f"AND business_id='{record_id}' ORDER BY id DESC")
    return rows


if FACTS.exists():
    previous = json.loads(FACTS.read_text(encoding="utf-8"))
    # 仅已禁用且没有业务记录的失败夹具可重试；角色只禁用本次前缀和租户下的具体 ID。
    assert previous.get("cleanup", {}).get("account_disabled") and not previous["records"], "接手现有成功夹具，不重复注册"
    if previous["role_ids"]:
        ids = ",".join(str(int(value)) for value in previous["role_ids"])
        sql(f"UPDATE sys_role SET status='DISABLED',version=version+1 WHERE id IN ({ids}) "
            f"AND tenant_id={RECIPIENT_TENANT} AND name LIKE '公共身份链验收·%'")
        previous["cleanup"]["unused_roles_disabled"] = True
        previous["failed_stage"] = "recipient_root_department_missing"
        archive = "shared-actor-before-root-department.json"
    else:
        previous.setdefault("failed_stage", "inherited_module_role_reference")
        archive = "shared-actor-before-catalog-fix.json"
    (AREA / archive).write_text(json.dumps(previous, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
facts = {"system_id": SYSTEM, "actor_tenant_id": RECIPIENT_TENANT, "data_tenant_id": OWNER_TENANT,
         "records": [], "role_ids": [], "cases": [], "business_verification": "in_progress"}
member = registration = None
owner_admin = recipient_admin = None
username = "shared_actor_" + secrets.token_hex(6)
password = secrets.token_urlsafe(24)
try:
    bootstrap = (JAVA / "platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    owner_entry = enter(login["accessToken"], OWNER_TENANT)
    owner_admin = owner_entry["tokens"]["accessToken"]
    recipient_admin = enter(login["accessToken"], RECIPIENT_TENANT)["tokens"]["accessToken"]
    # 当前注册契约创建一个空系统；邮箱/SMS不参与，结束时仅禁用本次空系统。
    registration = api("POST", "/api/auth/register", {"username": username, "password": password,
        "displayName": "独立共享身份验收", "systemName": "公共身份链临时注册系统"})
    facts.update({"account_id": registration["accountId"], "registration_system_id": registration["systemId"],
                  "email_or_sms_used": False})
    catalog = api("GET", "/api/admin/system/authorization", token=recipient_admin)
    assert {SOURCE, TARGET} <= {resource["resourceCode"] for resource in catalog["resources"]}
    # 正常组织配置先建立接收租户部门；这不补来源成员，也不声称修好了新租户初始化缺口。
    root = next((value for value in catalog["departments"] if value["parentId"] is None), None)
    if root is None:
        root = api("POST", "/api/admin/system/authorization/departments", {
            "code": "root", "name": "接收租户根部门", "parentId": None, "sortOrder": 0}, recipient_admin)
    facts["organization_fixture"] = {"root_department_id": root["id"], "public_api": True,
        "automatic_new_tenant_root_initialization": "still_missing"}
    # 资源目录新增主租户模板，不允许引用另一个系统真实存在的模块。
    status, foreign = call("POST", "/api/admin/system/authorization/roles", {
        "name": "跨系统引用必须拒绝", "permissions": [{"resourceType": "MODULE",
            "resourceCode": "module_49d10b5a8b", "actionCode": "DETAIL", "dataScopeType": "ALL"}],
        "fieldPolicies": []}, recipient_admin)
    assert status == 422 and foreign["code"] == "AUTHORIZATION_REFERENCE_INVALID"
    facts["catalog"] = {"inherited_modules_named": True, "foreign_system_module_denied": True}
    roles = {"no_convert": role_for("缺少转化", convert=False),
             "no_create": role_for("缺少目标新建", create=False),
             "self": role_for("仅本人范围", scope="SELF"), "all": role_for("明确允许全部范围")}
    member = api("POST", "/api/admin/system/authorization/members", {
        "account": username, "departmentId": None, "roleIds": [roles["no_convert"]]}, recipient_admin)
    facts["tenant_member_id"] = member["tenantMemberId"]
    actor_token = enter(registration["platformTokens"]["accessToken"], RECIPIENT_TENANT)["tokens"]["accessToken"]
    facts["source_membership_count"] = int(sql("SELECT COUNT(*) FROM sys_tenant_member tm "
        "JOIN sys_member m ON m.id=tm.system_member_id "
        f"WHERE m.account_id={registration['accountId']} AND tm.tenant_id={OWNER_TENANT}")[0][0])
    assert facts["source_membership_count"] == 0
    record = new_shared_record("权限矩阵")
    path = f"/api/runtime/modules/{SOURCE}/records/{record['id']}"
    body = {"targetModuleCode": TARGET, "version": record["version"]}
    for case in ["no_convert", "no_create", "self"]:
        assign(roles[case])
        before = sql(f"SELECT COUNT(*) FROM biz_record WHERE system_id={SYSTEM} AND tenant_id={RECIPIENT_TENANT} AND module_id=34")
        status, result = call("POST", path + "/convert", body, actor_token, secrets.token_hex(16))
        assert status == 403, (case, status, result.get("code"))
        assert marker_count(record["id"]) == 0
        assert before == sql(f"SELECT COUNT(*) FROM biz_record WHERE system_id={SYSTEM} AND tenant_id={RECIPIENT_TENANT} AND module_id=34")
        facts["cases"].append({"case": case, "http": status, "code": result["code"], "no_half_writes": True})
        save()
    assign(roles["all"])
    preview = api("POST", path + "/conversion-preview", body, actor_token)
    assert preview["executable"], "明确允许的共享转化预览应可执行"
    action_key = secrets.token_hex(16)
    action = api("POST", path + "/convert", body, actor_token, action_key)
    assert action["mode"] == "AFTER_EXECUTION" and action["value"]["targetRecord"]["dataTenantId"] == RECIPIENT_TENANT
    assert marker_count(record["id"]) == 1
    facts["target_record_id"] = action["value"]["targetRecord"]["id"]
    facts["action_id"] = action["idempotencyRecordId"]
    replay = api("POST", path + "/convert", body, actor_token, action_key)
    assert replay["replayed"] and replay["value"]["targetRecord"]["id"] == facts["target_record_id"]
    status, repeated = call("POST", path + "/convert", body, actor_token, secrets.token_hex(16))
    assert status == 409 and repeated["code"] == "RECORD_ALREADY_CONVERTED"
    facts["cases"].append({"case": "allowed_all", "positive_persisted": True, "recipient_target": True,
                            "replay_same_target": True, "permanent_duplicate_denied": True})
    save()
    print("B07.4b.2a: independent recipient actor permission/scope denials and positive conversion persisted; source Flow checked separately.", flush=True)
    print("WAITING: before, after or finish; credentials remain only in this process.", flush=True)
    while True:
        phase = input().strip()
        if phase == "finish":
            break
        if phase in {"before", "after"}:
            if phase == "after":
                record = new_shared_record("来源流程接线")
                body = {"targetModuleCode": TARGET, "version": record["version"]}
                action = api("POST", f"/api/runtime/modules/{SOURCE}/records/{record['id']}/convert", body,
                    actor_token, secrets.token_hex(16))
                facts["after_action_id"] = action["idempotencyRecordId"]
                facts["after_target_record_id"] = action["value"]["targetRecord"]["id"]
            actor_token = enter(registration["platformTokens"]["accessToken"], RECIPIENT_TENANT)["tokens"]["accessToken"]
            delivery = api("GET", f"/api/runtime/modules/{SOURCE}/records/action-results/{action['idempotencyRecordId']}", token=actor_token)
            flow_rows = verify_flow(record["id"], action)
            facts[phase] = {"delivery": {k: delivery.get(k) for k in ["status", "mode", "flowInstanceId", "businessStatus", "errorCode", "message"]},
                            "flow_rows": flow_rows}
            if phase == "after":
                assert len(flow_rows) == 1 and int(flow_rows[0][1]) == OWNER_TENANT
                assert int(flow_rows[0][2]) == registration["accountId"]
                assert facts["source_membership_count"] == 0
            save()
            print(json.dumps({"phase": phase, "delivery": facts[phase]["delivery"], "flow_count": len(flow_rows)},ensure_ascii=False), flush=True)
finally:
    if member and recipient_admin:
        api("POST", f"/api/admin/system/authorization/members/{member['tenantMemberId']}/assignment", {
            "departmentId": None, "roleIds": [], "expectedVersion": member["version"]}, recipient_admin)
    if registration:
        # 收尾只命中随机测试账号及其注册所得空系统，保留不可变审计和成员历史。
        table_source = (JAVA / "system/base/entity/SystemDefinition.java").read_text(encoding="utf-8")
        table = re.search(r'@TableName\("([a-z_]+)"\)', table_source).group(1)
        sql("START TRANSACTION; UPDATE auth_session SET revoked=1,revoked_reason='shared-actor-acceptance-finished' "
            f"WHERE account_id={registration['accountId']}; UPDATE plat_account SET status='DISABLED',version=version+1 "
            f"WHERE id={registration['accountId']} AND username='{username}'; "
            f"UPDATE {table} SET status='DISABLED',version=version+1 WHERE id={registration['systemId']} "
            f"AND code='{registration['systemCode']}'; COMMIT;")
        facts["cleanup"] = {"account_disabled": True, "sessions_revoked": True,
                            "registration_system_disabled": True, "audit_preserved": True}
    save()
