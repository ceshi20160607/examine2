package com.unique.examine.work.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkDailyReportTest {
    private static final LocalDate BUSINESS_DATE =
            LocalDate.parse("2026-07-31");
    private static final Instant NOW =
            Instant.parse("2026-07-31T09:00:00Z");

    @Test
    void validatesEachNarrativeAndRejectsFutureWorkDate() {
        assertThatThrownBy(() -> WorkDailyReport.draft(
                1L, 10L, 20L, 100L, BUSINESS_DATE.plusDays(1),
                "Done", "Next", null, BUSINESS_DATE, NOW))
                .isInstanceOf(WorkDomainException.class)
                .hasMessageContaining("future");
        assertThatThrownBy(() -> WorkDailyReport.draft(
                1L, 10L, 20L, 100L, BUSINESS_DATE,
                "x".repeat(4_001), "Next", null, BUSINESS_DATE, NOW))
                .isInstanceOf(WorkDomainException.class);
        assertThatThrownBy(() -> WorkDailyReport.draft(
                1L, 10L, 20L, 100L, BUSINESS_DATE,
                "Done", "Next", "x".repeat(4_001), BUSINESS_DATE, NOW))
                .isInstanceOf(WorkDomainException.class);
    }

    @Test
    void revisesOnlyDraftAndSubmitReopenAreTargetStateIdempotent() {
        var draft = report();
        var revised = draft.revise(
                "Done more", "Next more", "Waiting",
                BUSINESS_DATE, NOW.plusSeconds(1));
        var submitted = revised.submit(NOW.plusSeconds(2));
        var reopened = submitted.reopen(NOW.plusSeconds(3));

        assertThat(submitted.submit(NOW.plusSeconds(4))).isSameAs(submitted);
        assertThat(reopened.reopen(NOW.plusSeconds(4))).isSameAs(reopened);
        assertThat(submitted.status())
                .isEqualTo(WorkDailyReport.Status.SUBMITTED);
        assertThat(submitted.submittedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(reopened.status()).isEqualTo(WorkDailyReport.Status.DRAFT);
        assertThat(reopened.completedWork()).isEqualTo("Done more");
        assertThat(reopened.submittedAt()).isNull();
        assertThatThrownBy(() -> submitted.revise(
                "Changed", "Next", null, BUSINESS_DATE,
                NOW.plusSeconds(3)))
                .isInstanceOf(WorkDomainException.class)
                .hasMessageContaining("draft");
    }

    @Test
    void queryRequiresAnOrderedBoundedInclusiveWindow() {
        assertThatThrownBy(() -> new WorkDailyReportQuery(
                WorkDailyReportQuery.Scope.SELF, null,
                BUSINESS_DATE, BUSINESS_DATE.minusDays(1),
                WorkDailyReportQuery.StatusFilter.ALL, 1, 20))
                .isInstanceOf(WorkDomainException.class);
        assertThatThrownBy(() -> new WorkDailyReportQuery(
                WorkDailyReportQuery.Scope.ALL, null,
                BUSINESS_DATE.minusDays(366), BUSINESS_DATE,
                WorkDailyReportQuery.StatusFilter.ALL, 1, 20))
                .isInstanceOf(WorkDomainException.class);
    }

    private static WorkDailyReport report() {
        return WorkDailyReport.draft(
                1L, 10L, 20L, 100L, BUSINESS_DATE,
                "Done", "Next", null, BUSINESS_DATE, NOW);
    }
}
