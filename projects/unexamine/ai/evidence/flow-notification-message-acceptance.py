"""B10.2b 抄送、通知、消息节点的真实 API、数据库、重启与清理验收。"""
import ast
import json
import re
import secrets
import sys
import time
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))

PATH = AREA / "flow-notification-message-facts.json"
facts = json.loads(PATH.read_text(encoding="utf-8")) if PATH.exists() else {
    "step": "B10.2b", "status": "running", "code": "flow_message_" + secrets.token_hex(5),
    "flows": [], "instances": [], "roles": [], "messages": [], "cases": []}
assert facts["status"] == "running", "验收事实已经封存"

java = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
default_username = re.search(r"default-admin\.username:([^}]+)", java).group(1)
login = api("POST", "/api/auth/login", {
    "username": default_username,
    "password": re.search(r"default-admin\.initial-password:([^}]+)", java).group(1)})
admin = api("POST", "/api/systems/54/tenants/57/enter", {}, login["accessToken"])["tokens"]["accessToken"]


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def case(name, result, **extra):
    if not any(item["case"] == name for item in facts["cases"]):
        facts["cases"].append({"case": name, "result": result, **extra})
        save()


def node(key, kind, name, x, recipient=None, subject=None, content=None, retry=False, destination="FLOW_INSTANCE"):
    message = kind in {"CC", "NOTIFICATION", "MESSAGE"}
    return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
        "config": ({"schemaVersion": 1, "subject": subject or name, "content": content or "请查看流程处理详情。",
                    "appendInstanceTitle": True, "destinationType": destination} if message else {}),
        "assigneePolicy": ({"type": "PERSON", "tenantMemberIds": [recipient]} if message and recipient else {}),
        "formPolicy": {}, "timeoutPolicy": {},
        "exceptionPolicy": ({"action": "RETRY", "maxAttempts": 3, "retryDelayMinutes": 1}
                            if message and retry else {"action": "MANUAL"} if message else {})}


def edge(index, source, target):
    return {"edgeKey": "e" + str(index), "sourceNodeKey": source, "targetNodeKey": target,
        "priorityOrder": 0, "config": {}}


def create_flow(label, middle):
    flow = api("POST", "/api/flows", {"code": facts["code"] + "_" + secrets.token_hex(3),
        "name": label, "description": "B10.2b 真实验收夹具"}, admin)
    facts["flows"].append(flow["id"]); save()
    nodes = [node("start", "START", "开始", 0)] + middle + [node("end", "END", "结束", (len(middle) + 1) * 240)]
    chain = [item["nodeKey"] for item in nodes]
    edges = [edge(index, chain[index], chain[index + 1]) for index in range(len(chain) - 1)]
    api("PUT", f"/api/flows/{flow['id']}/draft", {
        "expectedVersion": flow["version"], "nodes": nodes, "edges": edges}, admin)
    return flow["id"]


def publish(flow_id, summary):
    check = api("GET", f"/api/flows/{flow_id}/publication-check", token=admin)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/flows/{flow_id}/publish", {
        "expectedDraftRevision": check["draftRevision"], "changeSummary": summary,
        "simulationVariables": {}}, admin)


def start(flow_id, title, key):
    body = {"flowId": flow_id, "title": title, "businessSnapshot": {}, "variables": {},
        "idempotencyKey": key}
    result = api("POST", "/api/flow-runtime/instances", body, admin)
    instance_id = result["instance"]["id"]
    if instance_id not in facts["instances"]:
        facts["instances"].append(instance_id); save()
    return result, body


def view(instance_id):
    return api("GET", f"/api/flow-runtime/instances/{instance_id}", token=admin)


def wait_for(instance_id, predicate, seconds=20):
    deadline = time.time() + seconds
    latest = view(instance_id)
    while time.time() < deadline and not predicate(latest):
        time.sleep(0.5)
        latest = view(instance_id)
    assert predicate(latest), (instance_id, latest["status"], latest.get("jobs"), latest.get("exceptions"))
    return latest


def flow_messages(instance_id):
    rows = sql("SELECT m.id,m.source_id,m.subject,r.status,d.status,d.channel,m.target_type,m.target_id "
        "FROM msg_message m JOIN msg_recipient r ON r.message_id=m.id "
        "JOIN msg_delivery d ON d.message_id=m.id AND d.account_id=r.account_id "
        f"WHERE m.source_type='FLOW' AND m.source_id LIKE 'instance-{int(instance_id)}-message-%' ORDER BY m.id")
    return rows


