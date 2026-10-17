"""在隔离验收库验证模块审批动作的并行分支与配对汇合。"""

import argparse
import json
import os
import runpy
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "projects/unexamine/ai/evidence"
STATE = EVIDENCE / "s18-flow-gateway-live.json"
previous = runpy.run_path(str(ROOT / "projects/unexamine/tools/verify-flow-conditions.py"))
success = previous["success"]
denied = previous["denied"]
session = previous["session"]
sql = previous["sql"]
pdf_hash = previous["pdf_hash"]
PRIOR = previous["PRIOR"]
MODULE_CODE = previous["MODULE_CODE"]
MODULE_ID = previous["MODULE_ID"]
ACTION_ID = previous["ACTION_ID"]


def save(state):
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def graph(invalid=None):
    node = previous["node"]
    edge = previous["edge"]
    nodes = [
        node("start", "START", 0, config={"moduleId": MODULE_ID}),
        node("split", "GATEWAY", 150, config={
            "gatewayMode": "PARALLEL", "mergeNodeKey": "missing" if invalid == "merge" else "merge"}),
        node("owner_review", "APPROVAL", 320, -110, assignee={"type": "RECORD_OWNER"}),
        node("member_review", "APPROVAL", 320, 110,
             assignee={"type": "PERSON", "tenantMemberIds": [258]}),
        node("merge", "GATEWAY", 520, config={"gatewayMode": "MERGE", "splitNodeKey": "split"}),
        node("end", "END", 680),
    ]
    condition = {"mode": "ALL", "conditions": [
        {"field": "activity", "operator": "EQ", "value": "PARALLEL"}
    ]} if invalid in ("merge", "condition") else None
    edges = [
        edge("enter", "start", "split"),
        edge("owner_branch", "split", "owner_review", condition),
        edge("member_branch", "split", "member_review"),
        edge("owner_join", "owner_review", "merge"),
        edge("member_join", "member_review", "merge"),
        edge("finish", "merge", "end"),
    ]
    return nodes, edges


def pending(view):
    tasks = [task for task in view["tasks"] if task["status"] == "PENDING"]
    return {task["nodeKey"]: task["id"] for task in tasks}


def snapshot_rows():
    return sql("SELECT id,flow_version_id,runtime_snapshot_json,status FROM un_flow_instance "
               "WHERE id BETWEEN 53 AND 58 ORDER BY id")


def role_token():
    platform = success("/api/auth/login", "POST",
                       {"username": "action_receiver_1c5c2e86", "password": "correct-password"})
    return success("/api/systems/143/enter", "POST", {}, platform["accessToken"])["tokens"]["accessToken"]


