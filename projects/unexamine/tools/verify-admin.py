"""以两个独立浏览器会话验收注册、建系统、权限和 MySQL 持久化。"""

from __future__ import annotations

import json
import os
import secrets
import urllib.error
import urllib.request
from http.cookiejar import CookieJar

import pymysql


BASE = os.environ.get("APP_VERIFY_URL", "http://127.0.0.1:15173/api")


def browser() -> urllib.request.OpenerDirector:
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def call(opener: urllib.request.OpenerDirector, method: str, path: str, body: dict | None = None) -> tuple[int, dict | list | None]:
    payload = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, data=payload, method=method, headers={"Content-Type": "application/json"})
    try:
        with opener.open(request, timeout=12) as response:
            data = response.read()
            return response.status, json.loads(data) if data else None
    except urllib.error.HTTPError as error:
        data = error.read()
        return error.code, json.loads(data) if data else None


def expect(actual: int, wanted: int, label: str) -> None:
    if actual != wanted:
        raise AssertionError(f"{label}: expected {wanted}, got {actual}")


def main() -> None:
    suffix = secrets.token_hex(4)
    owner = browser()
    stranger = browser()
    anonymous = browser()
    user1 = "owner_" + suffix
    user2 = "other_" + suffix
    code = "system_" + suffix
    password = "AdminCheck9!" + suffix

    expect(call(anonymous, "GET", "/systems")[0], 401, "anonymous system list")
    expect(call(owner, "POST", "/auth/register", {"username": user1, "password": password, "displayName": "验收管理员"})[0], 201, "owner registration")
    expect(call(owner, "GET", "/auth/me")[0], 200, "owner Redis session")
    expect(call(stranger, "POST", "/auth/register", {"username": user2, "password": password, "displayName": "其他用户"})[0], 201, "stranger registration")
    expect(call(stranger, "POST", "/auth/logout")[0], 204, "stranger logout")
    expect(call(stranger, "GET", "/auth/me")[0], 401, "logout session deletion")
    expect(call(stranger, "POST", "/auth/login", {"username": user2, "password": "wrong-password"})[0], 401, "wrong password")
    expect(call(stranger, "POST", "/auth/login", {"username": user2, "password": password})[0], 200, "stranger login")

    status, created = call(owner, "POST", "/systems", {"code": code, "name": "验收系统", "description": "验证建系统旅程"})
    expect(status, 201, "create system")
    assert isinstance(created, dict)
    system_id = created["id"]
    expect(call(owner, "POST", "/systems", {"code": code, "name": "重复系统"})[0], 409, "duplicate code")
    expect(call(anonymous, "GET", f"/systems/{system_id}")[0], 401, "anonymous enter")
    expect(call(stranger, "GET", f"/systems/{system_id}")[0], 404, "stranger enter")
    status, other_list = call(stranger, "GET", "/systems")
    expect(status, 200, "stranger list")
    assert other_list == [], "other account must have no systems"
    status, owner_list = call(owner, "GET", "/systems")
    expect(status, 200, "owner list")
    assert isinstance(owner_list, list) and created in owner_list
    status, entered = call(owner, "GET", f"/systems/{system_id}")
    expect(status, 200, "owner enter")
    assert entered == created

    connection = pymysql.connect(host="127.0.0.1", port=3306, user="root", password=os.environ["APP_DB_PASSWORD"],
                                 database="unexamine_r15", charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with connection.cursor() as cursor:
            # 独立读取实际写入的业务关系；不依赖 API 回显判断事务已落库。
            cursor.execute("SELECT id, username, password_hash FROM un_plat_account WHERE username=%s", (user1,))
            account = cursor.fetchone()
            assert account and account["password_hash"].startswith("pbkdf2-sha256:")
            assert password not in account["password_hash"]
            cursor.execute("SELECT id, code, name, owner_account_id FROM un_sys_system WHERE id=%s", (system_id,))
            system = cursor.fetchone()
            assert system and system["code"] == code and system["owner_account_id"] == account["id"]
            cursor.execute("SELECT id, code, main_flag FROM un_sys_tenant WHERE system_id=%s", (system_id,))
            tenant = cursor.fetchone()
            assert tenant and tenant["code"] == "main" and tenant["main_flag"] == 1
            cursor.execute("SELECT id FROM un_sys_member WHERE system_id=%s AND account_id=%s", (system_id, account["id"]))
            member = cursor.fetchone()
            assert member
            cursor.execute("SELECT id FROM un_sys_tenant_member WHERE system_id=%s AND tenant_id=%s AND member_id=%s",
                           (system_id, tenant["id"], member["id"]))
            assert cursor.fetchone()
    finally:
        connection.close()
    print(json.dumps({"status": "passed", "system_id": system_id, "code": code,
                      "checks": ["register", "redis_login", "logout", "password_hash", "create_transaction",
                                 "page_proxy_api", "mysql_readback", "cross_account_isolation", "anonymous_denial"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
