"""来源覆盖表单、字典级联及关联的真实验收；凭证只驻内存，保留夹具供一次页面验收。"""
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
FACTS = AREA / "shared-catalog-facts.json"
prior = json.loads(FACTS.read_text(encoding="utf-8")) if FACTS.exists() else None
assert not prior or not prior["cases"] and not prior["records"] and not prior["registrations"], "已有业务验收从准确事实接手，不重复执行"
facts = {"step": "B07.4b.2c.2", "business_verification": "in_progress", "cases": [],
    "modules": [], "dictionaries": [], "records": [], "shares": [], "roles": [], "registrations": []}
nonce = secrets.token_hex(5)
if prior:
    facts = prior
    facts.setdefault("preparation_failures", []).append(facts.pop("error", "preparation interrupted"))
    nonce = facts["module_code"].removeprefix("catalog_accept_")
code, target_code = "catalog_accept_" + nonce, "catalog_target_" + nonce
facts.update(system_id=54, main_tenant_id=56, recipient_tenant_id=57, module_code=code, target_code=target_code)


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def enter(token, tenant):
    return api("POST", f"/api/systems/54/tenants/{tenant}/enter", {}, token)


def deny(method, url, body, token, expected):
    status, result = call(method, url, body, token)
    assert (status, result.get("code")) == expected, (url, status, result.get("code"))
    return {"status": status, "code": result["code"]}


def dictionary(admin, tenant, prefix, hierarchical):
    dictionary_code = ("tree_" if hierarchical else "choice_") + nonce
    draft = next((d for d in api("GET", "/api/admin/dictionaries", token=admin) if
        d["dictionary"]["code"] == dictionary_code), None)
    if draft is None:
        draft = api("POST", "/api/admin/dictionaries", {"code": dictionary_code,
            "name": prefix + ("层级字典" if hierarchical else "选项字典"), "hierarchical": hierarchical}, admin)
    did = draft["dictionary"]["id"]
    if not any(d["id"] == did for d in facts["dictionaries"]): facts["dictionaries"].append({"id": did, "tenant": tenant})
    items = list(draft["items"])
    for index, value in enumerate(["one", "two"]):
        if index < len(items): continue
        items.append(api("POST", f"/api/admin/dictionaries/{did}/items", {"parentId": None,
            "code": ("src_" if prefix == "来源" else "recipient_" if prefix == "接收方" else "main_") + value,
            "label": prefix + ("省" if hierarchical else "选项") + str(index + 1), "sortOrder": index}, admin))
    if hierarchical and len(items) == 2:
        leaf = api("POST", f"/api/admin/dictionaries/{did}/items", {"parentId": items[0]["id"],
            "code": "leaf_" + prefix.encode().hex(), "label": prefix + "城市", "sortOrder": 0}, admin)
        items.append(leaf)
    current = api("GET", f"/api/admin/dictionaries/{did}", token=admin)
    if not current["currentVersionId"]:
        api("POST", f"/api/admin/dictionaries/{did}/publish", {"expectedDraftRevision": current["dictionary"]["version"]}, admin)
    save()
    return did, items


def module(admin, tenant, module_code, name, specs, order=None):
    draft = api("POST", "/api/admin/module-config/modules", {"name": name, "code": module_code,
        "groupName": "共享来源字典关联验收"}, admin)
    mid = draft["module"]["id"]
    facts["modules"].append({"id": mid, "tenant": tenant, "code": module_code})
    for index, spec in enumerate(specs):
        api("POST", f"/api/admin/module-config/modules/{mid}/fields", {"required": spec["code"] == "subject",
            "uniqueValue": False, "searchable": False, "sortOrder": index, **spec}, admin)
    current = api("GET", f"/api/admin/module-config/modules/{mid}/draft", token=admin)
    if order:
        page = next(p for p in current["pages"] if p["pageType"] == "FORM")
        api("PUT", f"/api/admin/module-config/modules/{mid}/pages/FORM", {"version": page["version"],
            "layout": {"fieldCodes": order, "components": [{"id": "source_form", "type": "SECTION", "title": name,
                "columns": 1, "fieldCodes": order}]}}, admin)
        current = api("GET", f"/api/admin/module-config/modules/{mid}/draft", token=admin)
    api("POST", f"/api/admin/module-config/modules/{mid}/publish", {"expectedDraftRevision": current["module"]["draftRevision"]}, admin)
    save()
    return mid


