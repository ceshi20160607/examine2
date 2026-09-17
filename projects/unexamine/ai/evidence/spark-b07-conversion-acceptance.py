"""当前本地转化主链验收；复用现有服务，凭证和请求键仅在进程内使用。"""
import json
import re
import subprocess
import sys
import time
import uuid
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
SOURCE = "module_49d10b5a8b"
TARGET = "module_4513ff83a1"


def rows(sql):
    r = subprocess.run(["docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"', "readback", sql],
        capture_output=True, text=True, encoding="utf-8", check=True)
    return [line.split("\t") for line in r.stdout.strip().splitlines()]


def decode(value):
    return json.loads(bytes.fromhex(value).decode("utf-8"))


def call(method, path, body=None, token=None, headers=None):
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    h.update(headers or {})
    request = urllib.request.Request("http://localhost:18080" + path,
        data=json.dumps(body, ensure_ascii=False).encode() if body is not None else None, headers=h, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def api(method, path, body=None, headers=None):
    status, result = call(method, path, body, TOKEN, headers)
    assert 200 <= status < 300, (status, result.get("code"), result.get("message"))
    return result["data"]


def save(name, value):
    (AREA / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def convert_path(record_id):
    return f"/api/runtime/modules/{SOURCE}/records/{record_id}/convert"


def convert(record_id, version=0):
    return call("POST", convert_path(record_id), {"version": version, "targetModuleCode": TARGET}, TOKEN,
                {"Prefer": "return=action", "Idempotency-Key": str(uuid.uuid4())})


def receipt(record_id):
    # 只定位本次来源记录的转化动作，不读取或恢复旧周期结果。
    return int(rows("SELECT aggregate_id FROM core_event_outbox WHERE aggregate_type='BUSINESS_ACTION' "
        "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.action.actionCode'))='CONVERT' "
        "AND JSON_EXTRACT(payload_json,'$.action.recordId')=" + str(record_id) + " ORDER BY id DESC LIMIT 1")[0][0])


def replay(action_id):
    stored = decode(rows("SELECT HEX(response_json) FROM core_idempotency_record WHERE id=" + str(action_id))[0][0])
    action = decode(rows("SELECT HEX(payload_json) FROM core_event_outbox WHERE id=" + str(stored["outboxId"]))[0][0])["action"]
    result = api("POST", convert_path(action["recordId"]), action["payload"],
                 {"Prefer": "return=action", "Idempotency-Key": stored["idempotencyKey"]})
    assert result["replayed"] and result["idempotencyRecordId"] == action_id
    return {"id": action_id, "replayed": True, "mode": result["mode"], "status": result["status"],
            "target_record_id": result["value"]["targetRecord"]["id"]}


def approve(result):
    instance = api("GET", f"/api/flow-runtime/instances/{result['flowInstanceId']}")
    task = next(t for t in instance["tasks"] if t["status"] == "PENDING"
                and t["assigneeTenantMemberId"] == ENTRY["tenantMemberId"])
    api("POST", f"/api/flow-runtime/tasks/{task['id']}/actions", {"actionCode": "APPROVE",
        "comment": "本地验收：批准当前新建的转化验收动作。", "idempotencyKey": str(uuid.uuid4())})
    for attempt in range(30):
        delivery = api("GET", f"/api/runtime/modules/{SOURCE}/records/action-results/{result['idempotencyRecordId']}")
        if delivery["businessStatus"] == "EXECUTED":
            return {"flow_id": result["flowInstanceId"], "task_id": task["id"], "business_status": "EXECUTED"}
        time.sleep(.3)
    raise AssertionError(delivery["businessStatus"])


bootstrap = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
status, login = call("POST", "/api/auth/login", {
    "username": re.search(r"default-admin\.username:([^}]+)", bootstrap).group(1),
    "password": re.search(r"default-admin\.initial-password:([^}]+)", bootstrap).group(1)})
assert status == 200
status, entry_response = call("POST", "/api/systems/19/enter", {}, login["data"]["accessToken"])
assert status == 200
ENTRY = entry_response["data"]
TOKEN = ENTRY["tokens"]["accessToken"]
fixture_path = AREA / "spark-b07-conversion-fixtures.json"
phase = sys.argv[1]

if phase == "fixtures":
    assert not fixture_path.exists(), "本轮验收记录已创建，禁止重复生成"
    config = api("GET", f"/api/runtime/modules/{SOURCE}/configuration")
    fields = {f["name"]: f["code"] for f in config["configuration"]["fields"]}
    fixtures = {}
    for label, title, customer, number in [
        ("main", "华东工厂设备回访", "华东工厂", 9), ("race", "并发设备回访验收", "北区设备客户", 8),
        ("missing", "缺少目标客户名称验收", None, 7), ("collision", "华东工厂设备回访", "重名编码验收客户", 6),
        ("approval", "审批后设备客户建档", "东区设备客户", 5)]:
        values = {fields["巡检项目"]: title, fields["仪表读数"]: number, fields["正常运行"]: True}
        if customer is not None:
            values[fields["巡检备注"]] = customer
        record = api("POST", f"/api/runtime/modules/{SOURCE}/records", {"title": title, "status": "ACTIVE",
            "ownerMemberId": ENTRY["systemMemberId"], "departmentId": 3, "participantMemberIds": [], "fields": values})
        fixtures[label] = {"id": record["id"], "version": record["version"], "title": title, "customer": customer}
    save(fixture_path.name, {"checked_at": datetime.now().astimezone().isoformat(), "source": SOURCE,
                           "target": TARGET, "records": fixtures})
    print(json.dumps({key: value["id"] for key, value in fixtures.items()}))
else:
    fixtures = json.loads(fixture_path.read_text(encoding="utf-8"))["records"]
    if phase == "verify":
        main = receipt(fixtures["main"]["id"])
        first = replay(main)
        negative = []
        for label in ["missing", "collision"]:
            record_id = fixtures[label]["id"]
            preview = api("POST", convert_path(record_id).replace("/convert", "/conversion-preview"), {"version": 0, "targetModuleCode": TARGET})
            assert not preview["executable"] and preview["issues"]
            status, response = convert(record_id)
            assert status == 409 and response["code"] == "CONVERSION_MAPPING_INVALID"
            assert rows("SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id=" + str(record_id)) == [["0"]]
            negative.append({"case": label, "http": status, "code": response["code"], "issues": preview["issues"]})
        race_id = fixtures["race"]["id"]
        with ThreadPoolExecutor(max_workers=2) as pool:
            race = list(pool.map(lambda _: convert(race_id), range(2)))
        assert sorted(status for status, response in race) == [200, 409]
        winner = next(response["data"] for status, response in race if status == 200)
        loser = next(response for status, response in race if status == 409)
        assert loser["code"] == "RECORD_ALREADY_CONVERTED"
        target_id = first["target_record_id"]
        target = api("GET", f"/api/runtime/modules/{TARGET}/records/{target_id}")
        deleted = api("POST", f"/api/runtime/modules/{TARGET}/records/{target_id}/delete",
            {"version": target["version"], "reason": "本地验收：目标软删后仍禁止重复转化。"},
            {"Prefer": "return=action", "Idempotency-Key": str(uuid.uuid4())})
        assert deleted["value"]["deleted"]
        preview = api("POST", convert_path(fixtures["main"]["id"]).replace("/convert", "/conversion-preview"), {"version": 0, "targetModuleCode": TARGET})
        assert preview["alreadyConverted"] and not preview["executable"]
        status, response = convert(fixtures["main"]["id"])
        assert status == 409 and response["code"] == "RECORD_ALREADY_CONVERTED"
        assert replay(main)["target_record_id"] == target_id
        condition = {"mode": "ALL", "conditions": [{"field": next(f["code"] for f in api("GET", f"/api/runtime/modules/{SOURCE}/configuration")["configuration"]["fields"] if f["name"] == "仪表读数"), "operator": "GT", "value": 0}]}
        binding = api("POST", "/api/flow-bindings/publish", {"moduleId": 30, "triggerEvent": "CONVERT",
            "flowId": 10, "executionMode": "AFTER_APPROVAL", "priorityOrder": 0, "replaceExisting": True,
            "mutuallyExclusive": False, "condition": condition})
        save("spark-b07-conversion-api.json", {"checked_at": datetime.now().astimezone().isoformat(), "main": first,
            "negative_cases": negative, "race": {"statuses": [s for s, r in race], "loser_code": loser["code"],
            "receipt_id": winner["idempotencyRecordId"], "target_id": winner["value"]["targetRecord"]["id"]},
            "soft_deleted_target_id": target_id, "no_duplicate_after_delete": True, "approval_binding_id": binding["id"]})
        print("Target required/unique failures leave no marker; race creates one target; soft-delete retains uniqueness/replay; CONVERT approval binding published.")
    elif phase == "approve":
        record_id = fixtures["approval"]["id"]
        action_id = receipt(record_id)
        stored = decode(rows("SELECT HEX(response_json) FROM core_idempotency_record WHERE id=" + str(action_id))[0][0])
        assert stored["status"] == "PENDING_APPROVAL" and stored["value"] is None
        assert rows("SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id=" + str(record_id)) == [["0"]]
        outcome = approve(stored)
        outcome.update({"receipt_id": action_id, "no_target_before_approval": True, "replay": replay(action_id)})
        save("spark-b07-conversion-approval.json", outcome)
        print(json.dumps(outcome))
    elif phase == "final":
        # 当前页面导航修复后的新记录：转化不修改来源，来源软删不级联删除目标。
        record_id = fixtures["navigation"]["id"]
        action_id = receipt(record_id)
        original = replay(action_id)
        assert original["mode"] == "DIRECT"
        target_id = original["target_record_id"]
        source = api("GET", f"/api/runtime/modules/{SOURCE}/records/{record_id}")
        assert source["version"] == 0 and not source["deleted"]
        deleted = api("POST", f"/api/runtime/modules/{SOURCE}/records/{record_id}/delete",
            {"version": source["version"], "reason": "本地验收：来源软删不级联删除转化目标。"},
            {"Prefer": "return=action", "Idempotency-Key": str(uuid.uuid4())})
        assert deleted["value"]["deleted"]
        target = api("GET", f"/api/runtime/modules/{TARGET}/records/{target_id}")
        assert not target["deleted"] and target["version"] == 0
        links = api("GET", f"/api/runtime/modules/{TARGET}/records/{target_id}/conversions")["links"]
        assert any(link["recordId"] == record_id and link["recordStatus"] == "DELETED" for link in links)
        # 发布新的审批绑定后，旧已执行动作回放仍返回原模式，不再次启动审批或写入。
        race_replay = replay(receipt(fixtures["race"]["id"]))
        assert race_replay["mode"] == "DIRECT"
        save("spark-b07-conversion-final.json", {"navigation": original, "source_deleted": True,
            "target_remains_active": True, "source_status_link": "DELETED", "race_original_replay": race_replay})
        print("Current direct navigation receipt replay passed; source deletion keeps target active; original direct replay ignores later approval binding.")
