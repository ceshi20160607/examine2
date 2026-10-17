"""用独立账号及 MySQL 读回验收模块草稿、发布快照和访问隔离。"""

from __future__ import annotations

import json
import os
import secrets
import urllib.error
import urllib.request
from http.cookiejar import CookieJar

import pymysql


BASE = "http://127.0.0.1:15173/api"


def browser() -> urllib.request.OpenerDirector:
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def call(opener: urllib.request.OpenerDirector, method: str, path: str, body: dict | None = None) -> tuple[int, dict | list | None]:
    request = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                                     method=method, headers={"Content-Type": "application/json"})
    try:
        with opener.open(request, timeout=12) as response:
            data = response.read()
            return response.status, json.loads(data) if data else None
    except urllib.error.HTTPError as error:
        data = error.read()
        return error.code, json.loads(data) if data else None


def checked(opener: urllib.request.OpenerDirector, method: str, path: str, expected: int, body: dict | None = None):
    status, result = call(opener, method, path, body)
    if status != expected:
        raise AssertionError(f"{method} {path}: expected {expected}, got {status}: {result}")
    return result


def main() -> None:
    suffix = secrets.token_hex(4)
    owner, other = browser(), browser()
    password = "ModuleCheck9!" + suffix
    checked(owner, "POST", "/auth/register", 201,
            {"username": "module_" + suffix, "password": password, "displayName": "模块管理员"})
    checked(other, "POST", "/auth/register", 201,
            {"username": "outsider_" + suffix, "password": password, "displayName": "其他账号"})
    system = checked(owner, "POST", "/systems", 201,
                     {"code": "module_sys_" + suffix, "name": "模块验收系统"})
    sid = system["id"]
    prefix = f"/systems/{sid}"
    checked(other, "POST", prefix + "/module-groups", 404, {"code": "outside", "name": "越权组"})
    group = checked(owner, "POST", prefix + "/module-groups", 201,
                    {"code": "sales_" + suffix, "name": "销售业务"})
    assert group in checked(owner, "GET", prefix + "/module-groups", 200)
    module = checked(owner, "POST", prefix + "/modules", 201,
                     {"groupId": group["id"], "code": "customer_" + suffix, "name": "客户"})
    assert module in checked(owner, "GET", prefix + f"/modules?groupId={group['id']}", 200)
    mid = module["id"]
    mp = prefix + f"/modules/{mid}"
    checked(owner, "POST", mp + "/publish", 400)
    field = checked(owner, "POST", mp + "/fields", 201,
                    {"key": "customer_name", "label": "客户名称", "type": "TEXT", "required": True, "searchable": True})
    assert field in checked(owner, "GET", mp + "/fields", 200)
    version1 = checked(owner, "POST", mp + "/publish", 200)
    assert version1["number"] == 1
    runtime1 = checked(owner, "GET", mp + "/runtime", 200)
    assert runtime1["snapshot"]["fields"][0]["label"] == "客户名称"
    assert runtime1["snapshot"]["menus"][0]["name"] == "客户"
    assert runtime1["snapshot"]["groupName"] == "销售业务"
    navigation = checked(owner, "GET", prefix + "/runtime-navigation", 200)
    assert len(navigation) == 1 and navigation[0]["moduleId"] == mid
    checked(other, "GET", prefix + "/runtime-navigation", 404)
    checked(other, "PUT", mp + f"/fields/{field['id']}", 404, {"label": "越权修改"})
    checked(owner, "PUT", mp + f"/fields/{field['id']}", 200, {"label": "客户姓名"})
    assert checked(owner, "GET", mp + "/runtime", 200)["snapshot"]["fields"][0]["label"] == "客户名称", "draft leaked into published version"
    version2 = checked(owner, "POST", mp + "/publish", 200)
    assert version2["number"] == 2
    assert checked(owner, "GET", mp + "/runtime", 200)["snapshot"]["fields"][0]["label"] == "客户姓名"

    connection = pymysql.connect(host="127.0.0.1", port=3306, user="root", password=os.environ["APP_DB_PASSWORD"],
                                 database="unexamine_r15", charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with connection.cursor() as cursor:
            # 按主键独立检查版本快照是否各自固定，并核对模块组、默认菜单和动作。
            cursor.execute("SELECT snapshot_json FROM un_module_version WHERE module_id=%s ORDER BY version_number", (mid,))
            versions = cursor.fetchall()
            assert len(versions) == 2
            assert json.loads(versions[0]["snapshot_json"])["fields"][0]["label"] == "客户名称"
            assert json.loads(versions[1]["snapshot_json"])["fields"][0]["label"] == "客户姓名"
            cursor.execute("SELECT published_version_id FROM un_module WHERE id=%s AND group_id=%s AND system_id=%s",
                           (mid, group["id"], sid))
            assert cursor.fetchone()["published_version_id"] == version2["id"]
            cursor.execute("SELECT code FROM un_module_menu WHERE module_id=%s", (mid,))
            assert cursor.fetchone()["code"] == "list"
            cursor.execute("SELECT code FROM un_module_action WHERE module_id=%s ORDER BY sort_order", (mid,))
            assert [row["code"] for row in cursor.fetchall()] == ["create", "view", "update"]
    finally:
        connection.close()
    print(json.dumps({"status": "passed", "system_id": sid, "module_id": mid, "versions": [version1["id"], version2["id"]],
                      "checks": ["group_module_field", "default_menu_actions", "publish_snapshot", "draft_isolation", "republish", "cross_account_denial", "mysql_readback"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
