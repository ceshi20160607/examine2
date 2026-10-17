"""读取 S19.4 独立库的发布、账号、系统成员与审计结果，不修改业务数据。"""

import hashlib
import json
import subprocess
from pathlib import Path


DATABASE = "unexamine_s19_saml"
PACKAGE = Path("projects/unexamine/release/0.1.0-s19.4-dev1.zip")
EVIDENCE = Path("projects/unexamine/ai/evidence/s19-saml-db-readback.json")


def scalar(sql: str) -> str:
    """经容器内 MySQL 只读查询当前隔离库；每条 SQL 对应一个发布验收计数。"""
    if not sql.startswith("SELECT "):
        raise ValueError("Only read-only acceptance queries are allowed")
    result = subprocess.run(
        ["docker", "exec", "-i", "unexamine-s19-mysql", "sh", "-c",
         'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database="$1" '
         '--batch --skip-column-names --execute="$2"', "sh", DATABASE, sql],
        capture_output=True, text=True, check=False,
    )
    if result.returncode:
        raise RuntimeError(result.stderr or f"MySQL exited {result.returncode}")
    return result.stdout.strip()


readback = {
    "step": "S19.4",
    "candidate": "0.1.0-s19.4-dev1",
    "schema": DATABASE,
    "flywayVersion": int(scalar("SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) "
                                "FROM flyway_schema_history WHERE success=1")),
    "providerPublished": int(scalar("SELECT COUNT(*) FROM un_plat_sso_provider "
                                    "WHERE code='s19-saml2' AND status='PUBLISHED' "
                                    "AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.samlIdentityValidation'))="
                                    "'SIGNED_RESPONSE_POST_V1'")),
    "identityBound": int(scalar("SELECT COUNT(*) FROM un_plat_sso_identity "
                               "WHERE provider_id=1 AND external_subject='s19-saml-subject'")),
    "systemBindingActive": int(scalar("SELECT COUNT(*) FROM un_sys_sso_binding "
                                     "WHERE provider_id=1 AND status='ACTIVE'")),
    "successfulSsoLogCount": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                         "WHERE event_code='SSO_LOGIN' AND result_code='SUCCESS' "
                                         "AND detail_json LIKE '%s19-saml2%'")),
    "systemEnterSuccessCount": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                            "WHERE event_code='SYSTEM_ENTER' AND result_code='SUCCESS' "
                                            "AND actor_account_id=(SELECT account_id FROM un_plat_sso_identity "
                                            "WHERE provider_id=1 AND external_subject='s19-saml-subject')")),
    "invalidTicketAuditCount": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                          "WHERE event_code='SSO_LOGIN' AND result_code='SSO_STATE_INVALID' "
                                          "AND detail_json LIKE '%s19-saml2%'")),
    "deniedSystemAuditCount": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                          "WHERE event_code='SYSTEM_ENTER' AND result_code='SYSTEM_ACCESS_DENIED' "
                                          "AND actor_account_id=(SELECT account_id FROM un_plat_sso_identity "
                                          "WHERE provider_id=1 AND external_subject='s19-saml-subject')")),
    "loginAuditTraceIdPresent": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                              "WHERE event_code='SSO_LOGIN' AND result_code='SUCCESS' "
                                              "AND detail_json LIKE '%s19-saml2%' AND trace_id<>''")) > 0,
    "loginAuditRequestIdPresent": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                                "WHERE event_code='SSO_LOGIN' AND result_code='SUCCESS' "
                                                "AND detail_json LIKE '%s19-saml2%' AND request_id IS NOT NULL")) > 0,
    "packageSha256": hashlib.sha256(PACKAGE.read_bytes()).hexdigest(),
}
if not (readback["flywayVersion"] == 23 and readback["providerPublished"] == 1
        and readback["identityBound"] == 1 and readback["systemBindingActive"] == 1
        and readback["successfulSsoLogCount"] >= 1 and readback["systemEnterSuccessCount"] >= 1
        and readback["invalidTicketAuditCount"] >= 1 and readback["deniedSystemAuditCount"] >= 1
        and readback["loginAuditTraceIdPresent"] and readback["loginAuditRequestIdPresent"]):
    raise RuntimeError(f"SAML release readback failed: {readback}")
EVIDENCE.write_text(json.dumps(readback, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(readback, ensure_ascii=False))
