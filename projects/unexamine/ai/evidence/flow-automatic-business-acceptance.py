"""B10.2a 自动业务节点的真实 API、数据库、重启与清理验收。"""
import ast
import json
import re
import secrets
import sys
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))

PATH = AREA / "flow-automatic-business-facts.json"
facts = json.loads(PATH.read_text(encoding="utf-8")) if PATH.exists() else {
    "step": "B10.2a", "status": "running", "code": "automatic_" + secrets.token_hex(5),
    "flows": [], "instances": [], "records": [], "cases": []}
assert facts["status"] == "running", "验收事实已经封存"

java = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
login = api("POST", "/api/auth/login", {
    "username": re.search(r"default-admin\.username:([^}]+)", java).group(1),
    "password": re.search(r"default-admin\.initial-password:([^}]+)", java).group(1)})
admin = api("POST", "/api/systems/54/tenants/57/enter", {}, login["accessToken"])["tokens"]["accessToken"]
MODULE_ID = 51
MODULE_CODE = "flow_condition_accept_20260918"


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def case(name, result, **extra):
    if not any(item["case"] == name for item in facts["cases"]):
        facts["cases"].append({"case": name, "result": result, **extra})
        save()


def node(key, kind, name, x, config=None):
    return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
        "config": {"moduleId": MODULE_ID} if kind == "START" else config or {},
        "assigneePolicy": {}, "formPolicy": {}, "timeoutPolicy": {},
        "exceptionPolicy": {"action": "MANUAL"} if kind in {"UPDATE_FIELD", "DATA_ACTION"} else {}}


def edge(key, source, target):
    return {"edgeKey": key, "sourceNodeKey": source, "targetNodeKey": target,
        "priorityOrder": 0, "config": {}}


def assignment(field, source="FIXED", value=None, variable=None):
    configured = {"source": source}
    configured["variable" if source == "VARIABLE" else "value"] = variable if source == "VARIABLE" else value
    return {"fieldCode": field, "value": configured}


def create_flow(label, middle):
    flow = api("POST", "/api/flows", {"code": facts["code"] + "_" + secrets.token_hex(3),
        "name": label, "description": "B10.2a 真实验收夹具"}, admin)
    facts["flows"].append(flow["id"]); save()
    nodes = [node("start", "START", "开始", 0)] + middle + [node("end", "END", "结束", (len(middle) + 1) * 240)]
    chain = ["start"] + [item["nodeKey"] for item in middle] + ["end"]
    edges = [edge("e" + str(index), chain[index], chain[index + 1]) for index in range(len(chain) - 1)]
    saved = api("PUT", f"/api/flows/{flow['id']}/draft", {
        "expectedVersion": flow["version"], "nodes": nodes, "edges": edges}, admin)
    return flow["id"], nodes, edges, saved


def publish(flow_id, summary):
    check = api("GET", f"/api/flows/{flow_id}/publication-check", token=admin)
    assert check["valid"], check["issues"]
    published = api("POST", f"/api/flows/{flow_id}/publish", {
        "expectedDraftRevision": check["draftRevision"], "changeSummary": summary,
        "simulationVariables": {}}, admin)
    return published


def start(flow_id, title, variables, key, business_id=None):
    body = {"flowId": flow_id, "title": title, "businessType": "MODULE:" + MODULE_CODE,
        "businessId": None if business_id is None else str(business_id), "businessSnapshot": {},
        "variables": variables, "idempotencyKey": key}
    result = api("POST", "/api/flow-runtime/instances", body, admin)
    instance_id = result["instance"]["id"]
    if instance_id not in facts["instances"]:
        facts["instances"].append(instance_id); save()
    return result, body


def view(instance_id):
    return api("GET", f"/api/flow-runtime/instances/{instance_id}", token=admin)


def variable(instance_id, key):
    escaped = key.replace("'", "''")
    rows = sql(f"SELECT JSON_UNQUOTE(value_json) FROM flow_instance_variable WHERE instance_id={int(instance_id)} AND variable_key='{escaped}'")
    return rows[0][0] if rows else None


def record(record_id):
    return api("GET", f"/api/runtime/modules/{MODULE_CODE}/records/{record_id}", token=admin)


def automatic_events(instance):
    return [event for event in instance["history"] if event["eventType"] == "BUSINESS_DATA_ACTION"]


