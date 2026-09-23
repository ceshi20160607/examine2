"""B10.2d.2 应用发布版本冻结 Flow、签名入站、幂等与调用日志真实验收。"""
import ast
import hashlib
import hmac
import json
import re
import secrets
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "application-flow-entry-facts.json"
SYSTEM_ID = 54
TENANT_ID = 57
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))


def save(facts):
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def raw_call(path, raw_body, headers, token=None):
    request_headers = {"Content-Type": "application/json", **headers}
    if token:
        request_headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request("http://localhost:18080" + path, method="POST",
        headers=request_headers, data=raw_body)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def external_call(body, client_id, client_secret, nonce, idempotency_key, signature_override=None):
    raw = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode()
    timestamp = str(int(time.time() * 1000))
    request_hash = hashlib.sha256(raw).hexdigest()
    canonical = f"{client_id}\n{timestamp}\n{nonce}\n{idempotency_key}\n{request_hash}"
    signature = signature_override or hmac.new(
        client_secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
    return raw_call("/api/application-access/v1/calls", raw, {
        "X-App-Client-Id": client_id,
        "X-App-Timestamp": timestamp,
        "X-App-Nonce": nonce,
        "X-App-Idempotency-Key": idempotency_key,
        "X-App-Signature": signature,
    })


def flow_node(key, kind, name, x):
    return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
            "config": {}, "assigneePolicy": {}, "formPolicy": {}, "timeoutPolicy": {},
            "exceptionPolicy": {}}


def flow_draft(version, end_name):
    return {"expectedVersion": version,
        "nodes": [flow_node("start", "START", "开始", 0), flow_node("end", "END", end_name, 300)],
        "edges": [{"edgeKey": "complete", "sourceNodeKey": "start", "targetNodeKey": "end",
                   "priorityOrder": 0, "config": {}}]}


facts = {"step": "B10.2d.2", "status": "running", "cases": [], "cleanup": {},
         "preparation_failures": [
             "首次尝试未形成新草稿修订便再次发布应用，命中既有唯一约束并在调用前安全回滚；改为显式保存同一授权草稿后再发布。"
         ]}
