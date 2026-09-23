"""B10.2c.2a 导入完成/异常可靠 Flow 事件的真实 API、数据库、重启与清理验收。"""
import ast
import hashlib
import json
import re
import secrets
import sys
import time
import urllib.request
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))

FACTS = AREA / "flow-import-event-facts.json"
facts = json.loads(FACTS.read_text(encoding="utf-8")) if FACTS.exists() else {
    "step": "B10.2c.2a", "status": "running", "code": "flow_import_" + secrets.token_hex(5),
    "flows": [], "bindings": [], "batches": [], "files": [], "instances": [], "cases": []}
assert facts["status"] == "running", "验收事实已经封存"

MODULE_ID = 51
MODULE_CODE = "flow_condition_accept_20260918"
SYSTEM_ID = 54
TENANT_ID = 57

java = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
default_username = re.search(r"default-admin\.username:([^}]+)", java).group(1)
login = api("POST", "/api/auth/login", {
    "username": default_username,
    "password": re.search(r"default-admin\.initial-password:([^}]+)", java).group(1)})
admin = api("POST", f"/api/systems/{SYSTEM_ID}/tenants/{TENANT_ID}/enter", {}, login["accessToken"])["tokens"]["accessToken"]
account_id = int(sql("SELECT id FROM plat_account WHERE username='" + default_username.replace("'", "''") + "'")[0][0])


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def case(name, result, **extra):
    if not any(item["case"] == name for item in facts["cases"]):
        facts["cases"].append({"case": name, "result": result, **extra})
        save()


def node(key, kind, name, x):
    return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
        "config": {}, "assigneePolicy": {}, "formPolicy": {}, "timeoutPolicy": {}, "exceptionPolicy": {}}


def create_flow():
    flow = api("POST", "/api/flows", {"code": facts["code"], "name": "导入事件可靠触发验收",
        "description": "B10.2c.2a 真实导入事件验收夹具"}, admin)
    facts["flows"].append(flow["id"]); save()
    api("PUT", f"/api/flows/{flow['id']}/draft", {"expectedVersion": flow["version"],
        "nodes": [node("start", "START", "开始", 0), node("end", "END", "结束", 240)],
        "edges": [{"edgeKey": "e1", "sourceNodeKey": "start", "targetNodeKey": "end",
                   "priorityOrder": 0, "config": {}}]}, admin)
    check = api("GET", f"/api/flows/{flow['id']}/publication-check", token=admin)
    assert check["valid"], check["issues"]
    api("POST", f"/api/flows/{flow['id']}/publish", {"expectedDraftRevision": check["draftRevision"],
        "changeSummary": "导入完成与数据异常可靠事件验收", "simulationVariables": {}}, admin)
    return flow["id"]


def publish_binding(flow_id, event_type, condition):
    binding = api("POST", "/api/flow-bindings/publish", {"moduleId": MODULE_ID,
        "triggerEvent": event_type, "flowId": flow_id, "executionMode": "FLOW_ONLY",
        "priorityOrder": 0, "condition": condition, "mutuallyExclusive": True,
        "replaceExisting": False}, admin)
    facts["bindings"].append(binding["id"]); save()
    return binding


def upload_csv(content, label):
    raw = content.encode("utf-8")
    session = api("POST", "/api/files/uploads", {"originalName": facts["code"] + "_" + label + ".csv",
        "contentType": "text/csv", "expectedSize": len(raw),
        "expectedSha256": hashlib.sha256(raw).hexdigest()}, admin)
    request = urllib.request.Request(f"http://localhost:18080/api/files/uploads/{session['id']}/content",
        method="PUT", headers={"Authorization": "Bearer " + admin, "Content-Type": "application/octet-stream",
            "X-Upload-Token": session["uploadToken"]}, data=raw)
    with urllib.request.urlopen(request, timeout=25) as response:
        uploaded = json.load(response)["data"]
    deadline = time.time() + 20
    while uploaded["scanStatus"] not in {"CLEAN", "INFECTED", "FAILED"} and time.time() < deadline:
        time.sleep(.3); uploaded = api("GET", f"/api/files/{uploaded['id']}", token=admin)
    assert uploaded["scanStatus"] == "CLEAN", uploaded["scanStatus"]
    uploaded = api("POST", f"/api/files/{uploaded['id']}/references", {"ownerType": "ACCOUNT",
        "ownerId": str(account_id), "fieldCode": "managed_files", "referenceType": "DOCUMENT"}, admin)
    facts["files"].append({"id": uploaded["id"], "referenceId": uploaded["references"][-1]["id"]}); save()
    return uploaded["id"]


