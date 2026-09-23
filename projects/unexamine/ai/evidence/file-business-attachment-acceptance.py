"""B13.1 分片上传、文件安全边界和业务附件字段真实验收。"""
import ast
import base64
import hashlib
import json
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "file-business-attachment-facts.json"
SYSTEM_ID, TENANT_ID, OTHER_TENANT_ID = 54, 57, 56

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
            raw = response.read()
            if response.headers.get_content_type() == "application/json":
                return response.status, json.loads(raw)
            return response.status, raw
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else {}


def start_upload(token, name, content_type, content):
    return api("POST", "/api/files/uploads", {"originalName": name, "contentType": content_type,
        "expectedSize": len(content), "expectedSha256": hashlib.sha256(content).hexdigest()}, token)


def put_chunk(token, session, offset, chunk, digest=None):
    return binary_call("PUT", f"/api/files/uploads/{session['id']}/chunks?offset={offset}", token, chunk,
        {"Content-Type": "application/octet-stream", "X-Upload-Token": session["uploadToken"],
         "X-Chunk-Sha256": digest or hashlib.sha256(chunk).hexdigest()})


def complete(token, session):
    return binary_call("POST", f"/api/files/uploads/{session['id']}/complete", token, headers={
        "X-Upload-Token": session["uploadToken"]})


def controlled_upload(token, name, content_type, content):
    session = start_upload(token, name, content_type, content)
    offset = 0
    while offset < len(content):
        chunk = content[offset:offset + 1024 * 1024]
        status, result = put_chunk(token, session, offset, chunk)
        assert status == 200, result
        offset = result["data"]["uploadedSize"]
    status, result = complete(token, session)
    assert status == 200, result
    return session, result["data"]


def publish_module(module_id, token):
    check = api("GET", f"/api/admin/module-config/modules/{module_id}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/admin/module-config/modules/{module_id}/publish",
               {"expectedDraftRevision": check["draftRevision"]}, token)


facts = {"step": "B13.1", "status": "running", "cases": [], "fixtures": {}, "cleanup": {}}
suffix = secrets.token_hex(5)
module_code = "file_accept_" + suffix

