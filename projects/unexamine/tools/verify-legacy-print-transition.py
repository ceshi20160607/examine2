"""在隔离验收库验证旧打印模板迁移、权限和重启后的历史 PDF 读取。"""

import argparse
import hashlib
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

import pymysql


ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "projects/unexamine/ai/evidence"
STATE = EVIDENCE / "s18-module-legacy-print-api-readback.json"
BASE = "http://127.0.0.1:18081"
MODULE_ID = 68
SYSTEM_ID = 143
TENANT_ID = 144
MODULE_CODE = "g7_layout"
OWNER_NAME = "g7_layout_owner_6b4dee80"
MEMBER_NAME = "g7_layout_member_6b4dee80"
TEMPLATE_ID = 36
RECORD_ID = 121


def request(path, method="GET", body=None, token=None, binary=False):
    content = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=content, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read()
            return response.status, raw if binary else json.loads(raw)
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, raw if binary else json.loads(raw)


def success(path, method="GET", body=None, token=None):
    status, result = request(path, method, body, token)
    assert status == 200 and result.get("code") in ("OK", "SUCCESS"), (path, status, result)
    return result["data"]


def denied(path, method, body, token, status, code=None):
    actual, result = request(path, method, body, token)
    assert actual == status and (code is None or result.get("code") == code), (path, actual, result)
    return result.get("code")


def token(username):
    login = success("/api/auth/login", "POST", {"username": username, "password": "correct-password"})
    entered = success(f"/api/systems/{SYSTEM_ID}/enter", "POST", {}, login["accessToken"])
    return entered["tokens"]["accessToken"]


def database():
    env = dict(line.split("=", 1) for line in
               (ROOT / "projects/unexamine/deploy/local.env.example").read_text(encoding="utf-8").splitlines()
               if "=" in line and not line.startswith("#"))
    return pymysql.connect(host="127.0.0.1", port=33077, user=env["APP_DB_USERNAME"],
                           password=env["APP_DB_PASSWORD"], database="unexamine_drill_s17_files_1054")


def snapshot():
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("""SELECT p.version, v.id, v.version_number, v.schema_hash, v.snapshot_json,
                       m.draft_revision, m.system_id, m.owner_tenant_id
                       FROM un_cfg_module m JOIN un_cfg_module_publication p ON p.module_id=m.id
                       JOIN un_cfg_module_version v ON v.id=p.current_version_id WHERE m.id=%s""", (MODULE_ID,))
            version, version_id, number, digest, raw, revision, system, tenant = cur.fetchone()
            cur.execute("SELECT id, module_config_version_id, template_version_id, status, output_file_id, "
                        "created_by_member_id "
                        "FROM un_print_job WHERE module_id=%s ORDER BY id", (MODULE_ID,))
            jobs = [list(row) for row in cur.fetchall()]
            cur.execute("SELECT id, current_version_id FROM un_print_template_publication WHERE template_id=%s",
                        (TEMPLATE_ID,))
            template = cur.fetchone()
    assert (system, tenant) == (SYSTEM_ID, TENANT_ID)
    return {"publicationVersion": version, "versionId": version_id, "versionNumber": number,
            "schemaHash": digest, "snapshotHash": hashlib.sha256(raw.encode()).hexdigest(),
            "actions": [action.get("code") for action in json.loads(raw)["actions"]],
            "draftRevision": revision, "jobs": jobs, "templatePublication": list(template)}


def check_old_version(old):
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT schema_hash, snapshot_json FROM un_cfg_module_version WHERE id=%s",
                        (old["versionId"],))
            digest, raw = cur.fetchone()
    assert digest == old["schemaHash"]
    assert hashlib.sha256(raw.encode()).hexdigest() == old["snapshotHash"]


def check_pdf(job_id, owner, label):
    status, content = request(f"/api/print/jobs/{job_id}/preview", token=owner, binary=True)
    assert status == 200 and content.startswith(b"%PDF"), (label, status, content[:40])
    path = EVIDENCE / f"s18-module-legacy-print-{label}.pdf"
    path.write_bytes(content)
    return {"jobId": job_id, "path": str(path.relative_to(ROOT)).replace("\\", "/"),
            "sha256": hashlib.sha256(content).hexdigest(), "bytes": len(content)}


