package com.unique.examine.event.adapter.jdbc;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.id.IdService;
import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.domain.InboxMessageFilter;
import com.unique.examine.event.port.InboxMessageRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public final class JdbcInboxMessageRepository implements InboxMessageRepository {
    static final String INSERT_SQL = """
            INSERT INTO un_event_message (
                id, system_id, tenant_id, sender_member_id, recipient_member_id,
                template_code, title, body, target_type, target_id, target_path, status,
                created_at, read_at, archived_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    static final String UPDATE_SQL = """
            UPDATE un_event_message
               SET status = ?, read_at = ?, archived_at = ?, version = ?
             WHERE id = ? AND system_id = ? AND tenant_id = ? AND version = ?
            """;
    static final String SELECT_COLUMNS = """
            id, system_id, tenant_id, sender_member_id, recipient_member_id,
            template_code, title, body, target_type, target_id, target_path, status,
            created_at, read_at, archived_at, version
            """;
    static final String FIND_SQL = "SELECT " + SELECT_COLUMNS + """
             FROM un_event_message
            WHERE system_id = ? AND tenant_id = ? AND id = ?
            """;
    static final String INBOX_SQL = "SELECT " + SELECT_COLUMNS + """
             FROM un_event_message
            WHERE system_id = ? AND tenant_id = ? AND recipient_member_id = ?
            ORDER BY created_at DESC, id DESC
            """;
    static final String PAGE_SCOPE_SQL = """
             FROM un_event_message
            WHERE system_id = ? AND tenant_id = ? AND recipient_member_id = ?
            """;
    static final RowMapper<InboxMessage> ROW_MAPPER = (resultSet, rowNum) -> map(resultSet);

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public JdbcInboxMessageRepository(JdbcTemplate jdbc, IdService ids) {
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
    public Optional<InboxMessage> findById(long systemId, long tenantId, long id) {
        return jdbc.query(FIND_SQL, ROW_MAPPER, systemId, tenantId, id).stream().findFirst();
    }

    @Override
    public List<InboxMessage> findInbox(long systemId, long tenantId, long recipientMemberId) {
        return jdbc.query(INBOX_SQL, ROW_MAPPER, systemId, tenantId, recipientMemberId);
    }

    @Override
    public long countInbox(
            long systemId,
            long tenantId,
            long recipientMemberId,
            InboxMessageFilter status
    ) {
        var total = jdbc.queryForObject(
                "SELECT COUNT(*) " + PAGE_SCOPE_SQL + statusPredicate(status),
                Long.class,
                systemId,
                tenantId,
                recipientMemberId
        );
        return total == null ? 0 : total;
    }

    @Override
    public List<InboxMessage> findInboxPage(
            long systemId,
            long tenantId,
            long recipientMemberId,
            InboxMessageFilter status,
            long offset,
            int limit
    ) {
        var sql = "SELECT " + SELECT_COLUMNS + PAGE_SCOPE_SQL + statusPredicate(status) + """
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;
        return jdbc.query(
                sql,
                ROW_MAPPER,
                systemId,
                tenantId,
                recipientMemberId,
                limit,
                offset
        );
    }

    @Override
    public InboxMessage save(InboxMessage message) {
        if (message.version() == 1) {
            insert(message);
        } else {
            update(message);
        }
        return message;
    }

    private void insert(InboxMessage message) {
        try {
            jdbc.update(INSERT_SQL,
                    message.id(), message.systemId(), message.tenantId(), message.senderMemberId(),
                    message.recipientMemberId(), message.templateCode(), message.title(), message.body(),
                    message.target() == null ? null : message.target().type(),
                    message.target() == null ? null : message.target().id(),
                    message.targetPath(), message.status().name(), Timestamp.from(message.createdAt()),
                    timestamp(message.readAt()), timestamp(message.archivedAt()), message.version());
        } catch (DuplicateKeyException duplicate) {
            throw conflict(duplicate);
        }
    }

    private void update(InboxMessage message) {
        int updated = jdbc.update(UPDATE_SQL,
                message.status().name(), timestamp(message.readAt()), timestamp(message.archivedAt()),
                message.version(), message.id(), message.systemId(), message.tenantId(), message.version() - 1);
        if (updated != 1) {
            throw conflict(null);
        }
    }

    private static InboxMessage map(ResultSet resultSet) throws SQLException {
        var targetType = resultSet.getString("target_type");
        var targetId = resultSet.getString("target_id");
        var readAt = instant(resultSet.getTimestamp("read_at"));
        var archivedAt = instant(resultSet.getTimestamp("archived_at"));
        var message = new InboxMessage(
                resultSet.getLong("id"),
                resultSet.getLong("system_id"),
                resultSet.getLong("tenant_id"),
                resultSet.getLong("sender_member_id"),
                resultSet.getLong("recipient_member_id"),
                resultSet.getString("template_code"),
                resultSet.getString("title"),
                resultSet.getString("body"),
                targetType == null ? null : new AggregateRef(targetType, targetId),
                resultSet.getString("target_path"),
                resultSet.getTimestamp("created_at").toInstant(),
                readAt,
                archivedAt,
                resultSet.getLong("version"));
        if (!message.status().name().equals(resultSet.getString("status"))) {
            throw new SQLException("Message status and timestamps disagree");
        }
        return message;
    }

    private static Timestamp timestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    static String statusPredicate(InboxMessageFilter status) {
        return switch (status) {
            case ALL -> " AND status <> 'ARCHIVED'\n";
            case UNREAD -> " AND status = 'UNREAD'\n";
            case READ -> " AND status = 'READ'\n";
            case ARCHIVED -> " AND status = 'ARCHIVED'\n";
        };
    }

    private static EventDomainException conflict(Throwable cause) {
        var error = new EventDomainException("EVENT_MESSAGE_VERSION_CONFLICT", "Message version is stale");
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }
}
