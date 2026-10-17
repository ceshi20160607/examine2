"""在隔离验收库验证模块仪表盘入口的发布、停用、回滚与重启读回。"""

import argparse
import json
import uuid
import urllib.error
import urllib.request
from pathlib import Path

import pymysql


ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "projects/unexamine/ai/evidence"
STATE = EVIDENCE / "s18-module-dashboard-action-live.json"
APPROVAL = EVIDENCE / "s18-module-approval-action-live.json"
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


def denied(path, method, body, token, status, code=None):
    actual, result = request(path, method, body, token)
    assert actual == status and (code is None or result.get("code") == code), (path, actual, result)
    return {"status": actual, "code": result.get("code")}


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


def current_version(module_id):
    return scalar("select version from un_cfg_module_publication where module_id=%s", module_id)


def publish_action(module_id, action, token):
    return success(f"/api/admin/module-config/modules/{module_id}/dashboard-action-publish"
                   f"?expectedPublicationVersion={current_version(module_id)}"
                   f"&expectedActionVersion={action['version']}", "POST", token=token)


def dashboard(module_id, code, name, module_code, token):
    component = {"componentKey": "records", "componentType": "QUICK_ENTRY", "title": "进入业务记录",
                 "dataSourceId": None, "layout": {"width": 1}, "queryParameters": {},
                 "displayConfig": {"refreshSeconds": 60, "accentColor": "#315efb"},
                 "drillTarget": {"moduleCode": module_code}, "sortOrder": 0}
    created = success("/api/analytics/admin/dashboards", "POST",
                      {"code": code, "name": name, "description": "隔离库入口验收",
                       "dashboardKind": "MODULE_HOME", "moduleId": module_id,
                       "components": [component]}, token)
    success(f"/api/analytics/admin/dashboards/{created['id']}/publish", "POST",
            {"expectedDraftRevision": created["draftRevision"]}, token)
    return created["id"]


