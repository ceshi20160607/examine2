"""B10.2d.1 已发布应用回调、签名、重试、重启恢复和单次推进验收。"""
import ast
import hashlib
import hmac
import http.server
import json
import re
import secrets
import ssl
import subprocess
import threading
import time
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
TLS = AREA / "webhook-acceptance-tls-b102d1-pem"
FACTS = AREA / "flow-webhook-facts.json"
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))

SYSTEM_ID = 54
TENANT_ID = 57
PORT = 18443


def save(facts):
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def wait_health(seconds=100):
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            status, result = call("GET", "/actuator/health")
            if status == 200 and result.get("status") == "UP":
                return
        except Exception:
            pass
        time.sleep(.5)
    raise AssertionError("backend health timeout")


def start_receiver(secret, log):
    """启动一个本机 HTTPS 接收器；只记录签名结论、事件标识和载荷摘要。"""
    class Receiver(http.server.ThreadingHTTPServer):
        daemon_threads = True

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_POST(self):
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length)
            event_id = self.headers.get("X-Unexamine-Event-Id", "")
            timestamp = self.headers.get("X-Unexamine-Timestamp", "")
            supplied = self.headers.get("X-Unexamine-Signature", "")
            payload_hash = hashlib.sha256(body).hexdigest()
            expected = "v1=" + hmac.new(secret.encode(),
                f"{timestamp}\n{event_id}\n{payload_hash}".encode(), hashlib.sha256).hexdigest()
            valid = hmac.compare_digest(expected, supplied)
            with self.server.acceptance_lock:
                self.server.acceptance_count += 1
                sequence = self.server.acceptance_count
                status = 401 if not valid else 503 if sequence == 1 else 204
                with log.open("a", encoding="utf-8") as output:
                    output.write(f"{sequence}\t{str(valid).lower()}\t{status}\t{event_id}\t{payload_hash}\n")
            self.send_response(status)
            self.end_headers()

        def log_message(self, *_):
            return

    server = Receiver(("0.0.0.0", PORT), Handler)
    server.acceptance_count = 0
    server.acceptance_lock = threading.Lock()
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(TLS / "receiver.crt", TLS / "receiver.key")
    server.socket = context.wrap_socket(server.socket, server_side=True)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def receiver_rows(log):
    if not log.exists():
        return []
    rows = []
    for line in log.read_text(encoding="utf-8").splitlines():
        sequence, valid, status, event_id, payload_hash = line.split("\t")
        rows.append({"sequence": int(sequence), "signatureValid": valid == "true",
                     "responseStatus": int(status), "eventId": event_id, "payloadHash": payload_hash})
    return rows


