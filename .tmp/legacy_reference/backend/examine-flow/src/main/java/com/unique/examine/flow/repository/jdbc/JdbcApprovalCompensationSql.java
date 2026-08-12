package com.unique.examine.flow.repository.jdbc;

final class JdbcApprovalCompensationSql {
    private JdbcApprovalCompensationSql() {
    }

    private static final String COLUMNS = """
            compensation_id,compensation_id AS execution_id,instance_id,
            original_execution_id,original_ordinal,reverse_ordinal,
            definition_id,definition_version,reverse_ordinal AS ordinal,
            step_code,step_name,execution_type,NULL AS parallel_group,
            config_json,payload_json,status,attempt_count,state_version,
            available_at,lease_owner,lease_token_hash,lease_expires_at,
            created_at,started_at,terminal_at,result_json,failure_code,
            failure_message,failure_retryable
            """;

    private static final String ATTEMPT_COLUMNS = """
            attempt_id,compensation_id,compensation_id AS execution_id,
            attempt_number,event_sequence,event_type,actor_member_id,
            lease_owner,idempotency_key_hash,result_json,failure_code,
            failure_message,http_status,duration_ms,response_sha256,
            started_at,completed_at,occurred_at
            """;

    private static final String SUBFLOW_COLUMNS = """
            compensation_subflow_run_id,
            compensation_subflow_run_id AS subflow_run_id,
            compensation_id,compensation_id AS execution_id,attempt_number,
            launch_key,child_instance_id,target_definition_id,
            target_definition_version,root_instance_id,subflow_depth,
            child_status,launched_at,terminal_at,result_code,
            result_applied_at,state_version
            """;

    static final String INSERT = """
            INSERT INTO un_flow_completion_compensation
              (system_id,tenant_id,compensation_id,instance_id,
               original_execution_id,original_ordinal,reverse_ordinal,
               definition_id,definition_version,step_code,step_name,
               execution_type,config_json,payload_json,status,attempt_count,
               state_version,available_at,lease_owner,lease_token_hash,
               lease_expires_at,created_at,started_at,terminal_at,result_json,
               failure_code,failure_message,failure_retryable)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    static final String SELECT = """
            SELECT %s FROM un_flow_completion_compensation
            WHERE system_id=? AND tenant_id=? AND compensation_id=?
            """.formatted(COLUMNS);
    static final String SELECT_FOR_UPDATE = SELECT + " FOR UPDATE";
    static final String SELECT_BY_INSTANCE = """
            SELECT %s FROM un_flow_completion_compensation
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            ORDER BY original_ordinal DESC,compensation_id DESC
            """.formatted(COLUMNS);
    static final String SELECT_PLAN_FOR_UPDATE =
            SELECT_BY_INSTANCE + " FOR UPDATE";

    static final String SELECT_AVAILABLE_EXTERNAL = """
            SELECT %s FROM un_flow_completion_compensation
            WHERE system_id=? AND tenant_id=? AND execution_type='EXTERNAL_TASK'
              AND (? IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(config_json,'$.topic'))=?)
              AND ((status IN ('AVAILABLE','RETRYING') AND available_at<=?)
                OR (status='LEASED' AND lease_expires_at<=?))
            ORDER BY COALESCE(available_at,lease_expires_at),compensation_id
            LIMIT ? OFFSET ?
            """.formatted(COLUMNS);
    static final String COUNT_AVAILABLE_EXTERNAL = """
            SELECT COUNT(*) FROM un_flow_completion_compensation
            WHERE system_id=? AND tenant_id=? AND execution_type='EXTERNAL_TASK'
              AND (? IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(config_json,'$.topic'))=?)
              AND ((status IN ('AVAILABLE','RETRYING') AND available_at<=?)
                OR (status='LEASED' AND lease_expires_at<=?))
            """;
    static final String SELECT_DUE_WEBHOOK_FOR_UPDATE = """
            SELECT %s FROM un_flow_completion_compensation
            WHERE system_id=? AND tenant_id=? AND execution_type='WEBHOOK'
              AND ((status IN ('AVAILABLE','RETRYING') AND available_at<=?)
                OR (status='LEASED' AND lease_expires_at<=?))
            ORDER BY COALESCE(available_at,lease_expires_at),compensation_id
            LIMIT ? FOR UPDATE SKIP LOCKED
            """.formatted(COLUMNS);
    static final String SELECT_DUE_SUBFLOW = """
            SELECT %s FROM un_flow_completion_compensation
            WHERE system_id=? AND tenant_id=? AND execution_type='SUBFLOW'
              AND status IN ('AVAILABLE','RETRYING') AND available_at<=?
            ORDER BY available_at,compensation_id LIMIT ?
            """.formatted(COLUMNS);
    static final String UPDATE = """
            UPDATE un_flow_completion_compensation
            SET status=?,attempt_count=?,state_version=?,available_at=?,
                lease_owner=?,lease_token_hash=?,lease_expires_at=?,
                started_at=?,terminal_at=?,result_json=?,failure_code=?,
                failure_message=?,failure_retryable=?
            WHERE system_id=? AND tenant_id=? AND compensation_id=?
              AND state_version=?
            """;

    static final String INSERT_ATTEMPT = """
            INSERT INTO un_flow_compensation_attempt
              (system_id,tenant_id,attempt_id,compensation_id,attempt_number,
               event_sequence,event_type,actor_member_id,lease_owner,
               idempotency_key_hash,result_json,failure_code,failure_message,
               http_status,duration_ms,response_sha256,started_at,completed_at,
               occurred_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String SELECT_ATTEMPTS = """
            SELECT %s FROM un_flow_compensation_attempt
            WHERE system_id=? AND tenant_id=? AND compensation_id=?
            ORDER BY event_sequence
            """.formatted(ATTEMPT_COLUMNS);

    static final String INSERT_SUBFLOW = """
            INSERT INTO un_flow_compensation_subflow_run
              (system_id,tenant_id,compensation_subflow_run_id,
               compensation_id,attempt_number,launch_key,child_instance_id,
               target_definition_id,target_definition_version,
               root_instance_id,subflow_depth,child_status,launched_at,
               terminal_at,result_code,result_applied_at,state_version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String SELECT_SUBFLOW_ATTEMPT = """
            SELECT %s FROM un_flow_compensation_subflow_run
            WHERE system_id=? AND tenant_id=? AND compensation_id=?
              AND attempt_number=?
            """.formatted(SUBFLOW_COLUMNS);
    static final String SELECT_SUBFLOWS = """
            SELECT %s FROM un_flow_compensation_subflow_run
            WHERE system_id=? AND tenant_id=? AND compensation_id=?
            ORDER BY attempt_number
            """.formatted(SUBFLOW_COLUMNS);
    static final String SELECT_PENDING_SUBFLOWS = """
            SELECT %s FROM un_flow_compensation_subflow_run
            WHERE system_id=? AND tenant_id=? AND child_status<>'RUNNING'
              AND result_applied_at IS NULL
            ORDER BY terminal_at,compensation_subflow_run_id LIMIT ?
            """.formatted(SUBFLOW_COLUMNS);
    static final String UPDATE_SUBFLOW = """
            UPDATE un_flow_compensation_subflow_run
            SET child_status=?,terminal_at=?,result_code=?,
                result_applied_at=?,state_version=?
            WHERE system_id=? AND tenant_id=?
              AND compensation_subflow_run_id=? AND state_version=?
            """;
}
