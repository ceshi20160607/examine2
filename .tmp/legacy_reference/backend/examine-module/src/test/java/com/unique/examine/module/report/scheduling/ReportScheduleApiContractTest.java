package com.unique.examine.module.report.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportScheduleApiContractTest {
    @Test
    void freezesAdminAndDeliveredRuntimeRoutes() throws Exception {
        assertThat(ReportScheduleAdminController.class
                .getAnnotation(RequestMapping.class).value()).containsExactly(
                "/api/v1/systems/{systemId}/admin/reports/{reportId}/schedules");
        assertThat(ReportScheduledRunController.class
                .getAnnotation(RequestMapping.class).value()).containsExactly(
                "/api/v1/systems/{systemId}/reports/{reportCode}/scheduled-runs");
        assertThat(ReportScheduleAdminController.class.getMethod("preview",
                        long.class, long.class,
                        ReportScheduleViews.PreviewRequest.class,
                        Object.class, jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(PostMapping.class).value()).containsExactly(
                "/next-fire:preview");
        assertThat(ReportScheduledRunController.class.getMethod("result",
                        long.class, String.class, long.class, Object.class)
                .getAnnotation(GetMapping.class).value()).containsExactly(
                "/{occurrenceId}/result.xlsx");
    }

    @Test
    void freezesNestedScheduleAndDeliveredRunJson() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var schedule = mapper.readTree(mapper.writeValueAsString(
                new ReportScheduleViews.Schedule(
                        "10", "20", "daily_ops", "Daily operations", true,
                        "Asia/Shanghai", new ReportScheduleViews.Cadence(
                        "DAILY", "09:00", List.of()), List.of("30"),
                        "40", "2026-08-05T01:00:00Z", 2,
                        "2026-08-04T00:00:00Z", "2026-08-04T00:01:00Z")));
        var run = mapper.readTree(mapper.writeValueAsString(
                new ReportScheduleViews.Run(
                        "50", "10", "daily_ops", "Daily operations",
                        "ops_report", "2026-08-05T01:00:00Z", "SUCCEEDED",
                        "60", "ops.xlsx", 5_001L, 5_000, true,
                        null, null, "2026-08-05T01:00:00Z",
                        "2026-08-05T01:01:00Z")));

        assertThat(schedule.fieldNames()).toIterable().containsExactly(
                "id", "reportId", "code", "name", "enabled", "timeZone",
                "cadence", "recipientMemberIds", "ownerMemberId",
                "nextFireAt", "version", "createdAt", "updatedAt");
        assertThat(schedule.path("cadence").fieldNames()).toIterable()
                .containsExactly("kind", "localTime", "daysOfWeek");
        assertThat(run.fieldNames()).toIterable().containsExactly(
                "id", "scheduleId", "scheduleCode", "scheduleName",
                "reportCode", "scheduledAt", "status", "exportId",
                "filename", "totalRows", "processedRows", "truncated",
                "failureCode", "failureMessage", "createdAt", "finishedAt");
    }
}
