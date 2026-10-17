"""核对 V22 升级固定的旧模板清单，以及升级前后业务数据未被改写。"""

import hashlib
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

import pymysql


ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "projects/unexamine/ai/evidence"
BASELINE = EVIDENCE / "s18-module-print-upgrade-before.json"
AFTER = EVIDENCE / "s18-module-print-upgrade-after.json"
BASE_URL = "http://127.0.0.1:18081"


def database():
    env = dict(line.split("=", 1) for line in
               (ROOT / "projects/unexamine/deploy/local.env.example").read_text(encoding="utf-8").splitlines()
               if "=" in line and not line.startswith("#"))
    return pymysql.connect(host="127.0.0.1", port=33077, user=env["APP_DB_USERNAME"],
                           password=env["APP_DB_PASSWORD"], database="unexamine_drill_s17_files_1054")


def inventory():
    queries = {
        "moduleVersions": "SELECT id,module_id,version_number,schema_hash FROM un_cfg_module_version ORDER BY id",
        "templatePointers": "SELECT id,template_id,current_version_id,version FROM un_print_template_publication ORDER BY id",
        "printJobs": "SELECT id,module_id,record_id,template_version_id,module_config_version_id,status,output_file_id FROM un_print_job ORDER BY id",
        "records": "SELECT id,module_id,status,version FROM un_biz_record ORDER BY id",
    }
    result = {}
    with database() as conn:
        with conn.cursor() as cur:
            for name, query in queries.items():
                cur.execute(query)
                rows = cur.fetchall()
                raw = json.dumps(rows, default=str, ensure_ascii=False, separators=(",", ":")).encode()
                result[name] = {"count": len(rows), "sha256": hashlib.sha256(raw).hexdigest()}
            cur.execute("""SELECT COUNT(DISTINCT m.id),COUNT(*) FROM un_cfg_module m
                JOIN un_cfg_module_publication mp ON mp.module_id=m.id
                JOIN un_cfg_module_version mv ON mv.id=mp.current_version_id
                JOIN un_print_template t ON t.module_id=m.id AND t.system_id=m.system_id
                  AND t.owner_tenant_id=m.owner_tenant_id
                JOIN un_print_template_publication tp ON tp.template_id=t.id
                WHERE m.status='ACTIVE'
                  AND JSON_SEARCH(mv.snapshot_json,'one','PRINT',NULL,'$.actions[*].code') IS NULL""")
            result["legacyPublishedWithoutPrint"] = dict(zip(("modules", "templates"), cur.fetchone()))
    return result


def request(path, token=None, method="GET", body=None, binary=False):
    content = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE_URL + path, data=content, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read()
            return response.status, raw if binary else json.loads(raw)
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, raw if binary else json.loads(raw)


def success(path, token=None, method="GET", body=None):
    status, value = request(path, token, method, body)
    assert status == 200 and value.get("code") == "OK", (path, status, value)
    return value["data"]


def system_token(username, system_id):
    login = success("/api/auth/login", method="POST",
                    body={"username": username, "password": "correct-password"})
    entered = success(f"/api/systems/{system_id}/enter", login["accessToken"], "POST", {})
    return entered["tokens"]["accessToken"]


def owner_token():
    return system_token("g7_layout_owner_ea39f0f4", 141)


def before():
    data = inventory()
    assert data["legacyPublishedWithoutPrint"] == {"modules": 31, "templates": 35}
    BASELINE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "phase": "before", "legacy": data["legacyPublishedWithoutPrint"]}))


def after():
    expected = json.loads(BASELINE.read_text(encoding="utf-8"))
    actual = inventory()
    assert actual == expected, {key: (expected[key], actual[key]) for key in expected if expected[key] != actual[key]}
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(DISTINCT module_id),COUNT(*) FROM un_print_legacy_template WHERE retired_time IS NULL")
            modules, templates = cur.fetchone()
            cur.execute("SELECT module_id,template_id,source_module_version_id FROM un_print_legacy_template "
                        "WHERE module_id IN (67,68,83,92) ORDER BY module_id,template_id")
            samples = [list(row) for row in cur.fetchall()]
    assert (modules, templates) == (31, 35), (modules, templates)
    assert any(row[:2] == [67, 34] for row in samples)
    assert any(row[0] == 83 for row in samples)
    assert not any(row[0] in (68, 92) for row in samples)
    result = {"ok": True, "phase": "after", "unchanged": actual,
              "allowlist": {"modules": modules, "templates": templates, "samples": samples}}
    AFTER.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "phase": "after", "allowlist": {"modules": modules, "templates": templates}}))


