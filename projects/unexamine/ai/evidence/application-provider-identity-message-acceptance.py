"""B12.2 企业微信成员映射、当前权限回调和结构化消息真实验收。"""
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
FACTS = AREA / "application-provider-identity-message-facts.json"

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
        "expectedDraftRevision": check["draftRevision"], "changeSummary": "B12.2 映射成员回调入口",
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


def raw_callback(path, body):
    request = urllib.request.Request("http://localhost:18080" + path, method="POST",
        headers={"Content-Type": "application/xml"}, data=body.encode())
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def callback(path, corp_id, agent_id, token, encoding_key, sender, message_id):
    event_xml = (f"<xml><ToUserName><![CDATA[{corp_id}]]></ToUserName>"
                 f"<FromUserName><![CDATA[{sender}]]></FromUserName>"
                 f"<CreateTime>{int(time.time())}</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                 f"<Content><![CDATA[请发起映射成员审批]]></Content><MsgId>{message_id}</MsgId>"
                 f"<AgentID>{agent_id}</AgentID></xml>")
    encrypted = encrypt(encoding_key, event_xml, corp_id)
    timestamp, nonce = str(int(time.time())), secrets.token_hex(8)
    query = urllib.parse.urlencode({"msg_signature": sign(token, timestamp, nonce, encrypted),
                                    "timestamp": timestamp, "nonce": nonce})
    body = (f"<xml><ToUserName><![CDATA[{corp_id}]]></ToUserName><AgentID>{agent_id}</AgentID>"
            f"<Encrypt><![CDATA[{encrypted}]]></Encrypt></xml>")
    return raw_callback(path + "?" + query, body)


def code(body):
    value = json.loads(body)
    assert value.get("requestId"), value
    return value["code"]


def save_facts(facts):
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


facts = {"step": "B12.2", "status": "running", "cases": [], "cleanup": {},
         "external_status": "blocked_external",
         "external_reason": "工作区没有真实企业微信租户凭据和公网回调地址控制权"}