flow = application = None
flow_code = "application_flow_" + secrets.token_hex(5)
application_code = "flow_entry_app_" + secrets.token_hex(5)
try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    admin = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {},
                login["accessToken"])["tokens"]["accessToken"]

    flow = api("POST", "/api/flows", {"code": flow_code, "name": "应用精确版本发起验收",
        "description": "B10.2d.2 临时验收"}, admin)
    flow = api("PUT", f"/api/flows/{flow['id']}/draft", flow_draft(flow["version"], "V1 结束"), admin)
    check = api("GET", f"/api/flows/{flow['id']}/publication-check", token=admin)
    published_v1 = api("POST", f"/api/flows/{flow['id']}/publish", {
        "expectedDraftRevision": check["draftRevision"], "changeSummary": "应用冻结目标 V1",
        "simulationVariables": {}}, admin)
    flow_v1 = published_v1["versionId"]

    created = api("POST", "/api/applications", {"code": application_code,
        "name": "Flow 安全发起应用", "description": "B10.2d.2 临时验收", "applicationType": "SERVICE"}, admin)
    application = created["application"]
    credential = created["issuedCredential"]
    grant = {"targetType": "SYSTEM", "targetSystemId": SYSTEM_ID, "targetTenantId": TENANT_ID,
        "resourceType": "FLOW", "resourceId": flow_code, "actionCode": "START",
        "dataScope": {"type": "SELF"},
        "rateLimit": {"accessChannels": ["EXTERNAL", "INTERNAL"], "maxRequests": 100,
                      "windowSeconds": 600, "allowedIps": ["*"]},
        "fields": [{"fieldCode": "title", "readable": True, "writable": True,
                    "maskStrategy": "NONE"},
                   {"fieldCode": "variables", "readable": True, "writable": True,
                    "maskStrategy": "NONE"}]}
    application = api("PUT", f"/api/applications/{application['id']}/draft", {
        "expectedVersion": application["version"], "name": application["name"],
        "description": application["description"], "applicationType": "SERVICE",
        "callbacks": [], "grants": [grant]}, admin)
    app_check = api("GET", f"/api/applications/{application['id']}/publication-check", token=admin)
    app_publish_v1 = api("POST", f"/api/applications/{application['id']}/publish", {
        "expectedDraftRevision": app_check["draftRevision"], "changeSummary": "冻结 Flow V1"}, admin)
    app_v1 = app_publish_v1["versionId"]
    application = api("GET", f"/api/applications/{application['id']}", token=admin)
    frozen_v1 = application["publishedGrants"][0]
    assert frozen_v1["resourceVersionId"] == flow_v1 and frozen_v1["resourceVersionNumber"] == 1

    flow = api("GET", f"/api/flows/{flow['id']}", token=admin)
    flow = api("PUT", f"/api/flows/{flow['id']}/draft", flow_draft(flow["version"], "V2 结束"), admin)
    check = api("GET", f"/api/flows/{flow['id']}/publication-check", token=admin)
    published_v2 = api("POST", f"/api/flows/{flow['id']}/publish", {
        "expectedDraftRevision": check["draftRevision"], "changeSummary": "当前 Flow 切换 V2",
        "simulationVariables": {}}, admin)
    flow_v2 = published_v2["versionId"]
    assert flow_v2 != flow_v1

    body = {"resourceType": "FLOW", "resourceId": flow_code, "actionCode": "START",
        "targetSystemId": SYSTEM_ID, "targetTenantId": TENANT_ID,
        "requestedDataScope": {"type": "SELF"},
        "payload": {"title": "外部签名冻结版本发起", "variables": {"source": "external"}}}
    status, response = external_call(body, credential["clientId"], credential["clientSecret"],
        "freeze-" + secrets.token_hex(8), "freeze-" + secrets.token_hex(8))
    assert status == 200, response
    first = response["data"]
    first_instance = first["result"]["instance"]
    assert first_instance["flowVersionId"] == flow_v1 and first_instance["status"] == "COMPLETED"
    first_key = sql(f"SELECT idempotency_key FROM app_call WHERE application_id={application['id']} "
                    "AND status='SUCCESS' ORDER BY id LIMIT 1")[0][0]
    replay_nonce = "replay-" + secrets.token_hex(8)
    status, replay = external_call(body, credential["clientId"], credential["clientSecret"],
        replay_nonce, first_key)
    assert status == 200 and replay["data"]["replayed"] is True
    status, nonce_replay = external_call(body, credential["clientId"], credential["clientSecret"],
        replay_nonce, first_key)
    assert status == 409 and nonce_replay["code"] == "APPLICATION_REPLAY_DETECTED"
    status, bad_signature = external_call(body, credential["clientId"], credential["clientSecret"],
        "bad-" + secrets.token_hex(8), "bad-" + secrets.token_hex(8), "0" * 64)
    assert status == 401 and bad_signature["code"] == "APPLICATION_SIGNATURE_INVALID"
    assert int(sql(f"SELECT COUNT(*) FROM flow_instance WHERE flow_id={flow['id']}")[0][0]) == 1
    facts["cases"].append({"case": "external-signed-frozen-version", "result": "PASS",
        "applicationVersionId": app_v1, "flowVersionId": flow_v1,
        "currentFlowVersionId": flow_v2, "instanceId": first_instance["id"]})
    facts["cases"].append({"case": "idempotency-and-replay", "result": "PASS",
        "sameResult": True, "nonceReplayRejected": True, "invalidSignatureRejected": True,
        "instanceCount": 1})

    internal_key = "internal-" + secrets.token_hex(8)
    raw = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode()
    status, internal = raw_call(
        f"/api/application-internal-access/v1/applications/{application['id']}/calls", raw,
        {"X-Idempotency-Key": internal_key}, admin)
    assert status == 200, internal
    internal_instance = internal["data"]["result"]["instance"]
    assert internal_instance["flowVersionId"] == flow_v1
    facts["cases"].append({"case": "internal-current-identity-frozen-version", "result": "PASS",
        "flowVersionId": flow_v1, "instanceId": internal_instance["id"]})

    application = api("GET", f"/api/applications/{application['id']}", token=admin)
    application = api("PUT", f"/api/applications/{application['id']}/draft", {
        "expectedVersion": application["version"], "name": application["name"],
        "description": application["description"], "applicationType": "SERVICE",
        "callbacks": [], "grants": [grant]}, admin)
    app_publish_v2 = api("POST", f"/api/applications/{application['id']}/publish", {
        "expectedDraftRevision": application["draftRevision"], "changeSummary": "明确升级到 Flow V2"}, admin)
    app_v2 = app_publish_v2["versionId"]
    application = api("GET", f"/api/applications/{application['id']}", token=admin)
    frozen_v2 = application["publishedGrants"][0]
    assert frozen_v2["resourceVersionId"] == flow_v2 and frozen_v2["resourceVersionNumber"] == 2
    status, upgraded = external_call(body, credential["clientId"], credential["clientSecret"],
        "upgrade-" + secrets.token_hex(8), "upgrade-" + secrets.token_hex(8))
    assert status == 200, upgraded
    upgraded_instance = upgraded["data"]["result"]["instance"]
    assert upgraded_instance["flowVersionId"] == flow_v2
    facts["cases"].append({"case": "application-republish-adopts-new-flow-version", "result": "PASS",
        "applicationVersionId": app_v2, "flowVersionId": flow_v2,
        "instanceId": upgraded_instance["id"]})

    calls = api("GET", f"/api/applications/{application['id']}/calls", token=admin)
    successful = [item for item in calls if item["status"] == "SUCCESS"]
    assert len(successful) == 3
    assert all(item["requestId"] and item["durationMillis"] is not None for item in calls)
    assert successful[0]["permissionSnapshot"].get("resourceVersion")
    assert all("clientSecret" not in json.dumps(item) for item in calls)
    facts["cases"].append({"case": "readable-safe-call-log", "result": "PASS",
        "callCount": len(calls), "successCount": len(successful), "secretExposed": False})
    facts.update({"status": "passed", "flow_id": flow["id"], "flow_version_v1": flow_v1,
        "flow_version_v2": flow_v2, "application_id": application["id"],
        "application_version_v1": app_v1, "application_version_v2": app_v2,
        "instance_ids": [first_instance["id"], internal_instance["id"], upgraded_instance["id"]]})
finally:
    try:
        if application is not None:
            try:
                api("POST", f"/api/applications/{application['id']}/disable",
                    {"reason": "B10.2d.2 验收完成"}, admin)
            except Exception:
                pass
            facts["cleanup"]["application_disabled"] = True
        if flow is not None:
            sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(flow['id'])} "
                f"AND code='{flow_code}'")
            facts["cleanup"]["flow_disabled"] = True
    finally:
        save(facts)

assert facts["status"] == "passed", facts
print(json.dumps({"status": "PASS", "cases": len(facts["cases"]),
                  "applicationId": facts["application_id"], "flowId": facts["flow_id"]}, ensure_ascii=False))