facts = {"step": "B10.2d.1", "status": "running", "cases": [], "cleanup": {}}
secret_code = "flow_webhook_" + secrets.token_hex(5)
application_code = "flow_webhook_app_" + secrets.token_hex(5)
flow_code = "flow_webhook_flow_" + secrets.token_hex(5)
receiver = None
application = flow = secret_ref = None
try:
    wait_health()
    java_root = AREA.parents[1] / "backend/src/main/java/com/unique/unexamine"
    bootstrap = (java_root / "platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    admin = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                login["accessToken"])["tokens"]["accessToken"]

    issued = api("POST", "/api/admin/system/operations/security-performance/secret-refs", {
        "secretCode": secret_code, "secretType": "WEBHOOK", "consumerCodes": [application_code],
        "approvalReference": "B10.2d.1 local acceptance", "confirmation": "CREATE SECRET " + secret_code}, admin)
    one_time_secret = issued["oneTimeSecret"]
    secret_ref = issued["secretRef"]

    log = TLS / "receiver.log"
    if log.exists():
        log.unlink()
    receiver = start_receiver(one_time_secret, log)

    resources = api("GET", "/api/applications/resources", token=admin)
    resource = next(item for item in resources if item["resourceType"] == "MODULE" and item["actions"])
    action = next((item for item in resource["actions"] if item["code"] == "DETAIL"), resource["actions"][0])
    created = api("POST", "/api/applications", {"code": application_code,
        "name": "流程 Webhook 验收应用", "description": "B10.2d.1 临时验收", "applicationType": "WEBHOOK"}, admin)
    application = created["application"]
    callback = {"callbackType": "EVENT", "url": f"https://host.docker.internal:{PORT}/webhook",
        "eventCodes": ["FLOW.NODE.REACHED"], "signingSecretRef": secret_ref["referencePath"],
        "timeoutMillis": 3000, "maxAttempts": 3}
    grant = {"targetType": "SYSTEM", "targetSystemId": SYSTEM_ID, "targetTenantId": TENANT_ID,
        "resourceType": resource["resourceType"], "resourceId": resource["resourceId"],
        "actionCode": action["code"], "dataScope": {"type": "SELF"},
        "rateLimit": {"accessChannels": ["INTERNAL"], "maxRequests": 100, "windowSeconds": 60},
        "fields": [{"fieldCode": field["code"], "readable": True, "writable": False,
                    "maskStrategy": "NONE"} for field in resource.get("fields", [])]}
    application = api("PUT", f"/api/applications/{application['id']}/draft", {
        "expectedVersion": application["version"], "name": application["name"],
        "description": application["description"], "applicationType": "WEBHOOK",
        "callbacks": [callback], "grants": [grant]}, admin)
    check = api("GET", f"/api/applications/{application['id']}/publication-check", token=admin)
    assert check["valid"], check["issues"]
    published = api("POST", f"/api/applications/{application['id']}/publish", {
        "expectedDraftRevision": check["draftRevision"], "changeSummary": "B10.2d.1 回调验收"}, admin)
    application = published["application"]
    callback_id = application["callbacks"][0]["id"]
    catalog = api("GET", "/api/flows/callback-catalog", token=admin)
    option = next(item for item in catalog if item["applicationId"] == application["id"]
                  and item["callbackId"] == callback_id)
    assert "url" not in option and "signingSecretRef" not in option
    facts["cases"].append({"case": "safe-published-callback-selector", "result": "PASS",
        "applicationId": application["id"], "applicationVersionId": option["applicationVersionId"],
        "callbackId": callback_id, "addressOrSecretExposed": False})

    def node(key, kind, name, x, config=None):
        return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
                "config": config or {}, "assigneePolicy": {}, "formPolicy": {}, "timeoutPolicy": {},
                "exceptionPolicy": {}}

    flow = api("POST", "/api/flows", {"code": flow_code, "name": "流程应用回调验收",
        "description": "B10.2d.1 临时验收"}, admin)
    flow = api("PUT", f"/api/flows/{flow['id']}/draft", {"expectedVersion": flow["version"],
        "nodes": [node("start", "START", "开始", 0),
                  node("callback", "WEBHOOK", "通知外部业务服务", 240,
                       {"applicationId": application["id"], "callbackId": callback_id}),
                  node("end", "END", "结束", 480)],
        "edges": [{"edgeKey": "e1", "sourceNodeKey": "start", "targetNodeKey": "callback",
                   "priorityOrder": 0, "config": {}},
                  {"edgeKey": "e2", "sourceNodeKey": "callback", "targetNodeKey": "end",
                   "priorityOrder": 0, "config": {}}]}, admin)
    flow_check = api("GET", f"/api/flows/{flow['id']}/publication-check", token=admin)
    assert flow_check["valid"], flow_check["issues"]
    flow_publish = api("POST", f"/api/flows/{flow['id']}/publish", {
        "expectedDraftRevision": flow_check["draftRevision"], "changeSummary": "B10.2d.1 回调验收",
        "simulationVariables": {}}, admin)
    detail = api("GET", f"/api/flows/{flow['id']}", token=admin)
    frozen = next(item for item in detail["versions"] if item["id"] == flow_publish["versionId"])["snapshot"]
    frozen_config = next(item for item in frozen["nodes"] if item["nodeKey"] == "callback")["config"]
    assert frozen_config["applicationVersionId"] == option["applicationVersionId"]
    assert frozen_config["callbackId"] == callback_id and frozen_config["timeoutMillis"] == 3000
    assert frozen_config["managedCallback"] is True
    assert "url" not in frozen_config and "signingSecretRef" not in frozen_config
    assert "applicationSnapshotHash" not in frozen_config

    started = api("POST", "/api/flow-runtime/instances", {"flowId": flow["id"],
        "title": "流程 Webhook 真实投递验收", "businessType": "ACCEPTANCE",
        "businessId": secrets.token_hex(6), "businessSnapshot": {}, "variables": {},
        "idempotencyKey": "flow-webhook-" + secrets.token_hex(12)}, admin)["instance"]
    instance_id = started["id"]
    deadline = time.time() + 25
    pending = None
    while time.time() < deadline:
        pending = api("GET", f"/api/flow-runtime/instances/{instance_id}", token=admin)
        jobs = [job for job in pending["jobs"] if job["jobType"] == "WEBHOOK_DELIVERY"]
        if jobs and jobs[0]["status"] == "PENDING" and jobs[0]["attemptCount"] == 1:
            break
        time.sleep(.3)
    assert jobs and jobs[0]["status"] == "PENDING" and jobs[0]["attemptCount"] == 1, pending
    first_rows = receiver_rows(log)
    assert len(first_rows) == 1 and first_rows[0]["signatureValid"] and first_rows[0]["responseStatus"] == 503
    job_id = jobs[0]["id"]
    facts["cases"].append({"case": "signed-delivery-failure-is-durable", "result": "PASS",
        "instanceId": instance_id, "jobId": job_id, **first_rows[0]})
    save(facts)

    subprocess.run(["docker", "stop", "unexamine-golden-backend"], check=True,
                   capture_output=True, text=True, encoding="utf-8")
    sql(f"UPDATE flow_job SET next_run_at=NOW(),version=version+1 WHERE id={int(job_id)} AND status='PENDING'")
    subprocess.run(["docker", "start", "unexamine-golden-backend"], check=True,
                   capture_output=True, text=True, encoding="utf-8")
    wait_health()
    deadline = time.time() + 35
    completed = None
    while time.time() < deadline:
        completed = api("GET", f"/api/flow-runtime/instances/{instance_id}", token=admin)
        if completed["status"] == "COMPLETED":
            break
        time.sleep(.4)
    assert completed["status"] == "COMPLETED", completed
    rows = receiver_rows(log)
    assert len(rows) == 2 and all(item["signatureValid"] for item in rows)
    assert rows[0]["eventId"] == rows[1]["eventId"] and rows[0]["payloadHash"] == rows[1]["payloadHash"]
    webhook_job = next(job for job in completed["jobs"] if job["id"] == job_id)
    assert webhook_job["status"] == "COMPLETED" and webhook_job["attemptCount"] == 2
    delivered_history = [event for event in completed["history"] if event["eventType"] == "FLOW_WEBHOOK_DELIVERED"]
    assert len(delivered_history) == 1 and delivered_history[0]["detail"]["applicationCode"] == application_code
    executions = int(sql("SELECT COUNT(*) FROM flow_execution "
        f"WHERE instance_id={int(instance_id)} AND node_key='callback' AND result_code='WEBHOOK_DELIVERED'")[0][0])
    assert executions == 1
    facts["cases"].append({"case": "restart-retry-advances-once", "result": "PASS",
        "instanceId": instance_id, "attemptCount": 2, "sameEventId": True, "samePayloadHash": True,
        "callbackExecutionCount": executions})

    before = len(rows)
    time.sleep(2.5)
    after = len(receiver_rows(log))
    executions_after = int(sql("SELECT COUNT(*) FROM flow_execution "
        f"WHERE instance_id={int(instance_id)} AND node_key='callback' AND result_code='WEBHOOK_DELIVERED'")[0][0])
    assert after == before and executions_after == 1
    facts["cases"].append({"case": "completed-job-scan-is-idempotent", "result": "PASS",
        "receiverRequestCount": after, "callbackExecutionCount": executions_after})
    facts.update({"status": "passed", "application_id": application["id"], "flow_id": flow["id"],
                  "flow_version_id": flow_publish["versionId"], "callback_id": callback_id,
                  "instance_id": instance_id, "job_id": job_id,
                  "receiver": {"requestCount": after, "signaturesValid": True, "rawPayloadStored": False}})
finally:
    if receiver is not None:
        receiver.shutdown()
        receiver.server_close()
    try:
        if application is not None:
            api("POST", f"/api/applications/{application['id']}/disable", {"reason": "B10.2d.1 验收完成"}, admin)
            facts["cleanup"]["application_disabled"] = True
        if flow is not None:
            sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(flow['id'])} "
                f"AND code='{flow_code}'")
            facts["cleanup"]["flow_disabled"] = True
        if secret_ref is not None:
            sql(f"UPDATE ops_secret_ref SET status='DISABLED',updated_at=NOW(),version=version+1 "
                f"WHERE id={int(secret_ref['id'])} AND secret_code='{secret_code}'")
            facts["cleanup"]["secret_disabled"] = True
    finally:
        save(facts)

assert facts["status"] == "passed", facts
print(json.dumps({"status": "PASS", "cases": len(facts["cases"]),
                  "instanceId": facts["instance_id"], "jobId": facts["job_id"]}, ensure_ascii=False))