def record(admin, tenant, module_code, label, fields):
    row = api("POST", f"/api/runtime/modules/{module_code}/records", {"title": label, "status": "ACTIVE",
        "participantMemberIds": [], "fields": fields}, admin)
    facts["records"].append({"id": row["id"], "tenant": tenant, "module": module_code})
    save()
    return row


def share(admin, row, module_code, actions):
    value = api("POST", f"/api/runtime/modules/{module_code}/records/{row['id']}/shares",
        {"targetTenantId": 57, "allowedActions": actions}, admin)
    facts["shares"].append({"id": value["id"], "record": row["id"], "module": module_code})
    save()
    return value


def role(admin, label, source_actions, target_actions, target_scope="ALL"):
    permissions = [{"resourceType": "MODULE", "resourceCode": code, "actionCode": action, "dataScopeType": "ALL"}
        for action in source_actions] + [{"resourceType": "MODULE", "resourceCode": target_code, "actionCode": action,
            "dataScopeType": target_scope} for action in target_actions]
    policies = [{"resourceCode": code, "fieldCode": field, "channel": "PAGE", "readable": field != "hidden",
        "writable": "UPDATE" in source_actions and field not in {"hidden", "readonly"}}
        for field in ["subject", "choice", "city", "related", "hidden", "readonly"]]
    policies.append({"resourceCode": target_code, "fieldCode": "subject", "channel": "PAGE", "readable": True, "writable": False})
    value = api("POST", "/api/admin/system/authorization/roles", {"name": "来源目录验收·" + nonce + "·" + label,
        "permissions": permissions, "fieldPolicies": policies}, admin)
    api("POST", f"/api/admin/system/authorization/roles/{value['id']}/publish", {"expectedVersion": value["version"],
        "reason": "实际来源字典和关联授权验收"}, admin)
    facts["roles"].append(value["id"])
    save()
    return value["id"]


def values(record_id):
    return sql(f"SELECT field_code,HEX(COALESCE(value_text,CAST(value_reference_id AS CHAR),value_json)) FROM biz_record_value WHERE record_id={int(record_id)} ORDER BY field_code")


