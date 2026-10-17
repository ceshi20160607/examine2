"""在 S19 的两个现有系统之间验收目标应用授权、字段裁剪和撤权。"""

import json
import os
import runpy
import secrets
import sys
import urllib.error
import urllib.request
from pathlib import Path

import pymysql


ROOT = Path(os.environ.get("S19_WORK_ROOT", str(Path(__file__).resolve().parents[3])))
EVIDENCE = ROOT / "projects/unexamine/ai/evidence/s19-cross-system-readback.json"
api = runpy.run_path(str(Path(__file__).with_name("verify-product-release-member.py")))
success = api["success"]
request = api["request"]
login = api["login"]
enter = api["enter"]
BASE = api["BASE"]


def sessions():
    admin_home = login("admin", os.environ["S19_ADMIN_PASSWORD"])
    member_home = login("s19_member", os.environ["S19_USER_PASSWORD"])
    unrelated_home = login("s19_unrelated", os.environ["S19_USER_PASSWORD"])
    return {"target": enter(2, admin_home), "source": enter(1, member_home),
            "unrelated": unrelated_home}


def internal_call(state, token, resource_type, resource_id, action, payload,
                  target_system=None, scope=None):
    body = {"resourceType": resource_type, "resourceId": resource_id, "actionCode": action,
            "targetSystemId": target_system if target_system is not None else state["targetSystemId"],
            "targetTenantId": state["targetTenantId"],
            "requestedDataScope": scope if scope is not None else state["dataScope"],
            "payload": payload}
    content = json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json", "Authorization": "Bearer " + token,
               "X-Idempotency-Key": "s19-bridge-" + secrets.token_hex(12)}
    call = urllib.request.Request(BASE + f"/api/application-internal-access/v1/applications/{state['applicationId']}/calls",
                                  data=content, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(call, timeout=30) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def accepted(state, token, resource_type, resource_id, action, payload):
    status, result = internal_call(state, token, resource_type, resource_id, action, payload)
    if status != 200 or result.get("code") != "OK":
        raise AssertionError(f"Cross-system {resource_type}:{action} failed: {status} {result}")
    value = result["data"]["result"]
    return value.get("businessResult", value)


def rejected(state, token, resource_type, resource_id, action, payload, code,
             target_system=None, scope=None):
    status, result = internal_call(state, token, resource_type, resource_id, action, payload,
                                   target_system, scope)
    if status != 403 or result.get("code") != code:
        raise AssertionError(f"Cross-system denial mismatch: {status} {result}")
    return code


def setup():
    if EVIDENCE.exists():
        raise AssertionError("S19 target application already has an evidence state")
    target = sessions()["target"]
    current = success("/api/auth/me", token=target)
    tenant_id = current["tenantId"]
    group = success("/api/admin/module-config/groups", "POST", {
        "code": "s19_service", "name": "跨系统服务", "sortOrder": 10}, target)
    module = success("/api/admin/module-config/modules", "POST", {
        "groupId": group["id"], "code": "s19_service_contract", "name": "服务合同"}, target)["module"]
    for code, name in (("public_note", "公开说明"), ("private_note", "内部备注")):
        success(f"/api/admin/module-config/modules/{module['id']}/fields", "POST", {
            "code": code, "name": name, "fieldType": "TEXT", "required": False,
            "sortOrder": 10 if code == "public_note" else 20, "config": {}}, target)
    check = success(f"/api/admin/module-config/modules/{module['id']}/publication-check", token=target)
    if not check["valid"]:
        raise AssertionError(f"Target module publication check failed: {check}")
    draft = success(f"/api/admin/module-config/modules/{module['id']}/draft", token=target)
    success(f"/api/admin/module-config/modules/{module['id']}/publish", "POST",
            {"expectedDraftRevision": draft["module"]["draftRevision"]}, target)
    record = success(f"/api/runtime/modules/{module['code']}/records", "POST", {
        "title": "S19 目标系统合同", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"public_note": "授权给来源系统", "private_note": "仅目标系统内部可见"}}, target)
    flow = success("/api/flows", "POST", {"code": "s19_service_review", "name": "服务合同流程"}, target)
    saved = success(f"/api/flows/{flow['id']}/draft", "PUT", {
        "expectedVersion": 0,
        "nodes": [{"nodeKey": "start", "nodeType": "START", "name": "开始", "positionX": 0,
                   "positionY": 0, "config": {}},
                  {"nodeKey": "end", "nodeType": "END", "name": "结束", "positionX": 200,
                   "positionY": 0, "config": {}}],
        "edges": [{"edgeKey": "to-end", "sourceNodeKey": "start", "targetNodeKey": "end",
                   "priorityOrder": 0, "config": {}}]}, target)
    flow_check = success(f"/api/flows/{flow['id']}/publication-check", token=target)
    if not flow_check["valid"]:
        raise AssertionError(f"Target Flow publication check failed: {flow_check}")
    success(f"/api/flows/{flow['id']}/publish", "POST", {
        "expectedDraftRevision": saved["draftRevision"], "changeSummary": "S19 跨系统流程"}, target)
    application = success("/api/applications", "POST", {
        "code": "s19_target_bridge", "name": "S19 目标系统应用", "applicationType": "SERVICE"}, target)["application"]
    catalog = success(f"/api/applications/{application['id']}/scope-catalog", token=target)
    if len(catalog["members"]) != 1:
        raise AssertionError(f"Target owner scope is ambiguous: {catalog['members']}")
    owner_id = catalog["members"][0]["id"]
    data_scope = {"type": "RESPONSIBLE", "memberIds": [owner_id], "departmentIds": []}
    rate = {"accessChannels": ["INTERNAL"], "maxRequests": 100, "windowSeconds": 600,
            "failureDisableThreshold": 100, "failureWindowSeconds": 3600}

    def grant(resource_type, resource_id, action, fields=None):
        return {"targetType": "SYSTEM", "targetSystemId": 2, "targetTenantId": tenant_id,
                "resourceType": resource_type, "resourceId": resource_id, "actionCode": action,
                "dataScope": data_scope, "rateLimit": rate, "fields": fields or []}

    fields = [{"fieldCode": "public_note", "readable": True, "writable": False,
               "maskStrategy": "NONE"}]
    saved_app = success(f"/api/applications/{application['id']}/draft", "PUT", {
        "expectedVersion": 0, "name": application["name"], "applicationType": "SERVICE",
        "callbacks": [], "grants": [grant("MODULE", module["code"], "LIST", fields),
                                     grant("MODULE", module["code"], "DETAIL", fields),
                                     grant("FLOW", flow["code"], "START")]}, target)
    app_check = success(f"/api/applications/{application['id']}/publication-check", token=target)
    if not app_check["valid"]:
        raise AssertionError(f"Target application publication check failed: {app_check}")
    success(f"/api/applications/{application['id']}/publish", "POST", {
        "expectedDraftRevision": saved_app["draftRevision"], "changeSummary": "S19 跨系统受控访问"}, target)
    state = {"step": "S19.1", "status": "target_published", "sourceSystemId": 1,
             "targetSystemId": 2, "targetTenantId": tenant_id, "moduleId": module["id"],
             "moduleCode": module["code"], "recordId": record["id"],
             "flowId": flow["id"], "flowCode": flow["code"],
             "applicationId": application["id"], "dataScope": data_scope}
    EVIDENCE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state, ensure_ascii=False))


