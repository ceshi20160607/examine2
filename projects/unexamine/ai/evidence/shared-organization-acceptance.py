"""共享来源组织、受限字段与渠道的真实验收；只复用既有服务，凭证仅驻内存。"""
import ast
import json
import re
import secrets
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
JAVA = AREA.parents[1] / "backend/src/main/java/com/unique/unexamine"
# 仅复用既有 HTTP/SQL 工具函数，不执行旧夹具或复制其已通过用例。
helpers = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in helpers.body if isinstance(node, ast.Import) or
    isinstance(node, ast.ImportFrom) or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}],
    type_ignores=[]), "local-acceptance-helpers", "exec"))
SYSTEM, SOURCE_TENANT, RECIPIENT_TENANT = 54, 56, 57
FACTS = AREA / "shared-organization-facts.json"
assert not FACTS.exists(), "已有夹具须按 facts 准确接手，不重复执行"
facts = {"step": "B07.4b.2c", "business_verification": "in_progress", "cases": [],
         "records": [], "shares": [], "registrations": [], "roles": [], "departments": []}
source_admin = recipient_admin = None


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def enter(platform_token, tenant):
    return api("POST", f"/api/systems/{SYSTEM}/tenants/{tenant}/enter", {}, platform_token)


def register(label):
    username = "shared_org_" + secrets.token_hex(6)
    registration = api("POST", "/api/auth/register", {"username": username,
        "password": secrets.token_urlsafe(24), "displayName": label, "systemName": "共享组织临时注册系统"})
    facts["registrations"].append({"account_id": registration["accountId"],
        "system_id": registration["systemId"], "username": username})
    save()
    return username, registration


def values(record_id):
    return {code: bytes.fromhex(value).decode("utf-8") for code, value in sql(
        f"SELECT field_code,HEX(COALESCE(value_text,CAST(value_reference_id AS CHAR),value_json)) FROM biz_record_value WHERE record_id={int(record_id)}")}


def assign(role_id):
    global actor_member
    actor_member = api("POST", f"/api/admin/system/authorization/members/{actor_member['tenantMemberId']}/assignment",
        {"roleIds": [role_id], "departmentId": actor_member.get("departmentId"),
         "expectedVersion": actor_member["version"]}, recipient_admin)
    return enter(actor_registration["platformTokens"]["accessToken"], RECIPIENT_TENANT)["tokens"]["accessToken"]


def deny(method, url, body, token, expected):
    status, result = call(method, url, body, token)
    assert (status, result.get("code")) == expected, (url, status, result.get("code"))
    return {"status": status, "code": result["code"]}


def update_body(record, fields):
    return {"title": record["title"], "recordNumber": record.get("recordNumber"),
        "status": record["status"], "version": record["version"], "ownerMemberId": record["ownerMemberId"],
        "departmentId": record["departmentId"], "participantMemberIds": record["participantMemberIds"], "fields": fields}


