package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Tenant-scoped template head with its current immutable version projection.
 */
public record ApprovalDecisionCommentTemplate(
        long id,
        String name,
        String body,
        int currentVersion,
        Status status,
        long createdBy,
        Instant createdAt,
        long updatedBy,
        Instant updatedAt
) {
    public ApprovalDecisionCommentTemplate {
        if (id <= 0 || currentVersion < 1 || createdBy <= 0 || updatedBy <= 0) {
            throw new IllegalArgumentException(
                    "Comment template identity must be positive");
        }
        name = ApprovalDecisionCommentTemplateVersion.bounded(
                name, "Comment template name", 80);
        body = ApprovalDecisionCommentTemplateVersion.bounded(
                body, "Comment template body", 1000);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Comment template update precedes creation");
        }
    }

    public ApprovalDecisionCommentTemplateVersion currentVersionSnapshot() {
        return new ApprovalDecisionCommentTemplateVersion(
                id, currentVersion, name, body, updatedBy, updatedAt);
    }

    public ApprovalDecisionCommentTemplate revise(
            String nextName,
            String nextBody,
            long actorId,
            Instant occurredAt
    ) {
        return new ApprovalDecisionCommentTemplate(
                id, nextName, nextBody, currentVersion + 1, status,
                createdBy, createdAt, actorId, occurredAt);
    }

    public ApprovalDecisionCommentTemplate withStatus(
            Status nextStatus,
            long actorId,
            Instant occurredAt
    ) {
        return new ApprovalDecisionCommentTemplate(
                id, name, body, currentVersion, nextStatus,
                createdBy, createdAt, actorId, occurredAt);
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }
}
