package com.unique.examine.flow.repository.jdbc;

final class JdbcApprovalCompletionSql {
    private JdbcApprovalCompletionSql() {
    }

    private static final String EXECUTION_COLUMNS = """
            execution_id,instance_id,definition_id,definition_version,ordinal,
            step_code,step_name,execution_type,parallel_group,config_json,payload_json,status,
            attempt_count,state_version,available_at,lease_owner,
            lease_token_hash,lease_expires_at,created_at,started_at,terminal_at,
            result_json,failure_code,failure_message,failure_retryable
            """;

    private static final String SUBFLOW_RUN_COLUMNS = """
            subflow_run_id,execution_id,attempt_number,launch_key,
            child_instance_id,target_definition_id,target_definition_version,
            root_instance_id,subflow_depth,child_status,launched_at,terminal_at,
            result_code,result_applied_at,state_version
            """;

    static final String INSERT_EXECUTION = """
            INSERT INTO un_flow_completion_execution
              (system_id,tenant_id,execution_id,instance_id,definition_id,
               definition_version,ordinal,step_code,step_name,execution_type,
               parallel_group,config_json,payload_json,status,attempt_count,state_version,
               available_at,lease_owner,lease_token_hash,lease_expires_at,
               created_at,started_at,terminal_at,result_json,failure_code,
               failure_message,failure_retryable)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    static final String SELECT_EXECUTION = """
            SELECT %s
            FROM un_flow_completion_execution
            WHERE system_id=? AND tenant_id=? AND execution_id=?
            """.formatted(EXECUTION_COLUMNS);

    static final String SELECT_EXECUTION_FOR_UPDATE =
            SELECT_EXECUTION + " FOR UPDATE";

    static final String SELECT_BY_INSTANCE = """
            SELECT %s
            FROM un_flow_completion_execution
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            ORDER BY ordinal
            """.formatted(EXECUTION_COLUMNS);

    static final String SELECT_STAGE_FOR_UPDATE = """
            SELECT %s
            FROM un_flow_completion_execution execution_row
            WHERE execution_row.system_id=? AND execution_row.tenant_id=?
              AND execution_row.instance_id=?
              AND (
                execution_row.ordinal=?
                OR (
                  execution_row.parallel_group IS NOT NULL
                  AND execution_row.parallel_group=(
                    SELECT cursor_row.parallel_group
                    FROM un_flow_completion_execution cursor_row
                    WHERE cursor_row.system_id=execution_row.system_id
                      AND cursor_row.tenant_id=execution_row.tenant_id
                      AND cursor_row.instance_id=execution_row.instance_id
                      AND cursor_row.ordinal=?
                  )
                )
              )
            ORDER BY execution_row.ordinal,execution_row.execution_id
            FOR UPDATE
            """.formatted(EXECUTION_COLUMNS);

    static final String SELECT_AVAILABLE_EXTERNAL = """
            SELECT %s
            FROM un_flow_completion_execution
            WHERE system_id=? AND tenant_id=?
              AND execution_type='EXTERNAL_TASK'
              AND (? IS NULL
                OR JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.topic'))=?)
              AND (
                  (status IN ('AVAILABLE','RETRYING') AND available_at<=?)
                  OR (status='LEASED' AND lease_expires_at<=?)
              )
            ORDER BY COALESCE(available_at,lease_expires_at),execution_id
            LIMIT ? OFFSET ?
            """.formatted(EXECUTION_COLUMNS);

    static final String COUNT_AVAILABLE_EXTERNAL = """
            SELECT COUNT(*)
            FROM un_flow_completion_execution
            WHERE system_id=? AND tenant_id=?
              AND execution_type='EXTERNAL_TASK'
              AND (? IS NULL
                OR JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.topic'))=?)
              AND (
                  (status IN ('AVAILABLE','RETRYING') AND available_at<=?)
                  OR (status='LEASED' AND lease_expires_at<=?)
              )
            """;

    static final String SELECT_DUE_WEBHOOK_FOR_UPDATE = """
            SELECT %s
            FROM un_flow_completion_execution
            WHERE system_id=? AND tenant_id=?
              AND execution_type='WEBHOOK'
              AND (
                  (status IN ('AVAILABLE','RETRYING') AND available_at<=?)
                  OR (status='LEASED' AND lease_expires_at<=?)
              )
            ORDER BY COALESCE(available_at,lease_expires_at),execution_id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.formatted(EXECUTION_COLUMNS);

    static final String SELECT_DUE_SUBFLOW_FOR_UPDATE = """
            SELECT %s
            FROM un_flow_completion_execution
            WHERE system_id=? AND tenant_id=?
              AND execution_type='SUBFLOW'
              AND status IN ('AVAILABLE','RETRYING')
              AND available_at<=?
            ORDER BY available_at,execution_id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.formatted(EXECUTION_COLUMNS);

    static final String SELECT_DUE_SUBFLOW = """
            SELECT %s
            FROM un_flow_completion_execution
            WHERE system_id=? AND tenant_id=?
              AND execution_type='SUBFLOW'
              AND status IN ('AVAILABLE','RETRYING')
              AND available_at<=?
            ORDER BY available_at,execution_id
            LIMIT ?
            """.formatted(EXECUTION_COLUMNS);

    static final String UPDATE_EXECUTION = """
            UPDATE un_flow_completion_execution
            SET status=?,attempt_count=?,state_version=?,available_at=?,
                lease_owner=?,lease_token_hash=?,lease_expires_at=?,
                started_at=?,terminal_at=?,result_json=?,failure_code=?,
                failure_message=?,failure_retryable=?
            WHERE system_id=? AND tenant_id=? AND execution_id=?
              AND state_version=?
            """;

    static final String INSERT_ATTEMPT = """
            INSERT INTO un_flow_completion_attempt
              (system_id,tenant_id,attempt_id,execution_id,attempt_number,
               event_sequence,event_type,actor_member_id,lease_owner,
               idempotency_key_hash,result_json,failure_code,failure_message,
               http_status,duration_ms,response_sha256,started_at,completed_at,
               occurred_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    static final String SELECT_ATTEMPTS = """
            SELECT attempt_id,execution_id,attempt_number,event_sequence,
                   event_type,actor_member_id,lease_owner,idempotency_key_hash,
                   result_json,failure_code,failure_message,occurred_at
                   ,http_status,duration_ms,response_sha256,started_at,completed_at
            FROM un_flow_completion_attempt
            WHERE system_id=? AND tenant_id=? AND execution_id=?
            ORDER BY event_sequence
            """;

    static final String INSERT_SUBFLOW_RUN = """
            INSERT INTO un_flow_subflow_run
              (system_id,tenant_id,subflow_run_id,execution_id,attempt_number,
               launch_key,child_instance_id,target_definition_id,
               target_definition_version,root_instance_id,subflow_depth,
               child_status,launched_at,terminal_at,result_code,
               result_applied_at,state_version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    static final String SELECT_SUBFLOW_BY_EXECUTION_ATTEMPT = """
            SELECT %s
            FROM un_flow_subflow_run
            WHERE system_id=? AND tenant_id=?
              AND execution_id=? AND attempt_number=?
            """.formatted(SUBFLOW_RUN_COLUMNS);

    static final String SELECT_SUBFLOW_BY_CHILD = """
            SELECT %s
            FROM un_flow_subflow_run
            WHERE system_id=? AND tenant_id=? AND child_instance_id=?
            """.formatted(SUBFLOW_RUN_COLUMNS);

    static final String SELECT_SUBFLOWS_BY_EXECUTION = """
            SELECT %s
            FROM un_flow_subflow_run
            WHERE system_id=? AND tenant_id=? AND execution_id=?
            ORDER BY attempt_number
            """.formatted(SUBFLOW_RUN_COLUMNS);

    static final String SELECT_PENDING_SUBFLOW_RESULTS_FOR_UPDATE = """
            SELECT %s
            FROM un_flow_subflow_run
            WHERE system_id=? AND tenant_id=?
              AND child_status<>'RUNNING'
              AND result_applied_at IS NULL
            ORDER BY terminal_at,subflow_run_id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.formatted(SUBFLOW_RUN_COLUMNS);

    static final String SELECT_PENDING_SUBFLOW_RESULTS = """
            SELECT %s
            FROM un_flow_subflow_run
            WHERE system_id=? AND tenant_id=?
              AND child_status<>'RUNNING'
              AND result_applied_at IS NULL
            ORDER BY terminal_at,subflow_run_id
            LIMIT ?
            """.formatted(SUBFLOW_RUN_COLUMNS);

    static final String SELECT_RUNNING_SUBFLOWS_FOR_UPDATE = """
            SELECT %s
            FROM un_flow_subflow_run
            WHERE system_id=? AND tenant_id=? AND child_status='RUNNING'
            ORDER BY launched_at,subflow_run_id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.formatted(SUBFLOW_RUN_COLUMNS);

    static final String SELECT_RUNNING_SUBFLOWS = """
            SELECT %s
            FROM un_flow_subflow_run
            WHERE system_id=? AND tenant_id=? AND child_status='RUNNING'
            ORDER BY launched_at,subflow_run_id
            LIMIT ?
            """.formatted(SUBFLOW_RUN_COLUMNS);

    static final String UPDATE_SUBFLOW_RUN = """
            UPDATE un_flow_subflow_run
            SET child_status=?,terminal_at=?,result_code=?,
                result_applied_at=?,state_version=?
            WHERE system_id=? AND tenant_id=? AND subflow_run_id=?
              AND state_version=?
            """;
}
