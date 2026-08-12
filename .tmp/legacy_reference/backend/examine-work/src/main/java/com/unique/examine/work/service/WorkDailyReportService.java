package com.unique.examine.work.service;

import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDailyReport;
import com.unique.examine.work.domain.WorkDailyReportPage;
import com.unique.examine.work.domain.WorkDailyReportQuery;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.port.WorkDailyReportRepository;
import com.unique.examine.work.port.WorkMemberDirectory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Transactional
public class WorkDailyReportService {
    public static final String ACCESS = "work.report.access";
    public static final String CREATE = "work.report.create";
    public static final String MANAGE = "work.report.manage";

    private final WorkDailyReportRepository repository;
    private final WorkMemberDirectory members;
    private final Clock clock;

    public WorkDailyReportService(
            WorkDailyReportRepository repository,
            WorkMemberDirectory members,
            Clock clock
    ) {
        this.repository = required(repository, "repository");
        this.members = required(members, "member directory");
        this.clock = required(clock, "clock");
    }

    public WorkDailyReport create(
            WorkActor actor,
            LocalDate workDate,
            String completedWork,
            String plannedWork,
            String blockers
    ) {
        require(actor, CREATE);
        if (repository.findByAuthorAndDate(
                actor.systemId(), actor.tenantId(), actor.memberId(),
                workDate).isPresent()) {
            throw error("WORK_REPORT_STATE_INVALID",
                    "A report already exists for this member and work date");
        }
        return repository.save(WorkDailyReport.draft(
                repository.nextId(), actor.systemId(), actor.tenantId(),
                actor.memberId(), workDate, completedWork, plannedWork,
                blockers, businessDate(), now()));
    }

    @Transactional(readOnly = true)
    public WorkDailyReport get(WorkActor actor, long reportId) {
        require(actor, ACCESS);
        return visible(actor, reportId);
    }

    public WorkDailyReport update(
            WorkActor actor,
            long reportId,
            String completedWork,
            String plannedWork,
            String blockers,
            long expectedVersion
    ) {
        require(actor, ACCESS);
        var report = authored(actor, reportId);
        requireVersion(report.version(), expectedVersion);
        return repository.save(report.revise(
                completedWork, plannedWork, blockers,
                businessDate(), now()));
    }

    public WorkDailyReport submit(
            WorkActor actor, long reportId, long expectedVersion
    ) {
        require(actor, ACCESS);
        var report = authored(actor, reportId);
        if (report.status() == WorkDailyReport.Status.SUBMITTED) {
            requireReplayVersion(report.version(), expectedVersion);
            return report;
        }
        requireVersion(report.version(), expectedVersion);
        return repository.save(report.submit(now()));
    }

    public WorkDailyReport reopen(
            WorkActor actor, long reportId, long expectedVersion
    ) {
        require(actor, MANAGE);
        var report = visible(actor, reportId);
        if (report.status() == WorkDailyReport.Status.DRAFT) {
            requireReplayVersion(report.version(), expectedVersion);
            return report;
        }
        requireVersion(report.version(), expectedVersion);
        return repository.save(report.reopen(now()));
    }

    @Transactional(readOnly = true)
    public WorkDailyReportPage page(
            WorkActor actor,
            WorkDailyReportQuery.Scope scope,
            Long memberId,
            LocalDate dateFrom,
            LocalDate dateTo,
            WorkDailyReportQuery.StatusFilter status,
            int page,
            int size
    ) {
        require(actor, ACCESS);
        var queryScope = scope == null
                ? WorkDailyReportQuery.Scope.SELF : scope;
        var end = dateTo == null ? businessDate() : dateTo;
        var start = dateFrom == null ? end.minusDays(29) : dateFrom;
        var filterStatus = status == null
                ? WorkDailyReportQuery.StatusFilter.ALL : status;
        validateQueryVisibility(actor, queryScope, memberId);
        var query = new WorkDailyReportQuery(
                queryScope, memberId, start, end,
                filterStatus, page, size);
        return repository.findPage(
                actor.systemId(), actor.tenantId(), actor.memberId(),
                actor.has(MANAGE), query);
    }