def verify():
    state = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    tokens = sessions()
    source = tokens["source"]
    listed = accepted(state, source, "MODULE", state["moduleCode"], "LIST", {"page": 1, "pageSize": 20})
    if not any(item["id"] == state["recordId"] for item in listed.get("records", listed.get("items", []))):
        raise AssertionError("Source system did not receive target record")
    detail = accepted(state, source, "MODULE", state["moduleCode"], "DETAIL",
                      {"recordId": state["recordId"]})
    if detail["fields"].get("public_note") != "授权给来源系统" \
            or "private_note" in detail["fields"]:
        raise AssertionError(f"Target field trimming failed: {detail['fields']}")
    denied_update = rejected(state, source, "MODULE", state["moduleCode"], "UPDATE",
                             {"recordId": state["recordId"]}, "APPLICATION_GRANT_DENIED")
    denied_scope = rejected(state, source, "MODULE", state["moduleCode"], "LIST", {},
                            "APPLICATION_TARGET_SCOPE_EXPANSION", target_system=999)
    denied_user = rejected(state, tokens["unrelated"], "MODULE", state["moduleCode"], "LIST", {},
                           "APPLICATION_INTERNAL_CONTEXT_DENIED")
    started = accepted(state, source, "FLOW", state["flowCode"], "START", {
        "title": "S19 来源系统发起目标流程", "variables": {}})
    instance = started["instance"]
    if instance["status"] != "COMPLETED":
        raise AssertionError(f"Target Flow did not complete: {instance['status']}")
    state.update({"status": "cross_system_api_verified", "flowInstanceId": instance["id"],
                  "visibleFields": sorted(detail["fields"]), "deniedUpdate": denied_update,
                  "deniedScope": denied_scope, "deniedUnrelated": denied_user})
    EVIDENCE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state, ensure_ascii=False))


