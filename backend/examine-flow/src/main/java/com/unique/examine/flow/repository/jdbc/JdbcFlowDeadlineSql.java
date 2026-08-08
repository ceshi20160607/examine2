package com.unique.examine.flow.repository.jdbc;

public final class JdbcFlowDeadlineSql {
    private JdbcFlowDeadlineSql() {
    }

    public static final String SELECT_DUE = """
            SELECT due.system_id,due.tenant_id,due.instance_id,due.branch_code,
                   due.event_type,due.event_at
            FROM (
                SELECT instance_row.system_id,instance_row.tenant_id,
                       instance_row.instance_id,CAST(NULL AS CHAR(64)) AS branch_code,
                       CASE
                         WHEN instance_row.deadline_due_at<=? THEN 'TIMEOUT'
                         ELSE 'REMINDER'
                       END AS event_type,
                       CASE
                         WHEN instance_row.deadline_due_at<=? THEN instance_row.deadline_due_at
                         ELSE instance_row.deadline_remind_at
                       END AS event_at
                FROM un_flow_instance instance_row
                WHERE instance_row.status='PENDING'
                  AND instance_row.deadline_policy IS NOT NULL
                  AND instance_row.deadline_processed_at IS NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM un_flow_parallel_branch_execution branch_row
                      WHERE branch_row.system_id=instance_row.system_id
                        AND branch_row.tenant_id=instance_row.tenant_id
                        AND branch_row.instance_id=instance_row.instance_id
                  )
                  AND (
                      instance_row.deadline_due_at<=?
                      OR (
                          instance_row.deadline_remind_at<=?
                          AND instance_row.deadline_reminded_at IS NULL
                          AND instance_row.deadline_due_at>?
                      )
                  )
                UNION ALL
                SELECT branch_row.system_id,branch_row.tenant_id,
                       branch_row.instance_id,branch_row.branch_code,
                       CASE
                         WHEN branch_row.deadline_due_at<=? THEN 'TIMEOUT'
                         ELSE 'REMINDER'
                       END AS event_type,
                       CASE
                         WHEN branch_row.deadline_due_at<=? THEN branch_row.deadline_due_at
                         ELSE branch_row.deadline_remind_at
                       END AS event_at
                FROM un_flow_parallel_branch_execution branch_row
                JOIN un_flow_instance instance_row
                  ON instance_row.system_id=branch_row.system_id
                 AND instance_row.tenant_id=branch_row.tenant_id
                 AND instance_row.instance_id=branch_row.instance_id
                WHERE instance_row.status='PENDING'
                  AND branch_row.status='PENDING'
                  AND branch_row.deadline_policy IS NOT NULL
                  AND branch_row.deadline_processed_at IS NULL
                  AND (
                      branch_row.deadline_due_at<=?
                      OR (
                          branch_row.deadline_remind_at<=?
                          AND branch_row.deadline_reminded_at IS NULL
                          AND branch_row.deadline_due_at>?
                      )
                  )
            ) due
            ORDER BY due.event_at,due.system_id,due.tenant_id,due.instance_id,
                     due.branch_code,due.event_type DESC
            LIMIT ?
            """;

    public static final String LOCK_INSTANCE = """
            SELECT instance_id
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            FOR UPDATE
            """;
}
