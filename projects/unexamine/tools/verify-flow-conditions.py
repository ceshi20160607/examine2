"""在隔离验收库验证模块审批条件分支、版本快照和旧待办继续办理。"""

import argparse
import hashlib
import json
import os
import runpy
import urllib.request
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "projects/unexamine/ai/evidence"
STATE = EVIDENCE / "s18-flow-conditions-live.json"
PRIOR = json.loads((EVIDENCE / "s18-module-action-integration-live.json").read_text(encoding="utf-8"))
helpers = runpy.run_path(str(ROOT / "projects/unexamine/tools/verify-module-approval-action.py"))
success = helpers["success"]
denied = helpers["denied"]
session = helpers["session"]
database = helpers["database"]

MODULE_ID = 94
MODULE_CODE = "dashboard_accept_1a4547cf"
ACTION_ID = 1140


def sql(query, *parameters):
    with database() as connection, connection.cursor() as cursor:
        cursor.execute(query, parameters)
        return list(cursor.fetchall())


def pdf_hash(token):
    call = urllib.request.Request(
        helpers["success"].__globals__["BASE"] + f"/api/print/jobs/{PRIOR['printJobId']}/preview",
        headers={"Authorization": "Bearer " + token},
    )
    with urllib.request.urlopen(call, timeout=30) as response:
        return hashlib.sha256(response.read()).hexdigest()


def node(key, kind, x, y=0, config=None, assignee=None):
    item = {"nodeKey": key, "nodeType": kind, "name": key, "positionX": x,
            "positionY": y, "config": config or {}}
    if assignee:
        item["assigneePolicy"] = assignee
    return item


def edge(key, source, target, condition=None, order=0):
    return {"edgeKey": key, "sourceNodeKey": source, "targetNodeKey": target,
            "priorityOrder": order, "config": {"condition": condition} if condition else {}}


def graph(second_review=False, invalid=False):
    owner = {"type": "RECORD_OWNER"}
    nodes = [
        node("start", "START", 0, config={"moduleId": MODULE_ID}),
        node("decision", "GATEWAY", 160, config={"gatewayMode": "EXCLUSIVE"}),
        node("high_review_v2" if second_review else "high_review", "APPROVAL", 320, -100,
             config={"approvalMode": "INVALID"} if invalid else {}, assignee=owner),
        node("low_review", "APPROVAL", 320, 100, assignee=owner),
        node("end", "END", 680),
    ]
    high = "high_review_v2" if second_review else "high_review"
    if second_review:
        nodes.insert(-1, node("second_review", "APPROVAL", 500, -100, assignee=owner))
    condition = {"mode": "ALL", "conditions": [
        {"field": "missing" if invalid else "activity", "operator": "EQ", "value": "FLOW_HIGH"}
    ]}
    edges = [
        edge("enter", "start", "decision"),
        edge("high", "decision", high, condition, 0),
        edge("low", "decision", "low_review", order=1),
        edge("high_finish", high, "second_review" if second_review else "end"),
        edge("low_finish", "low_review", "end"),
    ]
    if second_review:
        edges.append(edge("second_finish", "second_review", "end"))
    return nodes, edges


def save(state):
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def create_record(token, activity, suffix):
    return success(f"/api/runtime/modules/{MODULE_CODE}/records", "POST",
                   {"title": f"Flow condition {activity} {suffix}",
                    "participantMemberIds": [], "fields": {"activity": activity}}, token)["id"]


def start(token, record_id, suffix):
    return success("/api/flow-runtime/module-approvals", "POST",
                   {"moduleCode": MODULE_CODE, "recordId": record_id,
                    "idempotencyKey": "flow-condition-" + suffix}, token)["instance"]


def pending(instance, key):
    tasks = [item for item in instance["tasks"] if item["status"] == "PENDING"]
    assert len(tasks) == 1 and tasks[0]["nodeKey"] == key, (instance["id"], tasks)
    return tasks[0]["id"]


def approve(token, task_id, suffix):
    return success(f"/api/flow-runtime/tasks/{task_id}/actions", "POST",
                   {"actionCode": "APPROVE", "idempotencyKey": "flow-condition-handle-" + suffix,
                    "variables": {}}, token)["instance"]


def historical_rows():
    return sql("SELECT id,flow_version_id,business_snapshot_json,runtime_snapshot_json,status "
               "FROM un_flow_instance WHERE id IN (53,54) ORDER BY id")


