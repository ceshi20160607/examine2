"""复用正式扩展与来源记录151，定向验证普通操作者手动动作；凭证仅在内存。"""
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
action_code = "ACTION_TENANT_ACCEPT"
endpoint = base + f"/actions/{action_code}"
path = AREA / "shared-manual-facts.json"
facts = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"step": "B07.4b.2c.2b.2", "status": "running", "cases": []}
assert facts["status"] == "running", "已有完成事实，不重复测试"
resume_after_validation = any(c["case"] == "wrong_dictionary_missing_extra_private_and_unshared_reference_atomic" for c in facts["cases"])
assert not facts.get("identity") or facts.get("temporary_identity_cleanup") == [["DISABLED"]], "旧身份尚未正确收尾，先处理该阻塞"
if path.exists() and not resume_after_validation:
    facts.setdefault("preparation_failures", []).append("首次已有share15更新未带版本，接口409 SHARE_VERSION_REQUIRED且未写共享；补真实当前版本后接手，尚无业务分支执行")
if resume_after_validation:
    facts.setdefault("preparation_failures", []).append("首次动作已实际保存record151v5；MySQL客户端中文回读为问号导致字符串断言失败，HEX解码确认私有值未变。旧临时身份74已关闭；新短期身份只执行剩余保存/重放/撤权分支，不重跑已通过的四组分支")
registration = None
desired_role = None


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    path.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def case(name, **result):
    facts["cases"].append({"case": name, **result})
    save()


def role_set(role):
    current = next(r for r in api("GET", "/api/admin/system/authorization", token=admins[57])["roles"] if r["id"] == 114)
    draft = api("POST", "/api/admin/system/authorization/roles", {"id": 114, "code": current["code"], "name": current["name"],
        "description": current["description"], "expectedVersion": current["version"], "permissions": role["permissions"],
        "fieldPolicies": role["fieldPolicies"]}, admins[57])
    api("POST", "/api/admin/system/authorization/roles/114/publish", {"expectedVersion": draft["version"],
        "reason": "本地正式共享来源动作定向验收"}, admins[57])


def enter():
    return api("POST", "/api/systems/54/tenants/57/enter", {}, registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]


def grant():
    current = next(s for s in api("GET", base + "/shares", token=admins[77]) if s["id"] == 15)
    return api("POST", base + "/shares", {"targetTenantId": 57, "allowedActions": ["UPDATE", action_code], "version": current["version"]}, admins[77])


def version():
    return sql("SELECT version FROM biz_record WHERE id=151")


