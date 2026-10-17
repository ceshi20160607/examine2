"""在新系统验证审批通知的收件隔离、已读、目标鉴权和事务落库。"""

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
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    if status != wanted:
        raise AssertionError(f"{method} {path}: wanted {wanted}, got {status}: {raw[:400]}")
    return json.loads(raw) if raw else None


def main():
    suffix = secrets.token_hex(4)
    owner, applicant, approver, outsider = [browser() for _ in range(4)]
    password = "MessageCheck9!" + suffix
    names = ["msg_owner_" + suffix, "msg_app_" + suffix,
             "msg_review_" + suffix, "msg_out_" + suffix]
    for client, username in zip((owner, applicant, approver, outsider), names):
        call(client, "POST", "/auth/register", 201,
             {"username": username, "password": password, "displayName": username})
    system = call(owner, "POST", "/systems", 201,
                  {"code": "message_" + suffix, "name": "消息验收系统"})
    sid = system["id"]
    base = f"/systems/{sid}"
    role = call(owner, "GET", base + "/roles", 200)[0]
    group = call(owner, "POST", base + "/module-groups", 201, {"code": "business", "name": "业务"})
    module = call(owner, "POST", base + "/modules", 201,
                  {"groupId": group["id"], "code": "request", "name": "申请单"})
    mid = module["id"]
    path = base + f"/modules/{mid}"
    call(owner, "POST", path + "/fields", 201,
         {"key": "title", "label": "申请标题", "type": "TEXT", "required": True, "searchable": True})
    call(owner, "POST", path + "/publish", 200)
    call(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
         {"moduleId": mid, "canView": True, "canCreate": True, "canUpdate": True,
          "canDelete": False, "canExport": False})
    for username in names[1:3]:
        call(owner, "POST", base + "/members/invite", 201,
             {"username": username, "roleId": role["id"]})
    members = {member["username"]: member["id"] for member in call(owner, "GET", base + "/members", 200)}
    call(owner, "PUT", path + "/approval", 200,
         {"name": "申请审批", "nodeName": "主管审批", "approverMemberId": members[names[2]]})
    call(owner, "POST", path + "/approval/publish", 200)
    record = call(applicant, "POST", path + "/records", 201,
                  {"values": {"title": "含敏感信息的申请"}})
    submit = path + f"/records/{record['id']}/submit-approval"
    assert call(applicant, "GET", base + "/messages/unread-count", 200)["count"] == 0
    first = call(applicant, "POST", submit, 201)
    call(applicant, "POST", submit, 409)
    inbox = call(approver, "GET", base + "/messages", 200)
    assert len(inbox) == 1 and inbox[0]["kind"] == "APPROVAL_TODO"
    assert inbox[0]["taskId"] == first["taskId"] and "敏感" not in inbox[0]["title"]
    assert call(approver, "GET", base + "/messages/unread-count", 200)["count"] == 1
    assert call(applicant, "GET", base + "/messages", 200) == []
    call(applicant, "POST", base + f"/messages/{inbox[0]['id']}/read", 404)
    call(outsider, "GET", base + "/messages", 404)
    assert call(approver, "GET", base + f"/approval/tasks/{first['taskId']}", 200)["record"]["id"] == record["id"]
    read = call(approver, "POST", base + f"/messages/{inbox[0]['id']}/read", 200)
    assert read["readTime"] and call(approver, "POST", base + f"/messages/{inbox[0]['id']}/read", 200)["readTime"] == read["readTime"]
    assert call(approver, "GET", base + "/messages/unread-count", 200)["count"] == 0

    call(approver, "POST", base + f"/approval/tasks/{first['taskId']}/decision", 200,
         {"approve": False, "opinion": "请补充个人敏感数据"})
    result = call(applicant, "GET", base + "/messages", 200)
    assert len(result) == 1 and result[0]["kind"] == "APPROVAL_REJECTED"
    assert "敏感" not in result[0]["title"] and result[0]["recordId"] == record["id"]
    assert call(applicant, "GET", path + f"/records/{record['id']}", 200)["recordStatus"] == "REJECTED"
    call(applicant, "POST", base + f"/messages/{result[0]['id']}/read", 200)
    second = call(applicant, "POST", submit, 201)
    assert len(call(approver, "GET", base + "/messages", 200)) == 2
    call(approver, "POST", base + f"/approval/tasks/{second['taskId']}/decision", 200,
         {"approve": True, "opinion": "已核对"})
    assert {message["kind"] for message in call(applicant, "GET", base + "/messages", 200)} == {
        "APPROVAL_REJECTED", "APPROVAL_APPROVED"}
    second_system = call(owner, "POST", "/systems", 201,
                         {"code": "message_other_" + suffix, "name": "其他消息系统"})
    assert call(owner, "GET", f"/systems/{second_system['id']}/messages", 200) == []
    call(owner, "POST", f"/systems/{second_system['id']}/messages/{inbox[0]['id']}/read", 404)
    call(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
         {"moduleId": mid, "canView": False, "canCreate": False, "canUpdate": False,
          "canDelete": False, "canExport": False})
    assert len(call(applicant, "GET", base + "/messages", 200)) == 2
    call(applicant, "GET", path + f"/records/{record['id']}", 404)

    db = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                         password=os.environ["APP_DB_PASSWORD"], database="unexamine_r15",
                         charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with db.cursor() as cursor:
            cursor.execute("SELECT kind,title,read_time FROM un_message WHERE system_id=%s ORDER BY id", (sid,))
            rows = cursor.fetchall()
            assert len(rows) == 4 and [row["kind"] for row in rows] == [
                "APPROVAL_TODO", "APPROVAL_REJECTED", "APPROVAL_TODO", "APPROVAL_APPROVED"]
            assert all("敏感" not in row["title"] for row in rows)
            assert rows[0]["read_time"] and rows[1]["read_time"]
            cursor.execute("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")
            assert cursor.fetchone() == {"version": "9", "success": 1}
    finally:
        db.close()
    print(json.dumps({"status": "passed", "system_id": sid, "module_id": mid,
                      "checks": ["submit_notice", "duplicate_no_message", "recipient_isolation", "unread",
                                 "read_idempotent", "target_permission", "reject_notice", "approve_notice",
                                 "generic_title", "system_isolation", "revoked_target_denial",
                                 "mysql_readback", "flyway_v9"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
