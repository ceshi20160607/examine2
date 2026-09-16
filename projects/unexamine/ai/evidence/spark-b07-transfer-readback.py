"""只读核对本地转交验收数据；不启动环境，不保存凭证或动作快照。"""
import json
import subprocess
from datetime import datetime
from pathlib import Path


def rows(sql):
    result = subprocess.run(["docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"', "readback", sql],
        capture_output=True, text=True, encoding="utf-8", check=True)
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def decode(value):
    return json.loads(bytes.fromhex(value).decode("utf-8"))


evidence = Path(__file__).parent
approval = json.loads((evidence / "spark-b07-transfer-approval-replay.json").read_text(encoding="utf-8"))
final = json.loads((evidence / "spark-b07-transfer-final-clock.json").read_text(encoding="utf-8"))
record = rows("SELECT id,version,system_id,tenant_id,module_id,owner_member_id,department_id,"
              "updated_config_version_id,updated_at>=created_at FROM biz_record WHERE id=75")
assert record == [["75", "3", "19", "21", "30", "69", "70", "44", "1"]], record
assert rows("SELECT system_member_id FROM biz_record_participant WHERE record_id=75") == [["21"]]
values = rows("SELECT value_type,COALESCE(value_number,value_boolean) FROM biz_record_value "
              "WHERE record_id=75 AND value_type IN('NUMBER','BOOLEAN') ORDER BY value_type")
assert [(kind, float(value)) for kind, value in values] == [("BOOLEAN", 1), ("NUMBER", 14)], values
assert final["fields_unchanged"] and final["unchanged_before_approval"]

# 本次只发布动作位置，字段及公共页面结构必须与接手时的当前版本一致。
snapshots = {int(i): decode(s) for i, s in rows(
    "SELECT id,HEX(snapshot_json) FROM cfg_module_version WHERE id IN(43,44) ORDER BY id")}
assert snapshots[43]["fields"] == snapshots[44]["fields"] and snapshots[43]["pages"] == snapshots[44]["pages"]
action = next(a for a in snapshots[44]["actions"] if a["code"] == "TRANSFER")
assert action["location"] == "DETAIL_HEADER" and action["status"] == "ACTIVE"

# 三次真实归属变化各只有一条历史及成功审计；失败请求与原键回放不能追加历史。
history = rows("SELECT from_owner_member_id,to_owner_member_id,from_department_id,to_department_id,HEX(reason) "
               "FROM biz_record_owner_history WHERE record_id=75 ORDER BY id")
assert [r[:4] for r in history] == [["21", "69", "3", "70"], ["69", "21", "70", "3"], ["21", "69", "3", "70"]]
reasons = [bytes.fromhex(r[-1]).decode("utf-8") for r in history]
assert reasons == ["本地验收：转交设备保障负责人和部门。", "本地验收：审批通过后才转交负责人和部门。",
                   "本地验收：最终代码核对转交归属、审批及更新时间。"]
assert rows("SELECT COUNT(*) FROM audit_event WHERE system_id=19 AND object_id='75' "
            "AND event_code='BUSINESS_RECORD_TRANSFERRED' AND result_code='SUCCESS'") == [["3"]]
ids = [70, approval["approval_receipt_id"], final["receipt_id"]]
receipts = {int(i): decode(s) for i, s in rows("SELECT id,HEX(response_json) FROM core_idempotency_record "
                                             "WHERE id IN(" + ",".join(map(str, ids)) + ") ORDER BY id")}
assert [receipts[i]["value"]["version"] for i in ids] == [1, 2, 3]
assert receipts[70]["mode"] == "DIRECT" and receipts[70]["status"] == "COMPLETED"
assert all(receipts[i]["mode"] == "AFTER_APPROVAL" and receipts[i]["status"] == "EXECUTED" for i in ids[1:])
binding_status, policy_hex = rows("SELECT status,HEX(condition_expression) FROM flow_trigger_binding WHERE id=10")[0]
policy = decode(policy_hex)
assert binding_status == "ACTIVE" and policy["executionMode"] == "AFTER_APPROVAL" and policy["flowVersionId"] == 10
assert policy["condition"] == {"mode": "ALL", "conditions": [{"field": "field_54b4d262eb", "operator": "GT", "value": 10}]}
flows = rows("SELECT id,flow_version_id,status FROM flow_instance WHERE id IN(43,44) ORDER BY id")
assert flows == [["43", "10", "COMPLETED"], ["44", "10", "COMPLETED"]]
outbox = rows("SELECT id,event_type,status FROM core_event_outbox WHERE id>=52 ORDER BY id")
assert outbox and all(r[-1] == "PUBLISHED" for r in outbox), outbox

# 保留此前当前验收数据及数据库版本，不依赖旧周期报告判定功能完成。
assert rows("SELECT id,version,deleted FROM biz_record WHERE id IN(70,71,72,73,74) ORDER BY id") == [
    ["70", "6", "0"], ["71", "5", "0"], ["72", "2", "1"], ["73", "2", "0"], ["74", "1", "1"]]
assert rows("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1") == [["40", "1"]]
readback = {"checked_at": datetime.now().astimezone().isoformat(), "assertions": "passed", "record": record,
    "history": [r[:4] + [reason] for r, reason in zip(history, reasons)], "successful_transfer_audits": 3,
    "receipts": [{"id": i, "mode": receipts[i]["mode"], "status": receipts[i]["status"],
                  "version": receipts[i]["value"]["version"]} for i in ids],
    "flows": flows, "outbox": outbox, "fields_and_pages_unchanged": True, "schema_version": 40,
    "limits": "仅当前本地单条转交主链；完整批量、角色/共享租户/渠道权限、非零关联影响及另两种转交模式仍需验证。"}
(evidence / "spark-b07-transfer-readback.json").write_text(json.dumps(readback, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("B07.2a DB assertions passed: ownership/history, conditional approval, replay, DB time, preserved fields/pages/fixtures/schema40.")
