package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable version of a reusable decision-comment template.
 */
public record ApprovalDecisionCommentTemplateVersion(
        long templateId,
        int version,
        String name,
        String body,
        long createdBy,
        Instant createdAt
) {
    public ApprovalDecisionCommentTemplateVersion {
        if (templateId <= 0 || version < 1 || createdBy <= 0) {
            throw new IllegalArgumentException(
                    "Comment template version identity must be positive");
        }
        name = bounded(name, "Comment template name", 80);
        body = bounded(body, "Comment template body", 1000);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    static String bounded(String value, String label, int maximum) {
        if (value == null || value.isBlank()) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.COMMENT_TEMPLATE_INVALID,
                    label + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.COMMENT_TEMPLATE_INVALID,
                    label + " accepts at most " + maximum + " characters");
        }
        return value;
    }
}