def setup():
    platform, token = session()
    approval = json.loads(APPROVAL.read_text(encoding="utf-8"))
    old_id, old_code = approval["moduleId"], approval["moduleCode"]
    old_url = f"/api/analytics/runtime?moduleCode={old_code}"
    suffix = uuid.uuid4().hex[:8]
    status, _ = request(old_url, token=token)
    if status == 409:
        before = denied(old_url, "GET", None, token, 409, "DASHBOARD_MODULE_ACTION_NOT_PUBLISHED")
        old_dashboard_id = dashboard(old_id, "approval_dashboard_" + suffix, "审批业务首页", old_code, token)
        denied(old_url, "GET", None, token, 409, "DASHBOARD_MODULE_ACTION_NOT_PUBLISHED")
        old_action = success(f"/api/admin/module-config/modules/{old_id}/actions/dashboard", "POST", {}, token)
        success(f"/api/admin/module-config/modules/{old_id}/fields", "POST",
                {"code": "future_dashboard_note", "name": "待发布首页备注", "fieldType": "TEXT",
                 "required": False, "searchable": False, "sortOrder": 90, "config": {}}, token)
        runtime_before = success(f"/api/runtime/modules/{old_code}/configuration", token=token)
        assert all(action["code"] != "DASHBOARD" for action in runtime_before["configuration"]["actions"])
        assert any(action["code"] == "APPROVAL" for action in runtime_before["configuration"]["actions"])
        enabled = publish_action(old_id, old_action, token)
    else:
        # 上次执行已完成入口发布，继续后续阶段；先前缺失动作和草稿隔离的断言已通过。
        assert status == 200
        before = {"status": 409, "code": "DASHBOARD_MODULE_ACTION_NOT_PUBLISHED"}
        old_dashboard_id = scalar("select max(id) from un_ana_dashboard where module_id=%s", old_id)
        old_action = {"version": scalar(
            "select version from un_cfg_module_action where module_id=%s and code='DASHBOARD'", old_id)}
        enabled = {"versionId": scalar(
            "select current_version_id from un_cfg_module_publication where module_id=%s", old_id)}
    runtime_enabled = success(f"/api/runtime/modules/{old_code}/configuration", token=token)
    assert any(action["code"] == "DASHBOARD" and action["status"] == "ACTIVE"
               for action in runtime_enabled["configuration"]["actions"])
    assert any(action["code"] == "APPROVAL" for action in runtime_enabled["configuration"]["actions"])
    assert all(field["code"] != "future_dashboard_note" for field in runtime_enabled["configuration"]["fields"])
    assert success(old_url, token=token)["configured"] is True
    denied(old_url, "GET", None, platform, 422, "DASHBOARD_MODULE_INVALID")
    flow = success("/api/flow-runtime/module-approvals", "POST",
                   {"moduleCode": old_code, "recordId": approval["recordId"],
                    "idempotencyKey": "dashboard-preserves-approval-" + suffix}, token)
    assert flow["instance"]["businessId"] == str(approval["recordId"])

    disabled_action = success(f"/api/admin/module-config/modules/{old_id}/actions/DASHBOARD", "PUT",
                              {"location": "MODULE_ENTRY", "config": {}, "status": "DISABLED",
                               "version": old_action["version"]}, token)
    assert success(old_url, token=token)["configured"] is True
    publish_action(old_id, disabled_action, token)
    disabled = denied(old_url, "GET", None, token, 409, "DASHBOARD_MODULE_ACTION_NOT_PUBLISHED")
    success(f"/api/admin/module-config/modules/{old_id}/rollback", "POST",
            {"targetVersionId": enabled["versionId"], "expectedPublicationVersion": current_version(old_id)}, token)
    assert success(old_url, token=token)["configured"] is True
    assert scalar("select status from un_flow_instance where id=%s", flow["instance"]["id"]) == "WAITING"

    new_code = "dashboard_accept_" + suffix
    group = success("/api/admin/module-config/groups", "POST",
                    {"code": new_code, "name": "仪表盘验收", "sortOrder": 95}, token)
    module = success("/api/admin/module-config/modules", "POST",
                     {"groupId": group["id"], "code": new_code, "name": "经营活动"}, token)["module"]
    new_id = module["id"]
    success(f"/api/admin/module-config/modules/{new_id}/fields", "POST",
            {"code": "activity", "name": "活动名称", "fieldType": "TEXT", "required": True,
             "searchable": True, "sortOrder": 10, "config": {}}, token)
    success(f"/api/admin/module-config/modules/{new_id}/actions/dashboard", "POST", {}, token)
    check = success(f"/api/admin/module-config/modules/{new_id}/publication-check", token=token)
    assert check["valid"] is True
    success(f"/api/admin/module-config/modules/{new_id}/publish", "POST",
            {"expectedDraftRevision": check["draftRevision"]}, token)
    new_url = f"/api/analytics/runtime?moduleCode={new_code}"
    assert success(new_url, token=token)["configured"] is False
    new_dashboard_id = dashboard(new_id, "new_dashboard_" + suffix, "经营活动首页", new_code, token)
    assert success(new_url, token=token)["configured"] is True
    outsider = success("/api/auth/register", "POST",
                       {"username": "dashboard_other_" + suffix, "password": "correct-password",
                        "displayName": "隔离库外部用户", "email": "dashboard-other-" + suffix + "@example.com",
                        "systemName": "外部系统", "systemCode": "dashboard_other_" + suffix})
    wrong_system = denied(old_url, "GET", None, outsider["tokens"]["accessToken"], 404)
    state = {"status": "verified_before_browser", "systemId": SYSTEM_ID, "tenantId": approval["tenantId"],
             "existingModuleId": old_id, "existingModuleCode": old_code, "existingDashboardId": old_dashboard_id,
             "newModuleId": new_id, "newModuleCode": new_code, "newDashboardId": new_dashboard_id,
             "approvalInstanceId": flow["instance"]["id"], "draftIsolation": True,
             "missingAction": before, "disabledAction": disabled, "wrongSystem": wrong_system,
             "rollbackTargetVersionId": enabled["versionId"]}
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state, ensure_ascii=False))


def restart():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    _, token = session()
    old = success(f"/api/analytics/runtime?moduleCode={state['existingModuleCode']}", token=token)
    new = success(f"/api/analytics/runtime?moduleCode={state['newModuleCode']}", token=token)
    assert old["configured"] and new["configured"]
    assert old["dashboardId"] == state["existingDashboardId"]
    assert new["dashboardId"] == state["newDashboardId"]
    flow_status = scalar("select status from un_flow_instance where id=%s", state["approvalInstanceId"])
    assert flow_status == "WAITING"
    state["restart"] = {"existingDashboardVersionId": old["versionId"],
                        "newDashboardVersionId": new["versionId"], "approvalStatus": flow_status}
    state["status"] = "verified_live_after_restart"
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state["restart"], ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("setup", "restart"))
    arguments = parser.parse_args()
    {"setup": setup, "restart": restart}[arguments.phase]()
