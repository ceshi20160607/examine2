package com.unique.examine.flow.domain;

import java.util.List;
import java.util.Objects;

/**
 * Immutable definition-time contract for one ordered approval stage.
 */
public record ApprovalStage(
        String code,
        String name,
        List<Long> approverIds,
        ApprovalMode approvalMode,
        ApprovalApproverSource approverSource,
        ApprovalQuorumRule quorumRule,
        ApprovalDeadlinePolicy deadlinePolicy,
        ApprovalDecisionCommentPolicy decisionCommentPolicy,
        ApprovalDecisionEvidencePolicy decisionEvidencePolicy
) {
    private static final String CODE_PATTERN = "^[A-Za-z][A-Za-z0-9_]{0,63}$";

    public ApprovalStage(
            String code,
            String name,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            ApprovalApproverSource approverSource,
            ApprovalQuorumRule quorumRule,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy
    ) {
        this(
                code, name, approverIds, approvalMode, approverSource,
                quorumRule, deadlinePolicy, decisionCommentPolicy, null
        );
    }

    public ApprovalStage {
        if (code == null || !code.matches(CODE_PATTERN)) {
            throw new IllegalArgumentException("Approval stage code is invalid");
        }
        name = ApprovalDefinitionDraft.requireText(name, "Approval stage name");
        approverIds = approverIds == null ? List.of() : List.copyOf(approverIds);
        approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
        approverSource = approverSource == null
                ? ApprovalApproverSource.fixed()
                : approverSource;
        decisionCommentPolicy = decisionCommentPolicy == null
                ? ApprovalDecisionCommentPolicy.defaults()
                : decisionCommentPolicy;
        if (approverSource.kind() == ApprovalApproverSource.Kind.FIXED) {
            approverIds = ApprovalDefinitionDraft.requireApprovers(approverIds);
            ApprovalDefinitionDraft.requireApprovalModeApprovers(
                    approvalMode, approverIds);
        } else if (!approverIds.isEmpty()) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID,
                    "Dynamic approval stages must not define fixed approver ids");
        }
        if (approvalMode == ApprovalMode.QUORUM && quorumRule == null) {
            throw new IllegalArgumentException(
                    "QUORUM approval stage requires a quorum rule");
        }
        if (approvalMode != ApprovalMode.QUORUM && quorumRule != null) {
            throw new IllegalArgumentException(
                    "Only QUORUM approval stages may define a quorum rule");
        }
        Objects.requireNonNull(decisionCommentPolicy, "decisionCommentPolicy");
    }

    public int requiredApprovals(int memberCount) {
        return ApprovalQuorumRules.requiredApprovals(
                approvalMode, quorumRule, memberCount);
    }
}
