"""复用正式扩展151，检查来源展示与保护；只创建一个短期接口身份，凭证仅在内存。"""
import ast
import copy
import json
import re
import secrets
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {"call", "api", "sql"}], type_ignores=[]), "helpers", "exec"))
fixture = json.loads((AREA / "tenant-extension-facts.json").read_text(encoding="utf-8"))
code, target_code = fixture["module_code"], fixture["target_code"]
base = f"/api/runtime/modules/{code}/records/151"
path = AREA / "shared-presentation-facts.json"
assert not path.exists(), "已有事实，不重复执行已通过场景；先检查断点"
facts = {"step": "B07.4b.2c.2b.1", "status": "running", "record_id": 151, "cases": []}
registration = None


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    path.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def enter():
    return api("POST", "/api/systems/54/tenants/57/enter", {}, registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]


def set_role(role):
    current = next(r for r in api("GET", "/api/admin/system/authorization", token=admins[57])["roles"] if r["id"] == 114)
    draft = api("POST", "/api/admin/system/authorization/roles", {"id": 114, "code": current["code"], "name": current["name"],
        "description": current["description"], "expectedVersion": current["version"], "permissions": role["permissions"],
        "fieldPolicies": role["fieldPolicies"]}, admins[57])
    api("POST", "/api/admin/system/authorization/roles/114/publish", {"expectedVersion": draft["version"],
        "reason": "仅本地随机验收角色定向保护验证"}, admins[57])


def row(token):
    return next(r for r in api("GET", f"/api/runtime/modules/{code}/records", token=token)["records"] if r["id"] == 151)


def labels(record):
    p = record["fieldPresentation"]
    assert p["choice"]["name"].startswith("来源扩展") and p["choice"]["displayValue"] == "来源选项2"
    assert p["city"]["displayValue"] == "来源省2"
    assert p["related"]["displayValue"] == "正式扩展来源关联目标"
    assert "hidden" not in record["fields"] and "hidden" not in p
    return p


def change_reference(value):
    ctx = api("GET", base + "/edit-context", token=admins[77])["record"]
    body = {k: ctx[k] for k in ["title", "status", "ownerMemberId", "departmentId", "participantMemberIds", "version", "fields"]}
    body["fields"]["related"] = value
    return api("PUT", base, body, admins[77])


