"""隔离库 V22→V23 模块首页兼容：先在 V22 固定旧页面，再升级并读回。"""

import argparse
import hashlib
import json
import os
import runpy
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "projects/unexamine/ai/evidence"
STATE = EVIDENCE / "s18-module-dashboard-upgrade-live.json"
helpers = runpy.run_path(str(ROOT / "projects/unexamine/tools/verify-module-dashboard-action.py"))
success = helpers["success"]
denied = helpers["denied"]
session = helpers["session"]
database = helpers["database"]
dashboard = helpers["dashboard"]
publish_action = helpers["publish_action"]
current_version = helpers["current_version"]
MODULE_ID = 68
MODULE_CODE = "g7_layout"
MEMBER = "g7_layout_member_6b4dee80"


def one(sql, *values):
    with database() as conn, conn.cursor() as cursor:
        cursor.execute(sql, values)
        return cursor.fetchone()[0]


def digest(value):
    return hashlib.sha256(str(value).encode("utf-8")).hexdigest()


def module_snapshot():
    with database() as conn, conn.cursor() as cursor:
        cursor.execute("""SELECT p.current_version_id, v.snapshot_json, m.draft_revision
                        FROM un_cfg_module_publication p
                        JOIN un_cfg_module_version v ON v.id=p.current_version_id
                        JOIN un_cfg_module m ON m.id=p.module_id WHERE p.module_id=%s""", (MODULE_ID,))
        return cursor.fetchone()


def dashboard_snapshot(dashboard_id):
    with database() as conn, conn.cursor() as cursor:
        cursor.execute("""SELECT p.current_version_id, v.snapshot_json, d.draft_revision
                        FROM un_ana_dashboard_publication p
                        JOIN un_ana_dashboard_version v ON v.id=p.current_version_id
                        JOIN un_ana_dashboard d ON d.id=p.dashboard_id WHERE p.dashboard_id=%s""",
                       (dashboard_id,))
        return cursor.fetchone()


def prepare():
    assert one("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1") == 22
    _, token = session()
    module_version, module_json, module_draft = module_snapshot()
    assert all(action.get("code") != "DASHBOARD" for action in json.loads(module_json)["actions"])
    assert one("SELECT COUNT(*) FROM un_ana_dashboard WHERE module_id=%s", MODULE_ID) == 0
    dashboard_id = dashboard(MODULE_ID, "g7_legacy_module_home", "排版业务旧首页", MODULE_CODE, token)
    dashboard_version, dashboard_json, dashboard_draft = dashboard_snapshot(dashboard_id)
    denied(f"/api/analytics/runtime?moduleCode={MODULE_CODE}", "GET", None, token, 409,
           "DASHBOARD_MODULE_ACTION_NOT_PUBLISHED")
    state = {"status": "v22_fixture_ready", "systemId": 143, "tenantId": 144,
             "moduleId": MODULE_ID, "moduleCode": MODULE_CODE,
             "legacyDashboardId": dashboard_id, "sourceModuleVersionId": module_version,
             "sourceDashboardVersionId": dashboard_version,
             "moduleSnapshotSha256": digest(module_json), "dashboardSnapshotSha256": digest(dashboard_json),
             "moduleDraftRevision": module_draft, "dashboardDraftRevision": dashboard_draft,
             "printJobId": 130, "printJobStatus": one("SELECT status FROM un_print_job WHERE id=%s", 130),
             "recordId": 121, "recordTitle": one("SELECT title FROM un_biz_record WHERE id=%s", 121)}
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(state, ensure_ascii=False))