try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]
    entered = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {}, platform)
    token = entered["tokens"]["accessToken"]

    content = (b"B13-resumable-" + secrets.token_bytes(127)) * 40001
    session = start_upload(token, "B13-large.bin", "application/octet-stream", content)
    status, incomplete = complete(token, session)
    assert status == 409 and incomplete["code"] == "FILE_UPLOAD_INCOMPLETE", incomplete
    assert sql(f"SELECT COUNT(*) FROM file_object WHERE upload_session_id={session['id']}")[0][0] == "0"
    first = content[:1024 * 1024]
    status, written = put_chunk(token, session, 0, first)
    assert status == 200 and not written["data"]["idempotent"]
    status, replay = put_chunk(token, session, 0, first)
    assert status == 200 and replay["data"]["idempotent"] and replay["data"]["uploadedSize"] == len(first)
    status, mismatch = put_chunk(token, session, 0, b"x" * len(first))
    assert status == 409 and mismatch["code"] == "FILE_CHUNK_REPLAY_MISMATCH", mismatch
    digest_probe = content[len(first):len(first) + 100_000]
    status, bad_digest = put_chunk(token, session, len(first), digest_probe, "0" * 64)
    assert status == 422 and bad_digest["code"] == "FILE_CHUNK_CHECKSUM_MISMATCH", bad_digest
    offset = len(first)
    while offset < len(content):
        chunk = content[offset:offset + 1024 * 1024]
        status, final_chunk = put_chunk(token, session, offset, chunk)
        assert status == 200, final_chunk
        offset = final_chunk["data"]["uploadedSize"]
    assert offset == len(content)
    status, completed = complete(token, session)
    assert status == 200 and completed["data"]["sha256"] == hashlib.sha256(content).hexdigest()
    file_id = completed["data"]["id"]
    status, replay_complete = complete(token, session)
    assert status == 200 and replay_complete["data"]["id"] == file_id
    status, downloaded = binary_call("GET", f"/api/files/{file_id}/download", token)
    assert status == 200 and downloaded == content
    facts["cases"].append("resumable-chunk-idempotency-incomplete-and-checksum-boundaries")
    facts["fixtures"]["large_file_id"] = file_id

    module = api("POST", "/api/admin/module-config/modules", {
        "code": module_code, "name": "文件字段受控验收", "groupName": "文件与附件验收"}, token)["module"]
    api("POST", f"/api/admin/module-config/modules/{module['id']}/fields", {
        "code": "subject", "name": "主题", "fieldType": "TEXT", "required": True,
        "sortOrder": 10, "config": {"placeholder": "请输入主题"}}, token)
    api("POST", f"/api/admin/module-config/modules/{module['id']}/fields", {
        "code": "required_file", "name": "必交材料", "fieldType": "ATTACHMENT", "required": True,
        "sortOrder": 20, "config": {"maxSizeMb": 50, "allowedTypes": ["text/plain"]}}, token)
    api("POST", f"/api/admin/module-config/modules/{module['id']}/fields", {
        "code": "cover_image", "name": "材料缩略图", "fieldType": "IMAGE", "required": False,
        "sortOrder": 30, "config": {"maxSizeMb": 2}}, token)
    publish_module(module["id"], token)

    _, rejected_type = controlled_upload(token, "not-allowed.pdf", "application/pdf", b"%PDF-1.7 rejected")
    rejected_status, rejected_record = call("POST", f"/api/runtime/modules/{module_code}/records", {
        "title": "不得创建的错误类型记录", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"subject": "字段类型必须由服务端复核", "required_file": rejected_type["id"]}}, token)
    assert rejected_status == 422 and rejected_record["code"] == "FIELD_VALIDATION_FAILED", rejected_record
    delete_status, deleted_rejected = binary_call("DELETE", f"/api/files/{rejected_type['id']}", token)
    assert delete_status == 200 and deleted_rejected["data"]["status"] == "DELETED"
    facts["cases"].append("field-configured-file-type-is-server-enforced-and-invalid-file-stays-unattachable")
    facts["cleanup"]["rejected_type_file_deleted"] = True

    _, attachment = controlled_upload(token, "B13-material.txt", "text/plain",
                                      ("真实业务附件 " + suffix).encode("utf-8"))
    png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    _, image = controlled_upload(token, "B13-thumbnail.png", "image/png", png)
    record = api("POST", f"/api/runtime/modules/{module_code}/records", {
        "title": "B13 文件字段真实记录", "status": "ACTIVE", "participantMemberIds": [],
        "fields": {"subject": "断点上传与必填附件", "required_file": attachment["id"],
                   "cover_image": image["id"]}}, token)
    assert record["fields"]["required_file"] == attachment["id"]
    stored = sql("SELECT v.value_file_id,r.id FROM biz_record_value v JOIN file_reference r "
        f"ON r.file_id=v.value_file_id AND r.owner_type='BUSINESS_RECORD' AND r.owner_id='{module_code}:{record['id']}' "
        "AND r.field_code='required_file' "
        f"WHERE v.record_id={record['id']} AND v.field_code='required_file'")
    assert len(stored) == 1 and int(stored[0][0]) == attachment["id"]
    listed = api("GET", f"/api/business-attachments/records/{module_code}/{record['id']}/fields/required_file", token=token)
    assert len(listed) == 1 and listed[0]["fileId"] == attachment["id"] and listed[0]["previewable"]
    assert not listed[0]["removable"]
    remove_status, remove_body = binary_call("DELETE", f"/api/business-attachments/{listed[0]['attachmentId']}", token)
    assert remove_status == 409 and remove_body["code"] == "FILE_FIELD_MANAGED_BY_RECORD", remove_body
    delete_status, delete_body = binary_call("DELETE", f"/api/files/{attachment['id']}", token)
    assert delete_status == 409 and delete_body["code"] == "FILE_IN_USE", delete_body
    invalid_status, invalid_update = call("PUT", f"/api/runtime/modules/{module_code}/records/{record['id']}", {
        "title": record["title"], "status": record["status"], "participantMemberIds": [],
        "version": record["version"], "fields": {"subject": "禁止清空必交材料", "required_file": None}}, token)
    assert invalid_status == 422 and invalid_update["code"] == "FIELD_VALIDATION_FAILED", invalid_update

    _, replacement = controlled_upload(token, "B13-material-v2.txt", "text/plain",
                                       ("替换后的真实业务附件 " + suffix).encode("utf-8"))
    updated = api("PUT", f"/api/runtime/modules/{module_code}/records/{record['id']}", {
        "title": record["title"], "status": record["status"], "participantMemberIds": [],
        "version": record["version"], "fields": {"subject": "附件已通过记录编辑替换",
                                                    "required_file": replacement["id"],
                                                    "cover_image": image["id"]}}, token)
    assert updated["version"] == record["version"] + 1 and updated["fields"]["required_file"] == replacement["id"]
    listed = api("GET", f"/api/business-attachments/records/{module_code}/{record['id']}/fields/required_file", token=token)
    assert len(listed) == 1 and listed[0]["fileId"] == replacement["id"] and not listed[0]["removable"]
    delete_status, deleted_old = binary_call("DELETE", f"/api/files/{attachment['id']}", token)
    assert delete_status == 200 and deleted_old["data"]["status"] == "DELETED"
    attachment = replacement
    facts["cases"].append("required-attachment-create-clear-rejection-replacement-and-delete-protection")
    facts["fixtures"].update({"module_id": module["id"], "module_code": module_code,
                              "record_id": record["id"], "attachment_file_id": attachment["id"],
                              "image_file_id": image["id"]})

    other_token = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{OTHER_TENANT_ID}/enter", {}, platform)["tokens"]["accessToken"]
    cross_status, cross_body = binary_call("GET", f"/api/files/{attachment['id']}", other_token)
    assert cross_status == 404 and cross_body["code"] == "FILE_NOT_FOUND", cross_body
    facts["cases"].append("cross-tenant-file-read-rejected")

    # 未引用的大文件验证删除及磁盘清理；业务附件保留供普通页面复核。
    delete_status, deleted = binary_call("DELETE", f"/api/files/{file_id}", token)
    assert delete_status == 200 and deleted["data"]["status"] == "DELETED"
    facts["cleanup"]["large_unreferenced_file_deleted"] = True
    facts["status"] = "passed"
finally:
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(json.dumps({"status": facts["status"], "cases": len(facts["cases"]),
                  "module": facts["fixtures"].get("module_code"),
                  "record": facts["fixtures"].get("record_id")}, ensure_ascii=False))
