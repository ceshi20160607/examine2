"""用真实双账号验证系统邀请、角色授权与菜单即时隔离。"""

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


def request(opener, method, path, expected, body=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                                 method=method, headers={"Content-Type": "application/json"})
    try:
        with opener.open(req, timeout=12) as response:
            result = response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        result = error.read()
        status = error.code
    if status != expected:
        raise AssertionError(f"{method} {path}: wanted {expected}, got {status}: {result[:300]}")
    return json.loads(result) if result else None


def make_module(owner, base, group_id, code, label):
    module = request(owner, "POST", base + "/modules", 201, {"groupId": group_id, "code": code, "name": label})
    path = base + f"/modules/{module['id']}"
    request(owner, "POST", path + "/fields", 201,
            {"key": "title", "label": "标题", "type": "TEXT", "required": True, "searchable": True})
    request(owner, "POST", path + "/publish", 200)
    return module["id"]


def main():
    suffix = secrets.token_hex(4)
    owner, member, outsider = browser(), browser(), browser()
    password = "MemberCheck9!" + suffix
    owner_name, member_name = "owner_" + suffix, "member_" + suffix
    for opener, username in [(owner, owner_name), (member, member_name), (outsider, "outside_" + suffix)]:
        request(opener, "POST", "/auth/register", 201, {"username": username, "password": password, "displayName": username})
    system = request(owner, "POST", "/systems", 201, {"code": "access_" + suffix, "name": "权限验收系统"})
    sid = system["id"]
    base = f"/systems/{sid}"
    assert system["owner"] is True
    request(member, "GET", base, 404)
    roles = request(owner, "GET", base + "/roles", 200)
    assert len(roles) == 1 and roles[0]["code"] == "member"
    role = request(owner, "POST", base + "/roles", 201, {"code": "operator", "name": "业务操作员"})
    group = request(owner, "POST", base + "/module-groups", 201, {"code": "business", "name": "业务"})
    allowed = make_module(owner, base, group["id"], "allowed", "已授权模块")
    blocked = make_module(owner, base, group["id"], "blocked", "未授权模块")
    request(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
            {"moduleId": allowed, "canView": True, "canCreate": True, "canUpdate": True, "canDelete": False})
    request(owner, "POST", base + "/members/invite", 201, {"username": member_name, "roleId": role["id"]})
    assert len(request(owner, "GET", base + "/members", 200)) == 2
    assert request(member, "GET", base, 200)["owner"] is False
    assert any(item["id"] == sid for item in request(member, "GET", "/systems", 200))
    navigation = request(member, "GET", base + "/runtime-navigation", 200)
    assert [item["moduleId"] for item in navigation] == [allowed]
    request(member, "GET", base + f"/modules/{allowed}/runtime", 200)
    request(member, "GET", base + f"/modules/{blocked}/runtime", 404)
    request(member, "GET", base + "/module-groups", 403)
    request(member, "POST", base + f"/roles/{role['id']}/grants", 403,
            {"moduleId": blocked, "canView": True, "canCreate": False, "canUpdate": False, "canDelete": False})
    request(outsider, "GET", base + "/runtime-navigation", 404)
    request(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
            {"moduleId": allowed, "canView": False, "canCreate": False, "canUpdate": False, "canDelete": False})
    assert request(member, "GET", base + "/runtime-navigation", 200) == [], "permission change must take effect immediately"
    request(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
            {"moduleId": allowed, "canView": True, "canCreate": True, "canUpdate": True, "canDelete": False})

    db = pymysql.connect(host="127.0.0.1", port=3306, user="root", password=os.environ["APP_DB_PASSWORD"],
                         database="unexamine_r15", charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with db.cursor() as cursor:
            # 核实角色、成员、租户成员、角色关系与菜单授权都归属同一系统主租户。
            cursor.execute("SELECT id FROM un_sys_tenant WHERE system_id=%s AND main_flag=1", (sid,))
            tenant = cursor.fetchone()["id"]
            cursor.execute("SELECT id FROM un_sys_member WHERE system_id=%s AND account_id=(SELECT id FROM un_plat_account WHERE username=%s)",
                           (sid, member_name))
            member_id = cursor.fetchone()["id"]
            cursor.execute("SELECT id FROM un_sys_tenant_member WHERE system_id=%s AND tenant_id=%s AND member_id=%s", (sid, tenant, member_id))
            assert cursor.fetchone()
            cursor.execute("SELECT id FROM un_sys_member_role WHERE tenant_id=%s AND member_id=%s AND role_id=%s", (tenant, member_id, role["id"]))
            assert cursor.fetchone()
            cursor.execute("SELECT g.can_view, g.can_create, g.can_update FROM un_sys_role_menu g JOIN un_module_menu m ON m.id=g.menu_id WHERE g.role_id=%s AND m.module_id=%s",
                           (role["id"], allowed))
            grant = cursor.fetchone()
            assert grant and all(grant.values())
            cursor.execute("SELECT COUNT(*) AS missing FROM un_sys_tenant t LEFT JOIN un_sys_role r ON r.tenant_id=t.id AND r.code='member' WHERE t.main_flag=1 AND r.id IS NULL")
            assert cursor.fetchone()["missing"] == 0
    finally:
        db.close()
    print(json.dumps({"status": "passed", "system_id": sid, "allowed_module_id": allowed, "blocked_module_id": blocked,
                      "checks": ["default_role_migration", "role_create", "invite", "system_tenant_membership", "menu_action_grant", "member_navigation", "unauthorized_denial", "instant_revoke", "mysql_readback"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
