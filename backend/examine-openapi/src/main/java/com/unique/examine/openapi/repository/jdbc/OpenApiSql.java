package com.unique.examine.openapi.repository.jdbc;

public final class OpenApiSql {
    private OpenApiSql() {
    }

    static final String APPLICATION_COLUMNS = """
            a.id,a.system_id,a.tenant_id,a.service_member_id,a.app_key,a.name,a.status,
            a.scopes_json,a.ip_allowlist_json,a.rate_limit_per_minute,
            a.current_credential_version,a.created_at,a.created_by,a.updated_at,a.updated_by,a.version,
            c.id AS credential_id,c.credential_version,c.secret_ref,
            c.status AS credential_status,c.activated_at,c.revoked_at,
            c.created_at AS credential_created_at,c.created_by AS credential_created_by
            """;

    public static final String SELECT_BY_APP_KEY = """
            SELECT %s
            FROM un_openapi_application a
            JOIN un_openapi_credential c
              ON c.application_id=a.id
             AND c.credential_version=a.current_credential_version
            WHERE a.app_key=?
            """.formatted(APPLICATION_COLUMNS);

    public static final String SELECT_APPLICATION = """
            SELECT %s
            FROM un_openapi_application a
            JOIN un_openapi_credential c
              ON c.application_id=a.id
             AND c.credential_version=a.current_credential_version
            WHERE a.system_id=? AND a.tenant_id=? AND a.id=?
            """.formatted(APPLICATION_COLUMNS);

    public static final String SELECT_APPLICATIONS = """
            SELECT %s
            FROM un_openapi_application a
            JOIN un_openapi_credential c
              ON c.application_id=a.id
             AND c.credential_version=a.current_credential_version
            WHERE a.system_id=? AND a.tenant_id=?
            ORDER BY a.updated_at DESC,a.id DESC
            LIMIT ? OFFSET ?
            """.formatted(APPLICATION_COLUMNS);

    public static final String COUNT_APPLICATIONS = """
            SELECT COUNT(*)
            FROM un_openapi_application
            WHERE system_id=? AND tenant_id=?
            """;

    public static final String INSERT_APPLICATION = """
            INSERT INTO un_openapi_application
              (id,system_id,tenant_id,service_member_id,app_key,name,status,
               scopes_json,ip_allowlist_json,rate_limit_per_minute,current_credential_version,
               created_at,created_by,updated_at,updated_by,version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String INSERT_CREDENTIAL = """
            INSERT INTO un_openapi_credential
              (id,application_id,credential_version,secret_ref,status,
               activated_at,revoked_at,created_at,created_by)
            VALUES (?,?,?,?,?,?,?,?,?)
            """;

    public static final String UPDATE_POLICY = """
            UPDATE un_openapi_application
            SET service_member_id=?,name=?,scopes_json=?,ip_allowlist_json=?,
                rate_limit_per_minute=?,updated_at=?,updated_by=?,version=version+1
            WHERE system_id=? AND tenant_id=? AND id=? AND version=?
            """;

    public static final String UPDATE_STATUS = """
            UPDATE un_openapi_application
            SET status=?,updated_at=?,updated_by=?,version=version+1
            WHERE system_id=? AND tenant_id=? AND id=? AND version=?
            """;

    public static final String UPDATE_CURRENT_CREDENTIAL = """
            UPDATE un_openapi_application
            SET current_credential_version=?,updated_at=?,updated_by=?,version=version+1
            WHERE system_id=? AND tenant_id=? AND id=? AND version=?
            """;

    public static final String REVOKE_CREDENTIAL = """
            UPDATE un_openapi_credential
            SET status='REVOKED',revoked_at=?
            WHERE application_id=? AND credential_version=? AND status='ACTIVE'
            """;

    public static final String DELETE_EXPIRED_NONCES = """
            DELETE FROM un_openapi_nonce
            WHERE application_id=? AND expires_at<?
            """;

    public static final String INSERT_NONCE = """
            INSERT INTO un_openapi_nonce
              (application_id,credential_version,nonce,expires_at,created_at)
            VALUES (?,?,?,?,?)
            """;

    public static final String INSERT_RATE_BUCKET = """
            INSERT IGNORE INTO un_openapi_rate_bucket
              (application_id,window_start,request_count,version)
            VALUES (?,?,0,0)
            """;

    public static final String SELECT_RATE_BUCKET_FOR_UPDATE = """
            SELECT application_id,window_start,request_count,version
            FROM un_openapi_rate_bucket
            WHERE application_id=? AND window_start=?
            FOR UPDATE
            """;

    public static final String INCREMENT_RATE_BUCKET = """
            UPDATE un_openapi_rate_bucket
            SET request_count=request_count+1,version=version+1
            WHERE application_id=? AND window_start=?
              AND request_count=? AND version=?
            """;

    public static final String INSERT_CALL_LOG = """
            INSERT INTO un_openapi_call_log
              (id,application_id,app_key_hash,credential_version,route_template,
               request_method,result_category,http_status,latency_ms,request_id,
               trace_id,observed_ip,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String COUNT_APPLICATION_CALL_LOGS = """
            SELECT COUNT(*)
            FROM un_openapi_call_log
            WHERE application_id=?
              AND (?='ALL' OR result_category=?)
              AND (?='ALL' OR request_method=?)
            """;

    public static final String SELECT_APPLICATION_CALL_LOGS = """
            SELECT id,credential_version,route_template,request_method,
              result_category,http_status,latency_ms,request_id,trace_id,
              observed_ip,created_at
            FROM un_openapi_call_log
            WHERE application_id=?
              AND (?='ALL' OR result_category=?)
              AND (?='ALL' OR request_method=?)
            ORDER BY created_at DESC,id DESC
            LIMIT ? OFFSET ?
            """;
}
