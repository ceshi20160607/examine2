package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ApprovalDefinitionVersion(
        long definitionId,
        int version,
        String name,
        List<Long> approverIds,
        int sourceRevision,
        Instant publishedAt,
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
    public ApprovalDefinitionVersion(
            long definitionId, int version, String name,
            List<Long> approverIds, int sourceRevision, Instant publishedAt,
            TriggerBinding triggerBinding,
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
        this(definitionId, version, name, approverIds, sourceRevision,
                publishedAt, triggerBinding, recordStatusMapping, gateway,
                approvalMode, parallelGateway, inclusiveGateway,
                approverSources, quorumRules, deadlinePolicies,
                decisionCommentPolicies, approvalStages,
                decisionEvidencePolicies, completionSteps,
                CompletionFailurePolicy.MANUAL_RETRY);
    }
    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
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
                definitionId, version, name, approverIds, sourceRevision,
                publishedAt, triggerBinding, recordStatusMapping, gateway,
                approvalMode, parallelGateway, inclusiveGateway, approverSources,
                quorumRules, deadlinePolicies, decisionCommentPolicies,
                approvalStages, decisionEvidencePolicies, null,
                CompletionFailurePolicy.MANUAL_RETRY
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
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
                definitionId, version, name, approverIds, sourceRevision,
                publishedAt, triggerBinding, recordStatusMapping, gateway,
                approvalMode, parallelGateway, inclusiveGateway, approverSources,
                quorumRules, deadlinePolicies, decisionCommentPolicies,
                approvalStages, null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
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
                definitionId, version, name, approverIds, sourceRevision,
                publishedAt, triggerBinding, recordStatusMapping, gateway,
                approvalMode, parallelGateway, inclusiveGateway, approverSources,
                quorumRules, deadlinePolicies, decisionCommentPolicies, null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
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
                definitionId, version, name, approverIds, sourceRevision, publishedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, inclusiveGateway, approverSources, quorumRules,
                deadlinePolicies, null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
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
                definitionId, version, name, approverIds, sourceRevision, publishedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, inclusiveGateway, approverSources, quorumRules, null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources
    ) {
        this(
                definitionId, version, name, approverIds, sourceRevision, publishedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, inclusiveGateway, approverSources, null
        );
    }
    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        this(
                definitionId, version, name, approverIds, sourceRevision, publishedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, inclusiveGateway, null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway
    ) {
        this(
                definitionId, version, name, approverIds, sourceRevision, publishedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode,
                parallelGateway, null, null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode
    ) {
        this(
                definitionId, version, name, approverIds, sourceRevision, publishedAt,
                triggerBinding, recordStatusMapping, gateway, approvalMode, null, null, null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway
    ) {
        this(
                definitionId,
                version,
                name,
                approverIds,
                sourceRevision,
                publishedAt,
                triggerBinding,
                recordStatusMapping,
                gateway,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping
    ) {
        this(
                definitionId,
                version,
                name,
                approverIds,
                sourceRevision,
                publishedAt,
                triggerBinding,
                recordStatusMapping,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt,
            TriggerBinding triggerBinding
    ) {
        this(
                definitionId,
                version,
                name,
                approverIds,
                sourceRevision,
                publishedAt,
                triggerBinding,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                null
        );
    }

    public ApprovalDefinitionVersion {
        if (definitionId <= 0) {
            throw new IllegalArgumentException("Definition id must be positive");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Published version must be positive");
        }
        name = ApprovalDefinitionDraft.requireText(name, "Definition name");
        approverIds = ApprovalDefinitionDraft.requireApprovers(approverIds);
        if (sourceRevision < 1) {
            throw new IllegalArgumentException("Source revision must be positive");
        }
        Objects.requireNonNull(publishedAt, "publishedAt");
        approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
        ApprovalDefinitionDraft.requireApprovalModeApprovers(approvalMode, approverIds);
        ApprovalDefinitionDraft.requireCompatibleTriggerStatusMapping(
                triggerBinding,
                recordStatusMapping);
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
                    "Published approver route must equal the gateway default branch route"
            );
        }
        if (gateway != null && approvalMode != gateway.defaultBranch().approvalMode()) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.GATEWAY_INVALID,
                    "Published approval mode must equal the gateway default branch mode"
            );
        }
        if (parallelGateway != null
                && (!approverIds.equals(parallelGateway.branches().getFirst().approverIds())
                || approvalMode != parallelGateway.branches().getFirst().approvalMode())) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.GATEWAY_INVALID,
                    "Published route must mirror the first parallel branch"
            );
        }
        if (inclusiveGateway != null
                && (!approverIds.equals(inclusiveGateway.branches().getFirst().approverIds())
                || approvalMode != inclusiveGateway.branches().getFirst().approvalMode())) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.GATEWAY_INVALID,
                    "Published route must mirror the first inclusive branch"
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
        approvalStages = ApprovalDefinitionDraft.requireApprovalStages(
                approvalStages, gateway, parallelGateway, inclusiveGateway);
        ApprovalDefinitionDraft.requireBranchStagePolicyProjection(
                gateway, parallelGateway, inclusiveGateway, approverSources,
                quorumRules, deadlinePolicies, decisionCommentPolicies);
        decisionEvidencePolicies = (decisionEvidencePolicies == null
                ? ApprovalDecisionEvidencePolicies.none()
                : decisionEvidencePolicies).requireShape(
                gateway, parallelGateway, inclusiveGateway);
        ApprovalDefinitionDraft.requireDecisionEvidenceStagePolicyProjection(
                approvalStages, gateway, parallelGateway, inclusiveGateway,
                decisionEvidencePolicies);
        completionSteps = ApprovalCompletionStep.requireSteps(completionSteps);
        completionFailurePolicy = CompletionFailurePolicy.require(
                completionFailurePolicy, completionSteps);
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            List<Long> approverIds,
            int sourceRevision,
            Instant publishedAt
    ) {
        this(
                definitionId,
                version,
                name,
                approverIds,
                sourceRevision,
                publishedAt,
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                null
        );
    }

    public ApprovalDefinitionVersion(
            long definitionId,
            int version,
            String name,
            long approverId,
            int sourceRevision,
            Instant publishedAt
    ) {
        this(
                definitionId,
                version,
                name,
                List.of(approverId),
                sourceRevision,
                publishedAt,
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                null
        );
    }

    public long approverId() {
        return approverIds.getFirst();
    }

    public static ApprovalDefinitionVersion publish(
            ApprovalDefinitionDraft draft,
            int version,
            Instant publishedAt
    ) {
        Objects.requireNonNull(draft, "draft");
        return new ApprovalDefinitionVersion(
                draft.id(),
                version,
                draft.name(),
                draft.approverIds(),
                draft.revision(),
                publishedAt,
                draft.triggerBinding(),
                draft.recordStatusMapping(),
                draft.gateway(),
                draft.approvalMode(),
                draft.parallelGateway(),
                draft.inclusiveGateway(),
                draft.approverSources(),
                draft.quorumRules(),
                draft.deadlinePolicies(),
                draft.decisionCommentPolicies(),
                draft.approvalStages(),
                draft.decisionEvidencePolicies(),
                draft.completionSteps(),
                draft.completionFailurePolicy()
        );
    }
}
