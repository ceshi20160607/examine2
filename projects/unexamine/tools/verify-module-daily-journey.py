"""隔离库同一模块的导入、导出和负责人转移组合旅程。"""

import argparse
import csv
import hashlib
import io
import json
import runpy
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "projects/unexamine/ai/evidence"
STATE = EVIDENCE / "s18-module-daily-journey-live.json"
PRIOR = EVIDENCE / "s18-module-action-integration-live.json"
helpers = runpy.run_path(str(ROOT / "projects/unexamine/tools/verify-module-approval-action.py"))
success = helpers["success"]
denied = helpers["denied"]
session = helpers["session"]
database = helpers["database"]
CODE = "dashboard_accept_1a4547cf"
MODULE_ID = 94


def raw(path, token, method="GET", content=None, headers=None):
    request = urllib.request.Request(
        helpers["success"].__globals__["BASE"] + path, data=content,
        headers={"Authorization": "Bearer " + token, **(headers or {})}, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def upload_csv(content, token, account_id):
    digest = hashlib.sha256(content).hexdigest()
    session = success("/api/files/uploads", "POST", {
        "originalName": "same-module-journey.csv", "contentType": "text/csv",
        "expectedSize": len(content), "expectedSha256": digest}, token)
    status, body = raw(f"/api/files/uploads/{session['id']}/chunks?offset=0", token, "PUT", content, {
        "Content-Type": "application/octet-stream", "X-Upload-Token": session["uploadToken"],
        "X-Chunk-Sha256": digest})
    assert status == 200 and json.loads(body)["code"] == "OK", (status, body[:300])
    status, body = raw(f"/api/files/uploads/{session['id']}/complete", token, "POST", b"", {
        "X-Upload-Token": session["uploadToken"]})
    result = json.loads(body)
    assert status == 200 and result["code"] == "OK", (status, result)
    file = result["data"]
    assert file["scanStatus"] == "CLEAN"
    success(f"/api/files/{file['id']}/references", "POST", {
        "ownerType": "ACCOUNT", "ownerId": str(account_id),
        "fieldCode": "managed_files", "referenceType": "DOCUMENT"}, token)
    return file["id"]


def db_row(sql, value):
    with database() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (value,))
        return list(cursor.fetchone())


def old_work_unchanged(prior, token):
    print_job = db_row("SELECT template_version_id,module_config_version_id,status,output_file_id "
                       "FROM un_print_job WHERE id=%s", prior["printJobId"])
    flow = db_row("SELECT flow_version_id,business_snapshot_json,runtime_snapshot_json,status "
                  "FROM un_flow_instance WHERE id=%s", prior["approvalInstanceId"])
    assert print_job == prior["printSnapshot"] and flow == prior["flowSnapshot"]
    assert success(f"/api/analytics/runtime?moduleCode={CODE}", token=token)["dashboardId"] == prior["dashboardId"]
    assert {item["code"] for item in success(f"/api/runtime/modules/{CODE}/configuration", token=token)
            ["configuration"]["actions"] if item["status"] == "ACTIVE"} >= {"PRINT", "APPROVAL", "DASHBOARD"}


def imported():
    assert not STATE.exists(), "已有导入旅程状态，避免重复写入"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    platform_token, token = session()
    account_id = success("/api/auth/me", token=platform_token)["accountId"]
    base = f"/api/runtime/modules/{CODE}/imports"
    template = success(base + "/template", token=token)
    assert {item["code"] for item in template["columns"]} >= {"title", "activity"}
    suffix = uuid.uuid4().hex[:8]
    title = "same-module-import-" + suffix
    source = ("title,activity\n" + title + ",imported-activity\n,invalid-row\n").encode("utf-8")
    file_id = upload_csv(source, token, account_id)
    headers = success(base + f"/sources/{file_id}/headers", token=token)
    assert headers["format"] == "CSV" and headers["headers"][:2] == ["title", "activity"]
    preview = success(base + "/preview", "POST", {
        "sourceFileId": file_id, "columnMapping": {"title": "title", "activity": "activity"},
        "conflictPolicy": "ERROR", "matchFieldCode": None, "mappingName": "same-module-" + suffix,
    }, token)
    assert preview["summary"]["create"] == 1 and preview["summary"]["error"] == 1, preview["summary"]
    assert any(row["errorCode"] == "IMPORT_TITLE_REQUIRED" for row in preview["rows"])
    assert not any(item["title"] == title for item in
                   success(f"/api/runtime/modules/{CODE}/records", token=token)["records"])
    queued = success(base + f"/{preview['id']}/execute", "POST", {"version": preview["version"]}, token)
    result = queued
    for _ in range(60):
        result = success(base + f"/{preview['id']}", token=token)
        if result["status"] in ("COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED"):
            break
        time.sleep(0.3)
    assert result["status"] == "COMPLETED_WITH_ERRORS" and result["successRows"] == 1
    assert result["failedRows"] == 1, result
    created = next(item for item in success(f"/api/runtime/modules/{CODE}/records", token=token)["records"]
                   if item["title"] == title)
    detail = success(f"/api/runtime/modules/{CODE}/records/{created['id']}", token=token)
    assert detail["fields"]["activity"] == "imported-activity"
    old_work_unchanged(prior, token)
    state = {"status": "import_verified", "moduleId": MODULE_ID, "moduleCode": CODE,
             "sourceFileId": file_id, "importBatchId": preview["id"],
             "importedRecordId": created["id"], "importedTitle": title,
             "invalidRowCode": "IMPORT_TITLE_REQUIRED", "importSummary": result["summary"]}
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "recordId": created["id"], "batchId": preview["id"]}))


