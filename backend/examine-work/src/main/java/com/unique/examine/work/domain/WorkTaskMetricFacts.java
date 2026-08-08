package com.unique.examine.work.domain;

import java.time.LocalDate;
import java.util.List;

/** Owner-side aggregate facts before native drill routes are attached. */
public record WorkTaskMetricFacts(
        long total,
        long completed,
        long open,
        long overdueOpen,
        long dueInRangeOpen,
        long completedInRange,
        List<DayCount> createdByDay,
        List<DayCount> completedByDay,
        List<AssigneeOpen> topAssignees
) {
    public WorkTaskMetricFacts {
        if (total < 0 || completed < 0 || open < 0
                || total != open + completed
                || overdueOpen < 0 || dueInRangeOpen < 0
                || completedInRange < 0 || createdByDay == null
                || completedByDay == null || topAssignees == null
                || topAssignees.size() > 20) {
            throw new IllegalArgumentException(
                    "Work task metric facts are invalid");
        }
        createdByDay = List.copyOf(createdByDay);
        completedByDay = List.copyOf(completedByDay);
        topAssignees = List.copyOf(topAssignees);
    }

    public record DayCount(LocalDate date, long count) {
        public DayCount {
            if (date == null || count < 0) {
                throw new IllegalArgumentException(
                        "Work task daily metric is invalid");
            }
        }
    }

    public record AssigneeOpen(long assigneeMemberId, long openCount) {
        public AssigneeOpen {
            if (assigneeMemberId <= 0 || openCount <= 0) {
                throw new IllegalArgumentException(
                        "Work task assignee metric is invalid");
            }
        }
    }
}
