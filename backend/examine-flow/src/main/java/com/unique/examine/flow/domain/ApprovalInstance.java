package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.APPROVER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.ASSIGNMENT_REQUEST_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.CANCEL_CLAIM_REQUEST_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.CLAIM_REQUEST_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.REDUCE_SIGN_REQUEST_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.REQUESTER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.RETURN_REQUEST_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.TERMINATE_REASON_REQUIRED;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.WITHDRAW_REASON_REQUIRED;

public record ApprovalInstance(
        long id,
        long definitionId,
        int definitionVersion,
        String businessKey,
        long requesterId,
        long approverId,
        List<Long> approverIds,
        Status status,
        Instant startedAt,
        Instant completedAt,
        List<ApprovalHistoryEvent> history,
        int currentStepIndex,
        ClaimState claimState,
        RecordBinding recordBinding,
        ApprovalMode approvalMode,
        int requiredApprovals,
        Map<Long, Decision> decisions,
        List<ApprovalBranchExecution> parallelBranches,
        ApprovalDeadlineState deadline,
        ApprovalDecisionCommentPolicy decisionCommentPolicy,
        int currentStageIndex,
        List<ApprovalStageExecution> stages,
        ApprovalStartContext startContext,
        ApprovalDecisionEvidencePolicy decisionEvidencePolicy,
        CompletionPhase completionPhase,
        Integer activeCompletionOrdinal,
        List<ApprovalCompletionExecution> completionExecutions,
        CompletionFailurePolicy completionFailurePolicy
) {
    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Map<Long, Decision> decisions,
            List<ApprovalBranchExecution> parallelBranches,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            int currentStageIndex,
            List<ApprovalStageExecution> stages,
            ApprovalStartContext startContext,
            ApprovalDecisionEvidencePolicy decisionEvidencePolicy,
            CompletionPhase completionPhase,
            Integer activeCompletionOrdinal,
            List<ApprovalCompletionExecution> completionExecutions
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, recordBinding, approvalMode,
                requiredApprovals, decisions, parallelBranches, deadline,
                decisionCommentPolicy, currentStageIndex, stages, startContext,
                decisionEvidencePolicy, completionPhase, activeCompletionOrdinal,
                completionExecutions, CompletionFailurePolicy.MANUAL_RETRY
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Map<Long, Decision> decisions,
            List<ApprovalBranchExecution> parallelBranches,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            int currentStageIndex,
            List<ApprovalStageExecution> stages,
            ApprovalStartContext startContext,
            ApprovalDecisionEvidencePolicy decisionEvidencePolicy
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, recordBinding, approvalMode,
                requiredApprovals, decisions, parallelBranches, deadline,
                decisionCommentPolicy, currentStageIndex, stages, startContext,
                decisionEvidencePolicy, null, null, null
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Map<Long, Decision> decisions,
            List<ApprovalBranchExecution> parallelBranches,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            int currentStageIndex,
            List<ApprovalStageExecution> stages,
            ApprovalStartContext startContext
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, recordBinding, approvalMode,
                requiredApprovals, decisions, parallelBranches, deadline,
                decisionCommentPolicy, currentStageIndex, stages, startContext,
                null
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Map<Long, Decision> decisions,
            List<ApprovalBranchExecution> parallelBranches,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            int currentStageIndex,
            List<ApprovalStageExecution> stages
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, recordBinding, approvalMode,
                requiredApprovals, decisions, parallelBranches, deadline,
                decisionCommentPolicy, currentStageIndex, stages, null
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Map<Long, Decision> decisions,
            List<ApprovalBranchExecution> parallelBranches,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, recordBinding, approvalMode,
                requiredApprovals, decisions, parallelBranches, deadline,
                decisionCommentPolicy, 0, null
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Map<Long, Decision> decisions,
            List<ApprovalBranchExecution> parallelBranches,
            ApprovalDeadlineState deadline
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, recordBinding, approvalMode,
                requiredApprovals, decisions, parallelBranches, deadline, null
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Map<Long, Decision> decisions,
            List<ApprovalBranchExecution> parallelBranches
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, recordBinding, approvalMode,
                requiredApprovals, decisions, parallelBranches, null, null
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding,
            ApprovalMode approvalMode,
            Map<Long, Decision> decisions,
            List<ApprovalBranchExecution> parallelBranches
    ) {
        this(
                id,
                definitionId,
                definitionVersion,
                businessKey,
                requesterId,
                approverId,
                approverIds,
                status,
                startedAt,
                completedAt,
                history,
                currentStepIndex,
                claimState,
                recordBinding,
                approvalMode,
                ApprovalQuorumRules.requiredApprovals(
                        approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode,
                        null,
                        approverIds == null ? 0 : approverIds.size()
                ),
                decisions,
                parallelBranches,
                null,
                null
        );
    }

    public ApprovalInstance {
        if (id <= 0 || definitionId <= 0) {
            throw new IllegalArgumentException("Instance and definition ids must be positive");
        }
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("Definition version must be positive");
        }
        businessKey = ApprovalDefinitionDraft.requireText(businessKey, "Business key");
        if (requesterId <= 0 || approverId <= 0) {
            throw new IllegalArgumentException("Requester and approver ids must be positive");
        }
        approverIds = ApprovalDefinitionDraft.requireApprovers(approverIds);
        approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
        ApprovalDefinitionDraft.requireApprovalModeApprovers(approvalMode, approverIds);
        requiredApprovals = ApprovalBranchExecution.requireRuntimeThreshold(
                approvalMode,
                requiredApprovals,
                approverIds.size()
        );
        if (!approverIds.contains(approverId)) {
            throw new IllegalArgumentException("Current approver must belong to the published sequence");
        }
        Objects.requireNonNull(status, "status");
        completionPhase = completionPhase == null
                ? status == Status.PENDING
                ? CompletionPhase.HUMAN_APPROVAL
                : CompletionPhase.COMPLETED
                : completionPhase;
        completionExecutions = completionExecutions == null
                ? List.of()
                : List.copyOf(completionExecutions);
        completionFailurePolicy = completionFailurePolicy == null
                ? CompletionFailurePolicy.MANUAL_RETRY
                : completionFailurePolicy;
        if (completionPhase == CompletionPhase.COMPENSATING
                && completionFailurePolicy != CompletionFailurePolicy.COMPENSATE) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Compensating phase requires COMPENSATE failure policy");
        }
        activeCompletionOrdinal = requireCompletionPhase(
                id, definitionId, definitionVersion, status, completionPhase,
                activeCompletionOrdinal, completionExecutions);
        Objects.requireNonNull(startedAt, "startedAt");
        decisionCommentPolicy = decisionCommentPolicy == null
                ? ApprovalDecisionCommentPolicy.defaults()
                : decisionCommentPolicy;
        if (deadline != null && !deadline.dueAt().isAfter(startedAt)) {
            throw new IllegalArgumentException("Approval deadline must be after its start");
        }
        history = List.copyOf(history);
        if (currentStepIndex < 0 || currentStepIndex >= approverIds.size()) {
            throw new IllegalArgumentException("Current approval step is outside the runtime sequence");
        }
        if (approvalMode == ApprovalMode.SEQUENTIAL
                && approverIds.get(currentStepIndex) != approverId) {
            throw new IllegalArgumentException("Current approver must match the runtime sequence cursor");
        }
        Objects.requireNonNull(claimState, "claimState");
        decisions = requireDecisions(approvalMode, approverIds, decisions);
        parallelBranches = parallelBranches == null ? List.of() : List.copyOf(parallelBranches);
        if (!parallelBranches.isEmpty()) {
            requireParallelBranches(parallelBranches);
            var first = parallelBranches.getFirst();
            if (decisionEvidencePolicy == null) {
                decisionEvidencePolicy = first.decisionEvidencePolicy();
            }
            if (!approverIds.equals(first.approverIds())
                    || approvalMode != first.approvalMode()
                    || requiredApprovals != first.requiredApprovals()
                    || approverId != first.approverId()
                    || currentStepIndex != first.currentStepIndex()
                    || !Objects.equals(deadline, first.deadline())
                    || !Objects.equals(
                             decisionCommentPolicy,
                             first.decisionCommentPolicy()
                     )
                    || !Objects.equals(
                    decisionEvidencePolicy,
                    first.decisionEvidencePolicy())) {
                throw new IllegalArgumentException(
                        "Parent route snapshot must mirror the first parallel branch"
                );
            }
            requireParallelStatus(status, completionPhase, parallelBranches);
        }
        if (approvalMode == ApprovalMode.SEQUENTIAL && !decisions.isEmpty()) {
            throw new IllegalArgumentException("Sequential approval instances do not store concurrent decisions");
        }
        if (approvalMode != ApprovalMode.SEQUENTIAL && claimState != ClaimState.CLAIMED) {
            throw new IllegalArgumentException("Concurrent approval instances cannot expose an open claim");
        }
        if (status == Status.PENDING && completedAt != null) {
            throw new IllegalArgumentException("Pending instance cannot have a completion time");
        }
        if (status != Status.PENDING && completedAt == null) {
            throw new IllegalArgumentException("Terminal instance requires a completion time");
        }
        if (status == Status.PENDING
                && completionPhase == CompletionPhase.HUMAN_APPROVAL
                && approvalMode != ApprovalMode.SEQUENTIAL
                && decisions.size() >= approverIds.size()) {
            throw new IllegalArgumentException("Pending concurrent approval requires an undecided member");
        }
        var approvalStatus = completionPhase
                == CompletionPhase.EXTERNAL_EXECUTION
                || completionPhase == CompletionPhase.COMPENSATING
                ? Status.APPROVED : status;
        var approvalCompletedAt = completionPhase
                == CompletionPhase.EXTERNAL_EXECUTION
                || completionPhase == CompletionPhase.COMPENSATING
                ? history.getLast().occurredAt() : completedAt;
        stages = normalizeStages(
                stages,
                currentStageIndex,
                currentStepIndex,
                approverIds,
                approvalMode,
                requiredApprovals,
                approvalStatus,
                startedAt,
                approvalCompletedAt,
                history,
                deadline,
                decisionCommentPolicy,
                decisionEvidencePolicy
        );
        if (currentStageIndex < 0 || currentStageIndex >= stages.size()) {
            throw new IllegalArgumentException(
                    "Current approval stage is outside the runtime plan");
        }
        requireStageShape(stages, currentStageIndex);
        var activeEvidencePolicy = stages.get(
                currentStageIndex).decisionEvidencePolicy();
        if (decisionEvidencePolicy == null) {
            decisionEvidencePolicy = activeEvidencePolicy;
        } else if (!decisionEvidencePolicy.equals(activeEvidencePolicy)) {
            throw new IllegalArgumentException(
                    "Instance evidence policy must mirror its active stage");
        }
        if (startContext != null) {
            if (startContext.requesterMemberId() != requesterId) {
                throw new IllegalArgumentException(
                        "Approval start context requester must match the instance");
            }
            var inheritedChildRecordFacts = recordBinding == null
                    && startContext.hasRecord()
                    && startContext.subflowDepth() > 0
                    && startContext.rootInstanceId() != null;
            if (recordBinding == null && startContext.hasRecord()
                    && !inheritedChildRecordFacts
                    || recordBinding != null
                    && (!startContext.hasRecord()
                    || !recordBinding.moduleCode().equals(
                            startContext.moduleCode())
                    || recordBinding.recordId() != startContext.recordId())) {
                throw new IllegalArgumentException(
                        "Approval start context must mirror the record binding");
            }
        }
    }

    public ApprovalInstance withDecisionEvidence(
            ApprovalDecisionEvidence evidence
    ) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.instanceId() != id
                || evidence.historySequence() > history.size()) {
            throw new IllegalArgumentException(
                    "Decision evidence does not belong to this instance history");
        }
        var nextHistory = new ArrayList<>(history);
        var eventIndex = evidence.historySequence() - 1;
        nextHistory.set(
                eventIndex,
                nextHistory.get(eventIndex).withEvidence(evidence)
        );
        return new ApprovalInstance(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt,
                nextHistory, currentStepIndex, claimState, recordBinding,
                approvalMode, requiredApprovals, decisions, parallelBranches,
                deadline, decisionCommentPolicy, currentStageIndex, stages,
                startContext, decisionEvidencePolicy, completionPhase,
                activeCompletionOrdinal, completionExecutions,
                completionFailurePolicy
        );
    }

    public ApprovalInstance withLatestDecisionEvidence(
            ApprovalDecisionEvidence evidence
    ) {
        Objects.requireNonNull(evidence, "evidence");
        if (history.isEmpty()
                || evidence.historySequence() != history.size()
                || history.getLast().evidence() != null) {
            throw new IllegalArgumentException(
                    "Decision evidence must bind the latest unbound history event");
        }
        return withDecisionEvidence(evidence);
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding,
            ApprovalMode approvalMode,
            Map<Long, Decision> decisions
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, recordBinding, approvalMode, decisions,
                List.of()
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState,
            RecordBinding recordBinding
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, recordBinding,
                ApprovalMode.SEQUENTIAL, Map.of()
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            int currentStepIndex,
            ClaimState claimState
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                currentStepIndex, claimState, null,
                ApprovalMode.SEQUENTIAL, Map.of()
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, status, startedAt, completedAt, history,
                legacyCursor(approverIds, history), ClaimState.CLAIMED, null,
                ApprovalMode.SEQUENTIAL, Map.of()
        );
    }

    public ApprovalInstance(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history
    ) {
        this(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, List.of(approverId), status, startedAt, completedAt, history,
                0, ClaimState.CLAIMED, null,
                ApprovalMode.SEQUENTIAL, Map.of()
        );
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            String businessKey,
            long requesterId,
            Instant startedAt
    ) {
        return start(id, definition, businessKey, requesterId, startedAt, null);
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding
    ) {
        return start(
                id, definition, businessKey, requesterId, startedAt,
                recordBinding, null);
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding,
            ApprovalStartContext startContext
    ) {
        Objects.requireNonNull(definition, "definition");
        if (definition.parallelGateway() != null) {
            return startBranches(
                    id,
                    definition,
                    definition.parallelGateway().branches(),
                    businessKey,
                    requesterId,
                    startedAt,
                    recordBinding,
                    startContext
            );
        }
        if (definition.inclusiveGateway() != null) {
            throw new IllegalArgumentException(
                    "Inclusive gateway requires a resolved branch selection"
            );
        }
        return startResolved(
                id,
                definition,
                definition.approverIds(),
                definition.approvalMode(),
                definition.quorumRules().requiredApprovals(
                        definition.approvalMode(),
                        definition.approverIds().size()
                ),
                definition.deadlinePolicies().primary(),
                definition.decisionCommentPolicies().primary(),
                definition.decisionEvidencePolicies().primary(),
                businessKey,
                requesterId,
                startedAt,
                recordBinding,
                startContext,
                definition.approvalStages()
        );
    }

    public static ApprovalInstance startBranch(
            long id,
            ApprovalDefinitionVersion definition,
            ApprovalBranchRoute selectedBranch,
            List<Long> resolvedApproverIds,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding,
            ApprovalStartContext startContext
    ) {
        Objects.requireNonNull(selectedBranch, "selectedBranch");
        return startResolved(
                id, definition, resolvedApproverIds,
                selectedBranch.approvalMode(),
                definition.quorumRules().requiredApprovals(
                        selectedBranch.code(), selectedBranch.approvalMode(),
                        resolvedApproverIds.size()),
                definition.deadlinePolicies().branch(selectedBranch.code()),
                definition.decisionCommentPolicies().branch(
                        selectedBranch.code()),
                definition.decisionEvidencePolicies().branch(
                        selectedBranch.code()),
                businessKey, requesterId, startedAt, recordBinding,
                startContext, selectedBranch.approvalStages());
    }

    public static ApprovalInstance startBranches(
            long id,
            ApprovalDefinitionVersion definition,
            List<? extends ApprovalBranchRoute> selectedBranches,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding
    ) {
        return startBranches(
                id, definition, selectedBranches, businessKey, requesterId,
                startedAt, recordBinding, null);
    }

    public static ApprovalInstance startBranches(
            long id,
            ApprovalDefinitionVersion definition,
            List<? extends ApprovalBranchRoute> selectedBranches,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding,
            ApprovalStartContext startContext
    ) {
        Objects.requireNonNull(selectedBranches, "selectedBranches");
        if (selectedBranches.isEmpty()) {
            throw new IllegalArgumentException("At least one branch must be selected");
        }
        var branches = selectedBranches.stream()
                .map(branch -> ApprovalBranchExecution.start(
                        branch,
                        definition.quorumRules().branch(branch.code()),
                        definition.deadlinePolicies().branch(branch.code()),
                        definition.decisionCommentPolicies().branch(branch.code()),
                        definition.decisionEvidencePolicies().branch(branch.code()),
                        startedAt
                ))
                .toList();
        var first = branches.getFirst();
        var started = new ApprovalHistoryEvent(
                ApprovalHistoryEvent.Type.STARTED,
                requesterId,
                null,
                Status.PENDING,
                "",
                startedAt
        );
        return new ApprovalInstance(
                id,
                definition.definitionId(),
                definition.version(),
                businessKey,
                requesterId,
                first.approverId(),
                first.approverIds(),
                Status.PENDING,
                startedAt,
                null,
                List.of(started),
                first.currentStepIndex(),
                ClaimState.CLAIMED,
                recordBinding,
                first.approvalMode(),
                first.requiredApprovals(),
                first.decisions(),
                branches,
                first.deadline(),
                first.decisionCommentPolicy(),
                0,
                null,
                startContext,
                first.decisionEvidencePolicy(),
                CompletionPhase.HUMAN_APPROVAL, null, List.of(),
                definition.completionFailurePolicy()
        );
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding
    ) {
        return start(
                id, definition, resolvedApproverIds, businessKey, requesterId,
                startedAt, recordBinding, null);
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding,
            ApprovalStartContext startContext
    ) {
        return startResolved(
                id,
                definition,
                resolvedApproverIds,
                definition.approvalMode(),
                definition.quorumRules().requiredApprovals(
                        definition.approvalMode(),
                        resolvedApproverIds.size()
                ),
                definition.deadlinePolicies().primary(),
                definition.decisionCommentPolicies().primary(),
                definition.decisionEvidencePolicies().primary(),
                businessKey,
                requesterId,
                startedAt,
                recordBinding,
                startContext,
                definition.approvalStages()
        );
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding
    ) {
        return start(
                id,
                definition,
                resolvedApproverIds,
                resolvedApprovalMode,
                definition.quorumRules().requiredApprovals(
                        resolvedApprovalMode,
                        resolvedApproverIds.size()
                ),
                businessKey,
                requesterId,
                startedAt,
                recordBinding
        );
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int resolvedRequiredApprovals,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding,
            ApprovalStartContext startContext
    ) {
        return startResolved(
                id,
                definition,
                resolvedApproverIds,
                resolvedApprovalMode,
                resolvedRequiredApprovals,
                deadlinePolicy,
                decisionCommentPolicy,
                definition.decisionEvidencePolicies().primary(),
                businessKey,
                requesterId,
                startedAt,
                recordBinding,
                startContext,
                definition.approvalStages()
        );
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int resolvedRequiredApprovals,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding
    ) {
        return start(
                id,
                definition,
                resolvedApproverIds,
                resolvedApprovalMode,
                resolvedRequiredApprovals,
                definition.deadlinePolicies().primary(),
                definition.decisionCommentPolicies().primary(),
                businessKey,
                requesterId,
                startedAt,
                recordBinding
        );
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int resolvedRequiredApprovals,
            ApprovalDeadlinePolicy deadlinePolicy,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding
    ) {
        return start(
                id,
                definition,
                resolvedApproverIds,
                resolvedApprovalMode,
                resolvedRequiredApprovals,
                deadlinePolicy,
                definition.decisionCommentPolicies().primary(),
                businessKey,
                requesterId,
                startedAt,
                recordBinding
        );
    }

    public static ApprovalInstance start(
            long id,
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int resolvedRequiredApprovals,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding
    ) {
        return startResolved(
                id,
                definition,
                resolvedApproverIds,
                resolvedApprovalMode,
                resolvedRequiredApprovals,
                deadlinePolicy,
                decisionCommentPolicy,
                definition.decisionEvidencePolicies().primary(),
                businessKey,
                requesterId,
                startedAt,
                recordBinding,
                null,
                definition.approvalStages()
        );
    }

    private static ApprovalInstance startResolved(
            long id,
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int resolvedRequiredApprovals,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            ApprovalDecisionEvidencePolicy decisionEvidencePolicy,
            String businessKey,
            long requesterId,
            Instant startedAt,
            RecordBinding recordBinding,
            ApprovalStartContext startContext,
            List<ApprovalStage> executionStages
    ) {
        Objects.requireNonNull(definition, "definition");
        resolvedApproverIds = ApprovalDefinitionDraft.requireApprovers(resolvedApproverIds);
        resolvedApprovalMode = resolvedApprovalMode == null
                ? ApprovalMode.SEQUENTIAL
                : resolvedApprovalMode;
        ApprovalDefinitionDraft.requireApprovalModeApprovers(
                resolvedApprovalMode,
                resolvedApproverIds
        );
        var started = new ApprovalHistoryEvent(
                ApprovalHistoryEvent.Type.STARTED,
                requesterId,
                null,
                Status.PENDING,
                "",
                startedAt
        );
        var deadlineState = ApprovalDeadlineState.start(deadlinePolicy, startedAt);
        var stageExecutions = initialStageExecutions(
                executionStages,
                resolvedApproverIds,
                resolvedApprovalMode,
                resolvedRequiredApprovals,
                deadlineState,
                decisionCommentPolicy,
                decisionEvidencePolicy,
                startedAt
        );
        return new ApprovalInstance(
                id,
                definition.definitionId(),
                definition.version(),
                businessKey,
                requesterId,
                resolvedApproverIds.getFirst(),
                resolvedApproverIds,
                Status.PENDING,
                startedAt,
                null,
                List.of(started),
                0,
                ClaimState.CLAIMED,
                recordBinding,
                resolvedApprovalMode,
                resolvedRequiredApprovals,
                Map.of(),
                List.of(),
                deadlineState,
                decisionCommentPolicy,
                0,
                stageExecutions,
                startContext,
                executionStages == null
                        ? decisionEvidencePolicy
                        : executionStages.getFirst().decisionEvidencePolicy(),
                CompletionPhase.HUMAN_APPROVAL, null, List.of(),
                definition.completionFailurePolicy()
        );
    }

    public ApprovalInstance approve(long actorId, String comment, Instant occurredAt) {
        return approve(actorId, actorId, null, comment, occurredAt);
    }

    public ApprovalInstance approve(
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            String comment,
            Instant occurredAt
    ) {
        requireNonParallelDecision();
        requireDecision(
                actorId,
                representedMemberId,
                delegationRuleId,
                occurredAt
        );
        var normalizedComment = decisionCommentPolicy.validateApproval(comment);
        if (approvalMode != ApprovalMode.SEQUENTIAL) {
            return concurrentDecision(
                    actorId,
                    representedMemberId,
                    delegationRuleId,
                    Decision.APPROVED,
                    normalizedComment,
                    occurredAt
            );
        }
        if (currentStepIndex + 1 < approverIds.size()) {
            return transition(
                    actorId,
                    representedMemberId,
                    delegationRuleId,
                    approverIds.get(currentStepIndex + 1),
                    currentStepIndex + 1,
                    Status.PENDING,
                    ApprovalHistoryEvent.Type.APPROVED,
                    normalizedComment,
                    occurredAt
            );
        }
        return transition(
                actorId,
                representedMemberId,
                delegationRuleId,
                approverId,
                currentStepIndex,
                Status.APPROVED,
                ApprovalHistoryEvent.Type.APPROVED,
                normalizedComment,
                occurredAt
        );
    }

    /**
     * Converts the transient final human-approval result into the durable
     * external-execution phase. The caller persists this instance and all
     * immutable execution snapshots in the same transaction.
     */
    public ApprovalInstance beginCompletion(
            List<ApprovalCompletionExecution> executions
    ) {
        var finalApproval = history.isEmpty() ? null : history.getLast();
        if (status != Status.APPROVED
                || completionPhase != CompletionPhase.COMPLETED
                || !completionExecutions.isEmpty()
                || finalApproval == null
                || finalApproval.toStatus() != Status.APPROVED
                || finalApproval.type() != ApprovalHistoryEvent.Type.APPROVED
                && finalApproval.type()
                != ApprovalHistoryEvent.Type.DEADLINE_AUTO_APPROVED) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Completion starts only after final human approval");
        }
        var snapshot = List.copyOf(executions);
        var externalHistory = new ArrayList<>(history);
        externalHistory.set(
                externalHistory.size() - 1,
                new ApprovalHistoryEvent(
                        finalApproval.type(), finalApproval.actorId(),
                        finalApproval.fromStatus(), Status.PENDING,
                        finalApproval.comment(), finalApproval.occurredAt(),
                        finalApproval.targetMemberId(),
                        finalApproval.assignmentPosition(),
                        finalApproval.targetStepIndex(),
                        finalApproval.representedMemberId(),
                        finalApproval.delegationRuleId(),
                        finalApproval.evidence()));
        return new ApprovalInstance(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, Status.PENDING, startedAt, null,
                externalHistory, currentStepIndex, claimState, recordBinding,
                approvalMode, requiredApprovals, decisions, parallelBranches,
                deadline, decisionCommentPolicy, currentStageIndex, stages,
                startContext, decisionEvidencePolicy,
                CompletionPhase.EXTERNAL_EXECUTION, 0, snapshot,
                completionFailurePolicy
        );
    }

    public ApprovalCompletionExecution activeCompletionExecution() {
        if (completionPhase != CompletionPhase.EXTERNAL_EXECUTION
                || activeCompletionOrdinal == null) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Instance has no active completion execution");
        }
        return completionExecutions.get(activeCompletionOrdinal);
    }

    public List<Integer> activeCompletionOrdinals() {
        if (completionPhase != CompletionPhase.EXTERNAL_EXECUTION
                || activeCompletionOrdinal == null) {
            return List.of();
        }
        return ApprovalCompletionStage.stageAtOrdinal(
                completionExecutions, activeCompletionOrdinal).ordinals();
    }

    /**
     * Replaces only mutable execution state after the repository has applied
     * optimistic transitions. Immutable identity/config/payload is verified by
     * each execution and by the aggregate cursor invariant.
     */
    public ApprovalInstance advanceCompletion(
            List<ApprovalCompletionExecution> executions
    ) {
        if (completionPhase != CompletionPhase.EXTERNAL_EXECUTION) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only external execution phase can advance");
        }
        var snapshot = List.copyOf(executions);
        var nextActive = -1;
        for (var execution : snapshot) {
            if (execution.status()
                    != ApprovalCompletionExecution.Status.SUCCEEDED) {
                nextActive = execution.ordinal();
                break;
            }
        }
        if (nextActive < 0) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Use completeCompletion for the final successful step");
        }
        nextActive = ApprovalCompletionStage.stageAtOrdinal(
                snapshot, nextActive).cursor();
        return copyCompletionState(
                Status.PENDING, null, history,
                CompletionPhase.EXTERNAL_EXECUTION,
                nextActive, snapshot);
    }

    public ApprovalInstance completeCompletion(
            List<ApprovalCompletionExecution> executions,
            long actorId,
            Instant occurredAt
    ) {
        if (completionPhase != CompletionPhase.EXTERNAL_EXECUTION
                || actorId <= 0) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only an active completion instance can complete");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        var snapshot = List.copyOf(executions);
        requireCompletionExecutionIdentity(
                id, definitionId, definitionVersion, snapshot);
        if (snapshot.isEmpty()
                || snapshot.stream().anyMatch(execution ->
                execution.status()
                        != ApprovalCompletionExecution.Status.SUCCEEDED)) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "All completion executions must succeed before completion");
        }
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(new ApprovalHistoryEvent(
                ApprovalHistoryEvent.Type.COMPLETION_COMPLETED,
                actorId, Status.PENDING, Status.APPROVED, "",
                occurredAt));
        return copyCompletionState(
                Status.APPROVED, occurredAt, nextHistory,
                CompletionPhase.COMPLETED, null, snapshot);
    }

    /**
     * Freezes the closed forward executions and switches the parent into the
     * independently persisted compensation phase.
     */
    public ApprovalInstance beginCompensation(
            List<ApprovalCompletionExecution> closedOriginals
    ) {
        if (completionFailurePolicy != CompletionFailurePolicy.COMPENSATE
                || completionPhase != CompletionPhase.EXTERNAL_EXECUTION
                || status != Status.PENDING) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Compensation starts only for a compensating completion policy");
        }
        var snapshot = List.copyOf(closedOriginals);
        requireClosedFailedCompletionExecutions(snapshot);
        return copyCompletionState(
                Status.PENDING, null, history,
                CompletionPhase.COMPENSATING, null, snapshot);
    }

    public ApprovalInstance advanceCompensation(
            List<ApprovalCompletionExecution> closedOriginals
    ) {
        if (completionPhase != CompletionPhase.COMPENSATING
                || status != Status.PENDING) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only a compensating instance can advance");
        }
        var snapshot = List.copyOf(closedOriginals);
        requireClosedFailedCompletionExecutions(snapshot);
        return copyCompletionState(
                Status.PENDING, null, history,
                CompletionPhase.COMPENSATING, null, snapshot);
    }

    public ApprovalInstance completeCompensation(
            List<ApprovalCompletionExecution> closedOriginals,
            long actorId,
            Instant occurredAt
    ) {
        if (completionPhase != CompletionPhase.COMPENSATING || actorId <= 0) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only a compensating instance can complete compensation");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        var snapshot = List.copyOf(closedOriginals);
        requireClosedFailedCompletionExecutions(snapshot);
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(new ApprovalHistoryEvent(
                ApprovalHistoryEvent.Type.COMPLETION_COMPENSATED,
                actorId, Status.PENDING, Status.TERMINATED, "", occurredAt));
        return copyCompletionState(
                Status.TERMINATED, occurredAt, nextHistory,
                CompletionPhase.COMPLETED, null, snapshot);
    }

    private void requireClosedFailedCompletionExecutions(
            List<ApprovalCompletionExecution> executions
    ) {
        requireCompletionExecutionIdentity(
                id, definitionId, definitionVersion, executions);
        if (executions.isEmpty()
                || executions.stream().noneMatch(execution ->
                execution.status() == ApprovalCompletionExecution.Status.FAILED)
                || executions.stream().anyMatch(execution ->
                execution.status() != ApprovalCompletionExecution.Status.SUCCEEDED
                        && execution.status()
                        != ApprovalCompletionExecution.Status.FAILED
                        && execution.status()
                        != ApprovalCompletionExecution.Status.CANCELLED)) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Compensation requires a closed forward plan with a failure");
        }
    }

    public ApprovalInstance withTerminatedCompletionExecutions(
            List<ApprovalCompletionExecution> executions
    ) {
        if (status != Status.TERMINATED) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only a terminated instance can attach cancelled executions");
        }
        return copyCompletionState(
                Status.TERMINATED, completedAt, history,
                CompletionPhase.COMPLETED, null, List.copyOf(executions));
    }

    private ApprovalInstance copyCompletionState(
            Status nextStatus,
            Instant nextCompletedAt,
            List<ApprovalHistoryEvent> nextHistory,
            CompletionPhase nextPhase,
            Integer nextActiveOrdinal,
            List<ApprovalCompletionExecution> nextExecutions
    ) {
        return new ApprovalInstance(
                id, definitionId, definitionVersion, businessKey, requesterId,
                approverId, approverIds, nextStatus, startedAt, nextCompletedAt,
                nextHistory, currentStepIndex, claimState, recordBinding,
                approvalMode, requiredApprovals, decisions, parallelBranches,
                deadline, decisionCommentPolicy, currentStageIndex, stages,
                startContext, decisionEvidencePolicy, nextPhase,
                nextActiveOrdinal, nextExecutions, completionFailurePolicy
        );
    }

    public List<Long> activeApproverIds() {
        if (status != Status.PENDING
                || completionPhase != CompletionPhase.HUMAN_APPROVAL) {
            return List.of();
        }
        if (isParallel()) {
            return parallelBranches.stream()
                    .flatMap(branch -> branch.activeApproverIds().stream())
                    .distinct()
                    .toList();
        }
        if (approvalMode == ApprovalMode.SEQUENTIAL) {
            return claimState == ClaimState.CLAIMED ? List.of(approverId) : List.of();
        }
        return approverIds.stream()
                .filter(memberId -> !decisions.containsKey(memberId))
                .toList();
    }

    public List<Long> approvedApproverIds() {
        if (isParallel()) {
            return parallelBranches.stream()
                    .flatMap(branch -> branch.approvedApproverIds().stream())
                    .distinct()
                    .toList();
        }
        return decisionMembers(Decision.APPROVED);
    }

    public List<Long> rejectedApproverIds() {
        if (isParallel()) {
            return parallelBranches.stream()
                    .flatMap(branch -> branch.rejectedApproverIds().stream())
                    .distinct()
                    .toList();
        }
        return decisionMembers(Decision.REJECTED);
    }

    public boolean hasApprovalTask(long memberId, ApprovalTaskStatus taskStatus) {
        Objects.requireNonNull(taskStatus, "taskStatus");
        return switch (taskStatus) {
            case PENDING -> status == Status.PENDING && activeApproverIds().contains(memberId);
            case COMPLETED -> status != Status.PENDING && allApproverIds().contains(memberId);
            case ALL -> status == Status.PENDING
                    ? activeApproverIds().contains(memberId)
                    : allApproverIds().contains(memberId);
        };
    }

    public void requireCurrentApprover(long actorId) {
        requireNonParallelMutation("Assignment");
        requireSequentialMutation("Assignment");
        if (actorId != approverId) {
            throw new ApprovalDomainException(APPROVER_FORBIDDEN, "Only the assigned approver can assign");
        }
        if (status != Status.PENDING
                || completionPhase != CompletionPhase.HUMAN_APPROVAL) {
            throw new ApprovalDomainException(INSTANCE_STATE_INVALID, "Only a pending instance can be assigned");
        }
        if (claimState != ClaimState.CLAIMED) {
            throw new ApprovalDomainException(INSTANCE_STATE_INVALID, "Only a claimed task can be handled");
        }
    }

    public ApprovalInstance transfer(
            long actorId,
            long targetMemberId,
            String reason,
            Instant occurredAt
    ) {
        requireCurrentApprover(actorId);
        var normalizedReason = assignmentReason(reason);
        requireAssignmentTarget(targetMemberId);
        Objects.requireNonNull(occurredAt, "occurredAt");
        var nextApprovers = new ArrayList<>(approverIds);
        nextApprovers.set(currentStepIndex, targetMemberId);
        return assignmentTransition(
                actorId,
                targetMemberId,
                targetMemberId,
                nextApprovers,
                currentStepIndex,
                ClaimState.CLAIMED,
                ApprovalHistoryEvent.Type.TRANSFERRED,
                null,
                normalizedReason,
                occurredAt
        );
    }

    public ApprovalInstance addSign(
            long actorId,
            long targetMemberId,
            ApprovalHistoryEvent.AssignmentPosition position,
            String reason,
            Instant occurredAt
    ) {
        requireCurrentApprover(actorId);
        var normalizedReason = assignmentReason(reason);
        requireAssignmentTarget(targetMemberId);
        if (position == null || approverIds.size() >= ApprovalDefinitionDraft.MAX_APPROVERS) {
            throw assignmentInvalid(
                    "Add-sign position must be BEFORE or AFTER and the resulting sequence may contain at most 10 steps"
            );
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        var insertIndex = position == ApprovalHistoryEvent.AssignmentPosition.BEFORE
                ? currentStepIndex
                : currentStepIndex + 1;
        var nextApprovers = new ArrayList<>(approverIds);
        nextApprovers.add(insertIndex, targetMemberId);
        var nextApproverId = position == ApprovalHistoryEvent.AssignmentPosition.BEFORE
                ? targetMemberId
                : approverId;
        return assignmentTransition(
                actorId,
                targetMemberId,
                nextApproverId,
                nextApprovers,
                currentStepIndex,
                ClaimState.CLAIMED,
                ApprovalHistoryEvent.Type.ADD_SIGNED,
                position,
                normalizedReason,
                occurredAt
        );
    }

    public ApprovalInstance returnToPrevious(long actorId, String reason, Instant occurredAt) {
        requireCurrentApprover(actorId);
        var normalizedReason = requiredReason(
                reason,
                RETURN_REQUEST_INVALID,
                "A return reason of 1 to 500 characters is required"
        );
        if (currentStepIndex == 0) {
            throw new ApprovalDomainException(
                    RETURN_REQUEST_INVALID,
                    "The first approval step cannot be returned"
            );
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        var previousIndex = currentStepIndex - 1;
        var previousApprover = approverIds.get(previousIndex);
        return assignmentTransition(
                actorId,
                previousApprover,
                previousApprover,
                approverIds,
                previousIndex,
                ClaimState.CLAIMED,
                ApprovalHistoryEvent.Type.RETURNED,
                null,
                normalizedReason,
                occurredAt
        );
    }

    public ApprovalInstance cancelClaim(long actorId, String reason, Instant occurredAt) {
        requireCurrentApprover(actorId);
        var normalizedReason = requiredReason(
                reason,
                CANCEL_CLAIM_REQUEST_INVALID,
                "A cancel-claim reason of 1 to 500 characters is required"
        );
        Objects.requireNonNull(occurredAt, "occurredAt");
        return assignmentTransition(
                actorId,
                null,
                approverId,
                approverIds,
                currentStepIndex,
                ClaimState.OPEN,
                ApprovalHistoryEvent.Type.CLAIM_CANCELLED,
                null,
                normalizedReason,
                occurredAt
        );
    }

    public ApprovalInstance claim(long actorId, String comment, Instant occurredAt) {
        requireSequentialMutation("Claim");
        if (status != Status.PENDING
                || completionPhase != CompletionPhase.HUMAN_APPROVAL
                || claimState != ClaimState.OPEN) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only an open pending task can be claimed"
            );
        }
        if (actorId <= 0) {
            throw claimInvalid("Claimant must be a positive member id");
        }
        var normalizedComment = optionalComment(comment);
        for (var index = 0; index < approverIds.size(); index++) {
            if (index != currentStepIndex && approverIds.get(index) == actorId) {
                throw claimInvalid("Claimant already occurs at another runtime approval step");
            }
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        var nextApprovers = new ArrayList<>(approverIds);
        nextApprovers.set(currentStepIndex, actorId);
        return assignmentTransition(
                actorId,
                actorId,
                actorId,
                nextApprovers,
                currentStepIndex,
                ClaimState.CLAIMED,
                ApprovalHistoryEvent.Type.CLAIMED,
                null,
                normalizedComment,
                occurredAt
        );
    }

    public ApprovalInstance reduceSign(
            long actorId,
            Integer targetStepIndex,
            String reason,
            Instant occurredAt
    ) {
        requireCurrentApprover(actorId);
        var normalizedReason = requiredReason(
                reason,
                REDUCE_SIGN_REQUEST_INVALID,
                "A reduce-sign reason of 1 to 500 characters is required"
        );
        if (targetStepIndex == null
                || targetStepIndex <= currentStepIndex
                || targetStepIndex >= approverIds.size()) {
            throw new ApprovalDomainException(
                    REDUCE_SIGN_REQUEST_INVALID,
                    "Reduce-sign target must identify a future runtime approval step"
            );
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        var removedMemberId = approverIds.get(targetStepIndex);
        var nextApprovers = new ArrayList<>(approverIds);
        nextApprovers.remove((int) targetStepIndex);
        return assignmentTransition(
                actorId,
                removedMemberId,
                approverId,
                nextApprovers,
                currentStepIndex,
                claimState,
                ApprovalHistoryEvent.Type.SIGN_REMOVED,
                null,
                normalizedReason,
                occurredAt,
                targetStepIndex
        );
    }

    public ApprovalInstance reject(long actorId, String reason, Instant occurredAt) {
        return reject(actorId, actorId, null, reason, occurredAt);
    }

    public ApprovalInstance reject(
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            String reason,
            Instant occurredAt
    ) {
        requireNonParallelDecision();
        requireDecision(
                actorId,
                representedMemberId,
                delegationRuleId,
                occurredAt
        );
        var normalizedReason = decisionCommentPolicy.validateRejection(reason);
        if (approvalMode != ApprovalMode.SEQUENTIAL) {
            return concurrentDecision(
                    actorId,
                    representedMemberId,
                    delegationRuleId,
                    Decision.REJECTED,
                    normalizedReason,
                    occurredAt
            );
        }
        return transition(
                actorId,
                representedMemberId,
                delegationRuleId,
                approverId,
                currentStepIndex,
                Status.REJECTED,
                ApprovalHistoryEvent.Type.REJECTED,
                normalizedReason,
                occurredAt
        );
    }

    public boolean isParallel() {
        return !parallelBranches.isEmpty();
    }

    public ApprovalBranchExecution parallelBranch(String branchCode) {
        return parallelBranches.stream()
                .filter(branch -> branch.code().equals(branchCode))
                .findFirst()
                .orElseThrow(() -> new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Parallel branch execution was not found"
                ));
    }

    public ApprovalInstance approveBranch(
            String branchCode,
            long actorId,
            String comment,
            Instant occurredAt
    ) {
        return approveBranch(
                branchCode,
                actorId,
                actorId,
                null,
                comment,
                occurredAt
        );
    }

    public ApprovalInstance approveBranch(
            String branchCode,
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            String comment,
            Instant occurredAt
    ) {
        return decideBranch(
                branchCode,
                actorId,
                representedMemberId,
                delegationRuleId,
                true,
                comment,
                occurredAt
        );
    }

    public ApprovalInstance rejectBranch(
            String branchCode,
            long actorId,
            String reason,
            Instant occurredAt
    ) {
        return rejectBranch(
                branchCode,
                actorId,
                actorId,
                null,
                reason,
                occurredAt
        );
    }

    public ApprovalInstance rejectBranch(
            String branchCode,
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            String reason,
            Instant occurredAt
    ) {
        return decideBranch(
                branchCode,
                actorId,
                representedMemberId,
                delegationRuleId,
                false,
                reason,
                occurredAt
        );
    }

    public ApprovalDeadlineState deadline(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            if (isParallel()) {
                throw new IllegalArgumentException(
                        "Parallel approval deadlines require a branch code"
                );
            }
            return deadline;
        }
        return parallelBranch(branchCode).deadline();
    }

    public List<Long> deadlineRecipients(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            if (isParallel()) {
                throw new IllegalArgumentException(
                        "Parallel approval reminders require a branch code"
                );
            }
            return activeApproverIds();
        }
        return parallelBranch(branchCode).activeApproverIds();
    }

    public ApprovalInstance markDeadlineReminded(
            String branchCode,
            long actorId,
            Instant occurredAt
    ) {
        requirePendingDeadline(actorId, occurredAt);
        if (branchCode == null || branchCode.isBlank()) {
            if (isParallel()) {
                throw new IllegalArgumentException(
                        "Parallel approval reminders require a branch code"
                );
            }
            var nextDeadline = Objects.requireNonNull(
                    deadline,
                    "Approval deadline is not configured"
            ).markReminded(occurredAt);
            if (nextDeadline == deadline) {
                return this;
            }
            return deadlineTransition(
                    approvalMode,
                    requiredApprovals,
                    approverId,
                    currentStepIndex,
                    decisions,
                    parallelBranches,
                    nextDeadline,
                    Status.PENDING,
                    actorId,
                    ApprovalHistoryEvent.Type.DEADLINE_REMINDER_SENT,
                    "Approval deadline reminder sent",
                    occurredAt
            );
        }
        var currentBranch = parallelBranch(branchCode);
        var reminded = currentBranch.markDeadlineReminded(occurredAt);
        if (reminded == currentBranch) {
            return this;
        }
        var nextBranches = parallelBranches.stream()
                .map(branch -> branch.code().equals(branchCode) ? reminded : branch)
                .toList();
        var first = nextBranches.getFirst();
        return deadlineTransition(
                first.approvalMode(),
                first.requiredApprovals(),
                first.approverId(),
                first.currentStepIndex(),
                first.decisions(),
                nextBranches,
                first.deadline(),
                Status.PENDING,
                actorId,
                ApprovalHistoryEvent.Type.DEADLINE_REMINDER_SENT,
                "Approval deadline reminder sent for branch " + branchCode,
                occurredAt
        );
    }

    public ApprovalInstance processDeadline(
            String branchCode,
            long actorId,
            Instant occurredAt
    ) {
        if (branchCode == null || branchCode.isBlank()) {
            if (isParallel()) {
                throw new IllegalArgumentException(
                        "Parallel approval timeouts require a branch code"
                );
            }
            var currentDeadline = Objects.requireNonNull(
                    deadline,
                    "Approval deadline is not configured"
            );
            if (currentDeadline.processedAt() != null) {
                return this;
            }
            requirePendingDeadline(actorId, occurredAt);
            var processed = currentDeadline.markProcessed(occurredAt);
            var nextStatus = switch (currentDeadline.policy().timeoutAction()) {
                case NONE -> Status.PENDING;
                case AUTO_APPROVE -> Status.APPROVED;
                case AUTO_REJECT -> Status.REJECTED;
            };
            return deadlineTransition(
                    approvalMode,
                    requiredApprovals,
                    approverId,
                    currentStepIndex,
                    decisions,
                    parallelBranches,
                    processed,
                    nextStatus,
                    actorId,
                    timeoutHistory(currentDeadline.policy().timeoutAction()),
                    timeoutComment(currentDeadline.policy().timeoutAction(), null),
                    occurredAt
            );
        }
        var currentBranch = parallelBranch(branchCode);
        var currentDeadline = Objects.requireNonNull(
                currentBranch.deadline(),
                "Approval branch deadline is not configured"
        );
        if (currentDeadline.processedAt() != null) {
            return this;
        }
        requirePendingDeadline(actorId, occurredAt);
        var processedBranch = currentBranch.processDeadline(occurredAt);
        if (processedBranch == currentBranch) {
            return this;
        }
        var nextBranches = parallelBranches.stream()
                .map(branch -> branch.code().equals(branchCode) ? processedBranch : branch)
                .toList();
        var nextStatus = processedBranch.status() == ApprovalBranchExecution.Status.REJECTED
                ? Status.REJECTED
                : nextBranches.stream().allMatch(
                        branch -> branch.status() == ApprovalBranchExecution.Status.APPROVED)
                        ? Status.APPROVED
                        : Status.PENDING;
        if (nextStatus == Status.REJECTED) {
            nextBranches = nextBranches.stream()
                    .map(branch -> branch.close(
                            ApprovalBranchExecution.Status.CANCELLED,
                            occurredAt
                    ))
                    .toList();
        }
        var first = nextBranches.getFirst();
        return deadlineTransition(
                first.approvalMode(),
                first.requiredApprovals(),
                first.approverId(),
                first.currentStepIndex(),
                first.decisions(),
                nextBranches,
                first.deadline(),
                nextStatus,
                actorId,
                timeoutHistory(currentDeadline.policy().timeoutAction()),
                timeoutComment(currentDeadline.policy().timeoutAction(), branchCode),
                occurredAt
        );
    }

    private ApprovalInstance decideBranch(
            String branchCode,
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            boolean approved,
            String comment,
            Instant occurredAt
    ) {
        if (!isParallel()) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "An explicit branch decision requires a parallel parent"
            );
        }
        if (status != Status.PENDING) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only a pending parallel parent can be decided"
            );
        }
        var target = parallelBranch(branchCode);
        var normalizedComment = approved
                ? target.decisionCommentPolicy().validateApproval(comment)
                : target.decisionCommentPolicy().validateRejection(comment);
        var decided = approved
                ? target.approve(
                        actorId,
                        representedMemberId,
                        delegationRuleId,
                        normalizedComment,
                        occurredAt
                )
                : target.reject(
                        actorId,
                        representedMemberId,
                        delegationRuleId,
                        normalizedComment,
                        occurredAt
                );
        var nextBranches = parallelBranches.stream()
                .map(branch -> branch.code().equals(branchCode) ? decided : branch)
                .toList();
        var nextStatus = decided.status() == ApprovalBranchExecution.Status.REJECTED
                ? Status.REJECTED
                : nextBranches.stream().allMatch(
                        branch -> branch.status() == ApprovalBranchExecution.Status.APPROVED)
                        ? Status.APPROVED
                        : Status.PENDING;
        if (nextStatus == Status.REJECTED) {
            nextBranches = nextBranches.stream()
                    .map(branch -> branch.close(
                            ApprovalBranchExecution.Status.CANCELLED,
                            occurredAt
                    ))
                    .toList();
        }
        var first = nextBranches.getFirst();
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(new ApprovalHistoryEvent(
                approved
                        ? ApprovalHistoryEvent.Type.APPROVED
                        : ApprovalHistoryEvent.Type.REJECTED,
                actorId,
                Status.PENDING,
                nextStatus,
                normalizedComment,
                occurredAt,
                null,
                null,
                null,
                delegationRuleId == null ? null : representedMemberId,
                delegationRuleId
        ));
        return new ApprovalInstance(
                id,
                definitionId,
                definitionVersion,
                businessKey,
                requesterId,
                first.approverId(),
                first.approverIds(),
                nextStatus,
                startedAt,
                nextStatus == Status.PENDING ? null : occurredAt,
                nextHistory,
                first.currentStepIndex(),
                ClaimState.CLAIMED,
                recordBinding,
                first.approvalMode(),
                first.requiredApprovals(),
                first.decisions(),
                nextBranches,
                first.deadline(),
                first.decisionCommentPolicy(),
                currentStageIndex,
                stages,
                startContext,
                decisionEvidencePolicy,
                nextStatus == Status.PENDING
                        ? completionPhase : CompletionPhase.COMPLETED,
                nextStatus == Status.PENDING ? activeCompletionOrdinal : null,
                completionExecutions,
                completionFailurePolicy
        );
    }

    private void requirePendingDeadline(long actorId, Instant occurredAt) {
        if (actorId <= 0) {
            throw new IllegalArgumentException("Deadline actor id must be positive");
        }
        if (status != Status.PENDING) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only a pending approval can process a deadline"
            );
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private ApprovalInstance deadlineTransition(
            ApprovalMode nextMode,
            int nextRequiredApprovals,
            long nextApproverId,
            int nextStepIndex,
            Map<Long, Decision> nextDecisions,
            List<ApprovalBranchExecution> nextBranches,
            ApprovalDeadlineState nextDeadline,
            Status nextStatus,
            long actorId,
            ApprovalHistoryEvent.Type eventType,
            String comment,
            Instant occurredAt
    ) {
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(new ApprovalHistoryEvent(
                eventType,
                actorId,
                status,
                nextStatus,
                comment,
                occurredAt
        ));
        return new ApprovalInstance(
                id,
                definitionId,
                definitionVersion,
                businessKey,
                requesterId,
                nextApproverId,
                nextBranches.isEmpty() ? approverIds : nextBranches.getFirst().approverIds(),
                nextStatus,
                startedAt,
                nextStatus == Status.PENDING ? null : occurredAt,
                nextHistory,
                nextStepIndex,
                ClaimState.CLAIMED,
                recordBinding,
                nextMode,
                nextRequiredApprovals,
                nextDecisions,
                nextBranches,
                nextDeadline,
                nextBranches.isEmpty()
                        ? decisionCommentPolicy
                        : nextBranches.getFirst().decisionCommentPolicy(),
                currentStageIndex,
                stages,
                startContext,
                decisionEvidencePolicy,
                nextStatus == Status.PENDING
                        ? completionPhase : CompletionPhase.COMPLETED,
                nextStatus == Status.PENDING ? activeCompletionOrdinal : null,
                completionExecutions,
                completionFailurePolicy
        );
    }

    private static ApprovalHistoryEvent.Type timeoutHistory(
            ApprovalDeadlinePolicy.TimeoutAction action
    ) {
        return switch (action) {
            case NONE -> ApprovalHistoryEvent.Type.DEADLINE_OVERDUE;
            case AUTO_APPROVE -> ApprovalHistoryEvent.Type.DEADLINE_AUTO_APPROVED;
            case AUTO_REJECT -> ApprovalHistoryEvent.Type.DEADLINE_AUTO_REJECTED;
        };
    }

    private static String timeoutComment(
            ApprovalDeadlinePolicy.TimeoutAction action,
            String branchCode
    ) {
        var route = branchCode == null ? "route" : "branch " + branchCode;
        return switch (action) {
            case NONE -> "Approval deadline overdue for " + route;
            case AUTO_APPROVE -> "Approval automatically approved after deadline for " + route;
            case AUTO_REJECT -> "Approval automatically rejected after deadline for " + route;
        };
    }

    public ApprovalInstance withdraw(long actorId, String reason, Instant occurredAt) {
        var normalizedReason = reason == null ? "" : reason.strip();
        var reasonLength = normalizedReason.codePointCount(0, normalizedReason.length());
        if (reasonLength < 1 || reasonLength > 500) {
            throw new ApprovalDomainException(
                    WITHDRAW_REASON_REQUIRED,
                    "A withdrawal reason of 1 to 500 characters is required"
            );
        }
        if (actorId != requesterId) {
            throw new ApprovalDomainException(
                    REQUESTER_FORBIDDEN,
                    "Only the instance requester can withdraw"
            );
        }
        if (status != Status.PENDING) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only a pending instance can be decided"
            );
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(new ApprovalHistoryEvent(
                ApprovalHistoryEvent.Type.WITHDRAWN,
                actorId,
                status,
                Status.WITHDRAWN,
                normalizedReason,
                occurredAt
        ));
        var nextBranches = parallelBranches.stream()
                .map(branch -> branch.close(
                        ApprovalBranchExecution.Status.WITHDRAWN,
                        occurredAt
                ))
                .toList();
        var nextExecutions = completionExecutions.stream()
                .map(execution -> switch (execution.status()) {
                    case WAITING, AVAILABLE, LEASED, RETRYING, RUNNING ->
                            execution.cancel(occurredAt);
                    case SUCCEEDED, FAILED, CANCELLED -> execution;
                })
                .toList();
        return new ApprovalInstance(
                id,
                definitionId,
                definitionVersion,
                businessKey,
                requesterId,
                approverId,
                approverIds,
                Status.WITHDRAWN,
                startedAt,
                occurredAt,
                nextHistory,
                currentStepIndex,
                claimState,
                recordBinding,
                approvalMode,
                requiredApprovals,
                decisions,
                nextBranches,
                deadline,
                decisionCommentPolicy,
                currentStageIndex,
                stages,
                startContext,
                decisionEvidencePolicy,
                CompletionPhase.COMPLETED,
                null,
                nextExecutions,
                completionFailurePolicy
        );
    }

    private List<ApprovalStageExecution> stagesAfterAssignment(
            List<Long> nextApprovers,
            int nextStepIndex,
            ApprovalHistoryEvent.Type eventType
    ) {
        if (eventType != ApprovalHistoryEvent.Type.RETURNED
                || stages.size() == 1
                && stages.getFirst().code().equals("legacy")) {
            return stages;
        }
        var next = new ArrayList<>(stages);
        next.set(
                currentStageIndex,
                currentStage().removeHandlersFrom(nextApprovers, nextStepIndex)
        );
        return List.copyOf(next);
    }

    public ApprovalInstance terminate(long actorId, String reason, Instant occurredAt) {
        var normalizedReason = reason == null ? "" : reason.strip();
        var reasonLength = normalizedReason.codePointCount(0, normalizedReason.length());
        if (reasonLength < 1 || reasonLength > 500) {
            throw new ApprovalDomainException(
                    TERMINATE_REASON_REQUIRED,
                    "A termination reason of 1 to 500 characters is required"
            );
        }
        if (status != Status.PENDING) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only a pending instance can be decided"
            );
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(new ApprovalHistoryEvent(
                ApprovalHistoryEvent.Type.TERMINATED,
                actorId,
                status,
                Status.TERMINATED,
                normalizedReason,
                occurredAt
        ));
        var nextBranches = parallelBranches.stream()
                .map(branch -> branch.close(
                        ApprovalBranchExecution.Status.TERMINATED,
                        occurredAt
                ))
                .toList();
        var nextExecutions = completionExecutions.stream()
                .map(execution -> switch (execution.status()) {
                    case WAITING, AVAILABLE, LEASED, RETRYING, RUNNING ->
                            execution.cancel(occurredAt);
                    case SUCCEEDED, FAILED, CANCELLED -> execution;
                })
                .toList();
        return new ApprovalInstance(
                id,
                definitionId,
                definitionVersion,
                businessKey,
                requesterId,
                approverId,
                approverIds,
                Status.TERMINATED,
                startedAt,
                occurredAt,
                nextHistory,
                currentStepIndex,
                claimState,
                recordBinding,
                approvalMode,
                requiredApprovals,
                decisions,
                nextBranches,
                deadline,
                decisionCommentPolicy,
                currentStageIndex,
                stages,
                startContext,
                decisionEvidencePolicy,
                CompletionPhase.COMPLETED,
                null,
                nextExecutions,
                completionFailurePolicy
        );
    }

    private List<ApprovalStageExecution> stagesAfterDecision(
            long actorId,
            long representedMemberId,
            boolean approved
    ) {
        if (!approved
                || stages.size() == 1
                && stages.getFirst().code().equals("legacy")) {
            return stages;
        }
        var current = currentStage();
        if (current.status() != ApprovalStageExecution.Status.ACTIVE) {
            throw new IllegalStateException(
                    "Only the active approval stage can record a decision");
        }
        var next = new ArrayList<>(stages);
        next.set(
                currentStageIndex,
                current.recordApproval(representedMemberId, actorId)
        );
        return List.copyOf(next);
    }

    private void requireDecision(
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            Instant occurredAt
    ) {
        if (status != Status.PENDING
                || completionPhase != CompletionPhase.HUMAN_APPROVAL) {
            throw new ApprovalDomainException(INSTANCE_STATE_INVALID, "Only a pending instance can be decided");
        }
        if (claimState != ClaimState.CLAIMED) {
            throw new ApprovalDomainException(INSTANCE_STATE_INVALID, "Only a claimed task can be decided");
        }
        requireRepresentation(actorId, representedMemberId, delegationRuleId);
        if (approvalMode == ApprovalMode.SEQUENTIAL
                && representedMemberId != approverId) {
            throw new ApprovalDomainException(APPROVER_FORBIDDEN, "Only the assigned approver can decide");
        }
        if (approvalMode != ApprovalMode.SEQUENTIAL
                && (!approverIds.contains(representedMemberId)
                || decisions.containsKey(representedMemberId))) {
            throw new ApprovalDomainException(
                    APPROVER_FORBIDDEN,
                    "Only an undecided route member can decide"
            );
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private ApprovalInstance concurrentDecision(
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            Decision decision,
            String comment,
            Instant occurredAt
    ) {
        var nextDecisions = new LinkedHashMap<>(decisions);
        nextDecisions.put(representedMemberId, decision);
        if (approvalMode == ApprovalMode.SEQUENTIAL) {
            throw new IllegalStateException(
                    "Sequential decisions must use the ordered transition"
            );
        }
        var approvals = nextDecisions.values().stream()
                .filter(value -> value == Decision.APPROVED)
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
        var nextStepIndex = approverIds.indexOf(nextApproverId);
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(new ApprovalHistoryEvent(
                decision == Decision.APPROVED
                        ? ApprovalHistoryEvent.Type.APPROVED
                        : ApprovalHistoryEvent.Type.REJECTED,
                actorId,
                Status.PENDING,
                target,
                comment,
                occurredAt,
                null,
                null,
                null,
                delegationRuleId == null ? null : representedMemberId,
                delegationRuleId
        ));
        return new ApprovalInstance(
                id,
                definitionId,
                definitionVersion,
                businessKey,
                requesterId,
                nextApproverId,
                approverIds,
                target,
                startedAt,
                target == Status.PENDING ? null : occurredAt,
                nextHistory,
                nextStepIndex,
                ClaimState.CLAIMED,
                recordBinding,
                approvalMode,
                requiredApprovals,
                nextDecisions,
                List.of(),
                deadline,
                decisionCommentPolicy,
                currentStageIndex,
                stagesAfterDecision(
                        actorId,
                        representedMemberId,
                        decision == Decision.APPROVED
                ),
                startContext,
                decisionEvidencePolicy,
                target == Status.PENDING
                        ? completionPhase : CompletionPhase.COMPLETED,
                target == Status.PENDING ? activeCompletionOrdinal : null,
                completionExecutions,
                completionFailurePolicy
        );
    }

    private void requireSequentialMutation(String action) {
        requireNonParallelMutation(action);
        if (approvalMode != ApprovalMode.SEQUENTIAL) {
            throw assignmentInvalid(action + " is supported only for sequential approval");
        }
    }

    private void requireNonParallelDecision() {
        if (isParallel()) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Parallel approvals require an explicit branch execution"
            );
        }
    }

    private void requireNonParallelMutation(String action) {
        if (isParallel()) {
            throw assignmentInvalid(action + " is not supported for a parallel parent");
        }
    }

    private List<Long> allApproverIds() {
        if (!isParallel()) {
            return approverIds;
        }
        return parallelBranches.stream()
                .flatMap(branch -> branch.approverIds().stream())
                .distinct()
                .toList();
    }

    private static void requireParallelBranches(List<ApprovalBranchExecution> branches) {
        if (branches.isEmpty()
                || branches.size() > ApprovalParallelGateway.MAX_BRANCHES
                || branches.stream().map(ApprovalBranchExecution::code).distinct().count()
                        != branches.size()
                || branches.stream().map(ApprovalBranchExecution::name).distinct().count()
                        != branches.size()) {
            throw new IllegalArgumentException("Parallel branch execution snapshot is invalid");
        }
    }

    private static void requireParallelStatus(
            Status parentStatus,
            CompletionPhase completionPhase,
            List<ApprovalBranchExecution> branches
    ) {
        var pending = branches.stream().anyMatch(
                branch -> branch.status() == ApprovalBranchExecution.Status.PENDING);
        var rejected = branches.stream().anyMatch(
                branch -> branch.status() == ApprovalBranchExecution.Status.REJECTED);
        var allApproved = branches.stream().allMatch(
                branch -> branch.status() == ApprovalBranchExecution.Status.APPROVED);
        var valid = switch (parentStatus) {
            case PENDING ->
                    completionPhase == CompletionPhase.EXTERNAL_EXECUTION
                            || completionPhase == CompletionPhase.COMPENSATING
                            ? allApproved
                            : pending && !rejected;
            case APPROVED -> allApproved;
            case REJECTED -> rejected && !pending;
            case WITHDRAWN -> branches.stream().allMatch(branch ->
                    branch.status() != ApprovalBranchExecution.Status.PENDING
                            && branch.status() != ApprovalBranchExecution.Status.REJECTED);
            case TERMINATED -> branches.stream().allMatch(branch ->
                    branch.status() != ApprovalBranchExecution.Status.PENDING
                            && branch.status() != ApprovalBranchExecution.Status.REJECTED);
        };
        if (!valid) {
            throw new IllegalArgumentException("Parallel parent and branch statuses are inconsistent");
        }
    }

    private static Integer requireCompletionPhase(
            long instanceId,
            long definitionId,
            int definitionVersion,
            Status status,
            CompletionPhase phase,
            Integer activeOrdinal,
            List<ApprovalCompletionExecution> executions
    ) {
        if (phase == CompletionPhase.HUMAN_APPROVAL) {
            if (status != Status.PENDING
                    || activeOrdinal != null
                    || !executions.isEmpty()) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Human approval phase cannot contain completion execution state");
            }
            return null;
        }
        if (phase == CompletionPhase.COMPLETED) {
            if (status == Status.PENDING || activeOrdinal != null) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Completed phase requires a terminal approval instance");
            }
            requireCompletionExecutionIdentity(
                    instanceId, definitionId, definitionVersion, executions);
            if (status == Status.APPROVED
                    && executions.stream().anyMatch(execution ->
                    execution.status()
                            != ApprovalCompletionExecution.Status.SUCCEEDED)
                    || status == Status.TERMINATED
                    && executions.stream().anyMatch(execution ->
                    execution.status()
                            != ApprovalCompletionExecution.Status.SUCCEEDED
                            && execution.status()
                            != ApprovalCompletionExecution.Status.FAILED
                            && execution.status()
                            != ApprovalCompletionExecution.Status.CANCELLED)) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Terminal completion execution state is inconsistent");
            }
            return null;
        }
        if (phase == CompletionPhase.COMPENSATING) {
            if (status != Status.PENDING || activeOrdinal != null) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Compensating phase requires a pending parent without a forward cursor");
            }
            requireCompletionExecutionIdentity(
                    instanceId, definitionId, definitionVersion, executions);
            if (executions.isEmpty()
                    || executions.stream().noneMatch(execution ->
                    execution.status() == ApprovalCompletionExecution.Status.FAILED)
                    || executions.stream().anyMatch(execution ->
                    execution.status() != ApprovalCompletionExecution.Status.SUCCEEDED
                            && execution.status()
                            != ApprovalCompletionExecution.Status.FAILED
                            && execution.status()
                            != ApprovalCompletionExecution.Status.CANCELLED)) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Compensating phase requires a closed failed forward plan");
            }
            return null;
        }
        if (status != Status.PENDING
                || executions.isEmpty()
                || activeOrdinal == null
                || activeOrdinal < 0
                || activeOrdinal >= executions.size()) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "External execution phase requires one active completion step");
        }
        requireCompletionExecutionIdentity(
                instanceId, definitionId, definitionVersion, executions);
        var activeStage = ApprovalCompletionStage.stageAtOrdinal(
                executions, activeOrdinal);
        if (activeStage.cursor() != activeOrdinal) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Completion cursor must reference the stage start");
        }
        for (var ordinal = 0; ordinal < executions.size(); ordinal++) {
            var executionStatus = executions.get(ordinal).status();
            var valid = ordinal < activeStage.cursor()
                    ? executionStatus
                    == ApprovalCompletionExecution.Status.SUCCEEDED
                    : ordinal >= activeStage.endExclusive()
                    ? executionStatus
                    == ApprovalCompletionExecution.Status.WAITING
                    : executionStatus
                    == ApprovalCompletionExecution.Status.AVAILABLE
                    || executionStatus
                    == ApprovalCompletionExecution.Status.LEASED
                    || executionStatus
                    == ApprovalCompletionExecution.Status.RETRYING
                    || executionStatus
                    == ApprovalCompletionExecution.Status.RUNNING
                    || executionStatus
                    == ApprovalCompletionExecution.Status.SUCCEEDED
                    || executionStatus
                    == ApprovalCompletionExecution.Status.FAILED;
            if (!valid) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Completion execution cursor is corrupt");
            }
        }
        return activeOrdinal;
    }

    private static void requireCompletionExecutionIdentity(
            long instanceId,
            long definitionId,
            int definitionVersion,
            List<ApprovalCompletionExecution> executions
    ) {
        if (executions.size() > ApprovalCompletionStep.MAX_STEPS) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Completion execution plan exceeds its step limit");
        }
        for (var ordinal = 0; ordinal < executions.size(); ordinal++) {
            var execution = executions.get(ordinal);
            if (execution.instanceId() != instanceId
                    || execution.definitionId() != definitionId
                    || execution.definitionVersion() != definitionVersion
                    || execution.ordinal() != ordinal) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Completion execution identity does not match its instance");
            }
        }
    }

    private List<Long> decisionMembers(Decision target) {
        return approverIds.stream()
                .filter(memberId -> decisions.get(memberId) == target)
                .toList();
    }

    private static Map<Long, Decision> requireDecisions(
            ApprovalMode approvalMode,
            List<Long> approverIds,
            Map<Long, Decision> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        var next = new LinkedHashMap<Long, Decision>();
        for (var memberId : approverIds) {
            var decision = values.get(memberId);
            if (decision != null) {
                next.put(memberId, decision);
            }
        }
        if (next.size() != values.size()
                || values.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null
                                || entry.getValue() == null
                                || !approverIds.contains(entry.getKey()))) {
            throw new IllegalArgumentException(
                    "Approval decisions must belong to configured route members"
            );
        }
        if (approvalMode == ApprovalMode.SEQUENTIAL) {
            throw new IllegalArgumentException(
                    "Sequential approval cannot contain concurrent member decisions"
            );
        }
        return Collections.unmodifiableMap(next);
    }

    private void requireAssignmentTarget(long targetMemberId) {
        if (targetMemberId <= 0
                || targetMemberId == approverId
                || approverIds.contains(targetMemberId)) {
            throw assignmentInvalid(
                    "Assignment target must be a different member who is not already in the approval sequence"
            );
        }
    }

    private static String assignmentReason(String reason) {
        return requiredReason(
                reason,
                ASSIGNMENT_REQUEST_INVALID,
                "An assignment reason of 1 to 500 characters is required"
        );
    }

    private ApprovalInstance assignmentTransition(
            long actorId,
            Long targetMemberId,
            long nextApproverId,
            List<Long> nextApprovers,
            int nextStepIndex,
            ClaimState nextClaimState,
            ApprovalHistoryEvent.Type eventType,
            ApprovalHistoryEvent.AssignmentPosition position,
            String reason,
            Instant occurredAt
    ) {
        return assignmentTransition(
                actorId,
                targetMemberId,
                nextApproverId,
                nextApprovers,
                nextStepIndex,
                nextClaimState,
                eventType,
                position,
                reason,
                occurredAt,
                null
        );
    }

    private ApprovalInstance assignmentTransition(
            long actorId,
            Long targetMemberId,
            long nextApproverId,
            List<Long> nextApprovers,
            int nextStepIndex,
            ClaimState nextClaimState,
            ApprovalHistoryEvent.Type eventType,
            ApprovalHistoryEvent.AssignmentPosition position,
            String reason,
            Instant occurredAt,
            Integer targetStepIndex
    ) {
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(new ApprovalHistoryEvent(
                eventType,
                actorId,
                Status.PENDING,
                Status.PENDING,
                reason,
                occurredAt,
                targetMemberId,
                position,
                targetStepIndex
        ));
        return new ApprovalInstance(
                id,
                definitionId,
                definitionVersion,
                businessKey,
                requesterId,
                nextApproverId,
                nextApprovers,
                Status.PENDING,
                startedAt,
                null,
                nextHistory,
                nextStepIndex,
                nextClaimState,
                recordBinding,
                approvalMode,
                nextApprovers.size(),
                decisions,
                List.of(),
                deadline,
                decisionCommentPolicy,
                currentStageIndex,
                stagesAfterAssignment(
                        nextApprovers,
                        nextStepIndex,
                        eventType
                ),
                startContext,
                decisionEvidencePolicy, completionPhase,
                activeCompletionOrdinal, completionExecutions,
                completionFailurePolicy
        );
    }

    private static ApprovalDomainException assignmentInvalid(String message) {
        return new ApprovalDomainException(ASSIGNMENT_REQUEST_INVALID, message);
    }

    private ApprovalInstance transition(
            long actorId,
            long representedMemberId,
            Long delegationRuleId,
            long nextApproverId,
            int nextStepIndex,
            Status target,
            ApprovalHistoryEvent.Type eventType,
            String comment,
            Instant occurredAt
    ) {
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(new ApprovalHistoryEvent(
                eventType,
                actorId,
                status,
                target,
                comment,
                occurredAt,
                null,
                null,
                null,
                delegationRuleId == null ? null : representedMemberId,
                delegationRuleId
        ));
        return new ApprovalInstance(
                id,
                definitionId,
                definitionVersion,
                businessKey,
                requesterId,
                nextApproverId,
                approverIds,
                target,
                startedAt,
                target == Status.PENDING ? null : occurredAt,
                nextHistory,
                nextStepIndex,
                claimState,
                recordBinding,
                approvalMode,
                approverIds.size(),
                decisions,
                List.of(),
                deadline,
                decisionCommentPolicy,
                currentStageIndex,
                stagesAfterDecision(
                        actorId,
                        representedMemberId,
                        eventType == ApprovalHistoryEvent.Type.APPROVED
                ),
                startContext,
                decisionEvidencePolicy,
                target == Status.PENDING
                        ? completionPhase : CompletionPhase.COMPLETED,
                target == Status.PENDING ? activeCompletionOrdinal : null,
                completionExecutions,
                completionFailurePolicy
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

    private static String requiredReason(
            String reason,
            ApprovalDomainException.Code code,
            String message
    ) {
        var normalized = reason == null ? "" : reason.strip();
        var length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 500) {
            throw new ApprovalDomainException(code, message);
        }
        return normalized;
    }

    private static String optionalComment(String comment) {
        var normalized = comment == null ? "" : comment.strip();
        if (normalized.codePointCount(0, normalized.length()) > 500) {
            throw claimInvalid("A claim comment may contain at most 500 characters");
        }
        return normalized;
    }

    private static ApprovalDomainException claimInvalid(String message) {
        return new ApprovalDomainException(CLAIM_REQUEST_INVALID, message);
    }

    public ApprovalStageExecution currentStage() {
        return stages.get(currentStageIndex);
    }

    public boolean hasWaitingStage() {
        return currentStageIndex + 1 < stages.size();
    }

    /**
     * Activates exactly one waiting linear stage after the current stage has
     * approved. Membership validation is performed by the caller before this
     * pure domain transition.
     */
    public ApprovalInstance activateNextStage(
            ApprovalStage nextStage,
            List<Long> resolvedApproverIds,
            Instant activatedAt
    ) {
        Objects.requireNonNull(nextStage, "nextStage");
        Objects.requireNonNull(activatedAt, "activatedAt");
        if (status != Status.APPROVED || !hasWaitingStage()) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Approval instance has no stage ready for activation");
        }
        var nextIndex = currentStageIndex + 1;
        var waiting = stages.get(nextIndex);
        if (waiting.status() != ApprovalStageExecution.Status.WAITING
                || !waiting.code().equals(nextStage.code())) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Waiting approval stage does not match the published plan");
        }
        resolvedApproverIds = ApprovalDefinitionDraft.requireApprovers(
                resolvedApproverIds);
        if (nextStage.approverSource().kind()
                == ApprovalApproverSource.Kind.PREVIOUS_HANDLER) {
            var handlers = currentStage().actualHandlerIds();
            if (handlers.isEmpty() || !handlers.equals(resolvedApproverIds)) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "PREVIOUS_HANDLER must resolve the completed stage actors");
            }
        } else if (nextStage.approverSource().kind()
                == ApprovalApproverSource.Kind.FIXED) {
            if (!nextStage.approverIds().equals(resolvedApproverIds)) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "Fixed stage activation must preserve published members");
            }
        } else {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Later stage source is unsupported");
        }
        var active = ApprovalStageExecution.active(
                nextIndex, nextStage, resolvedApproverIds, activatedAt);
        var nextStages = new ArrayList<>(stages);
        nextStages.set(nextIndex, active);
        var nextHistory = stageBoundaryHistory(history);
        return new ApprovalInstance(
                id, definitionId, definitionVersion, businessKey, requesterId,
                resolvedApproverIds.getFirst(), resolvedApproverIds,
                Status.PENDING, startedAt, null, nextHistory, 0,
                ClaimState.CLAIMED, recordBinding, nextStage.approvalMode(),
                active.requiredApprovals(), Map.of(), List.of(),
                active.deadline(), active.decisionCommentPolicy(),
                nextIndex, nextStages, startContext,
                active.decisionEvidencePolicy(), CompletionPhase.HUMAN_APPROVAL,
                null, List.of(),
                completionFailurePolicy
        );
    }

    public ApprovalInstance activateNextBranchStage(
            String branchCode,
            ApprovalStage nextStage,
            List<Long> resolvedApproverIds,
            Instant activatedAt
    ) {
        if (!isParallel()) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Branch stage activation requires a branch execution");
        }
        var currentBranch = parallelBranch(branchCode);
        var activated = currentBranch.activateNextStage(
                nextStage, resolvedApproverIds, activatedAt);
        var nextBranches = parallelBranches.stream()
                .map(branch -> branch.code().equals(branchCode)
                        ? activated : branch)
                .toList();
        var first = nextBranches.getFirst();
        return new ApprovalInstance(
                id, definitionId, definitionVersion, businessKey, requesterId,
                first.approverId(), first.approverIds(), Status.PENDING,
                startedAt, null, branchStageBoundaryHistory(history),
                first.currentStepIndex(), ClaimState.CLAIMED, recordBinding,
                first.approvalMode(), first.requiredApprovals(),
                first.decisions(), nextBranches, first.deadline(),
                first.decisionCommentPolicy(), currentStageIndex, stages,
                startContext,
                first.decisionEvidencePolicy(), CompletionPhase.HUMAN_APPROVAL,
                null, List.of(),
                completionFailurePolicy
        );
    }

    private static List<ApprovalStageExecution> initialStageExecutions(
            List<ApprovalStage> approvalStages,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int resolvedRequiredApprovals,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy commentPolicy,
            ApprovalDecisionEvidencePolicy evidencePolicy,
            Instant startedAt
    ) {
        if (approvalStages == null) {
            return null;
        }
        var result = new ArrayList<ApprovalStageExecution>();
        var first = approvalStages.getFirst();
        result.add(new ApprovalStageExecution(
                0, first.code(), first.name(),
                ApprovalStageExecution.Status.ACTIVE,
                resolvedApproverIds, resolvedApprovalMode,
                resolvedRequiredApprovals, List.of(), startedAt, null,
                deadline, commentPolicy, Map.of(),
                first.decisionEvidencePolicy()));
        for (var index = 1; index < approvalStages.size(); index++) {
            result.add(ApprovalStageExecution.waiting(
                    index, approvalStages.get(index)));
        }
        return List.copyOf(result);
    }

    private static List<ApprovalStageExecution> normalizeStages(
            List<ApprovalStageExecution> supplied,
            int currentStageIndex,
            int currentStepIndex,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Status instanceStatus,
            Instant instanceStartedAt,
            Instant instanceCompletedAt,
            List<ApprovalHistoryEvent> history,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy commentPolicy,
            ApprovalDecisionEvidencePolicy evidencePolicy
    ) {
        if (supplied == null || supplied.isEmpty()) {
            return List.of(legacyStage(
                    approverIds, approvalMode, requiredApprovals,
                    instanceStatus, instanceStartedAt, instanceCompletedAt,
                    history, deadline, commentPolicy, evidencePolicy));
        }
        var stages = new ArrayList<>(supplied);
        if (currentStageIndex < 0 || currentStageIndex >= stages.size()) {
            throw new IllegalArgumentException(
                    "Current approval stage is outside the runtime plan");
        }
        var current = stages.get(currentStageIndex);
        if (current.status() == ApprovalStageExecution.Status.WAITING) {
            throw new IllegalArgumentException(
                    "Current approval stage cannot be waiting");
        }
        var actors = new LinkedHashMap<>(current.handlerActorsByParticipantId());
        actors.entrySet().removeIf(entry -> !approverIds.contains(entry.getKey()));
        var actualHandlers = approverIds.stream()
                .map(actors::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        var stageStatus = switch (instanceStatus) {
            case PENDING -> ApprovalStageExecution.Status.ACTIVE;
            case APPROVED -> ApprovalStageExecution.Status.APPROVED;
            case REJECTED, WITHDRAWN, TERMINATED ->
                    ApprovalStageExecution.Status.REJECTED;
        };
        stages.set(currentStageIndex, new ApprovalStageExecution(
                currentStageIndex, current.code(), current.name(), stageStatus,
                approverIds, approvalMode, requiredApprovals,
                actualHandlers,
                current.startedAt() == null ? instanceStartedAt : current.startedAt(),
                stageStatus == ApprovalStageExecution.Status.ACTIVE
                        ? null
                        : instanceCompletedAt,
                deadline, commentPolicy,
                actors, current.decisionEvidencePolicy()));
        return List.copyOf(stages);
    }

    private static ApprovalStageExecution legacyStage(
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Status status,
            Instant startedAt,
            Instant completedAt,
            List<ApprovalHistoryEvent> history,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy commentPolicy,
            ApprovalDecisionEvidencePolicy evidencePolicy
    ) {
        var actors = new LinkedHashMap<Long, Long>();
        for (var event : history) {
            if (event.type() != ApprovalHistoryEvent.Type.APPROVED) {
                continue;
            }
            var represented = event.representedMemberId() == null
                    ? event.actorId()
                    : event.representedMemberId();
            if (approverIds.contains(represented)) {
                actors.put(represented, event.actorId());
            }
        }
        var actual = approverIds.stream()
                .map(actors::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        var stageStatus = switch (status) {
            case PENDING -> ApprovalStageExecution.Status.ACTIVE;
            case APPROVED -> ApprovalStageExecution.Status.APPROVED;
            case REJECTED, WITHDRAWN, TERMINATED ->
                    ApprovalStageExecution.Status.REJECTED;
        };
        return new ApprovalStageExecution(
                0, "legacy", "Approval", stageStatus, approverIds,
                approvalMode, requiredApprovals,
                actual,
                startedAt,
                stageStatus == ApprovalStageExecution.Status.ACTIVE
                        ? null
                        : completedAt,
                deadline, commentPolicy,
                actors, evidencePolicy);
    }

    private static void requireStageShape(
            List<ApprovalStageExecution> stages,
            int currentStageIndex
    ) {
        if (stages.isEmpty() || stages.size() > 10
                || stages.stream().map(ApprovalStageExecution::code)
                .distinct().count() != stages.size()) {
            throw new IllegalArgumentException(
                    "Approval instance stage plan is invalid");
        }
        for (var index = 0; index < stages.size(); index++) {
            var stage = stages.get(index);
            if (stage.stageIndex() != index
                    || (index < currentStageIndex
                    && stage.status() != ApprovalStageExecution.Status.APPROVED)
                    || (index > currentStageIndex
                    && stage.status() != ApprovalStageExecution.Status.WAITING)) {
                throw new IllegalArgumentException(
                        "Approval instance stage cursor is corrupt");
            }
        }
    }

    private static List<ApprovalHistoryEvent> stageBoundaryHistory(
            List<ApprovalHistoryEvent> history
    ) {
        if (history.isEmpty()) {
            throw new IllegalStateException(
                    "Approval stage completion history is missing");
        }
        var result = new ArrayList<>(history);
        var last = result.removeLast();
        if (last.toStatus() != Status.APPROVED) {
            throw new IllegalStateException(
                    "Approval stage boundary must follow approval completion");
        }
        result.add(new ApprovalHistoryEvent(
                last.type(), last.actorId(), last.fromStatus(), Status.PENDING,
                last.comment(), last.occurredAt(), last.targetMemberId(),
                last.assignmentPosition(), last.targetStepIndex(),
                last.representedMemberId(), last.delegationRuleId()));
        return List.copyOf(result);
    }

    private static List<ApprovalHistoryEvent> branchStageBoundaryHistory(
            List<ApprovalHistoryEvent> history
    ) {
        if (history.isEmpty()) {
            throw new IllegalStateException(
                    "Approval branch stage completion history is missing");
        }
        var last = history.getLast();
        if (last.type() != ApprovalHistoryEvent.Type.APPROVED
                || last.toStatus() == Status.REJECTED
                || last.toStatus() == Status.WITHDRAWN
                || last.toStatus() == Status.TERMINATED) {
            throw new IllegalStateException(
                    "Approval branch stage boundary must follow approval");
        }
        return last.toStatus() == Status.PENDING
                ? history
                : stageBoundaryHistory(history);
    }

    private static int legacyCursor(
            List<Long> approverIds,
            List<ApprovalHistoryEvent> history
    ) {
        Objects.requireNonNull(approverIds, "approverIds");
        Objects.requireNonNull(history, "history");
        var forward = history.stream()
                .filter(event -> event.type() == ApprovalHistoryEvent.Type.APPROVED)
                .filter(event -> event.toStatus() == Status.PENDING)
                .count();
        var returned = history.stream()
                .filter(event -> event.type() == ApprovalHistoryEvent.Type.RETURNED)
                .count();
        var cursor = forward - returned;
        if (cursor < 0 || cursor >= approverIds.size()) {
            throw new IllegalArgumentException("Legacy approval history produces an invalid cursor");
        }
        return (int) cursor;
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        WITHDRAWN,
        TERMINATED
    }

    public enum CompletionPhase {
        HUMAN_APPROVAL,
        EXTERNAL_EXECUTION,
        COMPENSATING,
        COMPLETED
    }

    public enum Decision {
        APPROVED,
        REJECTED
    }

    public enum ClaimState {
        CLAIMED,
        OPEN
    }

    public record RecordBinding(String moduleCode, long recordId) {
        public RecordBinding {
            if (moduleCode == null || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("Record binding module code is invalid");
            }
            if (recordId <= 0) {
                throw new IllegalArgumentException("Record binding record id must be positive");
            }
        }
    }
}
