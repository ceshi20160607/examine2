"""B10.2c.1 等待与定时器节点的真实 API、数据库、重启和清理验收。"""
import ast
import json
import re
import secrets
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / "shared-actor-acceptance.py").read_text(encoding="utf-8"))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {"call", "api", "sql"}], type_ignores=[]),
    "shared-actor-acceptance.py", "exec"))

PATH = AREA / "flow-wait-timer-facts.json"
facts = json.loads(PATH.read_text(encoding="utf-8")) if PATH.exists() else {
    "step": "B10.2c.1", "status": "running", "code": "flow_wait_" + secrets.token_hex(5),
    "flows": [], "instances": [], "cases": []}
assert facts["status"] == "running", "验收事实已经封存"

java = (AREA.parents[1] / "backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java").read_text(encoding="utf-8")
default_username = re.search(r"default-admin\.username:([^}]+)", java).group(1)
login = api("POST", "/api/auth/login", {
    "username": default_username,
    "password": re.search(r"default-admin\.initial-password:([^}]+)", java).group(1)})
admin = api("POST", "/api/systems/54/tenants/57/enter", {}, login["accessToken"])["tokens"]["accessToken"]


def save():
    facts["checked_time"] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def case(name, result, **extra):
    if not any(item["case"] == name for item in facts["cases"]):
        facts["cases"].append({"case": name, "result": result, **extra})
        save()


def node(key, kind, name, x, config=None):
    return {"nodeKey": key, "nodeType": kind, "name": name, "positionX": x, "positionY": 100,
        "config": config or {}, "assigneePolicy": {}, "formPolicy": {}, "timeoutPolicy": {},
        "exceptionPolicy": {}}


def edge(index, source, target):
    return {"edgeKey": "e" + str(index), "sourceNodeKey": source, "targetNodeKey": target,
        "priorityOrder": 0, "config": {}}


def create_flow(label, middle):
    flow = api("POST", "/api/flows", {"code": facts["code"] + "_" + secrets.token_hex(3),
        "name": label, "description": "B10.2c.1 等待与定时器真实验收夹具"}, admin)
    facts["flows"].append(flow["id"]); save()
    nodes = [node("start", "START", "开始", 0)] + middle + [node("end", "END", "结束", (len(middle) + 1) * 240)]
    keys = [item["nodeKey"] for item in nodes]
    api("PUT", f"/api/flows/{flow['id']}/draft", {
        "expectedVersion": flow["version"], "nodes": nodes,
        "edges": [edge(index, keys[index], keys[index + 1]) for index in range(len(keys) - 1)]}, admin)
    return flow["id"]


def publication_check(flow_id):
    return api("GET", f"/api/flows/{flow_id}/publication-check", token=admin)


def publish(flow_id, summary):
    check = publication_check(flow_id)
    assert check["valid"], check["issues"]
    return api("POST", f"/api/flows/{flow_id}/publish", {
        "expectedDraftRevision": check["draftRevision"], "changeSummary": summary,
        "simulationVariables": {}}, admin)


def start(flow_id, title, key):
    body = {"flowId": flow_id, "title": title, "businessSnapshot": {}, "variables": {},
        "idempotencyKey": key}
    result = api("POST", "/api/flow-runtime/instances", body, admin)
    instance_id = result["instance"]["id"]
    if instance_id not in facts["instances"]:
        facts["instances"].append(instance_id); save()
    return result, body


def view(instance_id):
    return api("GET", f"/api/flow-runtime/instances/{instance_id}", token=admin)


def wait_for(instance_id, predicate, seconds=25):
    deadline = time.time() + seconds
    latest = view(instance_id)
    while time.time() < deadline and not predicate(latest):
        time.sleep(0.5)
        latest = view(instance_id)
    assert predicate(latest), (instance_id, latest["status"], latest.get("jobs"), latest.get("exceptions"))
    return latest


def timer_jobs(instance):
    return [item for item in instance["jobs"] if item["jobType"] == "TIMER"]


def wait_events(instance, event_type):
    return [item for item in instance["history"] if item["eventType"] == event_type]


def frozen_node(flow_id, version_id, node_key):
    detail = api("GET", f"/api/flows/{flow_id}", token=admin)
    version = next(item for item in detail["versions"] if item["id"] == version_id)
    return next(item for item in version["snapshot"]["nodes"] if item["nodeKey"] == node_key)


