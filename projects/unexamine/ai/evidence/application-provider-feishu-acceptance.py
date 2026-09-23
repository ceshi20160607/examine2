"""B12.3 飞书企业自建应用回调、成员权限和结构化消息真实验收。"""
import ast
import hashlib
import json
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "application-provider-feishu-facts.json"

tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))


def flow_node(key, kind, name, x):
    return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
            "config": {}, "assigneePolicy": {}, "formPolicy": {}, "timeoutPolicy": {}, "exceptionPolicy": {}}


def publish_flow(flow, token):
    flow = api("PUT", f"/api/flows/{flow['id']}/draft", {"expectedVersion": flow["version"],
        "nodes": [flow_node("start", "START", "开始", 0), flow_node("end", "END", "完成", 260)],
        "edges": [{"edgeKey": "done", "sourceNodeKey": "start", "targetNodeKey": "end",
                   "priorityOrder": 0, "config": {}}]}, token)
    check = api("GET", f"/api/flows/{flow['id']}/publication-check", token=token)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/flows/{flow['id']}/publish", {
        "expectedDraftRevision": check["draftRevision"], "changeSummary": "B12.3 飞书事件入口",
        "simulationVariables": {}}, token)


NODE_VECTOR = r"""
const crypto = require('crypto');
let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => input += chunk);
process.stdin.on('end', () => {
  const value = JSON.parse(input);
  const key = crypto.createHash('sha256').update(value.encryptKey, 'utf8').digest();
  const iv = crypto.randomBytes(16);
  const cipher = crypto.createCipheriv('aes-256-cbc', key, iv);
  const ciphertext = Buffer.concat([cipher.update(value.plaintext, 'utf8'), cipher.final()]);
  process.stdout.write(Buffer.concat([iv, ciphertext]).toString('base64'));
});
"""


