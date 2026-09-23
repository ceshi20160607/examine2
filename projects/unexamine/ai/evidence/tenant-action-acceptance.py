"""已有正式扩展的动作作者链验收，不创建替代模块、账号或运行环境。"""
import ast
import copy
import json
import re
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {"call", "api", "sql"}], type_ignores=[]), "helpers", "exec"))
path = AREA / "tenant-action-facts.json"
assert not path.exists(), "已有事实，不重跑整个验证；从准确失败点继续"
facts = {"step": "B04.1b", "status": "running", "module_id": 45, "cases": []}
fixture = json.loads((AREA / "tenant-extension-facts.json").read_text(encoding="utf-8"))
code = fixture["module_code"]
extension_path = "/api/admin/tenant-extensions/modules/45"


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    path.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def case(name, **result):
    facts["cases"].append({"case": name, **result})
    save()


def body(detail):
    return {"fields": [{"fieldId": f["id"], "code": f["code"], "name": f["name"], "fieldType": f["fieldType"],
        "required": f["required"], "sortOrder": f["sortOrder"], "dictionaryId": f.get("dictionaryId"),
        "referenceModuleId": f.get("referenceModuleId"), "config": json.loads(f["configJson"])} for f in detail["draft"]["fields"]],
        "pages": [{"pageType": p["pageType"], "fieldCodes": p["fieldCodes"]} for p in detail["draft"]["pages"]],
        "applicationBindings": [{"applicationId": p["applicationId"], "grantIds": p["grantIds"]} for p in detail["draft"]["applicationBindings"]],
        "expectedVersion": detail["extension"]["version"]}


def state():
    return sql("SELECT id,owner_tenant_id,code,version FROM cfg_module_action WHERE module_id=45 ORDER BY id"), sql("SELECT id,version,draft_revision,current_version_id FROM cfg_tenant_extension WHERE base_module_id=45 ORDER BY id"), sql("SELECT id,owner_tenant_id,version FROM cfg_module_field WHERE module_id=45 ORDER BY id")


def publish(tenant, detail):
    checked = api("GET", extension_path + "/publication-check", token=admins[tenant])
    assert checked["valid"], checked["issues"]
    return api("POST", extension_path + "/publish", {"expectedDraftRevision": checked["draftRevision"]}, admins[tenant])


def configured_action(tenant):
    configuration = api("GET", f"/api/runtime/modules/{code}/configuration", token=admins[tenant])
    return next(a for a in configuration["configuration"]["actions"] if a["code"] == "ACTION_TENANT_ACCEPT")


