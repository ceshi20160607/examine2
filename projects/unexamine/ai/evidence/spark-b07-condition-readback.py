"""Read-only assertions for the B07.1b local browser acceptance fixtures."""
import json
from datetime import datetime
from pathlib import Path
import subprocess


def rows(sql):
    result = subprocess.run([
        "docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"',
        "readback", sql,
    ], capture_output=True, text=True, encoding="utf-8", check=True)
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def decode(value):
    return json.loads(bytes.fromhex(value).decode("utf-8"))


records = rows("SELECT r.id,r.version,r.created_config_version_id,r.updated_config_version_id,v.value_number "
               "FROM biz_record r JOIN biz_record_value v ON v.record_id=r.id "
               "AND v.field_code='field_54b4d262eb' WHERE r.id IN(70,71) ORDER BY r.id")
assert records == [["70", "2", "40", "41", "14.00000000"], ["71", "4", "41", "41", "18.00000000"]], records
receipts = rows("SELECT id,HEX(response_json) FROM core_idempotency_record WHERE id BETWEEN 41 AND 46 ORDER BY id")
results = {int(key): decode(value) for key, value in receipts}
assert [results[i]["status"] for i in range(41, 47)] == ["COMPLETED"] * 4 + ["FLOW_STARTED", "EXECUTED"]
assert [results[i]["value"]["version"] for i in range(41, 45)] == [0, 1, 2, 3]
assert [results[i]["value"]["fields"]["field_54b4d262eb"] for i in range(41, 45)] == [8, 9, 10, 16]
assert results[45]["value"] is None and results[45]["flowInstanceId"] == 36
assert results[46]["flowInstanceId"] == 37 and results[46]["value"]["version"] == 4
bindings = rows("SELECT id,status,flow_id,HEX(condition_expression) FROM flow_trigger_binding "
                "WHERE system_id=19 AND module_id=30 AND trigger_event='UPDATE' ORDER BY id")
policies = {int(key): decode(policy) for key, status, flow, policy in bindings}
assert [policies[i]["executionMode"] for i in range(3, 7)] == ["DIRECT", "AFTER_EXECUTION", "FLOW_ONLY", "AFTER_APPROVAL"]
assert bindings[0][2] == "NULL" and [b[1] for b in bindings] == ["DISABLED"] * 3 + ["ACTIVE"]
assert policies[4]["condition"] == policies[5]["condition"] == policies[6]["condition"]
assert all(policies[i]["flowVersionId"] == 10 for i in range(4, 7))
flows = rows("SELECT id,flow_id,flow_version_id,status FROM flow_instance WHERE id IN(35,36,37) ORDER BY id")
assert flows == [["35", "10", "10", "WAITING"], ["36", "10", "10", "WAITING"], ["37", "10", "10", "COMPLETED"]], flows
outbox = rows("SELECT id,event_type,status,JSON_EXTRACT(payload_json,'$.flowInstanceId') "
              "FROM core_event_outbox WHERE id BETWEEN 31 AND 36 ORDER BY id")
assert [x[2] for x in outbox] == ["PUBLISHED"] * 6
assert [x[3] for x in outbox] == ["null", "null", "null", "35", "36", "37"]
audits = rows("SELECT e.id,e.event_code,e.result_code,c.before_value_json,c.after_value_json "
              "FROM audit_event e JOIN audit_field_change c ON c.audit_event_id=e.id "
              "WHERE e.system_id=19 AND e.object_id='71' AND c.field_code='field_54b4d262eb' ORDER BY e.id")
assert len([a for a in audits if json.loads(a[-2]) == 16 and json.loads(a[-1]) == 18]) == 1, audits
migration = rows("SELECT version,success FROM flyway_schema_history WHERE version='40'")
assert migration == [["40", "1"]]
nullable = rows("SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='unexamine' "
                "AND TABLE_NAME='flow_trigger_binding' AND COLUMN_NAME='flow_id'")
assert nullable == [["YES"]]
readback = {
    "sampled_at": datetime.now().astimezone().isoformat(), "assertions": "passed",
    "records": records, "receipts": [{"id": i, "status": r["status"], "mode": r["mode"],
        "flow_instance_id": r.get("flowInstanceId"), "value_version": (r.get("value") or {}).get("version")}
        for i, r in results.items()],
    "bindings": [{"id": int(i), "status": s, "flow_id": f, "policy": decode(p)} for i,s,f,p in bindings],
    "flows": flows, "outbox": outbox, "number_change_audits": audits,
    "migration": migration, "flow_id_nullable": nullable,
    "before_approval_observed": {"record": 71, "version": 3, "number": 16,
        "intent": 46, "status": "PENDING_APPROVAL", "flow_instance": 37, "flow_status": "WAITING"},
    "limits": "Current fixture readback, not all action families/roles/approval behavior or future business data.",
}
Path(__file__).with_name("spark-b07-condition-readback.json").write_text(
    json.dumps(readback, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("B07.1b DB assertions passed: four modes, preserved record70, record71 version4/18, one approval mutation, pinned Flow10, V40.")