def setup():
    assert not STATE.exists(), "并行网关验收已创建，禁止重复发布"
    _, owner = session()
    before = snapshot_rows()
    candidates = [item for item in success("/api/flows", token=owner)
                  if item["code"].startswith("parallel_accept_") and item["currentVersionId"] is None]
    if candidates:
        flow = candidates[-1]
        suffix = flow["code"].removeprefix("parallel_accept_")
    else:
        suffix = uuid.uuid4().hex[:8]
        flow = success("/api/flows", "POST",
                       {"code": "parallel_accept_" + suffix, "name": "模块并行审批验收"}, owner)
    flow_id = flow["id"]
    if flow["version"] == 0:
        nodes, edges = graph(invalid="merge")
        invalid = success(f"/api/flows/{flow_id}/draft", "PUT",
                          {"expectedVersion": 0, "nodes": nodes, "edges": edges}, owner)
    else:
        invalid = flow
    check = success(f"/api/flows/{flow_id}/publication-check", token=owner)
    issues = {issue["code"] for issue in check["issues"]}
    assert "FLOW_GATEWAY_MERGE_MISSING" in issues
    denied(f"/api/flows/{flow_id}/publish", "POST",
           {"expectedDraftRevision": invalid["draftRevision"], "changeSummary": "invalid parallel graph"},
           owner, {422}, "FLOW_PUBLICATION_INVALID")
    nodes, edges = graph(invalid="condition")
    conditional = success(f"/api/flows/{flow_id}/draft", "PUT",
                          {"expectedVersion": invalid["version"], "nodes": nodes, "edges": edges}, owner)
    conditional_check = success(f"/api/flows/{flow_id}/publication-check", token=owner)
    conditional_issues = {issue["code"] for issue in conditional_check["issues"]}
    assert "FLOW_GATEWAY_PARALLEL_CONDITION_INVALID" in conditional_issues, conditional_issues
    denied(f"/api/flows/{flow_id}/publish", "POST",
           {"expectedDraftRevision": conditional["draftRevision"],
            "changeSummary": "parallel edge must be unconditional"}, owner,
           {422}, "FLOW_PUBLICATION_INVALID")
    assert sql("SELECT COUNT(*) FROM un_flow_version WHERE flow_id=%s", flow_id)[0][0] == 0
    nodes, edges = graph()
    draft = success(f"/api/flows/{flow_id}/draft", "PUT",
                    {"expectedVersion": conditional["version"], "nodes": nodes, "edges": edges}, owner)
    simulation = success(f"/api/flows/{flow_id}/simulate", "POST", {"variables": {}}, owner)
    route = [step["nodeKey"] for step in simulation["steps"]]
    assert simulation["successful"] and all(key in route for key in
                                            ("split", "owner_review", "member_review", "merge", "end")), route
    published = success(f"/api/flows/{flow_id}/publish", "POST",
                        {"expectedDraftRevision": draft["draftRevision"],
                         "changeSummary": "双审批分支并行汇合"}, owner)
    binding = success("/api/flow-bindings/publish", "POST",
                      {"moduleId": MODULE_ID, "actionId": ACTION_ID, "flowId": flow_id,
                       "triggerEvent": "MANUAL", "executionMode": "FLOW_ONLY", "priorityOrder": 0,
                       "mutuallyExclusive": True, "replaceExisting": True}, owner)
    record = success(f"/api/runtime/modules/{MODULE_CODE}/records", "POST",
                     {"title": "Parallel approval " + suffix, "participantMemberIds": [],
                      "fields": {"activity": "PARALLEL"}}, owner)
    instance = success("/api/flow-runtime/module-approvals", "POST",
                       {"moduleCode": MODULE_CODE, "recordId": record["id"],
                        "idempotencyKey": "parallel-approval-" + suffix}, owner)["instance"]
    tasks = pending(instance)
    assert instance["flowVersionId"] == published["versionId"]
    assert set(tasks) == {"owner_review", "member_review"} and instance["status"] == "WAITING"
    assert sql("SELECT status FROM un_flow_trigger_binding WHERE id=%s", 10)[0][0] == "DISABLED"
    assert snapshot_rows() == before
    state = {"status": "parallel_started", "flowId": flow_id, "flowVersionId": published["versionId"],
             "bindingId": binding["id"], "recordId": record["id"], "instanceId": instance["id"],
             "ownerTaskId": tasks["owner_review"], "memberTaskId": tasks["member_review"],
             "invalidIssues": sorted(issues | conditional_issues),
             "simulationRoute": route, "historicalRows": before}
    save(state)
    print(json.dumps({"status": state["status"], "instanceId": instance["id"], "tasks": tasks}))


def approve(token, task_id, suffix):
    return success(f"/api/flow-runtime/tasks/{task_id}/actions", "POST",
                   {"actionCode": "APPROVE", "idempotencyKey": "parallel-handle-" + suffix,
                    "variables": {}}, token)["instance"]


