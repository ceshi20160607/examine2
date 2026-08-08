package com.unique.examine.work.domain;

import java.time.Instant;
import java.util.Objects;

public record WorkProject(
        long id,
        long systemId,
        long tenantId,
        long creatorMemberId,
        String title,
        String description,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static final int MAX_TITLE_CHARACTERS = 200;
    public static final int MAX_DESCRIPTION_CHARACTERS = 2_000;

    public WorkProject {
        if (id <= 0 || systemId <= 0 || tenantId <= 0
                || creatorMemberId <= 0) {
            throw invalid("WORK_PROJECT_IDENTITY_INVALID",
                    "Project identity and scope values must be positive");
        }
        title = text(title, "title", MAX_TITLE_CHARACTERS, false);
        description = text(
                description, "description", MAX_DESCRIPTION_CHARACTERS, true);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt) || version <= 0) {
            throw invalid("WORK_PROJECT_STATE_INVALID",
                    "Project timestamps and version are invalid");
        }
    }

    public WorkProject revise(
            String nextTitle, String nextDescription, Instant now
    ) {
        return copy(nextTitle, nextDescription, status, now);
    }

    public WorkProject archive(Instant now) {
        if (status != Status.ACTIVE) {
            throw invalid("WORK_PROJECT_STATE_INVALID",
                    "Only an active project can be archived");
        }
        return copy(title, description, Status.ARCHIVED, now);
    }

    public WorkProject reopen(Instant now) {
        if (status != Status.ARCHIVED) {
            throw invalid("WORK_PROJECT_STATE_INVALID",
                    "Only an archived project can be reopened");
        }
        return copy(title, description, Status.ACTIVE, now);
    }

    private WorkProject copy(
            String nextTitle,
            String nextDescription,
            Status nextStatus,
            Instant now
    ) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) {
            throw invalid("WORK_PROJECT_STATE_INVALID",
                    "Project update time cannot move backwards");
        }
        return new WorkProject(
                id, systemId, tenantId, creatorMemberId,
                nextTitle, nextDescription, nextStatus,
                createdAt, now, version + 1);
    }

    static String text(
            String value, String label, int maxCharacters, boolean nullable
    ) {
        if (value == null) {
            if (nullable) {
                return null;
            }
            throw invalid("WORK_PROJECT_METADATA_INVALID",
                    label + " is required");
        }
        value = value.strip();
        if (nullable && value.isEmpty()) {
            return null;
        }
        var count = value.codePointCount(0, value.length());
        if (count < 1 || count > maxCharacters) {
            throw invalid("WORK_PROJECT_METADATA_INVALID",
                    label + " must contain 1 to " + maxCharacters + " characters");
        }
        return value;
    }

    private static WorkDomainException invalid(String code, String message) {
        return new WorkDomainException(code, message);
    }

    public enum Status {
        ACTIVE,
        ARCHIVED
    }
}
