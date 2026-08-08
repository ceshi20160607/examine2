package com.unique.examine.event.adapter.jdbc;

import com.unique.examine.core.id.IdService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMessageTemplateRepositoryRetryContractTest {
    private static final LocalDateTime NOW =
            LocalDateTime.parse("2026-08-05T12:00:00");
    private static final String TEMPORARY =
            "MESSAGE_DELIVERY_TEMPORARY_FAILURE";

    @Test
    void idLookupsRestoreTheGloballyStableDeliveryAndTemplateVersion() {
        var delivery = delivery("DELIVERED", 1, null, 701L);
        var version = version();
        var jdbc = new StatefulJdbcTemplate(delivery, version);
        var repository = repository(jdbc);

        assertThat(repository.delivery(501)).contains(delivery);
        assertThat(jdbc.lastQuerySql)
                .isEqualTo(JdbcMessageTemplateRepository.DELIVERY_BY_ID_SQL);
        assertThat(jdbc.lastQueryArguments).containsExactly(501L);

        assertThat(repository.version(301)).contains(version);
        assertThat(jdbc.lastQuerySql)
                .isEqualTo(JdbcMessageTemplateRepository.VERSION_BY_ID_SQL);
        assertThat(jdbc.lastQueryArguments).containsExactly(301L);
    }

    @Test
    void temporaryFailureCasReopensOneAttemptAndClearsTerminalFacts() {
        var jdbc = new StatefulJdbcTemplate(
                delivery("FAILED", 1, TEMPORARY, null), version());
        var repository = repository(jdbc);

        var retried = repository.retryTemporary(501, 1).orElseThrow();

        assertThat(retried.status()).isEqualTo("PENDING");
        assertThat(retried.attemptCount()).isEqualTo(2);
        assertThat(retried.messageId()).isNull();
        assertThat(retried.failureCode()).isNull();
        assertThat(retried.failureMessage()).isNull();
        assertThat(retried.completedAt()).isNull();
        assertThat(retried.templateVersionId()).isEqualTo(301);
        assertThat(retried.dedupeKey()).isEqualTo("delivery-501");
        assertThat(jdbc.retryUpdateArguments).containsExactly(501L, 1);
        assertThat(JdbcMessageTemplateRepository.RETRY_TEMPORARY_SQL)
                .contains("status='PENDING'")
                .contains("attempt_count=attempt_count+1")
                .contains("message_id=NULL")
                .contains("failure_code=NULL")
                .contains("failure_message=NULL")
                .contains("completed_at=NULL")
                .contains("status='FAILED'")
                .contains("failure_code='MESSAGE_DELIVERY_TEMPORARY_FAILURE'")
                .contains("attempt_count=?")
                .contains("attempt_count<3");
    }

    @Test
    void terminalPermanentStaleExhaustedAndMissingDeliveriesNeverReopen() {
        assertRejected(delivery("DELIVERED", 1, null, 701L), 1);
        assertRejected(delivery("SKIPPED", 1,
                "MESSAGE_TEMPLATE_DISABLED", null), 1);
        assertRejected(delivery("FAILED", 1,
                "MESSAGE_TEMPLATE_INVALID", null), 1);
        assertRejected(delivery("FAILED", 2, TEMPORARY, null), 1);
        assertRejected(delivery("FAILED", 3, TEMPORARY, null), 3);
        assertRejected(null, 1);
    }

    private static void assertRejected(
            JdbcMessageTemplateRepository.DeliveryRecord current,
            int expectedAttemptCount
    ) {
        var jdbc = new StatefulJdbcTemplate(current, version());
        var repository = repository(jdbc);

        assertThat(repository.retryTemporary(501, expectedAttemptCount)).isEmpty();
        assertThat(jdbc.delivery).isSameAs(current);
        assertThat(jdbc.queryCount).isZero();
    }

    private static JdbcMessageTemplateRepository repository(
            StatefulJdbcTemplate jdbc
    ) {
        return new JdbcMessageTemplateRepository(jdbc, new IdService());
    }

    private static JdbcMessageTemplateRepository.DeliveryRecord delivery(
            String status,
            int attemptCount,
            String failureCode,
            Long messageId
    ) {
        return new JdbcMessageTemplateRepository.DeliveryRecord(
                501, 10, 20, 101, "MODULE_EXPORT_SUCCEEDED", 301L,
                "INBOX", "delivery-501", "MODULE_EXPORT_TASK", "42",
                "/systems/10/workbench?task=42", status, attemptCount,
                messageId, failureCode,
                failureCode == null ? null : "bounded safe failure",
                NOW.minusMinutes(1),
                "PENDING".equals(status) ? null : NOW);
    }

    private static JdbcMessageTemplateRepository.VersionRecord version() {
        return new JdbcMessageTemplateRepository.VersionRecord(
                301, 10, 201, "MODULE_EXPORT_SUCCEEDED",
                "MODULE_EXPORT_SUCCEEDED", 2, 2, true,
                "Export completed", "Exported {rows} rows", "[\"INBOX\"]",
                "[\"rows\"]", NOW.minusMinutes(2), 100);
    }

    private static final class StatefulJdbcTemplate extends JdbcTemplate {
        private JdbcMessageTemplateRepository.DeliveryRecord delivery;
        private final JdbcMessageTemplateRepository.VersionRecord version;
        private String lastQuerySql;
        private Object[] lastQueryArguments;
        private Object[] lastUpdateArguments;
        private Object[] retryUpdateArguments;
        private int queryCount;

        private StatefulJdbcTemplate(
                JdbcMessageTemplateRepository.DeliveryRecord delivery,
                JdbcMessageTemplateRepository.VersionRecord version
        ) {
            this.delivery = delivery;
            this.version = version;
        }

        @Override
        public int update(String sql, Object... arguments) {
            lastUpdateArguments = arguments;
            if (JdbcMessageTemplateRepository.RETRY_TEMPORARY_SQL.equals(sql)) {
                retryUpdateArguments = arguments;
            }
            if (!JdbcMessageTemplateRepository.RETRY_TEMPORARY_SQL.equals(sql)
                    || delivery == null
                    || delivery.id() != ((Number) arguments[0]).longValue()
                    || !"FAILED".equals(delivery.status())
                    || !TEMPORARY.equals(delivery.failureCode())
                    || delivery.attemptCount() != ((Number) arguments[1]).intValue()
                    || delivery.attemptCount() >= 3) {
                return 0;
            }
            delivery = new JdbcMessageTemplateRepository.DeliveryRecord(
                    delivery.id(), delivery.systemId(), delivery.tenantId(),
                    delivery.recipientMemberId(), delivery.templateCode(),
                    delivery.templateVersionId(), delivery.channel(),
                    delivery.dedupeKey(), delivery.targetType(), delivery.targetId(),
                    delivery.targetPath(), "PENDING", delivery.attemptCount() + 1,
                    null, null, null, delivery.createdAt(), null);
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(
                String sql,
                RowMapper<T> rowMapper,
                Object... arguments
        ) {
            lastQuerySql = sql;
            lastQueryArguments = arguments;
            queryCount++;
            if (JdbcMessageTemplateRepository.DELIVERY_BY_ID_SQL.equals(sql)) {
                return delivery == null ? List.of() : (List<T>) List.of(delivery);
            }
            if (JdbcMessageTemplateRepository.VERSION_BY_ID_SQL.equals(sql)) {
                return version == null ? List.of() : (List<T>) List.of(version);
            }
            throw new AssertionError("Unexpected query: " + sql);
        }
    }
}