try:
    initializer = (JAVA / "platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {"username": re.search(r"default-admin\.username:([^}]+)", initializer).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", initializer).group(1)})
    main_admin = enter(login["accessToken"], 56)["tokens"]["accessToken"]
    recipient_admin = enter(login["accessToken"], 57)["tokens"]["accessToken"]
    tenant = facts.get("source_tenant") or api("POST", "/api/admin/system/tenants", {"code": "catalog_source_" + nonce, "name": "来源目录隔离验收"}, main_admin)
    source_tenant = tenant["id"]
    facts["source_tenant"] = {"id": source_tenant, "code": tenant["code"]}
    source_admin = enter(login["accessToken"], source_tenant)["tokens"]["accessToken"]
    datasets = {}
    for tenant_id, admin, prefix in [(56, main_admin, "主租户"), (source_tenant, source_admin, "来源"), (57, recipient_admin, "接收方")]:
        choice_id, choices = dictionary(admin, tenant_id, prefix, False)
        tree_id, tree = dictionary(admin, tenant_id, prefix, True)
        target_id = module(admin, tenant_id, target_code, prefix + "关联目标", [{"code": "subject", "name": prefix + "主题", "fieldType": "TEXT", "config": {}}])
        specs = [{"code": "subject", "name": prefix + "主题", "fieldType": "TEXT", "config": {"maxLength": 60 if prefix == "来源" else 200}},
            {"code": "choice", "name": prefix + "选择", "fieldType": "SINGLE_SELECT", "dictionaryId": choice_id, "config": {}},
            {"code": "city", "name": prefix + "地区", "fieldType": "CASCADE", "dictionaryId": tree_id, "config": {"maxDepth": 2, "saveMode": "PATH", "allowIntermediate": False}},
            {"code": "related", "name": prefix + "关联", "fieldType": "REFERENCE", "referenceModuleId": target_id, "config": {}},
            {"code": "hidden", "name": prefix + "私有字段", "fieldType": "TEXT", "config": {}},
            {"code": "readonly", "name": prefix + "只读字段", "fieldType": "TEXT", "config": {"readOnly": True}}]
        mid = module(admin, tenant_id, code, prefix + "共享表单", specs, ["city", "choice", "subject", "related", "hidden", "readonly"] if prefix == "来源" else ["subject", "related", "choice", "city", "hidden", "readonly"])
        datasets[prefix] = {"module_id": mid, "target_module_id": target_id, "choice_id": choice_id,
            "tree_id": tree_id, "choices": choices, "tree": tree}
    facts["datasets"] = datasets
    src, rec = datasets["来源"], datasets["接收方"]
    target = record(source_admin, source_tenant, target_code, "可授权来源关联", {"subject": "来源目标"})
    private_target = record(source_admin, source_tenant, target_code, "未共享来源关联不得出现", {"subject": "来源私有目标"})
    recipient_target = record(recipient_admin, 57, target_code, "接收方记录不得写入来源引用", {"subject": "接收方目标"})
    source_fields = {"subject": "来源表单待编辑", "choice": src["choices"][0]["code"], "city": [src["tree"][0]["id"], src["tree"][2]["id"]], "related": target["id"], "hidden": "原始私有值保持"}
    current = record(source_admin, source_tenant, code, "来源表单真实验收", source_fields)
    private_record = record(source_admin, source_tenant, code, "未共享表单不得读取", {"subject": "私有表单"})
    share(source_admin, target, target_code, ["LIST", "DETAIL"])
    current_share = share(source_admin, current, code, ["LIST", "DETAIL", "UPDATE"])
    facts.update(record_id=current["id"], target_record_id=target["id"], private_target_record_id=private_target["id"],
        recipient_target_record_id=recipient_target["id"], private_record_id=private_record["id"])
    full = role(recipient_admin, "可编辑", ["LIST", "DETAIL", "UPDATE"], ["LIST", "DETAIL"])
    readonly = role(recipient_admin, "只读", ["LIST", "DETAIL"], ["LIST", "DETAIL"])
    no_detail = role(recipient_admin, "无目标详情", ["LIST", "DETAIL", "UPDATE"], ["LIST"])
    target_self = role(recipient_admin, "目标本人范围", ["LIST", "DETAIL", "UPDATE"], ["LIST", "DETAIL"], "SELF")
    update_only = role(recipient_admin, "只有编辑", ["LIST", "UPDATE"], ["LIST", "DETAIL"])
    facts["role_cases"] = {"full": full, "readonly": readonly, "no_target_detail": no_detail, "target_self": target_self, "update_only": update_only}
    username = "shared_catalog_" + secrets.token_hex(6)
    registration = api("POST", "/api/auth/register", {"username": username, "password": secrets.token_urlsafe(24),
        "displayName": "独立共享目录编辑者", "systemName": "来源目录临时注册系统"})
    facts["registrations"].append({"account_id": registration["accountId"], "system_id": registration["systemId"], "username": username})
    member = api("POST", "/api/admin/system/authorization/members", {"account": username, "roleIds": [full]}, recipient_admin)
    facts.update(actor_account_id=registration["accountId"], actor_member_id=member["systemMemberId"])

    def assign(role_id):
        global member
        member = api("POST", f"/api/admin/system/authorization/members/{member['tenantMemberId']}/assignment",
            {"departmentId": member["departmentId"], "roleIds": [role_id], "expectedVersion": member["version"]}, recipient_admin)
        return enter(registration["platformTokens"]["accessToken"], 57)["tokens"]["accessToken"]

    actor = assign(full)
    base = f"/api/runtime/modules/{code}/records"
    context = api("GET", f"{base}/{current['id']}/edit-context", token=actor)
    fields = context["configuration"]["configuration"]["fields"]
    assert context["configuration"]["moduleId"] == src["module_id"] and context["record"]["dataTenantId"] == source_tenant
    assert next(f for f in fields if f["code"] == "choice")["dictionaryId"] == src["choice_id"]
    assert not any(f["code"] == "hidden" for f in fields) and "hidden" not in context["record"]["fields"]
    assert api("GET", f"/api/runtime/modules/{code}/configuration", token=actor)["moduleId"] == rec["module_id"]
    choices = api("GET", f"{base}/{current['id']}/fields/choice/dictionary?tenantId=57", token=actor)
    roots = api("GET", f"{base}/{current['id']}/fields/city/dictionary", token=actor)
    children = api("GET", f"{base}/{current['id']}/fields/city/dictionary?parentId={src['tree'][0]['id']}", token=actor)
    assert choices["dictionaryId"] == src["choice_id"] and [x["code"] for x in choices["items"]] == [x["code"] for x in src["choices"]]
    assert roots["dictionaryId"] == src["tree_id"] and [x["id"] for x in children["items"]] == [src["tree"][2]["id"]]
    foreign = deny("GET", f"/api/runtime/dictionaries/by-id/{src['choice_id']}?tenantId={source_tenant}", None, actor, (404, "DICTIONARY_NOT_FOUND"))
    selected = api("GET", f"{base}/{current['id']}/fields/related/reference-options", token=actor)
    assert [x["value"] for x in selected] == [target["id"]], selected
    facts["cases"].append({"case": "source_published_form_dictionary_cascade_reference", "configuration": context["configuration"],
        "dictionary_id": choices["dictionaryId"], "root_ids": [x["id"] for x in roots["items"]], "child_ids": [x["id"] for x in children["items"]],
        "reference_options": selected, "foreign_dictionary": foreign})
    for field in ["hidden", "readonly"]:
        facts["cases"].append({"case": "restricted_field_options", "field": field,
            "denial": deny("GET", f"{base}/{current['id']}/fields/{field}/dictionary", None, actor, (403, "PERMISSION_DENIED"))})
    facts["cases"].append({"case": "unshared_edit_context", "denial": deny("GET", f"{base}/{private_record['id']}/edit-context", None, actor, (404, "RECORD_NOT_FOUND"))})
    for role_id, label in [(readonly, "readonly"), (no_detail, "no_target_detail"), (target_self, "target_self")]:
        actor = assign(role_id)
        if role_id == readonly:
            denial = deny("GET", f"{base}/{current['id']}/edit-context", None, actor, (403, "PERMISSION_DENIED"))
        elif role_id == no_detail:
            denial = deny("GET", f"{base}/{current['id']}/fields/related/reference-options", None, actor, (403, "PERMISSION_DENIED"))
        else:
            assert api("GET", f"{base}/{current['id']}/fields/related/reference-options", token=actor) == []
            denial = {"no_reference_options": True}
        facts["cases"].append({"case": label, "denial": denial})
    actor = assign(update_only)
    context = api("GET", f"{base}/{current['id']}/edit-context", token=actor)
    deny("GET", f"{base}/{current['id']}", None, actor, (403, "PERMISSION_DENIED"))
    facts["cases"].append({"case": "update_without_detail", "source_configuration_id": context["configuration"]["moduleId"], "detail_denied": True})
    actor = assign(full)
    record_view = context["record"]
    fields_update = {"subject": "接口来源目录编辑验收", "choice": src["choices"][1]["code"],
        "city": [src["tree"][0]["id"], src["tree"][2]["id"]], "related": target["id"]}
    body = {"title": record_view["title"], "recordNumber": record_view["recordNumber"], "status": record_view["status"],
        "version": record_view["version"], "ownerMemberId": record_view["ownerMemberId"], "departmentId": record_view["departmentId"],
        "participantMemberIds": record_view["participantMemberIds"], "fields": fields_update}
    before = values(current["id"])
    for label, field, value in [("recipient_dictionary", "choice", rec["choices"][0]["code"]),
        ("recipient_cascade", "city", [rec["tree"][0]["id"], rec["tree"][2]["id"]]),
        ("recipient_reference", "related", recipient_target["id"]), ("unshared_reference", "related", private_target["id"])]:
        denial = deny("PUT", f"{base}/{current['id']}", {**body, "fields": {**fields_update, field: value}}, actor, (422, "FIELD_VALIDATION_FAILED"))
        assert values(current["id"]) == before
        facts["cases"].append({"case": label, "denial": denial, "values_unchanged": True})
    updated = api("PUT", f"{base}/{current['id']}", body, actor)
    persisted = values(current["id"])
    assert updated["version"] == record_view["version"] + 1
    assert next(v for k, v in persisted if k == "hidden") == next(v for k, v in before if k == "hidden")
    assert updated["fields"]["choice"] == src["choices"][1]["code"] and updated["fields"]["city"] == fields_update["city"]
    facts["cases"].append({"case": "actual_source_edit_persisted", "record": updated, "values": persisted})
    facts["actor_source_membership_count"] = int(sql(f"SELECT COUNT(*) FROM sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id WHERE sm.account_id={registration['accountId']} AND tm.tenant_id={source_tenant}")[0][0])
    assert facts["actor_source_membership_count"] == 0
    facts["business_verification"] = "verified_api_database_page_pending"
    save()
    print(json.dumps({"step": facts["step"], "record_id": current["id"], "source_tenant": source_tenant,
        "cases": len(facts["cases"]), "page_pending": True}))
except Exception as error:
    facts["error"] = str(error)
    save()
    raise
