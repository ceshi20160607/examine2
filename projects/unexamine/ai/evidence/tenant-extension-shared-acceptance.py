"""承接已发布的正式扩展，验证独立接收方真实共享编辑；不重新执行模块身份与扩展发布案例。"""
import ast
import json
import re
import secrets
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {"call", "api", "sql"}], type_ignores=[]), "helpers", "exec"))
FACTS = AREA / "tenant-extension-facts.json"
f = json.loads(FACTS.read_text(encoding="utf-8"))
assert f["status"] == "formal_extensions_ready_for_ordinary_shared_and_page_verification" and (not f["records"] or len(f["records"]) == 3 and f.get("error") is not None)
code, target_code = f["module_code"], f["target_code"]


def save():
    f["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(f, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def create(token, module_code, fields):
    record = api("POST", f"/api/runtime/modules/{module_code}/records", {"title": fields["subject"],
        "status": "ACTIVE", "participantMemberIds": [], "fields": fields}, token)
    f["records"].append({"id": record["id"], "module": module_code, "tenant": 77})
    save()
    return record


try:
    j = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {"username": re.search(r"default-admin\.username:([^}]+)", j).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", j).group(1)})
    admins = {t: api("POST", f"/api/systems/54/tenants/{t}/enter", {}, login["accessToken"])["tokens"]["accessToken"] for t in [57, 77]}
    if f["records"]:
        f.setdefault("preparation_failures", []).append({"reason": "验收请求遗漏必填 participantMemberIds，返回输入校验错误；未修改业务值", "prior_identity": f.pop("registration"), "record_version": 0})
        f.pop("error", None)
        target = {"id": f["target_record_id"]}
        private = {"id": f["private_target_record_id"]}
        record = api("GET", f"/api/runtime/modules/{code}/records/{f['record_id']}", token=admins[77])
        role = {"id": f["role_id"]}
    else:
        target = create(admins[77], target_code, {"subject": "正式扩展来源关联目标"})
        private = create(admins[77], target_code, {"subject": "正式扩展未共享目标"})
        record = create(admins[77], code, {"subject": "正式扩展共享待编辑", "choice": "src_one", "city": [14, 16],
            "related": target["id"], "hidden": "正式扩展私有值不得丢失"})
        f.update(record_id=record["id"], target_record_id=target["id"], private_target_record_id=private["id"])
        for row, module_code, actions in [(target, target_code, ["LIST", "DETAIL"]), (record, code, ["LIST", "UPDATE"])]:
            share = api("POST", f"/api/runtime/modules/{module_code}/records/{row['id']}/shares",
                {"targetTenantId": 57, "allowedActions": actions}, admins[77])
            f["shares"].append({"id": share["id"], "record": row["id"], "module": module_code})
            save()
        permissions = [{"resourceType": "MODULE", "resourceCode": module_code, "actionCode": action, "dataScopeType": "ALL"}
            for module_code, actions in [(code, ["LIST", "UPDATE"]), (target_code, ["LIST", "DETAIL"])] for action in actions]
        policies = [{"resourceCode": code, "fieldCode": field, "channel": "PAGE", "readable": field != "hidden",
            "writable": field != "hidden"} for field in ["subject", "choice", "city", "related", "hidden"]]
        policies.append({"resourceCode": target_code, "fieldCode": "subject", "channel": "PAGE", "readable": True, "writable": False})
        role = api("POST", "/api/admin/system/authorization/roles", {"name": "正式扩展共享验收·" + code,
            "permissions": permissions, "fieldPolicies": policies}, admins[57])
        api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {"expectedVersion": role["version"], "reason": "正式逻辑扩展字段授权"}, admins[57])
        f["role_id"] = role["id"]
    username = "extension_shared_" + secrets.token_hex(6)
    registration = api("POST", "/api/auth/register", {"username": username, "password": secrets.token_urlsafe(24),
        "displayName": "正式扩展独立编辑者", "systemName": "正式扩展临时验收系统"})
    f["registration"] = {"account_id": registration["accountId"], "system_id": registration["systemId"], "username": username}
    member = api("POST", "/api/admin/system/authorization/members", {"account": username, "roleIds": [role["id"]]}, admins[57])
    actor = api("POST", "/api/systems/54/tenants/57/enter", {}, registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]
    f.update(actor_member_id=member["systemMemberId"])
    save()
    base = f"/api/runtime/modules/{code}/records/{record['id']}"
    ctx = api("GET", base + "/edit-context", token=actor)
    own = api("GET", f"/api/runtime/modules/{code}/configuration", token=actor)
    assert ctx["configuration"]["moduleId"] == own["moduleId"] == f["module_id"]
    fields = ctx["configuration"]["configuration"]["fields"]
    assert next(x for x in fields if x["code"] == "choice")["dictionaryId"] == 6
    assert next(x for x in own["configuration"]["fields"] if x["code"] == "choice")["dictionaryId"] == 8
    assert "hidden" not in ctx["record"]["fields"] and not any(x["code"] == "hidden" for x in fields)
    detail_status, detail = call("GET", base, token=actor)
    assert (detail_status, detail["code"]) == (403, "PERMISSION_DENIED")
    dictionary = api("GET", base + "/fields/choice/dictionary", token=actor)
    assert dictionary["dictionaryId"] == 6
    references = api("GET", base + "/fields/related/reference-options", token=actor)
    assert [x["value"] for x in references] == [target["id"]]
    before = sql(f"SELECT field_code,HEX(COALESCE(value_text,CAST(value_reference_id AS CHAR),value_json)) FROM biz_record_value WHERE record_id={record['id']} ORDER BY field_code")
    wrong = dict(ctx["record"]["fields"], choice="recipient_one")
    status, result = call("PUT", base, {"version": ctx["record"]["version"], "title": record["title"], "status": "ACTIVE", "participantMemberIds": ctx["record"]["participantMemberIds"], "fields": wrong}, actor)
    assert (status, result["code"]) == (422, "FIELD_VALIDATION_FAILED")
    assert before == sql(f"SELECT field_code,HEX(COALESCE(value_text,CAST(value_reference_id AS CHAR),value_json)) FROM biz_record_value WHERE record_id={record['id']} ORDER BY field_code")
    good = dict(ctx["record"]["fields"], subject="正式基础加扩展保存成功", choice="src_two", city=[15])
    saved = api("PUT", base, {"version": ctx["record"]["version"], "title": record["title"], "status": "ACTIVE", "participantMemberIds": ctx["record"]["participantMemberIds"], "fields": good}, actor)
    assert saved["version"] == 1
    readback = sql(f"SELECT r.id,r.module_id,r.tenant_id,r.version,r.updated_by_member_id,v.field_code,HEX(COALESCE(v.value_text,CAST(v.value_reference_id AS CHAR),v.value_json)) FROM biz_record r JOIN biz_record_value v ON v.record_id=r.id WHERE r.id={record['id']} ORDER BY v.field_code")
    assert all(int(row[1]) == f["module_id"] and row[2] == "77" for row in readback)
    assert sql(f"SELECT COUNT(*) FROM sys_tenant_member tm JOIN sys_member m ON m.id=tm.system_member_id WHERE m.account_id={registration['accountId']} AND m.system_id=54 AND tm.tenant_id=77") == [["0"]]
    f["cases"].append({"case": "ordinary_recipient_edits_formal_source_extension_without_detail_or_source_membership", "edit_context": ctx,
        "source_dictionary": dictionary, "authorized_references": references, "detail_denied": detail_status,
        "wrong_tenant_value_denied": status, "record_readback": readback})
    # 页面先验证明确标记的本地配置管理员，之后恢复仅 LIST/UPDATE 的普通角色；不授予来源租户身份。
    overview = api("GET", "/api/admin/system/authorization", token=admins[57])
    browser_member = next(x for x in overview["members"] if x["accountId"] == 69)
    api("POST", f"/api/admin/system/authorization/members/{browser_member['tenantMemberId']}/assignment",
        {"departmentId": browser_member["departmentId"], "roleIds": [73], "expectedVersion": browser_member["version"]}, admins[57])
    f["browser_identity"] = {"account_id": 69, "configuration_role": 73, "ordinary_role": role["id"], "tenant": 57, "source_memberships": 0}
    f["status"] = "ordinary_api_verified_waiting_configuration_page_then_ordinary_page"
    save()
    print("VERIFIED:正式基础+来源扩展独立接收方API编辑通过；页面身份69先配置管理员再恢复普通角色")
except Exception as error:
    f["error"] = str(error)
    save()
    print("FAILED:", type(error).__name__, str(error))
    raise
