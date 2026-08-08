package com.unique.examine.flow.domain;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.APPROVAL_COMMENT_REQUIRED;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.REJECTION_REASON_REQUIRED;

/**
 * Immutable human approval/rejection comment requirements.
 */
public record ApprovalDecisionCommentPolicy(
        boolean approveRequired,
        boolean rejectRequired,
        int minimumLength
) {
    public static final int MAXIMUM_LENGTH = 500;

    public ApprovalDecisionCommentPolicy {
        if (minimumLength < 1 || minimumLength > MAXIMUM_LENGTH) {
            throw new IllegalArgumentException(
                    "Decision comment minimum length must be between 1 and 500"
            );
        }
    }

    public static ApprovalDecisionCommentPolicy defaults() {
        return new ApprovalDecisionCommentPolicy(false, true, 1);
    }

    public String validateApproval(String comment) {
        return validate(
                comment,
                approveRequired,
                APPROVAL_COMMENT_REQUIRED,
                "An approval comment"
        );
    }

    public String validateRejection(String comment) {
        return validate(
                comment,
                rejectRequired,
                REJECTION_REASON_REQUIRED,
                "A rejection reason"
        );
    }

    private String validate(
            String comment,
            boolean required,
            ApprovalDomainException.Code code,
            String label
    ) {
        var normalized = comment == null ? "" : comment.strip();
        var length = normalized.codePointCount(0, normalized.length());
        if (length > MAXIMUM_LENGTH || (required && length < minimumLength)) {
            var requirement = required
                    ? " must contain " + minimumLength + " to 500 Unicode characters"
                    : " may contain at most 500 Unicode characters";
            throw new ApprovalDomainException(code, label + requirement);
        }
        return normalized;
    }
}