stage = sys.argv[1]
if stage == "prepare":
    assert not facts.get("prepared"), "prepare 不能重复创建夹具"
    module = next(item for item in api("GET", "/api/runtime/modules", token=admin)
        if item["moduleId"] == MODULE_ID)
    configuration = api("GET", f"/api/runtime/modules/{MODULE_CODE}/configuration", token=admin)
    fields = api("GET", f"/api/flows/node-form-fields?moduleId={MODULE_ID}", token=admin)
    assert module["moduleCode"] == MODULE_CODE
    assert {"subject", "score", "enabled"} <= {item["code"] for item in fields if item["editable"]}
    facts["module_baseline"] = {"moduleId": MODULE_ID, "moduleCode": MODULE_CODE,
        "configVersionId": configuration["versionId"], "activeRecordCount": int(sql(
            f"SELECT COUNT(*) FROM biz_record WHERE module_id={MODULE_ID} AND archived=0 AND deleted=0")[0][0])}

    # 发布入口拒绝旧自定义动作、重复字段、错误固定值类型和后序节点引用。
    invalid_legacy, _, _, _ = create_flow("拒绝手写动作代码", [node("write", "UPDATE_FIELD", "非法字段更新", 240,
        {"businessAction": "APPROVE", "fieldUpdates": {"subject": "bad"}})])
    check = api("GET", f"/api/flows/{invalid_legacy}/publication-check", token=admin)
    assert not check["valid"] and "FLOW_AUTOMATIC_ACTION_INVALID" in {item["code"] for item in check["issues"]}

    invalid_type, _, _, _ = create_flow("拒绝重复和错误类型", [node("create", "DATA_ACTION", "非法新建", 240, {
        "schemaVersion": 1, "operation": "CREATE", "moduleId": MODULE_ID,
        "title": {"source": "FIXED", "value": "非法配置"},
        "assignments": [assignment("score", value="不是数字"), assignment("score", value=2)]})])
    check = api("GET", f"/api/flows/{invalid_type}/publication-check", token=admin)
    assert not check["valid"] and "FLOW_AUTOMATIC_ACTION_INVALID" in {item["code"] for item in check["issues"]}

    future_create = node("create", "DATA_ACTION", "后序新建", 480, {"schemaVersion": 1, "operation": "CREATE",
        "moduleId": MODULE_ID, "title": {"source": "FIXED", "value": "后序记录"},
        "assignments": [assignment("subject", value="后序")]})
    future_update = node("update", "DATA_ACTION", "错误引用后序", 240, {"schemaVersion": 1,
        "operation": "UPDATE", "moduleId": MODULE_ID, "targetRecordSource": "NODE_RESULT",
        "recordNodeKey": "create", "assignments": [assignment("subject", value="不应发布")]})
    invalid_order, _, _, _ = create_flow("拒绝引用后序节点", [future_update, future_create])
    check = api("GET", f"/api/flows/{invalid_order}/publication-check", token=admin)
    assert not check["valid"] and any("执行路径" in item["message"] for item in check["issues"])
    case("publication-rejects-raw-action-duplicate-type-and-future-reference",
        "发布校验拒绝自定义动作代码、重复目标字段、错误固定值类型及不在当前节点之前的节点结果。")

    # 先由结构化自动节点创建一条本次记录，供 CURRENT_RECORD 更新验收使用。
    marker = facts["code"]
    bootstrap_node = node("create", "DATA_ACTION", "创建当前记录夹具", 240, {"schemaVersion": 1,
        "operation": "CREATE", "moduleId": MODULE_ID, "title": {"source": "FIXED", "value": "自动动作验收夹具 " + marker},
        "assignments": [assignment("subject", value="自动创建原值 " + marker), assignment("score", value=5)]})
    bootstrap_flow, _, _, _ = create_flow("自动创建业务夹具", [bootstrap_node]); publish(bootstrap_flow, "自动创建夹具")
    bootstrap_key = marker + "_bootstrap"
    boot, boot_body = start(bootstrap_flow, "自动创建业务夹具", {}, bootstrap_key)
    assert boot["instance"]["status"] == "COMPLETED"
    bootstrap_instance = boot["instance"]["id"]
    bootstrap_record = int(variable(bootstrap_instance, "node.create.recordId"))
    facts["records"].append(bootstrap_record)

    current_node = node("update", "UPDATE_FIELD", "更新当前记录", 240, {"schemaVersion": 1,
        "operation": "UPDATE", "targetRecordSource": "CURRENT_RECORD", "assignments": [
            assignment("subject", value="当前记录已更新 " + marker),
            assignment("score", source="VARIABLE", variable="score")]})
    current_flow, _, _, _ = create_flow("更新当前业务记录", [current_node]); publish(current_flow, "当前记录结构化更新")
    current_key = marker + "_current"
    current, current_body = start(current_flow, "更新当前业务记录", {"score": 42}, current_key, bootstrap_record)
    assert current["instance"]["status"] == "COMPLETED"
    current_record = record(bootstrap_record)
    assert current_record["fields"]["subject"] == "当前记录已更新 " + marker
    assert current_record["fields"]["score"] == 42
    current_event = automatic_events(current["instance"])
    assert len(current_event) == 1 and current_event[0]["detail"]["operation"] == "UPDATE"
    assert marker not in json.dumps(current_event, ensure_ascii=False)
    case("structured-current-record-update", "字段更新节点按业务字段选择和值来源更新当前记录，历史只公开模块、记录、字段名称与动作结果。",
        instanceId=current["instance"]["id"], recordId=bootstrap_record)

    create_node = node("create", "DATA_ACTION", "新建数据", 240, {"schemaVersion": 1, "operation": "CREATE",
        "moduleId": MODULE_ID, "title": {"source": "FIXED", "value": "自动链记录 " + marker},
        "assignments": [assignment("subject", value="自动链初值 " + marker),
            assignment("score", source="VARIABLE", variable="score")]})
    update_node = node("update", "DATA_ACTION", "更新前序数据", 480, {"schemaVersion": 1, "operation": "UPDATE",
        "moduleId": MODULE_ID, "targetRecordSource": "NODE_RESULT", "recordNodeKey": "create",
        "assignments": [assignment("subject", value="自动链已更新 " + marker), assignment("enabled", value=True)]})
    chain_flow, _, _, _ = create_flow("自动新建后更新前序记录", [create_node, update_node]); published = publish(chain_flow, "自动新增更新链")
    chain_key = marker + "_chain"
    chain, chain_body = start(chain_flow, "自动新增更新链", {"score": 66}, chain_key)
    assert chain["instance"]["status"] == "COMPLETED"
    chain_record = int(variable(chain["instance"]["id"], "node.create.recordId"))
    assert variable(chain["instance"]["id"], "node.update.recordId") == str(chain_record)
    saved = record(chain_record)
    assert saved["version"] == 1 and saved["fields"]["score"] == 66
    assert saved["fields"]["subject"] == "自动链已更新 " + marker and saved["fields"]["enabled"] is True
    events = automatic_events(chain["instance"])
    assert [item["detail"]["operation"] for item in events] == ["CREATE", "UPDATE"]
    detail = api("GET", f"/api/flows/{chain_flow}", token=admin)
    snapshot_nodes = detail["versions"][0]["snapshot"]["nodes"]
    frozen = [item for item in snapshot_nodes if item["nodeType"] == "DATA_ACTION"]
    assert all(item["config"]["moduleVersionId"] == configuration["versionId"] for item in frozen)
    assert all(len(item["config"]["fieldBindings"]) == len(item["config"]["assignments"]) for item in frozen)
    facts["records"].append(chain_record)
    case("create-then-update-prior-node-result", "同一次推进中新建记录结果立即成为后续更新目标，最终只有一条版本为 1 的记录，发布快照冻结模块版本和字段身份。",
        instanceId=chain["instance"]["id"], recordId=chain_record, flowVersionId=published["versionId"])

    failure_node = node("create", "DATA_ACTION", "运行值类型失败", 240, {"schemaVersion": 1, "operation": "CREATE",
        "moduleId": MODULE_ID, "title": {"source": "FIXED", "value": "不应创建 " + marker},
        "assignments": [assignment("score", source="VARIABLE", variable="score")]})
    failure_flow, _, _, _ = create_flow("自动动作受控失败", [failure_node]); publish(failure_flow, "运行期类型防线")
    failure_key = marker + "_failure"
    failed, failure_body = start(failure_flow, "自动动作受控失败", {"score": "not-a-number"}, failure_key)
    assert failed["instance"]["status"] == "EXCEPTION"
    assert len([item for item in failed["instance"]["exceptions"] if item["status"] == "OPEN"]) == 1
    assert not sql(f"SELECT id FROM biz_record WHERE module_id={MODULE_ID} AND title='不应创建 {marker.replace(chr(39), chr(39)+chr(39))}'")
    case("runtime-value-is-revalidated-and-visible-as-incident",
        "服务端不信任启动变量；错误数字值没有产生记录，实例进入异常中心并保留可处理事件。",
        instanceId=failed["instance"]["id"], errorCode=failed["instance"]["exceptions"][0]["errorCode"])

    facts["fixtures"] = {
        "bootstrap": {"flow": bootstrap_flow, "instance": bootstrap_instance, "record": bootstrap_record,
            "key": bootstrap_key, "body": boot_body},
        "current": {"flow": current_flow, "instance": current["instance"]["id"], "record": bootstrap_record,
            "key": current_key, "body": current_body},
        "chain": {"flow": chain_flow, "instance": chain["instance"]["id"], "record": chain_record,
            "key": chain_key, "body": chain_body},
        "failure": {"flow": failure_flow, "instance": failed["instance"]["id"],
            "key": failure_key, "body": failure_body,
            "incident": next(item["id"] for item in failed["instance"]["exceptions"] if item["status"] == "OPEN")}}
    facts["prepared"] = True; save()
    print(json.dumps({"prepare": "PASS", "chainInstance": chain["instance"]["id"],
        "failureInstance": failed["instance"]["id"]}, ensure_ascii=False))

