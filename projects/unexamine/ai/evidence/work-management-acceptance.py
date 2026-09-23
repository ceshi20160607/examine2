"""B14.1 项目、普通任务、生命周期、权限和持久化验收。"""
import json
import re
import secrets
import subprocess
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "work-management-facts.json"
BASE = "http://127.0.0.1:18080"
SYSTEM_ID, TENANT_ID = 54, 57


def call(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, method=method, headers=headers,
        data=None if body is None else json.dumps(body, ensure_ascii=False).encode())
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else {}


def api(method, path, body=None, token=None):
    status, result = call(method, path, body, token)
    assert 200 <= status < 300, (path, status, result.get("code"), result.get("message"))
    return result["data"]


def sql(statement):
    result = subprocess.run(["docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N unexamine -e "$1"',
        "work-acceptance", statement], capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, result.stderr
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def assignment(owner, collaborators=None, followers=None, acceptors=None):
    return {"ownerTenantMemberId": owner,
            "collaboratorTenantMemberIds": collaborators or [],
            "followerTenantMemberIds": followers or [],
            "acceptanceTenantMemberIds": acceptors or []}


facts = {"step": "B14.1", "status": "running", "cases": [], "fixtures": {}, "cleanup": {}}
suffix = secrets.token_hex(4)
users = []
role = None
try:
    bootstrap_path = AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java"
    bootstrap = bootstrap_path.read_text(encoding="utf-8")
    admin_login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    admin = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                admin_login["accessToken"])["tokens"]["accessToken"]

    permissions = [{"resourceType": "WORK", "resourceCode": "SYSTEM", "actionCode": action,
                    "dataScopeType": "ALL"}
                   for action in ["VIEW", "CREATE_PROJECT", "MANAGE_PROJECT", "CREATE_TASK", "UPDATE_TASK"]]
    role = api("POST", "/api/admin/system/authorization/roles", {
        "name": "任务主链路验收角色 " + suffix, "permissions": permissions, "fieldPolicies": []}, admin)
    role = api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": role["version"], "reason": "验证项目和任务完整主链路"}, admin)

    actors = []
    for index, display_name in enumerate(["任务执行验收员", "任务协作验收员"], 1):
        username = f"work_accept_{index}_{suffix}"
        password = secrets.token_urlsafe(24)
        registration = api("POST", "/api/auth/register", {
            "username": username, "password": password, "displayName": display_name,
            "systemName": f"任务验收临时系统 {index} {suffix}"})
        member = api("POST", "/api/admin/system/authorization/members", {
            "account": username, "departmentId": None, "roleIds": [role["id"]]}, admin)
        actor_token = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                          registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]
        users.append({"username": username, "accountId": member["accountId"]})
        actors.append({"token": actor_token, "tenantMemberId": member["tenantMemberId"],
                       "displayName": display_name, "accountId": member["accountId"]})
    actor1, actor2 = actors

    directory = api("GET", "/api/system-directory", token=admin)
    admin_context = api("GET", "/api/auth/me", token=admin)
    admin_person = next(item for item in directory["people"] if item["accountId"] == admin_context["accountId"])
    module_facts = json.loads((AREA / "print-template-facts.json").read_text(encoding="utf-8"))["fixtures"]
    due = (datetime.now() + timedelta(days=5)).replace(microsecond=0).isoformat()

    project = api("POST", "/api/work/projects", {
        "name": "客户交付项目 " + suffix, "description": "验证项目、任务组和任务主链路",
        "customerName": "星河客户", "internalDepartmentName": "交付中心", "stage": "开发",
        "labels": ["重点", "交付"], "startDate": datetime.now().date().isoformat(),
        "dueDate": (datetime.now() + timedelta(days=30)).date().isoformat(),
        "businessType": "MODULE:" + module_facts["module_code"],
        "businessId": str(module_facts["record_id"]), "businessTitle": "打印验收申请单",
        "members": [
            {"tenantMemberId": actor1["tenantMemberId"], "projectRole": "MANAGER"},
            {"tenantMemberId": actor2["tenantMemberId"], "projectRole": "MEMBER"}]}, admin)
    group = api("POST", f"/api/work/projects/{project['id']}/groups", {
        "name": "开发与验收", "sortOrder": 10, "ownerTenantMemberId": actor1["tenantMemberId"],
        "dueTime": due, "progressPercent": 20}, admin)
    group_view = next(item for item in group["taskGroups"] if item["name"] == "开发与验收")
    assert group_view["owner"]["displayName"] == actor1["displayName"] and float(group_view["progressPercent"]) == 20, group_view
    project_task = api("POST", f"/api/work/projects/{project['id']}/tasks", {
        "taskGroupId": group_view["id"], "title": "完成交付接口并验收", "description": "包含清单和业务记录",
        "priority": "HIGH", "dueAt": due, "estimatedHours": 12.5, "labels": ["接口", "验收"],
        "checklist": [{"text": "接口联调", "completed": True}, {"text": "验收确认", "completed": False}],
        **assignment(actor1["tenantMemberId"], [actor2["tenantMemberId"]],
                     [admin_person["tenantMemberId"]], [actor2["tenantMemberId"]]),
        "businessType": "MODULE:" + module_facts["module_code"],
        "businessId": str(module_facts["record_id"]), "businessTitle": "打印验收申请单",
        "configuredValues": {}}, admin)
    assert project_task["businessTitle"] and len(project_task["checklist"]) == 2
    facts["cases"].append("project-fields-members-group-and-linked-project-task")

    ordinary = api("POST", "/api/work/tasks", {
        "title": "普通任务完整生命周期 " + suffix, "description": "验证转派、延期、验收和归档",
        "priority": "URGENT", "dueAt": due, "estimatedHours": 8,
        "labels": ["紧急", "验收"], "checklist": [{"text": "完成实现", "completed": False}],
        **assignment(actor1["tenantMemberId"], [actor2["tenantMemberId"]],
                     [admin_person["tenantMemberId"]], [actor2["tenantMemberId"]]),
        "configuredValues": {}}, actor1["token"])
    subtask = api("POST", "/api/work/tasks", {
        "parentTaskId": ordinary["id"], "title": "普通任务子任务", "priority": "NORMAL",
        **assignment(actor1["tenantMemberId"]), "configuredValues": {}}, actor1["token"])
    ordinary_detail = api("GET", f"/api/work/tasks/{ordinary['id']}", token=actor1["token"])
    assert ordinary_detail["subtasks"][0]["id"] == subtask["id"]

    ordinary = api("PUT", f"/api/work/tasks/{ordinary['id']}", {
        "expectedVersion": ordinary_detail["version"], "status": "IN_PROGRESS", "title": ordinary_detail["title"],
        "description": ordinary_detail["description"], "priority": ordinary_detail["priority"], "progressPercent": 10,
        **assignment(actor1["tenantMemberId"], [actor2["tenantMemberId"]],
                     [admin_person["tenantMemberId"]], [actor2["tenantMemberId"]]),
        "estimatedHours": 8, "labels": ordinary_detail["labels"], "checklist": ordinary_detail["checklist"],
        "dueAt": ordinary_detail["dueAt"], "configuredValues": {}, "comment": "开始执行"}, actor1["token"])
    ordinary = api("POST", f"/api/work/tasks/{ordinary['id']}/progress", {
        "expectedVersion": ordinary["version"], "progressPercent": 55, "actualHours": 4.5,
        "comment": "已完成核心实现"}, actor2["token"])
    new_due = (datetime.now() + timedelta(days=8)).replace(microsecond=0).isoformat()
    ordinary = api("POST", f"/api/work/tasks/{ordinary['id']}/delay", {
        "expectedVersion": ordinary["version"], "dueTime": new_due, "reason": "等待联合验收窗口"}, actor1["token"])
    ordinary = api("POST", f"/api/work/tasks/{ordinary['id']}/transfer", {
        "expectedVersion": ordinary["version"], "ownerTenantMemberId": actor2["tenantMemberId"],
        "comment": "转由协作验收员收尾"}, actor1["token"])
    ordinary = api("POST", f"/api/work/tasks/{ordinary['id']}/submit-completion", {
        "expectedVersion": ordinary["version"], "completionNote": "实现与清单均已完成", "actualHours": 7.5}, actor2["token"])
    assert ordinary["status"] == "WAITING_CONFIRMATION" and ordinary["actions"]["canAcceptCompletion"]
    ordinary = api("POST", f"/api/work/tasks/{ordinary['id']}/accept-completion", {
        "expectedVersion": ordinary["version"], "comment": "验收通过"}, actor2["token"])
    ordinary = api("POST", f"/api/work/tasks/{ordinary['id']}/reopen", {
        "expectedVersion": ordinary["version"], "comment": "补充一项交付说明"}, actor1["token"])
    ordinary = api("POST", f"/api/work/tasks/{ordinary['id']}/submit-completion", {
        "expectedVersion": ordinary["version"], "completionNote": "补充说明已完成", "actualHours": 8}, actor2["token"])
    ordinary = api("POST", f"/api/work/tasks/{ordinary['id']}/accept-completion", {
        "expectedVersion": ordinary["version"], "comment": "再次验收通过"}, actor2["token"])
    ordinary = api("POST", f"/api/work/tasks/{ordinary['id']}/archive", {
        "expectedVersion": ordinary["version"], "comment": "交付结束归档"}, actor1["token"])
    assert ordinary["status"] == "ARCHIVED" and ordinary["archiveTime"]
    facts["cases"].append("ordinary-subtask-progress-delay-transfer-submit-accept-reopen-and-archive")

    follower_task = api("POST", "/api/work/tasks", {
        "title": "关注人只读权限 " + suffix, "priority": "NORMAL", "dueAt": due,
        **assignment(actor1["tenantMemberId"], [], [actor2["tenantMemberId"]], []),
        "configuredValues": {}}, actor1["token"])
    actor2_list = api("GET", "/api/work/tasks", token=actor2["token"])
    assert any(item["id"] == follower_task["id"] for item in actor2_list)
    denied_status, denied = call("POST", f"/api/work/tasks/{follower_task['id']}/progress", {
        "expectedVersion": follower_task["version"], "progressPercent": 20}, actor2["token"])
    assert denied_status == 403 and denied.get("code") == "WORK_TASK_MODIFICATION_DENIED", (denied_status, denied)
    api("POST", f"/api/work/tasks/{follower_task['id']}/delete", {
        "expectedVersion": follower_task["version"], "comment": "权限验证夹具收尾"}, actor1["token"])
    assert not any(item["id"] == follower_task["id"] for item in api("GET", "/api/work/tasks", token=actor1["token"]))
    facts["cases"].append("follower-visible-but-cannot-modify-and-soft-delete-hides-task")

    task_rows = sql("SELECT status,progress_percent,estimated_hours,actual_hours,completion_note,"
        "completion_submitted_time IS NOT NULL,completion_accepted_time IS NOT NULL,archive_time IS NOT NULL "
        f"FROM work_task WHERE id={ordinary['id']}")
    history_rows = sql("SELECT action_code FROM work_task_history "
        f"WHERE task_id={ordinary['id']} ORDER BY id")
    member_rows = sql("SELECT member_type,COUNT(*) FROM work_task_member "
        f"WHERE task_id={ordinary['id']} GROUP BY member_type ORDER BY member_type")
    project_rows = sql("SELECT customer_name,internal_department_name,stage,JSON_LENGTH(labels_json),business_title "
        f"FROM work_project WHERE id={project['id']}")
    assert task_rows == [["ARCHIVED", "100.00", "8.00", "8.00", "补充说明已完成", "1", "1", "1"]], task_rows
    actions = [row[0] for row in history_rows]
    for required in ["CREATED", "PROGRESS_RECORDED", "DELAY_REQUESTED", "TRANSFERRED",
                     "COMPLETION_SUBMITTED", "COMPLETION_ACCEPTED", "REOPENED", "ARCHIVED"]:
        assert required in actions, (required, actions)
    assert member_rows == [["ACCEPTOR", "1"], ["COLLABORATOR", "1"], ["FOLLOWER", "1"]], member_rows
    assert project_rows == [["星河客户", "交付中心", "开发", "2", "打印验收申请单"]], project_rows
    facts["cases"].append("database-readback-preserves-typed-fields-members-and-immutable-history")

    public_actors = [{key: value for key, value in actor.items() if key != "token"} for actor in actors]
    facts["fixtures"] = {"project_id": project["id"], "group_id": group_view["id"],
        "project_task_id": project_task["id"], "ordinary_task_id": ordinary["id"],
        "subtask_id": subtask["id"], "follower_task_id": follower_task["id"],
        "role_id": role["id"], "actors": public_actors, "users": users}
    account_ids = ",".join(str(item["accountId"]) for item in users)
    usernames = ",".join("'" + item["username"].replace("'", "''") + "'" for item in users)
    sql(f"UPDATE sys_role SET status='DISABLED',version=version+1 WHERE id={role['id']} AND status='ACTIVE'; "
        "UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id "
        f"SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id IN({account_ids}) AND tm.status='ACTIVE'; "
        f"UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id IN({account_ids}) AND status='ACTIVE'; "
        f"UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id IN({account_ids}) AND status='ACTIVE'; "
        f"UPDATE auth_session SET revoked=1,revoked_reason='work-acceptance-closed' WHERE account_id IN({account_ids}) AND revoked=0; "
        f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id IN({account_ids}) AND username IN({usernames}) AND status='ACTIVE'; "
        f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE creator_account_id IN({account_ids}) AND status='ACTIVE'")
    cleanup_rows = sql("SELECT "
        f"(SELECT COUNT(*) FROM plat_account WHERE id IN({account_ids}) AND status='DISABLED'),"
        f"(SELECT COUNT(*) FROM sys_role WHERE id={role['id']} AND status='DISABLED'),"
        f"(SELECT COUNT(*) FROM auth_session WHERE account_id IN({account_ids}) AND revoked=0),"
        f"(SELECT COUNT(*) FROM sys_system WHERE creator_account_id IN({account_ids}) AND status='ACTIVE')")
    assert cleanup_rows == [[str(len(users)), "1", "0", "0"]], cleanup_rows
    facts["cleanup"] = {"accounts_disabled": len(users), "role_disabled": True,
        "active_sessions": 0, "temporary_systems_active": 0, "business_evidence_retained": True}
    facts["status"] = "passed"
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "passed", "cases": len(facts["cases"]),
                      "project": project["id"], "task": ordinary["id"]}, ensure_ascii=False))
except Exception as error:
    facts["failure"] = str(error)
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    raise
