package com.unique.examine.module.report.scheduling;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotentMemberMessageFacade;
import com.unique.examine.core.api.ReportExportPrincipalFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.security.ConfigPermissions;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.exporting.ReportExportViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

@Component
public class ReportScheduleOccurrenceWorker {
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration EXPORT_CHECK_DELAY = Duration.ofSeconds(1);

    private final ReportScheduleStore store;
    private final ReportExportPrincipalFacade principals;
    private final RuntimeActiveMemberFacade activeMembers;
    private final IdempotentMemberMessageFacade messages;
    private final ScheduledReportExportGateway exports;
    private final IdService ids;
    private final Clock clock;

    @Autowired
    public ReportScheduleOccurrenceWorker(
            ReportScheduleStore store,
            ReportExportPrincipalFacade principals,
            RuntimeActiveMemberFacade activeMembers,
            IdempotentMemberMessageFacade messages,
            ScheduledReportExportGateway exports,
            IdService ids
    ) {
        this(store, principals, activeMembers, messages, exports, ids,
                Clock.systemUTC());
    }

    ReportScheduleOccurrenceWorker(
            ReportScheduleStore store,
            ReportExportPrincipalFacade principals,
            RuntimeActiveMemberFacade activeMembers,
            IdempotentMemberMessageFacade messages,
            ScheduledReportExportGateway exports,
            IdService ids,
            Clock clock
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.principals = Objects.requireNonNull(principals, "principals");
        this.activeMembers = Objects.requireNonNull(activeMembers,
                "activeMembers");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.exports = Objects.requireNonNull(exports, "exports");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString =
            "${examine.jobs.report-schedule.start-delay-ms:250}")
    @Transactional
    public void startPending() {
        var now = clock.instant();
        store.claimPending(now, now.plus(LEASE), 1).stream().findFirst()
                .ifPresent(this::start);
    }

    @Scheduled(fixedDelayString =
            "${examine.jobs.report-schedule.monitor-delay-ms:250}")
    @Transactional
    public void monitorRunning() {
        var now = clock.instant();
        store.claimRunning(now, now.plus(LEASE), 1).stream().findFirst()
                .ifPresent(this::monitor);
    }

    private void start(ReportScheduleStore.Occurrence occurrence) {
        try {
            var owner = owner(occurrence);
            var task = exports.start(owner, occurrence.reportCode(),
                    occurrence.occurrenceKey(), occurrence.requestId(),
                    occurrence.traceId());
            store.attachExport(occurrence.id(), occurrence.version(),
                    id(task.exportId()), task.status(),
                    clock.instant().plus(EXPORT_CHECK_DELAY), clock.instant());
        } catch (RuntimeException failure) {
            handleFailure(occurrence,
                    ReportScheduleStore.OccurrenceStatus.PENDING, failure);
        }
    }

    private void monitor(ReportScheduleStore.Occurrence occurrence) {
        try {
            if (occurrence.exportId() == null) {
                throw new IllegalStateException(
                        "Running schedule occurrence has no export");
            }
            var owner = owner(occurrence);
            var task = exports.get(owner, occurrence.reportCode(),
                    occurrence.exportId());
            switch (task.status()) {
                case "QUEUED", "RUNNING" -> store.awaitExport(
                        occurrence.id(), occurrence.version(), task.status(),
                        clock.instant().plus(EXPORT_CHECK_DELAY),
                        clock.instant());
                case "SUCCEEDED" -> complete(occurrence, task);
                case "FAILED" -> terminalFailure(occurrence,
                        safeCode(task.failureCode()),
                        safeMessage(task.failureMessage()));
                default -> throw new IllegalStateException(
                        "Unsupported report export status " + task.status());
            }
        } catch (RuntimeException failure) {
            handleFailure(occurrence,
                    ReportScheduleStore.OccurrenceStatus.RUNNING, failure);
        }
    }

    private void complete(
            ReportScheduleStore.Occurrence occurrence,
            ReportExportViews.Task task
    ) {
        if (task.resultFilename() == null || task.resultSize() == null
                || task.totalRows() == null) {
            throw new IllegalStateException(
                    "Successful report export result is incomplete");
        }
        var delivered = deliver(occurrence, true, null);
        store.succeed(occurrence.id(), occurrence.version(), task.status(),
                task.resultFilename(), task.resultSize(), task.totalRows(),
                task.processedRows(), task.truncated(), delivered,
                clock.instant());
    }

