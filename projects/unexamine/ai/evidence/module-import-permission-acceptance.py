"""B13.2 普通成员导入批次隔离与同一会话撤权验收。"""
import ast
import hashlib
import io
import json
import re
import secrets
import time
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "module-import-permission-facts.json"
fixture = json.loads((AREA / "module-import-export-facts.json").read_text(encoding="utf-8"))["fixtures"]
module_code = fixture["module_code"]
admin_batch_id = fixture["import_batch_id"]

tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))

facts = {"step": "B13.2", "status": "running", "cases": [], "cleanup": {}}
registration = member = role = admin = None
username = "exchange_revoke_" + secrets.token_hex(6)
password = secrets.token_urlsafe(24)

try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    admin = api("POST", "/api/systems/54/tenants/57/enter", {}, login["accessToken"])["tokens"]["accessToken"]
    registration = api("POST", "/api/auth/register", {"username": username, "password": password,
        "displayName": "导入权限撤销验收成员", "systemName": "导入权限临时注册系统"})
    permissions = [
        {"resourceType": "MODULE", "resourceCode": module_code, "actionCode": action, "dataScopeType": "ALL"}
        for action in ["LIST", "DETAIL", "CREATE", "UPDATE", "IMPORT", "EXPORT"]
    ] + [
        {"resourceType": "FILE", "resourceCode": "OBJECT", "actionCode": action, "dataScopeType": "ALL"}
        for action in ["VIEW", "UPLOAD", "REFERENCE", "DOWNLOAD"]
    ]
    field_policies = [
        {"resourceCode": module_code, "fieldCode": code, "channel": channel,
         "readable": True, "writable": True,
         "maskStrategy": "FULL" if code == "note" and channel == "FILE" else None}
        for code in ["external_code", "note", "amount", "stage"] for channel in ["PAGE", "FILE"]
    ]
    role = api("POST", "/api/admin/system/authorization/roles", {
        "name": "模块导入撤权验收", "permissions": permissions, "fieldPolicies": field_policies}, admin)
    role = api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": role["version"], "reason": "验证导入批次隔离与撤权"}, admin)
    member = api("POST", "/api/admin/system/authorization/members", {
        "account": username, "departmentId": None, "roleIds": [role["id"]]}, admin)
    actor = api("POST", "/api/systems/54/tenants/57/enter", {},
                registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]

    export_body = {"lifecycleState": "ACTIVE", "tenantScope": "OWN", "search": "",
        "filters": [{"fieldCode": "external_code", "operator": "EQ", "value": "EX-A"}],
        "sortField": "updatedAt", "sortDirection": "DESC", "selectedRecordIds": [],
        "format": "XLSX", "idempotencyKey": "b13-masked-export-" + secrets.token_hex(6),
        "selectedFields": ["title", "external_code", "note"]}
    export_batch = api("POST", f"/api/runtime/modules/{module_code}/exports", export_body, actor)
    for _ in range(80):
        export_batch = api("GET", f"/api/runtime/modules/{module_code}/exports/{export_batch['id']}",
                           token=actor)
        if export_batch["status"] in {"COMPLETED", "FAILED"}:
            break
        time.sleep(.25)
    assert export_batch["status"] == "COMPLETED", export_batch
    download = urllib.request.Request(
        f"http://localhost:18080/api/runtime/modules/{module_code}/exports/{export_batch['id']}/download",
        headers={"Authorization": "Bearer " + actor})
    with urllib.request.urlopen(download, timeout=25) as response:
        exported_bytes = response.read()
    with zipfile.ZipFile(io.BytesIO(exported_bytes)) as archive:
        export_text = "".join(archive.read(name).decode("utf-8", "ignore")
                              for name in archive.namelist() if name.endswith(".xml"))
    assert "***" in export_text and "导入后人工修改，回滚不得覆盖" not in export_text
    facts["cases"].append("file-channel-mask-is-applied-in-generated-excel")

    content = ("标题,外部唯一编码,金额,阶段\n普通成员待执行,EX-PERM-" + secrets.token_hex(3) +
               ",9.90,进行中\n").encode("utf-8")
    session = api("POST", "/api/files/uploads", {"originalName": "permission-import.csv",
        "contentType": "text/csv", "expectedSize": len(content),
        "expectedSha256": hashlib.sha256(content).hexdigest()}, actor)
    request = urllib.request.Request(f"http://localhost:18080/api/files/uploads/{session['id']}/content",
        method="PUT", headers={"Authorization": "Bearer " + actor, "Content-Type": "application/octet-stream",
            "X-Upload-Token": session["uploadToken"]}, data=content)
    with urllib.request.urlopen(request, timeout=25) as response:
        source = json.load(response)["data"]
    source = api("POST", f"/api/files/{source['id']}/references", {"ownerType": "ACCOUNT",
        "ownerId": str(registration["accountId"]), "fieldCode": "managed_files",
        "referenceType": "DOCUMENT"}, actor)
    batch = api("POST", f"/api/runtime/modules/{module_code}/imports/preview", {
        "sourceFileId": source["id"], "columnMapping": {"标题": "title", "外部唯一编码": "external_code",
        "金额": "amount", "阶段": "stage"}, "conflictPolicy": "UPDATE",
        "matchFieldCode": "external_code", "mappingName": "普通成员字段映射"}, actor)
    assert batch["status"] == "PREVIEWED" and batch["validRows"] == 1

    status, denied_admin = call("GET", f"/api/runtime/modules/{module_code}/imports/{batch['id']}", token=admin)
    assert status == 404 and denied_admin["code"] == "IMPORT_BATCH_NOT_FOUND", denied_admin
    status, denied_actor = call("GET", f"/api/runtime/modules/{module_code}/imports/{admin_batch_id}", token=actor)
    assert status == 404 and denied_actor["code"] == "IMPORT_BATCH_NOT_FOUND", denied_actor
    facts["cases"].append("same-tenant-import-batches-and-raw-rows-are-owner-isolated")

    current = next(item for item in api("GET", "/api/admin/system/authorization", token=admin)["roles"]
                   if item["id"] == role["id"])
    revoked = [item for item in permissions if not (
        item["resourceType"] == "MODULE" and item["actionCode"] == "IMPORT")]
    draft = api("POST", "/api/admin/system/authorization/roles", {
        "id": current["id"], "code": current["code"], "name": current["name"],
        "description": current["description"], "expectedVersion": current["version"],
        "permissions": revoked, "fieldPolicies": field_policies}, admin)
    api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": draft["version"], "reason": "撤销模块导入权限"}, admin)
    status, denied_execute = call("POST", f"/api/runtime/modules/{module_code}/imports/{batch['id']}/execute",
                                  {"version": batch["version"]}, actor)
    assert status == 403 and denied_execute["code"] == "IMPORT_PERMISSION_DENIED", denied_execute
    assert sql(f"SELECT status,job_id FROM exchange_import_batch WHERE id={batch['id']}") == [["PREVIEWED", "NULL"]]
    facts["cases"].append("same-session-import-execute-is-denied-after-role-republish")
    export_after_import_revoke = api(
        "POST", f"/api/runtime/modules/{module_code}/exports/estimate", export_body, actor)
    assert export_after_import_revoke["estimatedRows"] == 1, export_after_import_revoke
    facts["cases"].append("import-revoke-does-not-remove-independent-export-action")
    facts["fixture"] = {"batch_id": batch["id"], "source_file_id": source["id"],
                        "masked_export_batch_id": export_batch["id"]}
    facts["status"] = "passed"
finally:
    if member and admin:
        try:
            api("POST", f"/api/admin/system/authorization/members/{member['tenantMemberId']}/assignment", {
                "departmentId": None, "roleIds": [], "expectedVersion": member["version"]}, admin)
        except Exception:
            pass
    if role:
        sql(f"UPDATE sys_role SET status='DISABLED',version=version+1 WHERE id={int(role['id'])} "
            "AND name='模块导入撤权验收'")
    if registration:
        sql("START TRANSACTION; "
            f"UPDATE auth_session SET revoked=1,revoked_reason='module-import-permission-finished' WHERE account_id={registration['accountId']}; "
            f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={registration['accountId']} AND username='{username}'; "
            f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={registration['systemId']} "
            f"AND code='{registration['systemCode']}'; COMMIT")
        facts["cleanup"] = {"account_disabled": True, "sessions_revoked": True,
                            "registration_system_disabled": True, "temporary_role_disabled": True}
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(json.dumps({"status": facts["status"], "cases": len(facts["cases"]),
                  "cleanup": facts.get("cleanup")}, ensure_ascii=False))
