package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.APPROVER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
public record ApprovalBranchExecution(
        String code,
        String name,
        List<Long> approverIds,
        ApprovalMode approvalMode,
        int requiredApprovals,
        long approverId,
        int currentStepIndex,
        Status status,
        Map<Long, ApprovalInstance.Decision> decisions,
        Instant startedAt,
        Instant completedAt,
        ApprovalDeadlineState deadline,
        ApprovalDecisionCommentPolicy decisionCommentPolicy,
        int currentStageIndex,
        List<ApprovalStageExecution> stages,
        ApprovalDecisionEvidencePolicy decisionEvidencePolicy
) {
    public ApprovalBranchExecution(
            String code,
            String name,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            long approverId,
            int currentStepIndex,
            Status status,
            Map<Long, ApprovalInstance.Decision> decisions,
            Instant startedAt,
            Instant completedAt,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            int currentStageIndex,
            List<ApprovalStageExecution> stages
    ) {
        this(
                code, name, approverIds, approvalMode, requiredApprovals,
                approverId, currentStepIndex, status, decisions, startedAt,
                completedAt, deadline, decisionCommentPolicy,
                currentStageIndex, stages, null
        );
    }

    public ApprovalBranchExecution(
            String code,
            String name,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            long approverId,
            int currentStepIndex,
            Status status,
            Map<Long, ApprovalInstance.Decision> decisions,
            Instant startedAt,
            Instant completedAt,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy
    ) {
        this(
                code, name, approverIds, approvalMode, requiredApprovals,
                approverId, currentStepIndex, status, decisions, startedAt,
                completedAt, deadline, decisionCommentPolicy, 0, null
        );
    }

    public ApprovalBranchExecution(
            String code,
            String name,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            long approverId,
            int currentStepIndex,
            Status status,
            Map<Long, ApprovalInstance.Decision> decisions,
            Instant startedAt,
            Instant completedAt,
            ApprovalDeadlineState deadline
    ) {
        this(
                code, name, approverIds, approvalMode, requiredApprovals,
                approverId, currentStepIndex, status, decisions, startedAt, completedAt,
                deadline, null
        );
    }

    public ApprovalBranchExecution(
            String code,
            String name,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            long approverId,
            int currentStepIndex,
            Status status,
            Map<Long, ApprovalInstance.Decision> decisions,
            Instant startedAt,
            Instant completedAt
    ) {
        this(
                code, name, approverIds, approvalMode, requiredApprovals,
                approverId, currentStepIndex, status, decisions, startedAt, completedAt,
                null, null
        );
    }

    public ApprovalBranchExecution(
            String code,
            String name,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            long approverId,
            int currentStepIndex,
            Status status,
            Map<Long, ApprovalInstance.Decision> decisions,
            Instant startedAt,
            Instant completedAt
    ) {
        this(
                code,
                name,
                approverIds,
                approvalMode,
                ApprovalQuorumRules.requiredApprovals(
                        approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode,
                        null,
                        approverIds == null ? 0 : approverIds.size()
                ),
                approverId,
                currentStepIndex,
                status,
                decisions,
                startedAt,
                completedAt,
                null,
                null
        );
    }

    public ApprovalBranchExecution {
        if (code == null || !code.matches("^[a-z][a-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("Parallel branch code is invalid");
        }
        name = ApprovalDefinitionDraft.requireText(name, "Parallel branch name");
        approverIds = ApprovalDefinitionDraft.requireApprovers(approverIds);
        approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
        ApprovalDefinitionDraft.requireApprovalModeApprovers(approvalMode, approverIds);
        requiredApprovals = requireRuntimeThreshold(
                approvalMode,
                requiredApprovals,
                approverIds.size()
        );
        if (currentStepIndex < 0 || currentStepIndex >= approverIds.size()
                || !approverIds.contains(approverId)) {
            throw new IllegalArgumentException("Parallel branch cursor is invalid");
        }
        if (approvalMode == ApprovalMode.SEQUENTIAL
                && approverIds.get(currentStepIndex) != approverId) {
            throw new IllegalArgumentException("Parallel branch approver must match its cursor");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        decisionCommentPolicy = decisionCommentPolicy == null
                ? ApprovalDecisionCommentPolicy.defaults()
                : decisionCommentPolicy;
        if (deadline != null && !deadline.dueAt().isAfter(startedAt)) {
            throw new IllegalArgumentException(
                    "Parallel branch deadline must be after its start"
            );
        }
        decisions = requireDecisions(approvalMode, approverIds, decisions);
        if (status == Status.PENDING && completedAt != null) {
            throw new IllegalArgumentException("Pending parallel branch cannot be completed");
        }
        if (status != Status.PENDING && completedAt == null) {
            throw new IllegalArgumentException("Terminal parallel branch requires completion time");
        }
        stages = normalizeStages(
                stages, currentStageIndex, approverIds, approvalMode,
                requiredApprovals, status, startedAt, completedAt, deadline,
                decisionCommentPolicy, decisionEvidencePolicy);
        if (currentStageIndex < 0 || currentStageIndex >= stages.size()) {
            throw new IllegalArgumentException(
                    "Parallel branch stage cursor is invalid");
        }
        requireStageShape(stages, currentStageIndex);
        var activeEvidencePolicy = stages.get(
                currentStageIndex).decisionEvidencePolicy();
        if (decisionEvidencePolicy == null) {
            decisionEvidencePolicy = activeEvidencePolicy;
        } else if (!decisionEvidencePolicy.equals(activeEvidencePolicy)) {
            throw new IllegalArgumentException(
                    "Branch evidence policy must mirror its active stage");
        }
    }

    public static ApprovalBranchExecution start(
            ApprovalBranchRoute branch,
            Instant startedAt
    ) {
        return start(branch, null, startedAt);
    }

    public static ApprovalBranchExecution start(
            ApprovalBranchRoute branch,
            ApprovalQuorumRule quorumRule,
            Instant startedAt
    ) {
        return start(branch, quorumRule, null, startedAt);
    }

    public static ApprovalBranchExecution start(
            ApprovalBranchRoute branch,
            ApprovalQuorumRule quorumRule,
            ApprovalDeadlinePolicy deadlinePolicy,
            Instant startedAt
    ) {
        return start(branch, quorumRule, deadlinePolicy, null, startedAt);
    }

    public static ApprovalBranchExecution start(
            ApprovalBranchRoute branch,
            ApprovalQuorumRule quorumRule,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            Instant startedAt
    ) {
        return start(
                branch, quorumRule, deadlinePolicy, decisionCommentPolicy,
                null, startedAt);
    }

    public static ApprovalBranchExecution start(
            ApprovalBranchRoute branch,
            ApprovalQuorumRule quorumRule,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            ApprovalDecisionEvidencePolicy decisionEvidencePolicy,
            Instant startedAt
    ) {
        Objects.requireNonNull(branch, "branch");
        var stages = initialStages(
                branch, branch.approverIds(), branch.approvalMode(),
                ApprovalQuorumRules.requiredApprovals(
                        branch.approvalMode(), quorumRule,
                        branch.approverIds().size()),
                ApprovalDeadlineState.start(deadlinePolicy, startedAt),
                decisionCommentPolicy, decisionEvidencePolicy, startedAt);
        return new ApprovalBranchExecution(
                branch.code(),
                branch.name(),
                branch.approverIds(),
                branch.approvalMode(),
                ApprovalQuorumRules.requiredApprovals(
                        branch.approvalMode(),
                        quorumRule,
                        branch.approverIds().size()
                ),
                branch.approverIds().getFirst(),
                0,
                Status.PENDING,
                Map.of(),
                startedAt,
                null,
                ApprovalDeadlineState.start(deadlinePolicy, startedAt),
                decisionCommentPolicy,
                0,
                stages,
                branch.approvalStages() == null
                        ? decisionEvidencePolicy
                        : branch.approvalStages().getFirst()
                        .decisionEvidencePolicy()
        );
    }

    public ApprovalBranchExecution approve(long actorId, Instant occurredAt) {
        return approve(actorId, null, occurredAt);
    }

    public ApprovalBranchExecution approve(
            long actorId,
            String comment,
            Instant occurredAt
    ) {
        return approve(actorId, actorId, null, comment, occurredAt);
    }

    public ApprovalBranchExecution approve(
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            String comment,
            Instant occurredAt
    ) {
        requireDecision(
                actorId,
                representedMemberId,
                delegationRuleId,
                occurredAt
        );
        decisionCommentPolicy.validateApproval(comment);
        if (approvalMode == ApprovalMode.SEQUENTIAL) {
            if (currentStepIndex + 1 < approverIds.size()) {
                return next(
                        approverIds.get(currentStepIndex + 1),
                        currentStepIndex + 1,
                        Status.PENDING,
                        decisions,
                        occurredAt,
                        stagesAfterDecision(
                                actorId, representedMemberId, true)
                );
            }
            return next(
                    approverId, currentStepIndex, Status.APPROVED, decisions,
                    occurredAt,
                    stagesAfterDecision(actorId, representedMemberId, true));
        }
        return concurrent(
                actorId,
                representedMemberId,
                ApprovalInstance.Decision.APPROVED,
                occurredAt
        );
    }

    public ApprovalBranchExecution reject(long actorId, String reason, Instant occurredAt) {
        return reject(actorId, actorId, null, reason, occurredAt);
    }

    public ApprovalBranchExecution reject(
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            String reason,
            Instant occurredAt
    ) {
        requireDecision(
                actorId,
                representedMemberId,
                delegationRuleId,
                occurredAt
        );
        decisionCommentPolicy.validateRejection(reason);
        if (approvalMode == ApprovalMode.SEQUENTIAL) {
            return next(approverId, currentStepIndex, Status.REJECTED, decisions, occurredAt);
        }
        return concurrent(
                actorId,
                representedMemberId,
                ApprovalInstance.Decision.REJECTED,
                occurredAt
        );
    }

    public ApprovalBranchExecution close(Status target, Instant occurredAt) {
        if (status != Status.PENDING) {
            return this;
        }
        if (target != Status.CANCELLED
                && target != Status.WITHDRAWN
                && target != Status.TERMINATED) {
            throw new IllegalArgumentException("Parallel branch close status is invalid");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        return next(approverId, currentStepIndex, target, decisions, occurredAt);
    }

    public ApprovalBranchExecution markDeadlineReminded(Instant occurredAt) {
        if (status != Status.PENDING || deadline == null) {
            return this;
        }
        var nextDeadline = deadline.markReminded(occurredAt);
        return nextDeadline == deadline ? this : withDeadline(nextDeadline);
    }

    public ApprovalBranchExecution processDeadline(Instant occurredAt) {
        if (status != Status.PENDING || deadline == null || deadline.processedAt() != null) {
            return this;
        }
        var processed = deadline.markProcessed(occurredAt);
        var target = switch (deadline.policy().timeoutAction()) {
            case NONE -> Status.PENDING;
            case AUTO_APPROVE -> Status.APPROVED;
            case AUTO_REJECT -> Status.REJECTED;
        };
        return new ApprovalBranchExecution(
                code,
                name,
                approverIds,
                approvalMode,
                requiredApprovals,
                approverId,
                currentStepIndex,
                target,
                decisions,
                startedAt,
                target == Status.PENDING ? null : occurredAt,
                processed,
                decisionCommentPolicy,
                currentStageIndex,
                stages,
                decisionEvidencePolicy
        );
    }

    public List<Long> activeApproverIds() {
        if (status != Status.PENDING) {
            return List.of();
        }
        if (approvalMode == ApprovalMode.SEQUENTIAL) {
            return List.of(approverId);
        }
        return approverIds.stream()
                .filter(memberId -> !decisions.containsKey(memberId))
                .toList();
    }

    public List<Long> approvedApproverIds() {
        return decisionMembers(ApprovalInstance.Decision.APPROVED);
    }

    public List<Long> rejectedApproverIds() {
        return decisionMembers(ApprovalInstance.Decision.REJECTED);
    }

    private void requireDecision(
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            Instant occurredAt
    ) {
        if (status != Status.PENDING) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only a pending parallel branch can be decided"
            );
        }
        requireRepresentation(actorId, representedMemberId, delegationRuleId);
        var eligible = approvalMode == ApprovalMode.SEQUENTIAL
                ? representedMemberId == approverId
                : approverIds.contains(representedMemberId)
                        && !decisions.containsKey(representedMemberId);
        if (!eligible) {
            throw new ApprovalDomainException(
                    APPROVER_FORBIDDEN,
                    "Only an active parallel branch approver can decide"
            );
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private ApprovalBranchExecution concurrent(
            long actorId,
            long representedMemberId,
            ApprovalInstance.Decision decision,
            Instant occurredAt
    ) {
        var nextDecisions = new LinkedHashMap<>(decisions);
        nextDecisions.put(representedMemberId, decision);
        if (approvalMode == ApprovalMode.SEQUENTIAL) {
            throw new IllegalStateException("Sequential branch is not concurrent");
        }
        var approvals = nextDecisions.values().stream()
                .filter(value -> value == ApprovalInstance.Decision.APPROVED)
                .count();
        var remaining = approverIds.size() - nextDecisions.size();
        var target = approvals >= requiredApprovals
                ? Status.APPROVED
                : approvals + remaining < requiredApprovals
                        ? Status.REJECTED
                        : Status.PENDING;
        var nextApproverId = target == Status.PENDING
                ? approverIds.stream()
                        .filter(memberId -> !nextDecisions.containsKey(memberId))
                        .findFirst()
                        .orElseThrow()
                : representedMemberId;
        return next(
                nextApproverId,
                approverIds.indexOf(nextApproverId),
                target,
                nextDecisions,
                occurredAt,
                decision == ApprovalInstance.Decision.APPROVED
                        ? stagesAfterDecision(
                                actorId, representedMemberId, true)
                        : stages
        );
    }

    private static void requireRepresentation(
            long actorId,
            long representedMemberId,
            Long delegationRuleId
    ) {
        if (actorId <= 0 || representedMemberId <= 0) {
            throw new ApprovalDomainException(
                    APPROVER_FORBIDDEN,
                    "Decision actor and represented member must be positive"
            );
        }
        if (actorId == representedMemberId) {
            if (delegationRuleId != null) {
                throw new ApprovalDomainException(
                        APPROVER_FORBIDDEN,
                        "A direct decision cannot carry delegation authority"
                );
            }
        } else if (delegationRuleId == null || delegationRuleId <= 0) {
            throw new ApprovalDomainException(
                    APPROVER_FORBIDDEN,
                    "An on-behalf decision requires a delegation rule"
            );
        }
    }

    private ApprovalBranchExecution next(
            long nextApproverId,
            int nextStepIndex,
            Status target,
            Map<Long, ApprovalInstance.Decision> nextDecisions,
            Instant occurredAt
    ) {
        return next(
                nextApproverId, nextStepIndex, target, nextDecisions,
                occurredAt, stages);
    }

    private ApprovalBranchExecution next(
            long nextApproverId,
            int nextStepIndex,
            Status target,
            Map<Long, ApprovalInstance.Decision> nextDecisions,
            Instant occurredAt,
            List<ApprovalStageExecution> nextStages
    ) {
        return new ApprovalBranchExecution(
                code,
                name,
                approverIds,
                approvalMode,
                requiredApprovals,
                nextApproverId,
                nextStepIndex,
                target,
                nextDecisions,
                startedAt,
                target == Status.PENDING ? null : occurredAt,
                deadline,
                decisionCommentPolicy,
                currentStageIndex,
                nextStages,
                decisionEvidencePolicy
        );
    }

    private ApprovalBranchExecution withDeadline(ApprovalDeadlineState nextDeadline) {
        return new ApprovalBranchExecution(
                code,
                name,
                approverIds,
                approvalMode,
                requiredApprovals,
                approverId,
                currentStepIndex,
                status,
                decisions,
                startedAt,
                completedAt,
                nextDeadline,
                decisionCommentPolicy,
                currentStageIndex,
                stages,
                decisionEvidencePolicy
        );
    }

    public ApprovalStageExecution currentStage() {
        return stages.get(currentStageIndex);
    }

    public boolean hasWaitingStage() {
        return currentStageIndex + 1 < stages.size();
    }

    public ApprovalBranchExecution activateNextStage(
            ApprovalStage nextStage,
            List<Long> resolvedApproverIds,
            Instant activatedAt
    ) {
        Objects.requireNonNull(nextStage, "nextStage");
        Objects.requireNonNull(activatedAt, "activatedAt");
        if (status != Status.APPROVED || !hasWaitingStage()) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Approval branch has no stage ready for activation");
        }
        var nextIndex = currentStageIndex + 1;
        var waiting = stages.get(nextIndex);
        if (waiting.status() != ApprovalStageExecution.Status.WAITING
                || !waiting.code().equals(nextStage.code())) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Waiting branch stage does not match the published plan");
        }
        resolvedApproverIds = ApprovalDefinitionDraft.requireApprovers(
                resolvedApproverIds);
        if (nextStage.approverSource().kind()
                == ApprovalApproverSource.Kind.PREVIOUS_HANDLER) {
            if (currentStage().actualHandlerIds().isEmpty()
                    || !currentStage().actualHandlerIds()
                    .equals(resolvedApproverIds)) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Branch PREVIOUS_HANDLER must use only its preceding actors");
            }
        } else if (nextStage.approverSource().kind()
                == ApprovalApproverSource.Kind.FIXED
                && !nextStage.approverIds().equals(resolvedApproverIds)) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Fixed branch stage must preserve published members");
        }
        var active = ApprovalStageExecution.active(
                nextIndex, nextStage, resolvedApproverIds, activatedAt);
        var nextStages = new java.util.ArrayList<>(stages);
        nextStages.set(nextIndex, active);
        return new ApprovalBranchExecution(
                code, name, resolvedApproverIds, nextStage.approvalMode(),
                active.requiredApprovals(), resolvedApproverIds.getFirst(), 0,
                Status.PENDING, Map.of(), startedAt, null, active.deadline(),
                active.decisionCommentPolicy(), nextIndex, nextStages,
                active.decisionEvidencePolicy());
    }

    private List<ApprovalStageExecution> stagesAfterDecision(
            long actorId,
            long representedMemberId,
            boolean approved
    ) {
        if (!approved || stages.size() == 1
                && stages.getFirst().code().equals("legacy")) {
            return stages;
        }
        var nextStages = new java.util.ArrayList<>(stages);
        nextStages.set(
                currentStageIndex,
                currentStage().recordApproval(representedMemberId, actorId)
        );
        return List.copyOf(nextStages);
    }

    private static List<ApprovalStageExecution> initialStages(
            ApprovalBranchRoute branch,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy commentPolicy,
            ApprovalDecisionEvidencePolicy evidencePolicy,
            Instant startedAt
    ) {
        if (branch.approvalStages() == null) {
            return null;
        }
        var result = new java.util.ArrayList<ApprovalStageExecution>();
        var first = branch.approvalStages().getFirst();
        result.add(new ApprovalStageExecution(
                0, first.code(), first.name(),
                ApprovalStageExecution.Status.ACTIVE, approverIds,
                approvalMode, requiredApprovals, List.of(), startedAt, null,
                deadline, commentPolicy, Map.of(),
                first.decisionEvidencePolicy()));
        for (var index = 1; index < branch.approvalStages().size(); index++) {
            result.add(ApprovalStageExecution.waiting(
                    index, branch.approvalStages().get(index)));
        }
        return List.copyOf(result);
    }

    private static List<ApprovalStageExecution> normalizeStages(
            List<ApprovalStageExecution> supplied,
            int currentStageIndex,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Status branchStatus,
            Instant startedAt,
            Instant completedAt,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy commentPolicy,
            ApprovalDecisionEvidencePolicy evidencePolicy
    ) {
        if (supplied == null || supplied.isEmpty()) {
            var stageStatus = branchStatus == Status.PENDING
                    ? ApprovalStageExecution.Status.ACTIVE
                    : branchStatus == Status.APPROVED
                            ? ApprovalStageExecution.Status.APPROVED
                            : ApprovalStageExecution.Status.REJECTED;
            return List.of(new ApprovalStageExecution(
                    0, "legacy", "Approval", stageStatus, approverIds,
                    approvalMode, requiredApprovals, List.of(), startedAt,
                    stageStatus == ApprovalStageExecution.Status.ACTIVE
                            ? null : completedAt,
                    deadline, commentPolicy, Map.of(), evidencePolicy));
        }
        if (currentStageIndex < 0 || currentStageIndex >= supplied.size()) {
            throw new IllegalArgumentException(
                    "Parallel branch stage cursor is invalid");
        }
        var result = new java.util.ArrayList<>(supplied);
        var current = result.get(currentStageIndex);
        var actors = new LinkedHashMap<>(
                current.handlerActorsByParticipantId());
        actors.entrySet().removeIf(
                entry -> !approverIds.contains(entry.getKey()));
        var actualHandlers = approverIds.stream()
                .map(actors::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        var stageStatus = branchStatus == Status.PENDING
                ? ApprovalStageExecution.Status.ACTIVE
                : branchStatus == Status.APPROVED
                        ? ApprovalStageExecution.Status.APPROVED
                        : ApprovalStageExecution.Status.REJECTED;
        result.set(currentStageIndex, new ApprovalStageExecution(
                currentStageIndex, current.code(), current.name(), stageStatus,
                approverIds, approvalMode, requiredApprovals, actualHandlers,
                current.startedAt() == null ? startedAt : current.startedAt(),
                stageStatus == ApprovalStageExecution.Status.ACTIVE
                        ? null : completedAt,
                deadline, commentPolicy, actors,
                current.decisionEvidencePolicy()));
        return List.copyOf(result);
    }

    private static void requireStageShape(
            List<ApprovalStageExecution> stages,
            int currentStageIndex
    ) {
        if (stages.isEmpty() || stages.size() > 10
                || stages.stream().map(ApprovalStageExecution::code)
                .distinct().count() != stages.size()) {
            throw new IllegalArgumentException(
                    "Parallel branch stage plan is invalid");
        }
        for (var index = 0; index < stages.size(); index++) {
            var stage = stages.get(index);
            if (stage.stageIndex() != index
                    || index < currentStageIndex
                    && stage.status()
                    != ApprovalStageExecution.Status.APPROVED
                    || index > currentStageIndex
                    && stage.status()
                    != ApprovalStageExecution.Status.WAITING) {
                throw new IllegalArgumentException(
                        "Parallel branch stage cursor is corrupt");
            }
        }
    }

    private List<Long> decisionMembers(ApprovalInstance.Decision target) {
        return approverIds.stream()
                .filter(memberId -> decisions.get(memberId) == target)
                .toList();
    }

    private static Map<Long, ApprovalInstance.Decision> requireDecisions(
            ApprovalMode mode,
            List<Long> approverIds,
            Map<Long, ApprovalInstance.Decision> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        if (mode == ApprovalMode.SEQUENTIAL
                || values.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null
                                || entry.getValue() == null
                                || !approverIds.contains(entry.getKey()))) {
            throw new IllegalArgumentException("Parallel branch decisions are invalid");
        }
        var ordered = new LinkedHashMap<Long, ApprovalInstance.Decision>();
        approverIds.forEach(memberId -> {
            if (values.containsKey(memberId)) {
                ordered.put(memberId, values.get(memberId));
            }
        });
        return Collections.unmodifiableMap(ordered);
    }

    static int requireRuntimeThreshold(
            ApprovalMode mode,
            int requiredApprovals,
            int memberCount
    ) {
        if (requiredApprovals < 1 || requiredApprovals > memberCount) {
            throw new IllegalArgumentException(
                    "Runtime required approvals must be within the member snapshot"
            );
        }
        if ((mode == ApprovalMode.ANY && requiredApprovals != 1)
                || ((mode == ApprovalMode.SEQUENTIAL || mode == ApprovalMode.ALL)
                && requiredApprovals != memberCount)) {
            throw new IllegalArgumentException(
                    "Runtime required approvals do not match the approval mode"
            );
        }
        return requiredApprovals;
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED,
        WITHDRAWN,
        TERMINATED
    }
}
