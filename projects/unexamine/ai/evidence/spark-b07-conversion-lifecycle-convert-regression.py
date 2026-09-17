"""只读回本次已执行的转化审批，复验当前必填失败，并关闭不再满足唯一冲突前提的测试提交。"""
import json
import runpy
import sys
import uuid
from pathlib import Path

AREA = Path(__file__).parent
sys.argv = ["helper", "inspect"]
s = runpy.run_path(str(AREA / "spark-b07-conversion-acceptance.py"))
api, rows, decode, save = [s[name] for name in ["api", "rows", "decode", "save"]]
SOURCE = s["SOURCE"]
source_id = 94  # 本次来源锁审批脚本实际生成；下面校验系统、租户、模块，禁止泛化修改历史数据。
assert rows("SELECT system_id,tenant_id,module_id,version FROM biz_record WHERE id=94") == [["19", "21", "30", "0"]]
action_id = s["receipt"](source_id)
stored = decode(rows("SELECT HEX(response_json) FROM core_idempotency_record WHERE id=" + str(action_id))[0][0])
instance = api("GET", f"/api/flow-runtime/instances/{stored['flowInstanceId']}")
assert instance["status"] == "COMPLETED"
delivery = api("GET", f"/api/runtime/modules/{SOURCE}/records/action-results/{action_id}")
assert delivery["businessStatus"] == "EXECUTED" and delivery["recordId"] == source_id
replayed = s["replay"](action_id)
assert replayed["mode"] == "AFTER_APPROVAL"
assert rows("SELECT finished_at>=created_at FROM biz_record_conversion WHERE source_record_id=94") == [["1"]]
status, response = s["convert"](78)
assert status == 409 and response["code"] == "CONVERSION_MAPPING_INVALID"
assert rows("SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id=78") == [["0"]]

# 原唯一冲突来源79对应的旧目标81已软删，唯一字段被释放；不再能用它声称当前唯一冲突。
# 先前验收脚本按旧前提断言失败，实际产生待审批126；拒绝本次合成提交，保留动作/审批审计。
mistaken_id = s["receipt"](79)
mistaken = decode(rows("SELECT HEX(response_json) FROM core_idempotency_record WHERE id=" + str(mistaken_id))[0][0])
pending_flow = api("GET", f"/api/flow-runtime/instances/{mistaken['flowInstanceId']}")
pending = [task for task in pending_flow["tasks"] if task["status"] == "PENDING"
           and task["assigneeTenantMemberId"] == s["ENTRY"]["tenantMemberId"]]
if pending:
    api("POST", f"/api/flow-runtime/tasks/{pending[0]['id']}/actions", {"actionCode": "REJECT",
        "comment": "本地验收：旧冲突目标已软删，拒绝不再满足冲突前提的测试提交。", "idempotencyKey": str(uuid.uuid4())})
assert rows("SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id=79") == [["0"]]
after = api("GET", f"/api/flow-runtime/instances/{mistaken['flowInstanceId']}")
assert after["status"] == "REJECTED"
save("spark-b07-conversion-lifecycle-convert-regression.json", {
    "scope": "Affected source lock/clock under actual CONVERT approval and current required rollback only.",
    "source_id": source_id, "receipt_id": action_id, "flow_id": stored["flowInstanceId"],
    "no_target_before_approval": True, "delivery": delivery, "replay": replayed, "clock_consistent": True,
    "required_failure": {"source_id": 78, "http": status, "code": response["code"], "zero_partial_marker": True},
    "fixture_correction": {"source_id": 79, "receipt_id": mistaken_id, "flow_id": mistaken["flowInstanceId"],
        "status": after["status"], "no_conversion": True,
        "reason": "旧目标81已软删释放唯一字段，原唯一冲突先决条件失效；错误测试提交已拒绝，不据此修改业务校验。"}})
print("Affected current CONVERT approval lock/clock and required rollback passed; invalid old-fixture submission rejected.")
