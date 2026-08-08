package com.unique.examine.work.adapter.memory;

import com.unique.examine.work.domain.WorkDailyReport;
import com.unique.examine.work.domain.WorkDailyReportPage;
import com.unique.examine.work.domain.WorkDailyReportQuery;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.port.WorkDailyReportRepository;
import com.unique.examine.work.port.WorkMemberDirectory;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryWorkDailyReportRepository
        implements WorkDailyReportRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final Map<ReportKey, WorkDailyReport> reports =
            new ConcurrentHashMap<>();
    private final Map<AuthorDateKey, ReportKey> byAuthorDate =
            new ConcurrentHashMap<>();
    private final WorkMemberDirectory members;

    public InMemoryWorkDailyReportRepository() {
        this((systemId, tenantId, memberId) -> true);
    }

    public InMemoryWorkDailyReportRepository(WorkMemberDirectory members) {
        if (members == null) {
            throw new IllegalArgumentException("WorkMemberDirectory is required");
        }
        this.members = members;
    }

    @Override
    public long nextId() {
        return sequence.incrementAndGet();
    }

    @Override
    public Optional<WorkDailyReport> findById(
            long systemId, long tenantId, long reportId
    ) {
        return Optional.ofNullable(reports.get(
                new ReportKey(systemId, tenantId, reportId)));
    }

    @Override
    public Optional<WorkDailyReport> findByAuthorAndDate(
            long systemId,
            long tenantId,
            long authorMemberId,
            LocalDate workDate
    ) {
        var reportKey = byAuthorDate.get(new AuthorDateKey(
                systemId, tenantId, authorMemberId, workDate));
        return Optional.ofNullable(reportKey).map(reports::get);
    }

    @Override
    public WorkDailyReportPage findPage(
            long systemId,
            long tenantId,
            long currentMemberId,
            boolean manager,
            WorkDailyReportQuery query
    ) {
        var values = reports.values().stream()
                .filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId)
                .filter(value -> members.isActiveMember(
                        systemId, tenantId, value.authorMemberId()))
                .filter(value -> visibleAuthor(
                        value, currentMemberId, manager, query))
                .filter(value -> !value.workDate().isBefore(query.dateFrom())
                        && !value.workDate().isAfter(query.dateTo()))
                .filter(value -> query.status()
                        == WorkDailyReportQuery.StatusFilter.ALL
                        || value.status().name().equals(query.status().name()))
                .sorted(order())
                .toList();
        var from = (int) Math.min(query.offset(), values.size());
        var to = Math.min(from + query.size(), values.size());
        return new WorkDailyReportPage(
                values.subList(from, to), query.page(), query.size(),
                values.size());
    }

    @Override
    public synchronized WorkDailyReport save(WorkDailyReport report) {
        var reportKey = new ReportKey(
                report.systemId(), report.tenantId(), report.id());
        var identity = new AuthorDateKey(
                report.systemId(), report.tenantId(),
                report.authorMemberId(), report.workDate());
        var current = reports.get(reportKey);
        if (current == null) {
            if (report.version() != 1
                    || byAuthorDate.putIfAbsent(identity, reportKey) != null) {
                throw conflict("Daily report already exists or version is stale");
            }
        } else {
            if (current.equals(report)) {
                return current;
            }
            if (report.version() != current.version() + 1
                    || report.authorMemberId() != current.authorMemberId()
                    || !report.workDate().equals(current.workDate())
                    || !report.createdAt().equals(current.createdAt())) {
                throw conflict("Daily report version is stale");
            }
        }
        reports.put(reportKey, report);
        return report;
    }

    private static boolean visibleAuthor(
            WorkDailyReport value,
            long currentMemberId,
            boolean manager,
            WorkDailyReportQuery query
    ) {
        if (!manager || query.scope() == WorkDailyReportQuery.Scope.SELF) {
            return value.authorMemberId() == currentMemberId;
        }
        return query.authorMemberId() == null
                || value.authorMemberId() == query.authorMemberId();
    }

    private static Comparator<WorkDailyReport> order() {
        return Comparator.comparing(WorkDailyReport::workDate).reversed()
                .thenComparing(Comparator.comparing(
                        WorkDailyReport::updatedAt).reversed())
                .thenComparing(Comparator.comparingLong(
                        WorkDailyReport::id).reversed());
    }

    private static WorkDomainException conflict(String message) {
        return new WorkDomainException(
                "WORK_REPORT_VERSION_CONFLICT", message);
    }

    private record ReportKey(long systemId, long tenantId, long reportId) {
    }

    private record AuthorDateKey(
            long systemId,
            long tenantId,
            long authorMemberId,
            LocalDate workDate
    ) {
    }
}
