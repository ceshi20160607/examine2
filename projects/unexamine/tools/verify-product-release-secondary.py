"""在 S19 同一系统的次租户验证成员准入、主租户配置复用和业务数据隔离。"""

import json
import os
import runpy
import sys
from pathlib import Path

import pymysql


ROOT = Path(os.environ.get("S19_WORK_ROOT", str(Path(__file__).resolve().parents[3])))
EVIDENCE = ROOT / "projects/unexamine/ai/evidence/s19-secondary-tenant-readback.json"
MEMBER = ROOT / "projects/unexamine/ai/evidence/s19-member-record-readback.json"
api = runpy.run_path(str(Path(__file__).with_name("verify-product-release-member.py")))
success = api["success"]
request = api["request"]
login = api["login"]
enter = api["enter"]


def sessions():
    admin_home = login("admin", os.environ["S19_ADMIN_PASSWORD"])
    member_home = login("s19_member", os.environ["S19_USER_PASSWORD"])
    tenants = success("/api/admin/system/tenants", token=enter(1, admin_home))
    branch = next((item for item in tenants if item["code"] == "release_branch"), None)
    if branch is None:
        raise AssertionError("Browser-created secondary workspace is missing")
    branch_id = branch["id"]
    admin_branch = success(f"/api/systems/1/tenants/{branch_id}/enter", "POST", {}, admin_home)["tokens"]["accessToken"]
    return admin_home, member_home, admin_branch, branch_id


def setup():
    if EVIDENCE.exists():
        raise AssertionError("Secondary tenant evidence already exists")
    admin_home, member_home, admin, branch_id = sessions()
    original = json.loads(MEMBER.read_text(encoding="utf-8"))["records"]["客户记录"]
    module_code = original["moduleCode"]
    status, denied = request(f"/api/systems/1/tenants/{branch_id}/enter", "POST", {}, member_home)
    if status != 403 or denied.get("code") == "OK":
        raise AssertionError(f"Unassigned member entered secondary tenant: {status} {denied}")
    inherited = success(f"/api/runtime/modules/{module_code}/configuration", token=admin)
    main_admin = enter(1, admin_home)
    modules = success("/api/admin/module-config", token=main_admin)["modules"]
    expected_module = next((item for item in modules if item["code"] == module_code), None)
    if expected_module is None or inherited["moduleId"] != expected_module["id"]:
        raise AssertionError("Main tenant's published customer module is unavailable")
    branch_list = success(f"/api/runtime/modules/{module_code}/records", token=admin)
    if any(item["id"] == original["recordId"] for item in branch_list["records"]):
        raise AssertionError("Main tenant customer leaked into secondary tenant")
    role = success("/api/admin/system/authorization/roles", "POST", {
        "code": "s19_branch_operator", "name": "次空间业务成员",
        "permissions": [{"resourceType": "MODULE", "resourceCode": module_code,
                         "actionCode": action, "dataScopeType": "ALL" if action == "CREATE" else "SELF",
                         "dataScopeJson": "{}"} for action in ("LIST", "DETAIL", "CREATE", "UPDATE")],
        "fieldPolicies": [{"resourceCode": module_code, "fieldCode": original["fieldCode"],
                           "channel": "PAGE", "readable": True, "writable": True,
                           "maskStrategy": None}]}, admin)
    role = success(f"/api/admin/system/authorization/roles/{role['id']}/publish", "POST", {
        "reason": "S19 次空间业务成员授权", "expectedVersion": role["version"]}, admin)
    assigned = success("/api/admin/system/authorization/members", "POST", {
        "account": "s19_member", "roleIds": [role["id"]]}, admin)
    member_entry = success(f"/api/systems/1/tenants/{branch_id}/enter", "POST", {}, member_home)
    member = member_entry["tokens"]["accessToken"]
    status, denied_record = request(f"/api/runtime/modules/{module_code}/records/{original['recordId']}",
                                    token=member)
    if status not in (403, 404) or denied_record.get("code") == "OK":
        raise AssertionError(f"Member read main tenant record: {status} {denied_record}")
    created = success(f"/api/runtime/modules/{module_code}/records", "POST", {
        "title": "S19 次空间客户", "status": "ACTIVE",
        "ownerMemberId": member_entry["systemMemberId"], "participantMemberIds": [],
        "fields": {original["fieldCode"]: "次空间独立记录"}}, member)
    detail = success(f"/api/runtime/modules/{module_code}/records/{created['id']}", token=member)
    if detail["fields"].get(original["fieldCode"]) != "次空间独立记录":
        raise AssertionError("Secondary tenant field readback failed")
    main = enter(1, member_home)
    status, main_denial = request(f"/api/runtime/modules/{module_code}/records/{created['id']}", token=main)
    if status not in (403, 404) or main_denial.get("code") == "OK":
        raise AssertionError(f"Secondary tenant record leaked into main tenant: {status} {main_denial}")
    state = {"step": "S19.1", "status": "api_verified_browser_pending", "systemId": 1,
             "mainTenantId": 1, "secondaryTenantId": branch_id, "moduleCode": module_code,
             "mainRecordId": original["recordId"], "secondaryRecordId": created["id"],
             "roleId": role["id"], "systemMemberId": assigned["systemMemberId"],
             "tenantMemberId": member_entry["tenantMemberId"],
             "unassignedDenied": denied.get("code"), "mainRecordDenied": denied_record.get("code"),
             "secondaryRecordDeniedFromMain": main_denial.get("code")}
    EVIDENCE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state, ensure_ascii=False))


