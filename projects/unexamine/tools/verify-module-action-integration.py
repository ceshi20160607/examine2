"""在隔离库同一模块核对打印、审批和模块首页的发布、停用、回滚及历史快照。"""

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
STATE = EVIDENCE / "s18-module-action-integration-live.json"
helpers = runpy.run_path(str(ROOT / "projects/unexamine/tools/verify-module-approval-action.py"))
success = helpers["success"]
denied = helpers["denied"]
session = helpers["session"]
database = helpers["database"]
publish_module = helpers["publish_module"]
scalar = helpers["scalar"]
MODULE_ID = 94
MODULE_CODE = "dashboard_accept_1a4547cf"


def snapshot(sql, value):
    with database() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (value,))
        return list(cursor.fetchone())


def pdf_bytes(job_id, token):
    url = helpers["success"].__globals__["BASE"] + f"/api/print/jobs/{job_id}/preview"
    call = urllib.request.Request(url, headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(call, timeout=30) as response:
        assert response.status == 200 and response.headers.get_content_type() == "application/pdf"
        return response.read()


def save(state):
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def setup():
    assert not STATE.exists(), "组合验收已有状态，避免重复写入"
    _, token = session()
    suffix = uuid.uuid4().hex[:8]
    published = success(f"/api/runtime/modules/{MODULE_CODE}/configuration", token=token)
    assert published["moduleId"] == MODULE_ID
    assert {item["code"] for item in published["configuration"]["actions"]} >= {"LIST", "DASHBOARD"}
    assert not {item["code"] for item in published["configuration"]["actions"]} & {"PRINT", "APPROVAL"}
    baseline_dashboard = success(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", token=token)
    assert baseline_dashboard["configured"]
    record = success(f"/api/runtime/modules/{MODULE_CODE}/records", "POST", {
        "title": "组合动作采购申请", "participantMemberIds": [],
        "fields": {"activity": "同一模块打印审批首页验收"},
    }, token)
    print_action = success(f"/api/admin/module-config/modules/{MODULE_ID}/actions/print", "POST", {}, token)
    approval_action = success(f"/api/admin/module-config/modules/{MODULE_ID}/actions/approval", "POST", {}, token)
    draft_runtime = success(f"/api/runtime/modules/{MODULE_CODE}/configuration", token=token)
    assert not {item["code"] for item in draft_runtime["configuration"]["actions"]} & {"PRINT", "APPROVAL"}
    active = publish_module(MODULE_ID, token)
    runtime = success(f"/api/runtime/modules/{MODULE_CODE}/configuration", token=token)
    assert {item["code"] for item in runtime["configuration"]["actions"]} >= {"PRINT", "APPROVAL", "DASHBOARD"}
    assert success(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", token=token)["dashboardId"] == baseline_dashboard["dashboardId"]

    template = success("/api/print/admin/templates", "POST", {
        "moduleId": MODULE_ID, "code": "action_combo_" + suffix, "name": "组合动作打印单",
        "pageSize": "A4", "orientation": "PORTRAIT",
        "layout": {"header": "组合动作验收", "footer": "隔离测试", "signatureLabel": "经办人",
                   "fieldCodes": ["activity"], "detailFieldCodes": [], "rowsPerPage": 4,
                   "offsetXmm": 0, "offsetYmm": 0},
    }, token)
    template_publication = success(f"/api/print/admin/templates/{template['id']}/publish", "POST",
                                   {"expectedDraftRevision": template["draftRevision"]}, token)
    flow = success("/api/flows", "POST", {"code": "action_combo_" + suffix, "name": "组合动作审批"}, token)
    draft = success(f"/api/flows/{flow['id']}/draft", "PUT", {
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
    flow_publication = success(f"/api/flows/{flow['id']}/publish", "POST",
                               {"expectedDraftRevision": draft["draftRevision"],
                                "changeSummary": "同一模块动作组合验收"}, token)
    binding = success("/api/flow-bindings/publish", "POST", {
        "moduleId": MODULE_ID, "actionId": approval_action["id"], "flowId": flow["id"],
        "triggerEvent": "MANUAL", "executionMode": "FLOW_ONLY", "priorityOrder": 0,
        "mutuallyExclusive": True, "replaceExisting": True,
    }, token)
    print_job = success(f"/api/print/runtime/modules/{MODULE_CODE}/records/{record['id']}/templates/{template['id']}",
                        "POST", {}, token)
    assert print_job["status"] == "SUCCEEDED"
    pdf_sha = hashlib.sha256(pdf_bytes(print_job["id"], token)).hexdigest()
    approval = success("/api/flow-runtime/module-approvals", "POST", {
        "moduleCode": MODULE_CODE, "recordId": record["id"],
        "idempotencyKey": "action-combo-" + suffix,
    }, token)
    instance_id = approval["instance"]["id"]
    assert approval["instance"]["status"] == "WAITING"
    state = {
        "status": "three_actions_live", "moduleId": MODULE_ID, "moduleCode": MODULE_CODE,
        "recordId": record["id"], "printActionId": print_action["id"],
        "approvalActionId": approval_action["id"], "templateId": template["id"],
        "templateVersionId": template_publication["versionId"], "flowId": flow["id"],
        "flowVersionId": flow_publication["versionId"], "bindingId": binding["id"],
        "printJobId": print_job["id"], "pdfSha256": pdf_sha,
        "approvalInstanceId": instance_id, "dashboardId": baseline_dashboard["dashboardId"],
        "dashboardVersionId": baseline_dashboard["versionId"],
        "activeModuleVersionId": active["versionId"],
        "flowSnapshot": snapshot("SELECT flow_version_id,business_snapshot_json,runtime_snapshot_json,status "
                                 "FROM un_flow_instance WHERE id=%s", instance_id),
        "printSnapshot": snapshot("SELECT template_version_id,module_config_version_id,status,output_file_id "
                                  "FROM un_print_job WHERE id=%s", print_job["id"]),
    }
    save(state)
    print(json.dumps({"status": state["status"], "moduleId": MODULE_ID, "recordId": record["id"],
                      "printJobId": print_job["id"], "approvalInstanceId": instance_id,
                      "dashboardId": state["dashboardId"]}))


def lifecycle():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "three_actions_live"
    _, token = session()
    action = next(item for item in success(f"/api/admin/module-config/modules/{MODULE_ID}/draft", token=token)["actions"]
                  if item["code"] == "DASHBOARD")
    success(f"/api/admin/module-config/modules/{MODULE_ID}/actions/DASHBOARD", "PUT",
            {"location": "MODULE_ENTRY", "config": {}, "status": "DISABLED", "version": action["version"]}, token)
    assert success(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", token=token)["dashboardId"] == state["dashboardId"]
    publish_module(MODULE_ID, token)
    denied(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", "GET", None, token, {409},
           "DASHBOARD_MODULE_ACTION_NOT_PUBLISHED")
    runtime = success(f"/api/runtime/modules/{MODULE_CODE}/configuration", token=token)
    assert {item["code"] for item in runtime["configuration"]["actions"] if item["status"] == "ACTIVE"} >= {"PRINT", "APPROVAL"}
    assert hashlib.sha256(pdf_bytes(state["printJobId"], token)).hexdigest() == state["pdfSha256"]
    assert snapshot("SELECT flow_version_id,business_snapshot_json,runtime_snapshot_json,status "
                    "FROM un_flow_instance WHERE id=%s", state["approvalInstanceId"]) == state["flowSnapshot"]
    assert snapshot("SELECT template_version_id,module_config_version_id,status,output_file_id "
                    "FROM un_print_job WHERE id=%s", state["printJobId"]) == state["printSnapshot"]
    extra = success("/api/flow-runtime/module-approvals", "POST", {
        "moduleCode": MODULE_CODE, "recordId": state["recordId"],
        "idempotencyKey": "action-combo-disabled-dashboard-" + str(state["recordId"]),
    }, token)
    assert extra["instance"]["status"] == "WAITING"
    current = scalar("SELECT version FROM un_cfg_module_publication WHERE module_id=%s", MODULE_ID)
    success(f"/api/admin/module-config/modules/{MODULE_ID}/rollback", "POST", {
        "targetVersionId": state["activeModuleVersionId"], "expectedPublicationVersion": current,
    }, token)
    assert success(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", token=token)["dashboardId"] == state["dashboardId"]
    assert hashlib.sha256(pdf_bytes(state["printJobId"], token)).hexdigest() == state["pdfSha256"]
    assert snapshot("SELECT flow_version_id,business_snapshot_json,runtime_snapshot_json,status "
                    "FROM un_flow_instance WHERE id=%s", state["approvalInstanceId"]) == state["flowSnapshot"]
    state.update({"status": "disable_rollback_verified", "approvalDuringDashboardDisable": extra["instance"]["id"],
                  "otherActionsPreserved": True, "historicalSnapshotsPreserved": True})
    save(state)
    print(json.dumps({"status": state["status"], "approvalDuringDashboardDisable": extra["instance"]["id"]}))


def restarted():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "disable_rollback_verified"
    helpers["success"].__globals__["BASE"] = os.environ.get("UPGRADE_READBACK_BASE", "http://127.0.0.1:18082")
    _, token = session()
    runtime = success(f"/api/runtime/modules/{MODULE_CODE}/configuration", token=token)
    assert {item["code"] for item in runtime["configuration"]["actions"] if item["status"] == "ACTIVE"} >= {
        "PRINT", "APPROVAL", "DASHBOARD"}
    assert success(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", token=token)["dashboardId"] == state["dashboardId"]
    assert hashlib.sha256(pdf_bytes(state["printJobId"], token)).hexdigest() == state["pdfSha256"]
    assert snapshot("SELECT flow_version_id,business_snapshot_json,runtime_snapshot_json,status "
                    "FROM un_flow_instance WHERE id=%s", state["approvalInstanceId"]) == state["flowSnapshot"]
    state["status"] = "new_jvm_verified"
    save(state)
    print(json.dumps({"status": state["status"], "moduleId": MODULE_ID, "printJobId": state["printJobId"]}))


def permissions():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "new_jvm_verified"
    helpers["success"].__globals__["BASE"] = "http://127.0.0.1:18081"
    account = success("/api/auth/login", "POST", {
        "username": "g7_layout_member_6b4dee80", "password": "correct-password"})
    entered = success("/api/systems/143/enter", "POST", {}, account["accessToken"])
    token = entered["tokens"]["accessToken"]
    checks = {
        "configuration": denied(f"/api/runtime/modules/{MODULE_CODE}/configuration", "GET", None, token,
                                {403}, "PERMISSION_DENIED"),
        "dashboard": denied(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", "GET", None, token,
                            {403}, "DASHBOARD_MODULE_FORBIDDEN"),
        "templates": denied(f"/api/print/runtime/modules/{MODULE_CODE}/templates", "GET", None, token,
                            {403}, "PERMISSION_DENIED"),
        "record": denied(f"/api/runtime/modules/{MODULE_CODE}/records/{state['recordId']}", "GET", None, token,
                         {403}, "PERMISSION_DENIED"),
        "approval": denied("/api/flow-runtime/module-approvals", "POST", {
            "moduleCode": MODULE_CODE, "recordId": state["recordId"],
            "idempotencyKey": "member-denied-action-combo",
        }, token, {403}, "PERMISSION_DENIED"),
    }
    state["permissionDenial"] = checks
    state["status"] = "integrated_api_verified"
    save(state)
    print(json.dumps({"status": state["status"], "deniedEntrypoints": list(checks)}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("setup", "lifecycle", "restarted", "permissions"))
    args = parser.parse_args()
    {"setup": setup, "lifecycle": lifecycle, "restarted": restarted,
     "permissions": permissions}[args.phase]()
