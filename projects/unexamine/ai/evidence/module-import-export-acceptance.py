"""B13.2 模块内 Excel 导入、预演、执行、回滚和权限导出真实验收。"""
import ast
import hashlib
import io
import json
import re
import secrets
import time
import urllib.error
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path
from xml.sax.saxutils import escape

AREA = Path(__file__).parent
FACTS = AREA / "module-import-export-facts.json"
SYSTEM_ID, TENANT_ID = 54, 57

tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))


def binary_call(method, path, token, body=None, headers=None):
    supplied = {"Authorization": "Bearer " + token} | (headers or {})
    request = urllib.request.Request("http://localhost:18080" + path, method=method,
        headers=supplied, data=body)
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            return response.status, response.read(), dict(response.headers)
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else {}, dict(error.headers)


def xlsx(rows):
    """只生成验收需要的单工作表字符串单元格，避免引入第二套运行依赖。"""
    sheet_rows = []
    for row_number, values in enumerate(rows, 1):
        cells = []
        for column, value in enumerate(values, 1):
            letters, number = "", column
            while number:
                number, remain = divmod(number - 1, 26)
                letters = chr(65 + remain) + letters
            cells.append(f'<c r="{letters}{row_number}" t="inlineStr"><is><t xml:space="preserve">'
                         f'{escape(str(value))}</t></is></c>')
        sheet_rows.append(f'<row r="{row_number}">{"".join(cells)}</row>')
    parts = {
        "[Content_Types].xml": '<?xml version="1.0" encoding="UTF-8"?>'
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
            '</Types>',
        "_rels/.rels": '<?xml version="1.0" encoding="UTF-8"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            '</Relationships>',
        "xl/workbook.xml": '<?xml version="1.0" encoding="UTF-8"?>'
            '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            '<sheets><sheet name="导入数据" sheetId="1" r:id="rId1"/></sheets></workbook>',
        "xl/_rels/workbook.xml.rels": '<?xml version="1.0" encoding="UTF-8"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
            '</Relationships>',
        "xl/worksheets/sheet1.xml": '<?xml version="1.0" encoding="UTF-8"?>'
            '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            f'<sheetData>{"".join(sheet_rows)}</sheetData></worksheet>',
    }
    result = io.BytesIO()
    with zipfile.ZipFile(result, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in parts.items():
            archive.writestr(name, content.encode("utf-8"))
    return result.getvalue()


def upload(token, name, content_type, content):
    session = api("POST", "/api/files/uploads", {"originalName": name, "contentType": content_type,
        "expectedSize": len(content), "expectedSha256": hashlib.sha256(content).hexdigest()}, token)
    offset = 0
    while offset < len(content):
        chunk = content[offset:offset + 1024 * 1024]
        status, result, _ = binary_call("PUT", f"/api/files/uploads/{session['id']}/chunks?offset={offset}", token,
            chunk, {"Content-Type": "application/octet-stream", "X-Upload-Token": session["uploadToken"],
                    "X-Chunk-Sha256": hashlib.sha256(chunk).hexdigest()})
        assert status == 200, result
        offset = json.loads(result)["data"]["uploadedSize"] if isinstance(result, bytes) else result["data"]["uploadedSize"]
    status, result, _ = binary_call("POST", f"/api/files/uploads/{session['id']}/complete", token,
        headers={"X-Upload-Token": session["uploadToken"]})
    assert status == 200, result
    completed = json.loads(result)["data"] if isinstance(result, bytes) else result["data"]
    return api("POST", f"/api/files/{completed['id']}/references", {
        "ownerType": "ACCOUNT", "ownerId": str(account_id), "fieldCode": "managed_files",
        "referenceType": "DOCUMENT"}, token)


def publish(module_id, token):
    check = api("GET", f"/api/admin/module-config/modules/{module_id}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/admin/module-config/modules/{module_id}/publish",
               {"expectedDraftRevision": check["draftRevision"]}, token)


def wait_batch(token, path, terminal, attempts=80):
    for _ in range(attempts):
        batch = api("GET", path, token=token)
        if batch["status"] in terminal:
            return batch
        time.sleep(.25)
    raise AssertionError(("batch timeout", path, batch))


def records(token, module_code):
    result = api("GET", f"/api/runtime/modules/{module_code}/records?page=1&pageSize=100", token=token)
    return result["records"]


facts = {"step": "B13.2", "status": "running", "cases": [], "fixtures": {}, "cleanup": {}}
suffix = secrets.token_hex(5)
module_code = "exchange_accept_" + suffix

try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]
    entered = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {}, platform)
    token = entered["tokens"]["accessToken"]
    account_id = int(sql("SELECT id FROM plat_account WHERE username='" +
        re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1).replace("'", "''") + "'")[0][0])

    module = api("POST", "/api/admin/module-config/modules", {
        "code": module_code, "name": "模块导入导出验收", "groupName": "数据交换验收"}, token)["module"]
    for field in [
        {"code": "external_code", "name": "外部唯一编码", "fieldType": "TEXT", "required": True,
         "uniqueValue": True, "searchable": True, "sortOrder": 10, "config": {}},
        {"code": "note", "name": "备注", "fieldType": "TEXT", "required": False,
         "uniqueValue": False, "searchable": True, "sortOrder": 20, "config": {}},
        {"code": "amount", "name": "金额", "fieldType": "MONEY", "required": True,
         "uniqueValue": False, "searchable": False, "sortOrder": 30,
         "config": {"currency": "CNY", "precision": 2}},
        {"code": "stage", "name": "阶段", "fieldType": "SINGLE_SELECT", "required": True,
         "uniqueValue": False, "searchable": True, "sortOrder": 40,
         "config": {"options": [{"value": "active", "label": "进行中"},
                                  {"value": "done", "label": "已完成"}]}}
    ]:
        api("POST", f"/api/admin/module-config/modules/{module['id']}/fields", field, token)
    publish(module["id"], token)
    original = api("POST", f"/api/runtime/modules/{module_code}/records", {
        "title": "导入前标题", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"external_code": "EX-A", "note": "必须保留的原备注",
                   "amount": 1, "stage": "active"}}, token)

    status, template_bytes, headers = binary_call(
        "GET", f"/api/runtime/modules/{module_code}/imports/template.xlsx", token)
    assert status == 200 and template_bytes.startswith(b"PK") and "spreadsheetml" in headers.get("Content-Type", "")
    with zipfile.ZipFile(io.BytesIO(template_bytes)) as archive:
        template_text = "".join(archive.read(name).decode("utf-8", "ignore")
            for name in archive.namelist() if name.endswith(".xml"))
    assert "外部唯一编码" in template_text and "业务编号（仅匹配）" in template_text
    facts["cases"].append("server-generated-readable-excel-template")

    source_bytes = xlsx([
        ["标题", "外部唯一编码", "金额", "阶段"],
        ["导入更新标题", "EX-A", "12.50", "进行中"],
        ["导入新增标题", "EX-B", "21.00", "已完成"],
        ["金额错误", "EX-C", "不是数字", "进行中"],
        ["文件内重复", "EX-B", "30", "进行中"],
    ])
    source = upload(token, "B13-module-import.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", source_bytes)
    source_headers = api("GET", f"/api/runtime/modules/{module_code}/imports/sources/{source['id']}/headers", token=token)
    assert source_headers == {"sourceFileId": source["id"], "format": "XLSX",
                              "headers": ["标题", "外部唯一编码", "金额", "阶段"]}
    template = api("GET", f"/api/runtime/modules/{module_code}/imports/template", token=token)
    external = next(column for column in template["columns"] if column["code"] == "external_code")
    record_number = next(column for column in template["columns"] if column["code"] == "recordNumber")
    assert external["uniqueMatch"] and record_number["uniqueMatch"] and not record_number["writable"]
    preview = api("POST", f"/api/runtime/modules/{module_code}/imports/preview", {
        "sourceFileId": source["id"],
        "columnMapping": {"标题": "title", "外部唯一编码": "external_code", "金额": "amount", "阶段": "stage"},
        "conflictPolicy": "UPDATE", "matchFieldCode": "external_code", "mappingName": "Excel 可视化字段映射"}, token)
    assert preview["status"] == "PREVIEWED" and preview["totalRows"] == 4
    assert preview["summary"]["create"] == 1 and preview["summary"]["update"] == 1
    assert preview["failedRows"] == 2 and preview["summary"]["error"] == 2
    before_execute = next(item for item in records(token, module_code) if item["id"] == original["id"])
    assert before_execute["title"] == "导入前标题" and before_execute["fields"]["note"] == "必须保留的原备注"
    status, errors_csv, _ = binary_call(
        "GET", f"/api/runtime/modules/{module_code}/imports/{preview['id']}/errors.csv", token)
    assert status == 200 and b"IMPORT_DUPLICATE_MATCH_IN_FILE" in errors_csv and "必须是数字".encode() in errors_csv
    facts["cases"].append("excel-header-mapping-preview-errors-and-zero-write")

    queued = api("POST", f"/api/runtime/modules/{module_code}/imports/{preview['id']}/execute",
        {"version": preview["version"]}, token)
    assert queued["status"] in {"QUEUED", "RUNNING", "COMPLETED_WITH_ERRORS"}
    completed = wait_batch(token, f"/api/runtime/modules/{module_code}/imports/{preview['id']}",
                           {"COMPLETED", "COMPLETED_WITH_ERRORS"})
    assert completed["status"] == "COMPLETED_WITH_ERRORS" and completed["successRows"] == 2
    current = records(token, module_code)
    updated = next(item for item in current if item["id"] == original["id"])
    created = next(item for item in current if item["fields"].get("external_code") == "EX-B")
    assert updated["title"] == "导入更新标题" and updated["fields"]["note"] == "必须保留的原备注"
    assert updated["fields"]["stage"] == "active" and float(updated["fields"]["amount"]) == 12.5
    assert len([item for item in current if item["fields"].get("external_code") == "EX-C"]) == 0
    facts["cases"].append("background-create-update-current-permission-and-unmapped-field-preservation")

    changed = api("PUT", f"/api/runtime/modules/{module_code}/records/{updated['id']}", {
        "title": updated["title"], "recordNumber": updated.get("recordNumber"), "status": updated["status"],
        "ownerMemberId": updated.get("ownerMemberId"), "departmentId": updated.get("departmentId"),
        "participantMemberIds": updated["participantMemberIds"], "version": updated["version"],
        "fields": updated["fields"] | {"note": "导入后人工修改，回滚不得覆盖"}}, token)
    rolled = api("POST", f"/api/runtime/modules/{module_code}/imports/{preview['id']}/rollback",
        {"reason": "验证只回滚本批次且未被后续修改的数据"}, token)
    assert rolled["status"] == "ROLLBACK_PARTIAL"
    active = records(token, module_code)
    preserved = next(item for item in active if item["id"] == original["id"])
    assert preserved["version"] == changed["version"] and preserved["fields"]["note"] == "导入后人工修改，回滚不得覆盖"
    assert all(item["id"] != created["id"] for item in active)
    assert any(row.get("rollbackStatus") == "ROLLBACK_CONFLICT" for row in rolled["rows"])
    facts["cases"].append("batch-rollback-removes-own-create-and-preserves-later-change")

    export_body = {
        "lifecycleState": "ACTIVE", "tenantScope": "OWN", "search": "", "filters": [
            {"fieldCode": "external_code", "operator": "EQ", "value": "EX-A"}],
        "sortField": "updatedAt", "sortDirection": "DESC", "selectedRecordIds": [], "format": "XLSX",
        "idempotencyKey": "b13-export-" + suffix,
        "selectedFields": ["recordNumber", "title", "external_code", "note", "amount", "stage"]}
    estimate = api("POST", f"/api/runtime/modules/{module_code}/exports/estimate", export_body, token)
    assert estimate["scope"] == "CURRENT_FILTER" and estimate["estimatedRows"] == 1
    first_export = api("POST", f"/api/runtime/modules/{module_code}/exports", export_body, token)
    replay_export = api("POST", f"/api/runtime/modules/{module_code}/exports", export_body, token)
    assert replay_export["id"] == first_export["id"]
    exported = wait_batch(token, f"/api/runtime/modules/{module_code}/exports/{first_export['id']}",
                          {"COMPLETED", "FAILED"})
    assert exported["status"] == "COMPLETED" and exported["format"] == "XLSX" and exported["exportedRows"] == 1
    status, exported_bytes, response_headers = binary_call(
        "GET", f"/api/runtime/modules/{module_code}/exports/{exported['id']}/download", token)
    assert status == 200 and exported_bytes.startswith(b"PK") and "spreadsheetml" in response_headers.get("Content-Type", "")
    with zipfile.ZipFile(io.BytesIO(exported_bytes)) as archive:
        export_text = "".join(archive.read(name).decode("utf-8", "ignore")
            for name in archive.namelist() if name.endswith(".xml"))
    assert "导入后人工修改，回滚不得覆盖" in export_text and "进行中" in export_text and "EX-A" in export_text
    facts["cases"].append("filtered-permission-export-excel-display-values-and-idempotent-submit")

    db_rows = sql("SELECT b.id,b.status,b.total_rows,b.success_rows,b.failed_rows,COUNT(r.id) "
        "FROM exchange_import_batch b JOIN exchange_import_row r ON r.batch_id=b.id "
        f"WHERE b.id={preview['id']} GROUP BY b.id,b.status,b.total_rows,b.success_rows,b.failed_rows")
    assert db_rows == [[str(preview["id"]), "ROLLBACK_PARTIAL", "4", "2", "2", "4"]]
    facts["fixtures"] = {"module_id": module["id"], "module_code": module_code,
                          "import_batch_id": preview["id"], "export_batch_id": exported["id"],
                          "record_id": original["id"], "source_file_id": source["id"],
                          "result_file_id": exported["resultFileId"]}
    facts["status"] = "passed"
finally:
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(json.dumps({"status": facts["status"], "cases": len(facts["cases"]),
                  "module": facts["fixtures"].get("module_code"),
                  "importBatch": facts["fixtures"].get("import_batch_id"),
                  "exportBatch": facts["fixtures"].get("export_batch_id")}, ensure_ascii=False))
