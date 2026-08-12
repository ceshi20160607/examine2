package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID;

public record ApprovalDefinitionDraft(
        long id,
        String name,
        List<Long> approverIds,
        int revision,
        Instant updatedAt,
        TriggerBinding triggerBinding,
        RecordStatusMapping recordStatusMapping,
        ApprovalGateway gateway,
        ApprovalMode approvalMode,
        ApprovalParallelGateway parallelGateway,
        ApprovalInclusiveGateway inclusiveGateway,
        ApprovalApproverSources approverSources,
        ApprovalQuorumRules quorumRules,
        ApprovalDeadlinePolicies deadlinePolicies,
        ApprovalDecisionCommentPolicies decisionCommentPolicies,
        List<ApprovalStage> approvalStages,
        ApprovalDecisionEvidencePolicies decisionEvidencePolicies,
        List<ApprovalCompletionStep> completionSteps,
        CompletionFailurePolicy completionFailurePolicy
) {
    public ApprovalDefinitionDraft(
            long id, String name, List<Long> approverIds, int revision,
            Instant updatedAt, TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping, ApprovalGateway gateway,
            ApprovalMode approvalMode, ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources,
            ApprovalQuorumRules quorumRules,
            ApprovalDeadlinePolicies deadlinePolicies,
            ApprovalDecisionCommentPolicies decisionCommentPolicies,
            List<ApprovalStage> approvalStages,
            ApprovalDecisionEvidencePolicies decisionEvidencePolicies,
            List<ApprovalCompletionStep> completionSteps
    ) {
        this(id, name, approverIds, revision, updatedAt, triggerBinding,
                recordStatusMapping, gateway, approvalMode, parallelGateway,
                inclusiveGateway, approverSources, quorumRules,
                deadlinePolicies, decisionCommentPolicies, approvalStages,
                decisionEvidencePolicies, completionSteps,
                CompletionFailurePolicy.MANUAL_RETRY);
    }
    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources,
            ApprovalQuorumRules quorumRules,
            ApprovalDeadlinePolicies deadlinePolicies,
            ApprovalDecisionCommentPolicies decisionCommentPolicies,
            List<ApprovalStage> approvalStages,
            ApprovalDecisionEvidencePolicies decisionEvidencePolicies
    ) {
        this(
                id, name, approverIds, revision, updatedAt, triggerBinding,
                recordStatusMapping, gateway, approvalMode, parallelGateway,
                inclusiveGateway, approverSources, quorumRules, deadlinePolicies,
                decisionCommentPolicies, approvalStages,
                decisionEvidencePolicies, null,
                CompletionFailurePolicy.MANUAL_RETRY
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources,
            ApprovalQuorumRules quorumRules,
            ApprovalDeadlinePolicies deadlinePolicies,
            ApprovalDecisionCommentPolicies decisionCommentPolicies,
            List<ApprovalStage> approvalStages
    ) {
        this(
                id, name, approverIds, revision, updatedAt, triggerBinding,
                recordStatusMapping, gateway, approvalMode, parallelGateway,
                inclusiveGateway, approverSources, quorumRules, deadlinePolicies,
                decisionCommentPolicies, approvalStages, null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources,
            ApprovalQuorumRules quorumRules,
            ApprovalDeadlinePolicies deadlinePolicies,
            ApprovalDecisionCommentPolicies decisionCommentPolicies
    ) {
        this(
                id, name, approverIds, revision, updatedAt, triggerBinding,
                recordStatusMapping, gateway, approvalMode, parallelGateway,
                inclusiveGateway, approverSources, quorumRules, deadlinePolicies,
                decisionCommentPolicies, null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources,
            ApprovalQuorumRules quorumRules,
            ApprovalDeadlinePolicies deadlinePolicies
    ) {
        this(
                id, name, approverIds, revision, updatedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, inclusiveGateway, approverSources, quorumRules,
                deadlinePolicies, null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources,
            ApprovalQuorumRules quorumRules
    ) {
        this(
                id, name, approverIds, revision, updatedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, inclusiveGateway, approverSources, quorumRules, null
        );
    }

    public static final int MAX_APPROVERS = 10;

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources
    ) {
        this(
                id, name, approverIds, revision, updatedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, inclusiveGateway, approverSources, null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        this(
                id, name, approverIds, revision, updatedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, inclusiveGateway, null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway
    ) {
        this(
                id, name, approverIds, revision, updatedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, null, null
        );
    }

    public ApprovalDefinitionDraft {
        if (id <= 0) {
            throw new IllegalArgumentException("Definition id must be positive");
        }
        name = requireText(name, "Definition name");
        approverIds = requireApprovers(approverIds);
        if (revision < 1) {
            throw new IllegalArgumentException("Draft revision must be positive");
        }
        Objects.requireNonNull(updatedAt, "updatedAt");
        approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
        requireApprovalModeApprovers(approvalMode, approverIds);
        requireCompatibleTriggerStatusMapping(triggerBinding, recordStatusMapping);
        var gatewayCount = (gateway == null ? 0 : 1)
                + (parallelGateway == null ? 0 : 1)
                + (inclusiveGateway == null ? 0 : 1);
        if (gatewayCount > 1) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.GATEWAY_INVALID,
                    "Conditional, parallel and inclusive gateways are mutually exclusive"
            );
        }
        if (gateway != null && !approverIds.equals(gateway.defaultBranch().approverIds())) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.GATEWAY_INVALID,
                    "Definition approver route must equal the gateway default branch route"
            );
        }
        if (gateway != null && approvalMode != gateway.defaultBranch().approvalMode()) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.GATEWAY_INVALID,
                    "Definition approval mode must equal the gateway default branch mode"
            );
        }
        if (parallelGateway != null
                && (!approverIds.equals(parallelGateway.branches().getFirst().approverIds())
                || approvalMode != parallelGateway.branches().getFirst().approvalMode())) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.GATEWAY_INVALID,
                    "Definition route must mirror the first parallel branch"
            );
        }
        if (inclusiveGateway != null
                && (!approverIds.equals(inclusiveGateway.branches().getFirst().approverIds())
                || approvalMode != inclusiveGateway.branches().getFirst().approvalMode())) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.GATEWAY_INVALID,
                    "Definition route must mirror the first inclusive branch"
            );
        }
        approverSources = (approverSources == null
                ? ApprovalApproverSources.fixedFor(gateway, parallelGateway, inclusiveGateway)
                : approverSources).requireShape(gateway, parallelGateway, inclusiveGateway);
        quorumRules = (quorumRules == null
                ? ApprovalQuorumRules.none()
                : quorumRules).requireShape(
                        approvalMode,
                        gateway,
                        parallelGateway,
                        inclusiveGateway
                );
        deadlinePolicies = (deadlinePolicies == null
                ? ApprovalDeadlinePolicies.none()
                : deadlinePolicies).requireShape(
                        gateway,
                        parallelGateway,
                        inclusiveGateway
                );
        decisionCommentPolicies = (decisionCommentPolicies == null
                ? ApprovalDecisionCommentPolicies.none()
                : decisionCommentPolicies).requireShape(
                        gateway,
                        parallelGateway,
                        inclusiveGateway
                );
        approvalStages = requireApprovalStages(
                approvalStages, gateway, parallelGateway, inclusiveGateway);
        requireBranchStagePolicyProjection(
                gateway, parallelGateway, inclusiveGateway, approverSources,
                quorumRules, deadlinePolicies, decisionCommentPolicies);
        decisionEvidencePolicies = (decisionEvidencePolicies == null
                ? ApprovalDecisionEvidencePolicies.none()
                : decisionEvidencePolicies).requireShape(
                gateway, parallelGateway, inclusiveGateway);
        requireDecisionEvidenceStagePolicyProjection(
                approvalStages, gateway, parallelGateway, inclusiveGateway,
                decisionEvidencePolicies);
        completionSteps = ApprovalCompletionStep.requireSteps(completionSteps);
        completionFailurePolicy = CompletionFailurePolicy.require(
                completionFailurePolicy, completionSteps);
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode
    ) {
        this(
                id, name, approverIds, revision, updatedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode, null, null, null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway
    ) {
        this(
                id,
                name,
                approverIds,
                revision,
                updatedAt,
                triggerBinding,
                recordStatusMapping,
                gateway,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping
    ) {
        this(
                id,
                name,
                approverIds,
                revision,
                updatedAt,
                triggerBinding,
                recordStatusMapping,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt,
            TriggerBinding triggerBinding
    ) {
        this(
                id, name, approverIds, revision, updatedAt,
                triggerBinding, null, null, ApprovalMode.SEQUENTIAL, null, null, null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            List<Long> approverIds,
            int revision,
            Instant updatedAt
    ) {
        this(
                id, name, approverIds, revision, updatedAt,
                null, null, null, ApprovalMode.SEQUENTIAL, null, null, null
        );
    }

    public ApprovalDefinitionDraft(
            long id,
            String name,
            long approverId,
            int revision,
            Instant updatedAt
    ) {
        this(
                id, name, List.of(approverId), revision, updatedAt,
                null, null, null, ApprovalMode.SEQUENTIAL, null, null, null
        );
    }

    public long approverId() {
        return approverIds.getFirst();
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            Instant occurredAt
    ) {
        return revise(
                nextName,
                nextApproverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            Instant occurredAt
    ) {
        return revise(
                nextName,
                nextApproverIds,
                nextTriggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            Instant occurredAt
    ) {
        return revise(
                nextName,
                nextApproverIds,
                nextTriggerBinding,
                nextRecordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            Instant occurredAt
    ) {
        return revise(
                nextName,
                nextApproverIds,
                nextTriggerBinding,
                nextRecordStatusMapping,
                nextGateway,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                null,
                occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            Instant occurredAt
    ) {
        return revise(
                nextName,
                nextApproverIds,
                nextTriggerBinding,
                nextRecordStatusMapping,
                nextGateway,
                nextApprovalMode,
                null,
                null,
                null,
                occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            Instant occurredAt
    ) {
        return revise(
                nextName,
                nextApproverIds,
                nextTriggerBinding,
                nextRecordStatusMapping,
                nextGateway,
                nextApprovalMode,
                nextParallelGateway,
                null,
                null,
                occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            ApprovalInclusiveGateway nextInclusiveGateway,
            Instant occurredAt
    ) {
        return revise(
                nextName, nextApproverIds, nextTriggerBinding, nextRecordStatusMapping,
                nextGateway, nextApprovalMode, nextParallelGateway, nextInclusiveGateway,
                null, occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            ApprovalInclusiveGateway nextInclusiveGateway,
            ApprovalApproverSources nextApproverSources,
            Instant occurredAt
    ) {
        return revise(
                nextName,
                nextApproverIds,
                nextTriggerBinding,
                nextRecordStatusMapping,
                nextGateway,
                nextApprovalMode,
                nextParallelGateway,
                nextInclusiveGateway,
                nextApproverSources,
                quorumRules,
                occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            ApprovalInclusiveGateway nextInclusiveGateway,
            ApprovalApproverSources nextApproverSources,
            ApprovalQuorumRules nextQuorumRules,
            Instant occurredAt
    ) {
        return revise(
                nextName,
                nextApproverIds,
                nextTriggerBinding,
                nextRecordStatusMapping,
                nextGateway,
                nextApprovalMode,
                nextParallelGateway,
                nextInclusiveGateway,
                nextApproverSources,
                nextQuorumRules,
                deadlinePolicies,
                decisionCommentPolicies,
                occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            ApprovalInclusiveGateway nextInclusiveGateway,
            ApprovalApproverSources nextApproverSources,
            ApprovalQuorumRules nextQuorumRules,
            ApprovalDeadlinePolicies nextDeadlinePolicies,
            Instant occurredAt
    ) {
        return revise(
                nextName,
                nextApproverIds,
                nextTriggerBinding,
                nextRecordStatusMapping,
                nextGateway,
                nextApprovalMode,
                nextParallelGateway,
                nextInclusiveGateway,
                nextApproverSources,
                nextQuorumRules,
                nextDeadlinePolicies,
                decisionCommentPolicies,
                occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            ApprovalInclusiveGateway nextInclusiveGateway,
            ApprovalApproverSources nextApproverSources,
            ApprovalQuorumRules nextQuorumRules,
            ApprovalDeadlinePolicies nextDeadlinePolicies,
            ApprovalDecisionCommentPolicies nextDecisionCommentPolicies,
            Instant occurredAt
    ) {
        return revise(
                nextName, nextApproverIds, nextTriggerBinding,
                nextRecordStatusMapping, nextGateway, nextApprovalMode,
                nextParallelGateway, nextInclusiveGateway, nextApproverSources,
                nextQuorumRules, nextDeadlinePolicies,
                nextDecisionCommentPolicies, approvalStages, occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            ApprovalInclusiveGateway nextInclusiveGateway,
            ApprovalApproverSources nextApproverSources,
            ApprovalQuorumRules nextQuorumRules,
            ApprovalDeadlinePolicies nextDeadlinePolicies,
            ApprovalDecisionCommentPolicies nextDecisionCommentPolicies,
            List<ApprovalStage> nextApprovalStages,
            Instant occurredAt
    ) {
        return revise(
                nextName, nextApproverIds, nextTriggerBinding,
                nextRecordStatusMapping, nextGateway, nextApprovalMode,
                nextParallelGateway, nextInclusiveGateway, nextApproverSources,
                nextQuorumRules, nextDeadlinePolicies,
                nextDecisionCommentPolicies, nextApprovalStages,
                decisionEvidencePolicies, occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            ApprovalInclusiveGateway nextInclusiveGateway,
            ApprovalApproverSources nextApproverSources,
            ApprovalQuorumRules nextQuorumRules,
            ApprovalDeadlinePolicies nextDeadlinePolicies,
            ApprovalDecisionCommentPolicies nextDecisionCommentPolicies,
            List<ApprovalStage> nextApprovalStages,
            ApprovalDecisionEvidencePolicies nextDecisionEvidencePolicies,
            Instant occurredAt
    ) {
        return revise(
                nextName, nextApproverIds, nextTriggerBinding,
                nextRecordStatusMapping, nextGateway, nextApprovalMode,
                nextParallelGateway, nextInclusiveGateway, nextApproverSources,
                nextQuorumRules, nextDeadlinePolicies,
                nextDecisionCommentPolicies, nextApprovalStages,
                nextDecisionEvidencePolicies, completionSteps, occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            ApprovalInclusiveGateway nextInclusiveGateway,
            ApprovalApproverSources nextApproverSources,
            ApprovalQuorumRules nextQuorumRules,
            ApprovalDeadlinePolicies nextDeadlinePolicies,
            ApprovalDecisionCommentPolicies nextDecisionCommentPolicies,
            List<ApprovalStage> nextApprovalStages,
            ApprovalDecisionEvidencePolicies nextDecisionEvidencePolicies,
            List<ApprovalCompletionStep> nextCompletionSteps,
            Instant occurredAt
    ) {
        return revise(
                nextName, nextApproverIds, nextTriggerBinding,
                nextRecordStatusMapping, nextGateway, nextApprovalMode,
                nextParallelGateway, nextInclusiveGateway, nextApproverSources,
                nextQuorumRules, nextDeadlinePolicies,
                nextDecisionCommentPolicies, nextApprovalStages,
                nextDecisionEvidencePolicies, nextCompletionSteps,
                completionFailurePolicy, occurredAt
        );
    }

    public ApprovalDefinitionDraft revise(
            String nextName,
            List<Long> nextApproverIds,
            TriggerBinding nextTriggerBinding,
            RecordStatusMapping nextRecordStatusMapping,
            ApprovalGateway nextGateway,
            ApprovalMode nextApprovalMode,
            ApprovalParallelGateway nextParallelGateway,
            ApprovalInclusiveGateway nextInclusiveGateway,
            ApprovalApproverSources nextApproverSources,
            ApprovalQuorumRules nextQuorumRules,
            ApprovalDeadlinePolicies nextDeadlinePolicies,
            ApprovalDecisionCommentPolicies nextDecisionCommentPolicies,
            List<ApprovalStage> nextApprovalStages,
            ApprovalDecisionEvidencePolicies nextDecisionEvidencePolicies,
            List<ApprovalCompletionStep> nextCompletionSteps,
            CompletionFailurePolicy nextCompletionFailurePolicy,
            Instant occurredAt
    ) {
        return new ApprovalDefinitionDraft(
                id,
                nextName,
                nextApproverIds,
                revision + 1,
                occurredAt,
                nextTriggerBinding,
                nextRecordStatusMapping,
                nextGateway,
                nextApprovalMode,
                nextParallelGateway,
                nextInclusiveGateway,
                nextApproverSources == null ? approverSources : nextApproverSources,
                nextQuorumRules == null ? quorumRules : nextQuorumRules,
                nextDeadlinePolicies == null ? deadlinePolicies : nextDeadlinePolicies,
                 nextDecisionCommentPolicies == null
                        ? decisionCommentPolicies
                        : nextDecisionCommentPolicies,
                nextApprovalStages,
                nextDecisionEvidencePolicies == null
                        ? decisionEvidencePolicies
                        : nextDecisionEvidencePolicies,
                nextCompletionSteps,
                nextCompletionFailurePolicy
        );
    }

    public ApprovalDefinitionDraft revise(String nextName, long nextApproverId, Instant occurredAt) {
        return revise(nextName, List.of(nextApproverId), occurredAt);
    }

    static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.strip();
    }

    static List<Long> requireApprovers(List<Long> values) {
        if (values == null
                || values.isEmpty()
                || values.size() > MAX_APPROVERS
                || values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new ApprovalDomainException(
                    APPROVER_SEQUENCE_INVALID,
                    "Approver sequence must contain 1 to " + MAX_APPROVERS + " positive member ids"
            );
        }
        return List.copyOf(values);
    }

    static void requireApprovalModeApprovers(ApprovalMode mode, List<Long> approverIds) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(approverIds, "approverIds");
        if (mode != ApprovalMode.SEQUENTIAL
                && approverIds.stream().distinct().count() != approverIds.size()) {
            throw new ApprovalDomainException(
                    APPROVER_SEQUENCE_INVALID,
                    "Concurrent approval routes require unique member ids"
            );
        }
    }

    static void requireCompatibleTriggerStatusMapping(
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping
    ) {
        if (recordStatusMapping != null
                && triggerBinding != null
                && triggerBinding.event() != TriggerBinding.Event.RECORD_ACTIVATED) {
            throw new IllegalArgumentException(
                    "Only activation triggers may define a terminal record STATUS mapping");
        }
    }

    static List<ApprovalStage> requireApprovalStages(
            List<ApprovalStage> stages,
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        if (stages == null) {
            return null;
        }
        stages = requireStagePlan(stages, 2);
        if (gateway != null || parallelGateway != null || inclusiveGateway != null) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.APPROVAL_STAGES_GATEWAY_UNSUPPORTED,
                    "Ordered approval stages do not support gateways"
            );
        }
        return stages;
    }

    static List<ApprovalStage> requireBranchApprovalStages(
            List<ApprovalStage> stages
    ) {
        return stages == null ? null : requireStagePlan(stages, 1);
    }

    static void requireBranchStageZeroProjection(
            List<Long> approverIds,
            ApprovalMode approvalMode,
            List<ApprovalStage> stages
    ) {
        if (stages == null) {
            return;
        }
        var first = stages.getFirst();
        if (approvalMode != first.approvalMode()
                || first.approverSource().kind()
                == ApprovalApproverSource.Kind.FIXED
                && !approverIds.equals(first.approverIds())) {
            throw new ApprovalDomainException(
                    APPROVER_SEQUENCE_INVALID,
                    "Legacy branch route must mirror approvalStages[0]"
            );
        }
    }

    private static List<ApprovalStage> requireStagePlan(
            List<ApprovalStage> stages,
            int minimumSize
    ) {
        if (stages.size() < minimumSize || stages.size() > 10
                || stages.stream().anyMatch(Objects::isNull)
                || stages.stream().map(ApprovalStage::code).distinct().count()
                != stages.size()) {
            throw new ApprovalDomainException(
                    APPROVER_SEQUENCE_INVALID,
                    "Approval stages must contain bounded uniquely coded stages"
            );
        }
        for (var index = 0; index < stages.size(); index++) {
            var source = stages.get(index).approverSource().kind();
            if (index == 0 && source == ApprovalApproverSource.Kind.PREVIOUS_HANDLER) {
                throw new ApprovalDomainException(
                        APPROVER_SEQUENCE_INVALID,
                        "The first approval stage cannot use PREVIOUS_HANDLER"
                );
            }
            if (index > 0 && !laterStageSource(source)) {
                throw new ApprovalDomainException(
                        APPROVER_SEQUENCE_INVALID,
                        "Later approval stage source is unsupported"
                );
            }
            if (index > 0
                    && source == ApprovalApproverSource.Kind.PREVIOUS_HANDLER
                    && stages.get(index - 1).deadlinePolicy() != null
                    && stages.get(index - 1).deadlinePolicy().timeoutAction()
                    == ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE) {
                throw new ApprovalDomainException(
                        APPROVER_SEQUENCE_INVALID,
                        "PREVIOUS_HANDLER cannot follow an automatically approved stage"
                );
            }
        }
        return List.copyOf(stages);
    }

    private static boolean laterStageSource(
            ApprovalApproverSource.Kind source
    ) {
        return switch (source) {
            case FIXED, PREVIOUS_HANDLER, REQUESTER, REQUESTER_MANAGER,
                    REQUESTER_DEPARTMENT_LEADER, ROLE, DEPARTMENT,
                    RECORD_MEMBER_FIELD -> true;
            case DEPARTMENT_LEADER -> false;
        };
    }

    static void requireBranchStagePolicyProjection(
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources sources,
            ApprovalQuorumRules quorumRules,
            ApprovalDeadlinePolicies deadlinePolicies,
            ApprovalDecisionCommentPolicies commentPolicies
    ) {
        List<? extends ApprovalBranchRoute> branches = gateway != null
                ? gateway.branches()
                : parallelGateway != null
                        ? parallelGateway.branches()
                        : inclusiveGateway != null
                                ? inclusiveGateway.branches()
                                : List.of();
        for (var branch : branches) {
            if (branch.approvalStages() == null) {
                continue;
            }
            var first = branch.approvalStages().getFirst();
            if (!first.approverSource().equals(sources.branch(branch.code()))
                    || !Objects.equals(
                            first.quorumRule(),
                            quorumRules.branch(branch.code()))
                    || !Objects.equals(
                            first.deadlinePolicy(),
                            deadlinePolicies.branch(branch.code()))
                    || !first.decisionCommentPolicy().equals(
                            commentPolicies.branch(branch.code()))) {
                throw new ApprovalDomainException(
                        APPROVER_SEQUENCE_INVALID,
                        "Legacy branch policies must mirror approvalStages[0]"
                );
            }
        }
    }

    static void requireDecisionEvidenceStagePolicyProjection(
            List<ApprovalStage> approvalStages,
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalDecisionEvidencePolicies policies
    ) {
        if (approvalStages != null
                && policies.primary() != null
                && !policies.primary().equals(
                approvalStages.getFirst().decisionEvidencePolicy())) {
            throw new ApprovalDomainException(
                    APPROVER_SEQUENCE_INVALID,
                    "Legacy route evidence policy must mirror approvalStages[0]");
        }
        List<? extends ApprovalBranchRoute> branches = gateway != null
                ? gateway.branches()
                : parallelGateway != null
                        ? parallelGateway.branches()
                        : inclusiveGateway != null
                                ? inclusiveGateway.branches()
                                : List.of();
        for (var branch : branches) {
            var branchPolicy = policies.branch(branch.code());
            if (branch.approvalStages() != null
                    && branchPolicy != null
                    && !branchPolicy.equals(branch.approvalStages().getFirst()
                    .decisionEvidencePolicy())) {
                throw new ApprovalDomainException(
                        APPROVER_SEQUENCE_INVALID,
                        "Legacy branch evidence policy must mirror approvalStages[0]");
            }
        }
    }
}
