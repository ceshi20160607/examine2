package com.unique.examine.openapi.repository.jdbc;

final class OpenApiCallbackSql {
    private OpenApiCallbackSql() { }

    static final String COLUMNS = """
            s.id,s.system_id,s.tenant_id,s.application_id,s.name,s.status,
            s.current_config_version,s.created_at,s.created_by,s.updated_at,s.updated_by,s.version,
            v.id AS callback_version_id,v.config_version,v.endpoint_url,v.event_types_json,
            v.secret_ref,v.signing_secret_version,v.max_attempts,v.base_backoff_seconds,
            v.status AS callback_version_status,v.activated_at,v.retired_at,
            v.created_at AS callback_version_created_at,v.created_by AS callback_version_created_by
            """;

    static final String SELECT = """
            SELECT %s FROM un_openapi_callback_subscription s
            JOIN un_openapi_callback_version v ON v.subscription_id=s.id
              AND v.config_version=s.current_config_version
            WHERE s.system_id=? AND s.tenant_id=? AND s.application_id=? AND s.id=?
            """.formatted(COLUMNS);
    static final String LIST = """
            SELECT %s FROM un_openapi_callback_subscription s
            JOIN un_openapi_callback_version v ON v.subscription_id=s.id
              AND v.config_version=s.current_config_version
            WHERE s.system_id=? AND s.tenant_id=? AND s.application_id=?
            ORDER BY s.updated_at DESC,s.id DESC
            """.formatted(COLUMNS);
    static final String LIST_ACTIVE_EVENT = """
            SELECT %s FROM un_openapi_callback_subscription s
            JOIN un_openapi_callback_version v ON v.subscription_id=s.id
              AND v.config_version=s.current_config_version
            JOIN un_openapi_application a ON a.id=s.application_id AND a.system_id=s.system_id
              AND a.tenant_id=s.tenant_id
            WHERE s.system_id=? AND s.tenant_id=? AND s.application_id=?
              AND s.status='ACTIVE' AND v.status='ACTIVE' AND a.status='ACTIVE'
              AND JSON_CONTAINS(v.event_types_json,JSON_QUOTE(?))
            ORDER BY s.id
            """.formatted(COLUMNS);
    static final String INSERT_SUBSCRIPTION = """
            INSERT INTO un_openapi_callback_subscription
              (id,system_id,tenant_id,application_id,name,status,current_config_version,
               created_at,created_by,updated_at,updated_by,version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String INSERT_VERSION = """
            INSERT INTO un_openapi_callback_version
              (id,subscription_id,config_version,endpoint_url,event_types_json,secret_ref,
               signing_secret_version,max_attempts,base_backoff_seconds,status,activated_at,
               retired_at,created_at,created_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String REPLACE_CURRENT_VERSION = """
            UPDATE un_openapi_callback_subscription
            SET name=?,current_config_version=?,updated_at=?,updated_by=?,version=version+1
            WHERE system_id=? AND tenant_id=? AND application_id=? AND id=? AND version=?
            """;
    static final String RETIRE_VERSION = """
            UPDATE un_openapi_callback_version SET status='RETIRED',retired_at=?
            WHERE id=? AND subscription_id=? AND status='ACTIVE'
            """;
    static final String CHANGE_STATUS = """
            UPDATE un_openapi_callback_subscription
            SET status=?,updated_at=?,updated_by=?,version=version+1
            WHERE system_id=? AND tenant_id=? AND application_id=? AND id=? AND version=?
            """;
    static final String INSERT_DELIVERY = """
            INSERT INTO un_openapi_callback_delivery
              (id,system_id,tenant_id,application_id,subscription_id,callback_version_id,
               event_id,event_type,payload_json,payload_hash,status,attempt_count,last_http_status,
               failure_code,request_id,trace_id,created_at,updated_at,completed_at,version)
            VALUES (?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String DELIVERY_COLUMNS = """
            d.id,d.system_id,d.tenant_id,d.application_id,d.subscription_id,d.callback_version_id,
            d.event_id,d.event_type,CAST(d.payload_json AS CHAR) AS payload_json,d.payload_hash,
            d.status,d.attempt_count,d.last_http_status,d.failure_code,d.request_id,d.trace_id,
            d.created_at,d.updated_at,d.completed_at,d.version
            """;
    static final String DELIVERY_SCOPE_COLUMNS = """
            s.id AS subscription_row_id,s.system_id AS subscription_system_id,
            s.tenant_id AS subscription_tenant_id,s.application_id AS subscription_application_id,
            s.name AS subscription_name,s.status AS subscription_status,
            s.current_config_version AS subscription_current_config_version,
            s.created_at AS subscription_created_at,s.created_by AS subscription_created_by,
            s.updated_at AS subscription_updated_at,s.updated_by AS subscription_updated_by,
            s.version AS subscription_version,
            v.id AS version_row_id,v.config_version AS version_config_version,
            v.endpoint_url AS version_endpoint_url,v.event_types_json AS version_event_types_json,
            v.secret_ref AS version_secret_ref,v.signing_secret_version AS version_signing_secret_version,
            v.max_attempts AS version_max_attempts,
            v.base_backoff_seconds AS version_base_backoff_seconds,
            v.status AS version_status,v.activated_at AS version_activated_at,
            v.retired_at AS version_retired_at,v.created_at AS version_created_at,
            v.created_by AS version_created_by
            """;
    static final String FIND_DELIVERY = """
            SELECT %s,%s
            FROM un_openapi_callback_delivery d
            JOIN un_openapi_callback_subscription s ON s.id=d.subscription_id
              AND s.system_id=d.system_id AND s.tenant_id=d.tenant_id
              AND s.application_id=d.application_id
            JOIN un_openapi_callback_version v ON v.id=d.callback_version_id
              AND v.subscription_id=s.id
            WHERE d.id=?
            """.formatted(DELIVERY_COLUMNS, DELIVERY_SCOPE_COLUMNS);
    static final String UPDATE_DELIVERY = """
            UPDATE un_openapi_callback_delivery
            SET status=?,attempt_count=?,last_http_status=?,failure_code=?,updated_at=?,completed_at=?,
                version=version+1
            WHERE id=? AND attempt_count=? AND version=? AND status IN ('PENDING','RETRYING')
            """;
    static final String INSERT_ATTEMPT = """
            INSERT INTO un_openapi_callback_attempt
              (id,delivery_id,attempt_no,outcome,http_status,duration_ms,failure_code,started_at,completed_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            """;
    static final String LIST_DELIVERIES = """
            SELECT %s FROM un_openapi_callback_delivery d
            WHERE d.system_id=? AND d.tenant_id=? AND d.application_id=? AND d.subscription_id=?
            ORDER BY d.created_at DESC,d.id DESC LIMIT ? OFFSET ?
            """.formatted(DELIVERY_COLUMNS);
    static final String COUNT_DELIVERIES = """
            SELECT COUNT(*) FROM un_openapi_callback_delivery
            WHERE system_id=? AND tenant_id=? AND application_id=? AND subscription_id=?
            """;
}
