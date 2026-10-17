"""在隔离验收库验证模块审批动作、Flow 待办与重启回读。"""

import argparse
import json
import uuid
import urllib.error
import urllib.request
from pathlib import Path

import pymysql


ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "projects/unexamine/ai/evidence"
STATE = EVIDENCE / "s18-module-approval-action-live.json"
BASE = "http://127.0.0.1:18081"
SYSTEM_ID = 143
OWNER = "g7_layout_owner_6b4dee80"


def request(path, method="GET", body=None, token=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    call = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(call, timeout=30) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def success(path, method="GET", body=None, token=None):
    status, result = request(path, method, body, token)
    assert status == 200 and result.get("code") == "OK", (path, status, result)
    return result["data"]


def denied(path, method, body, token, statuses, code=None):
    status, result = request(path, method, body, token)
    assert status in statuses and (code is None or result.get("code") == code), (path, status, result)
    return {"status": status, "code": result.get("code")}


def session():
    platform = success("/api/auth/login", "POST", {"username": OWNER, "password": "correct-password"})
    entered = success(f"/api/systems/{SYSTEM_ID}/enter", "POST", {}, platform["accessToken"])
    return platform["accessToken"], entered["tokens"]["accessToken"]


def database():
    env = dict(line.split("=", 1) for line in
               (ROOT / "projects/unexamine/deploy/local.env.example").read_text(encoding="utf-8").splitlines()
               if "=" in line and not line.startswith("#"))
    return pymysql.connect(host="127.0.0.1", port=33077, user=env["APP_DB_USERNAME"],
                           password=env["APP_DB_PASSWORD"], database="unexamine_drill_s17_files_1054")


def scalar(sql, value):
    with database() as conn, conn.cursor() as cursor:
        cursor.execute(sql, (value,))
        return cursor.fetchone()[0]


def publish_module(module_id, token):
    revision = scalar("select draft_revision from un_cfg_module where id=%s", module_id)
    return success(f"/api/admin/module-config/modules/{module_id}/publish", "POST",
                   {"expectedDraftRevision": revision}, token)


def setup():
    platform_token, token = session()
    suffix = uuid.uuid4().hex[:8]
    code = "approval_accept_" + suffix
    group = success("/api/admin/module-config/groups", "POST",
                    {"code": code, "name": "审批验收", "sortOrder": 90}, token)
    module = success("/api/admin/module-config/modules", "POST",
                     {"groupId": group["id"], "code": code, "name": "审批业务"}, token)["module"]
    module_id = module["id"]
    success(f"/api/admin/module-config/modules/{module_id}/fields", "POST",
            {"code": "subject", "name": "申请主题", "fieldType": "TEXT", "required": True,
             "sortOrder": 10, "config": {}}, token)
    publish_module(module_id, token)
    record = success(f"/api/runtime/modules/{code}/records", "POST",
                     {"title": "隔离库审批申请", "participantMemberIds": [],
                      "fields": {"subject": "隔离库审批申请"}}, token)
    record_id = record["id"]
    action = success(f"/api/admin/module-config/modules/{module_id}/actions/approval", "POST", {}, token)
    runtime = success(f"/api/runtime/modules/{code}/configuration", token=token)
    assert all(item["code"] != "APPROVAL" for item in runtime["configuration"]["actions"])
    start = {"moduleCode": code, "recordId": record_id, "idempotencyKey": "approval-" + suffix}
    before_publish = denied("/api/flow-runtime/module-approvals", "POST", start, token, {409},
                            "MODULE_ACTION_NOT_PUBLISHED")
    publish_module(module_id, token)
    runtime = success(f"/api/runtime/modules/{code}/configuration", token=token)
    assert any(item["code"] == "APPROVAL" and item["status"] == "ACTIVE"
               for item in runtime["configuration"]["actions"])
    before_binding = denied("/api/flow-runtime/module-approvals", "POST", start, token, {409},
                            "MODULE_APPROVAL_FLOW_REQUIRED")

    flow = success("/api/flows", "POST", {"code": code, "name": "审批验收流程"}, token)
    flow_id = flow["id"]
    draft = success(f"/api/flows/{flow_id}/draft", "PUT", {
        "expectedVersion": 0,
        "nodes": [
            {"nodeKey": "start", "nodeType": "START", "name": "开始", "positionX": 0, "positionY": 0, "config": {}},
            {"nodeKey": "approve", "nodeType": "APPROVAL", "name": "负责人审批", "positionX": 200,
             "positionY": 0, "assigneePolicy": {"type": "RECORD_OWNER"}, "config": {}},
            {"nodeKey": "end", "nodeType": "END", "name": "结束", "positionX": 400, "positionY": 0, "config": {}},
        ],
        "edges": [
            {"edgeKey": "review", "sourceNodeKey": "start", "targetNodeKey": "approve",
             "priorityOrder": 0, "config": {}},
            {"edgeKey": "finish", "sourceNodeKey": "approve", "targetNodeKey": "end",
             "priorityOrder": 0, "config": {}},
        ],
    }, token)
    success(f"/api/flows/{flow_id}/publish", "POST",
            {"expectedDraftRevision": draft["draftRevision"], "changeSummary": "模块审批验收"}, token)
    invalid_binding = denied("/api/flow-bindings/publish", "POST",
                             {"moduleId": module_id, "actionId": action["id"], "flowId": flow_id,
                              "triggerEvent": "MANUAL", "executionMode": "DIRECT", "priorityOrder": 0},
                             token, {422}, "MODULE_APPROVAL_BINDING_INVALID")
    binding = success("/api/flow-bindings/publish", "POST",
                      {"moduleId": module_id, "actionId": action["id"], "flowId": flow_id,
                       "triggerEvent": "MANUAL", "executionMode": "FLOW_ONLY",
                       "priorityOrder": 0, "mutuallyExclusive": True, "replaceExisting": True}, token)
    started = success("/api/flow-runtime/module-approvals", "POST", start, token)
    instance_id = started["instance"]["id"]
    replay = success("/api/flow-runtime/module-approvals", "POST", start, token)
    assert replay["replayed"] and replay["instance"]["id"] == instance_id
    platform_denial = denied("/api/flow-runtime/module-approvals", "POST", start,
                             platform_token, {400, 403, 404, 409, 422})
    with database() as conn, conn.cursor() as cursor:
        cursor.execute("select system_id, tenant_id, flow_id, business_type, business_id "
                       "from un_flow_instance where id=%s", (instance_id,))
        instance = list(cursor.fetchone())
        cursor.execute("select id, status from un_flow_task where instance_id=%s", (instance_id,))
        tasks = [list(item) for item in cursor.fetchall()]
        cursor.execute("select title, deleted, archived from un_biz_record where id=%s", (record_id,))
        unchanged_record = list(cursor.fetchone())
    assert instance[0] == SYSTEM_ID and instance[2:] == [flow_id, "MODULE:" + code, str(record_id)]
    assert len(tasks) == 1 and tasks[0][1] == "PENDING"
    assert unchanged_record == ["隔离库审批申请", 0, 0]
    state = {
        "status": "verified_live_before_restart", "systemId": SYSTEM_ID,
        "tenantId": instance[1], "moduleId": module_id, "moduleCode": code,
        "recordId": record_id, "actionId": action["id"], "flowId": flow_id,
        "bindingId": binding["id"], "instanceId": instance_id, "taskId": tasks[0][0],
        "draftDenied": before_publish, "unboundDenied": before_binding,
        "invalidBindingDenied": invalid_binding, "platformDenied": platform_denial,
        "instance": instance, "record": unchanged_record,
        "replayed": replay["replayed"],
    }
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state, ensure_ascii=False))


