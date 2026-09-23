"""B10.2c.2b 周期 Flow 的真实时间、替换、重启、错过策略与清理验收。"""
import ast
import json
import re
import secrets
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))

FACTS = AREA / "flow-schedule-facts.json"
facts = json.loads(FACTS.read_text(encoding="utf-8")) if FACTS.exists() else {
    "step": "B10.2c.2b", "status": "running", "code": "flow_schedule_" + secrets.token_hex(5),
    "flows": [], "bindings": [], "events": [], "instances": [], "cases": []}
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
    flow = api("POST", "/api/flows", {"code": facts["code"], "name": "周期可靠触发验收",
        "description": "B10.2c.2b 周期、替换与重启验收夹具"}, admin)
    facts["flows"].append(flow["id"]); save()
    api("PUT", f"/api/flows/{flow['id']}/draft", {"expectedVersion": flow["version"],
        "nodes": [node("start", "START", "开始", 0), node("end", "END", "结束", 240)],
        "edges": [{"edgeKey": "e1", "sourceNodeKey": "start", "targetNodeKey": "end",
                   "priorityOrder": 0, "config": {}}]}, admin)
    check = api("GET", f"/api/flows/{flow['id']}/publication-check", token=admin)
    assert check["valid"], check["issues"]
    api("POST", f"/api/flows/{flow['id']}/publish", {"expectedDraftRevision": check["draftRevision"],
        "changeSummary": "周期可靠触发验收", "simulationVariables": {}}, admin)
    return flow["id"]


def publish_binding(flow_id, schedule, replace=False):
    binding = api("POST", "/api/flow-bindings/publish", {"moduleId": MODULE_ID,
        "triggerEvent": "SCHEDULED", "flowId": flow_id, "executionMode": "FLOW_ONLY",
        "priorityOrder": 0, "condition": {"mode": "ALL", "conditions": []},
        "mutuallyExclusive": True, "replaceExisting": replace, "schedule": schedule}, admin)
    facts["bindings"].append(binding["id"]); save()
    return binding


def event_rows():
    return api("GET", f"/api/flow-bindings/events?moduleId={MODULE_ID}&triggerEvent=SCHEDULED", token=admin)


def event(event_id, statuses=("PENDING", "FAILED", "PUBLISHED"), seconds=20):
    deadline = time.time() + seconds
    rows = []
    while time.time() < deadline:
        rows = event_rows()
        found = next((row for row in rows if row["id"] == event_id), None)
        if found and found["status"] in statuses:
            return found
        time.sleep(.35)
    raise AssertionError((event_id, statuses, rows))


def new_pending(binding_id, excluded=(), seconds=20):
    deadline = time.time() + seconds
    rows = []
    while time.time() < deadline:
        rows = sql("SELECT id,aggregate_id,HEX(payload_json),available_at FROM core_event_outbox "
            f"WHERE aggregate_type='FLOW_TRIGGER_EVENT' AND event_type='SCHEDULED' AND status='PENDING' "
            f"AND aggregate_id LIKE 'FLOW_SCHEDULE:{int(binding_id)}:%' ORDER BY id DESC")
        found = next((row for row in rows if int(row[0]) not in excluded), None)
        if found:
            payload = json.loads(bytes.fromhex(found[2]).decode("utf-8"))
            return {"id": int(found[0]), "aggregateId": found[1], "payload": payload,
                    "availableAt": found[3]}
        time.sleep(.35)
    raise AssertionError((binding_id, rows))


def force_due(event_id):
    sql(f"UPDATE core_event_outbox SET available_at=NOW(),version=version+1 WHERE id={int(event_id)} "
        "AND aggregate_type='FLOW_TRIGGER_EVENT' AND status IN ('PENDING','FAILED')")
    return event(event_id, ("PUBLISHED",), 20)


def mark_missed(event_id):
    missed_time = (datetime.now().astimezone() - timedelta(minutes=10)).replace(tzinfo=None, microsecond=0)
    missed = missed_time.isoformat()
    payload = json.loads(bytes.fromhex(sql(
        f"SELECT HEX(payload_json) FROM core_event_outbox WHERE id={int(event_id)}")[0][0]).decode("utf-8"))
    aggregate_id = f"FLOW_SCHEDULE:{int(payload['scheduleBindingId'])}:{missed_time.strftime('%Y%m%dT%H%M%S')}"
    safe = missed.replace("'", "''")
    sql("UPDATE core_event_outbox SET payload_json=JSON_SET(payload_json,'$.sourceId',"
        f"'{safe}','$.occurredTime','{safe}','$.variables.scheduledTime','{safe}'),"
        f"aggregate_id='{aggregate_id}',available_at=NOW(),version=version+1 "
        f"WHERE id={int(event_id)} AND status='PENDING'")
    return missed