    @Transactional(readOnly = true)
    public Summary summary(
            WorkActor actor, LocalDate requestedEndDate, Long memberId
    ) {
        require(actor, ACCESS);
        var targetMemberId = memberId == null
                ? actor.memberId() : memberId;
        if (targetMemberId != actor.memberId()) {
            if (!actor.has(MANAGE)) {
                throw notFound();
            }
            requireActiveMember(actor, targetMemberId);
        }
        var end = requestedEndDate == null
                ? businessDate() : requestedEndDate;
        if (end.isAfter(businessDate())) {
            throw error("WORK_REPORT_DATE_INVALID",
                    "Summary endDate cannot be in the future");
        }
        var start = end.minusDays(6);
        var scope = targetMemberId == actor.memberId()
                ? WorkDailyReportQuery.Scope.SELF
                : WorkDailyReportQuery.Scope.ALL;
        var reports = repository.findPage(
                        actor.systemId(), actor.tenantId(), actor.memberId(),
                        actor.has(MANAGE),
                        new WorkDailyReportQuery(
                                scope,
                                scope == WorkDailyReportQuery.Scope.ALL
                                        ? targetMemberId : null,
                                start, end,
                                WorkDailyReportQuery.StatusFilter.ALL,
                                1, 7))
                .items();
        Map<LocalDate, WorkDailyReport> byDate = reports.stream()
                .collect(Collectors.toMap(
                        WorkDailyReport::workDate, Function.identity()));
        var days = new ArrayList<SummaryDay>(7);
        var submitted = 0;
        var draft = 0;
        for (var date = start; !date.isAfter(end); date = date.plusDays(1)) {
            var report = byDate.get(date);
            if (report == null) {
                days.add(new SummaryDay(
                        date, SummaryState.MISSING, null, null));
            } else if (report.status()
                    == WorkDailyReport.Status.SUBMITTED) {
                days.add(new SummaryDay(
                        date, SummaryState.SUBMITTED,
                        report.id(), report.version()));
                submitted++;
            } else {
                days.add(new SummaryDay(
                        date, SummaryState.DRAFT,
                        report.id(), report.version()));
                draft++;
            }
        }
        return new Summary(
                targetMemberId, start, end,
                submitted, draft, 7 - submitted - draft, days);
    }

    private WorkDailyReport authored(WorkActor actor, long reportId) {
        var report = visible(actor, reportId);
        if (report.authorMemberId() != actor.memberId()) {
            throw notFound();
        }
        return report;
    }

    private WorkDailyReport visible(WorkActor actor, long reportId) {
        var report = repository.findById(
                        actor.systemId(), actor.tenantId(), reportId)
                .orElseThrow(WorkDailyReportService::notFound);
        if (report.authorMemberId() != actor.memberId()
                && !actor.has(MANAGE)) {
            throw notFound();
        }
        return report;
    }

    private void validateQueryVisibility(
            WorkActor actor,
            WorkDailyReportQuery.Scope scope,
            Long memberId
    ) {
        if (scope == WorkDailyReportQuery.Scope.ALL) {
            if (!actor.has(MANAGE)) {
                throw error("WORK_REPORT_FORBIDDEN",
                        "ALL report scope requires " + MANAGE);
            }
            if (memberId != null) {
                requireActiveMember(actor, memberId);
            }
            return;
        }
        if (memberId != null && memberId != actor.memberId()) {
            throw notFound();
        }
    }

    private void requireActiveMember(WorkActor actor, long memberId) {
        if (memberId <= 0 || !members.isActiveMember(
                actor.systemId(), actor.tenantId(), memberId)) {
            throw error("WORK_REPORT_MEMBER_INVALID",
                    "Report member filter must identify an active tenant member");
        }
    }

    private LocalDate businessDate() {
        return LocalDate.now(clock);
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static void require(WorkActor actor, String permission) {
        if (actor == null || !actor.has(permission)) {
            throw error("WORK_REPORT_FORBIDDEN",
                    "Missing permission: " + permission);
        }
    }

    private static void requireVersion(long current, long expected) {
        if (expected <= 0 || current != expected) {
            throw error("WORK_REPORT_VERSION_CONFLICT",
                    "Report version is stale");
        }
    }

    private static void requireReplayVersion(long current, long expected) {
        if (expected <= 0
                || expected != current && expected != current - 1) {
            throw error("WORK_REPORT_VERSION_CONFLICT",
                    "Report version is stale");
        }
    }

    private static WorkDomainException notFound() {
        return error("WORK_REPORT_NOT_FOUND", "Report was not found");
    }

    private static WorkDomainException error(String code, String message) {
        return new WorkDomainException(code, message);
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public record Summary(
            long memberId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int submittedCount,
            int draftCount,
            int missingCount,
            List<SummaryDay> days
    ) {
        public Summary {
            days = List.copyOf(days);
        }
    }

    public record SummaryDay(
            LocalDate workDate,
            SummaryState state,
            Long reportId,
            Long version
    ) {
    }

    public enum SummaryState {
        SUBMITTED,
        DRAFT,
        MISSING
    }
}
