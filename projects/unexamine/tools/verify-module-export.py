"""在隔离系统验证模块筛选导出、任务文件及独立 EXPORT 权限。"""

import csv
import importlib.util
import io
import json
import secrets
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("fixture", Path(__file__).with_name("verify-external-flow.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def fail_code(path, method, body, token, status, code):
    actual, response = fixture.request(path, method, body, token)
    if actual != status or response.get("code") != code:
        raise AssertionError(f"{method} {path}: expected {status}/{code}, got {actual}/{response}")
    return response


def download(path, token):
    request = urllib.request.Request(fixture.BASE + path, headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read(), response.headers.get("Content-Type", "")


def main():
    suffix = secrets.token_hex(4)

    def register(name):
        username = f"g6_{name}_{suffix}"
        result = fixture.expected("/api/auth/register", "POST", {
            "username": username, "password": "correct-password", "displayName": f"G6 {name}",
            "email": username + "@example.com", "systemName": f"G6 {name} 系统",
            "systemCode": f"g6_{name}_{suffix}"})
        return username, result

    owner_name, owner = register("owner")
    member_name, _ = register("member")
    reader_name, _ = register("reader")
    owner_token = owner["tokens"]["accessToken"]
    system_id, tenant_id = owner["systemId"], owner["tenantId"]
    group = fixture.expected("/api/admin/module-config/groups", "POST", {
        "code": "g6_sales", "name": "销售", "sortOrder": 10}, owner_token)
    module = fixture.expected("/api/admin/module-config/modules", "POST", {
        "groupId": group["id"], "code": "g6_customer", "name": "客户"}, owner_token)
    module_id = module["module"]["id"]
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/fields", "POST", {
        "code": "customer_code", "name": "客户编码", "fieldType": "TEXT",
        "required": False, "uniqueValue": True, "searchable": True, "sortOrder": 10, "config": {}}, owner_token)
    draft = fixture.expected(f"/api/admin/module-config/modules/{module_id}/draft", token=owner_token)
    fixture.expected(f"/api/admin/module-config/modules/{module_id}/publish", "POST", {
        "expectedDraftRevision": draft["module"]["draftRevision"]}, owner_token)

    def role(name, export):
        actions = ["LIST", "DETAIL"] + (["EXPORT"] if export else [])
        permissions = [{"resourceType": "MODULE", "resourceCode": "g6_customer",
                        "actionCode": action, "dataScopeType": "SELF", "dataScopeJson": "{}"}
                       for action in actions]
        policies = [{"resourceCode": "g6_customer", "fieldCode": "customer_code",
                     "channel": channel, "readable": True, "writable": False,
                     "maskStrategy": None} for channel in ("PAGE", "FILE")]
        saved = fixture.expected("/api/admin/system/authorization/roles", "POST", {
            "code": name, "name": name, "permissions": permissions,
            "fieldPolicies": policies}, owner_token)
        return fixture.expected(f"/api/admin/system/authorization/roles/{saved['id']}/publish", "POST", {
            "reason": "G6 导出独立权限验证", "expectedVersion": saved["version"]}, owner_token)
    exporter_role = role("g6_exporter", True)
    reader_role = role("g6_reader", False)
    exporter = fixture.expected("/api/admin/system/authorization/members", "POST", {
        "account": member_name, "roleIds": [exporter_role["id"]]}, owner_token)
    reader = fixture.expected("/api/admin/system/authorization/members", "POST", {
        "account": reader_name, "roleIds": [reader_role["id"]]}, owner_token)
    exporter_id = exporter["systemMemberId"]
    reader_id = reader["systemMemberId"]
    records = []
    for title, code, member_id in (("G6 上海客户", "SH-001", exporter_id),
                                   ("G6 北京客户", "BJ-002", exporter_id),
                                   ("G6 上海其他负责人", "SH-999", reader_id)):
        records.append(fixture.expected("/api/runtime/modules/g6_customer/records", "POST", {
            "title": title, "status": "ACTIVE", "ownerMemberId": member_id,
            "participantMemberIds": [], "fields": {"customer_code": code}}, owner_token))

    config_path = "/api/runtime/modules/g6_customer/exports/config"
    config = fixture.expected(config_path, token=owner_token)
    if not any(field["code"] == "customer_code" for field in config["availableFields"]):
        raise AssertionError("自定义字段未出现在导出配置")
    saved = fixture.expected(config_path, "PUT", {
        "name": "客户核对导出", "selectedFields": ["title", "customer_code", "ownerMemberId"],
        "relationDisplay": "LABEL", "expectedVersion": None}, owner_token)
    published = fixture.expected(config_path + "/publish", "POST", {
        "expectedDraftRevision": saved["draftRevision"]}, owner_token)
    if published["selectedFields"] != ["title", "customer_code", "ownerMemberId"]:
        raise AssertionError(f"发布字段顺序错误：{published}")

    def enter(username):
        platform = fixture.expected("/api/auth/login", "POST", {
            "username": username, "password": "correct-password"})
        return fixture.expected(f"/api/systems/{system_id}/enter", "POST", {}, platform["accessToken"])["tokens"]["accessToken"]
    exporter_token = enter(member_name)
    reader_token = enter(reader_name)
    export_path = "/api/runtime/modules/g6_customer/exports"
    query = {"lifecycleState": "ACTIVE", "tenantScope": "ALL", "search": "上海",
             "filters": [], "sortField": "title", "sortDirection": "ASC",
             "selectedRecordIds": [], "format": "CSV", "idempotencyKey": "g6-" + suffix,
             "selectedFields": []}
    fail_code(export_path + "/estimate", "POST", query, reader_token, 403, "EXPORT_PERMISSION_DENIED")
    estimate = fixture.expected(export_path + "/estimate", "POST", query, exporter_token)
    if estimate["estimatedRows"] != 1 or [field["code"] for field in estimate["selectedFields"]] != published["selectedFields"]:
        raise AssertionError(f"筛选与负责人范围预估错误：{estimate}")
    batch = fixture.expected(export_path, "POST", query, exporter_token)
    for _ in range(60):
        batch = fixture.expected(export_path + "/" + str(batch["id"]), token=exporter_token)
        if batch["status"] in ("COMPLETED", "FAILED"):
            break
        time.sleep(0.3)
    if batch["status"] != "COMPLETED" or batch["exportedRows"] != 1 or not batch["jobId"]:
        raise AssertionError(f"异步导出任务失败：{batch}")
    content, content_type = download(export_path + f"/{batch['id']}/download", exporter_token)
    rows = list(csv.reader(io.StringIO(content.decode("utf-8-sig"))))
    if rows[0] != ["标题", "客户编码", "负责人"] or len(rows) != 2 or rows[1][0] != "G6 上海客户" or rows[1][1] != "SH-001":
        raise AssertionError(f"下载文件行和列错误：{rows}")
    xlsx_query = dict(query, search="", format="XLSX",
                      selectedRecordIds=[records[0]["id"], records[2]["id"]],
                      idempotencyKey="g6-xlsx-" + suffix)
    xlsx_estimate = fixture.expected(export_path + "/estimate", "POST", xlsx_query, exporter_token)
    if xlsx_estimate["estimatedRows"] != 1 or xlsx_estimate["scope"] != "SELECTED":
        raise AssertionError(f"Excel 勾选记录未按负责人范围收缩：{xlsx_estimate}")
    xlsx_batch = fixture.expected(export_path, "POST", xlsx_query, exporter_token)
    for _ in range(60):
        xlsx_batch = fixture.expected(export_path + "/" + str(xlsx_batch["id"]), token=exporter_token)
        if xlsx_batch["status"] in ("COMPLETED", "FAILED"):
            break
        time.sleep(0.3)
    if xlsx_batch["status"] != "COMPLETED" or xlsx_batch["exportedRows"] != 1:
        raise AssertionError(f"Excel 异步任务失败：{xlsx_batch}")
    xlsx_content, _ = download(export_path + f"/{xlsx_batch['id']}/download", exporter_token)
    with zipfile.ZipFile(io.BytesIO(xlsx_content)) as workbook:
        sheet = workbook.read("xl/worksheets/sheet1.xml").decode()
        strings = workbook.read("xl/sharedStrings.xml").decode() if "xl/sharedStrings.xml" in workbook.namelist() else sheet
        if sheet.count("<row ") != 2 or "SH-001" not in strings or "SH-999" in strings:
            raise AssertionError("Excel 文件没有保持勾选集合与负责人数据权限")
    fail_code(export_path + f"/{batch['id']}", "GET", None, reader_token, 403, "EXPORT_PERMISSION_DENIED")

    listed = fixture.expected(export_path, token=exporter_token)
    if not any(item["id"] == batch["id"] for item in listed):
        raise AssertionError("导出历史缺少当前任务")
    if "--restart" in sys.argv:
        subprocess.run(["docker", "restart", "unexamine-g1-preview"], check=True, capture_output=True)
        for _ in range(70):
            try:
                with urllib.request.urlopen(fixture.BASE + "/actuator/health", timeout=2) as response:
                    if json.loads(response.read()).get("status") == "UP":
                        break
            except OSError:
                pass
            time.sleep(0.5)
        else:
            raise AssertionError("预览服务重启后未恢复")
        saved, _ = download(export_path + f"/{batch['id']}/download", exporter_token)
        if saved != content:
            raise AssertionError("重启后导出文件与原文件不同")

    # 测试专用 Redis 锁延迟一个作业，让角色撤权确实发生在异步执行之前。
    next_job = int(subprocess.check_output([
        "docker", "exec", "unexamine-mysql-1", "mysql", "-uunexamine", "-punexamine_local",
        "-Dunexamine_g1_preview", "-N", "-e",
        "SELECT COALESCE(MAX(id),0)+1 FROM un_job_background"],
        stderr=subprocess.DEVNULL).decode().strip())
    lock_key = f"unexamine:jobs:lock:{next_job}"
    lock_result = subprocess.check_output([
        "docker", "exec", "unexamine-redis-1", "redis-cli", "-n", "1",
        "SET", lock_key, "g6-fixture-hold", "EX", "90", "NX"]).decode().strip()
    if lock_result != "OK":
        raise AssertionError("无法为异步撤权测试保持作业队列")
    failure_batch = None
    try:
        failure_query = dict(query, search="北京", idempotencyKey="g6-revoke-" + suffix)
        failure_batch = fixture.expected(export_path, "POST", failure_query, exporter_token)
        if failure_batch["jobId"] != next_job:
            raise AssertionError("测试作业 ID 与隔离锁不一致")
        revoke = fixture.expected("/api/admin/system/authorization/roles", "POST", {
            "id": exporter_role["id"], "code": "g6_exporter", "name": "g6_exporter",
            "permissions": [{"resourceType": "MODULE", "resourceCode": "g6_customer",
                             "actionCode": action, "dataScopeType": "SELF", "dataScopeJson": "{}"}
                            for action in ("LIST", "DETAIL")],
            "fieldPolicies": [{"resourceCode": "g6_customer", "fieldCode": "customer_code",
                               "channel": channel, "readable": True, "writable": False,
                               "maskStrategy": None} for channel in ("PAGE", "FILE")],
            "expectedVersion": exporter_role["version"]}, owner_token)
        fixture.expected(f"/api/admin/system/authorization/roles/{revoke['id']}/publish", "POST", {
            "reason": "G6 队列中及已生成结果的撤权核对", "expectedVersion": revoke["version"]}, owner_token)
    finally:
        subprocess.run(["docker", "exec", "unexamine-redis-1", "redis-cli", "-n", "1",
                        "DEL", lock_key], check=True, capture_output=True)
    for _ in range(60):
        failed_job = fixture.expected(
            f"/api/admin/system/background-jobs/{failure_batch['jobId']}", token=owner_token)["job"]
        if failed_job["status"] in ("PERMANENT_FAILED", "SUCCEEDED"):
            break
        time.sleep(0.3)
    if failed_job["status"] != "PERMANENT_FAILED" or not failed_job["errorMessage"]:
        raise AssertionError(f"撤权后的异步任务未保留失败原因：{failed_job}")
    failed_batch_status = subprocess.check_output([
        "docker", "exec", "unexamine-mysql-1", "mysql", "-uunexamine", "-punexamine_local",
        "-Dunexamine_g1_preview", "-N", "-e",
        f"SELECT status FROM un_exchange_export_batch WHERE id={failure_batch['id']}"],
        stderr=subprocess.DEVNULL).decode().strip()
    if failed_batch_status != "FAILED":
        raise AssertionError(f"导出批次未记失败状态：{failed_batch_status}")
    revoked_token = enter(member_name)
    fail_code(export_path + f"/{batch['id']}/download", "GET", None,
              revoked_token, 403, "EXPORT_PERMISSION_DENIED")
    print(json.dumps({"systemId": system_id, "tenantId": tenant_id,
                      "ownerUsername": owner_name, "exporterUsername": member_name,
                      "moduleId": module_id, "recordIds": [item["id"] for item in records],
                      "exporterMemberId": exporter_id, "readerMemberId": reader_id,
                      "mappingVersionId": published["versionId"], "batchId": batch["id"],
                      "xlsxBatchId": xlsx_batch["id"],
                      "jobId": batch["jobId"], "fileId": batch["resultFileId"],
                      "status": batch["status"], "rows": rows,
                      "contentType": content_type, "restartVerified": "--restart" in sys.argv,
                      "revokedDownloadDenied": "EXPORT_PERMISSION_DENIED",
                      "failedBatchId": failure_batch["id"],
                      "failedJobCode": failed_job["errorCode"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
