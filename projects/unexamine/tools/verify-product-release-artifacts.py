"""在 S19 原客户记录上核对附件、已发布打印和成员导出文件。"""

import csv
import hashlib
import io
import json
import os
import runpy
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

import pymysql


ROOT = Path(os.environ.get("S19_WORK_ROOT", str(Path(__file__).resolve().parents[3])))
EVIDENCE = ROOT / "projects/unexamine/ai/evidence/s19-artifacts-readback.json"
MEMBER = ROOT / "projects/unexamine/ai/evidence/s19-member-record-readback.json"
api = runpy.run_path(str(Path(__file__).with_name("verify-product-release-member.py")))
success = api["success"]
request = api["request"]
login = api["login"]
enter = api["enter"]
BASE = api["BASE"]


def session():
    return (enter(1, login("admin", os.environ["S19_ADMIN_PASSWORD"])),
            enter(1, login("s19_member", os.environ["S19_USER_PASSWORD"])))


def raw(path, token, method="GET", content=None, headers=None):
    call = urllib.request.Request(BASE + path, data=content,
                                  headers={"Authorization": "Bearer " + token, **(headers or {})}, method=method)
    try:
        with urllib.request.urlopen(call, timeout=30) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read()


def configure():
    admin, member = session()
    record = json.loads(MEMBER.read_text(encoding="utf-8"))["records"]["客户记录"]
    module_code, record_id, text_code = record["moduleCode"], record["recordId"], record["fieldCode"]
    module = next(item for item in success("/api/admin/module-config", token=admin)["modules"]
                  if item["code"] == module_code)
    draft = success(f"/api/admin/module-config/modules/{module['id']}/draft", token=admin)
    file_field = next((item for item in draft["fields"] if item["code"] == "s19_customer_file"), None)
    changed = False
    if file_field is None:
        file_field = success(f"/api/admin/module-config/modules/{module['id']}/fields", "POST", {
            "code": "s19_customer_file", "name": "客户资料", "fieldType": "FILE",
            "required": False, "sortOrder": 20, "config": {}}, admin)
        changed = True
    print_action = next((item for item in draft["actions"] if item["code"] == "PRINT"), None)
    if print_action is None:
        print_action = success(f"/api/admin/module-config/modules/{module['id']}/actions/print", "POST", {}, admin)
        changed = True
    if changed:
        revision = success(f"/api/admin/module-config/modules/{module['id']}/draft", token=admin)["module"]["draftRevision"]
        success(f"/api/admin/module-config/modules/{module['id']}/publish", "POST",
                {"expectedDraftRevision": revision}, admin)
    role = next(item for item in success("/api/admin/system/authorization", token=admin)["roles"]
                if item["code"] == "s19_operator")
    permissions = [dict(item) for item in role["permissions"]]
    for resource_type, resource_code, action, scope in [
        ("MODULE", module_code, "PRINT", "SELF"),
        ("FILE", "OBJECT", "VIEW", "ALL"), ("FILE", "OBJECT", "UPLOAD", "ALL"),
        ("FILE", "OBJECT", "REFERENCE", "ALL"), ("FILE", "OBJECT", "DOWNLOAD", "ALL"),
    ]:
        if not any(item["resourceType"] == resource_type and item["resourceCode"] == resource_code
                   and item["actionCode"] == action for item in permissions):
            permissions.append({"resourceType": resource_type, "resourceCode": resource_code,
                                "actionCode": action, "dataScopeType": scope, "dataScopeJson": "{}"})
    policies = [dict(item) for item in role["fieldPolicies"]]
    for channel in ("PAGE", "FILE"):
        if not any(item["resourceCode"] == module_code and item["fieldCode"] == "s19_customer_file"
                   and item["channel"] == channel for item in policies):
            policies.append({"resourceCode": module_code, "fieldCode": "s19_customer_file",
                             "channel": channel, "readable": True, "writable": True, "maskStrategy": None})
    if permissions != role["permissions"] or policies != role["fieldPolicies"]:
        saved = success("/api/admin/system/authorization/roles", "POST", {
            "id": role["id"], "code": role["code"], "name": role["name"],
            "description": role.get("description"), "permissions": permissions,
            "fieldPolicies": policies, "expectedVersion": role["version"]}, admin)
        success(f"/api/admin/system/authorization/roles/{role['id']}/publish", "POST", {
            "reason": "S19 同版客户附件与打印授权", "expectedVersion": saved["version"]}, admin)
    print_overview = success("/api/print/admin", token=admin)
    template = next((item for item in print_overview["templates"] if item["code"] == "s19_customer_sheet"), None)
    if template is None:
        template = success("/api/print/admin/templates", "POST", {
            "moduleId": module["id"], "code": "s19_customer_sheet", "name": "客户资料单",
            "pageSize": "A4", "orientation": "PORTRAIT",
            "layout": {"header": "客户资料", "footer": "S19 发布验收", "signatureLabel": "经办人",
                       "fieldCodes": [text_code], "detailFieldCodes": [], "rowsPerPage": 4,
                       "offsetXmm": 0, "offsetYmm": 0}}, admin)
        success(f"/api/print/admin/templates/{template['id']}/publish", "POST",
                {"expectedDraftRevision": template["draftRevision"]}, admin)
    config_path = f"/api/runtime/modules/{module_code}/exports/config"
    config = success(config_path, token=admin)
    published = config.get("published")
    if not published or published["selectedFields"] != ["title", text_code]:
        saved = success(config_path, "PUT", {"name": "客户核对导出",
            "selectedFields": ["title", text_code], "relationDisplay": "LABEL",
            "expectedVersion": config.get("draft", {}).get("version") if config.get("draft") else None}, admin)
        success(config_path + "/publish", "POST", {"expectedDraftRevision": saved["draftRevision"]}, admin)
    state = {"step": "S19.1", "status": "configured", "moduleId": module["id"],
             "moduleCode": module_code, "recordId": record_id, "textCode": text_code,
             "fileCode": "s19_customer_file", "printTemplateId": template["id"]}
    EVIDENCE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state, ensure_ascii=False))


