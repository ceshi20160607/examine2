package com.unique.examine.flow.domain;

import java.util.List;

/**
 * Ephemeral branch input produced after a dynamic source is resolved. It is
 * persisted only through the instance's immutable branch execution snapshot.
 */
public record ResolvedApprovalBranchRoute(
        String code,
        String name,
        List<Long> approverIds,
        ApprovalMode approvalMode,
        List<ApprovalStage> approvalStages
) implements ApprovalBranchRoute {
    public ResolvedApprovalBranchRoute(
            String code,
            String name,
            List<Long> approverIds,
            ApprovalMode approvalMode
    ) {
        this(code, name, approverIds, approvalMode, null);
    }

    public ResolvedApprovalBranchRoute {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Resolved branch identity is required");
        }
        approverIds = ApprovalDefinitionDraft.requireApprovers(approverIds);
        approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
        ApprovalDefinitionDraft.requireApprovalModeApprovers(approvalMode, approverIds);
        approvalStages = ApprovalDefinitionDraft.requireBranchApprovalStages(
                approvalStages);
        ApprovalDefinitionDraft.requireBranchStageZeroProjection(
                approverIds, approvalMode, approvalStages);
    }

    public static ResolvedApprovalBranchRoute from(
            ApprovalBranchRoute route,
            List<Long> approverIds
    ) {
        return new ResolvedApprovalBranchRoute(
                route.code(),
                route.name(),
                approverIds,
                route.approvalMode(),
                route.approvalStages()
        );
    }
}