def cleanup():
    errors = []
    for record_id in facts["records"]:
        try:
            current = api("GET", f"{base}/{record_id}", token=source_admin)
            if not current["archived"]:
                api("POST", f"{base}/{record_id}/archive", {"version": current["version"], "reason": "真实组织字段验收收尾"}, source_admin)
        except Exception as error:
            errors.append(str(error))
    for item in facts["shares"]:
        try:
            rows = api("GET", f"{base}/{item['record']}/shares", token=source_admin)
            current = next(row for row in rows if row["id"] == item["share"])
            if current["status"] == "ACTIVE":
                api("POST", f"{base}/{item['record']}/shares/{item['share']}/revoke",
                    {"version": current["version"], "reason": "真实组织字段验收收尾"}, source_admin)
        except Exception as error:
            errors.append(str(error))
    for item in facts["registrations"]:
        account, system, username = item["account_id"], item["system_id"], item["username"]
        sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={account} AND sm.system_id={SYSTEM} AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={account} AND system_id={SYSTEM} AND status='ACTIVE'")
        sql(f"DELETE mr FROM sys_member_role mr JOIN sys_tenant_member tm ON tm.id=mr.tenant_member_id "
            f"JOIN sys_member sm ON sm.id=tm.system_member_id WHERE sm.account_id={account} AND sm.system_id={SYSTEM}; "
            f"UPDATE auth_session SET revoked=1,revoked_reason='shared-org-acceptance-closed' WHERE account_id={account}; "
            f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account} AND username='{username}'; "
            f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={system} AND creator_account_id={account}")
    if facts["roles"]:
        ids = ",".join(str(int(value)) for value in facts["roles"])
        sql(f"UPDATE sys_role r SET status='DISABLED',version=version+1 WHERE id IN ({ids}) "
            f"AND tenant_id={RECIPIENT_TENANT} AND name LIKE '共享组织验收·%' "
            "AND NOT EXISTS (SELECT 1 FROM sys_member_role mr WHERE mr.role_id=r.id)")
    # 保留审批、记录和配置审计；只停用本次随机模块与空部门，不删除已发表版本。
    if facts.get("module_id"):
        sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={facts['module_id']} "
            f"AND owner_tenant_id={SOURCE_TENANT} AND code='{code}'")
    if facts["departments"]:
        ids = ",".join(str(int(value)) for value in facts["departments"])
        sql(f"UPDATE sys_department SET status='DISABLED',version=version+1 WHERE id IN ({ids}) "
            f"AND tenant_id={SOURCE_TENANT} AND code LIKE 'org_accept_%'")
    facts["cleanup"] = {"archived_records": len(facts["records"]), "synthetic_identities_disabled": True,
                        "test_roles_unassigned_disabled": True, "errors": errors}
    save()
    assert not errors, errors


