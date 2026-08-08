package com.unique.examine.plat.lifecycle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class TenantDataLifecycleEngine {
    static final int PAYLOAD_SCHEMA_VERSION = 1;
    static final long MAX_ROWS = 5_000;
    static final int MAX_PLAINTEXT_BYTES = 32 * 1024 * 1024;
    private static final Set<String> CONTROL_TABLES = Set.of(
            "un_plat_tenant_domain", "un_plat_quota");
    private static final Set<String> RETAINED_LEDGER_TABLES = Set.of(
            "un_audit_operation", "un_audit_security", "un_sys_outbox_event");
    private static final Set<String> RETAINED_SECURITY_TABLES = Set.of(
            "un_plat_context_session");
    private static final Set<String> FILE_REFERENCE_TABLES = Set.of(
            "un_file_object", "un_file_reference");
    private static final Set<String> MERGEABLE_UNIQUE_TABLES = Set.of(
            "un_plat_member_tenant");
    private static final List<String> IMPORT_EXPORT_TABLES = List.of(
            "un_module_import_batch", "un_module_export_task", "un_module_report_export_run");
    private static final Set<String> QUOTA_KEYS = Set.of(
            "MEMBERS", "MODULES", "FIELDS", "STORAGE_BYTES", "IMPORT_EXPORT_JOBS", "OPENAPI_CALLS");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LifecyclePayloadSealer sealer;

    public TenantDataLifecycleEngine(JdbcTemplate jdbc, ObjectMapper json, LifecyclePayloadSealer sealer) {
        this.jdbc = jdbc;
        this.json = json;
        this.sealer = sealer;
    }

    BackupArtifact backup(long operationId, long systemId, long tenantId) {
        var discovery = discover(systemId, tenantId, null, Mode.BACKUP);
        if (!discovery.blockers().isEmpty()) throw blocked(discovery.blockers());
        var tablePayloads = new ArrayList<Map<String, Object>>();
        long totalRows = 0;
        for (var table : discovery.tables()) {
            if (!table.captured()) continue;
            totalRows += table.sourceRows();
            if (totalRows > MAX_ROWS) throw blocked(List.of("PAYLOAD_ROW_LIMIT_EXCEEDED:" + MAX_ROWS));
            var payload = new LinkedHashMap<String, Object>();
            payload.put("table", table.name());
            payload.put("category", table.category());
            payload.put("columns", table.writableColumns());
            payload.put("primaryKey", table.primaryKey());
            payload.put("rows", captureRows(table, systemId, tenantId));
            tablePayloads.add(payload);
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("payloadSchemaVersion", PAYLOAD_SCHEMA_VERSION);
        payload.put("systemId", Long.toString(systemId));
        payload.put("tenantId", Long.toString(tenantId));
        payload.put("schemaFingerprint", discovery.schemaFingerprint());
        payload.put("databaseMigrationVersion", discovery.databaseMigrationVersion());
        payload.put("tables", tablePayloads);
        final byte[] plaintext;
        try {
            plaintext = json.writeValueAsBytes(payload);
        } catch (Exception failure) {
            throw new IllegalStateException("tenant backup payload serialization failed", failure);
        }
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            Arrays.fill(plaintext, (byte) 0);
            throw blocked(List.of("PAYLOAD_SIZE_LIMIT_EXCEEDED:" + MAX_PLAINTEXT_BYTES));
        }
        try {
            var binding = new LifecyclePayloadSealer.Binding(
                    systemId, tenantId, operationId, discovery.schemaFingerprint());
            var sealed = sealer.seal(plaintext, binding);
            var plan = plan(discovery, quotaProjection(systemId, tenantId, null, null, null), true);
            return new BackupArtifact(plan, sealed, totalRows, sealed.ciphertext().length);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    DataPlan migrationPlan(long systemId, long sourceTenantId, long targetTenantId) {
        var discovery = discover(systemId, sourceTenantId, targetTenantId, Mode.MIGRATION);
        var quota = quotaProjection(systemId, sourceTenantId, targetTenantId, null, null);
        return plan(discovery, quota, true);
    }

    DataPlan recoveryPlan(long systemId, long tenantId, StoredBackup backup) {
        var payload = openPayload(systemId, tenantId, backup);
        try {
            var discovery = discover(systemId, tenantId, tenantId, Mode.RECOVERY);
            var blockers = new ArrayList<>(discovery.blockers());
            if (payload.schemaVersion() != PAYLOAD_SCHEMA_VERSION) blockers.add("PAYLOAD_SCHEMA_VERSION_UNSUPPORTED");
            if (payload.rowCount() > MAX_ROWS) blockers.add("PAYLOAD_ROW_LIMIT_EXCEEDED:" + MAX_ROWS);
            if (payload.plaintext().length > MAX_PLAINTEXT_BYTES) {
                blockers.add("PAYLOAD_SIZE_LIMIT_EXCEEDED:" + MAX_PLAINTEXT_BYTES);
            }
            if (!payload.schemaFingerprint().equals(discovery.schemaFingerprint())) blockers.add("DATABASE_SCHEMA_CHANGED");
            if (!payload.databaseMigrationVersion().equals(discovery.databaseMigrationVersion())) {
                blockers.add("DATABASE_MIGRATION_VERSION_CHANGED");
            }
            validatePayloadShape(payload, discovery, blockers);
            validateRecoveryCollisions(systemId, tenantId, payload, discovery, blockers);
            var quota = quotaProjection(systemId, tenantId, null, payload, backup.quotaLimits());
            addQuotaBlockers(quota, blockers);
            var rebuilt = new Discovery(discovery.tables(), discovery.foreignKeys(), discovery.insertOrder(),
                    List.copyOf(new LinkedHashSet<>(blockers)), discovery.schemaFingerprint(),
                    discovery.databaseMigrationVersion(), payload.rowCount(), backup.payloadSizeBytes());
            return plan(rebuilt, quota, true);
        } finally {
            payload.clearSensitive();
        }
    }

    DataPlan withAdditionalBlockers(DataPlan plan, List<String> additional) {
        if (additional.isEmpty()) return plan;
        var blockers = new ArrayList<>(plan.blockers());
        blockers.addAll(additional);
        var normalized = List.copyOf(new LinkedHashSet<>(blockers));
        var fingerprintInput = new TreeMap<String, Object>();
        fingerprintInput.put("schemaFingerprint", plan.schemaFingerprint());
        fingerprintInput.put("databaseMigrationVersion", plan.databaseMigrationVersion());
        fingerprintInput.put("tableImpacts", fingerprintedImpacts(plan.tables(), plan.tableImpacts()));
        fingerprintInput.put("quotaProjection", plan.quotaProjection());
        fingerprintInput.put("blockers", normalized.stream().sorted().toList());
        fingerprintInput.put("insertOrder", plan.insertOrder());
        return new DataPlan(plan.tables(), plan.foreignKeys(), plan.insertOrder(), normalized,
                plan.tableImpacts(), plan.quotaProjection(), hashJson(fingerprintInput),
                plan.schemaFingerprint(), plan.databaseMigrationVersion(), plan.rowCount(), plan.estimatedBytes());
    }

    ExecutionResult migrate(long systemId, long sourceTenantId, long targetTenantId, DataPlan expected) {
        var current = expected;
        requireEligible(current);
        var moved = new TreeMap<String, Long>();
        withForeignKeysSuspended(connection -> {
            for (var table : current.tables()) {
                if (!table.captured() || table.sourceRows() == 0) continue;
                var mergedRows = mergeKnownDuplicates(connection, table.name(), systemId,
                        sourceTenantId, targetTenantId);
                var assignments = new ArrayList<String>();
                assignments.add("tenant_id=?");
                table.tenantReferenceColumns().forEach(column -> assignments.add(quote(column)
                        + "=CASE WHEN " + quote(column) + "=? THEN ? ELSE " + quote(column) + " END"));
                var sourceScope = scope(table.scopeKind(), "", systemId, sourceTenantId);
                var sql = "UPDATE " + quote(table.name()) + " SET " + String.join(",", assignments)
                        + " WHERE " + sourceScope.sql();
                try (var statement = connection.prepareStatement(sql)) {
                    var parameter = 1;
                    statement.setLong(parameter++, targetTenantId);
                    for (var ignored : table.tenantReferenceColumns()) {
                        statement.setLong(parameter++, sourceTenantId);
                        statement.setLong(parameter++, targetTenantId);
                    }
                    bind(statement, parameter, sourceScope.arguments());
                    moved.put(table.name(), mergedRows + statement.executeUpdate());
                }
            }
        });
        validateForeignKeys(systemId, targetTenantId, current);
        return new ExecutionResult(Map.copyOf(moved), moved.values().stream().mapToLong(Long::longValue).sum());
    }

    ExecutionResult recover(long systemId, long tenantId, StoredBackup backup, DataPlan expected) {
        var current = expected;
        requireEligible(current);
        var payload = openPayload(systemId, tenantId, backup);
        try {
            var restored = new TreeMap<String, Long>();
            withForeignKeysSuspended(connection -> {
                var byName = payload.tablesByName();
                var deleteOrder = new ArrayList<>(current.insertOrder());
                java.util.Collections.reverse(deleteOrder);
                for (var tableName : deleteOrder) {
                    var table = current.table(tableName);
                    if (table == null || !table.captured()) continue;
                    var targetScope = scope(table.scopeKind(), "", systemId, tenantId);
                    try (var statement = connection.prepareStatement("DELETE FROM " + quote(tableName)
                            + " WHERE " + targetScope.sql())) {
                        bind(statement, 1, targetScope.arguments());
                        statement.executeUpdate();
                    }
                }
                for (var tableName : current.insertOrder()) {
                    var tablePayload = byName.get(tableName);
                    if (tablePayload == null) continue;
                    restored.put(tableName, insertRows(connection, tablePayload));
                }
            });
            validateForeignKeys(systemId, tenantId, current);
            return new ExecutionResult(Map.copyOf(restored), restored.values().stream().mapToLong(Long::longValue).sum());
        } finally {
            payload.clearSensitive();
        }
    }

    private Discovery discover(long systemId, long sourceTenantId, Long targetTenantId, Mode mode) {
        var names = jdbc.queryForList("SELECT DISTINCT table_name FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND column_name='tenant_id' ORDER BY table_name", String.class);
        var tables = new ArrayList<TablePlan>();
        var blockers = new ArrayList<String>();
        long totalRows = 0;
        for (var name : names) {
            if (!identifier(name)) continue;
            var columns = columns(name);
            var scopeKind = scopeKind(name, columns);
            var sourceScope = scope(scopeKind, "", systemId, sourceTenantId);
            var rows = count("SELECT COUNT(*) FROM " + quote(name) + " WHERE " + sourceScope.sql(),
                    sourceScope.arguments().toArray());
            var targetScope = targetTenantId == null ? null : scope(scopeKind, "", systemId, targetTenantId);
            var targetRows = targetScope == null ? 0 : count("SELECT COUNT(*) FROM " + quote(name)
                    + " WHERE " + targetScope.sql(), targetScope.arguments().toArray());
            var category = category(name);
            var captured = Set.of("DATA", "FILE_REFERENCE").contains(category);
            var primary = primaryKey(name);
            var writable = columns.stream().filter(column -> !column.generated()).map(Column::name).toList();
            var readable = new LinkedHashSet<>(writable);
            uniqueIndexes(name).values().forEach(readable::addAll);
            var tenantReferences = writable.stream().filter(column -> !"tenant_id".equals(column)
                    && column.endsWith("tenant_id")).toList();
            if (scopeKind == ScopeKind.UNSUPPORTED && rows > 0) {
                blockers.add("TABLE_WITHOUT_SYSTEM_SCOPE:" + name);
            }
            if (mode == Mode.BACKUP && "un_sys_job".equals(name)
                    && count("SELECT COUNT(*) FROM " + quote(name) + " WHERE " + sourceScope.sql()
                            + " AND status IN ('QUEUED','RUNNING')", sourceScope.arguments().toArray()) > 0) {
                blockers.add("TENANT_HAS_ACTIVE_JOBS");
            }
            if (captured && rows > 0 && primary.isEmpty()) blockers.add("TABLE_WITHOUT_PRIMARY_KEY:" + name);
            if (captured && rows > 0 && primary.size() > 1 && !primary.contains("tenant_id")) {
                blockers.add("COMPOSITE_GLOBAL_PRIMARY_KEY_UNSUPPORTED:" + name);
            }
            for (var reference : tenantReferences) {
                if (scopeKind != ScopeKind.UNSUPPORTED && rows > 0 && count("SELECT COUNT(*) FROM " + quote(name)
                        + " WHERE " + sourceScope.sql() + " AND " + quote(reference)
                        + " IS NOT NULL AND " + quote(reference) + "<>?",
                        append(sourceScope.arguments(), sourceTenantId)) > 0) {
                    blockers.add("CROSS_TENANT_REFERENCE:" + name + ":" + reference);
                }
            }
            var collisions = mode == Mode.MIGRATION && targetTenantId != null && rows > 0
                    ? uniqueCollisions(name, scopeKind, systemId, sourceTenantId, targetTenantId) : List.<String>of();
            if (!MERGEABLE_UNIQUE_TABLES.contains(name)) {
                collisions.forEach(index -> blockers.add("TARGET_UNIQUE_COLLISION:" + name + ":" + index));
            }
            totalRows += captured ? rows : 0;
            tables.add(new TablePlan(name, category, captured, scopeKind, rows, targetRows,
                    writable, List.copyOf(readable), primary, collisions, tenantReferences,
                    "EMPTY", "EMPTY", 0));
        }
        if (totalRows > MAX_ROWS) blockers.add("PAYLOAD_ROW_LIMIT_EXCEEDED:" + MAX_ROWS);
        long estimatedBytes = 0;
        if (totalRows <= MAX_ROWS) {
            var fingerprinted = new ArrayList<TablePlan>(tables.size());
            for (var table : tables) {
                var sourceFingerprint = "EMPTY";
                var targetFingerprint = "EMPTY";
                long tableBytes = 0;
                var needsFingerprint = table.captured() || "CONTROL".equals(table.category());
                if (needsFingerprint && table.scopeKind() != ScopeKind.UNSUPPORTED
                        && table.sourceRows() > 0 && !table.primaryKey().isEmpty()) {
                    var rows = captureRows(table, systemId, sourceTenantId);
                    var encoded = bytes(rows);
                    try {
                        sourceFingerprint = LifecyclePayloadSealer.sha256(encoded);
                        tableBytes += encoded.length;
                    } finally {
                        Arrays.fill(encoded, (byte) 0);
                    }
                }
                if (needsFingerprint && table.scopeKind() != ScopeKind.UNSUPPORTED
                        && targetTenantId != null && targetTenantId != sourceTenantId
                        && table.targetRows() > 0 && !table.primaryKey().isEmpty()) {
                    var rows = captureRows(table, systemId, targetTenantId);
                    var encoded = bytes(rows);
                    try {
                        targetFingerprint = LifecyclePayloadSealer.sha256(encoded);
                        tableBytes += encoded.length;
                    } finally {
                        Arrays.fill(encoded, (byte) 0);
                    }
                }
                estimatedBytes += tableBytes;
                fingerprinted.add(new TablePlan(table.name(), table.category(), table.captured(),
                        table.scopeKind(), table.sourceRows(), table.targetRows(), table.writableColumns(),
                        table.readableColumns(), table.primaryKey(), table.uniqueCollisions(),
                        table.tenantReferenceColumns(),
                        sourceFingerprint, targetFingerprint, tableBytes));
            }
            tables = fingerprinted;
        }
        if (estimatedBytes > MAX_PLAINTEXT_BYTES) blockers.add("PAYLOAD_SIZE_LIMIT_EXCEEDED:" + MAX_PLAINTEXT_BYTES);
        var foreignKeys = foreignKeys();
        var scoped = tables.stream().collect(java.util.stream.Collectors.toMap(TablePlan::name, value -> value));
        for (var foreignKey : foreignKeys) {
            var child = scoped.get(foreignKey.childTable());
            var parent = scoped.get(foreignKey.parentTable());
            if (parent != null && child == null && parent.captured() && parent.sourceRows() > 0
                    && hasUnscopedInboundReference(foreignKey, parent, systemId, sourceTenantId)) {
                blockers.add("UNSCOPED_INBOUND_TENANT_REFERENCE:" + foreignKey.name());
            }
            if (parent != null && child != null && child.sourceRows() > 0) {
                if (parent.captured() && child.captured()
                        && (!foreignKey.tenantBound() || !foreignKey.systemBound())) {
                    blockers.add("CROSS_TENANT_FOREIGN_KEY:" + foreignKey.name());
                }
                if (parent.captured() != child.captured() && foreignKey.tenantBound()) {
                    blockers.add("PARTIALLY_MIGRATED_TENANT_FOREIGN_KEY:" + foreignKey.name());
                }
            }
        }
        var order = insertOrder(tables, foreignKeys);
        var schema = schemaFingerprint(tables, foreignKeys);
        return new Discovery(List.copyOf(tables), foreignKeys, order,
                List.copyOf(new LinkedHashSet<>(blockers)), schema, migrationVersion(), totalRows, estimatedBytes);
    }

    private DataPlan plan(Discovery discovery, Map<String, Object> quota, boolean enforceQuota) {
        var blockers = new ArrayList<>(discovery.blockers());
        if (enforceQuota) addQuotaBlockers(quota, blockers);
        var impacts = new TreeMap<String, Object>();
        for (var table : discovery.tables()) {
            impacts.put(table.name(), Map.of(
                    "category", table.category(),
                    "action", action(table.category()),
                    "sourceRows", table.sourceRows(),
                    "targetRows", table.targetRows(),
                    "primaryKey", table.primaryKey(),
                    "uniqueCollisions", table.uniqueCollisions(),
                    "tenantReferenceColumns", table.tenantReferenceColumns(),
                    "sourceFingerprint", table.sourceFingerprint(),
                    "targetFingerprint", table.targetFingerprint(),
                    "estimatedBytes", table.estimatedBytes()));
        }
        var fingerprintInput = new TreeMap<String, Object>();
        fingerprintInput.put("schemaFingerprint", discovery.schemaFingerprint());
        fingerprintInput.put("databaseMigrationVersion", discovery.databaseMigrationVersion());
        fingerprintInput.put("tableImpacts", fingerprintedImpacts(discovery.tables(), impacts));
        fingerprintInput.put("quotaProjection", quota);
        fingerprintInput.put("blockers", blockers.stream().sorted().toList());
        fingerprintInput.put("insertOrder", discovery.insertOrder());
        return new DataPlan(discovery.tables(), discovery.foreignKeys(), discovery.insertOrder(),
                List.copyOf(new LinkedHashSet<>(blockers)), Map.copyOf(impacts), Map.copyOf(quota),
                hashJson(fingerprintInput), discovery.schemaFingerprint(), discovery.databaseMigrationVersion(),
                discovery.totalRows(), discovery.estimatedBytes());
    }

    private List<Map<String, Object>> captureRows(TablePlan table, long systemId, long tenantId) {
        if (table.sourceRows() == 0) return List.of();
        var tableScope = scope(table.scopeKind(), "", systemId, tenantId);
        var select = "SELECT " + table.readableColumns().stream().map(TenantDataLifecycleEngine::quote)
                .collect(java.util.stream.Collectors.joining(",")) + " FROM " + quote(table.name())
                + " WHERE " + tableScope.sql() + " ORDER BY "
                + table.primaryKey().stream().map(TenantDataLifecycleEngine::quote)
                .collect(java.util.stream.Collectors.joining(","));
        var rows = jdbc.queryForList(select, tableScope.arguments().toArray());
        var result = new ArrayList<Map<String, Object>>(rows.size());
        for (var row : rows) {
            var encoded = new LinkedHashMap<String, Object>();
            for (var column : table.readableColumns()) encoded.put(column, encode(row.get(column)));
            result.add(encoded);
        }
        return result;
    }

    private StoredPayload openPayload(long systemId, long tenantId, StoredBackup backup) {
        var maximumCiphertextBytes = MAX_PLAINTEXT_BYTES + 1L + 12 + 16;
        if (backup.payloadCiphertext() == null
                || backup.payloadSizeBytes() != backup.payloadCiphertext().length
                || backup.payloadSizeBytes() > maximumCiphertextBytes) {
            throw new BusinessException("TENANT_BACKUP_PAYLOAD_INVALID",
                    "tenant backup payload size is invalid", HttpStatus.CONFLICT);
        }
        var sealed = new LifecyclePayloadSealer.SealedPayload(backup.payloadCiphertext(), backup.keyReference(),
                backup.keyVersion(), backup.plaintextSha256(), backup.ciphertextSha256());
        var plaintext = sealer.open(sealed, new LifecyclePayloadSealer.Binding(
                systemId, tenantId, backup.operationId(), backup.schemaFingerprint()));
        try {
            var root = json.readValue(plaintext, new TypeReference<Map<String, Object>>() { });
            return StoredPayload.from(root, plaintext);
        } catch (Exception failure) {
            Arrays.fill(plaintext, (byte) 0);
            throw new BusinessException("TENANT_BACKUP_PAYLOAD_INVALID",
                    "tenant backup payload is invalid", HttpStatus.CONFLICT);
        }
    }

    private void validatePayloadShape(StoredPayload payload, Discovery discovery, List<String> blockers) {
        var current = discovery.tables().stream().filter(TablePlan::captured)
                .collect(java.util.stream.Collectors.toMap(TablePlan::name, value -> value));
        for (var table : payload.tables()) {
            var actual = current.get(table.name());
            if (actual == null) {
                blockers.add("BACKUP_TABLE_MISSING:" + table.name());
                continue;
            }
            if (!new LinkedHashSet<>(actual.writableColumns()).equals(new LinkedHashSet<>(table.columns()))) {
                blockers.add("BACKUP_COLUMN_SET_CHANGED:" + table.name());
            }
            if (!actual.primaryKey().equals(table.primaryKey())) {
                blockers.add("BACKUP_PRIMARY_KEY_CHANGED:" + table.name());
            }
            if ("un_sys_job".equals(table.name()) && table.rows().stream()
                    .map(row -> decode(row.get("status")))
                    .anyMatch(value -> Set.of("QUEUED", "RUNNING").contains(String.valueOf(value)))) {
                blockers.add("BACKUP_CONTAINS_ACTIVE_JOBS");
            }
        }
    }

    private void validateRecoveryCollisions(long systemId, long tenantId, StoredPayload payload,
                                             Discovery discovery, List<String> blockers) {
        var current = discovery.tables().stream().collect(
                java.util.stream.Collectors.toMap(TablePlan::name, value -> value));
        for (var table : payload.tables()) {
            var plan = current.get(table.name());
            if (plan == null || table.rows().isEmpty()) continue;
            var unique = uniqueIndexes(table.name());
            for (var row : table.rows()) {
                for (var index : unique.entrySet()) {
                    if (index.getValue().contains("tenant_id")) continue;
                    var values = new ArrayList<Object>();
                    var predicates = new ArrayList<String>();
                    var complete = true;
                    for (var column : index.getValue()) {
                        var cell = row.get(column);
                        if (cell == null || cell.get("value") == null) { complete = false; break; }
                        predicates.add(quote(column) + " <=> ?");
                        values.add(decode(cell));
                    }
                    if (!complete) continue;
                    values.add(systemId);
                    values.add(tenantId);
                    var sql = "SELECT COUNT(*) FROM " + quote(table.name()) + " WHERE "
                            + String.join(" AND ", predicates)
                            + " AND NOT (system_id=? AND tenant_id=?)";
                    if (count(sql, values.toArray()) > 0) {
                        blockers.add("RECOVERY_UNIQUE_COLLISION:" + table.name() + ":" + index.getKey());
                    }
                }
            }
        }
    }

    private Map<String, Object> quotaProjection(long systemId, long sourceTenantId, Long targetTenantId,
                                                StoredPayload payload, Map<String, Long> limitOverride) {
        var used = payload == null
                ? currentUsage(systemId, sourceTenantId, targetTenantId)
                : payloadUsage(systemId, payload);
        var quotaTenant = sourceTenantId;
        var limits = new TreeMap<String, Long>();
        if (limitOverride != null) {
            limits.putAll(limitOverride);
        } else if (tableExists("un_plat_quota")) {
            jdbc.query("SELECT quota_key,hard_limit FROM un_plat_quota WHERE system_id=? AND tenant_id=? "
                            + "AND status='ACTIVE'", rs -> {
                        limits.put(rs.getString(1), rs.getLong(2));
                    }, systemId, quotaTenant);
        }
        var result = new TreeMap<String, Object>();
        for (var key : QUOTA_KEYS.stream().sorted().toList()) {
            var value = used.getOrDefault(key, 0L);
            var hard = limits.get(key);
            var item = new LinkedHashMap<String, Object>();
            item.put("used", value);
            item.put("configured", hard != null);
            item.put("hardLimit", hard);
            item.put("exceeded", hard != null && value > hard);
            result.put(key, item);
        }
        return Map.copyOf(result);
    }

    private Map<String, Long> currentUsage(long systemId, long source, Long target) {
        var tenants = target == null ? List.of(source) : List.of(source, target);
        var result = new TreeMap<String, Long>();
        result.put("MEMBERS", tableExists("un_plat_member_tenant")
                ? distinctCount("un_plat_member_tenant", "member_id", systemId, tenants) : 0);
        result.put("MODULES", countIfExists("un_module_definition",
                "SELECT COUNT(*) FROM un_module_definition WHERE system_id=? AND deleted_at IS NULL", systemId));
        result.put("FIELDS", countIfExists("un_module_field",
                "SELECT COUNT(*) FROM un_module_field WHERE system_id=? AND deleted_at IS NULL", systemId));
        result.put("STORAGE_BYTES", sumForTenants("un_file_object", "size_bytes", systemId, tenants));
        long jobs = 0;
        for (var table : IMPORT_EXPORT_TABLES) jobs += countForTenants(table, systemId, tenants);
        result.put("IMPORT_EXPORT_JOBS", jobs);
        result.put("OPENAPI_CALLS", countForTenants("un_openapi_call_log", systemId, tenants));
        return result;
    }

    private Map<String, Long> payloadUsage(long systemId, StoredPayload payload) {
        var result = new TreeMap<String, Long>();
        var memberIds = new LinkedHashSet<String>();
        long storage = 0, jobs = 0, calls = 0;
        for (var table : payload.tables()) {
            if ("un_plat_member_tenant".equals(table.name())) {
                table.rows().forEach(row -> memberIds.add(String.valueOf(decode(row.get("member_id")))));
            }
            if ("un_file_object".equals(table.name())) {
                for (var row : table.rows()) storage += longValue(decode(row.get("size_bytes")));
            }
            if (IMPORT_EXPORT_TABLES.contains(table.name())) jobs += table.rows().size();
            if ("un_openapi_call_log".equals(table.name())) calls += table.rows().size();
        }
        result.put("MEMBERS", (long) memberIds.size());
        result.put("MODULES", countIfExists("un_module_definition",
                "SELECT COUNT(*) FROM un_module_definition WHERE system_id=? AND deleted_at IS NULL", systemId));
        result.put("FIELDS", countIfExists("un_module_field",
                "SELECT COUNT(*) FROM un_module_field WHERE system_id=? AND deleted_at IS NULL", systemId));
        result.put("STORAGE_BYTES", storage);
        result.put("IMPORT_EXPORT_JOBS", jobs);
        result.put("OPENAPI_CALLS", calls);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void addQuotaBlockers(Map<String, Object> quota, List<String> blockers) {
        quota.forEach((key, value) -> {
            var item = (Map<String, Object>) value;
            if (!Boolean.TRUE.equals(item.get("configured"))) blockers.add("QUOTA_NOT_CONFIGURED:" + key);
            else if (Boolean.TRUE.equals(item.get("exceeded"))) blockers.add("QUOTA_HARD_LIMIT_EXCEEDED:" + key);
        });
    }

    private List<String> uniqueCollisions(String table, ScopeKind scopeKind,
                                          long systemId, long source, long target) {
        var result = new ArrayList<String>();
        for (var index : uniqueIndexes(table).entrySet()) {
            var tenantBound = index.getValue().contains("tenant_id")
                    || index.getValue().contains("tenant_key");
            if (!tenantBound) continue;
            var comparable = index.getValue().stream()
                    .filter(column -> !Set.of("system_id", "tenant_id", "tenant_key").contains(column)).toList();
            var predicate = comparable.isEmpty() ? "1=1" : comparable.stream()
                    .map(column -> "t." + quote(column) + " <=> s." + quote(column))
                    .collect(java.util.stream.Collectors.joining(" AND "));
            var targetScope = scope(scopeKind, "t.", systemId, target);
            var sourceScope = scope(scopeKind, "s.", systemId, source);
            var sql = "SELECT COUNT(*) FROM " + quote(table) + " s JOIN " + quote(table)
                    + " t ON " + targetScope.sql() + " AND " + predicate
                    + " WHERE " + sourceScope.sql();
            if (count(sql, concat(targetScope.arguments(), sourceScope.arguments())) > 0) result.add(index.getKey());
        }
        return List.copyOf(result);
    }

    private List<Column> columns(String table) {
        return jdbc.query("SELECT column_name,extra FROM information_schema.columns WHERE table_schema=DATABASE() "
                        + "AND table_name=? ORDER BY ordinal_position",
                (rs, row) -> new Column(rs.getString(1), rs.getString(2).toUpperCase(Locale.ROOT).contains("GENERATED")),
                table);
    }

    private List<String> primaryKey(String table) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.key_column_usage "
                + "WHERE table_schema=DATABASE() AND table_name=? AND constraint_name='PRIMARY' "
                + "ORDER BY ordinal_position", String.class, table);
    }

    private Map<String, List<String>> uniqueIndexes(String table) {
        var rows = jdbc.queryForList("SELECT index_name,column_name,seq_in_index FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name=? AND non_unique=0 "
                + "ORDER BY index_name,seq_in_index", table);
        var result = new TreeMap<String, List<String>>();
        for (var row : rows) result.computeIfAbsent(String.valueOf(row.get("index_name")), key -> new ArrayList<>())
                .add(String.valueOf(row.get("column_name")));
        return result;
    }

    private List<ForeignKey> foreignKeys() {
        var rows = jdbc.queryForList("SELECT constraint_name,table_name,column_name,referenced_table_name,"
                + "referenced_column_name,ordinal_position FROM information_schema.key_column_usage "
                + "WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL "
                + "ORDER BY constraint_name,ordinal_position");
        var grouped = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var row : rows) grouped.computeIfAbsent(String.valueOf(row.get("constraint_name")), key -> new ArrayList<>())
                .add(row);
        var result = new ArrayList<ForeignKey>();
        grouped.forEach((name, values) -> {
            var mappings = values.stream().map(row -> new ColumnMapping(
                    String.valueOf(row.get("column_name")), String.valueOf(row.get("referenced_column_name")))).toList();
            result.add(new ForeignKey(name, String.valueOf(values.getFirst().get("table_name")),
                    String.valueOf(values.getFirst().get("referenced_table_name")), mappings));
        });
        return List.copyOf(result);
    }

    private List<String> insertOrder(List<TablePlan> tables, List<ForeignKey> foreignKeys) {
        var captured = tables.stream().filter(TablePlan::captured).map(TablePlan::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var incoming = new TreeMap<String, Integer>();
        var children = new TreeMap<String, Set<String>>();
        captured.forEach(name -> incoming.put(name, 0));
        for (var edge : foreignKeys) {
            if (!captured.contains(edge.parentTable()) || !captured.contains(edge.childTable())
                    || edge.parentTable().equals(edge.childTable())) continue;
            if (children.computeIfAbsent(edge.parentTable(), key -> new LinkedHashSet<>()).add(edge.childTable())) {
                incoming.compute(edge.childTable(), (key, value) -> value + 1);
            }
        }
        var ready = new java.util.PriorityQueue<String>();
        incoming.forEach((name, degree) -> { if (degree == 0) ready.add(name); });
        var result = new ArrayList<String>();
        while (!ready.isEmpty()) {
            var parent = ready.remove();
            result.add(parent);
            for (var child : children.getOrDefault(parent, Set.of())) {
                var degree = incoming.compute(child, (key, value) -> value - 1);
                if (degree == 0) ready.add(child);
            }
        }
        captured.stream().filter(name -> !result.contains(name)).sorted().forEach(result::add);
        return List.copyOf(result);
    }

    private boolean hasUnscopedInboundReference(
            ForeignKey foreignKey, TablePlan parent, long systemId, long tenantId) {
        var join = foreignKey.mappings().stream().map(mapping -> "c." + quote(mapping.childColumn())
                + "=p." + quote(mapping.parentColumn())).collect(java.util.stream.Collectors.joining(" AND "));
        var parentScope = scope(parent.scopeKind(), "p.", systemId, tenantId);
        var sql = "SELECT COUNT(*) FROM " + quote(foreignKey.childTable()) + " c JOIN "
                + quote(foreignKey.parentTable()) + " p ON " + join + " WHERE " + parentScope.sql();
        return count(sql, parentScope.arguments().toArray()) > 0;
    }

    private String schemaFingerprint(List<TablePlan> tables, List<ForeignKey> foreignKeys) {
        var value = new TreeMap<String, Object>();
        for (var table : tables) value.put(table.name(), Map.of(
                "columns", table.writableColumns(), "primaryKey", table.primaryKey(),
                "uniqueIndexes", uniqueIndexes(table.name()), "scopeKind", table.scopeKind().name()));
        value.put("foreignKeys", foreignKeys.stream().sorted(Comparator.comparing(ForeignKey::name)).toList());
        return hashJson(value);
    }

    private String migrationVersion() {
        if (!tableExists("flyway_schema_history")) return "UNVERSIONED";
        var value = jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=TRUE "
                + "AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class);
        return value == null ? "UNVERSIONED" : value;
    }

    private void validateForeignKeys(long systemId, long tenantId, DataPlan plan) {
        for (var fk : plan.foreignKeys()) {
            var child = plan.table(fk.childTable());
            if (child == null || !child.captured() || child.scopeKind() == ScopeKind.UNSUPPORTED) continue;
            var join = fk.mappings().stream().map(mapping -> "p." + quote(mapping.parentColumn())
                    + "=c." + quote(mapping.childColumn())).collect(java.util.stream.Collectors.joining(" AND "));
            var present = fk.mappings().stream().map(mapping -> "c." + quote(mapping.childColumn()) + " IS NOT NULL")
                    .collect(java.util.stream.Collectors.joining(" AND "));
            var missingColumn = fk.mappings().getFirst().parentColumn();
            var childScope = scope(child.scopeKind(), "c.", systemId, tenantId);
            var sql = "SELECT COUNT(*) FROM " + quote(fk.childTable()) + " c LEFT JOIN "
                    + quote(fk.parentTable()) + " p ON " + join + " WHERE " + childScope.sql() + " AND "
                    + present + " AND p." + quote(missingColumn) + " IS NULL";
            if (count(sql, childScope.arguments().toArray()) > 0) {
                throw new BusinessException("TENANT_DATA_FOREIGN_KEY_VALIDATION_FAILED",
                        "tenant data foreign key validation failed: " + fk.name(), HttpStatus.CONFLICT);
            }
        }
    }

    private void withForeignKeysSuspended(SqlWork work) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement command = connection.createStatement()) {
                command.execute("SET FOREIGN_KEY_CHECKS=0");
            }
            try {
                work.run(connection);
            } finally {
                try (Statement command = connection.createStatement()) {
                    command.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            }
            return null;
        });
    }

    private long insertRows(Connection connection, TablePayload table) throws SQLException {
        if (table.rows().isEmpty()) return 0;
        var sql = "INSERT INTO " + quote(table.name()) + " ("
                + table.columns().stream().map(TenantDataLifecycleEngine::quote)
                .collect(java.util.stream.Collectors.joining(",")) + ") VALUES ("
                + String.join(",", java.util.Collections.nCopies(table.columns().size(), "?")) + ")";
        long inserted = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (var row : table.rows()) {
                for (int index = 0; index < table.columns().size(); index++) {
                    statement.setObject(index + 1, decode(row.get(table.columns().get(index))));
                }
                statement.addBatch();
                if (++inserted % 250 == 0) statement.executeBatch();
            }
            statement.executeBatch();
        }
        return inserted;
    }

    private static Map<String, Object> encode(Object value) {
        var cell = new LinkedHashMap<String, Object>();
        if (value == null) {
            cell.put("kind", "NULL");
            cell.put("value", null);
        } else if (value instanceof byte[] bytes) {
            cell.put("kind", "BINARY");
            cell.put("value", Base64.getEncoder().encodeToString(bytes));
        } else if (value instanceof Blob blob) {
            try {
                cell.put("kind", "BINARY");
                cell.put("value", Base64.getEncoder().encodeToString(blob.getBytes(1, Math.toIntExact(blob.length()))));
            } catch (Exception failure) {
                throw new IllegalStateException("cannot read binary tenant cell", failure);
            }
        } else if (value instanceof TemporalAccessor || value instanceof java.util.Date) {
            cell.put("kind", "TEMPORAL");
            cell.put("value", value.toString());
        } else {
            cell.put("kind", "SCALAR");
            cell.put("value", value);
        }
        return cell;
    }

    private static Object decode(Map<String, Object> cell) {
        if (cell == null || "NULL".equals(cell.get("kind"))) return null;
        if ("BINARY".equals(cell.get("kind"))) {
            return Base64.getDecoder().decode(String.valueOf(cell.get("value")));
        }
        return cell.get("value");
    }

    private long countForTenants(String table, long systemId, List<Long> tenants) {
        if (!tableExists(table) || !tableHasColumn(table, "tenant_id") || !tableHasColumn(table, "system_id")) return 0;
        return count("SELECT COUNT(*) FROM " + quote(table) + " WHERE system_id=? AND tenant_id IN ("
                + placeholders(tenants.size()) + ")", arguments(systemId, tenants));
    }

    private long sumForTenants(String table, String column, long systemId, List<Long> tenants) {
        if (!tableExists(table)) return 0;
        return count("SELECT COALESCE(SUM(" + quote(column) + "),0) FROM " + quote(table)
                + " WHERE system_id=? AND tenant_id IN (" + placeholders(tenants.size()) + ")",
                arguments(systemId, tenants));
    }

    private long distinctCount(String table, String column, long systemId, List<Long> tenants) {
        return count("SELECT COUNT(DISTINCT " + quote(column) + ") FROM " + quote(table)
                + " WHERE system_id=? AND tenant_id IN (" + placeholders(tenants.size()) + ")",
                arguments(systemId, tenants));
    }

    private long countIfExists(String table, String sql, Object... args) {
        return tableExists(table) ? count(sql, args) : 0;
    }

    private static Object[] arguments(long systemId, List<Long> tenants) {
        var values = new ArrayList<Object>();
        values.add(systemId);
        values.addAll(tenants);
        return values.toArray();
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private boolean tableExists(String table) {
        return count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                table) == 1;
    }

    private boolean tableHasColumn(String table, String column) {
        return count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
                + "AND table_name=? AND column_name=?", table, column) == 1;
    }

    private static ScopeKind scopeKind(String table, List<Column> columns) {
        var names = columns.stream().map(Column::name).collect(java.util.stream.Collectors.toSet());
        if (names.contains("system_id")) return ScopeKind.SYSTEM_ID;
        if ("un_plat_department_closure".equals(table)
                && names.containsAll(Set.of("scope_type", "scope_key", "tenant_id"))) {
            return ScopeKind.SYSTEM_SCOPE_KEY;
        }
        if ("un_plat_member_role".equals(table)) return ScopeKind.GLOBAL_TENANT_ID;
        return ScopeKind.UNSUPPORTED;
    }

    private static ScopePredicate scope(ScopeKind kind, String alias, long systemId, long tenantId) {
        return switch (kind) {
            case SYSTEM_ID -> new ScopePredicate(alias + "system_id=? AND " + alias + "tenant_id=?",
                    List.of(systemId, tenantId));
            case SYSTEM_SCOPE_KEY -> new ScopePredicate(alias + "scope_type='SYSTEM' AND "
                    + alias + "scope_key=? AND " + alias + "tenant_id=?", List.of(systemId, tenantId));
            case GLOBAL_TENANT_ID -> new ScopePredicate("EXISTS (SELECT 1 FROM un_plat_tenant lifecycle_tenant "
                    + "WHERE lifecycle_tenant.system_id=? AND lifecycle_tenant.id=" + alias + "tenant_id) AND "
                    + alias + "tenant_id=?", List.of(systemId, tenantId));
            case UNSUPPORTED -> new ScopePredicate(alias + "tenant_id=?", List.of(tenantId));
        };
    }

    private static Map<String, Object> fingerprintedImpacts(
            List<TablePlan> tables, Map<String, Object> impacts) {
        var result = new TreeMap<String, Object>();
        tables.stream().filter(table -> table.captured() || "CONTROL".equals(table.category()))
                .map(TablePlan::name)
                .forEach(name -> result.put(name, impacts.get(name)));
        return result;
    }

    private static Object[] append(List<Object> values, Object value) {
        var result = new ArrayList<Object>(values);
        result.add(value);
        return result.toArray();
    }

    private static Object[] concat(List<Object> left, List<Object> right) {
        var result = new ArrayList<Object>(left.size() + right.size());
        result.addAll(left);
        result.addAll(right);
        return result.toArray();
    }

    private static void bind(PreparedStatement statement, int first, List<Object> values) throws SQLException {
        var parameter = first;
        for (var value : values) statement.setObject(parameter++, value);
    }

    private static long mergeKnownDuplicates(Connection connection, String table, long systemId,
                                             long sourceTenantId, long targetTenantId) throws SQLException {
        if (!"un_plat_member_tenant".equals(table)) return 0;
        var sql = "DELETE source_membership FROM `un_plat_member_tenant` source_membership "
                + "JOIN `un_plat_member_tenant` target_membership "
                + "ON target_membership.system_id=source_membership.system_id "
                + "AND target_membership.member_id=source_membership.member_id "
                + "AND target_membership.tenant_id=? "
                + "WHERE source_membership.system_id=? AND source_membership.tenant_id=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, targetTenantId);
            statement.setLong(2, systemId);
            statement.setLong(3, sourceTenantId);
            return statement.executeUpdate();
        }
    }

    private long count(String sql, Object... args) {
        var value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String hashJson(Object value) {
        try {
            return LifecyclePayloadSealer.sha256(json.writeValueAsBytes(value));
        } catch (Exception failure) {
            throw new IllegalStateException("tenant lifecycle plan serialization failed", failure);
        }
    }

    private byte[] bytes(Object value) {
        try {
            return json.writeValueAsBytes(value);
        } catch (Exception failure) {
            throw new IllegalStateException("tenant data fingerprint serialization failed", failure);
        }
    }

    private static void requireEligible(DataPlan plan) {
        if (!plan.blockers().isEmpty()) throw blocked(plan.blockers());
    }

    private static BusinessException blocked(List<String> blockers) {
        return new BusinessException("TENANT_DATA_PREFLIGHT_BLOCKED",
                "tenant data preflight blocked: " + String.join(",", blockers), HttpStatus.CONFLICT);
    }

    private static String category(String table) {
        if (CONTROL_TABLES.contains(table)) return "CONTROL";
        if (RETAINED_LEDGER_TABLES.contains(table)) return "RETAINED_LEDGER";
        if (RETAINED_SECURITY_TABLES.contains(table)) return "RETAINED_SECURITY";
        if (FILE_REFERENCE_TABLES.contains(table)) return "FILE_REFERENCE";
        return "DATA";
    }

    private static String action(String category) {
        return switch (category) {
            case "CONTROL" -> "CONTROL_PLANE_SERVICE";
            case "RETAINED_LEDGER" -> "RETAIN_IMMUTABLE";
            case "RETAINED_SECURITY" -> "RETAIN_REVOKED";
            case "FILE_REFERENCE" -> "MOVE_REFERENCE_OWNERSHIP_ONLY";
            default -> "ENCRYPT_BACKUP_AND_MOVE_OWNERSHIP";
        };
    }

    private static boolean identifier(String value) { return value != null && value.matches("[a-z0-9_]+"); }
    private static String quote(String value) {
        if (!identifier(value)) throw new IllegalArgumentException("unsafe SQL identifier");
        return "`" + value + "`";
    }
    private static long longValue(Object value) {
        if (value == null) return 0;
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    enum Mode { BACKUP, MIGRATION, RECOVERY }
    enum ScopeKind { SYSTEM_ID, SYSTEM_SCOPE_KEY, GLOBAL_TENANT_ID, UNSUPPORTED }
    record ScopePredicate(String sql, List<Object> arguments) { }
    record Column(String name, boolean generated) { }
    record ColumnMapping(String childColumn, String parentColumn) { }
    record ForeignKey(String name, String childTable, String parentTable, List<ColumnMapping> mappings) {
        boolean tenantBound() {
            return mappings.stream().anyMatch(mapping ->
                    ("tenant_id".equals(mapping.childColumn()) && "tenant_id".equals(mapping.parentColumn()))
                            || ("tenant_key".equals(mapping.childColumn())
                            && "tenant_key".equals(mapping.parentColumn())));
        }
        boolean systemBound() {
            var direct = mappings.stream().anyMatch(mapping -> "system_id".equals(mapping.childColumn())
                    && "system_id".equals(mapping.parentColumn()));
            var scopedKey = mappings.stream().anyMatch(mapping -> "scope_key".equals(mapping.childColumn())
                    && "scope_key".equals(mapping.parentColumn()));
            var scopedType = mappings.stream().anyMatch(mapping -> "scope_type".equals(mapping.childColumn())
                    && "scope_type".equals(mapping.parentColumn()));
            return direct || (scopedKey && scopedType);
        }
    }
    record TablePlan(String name, String category, boolean captured, ScopeKind scopeKind,
                     long sourceRows, long targetRows, List<String> writableColumns, List<String> readableColumns,
                     List<String> primaryKey, List<String> uniqueCollisions,
                     List<String> tenantReferenceColumns,
                     String sourceFingerprint, String targetFingerprint, long estimatedBytes) { }
    record Discovery(List<TablePlan> tables, List<ForeignKey> foreignKeys, List<String> insertOrder,
                     List<String> blockers, String schemaFingerprint, String databaseMigrationVersion,
                     long totalRows, long estimatedBytes) { }
    record DataPlan(List<TablePlan> tables, List<ForeignKey> foreignKeys, List<String> insertOrder,
                    List<String> blockers, Map<String, Object> tableImpacts, Map<String, Object> quotaProjection,
                    String fingerprint, String schemaFingerprint, String databaseMigrationVersion,
                    long rowCount, long estimatedBytes) {
        boolean eligible() { return blockers.isEmpty(); }
        TablePlan table(String name) {
            return tables.stream().filter(table -> table.name().equals(name)).findFirst().orElse(null);
        }
    }
    record BackupArtifact(DataPlan plan, LifecyclePayloadSealer.SealedPayload payload,
                          long rowCount, long payloadSizeBytes) { }
    record StoredBackup(long operationId, byte[] payloadCiphertext, String ciphertextSha256,
                        String plaintextSha256, String keyReference, String keyVersion,
                        String schemaFingerprint, long payloadSizeBytes, Map<String, Long> quotaLimits) { }
    record ExecutionResult(Map<String, Long> tableRows, long totalRows) { }
    record TablePayload(String name, String category, List<String> columns,
                        List<String> primaryKey, List<Map<String, Map<String, Object>>> rows) { }

    private record StoredPayload(int schemaVersion, String schemaFingerprint,
                                 String databaseMigrationVersion, List<TablePayload> tables,
                                 long rowCount, byte[] plaintext) {
        @SuppressWarnings("unchecked")
        static StoredPayload from(Map<String, Object> root, byte[] plaintext) {
            var tables = new ArrayList<TablePayload>();
            long rowCount = 0;
            for (var raw : (List<Map<String, Object>>) root.getOrDefault("tables", List.of())) {
                var rows = (List<Map<String, Map<String, Object>>>) raw.getOrDefault("rows", List.of());
                rowCount += rows.size();
                tables.add(new TablePayload(String.valueOf(raw.get("table")), String.valueOf(raw.get("category")),
                        (List<String>) raw.getOrDefault("columns", List.of()),
                        (List<String>) raw.getOrDefault("primaryKey", List.of()), rows));
            }
            return new StoredPayload(((Number) root.get("payloadSchemaVersion")).intValue(),
                    String.valueOf(root.get("schemaFingerprint")),
                    String.valueOf(root.get("databaseMigrationVersion")), List.copyOf(tables), rowCount, plaintext);
        }
        Map<String, TablePayload> tablesByName() {
            return tables.stream().collect(java.util.stream.Collectors.toMap(TablePayload::name, value -> value));
        }
        void clearSensitive() { Arrays.fill(plaintext, (byte) 0); }
    }

    @FunctionalInterface
    private interface SqlWork { void run(Connection connection) throws SQLException; }
}