def execute_import(content, label, mapping):
    file_id = upload_csv(content, label)
    batch = api("POST", f"/api/runtime/modules/{MODULE_CODE}/imports/preview", {"sourceFileId": file_id,
        "columnMapping": mapping, "conflictPolicy": "ERROR", "mappingName": label + "字段映射"}, admin)
    batch = api("POST", f"/api/runtime/modules/{MODULE_CODE}/imports/{batch['id']}/execute",
        {"version": batch["version"]}, admin)
    facts["batches"].append(batch["id"]); save()
    deadline = time.time() + 35
    while batch["status"] not in {"COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED"} and time.time() < deadline:
        time.sleep(.5)
        batch = api("GET", f"/api/runtime/modules/{MODULE_CODE}/imports/{batch['id']}", token=admin)
    assert batch["status"] in {"COMPLETED", "COMPLETED_WITH_ERRORS"}, batch
    return batch


def delivery(batch_id, event_type, seconds=20):
    deadline = time.time() + seconds
    while time.time() < deadline:
        rows = api("GET", f"/api/flow-bindings/events?moduleId={MODULE_ID}&triggerEvent={event_type}", token=admin)
        found = next((row for row in rows if row["sourceId"] == str(batch_id)), None)
        if found and found["status"] in {"PUBLISHED", "FAILED"}: return found
        time.sleep(.4)
    raise AssertionError((batch_id, event_type, rows))


def instance_count(batch_id):
    return int(sql("SELECT COUNT(*) FROM flow_instance WHERE business_snapshot_json->>'$.sourceType'='IMPORT_BATCH' "
        f"AND business_snapshot_json->>'$.sourceId'='{int(batch_id)}'")[0][0])


stage = sys.argv[1]
if stage == "prepare":
    assert not facts.get("prepared")
    for event in ["IMPORT_COMPLETED", "EXCEPTION"]:
        rows = api("GET", f"/api/flow-bindings?moduleId={MODULE_ID}&triggerEvent={event}", token=admin)
        assert not [row for row in rows["currentTenant"] + rows["inheritedDefaults"] if row["status"] == "ACTIVE"]

    no_binding = execute_import("title\n" + facts["code"] + "_no_binding\n", "no_binding", {"title": "title"})
    no_binding_event = delivery(no_binding["id"], "IMPORT_COMPLETED")
    assert no_binding_event["status"] == "PUBLISHED" and no_binding_event.get("flowInstanceId") is None
    assert instance_count(no_binding["id"]) == 0
    case("event-without-binding-finishes-safely", "导入完成事件没有匹配绑定时可靠确认，不伪造流程实例。",
         batchId=no_binding["id"], outboxId=no_binding_event["id"])

    flow_id = create_flow()
    publish_binding(flow_id, "IMPORT_COMPLETED", {"mode": "ALL", "conditions": []})
    publish_binding(flow_id, "EXCEPTION", {"mode": "ALL", "conditions": [
        {"field": "failedRows", "operator": "GT", "value": 0}]})

    success = execute_import("title\n" + facts["code"] + "_success\n", "success", {"title": "title"})
    success_event = delivery(success["id"], "IMPORT_COMPLETED")
    assert success["status"] == "COMPLETED" and success_event["status"] == "PUBLISHED"
    assert success_event.get("flowInstanceId") and instance_count(success["id"]) == 1
    facts["instances"].append(success_event["flowInstanceId"])
    case("import-completion-starts-current-binding-once", "成功导入保存后按当前启用绑定启动一个流程实例。",
         batchId=success["id"], outboxId=success_event["id"], instanceId=success_event["flowInstanceId"])

    mixed = execute_import("title,score\n" + facts["code"] + "_mixed,1\n,2\n", "mixed",
                           {"title": "title", "score": "score"})
    completed_event = delivery(mixed["id"], "IMPORT_COMPLETED")
    exception_event = delivery(mixed["id"], "EXCEPTION")
    assert mixed["status"] == "COMPLETED_WITH_ERRORS" and mixed["failedRows"] == 1
    assert completed_event.get("flowInstanceId") and exception_event.get("flowInstanceId")
    assert completed_event["flowInstanceId"] != exception_event["flowInstanceId"] and instance_count(mixed["id"]) == 2
    facts["instances"] += [completed_event["flowInstanceId"], exception_event["flowInstanceId"]]
    case("validation-errors-publish-completion-and-exception", "包含预演错误行的导入以部分成功结束，并分别触发导入完成和数据异常流程。",
         batchId=mixed["id"], completionInstance=completed_event["flowInstanceId"],
         exceptionInstance=exception_event["flowInstanceId"])

    payload_hex = sql(f"SELECT HEX(payload_json) FROM core_event_outbox WHERE id={int(exception_event['id'])}")[0][0]
    payload = bytes.fromhex(payload_hex).decode("utf-8")
    assert facts["code"] not in payload and all(secret not in payload.lower() for secret in ["password", "secret", "rawjson"])
    payload_data = json.loads(payload)
    assert payload_data["variables"]["failedRows"] == 1 and payload_data["eventType"] == "EXCEPTION"
    case("outbox-payload-is-safe-metadata", "持久化事件仅包含批次统计和身份引用，不包含原始导入行、密码或密钥。")

    sql(f"UPDATE core_event_outbox SET status='FAILED',published_at=NULL,available_at=NOW(),version=version+1 "
        f"WHERE id={int(success_event['id'])} AND aggregate_type='FLOW_TRIGGER_EVENT'")
    deadline = time.time() + 12
    while time.time() < deadline and delivery(success["id"], "IMPORT_COMPLETED")["status"] != "PUBLISHED": time.sleep(.5)
    assert instance_count(success["id"]) == 1
    case("repeated-delivery-reuses-event-instance", "已投递事件再次进入派发后读取原启动回执，流程实例仍只有一个。")

    sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={int(flow_id)}")
    deferred = execute_import("title\n" + facts["code"] + "_restart\n", "restart", {"title": "title"})
    deferred_event = delivery(deferred["id"], "IMPORT_COMPLETED")
    assert deferred["status"] == "COMPLETED" and deferred_event["status"] == "FAILED"
    assert instance_count(deferred["id"]) == 0
    facts["deferred"] = {"batchId": deferred["id"], "outboxId": deferred_event["id"], "flowId": flow_id}
    facts["prepared"] = True; save()
    case("trigger-failure-does-not-rollback-import", "流程停用导致触发失败时，导入批次仍保持完成，发件箱保留失败原因等待重试。",
         batchId=deferred["id"], outboxId=deferred_event["id"])
    print(json.dumps({"prepare": "PASS", "flowId": flow_id, "deferred": facts["deferred"]}, ensure_ascii=False))