def encrypt(encrypt_key, value):
    result = subprocess.run(["node", "-e", NODE_VECTOR], input=json.dumps({
        "encryptKey": encrypt_key, "plaintext": json.dumps(value, ensure_ascii=False, separators=(",", ":"))}),
        capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, result.stderr
    return result.stdout.strip()


def callback(path, encrypt_key, value, timestamp=None, signature=None):
    body = json.dumps({"encrypt": encrypt(encrypt_key, value)}, ensure_ascii=False, separators=(",", ":"))
    timestamp = timestamp or str(int(time.time()))
    nonce = secrets.token_hex(8)
    signature = signature or hashlib.sha256((timestamp + nonce + encrypt_key + body).encode()).hexdigest()
    request = urllib.request.Request("http://localhost:18080" + path, method="POST", data=body.encode(),
        headers={"Content-Type": "application/json", "X-Lark-Request-Timestamp": timestamp,
                 "X-Lark-Request-Nonce": nonce, "X-Lark-Signature": signature})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def error_code(body):
    value = json.loads(body)
    assert value.get("requestId"), value
    return value["code"]


def save_facts(facts):
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


facts = {"step": "B12.3", "status": "running", "cases": [], "cleanup": {},
         "external_status": "blocked_external",
         "external_reason": "工作区没有真实飞书企业租户凭据和公网请求地址控制权"}
flow = application = connection = identity = template = None
platform = None
suffix = secrets.token_hex(5)
flow_code = "feishu_provider_" + suffix
app_id = "cli_" + secrets.token_hex(10)
app_secret = "fs_" + secrets.token_hex(20)
verification_token = "vt_" + secrets.token_hex(16)
encrypt_key = "ek_" + secrets.token_hex(20)
external_user = "ou_" + secrets.token_hex(12)

try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]

    flow = api("POST", "/api/flows", {"code": flow_code, "name": "飞书事件入口验收 Flow",
        "description": "B12.3 飞书企业自建应用临时事件路由"}, platform)
    publish_flow(flow, platform)
    created = api("POST", "/api/applications", {"code": "feishu_provider_app_" + suffix,
        "name": "飞书受控接入验收", "description": "结构化连接、成员权限和消息",
        "applicationType": "SERVICE", "iconCode": "message", "credentialValidityDays": 30}, platform)
    application = created["application"]
    grant = {"targetType": "PLATFORM", "resourceType": "FLOW", "resourceId": flow_code,
        "actionCode": "START", "dataScope": {"type": "PLATFORM"},
        "rateLimit": {"accessChannels": ["EXTERNAL"], "maxRequests": 100, "windowSeconds": 600,
                      "allowedIps": ["*"], "failureDisableThreshold": 5, "failureWindowSeconds": 300},
        "fields": [{"fieldCode": "title", "readable": True, "writable": True, "maskStrategy": "NONE"}]}
    application = api("PUT", f"/api/applications/{application['id']}/draft", {
        "expectedVersion": application["version"], "name": application["name"],
        "description": application["description"], "applicationType": application["applicationType"],
        "iconCode": "message", "callbacks": [], "grants": [grant]}, platform)
    application = api("POST", f"/api/applications/{application['id']}/publish", {
        "expectedDraftRevision": application["draftRevision"],
        "changeSummary": "发布飞书成员事件入口"}, platform)["application"]
    published = application["publishedGrants"][0]

    connection = api("POST", f"/api/applications/{application['id']}/provider-connections", {
        "providerCode": "FEISHU", "productCode": "CUSTOM_APP", "displayName": "飞书审批入口",
        "tenantKey": app_id, "apiSecret": app_secret, "callbackToken": verification_token,
        "encodingAesKey": encrypt_key,
        "route": {"eventCode": "MESSAGE_TEXT", "resourceType": published["resourceType"],
                  "resourceId": published["resourceId"], "actionCode": published["actionCode"],
                  "targetSystemId": published.get("targetSystemId"),
                  "targetTenantId": published.get("targetTenantId")}}, platform)
    serialized = json.dumps(connection, ensure_ascii=False)
    assert all(secret not in serialized for secret in [app_secret, verification_token, encrypt_key])
    assert connection["callbackPath"].startswith("/api/provider-callbacks/v1/feishu/")
    assert {item["secretType"] for item in connection["secrets"] if item["status"] == "ACTIVE"} == {
        "API_SECRET", "CALLBACK_TOKEN", "ENCODING_AES_KEY"}
    options = api("GET", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identity-options", token=platform)
    assert options and options[0]["memberName"]
    selected = options[0]
    identity = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identities", {
        "externalUserId": external_user, "targetType": selected["targetType"],
        "targetMemberId": selected["targetMemberId"]}, platform)
    diagnosed = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/diagnose",
        {"activateAfterPass": True}, platform)
    connection = diagnosed["connection"]
    assert connection["status"] == "ACTIVE" and diagnosed["status"] == "LOCAL_PASSED"
    assert any("AES-256-CBC" in item for item in diagnosed["checks"])
    facts["cases"].append({"case": "structured-feishu-connection-identity-local-diagnostic", "result": "PASS",
        "connectionId": connection["id"], "identityId": identity["id"], "secretPlaintextReturned": False})

    challenge = "challenge-" + suffix
    challenge_value = {"type": "url_verification", "token": verification_token,
                       "challenge": challenge, "app_id": app_id}
    status, body = callback(connection["callbackPath"], encrypt_key, challenge_value)
    assert status == 200 and json.loads(body) == {"challenge": challenge}, (status, body)
    status, body = callback(connection["callbackPath"], encrypt_key, challenge_value, signature="0" * 64)
    assert status == 401 and error_code(body) == "FEISHU_CALLBACK_SIGNATURE_INVALID"
    expired = str(int(time.time()) - 301)
    status, body = callback(connection["callbackPath"], encrypt_key, challenge_value, timestamp=expired)
    assert status == 401 and error_code(body) == "PROVIDER_CALLBACK_TIMESTAMP_EXPIRED"
    wrong_token = dict(challenge_value)
    wrong_token["token"] = "wrong"
    status, body = callback(connection["callbackPath"], encrypt_key, wrong_token)
    assert status == 401 and error_code(body) == "FEISHU_CALLBACK_TOKEN_INVALID"
    facts["cases"].append({"case": "encrypted-challenge-signature-token-window", "result": "PASS",
        "challengeReturned": True, "forged": "FEISHU_CALLBACK_SIGNATURE_INVALID",
        "expired": "PROVIDER_CALLBACK_TIMESTAMP_EXPIRED", "wrongToken": "FEISHU_CALLBACK_TOKEN_INVALID"})

    unknown_event_id = "evt_unknown_" + suffix
    unknown = {"schema": "2.0", "header": {"event_id": unknown_event_id,
        "event_type": "im.message.receive_v1", "token": verification_token, "app_id": app_id},
        "event": {"sender": {"sender_id": {"open_id": "ou_unknown_" + suffix}},
                  "message": {"message_type": "text", "content": json.dumps({"text": "拒绝"}, ensure_ascii=False)}}}
    before = int(sql(f"SELECT COUNT(*) FROM flow_instance WHERE flow_id={int(flow['id'])}")[0][0])
    status, body = callback(connection["callbackPath"], encrypt_key, unknown)
    assert status == 403 and error_code(body) == "PROVIDER_IDENTITY_UNMAPPED"
    assert int(sql(f"SELECT COUNT(*) FROM app_provider_event WHERE connection_id={int(connection['id'])} AND provider_event_id='{unknown_event_id}'")[0][0]) == 0
    assert int(sql(f"SELECT COUNT(*) FROM flow_instance WHERE flow_id={int(flow['id'])}")[0][0]) == before
    facts["cases"].append({"case": "unknown-open-id-rejected-before-business-write", "result": "PASS",
        "errorCode": "PROVIDER_IDENTITY_UNMAPPED", "eventRows": 0, "flowWrites": 0})

    event_id = "evt_" + secrets.token_hex(8)
    event_value = {"schema": "2.0", "header": {"event_id": event_id,
        "event_type": "im.message.receive_v1", "token": verification_token, "app_id": app_id},
        "event": {"sender": {"sender_id": {"open_id": external_user}},
                  "message": {"message_id": "om_" + suffix, "message_type": "text",
                              "content": json.dumps({"text": "请发起飞书审批"}, ensure_ascii=False)}}}
    status, body = callback(connection["callbackPath"], encrypt_key, event_value)
    assert status == 200 and json.loads(body) == {}, (status, body)
    status, body = callback(connection["callbackPath"], encrypt_key, event_value)
    assert status == 200
    event_rows = api("GET", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/events", token=platform)
    event = next(item for item in event_rows if item["providerEventId"] == event_id)
    call_row = sql(f"SELECT HEX(permission_snapshot_json),HEX(response_json),status FROM app_call WHERE id={int(event['applicationCallId'])}")[0]
    permission = json.loads(bytes.fromhex(call_row[0]).decode())
    response = json.loads(bytes.fromhex(call_row[1]).decode())
    assert call_row[2] == "SUCCESS" and permission["accessChannel"] == "PROVIDER"
    assert response["source"]["accessChannel"] == "PROVIDER"
    counts = sql(f"SELECT (SELECT COUNT(*) FROM app_provider_event WHERE connection_id={int(connection['id'])} AND provider_event_id='{event_id}'),"
                 f"(SELECT COUNT(*) FROM flow_instance WHERE business_type='APPLICATION_CALL' AND business_id='{response['requestId']}')")[0]
    assert counts == ["1", "1"], counts
    facts["cases"].append({"case": "mapped-current-permission-event-and-replay", "result": "PASS",
        "applicationCallId": event["applicationCallId"], "accessChannel": "PROVIDER",
        "eventRows": 1, "flowInstanceRows": 1})

    template = api("POST", "/api/message-templates", {"code": "FEISHU_NOTICE_" + suffix.upper(),
        "name": "飞书审批入口通知", "channel": "FEISHU", "subjectTemplate": "待处理：{{title}}",
        "contentTemplate": "{{member}}，请处理 {{title}}。", "requiredVariables": ["member", "title"]}, platform)
    template = api("POST", f"/api/message-templates/{template['id']}/publish", {}, platform)
    message_key = "feishu-message-" + suffix
    message_body = {"identityId": identity["id"], "templateCode": template["code"],
                    "idempotencyKey": message_key,
                    "variables": {"member": identity["memberName"], "title": "采购审批"}}
    delivery = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/messages",
                   message_body, platform)
    replay = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/messages",
                 message_body, platform)
    assert delivery["id"] == replay["id"] and delivery["status"] == "BLOCKED_EXTERNAL"
    assert delivery["errorCode"] == "FEISHU_EXTERNAL_CREDENTIALS_UNAVAILABLE"
    delivery_counts = sql(f"SELECT (SELECT COUNT(*) FROM app_provider_delivery WHERE connection_id={int(connection['id'])} AND idempotency_key='{message_key}'),"
        f"(SELECT COUNT(*) FROM msg_delivery WHERE message_id={int(delivery['messageId'])} AND channel='FEISHU'),"
        f"(SELECT COUNT(*) FROM msg_delivery WHERE message_id={int(delivery['messageId'])} AND channel='IN_APP')")[0]
    assert delivery_counts == ["1", "1", "1"], delivery_counts
    facts["cases"].append({"case": "feishu-template-deep-link-idempotent-delivery", "result": "PASS",
        "deliveryId": delivery["id"], "status": delivery["status"], "deepLink": delivery["deepLink"],
        "providerRows": 1, "feishuRows": 1, "inAppRows": 1})

    connection = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/disable", {}, platform)
    status, body = callback(connection["callbackPath"], encrypt_key, event_value)
    assert status == 403 and error_code(body) == "PROVIDER_CONNECTION_INACTIVE"
    facts["cases"].append({"case": "connection-revocation-immediate", "result": "PASS",
        "status": connection["status"], "callbackError": "PROVIDER_CONNECTION_INACTIVE"})

    facts.update({"status": "passed", "flow_id": flow["id"], "application_id": application["id"],
                  "connection_id": connection["id"], "identity_id": identity["id"],
                  "application_call_id": event["applicationCallId"], "delivery_id": delivery["id"]})
finally:
    try:
        if connection is not None and connection.get("status") != "DISABLED" and platform and application:
            try:
                api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/disable", {}, platform)
            except Exception:
                pass
        facts["cleanup"]["provider_connection_disabled"] = connection is not None
        if application is not None and platform:
            try:
                api("POST", f"/api/applications/{application['id']}/disable", {"reason": "B12.3 飞书验收完成"}, platform)
            except Exception:
                pass
            facts["cleanup"]["application_disabled"] = True
        if template is not None:
            sql(f"UPDATE msg_template SET status='DRAFT',version=version+1 WHERE id={int(template['id'])}")
            facts["cleanup"]["message_template_unpublished"] = True
        if flow is not None:
            sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(flow['id'])}")
            facts["cleanup"]["flow_disabled"] = True
    finally:
        save_facts(facts)

assert facts["status"] == "passed", facts
print(json.dumps({"status": "PASS", "cases": len(facts["cases"]),
                  "applicationId": facts["application_id"], "connectionId": facts["connection_id"],
                  "deliveryId": facts["delivery_id"], "externalStatus": "blocked_external"}, ensure_ascii=False))