def exchanged():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "import_verified"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    _, token = session()
    base = f"/api/runtime/modules/{CODE}/exports"
    config = success(base + "/config", token=token)
    assert config["published"] is None
    saved = success(base + "/config", "PUT", {
        "name": "Same module journey", "selectedFields": ["title", "activity", "ownerMemberId"],
        "relationDisplay": "LABEL", "expectedVersion": None}, token)
    published = success(base + "/config/publish", "POST",
                        {"expectedDraftRevision": saved["draftRevision"]}, token)
    query = {"lifecycleState": "ACTIVE", "tenantScope": "ALL", "search": "",
             "filters": [], "sortField": "title", "sortDirection": "ASC",
             "selectedRecordIds": [state["importedRecordId"]], "format": "CSV",
             "idempotencyKey": "same-module-" + str(state["importedRecordId"]), "selectedFields": []}
    estimate = success(base + "/estimate", "POST", query, token)
    assert estimate["estimatedRows"] == 1 and estimate["scope"] == "SELECTED", estimate
    batch = success(base, "POST", query, token)
    for _ in range(60):
        batch = success(base + "/" + str(batch["id"]), token=token)
        if batch["status"] in ("COMPLETED", "FAILED"):
            break
        time.sleep(0.3)
    assert batch["status"] == "COMPLETED" and batch["exportedRows"] == 1, batch
    status, content = raw(base + f"/{batch['id']}/download", token)
    assert status == 200
    rows = list(csv.reader(io.StringIO(content.decode("utf-8-sig"))))
    assert len(rows) == 2 and state["importedTitle"] in rows[1], rows
    old_work_unchanged(prior, token)
    state.update({"status": "export_verified", "exportConfigVersionId": published["versionId"],
                  "exportBatchId": batch["id"], "exportedRows": batch["exportedRows"],
                  "exportSha256": hashlib.sha256(content).hexdigest()})
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "exportBatchId": batch["id"]}))


def transferred():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "export_verified"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    _, token = session()
    suffix = uuid.uuid4().hex[:8]
    username = "action_receiver_" + suffix
    success("/api/auth/register", "POST", {
        "username": username, "password": "correct-password", "displayName": "Action receiver",
        "email": username + "@example.com", "systemName": "Receiver workspace",
        "systemCode": "receiver_" + suffix,
    })
    role = success("/api/admin/system/authorization/roles", "POST", {
        "code": "action_receiver_" + suffix, "name": "Action receiver",
        "permissions": [{"resourceType": "MODULE", "resourceCode": CODE, "actionCode": action,
                         "dataScopeType": "SELF", "dataScopeJson": "{}"}
                        for action in ("LIST", "DETAIL")],
        "fieldPolicies": [{"resourceCode": CODE, "fieldCode": "activity", "channel": "PAGE",
                           "readable": True, "writable": False, "maskStrategy": None}],
    }, token)
    published = success(f"/api/admin/system/authorization/roles/{role['id']}/publish", "POST", {
        "reason": "Same module owner transfer", "expectedVersion": role["version"],
    }, token)
    member = success("/api/admin/system/authorization/members", "POST", {
        "account": username, "roleIds": [published["id"]],
    }, token)
    receiver_platform = success("/api/auth/login", "POST", {
        "username": username, "password": "correct-password"})
    receiver = success("/api/systems/143/enter", "POST", {}, receiver_platform["accessToken"])
    receiver_token = receiver["tokens"]["accessToken"]
    path = f"/api/runtime/modules/{CODE}/records/{state['importedRecordId']}"
    denied(path, "GET", None, receiver_token, {403}, "PERMISSION_DENIED")
    record = success(path, token=token)
    before_owner = record["ownerMemberId"]
    invalid = {"version": record["version"], "ownerMemberId": 999999999,
               "departmentId": None, "participantMemberIds": [], "reason": "Invalid transfer target"}
    status, failed = helpers["request"](path + "/transfer-preview", "POST", invalid, token)
    assert status in (404, 422) and failed["code"] != "OK", (status, failed)
    assert success(path, token=token)["ownerMemberId"] == before_owner
    request = {"version": record["version"], "ownerMemberId": member["systemMemberId"],
               "departmentId": None, "participantMemberIds": [],
               "reason": "Same module integrated business transfer"}
    preview = success(path + "/transfer-preview", "POST", request, token)
    assert preview["fromOwnerMemberId"] == before_owner
    assert preview["toOwnerMemberId"] == member["systemMemberId"]
    success(path + "/transfer", "POST", request, token)
    after = success(path, token=token)
    assert after["ownerMemberId"] == member["systemMemberId"] and after["version"] > record["version"]
    recipient_view = success(path, token=receiver_token)
    assert recipient_view["id"] == state["importedRecordId"]
    assert recipient_view["fields"]["activity"] == "imported-activity"
    denied(f"/api/runtime/modules/{CODE}/exports/estimate", "POST", {
        "lifecycleState": "ACTIVE", "tenantScope": "ALL", "search": "", "filters": [],
        "sortField": "title", "sortDirection": "ASC", "selectedRecordIds": [],
        "format": "CSV", "idempotencyKey": "receiver-denied-" + suffix, "selectedFields": [],
    }, receiver_token, {403}, "EXPORT_PERMISSION_DENIED")
    old_work_unchanged(prior, token)
    state.update({"status": "transfer_verified", "receiverAccount": username,
                  "receiverMemberId": member["systemMemberId"], "fromOwnerMemberId": before_owner,
                  "invalidTransfer": {"status": status, "code": failed["code"]},
                  "recipientCanReadAfterTransfer": True, "recipientExportDenied": True})
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "recordId": state["importedRecordId"],
                      "receiverMemberId": member["systemMemberId"]}))