def force_due(job_id, instance_id):
    sql(f"UPDATE flow_job SET next_run_at=NOW(),version=version+1 WHERE id={int(job_id)} "
        f"AND instance_id={int(instance_id)} AND status='PENDING'")


stage = sys.argv[1]
if stage == "prepare":
    assert not facts.get("prepared"), "prepare 不能重复创建夹具"

    # 页面协议无法生成的非法数字、超长等待和过期时间均须在发布前阻断。
    invalid_configs = [
        ("拒绝小数等待", "WAIT", {"schemaVersion": 1, "waitMode": "DURATION", "durationValue": 1.5, "durationUnit": "HOUR"}),
        ("拒绝超长等待", "WAIT", {"schemaVersion": 1, "waitMode": "DURATION", "durationValue": 366, "durationUnit": "DAY"}),
        ("拒绝过期定时器", "TIMER", {"schemaVersion": 1, "waitMode": "ABSOLUTE",
            "resumeTime": (datetime.now() - timedelta(minutes=5)).isoformat(timespec="seconds")})]
    for label, kind, config in invalid_configs:
        flow_id = create_flow(label, [node("wait", kind, label, 240, config)])
        check = publication_check(flow_id)
        assert not check["valid"] and "FLOW_WAIT_TIMER_CONFIG_INVALID" in {item["code"] for item in check["issues"]}, check
    case("publication-rejects-invalid-wait-and-timer-values",
        "发布检查拒绝小数、超过一年的等待以及已经过去的定时恢复时间。")

    wait_flow = create_flow("等待十分钟后恢复", [node("wait", "WAIT", "等待十分钟", 240,
        {"schemaVersion": 1, "waitMode": "DURATION", "durationValue": 10, "durationUnit": "MINUTE"})])
    wait_publication = publish(wait_flow, "结构化等待时长与可靠恢复")
    frozen = frozen_node(wait_flow, wait_publication["versionId"], "wait")["config"]
    assert frozen == {"schemaVersion": 1, "waitMode": "DURATION", "durationValue": 10,
        "durationUnit": "MINUTE", "durationMinutes": 10}, frozen
    wait_key = facts["code"] + "_duration"
    wait_result, wait_body = start(wait_flow, "等待节点可靠恢复验收", wait_key)
    waiting = wait_for(wait_result["instance"]["id"], lambda item: item["status"] == "WAITING"
        and len(timer_jobs(item)) == 1 and timer_jobs(item)[0]["status"] == "PENDING")
    wait_job = timer_jobs(waiting)[0]
    assert wait_job["attemptCount"] == 0 and len(wait_events(waiting, "FLOW_WAIT_SCHEDULED")) == 1
    scheduled = wait_events(waiting, "FLOW_WAIT_SCHEDULED")[0]
    assert scheduled["detail"]["waitMode"] == "DURATION" and scheduled["detail"]["durationMinutes"] == 10
    replay = api("POST", "/api/flow-runtime/instances", wait_body, admin)
    assert replay["replayed"] is True and replay["instance"]["id"] == waiting["id"]
    assert len(timer_jobs(view(waiting["id"]))) == 1
    force_due(wait_job["id"], waiting["id"])
    completed = wait_for(waiting["id"], lambda item: item["status"] == "COMPLETED")
    jobs = timer_jobs(completed)
    assert len(jobs) == 1 and jobs[0]["status"] == "COMPLETED" and jobs[0]["attemptCount"] == 1
    assert len(wait_events(completed, "FLOW_WAIT_SCHEDULED")) == 1
    assert len(wait_events(completed, "FLOW_WAIT_RESUMED")) == 1
    assert not [item for item in completed["executions"] if item["status"] == "ACTIVE"]
    case("duration-wait-freezes-and-resumes-once",
        "十分钟等待发布为标准分钟数；相同启动键只生成一个持久化作业，到期扫描只恢复一次。",
        flowVersionId=wait_publication["versionId"], instanceId=completed["id"], jobId=jobs[0]["id"])

    terminate_result, _ = start(wait_flow, "等待节点终止验收", facts["code"] + "_terminate")
    terminate_waiting = wait_for(terminate_result["instance"]["id"], lambda item: item["status"] == "WAITING"
        and len(timer_jobs(item)) == 1 and timer_jobs(item)[0]["status"] == "PENDING")
    terminated = api("POST", f"/api/flow-runtime/instances/{terminate_waiting['id']}/actions", {
        "actionCode": "TERMINATE", "comment": "等待期间终止流程并取消恢复作业",
        "idempotencyKey": facts["code"] + "_terminate_action"}, admin)["instance"]
    assert terminated["status"] == "TERMINATED" and timer_jobs(terminated)[0]["status"] == "CANCELLED"
    case("termination-cancels-pending-wait-job",
        "流程在等待期间终止后，未到期恢复作业立即取消，不能继续推进。",
        instanceId=terminated["id"], jobId=timer_jobs(terminated)[0]["id"])

    resume_time = (datetime.now() + timedelta(minutes=15)).replace(microsecond=0).isoformat()
    timer_flow = create_flow("指定时间恢复", [node("timer", "TIMER", "指定时间恢复", 240,
        {"schemaVersion": 1, "waitMode": "ABSOLUTE", "resumeTime": resume_time})])
    timer_publication = publish(timer_flow, "结构化指定时间与重启恢复")
    timer_frozen = frozen_node(timer_flow, timer_publication["versionId"], "timer")["config"]
    assert timer_frozen == {"schemaVersion": 1, "waitMode": "ABSOLUTE", "resumeTime": resume_time}, timer_frozen
    timer_key = facts["code"] + "_absolute"
    timer_result, timer_body = start(timer_flow, "定时器跨重启恢复验收", timer_key)
    timer_waiting = wait_for(timer_result["instance"]["id"], lambda item: item["status"] == "WAITING"
        and len(timer_jobs(item)) == 1 and timer_jobs(item)[0]["status"] == "PENDING")
    time.sleep(2)
    still_waiting = view(timer_waiting["id"])
    assert still_waiting["status"] == "WAITING" and timer_jobs(still_waiting)[0]["status"] == "PENDING"
    assert wait_events(still_waiting, "FLOW_WAIT_SCHEDULED")[0]["detail"]["waitMode"] == "ABSOLUTE"
    facts["fixtures"] = {
        "duration": {"flow": wait_flow, "instance": completed["id"], "key": wait_key, "body": wait_body},
        "terminated": {"instance": terminated["id"]},
        "absolute": {"flow": timer_flow, "instance": timer_waiting["id"], "key": timer_key,
            "body": timer_body, "job": timer_jobs(timer_waiting)[0]["id"], "resumeTime": resume_time}}
    facts["prepared"] = True; save()
    case("absolute-timer-does-not-run-early",
        "指定时间定时器在目标时间前保持等待，冻结恢复时间与作业计划一致。",
        instanceId=timer_waiting["id"], jobId=timer_jobs(timer_waiting)[0]["id"])
    print(json.dumps({"prepare": "PASS", "durationInstance": completed["id"],
        "timerInstance": timer_waiting["id"]}, ensure_ascii=False))