def instance_count(source_id):
    safe = source_id.replace("'", "''")
    return int(sql("SELECT COUNT(*) FROM flow_instance WHERE business_snapshot_json->>'$.sourceType'='FLOW_SCHEDULE' "
        f"AND business_snapshot_json->>'$.sourceId'='{safe}'")[0][0])


stage = sys.argv[1]
if stage == "prepare":
    assert not facts.get("prepared")
    active = [row for row in api("GET", f"/api/flow-bindings?moduleId={MODULE_ID}&triggerEvent=SCHEDULED", token=admin)["currentTenant"]
              if row["status"] == "ACTIVE"]
    assert not active, active
    flow_id = create_flow()
    first = publish_binding(flow_id, {"scheduleMode": "INTERVAL", "intervalValue": 1,
        "intervalUnit": "MINUTE", "misfirePolicy": "FIRE_ONCE"})
    assert first["schedule"] == {"schemaVersion": 1, "scheduleMode": "INTERVAL", "intervalValue": 1,
        "intervalUnit": "MINUTE", "dailyTime": None, "weekDay": None,
        "misfirePolicy": "FIRE_ONCE", "timezone": "Asia/Shanghai"}
    first_event = new_pending(first["id"])
    facts["events"].append(first_event["id"]); save()
    page = event(first_event["id"], ("PENDING",))
    assert page["availableTime"].startswith(first_event["payload"]["sourceId"])
    time.sleep(2)
    assert event(first_event["id"], ("PENDING",))["status"] == "PENDING"
    assert instance_count(first_event["payload"]["sourceId"]) == 0
    case("structured-plan-persists-without-early-run", "一分钟间隔配置被服务端冻结为上海时区，页面读取下一次时间且未提前执行。",
         bindingId=first["id"], outboxId=first_event["id"], nextTime=page["availableTime"])

    delivered = force_due(first_event["id"])
    assert delivered.get("flowInstanceId") and instance_count(first_event["payload"]["sourceId"]) == 1
    facts["instances"].append(delivered["flowInstanceId"]); save()
    sql(f"UPDATE core_event_outbox SET status='FAILED',published_at=NULL,available_at=NOW(),version=version+1 WHERE id={first_event['id']}")
    repeated = event(first_event["id"], ("PUBLISHED",), 20)
    assert repeated["flowInstanceId"] == delivered["flowInstanceId"]
    assert instance_count(first_event["payload"]["sourceId"]) == 1
    case("stable-slot-delivers-at-most-once", "同一周期时隙重复投递后复用原实例，流程实例仍只有一个。",
         instanceId=delivered["flowInstanceId"])

    old_next = new_pending(first["id"], {first_event["id"]})
    second = publish_binding(flow_id, {"scheduleMode": "INTERVAL", "intervalValue": 1,
        "intervalUnit": "DAY", "misfirePolicy": "FIRE_ONCE"}, True)
    second_event = new_pending(second["id"])
    facts["events"] += [old_next["id"], second_event["id"]]; save()
    before_instances = len(facts["instances"])
    stale = force_due(old_next["id"])
    stale_payload = json.loads(bytes.fromhex(sql(f"SELECT HEX(payload_json) FROM core_event_outbox WHERE id={old_next['id']}")[0][0]).decode("utf-8"))
    assert stale.get("flowInstanceId") is None and stale_payload["deliveryResult"] == "STALE_SCHEDULE"
    assert len(facts["instances"]) == before_instances
    case("replacement-invalidates-old-plan", "替换周期后旧计划到期只记录为失效计划，不启动新流程。",
         oldBindingId=first["id"], newBindingId=second["id"], staleOutboxId=old_next["id"])

    page_before = event(second_event["id"], ("PENDING",))
    facts["restart"] = {"flowId": flow_id, "bindingId": second["id"], "outboxId": second_event["id"],
                        "nextTime": page_before["availableTime"], "sourceId": second_event["payload"]["sourceId"]}
    facts["prepared"] = True; save()
    print(json.dumps({"prepare": "PASS", "restart": facts["restart"]}, ensure_ascii=False))

