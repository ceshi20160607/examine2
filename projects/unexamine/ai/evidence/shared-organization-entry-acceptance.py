"""补验默认入口的失效租户成员边界；不重跑已通过的共享字段用例。"""
import ast
import json
import re
import secrets
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
JAVA = AREA.parents[1] / "backend/src/main/java/com/unique/unexamine"
helpers = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[n for n in helpers.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {"call", "api", "sql"}], type_ignores=[]), "helpers", "exec"))
path = AREA / "shared-organization-system-entry.json"
facts = json.loads(path.read_text(encoding="utf-8"))
assert "inactive_membership_test" not in facts, "已有真实验收，不重复创建夹具"
registration = None
username = "shared_org_entry_" + secrets.token_hex(6)
try:
    initializer = (JAVA / "platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {"username": re.search(r"default-admin\.username:([^}]+)", initializer).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", initializer).group(1)})
    admin = api("POST", "/api/systems/54/tenants/57/enter", {}, login["accessToken"])["tokens"]["accessToken"]
    registration = api("POST", "/api/auth/register", {"username": username, "password": secrets.token_urlsafe(24),
        "displayName": "默认入口失效成员验收", "systemName": "默认入口临时验收系统"})
    member = api("POST", "/api/admin/system/authorization/members", {"account": username, "roleIds": []}, admin)
    token = registration["platformTokens"]["accessToken"]
    systems = api("GET", "/api/systems", token=token)
    visible = next(row for row in systems if row["systemId"] == 54)
    assert visible["defaultTenantId"] == 57, visible
    entry = api("POST", "/api/systems/54/enter", {}, token)
    assert entry["tenantId"] == 57, entry["tenantId"]
    account = int(registration["accountId"])
    tm = int(member["tenantMemberId"])
    # 仅停用本次随机账号的确切租户成员来构造硬限制；不宣称组织停用业务已完成。
    sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id JOIN plat_account a ON a.id=sm.account_id "
        f"SET tm.status='DISABLED',tm.version=tm.version+1 WHERE tm.id={tm} AND tm.tenant_id=57 AND sm.system_id=54 "
        f"AND a.id={account} AND a.username='{username}' AND tm.status='ACTIVE'")
    assert all(row["systemId"] != 54 for row in api("GET", "/api/systems", token=token))
    denied = []
    for url in ["/api/systems/54/enter", "/api/systems/54/tenants/56/enter"]:
        status, response = call("POST", url, {}, token)
        assert (status, response.get("code")) == (403, "TENANT_ACCESS_DENIED"), (url, status, response.get("code"))
        denied.append({"path": url, "status": status, "code": response["code"]})
    source_memberships = int(sql(f"SELECT COUNT(*) FROM sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id "
        f"WHERE sm.account_id={account} AND sm.system_id=54 AND tm.tenant_id=56")[0][0])
    assert source_memberships == 0
    facts["inactive_membership_test"] = {"account_id": account, "username": username, "tenant_member_id": tm,
        "normal_default_tenant": entry["tenantId"], "inactive_excluded_from_systems": True, "denied": denied,
        "source_membership_count": source_memberships,
        "fixture_inactivation": "Guarded SQL for this random tenant membership only; organization lifecycle is not claimed complete",
        "verified_time": datetime.now().astimezone().isoformat()}
finally:
    if registration:
        account, system = int(registration["accountId"]), int(registration["systemId"])
        sql(f"DELETE mr FROM sys_member_role mr JOIN sys_tenant_member tm ON tm.id=mr.tenant_member_id JOIN sys_member sm ON sm.id=tm.system_member_id "
            f"WHERE sm.account_id={account} AND sm.system_id=54; "
            f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 "
            f"WHERE sm.account_id={account} AND sm.system_id=54 AND tm.status='ACTIVE'; "
            f"UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={account} AND system_id=54 AND status='ACTIVE'; "
            f"UPDATE auth_session SET revoked=1,revoked_reason='shared-entry-acceptance-closed' WHERE account_id={account}; "
            f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account} AND username='{username}'; "
            f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={system} AND creator_account_id={account}")
        facts.setdefault("inactive_membership_test", {"account_id": account, "failed": True})["cleanup"] = "synthetic identity, sessions and own empty system disabled"
        path.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(facts.get("inactive_membership_test"), ensure_ascii=True))
