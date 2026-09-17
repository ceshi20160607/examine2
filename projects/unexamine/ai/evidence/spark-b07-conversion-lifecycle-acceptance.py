"""本次转化关系生命周期验收；复用当前服务，保存业务事实，不保存凭证或请求键。"""
import hashlib
import json
import runpy
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
phase = sys.argv[1]
sys.argv = ["conversion-acceptance", "inspect"]
shared = runpy.run_path(str(AREA / "spark-b07-conversion-acceptance.py"))
api, call, rows, decode, save = [shared[n] for n in ["api", "call", "rows", "decode", "save"]]
SOURCE, TARGET, TOKEN, ENTRY = [shared[n] for n in ["SOURCE", "TARGET", "TOKEN", "ENTRY"]]
fixture_path = AREA / "spark-b07-conversion-lifecycle-fixtures.json"
headers = lambda: {"Prefer": "return=action", "Idempotency-Key": str(uuid.uuid4())}


def path(record_id, suffix="", module=SOURCE):
    return f"/api/runtime/modules/{module}/records/{record_id}" + suffix


def marker(conversion_id):
    item = rows("SELECT status,version,finished_at,created_at,HEX(mapping_snapshot_json),idempotency_key "
                "FROM biz_record_conversion WHERE id=" + str(conversion_id))[0]
    return {"status": item[0], "version": int(item[1]), "finished": item[2], "created": item[3],
            "mapping_hash": hashlib.sha256(bytes.fromhex(item[4])).hexdigest(),
            "permanent_key_hash": hashlib.sha256(item[5].encode()).hexdigest()}


def new_conversion(label, title, customer):
    config = api("GET", f"/api/runtime/modules/{SOURCE}/configuration")
    fields = {f["name"]: f["code"] for f in config["configuration"]["fields"]}
    # 读数零不匹配既有 CONVERT 审批条件，便于独立验证解除关系的审批触发。
    record = api("POST", path("").rstrip("/"), {"title": title, "status": "ACTIVE",
        "ownerMemberId": ENTRY["systemMemberId"], "departmentId": 3, "participantMemberIds": [],
        "fields": {fields["巡检项目"]: title, fields["巡检备注"]: customer,
                   fields["仪表读数"]: 0, fields["正常运行"]: True}})
    result = api("POST", path(record["id"], "/convert"), {"version": record["version"],
                 "targetModuleCode": TARGET}, headers())
    assert result["mode"] == "DIRECT" and result["value"]
    conversion_id, target = result["value"]["conversionId"], result["value"]["targetRecord"]
    initial = marker(conversion_id)
    assert initial["status"] == "SUCCEEDED" and initial["finished"] >= initial["created"]
    fixture = {"source_id": record["id"], "target_id": target["id"], "conversion_id": conversion_id,
               "conversion_receipt": result["idempotencyRecordId"], "source_before": record,
               "target_before": target, "marker_before": initial}
    fixtures[label] = fixture
    save(fixture_path.name, fixtures)
    return fixture


def stored_action(action_id):
    stored = decode(rows("SELECT HEX(response_json) FROM core_idempotency_record WHERE id=" + str(action_id))[0][0])
    action = decode(rows("SELECT HEX(payload_json) FROM core_event_outbox WHERE id=" + str(stored["outboxId"]))[0][0])["action"]
    return stored, action


def replay(action_id, suffix):
    stored, action = stored_action(action_id)
    result = api("POST", path(action["recordId"], suffix), action["payload"],
                 {"Prefer": "return=action", "Idempotency-Key": stored["idempotencyKey"]})
    assert result["replayed"] and result["idempotencyRecordId"] == action_id
    return result


def unlink_body(fixture):
    return {"conversionId": fixture["conversion_id"], "version": fixture["source_before"]["version"],
            "conversionVersion": fixture["marker_before"]["version"], "reason": "本地验收：保留目标和历史，解除转化关系。"}


