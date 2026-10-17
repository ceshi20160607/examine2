"""验证动作草稿、发布快照、菜单名称和角色权限同时约束页面 API。"""

from __future__ import annotations

import json
import os
import secrets
import urllib.error
import urllib.request
from http.cookiejar import CookieJar

import pymysql

BASE = "http://127.0.0.1:15173/api"


def browser():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def call(client, method, path, wanted, body=None):
    payload = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, data=payload, method=method,
                                     headers={"Content-Type": "application/json"})
    try:
        with client.open(request, timeout=20) as response:
            status, raw, content_type = response.status, response.read(), response.headers.get_content_type()
    except urllib.error.HTTPError as error:
        status, raw, content_type = error.code, error.read(), error.headers.get_content_type()
    if status != wanted:
        raise AssertionError(f"{method} {path}: wanted {wanted}, got {status}: {raw[:400]}")
    return json.loads(raw) if raw and content_type == "application/json" else raw


def main():
    suffix = secrets.token_hex(4)
    owner, member = browser(), browser()
    password = "ActionCheck9!" + suffix
    names = ["action_owner_" + suffix, "action_member_" + suffix]
    for client, username in zip((owner, member), names):
        call(client, "POST", "/auth/register", 201,
             {"username": username, "password": password, "displayName": username})
    system = call(owner, "POST", "/systems", 201,
                  {"code": "action_" + suffix, "name": "动作配置验收系统"})
    sid = system["id"]
    base = f"/systems/{sid}"
    role = call(owner, "GET", base + "/roles", 200)[0]
    group = call(owner, "POST", base + "/module-groups", 201, {"code": "business", "name": "业务"})
    module = call(owner, "POST", base + "/modules", 201,
                  {"groupId": group["id"], "code": "orders", "name": "订单"})
    mid = module["id"]
    path = base + f"/modules/{mid}"
    assert {item["code"] for item in call(owner, "GET", path + "/actions", 200)} == {
        "create", "view", "update", "export", "import", "submit_approval"}
    call(member, "GET", path + "/actions", 404)
    call(owner, "PUT", path + "/actions/view", 400, {"enabled": False})
    call(owner, "POST", path + "/fields", 201,
         {"key": "title", "label": "订单名称", "type": "TEXT", "required": True, "searchable": True})
    call(owner, "POST", path + "/publish", 200)
    call(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
         {"moduleId": mid, "canView": True, "canCreate": True, "canUpdate": True,
          "canDelete": False, "canExport": True})
    call(owner, "POST", base + "/members/invite", 201,
         {"username": names[1], "roleId": role["id"]})
    record_path = path + "/records"
    first = call(member, "POST", record_path, 201, {"values": {"title": "首条订单"}})
    runtime = call(member, "GET", path + "/runtime", 200)
    assert runtime["versionNumber"] == 1 and all(runtime[key] for key in
        ("canCreate", "canUpdate", "canExport", "canImport", "canSubmitApproval"))

    for code in ("export", "import"):
        call(owner, "PUT", path + "/actions/" + code, 200, {"enabled": False})
    assert call(member, "GET", path + "/runtime", 200)["canImport"]
    assert call(member, "GET", path + "/import-jobs/template", 200)
    call(owner, "POST", path + "/publish", 200)
    runtime = call(member, "GET", path + "/runtime", 200)
    assert runtime["versionNumber"] == 2 and not runtime["canExport"] and not runtime["canImport"]
    call(member, "GET", path + "/import-jobs/template", 404)
    call(member, "POST", path + "/export-jobs", 404,
         {"columnKeys": ["title"]})
    call(owner, "GET", path + "/import-jobs/template", 404)

    call(owner, "PUT", path + "/menu", 200, {"name": "订单办理"})
    for code in ("create", "update", "submit_approval"):
        call(owner, "PUT", path + "/actions/" + code, 200, {"enabled": False})
    assert call(member, "GET", path + "/runtime", 200)["canCreate"]
    assert call(member, "GET", base + "/runtime-navigation", 200)[0]["snapshot"]["menus"][0]["name"] == "订单"
    call(owner, "POST", path + "/publish", 200)
    runtime = call(member, "GET", path + "/runtime", 200)
    assert runtime["versionNumber"] == 3 and runtime["snapshot"]["menus"][0]["name"] == "订单办理"
    assert not runtime["canCreate"] and not runtime["canUpdate"] and not runtime["canSubmitApproval"]
    assert call(member, "GET", record_path + f"/{first['id']}", 200)["title"] == "首条订单"
    call(member, "POST", record_path, 404, {"values": {"title": "应被拒绝"}})
    call(member, "PUT", record_path + f"/{first['id']}", 404,
         {"expectedVersion": first["version"], "values": {"title": "不应编辑"}})
    call(member, "POST", record_path + f"/{first['id']}/submit-approval", 404)
    call(owner, "POST", record_path, 404, {"values": {"title": "所属人也不能绕过关闭动作"}})

    for code in ("create", "update", "import", "export", "submit_approval"):
        call(owner, "PUT", path + "/actions/" + code, 200, {"enabled": True})
    call(owner, "POST", path + "/publish", 200)
    assert call(member, "GET", path + "/runtime", 200)["canExport"]
    call(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
         {"moduleId": mid, "canView": True, "canCreate": True, "canUpdate": True,
          "canDelete": False, "canExport": False})
    assert not call(member, "GET", path + "/runtime", 200)["canExport"]
    assert call(owner, "GET", path + "/runtime", 200)["canExport"]
    call(owner, "POST", base + f"/roles/{role['id']}/grants", 400,
         {"moduleId": mid, "canView": False, "canCreate": True, "canUpdate": True,
          "canDelete": False, "canExport": True})
    call(member, "POST", path + "/export-jobs", 404, {"columnKeys": ["title"]})

    db = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                         password=os.environ["APP_DB_PASSWORD"], database="unexamine_r15",
                         charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with db.cursor() as cursor:
            cursor.execute("SELECT code,enabled_flag FROM un_module_action WHERE module_id=%s", (mid,))
            assert len(cursor.fetchall()) == 6
            cursor.execute("SELECT version_number,snapshot_json FROM un_module_version WHERE module_id=%s ORDER BY version_number", (mid,))
            versions = cursor.fetchall()
            assert len(versions) == 4
            assert "export" in {a["code"] for a in json.loads(versions[0]["snapshot_json"])["actions"]}
            assert "export" not in {a["code"] for a in json.loads(versions[1]["snapshot_json"])["actions"]}
            assert json.loads(versions[2]["snapshot_json"])["menus"][0]["name"] == "订单办理"
            cursor.execute("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")
            assert cursor.fetchone() == {"version": "8", "success": 1}
            cursor.execute("""SELECT COUNT(*) AS c FROM un_module m
                              WHERE m.id < %s AND EXISTS
                                (SELECT 1 FROM un_module_action a WHERE a.module_id=m.id)
                              AND (NOT EXISTS (SELECT 1 FROM un_module_action a WHERE a.module_id=m.id AND a.code='import')
                                   OR NOT EXISTS (SELECT 1 FROM un_module_action a WHERE a.module_id=m.id AND a.code='submit_approval'))""", (mid,))
            assert cursor.fetchone()["c"] == 0
    finally:
        db.close()
    print(json.dumps({"status": "passed", "system_id": sid, "module_id": mid,
                      "checks": ["draft_action", "immutable_publish", "runtime_visibility", "backend_denial",
                                 "owner_cannot_bypass", "menu_publish", "role_intersection", "view_gate",
                                 "migration_backfill", "mysql_readback", "flyway_v8"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