def runtime():
    owner = owner_token()
    published = success("/api/admin/module-config/modules/67/published", owner)
    actions = published["configuration"]["actions"]
    assert published["configuration"].get("legacyPrintCompatibility") is True
    assert any(action["code"] == "PRINT" and action["status"] == "ACTIVE" for action in actions)
    templates = success("/api/print/runtime/modules/g7_layout/templates", owner)
    assert any(item["templateId"] == 34 for item in templates)
    job = success("/api/print/runtime/modules/g7_layout/records/120/templates/34", owner, "POST", {})
    assert job["status"] == "SUCCEEDED"
    status, pdf = request(f"/api/print/jobs/{job['id']}/preview", owner, binary=True)
    assert status == 200 and pdf.startswith(b"%PDF"), (status, pdf[:40])
    platform_login = success("/api/auth/login", method="POST",
                             body={"username": "g7_layout_owner_ea39f0f4", "password": "correct-password"})
    denied_status, denied_result = request("/api/print/runtime/modules/g7_layout/templates",
                                           platform_login["accessToken"])
    assert denied_status == 403, (denied_status, denied_result)
    path = EVIDENCE / "s18-module-print-upgrade-runtime.pdf"
    path.write_bytes(pdf)
    result = {"ok": True, "moduleId": 67, "systemId": 141, "templateId": 34, "recordId": 120,
              "legacyProjectedAction": True, "platformScopePrintDenied": denied_result["code"],
              "jobId": job["id"], "pdfSha256": hashlib.sha256(pdf).hexdigest(),
              "pdfBytes": len(pdf), "pdf": str(path.relative_to(ROOT)).replace("\\", "/")}
    (EVIDENCE / "s18-module-print-upgrade-runtime.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


def transition():
    owner = owner_token()
    before = json.loads((EVIDENCE / "s18-module-print-upgrade-runtime.json").read_text(encoding="utf-8"))
    job_id = before["jobId"]
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT current_version_id,version FROM un_cfg_module_publication WHERE module_id=67")
            old_version_id, publication_version = cur.fetchone()
            cur.execute("SELECT schema_hash,snapshot_json FROM un_cfg_module_version WHERE id=%s", (old_version_id,))
            old_hash, old_json = cur.fetchone()
            cur.execute("SELECT id,module_config_version_id,template_version_id,status,output_file_id "
                        "FROM un_print_job WHERE module_id=67 ORDER BY id")
            old_jobs = cur.fetchall()
    result = success(f"/api/admin/module-config/modules/67/legacy-print-transition"
                     f"?expectedPublicationVersion={publication_version}", owner, "POST")
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT schema_hash,snapshot_json FROM un_cfg_module_version WHERE id=%s", (old_version_id,))
            assert cur.fetchone() == (old_hash, old_json)
            cur.execute("SELECT id,module_config_version_id,template_version_id,status,output_file_id "
                        "FROM un_print_job WHERE module_id=67 ORDER BY id")
            assert cur.fetchall() == old_jobs
            cur.execute("SELECT COUNT(*) FROM un_print_legacy_template WHERE module_id=67 AND retired_time IS NULL")
            assert cur.fetchone()[0] == 0
            cur.execute("SELECT COUNT(DISTINCT module_id),COUNT(*) FROM un_print_legacy_template "
                        "WHERE retired_time IS NULL")
            remaining_modules, remaining_templates = cur.fetchone()
    published = success("/api/admin/module-config/modules/67/published", owner)
    assert "legacyPrintCompatibility" not in published["configuration"]
    assert any(action["code"] == "PRINT" for action in published["configuration"]["actions"])
    status, pdf = request(f"/api/print/jobs/{job_id}/preview", owner, binary=True)
    assert status == 200 and hashlib.sha256(pdf).hexdigest() == before["pdfSha256"]
    duplicate_status, duplicate = request(
        f"/api/admin/module-config/modules/67/legacy-print-transition"
        f"?expectedPublicationVersion={publication_version + 1}", owner, "POST")
    assert duplicate_status == 409, (duplicate_status, duplicate)
    readback = {"ok": True, "moduleId": 67, "oldVersionId": old_version_id,
                "newVersionId": result["versionId"], "retired": True,
                "oldJobsUnchanged": len(old_jobs), "oldJobPdfSha256": before["pdfSha256"],
                "duplicateDenied": duplicate["code"],
                "remainingCompatibility": {"modules": remaining_modules, "templates": remaining_templates}}
    (EVIDENCE / "s18-module-print-upgrade-transition.json").write_text(
        json.dumps(readback, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(readback))


def restart():
    with database() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1")
            assert cur.fetchone()[0] == "22"
            cur.execute("SELECT COUNT(DISTINCT module_id),COUNT(*) FROM un_print_legacy_template "
                        "WHERE retired_time IS NULL")
            modules, templates = cur.fetchone()
            assert (modules, templates) == (30, 33), (modules, templates)
    legacy_owner = system_token("g7_layout_owner_c16852a7", 140)
    legacy = success("/api/admin/module-config/modules/66/published", legacy_owner)
    assert legacy["configuration"].get("legacyPrintCompatibility") is True
    assert any(item["templateId"] == 32 for item in
               success("/api/print/runtime/modules/g7_layout/templates", legacy_owner))
    transitioned_owner = owner_token()
    transitioned = success("/api/admin/module-config/modules/67/published", transitioned_owner)
    assert "legacyPrintCompatibility" not in transitioned["configuration"]
    assert any(action["code"] == "PRINT" for action in transitioned["configuration"]["actions"])
    prior = json.loads((EVIDENCE / "s18-module-print-upgrade-transition.json").read_text(encoding="utf-8"))
    job_id = json.loads((EVIDENCE / "s18-module-print-upgrade-runtime.json").read_text(encoding="utf-8"))["jobId"]
    status, pdf = request(f"/api/print/jobs/{job_id}/preview", transitioned_owner, binary=True)
    assert status == 200 and hashlib.sha256(pdf).hexdigest() == prior["oldJobPdfSha256"]
    result = {"ok": True, "restartReadback": True, "migrationVersion": 22,
              "compatibility": {"modules": modules, "templates": templates},
              "legacyModule66ProjectedPrint": True, "module67Retired": True,
              "module67JobId": job_id, "module67PdfSha256": prior["oldJobPdfSha256"]}
    (EVIDENCE / "s18-module-print-upgrade-restart.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    phase = sys.argv[1]
    {"before": before, "after": after, "runtime": runtime,
     "transition": transition, "restart": restart}[phase]()