flow = application = connection = identity = template = None
system_flow = system_application = system_connection = system_identity = None
system_token = None
platform = None
suffix = secrets.token_hex(5)
flow_code = "wecom_identity_" + suffix
external_user = "mapped-" + suffix
corp_id = "ww" + secrets.token_hex(8)
agent_id = str(secrets.randbelow(800000) + 100000)
callback_token = "T" + secrets.token_hex(12)
encoding_key = base64.b64encode(os.urandom(32)).decode().rstrip("=")
try:
    bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
    login = api("POST", "/api/auth/login", {
        "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
        "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
    platform = login["accessToken"]

    flow = api("POST", "/api/flows", {"code": flow_code, "name": "企业微信映射身份验收 Flow",
        "description": "B12.2 临时映射成员事件路由"}, platform)
    publish_flow(flow, platform)
    created = api("POST", "/api/applications", {"code": "wecom_identity_app_" + suffix,
        "name": "企业微信映射身份与消息验收", "description": "当前成员权限与结构化消息",
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
        "changeSummary": "发布企业微信映射身份和消息入口"}, platform)["application"]
    published = application["publishedGrants"][0]

    connection = api("POST", f"/api/applications/{application['id']}/provider-connections", {
        "providerCode": "WECOM", "productCode": "INTERNAL_APP", "displayName": "企业微信映射身份入口",
        "tenantKey": corp_id, "applicationKey": agent_id, "apiSecret": "S" + secrets.token_hex(20),
        "callbackToken": callback_token, "encodingAesKey": encoding_key,
        "route": {"eventCode": "MESSAGE_TEXT", "resourceType": published["resourceType"],
                  "resourceId": published["resourceId"], "actionCode": published["actionCode"],
                  "targetSystemId": published.get("targetSystemId"),
                  "targetTenantId": published.get("targetTenantId")}}, platform)
    options = api("GET", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identity-options", token=platform)
    assert options and all(item["memberName"] and item["contextName"] for item in options)
    selected = options[0]
    identity = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identities", {
        "externalUserId": external_user, "targetType": selected["targetType"],
        "targetMemberId": selected["targetMemberId"]}, platform)
    assert identity["memberName"] == selected["memberName"] and identity["status"] == "ACTIVE"
    identity_rows = api("GET", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identities", token=platform)
    assert len(identity_rows) == 1 and "targetPlatformId" not in identity_rows[0]
    facts["cases"].append({"case": "named-member-selector-and-identity-persistence", "result": "PASS",
        "identityId": identity["id"], "memberName": identity["memberName"], "rawInternalContextReturned": False})

    diagnosed = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/diagnose",
        {"activateAfterPass": True}, platform)
    connection = diagnosed["connection"]
    assert connection["status"] == "ACTIVE" and any("UserID" in check for check in diagnosed["checks"])

    # 未映射成员在事件记录和Flow写入前拒绝。
    unknown_id = str(int(time.time() * 1000000))
    before_instances = int(sql(f"SELECT COUNT(*) FROM flow_instance WHERE flow_id={int(flow['id'])}")[0][0])
    status, body = callback(connection["callbackPath"], corp_id, agent_id, callback_token, encoding_key,
                            "unknown-" + suffix, unknown_id)
    assert status == 403 and code(body) == "PROVIDER_IDENTITY_UNMAPPED"
    unknown_events = int(sql(f"SELECT COUNT(*) FROM app_provider_event WHERE connection_id={int(connection['id'])} AND provider_event_id='msg:{unknown_id}'")[0][0])
    after_instances = int(sql(f"SELECT COUNT(*) FROM flow_instance WHERE flow_id={int(flow['id'])}")[0][0])
    assert unknown_events == 0 and before_instances == after_instances
    facts["cases"].append({"case": "unknown-provider-member-rejected-before-business-write", "result": "PASS",
        "errorCode": "PROVIDER_IDENTITY_UNMAPPED", "eventRows": unknown_events, "flowWrites": 0})

    # 有效成员进入既有应用网关，权限快照明确记录 PROVIDER，而不是应用创建人服务身份。
    valid_id = str(int(time.time() * 1000000) + 1)
    status, body = callback(connection["callbackPath"], corp_id, agent_id, callback_token, encoding_key,
                            external_user, valid_id)
    assert status == 200 and body == "success", (status, body)
    event_rows = api("GET", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/events", token=platform)
    event = next(item for item in event_rows if item["providerEventId"] == "msg:" + valid_id)
    call_row = sql(f"SELECT HEX(permission_snapshot_json),HEX(response_json),status FROM app_call WHERE id={int(event['applicationCallId'])}")[0]
    permission_snapshot = json.loads(bytes.fromhex(call_row[0]).decode("utf-8"))
    response = json.loads(bytes.fromhex(call_row[1]).decode("utf-8"))
    assert call_row[2] == "SUCCESS" and permission_snapshot["accessChannel"] == "PROVIDER"
    assert response["source"]["accessChannel"] == "PROVIDER"
    mapped_account = int(sql(f"SELECT account_id FROM plat_member WHERE id={int(selected['targetMemberId'])}")[0][0])
    assert permission_snapshot["sourceIdentity"]["accountId"] == mapped_account
    instance_count = int(sql(f"SELECT COUNT(*) FROM flow_instance WHERE business_type='APPLICATION_CALL' AND business_id='{response['requestId']}'")[0][0])
    assert instance_count == 1
    facts["cases"].append({"case": "mapped-current-permission-provider-callback", "result": "PASS",
        "applicationCallId": event["applicationCallId"], "accessChannel": "PROVIDER",
        "mappedActorAccountId": mapped_account, "flowInstanceCount": instance_count})

    template = api("POST", "/api/message-templates", {"code": "WECOM_NOTICE_" + suffix.upper(),
        "name": "企业微信审批入口通知", "channel": "WECOM", "subjectTemplate": "待处理：{{title}}",
        "contentTemplate": "{{member}}，请从受控入口处理 {{title}}。", "requiredVariables": ["member", "title"]}, platform)
    template = api("POST", f"/api/message-templates/{template['id']}/publish", {}, platform)
    key = "provider-message-" + suffix
    message_body = {"identityId": identity["id"], "templateCode": template["code"],
                    "idempotencyKey": key, "variables": {"member": identity["memberName"], "title": "采购审批"}}
    delivery = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/messages",
                   message_body, platform)
    replay = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/messages",
                 message_body, platform)
    assert replay["id"] == delivery["id"] and delivery["status"] == "BLOCKED_EXTERNAL"
    assert delivery["errorCode"] == "WECOM_EXTERNAL_CREDENTIALS_UNAVAILABLE"
    assert delivery["deepLink"].startswith("/platform/flows?flowCode=")
    altered = dict(message_body)
    altered["variables"] = {"member": identity["memberName"], "title": "另一审批"}
    conflict_status, conflict_body = call("POST",
        f"/api/applications/{application['id']}/provider-connections/{connection['id']}/messages",
        altered, platform)
    assert conflict_status == 409 and conflict_body["code"] == "PROVIDER_DELIVERY_IDEMPOTENCY_CONFLICT"
    counts = sql(f"SELECT (SELECT COUNT(*) FROM app_provider_delivery WHERE connection_id={int(connection['id'])} AND idempotency_key='{key}'),"
                 f"(SELECT COUNT(*) FROM msg_delivery WHERE message_id={int(delivery['messageId'])} AND channel='WECOM'),"
                 f"(SELECT COUNT(*) FROM msg_delivery WHERE message_id={int(delivery['messageId'])} AND channel='IN_APP')")[0]
    assert counts == ["1", "1", "1"]
    facts["cases"].append({"case": "structured-template-message-deep-link-idempotency", "result": "PASS",
        "deliveryId": delivery["id"], "status": delivery["status"], "deepLink": delivery["deepLink"],
        "providerDeliveryRows": 1, "wecomDeliveryRows": 1, "inAppDeliveryRows": 1,
        "idempotencyConflict": "PROVIDER_DELIVERY_IDEMPOTENCY_CONFLICT"})

    identity = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/identities/{identity['id']}/disable", {}, platform)
    disabled_id = str(int(time.time() * 1000000) + 2)
    status, body = callback(connection["callbackPath"], corp_id, agent_id, callback_token, encoding_key,
                            external_user, disabled_id)
    assert status == 403 and code(body) == "PROVIDER_IDENTITY_DISABLED"
    assert int(sql(f"SELECT COUNT(*) FROM app_provider_event WHERE connection_id={int(connection['id'])} AND provider_event_id='msg:{disabled_id}'")[0][0]) == 0
    facts["cases"].append({"case": "identity-revocation-immediate", "result": "PASS",
        "identityStatus": identity["status"], "callbackError": "PROVIDER_IDENTITY_DISABLED", "businessWrites": 0})

    # 系统应用使用同一映射协议，但成员范围必须固定到应用所属系统与租户。
    system_id, tenant_id = 54, 56
    system_token = api("POST", f"/api/systems/{system_id}/tenants/{tenant_id}/enter", {}, platform)["tokens"]["accessToken"]
    system_flow_code = "wecom_system_identity_" + suffix
    system_flow = api("POST", "/api/flows", {"code": system_flow_code,
        "name": "企业微信系统成员映射验收 Flow", "description": "B12.2 系统租户成员映射"}, system_token)
    publish_flow(system_flow, system_token)
    created = api("POST", "/api/applications", {"code": "wecom_system_identity_app_" + suffix,
        "name": "企业微信系统成员映射验收", "description": "系统租户成员当前权限",
        "applicationType": "SERVICE", "iconCode": "message", "credentialValidityDays": 30}, system_token)
    system_application = created["application"]
    system_grant = {"targetType": "SYSTEM", "targetSystemId": system_id, "targetTenantId": tenant_id,
        "resourceType": "FLOW", "resourceId": system_flow_code, "actionCode": "START",
        "dataScope": {"type": "ALL"}, "rateLimit": {"accessChannels": ["EXTERNAL"],
        "maxRequests": 100, "windowSeconds": 600, "allowedIps": ["*"],
        "failureDisableThreshold": 5, "failureWindowSeconds": 300},
        "fields": [{"fieldCode": "title", "readable": True, "writable": True, "maskStrategy": "NONE"}]}
    system_application = api("PUT", f"/api/applications/{system_application['id']}/draft", {
        "expectedVersion": system_application["version"], "name": system_application["name"],
        "description": system_application["description"], "applicationType": system_application["applicationType"],
        "iconCode": "message", "callbacks": [], "grants": [system_grant]}, system_token)
    system_application = api("POST", f"/api/applications/{system_application['id']}/publish", {
        "expectedDraftRevision": system_application["draftRevision"],
        "changeSummary": "发布企业微信系统成员映射入口"}, system_token)["application"]
    system_published = system_application["publishedGrants"][0]
    system_corp = "ww" + secrets.token_hex(8)
    system_agent = str(secrets.randbelow(800000) + 100000)
    system_callback_token = "T" + secrets.token_hex(12)
    system_encoding_key = base64.b64encode(os.urandom(32)).decode().rstrip("=")
    system_connection = api("POST", f"/api/applications/{system_application['id']}/provider-connections", {
        "providerCode": "WECOM", "productCode": "INTERNAL_APP", "displayName": "企业微信系统成员入口",
        "tenantKey": system_corp, "applicationKey": system_agent,
        "callbackToken": system_callback_token, "encodingAesKey": system_encoding_key,
        "route": {"eventCode": "MESSAGE_TEXT", "resourceType": system_published["resourceType"],
                  "resourceId": system_published["resourceId"], "actionCode": system_published["actionCode"],
                  "targetSystemId": system_published["targetSystemId"],
                  "targetTenantId": system_published["targetTenantId"]}}, system_token)
    system_options = api("GET", f"/api/applications/{system_application['id']}/provider-connections/{system_connection['id']}/identity-options", token=system_token)
    assert system_options and all(item["targetType"] == "SYSTEM" for item in system_options)
    system_selected = system_options[0]
    system_external_user = "system-" + suffix
    system_identity = api("POST", f"/api/applications/{system_application['id']}/provider-connections/{system_connection['id']}/identities", {
        "externalUserId": system_external_user, "targetType": "SYSTEM",
        "targetMemberId": system_selected["targetMemberId"]}, system_token)
    assert system_identity["contextName"].startswith("系统租户")
    system_connection = api("POST", f"/api/applications/{system_application['id']}/provider-connections/{system_connection['id']}/diagnose",
        {"activateAfterPass": True}, system_token)["connection"]
    system_message_id = str(int(time.time() * 1000000) + 3)
    status, body = callback(system_connection["callbackPath"], system_corp, system_agent,
                            system_callback_token, system_encoding_key, system_external_user, system_message_id)
    assert status == 200 and body == "success", (status, body)
    system_events = api("GET", f"/api/applications/{system_application['id']}/provider-connections/{system_connection['id']}/events", token=system_token)
    system_event = next(item for item in system_events if item["providerEventId"] == "msg:" + system_message_id)
    system_call = sql(f"SELECT HEX(permission_snapshot_json),status FROM app_call WHERE id={int(system_event['applicationCallId'])}")[0]
    system_permission = json.loads(bytes.fromhex(system_call[0]).decode("utf-8"))
    assert system_call[1] == "SUCCESS" and system_permission["accessChannel"] == "PROVIDER"
    assert system_permission["targetIdentity"]["systemId"] == system_id
    assert system_permission["targetIdentity"]["tenantId"] == tenant_id
    facts["cases"].append({"case": "system-tenant-member-boundary-and-provider-callback", "result": "PASS",
        "identityId": system_identity["id"], "memberName": system_identity["memberName"],
        "systemId": system_id, "tenantId": tenant_id, "accessChannel": "PROVIDER"})

    facts.update({"status": "passed", "application_id": application["id"], "flow_id": flow["id"],
                  "connection_id": connection["id"], "identity_id": identity["id"],
                  "delivery_id": delivery["id"], "message_id": delivery["messageId"],
                  "application_call_id": event["applicationCallId"]})
