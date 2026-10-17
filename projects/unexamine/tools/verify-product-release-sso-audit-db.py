"""重启后只读检查同一隔离库中 S19.6 企业登录、系统准入和本地登录的审计边界。"""

import hashlib
import json
import re
import subprocess
from pathlib import Path


ROOT = Path("projects/unexamine")
EVIDENCE = ROOT / "ai/evidence"
API = json.loads((EVIDENCE / "s19-unified-sso-api.json").read_text(encoding="utf-8"))
PROTOCOLS = json.loads((EVIDENCE / "s19-unified-sso-shared-protocols.json").read_text(encoding="utf-8"))


def scalar(sql: str) -> str:
    """固定验收 SQL 只读访问现有 MySQL 容器的隔离库。"""
    if not sql.startswith("SELECT "):
        raise ValueError("Read-only SQL required")
    result = subprocess.run(
        ["docker", "exec", "-i", "mysql", "sh", "-c",
         'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" '
         '--database="$1" --batch --skip-column-names --execute="$2"',
         "sh", "unexamine_s19_ldap", sql],
        capture_output=True, text=True, check=False,
    )
    if result.returncode:
        raise RuntimeError(result.stderr or f"MySQL exited {result.returncode}")
    return result.stdout.strip()


def safe(value: str) -> str:
    if not re.fullmatch(r"[a-zA-Z0-9_.-]{1,80}", value):
        raise ValueError("Unexpected evidence identifier")
    return value


def audited(event_id: int, provider: str, login_trace: str, entry_trace: str,
            result_code: str, mapping: str, session_id: int, system_id: int) -> bool:
    """逐条核对关联事件，防止只有聚合计数却关联到别的登录或系统。"""
    return scalar(
        "SELECT COUNT(*) FROM un_audit_event WHERE "
        f"id={int(event_id)} AND event_code='SSO_LOGIN_SYSTEM_ENTER' "
        f"AND result_code='{safe(result_code)}' AND system_id={int(system_id)} "
        f"AND object_type='AUTH_SESSION' AND object_id='{int(session_id)}' "
        f"AND trace_id='{safe(entry_trace)}' AND request_id='{safe(entry_trace)}' "
        f"AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.identityProvider'))='{safe(provider)}' "
        f"AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.loginTraceId'))='{safe(login_trace)}' "
        f"AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.systemMemberMapping'))='{safe(mapping)}' "
        "AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.accountBinding'))='MATCHED' "
        "AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.authenticationMethod'))<>'' "
        "AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.ip'))<>'' "
        "AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.device'))<>''"
    ) == "1"


ldap = {
    "authorized": audited(API["authorizedAuditId"], "s19-ldap", API["loginTraceId"],
                          API["acceptedEntryTraceId"], "SUCCESS", "MATCHED",
                          API["enterprisePlatformSessionId"], API["authorizedSystemId"]),
    "unauthorizedSystem": audited(API["deniedAuditId"], "s19-ldap", API["loginTraceId"],
                                  API["deniedEntryTraceId"], "SYSTEM_ACCESS_DENIED", "DENIED",
                                  API["enterprisePlatformSessionId"], API["deniedSystemId"]),
    "tenantDenied": audited(API["tenantDeniedAuditId"], "s19-ldap", API["loginTraceId"],
                            API["tenantDeniedTraceId"], "TENANT_SWITCH_NOT_AVAILABLE", "DENIED",
                            API["enterprisePlatformSessionId"], API["authorizedSystemId"]),
    "disabledMember": audited(API["disabledMemberAuditId"], "s19-ldap", API["loginTraceId"],
                              API["disabledMemberTraceId"], "SYSTEM_ACCESS_DENIED", "DENIED",
                              API["enterprisePlatformSessionId"], API["authorizedSystemId"]),
}
protocols = {row["protocol"]: audited(row["auditId"], row["provider"], row["loginTraceId"],
                                     row["systemEntryTraceId"], "SUCCESS", "MATCHED",
                                     row["platformSessionId"], row["systemId"])
             for row in PROTOCOLS["results"]}
readback = {
    "step": "S19.6", "candidate": "0.1.0-s19.6-dev2", "schema": "unexamine_s19_ldap",
    "flywayVersion": int(scalar("SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) "
                                "FROM flyway_schema_history WHERE success=1")),
    "ldapEvents": ldap, "protocolEvents": protocols,
    "platformOnlyLogin": scalar("SELECT COUNT(*) FROM un_audit_event WHERE event_code='SSO_LOGIN' "
                                f"AND result_code='SUCCESS' AND object_id='{int(API['enterprisePlatformSessionId'])}' "
                                "AND system_id IS NULL AND member_id IS NULL "
                                "AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.systemMemberMapping'))='NOT_REQUESTED'") == "1",
    "localLoginDistinct": scalar("SELECT COUNT(*) FROM un_audit_event WHERE event_code='SSO_LOGIN' "
                                 f"AND object_id='{int(API['localPlatformSessionId'])}'") == "0",
    "denialsHaveNoAuthorizedTenant": scalar("SELECT COUNT(*) FROM un_audit_event WHERE id IN "
                                             f"({int(API['deniedAuditId'])},{int(API['tenantDeniedAuditId'])},"
                                             f"{int(API['disabledMemberAuditId'])}) AND tenant_id IS NULL") == "3",
    "disabledMemberRestored": scalar("SELECT status FROM un_sys_member WHERE "
                                      f"id={int(API['authorizedMemberId'])}") == "ACTIVE",
    "passwordStoredCount": int(scalar("SELECT "
                                      "(SELECT COUNT(*) FROM un_plat_sso_identity WHERE attributes_json LIKE '%fixture-user-password%')+"
                                      "(SELECT COUNT(*) FROM un_audit_event WHERE detail_json LIKE '%fixture-user-password%')+"
                                      "(SELECT COUNT(*) FROM un_plat_sso_provider WHERE metadata_json LIKE '%fixture-user-password%')")),
    "jarSha256": hashlib.sha256((ROOT / "backend/target/unexamine-server-0.1.0-SNAPSHOT.jar").read_bytes()).hexdigest(),
    "frontendIndexSha256": hashlib.sha256((ROOT / "frontend/dist/index.html").read_bytes()).hexdigest(),
}
if (readback["flywayVersion"] != 23 or not all(ldap.values()) or not all(protocols.values())
        or not all(readback[key] for key in ("platformOnlyLogin", "localLoginDistinct",
                                            "denialsHaveNoAuthorizedTenant", "disabledMemberRestored"))
        or readback["passwordStoredCount"] != 0):
    raise RuntimeError(f"S19.6 restart readback failed: {readback}")
(EVIDENCE / "s19-unified-sso-db-readback.json").write_text(
    json.dumps(readback, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(readback, ensure_ascii=False))
