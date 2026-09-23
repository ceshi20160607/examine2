"""B13.1 文件容量、分片回收和 S3 兼容对象存储的真实边界验收。"""
import ast
import hashlib
import json
import math
import re
import secrets
import threading
import time
import urllib.parse
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "file-storage-boundary-facts.json"
SYSTEM_ID, TENANT_ID = 54, 57

tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))


def binary_call(method, path, token, body=None, headers=None):
    import urllib.error
    import urllib.request
    supplied = {"Authorization": "Bearer " + token} | (headers or {})
    request = urllib.request.Request("http://localhost:18080" + path, method=method, headers=supplied, data=body)
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            raw = response.read()
            return response.status, (json.loads(raw) if response.headers.get_content_type() == "application/json" else raw)
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else {}


def start(token, name, content_type, size, digest=None):
    return call("POST", "/api/files/uploads", {"originalName": name, "contentType": content_type,
        "expectedSize": size, "expectedSha256": digest}, token)


def cancel(token, session):
    return binary_call("DELETE", f"/api/files/uploads/{session['id']}", token,
        headers={"X-Upload-Token": session["uploadToken"]})


objects = {}


class FakeS3(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_PUT(self):
        length = int(self.headers.get("Content-Length", "0"))
        objects[urllib.parse.urlsplit(self.path).path] = self.rfile.read(length)
        self.send_response(200)
        self.send_header("ETag", '"acceptance-etag"')
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        key = urllib.parse.urlsplit(self.path).path
        if key not in objects:
            body = b"<Error><Code>NoSuchKey</Code></Error>"
            self.send_response(404)
            self.send_header("Content-Type", "application/xml")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        body = objects[key]
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_DELETE(self):
        objects.pop(urllib.parse.urlsplit(self.path).path, None)
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, _format, *_args):
        return


facts = {"step": "B13.1", "status": "running", "cases": [], "cleanup": {}}
reserved_sessions = []
object_file = None
server = None

