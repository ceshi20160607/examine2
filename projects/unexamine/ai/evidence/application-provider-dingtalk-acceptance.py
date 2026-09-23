"""B12.4 钉钉企业内部应用 HTTP 回调、成员权限和结构化消息真实验收。"""
import ast
import hashlib
import json
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
FACTS = AREA / "application-provider-dingtalk-facts.json"

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
        "expectedDraftRevision": check["draftRevision"], "changeSummary": "B12.4 钉钉事件入口",
        "simulationVariables": {}}, token)


NODE_VECTOR = r"""
const crypto = require('crypto');
let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => input += chunk);
process.stdin.on('end', () => {
  const value = JSON.parse(input);
  const key = Buffer.from(value.aesKey + '=', 'base64');
  const iv = key.subarray(0, 16);
  if (value.mode === 'encrypt') {
    const message = Buffer.from(value.message, 'utf8');
    const owner = Buffer.from(value.owner, 'utf8');
    const length = Buffer.alloc(4); length.writeUInt32BE(message.length);
    let plain = Buffer.concat([crypto.randomBytes(16), length, message, owner]);
    const padding = 32 - plain.length % 32;
    plain = Buffer.concat([plain, Buffer.alloc(padding, padding)]);
    const cipher = crypto.createCipheriv('aes-256-cbc', key, iv); cipher.setAutoPadding(false);
    process.stdout.write(Buffer.concat([cipher.update(plain), cipher.final()]).toString('base64'));
  } else {
    const decipher = crypto.createDecipheriv('aes-256-cbc', key, iv); decipher.setAutoPadding(false);
    let plain = Buffer.concat([decipher.update(Buffer.from(value.encrypted, 'base64')), decipher.final()]);
    const padding = plain[plain.length - 1]; plain = plain.subarray(0, plain.length - padding);
    const length = plain.readUInt32BE(16);
    process.stdout.write(JSON.stringify({message: plain.subarray(20, 20 + length).toString('utf8'),
      owner: plain.subarray(20 + length).toString('utf8')}));
  }
});
"""