try:
    java = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {"username": re.search(r"default-admin\.username:([^}]+)", java).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", java).group(1)})
    admins = {t: api("POST", f"/api/systems/54/tenants/{t}/enter", {}, login["accessToken"])["tokens"]["accessToken"] for t in [56, 57, 77]}
    definitions = {}
    for tenant, params, name in [(77, ["choice", "city", "related"], "来源扩展填写动作"), (57, ["subject"], "接收方扩展填写动作")]:
        detail = api("GET", extension_path, token=admins[tenant])
        facts.setdefault("prior_current_versions", {})[str(tenant)] = detail["extension"]["currentVersionId"]
        request = body(detail)
        action = {"code": "ACTION_TENANT_ACCEPT", "name": name, "location": "ROW", "parameterFieldCodes": params, "sortOrder": 10}
        request["actions"] = [action]
        before = state()
        for invalid_name, changes, expected in [("main_action_collision", {"code": "UPDATE"}, "TENANT_EXTENSION_INVALID"),
                ("unknown_parameter", {"parameterFieldCodes": ["not_existing"]}, "CUSTOM_ACTION_FIELD_INVALID"),
                ("duplicate_parameter", {"parameterFieldCodes": [params[0], params[0]]}, "CUSTOM_ACTION_CONFIG_INVALID")]:
            wrong = copy.deepcopy(request); wrong["actions"][0].update(changes)
            status, result = call("PUT", extension_path, wrong, admins[tenant])
            assert status == 422 and result["code"] == expected, (invalid_name, status, result.get("code"))
            assert state() == before
            case(f"tenant{tenant}_{invalid_name}_atomic", status=status, error_code=result["code"])
        detail = api("PUT", extension_path, request, admins[tenant])
        own = next(a for a in detail["draft"]["actions"] if a["code"] == action["code"])
        assert own["source"] == "TENANT" and own["sourceTenantId"] == tenant and own["id"] > 0
        detail = publish(tenant, detail)
        definitions[tenant] = configured_action(tenant)
        assert definitions[tenant]["id"] == own["id"] and json.loads(definitions[tenant]["configJson"])["parameterFieldCodes"] == params
        catalog = next(r for r in api("GET", "/api/admin/system/authorization", token=admins[tenant])["resources"] if r["resourceType"] == "MODULE" and r["resourceCode"] == code)
        assert action["code"] in catalog["actions"] and catalog["actionNames"][action["code"]] == name
        assert not any(c.startswith("__t") for c in catalog["actions"])
        facts.setdefault("published_versions", {})[str(tenant)] = detail["extension"]["currentVersionId"]
        case(f"tenant{tenant}_physical_identity_logical_published_action_and_own_catalog", action=definitions[tenant], catalog=catalog)
    assert definitions[57]["id"] != definitions[77]["id"]
    detail = api("GET", extension_path, token=admins[77]); wrong = body(detail)
    source = definitions[77]
    wrong["actions"] = [{"actionId": definitions[57]["id"], "code": source["code"], "name": source["name"], "location": source["location"],
        "parameterFieldCodes": ["choice", "city", "related"], "sortOrder": 10}]
    before = state(); status, result = call("PUT", extension_path, wrong, admins[77])
    assert status == 422 and state() == before
    case("foreign_tenant_action_identity_rejected_without_any_write", status=status, error_code=result["code"])
    # 原有客户端不提供 actions 时，保存仍保留现有动作而不是清空配置。
    detail = api("PUT", extension_path, body(detail), admins[77])
    assert detail["draft"]["actions"][0]["id"] == source["id"]
    detail = publish(77, detail)
    facts["published_versions"]["77"] = detail["extension"]["currentVersionId"]
    case("old_save_contract_preserves_existing_extension_action", id=source["id"])
    recipient = api("GET", extension_path, token=admins[57]); restore = recipient["extension"]["currentVersionId"]
    prior = facts["prior_current_versions"]["57"]
    old = api("POST", extension_path + "/rollback", {"targetVersionId": prior, "expectedVersion": recipient["extension"]["version"]}, admins[57])
    current = api("GET", f"/api/runtime/modules/{code}/configuration", token=admins[57])
    assert not any(a["code"] == "ACTION_TENANT_ACCEPT" for a in current["configuration"]["actions"])
    catalog = next(r for r in api("GET", "/api/admin/system/authorization", token=admins[57])["resources"] if r["resourceCode"] == code)
    assert "ACTION_TENANT_ACCEPT" not in catalog["actions"]
    api("POST", extension_path + "/rollback", {"targetVersionId": restore, "expectedVersion": old["extension"]["version"]}, admins[57])
    assert configured_action(57)["id"] == definitions[57]["id"]
    case("rollback_removes_action_from_effective_runtime_catalog_and_restores_same_identity")
    main = api("GET", "/api/admin/module-config/modules/45/draft", token=admins[56])
    assert all(f["ownerTenantId"] == 56 for f in main["fields"]) and all(a["ownerTenantId"] == 56 for a in main["actions"])
    physical = sql(f"SELECT code,version FROM cfg_module_action WHERE id={source['id']}")[0]
    status, result = call("PUT", f"/api/admin/module-config/modules/45/actions/{physical[0]}", {"version": int(physical[1]), "location": "ROW", "config": {"operation": "UPDATE_FIELDS", "parameterFieldCodes": ["subject"]}}, admins[56])
    assert status == 404 and sql(f"SELECT code,version FROM cfg_module_action WHERE id={source['id']}")[0] == physical
    case("main_draft_excludes_foreign_fields_actions_and_cannot_edit_internal_foreign_action", status=status)
    # 相同修订重复发布应被拒绝；通过正常基础动作保存产生一个新修订，再验收基础快照隔离。
    main_action = main["actions"][0]
    api("PUT", f"/api/admin/module-config/modules/45/actions/{main_action['code']}",
        {"version": main_action["version"], "location": main_action["location"], "config": json.loads(main_action["configJson"])}, admins[56])
    check = api("GET", "/api/admin/module-config/modules/45/publication-check", token=admins[56])
    assert check["valid"], check["issues"]
    published = api("POST", "/api/admin/module-config/modules/45/publish", {"expectedDraftRevision": check["draftRevision"]}, admins[56])
    main_effective = api("GET", f"/api/runtime/modules/{code}/configuration", token=admins[56])["configuration"]
    assert all(f["ownerTenantId"] == 56 for f in main_effective["fields"])
    assert all(a["ownerTenantId"] == 56 for a in main_effective["actions"])
    case("main_republication_keeps_extension_physical_rows_out_of_base_snapshot", version_id=published["versionId"])
    facts["physical_readback"] = sql("SELECT id,system_id,owner_tenant_id,module_id,code,action_type,status,version FROM cfg_module_action WHERE module_id=45 ORDER BY id")
    facts["status"] = "api_verified_pending_configuration_page"
    facts["remaining"] = "Runtime source custom metadata/options, all Flow combinations, stale base rebase and full extension lifecycle remain required. Source/recipient action fixture retained for exact dependent child."
except Exception as error:
    facts["status"] = "failed_exact_checkpoint"; facts["error"] = str(error)
    raise
finally:
    save()
    print(json.dumps({"status": facts["status"], "accepted_cases": len(facts["cases"]), "error": facts.get("error")}, ensure_ascii=False))
