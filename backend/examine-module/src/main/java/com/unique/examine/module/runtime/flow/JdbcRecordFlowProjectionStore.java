package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcRecordFlowProjectionStore implements RecordFlowProjectionStore {
    private static final String SELECT_PROJECTION = """
            SELECT system_id,tenant_id,record_id,logical_module_id,instance_id,status,version,updated_at,
                   status_field_code,status_approved_value,status_rejected_value,
                   status_withdrawn_value,status_terminated_value
            FROM un_module_record_flow_state
            """;
    private static final String SELECT_ADDITIONAL_PROJECTION = """
            SELECT system_id,tenant_id,record_id,logical_module_id,instance_id,status,version,updated_at,
                   status_field_code,status_approved_value,status_rejected_value,
                   status_withdrawn_value,status_terminated_value
            FROM un_module_record_flow_state_item
            """;

    private final JdbcTemplate jdbc;

    JdbcRecordFlowProjectionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LockedRecord> lockRecord(
            long systemId,
            long tenantId,
            String moduleCode,
            long recordId
    ) {
        var rows = jdbc.query("""
                        SELECT r.logical_module_id,r.status,r.schema_version_id,r.module_snapshot_id
                        FROM un_module_record r
                        JOIN un_module_runtime_schema_module m
                          ON m.system_id=r.system_id
                         AND m.schema_version_id=r.schema_version_id
                         AND m.module_snapshot_id=r.module_snapshot_id
                         AND m.logical_module_id=r.logical_module_id
                        WHERE r.system_id=? AND r.tenant_id=? AND r.record_id=? AND m.module_code=?
                        FOR UPDATE
                        """,
                (result, row) -> new LockedRecord(
                        result.getLong("logical_module_id"),
                        result.getString("status"),
                        result.getLong("schema_version_id"),
                        result.getLong("module_snapshot_id")),
                systemId, tenantId, recordId, moduleCode);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<Projection> lockByRecord(long systemId, long tenantId, long recordId) {
        return one(SELECT_PROJECTION
                        + " WHERE system_id=? AND tenant_id=? AND record_id=? FOR UPDATE",
                systemId, tenantId, recordId);
    }

    @Override
    public Optional<Projection> lockByInstance(long systemId, long tenantId, long instanceId) {
        return one(SELECT_PROJECTION
                        + " WHERE system_id=? AND tenant_id=? AND instance_id=? FOR UPDATE",
                systemId, tenantId, instanceId);
    }

    @Override
    public Optional<Projection> lockAdditionalByInstance(
            long systemId,
            long tenantId,
            long instanceId
    ) {
        return one(SELECT_ADDITIONAL_PROJECTION
                        + " WHERE system_id=? AND tenant_id=? AND instance_id=? FOR UPDATE",
                systemId, tenantId, instanceId);
    }

    @Override
    public boolean lockPendingAdditionalByRecord(long systemId, long tenantId, long recordId) {
        return !jdbc.query(
                        """
                        SELECT instance_id
                        FROM un_module_record_flow_state_item
                        WHERE system_id=? AND tenant_id=? AND record_id=? AND status='PENDING'
                        ORDER BY instance_id ASC
                        FOR UPDATE
                        """,
                        (result, row) -> result.getLong("instance_id"),
                        systemId,
                        tenantId,
                        recordId)
                .isEmpty();
    }

    @Override
    public Optional<Projection> findByRecord(long systemId, long tenantId, long recordId) {
        return one(SELECT_PROJECTION
                        + " WHERE system_id=? AND tenant_id=? AND record_id=?",
                systemId, tenantId, recordId);
    }

    @Override
    public List<Projection> findAdditionalByRecord(long systemId, long tenantId, long recordId) {
        return jdbc.query(
                SELECT_ADDITIONAL_PROJECTION
                        + " WHERE system_id=? AND tenant_id=? AND record_id=?"
                        + " ORDER BY created_at ASC,instance_id ASC",
                (result, row) -> projection(result),
                systemId,
                tenantId,
                recordId);
    }

    @Override
    public void insert(
            long systemId,
            long tenantId,
            long recordId,
            long logicalModuleId,
            long instanceId,
            long memberId,
            Instant occurredAt,
            RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
    ) {
        var timestamp = Timestamp.from(occurredAt);
        jdbc.update("""
                        INSERT INTO un_module_record_flow_state
                            (system_id,tenant_id,record_id,logical_module_id,instance_id,status,version,
                             created_at,created_by,updated_at,updated_by,status_field_code,
                             status_approved_value,status_rejected_value,status_withdrawn_value,
                             status_terminated_value)
                        VALUES (?,?,?,?,?,'PENDING',0,?,?,?,?,?,?,?,?,?)
                        """,
                systemId, tenantId, recordId, logicalModuleId, instanceId,
                timestamp, memberId, timestamp, memberId,
                fieldCode(statusMapping), approved(statusMapping), rejected(statusMapping),
                withdrawn(statusMapping), terminated(statusMapping));
    }

    @Override
    public void insertAdditional(
            long systemId,
            long tenantId,
            long recordId,
            long logicalModuleId,
            long instanceId,
            String eventKey,
            long memberId,
            Instant occurredAt,
            RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
    ) {
        var timestamp = Timestamp.from(occurredAt);
        jdbc.update("""
                        INSERT INTO un_module_record_flow_state_item
                            (system_id,tenant_id,record_id,logical_module_id,instance_id,event_key,
                             status,version,created_at,created_by,updated_at,updated_by,status_field_code,
                             status_approved_value,status_rejected_value,status_withdrawn_value,
                             status_terminated_value)
                        VALUES (?,?,?,?,?,?,'PENDING',0,?,?,?,?,?,?,?,?,?)
                        """,
                systemId, tenantId, recordId, logicalModuleId, instanceId, eventKey,
                timestamp, memberId, timestamp, memberId,
                fieldCode(statusMapping), approved(statusMapping), rejected(statusMapping),
                withdrawn(statusMapping), terminated(statusMapping));
    }

    @Override
    public int replaceTerminal(
            Projection previous,
            long logicalModuleId,
            long instanceId,
            long memberId,
            Instant occurredAt,
            RuntimeRecordFlowFacade.RecordStatusMapping statusMapping
    ) {
        var timestamp = Timestamp.from(occurredAt);
        return jdbc.update("""
                        UPDATE un_module_record_flow_state
                           SET logical_module_id=?,instance_id=?,status='PENDING',version=version+1,
                               updated_at=?,updated_by=?,status_field_code=?,status_approved_value=?,
                               status_rejected_value=?,status_withdrawn_value=?,status_terminated_value=?
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                           AND instance_id=? AND status=? AND version=?
                        """,
                logicalModuleId, instanceId, timestamp, memberId,
                fieldCode(statusMapping), approved(statusMapping), rejected(statusMapping),
                withdrawn(statusMapping), terminated(statusMapping),
                previous.systemId(), previous.tenantId(), previous.recordId(),
                previous.instanceId(), previous.status().name(), previous.version());
    }

    @Override
    public int transition(
            Projection previous,
            RuntimeRecordFlowFacade.FlowStatus status,
            long actorMemberId,
            Instant occurredAt
    ) {
        return jdbc.update("""
                        UPDATE un_module_record_flow_state
                           SET status=?,version=version+1,updated_at=?,updated_by=?
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                           AND instance_id=? AND status='PENDING' AND version=?
                        """,
                status.name(), Timestamp.from(occurredAt), actorMemberId,
                previous.systemId(), previous.tenantId(), previous.recordId(),
                previous.instanceId(), previous.version());
    }

    @Override
    public int transitionAdditional(
            Projection previous,
            RuntimeRecordFlowFacade.FlowStatus status,
            long actorMemberId,
            Instant occurredAt
    ) {
        return jdbc.update("""
                        UPDATE un_module_record_flow_state_item
                           SET status=?,version=version+1,updated_at=?,updated_by=?
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                           AND instance_id=? AND status='PENDING' AND version=?
                        """,
                status.name(), Timestamp.from(occurredAt), actorMemberId,
                previous.systemId(), previous.tenantId(), previous.recordId(),
                previous.instanceId(), previous.version());
    }

    private Optional<Projection> one(String sql, Object... arguments) {
        return jdbc.query(sql, (result, row) -> projection(result), arguments)
                .stream()
                .findFirst();
    }

    private static Projection projection(ResultSet result) throws SQLException {
        return new Projection(
                result.getLong("system_id"),
                result.getLong("tenant_id"),
                result.getLong("record_id"),
                result.getLong("logical_module_id"),
                result.getLong("instance_id"),
                RuntimeRecordFlowFacade.FlowStatus.valueOf(result.getString("status")),
                result.getLong("version"),
                result.getTimestamp("updated_at").toInstant(),
                mapping(result));
    }

    private static RuntimeRecordFlowFacade.RecordStatusMapping mapping(ResultSet result) throws SQLException {
        var fieldCode = result.getString("status_field_code");
        if (fieldCode == null) {
            return null;
        }
        return new RuntimeRecordFlowFacade.RecordStatusMapping(
                fieldCode,
                result.getString("status_approved_value"),
                result.getString("status_rejected_value"),
                result.getString("status_withdrawn_value"),
                result.getString("status_terminated_value"));
    }

    private static String fieldCode(RuntimeRecordFlowFacade.RecordStatusMapping mapping) {
        return mapping == null ? null : mapping.fieldCode();
    }

    private static String approved(RuntimeRecordFlowFacade.RecordStatusMapping mapping) {
        return mapping == null ? null : mapping.approvedValue();
    }

    private static String rejected(RuntimeRecordFlowFacade.RecordStatusMapping mapping) {
        return mapping == null ? null : mapping.rejectedValue();
    }

    private static String withdrawn(RuntimeRecordFlowFacade.RecordStatusMapping mapping) {
        return mapping == null ? null : mapping.withdrawnValue();
    }

    private static String terminated(RuntimeRecordFlowFacade.RecordStatusMapping mapping) {
        return mapping == null ? null : mapping.terminatedValue();
    }
}