elif stage == "resume":
    assert facts.get("prepared") and not facts.get("resumed")
    deferred = facts["deferred"]
    before = delivery(deferred["batchId"], "IMPORT_COMPLETED")
    assert before["status"] == "FAILED" and instance_count(deferred["batchId"]) == 0
    sql(f"UPDATE flow_definition SET status='ACTIVE',version=version+1 WHERE id={int(deferred['flowId'])}")
    sql(f"UPDATE core_event_outbox SET available_at=NOW(),version=version+1 WHERE id={int(deferred['outboxId'])} AND status='FAILED'")
    deadline = time.time() + 20
    current = before
    while time.time() < deadline:
        current = delivery(deferred["batchId"], "IMPORT_COMPLETED")
        if current["status"] == "PUBLISHED": break
        time.sleep(.5)
    assert current["status"] == "PUBLISHED" and current.get("flowInstanceId")
    assert instance_count(deferred["batchId"]) == 1
    facts["instances"].append(current["flowInstanceId"])
    facts["resumed"] = True; save()
    case("failed-event-survives-restart-and-recovers", "失败事件跨同一后端重启保留；恢复流程后自动重试并只创建一个实例。",
         batchId=deferred["batchId"], outboxId=deferred["outboxId"], instanceId=current["flowInstanceId"])
    print(json.dumps({"resume": "PASS", "instanceId": current["flowInstanceId"]}, ensure_ascii=False))

elif stage == "cleanup":
    assert facts.get("resumed") and not facts.get("cleanup")
    for batch_id in facts["batches"]:
        batch = api("GET", f"/api/runtime/modules/{MODULE_CODE}/imports/{batch_id}", token=admin)
        if batch["status"] in {"COMPLETED", "COMPLETED_WITH_ERRORS"}:
            api("POST", f"/api/runtime/modules/{MODULE_CODE}/imports/{batch_id}/rollback",
                {"reason": "B10.2c.2a 验收完成后精确清理导入记录"}, admin)
    if facts["bindings"]:
        ids = ",".join(str(int(value)) for value in facts["bindings"])
        sql(f"UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE id IN ({ids}) AND module_id={MODULE_ID}")
    if facts["flows"]:
        ids = ",".join(str(int(value)) for value in facts["flows"])
        sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN ({ids}) AND code LIKE 'flow_import_%'")
    for file in facts["files"]:
        api("DELETE", f"/api/files/{file['id']}/references/{file['referenceId']}", token=admin)
        api("DELETE", f"/api/files/{file['id']}", token=admin)
    assert not sql("SELECT id FROM flow_instance WHERE id IN (" + ",".join(str(int(value)) for value in facts["instances"])
        + ") AND status NOT IN ('COMPLETED','TERMINATED')")
    facts["cleanup"] = {"bindingsDisabled": True, "flowsDisabled": True, "importsRolledBack": True,
        "filesDeleted": True, "unfinishedInstances": 0}
    facts["status"] = "passed"; save()
    print(json.dumps({"cleanup": "PASS", "cases": len(facts["cases"])}, ensure_ascii=False))

else:
    raise SystemExit("stage must be prepare, resume or cleanup")
