package com.unique.examine.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.openapi.api.OpenApiAdminSession;
import com.unique.examine.openapi.api.OpenApiViews;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.repository.OpenApiRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiApplicationCallLogServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");

    @Test
    void resolvesScopedApplicationThenReturnsTruthfulSafeFilteredPage() {
        var repository = new RepositoryFixture();
        var service = service(repository.proxy());

        var page = service.callLogs(
                session(20, 30), 10, "SUCCESS", "GET", 1, 20);

        assertThat(repository.events)
                .containsExactly("find:20:30:10", "count:10:SUCCESS:GET",
                        "list:10:SUCCESS:GET:0:20");
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(OpenApiViews.CallLog::id)
                .containsExactly("102", "101");
        assertThat(page.items()).extracting(OpenApiViews.CallLog::observedIp)
                .containsExactly("2001:db8::1", "192.0.2.10");
        assertThat(OpenApiViews.CallLog.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly(
                        "id", "credentialVersion", "routeTemplate",
                        "requestMethod", "resultCategory", "httpStatus",
                        "latencyMs", "requestId", "traceId", "observedIp",
                        "createdAt");
        assertThat(page.toString())
                .doesNotContain("appKeyHash", "secretRef", "applicationId",
                        "systemId", "tenantId", "memberId");
        assertThat(repository.writes).isZero();
    }

    @Test
    void rejectsLowercaseUnknownAndOutOfRangeFiltersWithoutLogAccess() {
        var repository = new RepositoryFixture();
        var service = service(repository.proxy());

        assertCode("OPENAPI_REQUEST_INVALID", () -> service.callLogs(
                session(20, 30), 10, "success", "GET", 1, 20));
        assertCode("OPENAPI_REQUEST_INVALID", () -> service.callLogs(
                session(20, 30), 10, "ALL", "TRACE", 1, 20));
        assertCode("OPENAPI_REQUEST_INVALID", () -> service.callLogs(
                session(20, 30), 10, "ALL", "ALL", 10_001, 20));
        assertCode("OPENAPI_REQUEST_INVALID", () -> service.callLogs(
                session(20, 30), 10, "ALL", "ALL", 1, 101));
        assertThat(repository.events).isEmpty();
        assertThat(repository.writes).isZero();
    }

    @Test
    void foreignTenantSystemAndUnknownApplicationsStayHiddenBeforeLogRead() {
        var repository = new RepositoryFixture();
        var service = service(repository.proxy());

        assertCode("OPENAPI_APPLICATION_NOT_FOUND", () -> service.callLogs(
                session(20, 31), 10, "ALL", "ALL", 1, 20));
        assertCode("OPENAPI_APPLICATION_NOT_FOUND", () -> service.callLogs(
                session(21, 30), 10, "ALL", "ALL", 1, 20));
        assertCode("OPENAPI_APPLICATION_NOT_FOUND", () -> service.callLogs(
                session(20, 30), 99, "ALL", "ALL", 1, 20));

        assertThat(repository.events).containsExactly(
                "find:20:31:10", "find:21:30:10", "find:20:30:99");
        assertThat(repository.writes).isZero();
    }

    private static OpenApiApplicationService service(OpenApiRepository repository) {
        return new OpenApiApplicationService(
                repository,
                (systemId, tenantId, memberId) -> null,
                secretRef -> Optional.empty(),
                new NoIdempotency(),
                new NoAudits(),
                new IdService(),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OpenApiAdminSession session(long systemId, long tenantId) {
        return new OpenApiAdminSession(
                700, systemId, tenantId, 80,
                Set.of(OpenApiAdminSession.MANAGE_PERMISSION));
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo(code);
    }

    private static OpenApiRepository.ApplicationBundle application() {
        var value = new OpenApiApplication(
                10, 20, 30, 40, "application-key-1234567890", "machine",
                OpenApiApplication.Status.ACTIVE, Set.of("openapi.ping"),
                List.of("127.0.0.1"), 60, 1, NOW, 700, NOW, 700, 0);
        var credential = new OpenApiCredential(
                11, 10, 1, "env://OPENAPI_TEST_SECRET",
                OpenApiCredential.Status.ACTIVE, NOW, null, NOW, 700);
        return new OpenApiRepository.ApplicationBundle(value, credential);
    }

    private static List<OpenApiRepository.ApplicationCallLog> logs() {
        return List.of(
                new OpenApiRepository.ApplicationCallLog(
                        102, 2, "/openapi/v1/ping", "GET",
                        OpenApiCallLog.ResultCategory.SUCCESS, 200, 8,
                        "request-2", "trace-2", "2001:db8::1",
                        NOW.minusSeconds(1)),
                new OpenApiRepository.ApplicationCallLog(
                        101, 1, "/openapi/v1/ping", "GET",
                        OpenApiCallLog.ResultCategory.SUCCESS, 200, 12,
                        "request-1", "trace-1", "192.0.2.10",
                        NOW.minusSeconds(2)));
    }

    private static final class RepositoryFixture {
        private final List<String> events = new ArrayList<>();
        private int writes;

        OpenApiRepository proxy() {
            return (OpenApiRepository) Proxy.newProxyInstance(
                    OpenApiRepository.class.getClassLoader(),
                    new Class<?>[]{OpenApiRepository.class},
                    (value, method, arguments) -> switch (method.getName()) {
                        case "find" -> find(arguments);
                        case "countApplicationCallLogs" -> count(arguments);
                        case "listApplicationCallLogs" -> list(arguments);
                        case "toString" -> "RepositoryFixture";
                        default -> mutation(method.getName());
                    });
        }

        private Optional<OpenApiRepository.ApplicationBundle> find(
                Object[] arguments) {
            events.add("find:%s:%s:%s".formatted(arguments));
            return (long) arguments[0] == 20 && (long) arguments[1] == 30
                    && (long) arguments[2] == 10
                    ? Optional.of(application()) : Optional.empty();
        }

        private long count(Object[] arguments) {
            events.add("count:%s:%s:%s".formatted(arguments));
            return 2;
        }

        private List<OpenApiRepository.ApplicationCallLog> list(
                Object[] arguments) {
            events.add("list:%s:%s:%s:%s:%s".formatted(arguments));
            return logs();
        }

        private Object mutation(String name) {
            writes++;
            throw new AssertionError("Unexpected repository method: " + name);
        }
    }

    private static final class NoIdempotency implements IdempotencyFacade {
        @Override
        public Optional<IdempotencyRecord> find(
                String scopeType, String scopeKey, String key) {
            throw new AssertionError("Read-only call-log query used idempotency");
        }

        @Override
        public long begin(
                String scopeType, String scopeKey, String key,
                String requestHash, Duration ttl) {
            throw new AssertionError("Read-only call-log query began idempotency");
        }

        @Override
        public void complete(
                long id, int httpStatus, String responseCode, String responseBody) {
            throw new AssertionError("Read-only call-log query completed idempotency");
        }
    }

    private static final class NoAudits implements OperationAuditFacade {
        @Override public void recordSuccess(OperationAudit audit) {
            throw new AssertionError("Read-only call-log query wrote audit");
        }
        @Override public void recordDenied(OperationAudit audit) {
            throw new AssertionError("Read-only call-log query wrote audit");
        }
        @Override public void recordFailed(OperationAudit audit) {
            throw new AssertionError("Read-only call-log query wrote audit");
        }
    }
}