def readback():
    state = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    _, member_home, _, branch_id = sessions()
    branch = success(f"/api/systems/1/tenants/{branch_id}/enter", "POST", {}, member_home)["tokens"]["accessToken"]
    main = enter(1, member_home)
    for token, visible, hidden in ((branch, state["secondaryRecordId"], state["mainRecordId"]),
                                   (main, state["mainRecordId"], state["secondaryRecordId"])):
        listed = success(f"/api/runtime/modules/{state['moduleCode']}/records", token=token)
        ids = {item["id"] for item in listed["records"]}
        if visible not in ids or hidden in ids:
            raise AssertionError(f"Tenant record list leaked or lost data: {ids}")
    browser = json.loads((ROOT / "projects/unexamine/ai/evidence/s19-secondary-member-browser.json")
                         .read_text(encoding="utf-8"))
    if not browser["branchRecordVisible"] or browser["mainRecordVisible"]:
        raise AssertionError("Secondary tenant browser readback failed")
    connection = pymysql.connect(host="127.0.0.1", port=int(os.environ.get("S19_DB_PORT", "33078")),
                                 user=os.environ.get("APP_DB_USERNAME", "unexamine"),
                                 password=os.environ["APP_DB_PASSWORD"],
                                 database=os.environ.get("S19_DB_NAME", "unexamine"))
    with connection, connection.cursor() as cursor:
        cursor.execute("SELECT id,tenant_id,title FROM un_biz_record WHERE id IN (%s,%s) ORDER BY id",
                       (state["mainRecordId"], state["secondaryRecordId"]))
        rows = cursor.fetchall()
    if rows != ((state["mainRecordId"], 1, "S19 上海客户"),
                (state["secondaryRecordId"], state["secondaryTenantId"], "S19 次空间客户")):
        raise AssertionError(f"Tenant ownership changed in database: {rows}")
    state.update({"status": "restart_browser_api_database_verified", "browser": browser,
                  "databaseRecords": rows})
    EVIDENCE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "secondaryRecordId": state["secondaryRecordId"]},
                     ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("setup", "readback"):
        raise SystemExit("usage: verify-product-release-secondary.py setup|readback")
    {"setup": setup, "readback": readback}[sys.argv[1]]()
