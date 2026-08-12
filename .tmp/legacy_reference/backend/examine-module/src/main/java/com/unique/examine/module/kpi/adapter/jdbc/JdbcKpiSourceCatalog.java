package com.unique.examine.module.kpi.adapter.jdbc;

import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.kpi.port.KpiSourceCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves KPI publication metadata from one immutable data-source version and
 * its exact historical runtime schema. Runtime member permissions are checked
     * separately at calculation time; external projections are anchored to the
     * same immutable historical schema and therefore reuse identical pins.
 */
@Repository("jdbcKpiSourceCatalog")
public class JdbcKpiSourceCatalog implements KpiSourceCatalog {
    private static final Set<String> NUMERIC = Set.of(
            "NUMBER", "PERCENT", "MONEY", "RATING", "PROGRESS");
    private static final Set<String> TEMPORAL = Set.of("DATE", "DATETIME");
    private static final Set<String> DERIVED = Set.of(
            "CALCULATED", "LOOKUP", "AGGREGATE", "AI_FILL");
    static final String FIELD_SQL = """
            SELECT field_row.logical_field_id,field_row.field_code,
                   field_row.field_name,field_row.field_type,
                   field_row.field_scope,field_row.result_schema,
                   reference_target.field_type AS reference_target_type
              FROM un_module_runtime_schema_module module_row
              JOIN un_module_runtime_schema_field field_row
                ON field_row.system_id=module_row.system_id
               AND field_row.schema_version_id=module_row.schema_version_id
               AND field_row.module_snapshot_id=module_row.module_snapshot_id
              LEFT JOIN un_module_runtime_schema_field reference_target
                ON reference_target.system_id=field_row.system_id
               AND reference_target.schema_version_id=field_row.schema_version_id
               AND reference_target.source_field_id=CAST(NULLIF(JSON_UNQUOTE(
                   JSON_EXTRACT(field_row.property_json,'$.targetFieldId')),'')
                   AS UNSIGNED)
               AND reference_target.field_scope='RECORD'
             WHERE module_row.system_id=?
               AND module_row.schema_version_id=?
               AND module_row.logical_module_id=?
               AND module_row.module_code=?
               AND field_row.field_scope='RECORD'
               AND EXISTS (
                   SELECT 1 FROM un_plat_tenant tenant_row
                    WHERE tenant_row.system_id=module_row.system_id
                      AND tenant_row.id=? AND tenant_row.status='ACTIVE'
                      AND tenant_row.deleted_at IS NULL)
             ORDER BY field_row.field_snapshot_id,field_row.id
            """;

    private final DataSourceRepository dataSources;
    private final JdbcTemplate jdbc;

