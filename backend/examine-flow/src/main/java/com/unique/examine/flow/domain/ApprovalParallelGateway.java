package com.unique.examine.flow.domain;

import java.util.HashSet;
import java.util.List;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.GATEWAY_INVALID;

/**
 * One bounded parallel split/join. Every branch starts together and owns an
 * immutable route snapshot.
 */
public record ApprovalParallelGateway(List<Branch> branches) {
    public static final int MIN_BRANCHES = 2;
    public static final int MAX_BRANCHES = 5;

    public ApprovalParallelGateway {
        if (branches == null
                || branches.size() < MIN_BRANCHES
                || branches.size() > MAX_BRANCHES) {
            throw invalid("Parallel gateway must contain 2 to 5 branches");
        }
        branches = List.copyOf(branches);
        var codes = new HashSet<String>();
        var names = new HashSet<String>();
        for (var branch : branches) {
            if (branch == null) {
                throw invalid("Parallel gateway branch must not be null");
            }
            if (!codes.add(branch.code())) {
                throw invalid("Parallel gateway branch codes must be unique");
            }
            if (!names.add(branch.name())) {
                throw invalid("Parallel gateway branch names must be unique");
            }
        }
    }

    public record Branch(
            String code,
            String name,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            List<ApprovalStage> approvalStages
    ) implements ApprovalBranchRoute {
        public Branch {
            if (code == null || !code.matches("^[a-z][a-z0-9_]{0,63}$")) {
                throw invalid("Parallel branch code must be canonical lower snake case");
            }
            name = ApprovalDefinitionDraft.requireText(name, "Parallel branch name");
            if (name.length() > 80) {
                throw invalid("Parallel branch name accepts at most 80 characters");
            }
            approverIds = ApprovalDefinitionDraft.requireApprovers(approverIds);
            approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
            ApprovalDefinitionDraft.requireApprovalModeApprovers(approvalMode, approverIds);
            approvalStages = ApprovalDefinitionDraft.requireBranchApprovalStages(
                    approvalStages);
            ApprovalDefinitionDraft.requireBranchStageZeroProjection(
                    approverIds, approvalMode, approvalStages);
        }

        public Branch(
                String code,
                String name,
                List<Long> approverIds,
                ApprovalMode approvalMode
        ) {
            this(code, name, approverIds, approvalMode, null);
        }
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(GATEWAY_INVALID, message);
    }
}