def complete():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "parallel_started"
    _, owner = session()
    receiver = role_token()
    outsider_home = success("/api/auth/login", "POST",
                            {"username": "g7_layout_member_6b4dee80", "password": "correct-password"})
    outsider = success("/api/systems/143/enter", "POST", {},
                       outsider_home["accessToken"])["tokens"]["accessToken"]
    denied("/api/flow-runtime/module-approvals", "POST",
           {"moduleCode": MODULE_CODE, "recordId": state["recordId"],
            "idempotencyKey": "parallel-foreign-" + str(state["recordId"])},
           outsider, {403}, "PERMISSION_DENIED")
    denied(f"/api/flow-runtime/tasks/{state['memberTaskId']}/actions", "POST",
           {"actionCode": "APPROVE", "idempotencyKey": "parallel-foreign-task"},
           outsider, {403}, "PERMISSION_DENIED")
    receiver_view = success(f"/api/flow-runtime/instances/{state['instanceId']}", token=receiver)
    assert "APPROVE" in receiver_view["allowedActions"]
    first = approve(receiver, state["memberTaskId"], "member-" + str(state["instanceId"]))
    assert first["status"] == "WAITING" and pending(first) == {"owner_review": state["ownerTaskId"]}
    joins_before = sql("SELECT COUNT(*) FROM un_flow_history_event "
                       "WHERE instance_id=%s AND event_type='GATEWAY_JOIN'", state["instanceId"])[0][0]
    assert joins_before == 0
    second = approve(owner, state["ownerTaskId"], "owner-" + str(state["instanceId"]))
    assert second["status"] == "COMPLETED" and not pending(second)
    joins_after = sql("SELECT COUNT(*) FROM un_flow_history_event "
                      "WHERE instance_id=%s AND event_type='GATEWAY_JOIN'", state["instanceId"])[0][0]
    assert joins_after == 1
    assert snapshot_rows() == [tuple(row) for row in state["historicalRows"]]
    assert pdf_hash(owner) == PRIOR["pdfSha256"]
    assert success(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", token=owner)["dashboardId"] == PRIOR["dashboardId"]
    state.update({"status": "parallel_completed", "joinCountBefore": joins_before,
                  "joinCountAfter": joins_after, "memberHandled": True, "ownerHandled": True})
    save(state)
    print(json.dumps({"status": state["status"], "instanceId": state["instanceId"], "joinCount": joins_after}))


def readback():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "parallel_browser_completed"
    success.__globals__["BASE"] = os.environ.get("FLOW_READBACK_BASE", "http://127.0.0.1:18081")
    _, owner = session()
    instance = success(f"/api/flow-runtime/instances/{state['instanceId']}", token=owner)
    assert instance["status"] == "COMPLETED" and instance["flowVersionId"] == state["flowVersionId"]
    browser = success(f"/api/flow-runtime/instances/{state['browserInstanceId']}", token=owner)
    assert browser["status"] == "COMPLETED" and browser["flowVersionId"] == state["flowVersionId"]
    assert snapshot_rows() == [tuple(row) for row in state["historicalRows"]]
    for instance_id in (state["instanceId"], state["browserInstanceId"]):
        assert sql("SELECT COUNT(*) FROM un_flow_history_event "
                   "WHERE instance_id=%s AND event_type='GATEWAY_JOIN'", instance_id)[0][0] == 1
    assert pdf_hash(owner) == PRIOR["pdfSha256"]
    print(json.dumps({"status": "parallel_restart_readback_verified", "instanceId": state["instanceId"]}))


def complete_browser():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "parallel_completed"
    browser = json.loads((EVIDENCE / "s18-flow-gateway-browser-readback.json").read_text(encoding="utf-8"))
    instance_id = browser["browserInstanceId"]
    _, owner = session()
    receiver = role_token()
    view = success(f"/api/flow-runtime/instances/{instance_id}", token=owner)
    tasks = pending(view)
    assert set(tasks) == {"owner_review", "member_review"}
    first = approve(owner, tasks["owner_review"], "browser-owner-" + str(instance_id))
    assert first["status"] == "WAITING" and pending(first) == {"member_review": tasks["member_review"]}
    assert sql("SELECT COUNT(*) FROM un_flow_history_event "
               "WHERE instance_id=%s AND event_type='GATEWAY_JOIN'", instance_id)[0][0] == 0
    second = approve(receiver, tasks["member_review"], "browser-member-" + str(instance_id))
    assert second["status"] == "COMPLETED" and not pending(second)
    assert sql("SELECT COUNT(*) FROM un_flow_history_event "
               "WHERE instance_id=%s AND event_type='GATEWAY_JOIN'", instance_id)[0][0] == 1
    assert snapshot_rows() == [tuple(row) for row in state["historicalRows"]]
    state.update({"status": "parallel_browser_completed", "browserInstanceId": instance_id,
                  "browserOwnerTaskId": tasks["owner_review"],
                  "browserMemberTaskId": tasks["member_review"]})
    save(state)
    print(json.dumps({"status": state["status"], "browserInstanceId": instance_id}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=["setup", "complete", "browser-complete", "readback"])
    {"setup": setup, "complete": complete, "browser-complete": complete_browser,
     "readback": readback}[parser.parse_args().phase]()
