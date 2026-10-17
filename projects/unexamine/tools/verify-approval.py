"""同候选系统验证审批发布快照、双账号办理、重提与 MySQL 原子读回。"""

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


def call(opener, method, path, wanted, body=None):
    request = urllib.request.Request(BASE + path,
                                     data=json.dumps(body).encode() if body is not None else None,
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
    owner, applicant, approver, outsider = [browser() for _ in range(4)]
    password = "ApproveCheck9!" + suffix
    names = ["approval_owner_" + suffix, "approval_app_" + suffix,
             "approval_reviewer_" + suffix, "approval_out_" + suffix]
    for client, username in zip([owner, applicant, approver, outsider], names):
        call(client, "POST", "/auth/register", 201,
             {"username": username, "password": password, "displayName": username})
    system = call(owner, "POST", "/systems", 201,
                  {"code": "approval_" + suffix, "name": "审批验收系统"})
    sid = system["id"]
    base = f"/systems/{sid}"
    role = call(owner, "GET", base + "/roles", 200)[0]
    group = call(owner, "POST", base + "/module-groups", 201,
                 {"code": "business", "name": "业务"})
    module = call(owner, "POST", base + "/modules", 201,
                  {"groupId": group["id"], "code": "request", "name": "申请单"})
    mid = module["id"]
    path = base + f"/modules/{mid}"
    call(owner, "POST", path + "/fields", 201,
         {"key": "title", "label": "申请标题", "type": "TEXT", "required": True, "searchable": True})
    call(owner, "POST", path + "/publish", 200)
    call(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
         {"moduleId": mid, "canView": True, "canCreate": True, "canUpdate": True, "canDelete": False})
    for username in names[1:3]:
        call(owner, "POST", base + "/members/invite", 201,
             {"username": username, "roleId": role["id"]})
    members = call(owner, "GET", base + "/members", 200)
    ids = {item["username"]: item["id"] for item in members}
    record = call(applicant, "POST", path + "/records", 201,
                  {"values": {"title": "差旅申请"}})
    rid = record["id"]
    submit = path + f"/records/{rid}/submit-approval"
    call(applicant, "POST", submit, 400)
    call(applicant, "GET", path + "/approval", 403)
    call(owner, "GET", path + "/approval", 204)
    draft = {"name": "差旅审批", "nodeName": "主管审批", "approverMemberId": ids[names[2]]}
    call(owner, "PUT", path + "/approval", 200, draft)
    published = call(owner, "POST", path + "/approval/publish", 200)
    assert published["publishedVersionNumber"] == 1
    first = call(applicant, "POST", submit, 201)
    assert first["status"] == "PENDING" and first["attemptNumber"] == 1
    call(applicant, "POST", submit, 409)
    assert call(applicant, "GET", path + f"/records/{rid}", 200)["recordStatus"] == "PENDING"
    call(applicant, "PUT", path + f"/records/{rid}", 409,
         {"expectedVersion": 1, "values": {"title": "审批中不应修改"}})
    assert len(call(approver, "GET", base + "/approval/tasks", 200)) == 1
    call(outsider, "GET", base + "/approval/tasks", 404)
    call(applicant, "GET", base + f"/approval/tasks/{first['taskId']}", 404)

    # 只调整草稿和新发布版本；首轮实例仍由原审批人办理。
    call(owner, "PUT", path + "/approval", 200,
         {"name": "差旅审批第二版", "nodeName": "所属人审批", "approverMemberId": ids[names[0]]})
    assert len(call(approver, "GET", base + "/approval/tasks", 200)) == 1
    assert call(owner, "GET", base + "/approval/tasks", 200) == []
    second_version = call(owner, "POST", path + "/approval/publish", 200)
    assert second_version["publishedVersionNumber"] == 2
    detail = call(approver, "GET", base + f"/approval/tasks/{first['taskId']}", 200)
    assert detail["record"]["values"]["title"] == "差旅申请"
    call(approver, "POST", base + f"/approval/tasks/{first['taskId']}/decision", 400,
         {"approve": False, "opinion": ""})
    call(applicant, "POST", base + f"/approval/tasks/{first['taskId']}/decision", 404,
         {"approve": True})
    result = call(approver, "POST", base + f"/approval/tasks/{first['taskId']}/decision", 200,
                  {"approve": False, "opinion": "信息不足"})
    assert result["status"] == "REJECTED"
    call(approver, "POST", base + f"/approval/tasks/{first['taskId']}/decision", 409,
         {"approve": True})
    rejected = call(applicant, "GET", path + f"/records/{rid}", 200)
    assert rejected["recordStatus"] == "REJECTED"
    call(applicant, "PUT", path + f"/records/{rid}", 200,
         {"expectedVersion": rejected["version"], "values": {"title": "差旅申请已补充"}})
    second = call(applicant, "POST", submit, 201)
    assert second["attemptNumber"] == 2 and second["flowVersionId"] != first["flowVersionId"]
    assert call(approver, "GET", base + "/approval/tasks", 200) == []
    assert len(call(owner, "GET", base + "/approval/tasks", 200)) == 1
    call(owner, "POST", base + f"/approval/tasks/{second['taskId']}/decision", 200,
         {"approve": True, "opinion": "已核对"})
    final = call(applicant, "GET", path + f"/records/{rid}", 200)
    assert final["recordStatus"] == "APPROVED"
    assert call(applicant, "GET", path + "/records", 200)["items"][0]["recordStatus"] == "APPROVED"
    assert call(owner, "GET", base + "/approval/tasks", 200) == []

    db = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                         password=os.environ["APP_DB_PASSWORD"], database="unexamine_r15",
                         charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with db.cursor() as cursor:
            # 独立连接核对记录、两轮实例、两个版本与每轮事件，证明状态和历史均已落库。
            cursor.execute("SELECT record_status FROM un_module_record WHERE id=%s", (rid,))
            assert cursor.fetchone()["record_status"] == "APPROVED"
            cursor.execute("SELECT attempt_number,status,flow_version_id,snapshot_json FROM un_flow_example WHERE record_id=%s ORDER BY attempt_number", (rid,))
            rows = cursor.fetchall()
            assert [(r["attempt_number"], r["status"]) for r in rows] == [(1, "REJECTED"), (2, "APPROVED")]
            assert json.loads(rows[0]["snapshot_json"])["approverMemberId"] == ids[names[2]]
            assert json.loads(rows[1]["snapshot_json"])["approverMemberId"] == ids[names[0]]
            cursor.execute("SELECT event_type FROM un_flow_event WHERE example_id IN (SELECT id FROM un_flow_example WHERE record_id=%s) ORDER BY id", (rid,))
            assert [r["event_type"] for r in cursor.fetchall()] == ["SUBMIT", "REJECT", "SUBMIT", "APPROVE"]
            cursor.execute("SELECT version,success FROM flyway_schema_history WHERE version='4'")
            assert cursor.fetchone() == {"version": "4", "success": 1}
    finally:
        db.close()
    print(json.dumps({"status": "passed", "system_id": sid, "module_id": mid, "record_id": rid,
                      "checks": ["draft_publish", "frozen_snapshot", "cross_account_denial", "duplicate_denial",
                                 "reject_reason", "resubmit_new_version", "approve", "record_status", "mysql_readback", "flyway_v4"]},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()
