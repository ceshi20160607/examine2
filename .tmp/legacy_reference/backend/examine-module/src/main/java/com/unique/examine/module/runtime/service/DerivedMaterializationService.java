package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.unique.examine.module.runtime.service.DerivedExpressionEvaluator.ResultSchema;
import static com.unique.examine.module.runtime.service.DerivedExpressionEvaluator.Value;

@Service
public class DerivedMaterializationService {
    private static final Set<String> LOCAL_TYPES = Set.of("FORMULA", "CALCULATED");
    private static final Set<String> CROSS_TYPES = Set.of("SUMMARY", "LOOKUP", "AGGREGATE");
    private static final int EVALUATOR_VERSION = 1;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IdService ids;
    private final DerivedExpressionEvaluator expressions;

    public DerivedMaterializationService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            IdService ids,
            DerivedExpressionEvaluator expressions
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.ids = ids;
        this.expressions = expressions;
    }

    public boolean recomputeLocal(
            long systemId,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            LocalDateTime now
    ) {
        var context = record(systemId, tenantId, schemaVersionId, moduleSnapshotId, recordId, true);
        if (context == null) return false;
        var fields = fields(systemId, schemaVersionId, moduleSnapshotId, LOCAL_TYPES);
        if (fields.isEmpty()) return false;
        var current = new LinkedHashMap<Long, List<StoredValue>>();
        loadValues(systemId, tenantId, schemaVersionId, moduleSnapshotId, recordId)
                .forEach(current::put);
        var changed = false;
        for (var field : fields) {
            var dependencies = dependencySchemas(field);
            var result = expressions.evaluate(field.properties().path("expressionAst"), dependencyId -> {
                var schema = dependencies.get(dependencyId);
                if (schema == null) throw new IllegalStateException("Derived dependency schema is unavailable");
                return first(current.get(dependencyId), schema);
            });
            requireResult(field, result);
            var output = new Output(result, display(result), dependencyVersions(
                    dependencies.keySet(), recordId, context.version()));
            changed |= replace(field, context, List.of(output), now);
            current.put(field.fieldSnapshotId(), List.of(new StoredValue(
                    0, result, output.display(), output.dependencyJson(), "READY", null, EVALUATOR_VERSION)));
        }
        return changed;
    }

    public boolean recomputeCross(
            long systemId,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            LocalDateTime now
    ) {
        var context = record(systemId, tenantId, schemaVersionId, moduleSnapshotId, recordId, true);
        if (context == null) return false;
        var changed = false;
        for (var field : fields(systemId, schemaVersionId, moduleSnapshotId, CROSS_TYPES)) {
            var outputs = switch (field.type()) {
                case "SUMMARY" -> summary(field, context);
                case "LOOKUP" -> lookup(field, context);
                case "AGGREGATE" -> aggregate(field, context);
                default -> throw new IllegalStateException("Unsupported cross-record derived field");
            };
            changed |= replace(field, context, outputs, now);
        }
        return changed;
    }

    public int markPendingForSource(long systemId, long tenantId, long sourceRecordId, LocalDateTime now) {
        var result = 0;
        for (var affected : affectedRecords(systemId, tenantId, sourceRecordId)) {
            var context = record(affected.systemId(), affected.tenantId(), affected.schemaVersionId(),
                    affected.moduleSnapshotId(), affected.recordId(), true);
            if (context == null) continue;
            for (var field : fields(systemId, affected.schemaVersionId(), affected.moduleSnapshotId(), CROSS_TYPES)) {
                var existing = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_record_value WHERE system_id=? "
                                + "AND tenant_id=? AND record_id=? AND schema_version_id=? "
                                + "AND module_snapshot_id=? AND field_snapshot_id=?",
                        Long.class, systemId, tenantId, affected.recordId(), affected.schemaVersionId(),
                        affected.moduleSnapshotId(), field.fieldSnapshotId());
                if (existing == null || existing == 0) {
                    insert(field, context, new Output(Value.nullValue(field.resultSchema()), null, "[]"),
                            0, "PENDING", null, now);
                } else {
                    jdbc.update("UPDATE un_module_record_value SET recalculation_state='PENDING',"
                                    + "failure_correlation_id=NULL,updated_at=?,updated_by=?,version=version+1 "
                                    + "WHERE system_id=? AND tenant_id=? AND record_id=? AND schema_version_id=? "
                                    + "AND module_snapshot_id=? AND field_snapshot_id=?",
                            now, context.actorId(), systemId, tenantId, affected.recordId(),
                            affected.schemaVersionId(), affected.moduleSnapshotId(), field.fieldSnapshotId());
                }
                result++;
            }
        }
        return result;
    }

    public void failForSource(
            long systemId,
            long tenantId,
            long sourceRecordId,
            String correlationId,
            LocalDateTime now
    ) {
        for (var affected : affectedRecords(systemId, tenantId, sourceRecordId)) {
            jdbc.update("UPDATE un_module_record_value v JOIN un_module_runtime_schema_field f "
                            + "ON f.system_id=v.system_id AND f.schema_version_id=v.schema_version_id "
                            + "AND f.module_snapshot_id=v.module_snapshot_id "
                            + "AND f.field_snapshot_id=v.field_snapshot_id SET v.recalculation_state='FAILED',"
                            + "v.failure_correlation_id=?,v.updated_at=?,v.version=v.version+1 WHERE v.system_id=? "
                            + "AND v.tenant_id=? AND v.record_id=? AND v.schema_version_id=? "
                            + "AND v.module_snapshot_id=? AND v.recalculation_state='PENDING' "
                            + "AND f.field_type IN ('SUMMARY','LOOKUP','AGGREGATE')",
                    correlationId, now, systemId, tenantId, affected.recordId(), affected.schemaVersionId(),
                    affected.moduleSnapshotId());
        }
    }

    public RetryState retryFailed(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long fieldSnapshotId,
            LocalDateTime now
    ) {
        var sources = jdbc.query("SELECT t.source_record_id,t.source_record_version FROM "
                        + "un_module_record_value v JOIN un_module_reference_recalc_task t "
                        + "ON t.system_id=v.system_id AND t.tenant_id=v.tenant_id "
                        + "AND t.correlation_id=v.failure_correlation_id JOIN un_module_record source "
                        + "ON source.system_id=t.system_id AND source.tenant_id=t.tenant_id "
                        + "AND source.record_id=t.source_record_id AND source.version=t.source_record_version "
                        + "WHERE v.system_id=? AND v.tenant_id=? AND v.record_id=? AND v.schema_version_id=? "
                        + "AND v.module_snapshot_id=? AND v.field_snapshot_id=? "
                        + "AND v.recalculation_state='FAILED' LIMIT 1 FOR UPDATE",
                (row, number) -> new RetrySource(row.getLong("source_record_id"),
                        row.getLong("source_record_version")),
                systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, fieldSnapshotId);
        if (sources.size() != 1) return null;
        var source = sources.getFirst();
        var correlationId = "derived-retry-" + UUID.randomUUID();
        jdbc.update("UPDATE un_module_record_value SET recalculation_state='PENDING',"
                        + "failure_correlation_id=NULL,updated_at=?,version=version+1 WHERE system_id=? "
                        + "AND tenant_id=? AND record_id=? AND schema_version_id=? AND module_snapshot_id=? "
                        + "AND field_snapshot_id=? AND recalculation_state='FAILED'",
                now, systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, fieldSnapshotId);
        enqueue(systemId, tenantId, source.recordId(), source.version(), correlationId, now);
        return new RetryState(source.recordId(), source.version(), correlationId);
    }

    public void enqueue(
            long systemId,
            long tenantId,
            long sourceRecordId,
            long sourceRecordVersion,
            String correlationId,
            LocalDateTime now
    ) {
        jdbc.update("INSERT INTO un_module_reference_recalc_task "
                        + "(id,system_id,tenant_id,source_record_id,source_record_version,status,attempt_count,"
                        + "available_at,correlation_id,created_at,updated_at,version) "
                        + "VALUES (?,?,?,?,?,'PENDING',0,?,?,?,?,0) ON DUPLICATE KEY UPDATE "
                        + "status='PENDING',attempt_count=0,available_at=VALUES(available_at),"
                        + "correlation_id=VALUES(correlation_id),updated_at=VALUES(updated_at),version=version+1",
                ids.nextId(), systemId, tenantId, sourceRecordId, sourceRecordVersion, now,
                correlationId, now, now);
    }

    private List<Output> summary(DerivedField field, RecordContext context) {
        var reduction = field.properties().path("reduction").asText();
        var sources = relationSources(field, context, !"COUNT".equals(reduction));
        var dependencies = dependencyVersions(sources);
        if ("COUNT".equals(reduction)) {
            return List.of(new Output(new Value(ResultSchema.INTEGER, BigDecimal.valueOf(sources.size())),
                    Integer.toString(sources.size()), dependencies));
        }
        var values = sources.stream().map(RelationSource::value).filter(value -> value.value() != null).toList();
        if (values.isEmpty()) {
            return List.of(new Output(Value.nullValue(field.resultSchema()), null, dependencies));
        }
        Value result;
        if (Set.of("SUM", "AVG").contains(reduction)) {
            var total = values.stream().map(value -> (BigDecimal) value.value())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var decimal = "AVG".equals(reduction)
                    ? total.divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_EVEN) : total;
            result = new Value(ResultSchema.DECIMAL, bounded(decimal));
        } else {
            var comparator = Comparator.comparing(Value::value, this::compare);
            result = "MIN".equals(reduction) ? values.stream().min(comparator).orElseThrow()
                    : values.stream().max(comparator).orElseThrow();
        }
        requireResult(field, result);
        return List.of(new Output(result, display(result), dependencies));
    }

    private List<Output> lookup(DerivedField field, RecordContext context) {
        var sources = relationSources(field, context, true);
        var distinct = field.properties().path("distinct").asBoolean(false);
        var keys = new LinkedHashSet<String>();
        var outputs = new ArrayList<Output>();
        for (var source : sources) {
            if (source.value().value() == null) continue;
            var key = source.value().schema() + "|" + display(source.value());
            if (distinct && !keys.add(key)) continue;
            outputs.add(new Output(source.value(), source.display(), dependencyVersions(List.of(source))));
            requireLookupCardinality(outputs.size());
        }
        if (outputs.isEmpty()) {
            return List.of(new Output(Value.nullValue(field.resultSchema()), null,
                    dependencyVersions(sources)));
        }
        return List.copyOf(outputs);
    }

    static void requireLookupCardinality(int cardinality) {
        if (cardinality > 100) {
            throw new IllegalStateException("LOOKUP result exceeds cardinality 100");
        }
    }

    private List<Output> aggregate(DerivedField field, RecordContext context) {
        var subtableId = Long.parseLong(field.properties().path("subtableFieldId").asText());
        var aggregateId = field.properties().path("aggregateId").asText();
        var declarations = jdbc.query("SELECT property_json FROM un_module_runtime_schema_field WHERE system_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id=? "
                        + "AND field_scope='RECORD' AND field_type='SUBTABLE'",
                (row, number) -> parse(row.getString("property_json")), context.systemId(),
                context.schemaVersionId(), context.moduleSnapshotId(), subtableId);
        if (declarations.size() != 1) throw new IllegalStateException("AGGREGATE subtable is unavailable");
        JsonNode declaration = null;
        for (var candidate : declarations.getFirst().path("aggregates")) {
            if (aggregateId.equals(candidate.path("id").asText())) {
                declaration = candidate;
                break;
            }
        }
        if (declaration == null) throw new IllegalStateException("AGGREGATE declaration is unavailable");
        var function = declaration.path("function").asText();
        Long columnId = null;
        if (!"COUNT".equals(function)) {
            var logicalColumnId = Long.parseLong(declaration.path("columnFieldId").asText());
            var columns = jdbc.query("SELECT field_snapshot_id FROM un_module_runtime_schema_field "
                            + "WHERE system_id=? AND schema_version_id=? AND module_snapshot_id=? "
                            + "AND parent_field_snapshot_id=? AND field_scope='SUBTABLE_COLUMN' "
                            + "AND source_field_id=?",
                    (row, number) -> row.getLong("field_snapshot_id"), context.systemId(),
                    context.schemaVersionId(), context.moduleSnapshotId(), subtableId, logicalColumnId);
            if (columns.size() != 1) {
                throw new IllegalStateException("AGGREGATE column is unavailable");
            }
            columnId = columns.getFirst();
        }
        var rows = jdbc.query("SELECT sr.row_id,sr.version AS row_version,sv.decimal_value "
                        + "FROM un_module_sub_record sr LEFT JOIN un_module_sub_value sv "
                        + "ON sv.system_id=sr.system_id AND sv.tenant_id=sr.tenant_id "
                        + "AND sv.parent_record_id=sr.parent_record_id AND sv.schema_version_id=sr.schema_version_id "
                        + "AND sv.module_snapshot_id=sr.module_snapshot_id "
                        + "AND sv.parent_field_snapshot_id=sr.parent_field_snapshot_id AND sv.row_id=sr.row_id "
                        + "AND (? IS NULL OR sv.column_field_snapshot_id=?) WHERE sr.system_id=? AND sr.tenant_id=? "
                        + "AND sr.parent_record_id=? AND sr.schema_version_id=? AND sr.module_snapshot_id=? "
                        + "AND sr.parent_field_snapshot_id=? AND sr.status='ACTIVE' ORDER BY sr.ordinal,sv.ordinal",
                (row, number) -> new AggregateRow(row.getLong("row_id"), row.getLong("row_version"),
                        row.getBigDecimal("decimal_value")), columnId, columnId, context.systemId(),
                context.tenantId(), context.recordId(), context.schemaVersionId(), context.moduleSnapshotId(),
                subtableId);
        var dependencies = writeJson(rows.stream().map(row -> Map.of(
                "sourceRowId", Long.toString(row.rowId()), "version", row.version(),
                "fieldId", Long.toString(subtableId))).toList());
        BigDecimal value;
        if ("COUNT".equals(function)) {
            value = BigDecimal.valueOf(rows.stream().map(AggregateRow::rowId).distinct().count());
        } else {
            var values = rows.stream().map(AggregateRow::value).filter(Objects::nonNull).toList();
            value = switch (function) {
                case "SUM" -> values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                case "AVG" -> values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_EVEN);
                case "MIN" -> values.stream().min(BigDecimal::compareTo).orElse(null);
                case "MAX" -> values.stream().max(BigDecimal::compareTo).orElse(null);
                default -> throw new IllegalStateException("AGGREGATE function is unsupported");
            };
        }
        var result = value == null ? Value.nullValue(field.resultSchema())
                : new Value(field.resultSchema(), bounded(value));
        return List.of(new Output(result, display(result), dependencies));
    }

    private List<RelationSource> relationSources(
            DerivedField field,
            RecordContext context,
            boolean includeValue
    ) {
        var relationId = Long.parseLong(field.properties().path("relationFieldId").asText());
        var targetFieldId = includeValue
                ? Long.parseLong(field.properties().path("targetFieldId").asText()) : 0L;
        return jdbc.query("SELECT rr.target_record_id,r.version AS target_version,f.field_type,f.result_schema,"
                        + "v.ordinal,v.string_value,v.decimal_value,v.date_value,v.datetime_value,v.boolean_value,"
                        + "v.display_value FROM un_module_record_relation rr JOIN un_module_record r "
                        + "ON r.system_id=rr.system_id AND r.tenant_id=rr.tenant_id "
                        + "AND r.record_id=rr.target_record_id AND r.schema_version_id=rr.target_schema_version_id "
                        + "AND r.module_snapshot_id=rr.target_module_snapshot_id "
                        + "LEFT JOIN un_module_runtime_schema_field f ON ?=TRUE AND f.system_id=rr.system_id "
                        + "AND f.schema_version_id=rr.target_schema_version_id "
                        + "AND f.module_snapshot_id=rr.target_module_snapshot_id "
                        + "AND f.source_field_id=? AND f.field_scope='RECORD' LEFT JOIN un_module_record_value v "
                        + "ON v.system_id=f.system_id AND v.tenant_id=rr.tenant_id "
                        + "AND v.record_id=rr.target_record_id AND v.schema_version_id=f.schema_version_id "
                        + "AND v.module_snapshot_id=f.module_snapshot_id AND v.field_snapshot_id=f.field_snapshot_id "
                        + "WHERE rr.system_id=? AND rr.tenant_id=? AND rr.source_record_id=? "
                        + "AND rr.source_schema_version_id=? AND rr.source_module_snapshot_id=? "
                        + "AND rr.source_logical_field_id=? AND r.status IN ('ACTIVE','ARCHIVED') "
                        + "ORDER BY rr.ordinal,v.ordinal",
                (row, number) -> relationSource(row, field.resultSchema(), includeValue), includeValue,
                targetFieldId, context.systemId(), context.tenantId(), context.recordId(),
                context.schemaVersionId(), context.moduleSnapshotId(), relationId);
    }

    private RelationSource relationSource(ResultSet row, ResultSchema schema, boolean includeValue)
            throws SQLException {
        var value = includeValue ? readValue(row, schema) : Value.nullValue(schema);
        return new RelationSource(row.getLong("target_record_id"), row.getLong("target_version"), value,
                row.getString("display_value"));
    }

    private boolean replace(
            DerivedField field,
            RecordContext context,
            List<Output> outputs,
            LocalDateTime now
    ) {
        var prior = jdbc.query("SELECT ordinal,string_value,decimal_value,date_value,datetime_value,boolean_value,"
                        + "display_value,recalculation_state,failure_correlation_id,evaluator_version,"
                        + "dependency_version_json FROM un_module_record_value WHERE system_id=? AND tenant_id=? "
                        + "AND record_id=? AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id=? "
                        + "ORDER BY ordinal",
                (row, number) -> storedValue(row, field.resultSchema()), context.systemId(), context.tenantId(),
                context.recordId(), context.schemaVersionId(), context.moduleSnapshotId(), field.fieldSnapshotId());
        var changed = !same(prior, outputs);
        jdbc.update("DELETE FROM un_module_record_index WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id=?",
                context.systemId(), context.tenantId(), context.recordId(), context.schemaVersionId(),
                context.moduleSnapshotId(), field.logicalFieldId());
        jdbc.update("DELETE FROM un_module_record_value WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id=?",
                context.systemId(), context.tenantId(), context.recordId(), context.schemaVersionId(),
                context.moduleSnapshotId(), field.fieldSnapshotId());
        for (var ordinal = 0; ordinal < outputs.size(); ordinal++) {
            var output = outputs.get(ordinal);
            insert(field, context, output, ordinal, "READY", null, now);
            if (output.value().value() != null) insertIndex(field, context, output, ordinal, now);
        }
        return changed;
    }

    private void insert(
            DerivedField field,
            RecordContext context,
            Output output,
            int ordinal,
            String state,
            String failureCorrelationId,
            LocalDateTime now
    ) {
        var value = output.value();
        jdbc.update("INSERT INTO un_module_record_value "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "field_snapshot_id,logical_field_id,field_version,field_type,field_scope,result_schema,"
                        + "dependency_version_json,evaluator_version,recalculation_state,failure_correlation_id,"
                        + "ordinal,string_value,text_value,decimal_value,date_value,datetime_value,time_value,"
                        + "boolean_value,currency_code,reference_value,encrypted_value,encryption_key_version,"
                        + "value_hash,hash_key_version,display_value,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,'RECORD',?,CAST(? AS JSON),?,?,?,?,?,NULL,?,?,?,NULL,?,NULL,"
                        + "NULL,NULL,NULL,NULL,NULL,?,?,?, ?,?,0)",
                ids.nextId(), context.systemId(), context.tenantId(), context.recordId(), context.schemaVersionId(),
                context.moduleSnapshotId(), context.moduleSnapshotId(), field.fieldSnapshotId(),
                field.logicalFieldId(), Math.max(1L, context.version()), field.type(), field.resultSchema().name(),
                output.dependencyJson(), field.evaluatorVersion(), state, failureCorrelationId, ordinal,
                value.schema() == ResultSchema.STRING ? value.value() : null,
                value.schema().numeric() ? value.value() : null,
                value.schema() == ResultSchema.DATE ? value.value() : null,
                value.schema() == ResultSchema.DATETIME ? value.value() : null,
                value.schema() == ResultSchema.BOOLEAN ? value.value() : null,
                output.display(), now, context.actorId(), now, context.actorId());
    }

    private void insertIndex(
            DerivedField field,
            RecordContext context,
            Output output,
            int ordinal,
            LocalDateTime now
    ) {
        var value = output.value();
        var kind = switch (value.schema()) {
            case STRING -> "STRING";
            case DECIMAL, INTEGER -> "DECIMAL";
            case DATE -> "DATE";
            case DATETIME -> "DATETIME";
            case BOOLEAN -> "BOOLEAN";
        };
        jdbc.update("INSERT INTO un_module_record_index "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,"
                        + "record_status,value_kind,string_value,decimal_value,date_value,datetime_value,time_value,"
                        + "boolean_value,reference_value,hash_value,currency_code,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,1,1,0,?,?,?,?,?,?,?,NULL,?,NULL,NULL,NULL,?,?)",
                ids.nextId(), context.systemId(), context.tenantId(), context.recordId(), context.schemaVersionId(),
                context.moduleSnapshotId(), context.moduleSnapshotId(), field.logicalFieldId(), ordinal,
                context.status(), kind, value.schema() == ResultSchema.STRING ? value.value() : null,
                value.schema().numeric() ? value.value() : null,
                value.schema() == ResultSchema.DATE ? value.value() : null,
                value.schema() == ResultSchema.DATETIME ? value.value() : null,
                value.schema() == ResultSchema.BOOLEAN ? value.value() : null, now, now);
    }

    private List<DerivedField> fields(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            Set<String> types
    ) {
        return jdbc.query("SELECT field_snapshot_id,logical_field_id,field_type,result_schema,evaluator_version,"
                        + "property_json,dependency_json,topological_rank FROM un_module_runtime_schema_field "
                        + "WHERE system_id=? AND schema_version_id=? AND module_snapshot_id=? "
                        + "AND field_scope='RECORD' AND field_type IN (" + placeholders(types.size()) + ") "
                        + "ORDER BY topological_rank,field_snapshot_id",
                (row, number) -> new DerivedField(row.getLong("field_snapshot_id"),
                        row.getLong("logical_field_id"), row.getString("field_type"),
                        ResultSchema.valueOf(row.getString("result_schema")), row.getInt("evaluator_version"),
                        parse(row.getString("property_json")), parse(row.getString("dependency_json")),
                        row.getInt("topological_rank")), arguments(systemId, schemaVersionId, moduleSnapshotId, types));
    }

    private Map<Long, List<StoredValue>> loadValues(
            long systemId,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId
    ) {
        var result = new LinkedHashMap<Long, List<StoredValue>>();
        jdbc.query("SELECT f.field_snapshot_id,f.result_schema,v.ordinal,v.string_value,v.decimal_value,"
                        + "v.date_value,v.datetime_value,v.boolean_value,v.display_value,v.dependency_version_json,"
                        + "v.recalculation_state,v.failure_correlation_id,v.evaluator_version "
                        + "FROM un_module_runtime_schema_field f LEFT JOIN un_module_record_value v "
                        + "ON v.system_id=f.system_id AND v.tenant_id=? AND v.record_id=? "
                        + "AND v.schema_version_id=f.schema_version_id AND v.module_snapshot_id=f.module_snapshot_id "
                        + "AND v.field_snapshot_id=f.field_snapshot_id WHERE f.system_id=? AND f.schema_version_id=? "
                        + "AND f.module_snapshot_id=? AND f.field_scope='RECORD' ORDER BY f.field_snapshot_id,v.ordinal",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                    var schema = resultSchema(row);
                    result.computeIfAbsent(row.getLong("field_snapshot_id"), ignored -> new ArrayList<>())
                            .add(storedValue(row, schema));
                }, tenantId, recordId, systemId, schemaVersionId, moduleSnapshotId);
        return result;
    }

    private StoredValue storedValue(ResultSet row, ResultSchema schema) throws SQLException {
        return new StoredValue(row.getInt("ordinal"), readValue(row, schema), row.getString("display_value"),
                row.getString("dependency_version_json"), row.getString("recalculation_state"),
                row.getString("failure_correlation_id"), row.getInt("evaluator_version"));
    }

    private static Value readValue(ResultSet row, ResultSchema schema) throws SQLException {
        Object value = switch (schema) {
            case STRING -> row.getString("string_value");
            case DECIMAL, INTEGER -> row.getBigDecimal("decimal_value");
            case DATE -> row.getDate("date_value") == null ? null : row.getDate("date_value").toLocalDate();
            case DATETIME -> row.getTimestamp("datetime_value") == null ? null
                    : row.getTimestamp("datetime_value").toLocalDateTime();
            case BOOLEAN -> row.getObject("boolean_value", Boolean.class);
        };
        return new Value(schema, value);
    }

    private static ResultSchema resultSchema(ResultSet row) throws SQLException {
        var declared = row.getString("result_schema");
        if (declared != null) return ResultSchema.valueOf(declared);
        if (row.getString("string_value") != null) return ResultSchema.STRING;
        if (row.getBigDecimal("decimal_value") != null) return ResultSchema.DECIMAL;
        if (row.getDate("date_value") != null) return ResultSchema.DATE;
        if (row.getTimestamp("datetime_value") != null) return ResultSchema.DATETIME;
        if (row.getObject("boolean_value") != null) return ResultSchema.BOOLEAN;
        return ResultSchema.STRING;
    }

    private RecordContext record(
            long systemId,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            boolean lock
    ) {
        var rows = jdbc.query("SELECT updated_by,status,version FROM un_module_record WHERE system_id=? "
                        + "AND tenant_id=? AND record_id=? AND schema_version_id=? AND module_snapshot_id=?"
                        + (lock ? " FOR UPDATE" : ""),
                (row, number) -> new RecordContext(systemId, tenantId, recordId, schemaVersionId,
                        moduleSnapshotId, row.getLong("updated_by"), row.getString("status"),
                        row.getLong("version")), systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId);
        return rows.size() == 1 ? rows.getFirst() : null;
    }

    private List<AffectedRecord> affectedRecords(long systemId, long tenantId, long sourceRecordId) {
        var records = new LinkedHashMap<Long, AffectedRecord>();
        jdbc.query("SELECT record_id,schema_version_id,module_snapshot_id FROM un_module_record "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> records.put(row.getLong("record_id"),
                        new AffectedRecord(systemId, tenantId, row.getLong("record_id"),
                                row.getLong("schema_version_id"), row.getLong("module_snapshot_id"))),
                systemId, tenantId, sourceRecordId);
        jdbc.query("SELECT DISTINCT r.record_id,r.schema_version_id,r.module_snapshot_id "
                        + "FROM un_module_record_relation rr JOIN un_module_record r "
                        + "ON r.system_id=rr.system_id AND r.tenant_id=rr.tenant_id "
                        + "AND r.record_id=rr.source_record_id WHERE rr.system_id=? AND rr.tenant_id=? "
                        + "AND rr.target_record_id=? ORDER BY r.record_id",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> records.put(row.getLong("record_id"),
                        new AffectedRecord(systemId, tenantId, row.getLong("record_id"),
                                row.getLong("schema_version_id"), row.getLong("module_snapshot_id"))),
                systemId, tenantId, sourceRecordId);
        return List.copyOf(records.values());
    }

    private Map<Long, ResultSchema> dependencySchemas(DerivedField field) {
        var result = new LinkedHashMap<Long, ResultSchema>();
        field.dependencies().forEach(node -> {
            if (node.path("fieldId").isTextual() && node.path("resultSchema").isTextual()) {
                result.put(Long.parseLong(node.path("fieldId").asText()),
                        ResultSchema.valueOf(node.path("resultSchema").asText()));
            }
        });
        return result;
    }

    private String dependencyVersions(Set<Long> fields, long recordId, long version) {
        return writeJson(fields.stream().map(fieldId -> Map.of(
                "fieldId", Long.toString(fieldId), "sourceRecordId", Long.toString(recordId),
                "version", version)).toList());
    }

    private String dependencyVersions(List<RelationSource> sources) {
        return writeJson(sources.stream().map(source -> Map.of(
                "sourceRecordId", Long.toString(source.recordId()), "version", source.version())).toList());
    }

    private Value first(List<StoredValue> values, ResultSchema expected) {
        if (values == null || values.isEmpty()) return Value.nullValue(expected);
        var value = values.getFirst().value();
        if (value.value() == null) return Value.nullValue(expected);
        if (value.schema() != expected && !(value.schema().numeric() && expected.numeric())) {
            throw new IllegalStateException("Stored dependency type does not match its snapshot");
        }
        return expected == value.schema() ? value : new Value(expected, value.value());
    }

    private boolean same(List<StoredValue> prior, List<Output> outputs) {
        if (prior.size() != outputs.size()) return false;
        for (var index = 0; index < outputs.size(); index++) {
            var left = prior.get(index);
            var right = outputs.get(index);
            if (!"READY".equals(left.state()) || left.evaluatorVersion() != EVALUATOR_VERSION
                    || !Objects.equals(left.value().value(), right.value().value())
                    || !Objects.equals(left.display(), right.display())) return false;
        }
        return true;
    }

    private void requireResult(DerivedField field, Value result) {
        if (field.resultSchema() != result.schema()) {
            throw new IllegalStateException("Evaluator result does not match the published result schema");
        }
    }

    private int compare(Object left, Object right) {
        if (left instanceof BigDecimal decimal) return decimal.compareTo((BigDecimal) right);
        if (left instanceof String string) return string.compareTo((String) right);
        if (left instanceof LocalDate date) return date.compareTo((LocalDate) right);
        if (left instanceof LocalDateTime dateTime) return dateTime.compareTo((LocalDateTime) right);
        throw new IllegalStateException("SUMMARY values are not comparable");
    }

    private static BigDecimal bounded(BigDecimal value) {
        var result = value.scale() > 10 ? value.setScale(10, RoundingMode.HALF_EVEN) : value;
        if (result.precision() > 38) throw new IllegalStateException("Derived decimal exceeds precision 38");
        return result.signum() == 0 ? BigDecimal.ZERO : result.stripTrailingZeros();
    }

    private static String display(Value value) {
        if (value.value() == null) return null;
        return switch (value.schema()) {
            case STRING -> (String) value.value();
            case DECIMAL -> ((BigDecimal) value.value()).toPlainString();
            case INTEGER -> ((BigDecimal) value.value()).toBigIntegerExact().toString();
            case DATE -> ((LocalDate) value.value()).format(DateTimeFormatter.ISO_LOCAL_DATE);
            case DATETIME -> ((LocalDateTime) value.value()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case BOOLEAN -> value.value().toString();
        };
    }

    private JsonNode parse(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Published derived JSON is invalid", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize derived dependency versions", exception);
        }
    }

    private static Object[] arguments(long systemId, long schemaVersionId, long moduleSnapshotId,
                                      Set<String> types) {
        var result = new ArrayList<Object>();
        result.add(systemId);
        result.add(schemaVersionId);
        result.add(moduleSnapshotId);
        result.addAll(types.stream().sorted().toList());
        return result.toArray();
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private record DerivedField(
            long fieldSnapshotId,
            long logicalFieldId,
            String type,
            ResultSchema resultSchema,
            int evaluatorVersion,
            JsonNode properties,
            JsonNode dependencies,
            int topologicalRank
    ) { }

    private record RecordContext(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long actorId,
            String status,
            long version
    ) { }

    private record StoredValue(
            int ordinal,
            Value value,
            String display,
            String dependencyJson,
            String state,
            String failureCorrelationId,
            int evaluatorVersion
    ) { }

    private record Output(Value value, String display, String dependencyJson) { }

    private record RelationSource(long recordId, long version, Value value, String display) { }

    private record AggregateRow(long rowId, long version, BigDecimal value) { }

    private record AffectedRecord(long systemId, long tenantId, long recordId, long schemaVersionId,
                                  long moduleSnapshotId) { }

    private record RetrySource(long recordId, long version) { }

    public record RetryState(long sourceRecordId, long sourceRecordVersion, String correlationId) { }
}