stage = sys.argv[1]
if stage == "prepare":
    assert not facts.get("prepared"), "prepare 不能重复创建夹具"
    # 建立独立接收人；凭证只存在当前进程，事实文件仅记录清理所需身份主键。
    username = "flow_message_recipient_" + secrets.token_hex(6)
    registration = api("POST", "/api/auth/register", {"username": username,
        "password": secrets.token_urlsafe(24), "displayName": "消息可靠性验收接收人", "systemName": "消息验收临时系统"})
    role = api("POST", "/api/admin/system/authorization/roles", {"name": "消息验收只读角色",
        "permissions": [{"resourceType": "MESSAGE", "resourceCode": "SYSTEM", "actionCode": "VIEW", "dataScopeType": "ALL"}],
        "fieldPolicies": []}, admin)
    api("POST", f"/api/admin/system/authorization/roles/{role['id']}/publish", {
        "expectedVersion": role["version"], "reason": "消息可靠投递验收"}, admin)
    member = api("POST", "/api/admin/system/authorization/members", {"account": username,
        "departmentId": None, "roleIds": [role["id"]]}, admin)
    recipient_token = api("POST", "/api/systems/54/tenants/57/enter", {},
        registration["platformTokens"]["accessToken"])["tokens"]["accessToken"]
    facts["roles"].append(role["id"])
    facts["recipient"] = {"accountId": registration["accountId"], "tenantMemberId": member["tenantMemberId"],
        "registrationSystemId": registration["systemId"], "username": username}
    save()
    recipient_tenant_member = member["tenantMemberId"]

    # 页面协议之外的自定义跳转和空接收人必须在发布前拒绝。
    invalid = create_flow("拒绝自定义消息目标", [node("bad", "MESSAGE", "非法消息", 240,
        recipient_tenant_member, "非法消息", "不应发布", destination="SYSTEM_ROUTE")])
    check = api("GET", f"/api/flows/{invalid}/publication-check", token=admin)
    assert not check["valid"] and "FLOW_MESSAGE_CONFIG_INVALID" in {item["code"] for item in check["issues"]}
    missing = create_flow("拒绝空消息接收人", [node("missing", "CC", "空抄送", 240,
        subject="空抄送", content="不应发布")])
    check = api("GET", f"/api/flows/{missing}/publication-check", token=admin)
    assert not check["valid"] and "FLOW_MESSAGE_RECIPIENT_MISSING" in {item["code"] for item in check["issues"]}
    case("publication-rejects-unsafe-destination-and-empty-recipient",
        "发布校验拒绝自定义内部跳转和空接收人；节点只允许当前流程运行页。")

    marker = facts["code"]
    middle = [
        node("cc", "CC", "流程抄送", 240, recipient_tenant_member, marker + " 抄送", "抄送正文"),
        node("notice", "NOTIFICATION", "结果通知", 480, recipient_tenant_member, marker + " 通知", "通知正文"),
        node("message", "MESSAGE", "业务消息", 720, recipient_tenant_member, marker + " 消息", "消息正文")]
    success_flow = create_flow("抄送通知消息可靠投递", middle)
    published = publish(success_flow, "三类站内消息可靠投递")
    detail = api("GET", f"/api/flows/{success_flow}", token=admin)
    frozen = [item for item in detail["versions"][0]["snapshot"]["nodes"]
              if item["nodeType"] in {"CC", "NOTIFICATION", "MESSAGE"}]
    assert [item["config"]["messageKind"] for item in frozen] == ["CC", "NOTIFICATION", "MESSAGE"]
    assert all(item["config"]["schemaVersion"] == 1 and item["config"]["destinationType"] == "FLOW_INSTANCE"
               and item["assigneePolicy"]["tenantMemberIds"] == [recipient_tenant_member] for item in frozen)
    success_key = marker + "_success"
    success, success_body = start(success_flow, "三类消息验收", success_key)
    completed = wait_for(success["instance"]["id"], lambda item: item["status"] == "COMPLETED")
    jobs = [item for item in completed["jobs"] if item["jobType"] == "MESSAGE_DELIVERY"]
    events = [item for item in completed["history"] if item["eventType"] == "FLOW_MESSAGE_DELIVERED"]
    assert len(jobs) == 3 and all(item["status"] == "COMPLETED" and item["attemptCount"] == 1 for item in jobs)
    assert [item["detail"]["messageKind"] for item in events] == ["CC", "NOTIFICATION", "MESSAGE"]
    assert all(item["detail"]["recipientCount"] == 1 for item in events)
    rows = flow_messages(completed["id"])
    assert len(rows) == 3 and all(row[4:8] == ["DELIVERED", "IN_APP", "FLOW_INSTANCE", str(completed["id"])] for row in rows)
    facts["messages"].extend(int(row[0]) for row in rows); save()
    inbox = api("GET", "/api/messages?status=ALL&sourceType=FLOW", token=recipient_token)
    visible = [item for item in inbox["messages"] if item["id"] in facts["messages"]]
    assert len(visible) == 3 and all(item["targetType"] == "FLOW_INSTANCE"
        and f"instanceId={completed['id']}" in item["targetRoute"] for item in visible)
    replay = api("POST", "/api/flow-runtime/instances", success_body, admin)
    assert replay["replayed"] is True and replay["instance"]["id"] == completed["id"]
    assert len(flow_messages(completed["id"])) == 3
    case("three-message-kinds-use-one-durable-path",
        "抄送、通知和消息依次通过同一种持久化作业送达；收件箱展示三条站内消息并安全打开当前流程。",
        instanceId=completed["id"], flowVersionId=published["versionId"], messageCount=3)

    retry_flow = create_flow("失效接收人重启后可靠恢复", [node("retry_message", "MESSAGE", "恢复消息", 240,
        member["tenantMemberId"], marker + " 恢复消息", "接收人恢复后只应收到一次。", retry=True)])
    publish(retry_flow, "接收人失效后的可靠重试")
    sql(f"UPDATE sys_tenant_member SET status='DISABLED',version=version+1 WHERE id={int(member['tenantMemberId'])} "
        f"AND tenant_id=57 AND system_id=54")
    retry_key = marker + "_retry"
    retry_result, retry_body = start(retry_flow, "消息重启恢复验收", retry_key)
    pending = wait_for(retry_result["instance"]["id"], lambda item: item["status"] == "WAITING"
        and any(job["jobType"] == "MESSAGE_DELIVERY" and job["status"] == "PENDING" and job["attemptCount"] == 1
                for job in item["jobs"]))
    assert not flow_messages(pending["id"])
    facts["fixtures"] = {"success": {"flow": success_flow, "instance": completed["id"], "key": success_key,
        "body": success_body}, "retry": {"flow": retry_flow, "instance": pending["id"], "key": retry_key,
        "body": retry_body}}
    facts["prepared"] = True; save()
    case("disabled-recipient-is-retried-without-partial-message",
        "发布后停用的接收人不会产生半条消息；实例等待，可靠作业记录第一次失败并保留下一次执行。",
        instanceId=pending["id"])
    print(json.dumps({"prepare": "PASS", "successInstance": completed["id"],
        "retryInstance": pending["id"]}, ensure_ascii=False))

