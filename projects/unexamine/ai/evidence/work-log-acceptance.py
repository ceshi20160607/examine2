"""B14.2 工作日志结构化内容、权限、状态、选材、附件和持久化验收。"""
import json
import re
import secrets
import subprocess
import urllib.error
import urllib.request
import urllib.parse
from datetime import datetime, timedelta
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "work-log-facts.json"
BASE = "http://127.0.0.1:18080"
SYSTEM_ID, TENANT_ID = 54, 57


def call(method, path, body=None, token=None, headers=None, raw=None):
    request_headers = dict(headers or {})
    if raw is None:
        request_headers["Content-Type"] = "application/json"
        raw = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    if token:
        request_headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, method=method, headers=request_headers, data=raw)
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
        "work-log-acceptance", statement], capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, result.stderr
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def multipart_file(path, token, name, content, content_type="text/plain"):
    boundary = "----unexamine" + secrets.token_hex(12)
    raw = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
           f"Content-Type: {content_type}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    status, result = call("POST", path, token=token,
                          headers={"Content-Type": "multipart/form-data; boundary=" + boundary}, raw=raw)
    assert 200 <= status < 300, (path, status, result)
    return result["data"]


def log_payload(project_id, task_id, materials, visibility, role_ids, completed, reason=None, version=None):
    value = {
        "workDate": datetime.now().date().isoformat(), "title": "客户交付日报",
        "completedText": completed, "inProgressText": "联调附件和权限边界",
        "nextPlanText": "完成上线前回归", "riskText": "等待外部验收窗口",
        "assistanceText": "请项目负责人确认验收时间", "durationMinutes": 180,
        "projectId": project_id, "taskIds": [task_id], "visibilityType": visibility,
        "visibleRoleIds": role_ids, "materials": materials, "configuredValues": {}
    }
    if version is not None:
        value["expectedVersion"] = version
        value["revisionReason"] = reason
    return value