def lifecycle():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    _, token = session()
    module_id = state["moduleId"]
    record_id = state["recordId"]
    code = state["moduleCode"]
    action_id = state["actionId"]
    with database() as conn, conn.cursor() as cursor:
        cursor.execute("select id, snapshot_json from un_cfg_module_version where module_id=%s "
                       "order by version_number desc", (module_id,))
        active_version_id = next(version_id for version_id, raw in cursor.fetchall()
                                 if any(action.get("code") == "APPROVAL" and action.get("status") == "ACTIVE"
                                        for action in json.loads(raw).get("actions", [])))
        cursor.execute("select version from un_cfg_module_action where id=%s", (action_id,))
        action_version = cursor.fetchone()[0]
        cursor.execute("select id, flow_version_id, business_snapshot_json, runtime_snapshot_json, status "
                       "from un_flow_instance where id in (%s,%s) order by id",
                       (state["instanceId"], state["restart"]["browserInstanceId"]))
        old_instances = [list(row) for row in cursor.fetchall()]
    draft_runtime = success(f"/api/runtime/modules/{code}/configuration", token=token)
    if any(item["code"] == "APPROVAL" and item["status"] == "ACTIVE"
           for item in draft_runtime["configuration"]["actions"]):
        success(f"/api/admin/module-config/modules/{module_id}/actions/APPROVAL", "PUT",
                {"location": "DETAIL_HEADER", "config": {}, "status": "DISABLED", "version": action_version}, token)
        isolated_runtime = success(f"/api/runtime/modules/{code}/configuration", token=token)
        assert any(item["code"] == "APPROVAL" and item["status"] == "ACTIVE"
                   for item in isolated_runtime["configuration"]["actions"])
        publish_module(module_id, token)
    disabled = denied("/api/flow-runtime/module-approvals", "POST",
                      {"moduleCode": code, "recordId": record_id,
                       "idempotencyKey": "approval-disabled-" + str(module_id)},
                      token, {409}, "MODULE_ACTION_NOT_PUBLISHED")
    record = success(f"/api/runtime/modules/{code}/records/{record_id}", token=token)
    assert all(item["code"] != "APPROVAL" for item in record["actions"])
    publication_version = scalar("select version from un_cfg_module_publication where module_id=%s", module_id)
    success(f"/api/admin/module-config/modules/{module_id}/rollback", "POST",
            {"targetVersionId": active_version_id,
             "expectedPublicationVersion": publication_version}, token)
    restored_record = success(f"/api/runtime/modules/{code}/records/{record_id}", token=token)
    assert any(item["code"] == "APPROVAL" for item in restored_record["actions"])
    restored = success("/api/flow-runtime/module-approvals", "POST",
                       {"moduleCode": code, "recordId": record_id,
                        "idempotencyKey": "approval-restored-" + str(module_id)}, token)
    with database() as conn, conn.cursor() as cursor:
        cursor.execute("select id, flow_version_id, business_snapshot_json, runtime_snapshot_json, status "
                       "from un_flow_instance where id in (%s,%s) order by id",
                       (state["instanceId"], state["restart"]["browserInstanceId"]))
        assert [list(row) for row in cursor.fetchall()] == old_instances
    state["lifecycle"] = {"draftIsolation": True, "disabled": disabled,
                          "rollbackTargetVersionId": active_version_id,
                          "restoredInstanceId": restored["instance"]["id"],
                          "oldInstancesUnchanged": True}
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state["lifecycle"], ensure_ascii=False))


