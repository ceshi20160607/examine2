package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.Objects;

public record ApprovalHistoryEvent(
        Type type,
        long actorId,
        ApprovalInstance.Status fromStatus,
        ApprovalInstance.Status toStatus,
        String comment,
        Instant occurredAt,
        Long targetMemberId,
        AssignmentPosition assignmentPosition,
        Integer targetStepIndex,
        Long representedMemberId,
        Long delegationRuleId,
        ApprovalDecisionEvidence evidence
) {
    public ApprovalHistoryEvent(
            Type type,
            long actorId,
            ApprovalInstance.Status fromStatus,
            ApprovalInstance.Status toStatus,
            String comment,
            Instant occurredAt,
            Long targetMemberId,
            AssignmentPosition assignmentPosition,
            Integer targetStepIndex,
            Long representedMemberId,
            Long delegationRuleId
    ) {
        this(
                type, actorId, fromStatus, toStatus, comment, occurredAt,
                targetMemberId, assignmentPosition, targetStepIndex,
                representedMemberId, delegationRuleId, null
        );
    }

    public ApprovalHistoryEvent(
            Type type,
            long actorId,
            ApprovalInstance.Status fromStatus,
            ApprovalInstance.Status toStatus,
            String comment,
            Instant occurredAt,
            Long targetMemberId,
            AssignmentPosition assignmentPosition,
            Integer targetStepIndex
    ) {
        this(
                type, actorId, fromStatus, toStatus, comment, occurredAt,
                targetMemberId, assignmentPosition, targetStepIndex, null, null,
                null
        );
    }

    public ApprovalHistoryEvent {
        Objects.requireNonNull(type, "type");
        if (actorId <= 0) {
            throw new IllegalArgumentException("History actor id must be positive");
        }
        Objects.requireNonNull(toStatus, "toStatus");
        comment = comment == null ? "" : comment.strip();
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (type == Type.SIGN_REMOVED) {
            if (targetMemberId == null
                    || targetMemberId <= 0
                    || assignmentPosition != null
                    || targetStepIndex == null
                    || targetStepIndex < 0) {
                throw new IllegalArgumentException(
                        "Reduce-sign history requires a target member and step index"
                );
            }
        } else if (targetStepIndex != null) {
            throw new IllegalArgumentException(
                    "Only reduce-sign history may contain a target step index"
            );
        } else if (type == Type.TRANSFERRED || type == Type.RETURNED || type == Type.CLAIMED) {
            if (targetMemberId == null || targetMemberId <= 0 || assignmentPosition != null) {
                throw new IllegalArgumentException(
                        "Assignment history requires only a positive target member"
                );
            }
        } else if (type == Type.ADD_SIGNED) {
            if (targetMemberId == null || targetMemberId <= 0 || assignmentPosition == null) {
                throw new IllegalArgumentException("Add-sign history requires a target member and position");
            }
        } else if (targetMemberId != null || assignmentPosition != null) {
            throw new IllegalArgumentException("Decision history cannot contain assignment facts");
        }
        var participantDecision = type == Type.APPROVED || type == Type.REJECTED;
        if (!participantDecision
                && (representedMemberId != null || delegationRuleId != null)) {
            throw new IllegalArgumentException(
                    "Only participant decisions may contain representation audit"
            );
        }
        if (representedMemberId != null && representedMemberId <= 0) {
            throw new IllegalArgumentException(
                    "Represented member id must be positive"
            );
        }
        if ((representedMemberId == null) != (delegationRuleId == null)) {
            throw new IllegalArgumentException(
                    "Representation and delegation rule audit must occur together"
            );
        }
        if (delegationRuleId != null
                && (delegationRuleId <= 0 || representedMemberId == actorId)) {
            throw new IllegalArgumentException(
                    "Delegated decision audit requires a distinct represented member and rule"
            );
        }
        if (evidence != null) {
            if (!participantDecision
                    || evidence.decision()
                    != (type == Type.APPROVED
                    ? ApprovalInstance.Decision.APPROVED
                    : ApprovalInstance.Decision.REJECTED)
                    || evidence.actorId() != actorId
                    || evidence.representedMemberId()
                    != (representedMemberId == null
                    ? actorId
                    : representedMemberId)
                    || !Objects.equals(
                    evidence.delegationRuleId(), delegationRuleId)
                    || !evidence.decidedAt().equals(occurredAt)) {
                throw new IllegalArgumentException(
                        "Decision evidence must mirror its history audit facts");
            }
        }
    }

    public ApprovalHistoryEvent(
            Type type,
            long actorId,
            ApprovalInstance.Status fromStatus,
            ApprovalInstance.Status toStatus,
            String comment,
            Instant occurredAt,
            Long targetMemberId,
            AssignmentPosition assignmentPosition
    ) {
        this(
                type,
                actorId,
                fromStatus,
                toStatus,
                comment,
                occurredAt,
                targetMemberId,
                assignmentPosition,
                null,
                null,
                null,
                null
        );
    }

    public ApprovalHistoryEvent(
            Type type,
            long actorId,
            ApprovalInstance.Status fromStatus,
            ApprovalInstance.Status toStatus,
            String comment,
            Instant occurredAt
    ) {
        this(
                type, actorId, fromStatus, toStatus, comment, occurredAt,
                null, null, null, null, null, null
        );
    }

    public ApprovalHistoryEvent withEvidence(
            ApprovalDecisionEvidence nextEvidence
    ) {
        return new ApprovalHistoryEvent(
                type, actorId, fromStatus, toStatus, comment, occurredAt,
                targetMemberId, assignmentPosition, targetStepIndex,
                representedMemberId, delegationRuleId,
                Objects.requireNonNull(nextEvidence, "nextEvidence")
        );
    }

    public enum Type {
        STARTED,
        APPROVED,
        REJECTED,
        WITHDRAWN,
        TERMINATED,
        TRANSFERRED,
        ADD_SIGNED,
        RETURNED,
        CLAIM_CANCELLED,
        CLAIMED,
        SIGN_REMOVED,
        DEADLINE_REMINDER_SENT,
        DEADLINE_OVERDUE,
        DEADLINE_AUTO_APPROVED,
        DEADLINE_AUTO_REJECTED,
        COMPLETION_COMPLETED,
        COMPLETION_COMPENSATED
    }

    public enum AssignmentPosition {
        BEFORE,
        AFTER
    }
}