def historical_pdf(job_id, owner, member=None):
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("""SELECT a.username FROM un_print_job j JOIN un_sys_member m
                           ON m.id=j.created_by_member_id JOIN un_plat_account a ON a.id=m.account_id
                           WHERE j.id=%s""", (job_id,))
            row = cur.fetchone()
    reader = "owner" if row and row[0] == OWNER_NAME else "member" if row and row[0] == MEMBER_NAME else None
    if reader is None or (reader == "member" and member is None):
        return {"jobId": job_id, "status": "CREATOR_ACCOUNT_UNAVAILABLE", "creator": row[0] if row else None}
    selected_token = owner if reader == "owner" else member
    status, content = request(f"/api/print/jobs/{job_id}/preview", token=selected_token, binary=True)
    if status == 200:
        return {"status": "AVAILABLE", "reader": reader,
                **check_pdf(job_id, selected_token, f"historical-{job_id}")}
    result = json.loads(content)
    if status == 403 and result.get("code") == "PRINT_JOB_FIELD_ACCESS_REVOKED":
        return {"jobId": job_id, "status": "CURRENT_FIELD_PERMISSION_REVOKED",
                "reader": reader, "httpStatus": status, "code": result["code"]}
    assert status == 404 and result.get("code") == "FILE_CONTENT_MISSING", (job_id, status, result)
    return {"jobId": job_id, "status": "MISSING_BLOB_IN_ISOLATED_FIXTURE",
            "reader": reader, "httpStatus": status, "code": result["code"]}


def apply():
    owner = token(OWNER_NAME)
    member = token(MEMBER_NAME)
    old = snapshot()
    if "PRINT" in old["actions"] and not STATE.exists():
        return resume(owner, member, old)
    assert "PRINT" not in old["actions"]
    path = f"/api/print/runtime/modules/{MODULE_CODE}/records/{RECORD_ID}/templates/{TEMPLATE_ID}"
    before_denial = denied(path, "POST", {}, owner, 403, "PRINT_ACTION_NOT_PUBLISHED")
    migration = f"/api/admin/module-config/modules/{MODULE_ID}/legacy-print-transition"
    member_denial = denied(migration + f"?expectedPublicationVersion={old['publicationVersion']}",
                           "POST", None, member, 403, "PERMISSION_DENIED")
    stale_denial = denied(migration + "?expectedPublicationVersion=99999", "POST", None, owner, 409)
    published = success(migration + f"?expectedPublicationVersion={old['publicationVersion']}",
                        "POST", None, owner)
    current = snapshot()
    check_old_version(old)
    assert current["versionId"] == published["versionId"]
    assert current["versionNumber"] == old["versionNumber"] + 1
    assert current["publicationVersion"] == old["publicationVersion"] + 1
    assert current["templatePublication"] == old["templatePublication"]
    assert current["jobs"] == old["jobs"]
    assert current["actions"].count("PRINT") == 1
    assert current["draftRevision"] == old["draftRevision"] + 1
    duplicate_denial = denied(migration + f"?expectedPublicationVersion={current['publicationVersion']}",
                              "POST", None, owner, 409, "LEGACY_PRINT_ACTION_DRAFT_EXISTS")
    draft = success(f"/api/admin/module-config/modules/{MODULE_ID}/draft", token=owner)
    assert any(action["code"] == "PRINT" for action in draft["actions"])
    templates = success(f"/api/print/runtime/modules/{MODULE_CODE}/templates", token=owner)
    assert any(template["templateId"] == TEMPLATE_ID for template in templates)
    member_job = success(path, "POST", {}, member)
    assert member_job["status"] == "SUCCEEDED"
    job = success(path, "POST", {}, owner)
    for _ in range(30):
        job = success(f"/api/print/jobs/{job['id']}", token=owner)
        if job["status"] in ("SUCCEEDED", "FAILED"):
            break
        time.sleep(1)
    assert job["status"] == "SUCCEEDED", job
    pdf = check_pdf(job["id"], owner, "new")
    historical_pdfs = [historical_pdf(item[0], owner, member) for item in old["jobs"]
                       if item[3] == "SUCCEEDED"]
    result = {"step": "S18.29", "moduleId": MODULE_ID, "systemId": SYSTEM_ID,
              "templateId": TEMPLATE_ID, "recordId": RECORD_ID, "before": old, "after": current,
              "denials": {"before": before_denial, "memberTransition": member_denial,
                          "staleVersion": stale_denial, "duplicate": duplicate_denial,
                          "memberPrint": "ALLOWED"}, "memberJobId": member_job["id"],
              "newPdf": pdf, "historicalPdfs": historical_pdfs}
    STATE.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "version": current["versionNumber"], "job": job["id"],
                      "historicalJobs": len(old["jobs"]), "memberPrint": "ALLOWED"}))


