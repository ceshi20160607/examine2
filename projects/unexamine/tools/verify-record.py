"""沿同一新系统验证成员录入、筛选、编辑、隔离及三表原子落库。"""

from __future__ import annotations

import json
import os
import secrets
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

import pymysql

BASE = "http://127.0.0.1:15173/api"


def browser():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def call(opener, method, path, wanted, body=None):
    request = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                                     method=method, headers={"Content-Type": "application/json"})
    try:
        with opener.open(request, timeout=12) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    if status != wanted:
        raise AssertionError(f"{method} {path}: wanted {wanted}, got {status}: {data[:300]}")
    return json.loads(data) if data else None


def main():
    suffix = secrets.token_hex(4)
    owner, member, another, reader = browser(), browser(), browser(), browser()
    password = "RecordCheck9!" + suffix
    user_member = "record_a_" + suffix
    for opener, username in [(owner, "record_owner_" + suffix), (member, user_member),
                             (another, "record_b_" + suffix), (reader, "record_reader_" + suffix)]:
        call(opener, "POST", "/auth/register", 201,
             {"username": username, "password": password, "displayName": username})
    system = call(owner, "POST", "/systems", 201, {"code": "record_" + suffix, "name": "记录验收系统"})
    sid = system["id"]
    base = f"/systems/{sid}"
    role = call(owner, "GET", base + "/roles", 200)[0]
    group = call(owner, "POST", base + "/module-groups", 201, {"code": "business", "name": "业务"})
    module = call(owner, "POST", base + "/modules", 201,
                  {"groupId": group["id"], "code": "ticket", "name": "工单"})
    mid = module["id"]
    path = base + f"/modules/{mid}"
    call(owner, "POST", path + "/fields", 201,
         {"key": "title", "label": "工单标题", "type": "TEXT", "required": True, "searchable": True})
    call(owner, "POST", path + "/fields", 201,
         {"key": "estimate", "label": "预计金额", "type": "NUMBER", "required": False, "searchable": True})
    call(owner, "POST", path + "/publish", 200)
    call(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
         {"moduleId": mid, "canView": True, "canCreate": True, "canUpdate": True, "canDelete": False})
    for username in [user_member, "record_b_" + suffix]:
        call(owner, "POST", base + "/members/invite", 201, {"username": username, "roleId": role["id"]})
    reader_role = call(owner, "POST", base + "/roles", 201, {"code": "reader", "name": "只读成员"})
    call(owner, "POST", base + f"/roles/{reader_role['id']}/grants", 200,
         {"moduleId": mid, "canView": True, "canCreate": False, "canUpdate": False, "canDelete": False})
    call(owner, "POST", base + "/members/invite", 201,
         {"username": "record_reader_" + suffix, "roleId": reader_role["id"]})
    records = path + "/records"
    call(reader, "POST", records, 404, {"values": {"title": "不应创建"}})
    call(member, "POST", records, 400, {"values": {"estimate": 10}})
    call(member, "POST", records, 400, {"values": {"title": "坏记录", "unknown": "x"}})
    created = call(member, "POST", records, 201, {"values": {"title": "客户 A 工单", "estimate": 120.5}})
    rid = created["id"]
    assert created["title"] == "客户 A 工单" and created["values"]["estimate"] == 120.5
    assert created["recordNo"].startswith("R") and created["version"] == 0
    assert call(member, "GET", records + "?page=1&size=10", 200)["total"] == 1
    title_search = urllib.parse.quote("客户 A")
    assert call(member, "GET", records + "?search=" + title_search, 200)["total"] == 1
    assert call(member, "GET", records + "?filterKey=title&filterValue=" + urllib.parse.quote("客户 A 工单"), 200)["total"] == 1
    assert call(member, "GET", records + "?filterKey=estimate&filterValue=120.5", 200)["total"] == 1
    assert call(member, "GET", records + "?filterKey=title&filterValue=" + urllib.parse.quote("不存在"), 200)["total"] == 0
    assert call(member, "GET", records + f"/{rid}", 200)["values"]["title"] == "客户 A 工单"
    assert call(another, "GET", records, 200)["total"] == 0
    call(another, "GET", records + f"/{rid}", 404)
    call(another, "PUT", records + f"/{rid}", 404, {"expectedVersion": 0, "values": {"title": "越权"}})
    call(reader, "PUT", records + f"/{rid}", 404, {"expectedVersion": 0, "values": {"title": "只读越权"}})
    assert call(owner, "GET", records, 200)["total"] == 1
    updated = call(member, "PUT", records + f"/{rid}", 200,
                   {"expectedVersion": 0, "values": {"title": "客户 A 已处理", "estimate": 150}})
    assert updated["version"] == 1 and updated["values"]["title"] == "客户 A 已处理"
    call(member, "PUT", records + f"/{rid}", 409,
         {"expectedVersion": 0, "values": {"title": "旧版本覆盖"}})
    assert call(member, "GET", records + "?filterKey=title&filterValue=" + urllib.parse.quote("客户 A 已处理"), 200)["total"] == 1
    assert call(member, "GET", records + "?filterKey=title&filterValue=" + urllib.parse.quote("客户 A 工单"), 200)["total"] == 0

    db = pymysql.connect(host="127.0.0.1", port=3306, user="root", password=os.environ["APP_DB_PASSWORD"],
                         database="unexamine_r15", charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with db.cursor() as cursor:
            # 单独核对主记录、两个字段值与两个类型索引；失败请求没有留下第二条主记录。
            cursor.execute("SELECT id, title, record_no, version, owner_member_id FROM un_module_record WHERE module_id=%s", (mid,))
            rows = cursor.fetchall()
            assert len(rows) == 1 and rows[0]["id"] == rid and rows[0]["title"] == "客户 A 已处理"
            assert rows[0]["record_no"] == created["recordNo"] and rows[0]["version"] == 1
            cursor.execute("SELECT f.field_key, d.value_json FROM un_module_record_data d JOIN un_module_field f ON f.id=d.field_id WHERE d.record_id=%s", (rid,))
            values = {row["field_key"]: json.loads(row["value_json"]) for row in cursor.fetchall()}
            assert values == {"title": "客户 A 已处理", "estimate": 150}
            cursor.execute("SELECT f.field_key, i.text_value, i.number_value FROM un_module_record_index i JOIN un_module_field f ON f.id=i.field_id WHERE i.record_id=%s", (rid,))
            indexes = {row["field_key"]: row for row in cursor.fetchall()}
            assert indexes["title"]["text_value"] == "客户 A 已处理" and indexes["estimate"]["number_value"] == 150
            cursor.execute("SELECT version, success FROM flyway_schema_history WHERE version='3'")
            assert cursor.fetchone() == {"version": "3", "success": 1}
    finally:
        db.close()
    print(json.dumps({"status": "passed", "system_id": sid, "module_id": mid, "record_id": rid,
                      "checks": ["member_create", "required_field_rollback", "typed_values", "pagination", "search", "field_filter", "detail", "update_version", "owner_readback", "other_member_isolation", "read_only_action_denial", "mysql_three_table_readback", "flyway_v3"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