elif stage == "resume":
    assert facts.get("prepared") and not facts.get("resumed")
    fixture = facts["fixtures"]["absolute"]
    before = view(fixture["instance"])
    jobs = timer_jobs(before)
    assert before["status"] == "WAITING" and len(jobs) == 1
    assert jobs[0]["id"] == fixture["job"] and jobs[0]["status"] == "PENDING" and jobs[0]["attemptCount"] == 0
    assert len(wait_events(before, "FLOW_WAIT_SCHEDULED")) == 1 and not wait_events(before, "FLOW_WAIT_RESUMED")
    force_due(jobs[0]["id"], before["id"])
    completed = wait_for(before["id"], lambda item: item["status"] == "COMPLETED")
    job = timer_jobs(completed)[0]
    assert job["status"] == "COMPLETED" and job["attemptCount"] == 1
    assert len(wait_events(completed, "FLOW_WAIT_SCHEDULED")) == 1
    assert len(wait_events(completed, "FLOW_WAIT_RESUMED")) == 1
    assert wait_events(completed, "FLOW_WAIT_RESUMED")[0]["detail"]["waitMode"] == "ABSOLUTE"
    assert not [item for item in completed["executions"] if item["status"] == "ACTIVE"]
    facts["resumed"] = True; save()
    case("pending-timer-survives-restart-and-resumes-once",
        "定时作业跨同一后端容器重启后仍保持待执行；到期后完成一次并留下单一恢复历史。",
        instanceId=completed["id"], jobId=job["id"], attemptCount=job["attemptCount"])
    print(json.dumps({"resume": "PASS", "instance": completed["id"], "job": job["id"]}, ensure_ascii=False))

