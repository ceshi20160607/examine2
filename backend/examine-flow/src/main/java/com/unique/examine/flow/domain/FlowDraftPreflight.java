package com.unique.examine.flow.domain;

import java.util.List;

public final class FlowDraftPreflight {
    private FlowDraftPreflight() {
    }

    public enum Verdict {
        READY,
        BLOCKED
    }

    public enum Severity {
        BLOCKER,
        WARNING
    }

    public record Issue(
            Severity severity,
            String code,
            String path,
            String message
    ) {
        public Issue {
            if (severity == null
                    || code == null
                    || code.isBlank()
                    || path == null
                    || path.isBlank()
                    || message == null
                    || message.isBlank()) {
                throw new IllegalArgumentException("Flow draft issue is incomplete");
            }
        }
    }

    public record Check(
            long definitionId,
            int revision,
            Verdict verdict,
            List<Issue> issues
    ) {
        public Check {
            if (definitionId <= 0 || revision <= 0 || verdict == null || issues == null) {
                throw new IllegalArgumentException("Flow draft check is invalid");
            }
            issues = List.copyOf(issues);
            var blocked = issues.stream()
                    .anyMatch(issue -> issue.severity() == Severity.BLOCKER);
            if ((verdict == Verdict.BLOCKED) != blocked) {
                throw new IllegalArgumentException("Flow draft check verdict does not match issues");
            }
        }

        public int blockerCount() {
            return (int) issues.stream()
                    .filter(issue -> issue.severity() == Severity.BLOCKER)
                    .count();
        }

        public int warningCount() {
            return (int) issues.stream()
                    .filter(issue -> issue.severity() == Severity.WARNING)
                    .count();
        }
    }

    public record Step(int index, long approverId, boolean initial) {
        public Step {
            if (index < 0 || approverId <= 0) {
                throw new IllegalArgumentException("Flow simulation step is invalid");
            }
        }
    }

    public record TriggerResult(
            boolean configured,
            String event,
            String moduleCode,
            boolean matched,
            String reason
    ) {
        public TriggerResult {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Flow simulation trigger reason is required");
            }
        }
    }

    public record StatusEffects(
            String fieldCode,
            String approvedValue,
            String rejectedValue,
            String withdrawnValue,
            String terminatedValue
    ) {
        public static StatusEffects from(RecordStatusMapping value) {
            return value == null
                    ? null
                    : new StatusEffects(
                            value.fieldCode(),
                            value.approvedValue(),
                            value.rejectedValue(),
                            value.withdrawnValue(),
                            value.terminatedValue()
                    );
        }
    }

    public record RouteResult(
            boolean configured,
            String branchCode,
            String branchName,
            boolean defaultBranch,
            ApprovalMode approvalMode,
            int requiredApprovals,
            List<Long> activeApproverIds,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy
    ) {
        public RouteResult(
                boolean configured,
                String branchCode,
                String branchName,
                boolean defaultBranch,
                ApprovalMode approvalMode,
                int requiredApprovals,
                List<Long> activeApproverIds,
                ApprovalDeadlinePolicy deadlinePolicy
        ) {
            this(
                    configured, branchCode, branchName, defaultBranch,
                    approvalMode, requiredApprovals, activeApproverIds,
                    deadlinePolicy, null
            );
        }

        public RouteResult(
                boolean configured,
                String branchCode,
                String branchName,
                boolean defaultBranch,
                ApprovalMode approvalMode,
                int requiredApprovals,
                List<Long> activeApproverIds
        ) {
            this(
                    configured, branchCode, branchName, defaultBranch,
                    approvalMode, requiredApprovals, activeApproverIds, null, null
            );
        }

        public RouteResult(
                boolean configured,
                String branchCode,
                String branchName,
                boolean defaultBranch,
                ApprovalMode approvalMode,
                List<Long> activeApproverIds
        ) {
            this(
                    configured,
                    branchCode,
                    branchName,
                    defaultBranch,
                    approvalMode,
                    defaultRequiredApprovals(approvalMode, activeApproverIds),
                    activeApproverIds,
                    null,
                    null
            );
        }

        public RouteResult {
            approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
            decisionCommentPolicy = decisionCommentPolicy == null
                    ? ApprovalDecisionCommentPolicy.defaults()
                    : decisionCommentPolicy;
            if (requiredApprovals < 0) {
                throw new IllegalArgumentException(
                        "Flow simulation required approvals cannot be negative");
            }
            activeApproverIds = activeApproverIds == null
                    ? List.of()
                    : List.copyOf(activeApproverIds);
            if (configured
                    && (branchCode == null
                    || branchCode.isBlank()
                    || branchName == null
                    || branchName.isBlank())) {
                throw new IllegalArgumentException("Flow simulation route result is invalid");
            }
            if (!configured && (branchCode != null || branchName != null || !defaultBranch)) {
                throw new IllegalArgumentException("Ungated Flow simulation route result is invalid");
            }
        }

        public RouteResult(
                boolean configured,
                String branchCode,
                String branchName,
                boolean defaultBranch
        ) {
            this(
                    configured,
                    branchCode,
                    branchName,
                    defaultBranch,
                    ApprovalMode.SEQUENTIAL,
                    0,
                    List.of(),
                    null,
                    null
            );
        }

        private static int defaultRequiredApprovals(
                ApprovalMode mode,
                List<Long> activeApproverIds
        ) {
            var size = activeApproverIds == null ? 0 : activeApproverIds.size();
            if (size == 0 || mode == ApprovalMode.QUORUM) {
                return 0;
            }
            return mode == ApprovalMode.ANY ? 1 : size;
        }
    }

    public record Simulation(
            long definitionId,
            int revision,
            Check check,
            long requesterId,
            String businessKey,
            boolean startable,
            String reason,
            List<Step> steps,
            TriggerResult trigger,
            StatusEffects statusEffects,
            RouteResult route,
            List<RouteResult> parallelRoutes
    ) {
        public Simulation(
                long definitionId,
                int revision,
                Check check,
                long requesterId,
                String businessKey,
                boolean startable,
                String reason,
                List<Step> steps,
                TriggerResult trigger,
                StatusEffects statusEffects,
                RouteResult route
        ) {
            this(
                    definitionId, revision, check, requesterId, businessKey,
                    startable, reason, steps, trigger, statusEffects, route, List.of()
            );
        }

        public Simulation {
            if (definitionId <= 0
                    || revision <= 0
                    || check == null
                    || check.definitionId() != definitionId
                    || check.revision() != revision
                    || requesterId <= 0
                    || businessKey == null
                    || businessKey.isBlank()
                    || reason == null
                    || reason.isBlank()
                    || steps == null
                    || trigger == null
                    || route == null) {
                throw new IllegalArgumentException("Flow draft simulation is invalid");
            }
            if (startable && steps.isEmpty()) {
                throw new IllegalArgumentException(
                        "Startable Flow simulation requires at least one step");
            }
            steps = List.copyOf(steps);
            parallelRoutes = parallelRoutes == null ? List.of() : List.copyOf(parallelRoutes);
            if (startable != (check.verdict() == Verdict.READY && trigger.matched())) {
                throw new IllegalArgumentException(
                        "Flow simulation startability does not match check and trigger result");
            }
        }
    }
}
