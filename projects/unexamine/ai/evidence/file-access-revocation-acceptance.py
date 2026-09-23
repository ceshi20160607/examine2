"""B13.1 普通成员文件读取和同一会话撤权即时生效验收。"""
import ast
import json
import re
import secrets
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "file-access-revocation-facts.json"
fixture = json.loads((AREA / "file-business-attachment-facts.json").read_text(encoding="utf-8"))["fixtures"]
module_code, record_id, file_id = fixture["module_code"], fixture["record_id"], fixture["attachment_file_id"]

tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))


def download(token):
    import urllib.error
    import urllib.request
    request = urllib.request.Request(f"http://localhost:18080/api/files/{file_id}/download",
        headers={"Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


facts = {"step": "B13.1", "status": "running", "cases": [], "fixture": {
    "module_code": module_code, "record_id": record_id, "file_id": file_id}}
registration = member = role = admin = None
username = "file_revoke_" + secrets.token_hex(6)
password = secrets.token_urlsafe(24)

try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    admin = api("POST", "/api/systems/54/tenants/57/enter", {}, login["accessToken"])["tokens"]["accessToken"]
    registration = api("POST", "/api/auth/register", {"username": username, "password": password,
        "displayName": "文件撤权验收成员", "systemName": "文件撤权临时注册系统"})
    permissions = [{"resourceType": "MODULE", "resourceCode": module_code,
                    "actionCode": "DETAIL", "dataScopeType": "ALL"}] + [
        {"resourceType": "FILE", "resourceCode": "OBJECT", "actionCode": action, "dataScopeType": "ALL"}
        for action in ["VIEW", "DOWNLOAD"]]
    field_policies = [
        {"resourceCode": module_code, "fieldCode": code, "channel": channel,
         "readable": True, "writable": False, "maskStrategy": None}
        for code in ["subject", "required_file"] for channel in ["PAGE", "FILE"]]
    role = api("POST", "/api/admin/system/authorization/roles", {
        "name": "文件读取撤权验收", "permissions": permissions, "fieldPolicies": field_policies}, admin)
    role = api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": role["version"], "reason": "验证文件下载撤权即时生效"}, admin)
    member = api("POST", "/api/admin/system/authorization/members", {
        "account": username, "departmentId": None, "roleIds": [role["id"]]}, admin)
    actor = api("POST", "/api/systems/54/tenants/57/enter", {},
                registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]
    status, content = download(actor)
    assert status == 200 and content.startswith("替换后的真实业务附件".encode("utf-8"))
    current = next(item for item in api("GET", "/api/admin/system/authorization", token=admin)["roles"]
                   if item["id"] == role["id"])
    revoked_permissions = [item for item in permissions if item["actionCode"] != "DOWNLOAD"]
    draft = api("POST", "/api/admin/system/authorization/roles", {
        "id": current["id"], "code": current["code"], "name": current["name"],
        "description": current["description"], "expectedVersion": current["version"],
        "permissions": revoked_permissions, "fieldPolicies": field_policies}, admin)
    api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": draft["version"], "reason": "撤销文件下载权限"}, admin)
    status, denied = download(actor)
    assert status == 403 and denied["code"] == "FILE_PERMISSION_DENIED", denied
    facts["cases"].append("ordinary-member-download-works-then-same-session-is-denied-after-role-publish")
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
            "AND name='文件读取撤权验收'")
    if registration:
        sql("START TRANSACTION; "
            f"UPDATE auth_session SET revoked=1,revoked_reason='file-access-revocation-finished' WHERE account_id={registration['accountId']}; "
            f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={registration['accountId']} AND username='{username}'; "
            f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={registration['systemId']} "
            f"AND code='{registration['systemCode']}'; COMMIT")
        facts["cleanup"] = {"account_disabled": True, "sessions_revoked": True,
                            "registration_system_disabled": True, "temporary_role_disabled": True}
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(json.dumps({"status": facts["status"], "cases": len(facts["cases"]),
                  "cleanup": facts.get("cleanup")}, ensure_ascii=False))
