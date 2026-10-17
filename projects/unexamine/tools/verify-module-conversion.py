"""隔离库同一模块的发布转化、重复拒绝及来源目标独立性验收。"""

import argparse
import hashlib
import json
import runpy
import urllib.request
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "projects/unexamine/ai/evidence"
STATE = EVIDENCE / "s18-module-conversion-live.json"
DAILY = EVIDENCE / "s18-module-daily-journey-live.json"
PRIOR = EVIDENCE / "s18-module-action-integration-live.json"
helpers = runpy.run_path(str(ROOT / "projects/unexamine/tools/verify-module-approval-action.py"))
success = helpers["success"]
denied = helpers["denied"]
session = helpers["session"]
database = helpers["database"]
publish_module = helpers["publish_module"]
MODULE_ID = 94
SOURCE = "dashboard_accept_1a4547cf"
TARGET = "approval_accept_5b69b5d6"
TARGET_MODULE_ID = 93


def one(sql, *params):
    with database() as connection, connection.cursor() as cursor:
        cursor.execute(sql, params)
        return cursor.fetchone()[0]


def save(state):
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def unchanged(prior, token):
    assert success(f"/api/analytics/runtime?moduleCode={SOURCE}", token=token)["dashboardId"] == prior["dashboardId"]
    actions = success(f"/api/runtime/modules/{SOURCE}/configuration", token=token)["configuration"]["actions"]
    assert {action["code"] for action in actions if action["status"] == "ACTIVE"} >= {
        "PRINT", "APPROVAL", "DASHBOARD", "CONVERT"}
    assert one("SELECT status FROM un_print_job WHERE id=%s", prior["printJobId"]) == "SUCCEEDED"
    assert one("SELECT status FROM un_flow_instance WHERE id=%s", prior["approvalInstanceId"]) == "WAITING"
    call = urllib.request.Request(helpers["success"].__globals__["BASE"]
                                  + f"/api/print/jobs/{prior['printJobId']}/preview",
                                  headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(call, timeout=30) as response:
        assert hashlib.sha256(response.read()).hexdigest() == prior["pdfSha256"]


def published():
    assert not STATE.exists(), "转化验收已有状态，避免重复发布"
    daily = json.loads(DAILY.read_text(encoding="utf-8"))
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    assert daily["status"] == "daily_journey_verified"
    _, token = session()
    source_path = f"/api/runtime/modules/{SOURCE}/records/{daily['importedRecordId']}"
    source = success(source_path, token=token)
    target = success(f"/api/admin/module-config/modules/{TARGET_MODULE_ID}/published", token=token)
    assert [field["code"] for field in target["configuration"]["fields"] if field["required"]] == ["subject"]
    request = {"version": source["version"], "targetModuleCode": TARGET}
    before = success(source_path + "/conversion-preview", "POST", request, token)
    assert not before["executable"] and before["issues"]
    target_count = one("SELECT COUNT(*) FROM un_biz_record WHERE module_id=%s", TARGET_MODULE_ID)
    draft = success(f"/api/admin/module-config/modules/{MODULE_ID}/draft", token=token)
    source_published = success(f"/api/admin/module-config/modules/{MODULE_ID}/published", token=token)
    assert [(item["code"], item["version"]) for item in draft["fields"]] == [
        (item["code"], item["version"]) for item in source_published["configuration"]["fields"]]
    assert len(draft["pages"]) == len(source_published["configuration"]["pages"])
    assert len(draft["menus"]) == len(source_published["configuration"]["menus"])
    action_draft = {action["code"]: action for action in draft["actions"]}
    assert action_draft["DASHBOARD"]["status"] == "DISABLED"
    assert action_draft["CONVERT"]["configJson"] == "{}"
    success(f"/api/admin/module-config/modules/{MODULE_ID}/actions/DASHBOARD", "PUT", {
        "location": "MODULE_ENTRY", "config": {}, "status": "ACTIVE",
        "version": action_draft["DASHBOARD"]["version"]}, token)
    success(f"/api/admin/module-config/modules/{MODULE_ID}/actions/CONVERT", "PUT", {
        "location": "DETAIL_MORE", "status": "ACTIVE",
        "config": {"targetModuleCode": TARGET, "fieldMappings": {"subject": "activity"}},
        "version": action_draft["CONVERT"]["version"]}, token)
    check = success(f"/api/admin/module-config/modules/{MODULE_ID}/publication-check", token=token)
    assert check["valid"], check
    publication = publish_module(MODULE_ID, token)
    assert publication["versionId"] != source_published["versionId"]
    after = success(source_path + "/conversion-preview", "POST", request, token)
    assert after["executable"] and not after["issues"] and not after["alreadyConverted"], after
    assert one("SELECT COUNT(*) FROM un_biz_record WHERE module_id=%s", TARGET_MODULE_ID) == target_count
    unchanged(prior, token)
    state = {"status": "mapping_published", "sourceRecordId": daily["importedRecordId"],
             "sourceVersion": source["version"], "sourceTitle": source["title"],
             "targetModuleId": TARGET_MODULE_ID, "targetVersionId": target["versionId"],
             "targetRecordCountBefore": target_count, "moduleVersionId": publication["versionId"],
             "previewBeforeExecutable": False, "previewAfterExecutable": True}
    save(state)
    print(json.dumps({"status": state["status"], "moduleVersionId": publication["versionId"]}))


def converted():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "mapping_published"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    _, token = session()
    source_path = f"/api/runtime/modules/{SOURCE}/records/{state['sourceRecordId']}"
    source = success(source_path, token=token)
    request = {"version": source["version"], "targetModuleCode": TARGET}
    denied(source_path + "/convert", "POST",
           {"version": source["version"], "targetModuleCode": "g5_contract"},
           token, {404}, "CONFIGURATION_NOT_FOUND")
    result = success(source_path + "/convert", "POST", request, token)
    target = result["targetRecord"]
    assert target["fields"]["subject"] == source["fields"]["activity"]
    assert target["title"] == source["title"]
    assert one("SELECT COUNT(*) FROM un_biz_record WHERE module_id=%s", TARGET_MODULE_ID) == (
        state["targetRecordCountBefore"] + 1)
    assert one("SELECT COUNT(*) FROM un_biz_record_conversion WHERE id=%s", result["conversionId"]) == 1
    links = success(source_path + "/conversions", token=token)
    assert any(link["recordId"] == target["id"] and link["moduleCode"] == TARGET
               for link in links["links"])
    duplicate = success(source_path + "/conversion-preview", "POST", request, token)
    assert duplicate["alreadyConverted"] and not duplicate["executable"]
    denied(source_path + "/convert", "POST", request, token, {409}, "RECORD_ALREADY_CONVERTED")
    source_update = {"recordNumber": source.get("recordNumber"), "title": source["title"],
                     "status": source["status"], "ownerMemberId": source["ownerMemberId"],
                     "participantMemberIds": source.get("participantMemberIds", []),
                     "version": source["version"],
                     "fields": {**source["fields"], "activity": "changed-source-after-conversion"}}
    success(source_path, "PUT", source_update, token)
    target_path = f"/api/runtime/modules/{TARGET}/records/{target['id']}"
    assert success(target_path, token=token)["fields"]["subject"] == "imported-activity"
    target_current = success(target_path, token=token)
    target_update = {"recordNumber": target_current.get("recordNumber"), "title": target_current["title"],
                     "status": target_current["status"], "ownerMemberId": target_current["ownerMemberId"],
                     "participantMemberIds": target_current.get("participantMemberIds", []),
                     "version": target_current["version"],
                     "fields": {**target_current["fields"], "subject": "changed-target-after-conversion"}}
    success(target_path, "PUT", target_update, token)
    assert success(source_path, token=token)["fields"]["activity"] == "changed-source-after-conversion"
    assert success(target_path, token=token)["fields"]["subject"] == "changed-target-after-conversion"
    unchanged(prior, token)
    state.update({"status": "converted_and_independent", "conversionId": result["conversionId"],
                  "targetRecordId": target["id"], "duplicateDenied": True,
                  "crossSystemDenied": True, "sourceAndTargetIndependent": True})
    save(state)
    print(json.dumps({"status": state["status"], "conversionId": result["conversionId"],
                      "targetRecordId": target["id"]}))


def restarted():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "converted_and_independent"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    helpers["success"].__globals__["BASE"] = "http://127.0.0.1:18082"
    _, token = session()
    source = success(f"/api/runtime/modules/{SOURCE}/records/{state['sourceRecordId']}", token=token)
    target = success(f"/api/runtime/modules/{TARGET}/records/{state['targetRecordId']}", token=token)
    assert source["fields"]["activity"] == "changed-source-after-conversion"
    assert target["fields"]["subject"] == "changed-target-after-conversion"
    links = success(f"/api/runtime/modules/{SOURCE}/records/{state['sourceRecordId']}/conversions", token=token)
    assert any(link["recordId"] == state["targetRecordId"] for link in links["links"])
    assert one("SELECT COUNT(*) FROM un_biz_record_conversion WHERE id=%s", state["conversionId"]) == 1
    unchanged(prior, token)
    state["status"] = "conversion_restart_verified"
    save(state)
    print(json.dumps({"status": state["status"], "targetRecordId": state["targetRecordId"]}))


def deletion_boundaries():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "conversion_restart_verified"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    _, token = session()
    source_path = f"/api/runtime/modules/{SOURCE}/records/{state['sourceRecordId']}"
    target_path = f"/api/runtime/modules/{TARGET}/records/{state['targetRecordId']}"
    target = success(target_path, token=token)
    success(target_path + "/delete", "POST", {
        "reason": "Verify target deletion does not delete conversion source", "version": target["version"]}, token)
    assert one("SELECT deleted FROM un_biz_record WHERE id=%s", state["targetRecordId"]) == 1
    assert one("SELECT deleted FROM un_biz_record WHERE id=%s", state["sourceRecordId"]) == 0
    links = success(source_path + "/conversions", token=token)["links"]
    assert any(link["recordId"] == state["targetRecordId"] and link["recordStatus"] == "DELETED"
               for link in links), links
    source = success(source_path, token=token)
    denied(source_path + "/convert", "POST", {
        "version": source["version"], "targetModuleCode": TARGET},
        token, {409}, "RECORD_ALREADY_CONVERTED")

    suffix = uuid.uuid4().hex[:8]
    extra = success(f"/api/runtime/modules/{SOURCE}/records", "POST", {
        "title": "conversion-source-delete-" + suffix, "participantMemberIds": [],
        "fields": {"activity": "source-deletion-independent"}}, token)
    extra_path = f"/api/runtime/modules/{SOURCE}/records/{extra['id']}"
    extra_result = success(extra_path + "/convert", "POST", {
        "version": extra["version"], "targetModuleCode": TARGET}, token)
    durable_target_id = extra_result["targetRecord"]["id"]
    success(extra_path + "/delete", "POST", {
        "reason": "Verify source deletion does not delete target", "version": extra["version"]}, token)
    assert one("SELECT deleted FROM un_biz_record WHERE id=%s", extra["id"]) == 1
    assert one("SELECT deleted FROM un_biz_record WHERE id=%s", durable_target_id) == 0
    assert success(f"/api/runtime/modules/{TARGET}/records/{durable_target_id}", token=token)["id"] == durable_target_id
    assert one("SELECT COUNT(*) FROM un_biz_record_conversion WHERE id=%s", extra_result["conversionId"]) == 1
    unchanged(prior, token)
    state.update({"status": "deletion_boundaries_verified", "deletedTargetId": state["targetRecordId"],
                  "deletedSourceId": extra["id"], "durableTargetId": durable_target_id,
                  "secondConversionId": extra_result["conversionId"], "duplicateAfterTargetDeleteDenied": True})
    save(state)
    print(json.dumps({"status": state["status"], "durableTargetId": durable_target_id}))


def final_readback():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "deletion_boundaries_verified"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    helpers["success"].__globals__["BASE"] = "http://127.0.0.1:18082"
    _, token = session()
    assert one("SELECT deleted FROM un_biz_record WHERE id=%s", state["deletedTargetId"]) == 1
    assert one("SELECT deleted FROM un_biz_record WHERE id=%s", state["deletedSourceId"]) == 1
    assert one("SELECT deleted FROM un_biz_record WHERE id=%s", state["sourceRecordId"]) == 0
    assert success(f"/api/runtime/modules/{TARGET}/records/{state['durableTargetId']}",
                   token=token)["id"] == state["durableTargetId"]
    assert one("SELECT COUNT(*) FROM un_biz_record_conversion WHERE id=%s", state["conversionId"]) == 1
    assert one("SELECT COUNT(*) FROM un_biz_record_conversion WHERE id=%s", state["secondConversionId"]) == 1
    unchanged(prior, token)
    state["status"] = "conversion_full_verified"
    save(state)
    print(json.dumps({"status": state["status"], "sourceRecordId": state["sourceRecordId"],
                      "durableTargetId": state["durableTargetId"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("published", "converted", "restarted",
                                          "deletion_boundaries", "final_readback"))
    args = parser.parse_args()
    {"published": published, "converted": converted, "restarted": restarted,
     "deletion_boundaries": deletion_boundaries, "final_readback": final_readback}[args.phase]()
