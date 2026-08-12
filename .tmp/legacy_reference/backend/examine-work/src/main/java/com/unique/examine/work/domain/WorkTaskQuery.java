package com.unique.examine.work.domain;

public record WorkTaskQuery(
        String keyword,
        StatusFilter status,
        RoleFilter role,
        int page,
        int size,
        Long projectId,
        java.time.Instant dueFrom,
        java.time.Instant dueTo,
        java.time.Instant reminderFrom,
        java.time.Instant reminderTo,
        java.time.Instant dueBefore,
        java.time.Instant createdFrom,
        java.time.Instant createdBefore,
        java.time.Instant updatedFrom,
        java.time.Instant updatedBefore,
        Long assigneeMemberId
) {
    public static final int MAX_KEYWORD_CHARACTERS = 100;
    public static final int MAX_SIZE = 100;

    public WorkTaskQuery(
            String keyword,
            StatusFilter status,
            RoleFilter role,
            int page,
            int size
    ) {
        this(keyword, status, role, page, size,
                null, null, null, null, null,
                null, null, null, null, null, null);
    }

    public WorkTaskQuery(
            String keyword,
            StatusFilter status,
            RoleFilter role,
            int page,
            int size,
            Long projectId,
            java.time.Instant dueFrom,
            java.time.Instant dueTo
    ) {
        this(keyword, status, role, page, size,
                projectId, dueFrom, dueTo, null, null,
                null, null, null, null, null, null);
    }

    public WorkTaskQuery(
            String keyword,
            StatusFilter status,
            RoleFilter role,
            int page,
            int size,
            Long projectId,
            java.time.Instant dueFrom,
            java.time.Instant dueTo,
            java.time.Instant reminderFrom,
            java.time.Instant reminderTo
    ) {
        this(keyword, status, role, page, size,
                projectId, dueFrom, dueTo, reminderFrom, reminderTo,
                null, null, null, null, null, null);
    }

    public WorkTaskQuery {
        keyword = keyword == null ? "" : keyword.trim();
        if (keyword.codePointCount(0, keyword.length()) > MAX_KEYWORD_CHARACTERS) {
            throw invalid(
                    "WORK_TASK_KEYWORD_INVALID",
                    "keyword cannot exceed " + MAX_KEYWORD_CHARACTERS + " characters");
        }
        if (status == null) {
            throw invalid("WORK_TASK_STATUS_INVALID", "status is required");
        }
        if (role == null) {
            throw invalid("WORK_TASK_ROLE_INVALID", "role is required");
        }
        if (page < 1) {
            throw invalid("WORK_TASK_PAGE_INVALID", "page must be at least 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw invalid("WORK_TASK_SIZE_INVALID", "size must be within 1.." + MAX_SIZE);
        }
        if (projectId != null && projectId <= 0) {
            throw invalid("WORK_TASK_PROJECT_INVALID",
                    "projectId must be positive");
        }
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw invalid("WORK_TASK_DUE_WINDOW_INVALID",
                    "dueFrom cannot be after dueTo");
        }
        if (reminderFrom != null && reminderTo != null
                && reminderFrom.isAfter(reminderTo)) {
            throw invalid("WORK_TASK_REMINDER_WINDOW_INVALID",
                    "reminderFrom cannot be after reminderTo");
        }
        if (dueFrom != null && dueBefore != null
                && !dueFrom.isBefore(dueBefore)) {
            throw invalid("WORK_TASK_DUE_WINDOW_INVALID",
                    "dueFrom must be before dueBefore");
        }
        requireExclusiveWindow(
                createdFrom, createdBefore,
                "WORK_TASK_CREATED_WINDOW_INVALID",
                "createdFrom must be before createdBefore");
        requireExclusiveWindow(
                updatedFrom, updatedBefore,
                "WORK_TASK_UPDATED_WINDOW_INVALID",
                "updatedFrom must be before updatedBefore");
        if (assigneeMemberId != null && assigneeMemberId <= 0) {
            throw invalid("WORK_TASK_ASSIGNEE_INVALID",
                    "assigneeMemberId must be positive");
        }
    }

    public long offset() {
        return Math.multiplyExact((long) page - 1, size);
    }

    public enum StatusFilter {
        ALL,
        OPEN,
        COMPLETED
    }

    public enum RoleFilter {
        PARTICIPATING,
        CREATED_BY_ME,
        ASSIGNED_TO_ME,
        ALL
    }

    private static WorkDomainException invalid(String code, String message) {
        return new WorkDomainException(code, message);
    }

    private static void requireExclusiveWindow(
            java.time.Instant from,
            java.time.Instant before,
            String code,
            String message
    ) {
        if (from != null && before != null && !from.isBefore(before)) {
            throw invalid(code, message);
        }
    }
}
