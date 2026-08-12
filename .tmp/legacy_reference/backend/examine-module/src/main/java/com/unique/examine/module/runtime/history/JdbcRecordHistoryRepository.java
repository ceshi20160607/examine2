package com.unique.examine.module.runtime.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@Repository
public class JdbcRecordHistoryRepository implements RecordHistoryRepository {
    static final String PAGE_SQL = """
            SELECT history_id,record_id,record_version,action,actor_member_id,occurred_at,diff_json
              FROM un_module_record_history
             WHERE system_id=? AND tenant_id=? AND record_id=?
             ORDER BY occurred_at DESC,history_id DESC
             LIMIT ? OFFSET ?
            """;
    static final String COUNT_SQL = """
            SELECT COUNT(*)
              FROM un_module_record_history
             WHERE system_id=? AND tenant_id=? AND record_id=?
            """;
    private static final String FIND_EVENT_SQL = """
            SELECT history_id,record_id,record_version,action,actor_member_id,occurred_at,diff_json
              FROM un_module_record_history
             WHERE system_id=? AND tenant_id=? AND record_id=?
               AND record_version=? AND action=?
            """;
    private static final TypeReference<List<RecordHistoryDiff>> DIFF_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcRecordHistoryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public String append(RecordHistoryAppend history) {
        Objects.requireNonNull(history, "history");
        try {
            jdbc.update("""
                            INSERT INTO un_module_record_history (
                                history_id,system_id,tenant_id,record_id,record_version,
                                action,actor_member_id,occurred_at,diff_json
                            ) VALUES (?,?,?,?,?,?,?,?,?)
                            """,
                    history.historyId(),
                    history.systemId(),
                    history.tenantId(),
                    history.recordId(),
                    history.recordVersion(),
                    history.action(),
                    history.actorMemberId(),
                    history.occurredAt(),
                    write(history.diff()));
            return Long.toString(history.historyId());
        } catch (DuplicateKeyException exception) {
            var existing = jdbc.query(
                    FIND_EVENT_SQL,
                    this::map,
                    history.systemId(),
                    history.tenantId(),
                    history.recordId(),
                    history.recordVersion(),
                    history.action());
            if (existing.size() == 1
                    && Objects.equals(existing.getFirst().actorMemberId(), string(history.actorMemberId()))
                    && existing.getFirst().diff().equals(history.diff())) {
                return existing.getFirst().historyId();
            }
            throw new IllegalStateException(
                    "A different history event already occupies this record version", exception);
        }
    }

    @Override
    public RecordHistoryPage page(
            long systemId,
            long tenantId,
            long recordId,
            int page,
            int size
    ) {
        var total = jdbc.queryForObject(COUNT_SQL, Long.class, systemId, tenantId, recordId);
        var offset = Math.multiplyExact((long) page - 1, size);
        var items = jdbc.query(
                PAGE_SQL,
                this::map,
                systemId,
                tenantId,
                recordId,
                size,
                offset);
        return new RecordHistoryPage(items, page, size, total == null ? 0L : total);
    }

    private RecordHistoryEntry map(ResultSet result, int rowNumber) throws SQLException {
        var actor = result.getObject("actor_member_id", Long.class);
        return new RecordHistoryEntry(
                result.getString("history_id"),
                result.getString("record_id"),
                result.getLong("record_version"),
                result.getString("action"),
                string(actor),
                result.getTimestamp("occurred_at").toLocalDateTime(),
                read(result.getString("diff_json")));
    }

    private String write(List<RecordHistoryDiff> diff) {
        try {
            return objectMapper.writeValueAsString(diff);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Record history diff must be JSON serializable", exception);
        }
    }

    private List<RecordHistoryDiff> read(String diff) {
        try {
            return objectMapper.readValue(diff, DIFF_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted record history diff is invalid", exception);
        }
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }
}