    public JdbcKpiSourceCatalog(
            DataSourceRepository dataSources,
            JdbcTemplate jdbc
    ) {
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<SourceVersion> active(
            long systemId,
            long tenantId,
            long dataSourceId
    ) {
        var root = dataSources.findById(systemId, tenantId, dataSourceId);
        if (root.isEmpty() || root.orElseThrow().activeVersionId() == null) {
            return Optional.empty();
        }
        return dataSources.findVersionById(
                        systemId, tenantId, dataSourceId,
                        root.orElseThrow().activeVersionId())
                .flatMap(this::source);
    }

    @Override
    public Optional<SourceVersion> version(
            long systemId,
            long tenantId,
            long dataSourceId,
            long dataSourceVersionId
    ) {
        return dataSources.findVersionById(
                        systemId, tenantId, dataSourceId, dataSourceVersionId)
                .flatMap(this::source);
    }

    private Optional<SourceVersion> source(DataSourceVersion version) {
        if (version.snapshot().sourceKind()
                == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
            if (version.snapshot().multiModuleJoin() == null) {
                return Optional.empty();
            }
            var fields = version.snapshot().multiModuleJoin().projections()
                    .stream().filter(field -> field.logicalFieldId() > 0)
                    .map(field -> new Field(
                            field.logicalFieldId(), field.fieldCode(),
                            field.fieldName(), field.type(), field.queryType(),
                            true, field.numeric(), field.temporal())).toList();
            if (fields.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new SourceVersion(
                    version.dataSourceId(), version.systemId(),
                    version.tenantId(), version.code(), version.id(),
                    version.versionNumber(), version.moduleCode(),
                    version.schemaVersionId(), fields));
        }
        final long schemaVersionId;
        try {
            schemaVersionId = Long.parseLong(version.schemaVersionId());
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
        if (schemaVersionId <= 0) {
            return Optional.empty();
        }
        var rows = jdbc.query(FIELD_SQL, JdbcKpiSourceCatalog::field,
                version.systemId(), schemaVersionId, version.moduleId(),
                version.moduleCode(), version.tenantId());
        if (rows.isEmpty() || duplicateCodes(rows)) {
            return Optional.empty();
        }
        Map<String, FieldRow> byCode = new LinkedHashMap<>();
        rows.forEach(row -> byCode.put(row.code(), row));
        var selected = new ArrayList<Field>();
        for (var fieldCode : fieldCodes(version.snapshot())) {
            var row = byCode.get(fieldCode);
            if (row == null || !"RECORD".equals(row.scope())) {
                return Optional.empty();
            }
            var capability = capability(row);
            selected.add(new Field(
                    row.logicalFieldId(), row.code(), row.name(), row.type(),
                    capability.queryType(), capability.readable(),
                    capability.readable()
                            && NUMERIC.contains(capability.queryType()),
                    capability.readable()
                            && TEMPORAL.contains(capability.queryType())));
        }
        return Optional.of(new SourceVersion(
                version.dataSourceId(), version.systemId(), version.tenantId(),
                version.code(), version.id(), version.versionNumber(),
                version.moduleCode(), version.schemaVersionId(), selected));
    }

    private static List<String> fieldCodes(DataSourceDraft snapshot) {
        return switch (snapshot.sourceKind()) {
            case NATIVE_MODULE -> snapshot.outputFields().stream()
                    .map(DataSourceDraft.OutputField::fieldCode).toList();
            case HTTP_JSON -> snapshot.httpFieldProjections().stream()
                    .map(DataSourceDraft.HttpJsonFieldProjection::fieldCode)
                    .toList();
            case JDBC_TABLE -> snapshot.jdbcFieldProjections().stream()
                    .map(DataSourceDraft.JdbcTableFieldProjection::fieldCode)
                    .toList();
            case MULTI_MODULE_JOIN -> snapshot.multiModuleJoin().projections()
                    .stream().map(DataSourceDraft.JoinProjection::fieldCode)
                    .toList();
        };
    }

    private static Capability capability(FieldRow field) {
        try {
            return new Capability(queryType(field), true);
        } catch (IllegalArgumentException unsupported) {
            return new Capability(field.type(), false);
        }
    }

    private static String queryType(FieldRow field) {
        if ("REFERENCE".equals(field.type())) {
            return switch (Objects.requireNonNull(
                    field.referenceTargetType(), "reference target type")) {
                case "TEXT" -> "TEXT";
                case "NUMBER", "RATING" -> "NUMBER";
                case "DATE" -> "DATE";
                case "DATETIME" -> "DATETIME";
                case "SWITCH" -> "SWITCH";
                default -> throw new IllegalArgumentException(
                        "Unsupported reference result type");
            };
        }
        if (DERIVED.contains(field.type())) {
            return switch (Objects.requireNonNull(
                    field.resultSchema(), "derived result schema")) {
                case "STRING" -> "TEXT";
                case "DECIMAL", "INTEGER" -> "NUMBER";
                case "DATE" -> "DATE";
                case "DATETIME" -> "DATETIME";
                case "BOOLEAN" -> "SWITCH";
                default -> throw new IllegalArgumentException(
                        "Unsupported derived result schema");
            };
        }
        return switch (field.type()) {
            case "AUTO_NUMBER" -> "TEXT";
            case "TENANT", "CREATED_BY", "UPDATED_BY" -> "MEMBER";
            case "CREATED_AT", "UPDATED_AT" -> "DATETIME";
            default -> field.type();
        };
    }

    private static boolean duplicateCodes(List<FieldRow> rows) {
        return rows.stream().map(FieldRow::code).distinct().count()
                != rows.size()
                || rows.stream().map(FieldRow::logicalFieldId).distinct()
                .count() != rows.size();
    }

    private static FieldRow field(ResultSet result, int rowNumber)
            throws SQLException {
        return new FieldRow(
                result.getLong("logical_field_id"),
                result.getString("field_code"),
                result.getString("field_name"),
                result.getString("field_type"),
                result.getString("field_scope"),
                result.getString("result_schema"),
                result.getString("reference_target_type"));
    }

    record FieldRow(
            long logicalFieldId,
            String code,
            String name,
            String type,
            String scope,
            String resultSchema,
            String referenceTargetType
    ) {
    }

    private record Capability(String queryType, boolean readable) {
    }
}
