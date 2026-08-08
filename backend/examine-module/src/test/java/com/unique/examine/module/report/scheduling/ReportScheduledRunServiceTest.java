package com.unique.examine.module.report.scheduling;

import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.exporting.ReportExportStore;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportScheduledRunServiceTest {
    @Test
    void authorizesHistoryAndDownloadByImmutableDeliveryFact() {
        var occurrence = succeeded();
        var schedules = (ReportScheduleStore) Proxy.newProxyInstance(
                ReportScheduleStore.class.getClassLoader(),
                new Class<?>[]{ReportScheduleStore.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findDelivered" -> (Long) args[1] == 2L
                            && (Long) args[4] == 101L
                            ? Optional.of(occurrence) : Optional.empty();
                    case "countDelivered" -> (Long) args[3] == 101L
                            ? 1L : 0L;
                    case "pageDelivered" -> (Long) args[3] == 101L
                            ? List.of(occurrence) : List.of();
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                });
        var exports = exportStore();
        var service = new ReportScheduledRunService(schedules, exports);

        assertThat(service.list(session(101), "ops_report", 1, 20).items())
                .extracting(ReportScheduleViews.Run::id).containsExactly("50");
        assertThat(service.result(session(101), "ops_report", 50).content())
                .containsExactly(1, 2, 3);

        assertThatThrownBy(() -> service.detail(
                session(102), "ops_report", 50))
                .isInstanceOfSatisfying(ReportException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("REPORT_SCHEDULE_RUN_NOT_FOUND"));
        assertThatThrownBy(() -> service.result(
                new RuntimeSession(9, 1, 101, 99L,
                        Set.of("system.runtime.access")),
                "ops_report", 50))
                .isInstanceOf(ReportException.class);
    }

    private static RuntimeSession session(long memberId) {
        return new RuntimeSession(9, 1, memberId, 2L,
                Set.of("system.runtime.access"));
    }

    private static ReportScheduleStore.Occurrence succeeded() {
        var now = Instant.parse("2026-08-04T01:00:00Z");
        return new ReportScheduleStore.Occurrence(
                50, 1, 2, 10, 1, "daily_ops", "Daily operations",
                3, "ops_report", 9, 8, List.of(101L),
                now.minusSeconds(60), "schedule:10:123",
                ReportScheduleStore.OccurrenceStatus.SUCCEEDED, 1, 3,
                900L, "SUCCEEDED", "ops.xlsx", 3L, 1L, 1, false, 1,
                null, null, null, null, "request-50", "trace-50",
                now.minusSeconds(30), now, now.minusSeconds(60), now, 3);
    }

    private static ReportExportStore exportStore() {
        var now = LocalDateTime.parse("2026-08-04T01:00:00");
        var run = new ReportExportStore.Run(
                900, 1, 2, 3, "ops_report", "Operations", 20, 1,
                new ReportSourcePin(30, "ops_source", "Operations source",
                        40, 1, 50, "work_order", "schema-1", List.of(
                        new ReportFieldPin(101, "status", "Status",
                                "STATUS", "STATUS"))),
                "a".repeat(64), 70, 9, 8,
                ReportExportStore.Status.SUCCEEDED, 1L, 1, false,
                "ops.xlsx", 3L, null, null, "request-50", "trace-50",
                now.minusSeconds(30), now, now.minusMinutes(1), now, 2);
        return (ReportExportStore) Proxy.newProxyInstance(
                ReportExportStore.class.getClassLoader(),
                new Class<?>[]{ReportExportStore.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> (Long) args[2] == 900L
                            && (Long) args[0] == 1L && (Long) args[1] == 2L
                            ? Optional.of(run) : Optional.empty();
                    case "result" -> new byte[]{1, 2, 3};
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                });
    }
}
