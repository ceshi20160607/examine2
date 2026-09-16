"""只读核对本地批量删除验收记录，不启动环境或输出凭证。"""
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


records = rows("SELECT id,version,deleted FROM biz_record WHERE id IN(70,71,72,73,74) ORDER BY id")
assert records == [["70","6","0"],["71","5","0"],["72","2","1"],["73","2","0"],["74","1","1"]], records
values = rows("SELECT record_id,value_type,COALESCE(value_number,value_boolean) FROM biz_record_value "
              "WHERE record_id IN(72,73,74) AND value_type IN('NUMBER','BOOLEAN') ORDER BY record_id,value_type")
assert len(values) == 6 and all(float(v) == (14 if kind == "NUMBER" else 1) for target,kind,v in values)
versions = rows("SELECT id,version_number,HEX(snapshot_json) FROM cfg_module_version WHERE id IN(42,43) ORDER BY id")
snapshots = {int(i):decode(s) for i,n,s in versions}
assert versions[-1][:2] == ["43","4"]
assert snapshots[42]["fields"] == snapshots[43]["fields"] and snapshots[42]["pages"] == snapshots[43]["pages"]
action = next(a for a in snapshots[43]["actions"] if a["code"] == "DELETE")
assert action["location"] == "BATCH" and action["status"] == "ACTIVE"
receipt_rows = rows("SELECT id,HEX(response_json) FROM core_idempotency_record WHERE id IN(60,61,63,64) ORDER BY id")
receipts = {int(i):decode(s) for i,s in receipt_rows}
assert [receipts[i]["value"]["version"] for i in [60,61,63,64]] == [1,1,2,1]
assert all(receipts[i]["mode"] == "DIRECT" for i in [60,61,63])
assert receipts[64]["mode"] == "AFTER_APPROVAL" and receipts[64]["status"] == "EXECUTED"
assert receipts[64]["flowInstanceId"] == 42
binding = rows("SELECT status,HEX(condition_expression) FROM flow_trigger_binding WHERE id=9")[0]
policy = decode(binding[1])
assert binding[0] == "ACTIVE" and policy["executionMode"] == "AFTER_APPROVAL" and policy["flowVersionId"] == 10
assert policy["condition"] == {"mode":"ALL","conditions":[{"field":"field_54b4d262eb","operator":"GT","value":10}]}
assert rows("SELECT flow_id,flow_version_id,status FROM flow_instance WHERE id=42") == [["10","10","COMPLETED"]]

# 逐条删除审计与生命周期历史核对一次执行，包含恢复后原请求回放仍不能再次删除。
audits = rows("SELECT object_id,event_code,COUNT(*) FROM audit_event WHERE system_id=19 "
              "AND object_id IN('72','73','74') AND event_code IN('BUSINESS_RECORD_DELETED','BUSINESS_RECORD_RESTORED') "
              "GROUP BY object_id,event_code ORDER BY object_id,event_code")
assert audits == [["72","BUSINESS_RECORD_DELETED","1"],["73","BUSINESS_RECORD_DELETED","1"],
                  ["73","BUSINESS_RECORD_RESTORED","1"],["74","BUSINESS_RECORD_DELETED","1"]], audits
history = rows("SELECT record_id,from_status,to_status,HEX(reason) FROM biz_record_state_history "
               "WHERE record_id IN(72,73,74) ORDER BY record_id,id")
assert [r[:3] for r in history] == [["72","ACTIVE","DELETED"],["73","ACTIVE","DELETED"],
                                  ["73","DELETED","ACTIVE"],["74","ACTIVE","DELETED"]]
reasons = [bytes.fromhex(r[-1]).decode("utf-8") for r in history]
assert reasons == ["本地验收：删除重复巡检记录。"] * 2 + ["本地验收：确认批量删除后可恢复。","本地验收：审批通过后才删除巡检记录。"]
outbox = rows("SELECT id,event_type,status FROM core_event_outbox WHERE id BETWEEN 44 AND 51 ORDER BY id")
assert len(outbox) == 8 and all(o[-1] == "PUBLISHED" for o in outbox)
approval = json.loads(Path(__file__).with_name("spark-b07-delete-approval-replay.json").read_text(encoding="utf-8-sig"))
assert not approval["beforeApproval"]["deleted"] and all(r["replayed"] for r in approval["replays"])
assert rows("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1") == [["40","1"]]
readback = {"sampled_at":datetime.now().astimezone().isoformat(),"assertions":"passed","records":records,
    "published_config":{"id":43,"version":4,"delete_location":"BATCH","fields_and_pages_unchanged":True},
    "receipts":[{"id":i,"mode":r["mode"],"status":r["status"],"record_id":r["value"]["id"],
                 "value_version":r["value"]["version"]} for i,r in receipts.items()],
    "approval_binding":{"id":9,"mode":policy["executionMode"],"flow_version_id":10,"condition":policy["condition"]},
    "delete_restore_audits":audits,"lifecycle_history":[r[:3]+[reason] for r,reason in zip(history,reasons)],"outbox":outbox,
    "limits":"当前本地批量删除主链；不是全部角色/共享租户/渠道、四种删除模式、移动布局或完整产品验收。"}
Path(__file__).with_name("spark-b07-delete-readback.json").write_text(json.dumps(readback,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print("B07.1c.2 DB assertions passed: partial failure/retry, one delete per item, restore, conditional approval, replay, preserved old fixtures/schema40.")
