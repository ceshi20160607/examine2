package com.unique.examine.event.adapter.jdbc;

import com.unique.examine.core.id.IdService;
import com.unique.examine.event.domain.DeliveryPreference;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.port.DeliveryPreferenceRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public final class JdbcDeliveryPreferenceRepository implements DeliveryPreferenceRepository {
    static final String COLUMNS = """
            id, system_id, tenant_id, member_id, template_code, channel,
            enabled, created_at, updated_at, version
            """;
    static final String FIND_SQL = "SELECT " + COLUMNS + """
             FROM un_event_delivery_preference
            WHERE system_id = ? AND tenant_id = ? AND member_id = ?
              AND template_code = ? AND channel = ?
            """;
    static final String FIND_ALL_SQL = "SELECT " + COLUMNS + """
             FROM un_event_delivery_preference
            WHERE system_id = ? AND tenant_id = ? AND member_id = ? AND channel = ?
            ORDER BY template_code ASC
            """;
    static final String INSERT_SQL = """
            INSERT INTO un_event_delivery_preference (
                id, system_id, tenant_id, member_id, template_code, channel,
                enabled, created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    static final String UPDATE_SQL = """
            UPDATE un_event_delivery_preference
               SET enabled = ?, updated_at = ?, version = ?
             WHERE system_id = ? AND tenant_id = ? AND member_id = ?
               AND template_code = ? AND channel = ? AND version = ?
            """;
    static final RowMapper<DeliveryPreference> ROW_MAPPER = (row, rowNumber) -> new DeliveryPreference(
            row.getLong("id"),
            row.getLong("system_id"),
            row.getLong("tenant_id"),
            row.getLong("member_id"),
            row.getString("template_code"),
            DeliveryChannel.valueOf(row.getString("channel")),
            row.getBoolean("enabled"),
            row.getTimestamp("created_at").toInstant(),
            row.getTimestamp("updated_at").toInstant(),
            row.getLong("version"));

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public JdbcDeliveryPreferenceRepository(JdbcTemplate jdbc, IdService ids) {
        if (jdbc == null || ids == null) {
            throw new IllegalArgumentException("JdbcTemplate and IdService are required");
        }
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public long nextId() {
        return ids.nextId();
    }

    @Override
    public Optional<DeliveryPreference> find(long systemId, long tenantId, long memberId,
                                               String templateCode, DeliveryChannel channel) {
        return jdbc.query(FIND_SQL, ROW_MAPPER, systemId, tenantId, memberId,
                templateCode, channel.name()).stream().findFirst();
    }

    @Override
    public List<DeliveryPreference> findAll(long systemId, long tenantId, long memberId,
                                             DeliveryChannel channel) {
        return jdbc.query(FIND_ALL_SQL, ROW_MAPPER, systemId, tenantId, memberId, channel.name());
    }

    @Override
    public boolean insert(DeliveryPreference preference) {
        try {
            return jdbc.update(INSERT_SQL,
                    preference.id(), preference.systemId(), preference.tenantId(), preference.memberId(),
                    preference.templateCode(), preference.channel().name(), preference.enabled(),
                    Timestamp.from(preference.createdAt()), Timestamp.from(preference.updatedAt()),
                    preference.version()) == 1;
        } catch (DuplicateKeyException conflict) {
            return false;
        }
    }

    @Override
    public boolean update(DeliveryPreference preference, long expectedVersion) {
        return jdbc.update(UPDATE_SQL,
                preference.enabled(), Timestamp.from(preference.updatedAt()), preference.version(),
                preference.systemId(), preference.tenantId(), preference.memberId(),
                preference.templateCode(), preference.channel().name(), expectedVersion) == 1;
    }
}
