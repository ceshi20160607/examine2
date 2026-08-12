package com.unique.examine.event.adapter.jdbc;

import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.event.domain.DeliveryChannel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcMessageTemplateRepository {
    private static final String TEMPLATE_COLUMNS = """
            id, system_id, template_code, event_type, name, desired_enabled,
            draft_title_template, draft_body_template, channels_json, allowed_variables_json,
            published_version_id, created_at, created_by, updated_at, updated_by, version
            """;
    private static final String VERSION_COLUMNS = """
            id, system_id, template_id, template_code, event_type, version_no,
            source_draft_version, enabled, title_template, body_template, channels_json,
            allowed_variables_json, published_at, published_by
            """;
    private static final String DELIVERY_COLUMNS = """
            id, system_id, tenant_id, recipient_member_id, template_code, template_version_id,
            channel, dedupe_key, target_type, target_id, target_path, status, attempt_count,
            message_id, failure_code, failure_message, masked_destination, duration_ms, trace_id,
            created_at, completed_at
            """;
    private static final String ATTEMPT_COLUMNS = """
            id, delivery_id, system_id, tenant_id, attempt_no, status, duration_ms, trace_id,
            failure_code, failure_message, started_at, completed_at
            """;
    static final String DELIVERY_BY_ID_SQL = "SELECT " + DELIVERY_COLUMNS
            + " FROM un_event_message_delivery_log WHERE id=?";
    static final String VERSION_BY_ID_SQL = "SELECT " + VERSION_COLUMNS
            + " FROM un_event_message_template_version WHERE id=?";
    static final String RETRY_TEMPORARY_SQL = """
            UPDATE un_event_message_delivery_log
               SET status='PENDING', attempt_count=attempt_count+1,
                   message_id=NULL, failure_code=NULL, failure_message=NULL,
                   completed_at=NULL
             WHERE id=? AND status='FAILED'
               AND failure_code='MESSAGE_DELIVERY_TEMPORARY_FAILURE'
               AND attempt_count=? AND attempt_count<3
            """;

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public JdbcMessageTemplateRepository(JdbcTemplate jdbc, IdService ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    public long nextId() {
        return ids.nextId();
    }

    public List<TemplateRecord> templates(long systemId) {
        return jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM un_event_message_template "
                        + "WHERE system_id=? ORDER BY template_code", JdbcMessageTemplateRepository::template,
                systemId);
    }

    public Optional<TemplateRecord> template(long systemId, String code) {
        return jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM un_event_message_template "
                        + "WHERE system_id=? AND template_code=?", JdbcMessageTemplateRepository::template,
                systemId, code).stream().findFirst();
    }

    public void insertTemplate(TemplateRecord value) {
        jdbc.update("""
                INSERT INTO un_event_message_template (
                    id, system_id, template_code, event_type, name, desired_enabled,
                    draft_title_template, draft_body_template, channels_json, allowed_variables_json,
                    published_version_id, created_at, created_by, updated_at, updated_by, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), NULL,
                          UTC_TIMESTAMP(3), ?, UTC_TIMESTAMP(3), ?, 1)
                """, value.id(), value.systemId(), value.templateCode(), value.eventType(), value.name(),
                value.desiredEnabled(), value.draftTitleTemplate(), value.draftBodyTemplate(),
                value.channelsJson(), value.allowedVariablesJson(), value.createdBy(), value.createdBy());
    }

    public void insertVersion(VersionRecord value) {
        jdbc.update("""
                INSERT INTO un_event_message_template_version (
                    id, system_id, template_id, template_code, event_type, version_no,
                    source_draft_version, enabled, title_template, body_template, channels_json,
                    allowed_variables_json, published_at, published_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON),
                          UTC_TIMESTAMP(3), ?)
                """, value.id(), value.systemId(), value.templateId(), value.templateCode(), value.eventType(),
                value.versionNo(), value.sourceDraftVersion(), value.enabled(), value.titleTemplate(),
                value.bodyTemplate(), value.channelsJson(), value.allowedVariablesJson(), value.publishedBy());
    }

    public void pointInitialPublished(long systemId, long templateId, long versionId) {
        jdbc.update("UPDATE un_event_message_template SET published_version_id=? WHERE system_id=? AND id=?",
                versionId, systemId, templateId);
    }

    public boolean updateDraft(long systemId, long templateId, long expectedVersion, String name,
                               boolean enabled, String title, String body, String channelsJson, long actorId) {
        return jdbc.update("""
                UPDATE un_event_message_template
                   SET name=?, desired_enabled=?, draft_title_template=?, draft_body_template=?,
                       channels_json=CAST(? AS JSON), updated_at=UTC_TIMESTAMP(3), updated_by=?, version=version+1
                 WHERE system_id=? AND id=? AND version=?
                """, name, enabled, title, body, channelsJson, actorId, systemId, templateId,
                expectedVersion) == 1;
    }

    public Optional<VersionRecord> publishedVersion(TemplateRecord template) {
        if (template.publishedVersionId() == null) return Optional.empty();
        return version(template.systemId(), template.publishedVersionId());
    }

    public Optional<VersionRecord> version(long systemId, long versionId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM un_event_message_template_version "
                        + "WHERE system_id=? AND id=?", JdbcMessageTemplateRepository::version,
                systemId, versionId).stream().findFirst();
    }

    public Optional<VersionRecord> version(long id) {
        return jdbc.query(VERSION_BY_ID_SQL,
                JdbcMessageTemplateRepository::version, id)
                .stream().findFirst();
    }

    public Optional<VersionRecord> sourceVersion(long templateId, long sourceDraftVersion) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM un_event_message_template_version "
                        + "WHERE template_id=? AND source_draft_version=?",
                JdbcMessageTemplateRepository::version, templateId, sourceDraftVersion).stream().findFirst();
    }

    public long nextVersionNo(long templateId) {
        var value = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 "
                + "FROM un_event_message_template_version WHERE template_id=?", Long.class, templateId);
        return value == null ? 1 : value;
    }

    public boolean publish(long systemId, long templateId, long expectedVersion, long versionId, long actorId) {
        return jdbc.update("""
                UPDATE un_event_message_template
                   SET published_version_id=?, updated_at=UTC_TIMESTAMP(3), updated_by=?
                 WHERE system_id=? AND id=? AND version=?
                """, versionId, actorId, systemId, templateId, expectedVersion) == 1;
    }

    public BeginDelivery begin(ResultNotificationFacade.Command command, Long templateVersionId) {
        return begin(command, templateVersionId, DeliveryChannel.INBOX);
    }

    public BeginDelivery begin(ResultNotificationFacade.Command command, Long templateVersionId,
                               DeliveryChannel channel) {
        if (channel == null) throw new IllegalArgumentException("Delivery channel is required");
        var proposedId = nextId();
        var targetId = command.target().id();
        if (targetId == null) throw new IllegalArgumentException("Notification target id is required");
        var inserted = jdbc.update("""
                INSERT IGNORE INTO un_event_message_delivery_log (
                    id, system_id, tenant_id, recipient_member_id, template_code, template_version_id,
                    channel, dedupe_key, target_type, target_id, target_path, status, attempt_count,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 1, UTC_TIMESTAMP(3))
                """, proposedId, command.systemId(), command.tenantId(), command.recipientMemberId(),
                command.templateCode(), templateVersionId, channel.name(), command.dedupeKey(), command.target().type(),
                targetId, command.targetPath());
        var delivery = jdbc.query("SELECT " + DELIVERY_COLUMNS + " FROM un_event_message_delivery_log "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=? "
                        + "AND channel=? AND dedupe_key=?", JdbcMessageTemplateRepository::delivery,
                command.systemId(), command.tenantId(), command.recipientMemberId(),
                channel.name(), command.dedupeKey())
                .stream().findFirst().orElseThrow();
        if (inserted == 1) insertAttempt(delivery);
        return new BeginDelivery(delivery, inserted == 0);
    }

    public DeliveryRecord complete(long deliveryId, String status, Long messageId,
                                   String failureCode, String failureMessage) {
        return complete(deliveryId, status, messageId, failureCode, failureMessage, 0, null, null);
    }

    public DeliveryRecord complete(long deliveryId, String status, Long messageId,
                                   String failureCode, String failureMessage, long durationMillis,
                                   String traceId, String maskedDestination) {
        var updated = jdbc.update("""
                UPDATE un_event_message_delivery_log
                   SET status=?, message_id=?, failure_code=?, failure_message=?, masked_destination=?,
                       duration_ms=?, trace_id=?, completed_at=UTC_TIMESTAMP(3)
                 WHERE id=? AND status='PENDING'
                """, status, messageId, failureCode, truncate(failureMessage, 500),
                truncate(maskedDestination, 200), durationMillis, truncate(traceId, 64), deliveryId);
        var current = delivery(deliveryId).orElseThrow();
        if (updated == 1) {
            jdbc.update("""
                    UPDATE un_event_message_delivery_attempt
                       SET status=?, duration_ms=?, trace_id=?, failure_code=?, failure_message=?,
                           completed_at=UTC_TIMESTAMP(3)
                     WHERE delivery_id=? AND attempt_no=? AND status='PENDING'
                    """, status, durationMillis, truncate(traceId, 64), failureCode,
                    truncate(failureMessage, 500), deliveryId, current.attemptCount());
        }
        return current;
    }

    public Optional<DeliveryRecord> delivery(long id) {
        return jdbc.query(DELIVERY_BY_ID_SQL,
                JdbcMessageTemplateRepository::delivery, id)
                .stream().findFirst();
    }

    public Optional<DeliveryRecord> retryTemporary(
            long deliveryId,
            int expectedAttemptCount
    ) {
        var updated = jdbc.update(
                RETRY_TEMPORARY_SQL, deliveryId, expectedAttemptCount);
        if (updated != 1) return Optional.empty();
        var delivery = delivery(deliveryId).orElseThrow();
        insertAttempt(delivery);
        return Optional.of(delivery);
    }

    public DeliveryPage deliveryLogs(long systemId, long tenantId, int page, int size,
                                     String channel, String status, String templateCode) {
        var where = new StringBuilder(" WHERE system_id=? AND tenant_id=?");
        var arguments = new ArrayList<Object>(List.of(systemId, tenantId));
        appendFilter(where, arguments, "channel", channel);
        appendFilter(where, arguments, "status", status);
        appendFilter(where, arguments, "template_code", templateCode);
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM un_event_message_delivery_log" + where,
                Long.class, arguments.toArray());
        var queryArguments = new ArrayList<>(arguments);
        queryArguments.add(size);
        queryArguments.add((long) page * size);
        var items = jdbc.query("SELECT " + DELIVERY_COLUMNS + " FROM un_event_message_delivery_log"
                        + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                JdbcMessageTemplateRepository::delivery, queryArguments.toArray());
        return new DeliveryPage(items, count == null ? 0 : count, page, size);
    }

    public Optional<DeliveryRecord> scopedDelivery(long systemId, long tenantId, long deliveryId) {
        return jdbc.query("SELECT " + DELIVERY_COLUMNS + " FROM un_event_message_delivery_log"
                        + " WHERE system_id=? AND tenant_id=? AND id=?",
                JdbcMessageTemplateRepository::delivery, systemId, tenantId, deliveryId)
                .stream().findFirst();
    }

    public List<AttemptRecord> deliveryAttempts(long systemId, long tenantId, long deliveryId) {
        return jdbc.query("SELECT " + ATTEMPT_COLUMNS + " FROM un_event_message_delivery_attempt"
                        + " WHERE system_id=? AND tenant_id=? AND delivery_id=? ORDER BY attempt_no",
                JdbcMessageTemplateRepository::attempt, systemId, tenantId, deliveryId);
    }

    private void insertAttempt(DeliveryRecord delivery) {
        jdbc.update("""
                INSERT INTO un_event_message_delivery_attempt (
                    id, delivery_id, system_id, tenant_id, attempt_no, status, started_at
                ) VALUES (?, ?, ?, ?, ?, 'PENDING', UTC_TIMESTAMP(3))
                """, nextId(), delivery.id(), delivery.systemId(), delivery.tenantId(), delivery.attemptCount());
    }

    private static void appendFilter(StringBuilder where, List<Object> arguments,
                                     String column, String value) {
        if (value == null || value.isBlank()) return;
        where.append(" AND ").append(column).append("=?");
        arguments.add(value);
    }

    private static TemplateRecord template(ResultSet result, int row) throws SQLException {
        var published = result.getObject("published_version_id", Long.class);
        return new TemplateRecord(result.getLong("id"), result.getLong("system_id"),
                result.getString("template_code"), result.getString("event_type"), result.getString("name"),
                result.getBoolean("desired_enabled"), result.getString("draft_title_template"),
                result.getString("draft_body_template"), result.getString("channels_json"),
                result.getString("allowed_variables_json"), published, result.getTimestamp("created_at").toLocalDateTime(),
                result.getLong("created_by"), result.getTimestamp("updated_at").toLocalDateTime(),
                result.getLong("updated_by"), result.getLong("version"));
    }

    private static VersionRecord version(ResultSet result, int row) throws SQLException {
        return new VersionRecord(result.getLong("id"), result.getLong("system_id"),
                result.getLong("template_id"), result.getString("template_code"), result.getString("event_type"),
                result.getLong("version_no"), result.getLong("source_draft_version"),
                result.getBoolean("enabled"), result.getString("title_template"),
                result.getString("body_template"), result.getString("channels_json"),
                result.getString("allowed_variables_json"), result.getTimestamp("published_at").toLocalDateTime(),
                result.getLong("published_by"));
    }

    private static DeliveryRecord delivery(ResultSet result, int row) throws SQLException {
        return new DeliveryRecord(result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("recipient_member_id"),
                result.getString("template_code"), result.getObject("template_version_id", Long.class),
                result.getString("channel"), result.getString("dedupe_key"), result.getString("target_type"),
                result.getString("target_id"), result.getString("target_path"), result.getString("status"),
                result.getInt("attempt_count"), result.getObject("message_id", Long.class),
                result.getString("failure_code"), result.getString("failure_message"),
                result.getString("masked_destination"), result.getObject("duration_ms", Long.class),
                result.getString("trace_id"),
                result.getTimestamp("created_at").toLocalDateTime(),
                result.getTimestamp("completed_at") == null ? null : result.getTimestamp("completed_at").toLocalDateTime());
    }

    private static AttemptRecord attempt(ResultSet result, int row) throws SQLException {
        return new AttemptRecord(result.getLong("id"), result.getLong("delivery_id"),
                result.getLong("system_id"), result.getLong("tenant_id"), result.getInt("attempt_no"),
                result.getString("status"), result.getObject("duration_ms", Long.class),
                result.getString("trace_id"), result.getString("failure_code"),
                result.getString("failure_message"), result.getTimestamp("started_at").toLocalDateTime(),
                result.getTimestamp("completed_at") == null ? null
                        : result.getTimestamp("completed_at").toLocalDateTime());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public record TemplateRecord(long id, long systemId, String templateCode, String eventType, String name,
                                 boolean desiredEnabled, String draftTitleTemplate, String draftBodyTemplate,
                                 String channelsJson, String allowedVariablesJson, Long publishedVersionId,
                                 LocalDateTime createdAt, long createdBy, LocalDateTime updatedAt,
                                 long updatedBy, long version) { }

    public record VersionRecord(long id, long systemId, long templateId, String templateCode, String eventType,
                                long versionNo, long sourceDraftVersion, boolean enabled, String titleTemplate,
                                String bodyTemplate, String channelsJson, String allowedVariablesJson,
                                LocalDateTime publishedAt, long publishedBy) { }

    public record DeliveryRecord(long id, long systemId, long tenantId, long recipientMemberId,
                                 String templateCode, Long templateVersionId, String channel, String dedupeKey,
                                 String targetType, String targetId, String targetPath, String status,
                                 int attemptCount, Long messageId, String failureCode, String failureMessage,
                                 String maskedDestination, Long durationMillis, String traceId,
                                 LocalDateTime createdAt, LocalDateTime completedAt) {
        public DeliveryRecord(long id, long systemId, long tenantId, long recipientMemberId,
                              String templateCode, Long templateVersionId, String channel, String dedupeKey,
                              String targetType, String targetId, String targetPath, String status,
                              int attemptCount, Long messageId, String failureCode, String failureMessage,
                              LocalDateTime createdAt, LocalDateTime completedAt) {
            this(id, systemId, tenantId, recipientMemberId, templateCode, templateVersionId, channel,
                    dedupeKey, targetType, targetId, targetPath, status, attemptCount, messageId,
                    failureCode, failureMessage, null, null, null, createdAt, completedAt);
        }
    }

    public record AttemptRecord(long id, long deliveryId, long systemId, long tenantId,
                                int attemptNo, String status, Long durationMillis, String traceId,
                                String failureCode, String failureMessage,
                                LocalDateTime startedAt, LocalDateTime completedAt) { }

    public record DeliveryPage(List<DeliveryRecord> items, long total, int page, int size) {
        public DeliveryPage { items = List.copyOf(items); }
    }

    public record BeginDelivery(DeliveryRecord delivery, boolean replay) { }
}
