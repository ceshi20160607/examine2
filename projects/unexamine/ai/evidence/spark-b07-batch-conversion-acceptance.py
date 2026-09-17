"""本地批量转化真实验收；逐条复用现有公共 API，保存业务事实，不保存凭证和请求键。"""
import json
import runpy
import sys
import uuid
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
phase = sys.argv[1]
sys.argv = ["helper", "inspect"]
s = runpy.run_path(str(AREA / "spark-b07-conversion-acceptance.py"))
api, call, rows, decode, save = [s[name] for name in ["api", "call", "rows", "decode", "save"]]
SOURCE, TARGET, ENTRY, TOKEN = [s[name] for name in ["SOURCE", "TARGET", "ENTRY", "TOKEN"]]
fixture_path = AREA / "spark-b07-batch-conversion-fixtures.json"
fixtures = json.loads(fixture_path.read_text(encoding="utf-8")) if fixture_path.exists() else {}
fields = {field["name"]: field["code"] for field in api("GET", f"/api/runtime/modules/{SOURCE}/configuration")["configuration"]["fields"]}


def path(record_id, suffix=""):
    return f"/api/runtime/modules/{SOURCE}/records/{record_id}" + suffix


def edit(label, values):
    before = api("GET", path(fixtures[label]["id"]))
    result = api("PUT", path(before["id"]), {"title": before["title"], "status": before["status"],
        "ownerMemberId": before["ownerMemberId"], "departmentId": before["departmentId"],
        "participantMemberIds": before["participantMemberIds"], "version": before["version"],
        "fields": {**before["fields"], **values}}, {"Prefer": "return=action", "Idempotency-Key": str(uuid.uuid4())})
    assert result["mode"] == "DIRECT" and result["value"]["version"] == before["version"] + 1
    return result["value"]


def latest_receipt(record_id):
    result = rows("SELECT aggregate_id FROM core_event_outbox WHERE aggregate_type='BUSINESS_ACTION' "
        "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.action.actionCode'))='CONVERT' "
        "AND JSON_EXTRACT(payload_json,'$.action.recordId')=" + str(record_id) + " ORDER BY id DESC LIMIT 1")
    return int(result[0][0]) if result else None


def marker(record_id):
    return rows("SELECT c.id,c.status,c.version,r.target_record_id,c.finished_at>=c.created_at FROM biz_record_conversion c "
        "JOIN biz_record_conversion_result r ON r.conversion_id=c.id WHERE c.source_record_id=" + str(record_id))


if phase == "prepare":
    assert not fixtures, "本次批量验收已创建，禁止重复生成"
    for label, title, customer, number in [("main", "批量转化验收·正常", "批量正常客户", 0),
        ("missing", "批量转化验收·缺必填", None, 0), ("stale", "批量转化验收·版本变化", "批量版本客户", 0),
        ("approval", "批量转化验收·待审批", "批量审批客户", 2)]:
        values = {fields["巡检项目"]: title, fields["仪表读数"]: number, fields["正常运行"]: True}
        if customer is not None:
            values[fields["巡检备注"]] = customer
        record = api("POST", f"/api/runtime/modules/{SOURCE}/records", {"title": title, "status": "ACTIVE",
            "ownerMemberId": ENTRY["systemMemberId"], "departmentId": 3, "participantMemberIds": [], "fields": values})
        fixtures[label] = record
    save(fixture_path.name, fixtures)
    print(json.dumps({label: value["id"] for label, value in fixtures.items()}))
elif phase == "stale":
    assert api("GET", path(fixtures["stale"]["id"]))["version"] == 0
    current = edit("stale", {fields["巡检备注"]: "选择之后编辑的批量版本客户"})
    save("spark-b07-batch-conversion-stale.json", {"id": current["id"], "initial_selected_version": 0,
        "current_version": current["version"], "fields": current["fields"]})
    print("Selected-source version changed once; UI selected snapshot must remain0.")
elif phase == "first":
    assert len(marker(fixtures["main"]["id"])) == 1
    for label in ["missing", "stale", "approval"]:
        assert marker(fixtures[label]["id"]) == [], label
    pending_id = latest_receipt(fixtures["approval"]["id"])
    stored = decode(rows("SELECT HEX(response_json) FROM core_idempotency_record WHERE id=" + str(pending_id))[0][0])
    assert stored["status"] == "PENDING_APPROVAL" and stored["value"] is None
    save("spark-b07-batch-conversion-first.json", {"checked_at": datetime.now().astimezone().isoformat(),
        "one_direct_target": marker(fixtures["main"]["id"]), "failed_without_marker": [fixtures[k]["id"] for k in ["missing", "stale"]],
        "pending_without_target": {"source_id": fixtures["approval"]["id"], "receipt_id": pending_id,
                                   "flow_id": stored["flowInstanceId"]}, "receipt_ids": {k: latest_receipt(v["id"]) for k, v in fixtures.items()}})
    print("First batch: one DIRECT target, two failures without markers, one pending approval without target.")