try:
    initializer = (JAVA / "platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {"username": re.search(r"default-admin\.username:([^}]+)", initializer).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", initializer).group(1)})
    owner_entry = enter(login["accessToken"], SOURCE_TENANT)
    source_admin = owner_entry["tokens"]["accessToken"]
    recipient_admin = enter(login["accessToken"], RECIPIENT_TENANT)["tokens"]["accessToken"]
    source_catalog = api("GET", "/api/admin/system/authorization", token=source_admin)
    source_root = next(item for item in source_catalog["departments"] if item["parentId"] is None and item["status"] == "ACTIVE")
    hidden_dept = api("POST", "/api/admin/system/authorization/departments", {"code": "org_accept_" + secrets.token_hex(4),
        "name": "仅来源组织的受限部门", "parentId": source_root["id"], "sortOrder": 0}, source_admin)
    facts["departments"].append(hidden_dept["id"])
    hidden_name, hidden_registration = register("仅来源组织的受限成员")
    hidden_member = api("POST", "/api/admin/system/authorization/members", {"account": hidden_name,
        "departmentId": hidden_dept["id"], "roleIds": []}, source_admin)
    actor_name, actor_registration = register("独立来源组织编辑者")
    code = "org_accept_" + secrets.token_hex(4)
    draft = api("POST", "/api/admin/module-config/modules", {"name": "共享来源组织与字段验收", "code": code, "groupName": "共享来源组织验收"}, source_admin)
    module_id = draft["module"]["id"]
    facts.update({"module_id": module_id, "module_code": code, "system_id": SYSTEM,
        "actor_tenant_id": RECIPIENT_TENANT, "data_tenant_id": SOURCE_TENANT,
        "source_root": source_root["id"], "source_owner": owner_entry["systemMemberId"],
        "hidden_member": hidden_member["systemMemberId"], "hidden_department": hidden_dept["id"]})
    field_specs = [("subject", "主题", "TEXT"), ("visible_person", "来源成员", "MEMBER"),
        ("visible_department", "来源部门", "DEPARTMENT"), ("hidden_person", "受限成员", "MEMBER"),
        ("hidden_department", "受限部门", "DEPARTMENT"), ("secret_text", "受限备注", "TEXT")]
    for index, (field_code, name, kind) in enumerate(field_specs):
        api("POST", f"/api/admin/module-config/modules/{module_id}/fields", {"code": field_code, "name": name,
            "fieldType": kind, "required": field_code == "subject", "uniqueValue": False,
            "searchable": False, "sortOrder": index, "config": {}}, source_admin)
    custom = {"code": "UPDATE"}  # 旧版本类型限制已单独留证，新版本后再配置真实动作。
    draft = api("GET", f"/api/admin/module-config/modules/{module_id}/draft", token=source_admin)
    api("POST", f"/api/admin/module-config/modules/{module_id}/publish", {"expectedDraftRevision": draft["module"]["draftRevision"]}, source_admin)
    for label, writable in [("可编辑转移", True), ("只读", False)]:
        permissions = [{"resourceType": "MODULE", "resourceCode": code, "actionCode": action, "dataScopeType": "ALL"}
            for action in (["LIST", "DETAIL", "UPDATE", "TRANSFER"] if writable else ["LIST", "DETAIL"])]
        policies = []
        for channel in ["PAGE", "APPLICATION", "FILE", "FLOW"]:
            for field_code, _, _ in field_specs:
                policy = {"resourceCode": code, "fieldCode": field_code, "channel": channel,
                    "readable": field_code != "hidden_department" and (channel == "PAGE" or field_code != "secret_text"),
                    "writable": writable and field_code in {"subject", "visible_person", "visible_department"}}
                if field_code == "hidden_person": policy["maskStrategy"] = "FULL"
                policies.append(policy)
        role_request = {"name": "共享组织验收·" + label, "permissions": permissions, "fieldPolicies": policies}
        if writable: full_role_request = role_request
        role = api("POST", "/api/admin/system/authorization/roles", role_request, recipient_admin)
        api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {"expectedVersion": role["version"], "reason": "真实共享来源组织验收"}, recipient_admin)
        facts["roles"].append(role["id"])
    actor_member = api("POST", "/api/admin/system/authorization/members", {"account": actor_name, "roleIds": [facts["roles"][0]]}, recipient_admin)
    actor_token = enter(actor_registration["platformTokens"]["accessToken"], RECIPIENT_TENANT)["tokens"]["accessToken"]
    facts["source_membership_count"] = int(sql("SELECT COUNT(*) FROM sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id "
        f"WHERE sm.account_id={actor_registration['accountId']} AND tm.tenant_id={SOURCE_TENANT}")[0][0])
    assert facts["source_membership_count"] == 0
    base = f"/api/runtime/modules/{code}/records"
    original_fields = {"subject": "来源原主题", "visible_person": owner_entry["systemMemberId"],
        "visible_department": source_root["id"], "hidden_person": hidden_member["systemMemberId"],
        "hidden_department": hidden_dept["id"], "secret_text": "保留受限备注"}
    for label in ["改前证据", "修复验收"]:
        record = api("POST", base, {"title": "共享组织验收·" + label, "status": "ACTIVE", "ownerMemberId": owner_entry["systemMemberId"],
            "departmentId": source_root["id"], "participantMemberIds": [], "fields": original_fields}, source_admin)
        facts["records"].append(record["id"])
        share = api("POST", f"{base}/{record['id']}/shares", {"targetTenantId": RECIPIENT_TENANT,
            "allowedActions": ["LIST", "DETAIL", "UPDATE", "TRANSFER", custom["code"]]}, source_admin)
        facts["shares"].append({"record": record["id"], "share": share["id"]})
        save()
    baseline_id, record_id = facts["records"]
    baseline = api("GET", f"{base}/{baseline_id}", token=actor_token)
    assert "organizationLabels" not in baseline
    before = values(baseline_id)
    result = api("PUT", f"{base}/{baseline_id}", update_body(baseline, {key: value for key, value in original_fields.items()
        if key in {"subject", "visible_person", "visible_department"}}), actor_token)
    after = values(baseline_id)
    assert "hidden_person" in before and "hidden_person" not in after and "secret_text" not in after
    facts["baseline"] = {"organization_labels_missing": True, "hidden_and_nonwritable_fields_erased": True,
        "record": baseline_id, "before_field_codes": sorted(before), "after_field_codes": sorted(after)}
    save()
    print("PREPARED:真实改前缺失名称及受限字段误清空已记录；同一进程等待新版本 verify。", flush=True)
    assert input().strip() == "verify"
    custom = api("POST", f"/api/admin/module-config/modules/{module_id}/actions", {"name": "选择来源归属字段", "location": "DETAIL_MORE",
        "parameterFieldCodes": ["visible_person", "visible_department"], "confirmation": True,
        "confirmationText": "确认使用来源租户组织对象"}, source_admin)
    facts["custom_action"] = custom["code"]
    draft = api("GET", f"/api/admin/module-config/modules/{module_id}/draft", token=source_admin)
    api("POST", f"/api/admin/module-config/modules/{module_id}/publish", {"expectedDraftRevision": draft["module"]["draftRevision"]}, source_admin)
    custom_role_request = {**full_role_request, "name": "共享组织验收·含真实手动动作",
        "permissions": full_role_request["permissions"] + [{"resourceType": "MODULE", "resourceCode": code,
            "actionCode": custom["code"], "dataScopeType": "ALL"}]}
    role = api("POST", "/api/admin/system/authorization/roles", custom_role_request, recipient_admin)
    api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {"expectedVersion": role["version"], "reason": "真实组织手动动作权限"}, recipient_admin)
    facts["roles"].append(role["id"])
    actor_token = assign(role["id"])
    current_share = next(row for row in api("GET", f"{base}/{record_id}/shares", token=source_admin) if row["id"] == facts["shares"][1]["share"])
    api("POST", f"{base}/{record_id}/shares", {"targetTenantId": RECIPIENT_TENANT, "allowedActions": ["LIST", "DETAIL", "UPDATE", "TRANSFER", custom["code"]], "version": current_share["version"]}, source_admin)
    save()
    detail = api("GET", f"{base}/{record_id}", token=actor_token)
    labels = detail["organizationLabels"]
    assert labels["members"][str(owner_entry["systemMemberId"])] and labels["departments"][str(source_root["id"])]
    assert str(hidden_member["systemMemberId"]) not in labels["members"]
    assert str(hidden_dept["id"]) not in labels["departments"]
    assert detail["fields"]["hidden_person"] == "***" and "hidden_department" not in detail["fields"]
    ordinary_directory = api("GET", "/api/system-directory?tenantId=56", token=actor_token)
    assert all(person["systemMemberId"] != hidden_member["systemMemberId"] for person in ordinary_directory["people"])
    facts["cases"].append({"case": "source_labels_scoped_to_visible_references", "labels": labels,
        "hidden_and_masked_names_absent": True, "foreign_tenant_query_parameter_ignored": True})
    for action, field_code in [("UPDATE", "visible_person"), ("UPDATE", "visible_department"), ("TRANSFER", None),
                               (custom["code"], "visible_person")]:
        options = api("GET", f"{base}/{record_id}/organization-options?actionCode={action}" + (f"&fieldCode={field_code}" if field_code else ""), token=actor_token)
        assert all(person["systemMemberId"] != actor_member["systemMemberId"] for person in options["people"])
        assert all(not person.get("accountId") and not person.get("roleNames") and not person.get("managerTenantMemberId")
            and not person.get("positionTitle") and not person.get("tenantAdmin") for person in options["people"])
        assert options["people"] or options["departments"]
        facts["cases"].append({"case": "source_write_options", "action": action, "field": field_code,
            "member_ids": [person["systemMemberId"] for person in options["people"]],
            "department_ids": [dept["id"] for dept in options["departments"]], "private_properties_absent": True})
    denied = deny("GET", f"{base}/{record_id}/organization-options?actionCode=UPDATE&fieldCode=hidden_person", None,
        actor_token, (403, "PERMISSION_DENIED"))
    before = values(record_id)
    edit_fields = {"subject": "来源编辑后主题", "visible_person": hidden_member["systemMemberId"], "visible_department": hidden_dept["id"]}
    edited = api("PUT", f"{base}/{record_id}", update_body(detail, edit_fields), actor_token, "source-organization-" + secrets.token_hex(5))
    # 动作回执中的value；普通HTTP返回不能代替数据库字段保留检查。
    edited = edited["value"] if "value" in edited else edited
    after = values(record_id)
    assert all(after[key] == before[key] for key in ["hidden_person", "hidden_department", "secret_text"])
    assert after["visible_person"] == str(hidden_member["systemMemberId"])
    facts["cases"].append({"case": "shared_update_preserves_restricted_values", "nonwritable_options_denied": denied,
        "database_preserved_field_codes": ["hidden_person", "hidden_department", "secret_text"], "record_version": edited["version"]})
    null_body = update_body(edited, {**edit_fields, "secret_text": None})
    denied_null = deny("PUT", f"{base}/{record_id}", null_body, actor_token, (422, "FIELD_VALIDATION_FAILED"))
    assert values(record_id) == after
    wrong_body = update_body(edited, {**edit_fields, "visible_person": actor_member["systemMemberId"]})
    wrong_source = deny("PUT", f"{base}/{record_id}", wrong_body, actor_token, (422, "FIELD_VALIDATION_FAILED"))
    assert values(record_id) == after
    facts["cases"].append({"case": "null_write_and_foreign_source_member_denied", "null": denied_null, "foreign_member": wrong_source, "no_partial_write": True})
    for channel in ["APPLICATION", "FILE", "FLOW"]:
        response = api("GET", f"{base}/{record_id}/channels/{channel}", token=actor_token)
        assert "secret_text" not in response["fields"] and "hidden_department" not in response["fields"]
        assert response["fields"]["hidden_person"] == "***"
        facts["cases"].append({"case": "channel_intersection", "channel": channel, "secret_absent": True, "masked_member_preserved": True})
    # READONLY不能通过选项端点获得来源目录。
    readonly_token = assign(facts["roles"][1])
    readonly = deny("GET", f"{base}/{record_id}/organization-options?actionCode=UPDATE", None, readonly_token, (403, "PERMISSION_DENIED"))
    actor_token = assign(facts["roles"][0])
    facts["cases"].append({"case": "readonly_actor_denied_source_options", **readonly})
    facts["business_verification"] = "verified_api_database"
    save()
    print("VERIFIED:来源名称、字段保留/拒绝、组织选项、渠道交集通过；等待页面复核与 cleanup。", flush=True)
    command = input().strip()
    if command.startswith("browser="):
        browser_username = command.split("=", 1)[1]
        assert re.fullmatch(r"shared_org_browser_[a-z0-9]+", browser_username)
        browser_account = sql(f"SELECT a.id,s.id FROM plat_account a JOIN sys_system s ON s.creator_account_id=a.id WHERE a.username='{browser_username}'")
        assert len(browser_account) == 1
        facts["registrations"].append({"account_id": int(browser_account[0][0]), "system_id": int(browser_account[0][1]), "username": browser_username})
        browser_member = api("POST", "/api/admin/system/authorization/members", {"account": browser_username, "roleIds": [facts["roles"][-1]]}, recipient_admin)
        facts["browser_actor"] = {"account_id": int(browser_account[0][0]), "system_member_id": browser_member["systemMemberId"], "source_membership_count": 0}
        save()
        print("BROWSER_MEMBER_READY:正常成员加入完成，凭证仅在浏览器会话内。", flush=True)
        command = input().strip()
    assert command == "cleanup"
finally:
    if source_admin:
        cleanup()
    save()
