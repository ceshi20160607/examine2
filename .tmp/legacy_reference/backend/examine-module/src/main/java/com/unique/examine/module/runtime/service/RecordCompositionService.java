package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.api.ApiError;
import com.unique.examine.core.api.RuntimeReferenceFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.security.SensitiveCryptoService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecordCompositionService {
    private static final Set<String> OPTION_TYPES = Set.of("RADIO", "MULTI_SELECT", "CASCADE", "STATUS");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IdService ids;
    private final RuntimeReferenceFacade references;
    private final CanonicalFieldValueCodec codec;
    private final SensitiveCryptoService sensitiveCrypto;

    public RecordCompositionService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            IdService ids,
            RuntimeReferenceFacade references,
            CanonicalFieldValueCodec codec,
            SensitiveCryptoService sensitiveCrypto
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.ids = ids;
        this.references = references;
        this.codec = codec;
        this.sensitiveCrypto = sensitiveCrypto;
    }

    public void replace(
            RuntimeSession session,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            List<RecordRuntimeViews.RelationInput> relationInputs,
            List<RecordRuntimeViews.SubtableInput> subtableInputs,
            LocalDateTime now
    ) {
        var fields = parentFields(session.systemId(), schemaVersionId, moduleSnapshotId);
        var relationsByCode = uniqueRelations(relationInputs);
        var subtablesByCode = uniqueSubtables(subtableInputs);
        rejectUnknown(relationsByCode.keySet(), fields, "RELATION", "relations");
        rejectUnknown(subtablesByCode.keySet(), fields, "SUBTABLE", "subtables");

        for (var field : fields.values()) {
            if ("RELATION".equals(field.type())) {
                replaceRelation(session, tenantId, schemaVersionId, moduleSnapshotId, recordId, field,
                        relationsByCode.getOrDefault(field.code(), new RecordRuntimeViews.RelationInput(field.code(), List.of())), now);
            } else if ("SUBTABLE".equals(field.type())) {
                replaceSubtable(session, tenantId, schemaVersionId, moduleSnapshotId, recordId, field,
                        subtablesByCode.getOrDefault(field.code(), new RecordRuntimeViews.SubtableInput(field.code(), List.of())), now);
            }
        }
    }

    public PageSlice<RecordRuntimeViews.RelationItem> relationPage(
            RuntimeSession session,
            long tenantId,
            long recordId,
            long fieldSnapshotId,
            long targetModuleId,
            String targetScopeSql,
            List<Object> targetScopeArguments,
            int page,
            int size
    ) {
        var fixed = new ArrayList<Object>();
        fixed.add(session.systemId());
        fixed.add(tenantId);
        fixed.add(recordId);
        fixed.add(fieldSnapshotId);
        fixed.add(targetModuleId);
        fixed.addAll(targetScopeArguments);
        var where = "rr.system_id=? AND rr.tenant_id=? AND rr.source_record_id=? "
                + "AND rr.source_field_snapshot_id=? AND r.logical_module_id=? "
                + "AND r.status IN ('ACTIVE','ARCHIVED') AND " + targetScopeSql;
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_record_relation rr "
                        + "JOIN un_module_record r ON r.system_id=rr.system_id AND r.tenant_id=rr.tenant_id "
                        + "AND r.record_id=rr.target_record_id AND r.schema_version_id=rr.target_schema_version_id "
                        + "AND r.module_snapshot_id=rr.target_module_snapshot_id WHERE " + where,
                Long.class, fixed.toArray());
        var arguments = new ArrayList<>(fixed);
        arguments.add(size);
        arguments.add(Math.multiplyExact(page - 1, size));
        var items = jdbc.query("SELECT rr.target_record_id,r.version,rr.ordinal,r.title "
                        + "FROM un_module_record_relation rr JOIN un_module_record r "
                        + "ON r.system_id=rr.system_id AND r.tenant_id=rr.tenant_id "
                        + "AND r.record_id=rr.target_record_id AND r.schema_version_id=rr.target_schema_version_id "
                        + "AND r.module_snapshot_id=rr.target_module_snapshot_id WHERE " + where
                        + " ORDER BY rr.ordinal,rr.target_record_id LIMIT ? OFFSET ?",
                (row, number) -> new RecordRuntimeViews.RelationItem(
                        Long.toString(row.getLong("target_record_id")), row.getLong("version"),
                        row.getInt("ordinal"), row.getString("title")), arguments.toArray());
        return new PageSlice<>(items, total == null ? 0 : total);
    }

    public PageSlice<RecordRuntimeViews.SubRowResponse> subtablePage(
            RuntimeSession session,
            long tenantId,
            long recordId,
            long fieldSnapshotId,
            int page,
            int size
    ) {
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_sub_record WHERE system_id=? "
                        + "AND tenant_id=? AND parent_record_id=? AND parent_field_snapshot_id=? AND status='ACTIVE'",
                Long.class, session.systemId(), tenantId, recordId, fieldSnapshotId);
        var rows = jdbc.query("SELECT row_id,version,ordinal FROM un_module_sub_record WHERE system_id=? "
                        + "AND tenant_id=? AND parent_record_id=? AND parent_field_snapshot_id=? AND status='ACTIVE' "
                        + "ORDER BY ordinal,row_id LIMIT ? OFFSET ?",
                (row, number) -> new SubRow(row.getLong("row_id"), row.getLong("version"), row.getInt("ordinal")),
                session.systemId(), tenantId, recordId, fieldSnapshotId, size,
                Math.multiplyExact(page - 1, size));
        if (rows.isEmpty()) {
            return new PageSlice<>(List.of(), total == null ? 0 : total);
        }
        var values = new LinkedHashMap<Long, List<RecordRuntimeViews.FieldValue>>();
        rows.forEach(row -> values.put(row.rowId(), new ArrayList<>()));
        var rowIds = rows.stream().map(SubRow::rowId).toList();
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(tenantId);
        arguments.add(recordId);
        arguments.add(fieldSnapshotId);
        arguments.addAll(rowIds);
        jdbc.query("SELECT sv.row_id,sv.column_field_type,sv.string_value,sv.text_value,sv.decimal_value,"
                        + "sv.date_value,sv.datetime_value,sv.time_value,sv.boolean_value,sv.currency_code,"
                        + "sv.reference_value,sv.display_value,f.field_code,f.field_name,f.property_json "
                        + "FROM un_module_sub_value sv JOIN un_module_runtime_schema_field f "
                        + "ON f.system_id=sv.system_id AND f.schema_version_id=sv.schema_version_id "
                        + "AND f.module_snapshot_id=sv.module_snapshot_id "
                        + "AND f.field_snapshot_id=sv.column_field_snapshot_id WHERE sv.system_id=? "
                        + "AND sv.tenant_id=? AND sv.parent_record_id=? AND sv.parent_field_snapshot_id=? "
                        + "AND sv.row_id IN (" + placeholders(rowIds.size()) + ") "
                        + "ORDER BY sv.row_id,f.id,sv.ordinal",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> values.get(row.getLong("row_id")).add(
                        new RecordRuntimeViews.FieldValue(row.getString("field_code"), row.getString("field_name"),
                                row.getString("column_field_type"), readSubValue(row), row.getString("display_value"))),
                arguments.toArray());
        var items = rows.stream().map(row -> new RecordRuntimeViews.SubRowResponse(
                Long.toString(row.rowId()), row.version(), row.ordinal(), values.get(row.rowId()))).toList();
        return new PageSlice<>(items, total == null ? 0 : total);
    }

    public List<RecordRuntimeViews.SubRowInput> subtableState(
            RuntimeSession session, long tenantId, long recordId, long fieldSnapshotId
    ) {
        return subtablePage(session, tenantId, recordId, fieldSnapshotId, 1, 200).items().stream()
                .map(row -> {
                    var values = new LinkedHashMap<String, JsonNode>();
                    for (var value : row.values()) {
                        var node = objectMapper.valueToTree(value.value());
                        var existing = values.get(value.fieldCode());
                        if (existing == null) {
                            values.put(value.fieldCode(), node);
                        } else if (existing.isArray()) {
                            ((com.fasterxml.jackson.databind.node.ArrayNode) existing).add(node);
                        } else {
                            var array = objectMapper.createArrayNode();
                            array.add(existing);
                            array.add(node);
                            values.put(value.fieldCode(), array);
                        }
                    }
                    return new RecordRuntimeViews.SubRowInput(null, row.rowId(), row.version(), row.ordinal(), values);
                }).toList();
    }

    public List<RecordRuntimeViews.RelationTargetInput> relationState(
            long systemId, long tenantId, long recordId, long fieldSnapshotId
    ) {
        return jdbc.query("SELECT rr.target_record_id,r.version,rr.ordinal FROM un_module_record_relation rr "
                        + "JOIN un_module_record r ON r.system_id=rr.system_id AND r.tenant_id=rr.tenant_id "
                        + "AND r.record_id=rr.target_record_id AND r.schema_version_id=rr.target_schema_version_id "
                        + "AND r.module_snapshot_id=rr.target_module_snapshot_id WHERE rr.system_id=? "
                        + "AND rr.tenant_id=? AND rr.source_record_id=? AND rr.source_field_snapshot_id=? "
                        + "ORDER BY rr.ordinal,rr.target_record_id",
                (row, number) -> new RecordRuntimeViews.RelationTargetInput(
                        Long.toString(row.getLong("target_record_id")), row.getLong("version"), row.getInt("ordinal")),
                systemId, tenantId, recordId, fieldSnapshotId);
    }

    public void replaceRelation(
            RuntimeSession session,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            String fieldCode,
            List<RecordRuntimeViews.RelationTargetInput> targets,
            LocalDateTime now
    ) {
        var field = requireParentField(session.systemId(), schemaVersionId, moduleSnapshotId, fieldCode, "RELATION");
        replaceRelation(session, tenantId, schemaVersionId, moduleSnapshotId, recordId, field,
                new RecordRuntimeViews.RelationInput(fieldCode, targets), now);
    }

    public void replaceSubtable(
            RuntimeSession session,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            String fieldCode,
            List<RecordRuntimeViews.SubRowInput> rows,
            LocalDateTime now
    ) {
        var field = requireParentField(session.systemId(), schemaVersionId, moduleSnapshotId, fieldCode, "SUBTABLE");
        replaceSubtable(session, tenantId, schemaVersionId, moduleSnapshotId, recordId, field,
                new RecordRuntimeViews.SubtableInput(fieldCode, rows), now);
    }

    private void replaceRelation(
            RuntimeSession session,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            SchemaField field,
            RecordRuntimeViews.RelationInput input,
            LocalDateTime now
    ) {
        var multiple = field.properties().path("multiple").asBoolean(false);
        var minimum = field.required() ? 1 : 0;
        var maximum = multiple ? 500 : 1;
        if (input.targets().size() < minimum || input.targets().size() > maximum) {
            throw relationInvalid(field, "requires " + minimum + ".." + maximum + " targets");
        }
        var targetIds = new HashSet<Long>();
        var ordinals = new HashSet<Integer>();
        var targets = new ArrayList<TargetRecord>();
        for (var target : input.targets()) {
            var targetId = positiveId(target.targetRecordId(), "relations." + field.code());
            if (!targetIds.add(targetId) || target.ordinal() < 0 || target.ordinal() >= maximum
                    || !ordinals.add(target.ordinal())) {
                throw relationInvalid(field, "contains a duplicate target or ordinal");
            }
            var rows = jdbc.query("SELECT schema_version_id,module_snapshot_id,logical_module_id,version,status "
                            + "FROM un_module_record WHERE system_id=? AND tenant_id=? AND record_id=? "
                            + "AND logical_module_id=? AND status='ACTIVE'",
                    (result, row) -> new TargetRecord(result.getLong("schema_version_id"),
                            result.getLong("module_snapshot_id"), result.getLong("logical_module_id"),
                            result.getLong("version")),
                    session.systemId(), tenantId, targetId, field.targetModuleId());
            if (rows.size() != 1 || rows.getFirst().version() != target.targetExpectedVersion()) {
                throw relationInvalid(field, "contains an unavailable or stale target");
            }
            targets.add(rows.getFirst().withId(targetId, target.ordinal()));
        }
        requireDenseOrdinals(ordinals, targets.size(), "relations." + field.code());

        jdbc.update("DELETE FROM un_module_record_relation WHERE system_id=? AND tenant_id=? "
                        + "AND source_record_id=? AND source_field_snapshot_id=?",
                session.systemId(), tenantId, recordId, field.fieldSnapshotId());
        for (var target : targets) {
            jdbc.update("INSERT INTO un_module_record_relation "
                            + "(id,system_id,tenant_id,source_record_id,source_schema_version_id,"
                            + "source_module_snapshot_id,source_logical_module_id,source_field_snapshot_id,"
                            + "source_logical_field_id,source_field_type,source_field_scope,target_record_id,"
                            + "target_schema_version_id,target_module_snapshot_id,target_logical_module_id,ordinal,"
                            + "created_at,created_by,updated_at,updated_by,version) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,'RELATION','RECORD',?,?,?,?,?,?,?,?,?,0)",
                    ids.nextId(), session.systemId(), tenantId, recordId, schemaVersionId, moduleSnapshotId,
                    moduleSnapshotId, field.fieldSnapshotId(), field.logicalFieldId(), target.id(),
                    target.schemaVersionId(), target.moduleSnapshotId(), target.logicalModuleId(), target.ordinal(),
                    now, session.memberId(), now, session.memberId());
        }
    }

    private void replaceSubtable(
            RuntimeSession session,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            SchemaField field,
            RecordRuntimeViews.SubtableInput input,
            LocalDateTime now
    ) {
        var minimum = field.properties().path("minRows").asInt(field.required() ? 1 : 0);
        var maximum = field.properties().path("maxRows").asInt(200);
        if (input.rows().size() < minimum || input.rows().size() > maximum) {
            throw subtableInvalid(field, null, "requires " + minimum + ".." + maximum + " rows");
        }
        var columns = subtableColumns(session.systemId(), schemaVersionId, moduleSnapshotId, field.fieldSnapshotId());
        if (columns.isEmpty()) {
            throw subtableInvalid(field, null, "has no published columns");
        }
        var existing = existingRows(session.systemId(), tenantId, recordId, field.fieldSnapshotId());
        var ordinals = new HashSet<Integer>();
        var clientKeys = new HashSet<String>();
        var rowIds = new HashSet<Long>();
        var prepared = new ArrayList<PreparedRow>();
        for (var row : input.rows()) {
            if (row.ordinal() < 0 || row.ordinal() >= maximum || !ordinals.add(row.ordinal())) {
                throw subtableInvalid(field, row.clientRowKey(), "contains a duplicate or invalid ordinal");
            }
            final long rowId;
            final long nextVersion;
            final String clientRowKey;
            if (row.rowId() == null || row.rowId().isBlank()) {
                clientRowKey = requiredClientKey(row.clientRowKey(), field);
                if (!clientKeys.add(clientRowKey)) {
                    throw subtableInvalid(field, clientRowKey, "contains a duplicate clientRowKey");
                }
                rowId = ids.nextId();
                nextVersion = 0;
            } else {
                rowId = positiveId(row.rowId(), "subtables." + field.code() + ".rowId");
                var current = existing.get(rowId);
                if (current == null || row.expectedVersion() == null || current.version() != row.expectedVersion()) {
                    throw subtableInvalid(field, row.rowId(), "contains an unavailable or stale row");
                }
                clientRowKey = current.clientRowKey();
                nextVersion = current.version() + 1;
            }
            if (!rowIds.add(rowId)) {
                throw subtableInvalid(field, Long.toString(rowId), "contains a duplicate rowId");
            }
            prepared.add(new PreparedRow(rowId, clientRowKey, row.ordinal(), nextVersion,
                    normalizeRow(session, tenantId, field, columns, row, rowId)));
        }
        requireDenseOrdinals(ordinals, prepared.size(), "subtables." + field.code());

        jdbc.update("DELETE FROM un_module_sub_value WHERE system_id=? AND tenant_id=? "
                        + "AND parent_record_id=? AND parent_field_snapshot_id=?",
                session.systemId(), tenantId, recordId, field.fieldSnapshotId());
        jdbc.update("DELETE FROM un_module_sub_record WHERE system_id=? AND tenant_id=? "
                        + "AND parent_record_id=? AND parent_field_snapshot_id=?",
                session.systemId(), tenantId, recordId, field.fieldSnapshotId());
        for (var row : prepared) {
            jdbc.update("INSERT INTO un_module_sub_record "
                            + "(id,system_id,tenant_id,parent_record_id,schema_version_id,module_snapshot_id,"
                            + "logical_module_id,parent_field_snapshot_id,parent_logical_field_id,parent_field_type,"
                            + "parent_field_scope,row_id,client_row_key,ordinal,status,created_at,created_by,updated_at,"
                            + "updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,'SUBTABLE','RECORD',?,?,?,'ACTIVE',?,?,?,?,?)",
                    row.rowId(), session.systemId(), tenantId, recordId, schemaVersionId, moduleSnapshotId,
                    moduleSnapshotId, field.fieldSnapshotId(), field.logicalFieldId(), row.rowId(), row.clientRowKey(),
                    row.ordinal(), now, session.memberId(), now, session.memberId(), row.version());
            for (var cell : row.cells()) {
                for (var value : cell.value().rows()) {
                    insertSubValue(session, tenantId, schemaVersionId, moduleSnapshotId, recordId,
                            field.fieldSnapshotId(), row.rowId(), cell.field(), value, now);
                }
            }
        }
    }

    private List<PreparedCell> normalizeRow(
            RuntimeSession session,
            long tenantId,
            SchemaField parent,
            Map<String, SchemaField> columns,
            RecordRuntimeViews.SubRowInput row,
            long rowId
    ) {
        var unknown = new HashSet<>(row.values().keySet());
        unknown.removeAll(columns.keySet());
        if (!unknown.isEmpty()) {
            throw subtableInvalid(parent, row.clientRowKey(), "contains unknown columns " + unknown);
        }
        var result = new ArrayList<PreparedCell>();
        for (var column : columns.values()) {
            var supplied = row.values().get(column.code());
            if (supplied == null || supplied.isNull()) {
                if (column.required()) {
                    throw subtableInvalid(parent, row.clientRowKey(), "requires column " + column.code());
                }
                continue;
            }
            var options = options(session, tenantId, column);
            var canonical = codec.normalize(new CanonicalFieldValueCodec.FieldContract(
                    column.code(), column.name(), column.type(), column.properties(), options), supplied);
            result.add(new PreparedCell(column, canonical));
        }
        return List.copyOf(result);
    }

    private List<CanonicalFieldValueCodec.ValueOption> options(
            RuntimeSession session,
            long tenantId,
            SchemaField field
    ) {
        if ("MEMBER".equals(field.type()) || "DEPARTMENT".equals(field.type())) {
            var catalog = references.resolve(session.systemId(), tenantId, session.memberId());
            var source = "MEMBER".equals(field.type()) ? catalog.members() : catalog.departments();
            return source.stream().map(option -> new CanonicalFieldValueCodec.ValueOption(
                    option.value(), option.label(), null)).toList();
        }
        if (!OPTION_TYPES.contains(field.type()) || field.dictionaryId() == null) {
            return List.of();
        }
        return jdbc.query("SELECT id,item_label,parent_id FROM un_module_dictionary_item "
                        + "WHERE system_id=? AND dictionary_id=? AND deleted_at IS NULL "
                        + "AND desired_status='ENABLED' ORDER BY sort_order,id",
                (result, row) -> new CanonicalFieldValueCodec.ValueOption(
                        Long.toString(result.getLong("id")), result.getString("item_label"),
                        result.getObject("parent_id") == null ? null : Long.toString(result.getLong("parent_id"))),
                session.systemId(), field.dictionaryId());
    }

    private void insertSubValue(
            RuntimeSession session,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            long parentFieldSnapshotId,
            long rowId,
            SchemaField field,
            CanonicalFieldValueCodec.ValueRow value,
            LocalDateTime now
    ) {
        SensitiveCryptoService.EncryptedValue encrypted = null;
        if (Set.of("IDENTITY", "SECRET").contains(field.type())) {
            encrypted = sensitiveCrypto.encrypt(value.sensitiveValue(), new SensitiveCryptoService.ValueContext(
                    session.systemId(), tenantId, moduleSnapshotId, field.logicalFieldId(), rowId,
                    field.fieldSnapshotId(), value.ordinal(), field.type()));
        }
        jdbc.update("INSERT INTO un_module_sub_value "
                        + "(id,system_id,tenant_id,parent_record_id,schema_version_id,module_snapshot_id,"
                        + "parent_field_snapshot_id,row_id,column_field_snapshot_id,source_field_id,logical_field_id,"
                        + "column_field_type,column_field_scope,ordinal,string_value,text_value,decimal_value,date_value,"
                        + "datetime_value,time_value,boolean_value,currency_code,reference_value,encrypted_value,"
                        + "encryption_key_version,value_hash,hash_key_version,display_value,created_at,created_by,"
                        + "updated_at,updated_by,version) VALUES (" + placeholders(33) + ")",
                ids.nextId(), session.systemId(), tenantId, recordId, schemaVersionId, moduleSnapshotId,
                parentFieldSnapshotId, rowId, field.fieldSnapshotId(), field.sourceFieldId(), field.logicalFieldId(),
                field.type(), "SUBTABLE_COLUMN", value.ordinal(), value.stringValue(), value.textValue(),
                value.decimalValue(), value.dateValue(), value.dateTimeValue(), value.timeValue(), value.booleanValue(),
                value.currencyCode(), value.referenceValue(), encrypted == null ? null : encrypted.envelope(),
                encrypted == null ? null : encrypted.encryptionKeyVersion(),
                encrypted == null ? null : encrypted.valueHash(),
                encrypted == null ? null : encrypted.hashKeyVersion(), value.display(), now, session.memberId(),
                now, session.memberId(), 0L);
    }

    private Map<String, SchemaField> parentFields(long systemId, long schemaVersionId, long moduleSnapshotId) {
        var result = new LinkedHashMap<String, SchemaField>();
        jdbc.query("SELECT field_snapshot_id,source_field_id,logical_field_id,dictionary_id,target_module_id,"
                        + "field_code,field_name,field_type,is_required,property_json "
                        + "FROM un_module_runtime_schema_field WHERE system_id=? AND schema_version_id=? "
                        + "AND module_snapshot_id=? AND field_scope='RECORD' AND field_type IN ('RELATION','SUBTABLE')",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                    var field = schemaField(row);
                    result.put(field.code(), field);
                }, systemId, schemaVersionId, moduleSnapshotId);
        return result;
    }

    private SchemaField requireParentField(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            String fieldCode,
            String expectedType
    ) {
        var field = parentFields(systemId, schemaVersionId, moduleSnapshotId).get(fieldCode);
        if (field == null || !expectedType.equals(field.type())) {
            throw invalidComposition(fieldCode, "is not a published " + expectedType + " field");
        }
        return field;
    }

    private Object readSubValue(java.sql.ResultSet row) throws java.sql.SQLException {
        var type = row.getString("column_field_type");
        return switch (type) {
            case "TEXT", "TAG", "PHONE", "EMAIL", "URL" -> row.getString("string_value");
            case "TEXTAREA", "RICH_TEXT" -> row.getString("text_value");
            case "ADDRESS", "GEO", "BARCODE", "JSON" -> parseJson(row.getString("text_value"));
            case "NUMBER" -> row.getBigDecimal("decimal_value").setScale(
                    parseJson(row.getString("property_json")).path("scale").asInt(10));
            case "PERCENT" -> row.getBigDecimal("decimal_value").setScale(4);
            case "PROGRESS" -> row.getBigDecimal("decimal_value").setScale(2);
            case "RATING" -> row.getBigDecimal("decimal_value").intValueExact();
            case "MONEY" -> {
                var currency = row.getString("currency_code");
                yield Map.of("amount", row.getBigDecimal("decimal_value")
                                .setScale(Currency.getInstance(currency).getDefaultFractionDigits()).toPlainString(),
                        "currency", currency);
            }
            case "DATE", "DATE_RANGE" -> row.getDate("date_value").toLocalDate().toString();
            case "DATETIME" -> row.getTimestamp("datetime_value").toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "TIME", "TIME_RANGE" -> row.getTime("time_value").toLocalTime()
                    .format(DateTimeFormatter.ISO_LOCAL_TIME);
            case "SWITCH" -> row.getBoolean("boolean_value");
            case "RADIO", "MEMBER", "DEPARTMENT", "MULTI_SELECT", "CASCADE", "STATUS" ->
                    Long.toString(row.getLong("reference_value"));
            case "IDENTITY", "SECRET" -> null;
            default -> throw new IllegalStateException("Unsupported subtable field type " + type);
        };
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored subtable JSON is invalid", exception);
        }
    }

    private Map<String, SchemaField> subtableColumns(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            long parentFieldSnapshotId
    ) {
        var result = new LinkedHashMap<String, SchemaField>();
        jdbc.query("SELECT field_snapshot_id,source_field_id,logical_field_id,dictionary_id,target_module_id,"
                        + "field_code,field_name,field_type,is_required,property_json "
                        + "FROM un_module_runtime_schema_field WHERE system_id=? AND schema_version_id=? "
                        + "AND module_snapshot_id=? AND parent_field_snapshot_id=? "
                        + "AND field_scope='SUBTABLE_COLUMN' ORDER BY id",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                    var field = schemaField(row);
                    result.put(field.code(), field);
                }, systemId, schemaVersionId, moduleSnapshotId, parentFieldSnapshotId);
        return result;
    }

    private SchemaField schemaField(java.sql.ResultSet row) throws java.sql.SQLException {
        try {
            return new SchemaField(row.getLong("field_snapshot_id"), row.getLong("source_field_id"),
                    row.getLong("logical_field_id"), nullableLong(row, "dictionary_id"),
                    nullableLong(row, "target_module_id"), row.getString("field_code"),
                    row.getString("field_name"), row.getString("field_type"), row.getBoolean("is_required"),
                    (ObjectNode) objectMapper.readTree(row.getString("property_json")));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Published runtime field properties are invalid", exception);
        }
    }

    private Map<Long, ExistingRow> existingRows(long systemId, long tenantId, long recordId, long fieldSnapshotId) {
        var result = new HashMap<Long, ExistingRow>();
        jdbc.query("SELECT row_id,client_row_key,version FROM un_module_sub_record WHERE system_id=? "
                        + "AND tenant_id=? AND parent_record_id=? AND parent_field_snapshot_id=? AND status='ACTIVE'",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> result.put(row.getLong("row_id"),
                        new ExistingRow(row.getString("client_row_key"), row.getLong("version"))),
                systemId, tenantId, recordId, fieldSnapshotId);
        return result;
    }

    private static Map<String, RecordRuntimeViews.RelationInput> uniqueRelations(
            List<RecordRuntimeViews.RelationInput> inputs
    ) {
        var result = new LinkedHashMap<String, RecordRuntimeViews.RelationInput>();
        for (var input : inputs == null ? List.<RecordRuntimeViews.RelationInput>of() : inputs) {
            if (input == null || input.fieldCode() == null || result.putIfAbsent(input.fieldCode(), input) != null) {
                throw invalidComposition("relations", "contains a missing or duplicate fieldCode");
            }
        }
        return result;
    }

    private static Map<String, RecordRuntimeViews.SubtableInput> uniqueSubtables(
            List<RecordRuntimeViews.SubtableInput> inputs
    ) {
        var result = new LinkedHashMap<String, RecordRuntimeViews.SubtableInput>();
        for (var input : inputs == null ? List.<RecordRuntimeViews.SubtableInput>of() : inputs) {
            if (input == null || input.fieldCode() == null || result.putIfAbsent(input.fieldCode(), input) != null) {
                throw invalidComposition("subtables", "contains a missing or duplicate fieldCode");
            }
        }
        return result;
    }

    private static void rejectUnknown(Set<String> supplied, Map<String, SchemaField> fields, String type, String path) {
        for (var code : supplied) {
            var field = fields.get(code);
            if (field == null || !type.equals(field.type())) {
                throw invalidComposition(path + "." + code, "is not a published " + type + " field");
            }
        }
    }

    private static void requireDenseOrdinals(Set<Integer> ordinals, int size, String path) {
        for (var index = 0; index < size; index++) {
            if (!ordinals.contains(index)) {
                throw invalidComposition(path, "ordinals must be dense from zero");
            }
        }
    }

    private static String requiredClientKey(String value, SchemaField field) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw subtableInvalid(field, value, "requires clientRowKey of 1..64 characters");
        }
        return value;
    }

    private static long positiveId(String value, String path) {
        try {
            var result = Long.parseLong(value);
            if (result > 0) {
                return result;
            }
        } catch (RuntimeException ignored) {
            // Converted to the stable external validation error below.
        }
        throw invalidComposition(path, "contains an invalid id");
    }

    private static Long nullableLong(java.sql.ResultSet result, String column) throws java.sql.SQLException {
        var value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static BusinessException relationInvalid(SchemaField field, String reason) {
        return new BusinessException("RECORD_RELATION_INVALID", field.name() + " " + reason,
                HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError("RECORD_RELATION_INVALID", "relations." + field.code(), reason)));
    }

    private static BusinessException subtableInvalid(SchemaField field, String row, String reason) {
        var path = "subtables." + field.code() + (row == null || row.isBlank() ? "" : "." + row);
        return new BusinessException("SUBTABLE_ROW_INVALID", field.name() + " " + reason,
                HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError("SUBTABLE_ROW_INVALID", path, reason)));
    }

    private static BusinessException invalidComposition(String path, String reason) {
        return new BusinessException("FIELD_VALUE_INVALID", reason, HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError("FIELD_VALUE_INVALID", path, reason)));
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private record SchemaField(
            long fieldSnapshotId,
            long sourceFieldId,
            long logicalFieldId,
            Long dictionaryId,
            Long targetModuleId,
            String code,
            String name,
            String type,
            boolean required,
            ObjectNode properties
    ) { }

    private record TargetRecord(
            long id,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            long version,
            int ordinal
    ) {
        private TargetRecord(long schemaVersionId, long moduleSnapshotId, long logicalModuleId, long version) {
            this(0, schemaVersionId, moduleSnapshotId, logicalModuleId, version, 0);
        }

        private TargetRecord withId(long targetId, int targetOrdinal) {
            return new TargetRecord(targetId, schemaVersionId, moduleSnapshotId, logicalModuleId, version, targetOrdinal);
        }
    }

    private record ExistingRow(String clientRowKey, long version) { }

    private record SubRow(long rowId, long version, int ordinal) { }

    public record PageSlice<T>(List<T> items, long total) {
        public PageSlice {
            items = List.copyOf(items);
        }
    }

    private record PreparedCell(SchemaField field, CanonicalFieldValueCodec.CanonicalValue value) { }

    private record PreparedRow(
            long rowId,
            String clientRowKey,
            int ordinal,
            long version,
            List<PreparedCell> cells
    ) { }
}