def assert_preserved(fixture, unlinked=True, source_unchanged=True):
    current = marker(fixture["conversion_id"])
    initial = fixture["marker_before"]
    assert current == {**initial, "status": "UNLINKED" if unlinked else "SUCCEEDED",
                       "version": initial["version"] + (1 if unlinked else 0)}, current
    for module, key in [(SOURCE, "source"), (TARGET, "target")]:
        record = api("GET", path(fixture[key + "_id"], module=module))
        if key == "source" and not source_unchanged:
            continue
        before = fixture[key + "_before"]
        for field in ["id", "title", "version", "deleted", "fields", "ownerMemberId", "departmentId", "participantMemberIds"]:
            assert record[field] == before[field], (key, field)
    assert rows("SELECT target_record_id FROM biz_record_conversion_result WHERE conversion_id="
                + str(fixture["conversion_id"])) == [[str(fixture["target_id"])]]
    counts = rows("SELECT event_code,COUNT(*) FROM audit_event WHERE system_id=19 AND tenant_id=21 AND "
                 "object_type='BUSINESS_RECORD' AND object_id='" + str(fixture["source_id"]) + "' AND result_code='SUCCESS' "
                 "AND event_code IN('BUSINESS_RECORD_CONVERTED','BUSINESS_RECORD_CONVERSION_UNLINKED') "
                 "GROUP BY event_code ORDER BY event_code")
    expected = [["BUSINESS_RECORD_CONVERSION_UNLINKED", "1"], ["BUSINESS_RECORD_CONVERTED", "1"]] if unlinked else [["BUSINESS_RECORD_CONVERTED", "1"]]
    assert counts == expected, counts
    return current


def approve(result, expected):
    instance = api("GET", f"/api/flow-runtime/instances/{result['flowInstanceId']}")
    task = next(t for t in instance["tasks"] if t["status"] in {"PENDING", "APPROVE"} and t["assigneeTenantMemberId"] == ENTRY["tenantMemberId"])
    if task["status"] == "PENDING":
        api("POST", f"/api/flow-runtime/tasks/{task['id']}/actions", {"actionCode": "APPROVE",
            "comment": "本地验收：验证解除关系审批恢复与版本冲突。", "idempotencyKey": str(uuid.uuid4())})
    for _ in range(35):
        delivery = api("GET", f"/api/runtime/modules/{SOURCE}/records/action-results/{result['idempotencyRecordId']}")
        if delivery["businessStatus"] == expected:
            return {"flow_id": result["flowInstanceId"], "task_id": task["id"], "delivery": delivery}
        time.sleep(.3)
    raise AssertionError(delivery)


fixtures = json.loads(fixture_path.read_text(encoding="utf-8")) if fixture_path.exists() else {}
if phase == "prepare":
    assert "main" not in fixtures, "当前验收记录已存在，禁止重复生成"
    assert rows("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1") == [["41", "1"]]
    old = rows("SELECT c.id,c.status,c.version,c.created_at,c.finished_at,HEX(a.detail_json) FROM biz_record_conversion c "
        "JOIN audit_event a ON a.object_id=CAST(c.id AS CHAR) AND a.object_type='BUSINESS_RECORD_CONVERSION' "
        "AND a.event_code='CONVERSION_CLOCK_REPAIRED' AND a.request_id='migration-v41-conversion-clock' ORDER BY c.id")
    assert [int(item[0]) for item in old] == [2, 3, 5, 6]
    for item in old:
        assert item[1:3] == ["SUCCEEDED", "1"] and item[4] >= item[3]
        assert decode(item[5])["beforeTime"] < decode(item[5])["afterTime"]
    save("spark-b07-conversion-clock-repair.json", {"checked_at": datetime.now().astimezone().isoformat(),
        "schema_version": 41, "repaired": [{"id": int(r[0]), "version": int(r[2]), "created": r[3],
                                            "finished": r[4], "audit": decode(r[5])} for r in old]})
    fixture = new_conversion("main", "转化关系生命周期验收", "生命周期验收客户")
    print(json.dumps({k: fixture[k] for k in ["source_id", "target_id", "conversion_id"]}))
