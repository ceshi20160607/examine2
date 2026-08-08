package com.unique.examine.flow.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.GATEWAY_INVALID;

/**
 * One bounded inclusive split. Every matching conditional branch executes;
 * the optional default executes only when none match.
 */
public record ApprovalInclusiveGateway(List<Branch> branches) {
    public static final int MIN_BRANCHES = 2;
    public static final int MAX_BRANCHES = 5;
    public static final int MAX_CONDITIONS_PER_BRANCH = 10;

    public ApprovalInclusiveGateway {
        if (branches == null
                || branches.size() < MIN_BRANCHES
                || branches.size() > MAX_BRANCHES) {
            throw invalid("Inclusive gateway must contain 2 to 5 branches");
        }
        branches = List.copyOf(branches);
        var codes = new HashSet<String>();
        var names = new HashSet<String>();
        var defaultCount = 0;
        for (var index = 0; index < branches.size(); index++) {
            var branch = branches.get(index);
            if (branch == null) {
                throw invalid("Inclusive gateway branch must not be null");
            }
            if (!codes.add(branch.code())) {
                throw invalid("Inclusive gateway branch codes must be unique");
            }
            if (!names.add(branch.name())) {
                throw invalid("Inclusive gateway branch names must be unique");
            }
            if (branch.defaultBranch()) {
                defaultCount++;
                if (index != branches.size() - 1) {
                    throw invalid("Inclusive default branch must be ordered last");
                }
            }
        }
        if (defaultCount > 1) {
            throw invalid("Inclusive gateway accepts at most one default branch");
        }
    }

    public List<Branch> conditionalBranches() {
        return branches.stream().filter(branch -> !branch.defaultBranch()).toList();
    }

    public Optional<Branch> defaultBranch() {
        return branches.stream().filter(Branch::defaultBranch).findFirst();
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
                throw invalid("Inclusive branch code must be canonical lower snake case");
            }
            name = ApprovalDefinitionDraft.requireText(name, "Inclusive branch name");
            if (name.length() > 80) {
                throw invalid("Inclusive branch name accepts at most 80 characters");
            }
            if (conditions == null || conditions.size() > MAX_CONDITIONS_PER_BRANCH) {
                throw invalid("Inclusive branch accepts at most 10 all-of conditions");
            }
            conditions = List.copyOf(conditions);
            if (conditions.stream().anyMatch(java.util.Objects::isNull)) {
                throw invalid("Inclusive branch conditions must not contain null");
            }
            if (defaultBranch && !conditions.isEmpty()) {
                throw invalid("Inclusive default branch must not contain conditions");
            }
            if (!defaultBranch && conditions.isEmpty()) {
                throw invalid("Inclusive conditional branch requires at least one condition");
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
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(GATEWAY_INVALID, message);
    }
}
