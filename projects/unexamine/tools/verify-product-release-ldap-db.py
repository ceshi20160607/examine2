"""只读检查现有 MySQL 容器中 S19.5 隔离库的目录映射、审计和密码边界。"""

import hashlib
import json
import subprocess
from pathlib import Path


DATABASE = "unexamine_s19_ldap"
JAR = Path("projects/unexamine/backend/target/unexamine-server-0.1.0-SNAPSHOT.jar")
FRONTEND = Path("projects/unexamine/frontend/dist/index.html")
EVIDENCE = Path("projects/unexamine/ai/evidence/s19-ldap-db-readback.json")


def scalar(sql: str) -> str:
    """通过正在运行的 MySQL 容器只执行固定只读 SQL，避免接触其他数据库。"""
    if not sql.startswith("SELECT "):
        raise ValueError("Only read-only acceptance queries are allowed")
    result = subprocess.run(
        ["docker", "exec", "-i", "mysql", "sh", "-c",
         'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --database="$1" '
         '--batch --skip-column-names --execute="$2"', "sh", DATABASE, sql],
        capture_output=True, text=True, check=False,
    )
    if result.returncode:
        raise RuntimeError(result.stderr or f"MySQL exited {result.returncode}")
    return result.stdout.strip()


readback = {
    "step": "S19.5",
    "candidate": "0.1.0-s19.5-dev1",
    "schema": DATABASE,
    "flywayVersion": int(scalar("SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) "
                                "FROM flyway_schema_history WHERE success=1")),
    "providerPublished": int(scalar("SELECT COUNT(*) FROM un_plat_sso_provider "
                                    "WHERE code='s19-ldap' AND status='PUBLISHED' "
                                    "AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.ldapIdentityValidation'))="
                                    "'LDAPS_BIND_SEARCH_V1'")),
    "identityBound": int(scalar("SELECT COUNT(*) FROM un_plat_sso_identity "
                                "WHERE provider_id=1 AND external_subject='alice'")),
    "systemBindingActive": int(scalar("SELECT COUNT(*) FROM un_sys_sso_binding "
                                     "WHERE provider_id=1 AND status='ACTIVE'")),
    "successfulSsoLogCount": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                         "WHERE event_code='SSO_LOGIN' AND result_code='SUCCESS' "
                                         "AND detail_json LIKE '%s19-ldap%'")),
    "systemEnterSuccessCount": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                            "WHERE event_code='SYSTEM_ENTER' AND result_code='SUCCESS' "
                                            "AND actor_account_id=(SELECT account_id FROM un_plat_sso_identity "
                                            "WHERE provider_id=1 AND external_subject='alice')")),
    "deniedSystemAuditCount": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                         "WHERE event_code='SYSTEM_ENTER' AND result_code='SYSTEM_ACCESS_DENIED' "
                                         "AND actor_account_id=(SELECT account_id FROM un_plat_sso_identity "
                                         "WHERE provider_id=1 AND external_subject='alice')")),
    "directoryFailureAudits": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                          "WHERE event_code='SSO_LOGIN' AND result_code IN "
                                          "('SSO_DIRECTORY_CREDENTIAL_INVALID','SSO_DIRECTORY_IDENTITY_MISSING',"
                                          "'SSO_DIRECTORY_IDENTITY_CONFLICT','SSO_DOMAIN_NOT_ALLOWED','SSO_MAPPING_MISSING') "
                                          "AND detail_json LIKE '%s19-ldap%'")),
    "loginAuditTraceIdPresent": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                             "WHERE event_code='SSO_LOGIN' AND result_code='SUCCESS' "
                                             "AND detail_json LIKE '%s19-ldap%' AND trace_id<>''")) > 0,
    "loginAuditRequestIdPresent": int(scalar("SELECT COUNT(*) FROM un_audit_event "
                                               "WHERE event_code='SSO_LOGIN' AND result_code='SUCCESS' "
                                               "AND detail_json LIKE '%s19-ldap%' AND request_id IS NOT NULL")) > 0,
    "passwordStoredCount": int(scalar("SELECT "
                                       "(SELECT COUNT(*) FROM un_plat_sso_identity WHERE attributes_json LIKE '%fixture-user-password%')+"
                                       "(SELECT COUNT(*) FROM un_audit_event WHERE detail_json LIKE '%fixture-user-password%')+"
                                       "(SELECT COUNT(*) FROM un_plat_sso_provider WHERE metadata_json LIKE '%fixture-user-password%')")),
    "jarSha256": hashlib.sha256(JAR.read_bytes()).hexdigest(),
    "frontendIndexSha256": hashlib.sha256(FRONTEND.read_bytes()).hexdigest(),
}
if not (readback["flywayVersion"] == 23 and readback["providerPublished"] == 1
        and readback["identityBound"] == 1 and readback["systemBindingActive"] == 1
        and readback["successfulSsoLogCount"] >= 1 and readback["systemEnterSuccessCount"] >= 1
        and readback["deniedSystemAuditCount"] >= 1 and readback["directoryFailureAudits"] >= 5
        and readback["loginAuditTraceIdPresent"] and readback["loginAuditRequestIdPresent"]
        and readback["passwordStoredCount"] == 0):
    raise RuntimeError(f"LDAP release readback failed: {readback}")
EVIDENCE.write_text(json.dumps(readback, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(readback, ensure_ascii=False))