def restarted():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "transfer_verified"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    helpers["success"].__globals__["BASE"] = "http://127.0.0.1:18082"
    _, token = session()
    record = success(f"/api/runtime/modules/{CODE}/records/{state['importedRecordId']}", token=token)
    assert record["ownerMemberId"] == state["receiverMemberId"]
    old_work_unchanged(prior, token)
    status, pdf = raw(f"/api/print/jobs/{prior['printJobId']}/preview", token)
    assert status == 200 and hashlib.sha256(pdf).hexdigest() == prior["pdfSha256"]
    status, export = raw(f"/api/runtime/modules/{CODE}/exports/{state['exportBatchId']}/download", token)
    assert status == 200 and hashlib.sha256(export).hexdigest() == state["exportSha256"]
    login = success("/api/auth/login", "POST", {
        "username": state["receiverAccount"], "password": "correct-password"})
    entered = success("/api/systems/143/enter", "POST", {}, login["accessToken"])
    recipient = success(f"/api/runtime/modules/{CODE}/records/{state['importedRecordId']}",
                        token=entered["tokens"]["accessToken"])
    assert recipient["ownerMemberId"] == state["receiverMemberId"]
    state["status"] = "new_jvm_verified"
    state["restartPrintPdfUnchanged"] = True
    state["restartExportUnchanged"] = True
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "recordId": state["importedRecordId"],
                      "pdfUnchanged": True, "exportUnchanged": True}))


def rollback_conflict():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "new_jvm_verified"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    _, token = session()
    path = f"/api/runtime/modules/{CODE}/records/{state['importedRecordId']}"
    before = success(path, token=token)
    batch = success(f"/api/runtime/modules/{CODE}/imports/{state['importBatchId']}/rollback", "POST", {
        "reason": "Check later owner transfer is protected from import rollback"}, token)
    assert batch["status"] == "ROLLBACK_PARTIAL"
    assert batch["summary"]["rollbackConflicts"] == 1
    assert any(row["status"] == "ROLLBACK_CONFLICT"
               and row["errorCode"] == "IMPORT_ROLLBACK_VERSION_CONFLICT" for row in batch["rows"])
    after = success(path, token=token)
    assert after["version"] == before["version"] and after["ownerMemberId"] == state["receiverMemberId"]
    old_work_unchanged(prior, token)
    state["status"] = "rollback_conflict_verified"
    state["importRollbackStatus"] = batch["status"]
    state["importRollbackConflictCode"] = "IMPORT_ROLLBACK_VERSION_CONFLICT"
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "recordId": state["importedRecordId"]}))


def final_readback():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    assert state["status"] == "rollback_conflict_verified"
    prior = json.loads(PRIOR.read_text(encoding="utf-8"))
    helpers["success"].__globals__["BASE"] = "http://127.0.0.1:18082"
    _, token = session()
    record = success(f"/api/runtime/modules/{CODE}/records/{state['importedRecordId']}", token=token)
    assert record["ownerMemberId"] == state["receiverMemberId"]
    old_work_unchanged(prior, token)
    status, pdf = raw(f"/api/print/jobs/{prior['printJobId']}/preview", token)
    assert status == 200 and hashlib.sha256(pdf).hexdigest() == prior["pdfSha256"]
    state["status"] = "daily_journey_verified"
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": state["status"], "recordId": state["importedRecordId"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("imported", "exchanged", "transferred", "restarted",
                                          "rollback_conflict", "final_readback"))
    args = parser.parse_args()
    {"imported": imported, "exchanged": exchanged, "transferred": transferred,
     "restarted": restarted, "rollback_conflict": rollback_conflict,
     "final_readback": final_readback}[args.phase]()