def resume(owner, member, current):
    """首次运行已通过不可变性断言后中断时，按旧版本和作业编号继续验证。"""
    assert current["actions"].count("PRINT") == 1
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("""SELECT id, version_number, schema_hash, snapshot_json FROM un_cfg_module_version
                           WHERE module_id=%s AND version_number=%s""",
                        (MODULE_ID, current["versionNumber"] - 1))
            old_id, old_number, old_digest, old_raw = cur.fetchone()
    old_jobs = [item for item in current["jobs"] if item[0] < 126]
    assert len(old_jobs) == 11 and any(item[0] == 126 for item in current["jobs"])
    old = {"publicationVersion": current["publicationVersion"] - 1, "versionId": old_id,
           "versionNumber": old_number, "schemaHash": old_digest,
           "snapshotHash": hashlib.sha256(old_raw.encode()).hexdigest(),
           "actions": [action.get("code") for action in json.loads(old_raw)["actions"]],
           "draftRevision": current["draftRevision"] - 1, "jobs": old_jobs,
           "templatePublication": current["templatePublication"]}
    check_old_version(old)
    assert "PRINT" not in old["actions"]
    migration = f"/api/admin/module-config/modules/{MODULE_ID}/legacy-print-transition"
    member_denial = denied(migration + f"?expectedPublicationVersion={current['publicationVersion']}",
                           "POST", None, member, 403, "PERMISSION_DENIED")
    duplicate_denial = denied(migration + f"?expectedPublicationVersion={current['publicationVersion']}",
                              "POST", None, owner, 409, "LEGACY_PRINT_ACTION_DRAFT_EXISTS")
    member_pdf = check_pdf(126, member, "member")
    path = f"/api/print/runtime/modules/{MODULE_CODE}/records/{RECORD_ID}/templates/{TEMPLATE_ID}"
    job = success(path, "POST", {}, owner)
    assert job["status"] == "SUCCEEDED", job["status"]
    pdf = check_pdf(job["id"], owner, "new")
    historical_pdfs = [historical_pdf(item[0], owner, member) for item in old_jobs
                       if item[3] == "SUCCEEDED"]
    result = {"step": "S18.29", "moduleId": MODULE_ID, "systemId": SYSTEM_ID,
              "templateId": TEMPLATE_ID, "recordId": RECORD_ID, "before": old, "after": current,
              "denials": {"before": "PRINT_ACTION_NOT_PUBLISHED", "memberTransition": member_denial,
                          "staleVersion": "MODULE_CONFLICT", "duplicate": duplicate_denial,
                          "memberPrint": "ALLOWED"}, "memberPdf": member_pdf,
              "newPdf": pdf, "historicalPdfs": historical_pdfs,
              "note": "First apply passed old-version and old-job immutability assertions before member print returned 200."}
    STATE.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "resumed": True, "version": current["versionNumber"],
                      "ownerJob": job["id"], "memberJob": 126,
                      "historicalPdfs": len(historical_pdfs)}))