try:
    java = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {"username": re.search(r"default-admin\.username:([^}]+)", java).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", java).group(1)})
    admins = {t: api("POST", f"/api/systems/54/tenants/{t}/enter", {}, login["accessToken"])["tokens"]["accessToken"] for t in [57, 77]}
    original = next(r for r in api("GET", "/api/admin/system/authorization", token=admins[57])["roles"] if r["id"] == 114)
    facts.setdefault("original_role", original)
    desired_role = copy.deepcopy(original)
    desired_role["permissions"] = [p for p in desired_role["permissions"] if not (p["resourceCode"] == code and p["actionCode"] == action_code)]
    desired_role["permissions"].append({"resourceType": "MODULE", "resourceCode": code, "actionCode": action_code, "dataScopeType": "ALL"})
    role_set(desired_role)
    share = grant()
    username = "manual_reader_" + secrets.token_hex(6)
    registration = api("POST", "/api/auth/register", {"username": username, "password": secrets.token_urlsafe(24),
        "displayName": "来源动作接口临时验收者", "systemName": "来源动作接口临时空系统"})
    if facts.get("identity"):
        facts.setdefault("closed_previous_identities", []).append(facts["identity"])
    facts["identity"] = {"account_id": registration["accountId"], "system_id": registration["systemId"], "username": username}
    save()
    api("POST", "/api/admin/system/authorization/members", {"account": username, "roleIds": [114]}, admins[57])
    actor = enter()
    context = api("GET", endpoint + "/context", token=actor)
    if not resume_after_validation:
        assert context["name"] == "来源扩展填写动作" and [f["code"] for f in context["parameters"]] == ["choice", "city", "related"]
        assert set(context["record"]["fields"]) == {"choice", "city", "related"}
        assert set(context["record"]["fieldPresentation"]) == {"choice", "city", "related"}
        assert all(f["id"] > 0 and "defaultValue" not in json.loads(f["configJson"]) for f in context["parameters"])
        assert sql(f"SELECT COUNT(*) FROM sys_tenant_member tm JOIN sys_member m ON m.id=tm.system_member_id WHERE m.account_id={registration['accountId']} AND m.system_id=54 AND tm.tenant_id=77") == [["0"]]
        assert call("GET", base, token=actor)[0] == 403
        case("ordinary_actual_source_context_three_parameters_no_private_defaults_no_source_membership_no_DETAIL", context=context)
        field_base = base + "/fields/"
        query = "?actionCode=" + action_code
        dictionary = api("GET", field_base + "choice/dictionary" + query, token=actor)
        assert {i["code"] for i in dictionary["items"]} == {"src_one", "src_two"}
        root = api("GET", field_base + "city/dictionary" + query, token=actor)
        child = api("GET", field_base + "city/dictionary" + query + "&parentId=14", token=actor)
        assert {i["id"] for i in root["items"]} == {14, 15} and [i["id"] for i in child["items"]] == [16]
        options = api("GET", field_base + "related/reference-options" + query, token=actor)
        assert [i["value"] for i in options] == [149] and all("未共享" not in i["label"] for i in options)
        case("source_bound_dictionary_cascade_and_authorized_reference", dictionary=dictionary, root=root, child=child, references=options)
        for field in ["subject", "hidden", "not_existing"]:
            assert call("GET", field_base + field + "/dictionary" + query, token=actor)[0] == 403
        assert call("GET", base + "/actions/ACTION_NOT_REAL/context", token=actor)[0] in [403, 404]
        case("nonparameter_hidden_unknown_field_and_action_denied")
        before = version()
        request = {"version": context["record"]["version"], "values": {"choice": "src_one", "city": [14, 16], "related": 149}}
        wrong = copy.deepcopy(request); wrong["values"]["choice"] = "recipient_only"
        assert call("POST", endpoint, wrong, actor)[0] == 422 and version() == before
        wrong = copy.deepcopy(request); wrong["values"].pop("related")
        assert call("POST", endpoint, wrong, actor)[0] == 422 and version() == before
        wrong = copy.deepcopy(request); wrong["values"]["hidden"] = "不能覆盖私有值"
        assert call("POST", endpoint, wrong, actor)[0] == 422 and version() == before
        wrong = copy.deepcopy(request); wrong["values"]["related"] = 150
        assert call("POST", endpoint, wrong, actor)[0] == 422 and version() == before
        case("wrong_dictionary_missing_extra_private_and_unshared_reference_atomic")
    else:
        request = {"version": context["record"]["version"], "values": {"choice": "src_one", "city": [14, 16], "related": 149}}
        field_base = base + "/fields/"
        query = "?actionCode=" + action_code
    key = "formal_manual_" + secrets.token_hex(8)
    result = api("POST", endpoint, request, actor, key)
    facts["action_id"] = result["idempotencyRecordId"]
    facts["saved_action"] = result
    facts["saved_request"] = request
    facts["saved_idempotency_key"] = key
    save()
    assert result["value"]["version"] == context["record"]["version"] + 1 and "hidden" not in result["value"]["fields"]
    private = bytes.fromhex(sql("SELECT HEX(value_text) FROM biz_record_value WHERE record_id=151 AND field_code='hidden'")[0][0]).decode("utf-8")
    assert private == "正式扩展私有值不得丢失"
    after = version()
    replay = api("POST", endpoint, request, actor, key)
    assert replay["replayed"] and replay["idempotencyRecordId"] == result["idempotencyRecordId"] and version() == after
    facts["action_id"] = result["idempotencyRecordId"]
    case("public_manual_executor_save_and_idempotent_replay_source_tenant_private_values", result=result, replay=replay)
    no_update = copy.deepcopy(desired_role)
    no_update["permissions"] = [p for p in no_update["permissions"] if not (p["resourceCode"] == code and p["actionCode"] == "UPDATE")]
    role_set(no_update); actor = enter()
    assert call("GET", endpoint + "/context", token=actor)[0] == 403
    assert call("GET", field_base + "choice/dictionary" + query, token=actor)[0] == 403
    case("CUSTOM_without_UPDATE_denies_context_and_choices")
    limited = copy.deepcopy(desired_role)
    for p in limited["permissions"]:
        if p["resourceCode"] == code and p["actionCode"] == action_code: p["dataScopeType"] = "SELF"
    role_set(limited); actor = enter()
    assert call("GET", endpoint + "/context", token=actor)[0] == 403
    case("CUSTOM_actual_actor_range_excludes_source_record")
    readonly = copy.deepcopy(desired_role)
    for p in readonly["fieldPolicies"]:
        if p["resourceCode"] == code and p["fieldCode"] == "choice": p["writable"] = False
    role_set(readonly); actor = enter()
    assert call("GET", endpoint + "/context", token=actor)[0] == 403
    assert call("GET", field_base + "choice/dictionary" + query, token=actor)[0] == 403
    case("current_readonly_parameter_denies_context_and_directory")
    no_target = copy.deepcopy(desired_role)
    no_target["permissions"] = [p for p in no_target["permissions"] if not (p["resourceCode"] == target_code and p["actionCode"] == "DETAIL")]
    role_set(no_target); actor = enter()
    assert api("GET", field_base + "related/reference-options" + query, token=actor) == []
    protected = api("GET", endpoint + "/context", token=actor)
    assert protected["record"]["fields"]["related"] == 149 and protected["record"]["fieldPresentation"]["related"]["displayValue"] != "正式扩展来源关联目标"
    case("missing_target_DETAIL_empty_choices_no_target_name_parent_still_usable", context=protected)
    role_set(desired_role); actor = enter()
    current_share = next(s for s in api("GET", base + "/shares", token=admins[77]) if s["id"] == 15)
    api("POST", base + "/shares", {"targetTenantId": 57, "allowedActions": ["UPDATE"], "version": current_share["version"]}, admins[77])
    denied_context = call("GET", endpoint + "/context", token=actor)
    denied_replay = call("POST", endpoint, request, actor, key)
    assert denied_context[0] in [403, 404] and denied_replay[0] in [403, 404] and version() == after
    case("removed_share_CUSTOM_denies_new_context_and_stored_replay", context_status=denied_context[0], replay_status=denied_replay[0])
    share = grant()
    facts["share"] = share
    facts["record_readback"] = sql("SELECT tenant_id,module_id,version,updated_by_member_id FROM biz_record WHERE id=151")
    facts["status"] = "passed_directed_API_database"
    save()
finally:
    # 验收状态先落盘；关闭仅本包新建身份与空系统，保留历史，不影响真实账号。
    save()
    if desired_role is not None:
        role_set(desired_role)
        facts["retained_role"] = "ordinary114 plus formal CUSTOM grant for actual page acceptance"
        facts["share"] = grant()
    if registration:
        aid, sid = registration["accountId"], registration["systemId"]
        sql(f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{username}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid}; UPDATE auth_session SET revoked=1,revoked_reason='本包短期接口身份收尾' WHERE account_id={aid}; DELETE mr FROM sys_member_role mr JOIN sys_tenant_member tm ON tm.id=mr.tenant_member_id JOIN sys_member m ON m.id=tm.system_member_id WHERE m.account_id={aid} AND m.system_id=54 AND mr.role_id=114")
        facts["temporary_identity_cleanup"] = sql(f"SELECT status FROM plat_account WHERE id={aid}")
    save()

print(json.dumps({"status": facts["status"], "cases": len(facts["cases"]), "record": 151}, ensure_ascii=False))
