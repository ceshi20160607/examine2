"""B13.3 可视化打印模板、发布版本、真实 PDF 与当前权限验收。"""
import json
import re
import secrets
import subprocess
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "print-template-facts.json"
BASE = "http://127.0.0.1:18080"
SYSTEM_ID, TENANT_ID = 54, 57


def call(method, path, body=None, token=None):
    """调用本地项目接口；事实文件和异常均不记录访问凭证。"""
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, method=method, headers=headers,
        data=None if body is None else json.dumps(body, ensure_ascii=False).encode())
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}, dict(response.headers)
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else {}, dict(error.headers)


def api(method, path, body=None, token=None):
    status, result, _ = call(method, path, body, token)
    assert 200 <= status < 300, (path, status, result.get("code"), result.get("message"))
    return result["data"]


def binary(path, token):
    request = urllib.request.Request(BASE + path, headers={"Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            return response.status, response.read(), dict(response.headers)
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else {}, dict(error.headers)


def sql(statement):
    """读回验收事实；写入仅用于禁用本次随机临时身份和角色。"""
    result = subprocess.run(["docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"',
        "print-template", statement], capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, "打印验收数据库读回或临时身份收尾失败"
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def publish_module(module_id, token):
    check = api("GET", f"/api/admin/module-config/modules/{module_id}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/admin/module-config/modules/{module_id}/publish",
               {"expectedDraftRevision": check["draftRevision"]}, token)


def template_body(module_id, code, name, page_size, orientation, layout, expected_version=None):
    return {"moduleId": module_id, "code": code, "name": name, "pageSize": page_size,
            "orientation": orientation, "layout": layout, "expectedVersion": expected_version}


expected_cases = [
    "visual-layout-real-record-display-value-pagination-and-first-publication",
    "new-draft-does-not-replace-current-immutable-publication",
    "immutable-version-activation-and-snapshot-metadata",
    "ordinary-actor-field-intersection-real-pdf-and-job-owner-isolation",
    "same-session-print-revocation-invalidates-old-job-preview-and-download",
]

# 上次运行若只在最终汇总 SQL 失败，则直接重验已经完成的真实夹具，避免重复制造业务数据。
if FACTS.exists():
    previous = json.loads(FACTS.read_text(encoding="utf-8"))
    if (previous.get("status") == "running" and previous.get("cases") == expected_cases
            and all(previous.get("cleanup", {}).values())):
        recovered = sql("SELECT m.id,m.code,mp.current_version_id,r.id,t.id,t.code,"
            "p.current_version_id,j.id,j.output_file_id FROM cfg_module m "
            "JOIN cfg_module_publication mp ON mp.module_id=m.id "
            "JOIN biz_record r ON r.module_id=m.id AND r.tenant_id=m.owner_tenant_id "
            "JOIN print_template t ON t.module_id=m.id "
            "JOIN print_template_publication p ON p.template_id=t.id "
            "JOIN print_job j ON j.module_id=m.id "
            f"WHERE m.owner_tenant_id={TENANT_ID} AND m.code LIKE 'print_accept_%' "
            "ORDER BY m.id DESC,j.id DESC LIMIT 1")
        assert len(recovered) == 1, recovered
        module_id, recovered_code, module_version_id, record_id, template_id, recovered_template_code, current_version_id, job_id, output_file_id = recovered[0]
        versions = sql("SELECT id,version_number FROM print_template_version "
            f"WHERE template_id={int(template_id)} ORDER BY version_number")
        assert len(versions) == 2 and current_version_id == versions[1][0], versions
        template_state = sql("SELECT status,draft_revision,(SELECT COUNT(*) FROM print_job "
            f"WHERE template_version_id IN (SELECT id FROM print_template_version WHERE template_id={int(template_id)})) "
            f"FROM print_template WHERE id={int(template_id)}")
        assert template_state == [["PUBLISHED", "2", "1"]], template_state
        previous["status"] = "passed"
        previous["fixtures"] = {"module_id": int(module_id), "module_code": recovered_code,
            "module_version_id": int(module_version_id), "record_id": int(record_id),
            "template_id": int(template_id), "template_code": recovered_template_code,
            "version_v1_id": int(versions[0][0]), "version_v2_id": int(versions[1][0]),
            "current_version_id": int(current_version_id), "print_job_id": int(job_id),
            "output_file_id": int(output_file_id)}
        previous["checked_time"] = datetime.now().astimezone().isoformat()
        FACTS.write_text(json.dumps(previous, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"status": "passed", "cases": len(expected_cases),
                          "module": recovered_code, "template": int(template_id),
                          "job": int(job_id), "resumed": True}, ensure_ascii=False))
        raise SystemExit(0)


facts = {"step": "B13.3", "status": "running", "cases": [], "fixtures": {}, "cleanup": {}}
suffix = secrets.token_hex(5)
module_code = "print_accept_" + suffix
template_code = "print_order_" + suffix
username = "print_revoke_" + secrets.token_hex(6)
password = secrets.token_urlsafe(24)
registration = member = role = None

try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    admin_name = re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1)
    login = api("POST", "/api/auth/login", {"username": admin_name,
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    admin = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                login["accessToken"])["tokens"]["accessToken"]

    module = api("POST", "/api/admin/module-config/modules", {
        "code": module_code, "name": "可视化打印验收单", "groupName": "打印验收"}, admin)["module"]
    fields = [
        {"code": "subject", "name": "事项名称", "fieldType": "TEXT", "required": True,
         "uniqueValue": False, "searchable": True, "sortOrder": 10, "config": {}},
        {"code": "stage", "name": "处理阶段", "fieldType": "SINGLE_SELECT", "required": True,
         "uniqueValue": False, "searchable": True, "sortOrder": 20,
         "config": {"options": [{"value": "active", "label": "处理中"},
                                  {"value": "done", "label": "已完成"}]}},
        {"code": "amount", "name": "申请金额", "fieldType": "MONEY", "required": True,
         "uniqueValue": False, "searchable": False, "sortOrder": 30,
         "config": {"currency": "CNY", "precision": 2}},
        {"code": "applicant", "name": "申请说明", "fieldType": "TEXT", "required": False,
         "uniqueValue": False, "searchable": False, "sortOrder": 40, "config": {}},
        {"code": "detail_one", "name": "明细一", "fieldType": "TEXT", "required": False,
         "uniqueValue": False, "searchable": False, "sortOrder": 50, "config": {}},
        {"code": "detail_two", "name": "明细二", "fieldType": "TEXT", "required": False,
         "uniqueValue": False, "searchable": False, "sortOrder": 60, "config": {}},
        {"code": "restricted_note", "name": "受限备注", "fieldType": "TEXT", "required": False,
         "uniqueValue": False, "searchable": False, "sortOrder": 70, "config": {}}
    ]
    for field in fields:
        api("POST", f"/api/admin/module-config/modules/{module['id']}/fields", field, admin)
    publication = publish_module(module["id"], admin)
    record = api("POST", f"/api/runtime/modules/{module_code}/records", {
        "title": "打印验收申请单", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"subject": "办公设备采购", "stage": "active", "amount": 1268.50,
                   "applicant": "行政部申请", "detail_one": "显示器两台",
                   "detail_two": "键盘两套", "restricted_note": "不可输出的内部备注"}}, admin)

    layout_v1 = {"header": "星河公司 · 采购申请", "footer": "内部业务单据",
        "signatureLabel": "审批签章", "fieldCodes": ["subject", "stage", "amount", "applicant"],
        "detailFieldCodes": ["detail_one", "detail_two", "restricted_note"],
        "rowsPerPage": 4, "offsetXmm": 5, "offsetYmm": -3}
    created = api("POST", "/api/print/admin/templates", template_body(
        module["id"], template_code, "采购申请打印单 V1", "A4", "PORTRAIT", layout_v1), admin)
    preview_v1 = api("POST", f"/api/print/admin/templates/{created['id']}/preview",
        {"sampleRecordId": record["id"]}, admin)
    preview_fields = [field for page in preview_v1["pages"] for field in page["fields"]]
    stage = next(field for field in preview_fields if field["code"] == "stage")
    assert len(preview_v1["pages"]) == 2 and stage["value"] == "处理中", preview_v1
    version_v1 = api("POST", f"/api/print/admin/templates/{created['id']}/publish",
        {"expectedDraftRevision": created["draftRevision"]}, admin)
    runtime_v1 = api("GET", f"/api/print/runtime/modules/{module_code}/templates", token=admin)
    assert len(runtime_v1) == 1 and runtime_v1[0]["versionId"] == version_v1["versionId"]
    assert runtime_v1[0]["name"] == "采购申请打印单 V1" and runtime_v1[0]["layout"]["offsetXmm"] == 5
    facts["cases"].append("visual-layout-real-record-display-value-pagination-and-first-publication")

    overview = api("GET", "/api/print/admin", token=admin)
    current = next(item for item in overview["templates"] if item["id"] == created["id"])
    layout_v2 = dict(layout_v1, header="星河公司 · 采购申请（新版）", footer="新版打印页脚",
                     offsetXmm=-4, offsetYmm=6)
    draft_v2 = api("PUT", f"/api/print/admin/templates/{created['id']}", template_body(
        module["id"], template_code, "采购申请打印单 V2", "A5", "LANDSCAPE", layout_v2,
        current["version"]), admin)
    runtime_while_draft = api("GET", f"/api/print/runtime/modules/{module_code}/templates", token=admin)
    assert draft_v2["status"] == "DRAFT" and runtime_while_draft[0]["versionId"] == version_v1["versionId"]
    assert runtime_while_draft[0]["name"] == "采购申请打印单 V1"
    preview_v2 = api("POST", f"/api/print/admin/templates/{created['id']}/preview",
        {"sampleRecordId": record["id"]}, admin)
    version_v2 = api("POST", f"/api/print/admin/templates/{created['id']}/publish",
        {"expectedDraftRevision": draft_v2["draftRevision"]}, admin)
    assert preview_v2["draftRevision"] == draft_v2["draftRevision"]
    facts["cases"].append("new-draft-does-not-replace-current-immutable-publication")

    restored_v1 = api("POST", f"/api/print/admin/templates/{created['id']}/versions/{version_v1['versionId']}/activate",
        {"expectedCurrentVersionId": version_v2["versionId"]}, admin)
    restored_runtime = api("GET", f"/api/print/runtime/modules/{module_code}/templates", token=admin)[0]
    assert restored_v1["versionId"] == version_v1["versionId"]
    assert restored_runtime["versionId"] == version_v1["versionId"] and restored_runtime["name"].endswith("V1")
    api("POST", f"/api/print/admin/templates/{created['id']}/versions/{version_v2['versionId']}/activate",
        {"expectedCurrentVersionId": version_v1["versionId"]}, admin)
    current_runtime = api("GET", f"/api/print/runtime/modules/{module_code}/templates", token=admin)[0]
    assert current_runtime["versionId"] == version_v2["versionId"]
    assert current_runtime["pageSize"] == "A5" and current_runtime["orientation"] == "LANDSCAPE"
    assert current_runtime["layout"]["offsetXmm"] == -4 and current_runtime["layout"]["offsetYmm"] == 6
    facts["cases"].append("immutable-version-activation-and-snapshot-metadata")

    registration = api("POST", "/api/auth/register", {"username": username, "password": password,
        "displayName": "打印撤权验收成员", "systemName": "打印权限临时注册系统"})
    permissions = [
        {"resourceType": "MODULE", "resourceCode": module_code, "actionCode": action,
         "dataScopeType": "ALL"} for action in ["LIST", "DETAIL", "PRINT"]
    ] + [
        {"resourceType": "FILE", "resourceCode": "OBJECT", "actionCode": action,
         "dataScopeType": "ALL"} for action in ["VIEW", "DOWNLOAD"]
    ]
    visible_codes = ["subject", "stage", "amount", "applicant", "detail_one", "detail_two"]
    field_policies = [
        {"resourceCode": module_code, "fieldCode": code, "channel": channel,
         "readable": True, "writable": False, "maskStrategy": None}
        for code in visible_codes for channel in ["PAGE", "FILE"]
    ] + [{"resourceCode": module_code, "fieldCode": "restricted_note", "channel": "PAGE",
          "readable": True, "writable": False, "maskStrategy": None}]
    role = api("POST", "/api/admin/system/authorization/roles", {
        "name": "打印当前权限验收", "permissions": permissions, "fieldPolicies": field_policies}, admin)
    role = api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": role["version"], "reason": "验证打印当前权限和字段交集"}, admin)
    member = api("POST", "/api/admin/system/authorization/members", {
        "account": username, "departmentId": None, "roleIds": [role["id"]]}, admin)
    actor = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]

    actor_templates = api("GET", f"/api/print/runtime/modules/{module_code}/templates", token=actor)
    assert actor_templates[0]["versionId"] == version_v2["versionId"]
    job = api("POST", f"/api/print/runtime/modules/{module_code}/records/{record['id']}/templates/{created['id']}",
              {"templateVersionId": None}, actor)
    job_fields = [field for page in job["pages"] for field in page["fields"]]
    assert job["status"] == "SUCCEEDED" and job["pageCount"] == 2
    assert next(field for field in job_fields if field["code"] == "stage")["value"] == "处理中"
    assert "restricted_note" not in {field["code"] for field in job_fields}
    assert job["authorizationSnapshot"]["omittedFieldCodes"] == ["restricted_note"]
    status, pdf, headers = binary(job["downloadPath"], actor)
    assert status == 200 and pdf.startswith(b"%PDF-") and len(pdf) > 1500
    assert "application/pdf" in headers.get("Content-Type", "")
    admin_status, admin_job, _ = call("GET", f"/api/print/jobs/{job['id']}", token=admin)
    assert admin_status == 404 and admin_job["code"] == "PRINT_JOB_NOT_FOUND"
    facts["cases"].append("ordinary-actor-field-intersection-real-pdf-and-job-owner-isolation")

    current_role = next(item for item in api("GET", "/api/admin/system/authorization", token=admin)["roles"]
                        if item["id"] == role["id"])
    revoked = [item for item in permissions if not (
        item["resourceType"] == "MODULE" and item["actionCode"] == "PRINT")]
    draft_role = api("POST", "/api/admin/system/authorization/roles", {
        "id": current_role["id"], "code": current_role["code"], "name": current_role["name"],
        "description": current_role["description"], "expectedVersion": current_role["version"],
        "permissions": revoked, "fieldPolicies": field_policies}, admin)
    api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": draft_role["version"], "reason": "撤销打印权限并验证旧链接"}, admin)
    for path in [f"/api/print/jobs/{job['id']}", job["previewPath"], job["downloadPath"]]:
        denied_status, denied, _ = call("GET", path, token=actor)
        assert denied_status == 403 and denied["code"] == "PERMISSION_DENIED", (path, denied_status, denied)
    detail = api("GET", f"/api/runtime/modules/{module_code}/records/{record['id']}", token=actor)
    assert detail["id"] == record["id"]
    facts["cases"].append("same-session-print-revocation-invalidates-old-job-preview-and-download")

    db_rows = sql("SELECT t.id,t.status,t.draft_revision,p.current_version_id,"
        "(SELECT COUNT(*) FROM print_template_version v WHERE v.template_id=t.id),"
        "(SELECT COUNT(*) FROM print_job j WHERE j.template_version_id IN "
        "(SELECT v2.id FROM print_template_version v2 WHERE v2.template_id=t.id)) "
        "FROM print_template t JOIN print_template_publication p ON p.template_id=t.id "
        f"WHERE t.id={created['id']}")
    assert db_rows == [[str(created["id"]), "PUBLISHED", str(draft_v2["draftRevision"]),
                        str(version_v2["versionId"]), "2", "1"]], db_rows
    facts["fixtures"] = {"module_id": module["id"], "module_code": module_code,
        "module_version_id": publication["versionId"], "record_id": record["id"],
        "template_id": created["id"], "template_code": template_code,
        "version_v1_id": version_v1["versionId"], "version_v2_id": version_v2["versionId"],
        "current_version_id": version_v2["versionId"], "print_job_id": job["id"],
        "output_file_id": job["outputFileId"]}
    facts["status"] = "passed"
finally:
    if member and registration:
        try:
            api("POST", f"/api/admin/system/authorization/members/{member['tenantMemberId']}/assignment", {
                "departmentId": None, "roleIds": [], "expectedVersion": member["version"]}, admin)
        except Exception:
            pass
    if role:
        sql(f"UPDATE sys_role SET status='DISABLED',version=version+1 WHERE id={int(role['id'])} "
            "AND name='打印当前权限验收'")
    if registration:
        safe_username = username.replace("'", "''")
        sql("START TRANSACTION; "
            f"UPDATE auth_session SET revoked=1,revoked_reason='print-template-finished' WHERE account_id={int(registration['accountId'])}; "
            f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={int(registration['accountId'])} AND username='{safe_username}'; "
            f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={int(registration['systemId'])} "
            f"AND code='{registration['systemCode']}'; COMMIT")
        facts["cleanup"] = {"account_disabled": True, "sessions_revoked": True,
                            "registration_system_disabled": True, "temporary_role_disabled": True}
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(json.dumps({"status": facts["status"], "cases": len(facts["cases"]),
                  "module": facts["fixtures"].get("module_code"),
                  "template": facts["fixtures"].get("template_id"),
                  "job": facts["fixtures"].get("print_job_id")}, ensure_ascii=False))
