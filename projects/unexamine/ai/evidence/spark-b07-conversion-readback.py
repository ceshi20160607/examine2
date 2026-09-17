"""只读验证本次转化的落库结果；不启动服务，不读取凭证或恢复旧验收。"""
import json
import subprocess
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent


def rows(sql):
    result = subprocess.run(["docker", "exec", "unexamine-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"', "readback", sql],
        capture_output=True, text=True, encoding="utf-8", check=True)
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def decode(value):
    return json.loads(bytes.fromhex(value).decode("utf-8"))


fixtures = json.loads((AREA / "spark-b07-conversion-fixtures.json").read_text(encoding="utf-8"))["records"]
api = json.loads((AREA / "spark-b07-conversion-api.json").read_text(encoding="utf-8"))
approval = json.loads((AREA / "spark-b07-conversion-approval.json").read_text(encoding="utf-8"))
final = json.loads((AREA / "spark-b07-conversion-final.json").read_text(encoding="utf-8"))
labels = ["main", "race", "approval", "navigation"]
source_ids = [fixtures[label]["id"] for label in labels]
target_ids = [api["main"]["target_record_id"], api["race"]["target_id"],
              approval["replay"]["target_record_id"], final["navigation"]["target_record_id"]]
source_sql = ",".join(map(str, source_ids))
target_sql = ",".join(map(str, target_ids))

# 标记、主结果及目标必须同时存在，每个来源仅一条成功结果；失败来源无残留标记。
conversion = rows("SELECT c.id,c.source_record_id,c.system_id,c.tenant_id,c.status,r.target_module_id,"
    "r.target_record_id,r.result_type FROM biz_record_conversion c JOIN biz_record_conversion_result r "
    "ON r.conversion_id=c.id WHERE c.source_record_id IN(" + source_sql + ") ORDER BY c.source_record_id")
assert len(conversion) == 4
for item, source_id, target_id in zip(conversion, source_ids, target_ids):
    assert item[1:] == [str(source_id), "19", "21", "SUCCEEDED", "28", str(target_id), "PRIMARY"], item
negative_ids = ",".join(str(fixtures[label]["id"]) for label in ["missing", "collision"])
assert rows("SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id IN(" + negative_ids + ")") == [["0"]]
unique_keys = rows("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics "
    "WHERE table_schema='unexamine' AND table_name='biz_record_conversion' AND non_unique=0 GROUP BY index_name")
assert ["tenant_id,idempotency_key"] in unique_keys

# 转化不改来源正文、版本或归属；后续来源软删与目标软删相互独立。
source_records = rows("SELECT id,version,deleted,module_id,system_id,tenant_id,owner_member_id,department_id "
    "FROM biz_record WHERE id IN(" + source_sql + ") ORDER BY id")
for item, label in zip(source_records, labels):
    lifecycle = ["1", "1"] if label == "navigation" else ["0", "0"]
    assert item == [str(fixtures[label]["id"])] + lifecycle + ["30", "19", "21", "21", "3"], item
target_records = rows("SELECT id,version,deleted,module_id,system_id,tenant_id,owner_member_id,department_id,"
    "created_config_version_id FROM biz_record WHERE id IN(" + target_sql + ") ORDER BY id")
for item, target_id, label in zip(target_records, target_ids, labels):
    lifecycle = ["1", "1"] if label == "main" else ["0", "0"]
    assert item == [str(target_id)] + lifecycle + ["28", "19", "21", "21", "3", "39"], item
    values = {code: (bool(int(flag)) if kind == "BOOLEAN" else float(number) if kind == "NUMBER"
              else bytes.fromhex(text).decode("utf-8")) for code, kind, text, number, flag in rows(
        "SELECT field_code,value_type,COALESCE(HEX(value_text),''),COALESCE(value_number,0),COALESCE(value_boolean,0) FROM biz_record_value "
        "WHERE record_id=" + str(target_id) + " AND field_code IN('field_20a69d1ce3','field_21059c1892',"
        "'field_6690d5a5db','field_db1ac87c01')")}
    assert values["field_20a69d1ce3"] == fixtures[label]["customer"]
    assert values["field_6690d5a5db"] == fixtures[label]["title"]
    assert values["field_db1ac87c01"] is True
    assert values["field_21059c1892"] == {"main": 9, "race": 8, "approval": 5, "navigation": 0}[label]

# 成功转化与目标基础新建分别有一次审计；回放及失败不能增加成功记录。
audits = rows("SELECT object_id,COUNT(*) FROM audit_event WHERE system_id=19 AND "
    "event_code='BUSINESS_RECORD_CONVERTED' AND result_code='SUCCESS' AND object_id IN(" + source_sql + ") "
    "GROUP BY object_id ORDER BY CAST(object_id AS UNSIGNED)")
assert audits == [[str(record_id), "1"] for record_id in source_ids]
created_audits = rows("SELECT object_id,COUNT(*) FROM audit_event WHERE system_id=19 AND "
    "event_code='BUSINESS_RECORD_CREATED' AND result_code='SUCCESS' AND object_id IN(" + target_sql + ") "
    "GROUP BY object_id ORDER BY CAST(object_id AS UNSIGNED)")
assert created_audits == [[str(record_id), "1"] for record_id in target_ids]
snapshots = {int(i): decode(s) for i, s in rows("SELECT id,HEX(snapshot_json) FROM cfg_module_version WHERE id IN(44,45)")}
assert snapshots[44]["fields"] == snapshots[45]["fields"] and snapshots[44]["pages"] == snapshots[45]["pages"]
action = next(a for a in snapshots[45]["actions"] if a["code"] == "CONVERT")
assert action["location"] == "DETAIL_HEADER"
mapping = json.loads(action["configJson"])["targets"][0]
assert mapping["moduleCode"] == "module_4513ff83a1" and len(mapping["fieldMappings"]) == 4
assert rows("SELECT flow_version_id,status FROM flow_instance WHERE id=" + str(approval["flow_id"])) == [["10", "COMPLETED"]]
assert rows("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1") == [["40", "1"]]

# 明确记录实际发现的时区问题，不能把业务成功误记为时间字段也已验证。
clock_issues = rows("SELECT id FROM biz_record_conversion WHERE source_record_id IN(" + source_sql + ") AND finished_at<created_at")
result = {"checked_at": datetime.now().astimezone().isoformat(), "business_assertions": "passed",
    "conversion_results": conversion, "source_records": source_records, "target_records": target_records,
    "one_success_audit_per_source": audits, "one_create_audit_per_target": created_audits,
    "unique_database_key": "tenant_id,idempotency_key",
    "fields_and_pages_preserved": True, "published_mapping_version": 6, "schema_version": 40,
    "clock_issue_conversion_ids": [int(item[0]) for item in clock_issues],
    "limits": "B07.3a.1 main contribution only. Completion-time timezone, relation removal, full permissions, batch and other modes remain required."}
(AREA / "spark-b07-conversion-readback.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Conversion DB business assertions passed; completion-time timezone issue explicitly retained for next substep.")