elif phase == "correct":
    assert not (AREA / "spark-b07-batch-conversion-correction.json").exists(), "本次修正已完成，不重复编辑来源"
    current = edit("missing", {fields["巡检备注"]: "修正后的批量必填客户"})
    save("spark-b07-batch-conversion-correction.json", {"source_id": current["id"], "version": current["version"], "fields": current["fields"]})
    print("Failed required source corrected; source version1, no automatic conversion.")
elif phase == "final":
    assert not (AREA / "spark-b07-batch-conversion-final.json").exists(), "本次审批及最终验收已完成，不重复批准"
    first = json.loads((AREA / "spark-b07-batch-conversion-first.json").read_text(encoding="utf-8"))
    receipt_ids = {k: latest_receipt(v["id"]) for k, v in fixtures.items()}
    for label in ["main", "approval"]:
        assert receipt_ids[label] == first["receipt_ids"][label], "成功或待审批项被重复提交"
    for label in ["main", "missing", "stale"]:
        result = marker(fixtures[label]["id"])
        assert len(result) == 1 and result[0][1:3] == ["SUCCEEDED", "1"] and result[0][4] == "1", (label, result)
    pending_id = receipt_ids["approval"]
    pending = decode(rows("SELECT HEX(response_json) FROM core_idempotency_record WHERE id=" + str(pending_id))[0][0])
    assert marker(fixtures["approval"]["id"]) == []
    approved = s["approve"](pending)
    replay = s["replay"](pending_id)
    assert len(marker(fixtures["approval"]["id"])) == 1
    outcomes = {}
    for label, source in fixtures.items():
        result = marker(source["id"])[0]
        target_id = int(result[3])
        current = api("GET", path(source["id"]))
        target = api("GET", f"/api/runtime/modules/{TARGET}/records/{target_id}")
        assert current["version"] == (1 if label in {"missing", "stale"} else 0)
        assert target["fields"]["field_20a69d1ce3"] == current["fields"][fields["巡检备注"]]
        assert target["fields"]["field_6690d5a5db"] == current["fields"][fields["巡检项目"]]
        assert rows("SELECT COUNT(*) FROM audit_event WHERE system_id=19 AND tenant_id=21 AND object_type='BUSINESS_RECORD' "
            "AND event_code='BUSINESS_RECORD_CONVERTED' AND result_code='SUCCESS' AND object_id='" + str(source["id"]) + "'") == [["1"]]
        original = s["replay"](receipt_ids[label])
        assert original["target_record_id"] == target_id
        status, duplicate = s["convert"](source["id"], current["version"])
        assert status == 409 and duplicate["code"] == "RECORD_ALREADY_CONVERTED"
        outcomes[label] = {"source_id": source["id"], "source_version": current["version"], "target_id": target_id,
            "receipt_id": receipt_ids[label], "marker": result, "target_fields": target["fields"],
            "original_key_replay": True, "fresh_duplicate_http": status, "one_success_audit": True}
    versions = {int(i): decode(v) for i, v in rows("SELECT id,HEX(snapshot_json) FROM cfg_module_version WHERE id IN(45,(SELECT current_version_id FROM cfg_module_publication WHERE module_id=30))")}
    published = max(versions)
    assert versions[45]["fields"] == versions[published]["fields"] and versions[45]["pages"] == versions[published]["pages"]
    assert next(a for a in versions[published]["actions"] if a["code"] == "CONVERT")["location"] == "BATCH"
    save("spark-b07-batch-conversion-final.json", {"checked_at": datetime.now().astimezone().isoformat(),
        "outcomes": outcomes, "approval": approved, "approval_replay": replay, "only_failed_retried": True,
        "published_version_id": published, "fields_pages_preserved": True,
        "limits": "B07.3b.1 only; complex mapping families, remaining modes/full permissions/sharing/channels remain required."})
    print("Failed-only retry + approval/current receipt replay + DB atomic/field/unique/audit readback passed.")
else:
    raise ValueError(phase)