def readback():
    state = json.loads(STATE.read_text(encoding="utf-8"))
    owner = token(OWNER_NAME)
    member = token(MEMBER_NAME)
    current = snapshot()
    check_old_version(state["before"])
    assert current["versionId"] == state["after"]["versionId"]
    assert current["actions"].count("PRINT") == 1
    assert current["templatePublication"] == state["before"]["templatePublication"]
    job = success(f"/api/print/jobs/{state['newPdf']['jobId']}", token=owner)
    assert job["status"] == "SUCCEEDED"
    assert check_pdf(job["id"], owner, "new")["sha256"] == state["newPdf"]["sha256"]
    for item in state["historicalPdfs"]:
        actual = historical_pdf(item["jobId"], owner, member)
        assert actual["status"] == item["status"]
        if actual["status"] == "AVAILABLE":
            assert actual["sha256"] == item["sha256"]
    result = {"ok": True, "restartReadback": True, "versionId": current["versionId"],
              "newJobId": job["id"], "historicalPdfs": len(state["historicalPdfs"]),
              "historicalStatuses": {status: sum(item["status"] == status for item in state["historicalPdfs"])
                                     for status in {item["status"] for item in state["historicalPdfs"]}}}
    (EVIDENCE / "s18-module-legacy-print-restart-readback.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


def inspect_accounts():
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT a.id, a.username, a.status, c.password_hash "
                        "FROM un_plat_account a JOIN un_plat_account_credential c ON c.account_id=a.id "
                        "WHERE a.username LIKE %s ORDER BY a.id", ("g6_import_%e30db392",))
            rows = cur.fetchall()
    import base64
    def matches(encoded):
        kind, iterations, salt, expected = encoded.split("$")
        if kind != "pbkdf2-sha256":
            return False
        value = hashlib.pbkdf2_hmac("sha256", b"correct-password",
                                     base64.urlsafe_b64decode(salt + "=="), int(iterations))
        return value == base64.urlsafe_b64decode(expected + "==")
    print(json.dumps([{"id": row[0], "username": row[1], "status": row[2],
                       "knownPasswordMatches": matches(row[3])}
                      for row in rows]))


def candidates():
    import base64
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("""SELECT m.id, m.system_id, m.owner_tenant_id, m.code, t.id,
                       a.username, c.password_hash, v.snapshot_json, r.id,
                       (SELECT COUNT(*) FROM un_print_job j WHERE j.module_id=m.id) FROM un_cfg_module m
                       JOIN un_cfg_module_publication p ON p.module_id=m.id
                       JOIN un_cfg_module_version v ON v.id=p.current_version_id
                       JOIN un_print_template t ON t.module_id=m.id
                       JOIN un_print_template_publication tp ON tp.template_id=t.id
                       JOIN un_sys_system s ON s.id=m.system_id
                       JOIN un_plat_account a ON a.id=s.creator_account_id
                       JOIN un_plat_account_credential c ON c.account_id=a.id
                       LEFT JOIN un_biz_record r ON r.module_id=m.id
                       WHERE m.status='ACTIVE' ORDER BY m.id, t.id, r.id""")
            rows = cur.fetchall()
    result = []
    seen = set()
    for module_id, system_id, tenant_id, code, template_id, username, encoded, raw, record_id, jobs in rows:
        if module_id in seen or "PRINT" in [action.get("code") for action in json.loads(raw)["actions"]]:
            continue
        seen.add(module_id)
        kind, iterations, salt, expected = encoded.split("$")
        value = hashlib.pbkdf2_hmac("sha256", b"correct-password",
                                     base64.urlsafe_b64decode(salt + "=="), int(iterations))
        if kind == "pbkdf2-sha256" and value == base64.urlsafe_b64decode(expected + "=="):
            result.append({"moduleId": module_id, "systemId": system_id, "tenantId": tenant_id,
                           "code": code, "templateId": template_id, "owner": username,
                           "recordId": record_id, "historicalJobs": jobs})
    print(json.dumps(result))


def file_inventory():
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("""SELECT j.module_id, j.id, j.status, f.object_key FROM un_print_job j
                           LEFT JOIN un_file_object f ON f.id=j.output_file_id
                           WHERE j.module_id IN (28,29,30,31,35,37,38,40,42,44,45,46,47,49,50,
                           51,52,53,55,56,57,58,60,61,62,64,65,66,67,68,83)
                           ORDER BY j.module_id,j.id""")
            rows = cur.fetchall()
    root = ROOT / "projects/unexamine/backend/data/files"
    found = {}
    for module_id, job_id, status, key in rows:
        item = found.setdefault(module_id, {"jobs": 0, "blobs": 0, "availableJobs": []})
        item["jobs"] += 1
        if status == "SUCCEEDED" and key and (root / key).is_file():
            item["blobs"] += 1
            item["availableJobs"].append(job_id)
    print(json.dumps(found))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=["apply", "readback", "inspect", "candidates", "files"])
    args = parser.parse_args()
    if args.phase == "apply":
        apply()
    elif args.phase == "readback":
        readback()
    elif args.phase == "inspect":
        inspect_accounts()
    elif args.phase == "candidates":
        candidates()
    else:
        file_inventory()
