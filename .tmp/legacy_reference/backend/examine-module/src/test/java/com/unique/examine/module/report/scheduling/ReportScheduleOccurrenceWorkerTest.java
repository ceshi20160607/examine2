package com.unique.examine.module.report.scheduling;

import com.unique.examine.core.api.IdempotentMemberMessageFacade;
import com.unique.examine.core.api.ReportExportPrincipalFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.report.exporting.ReportExportViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReportScheduleOccurrenceWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void startsExportThenDeliversTerminalResultOnlyToCurrentRecipients() {
        var persistence = new StoreHandler(pending());
        var exports = new FakeExports(false, success());
        var commands = new ArrayList<IdempotentMemberMessageFacade.Command>();
        var worker = worker(persistence, currentOwner(), activeOnly(101),
                messages(commands), exports);

        worker.startPending();
        assertThat(persistence.current.exportId()).isEqualTo(900L);
        assertThat(persistence.current.status())
                .isEqualTo(ReportScheduleStore.OccurrenceStatus.RUNNING);
        assertThat(exports.startedKey).isEqualTo(
                persistence.current.occurrenceKey());

        worker.monitorRunning();

        assertThat(persistence.current.status())
                .isEqualTo(ReportScheduleStore.OccurrenceStatus.SUCCEEDED);
        assertThat(persistence.current.totalRows()).isEqualTo(5_001L);
        assertThat(persistence.current.processedRows()).isEqualTo(5_000);
        assertThat(persistence.current.truncated()).isTrue();
        assertThat(persistence.current.deliveredRecipientCount()).isEqualTo(1);
        assertThat(persistence.deliveries.values())
                .extracting(value -> value.recipientMemberId())
                .containsExactly(101L);
        assertThat(commands).hasSize(1);
    }

    @Test
    void revokedOwnerFailsClosedBeforeStartingAnExport() {
        var persistence = new StoreHandler(pending());
        var exports = new FakeExports(false, success());
        var worker = worker(persistence,
                (system, tenant, member) -> Optional.empty(),
                activeOnly(101), messages(new ArrayList<>()), exports);

        worker.startPending();

        assertThat(exports.startCalls).isZero();
        assertThat(persistence.current.status())
                .isEqualTo(ReportScheduleStore.OccurrenceStatus.FAILED);
        assertThat(persistence.current.failureCode())
                .isEqualTo("REPORT_SCHEDULE_OWNER_FORBIDDEN");
    }

    @Test
    void transientMonitorFailuresRetryTwiceThenBecomeTerminal() {
        var persistence = new StoreHandler(running(1));
        var exports = new FakeExports(true, null);
        var worker = worker(persistence, currentOwner(), activeOnly(101),
                messages(new ArrayList<>()), exports);

        worker.monitorRunning();
        assertThat(persistence.current.attemptCount()).isEqualTo(2);
        assertThat(persistence.current.status())
                .isEqualTo(ReportScheduleStore.OccurrenceStatus.RUNNING);
        worker.monitorRunning();
        assertThat(persistence.current.attemptCount()).isEqualTo(3);
        worker.monitorRunning();

        assertThat(exports.getCalls).isEqualTo(3);
        assertThat(persistence.current.status())
                .isEqualTo(ReportScheduleStore.OccurrenceStatus.FAILED);
        assertThat(persistence.current.failureCode())
                .isEqualTo("REPORT_SCHEDULE_EXECUTION_FAILED");
    }

    private static ReportScheduleOccurrenceWorker worker(
            StoreHandler persistence,
            ReportExportPrincipalFacade principals,
            RuntimeActiveMemberFacade members,
            IdempotentMemberMessageFacade messages,
            ScheduledReportExportGateway exports
    ) {
        return new ReportScheduleOccurrenceWorker(persistence.proxy(),
                principals, members, messages, exports, new IdService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ReportExportPrincipalFacade currentOwner() {
        return (system, tenant, member) -> Optional.of(
                new ReportExportPrincipalFacade.Principal(9, member, 7,
                        Set.of("system.runtime.access", "system.admin.access",
                                "module.config.manage")));
    }

    private static RuntimeActiveMemberFacade activeOnly(long activeId) {
        return (system, tenant, member) -> member == activeId
                ? Optional.of(new RuntimeActiveMemberFacade.ActiveMember(
                member, null)) : Optional.empty();
    }

    private static IdempotentMemberMessageFacade messages(
            List<IdempotentMemberMessageFacade.Command> commands
    ) {
        return command -> {
            commands.add(command);
            return 1_000 + command.recipientMemberId();
        };
    }

    private static ReportExportViews.Task success() {
        return new ReportExportViews.Task("900", "ops_report", "20", 1,
                "40", List.of("status"), "SUCCEEDED", 5_001L, 5_000,
                true, "70", "ops-report.xlsx", 123L, null, null,
                null, null, null);
    }

    private static ReportScheduleStore.Occurrence pending() {
        return occurrence(ReportScheduleStore.OccurrenceStatus.PENDING,
                0, null, null, null, null, null, 0, false, 0,
                null, null, NOW, null, null, null, 0);
    }

    private static ReportScheduleStore.Occurrence running(int attempt) {
        return occurrence(ReportScheduleStore.OccurrenceStatus.RUNNING,
                attempt, 900L, "RUNNING", null, null, null, 0, false, 0,
                null, null, NOW, null, NOW, null, attempt + 1L);
    }

    private static ReportScheduleStore.Occurrence occurrence(
            ReportScheduleStore.OccurrenceStatus status,
            int attempt,
            Long exportId,
            String exportStatus,
            String filename,
            Long resultSize,
            Long totalRows,
            int processedRows,
            boolean truncated,
            int delivered,
            String failureCode,
            String failureMessage,
            Instant availableAt,
            Instant leaseUntil,
            Instant startedAt,
            Instant finishedAt,
            long version
    ) {
        return new ReportScheduleStore.Occurrence(
                50, 1, 2, 10, 1, "daily_ops", "Daily operations",
                3, "ops_report", 9, 8, List.of(101L, 102L),
                NOW.minusSeconds(60), "schedule:10:123", status, attempt, 3,
                exportId, exportStatus, filename, resultSize, totalRows,
                processedRows, truncated, delivered, failureCode,
                failureMessage, availableAt, leaseUntil, "request-50",
                "trace-50", startedAt, finishedAt, NOW.minusSeconds(120),
                NOW, version);
    }

    private static final class FakeExports
            implements ScheduledReportExportGateway {
        private final boolean failGet;
        private final ReportExportViews.Task terminal;
        private int startCalls;
        private int getCalls;
        private String startedKey;

        private FakeExports(
                boolean failGet,
                ReportExportViews.Task terminal
        ) {
            this.failGet = failGet;
            this.terminal = terminal;
        }

        @Override
        public ReportExportViews.Task start(
                RuntimeSession owner, String reportCode, String occurrenceKey,
                String requestId, String traceId
        ) {
            startCalls++;
            startedKey = occurrenceKey;
            return new ReportExportViews.Task("900", reportCode, "20", 1,
                    "40", List.of("status"), "QUEUED", null, 0, false,
                    "70", null, null, null, null, null, null, null);
        }

        @Override
        public ReportExportViews.Task get(
                RuntimeSession owner, String reportCode, long exportId
        ) {
            getCalls++;
            if (failGet) {
                throw new IllegalStateException("temporary failure");
            }
            return terminal;
        }
    }

    private static final class StoreHandler implements InvocationHandler {
        private ReportScheduleStore.Occurrence current;
        private final Map<String, ReportScheduleStore.Delivery> deliveries =
                new LinkedHashMap<>();

        private StoreHandler(ReportScheduleStore.Occurrence current) {
            this.current = current;
        }

        private ReportScheduleStore proxy() {
            return (ReportScheduleStore) Proxy.newProxyInstance(
                    ReportScheduleStore.class.getClassLoader(),
                    new Class<?>[]{ReportScheduleStore.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "claimPending" -> {
                    if (current.status()
                            != ReportScheduleStore.OccurrenceStatus.PENDING) {
                        yield List.of();
                    }
                    current = transition(
                            ReportScheduleStore.OccurrenceStatus.RUNNING,
                            current.attemptCount() + 1, current.exportId(),
                            current.exportStatus(), null, null, null, 0,
                            false, 0, current.failureCode(),
                            current.failureMessage(), current.availableAt(),
                            (Instant) args[1], NOW, null);
                    yield List.of(current);
                }
                case "claimRunning" -> {
                    if (current.status()
                            != ReportScheduleStore.OccurrenceStatus.RUNNING) {
                        yield List.of();
                    }
                    current = transition(current.status(),
                            current.attemptCount(), current.exportId(),
                            current.exportStatus(), null, null, null, 0,
                            false, 0, current.failureCode(),
                            current.failureMessage(), current.availableAt(),
                            (Instant) args[1], current.startedAt(), null);
                    yield List.of(current);
                }
                case "attachExport" -> {
                    current = transition(
                            ReportScheduleStore.OccurrenceStatus.RUNNING,
                            current.attemptCount(), (Long) args[2],
                            (String) args[3], null, null, null, 0, false, 0,
                            null, null, (Instant) args[4], null,
                            current.startedAt(), null);
                    yield current;
                }
                case "awaitExport" -> {
                    current = transition(current.status(),
                            current.attemptCount(), current.exportId(),
                            (String) args[2], null, null, null, 0, false, 0,
                            null, null, (Instant) args[3], null,
                            current.startedAt(), null);
                    yield current;
                }
                case "retry" -> {
                    current = transition(
                            (ReportScheduleStore.OccurrenceStatus) args[2],
                            current.attemptCount() + 1, current.exportId(),
                            current.exportStatus(), null, null, null, 0,
                            false, 0, (String) args[3], (String) args[4],
                            (Instant) args[5], null, current.startedAt(), null);
                    yield current;
                }
                case "succeed" -> {
                    current = transition(
                            ReportScheduleStore.OccurrenceStatus.SUCCEEDED,
                            current.attemptCount(), current.exportId(),
                            (String) args[2], (String) args[3], (Long) args[4],
                            (Long) args[5], (Integer) args[6], (Boolean) args[7],
                            (Integer) args[8], null, null, null, null,
                            current.startedAt(), (Instant) args[9]);
                    yield current;
                }
                case "fail" -> {
                    current = transition(
                            ReportScheduleStore.OccurrenceStatus.FAILED,
                            current.attemptCount(), current.exportId(),
                            current.exportStatus(), null, null, null, 0,
                            false, (Integer) args[4], (String) args[2],
                            (String) args[3], null, null, current.startedAt(),
                            (Instant) args[5]);
                    yield current;
                }
                case "recordDeliveryIfAbsent" -> {
                    var delivery = (ReportScheduleStore.Delivery) args[0];
                    yield deliveries.computeIfAbsent(
                            delivery.deliveryKey(), ignored -> delivery);
                }
                case "countDeliveries" -> (long) deliveries.size();
                default -> throw new UnsupportedOperationException(
                        method.getName());
            };
        }

        private ReportScheduleStore.Occurrence transition(
                ReportScheduleStore.OccurrenceStatus status,
                int attempt,
                Long exportId,
                String exportStatus,
                String filename,
                Long resultSize,
                Long totalRows,
                int processed,
                boolean truncated,
                int delivered,
                String failureCode,
                String failureMessage,
                Instant available,
                Instant lease,
                Instant started,
                Instant finished
        ) {
            return new ReportScheduleStore.Occurrence(
                    current.id(), current.systemId(), current.tenantId(),
                    current.scheduleId(), current.scheduleVersion(),
                    current.scheduleCode(), current.scheduleName(),
                    current.reportId(), current.reportCode(),
                    current.ownerAccountId(), current.ownerMemberId(),
                    current.configuredRecipientMemberIds(), current.scheduledAt(),
                    current.occurrenceKey(), status, attempt,
                    current.maxAttempts(), exportId, exportStatus, filename,
                    resultSize, totalRows, processed, truncated, delivered,
                    failureCode, failureMessage, available, lease,
                    current.requestId(), current.traceId(), started, finished,
                    current.createdAt(), NOW, current.version() + 1);
        }
    }
}
