"""只读核对 B07.1c.1 当前本地验收数据；不启动环境，不输出凭证或完整动作快照。"""
import json
import subprocess
from datetime import datetime
from pathlib import Path


def rows(sql):
    result = subprocess.run([
        "docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"',
        "readback", sql,
    ], capture_output=True, text=True, encoding="utf-8", check=True)
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def decode(value):
    return json.loads(bytes.fromhex(value).decode("utf-8"))


versions = rows("SELECT id,version_number,HEX(snapshot_json) FROM cfg_module_version WHERE id IN(41,42) ORDER BY id")
snapshots = {int(key): decode(value) for key, number, value in versions}
assert versions[-1][:2] == ["42", "3"]
assert snapshots[41]["fields"] == snapshots[42]["fields"]
assert snapshots[41]["pages"] == snapshots[42]["pages"]
action = next(a for a in snapshots[42]["actions"] if a["id"] == 286)
config = json.loads(action["configJson"])
assert action["actionType"] == "CUSTOM" and action["location"] == "BATCH"
assert action["name"] == "填写巡检复核备注" and action["status"] == "ACTIVE"
assert config["operation"] == "UPDATE_FIELDS" and config["parameterFieldCodes"] == ["field_dcc06d22c1"]

records = rows("SELECT r.id,r.version,r.created_config_version_id,r.updated_config_version_id,"
               "r.owner_member_id,r.department_id,n.value_number,b.value_boolean,HEX(t.value_text) "
               "FROM biz_record r JOIN biz_record_value n ON n.record_id=r.id AND n.value_type='NUMBER' "
               "JOIN biz_record_value b ON b.record_id=r.id AND b.value_type='BOOLEAN' "
               "JOIN biz_record_value t ON t.record_id=r.id AND t.field_code='field_dcc06d22c1' "
               "WHERE r.id IN(70,71) ORDER BY r.id")
assert [r[:8] for r in records] == [
    ["70", "6", "40", "42", "21", "3", "14.00000000", "1"],
    ["71", "5", "41", "42", "21", "3", "18.00000000", "1"],
], records
notes = {int(r[0]): bytes.fromhex(r[-1]).decode("utf-8") for r in records}
assert notes == {70: "审批后复核完成，设备状态正常。", 71: "批量复核完成，设备状态正常。"}

receipt_rows = rows("SELECT id,HEX(response_json) FROM core_idempotency_record WHERE id IN(47,48,50,51,54) ORDER BY id")
receipts = {int(key): decode(value) for key, value in receipt_rows}
assert [receipts[i]["mode"] for i in [47,48,50,51,54]] == ["DIRECT"] + ["AFTER_EXECUTION"] * 3 + ["AFTER_APPROVAL"]
assert [receipts[i]["value"]["version"] for i in [47,48,50,51,54]] == [3,5,4,5,6]
assert receipts[54]["status"] == "EXECUTED" and receipts[54]["flowInstanceId"] == 41
bindings = rows("SELECT id,status,action_id,HEX(condition_expression) FROM flow_trigger_binding WHERE id IN(7,8) ORDER BY id")
policies = [decode(b[-1]) for b in bindings]
assert [b[:3] for b in bindings] == [["7","DISABLED","286"],["8","ACTIVE","286"]]
assert [p["executionMode"] for p in policies] == ["AFTER_EXECUTION","AFTER_APPROVAL"]
assert all(p["flowVersionId"] == 10 for p in policies)
flows = rows("SELECT id,flow_id,flow_version_id,status FROM flow_instance WHERE id IN(38,39,40,41) ORDER BY id")
assert flows == [["38","10","10","WAITING"],["39","10","10","WAITING"],
                 ["40","10","10","WAITING"],["41","10","10","COMPLETED"]]
outbox = rows("SELECT id,event_type,status FROM core_event_outbox WHERE id BETWEEN 38 AND 43 ORDER BY id")
assert len(outbox) == 6 and all(o[-1] == "PUBLISHED" for o in outbox)

# 用审计变化核对每条成功写入仅发生一次，不能仅依赖页面成功提示或回执状态。
audit_rows = rows("SELECT e.object_id,e.id,HEX(c.after_value_json) FROM audit_event e "
                  "JOIN audit_field_change c ON c.audit_event_id=e.id WHERE e.system_id=19 "
                  "AND e.object_id IN('70','71') AND c.field_code='field_dcc06d22c1' ORDER BY e.id")
once = []
for record_id, note in [(70,"批量复核完成，设备状态正常。"),(71,"批量复核完成，设备状态正常。"),
                        (70,"单条复核完成，设备状态正常。"),(70,"审批后复核完成，设备状态正常。")]:
    matching = [int(audit) for target, audit, value in audit_rows if int(target) == record_id and decode(value) == note]
    assert len(matching) == 1, (record_id, note, matching)
    once.append({"record_id": record_id, "note": note, "audit_id": matching[0], "writes": 1})
replay = json.loads(Path(__file__).with_name("spark-b07-manual-replay.json").read_text(encoding="utf-8-sig"))
approval = json.loads(Path(__file__).with_name("spark-b07-manual-approval.json").read_text(encoding="utf-8-sig"))
assert all(r["replayed"] for r in replay["replays"]) and approval["replayed"]
assert replay["extraFieldAttempt"] == {"code":"MANUAL_ACTION_VALUES_INVALID","httpStatus":422}
assert rows("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1") == [["40","1"]]

readback = {
    "sampled_at": datetime.now().astimezone().isoformat(), "assertions": "passed",
    "published_version": {"id":42,"number":3,"fields_and_pages_unchanged":True},
    "action": {k:action[k] for k in ["id","name","actionType","location","status"]},
    "parameter_fields": config["parameterFieldCodes"],
    "records": [{"id":int(r[0]),"version":int(r[1]),"number":r[6],"boolean":r[7],"note":notes[int(r[0])]} for r in records],
    "receipts": [{"id":i,"mode":r["mode"],"status":r["status"],"value_version":r["value"]["version"],
                  "flow_instance_id":r.get("flowInstanceId")} for i,r in receipts.items()],
    "manual_bindings": [{"id":int(b[0]),"status":b[1],"action_id":int(b[2]),"mode":p["executionMode"],
                         "flow_version_id":p["flowVersionId"]} for b,p in zip(bindings,policies)],
    "flows":flows,"outbox":outbox,"once_only_note_writes":once,
    "limits":"仅当前本地手动字段动作与批量修改验收；不是完整批量删除/转移/转化/共享、角色/渠道或高级审批验收。",
}
Path(__file__).with_name("spark-b07-manual-readback.json").write_text(
    json.dumps(readback,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print("B07.1c.1 DB assertions passed: typed manual action, partial-success retry, one write per item, approval resume/replay, pinned Flow10, schema40.")