elif phase == "verify-ui":
    f = fixtures["main"]
    action_id = int(rows("SELECT aggregate_id FROM core_event_outbox WHERE aggregate_type='BUSINESS_ACTION' "
        "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.action.actionCode'))='CONVERSION_UNLINK' "
        "AND JSON_EXTRACT(payload_json,'$.action.recordId')=" + str(f["source_id"]) + " ORDER BY id DESC LIMIT 1")[0][0])
    replayed = replay(action_id, "/conversions/unlink")
    assert replayed["mode"] == "DIRECT" and replayed["value"]["relationStatus"] == "INVALID"
    result = assert_preserved(f)
    links = []
    for module, record_id in [(SOURCE, f["source_id"]), (TARGET, f["target_id"])]:
        link = api("GET", path(record_id, "/conversions", module))["links"][0]
        assert link["relationStatus"] == "INVALID" and not link["canUnlink"] and link["conversionVersion"] == 2
        links.append(link)
    failures = []
    cases = [
        ("stale_relation_version", path(f["source_id"], "/conversions/unlink"), unlink_body(f), 409, "CONVERSION_VERSION_CONFLICT"),
        ("already_unlinked", path(f["source_id"], "/conversions/unlink"), {**unlink_body(f), "conversionVersion": 2}, 409, "CONVERSION_ALREADY_UNLINKED"),
        ("wrong_source", path(77, "/conversions/unlink"), unlink_body(f), 404, "CONVERSION_NOT_FOUND"),
        ("wrong_module", path(f["source_id"], "/conversions/unlink", TARGET), unlink_body(f), 404, "RECORD_NOT_FOUND"),
        ("blank_reason", path(f["source_id"], "/conversions/unlink"), {**unlink_body(f), "reason": ""}, 400, None),
        ("no_reconvert", path(f["source_id"], "/convert"), {"version": 0, "targetModuleCode": TARGET}, 409, "RECORD_ALREADY_CONVERTED")]
    for label, url, body, expected, code in cases:
        status, response = call("POST", url, body, TOKEN, headers())
        assert status == expected and (code is None or response["code"] == code), (label, status, response)
        failures.append({"case": label, "http": status, "code": response["code"]})
    status, response = call("POST", path(f["source_id"], "/conversions/unlink"), unlink_body(f), headers=headers())
    assert status == 401
    failures.append({"case": "no_auth", "http": status, "code": response["code"]})
    preview = api("POST", path(f["source_id"], "/conversion-preview"), {"version": 0, "targetModuleCode": TARGET})
    assert preview["alreadyConverted"] and not preview["executable"]
    old = replay(f["conversion_receipt"], "/convert")
    assert old["value"]["targetRecord"]["id"] == f["target_id"]
    timeline = api("GET", path(f["source_id"], "/timeline"))
    assert "本地验收：解除关系，保留目标和转化历史。" in json.dumps(timeline, ensure_ascii=False)
    assert_preserved(f)
    save("spark-b07-conversion-lifecycle-main.json", {"checked_at": datetime.now().astimezone().isoformat(),
        "receipt_id": action_id, "original_key_replay": True, "source_id": f["source_id"], "target_id": f["target_id"],
        "marker": result, "links": links, "failures": failures, "source_target_body_unchanged": True,
        "history_retained": True, "permanent_no_reconvert": True, "conversion_original_replay": True})
    print("UI unlink readback, exact replay, immutable bodies/history/key and 7 failure boundaries passed.")
elif phase == "race":
    assert "race_source_id" not in fixtures, "当前并发验证已有记录，禁止重复生成"
    fields = {f["name"]: f["code"] for f in api("GET", f"/api/runtime/modules/{SOURCE}/configuration")["configuration"]["fields"]}
    source = api("POST", path("").rstrip("/"), {"title": "来源锁并发转化验收", "status": "ACTIVE",
        "ownerMemberId": ENTRY["systemMemberId"], "departmentId": 3, "participantMemberIds": [],
        "fields": {fields["巡检项目"]: "来源锁并发转化验收", fields["巡检备注"]: "来源锁验收客户",
                   fields["仪表读数"]: 0, fields["正常运行"]: True}})
    fixtures["race_source_id"] = source["id"]
    save(fixture_path.name, fixtures)
    with ThreadPoolExecutor(max_workers=2) as pool:
        attempts = list(pool.map(lambda _: call("POST", path(source["id"], "/convert"),
            {"version": 0, "targetModuleCode": TARGET}, TOKEN, headers()), range(2)))
    assert sorted(status for status, response in attempts) == [200, 409]
    success = next(response["data"] for status, response in attempts if status == 200)
    failure = next(response for status, response in attempts if status == 409)
    assert failure["code"] == "RECORD_ALREADY_CONVERTED"
    assert rows("SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id=" + str(source["id"])) == [["1"]]
    assert rows("SELECT COUNT(*) FROM biz_record_conversion_result WHERE conversion_id=" + str(success["value"]["conversionId"])) == [["1"]]
    current = marker(success["value"]["conversionId"])
    assert current["finished"] >= current["created"]
    assert api("GET", path(source["id"]))["version"] == 0
    save("spark-b07-conversion-lifecycle-race.json", {"source_id": source["id"],
        "target_id": success["value"]["targetRecord"]["id"], "statuses": [status for status, response in attempts],
        "loser_code": failure["code"], "one_marker_and_result": True, "source_version": 0, "marker": current})
    print("Current source lock: concurrent conversion creates one target/marker/result, loser409, business clock consistent.")