facts = {"step": "B14.2", "status": "running", "cases": [], "fixtures": {}, "cleanup": {}}
suffix = secrets.token_hex(4)
accounts, roles = [], []
try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    admin_login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    admin = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                admin_login["accessToken"])["tokens"]["accessToken"]

    def create_role(name, work_actions, file_actions):
        permissions = [{"resourceType": "WORK", "resourceCode": "SYSTEM", "actionCode": action,
                        "dataScopeType": "ALL"} for action in work_actions]
        permissions += [{"resourceType": "FILE", "resourceCode": "OBJECT", "actionCode": action,
                         "dataScopeType": "ALL"} for action in file_actions]
        role = api("POST", "/api/admin/system/authorization/roles", {
            "name": name + " " + suffix, "permissions": permissions, "fieldPolicies": []}, admin)
        role = api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
            "expectedVersion": role["version"], "reason": "验证工作日志完整业务流程"}, admin)
        roles.append(role["id"])
        return role

    author_role = create_role("日志作者验收角色",
        ["VIEW", "CREATE_PROJECT", "MANAGE_PROJECT", "CREATE_TASK", "UPDATE_TASK", "CREATE_LOG", "UPDATE_LOG"],
        ["VIEW", "UPLOAD", "REFERENCE", "DOWNLOAD", "DELETE"])
    viewer_role = create_role("日志查看反馈角色", ["VIEW", "VIEW_OTHERS"], ["VIEW", "DOWNLOAD"])

    actors = []
    for index, (display, role_id) in enumerate([
            ("日志填写验收员", author_role["id"]), ("日志反馈验收员", viewer_role["id"])], 1):
        username = f"log_accept_{index}_{suffix}"
        password = secrets.token_urlsafe(24)
        registration = api("POST", "/api/auth/register", {
            "username": username, "password": password, "displayName": display,
            "systemName": f"日志验收临时系统 {index} {suffix}"})
        member = api("POST", "/api/admin/system/authorization/members", {
            "account": username, "departmentId": None, "roleIds": [role_id]}, admin)
        token = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                    registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]
        accounts.append({"id": member["accountId"], "username": username})
        actors.append({"token": token, "accountId": member["accountId"],
                       "tenantMemberId": member["tenantMemberId"], "displayName": display})
    author, viewer = actors

    due = (datetime.now() + timedelta(days=7)).replace(microsecond=0).isoformat()
    project = api("POST", "/api/work/projects", {
        "name": "日志验收项目 " + suffix, "description": "验证日志项目筛选和项目成员关联",
        "members": [{"tenantMemberId": viewer["tenantMemberId"], "projectRole": "MEMBER"}]}, author["token"])
    task = api("POST", f"/api/work/projects/{project['id']}/tasks", {
        "title": "完成工作日志业务验收", "description": "作为日报人工选材来源", "priority": "HIGH",
        "dueAt": due, "ownerTenantMemberId": author["tenantMemberId"],
        "collaboratorTenantMemberIds": [viewer["tenantMemberId"]], "followerTenantMemberIds": [],
        "acceptanceTenantMemberIds": [], "configuredValues": {}}, author["token"])
    candidates = api("GET", "/api/work/logs/materials", token=author["token"])
    task_candidate = next(item for item in candidates if item["materialType"] == "TASK" and item["materialId"] == str(task["id"]))
    operation_candidate = next(item for item in candidates if item["materialType"] == "BUSINESS_OPERATION")
    chosen = [{"materialType": item["materialType"], "materialId": item["materialId"]}
              for item in [task_candidate, operation_candidate]]

    log = api("POST", "/api/work/logs", log_payload(project["id"], task["id"], chosen, "SELF", [],
              "完成结构化日志后端与统一页面接入"), author["token"])
    log_id = log["id"]
    assert log["status"] == "DRAFT" and log["version"] == 0 and len(log["materials"]) == 2
    denied_status, denied = call("GET", f"/api/work/logs/{log_id}", token=viewer["token"])
    assert denied_status == 403 and denied.get("code") == "WORK_LOG_VISIBILITY_DENIED", (denied_status, denied)

    log = api("PUT", f"/api/work/logs/{log_id}", log_payload(project["id"], task["id"], chosen,
              "ROLE", [viewer_role["id"]], "完成结构化日志后端与统一页面接入",
              "开放给验收反馈角色", 0), author["token"])
    visible = api("GET", f"/api/work/logs/{log_id}", token=viewer["token"])
    assert visible["title"] == log["title"] and visible["visibilityType"] == "ROLE"
    facts["cases"].append("manual-structured-log-materials-and-visibility-denial-then-role-share")

    attachment = multipart_file(f"/api/business-attachments/work-logs/{log_id}", author["token"],
                                "日志验收说明.txt", "结构化日报附件验收".encode("utf-8"))
    assert attachment["scanStatus"] == "CLEAN" and attachment["downloadable"]
    log = api("POST", f"/api/work/logs/{log_id}/submit", {
        "expectedVersion": log["version"], "reason": "确认内容完整后提交"}, author["token"])
    assert log["status"] == "SUBMITTED" and log["submittedTime"]

    filtered = api("GET", f"/api/work/logs?status=SUBMITTED&projectId={project['id']}&keyword="
                   + urllib.parse.quote("结构化"),
                   token=author["token"])
    assert [item["id"] for item in filtered] == [log_id], filtered
    mutation_status, mutation = call("PUT", f"/api/work/logs/{log_id}",
        log_payload(project["id"], task["id"], chosen, "ROLE", [viewer_role["id"]],
                    "试图直接覆盖已提交正文", "非法覆盖", log["version"]), author["token"])
    assert mutation_status == 422 and mutation.get("code") == "WORK_LOG_SUBMITTED_IMMUTABLE"

    before_task_change = api("GET", f"/api/work/logs/{log_id}", token=author["token"])
    api("POST", f"/api/work/tasks/{task['id']}/progress", {
        "expectedVersion": task["version"], "progressPercent": 35, "actualHours": 2,
        "comment": "推进任务但不改写日报"}, author["token"])
    after_task_change = api("GET", f"/api/work/logs/{log_id}", token=author["token"])
    assert after_task_change["completedText"] == before_task_change["completedText"]
    assert after_task_change["revisions"] == before_task_change["revisions"]
    facts["cases"].append("explicit-submit-filter-lock-and-task-change-does-not-mutate-log")

    visible_list = api("GET", f"/api/work/logs?authorTenantMemberId={author['tenantMemberId']}", token=viewer["token"])
    assert [item["id"] for item in visible_list] == [log_id]
    visible = api("POST", f"/api/work/logs/{log_id}/comments", {
        "commentType": "FEEDBACK", "comment": "内容清晰，请按计划完成上线前回归"}, viewer["token"])
    assert visible["comments"][-1]["commentType"] == "FEEDBACK"
    denied_update_status, denied_update = call("PUT", f"/api/work/logs/{log_id}",
        log_payload(project["id"], task["id"], chosen, "ROLE", [viewer_role["id"]],
                    "越权修改", "越权", log["version"]), viewer["token"])
    assert denied_update_status == 403 and denied_update.get("code") == "PERMISSION_DENIED"
    facts["cases"].append("second-member-page-feedback-and-author-only-update")

    log = api("POST", f"/api/work/logs/{log_id}/withdraw", {
        "expectedVersion": log["version"], "reason": "补充验收结论"}, author["token"])
    log = api("PUT", f"/api/work/logs/{log_id}", log_payload(project["id"], task["id"], chosen,
        "ROLE", [viewer_role["id"]], "完成结构化日志、权限和附件验收", "补充验收结论", log["version"]), author["token"])
    log = api("POST", f"/api/work/logs/{log_id}/restore-draft", {
        "expectedVersion": log["version"], "reason": "恢复草稿确认最终内容"}, author["token"])
    log = api("POST", f"/api/work/logs/{log_id}/submit", {
        "expectedVersion": log["version"], "reason": "最终确认提交"}, author["token"])
    assert log["status"] == "SUBMITTED" and len(log["revisions"]) == 7

    rows = sql("SELECT status,visibility_type,JSON_LENGTH(visible_role_ids_json),submitted_time IS NOT NULL,"
               "completed_text,in_progress_text,next_plan_text,risk_text,assistance_text "
               f"FROM work_log WHERE id={log_id}")
    material_rows = sql("SELECT material_type,material_title FROM work_log_material "
                        f"WHERE work_log_id={log_id} ORDER BY material_type")
    comment_rows = sql("SELECT comment_type,comment_text FROM work_log_comment "
                       f"WHERE work_log_id={log_id}")
    attachment_rows = sql("SELECT owner_type,owner_id FROM file_reference "
                          f"WHERE owner_type='WORK_LOG' AND owner_id='{log_id}'")
    assert rows[0][0:4] == ["SUBMITTED", "ROLE", "1", "1"], rows
    assert len(material_rows) == 2 and comment_rows == [["FEEDBACK", "内容清晰，请按计划完成上线前回归"]]
    assert attachment_rows == [["WORK_LOG", str(log_id)]]
    facts["cases"].append("database-readback-versions-materials-comments-and-attachment")

    subprocess.run(["docker", "restart", "unexamine-golden-backend"], check=True, capture_output=True)
    for _ in range(40):
        try:
            if urllib.request.urlopen(BASE + "/actuator/health/readiness", timeout=2).status == 200:
                break
        except Exception:
            import time
            time.sleep(1)
    persisted = api("GET", f"/api/work/logs/{log_id}", token=author["token"])
    assert persisted["status"] == "SUBMITTED" and persisted["completedText"] == log["completedText"]
    assert len(persisted["revisions"]) == 7 and len(persisted["materials"]) == 2
    facts["cases"].append("existing-backend-restart-preserves-submitted-log")

    facts["fixtures"] = {"project_id": project["id"], "task_id": task["id"], "log_id": log_id,
        "attachment_id": attachment["attachmentId"], "actors": [
            {key: value for key, value in actor.items() if key != "token"} for actor in actors], "role_ids": roles}
    facts["status"] = "passed"
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    print(json.dumps({"status": "passed", "cases": len(facts["cases"]), "log": log_id}, ensure_ascii=False))
finally:
    if accounts:
        ids = ",".join(str(item["id"]) for item in accounts)
        names = ",".join("'" + item["username"].replace("'", "''") + "'" for item in accounts)
        role_ids = ",".join(str(value) for value in roles) or "0"
        sql(f"UPDATE sys_role SET status='DISABLED',version=version+1 WHERE id IN({role_ids}) AND status='ACTIVE'; "
            "UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id "
            f"SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id IN({ids}) AND tm.status='ACTIVE'; "
            f"UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id IN({ids}) AND status='ACTIVE'; "
            f"UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id IN({ids}) AND status='ACTIVE'; "
            f"UPDATE auth_session SET revoked=1,revoked_reason='work-log-acceptance-closed' WHERE account_id IN({ids}) AND revoked=0; "
            f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id IN({ids}) AND username IN({names}) AND status='ACTIVE'; "
            f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE creator_account_id IN({ids}) AND status='ACTIVE'")
        cleanup = sql("SELECT "
            f"(SELECT COUNT(*) FROM plat_account WHERE id IN({ids}) AND status='DISABLED'),"
            f"(SELECT COUNT(*) FROM sys_role WHERE id IN({role_ids}) AND status='DISABLED'),"
            f"(SELECT COUNT(*) FROM auth_session WHERE account_id IN({ids}) AND revoked=0),"
            f"(SELECT COUNT(*) FROM sys_system WHERE creator_account_id IN({ids}) AND status='ACTIVE')")
        facts["cleanup"] = {"accounts_disabled": int(cleanup[0][0]), "roles_disabled": int(cleanup[0][1]),
                            "active_sessions": int(cleanup[0][2]), "temporary_systems_active": int(cleanup[0][3]),
                            "business_evidence_retained": True}
    facts.setdefault("checked_time", datetime.now().astimezone().isoformat())
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
