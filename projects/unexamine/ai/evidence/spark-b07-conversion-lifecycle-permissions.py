"""本地临时普通账号验证转化关系硬权限；凭证只在内存，最终禁用该测试账号。"""
import base64
import hashlib
import json
import runpy
import secrets
import subprocess
import sys
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
evidence_path = AREA / "spark-b07-conversion-lifecycle-permissions.json"
assert not evidence_path.exists(), "本次权限验收已执行，不重复建立账号"
sys.argv = ["helper", "inspect"]
s = runpy.run_path(str(AREA / "spark-b07-conversion-acceptance.py"))
api, call, rows, save = [s[n] for n in ["api", "call", "rows", "save"]]
SOURCE, TARGET = s["SOURCE"], s["TARGET"]
fixture = json.loads((AREA / "spark-b07-conversion-lifecycle-fixtures.json").read_text(encoding="utf-8"))["conflict"]
username, password = "conversion_permission_" + secrets.token_hex(6), secrets.token_urlsafe(24)
salt = secrets.token_bytes(16)
encode = lambda value: base64.urlsafe_b64encode(value).decode().rstrip("=")
password_hash = "$".join(["pbkdf2-sha256", "210000", encode(salt),
    encode(hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 210000))])


def sql_write(sql):
    # 仅用于有唯一随机用户名的本地测试账号种子和最终禁用，不触及已有账号或不可变审计。
    result = subprocess.run(["docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"', "fixture", sql],
        capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, "本地权限测试种子/禁用失败；不输出包含凭证的 SQL"
    return result.stdout.strip()


account_id = int(sql_write("START TRANSACTION; INSERT INTO plat_account(username,display_name,status) "
    f"VALUES('{username}','转化权限本地验收','ACTIVE'); SET @test_conversion_account=LAST_INSERT_ID(); "
    "INSERT INTO plat_account_credential(account_id,password_hash,password_algorithm) "
    f"VALUES(@test_conversion_account,'{password_hash}','PBKDF2_SHA256'); COMMIT; SELECT @test_conversion_account;"))
roles = []
member = None
facts = {"checked_at": datetime.now().astimezone().isoformat(), "account_id": account_id,
         "source_id": fixture["source_id"], "cases": [], "role_ids": roles}
body = {"conversionId": fixture["conversion_id"], "version": 1, "conversionVersion": 1,
        "reason": "本地权限拒绝验收，不应执行。"}
url = f"/api/runtime/modules/{SOURCE}/records/{fixture['source_id']}"
before = rows("SELECT status,version FROM biz_record_conversion WHERE id=" + str(fixture["conversion_id"]))
try:
    for name, grants in [
        ("no_source_convert", [(SOURCE, "DETAIL"), (TARGET, "DETAIL")]),
        ("no_target_detail", [(SOURCE, "DETAIL"), (SOURCE, "CONVERT")])]:
        role = api("POST", "/api/admin/system/authorization/roles", {"name": "转化关系权限验收 " + name,
            "permissions": [{"resourceType": "MODULE", "resourceCode": module, "actionCode": action,
                             "dataScopeType": "ALL"} for module, action in grants], "fieldPolicies": []})
        published = api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish",
                        {"expectedVersion": role["version"], "reason": "本地转化关系硬权限验收。"})
        roles.append(role["id"])
        if member is None:
            member = api("POST", "/api/admin/system/authorization/members", {"account": username,
                         "departmentId": 3, "roleIds": [role["id"]]})
        else:
            member = api("POST", f"/api/admin/system/authorization/members/{member['tenantMemberId']}/assignment",
                {"departmentId": 3, "roleIds": [role["id"]], "expectedVersion": member["version"]})
        status, login = call("POST", "/api/auth/login", {"username": username, "password": password})
        assert status == 200
        status, entry = call("POST", "/api/systems/19/enter", {}, login["data"]["accessToken"])
        assert status == 200
        token = entry["data"]["tokens"]["accessToken"]
        status, links = call("GET", url + "/conversions", token=token)
        assert status == 200
        if name == "no_source_convert":
            assert len(links["data"]["links"]) == 1 and not links["data"]["links"][0]["canUnlink"]
        else:
            assert links["data"]["links"] == []
        status, denied = call("POST", url + "/conversions/unlink", body, token,
                              {"Prefer": "return=action", "Idempotency-Key": secrets.token_hex(16)})
        assert status == 403, (name, status, denied.get("code"))
        assert rows("SELECT status,version FROM biz_record_conversion WHERE id=" + str(fixture["conversion_id"])) == before
        facts["cases"].append({"case": name, "http": status, "code": denied["code"],
                               "returned_links": len(links["data"]["links"]), "no_write": True})
finally:
    if member:
        api("POST", f"/api/admin/system/authorization/members/{member['tenantMemberId']}/assignment",
            {"departmentId": 3, "roleIds": [], "expectedVersion": member["version"]})
    # 保留账号/授权/审计历史；撤销会话和禁用的双重条件只命中本次临时账号。
    sql_write(f"START TRANSACTION; UPDATE auth_session SET revoked=1,revoked_reason='local-conversion-acceptance-finished' "
        f"WHERE account_id={account_id}; UPDATE plat_account SET status='DISABLED',version=version+1 "
        f"WHERE id={account_id} AND username='{username}'; COMMIT;")
    facts["cleanup"] = {"disabled_account": rows(f"SELECT status FROM plat_account WHERE id={account_id}") == [["DISABLED"]],
        "active_sessions": int(rows(f"SELECT COUNT(*) FROM auth_session WHERE account_id={account_id} AND revoked=0")[0][0]),
        "member_roles_removed": member is not None, "audit_preserved": True}
    save(evidence_path.name, facts)
assert len(facts["cases"]) == 2 and facts["cleanup"]["disabled_account"] and not facts["cleanup"]["active_sessions"]
print("Real ordinary-account source CONVERT/target DETAIL denials passed; local test account disabled and sessions revoked.")