def verify():
    state = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    _, member = session()
    module_code, record_id = state["moduleCode"], state["recordId"]
    record_path = f"/api/runtime/modules/{module_code}/records/{record_id}"
    detail = success(record_path, token=member)
    content = "S19 客户资料文件：上海客户已跟进。\n".encode("utf-8")
    digest = hashlib.sha256(content).hexdigest()
    file_id = detail["fields"].get(state["fileCode"])
    if not file_id:
        uploaded = success("/api/files/uploads", "POST", {"originalName": "s19-customer-note.txt",
            "contentType": "text/plain", "expectedSize": len(content), "expectedSha256": digest}, member)
        status, _, chunk = raw(f"/api/files/uploads/{uploaded['id']}/chunks?offset=0", member,
                               "PUT", content, {"Content-Type": "application/octet-stream",
                                               "X-Upload-Token": uploaded["uploadToken"],
                                               "X-Chunk-Sha256": digest})
        if status != 200 or json.loads(chunk)["code"] != "OK":
            raise AssertionError(f"Upload chunk failed: {status} {chunk[:200]}")
        status, _, completed = raw(f"/api/files/uploads/{uploaded['id']}/complete", member,
                                   "POST", b"", {"X-Upload-Token": uploaded["uploadToken"]})
        response = json.loads(completed)
        if status != 200 or response["code"] != "OK" or response["data"]["scanStatus"] != "CLEAN":
            raise AssertionError(f"Upload completion failed: {status} {response}")
        file_id = response["data"]["id"]
        success(record_path, "PUT", {"title": detail["title"], "recordNumber": detail["recordNumber"],
            "status": detail["status"], "ownerMemberId": detail["ownerMemberId"],
            "departmentId": detail["departmentId"], "participantMemberIds": detail["participantMemberIds"],
            "fields": {**detail["fields"], state["fileCode"]: file_id}, "version": detail["version"]}, member)
        detail = success(record_path, token=member)
    if detail["fields"].get(state["fileCode"]) != file_id:
        raise AssertionError("Record file field did not persist")
    file_view = success(f"/api/files/{file_id}", token=member)
    status, _, downloaded = raw(f"/api/files/{file_id}/download", member)
    if status != 200 or downloaded != content:
        raise AssertionError("Record attachment download mismatch")
    templates = success(f"/api/print/runtime/modules/{module_code}/templates", token=member)
    if not any(item["templateId"] == state["printTemplateId"] for item in templates):
        raise AssertionError("Published print template is absent")
    job = success(f"/api/print/runtime/modules/{module_code}/records/{record_id}/templates/{state['printTemplateId']}",
                  "POST", {}, member)
    if job["status"] != "SUCCEEDED" or state["textCode"] not in [field["code"]
            for page in job["pages"] for field in page["fields"]]:
        raise AssertionError("Published print snapshot lacks customer field")
    status, headers, pdf = raw(job["downloadPath"], member)
    if status != 200 or not pdf.startswith(b"%PDF-") or headers.get_content_type() != "application/pdf":
        raise AssertionError("Printed PDF download failed")
    export_path = f"/api/runtime/modules/{module_code}/exports"
    query = {"lifecycleState": "ACTIVE", "tenantScope": "ALL", "search": "上海",
             "filters": [], "sortField": "title", "sortDirection": "ASC",
             "selectedRecordIds": [record_id], "format": "CSV",
             "idempotencyKey": "s19-customer-export-1", "selectedFields": []}
    estimate = success(export_path + "/estimate", "POST", query, member)
    if estimate["estimatedRows"] != 1:
        raise AssertionError(f"Export estimate mismatch: {estimate}")
    batch = success(export_path, "POST", query, member)
    for _ in range(60):
        batch = success(export_path + f"/{batch['id']}", token=member)
        if batch["status"] in ("COMPLETED", "FAILED"):
            break
        time.sleep(0.3)
    if batch["status"] != "COMPLETED" or batch["exportedRows"] != 1:
        raise AssertionError(f"Export failed: {batch}")
    status, _, csv_content = raw(export_path + f"/{batch['id']}/download", member)
    rows = list(csv.reader(io.StringIO(csv_content.decode("utf-8-sig"))))
    if status != 200 or len(rows) != 2 or "S19 上海客户" not in rows[1] \
            or "上海客户已跟进" not in rows[1]:
        raise AssertionError(f"Exported customer row mismatch: {rows}")
    state.update({"status": "api_verified_browser_pending", "fileId": file_id,
                  "fileName": file_view["originalName"], "fileSha256": digest,
                  "printJobId": job["id"], "pdfSha256": hashlib.sha256(pdf).hexdigest(),
                  "exportBatchId": batch["id"], "exportRows": rows})
    EVIDENCE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state, ensure_ascii=False))