elif stage == "resume":
    assert facts.get("prepared") and not facts.get("resumed")
    recipient = facts["recipient"]
    retry = facts["fixtures"]["retry"]
    before = view(retry["instance"])
    message_job = next(job for job in before["jobs"] if job["jobType"] == "MESSAGE_DELIVERY")
    assert before["status"] == "WAITING" and message_job["status"] == "PENDING" and message_job["attemptCount"] == 1
    sql(f"UPDATE sys_tenant_member SET status='ACTIVE',version=version+1 WHERE id={int(recipient['tenantMemberId'])} "
        f"AND tenant_id=57 AND system_id=54; UPDATE flow_job SET next_run_at=NOW(),version=version+1 "
        f"WHERE id={int(message_job['id'])} AND instance_id={int(retry['instance'])} AND status='PENDING'")
    completed = wait_for(retry["instance"], lambda item: item["status"] == "COMPLETED")
    job = next(item for item in completed["jobs"] if item["jobType"] == "MESSAGE_DELIVERY")
    rows = flow_messages(retry["instance"])
    assert job["status"] == "COMPLETED" and job["attemptCount"] == 2 and len(rows) == 1
    assert rows[0][3:8] == ["UNREAD", "DELIVERED", "IN_APP", "FLOW_INSTANCE", str(retry["instance"])]
    assert len([item for item in completed["history"] if item["eventType"] == "FLOW_MESSAGE_RETRY"]) == 1
    assert len([item for item in completed["history"] if item["eventType"] == "FLOW_MESSAGE_DELIVERED"]) == 1
    assert not [item for item in completed["executions"] if item["status"] == "ACTIVE"]
    facts["messages"].append(int(rows[0][0])); facts["resumed"] = True; save()
    case("pending-delivery-survives-restart-and-delivers-once",
        "第一次失败的消息作业跨同一后端重启保留；接收人恢复后第二次送达且只有一条收件消息。",
        instanceId=retry["instance"], attemptCount=2)
    print(json.dumps({"resume": "PASS", "instance": retry["instance"], "attempts": 2}, ensure_ascii=False))

