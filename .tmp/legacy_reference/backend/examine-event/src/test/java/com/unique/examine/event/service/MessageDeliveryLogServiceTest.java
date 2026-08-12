package com.unique.examine.event.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.event.adapter.jdbc.JdbcMessageTemplateRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageDeliveryLogServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-08-06T08:00:00");
    private final FakeRepository repository = new FakeRepository();
    private final MessageDeliveryLogService service = new MessageDeliveryLogService(repository);

    @Test
    void listScopesFiltersPagesAndReturnsOnlyMaskedOperationalFields() {
        var page = service.list(10, 20, 0, 25, "EMAIL", "FAILED", "MODULE_EXPORT_FAILED");

        assertThat(repository.filter).isEqualTo("10:20:0:25:EMAIL:FAILED:MODULE_EXPORT_FAILED");
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(value -> {
            assertThat(value.channel()).isEqualTo("EMAIL");
            assertThat(value.status()).isEqualTo("FAILED");
            assertThat(value.recipientMasked()).isEqualTo("member-***101");
            assertThat(value.retryable()).isTrue();
        });
    }

    @Test
    void detailRedactsDedupeDestinationQueryAndStoredExternalFailureBody() {
        var detail = service.detail(10, 20, 501);

        assertThat(detail.recipientMasked()).isEqualTo("member-***101");
        assertThat(detail.targetPath()).isEqualTo("/systems/10/workbench");
        assertThat(detail.dedupeFingerprint()).matches("[0-9a-f]{16}")
                .isNotEqualTo("raw-dedupe-secret");
        assertThat(detail.failureMessage()).isEqualTo(MessageTemplateService.SAFE_TEMPORARY_MESSAGE)
                .doesNotContain("password", "https://", "response-body");
        assertThat(detail.attempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.attemptNo()).isEqualTo(1);
            assertThat(attempt.failureMessage()).isEqualTo(MessageTemplateService.SAFE_TEMPORARY_MESSAGE);
        });
    }

    @Test
    void invalidFiltersAndCrossTenantIdsFailClosed() {
        assertCode(() -> service.list(10, 20, 0, 20, "email", null, null),
                "EVENT_DELIVERY_CHANNEL_INVALID");
        assertCode(() -> service.list(10, 20, 0, 20, null, "SUCCEEDED", null),
                "EVENT_DELIVERY_LOG_FILTER_INVALID");
        assertCode(() -> service.detail(10, 21, 501), "EVENT_DELIVERY_LOG_NOT_FOUND");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }

    private static final class FakeRepository extends JdbcMessageTemplateRepository {
        private final DeliveryRecord delivery = new DeliveryRecord(
                501, 10, 20, 101, "MODULE_EXPORT_FAILED", 301L, "EMAIL",
                "raw-dedupe-secret", "MODULE_EXPORT_TASK", "42",
                "/systems/10/workbench?token=secret#fragment", "FAILED", 1, null,
                MessageTemplateService.TEMPORARY_FAILURE,
                "password=https://provider.invalid?response-body=secret", "user@example.com",
                19L, "trace-email-1", NOW.minusMinutes(1), NOW);
        private String filter;

        private FakeRepository() { super(null, null); }

        @Override
        public DeliveryPage deliveryLogs(long systemId, long tenantId, int page, int size,
                                         String channel, String status, String templateCode) {
            filter = String.join(":", Long.toString(systemId), Long.toString(tenantId),
                    Integer.toString(page), Integer.toString(size), channel, status, templateCode);
            return new DeliveryPage(List.of(delivery), 1, page, size);
        }

        @Override
        public Optional<DeliveryRecord> scopedDelivery(long systemId, long tenantId, long deliveryId) {
            return systemId == 10 && tenantId == 20 && deliveryId == 501
                    ? Optional.of(delivery) : Optional.empty();
        }

        @Override
        public List<AttemptRecord> deliveryAttempts(long systemId, long tenantId, long deliveryId) {
            return List.of(new AttemptRecord(601, 501, 10, 20, 1, "FAILED", 19L,
                    "trace-email-1", MessageTemplateService.TEMPORARY_FAILURE,
                    "response-body=secret", NOW.minusMinutes(1), NOW));
        }
    }
}