def restart():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    _, token = session()
    instance = success(f"/api/flow-runtime/instances/{state['instanceId']}", token=token)
    record = success(f"/api/runtime/modules/{state['moduleCode']}/records/{state['recordId']}", token=token)
    with database() as conn, conn.cursor() as cursor:
        cursor.execute("select status from un_flow_task where id=%s and instance_id=%s",
                       (state["taskId"], state["instanceId"]))
        task_status = cursor.fetchone()[0]
    assert instance["id"] == state["instanceId"] and record["id"] == state["recordId"]
    assert task_status == "PENDING"
    browser = json.loads((EVIDENCE / "s18-module-approval-browser-readback.json").read_text(encoding="utf-8"))
    browser_instance = success(f"/api/flow-runtime/instances/{browser['instanceId']}", token=token)
    assert browser_instance["businessId"] == str(state["recordId"])
    restored_id = state["lifecycle"]["restoredInstanceId"]
    restored_instance = success(f"/api/flow-runtime/instances/{restored_id}", token=token)
    assert restored_instance["businessId"] == str(state["recordId"])
    with database() as conn, conn.cursor() as cursor:
        cursor.execute("select count(*) from un_flow_task where instance_id=%s and status='PENDING'",
                       (browser["instanceId"],))
        assert cursor.fetchone()[0] == 1
    state["status"] = "verified_live_after_restart"
    state["restart"] = {"instanceStatus": instance["status"], "taskStatus": task_status,
                        "recordTitle": record["title"],
                        "browserInstanceId": browser["instanceId"],
                        "browserInstanceStatus": browser_instance["status"],
                        "restoredInstanceId": restored_id,
                        "restoredInstanceStatus": restored_instance["status"]}
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state["restart"], ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("setup", "lifecycle", "restart"))
    arguments = parser.parse_args()
    {"setup": setup, "lifecycle": lifecycle, "restart": restart}[arguments.phase]()
