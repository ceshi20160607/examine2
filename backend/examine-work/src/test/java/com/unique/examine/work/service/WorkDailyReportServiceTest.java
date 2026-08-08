package com.unique.examine.work.service;

import com.unique.examine.work.adapter.memory.InMemoryWorkDailyReportRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDailyReport;
import com.unique.examine.work.domain.WorkDailyReportQuery;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.port.WorkMemberDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkDailyReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);
    private WorkDailyReportService service;

    @BeforeEach
    void setUp() {
        WorkMemberDirectory members = (systemId, tenantId, memberId) ->
                systemId == 10 && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId);
        service = new WorkDailyReportService(
                new InMemoryWorkDailyReportRepository(members), members,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void authorCreatesRevisesSubmitsAndReplaysWithoutDuplicatingState() {
        var author = author(100);
        var report = service.create(
                author, TODAY, "Completed", "Planned", null);
        assertThat(report.status()).isEqualTo(WorkDailyReport.Status.DRAFT);
        assertThat(report.version()).isEqualTo(1L);

        report = service.update(
                author, report.id(), "Completed 2", "Planned 2",
                "Waiting", report.version());
        assertThat(report.completedWork()).isEqualTo("Completed 2");
        assertThat(report.blockers()).isEqualTo("Waiting");

        var submitted = service.submit(
                author, report.id(), report.version());
        assertThat(submitted.status())
                .isEqualTo(WorkDailyReport.Status.SUBMITTED);
        assertThat(submitted.version()).isEqualTo(3L);
        assertThat(service.submit(author, report.id(), report.version()))
                .isEqualTo(submitted);
        assertThat(service.submit(
                author, report.id(), submitted.version()))
                .isEqualTo(submitted);

        assertThatThrownBy(() -> service.update(
                author, submitted.id(), "Changed", "Changed", null,
                submitted.version()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_STATE_INVALID"));
        assertThatThrownBy(() -> service.submit(
                author, submitted.id(), 1L))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_VERSION_CONFLICT"));
    }

    @Test
    void managerCanReadListAndIdempotentlyReopenButOthersAreHidden() {
        var created = service.create(
                author(100), TODAY, "Completed", "Planned", null);
        var submitted = service.submit(
                author(100), created.id(), created.version());

        assertThatThrownBy(() -> service.get(reader(101), created.id()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_NOT_FOUND"));
        assertThatThrownBy(() -> service.get(
                new WorkActor(10, 21, 100,
                        Set.of(WorkDailyReportService.ACCESS)),
                created.id()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_NOT_FOUND"));

        var manager = manager(102);
        assertThat(service.get(manager, created.id())).isEqualTo(submitted);
        var page = service.page(
                manager, WorkDailyReportQuery.Scope.ALL, 100L,
                TODAY.minusDays(1), TODAY,
                WorkDailyReportQuery.StatusFilter.SUBMITTED, 1, 20);
        assertThat(page.items()).extracting(WorkDailyReport::id)
                .containsExactly(created.id());

        var reopened = service.reopen(
                manager, created.id(), submitted.version());
        assertThat(reopened.status()).isEqualTo(WorkDailyReport.Status.DRAFT);
        assertThat(reopened.version()).isEqualTo(submitted.version() + 1);
        assertThat(service.reopen(
                manager, created.id(), submitted.version()))
                .isEqualTo(reopened);
        assertThat(service.reopen(
                manager, created.id(), reopened.version()))
                .isEqualTo(reopened);
    }

    @Test
    void permissionsFutureDatesDuplicatesAndTeamFiltersFailClosed() {
        assertThatThrownBy(() -> service.create(
                reader(100), TODAY, "Completed", "Planned", null))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_FORBIDDEN"));
        assertThatThrownBy(() -> service.create(
                author(100), TODAY.plusDays(1),
                "Completed", "Planned", null))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_DATE_INVALID"));
        service.create(author(100), TODAY, "Completed", "Planned", null);
        assertThatThrownBy(() -> service.create(
                author(100), TODAY, "Again", "Again", null))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_STATE_INVALID"));

        assertThatThrownBy(() -> service.page(
                reader(100), WorkDailyReportQuery.Scope.ALL, null,
                TODAY.minusDays(1), TODAY,
                WorkDailyReportQuery.StatusFilter.ALL, 1, 20))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_FORBIDDEN"));
        assertThatThrownBy(() -> service.page(
                manager(102), WorkDailyReportQuery.Scope.ALL, 999L,
                TODAY.minusDays(1), TODAY,
                WorkDailyReportQuery.StatusFilter.ALL, 1, 20))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_MEMBER_INVALID"));
    }

    @Test
    void sevenDaySummaryDerivesSubmittedDraftAndMissingDates() {
        var author = author(100);
        var submitted = service.create(
                author, TODAY.minusDays(1), "Done", "Next", null);
        service.submit(author, submitted.id(), submitted.version());
        service.create(author, TODAY, "Today", "Tomorrow", "Blocked");

        var summary = service.summary(author, TODAY, null);

        assertThat(summary.memberId()).isEqualTo(100L);
        assertThat(summary.dateFrom()).isEqualTo(TODAY.minusDays(6));
        assertThat(summary.dateTo()).isEqualTo(TODAY);
        assertThat(summary.submittedCount()).isEqualTo(1);
        assertThat(summary.draftCount()).isEqualTo(1);
        assertThat(summary.missingCount()).isEqualTo(5);
        assertThat(summary.days()).hasSize(7);
        assertThat(summary.days().get(5).state())
                .isEqualTo(WorkDailyReportService.SummaryState.SUBMITTED);
        assertThat(summary.days().get(6).state())
                .isEqualTo(WorkDailyReportService.SummaryState.DRAFT);

        assertThatThrownBy(() -> service.summary(
                reader(101), TODAY, 100L))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_NOT_FOUND"));
        assertThat(service.summary(manager(102), TODAY, 100L)
                .submittedCount()).isEqualTo(1);
    }

    private static WorkActor author(long memberId) {
        return new WorkActor(
                10, 20, memberId,
                Set.of(WorkDailyReportService.ACCESS,
                        WorkDailyReportService.CREATE));
    }

    private static WorkActor reader(long memberId) {
        return new WorkActor(
                10, 20, memberId,
                Set.of(WorkDailyReportService.ACCESS));
    }

    private static WorkActor manager(long memberId) {
        return new WorkActor(
                10, 20, memberId,
                Set.of(WorkDailyReportService.ACCESS,
                        WorkDailyReportService.MANAGE));
    }
}
