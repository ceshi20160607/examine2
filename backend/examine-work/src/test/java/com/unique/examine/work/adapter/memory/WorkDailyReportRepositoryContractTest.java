package com.unique.examine.work.adapter.memory;

import com.unique.examine.work.domain.WorkDailyReport;
import com.unique.examine.work.domain.WorkDailyReportQuery;
import com.unique.examine.work.domain.WorkDomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkDailyReportRepositoryContractTest {
    private static final LocalDate DATE = LocalDate.parse("2026-07-31");
    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void enforcesOnePerAuthorDateTenantIsolationAndCas() {
        var repository = new InMemoryWorkDailyReportRepository();
        var report = report(1L, 20L, 100L, DATE, NOW);
        repository.save(report);

        assertThat(repository.findByAuthorAndDate(
                10L, 20L, 100L, DATE)).contains(report);
        assertThat(repository.findByAuthorAndDate(
                10L, 21L, 100L, DATE)).isEmpty();
        assertThatThrownBy(() -> repository.save(
                report(2L, 20L, 100L, DATE, NOW)))
                .isInstanceOf(WorkDomainException.class)
                .hasMessageContaining("already exists");

        var revised = report.revise(
                "More", "Next", null, DATE, NOW.plusSeconds(1));
        repository.save(revised);
        assertThatThrownBy(() -> repository.save(report.revise(
                "Stale", "Next", null, DATE, NOW.plusSeconds(2))))
                .isInstanceOf(WorkDomainException.class)
                .hasMessageContaining("stale");
        assertThat(repository.save(revised)).isEqualTo(revised);
    }

    @Test
    void pagesStableFactsAndNeverWidensToInactiveAuthors() {
        var repository = new InMemoryWorkDailyReportRepository(
                (systemId, tenantId, memberId) -> memberId != 300L);
        repository.save(report(1L, 20L, 100L, DATE.minusDays(1), NOW));
        repository.save(report(2L, 20L, 200L, DATE, NOW));
        repository.save(report(3L, 20L, 201L, DATE,
                NOW.plusSeconds(1)));
        repository.save(report(4L, 20L, 300L, DATE,
                NOW.plusSeconds(2)));
        repository.save(report(5L, 21L, 200L, DATE,
                NOW.plusSeconds(3)));

        var manager = repository.findPage(
                10L, 20L, 999L, true,
                query(WorkDailyReportQuery.Scope.ALL, null));
        var filtered = repository.findPage(
                10L, 20L, 999L, true,
                query(WorkDailyReportQuery.Scope.ALL, 100L));
        var self = repository.findPage(
                10L, 20L, 200L, false,
                query(WorkDailyReportQuery.Scope.ALL, null));

        assertThat(manager.items()).extracting(WorkDailyReport::id)
                .containsExactly(3L, 2L, 1L);
        assertThat(filtered.items()).extracting(WorkDailyReport::id)
                .containsExactly(1L);
        assertThat(self.items()).extracting(WorkDailyReport::id)
                .containsExactly(2L);
    }

    private static WorkDailyReportQuery query(
            WorkDailyReportQuery.Scope scope, Long authorMemberId
    ) {
        return new WorkDailyReportQuery(
                scope, authorMemberId, DATE.minusDays(6), DATE,
                WorkDailyReportQuery.StatusFilter.ALL, 1, 20);
    }

    private static WorkDailyReport report(
            long id,
            long tenantId,
            long author,
            LocalDate date,
            Instant now
    ) {
        return WorkDailyReport.draft(
                id, 10L, tenantId, author, date,
                "Done " + id, "Next " + id, null, DATE, now);
    }
}
