package com.unique.examine.flow.domain;

import java.util.HashSet;
import java.util.List;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.GATEWAY_INVALID;

/**
 * One bounded exclusive gateway placed immediately before an approval route.
 * Conditional branches are evaluated in list order and the final branch is the
 * required default.
 */
public record ApprovalGateway(List<Branch> branches) {
    public static final int MAX_CONDITIONAL_BRANCHES = 5;
    public static final int MAX_CONDITIONS_PER_BRANCH = 10;

    public ApprovalGateway {
        if (branches == null
                || branches.size() < 2
                || branches.size() > MAX_CONDITIONAL_BRANCHES + 1) {
            throw invalid("Gateway must contain 1 to 5 conditional branches and one default branch");
        }
        branches = List.copyOf(branches);
        var codes = new HashSet<String>();
        var names = new HashSet<String>();
        for (var index = 0; index < branches.size(); index++) {
            var branch = branches.get(index);
            if (branch == null) {
                throw invalid("Gateway branch must not be null");
            }
            if (!codes.add(branch.code())) {
                throw invalid("Gateway branch codes must be unique");
            }
            if (!names.add(branch.name())) {
                throw invalid("Gateway branch names must be unique");
            }
            if (branch.defaultBranch() != (index == branches.size() - 1)) {
                throw invalid("Gateway default branch must be present exactly once and ordered last");
            }
        }
    }

    public List<Branch> conditionalBranches() {
        return branches.subList(0, branches.size() - 1);
    }

    public Branch defaultBranch() {
        return branches.getLast();
    }

    public record Branch(
            String code,
            String name,
            boolean defaultBranch,
            List<TriggerCondition> conditions,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            List<ApprovalStage> approvalStages
    ) implements ApprovalBranchRoute {
        public Branch {
            if (code == null || !code.matches("^[a-z][a-z0-9_]{0,63}$")) {
                throw invalid("Gateway branch code must be canonical lower snake case");
            }
            name = ApprovalDefinitionDraft.requireText(name, "Gateway branch name");
            if (name.length() > 80) {
                throw invalid("Gateway branch name accepts at most 80 characters");
            }
            if (conditions == null || conditions.size() > MAX_CONDITIONS_PER_BRANCH) {
                throw invalid("Gateway branch accepts at most 10 all-of conditions");
            }
            conditions = List.copyOf(conditions);
            if (conditions.stream().anyMatch(java.util.Objects::isNull)) {
                throw invalid("Gateway branch conditions must not contain null");
            }
            if (defaultBranch && !conditions.isEmpty()) {
                throw invalid("Gateway default branch must not contain conditions");
            }
            if (!defaultBranch && conditions.isEmpty()) {
                throw invalid("Conditional gateway branch requires at least one condition");
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
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<Long> approverIds,
                ApprovalMode approvalMode
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, null
            );
        }

        public Branch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<Long> approverIds
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    ApprovalMode.SEQUENTIAL, null
            );
        }
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(GATEWAY_INVALID, message);
    }
}