def readback():
    state = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    tokens = sessions()
    member_home = login("s19_member", os.environ["S19_USER_PASSWORD"])
    direct_status, direct_denial = request("/api/systems/2/enter", "POST", {}, member_home)
    if direct_status != 403 or direct_denial.get("code") == "OK":
        raise AssertionError(f"Source member entered target system directly: {direct_status} {direct_denial}")
    listed = accepted(state, tokens["source"], "MODULE", state["moduleCode"], "LIST",
                      {"page": 1, "pageSize": 20})
    if not any(item["id"] == state["recordId"] for item in listed.get("records", listed.get("items", []))):
        raise AssertionError("Cross-system record disappeared after restart")
    detail = accepted(state, tokens["source"], "MODULE", state["moduleCode"], "DETAIL",
                      {"recordId": state["recordId"]})
    if detail["fields"].get("public_note") != "授权给来源系统" or "private_note" in detail["fields"]:
        raise AssertionError("Cross-system field grant changed after restart")
    instance = success(f"/api/flow-runtime/instances/{state['flowInstanceId']}", token=tokens["target"])
    if instance["status"] != "COMPLETED":
        raise AssertionError("Target Flow instance changed after restart")
    connection = pymysql.connect(host="127.0.0.1", port=int(os.environ.get("S19_DB_PORT", "33078")),
                                 user=os.environ.get("APP_DB_USERNAME", "unexamine"),
                                 password=os.environ["APP_DB_PASSWORD"],
                                 database=os.environ.get("S19_DB_NAME", "unexamine"))
    with connection, connection.cursor() as cursor:
        cursor.execute("SELECT system_id,tenant_id,title FROM un_biz_record WHERE id=%s", (state["recordId"],))
        record_row = cursor.fetchone()
        cursor.execute("SELECT system_id,tenant_id,status FROM un_flow_instance WHERE id=%s",
                       (state["flowInstanceId"],))
        flow_row = cursor.fetchone()
    if record_row != (2, state["targetTenantId"], "S19 目标系统合同") \
            or flow_row != (2, state["targetTenantId"], "COMPLETED"):
        raise AssertionError(f"Target database readback mismatch: {record_row}, {flow_row}")
    browser = json.loads((ROOT / "projects/unexamine/ai/evidence/s19-target-browser-readback.json")
                         .read_text(encoding="utf-8"))
    if not browser["application"]["visible"] or not browser["runtime"]["visible"]:
        raise AssertionError("Target system browser readback is missing")
    state.update({"status": "restart_browser_api_database_verified",
                  "databaseRecord": record_row, "databaseFlow": flow_row,
                  "directTargetEntryDenied": direct_denial.get("code"),
                  "browser": {"applicationUrl": browser["application"]["url"],
                              "runtimeUrl": browser["runtime"]["url"]}})
    EVIDENCE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "recordId": state["recordId"],
                      "flowInstanceId": state["flowInstanceId"]}, ensure_ascii=False))


def revoke():
    state = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    tokens = sessions()
    success(f"/api/applications/{state['applicationId']}/disable", "POST", {
        "reason": "S19 同版跨系统授权撤销"}, tokens["target"])
    code = rejected(state, tokens["source"], "MODULE", state["moduleCode"], "LIST", {},
                    "APPLICATION_DISABLED")
    state.update({"status": "revocation_verified", "deniedAfterDisable": code})
    EVIDENCE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "deniedAfterDisable": code}, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("setup", "verify", "readback", "revoke"):
        raise SystemExit("usage: verify-product-release-bridge.py setup|verify|readback|revoke")
    {"setup": setup, "verify": verify, "readback": readback, "revoke": revoke}[sys.argv[1]]()