def codec(value):
    result = subprocess.run(["node", "-e", NODE_VECTOR], input=json.dumps(value),
                            capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, result.stderr
    return result.stdout.strip()


def encrypt(aes_key, value, owner):
    return codec({"mode": "encrypt", "aesKey": aes_key,
                  "message": json.dumps(value, ensure_ascii=False, separators=(",", ":")), "owner": owner})


def signature(token, timestamp, nonce, encrypted):
    return hashlib.sha1("".join(sorted([token, timestamp, nonce, encrypted])).encode()).hexdigest()


def callback(path, token, aes_key, owner, value, timestamp=None, supplied_signature=None):
    encrypted = encrypt(aes_key, value, owner)
    body = json.dumps({"encrypt": encrypted}, separators=(",", ":"))
    timestamp = timestamp or str(int(time.time()))
    nonce = secrets.token_hex(8)
    query = urllib.parse.urlencode({"msg_signature": supplied_signature or signature(token, timestamp, nonce, encrypted),
                                    "timestamp": timestamp, "nonce": nonce})
    request = urllib.request.Request("http://localhost:18080" + path + "?" + query,
        method="POST", data=body.encode(), headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def assert_success_response(body, token, aes_key, owner):
    value = json.loads(body)
    assert signature(token, value["timeStamp"], value["nonce"], value["encrypt"]) == value["msg_signature"]
    decoded = json.loads(codec({"mode": "decrypt", "aesKey": aes_key, "encrypted": value["encrypt"]}))
    assert decoded == {"message": "success", "owner": owner}, decoded


def error_code(body):
    value = json.loads(body)
    assert value.get("requestId"), value
    return value["code"]


def save_facts(facts):
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


facts = {"step": "B12.4", "status": "running", "cases": [], "cleanup": {},
         "external_status": "blocked_external",
         "external_reason": "工作区没有真实钉钉企业租户凭据、公网回调控制权和已确认的Stream/HTTP订阅配置"}
flow = application = connection = identity = template = None
platform = None
suffix = secrets.token_hex(5)
flow_code = "dingtalk_provider_" + suffix
corp_id = "ding" + secrets.token_hex(10)
client_id = "dingapp_" + secrets.token_hex(10)
client_secret = "dsec_" + secrets.token_hex(20)
callback_token = "dt_" + secrets.token_hex(16)
aes_key = __import__("base64").b64encode(secrets.token_bytes(32)).decode().rstrip("=")
external_user = "staff_" + secrets.token_hex(8)

try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]

    flow = api("POST", "/api/flows", {"code": flow_code, "name": "钉钉成员事件入口验收 Flow",
        "description": "B12.4 钉钉企业内部应用临时事件路由"}, platform)
    publish_flow(flow, platform)
    created = api("POST", "/api/applications", {"code": "dingtalk_provider_app_" + suffix,
        "name": "钉钉受控接入验收", "description": "结构化连接、成员权限和消息",
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
        "changeSummary": "发布钉钉成员事件入口"}, platform)["application"]
    published = application["publishedGrants"][0]

    connection = api("POST", f"/api/applications/{application['id']}/provider-connections", {
        "providerCode": "DINGTALK", "productCode": "INTERNAL_APP", "displayName": "钉钉成员入口",
        "tenantKey": corp_id, "applicationKey": client_id, "apiSecret": client_secret,
        "callbackToken": callback_token, "encodingAesKey": aes_key,
        "route": {"eventCode": "EVENT_USER_ADD_ORG", "resourceType": published["resourceType"],
                  "resourceId": published["resourceId"], "actionCode": published["actionCode"],
                  "targetSystemId": published.get("targetSystemId"),
                  "targetTenantId": published.get("targetTenantId")}}, platform)
    serialized = json.dumps(connection, ensure_ascii=False)
    assert all(secret not in serialized for secret in [client_secret, callback_token, aes_key])
    assert connection["callbackPath"].startswith("/api/provider-callbacks/v1/dingtalk/")
    assert {item["secretType"] for item in connection["secrets"] if item["status"] == "ACTIVE"} == {
        "API_SECRET", "CALLBACK_TOKEN", "ENCODING_AES_KEY"}
    options = api("GET", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identity-options", token=platform)
    selected = options[0]
    identity = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identities", {
        "externalUserId": external_user, "targetType": selected["targetType"],
        "targetMemberId": selected["targetMemberId"]}, platform)
    diagnosed = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/diagnose",
        {"activateAfterPass": True}, platform)
    connection = diagnosed["connection"]
    assert connection["status"] == "ACTIVE" and diagnosed["status"] == "LOCAL_PASSED"
    assert any("SHA-1" in item for item in diagnosed["checks"])
    facts["cases"].append({"case": "structured-dingtalk-connection-identity-local-diagnostic", "result": "PASS",
        "connectionId": connection["id"], "identityId": identity["id"], "secretPlaintextReturned": False})

    check_value = {"EventType": "check_url", "Random": suffix}
    status, body = callback(connection["callbackPath"], callback_token, aes_key, client_id, check_value,
                            timestamp=str(int(time.time() * 1000)))
    assert status == 200, (status, body)
    assert_success_response(body, callback_token, aes_key, client_id)
    status, body = callback(connection["callbackPath"], callback_token, aes_key, client_id,
                            check_value, supplied_signature="0" * 40)
    assert status == 401 and error_code(body) == "DINGTALK_CALLBACK_SIGNATURE_INVALID"
    status, body = callback(connection["callbackPath"], callback_token, aes_key, client_id,
                            check_value, timestamp=str(int(time.time()) - 301))
    assert status == 401 and error_code(body) == "PROVIDER_CALLBACK_TIMESTAMP_EXPIRED"
    status, body = callback(connection["callbackPath"], callback_token, aes_key, "wrong_owner", check_value)
    assert status == 403 and error_code(body) == "DINGTALK_CALLBACK_OWNER_MISMATCH"
    facts["cases"].append({"case": "encrypted-check-url-signature-owner-window", "result": "PASS",
        "encryptedSuccess": True, "forged": "DINGTALK_CALLBACK_SIGNATURE_INVALID",
        "expired": "PROVIDER_CALLBACK_TIMESTAMP_EXPIRED", "wrongOwner": "DINGTALK_CALLBACK_OWNER_MISMATCH"})

    unknown_event_id = "evt_unknown_" + suffix
    unknown = {"EventType": "user_add_org", "EventId": unknown_event_id,
               "UserId": "unknown_" + suffix, "CorpId": corp_id}
    before = int(sql(f"SELECT COUNT(*) FROM flow_instance WHERE flow_id={int(flow['id'])}")[0][0])
    status, body = callback(connection["callbackPath"], callback_token, aes_key, client_id, unknown)
    assert status == 403 and error_code(body) == "PROVIDER_IDENTITY_UNMAPPED"
    assert int(sql(f"SELECT COUNT(*) FROM app_provider_event WHERE connection_id={int(connection['id'])} AND provider_event_id='{unknown_event_id}'")[0][0]) == 0
    assert int(sql(f"SELECT COUNT(*) FROM flow_instance WHERE flow_id={int(flow['id'])}")[0][0]) == before
    facts["cases"].append({"case": "unknown-user-id-rejected-before-business-write", "result": "PASS",
        "errorCode": "PROVIDER_IDENTITY_UNMAPPED", "eventRows": 0, "flowWrites": 0})

    event_id = "evt_" + secrets.token_hex(8)
    event_value = {"EventType": "user_add_org", "EventId": event_id,
                   "UserId": external_user, "CorpId": corp_id}
    status, body = callback(connection["callbackPath"], callback_token, aes_key, client_id, event_value)
    assert status == 200, (status, body)
    assert_success_response(body, callback_token, aes_key, client_id)
    status, body = callback(connection["callbackPath"], callback_token, aes_key, client_id, event_value)
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

    template = api("POST", "/api/message-templates", {"code": "DINGTALK_NOTICE_" + suffix.upper(),
        "name": "钉钉审批入口通知", "channel": "DINGTALK", "subjectTemplate": "待处理：{{title}}",
        "contentTemplate": "{{member}}，请处理 {{title}}。", "requiredVariables": ["member", "title"]}, platform)
    template = api("POST", f"/api/message-templates/{template['id']}/publish", {}, platform)
    message_key = "dingtalk-message-" + suffix
    message_body = {"identityId": identity["id"], "templateCode": template["code"],
                    "idempotencyKey": message_key,
                    "variables": {"member": identity["memberName"], "title": "采购审批"}}
    delivery = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/messages",
                   message_body, platform)
    replay = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/messages",
                 message_body, platform)
    assert delivery["id"] == replay["id"] and delivery["status"] == "BLOCKED_EXTERNAL"
    assert delivery["errorCode"] == "DINGTALK_EXTERNAL_CREDENTIALS_UNAVAILABLE"
    delivery_counts = sql(f"SELECT (SELECT COUNT(*) FROM app_provider_delivery WHERE connection_id={int(connection['id'])} AND idempotency_key='{message_key}'),"
        f"(SELECT COUNT(*) FROM msg_delivery WHERE message_id={int(delivery['messageId'])} AND channel='DINGTALK'),"
        f"(SELECT COUNT(*) FROM msg_delivery WHERE message_id={int(delivery['messageId'])} AND channel='IN_APP')")[0]
    assert delivery_counts == ["1", "1", "1"], delivery_counts
    facts["cases"].append({"case": "dingtalk-template-deep-link-idempotent-delivery", "result": "PASS",
        "deliveryId": delivery["id"], "status": delivery["status"], "deepLink": delivery["deepLink"],
        "providerRows": 1, "dingtalkRows": 1, "inAppRows": 1})

    connection = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/disable", {}, platform)
    status, body = callback(connection["callbackPath"], callback_token, aes_key, client_id, event_value)
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
                api("POST", f"/api/applications/{application['id']}/disable", {"reason": "B12.4 钉钉验收完成"}, platform)
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