def upgraded():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert one("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1") == 23
    assert one("SELECT COUNT(*) FROM un_ana_legacy_module_dashboard WHERE dashboard_id=%s",
               state["legacyDashboardId"]) == 1
    assert one("SELECT source_module_version_id FROM un_ana_legacy_module_dashboard WHERE dashboard_id=%s",
               state["legacyDashboardId"]) == state["sourceModuleVersionId"]
    assert one("SELECT source_dashboard_version_id FROM un_ana_legacy_module_dashboard WHERE dashboard_id=%s",
               state["legacyDashboardId"]) == state["sourceDashboardVersionId"]
    module_version, module_json, module_draft = module_snapshot()
    dashboard_version, dashboard_json, dashboard_draft = dashboard_snapshot(state["legacyDashboardId"])
    assert module_version == state["sourceModuleVersionId"]
    assert dashboard_version == state["sourceDashboardVersionId"]
    assert digest(module_json) == state["moduleSnapshotSha256"]
    assert digest(dashboard_json) == state["dashboardSnapshotSha256"]
    assert module_draft == state["moduleDraftRevision"] and dashboard_draft == state["dashboardDraftRevision"]
    assert one("SELECT status FROM un_print_job WHERE id=%s", state["printJobId"]) == state["printJobStatus"]
    assert one("SELECT title FROM un_biz_record WHERE id=%s", state["recordId"]) == state["recordTitle"]
    _, owner = session()
    member_platform = success("/api/auth/login", "POST", {"username": MEMBER, "password": "correct-password"})
    member = success(f"/api/systems/{state['systemId']}/enter", "POST", {}, member_platform["accessToken"])
    member_token = member["tokens"]["accessToken"]
    old_url = f"/api/analytics/runtime?moduleCode={MODULE_CODE}"
    owner_runtime = success(old_url, token=owner)
    member_runtime = success(old_url, token=member_token)
    assert owner_runtime["dashboardId"] == state["legacyDashboardId"]
    assert member_runtime["dashboardId"] == state["legacyDashboardId"]
    member_configuration = success(f"/api/runtime/modules/{MODULE_CODE}/configuration", token=member_token)
    assert any(action["code"] == "DASHBOARD" and action["legacyDashboardSynthetic"]
               for action in member_configuration["configuration"]["actions"])
    state["status"] = "v23_legacy_readback"
    state["memberLegacyVisible"] = True
    state["legacyActionSynthetic"] = True
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"dashboardId": state["legacyDashboardId"], "memberLegacyVisible": True}, ensure_ascii=False))


