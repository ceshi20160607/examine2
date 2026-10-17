"""同系统验证记录附件、审批任务附件、筛选导出和数据库引用。"""

from __future__ import annotations

import json
import os
import secrets
import time
import urllib.error
import urllib.request
from http.cookiejar import CookieJar

import pymysql

BASE = "http://127.0.0.1:15173/api"
PNG = bytes.fromhex("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000b49444154789c636000020000050001a5f645400000000049454e44ae426082")


def browser():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def call(opener, method, path, wanted, body=None, content_type="application/json"):
    payload = json.dumps(body).encode() if body is not None and content_type == "application/json" else body
    request = urllib.request.Request(BASE + path, data=payload, method=method,
                                     headers={"Content-Type": content_type})
    try:
        with opener.open(request, timeout=15) as response:
            status, data, headers = response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        status, data, headers = error.code, error.read(), error.headers
    if status != wanted:
        raise AssertionError(f"{method} {path}: wanted {wanted}, got {status}: {data[:400]}")
    if content_type == "application/json" and data and headers.get_content_type() == "application/json":
        return json.loads(data)
    return data, headers


def upload(opener, path, filename, contents, wanted=201):
    boundary = "Boundary" + secrets.token_hex(8)
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
            "Content-Type: application/octet-stream\r\n\r\n").encode() + contents + f"\r\n--{boundary}--\r\n".encode()
    data, headers = call(opener, "POST", path, wanted, body, f"multipart/form-data; boundary={boundary}")
    return json.loads(data) if data and headers.get_content_type() == "application/json" else None


def wait_job(opener, path, job_id):
    for _ in range(50):
        jobs = call(opener, "GET", path, 200)
        found = next((job for job in jobs if job["id"] == job_id), None)
        if found and found["status"] in ("SUCCEEDED", "FAILED"):
            return found
        time.sleep(0.1)
    raise AssertionError("导出任务未结束")