def readback():
    state = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    _, member = session()
    detail = success(f"/api/runtime/modules/{state['moduleCode']}/records/{state['recordId']}", token=member)
    if detail["fields"].get(state["fileCode"]) != state["fileId"]:
        raise AssertionError("Record attachment changed after restart")
    status, _, file_bytes = raw(f"/api/files/{state['fileId']}/download", member)
    if status != 200 or hashlib.sha256(file_bytes).hexdigest() != state["fileSha256"]:
        raise AssertionError("Record attachment is unavailable after restart")
    status, _, pdf_bytes = raw(f"/api/print/jobs/{state['printJobId']}/download", member)
    if status != 200 or hashlib.sha256(pdf_bytes).hexdigest() != state["pdfSha256"]:
        raise AssertionError("Printed PDF changed after restart")
    status, _, csv_bytes = raw(f"/api/runtime/modules/{state['moduleCode']}/exports/"
                               f"{state['exportBatchId']}/download", member)
    if status != 200 or list(csv.reader(io.StringIO(csv_bytes.decode("utf-8-sig")))) != state["exportRows"]:
        raise AssertionError("Export file changed after restart")
    browser = json.loads(MEMBER.read_text(encoding="utf-8"))["browserViews"]
    if not all(browser[key]["text"] for key in ("attachment", "printed", "exporting")):
        raise AssertionError("Attachment, print and export browser views are required")
    if state["fileName"] not in browser["attachment"]["text"] \
            or "打印文件已生成" not in browser["printed"]["text"] \
            or "数据导出" not in browser["exporting"]["text"]:
        raise AssertionError("Browser artifact readback does not match this record")
    connection = pymysql.connect(host="127.0.0.1", port=int(os.environ.get("S19_DB_PORT", "33078")),
                                 user=os.environ.get("APP_DB_USERNAME", "unexamine"),
                                 password=os.environ["APP_DB_PASSWORD"],
                                 database=os.environ.get("S19_DB_NAME", "unexamine"))
    with connection, connection.cursor() as cursor:
        cursor.execute("SELECT status,sha256,object_key FROM un_file_object WHERE id=%s", (state["fileId"],))
        file_row = cursor.fetchone()
        cursor.execute("SELECT COUNT(*) FROM un_file_reference WHERE file_id=%s AND owner_type='BUSINESS_RECORD' "
                       "AND owner_id=%s AND field_code=%s",
                       (state["fileId"], f"{state['moduleCode']}:{state['recordId']}", state["fileCode"]))
        reference_count = cursor.fetchone()[0]
        cursor.execute("SELECT status,output_file_id FROM un_print_job WHERE id=%s", (state["printJobId"],))
        print_row = cursor.fetchone()
        cursor.execute("SELECT status,exported_rows,result_file_id FROM un_exchange_export_batch WHERE id=%s",
                       (state["exportBatchId"],))
        export_row = cursor.fetchone()
    if not file_row or file_row[0] != "ACTIVE" or file_row[1] != state["fileSha256"] \
            or reference_count < 1 or not print_row or print_row[0] != "SUCCEEDED" \
            or not print_row[1] or export_row[:2] != ("COMPLETED", 1) or not export_row[2]:
        raise AssertionError(f"Database artifact mismatch: {file_row}, {reference_count}, {print_row}, {export_row}")
    storage_root = Path(os.environ.get("S19_FILE_STORAGE_ROOT",
                                       str(ROOT / "projects/unexamine/backend/data/files")))
    disk_path = storage_root / file_row[2]
    if not disk_path.is_file() or hashlib.sha256(disk_path.read_bytes()).hexdigest() != state["fileSha256"]:
        raise AssertionError(f"Stored attachment bytes are missing: {disk_path}")
    state.update({"status": "browser_api_database_file_verified", "fileReferenceCount": reference_count,
                  "printOutputFileId": print_row[1], "exportResultFileId": export_row[2],
                  "browser": {key: browser[key] for key in ("attachment", "printed", "exporting")},
                  "storedAttachmentPath": str(disk_path)})
    EVIDENCE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "fileId": state["fileId"],
                      "printJobId": state["printJobId"], "exportBatchId": state["exportBatchId"]},
                     ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("configure", "verify", "readback"):
        raise SystemExit("usage: verify-product-release-artifacts.py configure|verify|readback")
    {"configure": configure, "verify": verify, "readback": readback}[sys.argv[1]]()
