"""B12.1 企业微信结构化连接、回调协议、去重和应用授权路由真实验收。"""
import ast
import base64
import hashlib
import json
import os
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
FACTS = AREA / "application-provider-wecom-facts.json"

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
        "expectedDraftRevision": check["draftRevision"], "changeSummary": "B12.1 企业微信事件路由",
        "simulationVariables": {}}, token)


def sign(token, timestamp, nonce, encrypted):
    return hashlib.sha1("".join(sorted([token, timestamp, nonce, encrypted])).encode()).hexdigest()


NODE_VECTOR = r"""
const crypto = require('crypto');
let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => input += chunk);
process.stdin.on('end', () => {
  const value = JSON.parse(input);
  const key = Buffer.from(value.encodingKey + '=', 'base64');
  const message = Buffer.from(value.message, 'utf8');
  const receiver = Buffer.from(value.receiver, 'utf8');
  const length = Buffer.alloc(4); length.writeUInt32BE(message.length, 0);
  const plain = Buffer.concat([crypto.randomBytes(16), length, message, receiver]);
  const padding = 32 - (plain.length % 32);
  const padded = Buffer.concat([plain, Buffer.alloc(padding, padding)]);
  const cipher = crypto.createCipheriv('aes-256-cbc', key, key.subarray(0, 16));
  cipher.setAutoPadding(false);
  process.stdout.write(Buffer.concat([cipher.update(padded), cipher.final()]).toString('base64'));
});
"""