elif stage == "resume":
    assert facts.get("prepared") and not facts.get("resumed")
    restart = facts["restart"]
    retained = event(restart["outboxId"], ("PENDING",))
    assert retained["availableTime"] == restart["nextTime"] and instance_count(restart["sourceId"]) == 0
    case("future-plan-survives-restart", "同一后端重启后未来计划保持原时间且没有提前启动流程。",
         outboxId=restart["outboxId"], nextTime=retained["availableTime"])

    missed_fire = mark_missed(restart["outboxId"])
    fired = event(restart["outboxId"], ("PUBLISHED",), 20)
    assert fired.get("flowInstanceId") and instance_count(missed_fire) == 1
    facts["instances"].append(fired["flowInstanceId"]); save()
    case("missed-fire-once-recovers-once", "中断期间错过的补执行配置恢复后只启动一个流程实例。",
         outboxId=restart["outboxId"], instanceId=fired["flowInstanceId"])

    fire_next = new_pending(restart["bindingId"], {restart["outboxId"]})
    third = publish_binding(restart["flowId"], {"scheduleMode": "WEEKLY", "dailyTime": "09:15",
        "weekDay": 1, "misfirePolicy": "SKIP"}, True)
    third_event = new_pending(third["id"])
    facts["events"] += [fire_next["id"], third_event["id"]]; save()
    missed_skip = mark_missed(third_event["id"])
    skipped = event(third_event["id"], ("PUBLISHED",), 20)
    skipped_payload = json.loads(bytes.fromhex(sql(f"SELECT HEX(payload_json) FROM core_event_outbox WHERE id={third_event['id']}")[0][0]).decode("utf-8"))
    assert skipped.get("flowInstanceId") is None and instance_count(missed_skip) == 0
    assert skipped_payload["deliveryResult"] == "MISFIRE_SKIPPED"
    next_skip = new_pending(third["id"], {third_event["id"]})
    assert next_skip["payload"]["sourceId"] > missed_skip
    facts["events"].append(next_skip["id"]); facts["finalBindingId"] = third["id"]
    facts["resumed"] = True; save()
    case("missed-skip-advances-without-instance", "跳过策略不补建流程实例，并继续保存下一次未来计划。",
         skippedOutboxId=third_event["id"], nextOutboxId=next_skip["id"])
    print(json.dumps({"resume": "PASS", "cases": len(facts["cases"])}, ensure_ascii=False))

elif stage == "cleanup":
    assert facts.get("resumed") and not facts.get("cleanup")
    binding_ids = ",".join(str(int(value)) for value in facts["bindings"])
    sql(f"UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE id IN ({binding_ids}) "
        f"AND module_id={MODULE_ID} AND trigger_event='SCHEDULED'")
    event_ids = ",".join(str(int(value)) for value in facts["events"])
    sql(f"UPDATE core_event_outbox SET available_at=NOW(),version=version+1 WHERE id IN ({event_ids}) "
        "AND status IN ('PENDING','FAILED')")
    deadline = time.time() + 20
    pending = [["pending"]]
    while time.time() < deadline:
        pending = sql(f"SELECT id FROM core_event_outbox WHERE id IN ({event_ids}) AND status IN ('PENDING','FAILED')")
        if not pending: break
        time.sleep(.4)
    assert not pending, pending
    flow_ids = ",".join(str(int(value)) for value in facts["flows"])
    sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN ({flow_ids}) "
        "AND code LIKE 'flow_schedule_%'")
    assert not sql("SELECT id FROM flow_instance WHERE id IN (" + ",".join(str(int(value)) for value in facts["instances"])
        + ") AND status NOT IN ('COMPLETED','TERMINATED')")
    facts["cleanup"] = {"bindingsDisabled": True, "eventsSettled": True,
        "flowsDisabled": True, "unfinishedInstances": 0}
    facts["status"] = "passed"; save()
    print(json.dumps({"cleanup": "PASS", "cases": len(facts["cases"])}, ensure_ascii=False))

else:
    raise SystemExit("stage must be prepare, resume or cleanup")
