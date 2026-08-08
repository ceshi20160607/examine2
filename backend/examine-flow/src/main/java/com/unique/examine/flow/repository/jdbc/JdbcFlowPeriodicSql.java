package com.unique.examine.flow.repository.jdbc;

public final class JdbcFlowPeriodicSql {
    private JdbcFlowPeriodicSql() {
    }

    public static final String UPSERT_SCHEDULE = """
            INSERT INTO un_flow_periodic_schedule
              (system_id,tenant_id,definition_id,definition_version,requester_id,
               interval_minutes,start_at,next_fire_at,last_scheduled_at,last_instance_id,
               status,pause_reason,updated_at)
            VALUES (?,?,?,?,?,?,?,?,NULL,NULL,'ACTIVE',NULL,?)
            ON DUPLICATE KEY UPDATE
              definition_version=VALUES(definition_version),
              requester_id=VALUES(requester_id),
              interval_minutes=VALUES(interval_minutes),
              start_at=VALUES(start_at),
              next_fire_at=VALUES(next_fire_at),
              last_scheduled_at=NULL,
              last_instance_id=NULL,
              status='ACTIVE',
              pause_reason=NULL,
              updated_at=VALUES(updated_at)
            """;

    public static final String DELETE_SCHEDULE = """
            DELETE FROM un_flow_periodic_schedule
            WHERE system_id=? AND tenant_id=? AND definition_id=?
            """;

    public static final String SELECT_SCHEDULE = """
            SELECT system_id,tenant_id,definition_id,definition_version,requester_id,
                   interval_minutes,start_at,next_fire_at,last_scheduled_at,last_instance_id,
                   status,pause_reason,updated_at
            FROM un_flow_periodic_schedule
            WHERE system_id=? AND tenant_id=? AND definition_id=?
            """;

    public static final String SELECT_DUE_KEYS = """
            SELECT system_id,tenant_id,definition_id
            FROM un_flow_periodic_schedule
            WHERE status='ACTIVE' AND next_fire_at<=?
            ORDER BY next_fire_at,system_id,tenant_id,definition_id
            LIMIT ?
            """;

    public static final String SELECT_SCHEDULE_FOR_UPDATE = """
            SELECT system_id,tenant_id,definition_id,definition_version,requester_id,
                   interval_minutes,start_at,next_fire_at,last_scheduled_at,last_instance_id,
                   status,pause_reason,updated_at
            FROM un_flow_periodic_schedule
            WHERE system_id=? AND tenant_id=? AND definition_id=?
            FOR UPDATE
            """;

    public static final String UPDATE_FIRED = """
            UPDATE un_flow_periodic_schedule
            SET next_fire_at=?,last_scheduled_at=?,last_instance_id=?,
                status='ACTIVE',pause_reason=NULL,updated_at=?
            WHERE system_id=? AND tenant_id=? AND definition_id=?
              AND definition_version=? AND status='ACTIVE' AND next_fire_at=?
            """;

    public static final String UPDATE_PAUSED = """
            UPDATE un_flow_periodic_schedule
            SET status='PAUSED',pause_reason=?,updated_at=?
            WHERE system_id=? AND tenant_id=? AND definition_id=?
              AND definition_version=? AND status='ACTIVE' AND next_fire_at=?
            """;
}