def encrypt(encoding_key, message, receiver):
    result = subprocess.run(["node", "-e", NODE_VECTOR], input=json.dumps({
        "encodingKey": encoding_key, "message": message, "receiver": receiver}),
        capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, "独立企业微信协议向量生成失败"
    return result.stdout.strip()


def raw_callback(method, path, body=None, content_type="application/xml"):
    request = urllib.request.Request("http://localhost:18080" + path, method=method,
        headers={"Content-Type": content_type}, data=None if body is None else body.encode())
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


facts = {"step": "B12.1", "status": "running", "cases": [], "cleanup": {},
         "external_status": "blocked_external", "external_reason": "工作区没有真实企业微信租户凭据和公网回调地址控制权"}
flow = application = connection = None
suffix = secrets.token_hex(5)
flow_code = "wecom_provider_" + suffix
corp_id = "ww" + secrets.token_hex(8)
agent_id = str(secrets.randbelow(800000) + 100000)
callback_token = "T" + secrets.token_hex(12)
encoding_key = base64.b64encode(os.urandom(32)).decode().rstrip("=")
api_secret = "S" + secrets.token_hex(20)
platform = None
try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]

    capabilities = api("GET", "/api/applications/provider-capabilities", token=platform)
    capability_keys = {(item["providerCode"], item["productCode"]) for item in capabilities}
    assert {("WECOM", "INTERNAL_APP"), ("FEISHU", "CUSTOM_APP"),
            ("DINGTALK", "INTERNAL_APP"), ("WECHAT", "OFFICIAL_ACCOUNT"),
            ("WECHAT", "MINI_PROGRAM")}.issubset(capability_keys)
    assert not any(item["providerCode"] == "WECHAT" and item["productCode"] == "INTERNAL_APP" for item in capabilities)
    facts["cases"].append({"case": "official-product-capability-boundary", "result": "PASS",
        "wechatProducts": ["OFFICIAL_ACCOUNT", "MINI_PROGRAM"], "genericWechatSelfBuiltApp": False})

    flow = api("POST", "/api/flows", {"code": flow_code, "name": "企业微信事件入口验收 Flow",
        "description": "B12.1 临时事件路由"}, platform)
    publish_flow(flow, platform)
    created = api("POST", "/api/applications", {"code": "wecom_application_" + suffix,
        "name": "企业微信受控接入验收", "description": "结构化连接与回调协议", "applicationType": "SERVICE",
        "iconCode": "message", "credentialValidityDays": 30}, platform)
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
        "expectedDraftRevision": application["draftRevision"], "changeSummary": "开放企业微信事件Flow路由"}, platform)["application"]
    published_grant = application["publishedGrants"][0]

    connection = api("POST", f"/api/applications/{application['id']}/provider-connections", {
        "providerCode": "WECOM", "productCode": "INTERNAL_APP", "displayName": "企业微信审批入口",
        "tenantKey": corp_id, "applicationKey": agent_id, "apiSecret": api_secret,
        "callbackToken": callback_token, "encodingAesKey": encoding_key,
        "route": {"eventCode": "MESSAGE_TEXT", "resourceType": published_grant["resourceType"],
                  "resourceId": published_grant["resourceId"], "actionCode": published_grant["actionCode"],
                  "targetSystemId": published_grant.get("targetSystemId"),
                  "targetTenantId": published_grant.get("targetTenantId")}}, platform)
    serialized = json.dumps(connection, ensure_ascii=False)
    assert all(secret not in serialized for secret in [api_secret, callback_token, encoding_key])
    assert {item["secretType"] for item in connection["secrets"] if item["status"] == "ACTIVE"} == {
        "API_SECRET", "CALLBACK_TOKEN", "ENCODING_AES_KEY"}
    identity_options = api("GET", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identity-options", token=platform)
    assert identity_options, "平台应用应提供至少一个有效平台成员"
    mapped_member = identity_options[0]
    identity = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identities", {
        "externalUserId": "member-" + suffix, "targetType": mapped_member["targetType"],
        "targetMemberId": mapped_member["targetMemberId"]}, platform)
    assert identity["status"] == "ACTIVE" and identity["memberName"]
    diagnosed = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/diagnose",
        {"activateAfterPass": True}, platform)
    connection = diagnosed["connection"]
    assert diagnosed["status"] == "LOCAL_PASSED" and not diagnosed["externallyVerified"]
    assert connection["status"] == "ACTIVE"
    facts["cases"].append({"case": "structured-connection-secret-reference-local-diagnostic", "result": "PASS",
        "connectionId": connection["id"], "status": connection["status"],
        "diagnosticStatus": connection["diagnosticStatus"], "externallyVerified": False,
        "secretPlaintextReturned": False, "route": f"MESSAGE_TEXT -> {flow_code}",
        "identityId": identity["id"], "mappedMember": identity["memberName"]})

    # URL校验：合法签名返回解密明文；伪造签名与过期时间均拒绝。
    now = str(int(time.time()))
    nonce = secrets.token_hex(8)
    echo_text = "wecom-url-check-" + suffix
    encrypted_echo = encrypt(encoding_key, echo_text, corp_id)
    query = urllib.parse.urlencode({"msg_signature": sign(callback_token, now, nonce, encrypted_echo),
        "timestamp": now, "nonce": nonce, "echostr": encrypted_echo})
    callback_path = connection["callbackPath"]
    status, response = raw_callback("GET", callback_path + "?" + query)
    assert status == 200 and response == echo_text, (status, response)
    forged = urllib.parse.urlencode({"msg_signature": "0" * 40, "timestamp": now,
        "nonce": nonce, "echostr": encrypted_echo})
    status, forged_body = raw_callback("GET", callback_path + "?" + forged)
    assert status == 401 and error_code(forged_body) == "PROVIDER_CALLBACK_SIGNATURE_INVALID"
    old = str(int(time.time()) - 301)
    expired = urllib.parse.urlencode({"msg_signature": sign(callback_token, old, nonce, encrypted_echo),
        "timestamp": old, "nonce": nonce, "echostr": encrypted_echo})
    status, expired_body = raw_callback("GET", callback_path + "?" + expired)
    assert status == 401 and error_code(expired_body) == "PROVIDER_CALLBACK_TIMESTAMP_EXPIRED"
    facts["cases"].append({"case": "wecom-url-verification-security", "result": "PASS",
        "echoReturned": True, "forgedSignature": "PROVIDER_CALLBACK_SIGNATURE_INVALID",
        "expiredTimestamp": "PROVIDER_CALLBACK_TIMESTAMP_EXPIRED"})

    # 真实公开回调入口：独立Node加密向量生成密文，后端验签解密后进入现有应用网关并发起Flow。
    provider_message_id = str(int(time.time() * 1000000))
    event_xml = (f"<xml><ToUserName><![CDATA[{corp_id}]]></ToUserName>"
                 f"<FromUserName><![CDATA[member-{suffix}]]></FromUserName>"
                 f"<CreateTime>{int(time.time())}</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                 f"<Content><![CDATA[请发起业务审批]]></Content><MsgId>{provider_message_id}</MsgId>"
                 f"<AgentID>{agent_id}</AgentID></xml>")
    encrypted_event = encrypt(encoding_key, event_xml, corp_id)
    timestamp = str(int(time.time()))
    nonce = secrets.token_hex(8)
    signature = sign(callback_token, timestamp, nonce, encrypted_event)
    body = f"<xml><ToUserName><![CDATA[{corp_id}]]></ToUserName><AgentID>{agent_id}</AgentID><Encrypt><![CDATA[{encrypted_event}]]></Encrypt></xml>"
    event_query = urllib.parse.urlencode({"msg_signature": signature, "timestamp": timestamp, "nonce": nonce})
    status, response = raw_callback("POST", callback_path + "?" + event_query, body)
    assert status == 200 and response == "success", (status, response)
    status, replay = raw_callback("POST", callback_path + "?" + event_query, body)
    assert status == 200 and replay == "success"
    event_rows = api("GET", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/events", token=platform)
    matched = [item for item in event_rows if item["providerEventId"] == "msg:" + provider_message_id]
    assert len(matched) == 1 and matched[0]["status"] == "PROCESSED" and matched[0]["applicationCallId"]
    application_calls = api("GET", f"/api/applications/{application['id']}/calls", token=platform)
    provider_calls = [item for item in application_calls if item["id"] == matched[0]["applicationCallId"]]
    assert len(provider_calls) == 1 and provider_calls[0]["status"] == "SUCCESS"
    instance_count = int(sql(f"SELECT COUNT(*) FROM flow_instance WHERE business_type='APPLICATION_CALL' AND business_id='{provider_calls[0]['requestId']}'")[0][0])
    assert instance_count == 1
    facts["cases"].append({"case": "verified-callback-dedup-application-flow-route", "result": "PASS",
        "providerEventId": "msg:" + provider_message_id, "eventRowCount": 1,
        "applicationCallId": matched[0]["applicationCallId"], "applicationCallStatus": "SUCCESS",
        "flowInstanceCount": instance_count, "duplicateBusinessWrite": False})

    # 停用立即撤销，密钥明文不出现在连接、事件、调用或审计读回。
    connection = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/disable", {}, platform)
    new_nonce = secrets.token_hex(8)
    new_time = str(int(time.time()))
    disabled_query = urllib.parse.urlencode({"msg_signature": sign(callback_token, new_time, new_nonce, encrypted_echo),
        "timestamp": new_time, "nonce": new_nonce, "echostr": encrypted_echo})
    status, disabled_body = raw_callback("GET", callback_path + "?" + disabled_query)
    assert status == 403 and error_code(disabled_body) == "PROVIDER_CONNECTION_INACTIVE"
    db_secrets = sql(f"SELECT secret_ciphertext,secret_hint FROM app_provider_secret WHERE connection_id={int(connection['id'])}")
    assert len(db_secrets) == 3 and all(all(secret not in row[0] for secret in [api_secret, callback_token, encoding_key]) for row in db_secrets)
    safe_text = json.dumps([connection, event_rows, application_calls], ensure_ascii=False)
    assert all(secret not in safe_text for secret in [api_secret, callback_token, encoding_key])
    audit_secret_hits = int(sql("SELECT COUNT(*) FROM audit_event WHERE (detail_json LIKE '%" + callback_token
        + "%' OR detail_json LIKE '%" + encoding_key + "%' OR detail_json LIKE '%" + api_secret + "%')")[0][0])
    assert audit_secret_hits == 0
    pending = int(sql(f"SELECT COUNT(*) FROM app_call WHERE application_id={int(application['id'])} AND status='PENDING'")[0][0])
    assert pending == 0
    facts["cases"].append({"case": "disable-revocation-safe-history", "result": "PASS",
        "disabledError": "PROVIDER_CONNECTION_INACTIVE", "encryptedSecretVersions": len(db_secrets),
        "secretPlaintextInReadbackOrAudit": False, "pendingApplicationCalls": pending})

    facts.update({"status": "passed", "application_id": application["id"], "flow_id": flow["id"],
                  "flow_code": flow_code, "connection_id": connection["id"],
                  "callback_path": callback_path, "application_call_id": matched[0]["applicationCallId"]})
finally:
    try:
        if connection is not None and platform is not None and application is not None:
            try:
                if connection.get("status") != "DISABLED":
                    api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/disable", {}, platform)
            except Exception:
                pass
            facts["cleanup"]["provider_connection_disabled"] = True
        if application is not None and platform is not None:
            try:
                api("POST", f"/api/applications/{application['id']}/disable", {"reason": "B12.1 企业微信本地协议验收完成"}, platform)
            except Exception:
                pass
            facts["cleanup"]["application_disabled"] = True
        if flow is not None:
            sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(flow['id'])} AND code='{flow_code}'")
            facts["cleanup"]["flow_disabled"] = True
    finally:
        save_facts(facts)

assert facts["status"] == "passed", facts
print(json.dumps({"status": "PASS", "cases": len(facts["cases"]),
                  "applicationId": facts["application_id"], "connectionId": facts["connection_id"],
                  "applicationCallId": facts["application_call_id"], "externalStatus": "blocked_external"}, ensure_ascii=False))