try:
    java = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {"username": re.search(r"default-admin\.username:([^}]+)", java).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", java).group(1)})
    admins = {t: api("POST", f"/api/systems/54/tenants/{t}/enter", {}, login["accessToken"])["tokens"]["accessToken"] for t in [57, 77]}
    original = next(r for r in api("GET", "/api/admin/system/authorization", token=admins[57])["roles"] if r["id"] == 114)
    facts["original_role"] = original
    username = "presentation_reader_" + secrets.token_hex(6)
    registration = api("POST", "/api/auth/register", {"username": username, "password": secrets.token_urlsafe(24),
        "displayName": "来源显示接口验收者", "systemName": "来源显示临时空系统"})
    facts["identity"] = {"account_id": registration["accountId"], "system_id": registration["systemId"], "username": username}
    save()
    api("POST", "/api/admin/system/authorization/members", {"account": username, "roleIds": [114]}, admins[57])
    actor = enter()
    readable = row(actor)
    labels(readable)
    assert sql(f"SELECT COUNT(*) FROM sys_tenant_member tm JOIN sys_member m ON m.id=tm.system_member_id WHERE m.account_id={registration['accountId']} AND m.system_id=54 AND tm.tenant_id=77") == [["0"]]
    facts["cases"].append({"case": "ordinary_LIST_source_names_and_bound_labels_no_source_membership", "record": readable})
    assert call("GET", base, token=actor)[0] == 403
    read_role = copy.deepcopy(original)
    read_role["permissions"].append({"resourceType": "MODULE", "resourceCode": code, "actionCode": "DETAIL", "dataScopeType": "ALL"})
    set_role(read_role)
    actor = enter()
    labels(api("GET", base, token=actor))
    own = api("GET", base, token=admins[77])
    assert own["fieldPresentation"]["choice"]["displayValue"] == "来源选项2" and "hidden" in own["fieldPresentation"]
    facts["cases"].append({"case": "DETAIL_source_presentation_and_own_tenant_display", "detail": api("GET", base, token=actor)})
    ctx = api("GET", base + "/edit-context", token=actor)["record"]
    body = {k: ctx[k] for k in ["title", "status", "ownerMemberId", "departmentId", "participantMemberIds", "version", "fields"]}
    key = "presentation_replay_" + secrets.token_hex(8)
    action = api("PUT", base, body, actor, key)
    facts["action_id"] = action["idempotencyRecordId"]
    masked = copy.deepcopy(read_role)
    for policy in masked["fieldPolicies"]:
        if policy["resourceCode"] == code and policy["fieldCode"] in ["choice", "city", "related"]:
            policy["maskStrategy"] = "FULL"
            policy["writable"] = False
    set_role(masked)
    actor = enter()
    record = row(actor)
    for field in ["choice", "city", "related"]:
        assert record["fields"][field] == "***" and record["fieldPresentation"][field]["displayValue"] == "***"
    replay = api("PUT", base, body, actor, key)
    assert replay["replayed"]
    for field in ["choice", "city", "related"]:
        assert replay["value"]["fieldPresentation"][field]["displayValue"] == "***"
    assert "hidden" not in replay["value"]["fieldPresentation"]
    facts["cases"].append({"case": "mask_and_replay_never_restore_dictionary_reference_values", "record": record, "replay": replay["value"]})
    no_target = copy.deepcopy(read_role)
    no_target["permissions"] = [p for p in no_target["permissions"] if not (p["resourceCode"] == target_code and p["actionCode"] == "DETAIL")]
    set_role(no_target)
    actor = enter()
    assert row(actor)["fieldPresentation"]["related"]["displayValue"] == "无权查看关联记录"
    facts["cases"].append({"case": "target_without_DETAIL_parent_remains_readable"})
    ranged = copy.deepcopy(read_role)
    for p in ranged["permissions"]:
        if p["resourceCode"] == target_code: p["dataScopeType"] = "SELF"
    set_role(ranged)
    actor = enter()
    assert row(actor)["fieldPresentation"]["related"]["displayValue"] == "无权查看关联记录"
    facts["cases"].append({"case": "target_data_scope_checked_for_original_actor"})
    set_role(read_role)
    actor = enter()
    change_reference(150)
    assert row(actor)["fieldPresentation"]["related"]["displayValue"] == "无权查看关联记录"
    facts["cases"].append({"case": "source_private_target_name_not_disclosed"})
    change_reference(149)
    share = next(s for s in api("GET", f"/api/runtime/modules/{target_code}/records/149/shares", token=admins[77]) if s["id"] == 14)
    api("POST", f"/api/runtime/modules/{target_code}/records/149/shares/14/revoke", {"version": share["version"], "reason": "本地来源展示撤权验收"}, admins[77])
    assert row(actor)["fieldPresentation"]["related"]["displayValue"] == "无权查看关联记录"
    replay = api("PUT", base, body, actor, key)
    assert replay["replayed"] and replay["value"]["fieldPresentation"]["related"]["displayValue"] == "无权查看关联记录"
    facts["cases"].append({"case": "revoked_target_and_replay_remove_target_label"})
    restored = api("POST", f"/api/runtime/modules/{target_code}/records/149/shares", {"targetTenantId": 57, "allowedActions": ["LIST", "DETAIL"]}, admins[77])
    facts["restored_target_share_id"] = restored["id"]
    share = next(s for s in api("GET", base + "/shares", token=admins[77]) if s["id"] == 15)
    api("POST", base + "/shares/15/revoke", {"version": share["version"], "reason": "本地来源展示父记录撤权验收"}, admins[77])
    assert call("GET", base, token=actor)[0] == 404
    assert not any(r["id"] == 151 for r in api("GET", f"/api/runtime/modules/{code}/records", token=actor)["records"])
    assert call("PUT", base, body, actor, key)[0] == 404
    facts["cases"].append({"case": "revoked_parent_not_readable_or_replayable"})
    restored = api("POST", base + "/shares", {"targetTenantId": 57, "allowedActions": ["LIST", "DETAIL", "UPDATE"]}, admins[77])
    facts["restored_parent_share_id"] = restored["id"]
    labels(row(actor))
    facts["record_readback"] = sql("SELECT id,module_id,tenant_id,version,updated_by_member_id FROM biz_record WHERE id=151")
    facts["status"] = "api_verified_pending_ordinary_page"
    facts["page_role114"] = "read_role temporarily retains parent DETAIL for ordinary page; restore original LIST+UPDATE after page"
except Exception as error:
    facts["status"] = "failed_exact_checkpoint"
    facts["error"] = str(error)
    raise
finally:
    # 先保存业务断言结果，避免测试身份收尾失败覆盖真实验证事实。
    save()
    if registration:
        account, system = registration["accountId"], registration["systemId"]
        sql(f"START TRANSACTION; UPDATE auth_session SET revoked=1,revoked_reason='LOCAL_PRESENTATION_ACCEPTANCE_CLOSED' WHERE account_id={account}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account}; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={system} AND creator_account_id={account}; DELETE FROM sys_member_role WHERE tenant_member_id IN (SELECT tm.id FROM sys_tenant_member tm JOIN sys_member m ON m.id=tm.system_member_id WHERE m.account_id={account} AND m.system_id=54); COMMIT")
        facts["closed_api_identity"] = sql(f"SELECT id,status FROM plat_account WHERE id={account}")
    save()
    print(json.dumps({"status": facts["status"], "accepted_cases": len(facts["cases"]), "error": facts.get("error")}, ensure_ascii=False))