finally:
    try:
        if connection is not None and platform is not None and application is not None:
            try:
                connection = api("POST", f"/api/applications/{application['id']}/provider-connections/{connection['id']}/disable", {}, platform)
            except Exception:
                pass
            facts["cleanup"]["provider_connection_disabled"] = True
        if application is not None and platform is not None:
            try:
                api("POST", f"/api/applications/{application['id']}/disable", {"reason": "B12.2 企业微信映射身份验收完成"}, platform)
            except Exception:
                pass
            facts["cleanup"]["application_disabled"] = True
        if template is not None:
            sql(f"UPDATE msg_template SET status='DRAFT',version=version+1 WHERE id={int(template['id'])}")
            facts["cleanup"]["message_template_unpublished"] = True
        if system_connection is not None and system_token is not None and system_application is not None:
            try:
                api("POST", f"/api/applications/{system_application['id']}/provider-connections/{system_connection['id']}/disable", {}, system_token)
            except Exception:
                pass
            facts["cleanup"]["system_provider_connection_disabled"] = True
        if system_application is not None and system_token is not None:
            try:
                api("POST", f"/api/applications/{system_application['id']}/disable", {"reason": "B12.2 系统成员映射验收完成"}, system_token)
            except Exception:
                pass
            facts["cleanup"]["system_application_disabled"] = True
        if system_flow is not None:
            sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(system_flow['id'])}")
            facts["cleanup"]["system_flow_disabled"] = True
        if flow is not None:
            sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(flow['id'])} AND code='{flow_code}'")
            facts["cleanup"]["flow_disabled"] = True
    finally:
        save_facts(facts)

assert facts["status"] == "passed", facts
print(json.dumps({"status": "PASS", "cases": len(facts["cases"]),
                  "applicationId": facts["application_id"], "connectionId": facts["connection_id"],
                  "identityId": facts["identity_id"], "deliveryId": facts["delivery_id"],
                  "externalStatus": "blocked_external"}, ensure_ascii=False))
