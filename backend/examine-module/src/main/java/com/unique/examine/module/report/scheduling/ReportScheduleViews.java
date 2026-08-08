package com.unique.examine.module.report.scheduling;

import java.util.List;

public final class ReportScheduleViews {
    private ReportScheduleViews() {
    }

    public record Cadence(
            String kind,
            String localTime,
            List<String> daysOfWeek
    ) {
        public Cadence {
            daysOfWeek = daysOfWeek == null
                    ? List.of() : List.copyOf(daysOfWeek);
        }
    }

    public record CreateRequest(
            String code,
            String name,
            boolean enabled,
            String timeZone,
            Cadence cadence,
            List<String> recipientMemberIds
    ) {
        public CreateRequest {
            recipientMemberIds = recipientMemberIds == null
                    ? List.of() : List.copyOf(recipientMemberIds);
        }
    }

    public record UpdateRequest(
            long expectedVersion,
            String name,
            String timeZone,
            Cadence cadence,
            List<String> recipientMemberIds
    ) {
        public UpdateRequest {
            recipientMemberIds = recipientMemberIds == null
                    ? List.of() : List.copyOf(recipientMemberIds);
        }
    }

    public record ToggleRequest(long expectedVersion) {
    }

    public record PreviewRequest(
            String timeZone,
            Cadence cadence,
            String from
    ) {
    }

    public record Preview(
            String nextFireAt,
            String localDateTime,
            String offset,
            String timeZone
    ) {
    }

    public record Schedule(
            String id,
            String reportId,
            String code,
            String name,
            boolean enabled,
            String timeZone,
            Cadence cadence,
            List<String> recipientMemberIds,
            String ownerMemberId,
            String nextFireAt,
            long version,
            String createdAt,
            String updatedAt
    ) {
        public Schedule {
            recipientMemberIds = List.copyOf(recipientMemberIds);
        }
    }

    public record SchedulePage(
            List<Schedule> items,
            int page,
            int size,
            long total
    ) {
        public SchedulePage {
            items = List.copyOf(items);
        }
    }

    public record Run(
            String id,
            String scheduleId,
            String scheduleCode,
            String scheduleName,
            String reportCode,
            String scheduledAt,
            String status,
            String exportId,
            String filename,
            Long totalRows,
            int processedRows,
            boolean truncated,
            String failureCode,
            String failureMessage,
            String createdAt,
            String finishedAt
    ) {
    }

    public record RunPage(
            List<Run> items,
            int page,
            int size,
            long total
    ) {
        public RunPage {
            items = List.copyOf(items);
        }
    }
}