def main():
    suffix = secrets.token_hex(4)
    owner, member, reviewer = [browser() for _ in range(3)]
    password = "FileCheck9!" + suffix
    names = ["file_owner_" + suffix, "file_member_" + suffix, "file_review_" + suffix]
    for client, username in zip([owner, member, reviewer], names):
        call(client, "POST", "/auth/register", 201,
             {"username": username, "password": password, "displayName": username})
    system = call(owner, "POST", "/systems", 201,
                  {"code": "file_" + suffix, "name": "文件导出验收系统"})
    sid = system["id"]
    base = f"/systems/{sid}"
    role = call(owner, "GET", base + "/roles", 200)[0]
    group = call(owner, "POST", base + "/module-groups", 201, {"code": "business", "name": "业务"})
    module = call(owner, "POST", base + "/modules", 201,
                  {"groupId": group["id"], "code": "report", "name": "报告"})
    mid = module["id"]
    path = base + f"/modules/{mid}"
    call(owner, "POST", path + "/fields", 201,
         {"key": "title", "label": "报告标题", "type": "TEXT", "required": True, "searchable": True})
    call(owner, "POST", path + "/fields", 201,
         {"key": "amount", "label": "金额", "type": "NUMBER", "required": False, "searchable": True})
    call(owner, "POST", path + "/publish", 200)
    call(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
         {"moduleId": mid, "canView": True, "canCreate": True, "canUpdate": True, "canDelete": False, "canExport": False})
    for username in names[1:]:
        call(owner, "POST", base + "/members/invite", 201,
             {"username": username, "roleId": role["id"]})
    members = {item["username"]: item["id"] for item in call(owner, "GET", base + "/members", 200)}
    record_path = path + "/records"
    first = call(member, "POST", record_path, 201, {"values": {"title": "第一份报告"}})
    second = call(member, "POST", record_path, 201, {"values": {"title": "第二份报告"}})
    other = call(reviewer, "POST", record_path, 201, {"values": {"title": "第一份报告-他人"}})
    rid = first["id"]
    files_path = record_path + f"/{rid}/files"
    image = upload(member, files_path, "测试图片.png", PNG)
    assert image["contentType"] == "image/png" and image["sizeBytes"] == len(PNG)
    text = upload(member, files_path, "说明.html", b"<h1>untrusted</h1>")
    assert text["contentType"] == "application/octet-stream"
    assert len(call(member, "GET", files_path, 200)) == 2
    assert len(call(owner, "GET", files_path, 200)) == 2
    call(reviewer, "GET", files_path, 404)
    image_url = files_path + f"/{image['id']}"
    raw, headers = call(member, "GET", image_url + "/download", 200)
    assert raw == PNG and headers.get_content_type() == "image/png"
    raw, headers = call(owner, "GET", image_url + "/preview", 200)
    assert raw == PNG and headers.get("Content-Disposition", "").startswith("inline")
    raw, headers = call(member, "GET", image_url + "/thumbnail", 200)
    assert raw.startswith(bytes.fromhex("89504e47")) and headers.get_content_type() == "image/png"
    call(member, "GET", files_path + f"/{text['id']}/preview", 400)
    call(reviewer, "GET", image_url + "/download", 404)

    # 审批任务只为指定处理人开放附件，记录负责人不能借任务接口读取。
    call(owner, "PUT", path + "/approval", 200,
         {"name": "报告审批", "nodeName": "审核", "approverMemberId": members[names[2]]})
    call(owner, "POST", path + "/approval/publish", 200)
    submitted = call(member, "POST", record_path + f"/{rid}/submit-approval", 201)
    task_files = base + f"/approval/tasks/{submitted['taskId']}/files"
    assert len(call(reviewer, "GET", task_files, 200)) == 2
    assert call(reviewer, "GET", task_files + f"/{image['id']}/download", 200)[0] == PNG
    call(member, "GET", task_files, 404)
    upload(member, files_path, "审批中.txt", b"blocked", wanted=409)
    call(member, "DELETE", image_url, 409)
    call(reviewer, "POST", base + f"/approval/tasks/{submitted['taskId']}/decision", 200,
         {"approve": False, "opinion": "请补充说明"})
    call(member, "DELETE", image_url, 204)
    call(member, "GET", image_url + "/download", 404)

    export_path = path + "/export-jobs"
    call(member, "POST", export_path, 404,
         {"search": "第一", "columnKeys": ["title"]})
    call(owner, "POST", base + f"/roles/{role['id']}/grants", 200,
         {"moduleId": mid, "canView": True, "canCreate": True, "canUpdate": True, "canDelete": False, "canExport": True})
    call(member, "POST", export_path, 400,
         {"search": "第一", "columnKeys": ["unknown"]})
    member_job = call(member, "POST", export_path, 201,
                      {"search": "第一", "columnKeys": ["title"]})
    result = wait_job(member, export_path, member_job["id"])
    assert result["status"] == "SUCCEEDED" and result["rowCount"] == 1, result
    csv, headers = call(member, "GET", export_path + f"/{member_job['id']}/download", 200)
    decoded = csv.decode("utf-8-sig")
    assert "报告标题" in decoded and "第一份报告" in decoded and "第一份报告-他人" not in decoded
    assert "第二份报告" not in decoded
    call(reviewer, "GET", export_path + f"/{member_job['id']}/download", 404)
    owner_job = call(owner, "POST", export_path, 201,
                     {"search": "第一", "columnKeys": ["title"]})
    assert wait_job(owner, export_path, owner_job["id"])["rowCount"] == 2
    call(member, "POST", record_path, 201, {"values": {"title": " =1+1"}})
    formula_job = call(member, "POST", export_path, 201,
                       {"search": "=1+1", "columnKeys": ["title"]})
    assert wait_job(member, export_path, formula_job["id"])["rowCount"] == 1
    formula_csv = call(member, "GET", export_path + f"/{formula_job['id']}/download", 200)[0].decode("utf-8-sig")
    assert "' =1+1" in formula_csv
    failed_job = call(member, "POST", export_path, 201,
                      {"filterKey": "amount", "filterValue": "不是数字", "columnKeys": ["title"]})
    failed = wait_job(member, export_path, failed_job["id"])
    assert failed["status"] == "FAILED" and "数字" in failed["failureReason"]
    call(member, "GET", export_path + f"/{failed_job['id']}/download", 409)

    db = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                         password=os.environ["APP_DB_PASSWORD"], database="unexamine_r15",
                         charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with db.cursor() as cursor:
            # 独立核对文件引用、软删除状态与受权限约束的导出结果，失败请求不新增引用。
            cursor.execute("SELECT status,size_bytes,sha256_hex FROM un_file_object WHERE id=%s", (image["id"],))
            assert cursor.fetchone()["status"] == "DELETED"
            cursor.execute("SELECT COUNT(*) AS c FROM un_file_reference WHERE record_id=%s", (rid,))
            assert cursor.fetchone()["c"] == 1
            cursor.execute("SELECT status,row_count FROM un_exchange_job WHERE id=%s", (member_job["id"],))
            assert cursor.fetchone() == {"status": "SUCCEEDED", "row_count": 1}
            cursor.execute("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")
            assert cursor.fetchone() == {"version": "6", "success": 1}
    finally:
        db.close()
    print(json.dumps({"status": "passed", "system_id": sid, "module_id": mid, "record_id": rid,
                      "other_record_id": other["id"], "checks": ["upload", "list", "download", "preview", "thumbnail",
                      "task_attachment", "pending_edit_denial", "remove", "filtered_export", "owner_scope",
                      "other_member_denial", "export_action_permission", "csv_formula_escape", "failure_reason", "mysql_readback", "flyway_v6"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