elif phase == "approval":
    fields = api("GET", f"/api/runtime/modules/{SOURCE}/configuration")["configuration"]["fields"]
    number = next(field["code"] for field in fields if field["name"] == "仪表读数")
    if "binding_id" not in fixtures:
        # 恢复脚本时复用已发布的本次触发绑定，不重建审批或测试记录。
        existing = rows("SELECT id FROM flow_trigger_binding WHERE system_id=19 AND owner_tenant_id=21 AND module_id=30 AND trigger_event='CONVERSION_UNLINK' AND status='ACTIVE' ORDER BY id DESC LIMIT 1")
        if existing:
            fixtures["binding_id"] = int(existing[0][0])
        else:
            binding = api("POST", "/api/flow-bindings/publish", {"moduleId": 30, "triggerEvent": "CONVERSION_UNLINK",
                "flowId": 10, "executionMode": "AFTER_APPROVAL", "priorityOrder": 0, "replaceExisting": True,
                "mutuallyExclusive": False, "condition": {"mode": "ALL", "conditions": [{"field": number, "operator": "GT", "value": -1}]}})
            fixtures["binding_id"] = binding["id"]
        save(fixture_path.name, fixtures)
    results = {"binding_id": fixtures["binding_id"], "trigger": "CONVERSION_UNLINK", "cases": []}
    for label, title, customer, expected in [("approval", "转化解除审批验收", "解除审批验收客户", "EXECUTED"),
        ("conflict", "转化解除版本冲突验收", "解除冲突验收客户", "CONFLICT")]:
        f = fixtures.get(label) or new_conversion(label, title, customer)
        if "unlink_receipt" not in f:
            pending = api("POST", path(f["source_id"], "/conversions/unlink"), unlink_body(f), headers())
            assert pending["mode"] == "AFTER_APPROVAL" and pending["status"] == "PENDING_APPROVAL" and pending["value"] is None
            assert_preserved(f, unlinked=False)
            f["unlink_receipt"] = pending["idempotencyRecordId"]
            save(fixture_path.name, fixtures)
        else:
            pending, _ = stored_action(f["unlink_receipt"])
        if label == "conflict":
            before = api("GET", path(f["source_id"]))
            values = {**before["fields"], next(field["code"] for field in fields if field["name"] == "巡检备注"): "审批期间编辑后的来源内容"}
            changed = api("PUT", path(f["source_id"]), {"title": before["title"], "status": before["status"],
                "ownerMemberId": before["ownerMemberId"], "departmentId": before["departmentId"],
                "participantMemberIds": before["participantMemberIds"], "fields": values, "version": before["version"]}, headers())
            assert changed["value"]["version"] == 1
        outcome = approve(pending, expected)
        delivery = outcome["delivery"]
        assert delivery["recordId"] == f["source_id"]
        replayed = replay(pending["idempotencyRecordId"], "/conversions/unlink")
        if label == "approval":
            assert replayed["value"]["relationStatus"] == "INVALID"
            assert_preserved(f)
        else:
            assert delivery["errorCode"] == "RECORD_VERSION_CONFLICT"
            assert_preserved(f, unlinked=False, source_unchanged=False)
            assert api("GET", path(f["source_id"]))["version"] == 1
        results["cases"].append({"case": label, "source_id": f["source_id"], "target_id": f["target_id"],
            "receipt_id": pending["idempotencyRecordId"], "no_effect_before_approval": True,
            "original_key_replay": True, **outcome})
        save("spark-b07-conversion-lifecycle-approval.json", results)
    print("Independent unlink approval executes once; edited-source conflict preserves active relation and target.")
else:
    raise ValueError(phase)
