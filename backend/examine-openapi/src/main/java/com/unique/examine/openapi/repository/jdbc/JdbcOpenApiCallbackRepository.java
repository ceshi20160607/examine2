package com.unique.examine.openapi.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.openapi.domain.OpenApiCallbackAttempt;
import com.unique.examine.openapi.domain.OpenApiCallbackDelivery;
import com.unique.examine.openapi.domain.OpenApiCallbackSubscription;
import com.unique.examine.openapi.domain.OpenApiCallbackVersion;
import com.unique.examine.openapi.repository.OpenApiCallbackRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class JdbcOpenApiCallbackRepository implements OpenApiCallbackRepository {
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcOpenApiCallbackRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override public Optional<Bundle> find(long systemId, long tenantId, long applicationId,
                                           long subscriptionId) {
        var rows = jdbc.query(OpenApiCallbackSql.SELECT, this::bundle,
                systemId, tenantId, applicationId, subscriptionId);
        if (rows.size() > 1) throw new IllegalStateException("Scoped callback query returned duplicates");
        return rows.stream().findFirst();
    }

    @Override public List<Bundle> list(long systemId, long tenantId, long applicationId) {
        return List.copyOf(jdbc.query(OpenApiCallbackSql.LIST, this::bundle,
                systemId, tenantId, applicationId));
    }

    @Override public List<Bundle> listActiveForEvent(long systemId, long tenantId,
                                                     long applicationId, String eventType) {
        return List.copyOf(jdbc.query(OpenApiCallbackSql.LIST_ACTIVE_EVENT, this::bundle,
                systemId, tenantId, applicationId, eventType));
    }

    @Override @Transactional public void insert(OpenApiCallbackSubscription subscription,
                                                 OpenApiCallbackVersion version) {
        jdbc.update(OpenApiCallbackSql.INSERT_SUBSCRIPTION, subscription.id(), subscription.systemId(),
                subscription.tenantId(), subscription.applicationId(), subscription.name(),
                subscription.status().name(), subscription.currentConfigVersion(),
                timestamp(subscription.createdAt()), subscription.createdBy(),
                timestamp(subscription.updatedAt()), subscription.updatedBy(), subscription.version());
        insertVersion(version);
    }

    @Override @Transactional public boolean replaceVersion(OpenApiCallbackSubscription updated,
                                                            OpenApiCallbackVersion previous,
                                                            OpenApiCallbackVersion replacement,
                                                            long expectedVersion) {
        var changed = jdbc.update(OpenApiCallbackSql.REPLACE_CURRENT_VERSION, updated.name(),
                updated.currentConfigVersion(), timestamp(updated.updatedAt()), updated.updatedBy(),
                updated.systemId(), updated.tenantId(), updated.applicationId(), updated.id(), expectedVersion);
        if (changed != 1) return false;
        if (jdbc.update(OpenApiCallbackSql.RETIRE_VERSION, timestamp(previous.retiredAt()),
                previous.id(), previous.subscriptionId()) != 1) {
            throw new IllegalStateException("OpenAPI callback version changed concurrently");
        }
        insertVersion(replacement);
        return true;
    }

    @Override public boolean changeStatus(long systemId, long tenantId, long applicationId,
                                          long subscriptionId, OpenApiCallbackSubscription.Status status,
                                          long actorId, Instant now, long expectedVersion) {
        return jdbc.update(OpenApiCallbackSql.CHANGE_STATUS, status.name(), timestamp(now), actorId,
                systemId, tenantId, applicationId, subscriptionId, expectedVersion) == 1;
    }

    @Override public boolean insertDelivery(OpenApiCallbackDelivery value) {
        try {
            jdbc.update(OpenApiCallbackSql.INSERT_DELIVERY, value.id(), value.systemId(), value.tenantId(),
                    value.applicationId(), value.subscriptionId(), value.callbackVersionId(),
                    value.eventId(), value.eventType(), value.payloadJson(), value.payloadHash(),
                    value.status().name(), value.attemptCount(), value.lastHttpStatus(), value.failureCode(),
                    value.requestId(), value.traceId(), timestamp(value.createdAt()),
                    timestamp(value.updatedAt()), timestamp(value.completedAt()), value.version());
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override public Optional<DeliveryBundle> findDelivery(long deliveryId) {
        var values = jdbc.query(OpenApiCallbackSql.FIND_DELIVERY,
                (row, number) -> new DeliveryBundle(delivery(row), deliverySubscription(row),
                        deliveryVersion(row)),
                deliveryId);
        if (values.size() > 1) throw new IllegalStateException("Callback delivery query returned duplicates");
        return values.stream().findFirst();
    }

    @Override @Transactional public boolean completeAttempt(OpenApiCallbackDelivery delivery,
                                                             OpenApiCallbackAttempt attempt,
                                                             int expectedAttemptCount,
                                                             long expectedVersion) {
        var changed = jdbc.update(OpenApiCallbackSql.UPDATE_DELIVERY, delivery.status().name(),
                delivery.attemptCount(), delivery.lastHttpStatus(), delivery.failureCode(),
                timestamp(delivery.updatedAt()), timestamp(delivery.completedAt()), delivery.id(),
                expectedAttemptCount, expectedVersion);
        if (changed != 1) return false;
        jdbc.update(OpenApiCallbackSql.INSERT_ATTEMPT, attempt.id(), attempt.deliveryId(),
                attempt.attemptNo(), attempt.outcome().name(), attempt.httpStatus(), attempt.durationMs(),
                attempt.failureCode(), timestamp(attempt.startedAt()), timestamp(attempt.completedAt()));
        return true;
    }

    @Override public List<OpenApiCallbackDelivery> listDeliveries(long systemId, long tenantId,
                                                                  long applicationId, long subscriptionId,
                                                                  int offset, int limit) {
        return List.copyOf(jdbc.query(OpenApiCallbackSql.LIST_DELIVERIES, this::deliveryRow,
                systemId, tenantId, applicationId, subscriptionId, limit, offset));
    }

    @Override public long countDeliveries(long systemId, long tenantId, long applicationId,
                                          long subscriptionId) {
        return Objects.requireNonNullElse(jdbc.queryForObject(OpenApiCallbackSql.COUNT_DELIVERIES,
                Long.class, systemId, tenantId, applicationId, subscriptionId), 0L);
    }

    private void insertVersion(OpenApiCallbackVersion value) {
        jdbc.update(OpenApiCallbackSql.INSERT_VERSION, value.id(), value.subscriptionId(),
                value.configVersion(), value.endpoint().toASCIIString(), write(value.eventTypes()),
                value.secretRef(), value.signingSecretVersion(), value.maxAttempts(),
                value.baseBackoffSeconds(), value.status().name(), timestamp(value.activatedAt()),
                timestamp(value.retiredAt()), timestamp(value.createdAt()), value.createdBy());
    }

    private Bundle bundle(ResultSet row, int number) throws SQLException {
        return new Bundle(subscription(row), callbackVersion(row));
    }

    private OpenApiCallbackSubscription subscription(ResultSet row) throws SQLException {
        return new OpenApiCallbackSubscription(row.getLong("id"), row.getLong("system_id"),
                row.getLong("tenant_id"), row.getLong("application_id"), row.getString("name"),
                OpenApiCallbackSubscription.Status.valueOf(row.getString("status")),
                row.getInt("current_config_version"), row.getTimestamp("created_at").toInstant(),
                row.getLong("created_by"), row.getTimestamp("updated_at").toInstant(),
                row.getLong("updated_by"), row.getLong("version"));
    }

    private OpenApiCallbackVersion callbackVersion(ResultSet row) throws SQLException {
        var retired = row.getTimestamp("retired_at");
        return new OpenApiCallbackVersion(row.getLong("callback_version_id"), row.getLong("id"),
                row.getInt("config_version"), URI.create(row.getString("endpoint_url")),
                readSet(row.getString("event_types_json")), row.getString("secret_ref"),
                row.getInt("signing_secret_version"), row.getInt("max_attempts"),
                row.getInt("base_backoff_seconds"),
                OpenApiCallbackVersion.Status.valueOf(row.getString("callback_version_status")),
                row.getTimestamp("activated_at").toInstant(),
                retired == null ? null : retired.toInstant(),
                row.getTimestamp("callback_version_created_at").toInstant(),
                row.getLong("callback_version_created_by"));
    }

    private OpenApiCallbackDelivery deliveryRow(ResultSet row, int number) throws SQLException {
        return delivery(row);
    }

    private OpenApiCallbackDelivery delivery(ResultSet row) throws SQLException {
        var completed = row.getTimestamp("completed_at");
        return new OpenApiCallbackDelivery(row.getLong("id"), row.getLong("system_id"),
                row.getLong("tenant_id"), row.getLong("application_id"),
                row.getLong("subscription_id"), row.getLong("callback_version_id"),
                row.getString("event_id"), row.getString("event_type"), row.getString("payload_json"),
                row.getString("payload_hash"), OpenApiCallbackDelivery.Status.valueOf(row.getString("status")),
                row.getInt("attempt_count"), row.getObject("last_http_status", Integer.class),
                row.getString("failure_code"), row.getString("request_id"), row.getString("trace_id"),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant(),
                completed == null ? null : completed.toInstant(), row.getLong("version"));
    }

    private OpenApiCallbackSubscription deliverySubscription(ResultSet row) throws SQLException {
        return new OpenApiCallbackSubscription(row.getLong("subscription_row_id"),
                row.getLong("subscription_system_id"), row.getLong("subscription_tenant_id"),
                row.getLong("subscription_application_id"), row.getString("subscription_name"),
                OpenApiCallbackSubscription.Status.valueOf(row.getString("subscription_status")),
                row.getInt("subscription_current_config_version"),
                row.getTimestamp("subscription_created_at").toInstant(),
                row.getLong("subscription_created_by"),
                row.getTimestamp("subscription_updated_at").toInstant(),
                row.getLong("subscription_updated_by"), row.getLong("subscription_version"));
    }

    private OpenApiCallbackVersion deliveryVersion(ResultSet row) throws SQLException {
        var retired = row.getTimestamp("version_retired_at");
        return new OpenApiCallbackVersion(row.getLong("version_row_id"),
                row.getLong("subscription_row_id"), row.getInt("version_config_version"),
                URI.create(row.getString("version_endpoint_url")),
                readSet(row.getString("version_event_types_json")), row.getString("version_secret_ref"),
                row.getInt("version_signing_secret_version"), row.getInt("version_max_attempts"),
                row.getInt("version_base_backoff_seconds"),
                OpenApiCallbackVersion.Status.valueOf(row.getString("version_status")),
                row.getTimestamp("version_activated_at").toInstant(),
                retired == null ? null : retired.toInstant(),
                row.getTimestamp("version_created_at").toInstant(), row.getLong("version_created_by"));
    }

    private String write(Set<String> value) {
        try { return json.writeValueAsString(new TreeSet<>(value)); }
        catch (JsonProcessingException failure) { throw new IllegalArgumentException("Callback events are invalid", failure); }
    }

    private Set<String> readSet(String value) {
        try { return json.readValue(value, STRING_SET); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Stored callback events are invalid", failure); }
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
}
