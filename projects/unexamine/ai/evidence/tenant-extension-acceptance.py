"""模块身份与正式租户扩展的定向 API/数据库验收；凭证仅驻内存，不重复已记录的业务案例。"""
import ast
import json
import re
import secrets
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {"call", "api", "sql"}], type_ignores=[]), "helpers", "exec"))
FACTS = AREA / "tenant-extension-facts.json"
prior = json.loads(FACTS.read_text(encoding="utf-8")) if FACTS.exists() else None
assert not prior or len(prior["cases"]) == 1 and not prior["modules"], "从已记录精确断点接手，不能重新制造验收"
f = {"step": "B04.1a", "status": "in_progress", "cases": [], "modules": [], "records": [], "shares": []}
if prior:
    f = prior
    f.setdefault("preparation_failures", []).append(f.pop("error", "fixture preparation interrupted"))
nonce = f["module_code"].removeprefix("extension_accept_") if prior else secrets.token_hex(5)
f.update(module_code="extension_accept_" + nonce, target_code="extension_target_" + nonce)


def save():
    f["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(f, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def case(name, **details):
    f["cases"].append({"case": name, **details})
    save()


def deny(method, path, body, token, expected):
    status, result = call(method, path, body, token)
    assert (status, result.get("code")) == expected, (path, status, result.get("code"))
    return {"status": status, "code": result["code"]}


def create_module(token, code, name):
    draft = api("POST", "/api/admin/module-config/modules", {"name": name, "code": code,
        "groupName": "正式租户扩展验收"}, token)
    mid = draft["module"]["id"]
    f["modules"].append(mid)
    save()
    api("POST", f"/api/admin/module-config/modules/{mid}/fields", {"code": "subject", "name": "公共主题",
        "fieldType": "TEXT", "required": True, "uniqueValue": False, "searchable": False,
        "sortOrder": 0, "config": {"maxLength": 80}}, token)
    current = api("GET", f"/api/admin/module-config/modules/{mid}/draft", token=token)
    api("POST", f"/api/admin/module-config/modules/{mid}/publish",
        {"expectedDraftRevision": current["module"]["draftRevision"]}, token)
    return mid


def extension(prefix, choice_id, tree_id, target_id):
    fields = [{"code": "choice", "name": prefix + "选择", "fieldType": "SINGLE_SELECT", "dictionaryId": choice_id, "config": {}},
        {"code": "city", "name": prefix + "地区", "fieldType": "CASCADE", "dictionaryId": tree_id,
            "config": {"maxDepth": 2, "allowIntermediate": False, "saveMode": "PATH"}},
        {"code": "related", "name": prefix + "关联", "fieldType": "REFERENCE", "referenceModuleId": target_id, "config": {}},
        {"code": "hidden", "name": prefix + "私有字段", "fieldType": "TEXT", "config": {"defaultValue": "保留默认值"}}]
    for index, field in enumerate(fields):
        field.update(required=False, sortOrder=index + 1)
    return {"fields": fields, "pages": [{"pageType": "FORM", "fieldCodes": ["subject", "city", "choice", "related", "hidden"]}],
        "applicationBindings": [], "expectedVersion": 0}


try:
    j = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {"username": re.search(r"default-admin\.username:([^}]+)", j).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", j).group(1)})
    admins = {t: api("POST", f"/api/systems/54/tenants/{t}/enter", {}, login["accessToken"])["tokens"]["accessToken"] for t in [56, 57, 77]}
    old = json.loads((AREA / "shared-catalog-facts.json").read_text(encoding="utf-8"))
    if not f["cases"]:
        before = sql("SELECT id,status,version FROM cfg_module WHERE id IN (38,39,40,41,42,43)")
        conflict = deny("GET", f"/api/runtime/modules/{old['module_code']}/configuration", None, admins[57], (409, "MODULE_CODE_CONFLICT"))
        catalog_conflict = deny("GET", "/api/runtime/modules", None, admins[57], (409, "MODULE_CODE_CONFLICT"))
        check = api("GET", "/api/admin/module-config/modules/43/publication-check", token=admins[57])
        assert not check["valid"] and any(x["code"] == "MODULE_CODE_CONFLICT" for x in check["issues"])
        denied_publish = deny("POST", "/api/admin/module-config/modules/43/publish", {"expectedDraftRevision": check["draftRevision"]}, admins[57], (422, "PUBLICATION_CHECK_FAILED"))
        assert before == sql("SELECT id,status,version FROM cfg_module WHERE id IN (38,39,40,41,42,43)")
        case("legacy_conflict_explicit_no_replacement_no_writes", configuration=conflict, catalog=catalog_conflict, publish=denied_publish, module_readback=before)
    # 仅停用前一项随机验收的重复模块，保留已归档记录、字段和不可变版本；不是迁移用户配置。
    assert sql("SELECT COUNT(*) FROM biz_record WHERE module_id IN (38,39,40,41,42,43) AND archived=0") == [["0"]]
    sql("UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id IN (38,39,40,41,42,43) AND system_id=54 AND code IN ('catalog_accept_887f2b7e92','catalog_target_887f2b7e92')")
    target_id = create_module(admins[56], f["target_code"], "扩展公共关联模块")
    mid = create_module(admins[56], f["module_code"], "正式扩展公共表单")
    f.update(module_id=mid, target_module_id=target_id)
    denied_create = deny("POST", "/api/admin/module-config/modules", {"name": "不得替换公共模块", "code": f["module_code"], "groupName": "不得遗留失败分组"}, admins[77], (409, "MODULE_CODE_EXISTS"))
    concurrent_code = "extension_concurrent_" + nonce
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda t: call("POST", "/api/admin/module-config/modules", {"name": "并发身份验收", "code": concurrent_code, "groupName": "并发身份验收"}, admins[t]), [57, 77]))
    assert sorted(s for s, _ in results) == [200, 409]
    assert sql(f"SELECT COUNT(*) FROM cfg_module WHERE system_id=54 AND code='{concurrent_code}'") == [["1"]]
    concurrent_id = next(r["data"]["module"]["id"] for s, r in results if s == 200)
    f["modules"].append(concurrent_id)
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={concurrent_id} AND code='{concurrent_code}' AND system_id=54")
    case("create_cannot_shadow_main_and_concurrent_identity_serialized", denied_create=denied_create, concurrent_statuses=[s for s, _ in results], concurrent_module_id=concurrent_id)
    path = f"/api/admin/tenant-extensions/modules/{mid}"
    src, rec = extension("来源扩展", 6, 7, target_id), extension("接收方扩展", 8, 9, target_id)
    before_fields = sql(f"SELECT COUNT(*) FROM cfg_module_field WHERE module_id={mid}")
    bad = json.loads(json.dumps(src)); bad["fields"][1]["dictionaryId"] = 9
    binding_denial = deny("PUT", path, bad, admins[77], (404, "DICTIONARY_NOT_FOUND"))
    assert before_fields == sql(f"SELECT COUNT(*) FROM cfg_module_field WHERE module_id={mid}")
    assert sql(f"SELECT COUNT(*) FROM cfg_tenant_extension WHERE base_module_id={mid}") == [["0"]]
    bad = json.loads(json.dumps(src)); bad["fields"][0]["code"] = "subject"
    base_denial = deny("PUT", path, bad, admins[77], (422, "TENANT_EXTENSION_INVALID"))
    bad = json.loads(json.dumps(src)); bad["fields"][1]["config"]["maxDepth"] = 0
    type_denial = deny("PUT", path, bad, admins[77], (422, "FIELD_CONFIG_INVALID"))
    assert before_fields == sql(f"SELECT COUNT(*) FROM cfg_module_field WHERE module_id={mid}")
    case("extension_wrong_tenant_binding_collision_invalid_config_atomic", binding=binding_denial, base_collision=base_denial, config=type_denial)
    for tenant, body in [(77, src), (57, rec)]:
        draft = api("PUT", path, body, admins[tenant])
        check = api("GET", path + "/publication-check", token=admins[tenant])
        assert check["valid"], check["issues"]
        published = api("POST", path + "/publish", {"expectedDraftRevision": check["draftRevision"]}, admins[tenant])
        config = api("GET", f"/api/runtime/modules/{f['module_code']}/configuration", token=admins[tenant])
        fields = config["configuration"]["fields"]
        assert config["moduleId"] == mid and len(fields) == 5
        assert next(x for x in fields if x["code"] == "subject")["mandatory"]
        assert next(x for x in fields if x["code"] == "choice")["dictionaryId"] == (6 if tenant == 77 else 8)
        assert next(x for x in fields if x["code"] == "related")["referenceModuleId"] == target_id
        f.setdefault("extensions", []).append({"tenant": tenant, "view": published, "runtime_configuration": config})
        save()
    catalog = api("GET", "/api/runtime/modules", token=admins[57])
    assert len([x for x in catalog if x["moduleCode"] == f["module_code"]]) == 1
    resources = api("GET", "/api/admin/system/authorization", token=admins[57])["resources"]
    resource = next(x for x in resources if x["resourceCode"] == f["module_code"])
    assert set(resource["fields"]) == {"subject", "choice", "city", "related", "hidden"}
    assert not any(x.startswith("__t") for x in resource["fields"])
    case("formal_extension_published_same_identity_with_tenant_bindings_and_logical_authorization", physical_fields=sql(f"SELECT id,owner_tenant_id,code,dictionary_id,reference_module_id FROM cfg_module_field WHERE module_id={mid}"), catalog_count=1, authorization_fields=resource["fields"])
    f["status"] = "formal_extensions_ready_for_ordinary_shared_and_page_verification"
    save()
    print("VERIFIED:模块冲突/并发身份/正式扩展发布和逻辑字段权限；接手普通共享及配置页面")
except Exception as error:
    f["error"] = str(error)
    save()
    print("FAILED:", type(error).__name__, str(error))
    raise