def setup():
    assert not STATE.exists(), "验收状态已经存在，禁止重复创建 Flow 和记录"
    _, token = session()
    suffix = uuid.uuid4().hex[:8]
    before_history = historical_rows()
    assert [row[4] for row in before_history] == ["WAITING", "WAITING"]
    assert pdf_hash(token) == PRIOR["pdfSha256"]
    flow = success("/api/flows", "POST",
                   {"code": "condition_accept_" + suffix, "name": "模块条件审批验收"}, token)
    flow_id = flow["id"]
    nodes, edges = graph(invalid=True)
    invalid = success(f"/api/flows/{flow_id}/draft", "PUT",
                      {"expectedVersion": 0, "nodes": nodes, "edges": edges}, token)
    check = success(f"/api/flows/{flow_id}/publication-check", token=token)
    issues = {issue["code"] for issue in check["issues"]}
    assert "FLOW_CONDITION_INVALID" in issues and "APPROVAL_MODE_INVALID" in issues, issues
    invalid_publish = denied(f"/api/flows/{flow_id}/publish", "POST",
                             {"expectedDraftRevision": invalid["draftRevision"],
                              "changeSummary": "invalid configuration"}, token,
                             {422}, "FLOW_PUBLICATION_INVALID")
    assert sql("SELECT COUNT(*) FROM un_flow_version WHERE flow_id=%s", flow_id)[0][0] == 0
    nodes, edges = graph()
    draft = success(f"/api/flows/{flow_id}/draft", "PUT",
                    {"expectedVersion": invalid["version"], "nodes": nodes, "edges": edges}, token)
    routes = {}
    for label, activity, expected in [("true", "FLOW_HIGH", "high_review"),
                                       ("false", "FLOW_LOW", "low_review")]:
        simulation = success(f"/api/flows/{flow_id}/simulate", "POST",
                             {"variables": {"activity": activity}}, token)
        route = [step["nodeKey"] for step in simulation["steps"]]
        assert simulation["successful"] and expected in route, route
        routes[label] = route
    v1 = success(f"/api/flows/{flow_id}/publish", "POST",
                 {"expectedDraftRevision": draft["draftRevision"], "changeSummary": "条件分支 V1",
                  "simulationVariables": {"activity": "FLOW_HIGH"}}, token)
    binding_v1 = success("/api/flow-bindings/publish", "POST",
                         {"moduleId": MODULE_ID, "actionId": ACTION_ID, "flowId": flow_id,
                          "triggerEvent": "MANUAL", "executionMode": "FLOW_ONLY",
                          "priorityOrder": 0, "mutuallyExclusive": True,
                          "replaceExisting": True}, token)
    high_record = create_record(token, "FLOW_HIGH", suffix)
    low_record = create_record(token, "FLOW_LOW", suffix)
    high_instance = start(token, high_record, suffix + "-high-v1")
    low_instance = start(token, low_record, suffix + "-low-v1")
    assert high_instance["flowVersionId"] == low_instance["flowVersionId"] == v1["versionId"]
    high_task = pending(high_instance, "high_review")
    low_task = pending(low_instance, "low_review")
    v1_snapshot = sql("SELECT snapshot_json FROM un_flow_version WHERE id=%s", v1["versionId"])[0][0]
    old_instances = sql("SELECT id,flow_version_id,runtime_snapshot_json,status FROM un_flow_instance "
                        "WHERE id IN (%s,%s) ORDER BY id", high_instance["id"], low_instance["id"])
    state = {"status": "v1_started", "flowId": flow_id, "v1VersionId": v1["versionId"],
             "v1Hash": v1["definitionHash"], "v1Snapshot": v1_snapshot,
             "bindingV1": binding_v1["id"], "highRecordId": high_record, "lowRecordId": low_record,
             "highV1InstanceId": high_instance["id"], "lowV1InstanceId": low_instance["id"],
             "highV1TaskId": high_task, "lowV1TaskId": low_task,
             "oldInstances": old_instances, "historicalRows": before_history,
             "invalidIssues": sorted(issues), "invalidPublication": invalid_publish,
             "simulationRoutes": routes}
    save(state)
    print(json.dumps({"status": state["status"], "flowId": flow_id,
                      "instances": [high_instance["id"], low_instance["id"]]}))