elif stage == "verify":
    assert facts.get("resumed") and not facts.get("verified")
    for name, expected in [("success", 3), ("retry", 1)]:
        fixture = facts["fixtures"][name]
        instance = view(fixture["instance"])
        assert instance["status"] == "COMPLETED" and len(flow_messages(instance["id"])) == expected
        replay = api("POST", "/api/flow-runtime/instances", fixture["body"], admin)
        assert replay["replayed"] is True and replay["instance"]["id"] == fixture["instance"]
        assert len(flow_messages(instance["id"])) == expected
    case("completed-deliveries-survive-restart-and-start-replay",
        "完成后的作业、投递历史和收件消息在再次重启后保持；相同启动键不重复节点或收件消息。")
    facts["verified"] = True; save()
    print(json.dumps({"verify": "PASS", "messages": len(facts["messages"])}, ensure_ascii=False))

elif stage == "gui":
    assert facts.get("verified") and not facts.get("gui")
    # 普通浏览器当前进入系统主工作空间，建立同构只读夹具供设计页和运行页验收。
    admin = api("POST", "/api/systems/54/tenants/56/enter", {}, login["accessToken"])["tokens"]["accessToken"]
    escaped_username = default_username.replace("'", "''")
    tenant_member = int(sql("SELECT tm.id FROM sys_tenant_member tm JOIN sys_member m ON m.id=tm.system_member_id "
        f"JOIN plat_account a ON a.id=m.account_id WHERE tm.system_id=54 AND tm.tenant_id=56 AND tm.status='ACTIVE' "
        f"AND a.username='{escaped_username}'")[0][0])
    marker = facts["code"] + " 界面"
    flow_id = create_flow("消息节点普通页面验收", [
        node("cc", "CC", "流程抄送", 240, tenant_member, marker + " 抄送", "抄送给相关处理人。"),
        node("notice", "NOTIFICATION", "结果通知", 480, tenant_member, marker + " 通知", "提醒关注处理结果。"),
        node("message", "MESSAGE", "业务消息", 720, tenant_member, marker + " 消息", "请查看流程运行详情。")])
    publish(flow_id, "普通页面验收")
    result, _ = start(flow_id, "消息节点普通页面验收", facts["code"] + "_gui")
    instance = wait_for(result["instance"]["id"], lambda item: item["status"] == "COMPLETED")
    rows = flow_messages(instance["id"])
    assert len(rows) == 3
    facts["messages"].extend(int(row[0]) for row in rows)
    facts["gui"] = {"flow": flow_id, "instance": instance["id"]}; save()
    print(json.dumps({"gui": "PASS", **facts["gui"]}, ensure_ascii=False))

elif stage == "cleanup":
    assert facts.get("verified") and not facts.get("cleanup")
    recipient = facts["recipient"]
    flow_ids = ",".join(str(int(value)) for value in facts["flows"])
    role_ids = ",".join(str(int(value)) for value in facts["roles"])
    message_ids = ",".join(str(int(value)) for value in facts["messages"])
    sql("START TRANSACTION; "
        + (f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN ({flow_ids}) "
           f"AND code LIKE '{facts['code']}%'; " if flow_ids else "")
        + (f"UPDATE sys_role SET status='DISABLED',version=version+1 WHERE id IN ({role_ids}) AND tenant_id=57; " if role_ids else "")
        + (f"UPDATE msg_recipient SET status='ARCHIVED',archived_at=NOW(),version=version+1 WHERE message_id IN ({message_ids}); " if message_ids else "")
        + f"UPDATE sys_tenant_member SET status='DISABLED',version=version+1 WHERE id={int(recipient['tenantMemberId'])} AND tenant_id=57; "
        + f"UPDATE auth_session SET revoked=1,revoked_reason='flow-message-acceptance-finished' WHERE account_id={int(recipient['accountId'])}; "
        + f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={int(recipient['accountId'])} AND username='{recipient['username']}'; "
        + f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={int(recipient['registrationSystemId'])}; COMMIT;")
    assert int(sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN ({','.join(str(int(v)) for v in facts['instances'])}) "
        "AND status IN ('PENDING','RUNNING','SUSPENDED')")[0][0]) == 0
    facts["cleanup"] = {"flowsDisabled": len(facts["flows"]), "messagesArchived": len(facts["messages"]),
        "temporaryRecipientDisabled": True, "sessionsRevoked": True, "pendingJobs": 0}
    facts["status"] = "passed"; save()
    print(json.dumps({"cleanup": "PASS", **facts["cleanup"]}, ensure_ascii=False))

else:
    raise AssertionError("stage 必须是 prepare、resume、verify、gui 或 cleanup")
