package com.unique.examine.work.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.work.domain.WorkDomainException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class JdbcWorkConfigurationRepository
        implements WorkConfigurationRepository {
    private static final String COLUMNS = """
            id,system_id,tenant_id,revision,status,snapshot_json,
            rollback_from_revision,created_by,created_at,published_by,
            published_at,version
            """;

    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final ObjectMapper json;
    private final RowMapper<WorkConfiguration> mapper = (row, ignored) ->
            new WorkConfiguration(
                    row.getLong("id"), row.getLong("system_id"),
                    row.getLong("tenant_id"), row.getLong("revision"),
                    WorkConfiguration.Status.valueOf(row.getString("status")),
                    readSnapshot(row.getString("snapshot_json")),
                    nullableLong(row.getObject("rollback_from_revision")),
                    row.getLong("created_by"),
                    row.getTimestamp("created_at").toInstant(),
                    nullableLong(row.getObject("published_by")),
                    row.getTimestamp("published_at") == null ? null
                            : row.getTimestamp("published_at").toInstant(),
                    row.getLong("version"));

    public JdbcWorkConfigurationRepository(
            JdbcTemplate jdbc, IdService ids, ObjectMapper json) {
        if (jdbc == null || ids == null || json == null) {
            throw new IllegalArgumentException(
                    "Work configuration JDBC dependencies are required");
        }
        this.jdbc = jdbc;
        this.ids = ids;
        this.json = json;
    }

    @Override
    public long nextId() {
        return ids.nextId();
    }

    @Override
    public long nextRevision(long systemId, long tenantId) {
        var next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(revision),0)+1
                  FROM un_work_configuration
                 WHERE system_id=? AND tenant_id=?
                """, Long.class, systemId, tenantId);
        return next == null ? 1 : next;
    }

    @Override
    public WorkConfiguration insert(WorkConfiguration value) {
        try {
            jdbc.update("""
                    INSERT INTO un_work_configuration(
                      id,system_id,tenant_id,revision,status,snapshot_json,
                      rollback_from_revision,created_by,created_at,published_by,
                      published_at,version)
                    VALUES (?,?,?,?,?,CAST(? AS JSON),?,?,?,?,?,?)
                    """, value.id(), value.systemId(), value.tenantId(),
                    value.revision(), value.status().name(),
                    write(value.snapshot()), value.rollbackFromRevision(),
                    value.createdBy(), Timestamp.from(value.createdAt()),
                    value.publishedBy(), timestamp(value.publishedAt()), value.version());
            return value;
        } catch (DuplicateKeyException duplicate) {
            throw conflict(duplicate);
        }
    }

    @Override
    public WorkConfiguration update(
            WorkConfiguration value, long expectedVersion) {
        try {
            var updated = jdbc.update("""
                    UPDATE un_work_configuration
                       SET status=?,snapshot_json=CAST(? AS JSON),
                           rollback_from_revision=?,published_by=?,published_at=?,
                           version=?
                     WHERE system_id=? AND tenant_id=? AND id=? AND version=?
                    """, value.status().name(), write(value.snapshot()),
                    value.rollbackFromRevision(), value.publishedBy(),
                    timestamp(value.publishedAt()), value.version(),
                    value.systemId(), value.tenantId(), value.id(), expectedVersion);
            if (updated != 1) throw conflict(null);
            return value;
        } catch (DuplicateKeyException duplicate) {
            throw conflict(duplicate);
        }
    }

    @Override
    public Optional<WorkConfiguration> active(long systemId, long tenantId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM un_work_configuration "
                        + "WHERE system_id=? AND tenant_id=? AND status='PUBLISHED'",
                mapper, systemId, tenantId).stream().findFirst();
    }

    @Override
    public Optional<WorkConfiguration> findByRevision(
            long systemId, long tenantId, long revision) {
        return jdbc.query("SELECT " + COLUMNS + " FROM un_work_configuration "
                        + "WHERE system_id=? AND tenant_id=? AND revision=?",
                mapper, systemId, tenantId, revision).stream().findFirst();
    }

    @Override
    public Optional<WorkConfiguration> findById(
            long systemId, long tenantId, long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM un_work_configuration "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                mapper, systemId, tenantId, id).stream().findFirst();
    }

    @Override
    public List<WorkConfiguration> history(long systemId, long tenantId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM un_work_configuration "
                        + "WHERE system_id=? AND tenant_id=? ORDER BY revision DESC",
                mapper, systemId, tenantId);
    }

    @Override
    public boolean enabledDictionary(long systemId, String dictionaryCode) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_module_dictionary
                 WHERE system_id=? AND dictionary_code=?
                   AND desired_status='ENABLED' AND deleted_at IS NULL
                """, Long.class, systemId, dictionaryCode);
        return count != null && count == 1;
    }

    @Override
    public boolean enabledDictionaryItems(
            long systemId, String dictionaryCode, Set<String> itemCodes) {
        if (itemCodes.isEmpty()) return true;
        var placeholders = String.join(",", java.util.Collections.nCopies(
                itemCodes.size(), "?"));
        var arguments = new java.util.ArrayList<Object>();
        arguments.add(systemId);
        arguments.add(dictionaryCode);
        arguments.addAll(itemCodes);
        var count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM un_module_dictionary_item item
                  JOIN un_module_dictionary dictionary_row
                    ON dictionary_row.system_id=item.system_id
                   AND dictionary_row.id=item.dictionary_id
                 WHERE dictionary_row.system_id=?
                   AND dictionary_row.dictionary_code=?
                   AND dictionary_row.desired_status='ENABLED'
                   AND dictionary_row.deleted_at IS NULL
                   AND item.desired_status='ENABLED'
                   AND item.deleted_at IS NULL
                   AND item.item_code IN (
                """ + placeholders + ")", Long.class, arguments.toArray());
        return count != null && count == itemCodes.size();
    }

    @Override
    public void saveRuntimeValues(
            long systemId, long tenantId, WorkConfiguration.ObjectType objectType,
            long objectId, long configurationRevision, JsonNode values, long actorId) {
        jdbc.update("""
                INSERT INTO un_work_runtime_field_value(
                  system_id,tenant_id,object_type,object_id,
                  configuration_revision,value_json,updated_at,updated_by,version)
                VALUES (?,?,?,?,?,CAST(? AS JSON),UTC_TIMESTAMP(6),?,1)
                ON DUPLICATE KEY UPDATE
                  configuration_revision=VALUES(configuration_revision),
                  value_json=VALUES(value_json),updated_at=VALUES(updated_at),
                  updated_by=VALUES(updated_by),version=version+1
                """, systemId, tenantId, objectType.name(), objectId,
                configurationRevision, write(values), actorId);
    }

    @Override
    public Optional<RuntimeValues> runtimeValues(
            long systemId, long tenantId, WorkConfiguration.ObjectType objectType,
            long objectId) {
        return jdbc.query("""
                SELECT configuration_revision,value_json
                  FROM un_work_runtime_field_value
                 WHERE system_id=? AND tenant_id=? AND object_type=? AND object_id=?
                """, (row, ignored) -> new RuntimeValues(
                        row.getLong("configuration_revision"),
                        readTree(row.getString("value_json"))),
                systemId, tenantId, objectType.name(), objectId)
                .stream().findFirst();
    }

    private WorkConfiguration.Snapshot readSnapshot(String value) {
        try {
            return json.readValue(value, WorkConfiguration.Snapshot.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored Work configuration is invalid", failure);
        }
    }

    private JsonNode readTree(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored Work field values are invalid", failure);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Work configuration JSON is invalid", failure);
        }
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static WorkDomainException conflict(Throwable cause) {
        var error = new WorkDomainException(
                "WORK_CONFIG_VERSION_CONFLICT", "Work configuration version is stale");
        if (cause != null) error.initCause(cause);
        return error;
    }
}