def version_and_complete():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "v1_started"
    _, token = session()
    flow_id = state["flowId"]
    published_versions = sql("SELECT id FROM un_flow_version WHERE flow_id=%s ORDER BY version_number", flow_id)
    if len(published_versions) == 1:
        flow = success(f"/api/flows/{flow_id}", token=token)
        nodes, edges = graph(second_review=True)
        draft = success(f"/api/flows/{flow_id}/draft", "PUT",
                        {"expectedVersion": flow["version"], "nodes": nodes, "edges": edges}, token)
        v2 = success(f"/api/flows/{flow_id}/publish", "POST",
                     {"expectedDraftRevision": draft["draftRevision"], "changeSummary": "条件分支 V2 双审批",
                      "simulationVariables": {"activity": "FLOW_HIGH"}}, token)
        binding_v2 = success("/api/flow-bindings/publish", "POST",
                             {"moduleId": MODULE_ID, "actionId": ACTION_ID, "flowId": flow_id,
                              "triggerEvent": "MANUAL", "executionMode": "FLOW_ONLY",
                              "priorityOrder": 0, "mutuallyExclusive": True,
                              "replaceExisting": True}, token)
    else:
        assert len(published_versions) == 2, published_versions
        v2 = {"versionId": published_versions[1][0]}
        binding_v2 = {"id": sql("SELECT id FROM un_flow_trigger_binding WHERE flow_id=%s "
                                "AND module_id=%s AND status='ACTIVE'", flow_id, MODULE_ID)[0][0]}
    assert binding_v2["id"] != state["bindingV1"]
    assert sql("SELECT snapshot_json FROM un_flow_version WHERE id=%s", state["v1VersionId"])[0][0] == state["v1Snapshot"]
    assert sql("SELECT id,flow_version_id,runtime_snapshot_json,status FROM un_flow_instance "
               "WHERE id IN (%s,%s) ORDER BY id",
               state["highV1InstanceId"], state["lowV1InstanceId"]) == [tuple(row) for row in state["oldInstances"]]
    suffix = uuid.uuid4().hex[:8]
    v2_record = create_record(token, "FLOW_HIGH", suffix)
    v2_instance = start(token, v2_record, suffix + "-high-v2")
    assert v2_instance["flowVersionId"] == v2["versionId"]
    first_v2_task = pending(v2_instance, "high_review_v2")
    old_high = approve(token, state["highV1TaskId"], suffix + "-old-high")
    old_low = approve(token, state["lowV1TaskId"], suffix + "-old-low")
    assert old_high["status"] == old_low["status"] == "COMPLETED"
    assert old_high["flowVersionId"] == old_low["flowVersionId"] == state["v1VersionId"]
    first_done = approve(token, first_v2_task, suffix + "-v2-first")
    second_v2_task = pending(first_done, "second_review")
    second_done = approve(token, second_v2_task, suffix + "-v2-second")
    assert second_done["status"] == "COMPLETED" and second_done["flowVersionId"] == v2["versionId"]
    platform = success("/api/auth/login", "POST",
                       {"username": "g7_layout_member_6b4dee80", "password": "correct-password"})
    outsider = success("/api/systems/143/enter", "POST", {}, platform["accessToken"])["tokens"]["accessToken"]
    forbidden_start = denied("/api/flow-runtime/module-approvals", "POST",
                             {"moduleCode": MODULE_CODE, "recordId": v2_record,
                              "idempotencyKey": "flow-condition-denied-" + suffix},
                             outsider, {403}, "PERMISSION_DENIED")
    forbidden_task = denied(f"/api/flow-runtime/tasks/{second_v2_task}/actions", "POST",
                            {"actionCode": "APPROVE", "idempotencyKey": "flow-condition-foreign-" + suffix},
                            outsider, {403, 404, 409})
    assert historical_rows() == [tuple(row) for row in state["historicalRows"]]
    assert pdf_hash(token) == PRIOR["pdfSha256"]
    assert success(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", token=token)["dashboardId"] == PRIOR["dashboardId"]
    state.update({"status": "versions_completed", "v2VersionId": v2["versionId"],
                  "bindingV2": binding_v2["id"], "v2RecordId": v2_record,
                  "v2InstanceId": v2_instance["id"], "v2TaskIds": [first_v2_task, second_v2_task],
                  "forbiddenStart": forbidden_start, "forbiddenTask": forbidden_task})
    save(state)
    print(json.dumps({"status": state["status"], "v2InstanceId": v2_instance["id"],
                      "oldV1Instances": [state["highV1InstanceId"], state["lowV1InstanceId"]]}))


def readback():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] in ("versions_completed", "separate_approver_verified")
    helpers["success"].__globals__["BASE"] = os.environ.get("FLOW_READBACK_BASE", "http://127.0.0.1:18081")
    _, token = session()
    for instance_id, version_id in [(state["highV1InstanceId"], state["v1VersionId"]),
                                    (state["lowV1InstanceId"], state["v1VersionId"]),
                                    (state["v2InstanceId"], state["v2VersionId"])]:
        view = success(f"/api/flow-runtime/instances/{instance_id}", token=token)
        assert view["flowVersionId"] == version_id and view["status"] == "COMPLETED"
    browser = json.loads((EVIDENCE / "s18-flow-conditions-browser-readback.json").read_text(encoding="utf-8"))
    browser_view = success(f"/api/flow-runtime/instances/{browser['browserInstanceId']}", token=token)
    assert browser_view["flowVersionId"] == state["v2VersionId"]
    if state["status"] == "versions_completed":
        pending(browser_view, "high_review_v2")
    else:
        assert browser_view["status"] == "COMPLETED"
        assert [task["nodeKey"] for task in browser_view["tasks"] if task["status"] == "APPROVE"] == [
            "high_review_v2", "second_review"]
    assert historical_rows() == [tuple(row) for row in state["historicalRows"]]
    assert pdf_hash(token) == PRIOR["pdfSha256"]
    assert success(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", token=token)["dashboardId"] == PRIOR["dashboardId"]
    print(json.dumps({"status": "restart_readback_verified", "flowId": state["flowId"]}))


def separate_approver():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "versions_completed"
    _, owner_token = session()
    browser = json.loads((EVIDENCE / "s18-flow-conditions-browser-readback.json").read_text(encoding="utf-8"))
    instance_id = browser["browserInstanceId"]
    instance = success(f"/api/flow-runtime/instances/{instance_id}", token=owner_token)
    first_task = pending(instance, "high_review_v2")
    if next(task for task in instance["tasks"] if task["id"] == first_task)["assigneeTenantMemberId"] != 258:
        success(f"/api/flow-runtime/tasks/{first_task}/actions", "POST",
                {"actionCode": "TRANSFER", "targetTenantMemberId": 258,
                 "idempotencyKey": "flow-condition-transfer-" + str(instance_id),
                 "variables": {}}, owner_token)
    account = success("/api/auth/login", "POST",
                      {"username": "action_receiver_1c5c2e86", "password": "correct-password"})
    entry = success("/api/systems/143/enter", "POST", {}, account["accessToken"])
    receiver_token = entry["tokens"]["accessToken"]
    before = denied(f"/api/flow-runtime/instances/{instance_id}", "GET", None,
                    receiver_token, {403}, "PERMISSION_DENIED")
    overview = success("/api/admin/system/authorization", token=owner_token)
    role = next(role for role in overview["roles"] if role["code"] == "action_receiver_1c5c2e86")
    permissions = list(role["permissions"]) + [
        {"resourceType": "FLOW", "resourceCode": "SYSTEM", "actionCode": action,
         "dataScopeType": "ALL", "dataScopeJson": "{}"}
        for action in ("VIEW_RUNTIME", "HANDLE")
    ]
    saved = success("/api/admin/system/authorization/roles", "POST",
                    {"id": role["id"], "code": role["code"], "name": role["name"],
                     "description": role["description"], "permissions": permissions,
                     "fieldPolicies": role["fieldPolicies"], "expectedVersion": role["version"]},
                    owner_token)
    denied(f"/api/flow-runtime/instances/{instance_id}", "GET", None,
           receiver_token, {403}, "PERMISSION_DENIED")
    success(f"/api/admin/system/authorization/roles/{role['id']}/publish", "POST",
            {"reason": "独立审批人验证 Flow V2 任务", "expectedVersion": saved["version"]},
            owner_token)
    account = success("/api/auth/login", "POST",
                      {"username": "action_receiver_1c5c2e86", "password": "correct-password"})
    entry = success("/api/systems/143/enter", "POST", {}, account["accessToken"])
    receiver_token = entry["tokens"]["accessToken"]
    visible = success(f"/api/flow-runtime/instances/{instance_id}", token=receiver_token)
    assert "APPROVE" in visible["allowedActions"] and pending(visible, "high_review_v2") == first_task
    forbidden_start = denied("/api/flow-runtime/module-approvals", "POST",
                             {"moduleCode": MODULE_CODE, "recordId": state["v2RecordId"],
                              "idempotencyKey": "flow-condition-receiver-denied-" + str(instance_id)},
                             receiver_token, {403}, "PERMISSION_DENIED")
    first_done = approve(receiver_token, first_task, "separate-approver-" + str(instance_id))
    second_task = pending(first_done, "second_review")
    second_done = approve(owner_token, second_task, "owner-final-" + str(instance_id))
    assert second_done["status"] == "COMPLETED"
    state.update({"status": "separate_approver_verified",
                  "browserInstanceId": instance_id, "receiverTenantMemberId": 258,
                  "receiverTaskId": first_task, "ownerFinalTaskId": second_task,
                  "receiverDraftDenied": before, "receiverStartDenied": forbidden_start})
    save(state)
    print(json.dumps({"status": state["status"], "instanceId": instance_id,
                      "approverTenantMemberId": 258}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=["setup", "complete", "readback", "approver"])
    phase = parser.parse_args().phase
    {"setup": setup, "complete": version_and_complete, "readback": readback,
     "approver": separate_approver}[phase]()
