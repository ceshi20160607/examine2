"""在 S19 同一新装候选库中核对普通成员的双模块数据与授权边界。"""

import json
import os
import urllib.error
import urllib.request
from decimal import Decimal
from pathlib import Path


BASE = os.environ.get("S19_API_BASE", "http://127.0.0.1:18083")
ROOT = Path(os.environ.get("S19_WORK_ROOT", str(Path(__file__).resolve().parents[3])))
EVIDENCE = ROOT / "projects/unexamine/ai/evidence/s19-member-record-readback.json"
SYSTEMS = ROOT / "projects/unexamine/ai/evidence/s19-two-systems-browser-readback.json"


def request(path, method="GET", body=None, token=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    item = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(item, timeout=30) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def success(path, method="GET", body=None, token=None):
    status, result = request(path, method, body, token)
    if status != 200 or result.get("code") != "OK":
        raise AssertionError(f"{method} {path}: {status} {result}")
    return result["data"]


def login(name, password):
    return success("/api/auth/login", "POST", {"username": name, "password": password})["accessToken"]


def enter(system_id, token):
    return success(f"/api/systems/{system_id}/enter", "POST", {}, token)["tokens"]["accessToken"]


def account(name, password):
    status, result = request("/api/auth/login", "POST", {"username": name, "password": password})
    if status != 200:
        success("/api/auth/register", "POST", {
            "username": name, "password": password, "displayName": name,
            "email": name + "@example.test"})
    return login(name, password)


def main():
    admin_password = os.environ["S19_ADMIN_PASSWORD"]
    user_password = os.environ["S19_USER_PASSWORD"]
    system_id = json.loads(SYSTEMS.read_text(encoding="utf-8"))["systems"][0]["id"]
    admin = enter(system_id, login("admin", admin_password))
    modules = success("/api/admin/module-config", token=admin)["modules"]
    targets = {item["name"]: item for item in modules if item["name"] in ("客户记录", "订单记录")}
    if set(targets) != {"客户记录", "订单记录"}:
        raise AssertionError("Two distinct published modules are required")
    for item in targets.values():
        success(f"/api/admin/module-config/modules/{item['id']}/published", token=admin)
    fields = {}
    for name, item in targets.items():
        draft = success(f"/api/admin/module-config/modules/{item['id']}/draft", token=admin)
        fields[name] = draft["fields"][0]["code"]

    account_tokens = {name: account(name, user_password) for name in
                      ("s19_member", "s19_approver", "s19_unrelated")}
    overview = success("/api/admin/system/authorization", token=admin)
    roles = {}
    for key, actions in (("s19_operator", ("LIST", "DETAIL", "CREATE", "UPDATE", "EXPORT")),
                         ("s19_approver", ("LIST", "DETAIL"))):
        existing = next((item for item in overview["roles"] if item["code"] == key), None)
        if existing is None:
            permissions = [{"resourceType": "MODULE", "resourceCode": module["code"],
                            "actionCode": action, "dataScopeType": "ALL" if action == "CREATE" else "SELF",
                            "dataScopeJson": "{}"}
                           for module in targets.values() for action in actions]
            policies = [{"resourceCode": targets[name]["code"], "fieldCode": code,
                         "channel": channel, "readable": True,
                         "writable": key == "s19_operator", "maskStrategy": None}
                        for name, code in fields.items() for channel in ("PAGE", "FILE")]
            saved = success("/api/admin/system/authorization/roles", "POST", {
                "code": key, "name": "发布验收操作员" if key == "s19_operator" else "发布验收审批员",
                "permissions": permissions, "fieldPolicies": policies}, admin)
            existing = success(f"/api/admin/system/authorization/roles/{saved['id']}/publish", "POST", {
                "reason": "S19 同版产品验收", "expectedVersion": saved["version"]}, admin)
        roles[key] = existing
    overview = success("/api/admin/system/authorization", token=admin)
    member_ids = {}
    for name, key in (("s19_member", "s19_operator"), ("s19_approver", "s19_approver")):
        account_id = success("/api/auth/me", token=account_tokens[name])["accountId"]
        existing = next((item for item in overview["members"] if item["accountId"] == account_id), None)
        if existing is None:
            existing = success("/api/admin/system/authorization/members", "POST", {
                "account": name, "roleIds": [roles[key]["id"]]}, admin)
        member_ids[name] = existing["systemMemberId"]
    member = enter(system_id, account_tokens["s19_member"])
    records = {}
    for name, title, value in (("客户记录", "S19 上海客户", "上海客户"),
                               ("订单记录", "S19 首笔订单", "99.50")):
        code = targets[name]["code"]
        path = f"/api/runtime/modules/{code}/records"
        listed = success(path, token=member)
        existing = next((item for item in listed["records"] if item["title"] == title), None)
        if existing is None:
            existing = success(path, "POST", {
                "title": title, "status": "ACTIVE", "ownerMemberId": member_ids["s19_member"],
                "participantMemberIds": [], "fields": {fields[name]: value}}, member)
        detail = success(path + "/" + str(existing["id"]), token=member)
        final_value = "上海客户已跟进" if name == "客户记录" else value
        if name == "客户记录" and detail["fields"][fields[name]] == value:
            success(path + "/" + str(existing["id"]), "PUT", {
                "title": detail["title"], "recordNumber": detail["recordNumber"],
                "status": detail["status"], "ownerMemberId": detail["ownerMemberId"],
                "departmentId": detail["departmentId"],
                "participantMemberIds": detail["participantMemberIds"],
                "fields": {**detail["fields"], fields[name]: final_value},
                "version": detail["version"]}, member)
            detail = success(path + "/" + str(existing["id"]), token=member)
        actual = detail["fields"][fields[name]]
        matches = Decimal(str(actual)) == Decimal(final_value) if name == "订单记录" else actual == final_value
        if not matches:
            raise AssertionError(f"Business field readback mismatch: {name}: {detail['fields']}")
        records[name] = {"moduleCode": code, "recordId": existing["id"],
                         "fieldCode": fields[name], "fieldValue": final_value}
    status, denied = request(f"/api/systems/{system_id}/enter", "POST", {}, account_tokens["s19_unrelated"])
    if status not in (403, 404) or denied.get("code") == "OK":
        raise AssertionError(f"Unrelated account entered system: {status} {denied}")
    evidence = {"step": "S19.1", "systemId": system_id, "memberIds": member_ids,
                "roles": {key: item["id"] for key, item in roles.items()}, "records": records,
                "unrelatedDenied": {"status": status, "code": denied.get("code")},
                "status": "api_verified_browser_pending"}
    EVIDENCE.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(evidence, ensure_ascii=False))


if __name__ == "__main__":
    main()