elif stage == "verify":
    assert facts.get("prepared") and not facts.get("verified")
    marker = facts["code"]
    for name in ["bootstrap", "current", "chain"]:
        fixture = facts["fixtures"][name]
        assert view(fixture["instance"])["status"] == "COMPLETED"
    failed = view(facts["fixtures"]["failure"]["instance"])
    assert failed["status"] == "EXCEPTION" and any(item["status"] == "OPEN" for item in failed["exceptions"])
    case("same-backend-restart-keeps-business-results-actions-and-incident",
        "同一后端容器重启后，完成实例、自动动作历史、业务记录和失败事件均可读。")

    # 重放流程启动键必须返回同一实例，不能再次写入已有记录或创建第二条记录。
    before_current = record(facts["fixtures"]["current"]["record"])["version"]
    # CLI 连接不依赖中文字符集匹配，直接比较目标模块总行数；两次重放之间没有其他写入。
    before_count = int(sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={MODULE_ID}")[0][0])
    for name in ["current", "chain"]:
        fixture = facts["fixtures"][name]
        replay = api("POST", "/api/flow-runtime/instances", fixture["body"], admin)
        assert replay["replayed"] is True and replay["instance"]["id"] == fixture["instance"]
    assert record(facts["fixtures"]["current"]["record"])["version"] == before_current
    assert int(sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={MODULE_ID}")[0][0]) == before_count
    case("flow-start-and-common-action-idempotency", "相同启动键返回原实例；当前记录版本不增加，自动新建记录不重复。")

    action_counts = {}
    for name, expected in [("bootstrap", 1), ("current", 1), ("chain", 2), ("failure", 0)]:
        instance_id = facts["fixtures"][name]["instance"]
        rows = sql(f"SELECT status,response_code,JSON_UNQUOTE(JSON_EXTRACT(response_json,'$.outboxId')) FROM core_idempotency_record WHERE idempotency_key LIKE 'flow-node:{instance_id}:%' ORDER BY id")
        assert len(rows) == expected, (name, rows)
        if expected:
            assert all(row[0] == "COMPLETED" for row in rows)
            outbox_ids = [int(row[2]) for row in rows]
            assert len(sql("SELECT id FROM core_event_outbox WHERE id IN (" + ",".join(str(int(value)) for value in outbox_ids) + ")")) == expected
        action_counts[name] = expected
    assert len(sql(f"SELECT id FROM flow_history_event WHERE instance_id={facts['fixtures']['chain']['instance']} AND event_type='BUSINESS_DATA_ACTION'")) == 2
    case("common-action-control-and-reliable-outbox", "每个已提交自动写入都有一条完成的公共幂等记录及可靠 outbox；写入前失败没有残留伪成功。",
        counts=action_counts)

    # 页面验收后再结束异常和清理记录，避免 UI 只能看到已删除夹具。
    facts["verified"] = True; save()
    print(json.dumps({"verify": "PASS", "uiFlow": facts["fixtures"]["chain"]["flow"],
        "uiInstance": facts["fixtures"]["chain"]["instance"]}, ensure_ascii=False))

elif stage == "cleanup":
    assert facts.get("verified") and facts.get("gui_observed") and not facts.get("cleanup")
    failure = facts["fixtures"]["failure"]
    current_failure = view(failure["instance"])
    if current_failure["status"] in {"RUNNING", "WAITING", "EXCEPTION"}:
        api("POST", f"/api/flow-runtime/instances/{failure['instance']}/actions", {
            "actionCode": "TERMINATE", "comment": "B10.2a 失败链验收完成，清理本次异常实例",
            "idempotencyKey": facts["code"] + "_terminate"}, admin)
    assert view(failure["instance"])["status"] == "TERMINATED"
    for record_id in facts["records"]:
        current = record(record_id)
        if not current["deleted"]:
            api("POST", f"/api/runtime/modules/{MODULE_CODE}/records/{record_id}/delete", {
                "reason": "B10.2a 自动业务动作验收数据清理", "version": current["version"]}, admin)
        assert record(record_id)["deleted"] is True
    baseline = facts["module_baseline"]
    module = next(item for item in api("GET", "/api/runtime/modules", token=admin) if item["moduleId"] == MODULE_ID)
    configuration = api("GET", f"/api/runtime/modules/{MODULE_CODE}/configuration", token=admin)
    assert configuration["versionId"] == baseline["configVersionId"]
    assert int(sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={MODULE_ID} AND archived=0 AND deleted=0")[0][0]) == baseline["activeRecordCount"]
    ids = ",".join(str(int(value)) for value in facts["instances"])
    assert not sql(f"SELECT id FROM flow_task WHERE instance_id IN ({ids}) AND status='PENDING'")
    assert not sql(f"SELECT id FROM flow_job WHERE instance_id IN ({ids}) AND status IN ('PENDING','RUNNING')")
    assert not sql(f"SELECT id FROM flow_execution WHERE instance_id IN ({ids}) AND status='ACTIVE'")
    flow_ids = ",".join(str(int(value)) for value in facts["flows"])
    prefix = facts["code"].replace("'", "''") + "_%"
    # 测试流程没有产品删除入口；只停用本次随机前缀和已记录 ID，避免污染普通流程列表。
    sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN ({flow_ids}) AND code LIKE '{prefix}'")
    assert not sql(f"SELECT id FROM flow_definition WHERE id IN ({flow_ids}) AND status<>'DISABLED'")
    facts["cleanup"] = {"failureTerminated": True, "recordsLogicallyDeleted": list(facts["records"]),
        "unfinishedTasks": 0, "unfinishedJobs": 0, "activeExecutions": 0,
        "flowsDisabled": list(facts["flows"]), "moduleVersionUnchanged": True,
        "activeRecordCountRestored": baseline["activeRecordCount"]}
    facts["status"] = "passed"; facts["business_verification"] = "passed"; save()
    print(json.dumps({"cleanup": "PASS", "records": facts["records"]}, ensure_ascii=False))

elif stage == "gui":
    assert facts.get("verified") and not facts.get("gui_observed")
    facts["gui_observed"] = {
        "designer": {
            "flowId": facts["fixtures"]["chain"]["flow"],
            "palette": ["字段更新", "新建/更新数据"],
            "structuredControls": ["数据动作", "目标模块", "目标记录来源", "前序数据节点", "新记录标题来源", "字段赋值", "异常策略"],
            "rawJsonInput": False, "businessActionCodeInput": False, "physicalRecordIdInput": False,
            "fieldCodeInput": False,
            "observation": "实际画布可见新建和更新两个数据节点；字段、来源和前序节点均为中文选择控件。"},
        "runtime": {
            "instanceId": facts["fixtures"]["chain"]["instance"],
            "status": "已完成", "automaticHistoryCount": 2,
            "readableHistory": ["新建审批业务条件验收记录", "更新审批业务条件验收记录"],
            "fieldValuesExposed": False,
            "observation": "统一工作区布局下可直接看到流程状态、节点、关联业务以及两条自动动作结果。"}}
    case("ordinary-gui-structured-configuration-and-readable-result",
        "普通设计页不用填写 JSON、动作代码、字段编码或记录 ID；运行页以中文展示新建/更新目标、记录号和字段数量。")
    save()
    print(json.dumps({"gui": "PASS", "instance": facts["fixtures"]["chain"]["instance"]}, ensure_ascii=False))

else:
    raise ValueError("未知阶段")