try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]
    token = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {}, platform)["tokens"]["accessToken"]

    policy = api("GET", "/api/files/policy", token=token)
    assert policy["maximumFileSizeBytes"] == 50 * 1024 * 1024
    assert policy["contextQuotaBytes"] > policy["maximumFileSizeBytes"]
    status, too_large = start(token, "too-large.bin", "application/octet-stream",
                              policy["maximumFileSizeBytes"] + 1)
    assert status == 422 and too_large["code"] == "FILE_SIZE_LIMIT_EXCEEDED", too_large

    partial = b"cancelled-partial-content" * 5000
    status, started = start(token, "cancelled.bin", "application/octet-stream", len(partial) * 2,
                            hashlib.sha256(partial * 2).hexdigest())
    assert status == 200, started
    session = started["data"]
    status, written = binary_call("PUT", f"/api/files/uploads/{session['id']}/chunks?offset=0", token, partial,
        {"Content-Type": "application/octet-stream", "X-Upload-Token": session["uploadToken"],
         "X-Chunk-Sha256": hashlib.sha256(partial).hexdigest()})
    assert status == 200 and written["data"]["uploadedSize"] == len(partial), written
    object_key = sql(f"SELECT object_key FROM file_upload_session WHERE id={session['id']}")[0][0]
    part_path = AREA.parents[1] / "backend/data/files" / (object_key + ".part")
    assert part_path.exists(), part_path
    status, cancelled = cancel(token, session)
    assert status == 200 and cancelled["data"]["status"] == "CANCELLED", cancelled
    time.sleep(.2)
    assert not part_path.exists()
    assert sql(f"SELECT COUNT(*) FROM file_object WHERE upload_session_id={session['id']}")[0][0] == "0"
    facts["cases"].append("visible-size-policy-and-cancelled-part-cleanup")

    # 只预留元数据而不写入大文件，精确触达配额边界；结束后逐一取消，验证预留容量全部释放。
    baseline = api("GET", "/api/files/policy", token=token)
    maximum = baseline["maximumFileSizeBytes"]
    for index in range(math.ceil(baseline["availableBytes"] / maximum) + 1):
        status, response = start(token, f"quota-{index}.bin", "application/octet-stream", maximum)
        if status == 422:
            assert response["code"] == "FILE_CONTEXT_QUOTA_EXCEEDED", response
            break
        assert status == 200, response
        reserved_sessions.append(response["data"])
    else:
        raise AssertionError("quota boundary was not reached")
    assert reserved_sessions
    for item in reserved_sessions:
        status, response = cancel(token, item)
        assert status == 200, response
    reserved_sessions.clear()
    released = api("GET", "/api/files/policy", token=token)
    assert released["reservedBytes"] == baseline["reservedBytes"]
    facts["cases"].append("context-quota-rejects-over-reservation-and-releases-cancelled-sessions")

    # 临时协议端点只验证 S3 适配器，不启动第二套项目服务；凭证不会写入验收文件。
    server = ThreadingHTTPServer(("0.0.0.0", 0), FakeS3)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    port = server.server_address[1]
    configured = api("PUT", "/api/files/storage/object", {
        "name": "B13 对象存储协议验收", "endpoint": f"http://host.docker.internal:{port}",
        "bucketName": "b13-files", "region": "us-east-1", "accessKeyId": "acceptance-access",
        "secretAccessKey": "acceptance-secret", "pathStyle": True, "activate": True}, token)
    assert configured["status"] == "ACTIVE" and configured["credentialConfigured"]
    backend_id = configured["id"]
    encrypted = sql(f"SELECT credential_ref FROM file_storage_backend WHERE id={backend_id}")[0][0]
    assert encrypted.startswith("runtime-secret:v1:") and "acceptance" not in encrypted
    object_content = ("对象存储真实读回 " + secrets.token_hex(8)).encode("utf-8")
    digest = hashlib.sha256(object_content).hexdigest()
    status, response = start(token, "object-storage.txt", "text/plain", len(object_content), digest)
    assert status == 200, response
    object_session = response["data"]
    status, response = binary_call("PUT", f"/api/files/uploads/{object_session['id']}/chunks?offset=0", token,
        object_content, {"Content-Type": "application/octet-stream", "X-Upload-Token": object_session["uploadToken"],
                         "X-Chunk-Sha256": digest})
    assert status == 200, response
    status, completed = binary_call("POST", f"/api/files/uploads/{object_session['id']}/complete", token,
        headers={"X-Upload-Token": object_session["uploadToken"]})
    assert status == 200, completed
    object_file = completed["data"]
    row = sql("SELECT b.backend_type,o.object_key FROM file_object o JOIN file_storage_backend b "
              f"ON b.id=o.storage_backend_id WHERE o.id={object_file['id']}")[0]
    assert row[0] == "S3" and objects[f"/b13-files/{row[1]}"] == object_content
    status, downloaded = binary_call("GET", f"/api/files/{object_file['id']}/download", token)
    assert status == 200 and downloaded == object_content
    status, deleted = binary_call("DELETE", f"/api/files/{object_file['id']}", token)
    assert status == 200 and deleted["data"]["status"] == "DELETED", deleted
    time.sleep(.2)
    assert f"/b13-files/{row[1]}" not in objects
    deactivated = api("POST", "/api/files/storage/object/deactivate", {}, token)
    assert deactivated["status"] == "INACTIVE"
    assert api("GET", "/api/files/policy", token=token)["backendType"] == "LOCAL"
    facts["cases"].append("encrypted-s3-compatible-probe-upload-read-delete-and-local-fallback")

    # 精确清理协议夹具；业务文件已经按公开删除接口完成字节和状态收尾。
    sql(f"DELETE FROM file_security_scan WHERE file_id={object_file['id']}; "
        f"DELETE FROM file_object WHERE id={object_file['id']}; "
        f"DELETE FROM file_upload_session WHERE id={object_session['id']}; "
        f"DELETE FROM file_storage_backend WHERE id={backend_id}")
    object_file = None
    facts["cleanup"].update(quota_sessions_cancelled=True, fake_object_backend_removed=True,
                            object_bytes_removed=True, credentials_not_recorded=True)
    facts["status"] = "passed"
finally:
    for item in reserved_sessions:
        try:
            cancel(token, item)
        except Exception:
            pass
    if server:
        server.shutdown()
        server.server_close()
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(json.dumps({"status": facts["status"], "cases": len(facts["cases"]),
                  "cleanup": facts["cleanup"]}, ensure_ascii=False))