    private void handleFailure(
            ReportScheduleStore.Occurrence occurrence,
            ReportScheduleStore.OccurrenceStatus retryStatus,
            RuntimeException failure
    ) {
        var code = failure instanceof BusinessException business
                ? safeCode(business.code()) : "REPORT_SCHEDULE_EXECUTION_FAILED";
        var message = failure instanceof BusinessException
                ? safeMessage(failure.getMessage())
                : "Scheduled report execution failed safely";
        if ("REPORT_SCHEDULE_OWNER_FORBIDDEN".equals(code)
                || occurrence.attemptCount() >= occurrence.maxAttempts()) {
            terminalFailure(occurrence, code, message);
            return;
        }
        var retryAt = clock.instant().plusSeconds(
                Math.min(60, Math.max(1, occurrence.attemptCount()) * 5L));
        store.retry(occurrence.id(), occurrence.version(), retryStatus,
                code, message, retryAt, clock.instant());
    }

    private void terminalFailure(
            ReportScheduleStore.Occurrence occurrence,
            String code,
            String message
    ) {
        var delivered = deliver(occurrence, false, message);
        store.fail(occurrence.id(), occurrence.version(), safeCode(code),
                safeMessage(message), delivered, clock.instant());
    }

    private int deliver(
            ReportScheduleStore.Occurrence occurrence,
            boolean succeeded,
            String failureMessage
    ) {
        for (var recipient : occurrence.configuredRecipientMemberIds()) {
            if (activeMembers.lockActiveMember(occurrence.systemId(),
                    occurrence.tenantId(), recipient).isEmpty()) {
                continue;
            }
            var terminal = succeeded ? "SUCCEEDED" : "FAILED";
            var deliveryKey = "report-schedule:" + occurrence.id() + ":"
                    + recipient + ":" + terminal;
            var title = succeeded
                    ? "Scheduled report is ready"
                    : "Scheduled report failed";
            var body = succeeded
                    ? occurrence.scheduleName() + " completed successfully."
                    : occurrence.scheduleName() + " failed: "
                    + safeMessage(failureMessage);
            var messageId = messages.deliver(
                    new IdempotentMemberMessageFacade.Command(
                            deliveryKey, occurrence.systemId(),
                            occurrence.tenantId(), recipient,
                            "REPORT_SCHEDULE", title, body,
                            new AggregateRef("REPORT_SCHEDULE_OCCURRENCE",
                                    Long.toString(occurrence.id())),
                            targetPath(occurrence)));
            store.recordDeliveryIfAbsent(new ReportScheduleStore.Delivery(
                    ids.nextId(), occurrence.systemId(), occurrence.tenantId(),
                    occurrence.id(), recipient, messageId, deliveryKey,
                    clock.instant()));
        }
        return Math.toIntExact(store.countDeliveries(occurrence.systemId(),
                occurrence.tenantId(), occurrence.id()));
    }

    private RuntimeSession owner(
            ReportScheduleStore.Occurrence occurrence
    ) {
        var principal = principals.current(occurrence.systemId(),
                        occurrence.tenantId(), occurrence.ownerMemberId())
                .filter(value -> value.accountId()
                        == occurrence.ownerAccountId())
                .filter(value -> value.permissions().contains(
                        "system.runtime.access"))
                .filter(value -> value.permissions().containsAll(
                        ConfigPermissions.REQUIRED_NOW))
                .orElseThrow(() -> new ReportException(
                        "REPORT_SCHEDULE_OWNER_FORBIDDEN",
                        "Schedule owner no longer has current report access"));
        return new RuntimeSession(principal.accountId(), occurrence.systemId(),
                principal.memberId(), occurrence.tenantId(),
                principal.permissions());
    }

    private static long id(String value) {
        try {
            var result = Long.parseLong(value);
            if (result <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return result;
        } catch (RuntimeException malformed) {
            throw new IllegalStateException(
                    "Scheduled report export id is invalid", malformed);
        }
    }

    private static String safeCode(String value) {
        if (value == null || !value.matches("^[A-Z][A-Z0-9_]{1,63}$")) {
            return "REPORT_SCHEDULE_EXECUTION_FAILED";
        }
        return value;
    }

    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "Scheduled report execution failed safely";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String targetPath(
            ReportScheduleStore.Occurrence occurrence
    ) {
        return "/systems/" + occurrence.systemId() + "/reports?report="
                + URLEncoder.encode(occurrence.reportCode(),
                StandardCharsets.UTF_8) + "&scheduledRun=" + occurrence.id();
    }
}