elif stage == "verify":
    assert facts.get("resumed") and not facts.get("verified")
    for name in ["duration", "absolute"]:
        fixture = facts["fixtures"][name]
        instance = view(fixture["instance"])
        assert instance["status"] == "COMPLETED" and len(timer_jobs(instance)) == 1
        assert timer_jobs(instance)[0]["status"] == "COMPLETED" and timer_jobs(instance)[0]["attemptCount"] == 1
        assert len(wait_events(instance, "FLOW_WAIT_SCHEDULED")) == 1
        assert len(wait_events(instance, "FLOW_WAIT_RESUMED")) == 1
        replay = api("POST", "/api/flow-runtime/instances", fixture["body"], admin)
        assert replay["replayed"] is True and replay["instance"]["id"] == fixture["instance"]
        assert len(timer_jobs(view(fixture["instance"]))) == 1
    ids = ",".join(str(int(facts["fixtures"][name]["instance"])) for name in ["duration", "absolute"])
    assert sql(f"SELECT COUNT(*),SUM(status='COMPLETED'),SUM(attempt_count) FROM flow_job "
        f"WHERE instance_id IN ({ids}) AND job_type='TIMER'")[0] == ["2", "2", "2"]
    case("completed-waits-survive-second-restart-and-replay",
        "两类已完成作业、执行和历史在再次重启后保持，相同启动键不会重复实例、作业或恢复。")
    facts["verified"] = True; save()
    print(json.dumps({"verify": "PASS", "instances": 2, "jobs": 2}, ensure_ascii=False))

elif stage == "gui":
    assert facts.get("verified") and not facts.get("gui")
    # 普通浏览器默认进入主工作空间，建立一个仍在等待的可读夹具供设计页和运行页核验。
    admin = api("POST", "/api/systems/54/tenants/56/enter", {}, login["accessToken"])["tokens"]["accessToken"]
    gui_flow = create_flow("等待与定时器页面验收", [
        node("wait", "WAIT", "等待三十分钟", 240,
            {"schemaVersion": 1, "waitMode": "DURATION", "durationValue": 30, "durationUnit": "MINUTE"})])
    publish(gui_flow, "普通页面结构化等待验收")
    result, _ = start(gui_flow, "等待与定时器页面验收", facts["code"] + "_gui")
    instance = wait_for(result["instance"]["id"], lambda item: item["status"] == "WAITING"
        and len(timer_jobs(item)) == 1 and timer_jobs(item)[0]["status"] == "PENDING")
    facts["gui"] = {"flow": gui_flow, "instance": instance["id"], "job": timer_jobs(instance)[0]["id"]}
    save()
    print(json.dumps({"gui": "PASS", **facts["gui"]}, ensure_ascii=False))

elif stage == "cleanup":
    assert facts.get("verified") and not facts.get("cleanup")
    if facts.get("gui"):
        admin = api("POST", "/api/systems/54/tenants/56/enter", {}, login["accessToken"])["tokens"]["accessToken"]
        current = view(facts["gui"]["instance"])
        if current["status"] in {"RUNNING", "WAITING", "EXCEPTION", "RETURNED"}:
            api("POST", f"/api/flow-runtime/instances/{current['id']}/actions", {
                "actionCode": "TERMINATE", "comment": "页面验收完成后清理等待实例",
                "idempotencyKey": facts["code"] + "_gui_cleanup"}, admin)
    flow_ids = ",".join(str(int(value)) for value in facts["flows"])
    if flow_ids:
        sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN ({flow_ids}) "
            f"AND code LIKE '{facts['code']}%'")
    instance_ids = ",".join(str(int(value)) for value in facts["instances"])
    assert int(sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN ({instance_ids}) "
        "AND status IN ('PENDING','RUNNING','SUSPENDED')")[0][0]) == 0
    facts["cleanup"] = {"flowsDisabled": len(facts["flows"]), "pendingJobs": 0}
    facts["status"] = "passed"; save()
    print(json.dumps({"cleanup": "PASS", **facts["cleanup"]}, ensure_ascii=False))

else:
    raise AssertionError("stage 必须是 prepare、resume、verify、gui 或 cleanup")