def lifecycle():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "v23_legacy_readback"
    _, owner = session()
    member_platform = success("/api/auth/login", "POST", {"username": MEMBER, "password": "correct-password"})
    member = success(f"/api/systems/{state['systemId']}/enter", "POST", {}, member_platform["accessToken"])
    member_token = member["tokens"]["accessToken"]
    member_context = success("/api/auth/me", token=member_token)
    grants = member_context["permissions"]
    assert any(item["resourceType"] == "MODULE" and item["resourceCode"] == MODULE_CODE
               and item["actionCode"] == "LIST" for item in grants)
    assert not any(item["resourceType"] == "MODULE" and item["resourceCode"] in (MODULE_CODE, "*")
                   and item["actionCode"] in ("DASHBOARD", "*") for item in grants)
    old_url = f"/api/analytics/runtime?moduleCode={MODULE_CODE}"
    suffix = uuid.uuid4().hex[:8]
    new_id = dashboard(MODULE_ID, "g7_new_module_home_" + suffix, "排版业务新版首页", MODULE_CODE, owner)
    assert success(old_url, token=owner)["dashboardId"] == state["legacyDashboardId"]
    assert success(old_url, token=member_token)["dashboardId"] == state["legacyDashboardId"]

    old_actions = success(f"/api/runtime/modules/{MODULE_CODE}/configuration", token=owner)["configuration"]["actions"]
    assert any(item["code"] == "PRINT" for item in old_actions)
    action = success(f"/api/admin/module-config/modules/{MODULE_ID}/actions/dashboard", "POST", {}, owner)
    enabled = publish_action(MODULE_ID, action, owner)
    owner_current = success(old_url, token=owner)
    member_current = success(old_url, token=member_token)
    assert owner_current["dashboardId"] == new_id
    assert member_current["dashboardId"] == state["legacyDashboardId"]
    current_actions = success(f"/api/runtime/modules/{MODULE_CODE}/configuration", token=owner)["configuration"]["actions"]
    assert any(item["code"] == "PRINT" for item in current_actions)
    assert any(item["code"] == "DASHBOARD" and item["status"] == "ACTIVE"
               and not item.get("legacyDashboardSynthetic", False) for item in current_actions)

    disabled_action = success(f"/api/admin/module-config/modules/{MODULE_ID}/actions/DASHBOARD", "PUT",
                              {"location": "MODULE_ENTRY", "config": {}, "status": "DISABLED",
                               "version": action["version"]}, owner)
    assert success(old_url, token=member_token)["dashboardId"] == state["legacyDashboardId"]
    publish_action(MODULE_ID, disabled_action, owner)
    denied(old_url, "GET", None, owner, 409, "DASHBOARD_MODULE_ACTION_NOT_PUBLISHED")
    denied(old_url, "GET", None, member_token, 409, "DASHBOARD_MODULE_ACTION_NOT_PUBLISHED")
    success(f"/api/admin/module-config/modules/{MODULE_ID}/rollback", "POST",
            {"targetVersionId": enabled["versionId"], "expectedPublicationVersion": current_version(MODULE_ID)}, owner)
    assert success(old_url, token=owner)["dashboardId"] == new_id
    assert success(old_url, token=member_token)["dashboardId"] == state["legacyDashboardId"]
    assert one("SELECT status FROM un_print_job WHERE id=%s", state["printJobId"]) == state["printJobStatus"]
    assert digest(one("SELECT snapshot_json FROM un_cfg_module_version WHERE id=%s",
                      state["sourceModuleVersionId"])) == state["moduleSnapshotSha256"]
    state.update({"status": "v23_lifecycle_verified", "newDashboardId": new_id,
                  "activeActionVersionId": enabled["versionId"], "listOnlyMemberSeesLegacy": True,
                  "ownerSeesNewDashboard": True, "disabledDeniesBoth": True, "rollbackRestoresBoth": True})
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"legacyDashboardId": state["legacyDashboardId"], "newDashboardId": new_id,
                      "listOnlyMemberSeesLegacy": True, "rollbackVersionId": enabled["versionId"]}, ensure_ascii=False))


def restarted():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "v23_lifecycle_verified"
    helpers["success"].__globals__["BASE"] = os.environ.get("UPGRADE_READBACK_BASE", "http://127.0.0.1:18081")
    _, owner = session()
    member_platform = success("/api/auth/login", "POST", {"username": MEMBER, "password": "correct-password"})
    member = success(f"/api/systems/{state['systemId']}/enter", "POST", {}, member_platform["accessToken"])
    member_token = member["tokens"]["accessToken"]
    runtime_url = f"/api/analytics/runtime?moduleCode={MODULE_CODE}"
    assert success(runtime_url, token=owner)["dashboardId"] == state["newDashboardId"]
    assert success(runtime_url, token=member_token)["dashboardId"] == state["legacyDashboardId"]
    assert one("SELECT COUNT(*) FROM un_ana_legacy_module_dashboard WHERE dashboard_id=%s",
               state["legacyDashboardId"]) == 1
    assert one("SELECT status FROM un_print_job WHERE id=%s", state["printJobId"]) == state["printJobStatus"]
    assert one("SELECT title FROM un_biz_record WHERE id=%s", state["recordId"]) == state["recordTitle"]
    assert digest(one("SELECT snapshot_json FROM un_cfg_module_version WHERE id=%s",
                      state["sourceModuleVersionId"])) == state["moduleSnapshotSha256"]
    state["status"] = "v23_restart_verified"
    state["restartOwnerDashboardId"] = state["newDashboardId"]
    state["restartListOnlyDashboardId"] = state["legacyDashboardId"]
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "ownerDashboardId": state["newDashboardId"],
                      "listOnlyDashboardId": state["legacyDashboardId"]}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("prepare", "upgraded", "lifecycle", "restarted"))
    arguments = parser.parse_args()
    {"prepare": prepare, "upgraded": upgraded, "lifecycle": lifecycle, "restarted": restarted}[arguments.phase]()
