package com.unique.examine.module.runtime.flow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class JdbcRecordMemberFieldStore implements RecordMemberFieldStore {
    private final JdbcTemplate jdbc;

    JdbcRecordMemberFieldStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PublishedSchema> activeSchema(long systemId, String moduleCode) {
        var rows = jdbc.query("""
                        SELECT m.system_id,m.schema_version_id,m.module_snapshot_id,
                               m.logical_module_id,m.module_code,v.snapshot_json
                        FROM un_module_config_root r
                        JOIN un_module_config_version v
                          ON v.system_id=r.system_id
                         AND v.id=r.active_version_id
                        JOIN un_module_runtime_schema_module m
                          ON m.system_id=v.system_id
                         AND m.schema_version_id=v.id
                        WHERE r.system_id=? AND m.module_code=?
                        """,
                (result, row) -> new PublishedSchema(
                        result.getLong("system_id"),
                        result.getLong("schema_version_id"),
                        result.getLong("module_snapshot_id"),
                        result.getLong("logical_module_id"),
                        result.getString("module_code"),
                        result.getString("snapshot_json")),
                systemId,
                moduleCode);
        return rows.stream().findFirst();
    }

    @Override
    public List<ProjectedField> projectedFields(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId
    ) {
        return jdbc.query("""
                        SELECT field_snapshot_id,source_field_id,logical_field_id,field_code,
                               field_name,field_type,field_scope,property_json
                        FROM un_module_runtime_schema_field
                        WHERE system_id=? AND schema_version_id=? AND module_snapshot_id=?
                          AND logical_module_id=?
                        ORDER BY field_snapshot_id
                        """,
                (result, row) -> projectedField(result),
                systemId,
                schemaVersionId,
                moduleSnapshotId,
                logicalModuleId);
    }

    @Override
    public Optional<LockedRecord> lockRecord(
            long systemId,
            long tenantId,
            String moduleCode,
            long recordId
    ) {
        var rows = jdbc.query("""
                        SELECT r.system_id,r.tenant_id,r.record_id,r.schema_version_id,
                               r.module_snapshot_id,r.logical_module_id,m.module_code,
                               v.snapshot_json
                        FROM un_module_record r
                        JOIN un_module_runtime_schema_module m
                          ON m.system_id=r.system_id
                         AND m.schema_version_id=r.schema_version_id
                         AND m.module_snapshot_id=r.module_snapshot_id
                         AND m.logical_module_id=r.logical_module_id
                        JOIN un_module_config_version v
                          ON v.system_id=r.system_id
                         AND v.id=r.schema_version_id
                        WHERE r.system_id=? AND r.tenant_id=? AND r.record_id=?
                          AND m.module_code=?
                        FOR UPDATE
                        """,
                (result, row) -> new LockedRecord(
                        result.getLong("system_id"),
                        result.getLong("tenant_id"),
                        result.getLong("record_id"),
                        result.getLong("schema_version_id"),
                        result.getLong("module_snapshot_id"),
                        result.getLong("logical_module_id"),
                        result.getString("module_code"),
                        result.getString("snapshot_json")),
                systemId,
                tenantId,
                recordId,
                moduleCode);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ProjectedField> projectedField(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            long fieldId
    ) {
        var rows = jdbc.query("""
                        SELECT field_snapshot_id,source_field_id,logical_field_id,field_code,
                               field_name,field_type,field_scope,property_json
                        FROM un_module_runtime_schema_field
                        WHERE system_id=? AND schema_version_id=? AND module_snapshot_id=?
                          AND logical_module_id=? AND logical_field_id=?
                        """,
                (result, row) -> projectedField(result),
                systemId,
                schemaVersionId,
                moduleSnapshotId,
                logicalModuleId,
                fieldId);
        return rows.stream().findFirst();
    }

    @Override
    public List<ValueRow> lockValues(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            long fieldId
    ) {
        return jdbc.query("""
                        SELECT ordinal,field_type,field_scope,reference_value
                        FROM un_module_record_value
                        WHERE system_id=? AND tenant_id=? AND record_id=?
                          AND schema_version_id=? AND module_snapshot_id=?
                          AND logical_module_id=? AND field_snapshot_id=?
                        ORDER BY ordinal
                        FOR UPDATE
                        """,
                (result, row) -> new ValueRow(
                        result.getInt("ordinal"),
                        result.getString("field_type"),
                        result.getString("field_scope"),
                        result.getObject("reference_value", Long.class)),
                systemId,
                tenantId,
                recordId,
                schemaVersionId,
                moduleSnapshotId,
                logicalModuleId,
                fieldId);
    }

    private static ProjectedField projectedField(java.sql.ResultSet result)
            throws java.sql.SQLException {
        return new ProjectedField(
                result.getLong("field_snapshot_id"),
                result.getLong("source_field_id"),
                result.getLong("logical_field_id"),
                result.getString("field_code"),
                result.getString("field_name"),
                result.getString("field_type"),
                result.getString("field_scope"),
                result.getString("property_json"));
    }
}
