package com.unique.examine.event.adapter.jdbc;

import com.unique.examine.core.id.IdService;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.port.EventChannelConfigurationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Durable system-level switches and non-secret references for external event channels. */
public final class JdbcEventChannelConfigurationRepository implements EventChannelConfigurationRepository {
    static final String COLUMNS = """
            id, system_id, channel, enabled, endpoint, secret_ref, timeout_ms,
            last_check_at, last_check_status, last_check_trace_id, last_check_duration_ms,
            created_at, created_by, updated_at, updated_by, version
            """;
    static final String FIND_SQL = "SELECT " + COLUMNS + """
              FROM un_event_channel_configuration
             WHERE system_id = ? AND channel = ?
            """;
    static final String LIST_SQL = "SELECT " + COLUMNS + """
              FROM un_event_channel_configuration
             WHERE system_id = ?
             ORDER BY FIELD(channel, 'EMAIL', 'WEBHOOK')
            """;
    static final String INSERT_SQL = """
            INSERT INTO un_event_channel_configuration (
                id, system_id, channel, enabled, endpoint, secret_ref, timeout_ms,
                last_check_at, last_check_status, last_check_trace_id, last_check_duration_ms,
                created_at, created_by, updated_at, updated_by, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, ?, ?, ?, ?, 0)
            """;
    static final String UPDATE_SQL = """
            UPDATE un_event_channel_configuration
               SET enabled = ?, endpoint = ?, secret_ref = ?, timeout_ms = ?,
                   updated_at = ?, updated_by = ?, version = version + 1
             WHERE system_id = ? AND channel = ? AND version = ?
            """;
    static final String RECORD_CHECK_SQL = """
            UPDATE un_event_channel_configuration
               SET last_check_at = ?, last_check_status = ?, last_check_trace_id = ?,
                   last_check_duration_ms = ?
             WHERE system_id = ? AND channel = ?
            """;

    static final RowMapper<Configuration> ROW_MAPPER = (row, rowNumber) -> new Configuration(
            row.getLong("id"), row.getLong("system_id"),
            DeliveryChannel.valueOf(row.getString("channel")), row.getBoolean("enabled"),
            row.getString("endpoint"), row.getString("secret_ref"), row.getInt("timeout_ms"),
            instant(row.getTimestamp("last_check_at")), row.getString("last_check_status"),
            row.getString("last_check_trace_id"), nullableLong(row, "last_check_duration_ms"),
            row.getTimestamp("created_at").toInstant(), row.getLong("created_by"),
            row.getTimestamp("updated_at").toInstant(), row.getLong("updated_by"),
            row.getLong("version"));

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public JdbcEventChannelConfigurationRepository(JdbcTemplate jdbc, IdService ids) {
        if (jdbc == null || ids == null) {
            throw new IllegalArgumentException("JdbcTemplate and IdService are required");
        }
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public Optional<Configuration> find(long systemId, DeliveryChannel channel) {
        return jdbc.query(FIND_SQL, ROW_MAPPER, systemId, channel.name()).stream().findFirst();
    }

    @Override
    public List<Configuration> list(long systemId) {
        return jdbc.query(LIST_SQL, ROW_MAPPER, systemId);
    }

    @Override
    public Optional<Configuration> create(long systemId, DeliveryChannel channel, boolean enabled,
                                          String endpoint, String secretRef, int timeoutMs,
                                          long actorId, Instant now) {
        try {
            jdbc.update(INSERT_SQL, ids.nextId(), systemId, channel.name(), enabled,
                    endpoint, secretRef, timeoutMs, Timestamp.from(now), actorId,
                    Timestamp.from(now), actorId);
            return find(systemId, channel);
        } catch (DuplicateKeyException conflict) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Configuration> update(long systemId, DeliveryChannel channel, boolean enabled,
                                          String endpoint, String secretRef, int timeoutMs,
                                          long actorId, long expectedVersion, Instant now) {
        var changed = jdbc.update(UPDATE_SQL, enabled, endpoint, secretRef, timeoutMs,
                Timestamp.from(now), actorId, systemId, channel.name(), expectedVersion);
        return changed == 1 ? find(systemId, channel) : Optional.empty();
    }

    @Override
    public void recordCheck(long systemId, DeliveryChannel channel, String status,
                            String traceId, long durationMillis, Instant checkedAt) {
        jdbc.update(RECORD_CHECK_SQL, Timestamp.from(checkedAt), status, traceId,
                durationMillis, systemId, channel.name());
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        var value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

}
