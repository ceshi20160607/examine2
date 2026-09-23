"""定向复验：目标详情不足不阻断父记录编辑，也不能手输 ID 扩大关联权限。"""
import ast
import json
import re
import secrets
from pathlib import Path

AREA = Path(__file__).parent
helpers = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[n for n in helpers.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {"call", "api", "sql"}], type_ignores=[]), "helpers", "exec"))
path = AREA / "shared-catalog-facts.json"
facts = json.loads(path.read_text(encoding="utf-8"))
assert "target_permission_supplement" not in facts, "不重复执行已通过定向验收"
initializer = Path("projects/unexamine/backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
login = api("POST", "/api/auth/login", {"username": re.search(r"default-admin\.username:([^}]+)", initializer).group(1),
    "password": re.search(r"default-admin\.initial-password:([^}]+)", initializer).group(1)})
admin = api("POST", "/api/systems/54/tenants/57/enter", {}, login["accessToken"])["tokens"]["accessToken"]
username = "shared_catalog_permission_" + secrets.token_hex(6)
registration = api("POST", "/api/auth/register", {"username": username, "password": secrets.token_urlsafe(24),
    "displayName": "目标权限独立复验", "systemName": "来源目录目标权限临时系统"})
facts["registrations"].append({"account_id": registration["accountId"], "system_id": registration["systemId"], "username": username})
path.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
member = api("POST", "/api/admin/system/authorization/members", {"account": username,
    "roleIds": [facts["role_cases"]["no_target_detail"]]}, admin)
token = api("POST", "/api/systems/54/tenants/57/enter", {}, registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]
base = f"/api/runtime/modules/{facts['module_code']}/records/{facts['record_id']}"
context = api("GET", base + "/edit-context", token=token)
assert api("GET", base + "/fields/related/reference-options", token=token) == []
record = context["record"]
body = {"title": record["title"], "recordNumber": record["recordNumber"], "status": record["status"],
    "ownerMemberId": record["ownerMemberId"], "departmentId": record["departmentId"], "participantMemberIds": record["participantMemberIds"],
    "version": record["version"], "fields": {**record["fields"], "subject": "目标无详情仍可编辑父字段"}}
before = sql(f"SELECT field_code,HEX(COALESCE(value_text,CAST(value_reference_id AS CHAR),value_json)) FROM biz_record_value WHERE record_id={facts['record_id']} ORDER BY field_code")
status, response = call("PUT", base, {**body, "fields": {**body["fields"], "related": facts["private_target_record_id"]}}, token)
assert (status, response.get("code")) == (422, "FIELD_VALIDATION_FAILED")
assert before == sql(f"SELECT field_code,HEX(COALESCE(value_text,CAST(value_reference_id AS CHAR),value_json)) FROM biz_record_value WHERE record_id={facts['record_id']} ORDER BY field_code")
updated = api("PUT", base, body, token)
assert updated["fields"]["related"] == record["fields"]["related"] and updated["version"] == record["version"] + 1
for case in facts["cases"]:
    if case["case"] == "no_target_detail":
        case["status"] = "superseded_behavior_after_target_permission_fix"
        case["note"] = "Prior API403 blocked unrelated form edit; directed current API returns no choices, preserving original reference and enforcing changed-reference authorization."
facts["target_permission_supplement"] = {"account_id": registration["accountId"], "target_choices": [],
    "changed_private_reference_denial": {"status": status, "code": response["code"]}, "denied_values_unchanged": True,
    "parent_unrelated_edit": {"version": updated["version"], "related": updated["fields"]["related"]}, "source_memberships": 0}
assert int(sql(f"SELECT COUNT(*) FROM sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id WHERE sm.account_id={registration['accountId']} AND tm.tenant_id={facts['source_tenant']['id']}")[0][0]) == 0
path.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(facts["target_permission_supplement"]))
