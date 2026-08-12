package com.unique.examine.flow.domain;

import java.util.Objects;

public final class ApprovalDomainException extends RuntimeException {
    private final Code code;

    public ApprovalDomainException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        DRAFT_NOT_FOUND,
        VERSION_NOT_FOUND,
        INSTANCE_NOT_FOUND,
        VERSION_ALREADY_EXISTS,
        APPROVER_SEQUENCE_INVALID,
        GATEWAY_INVALID,
        APPROVAL_STAGES_GATEWAY_UNSUPPORTED,
        APPROVER_FORBIDDEN,
        REQUESTER_FORBIDDEN,
        INSTANCE_STATE_INVALID,
        APPROVAL_COMMENT_REQUIRED,
        REJECTION_REASON_REQUIRED,
        EVIDENCE_INVALID,
        EVIDENCE_NOT_FOUND,
        COMMENT_TEMPLATE_INVALID,
        COMMENT_TEMPLATE_NOT_FOUND,
        COMPLETION_STEP_INVALID,
        COMPLETION_EXECUTION_INVALID,
        COMPLETION_EXECUTION_NOT_FOUND,
        COMPLETION_LEASE_INVALID,
        COMPLETION_STATE_CONFLICT,
        SUBFLOW_TARGET_INVALID,
        SUBFLOW_CYCLE,
        SUBFLOW_DEPTH_EXCEEDED,
        SUBFLOW_RUN_CONFLICT,
        DELEGATION_RULE_INVALID,
        DELEGATION_FORBIDDEN,
        DELEGATION_CONFLICT,
        DELEGATION_NOT_FOUND,
        DELEGATION_INACTIVE,
        WITHDRAW_REASON_REQUIRED,
        TERMINATE_REASON_REQUIRED,
        URGE_MESSAGE_INVALID,
        COMMENT_BODY_REQUIRED,
        ASSIGNMENT_REQUEST_INVALID,
        RETURN_REQUEST_INVALID,
        CANCEL_CLAIM_REQUEST_INVALID,
        CLAIM_REQUEST_INVALID,
        REDUCE_SIGN_REQUEST_INVALID,
        COPY_TARGET_INVALID,
        COPY_MESSAGE_INVALID,
        COPY_ALREADY_EXISTS,
        PERSISTENCE_CONFLICT
    }
}
