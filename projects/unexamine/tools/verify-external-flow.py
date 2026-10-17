"""在本地预览服务建立隔离测试系统，验证外部应用 Flow 发起、查询和审批。"""

import hashlib
import hmac
import json
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request


BASE = "http://127.0.0.1:9999"


def request(path, method="GET", body=None, token=None, headers=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode()
    values = {"Content-Type": "application/json"}
    if token:
        values["Authorization"] = "Bearer " + token
    values.update(headers or {})
    req = urllib.request.Request(BASE + path, data=data, headers=values, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def expected(path, method="GET", body=None, token=None):
    status, result = request(path, method, body, token)
    if status != 200 or result.get("code") not in ("OK", "SUCCESS", None):
        raise AssertionError(f"{method} {path}: HTTP {status} {result}")
    return result["data"]


def signed_call(client_id, client_secret, scope, action, payload, resource_id="g4_external_review"):
    body = {"resourceType": "FLOW", "resourceId": resource_id,
            "actionCode": action, "targetSystemId": scope["systemId"],
            "targetTenantId": scope["tenantId"], "requestedDataScope": scope["dataScope"],
            "payload": payload}
    raw = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode()
    timestamp = str(int(time.time() * 1000))
    nonce = secrets.token_hex(16)
    idem = "g4-" + secrets.token_hex(12)
    digest = hashlib.sha256(raw).hexdigest()
    canonical = "\n".join((client_id, timestamp, nonce, idem, digest)).encode()
    signature = hmac.new(client_secret.encode(), canonical, hashlib.sha256).hexdigest()
    req = urllib.request.Request(BASE + "/api/application-access/v1/calls", data=raw,
                                 headers={"Content-Type": "application/json",
                                          "X-App-Client-Id": client_id, "X-App-Timestamp": timestamp,
                                          "X-App-Nonce": nonce, "X-App-Idempotency-Key": idem,
                                          "X-App-Signature": signature}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def node(key, kind, name, x, policy=None, config=None):
    result = {"nodeKey": key, "nodeType": kind, "name": name,
              "positionX": x, "positionY": 0, "config": config or {}}
    if policy:
        result["assigneePolicy"] = policy
    return result


def edge(key, source, target, order):
    return {"edgeKey": key, "sourceNodeKey": source, "targetNodeKey": target,
            "priorityOrder": order, "config": {}}


def business(response):
    result = response["data"]["result"]
    return result.get("businessResult", result)


def main():
    suffix = secrets.token_hex(4)
    username = "g4_external_" + suffix
    registered = expected("/api/auth/register", "POST", {
        "username": username, "password": "correct-password", "displayName": "G4 外部流程验收",
        "email": username + "@example.com", "systemName": "G4 外部流程验收",
        "systemCode": "g4_external_" + suffix})
    token = registered["tokens"]["accessToken"]
    system_id, tenant_id = registered["systemId"], registered["tenantId"]
    created = expected("/api/applications", "POST", {
        "code": "g4_external_app", "name": "G4 外部审批应用", "applicationType": "SERVICE"}, token)
    app_id = created["application"]["id"]
    credential = created["issuedCredential"]
    catalog = expected(f"/api/applications/{app_id}/scope-catalog", token=token)
    members = catalog["members"]
    if len(members) != 1:
        raise AssertionError(f"新系统所属人未进入应用范围目录：{catalog}")
    member_id = members[0]["id"]
    principal = "external-approver-001"
    flow = expected("/api/flows", "POST", {
        "code": "g4_external_review", "name": "G4 外部审批验证"}, token)
    flow_id = flow["id"]
    config = {"approvalMode": "OR_SIGN", "manualOperationPolicy": {
        "allowedActions": ["APPROVE", "REJECT"],
        "opinionRequiredActions": ["REJECT"], "requireClaim": False}}
    expected(f"/api/flows/{flow_id}/draft", "PUT", {
        "expectedVersion": 0,
        "nodes": [node("start", "START", "开始", 0),
                  node("external", "APPROVAL", "外部审批", 200,
                       {"type": "EXTERNAL_PERSON", "applicationId": app_id,
                        "externalApproverId": principal}, config),
                  node("end", "END", "结束", 400)],
        "edges": [edge("to-external", "start", "external", 10),
                  edge("to-end", "external", "end", 20)]}, token)
    check = expected(f"/api/flows/{flow_id}/publication-check", token=token)
    if not check["valid"]:
        raise AssertionError(f"外部审批流程发布检查失败：{check}")
    expected(f"/api/flows/{flow_id}/publish", "POST", {
        "expectedDraftRevision": 2, "changeSummary": "外部审批端到端验收"}, token)
    data_scope = {"type": "RESPONSIBLE", "memberIds": [member_id], "departmentIds": []}
    rate_limit = {"accessChannels": ["EXTERNAL"], "maxRequests": 100,
                  "windowSeconds": 600, "allowedIps": ["172.17.0.1", "127.0.0.1"]}
    grants = [{"targetType": "SYSTEM", "targetSystemId": system_id,
               "targetTenantId": tenant_id, "resourceType": "FLOW",
               "resourceId": "g4_external_review", "actionCode": action,
               "dataScope": data_scope, "rateLimit": rate_limit, "fields": []}
              for action in ("START", "VIEW_INSTANCE", "LIST_TASKS", "HANDLE")]
    expected(f"/api/applications/{app_id}/draft", "PUT", {
        "expectedVersion": 0, "name": "G4 外部审批应用",
        "applicationType": "SERVICE", "callbacks": [], "grants": grants[:1]}, token)
    app_check = expected(f"/api/applications/{app_id}/publication-check", token=token)
    if not app_check["valid"]:
        raise AssertionError(f"应用发布检查失败：{app_check}")
    expected(f"/api/applications/{app_id}/publish", "POST", {
        "expectedDraftRevision": 2, "changeSummary": "仅开放 Flow 发起"}, token)
    scope = {"systemId": system_id, "tenantId": tenant_id, "dataScope": data_scope}
    client_id, client_secret = credential["clientId"], credential["clientSecret"]

    def call(action, payload, status=200):
        actual, response = signed_call(client_id, client_secret, scope, action, payload)
        if actual != status:
            raise AssertionError(f"{action} HTTP {actual}，预期 {status}：{response}")
        return response

    start_only_denied = call("LIST_TASKS", {"externalPrincipalId": principal}, 403)
    if start_only_denied.get("code") != "APPLICATION_GRANT_DENIED":
        raise AssertionError(f"仅授权 START 时查询未被拒绝：{start_only_denied}")
    app_view = expected(f"/api/applications/{app_id}", token=token)
    saved = expected(f"/api/applications/{app_id}/draft", "PUT", {
        "expectedVersion": app_view["version"], "name": "G4 外部审批应用",
        "applicationType": "SERVICE", "callbacks": [], "grants": grants}, token)
    app_check = expected(f"/api/applications/{app_id}/publication-check", token=token)
    if not app_check["valid"]:
        raise AssertionError(f"四项授权发布检查失败：{app_check}")
    expected(f"/api/applications/{app_id}/publish", "POST", {
        "expectedDraftRevision": saved["draftRevision"], "changeSummary": "四项 Flow 动作独立授权"}, token)

    started = call("START", {"externalPrincipalId": principal,
                             "title": "G4 真实外部审批", "variables": {}})["data"]
    instance_id = started["result"]["instance"]["id"]
    own = business(call("LIST_TASKS", {"externalPrincipalId": principal}))
    if own["total"] != 1 or own["items"][0]["instanceId"] != instance_id:
        raise AssertionError(f"外部审批待办不匹配：{own}")
    task_id = own["items"][0]["taskId"]
    wrong = business(call("LIST_TASKS", {"externalPrincipalId": "wrong-person"}))
    if wrong["total"] != 0:
        raise AssertionError(f"错误审批人看到了待办：{wrong}")
    denied = call("HANDLE", {"externalPrincipalId": "wrong-person",
                             "taskId": task_id, "decision": "APPROVE"}, 403)
    if denied.get("code") != "FLOW_EXTERNAL_APPROVER_DENIED":
        raise AssertionError(f"错误审批人拒绝码不符：{denied}")
    detail = business(call("VIEW_INSTANCE", {"externalPrincipalId": principal,
                                            "instanceId": instance_id}))
    if detail["instanceId"] != instance_id or len(detail["tasks"]) != 1:
        raise AssertionError(f"实例查询不匹配：{detail}")
    if "--restart" in sys.argv:
        subprocess.run(["docker", "restart", "unexamine-g1-preview"], check=True, capture_output=True)
        for attempt in range(60):
            try:
                with urllib.request.urlopen(BASE + "/actuator/health", timeout=2) as health:
                    if json.loads(health.read()).get("status") == "UP":
                        break
            except (OSError, TimeoutError):
                pass
            time.sleep(1)
        else:
            raise AssertionError("预览服务重启后未恢复健康")
        resumed = business(call("LIST_TASKS", {"externalPrincipalId": principal}))
        if resumed["total"] != 1 or resumed["items"][0]["taskId"] != task_id:
            raise AssertionError(f"重启后外部待办未恢复：{resumed}")
    handled = business(call("HANDLE", {"externalPrincipalId": principal,
                                      "taskId": task_id, "decision": "APPROVE"}))
    if handled["status"] != "COMPLETED":
        raise AssertionError(f"审批后流程未完成：{handled}")
    after = business(call("LIST_TASKS", {"externalPrincipalId": principal}))
    if after["total"] != 0:
        raise AssertionError(f"完成后仍存在待办：{after}")
    second = call("START", {"externalPrincipalId": principal,
                            "title": "G4 停用撤权验证", "variables": {}})["data"]
    revoked_instance_id = second["result"]["instance"]["id"]
    expected(f"/api/applications/{app_id}/disable", "POST", {"reason": "G4 测试撤权"}, token)
    revoked = call("LIST_TASKS", {"externalPrincipalId": principal}, 403)
    if revoked.get("code") != "APPLICATION_DISABLED":
        raise AssertionError(f"应用停用后调用未被拒绝：{revoked}")
    print(json.dumps({"systemId": system_id, "tenantId": tenant_id, "applicationId": app_id,
                      "flowId": flow_id, "instanceId": instance_id, "taskId": task_id,
                      "actionId": handled["actionId"], "wrongPersonDenied": denied["code"],
                      "startOnlyQueryDenied": start_only_denied["code"],
                      "finalStatus": handled["status"], "restartVerified": "--restart" in sys.argv,
                      "revokedInstanceId": revoked_instance_id,
                      "disabledDenied": revoked["code"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
