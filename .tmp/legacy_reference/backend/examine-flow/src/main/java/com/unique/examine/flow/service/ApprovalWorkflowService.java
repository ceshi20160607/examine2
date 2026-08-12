package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompensationAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionCompensation;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStage;
import com.unique.examine.flow.domain.ApprovalDefinitionDraft;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.CompletionFailurePolicy;
import com.unique.examine.flow.domain.ApprovalDelegationRule;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicies;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicy;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicies;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicy;
import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplate;
import com.unique.examine.flow.domain.ApprovalDecisionEvidence;
import com.unique.examine.flow.domain.ApprovalDecisionEvidenceFile;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicy;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicies;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalBranchRoute;
import com.unique.examine.flow.domain.ApprovalApproverSources;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.ApprovalInclusiveGateway;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalParallelGateway;
import com.unique.examine.flow.domain.ApprovalQuorumRules;
import com.unique.examine.flow.domain.ApprovalStage;
import com.unique.examine.flow.domain.ApprovalStageExecution;
import com.unique.examine.flow.domain.ApprovalStartContext;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.domain.ApprovalTaskAssignment;
import com.unique.examine.flow.domain.FlowTriggerDispatch;
import com.unique.examine.flow.domain.FlowPeriodicScheduleState;
import com.unique.examine.flow.domain.RecordStatusMapping;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.repository.ApprovalRepository;
import com.unique.examine.flow.transport.WebhookPayloadEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.DRAFT_NOT_FOUND;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.DELEGATION_CONFLICT;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.DELEGATION_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.DELEGATION_INACTIVE;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.DELEGATION_NOT_FOUND;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.DELEGATION_RULE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COMMENT_TEMPLATE_NOT_FOUND;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_NOT_FOUND;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.VERSION_NOT_FOUND;

public final class ApprovalWorkflowService {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final ApprovalRepository repository;
    private final IdService idService;
    private final Clock clock;
    private final long systemId;
    private final long tenantId;
    private final WebhookPayloadEncoder completionPayloads;

    @FunctionalInterface
    public interface StageActivationResolver {
        List<Long> resolve(
                ApprovalStage nextStage,
                ApprovalStageExecution completedStage
        );
    }

    @FunctionalInterface
    public interface ContextualStageActivationResolver {
        List<Long> resolve(
                ApprovalStage nextStage,
                ApprovalStageExecution completedStage,
                ApprovalStartContext startContext
        );
    }

    @FunctionalInterface
    public interface BranchStageActivationResolver {
        List<Long> resolve(
                String branchCode,
                ApprovalStage nextStage,
                ApprovalStageExecution completedStage,
                ApprovalStartContext startContext
        );
    }

    public record DecisionEvidenceInput(
            List<ApprovalDecisionEvidenceFile> attachments,
            ApprovalDecisionEvidence.Signature signature,
            Long commentTemplateId
    ) {
        public DecisionEvidenceInput {
            attachments = attachments == null
                    ? List.of()
                    : List.copyOf(attachments);
            if (commentTemplateId != null && commentTemplateId <= 0) {
                throw new ApprovalDomainException(
                        ApprovalDomainException.Code.COMMENT_TEMPLATE_INVALID,
                        "Decision comment template id must be positive");
            }
        }

        public static DecisionEvidenceInput none() {
            return new DecisionEvidenceInput(List.of(), null, null);
        }
    }

    public record DecisionResult(
            ApprovalInstance instance,
            ApprovalDecisionEvidence evidence
    ) {
        public DecisionResult {
            Objects.requireNonNull(instance, "instance");
        }
    }

    public ApprovalWorkflowService(ApprovalRepository repository, IdService idService, Clock clock) {
        this(repository, idService, clock, 1, 1);
    }

    public ApprovalWorkflowService(
            ApprovalRepository repository,
            IdService idService,
            Clock clock,
            long systemId,
            long tenantId
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idService = Objects.requireNonNull(idService, "idService");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (systemId <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException(
                    "Flow workflow scope ids must be positive");
        }
        this.systemId = systemId;
        this.tenantId = tenantId;
        this.completionPayloads = new WebhookPayloadEncoder(
                new ObjectMapper());
    }

    public ApprovalDefinitionDraft createDraft(String name, long approverId) {
        return createDraft(name, List.of(approverId));
    }

    public ApprovalDefinitionDraft createDraft(String name, List<Long> approverIds) {
        return createDraft(name, approverIds, null);
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding
    ) {
        return createDraft(name, approverIds, triggerBinding, null);
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping
    ) {
        return createDraft(name, approverIds, triggerBinding, recordStatusMapping, null);
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway
    ) {
        return createDraft(
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                ApprovalMode.SEQUENTIAL
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode
    ) {
        return createDraft(
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                null
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway
    ) {
        return createDraft(
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                null
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        return createDraft(
                name, approverIds, triggerBinding, recordStatusMapping, gateway,
                approvalMode, parallelGateway, inclusiveGateway,
                ApprovalApproverSources.fixedFor(gateway, parallelGateway, inclusiveGateway)
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources
    ) {
        return createDraft(
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                ApprovalQuorumRules.none()
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources,
            ApprovalQuorumRules quorumRules
    ) {
        return createDraft(
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                quorumRules,
                null
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
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
        return createDraft(
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                quorumRules,
                deadlinePolicies,
                ApprovalDecisionCommentPolicies.none()
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
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
        return createDraft(
                name, approverIds, triggerBinding, recordStatusMapping,
                gateway, approvalMode, parallelGateway, inclusiveGateway,
                approverSources, quorumRules, deadlinePolicies,
                decisionCommentPolicies, null
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
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
        return createDraft(
                name, approverIds, triggerBinding, recordStatusMapping,
                gateway, approvalMode, parallelGateway, inclusiveGateway,
                approverSources, quorumRules, deadlinePolicies,
                decisionCommentPolicies, approvalStages,
                ApprovalDecisionEvidencePolicies.none()
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
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
        return createDraft(
                name, approverIds, triggerBinding, recordStatusMapping,
                gateway, approvalMode, parallelGateway, inclusiveGateway,
                approverSources, quorumRules, deadlinePolicies,
                decisionCommentPolicies, approvalStages,
                decisionEvidencePolicies, List.of()
        );
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
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
            List<ApprovalCompletionStep> completionSteps
    ) {
        return createDraft(
                name, approverIds, triggerBinding, recordStatusMapping,
                gateway, approvalMode, parallelGateway, inclusiveGateway,
                approverSources, quorumRules, deadlinePolicies,
                decisionCommentPolicies, approvalStages,
                decisionEvidencePolicies, completionSteps,
                CompletionFailurePolicy.MANUAL_RETRY);
    }

    public ApprovalDefinitionDraft createDraft(
            String name,
            List<Long> approverIds,
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
        var draft = new ApprovalDefinitionDraft(
                idService.nextId(),
                name,
                approverIds,
                1,
                now(),
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                quorumRules,
                deadlinePolicies,
                decisionCommentPolicies,
                approvalStages,
                decisionEvidencePolicies,
                completionSteps,
                completionFailurePolicy
        );
        return repository.saveDraft(draft);
    }

    public ApprovalDefinitionDraft reviseDraft(long definitionId, String name, long approverId) {
        return reviseDraft(definitionId, name, List.of(approverId));
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds
    ) {
        var current = draft(definitionId);
        return repository.saveDraft(current.revise(name, approverIds, now()));
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding
    ) {
        var current = draft(definitionId);
        return repository.saveDraft(current.revise(
                name,
                approverIds,
                triggerBinding,
                current.recordStatusMapping(),
                now()
        ));
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping
    ) {
        return reviseDraft(
                definitionId,
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                null
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway
    ) {
        return reviseDraft(
                definitionId,
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                ApprovalMode.SEQUENTIAL
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode
    ) {
        return reviseDraft(
                definitionId,
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                null
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway
    ) {
        return reviseDraft(
                definitionId,
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                null
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        return reviseDraft(
                definitionId, name, approverIds, triggerBinding, recordStatusMapping,
                gateway, approvalMode, parallelGateway, inclusiveGateway,
                ApprovalApproverSources.fixedFor(gateway, parallelGateway, inclusiveGateway)
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources
    ) {
        return reviseDraft(
                definitionId,
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                ApprovalQuorumRules.none()
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources,
            ApprovalQuorumRules quorumRules
    ) {
        return reviseDraft(
                definitionId,
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                quorumRules,
                null
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
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
        return reviseDraft(
                definitionId,
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                quorumRules,
                deadlinePolicies,
                ApprovalDecisionCommentPolicies.none()
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
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
        return reviseDraft(
                definitionId, name, approverIds, triggerBinding,
                recordStatusMapping, gateway, approvalMode, parallelGateway,
                inclusiveGateway, approverSources, quorumRules,
                deadlinePolicies, decisionCommentPolicies, null
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
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
        return reviseDraft(
                definitionId, name, approverIds, triggerBinding,
                recordStatusMapping, gateway, approvalMode, parallelGateway,
                inclusiveGateway, approverSources, quorumRules,
                deadlinePolicies, decisionCommentPolicies, approvalStages,
                null
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
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
        var current = draft(definitionId);
        return reviseDraft(
                current, name, approverIds, triggerBinding,
                recordStatusMapping, gateway, approvalMode, parallelGateway,
                inclusiveGateway, approverSources, quorumRules,
                deadlinePolicies, decisionCommentPolicies, approvalStages,
                decisionEvidencePolicies, current.completionSteps(),
                current.completionFailurePolicy()
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
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
            List<ApprovalCompletionStep> completionSteps
    ) {
        var current = draft(definitionId);
        return reviseDraft(
                current, name, approverIds, triggerBinding,
                recordStatusMapping, gateway, approvalMode, parallelGateway,
                inclusiveGateway, approverSources, quorumRules,
                deadlinePolicies, decisionCommentPolicies, approvalStages,
                decisionEvidencePolicies, completionSteps,
                current.completionFailurePolicy()
        );
    }

    public ApprovalDefinitionDraft reviseDraft(
            long definitionId,
            String name,
            List<Long> approverIds,
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
        var current = draft(definitionId);
        return reviseDraft(
                current, name, approverIds, triggerBinding,
                recordStatusMapping, gateway, approvalMode, parallelGateway,
                inclusiveGateway, approverSources, quorumRules,
                deadlinePolicies, decisionCommentPolicies, approvalStages,
                decisionEvidencePolicies, completionSteps,
                completionFailurePolicy);
    }

    private ApprovalDefinitionDraft reviseDraft(
            ApprovalDefinitionDraft current,
            String name,
            List<Long> approverIds,
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
        return repository.saveDraft(current.revise(
                name,
                approverIds,
                triggerBinding,
                recordStatusMapping,
                gateway,
                approvalMode,
                parallelGateway,
                inclusiveGateway,
                approverSources,
                quorumRules,
                deadlinePolicies,
                decisionCommentPolicies,
                approvalStages,
                decisionEvidencePolicies,
                completionSteps,
                completionFailurePolicy,
                now()
        ));
    }

    public Page<ApprovalDefinitionDraft> definitions(int page, int size) {
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findDrafts(bounds.offset(), bounds.size()),
                page,
                size,
                repository.countDrafts()
        );
    }

    public ApprovalDecisionCommentTemplate createDecisionCommentTemplate(
            String name,
            String body,
            long actorId
    ) {
        requirePositiveActor(actorId);
        var occurredAt = now();
        return repository.saveDecisionCommentTemplate(
                new ApprovalDecisionCommentTemplate(
                        idService.nextId(),
                        name,
                        body,
                        1,
                        ApprovalDecisionCommentTemplate.Status.ACTIVE,
                        actorId,
                        occurredAt,
                        actorId,
                        occurredAt
                )
        );
    }

    public ApprovalDecisionCommentTemplate reviseDecisionCommentTemplate(
            long templateId,
            String name,
            String body,
            long actorId
    ) {
        requirePositiveActor(actorId);
        return repository.saveDecisionCommentTemplate(
                decisionCommentTemplate(templateId).revise(
                        name, body, actorId, now())
        );
    }

    public ApprovalDecisionCommentTemplate activateDecisionCommentTemplate(
            long templateId,
            long actorId
    ) {
        return setDecisionCommentTemplateStatus(
                templateId,
                ApprovalDecisionCommentTemplate.Status.ACTIVE,
                actorId
        );
    }

    public ApprovalDecisionCommentTemplate deactivateDecisionCommentTemplate(
            long templateId,
            long actorId
    ) {
        return setDecisionCommentTemplateStatus(
                templateId,
                ApprovalDecisionCommentTemplate.Status.INACTIVE,
                actorId
        );
    }

    public Page<ApprovalDecisionCommentTemplate> decisionCommentTemplates(
            boolean activeOnly,
            int page,
            int size
    ) {
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findDecisionCommentTemplates(
                        activeOnly, bounds.offset(), bounds.size()),
                page,
                size,
                repository.countDecisionCommentTemplates(activeOnly)
        );
    }

    private ApprovalDecisionCommentTemplate setDecisionCommentTemplateStatus(
            long templateId,
            ApprovalDecisionCommentTemplate.Status status,
            long actorId
    ) {
        requirePositiveActor(actorId);
        var current = decisionCommentTemplate(templateId);
        if (current.status() == status) {
            return current;
        }
        return repository.saveDecisionCommentTemplate(
                current.withStatus(status, actorId, now()));
    }

    private ApprovalDecisionCommentTemplate decisionCommentTemplate(
            long templateId
    ) {
        if (templateId <= 0) {
            throw decisionCommentTemplateNotFound();
        }
        return repository.findDecisionCommentTemplate(templateId)
                .orElseThrow(
                        ApprovalWorkflowService::decisionCommentTemplateNotFound);
    }

    private static ApprovalDomainException decisionCommentTemplateNotFound() {
        return new ApprovalDomainException(
                COMMENT_TEMPLATE_NOT_FOUND,
                "Decision comment template was not found"
        );
    }

    private static void requirePositiveActor(long actorId) {
        if (actorId <= 0) {
            throw new IllegalArgumentException("Actor ID must be positive");
        }
    }

    public Page<ApprovalDefinitionVersion> startableDefinitions(int page, int size) {
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findStartableDefinitions(bounds.offset(), bounds.size()),
                page,
                size,
                repository.countStartableDefinitions()
        );
    }

    public Page<ApprovalDefinitionVersion> definitionVersions(
            long definitionId,
            int page,
            int size
    ) {
        draft(definitionId);
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findVersions(definitionId, bounds.offset(), bounds.size()),
                page,
                size,
                repository.countVersions(definitionId)
        );
    }

    public ApprovalDefinitionDraft definitionDraft(long definitionId) {
        return draft(definitionId);
    }

    public ApprovalDefinitionDraft restoreVersion(
            long definitionId,
            int version,
            long restoringMemberId
    ) {
        if (restoringMemberId <= 0) {
            throw new IllegalArgumentException("Restoring member ID must be positive");
        }
        var snapshot = definitionVersion(definitionId, version);
        var triggerBinding = snapshot.triggerBinding() == null
                ? null
                : snapshot.triggerBinding().forDraftRequester(restoringMemberId);
        var current = draft(definitionId);
        return repository.saveDraft(current.revise(
                snapshot.name(),
                snapshot.approverIds(),
                triggerBinding,
                snapshot.recordStatusMapping(),
                snapshot.gateway(),
                snapshot.approvalMode(),
                snapshot.parallelGateway(),
                snapshot.inclusiveGateway(),
                snapshot.approverSources(),
                snapshot.quorumRules(),
                snapshot.deadlinePolicies(),
                snapshot.decisionCommentPolicies(),
                snapshot.approvalStages(),
                snapshot.decisionEvidencePolicies(),
                now()
        ));
    }

    public synchronized ApprovalDefinitionVersion publish(long definitionId) {
        var draft = draft(definitionId);
        var nextVersion = repository.findLatestVersion(definitionId)
                .map(version -> version.version() + 1)
                .orElse(1);
        return repository.saveVersion(ApprovalDefinitionVersion.publish(draft, nextVersion, now()));
    }

    /**
     * Revises only the completion plan while preserving every other draft
     * setting. Extension graphs use this before publication so Webhook,
     * subflow and external-task nodes execute through the existing durable
     * completion workers instead of a duplicate side-effect path.
     */
    public synchronized ApprovalDefinitionDraft reviseCompletionSteps(
            long definitionId,
            List<ApprovalCompletionStep> completionSteps) {
        var current = draft(definitionId);
        var desired = ApprovalCompletionStep.requireSteps(completionSteps);
        if (current.completionSteps().equals(desired)) {
            return current;
        }
        return reviseDraft(
                definitionId,
                current.name(),
                current.approverIds(),
                current.triggerBinding(),
                current.recordStatusMapping(),
                current.gateway(),
                current.approvalMode(),
                current.parallelGateway(),
                current.inclusiveGateway(),
                current.approverSources(),
                current.quorumRules(),
                current.deadlinePolicies(),
                current.decisionCommentPolicies(),
                current.approvalStages(),
                current.decisionEvidencePolicies(),
                desired,
                current.completionFailurePolicy());
    }

    public List<ApprovalDefinitionVersion> triggerCandidates(
            String moduleCode,
            TriggerBinding.Event event
    ) {
        return repository.findTriggerCandidates(moduleCode, event);
    }

    public ApprovalDefinitionVersion definitionVersion(long definitionId, int version) {
        return repository.findVersion(definitionId, version)
                .orElseThrow(() -> new ApprovalDomainException(
                        VERSION_NOT_FOUND,
                        "Published approval definition version was not found"
                ));
    }

    public ApprovalDefinitionVersion latestDefinitionVersion(long definitionId) {
        return repository.findLatestVersion(definitionId)
                .orElseThrow(() -> new ApprovalDomainException(
                        VERSION_NOT_FOUND,
                        "Approval definition has no published version"
                ));
    }

    public FlowPeriodicScheduleState periodicSchedule(long definitionId) {
        return repository.findPeriodicSchedule(definitionId)
                .orElseThrow(() -> new ApprovalDomainException(
                        VERSION_NOT_FOUND,
                        "Published periodic schedule was not found"
                ));
    }

    public Optional<FlowTriggerDispatch> triggerDispatchForUpdate(String eventKey) {
        return repository.findTriggerDispatchForUpdate(eventKey);
    }

    public FlowTriggerDispatch saveTriggerDispatch(FlowTriggerDispatch dispatch) {
        return repository.saveTriggerDispatch(dispatch);
    }

    public ApprovalInstance startLatest(long definitionId, String businessKey, long requesterId) {
        return startLatest(definitionId, businessKey, requesterId, null);
    }

    public ApprovalInstance startLatest(
            long definitionId,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        var definition = repository.findLatestVersion(definitionId)
                .orElseThrow(() -> new ApprovalDomainException(
                        VERSION_NOT_FOUND,
                        "Approval definition has no published version"
                ));
        return start(definition, businessKey, requesterId, recordBinding);
    }

    public ApprovalInstance start(
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId
    ) {
        return start(definitionId, definitionVersion, businessKey, requesterId, null);
    }

    public ApprovalInstance start(
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        var definition = repository.findVersion(definitionId, definitionVersion)
                .orElseThrow(() -> new ApprovalDomainException(
                        VERSION_NOT_FOUND,
                        "Published approval definition version was not found"
                ));
        return start(definition, businessKey, requesterId, recordBinding);
    }

    public ApprovalInstance startResolved(
            long definitionId,
            int definitionVersion,
            List<Long> resolvedApproverIds,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        return startResolved(
                definitionId,
                definitionVersion,
                resolvedApproverIds,
                ApprovalMode.SEQUENTIAL,
                businessKey,
                requesterId,
                recordBinding
        );
    }

    public ApprovalInstance startResolved(
            long definitionId,
            int definitionVersion,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        var definition = definitionVersion(definitionId, definitionVersion);
        return startResolved(
                definition,
                resolvedApproverIds,
                resolvedApprovalMode,
                definition.quorumRules().requiredApprovals(
                        resolvedApprovalMode,
                        resolvedApproverIds.size()
                ),
                definition.deadlinePolicies().primary(),
                definition.decisionCommentPolicies().primary(),
                businessKey,
                requesterId,
                recordBinding
        );
    }

    public ApprovalInstance startResolved(
            long definitionId,
            int definitionVersion,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int requiredApprovals,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        var definition = definitionVersion(definitionId, definitionVersion);
        return startResolved(
                definition,
                resolvedApproverIds,
                resolvedApprovalMode,
                requiredApprovals,
                definition.deadlinePolicies().primary(),
                definition.decisionCommentPolicies().primary(),
                businessKey,
                requesterId,
                recordBinding
        );
    }

    public ApprovalInstance startResolved(
            long definitionId,
            int definitionVersion,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int requiredApprovals,
            ApprovalDeadlinePolicy deadlinePolicy,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        var definition = definitionVersion(definitionId, definitionVersion);
        return startResolved(
                definition,
                resolvedApproverIds,
                resolvedApprovalMode,
                requiredApprovals,
                deadlinePolicy,
                definition.decisionCommentPolicies().primary(),
                businessKey,
                requesterId,
                recordBinding
        );
    }

    public ApprovalInstance startResolved(
            long definitionId,
            int definitionVersion,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int requiredApprovals,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        return startResolved(
                definitionId, definitionVersion, resolvedApproverIds,
                resolvedApprovalMode, requiredApprovals, deadlinePolicy,
                decisionCommentPolicy, businessKey, requesterId,
                recordBinding, null);
    }

    public ApprovalInstance startResolved(
            long definitionId,
            int definitionVersion,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int requiredApprovals,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding,
            ApprovalStartContext startContext
    ) {
        return startResolved(
                definitionVersion(definitionId, definitionVersion),
                resolvedApproverIds,
                resolvedApprovalMode,
                requiredApprovals,
                deadlinePolicy,
                decisionCommentPolicy,
                businessKey,
                requesterId,
                recordBinding,
                startContext
        );
    }

    private ApprovalInstance startResolved(
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int requiredApprovals,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        return startResolved(
                definition, resolvedApproverIds, resolvedApprovalMode,
                requiredApprovals, deadlinePolicy, decisionCommentPolicy,
                businessKey, requesterId, recordBinding, null);
    }

    private ApprovalInstance startResolved(
            ApprovalDefinitionVersion definition,
            List<Long> resolvedApproverIds,
            ApprovalMode resolvedApprovalMode,
            int requiredApprovals,
            ApprovalDeadlinePolicy deadlinePolicy,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding,
            ApprovalStartContext startContext
    ) {
        return repository.saveInstance(ApprovalInstance.start(
                idService.nextId(),
                definition,
                resolvedApproverIds,
                resolvedApprovalMode,
                requiredApprovals,
                deadlinePolicy,
                decisionCommentPolicy,
                businessKey,
                requesterId,
                now(),
                recordBinding,
                startContext
        ));
    }

    public ApprovalInstance startBranches(
            long definitionId,
            int definitionVersion,
            List<? extends ApprovalBranchRoute> selectedBranches,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        return startBranches(
                definitionId, definitionVersion, selectedBranches,
                businessKey, requesterId, recordBinding, null);
    }

    public ApprovalInstance startBranches(
            long definitionId,
            int definitionVersion,
            List<? extends ApprovalBranchRoute> selectedBranches,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding,
            ApprovalStartContext startContext
    ) {
        var definition = definitionVersion(definitionId, definitionVersion);
        return repository.saveInstance(ApprovalInstance.startBranches(
                idService.nextId(),
                definition,
                selectedBranches,
                businessKey,
                requesterId,
                now(),
                recordBinding,
                startContext
        ));
    }

    public ApprovalInstance startBranch(
            long definitionId,
            int definitionVersion,
            ApprovalBranchRoute selectedBranch,
            List<Long> resolvedApproverIds,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding,
            ApprovalStartContext startContext
    ) {
        var definition = definitionVersion(definitionId, definitionVersion);
        return repository.saveInstance(ApprovalInstance.startBranch(
                idService.nextId(), definition, selectedBranch,
                resolvedApproverIds, businessKey, requesterId, now(),
                recordBinding, startContext));
    }

    public synchronized ApprovalDelegationRule createDelegation(
            long tenantId,
            long actorMemberId,
            boolean canManageOthers,
            long delegatorMemberId,
            long delegateMemberId,
            Instant startsAt,
            Instant endsAt,
            Long definitionId
    ) {
        requireDelegationManager(
                actorMemberId,
                canManageOthers,
                delegatorMemberId
        );
        if (tenantId <= 0) {
            throw delegationInvalid("Delegation tenant id must be positive");
        }
        if (endsAt == null || !endsAt.isAfter(now())) {
            throw delegationInvalid("Delegation end time must be in the future");
        }
        if (definitionId != null) {
            draft(definitionId);
        }
        var createdAt = now();
        var candidate = ApprovalDelegationRule.create(
                idService.nextId(),
                tenantId,
                delegatorMemberId,
                delegateMemberId,
                startsAt,
                endsAt,
                definitionId,
                actorMemberId,
                createdAt
        );
        var conflicts = repository.findDelegationConflictsForUpdate(
                delegatorMemberId,
                delegateMemberId,
                startsAt,
                endsAt
        );
        for (var existing : conflicts) {
            if (!existing.overlaps(startsAt, endsAt, definitionId)) {
                continue;
            }
            if (existing.delegatorMemberId() == delegatorMemberId) {
                throw delegationConflict(
                        "Delegation overlaps an existing rule for this delegator and scope"
                );
            }
            if (existing.delegateMemberId() == delegatorMemberId
                    || existing.delegatorMemberId() == delegateMemberId) {
                throw delegationConflict(
                        "Delegation chains and cycles are not supported"
                );
            }
        }
        return repository.saveDelegation(candidate);
    }

    public Page<ApprovalDelegationRule> delegations(
            long actorMemberId,
            boolean canManageOthers,
            long delegatorMemberId,
            int page,
            int size
    ) {
        requireDelegationManager(
                actorMemberId,
                canManageOthers,
                delegatorMemberId
        );
        var bounds = bounds(page, size);
        var effectiveAt = now();
        return new Page<>(
                repository.findDelegationsByDelegator(
                        delegatorMemberId,
                        bounds.offset(),
                        bounds.size()
                ).stream().map(rule -> rule.at(effectiveAt)).toList(),
                page,
                size,
                repository.countDelegationsByDelegator(delegatorMemberId)
        );
    }

    public synchronized ApprovalDelegationRule revokeDelegation(
            long tenantId,
            long delegationRuleId,
            long actorMemberId,
            boolean canManageOthers
    ) {
        var current = repository.findDelegationForUpdate(delegationRuleId)
                .orElseThrow(() -> new ApprovalDomainException(
                        DELEGATION_NOT_FOUND,
                        "Approval delegation rule was not found"
                ));
        if (current.tenantId() != tenantId) {
            throw new ApprovalDomainException(
                    DELEGATION_NOT_FOUND,
                    "Approval delegation rule was not found"
            );
        }
        requireDelegationManager(
                actorMemberId,
                canManageOthers,
                current.delegatorMemberId()
        );
        var revoked = current.revoke(actorMemberId, now());
        return revoked == current ? current : repository.saveDelegation(revoked);
    }

    public synchronized ApprovalInstance approve(long instanceId, long actorId, String comment) {
        return approve(instanceId, actorId, null, comment);
    }

    public synchronized ApprovalInstance approve(
            long instanceId,
            long actorId,
            Long representedMemberId,
            String comment
    ) {
        return approve(
                instanceId,
                actorId,
                representedMemberId,
                comment,
                (nextStage, completedStage, startContext) ->
                        defaultStageMembers(nextStage, completedStage)
        );
    }

    public synchronized ApprovalInstance approve(
            long instanceId,
            long actorId,
            Long representedMemberId,
            String comment,
            StageActivationResolver stageActivationResolver
    ) {
        Objects.requireNonNull(
                stageActivationResolver, "stageActivationResolver");
        return approve(
                instanceId, actorId, representedMemberId, comment,
                (nextStage, completedStage, startContext) ->
                        stageActivationResolver.resolve(
                                nextStage, completedStage));
    }

    public synchronized ApprovalInstance approve(
            long instanceId,
            long actorId,
            Long representedMemberId,
            String comment,
            ContextualStageActivationResolver stageActivationResolver
    ) {
        Objects.requireNonNull(
                stageActivationResolver, "stageActivationResolver");
        var current = lockedInstance(instanceId);
        var authority = decisionAuthority(current, actorId, representedMemberId);
        var occurredAt = now();
        var decided = current.approve(
                actorId,
                authority.representedMemberId(),
                authority.delegationRuleId(),
                comment,
                occurredAt
        );
        decided = activateWaitingStage(
                decided, stageActivationResolver, occurredAt);
        return saveDecisionResult(
                decided, null, actorId, occurredAt).instance();
    }

    public synchronized DecisionResult approveWithEvidence(
            long instanceId,
            long actorId,
            Long representedMemberId,
            String comment,
            DecisionEvidenceInput input,
            ContextualStageActivationResolver stageActivationResolver
    ) {
        Objects.requireNonNull(
                stageActivationResolver, "stageActivationResolver");
        var current = lockedInstance(instanceId);
        var authority = decisionAuthority(
                current, actorId, representedMemberId);
        var resolved = resolveDecisionComment(comment, input);
        var occurredAt = now();
        var stageIndex = current.currentStageIndex();
        validateDecisionEvidence(
                current.decisionEvidencePolicy(), resolved.input());
        var decided = current.approve(
                actorId,
                authority.representedMemberId(),
                authority.delegationRuleId(),
                resolved.comment(),
                occurredAt
        );
        var evidence = evidence(
                decided,
                null,
                stageIndex,
                ApprovalInstance.Decision.APPROVED,
                actorId,
                authority,
                occurredAt,
                current.decisionEvidencePolicy(),
                resolved
        );
        if (evidence != null) {
            decided = decided.withLatestDecisionEvidence(evidence);
        }
        decided = activateWaitingStage(
                decided, stageActivationResolver, occurredAt);
        return saveDecisionResult(
                decided, evidence, actorId, occurredAt);
    }

    private static List<Long> defaultStageMembers(
            ApprovalStage nextStage,
            ApprovalStageExecution completedStage
    ) {
        return switch (nextStage.approverSource().kind()) {
            case FIXED -> nextStage.approverIds();
            case PREVIOUS_HANDLER -> completedStage.actualHandlerIds();
            default -> throw new ApprovalDomainException(
                    ApprovalDomainException.Code.INSTANCE_STATE_INVALID,
                    "Later approval stage source is unsupported");
        };
    }

    public synchronized ApprovalInstance reject(long instanceId, long actorId, String reason) {
        return reject(instanceId, actorId, null, reason);
    }

    public synchronized ApprovalInstance reject(
            long instanceId,
            long actorId,
            Long representedMemberId,
            String reason
    ) {
        var current = instance(instanceId);
        var authority = decisionAuthority(current, actorId, representedMemberId);
        return repository.saveInstance(current.reject(
                actorId,
                authority.representedMemberId(),
                authority.delegationRuleId(),
                reason,
                now()
        ));
    }

    public synchronized DecisionResult rejectWithEvidence(
            long instanceId,
            long actorId,
            Long representedMemberId,
            String reason,
            DecisionEvidenceInput input
    ) {
        var current = lockedInstance(instanceId);
        var authority = decisionAuthority(
                current, actorId, representedMemberId);
        var resolved = resolveDecisionComment(reason, input);
        var occurredAt = now();
        var stageIndex = current.currentStageIndex();
        validateDecisionEvidence(
                current.decisionEvidencePolicy(), resolved.input());
        var decided = current.reject(
                actorId,
                authority.representedMemberId(),
                authority.delegationRuleId(),
                resolved.comment(),
                occurredAt
        );
        var evidence = evidence(
                decided,
                null,
                stageIndex,
                ApprovalInstance.Decision.REJECTED,
                actorId,
                authority,
                occurredAt,
                current.decisionEvidencePolicy(),
                resolved
        );
        if (evidence != null) {
            decided = decided.withLatestDecisionEvidence(evidence);
        }
        return saveDecisionResult(
                decided, evidence, actorId, occurredAt);
    }

    public synchronized ApprovalInstance approveBranch(
            long instanceId,
            String branchCode,
            long actorId,
            String comment
    ) {
        return approveBranch(instanceId, branchCode, actorId, null, comment);
    }

    public synchronized ApprovalInstance approveBranch(
            long instanceId,
            String branchCode,
            long actorId,
            Long representedMemberId,
            String comment
    ) {
        return approveBranch(
                instanceId, branchCode, actorId, representedMemberId,
                comment,
                (code, nextStage, completedStage, startContext) ->
                        defaultStageMembers(nextStage, completedStage));
    }

    public synchronized ApprovalInstance approveBranch(
            long instanceId,
            String branchCode,
            long actorId,
            Long representedMemberId,
            String comment,
            BranchStageActivationResolver stageActivationResolver
    ) {
        Objects.requireNonNull(
                stageActivationResolver, "stageActivationResolver");
        var current = lockedInstance(instanceId);
        var authority = decisionAuthority(current, actorId, representedMemberId);
        var occurredAt = now();
        var decided = current.approveBranch(
                branchCode,
                actorId,
                authority.representedMemberId(),
                authority.delegationRuleId(),
                comment,
                occurredAt
        );
        decided = activateWaitingBranchStage(
                decided, branchCode, stageActivationResolver, occurredAt);
        return saveDecisionResult(
                decided, null, actorId, occurredAt).instance();
    }

    public synchronized DecisionResult approveBranchWithEvidence(
            long instanceId,
            String branchCode,
            long actorId,
            Long representedMemberId,
            String comment,
            DecisionEvidenceInput input,
            BranchStageActivationResolver stageActivationResolver
    ) {
        Objects.requireNonNull(
                stageActivationResolver, "stageActivationResolver");
        var current = lockedInstance(instanceId);
        var branch = current.parallelBranch(branchCode);
        var authority = decisionAuthority(
                current, actorId, representedMemberId);
        var resolved = resolveDecisionComment(comment, input);
        var occurredAt = now();
        var stageIndex = branch.currentStageIndex();
        validateDecisionEvidence(
                branch.decisionEvidencePolicy(), resolved.input());
        var decided = current.approveBranch(
                branchCode,
                actorId,
                authority.representedMemberId(),
                authority.delegationRuleId(),
                resolved.comment(),
                occurredAt
        );
        var evidence = evidence(
                decided,
                branchCode,
                stageIndex,
                ApprovalInstance.Decision.APPROVED,
                actorId,
                authority,
                occurredAt,
                branch.decisionEvidencePolicy(),
                resolved
        );
        if (evidence != null) {
            decided = decided.withLatestDecisionEvidence(evidence);
        }
        decided = activateWaitingBranchStage(
                decided, branchCode, stageActivationResolver, occurredAt);
        return saveDecisionResult(
                decided, evidence, actorId, occurredAt);
    }

    public synchronized ApprovalInstance rejectBranch(
            long instanceId,
            String branchCode,
            long actorId,
            String reason
    ) {
        return rejectBranch(instanceId, branchCode, actorId, null, reason);
    }

    public synchronized ApprovalInstance rejectBranch(
            long instanceId,
            String branchCode,
            long actorId,
            Long representedMemberId,
            String reason
    ) {
        var current = instance(instanceId);
        var authority = decisionAuthority(current, actorId, representedMemberId);
        return repository.saveInstance(
                current.rejectBranch(
                        branchCode,
                        actorId,
                        authority.representedMemberId(),
                        authority.delegationRuleId(),
                        reason,
                        now()
                )
        );
    }

    public synchronized DecisionResult rejectBranchWithEvidence(
            long instanceId,
            String branchCode,
            long actorId,
            Long representedMemberId,
            String reason,
            DecisionEvidenceInput input
    ) {
        var current = lockedInstance(instanceId);
        var branch = current.parallelBranch(branchCode);
        var authority = decisionAuthority(
                current, actorId, representedMemberId);
        var resolved = resolveDecisionComment(reason, input);
        var occurredAt = now();
        var stageIndex = branch.currentStageIndex();
        validateDecisionEvidence(
                branch.decisionEvidencePolicy(), resolved.input());
        var decided = current.rejectBranch(
                branchCode,
                actorId,
                authority.representedMemberId(),
                authority.delegationRuleId(),
                resolved.comment(),
                occurredAt
        );
        var evidence = evidence(
                decided,
                branchCode,
                stageIndex,
                ApprovalInstance.Decision.REJECTED,
                actorId,
                authority,
                occurredAt,
                branch.decisionEvidencePolicy(),
                resolved
        );
        if (evidence != null) {
            decided = decided.withLatestDecisionEvidence(evidence);
        }
        return saveDecisionResult(
                decided, evidence, actorId, occurredAt);
    }

    synchronized ApprovalInstance markDeadlineReminded(
            long instanceId,
            String branchCode,
            long actorId,
            Instant occurredAt
    ) {
        return repository.saveInstance(
                instance(instanceId).markDeadlineReminded(
                        branchCode,
                        actorId,
                        occurredAt
                )
        );
    }

    synchronized ApprovalInstance processDeadline(
            long instanceId,
            String branchCode,
            long actorId,
            Instant occurredAt
    ) {
        return processDeadline(
                instanceId,
                branchCode,
                actorId,
                occurredAt,
                ApprovalWorkflowService::defaultStageMembers
        );
    }

    synchronized ApprovalInstance processDeadline(
            long instanceId,
            String branchCode,
            long actorId,
            Instant occurredAt,
            StageActivationResolver stageActivationResolver
    ) {
        Objects.requireNonNull(
                stageActivationResolver, "stageActivationResolver");
        var decided = lockedInstance(instanceId).processDeadline(
                branchCode,
                actorId,
                occurredAt
        );
        decided = activateWaitingStage(
                decided,
                (nextStage, completedStage, startContext) ->
                        stageActivationResolver.resolve(
                                nextStage, completedStage),
                occurredAt);
        return saveDecisionResult(
                decided, null, actorId, occurredAt).instance();
    }

    public synchronized ApprovalInstance withdraw(long instanceId, long actorId, String reason) {
        var occurredAt = now();
        var current = lockedInstance(instanceId);
        var withdrawn = current.withdraw(actorId, reason, occurredAt);
        persistCancelledExecutions(
                current, withdrawn, actorId, occurredAt);
        return repository.saveInstance(withdrawn);
    }

    public synchronized ApprovalInstance terminate(long instanceId, long actorId, String reason) {
        var occurredAt = now();
        var current = lockedInstance(instanceId);
        var terminated = current.terminate(actorId, reason, occurredAt);
        persistCancelledExecutions(
                current, terminated, actorId, occurredAt);
        return repository.saveInstance(terminated);
    }

    private void persistCancelledExecutions(
            ApprovalInstance current,
            ApprovalInstance closed,
            long actorId,
            Instant occurredAt
    ) {
        if (current.completionExecutions().isEmpty()) {
            return;
        }
        for (var target : closed.completionExecutions()) {
            var stored = repository.findCompletionExecutionForUpdate(
                            target.id())
                    .orElseThrow(() -> new ApprovalDomainException(
                            ApprovalDomainException.Code
                                    .COMPLETION_EXECUTION_NOT_FOUND,
                            "Completion execution was not found"));
            if (stored.status() == target.status()) {
                continue;
            }
            var cancelled = repository.saveCompletionExecution(
                    stored.cancel(occurredAt));
            var sequence = repository.findCompletionAttempts(cancelled.id())
                    .stream()
                    .mapToInt(ApprovalCompletionAttempt::eventSequence)
                    .max()
                    .orElse(0) + 1;
            repository.appendCompletionAttempt(
                    new ApprovalCompletionAttempt(
                            idService.nextId(),
                            cancelled.id(),
                            cancelled.attemptCount(),
                            sequence,
                            ApprovalCompletionAttempt.Event.CANCELLED,
                            actorId,
                            null,
                            null,
                            null,
                            null,
                            null,
                            occurredAt
                    )
            );
            if (stored.step().type()
                    == ApprovalCompletionStep.Type.SUBFLOW
                    && stored.status()
                    == ApprovalCompletionExecution.Status.RUNNING) {
                terminateActiveSubflowChild(
                        stored, actorId, occurredAt);
            }
        }
        persistCancelledCompensations(
                current, actorId, occurredAt);
    }

    private void persistCancelledCompensations(
            ApprovalInstance current,
            long actorId,
            Instant occurredAt
    ) {
        if (current.completionPhase()
                != ApprovalInstance.CompletionPhase.COMPENSATING) {
            return;
        }
        for (var stored : repository.findCompensationsForUpdate(
                current.id())) {
            if (stored.status()
                    == ApprovalCompletionExecution.Status.SUCCEEDED
                    || stored.status()
                    == ApprovalCompletionExecution.Status.FAILED
                    || stored.status()
                    == ApprovalCompletionExecution.Status.CANCELLED) {
                continue;
            }
            var cancelled = repository.saveCompensation(
                    stored.cancel(occurredAt));
            var sequence = repository.findCompensationAttempts(
                            cancelled.id()).stream()
                    .map(ApprovalCompensationAttempt::fact)
                    .mapToInt(ApprovalCompletionAttempt::eventSequence)
                    .max().orElse(0) + 1;
            repository.appendCompensationAttempt(
                    new ApprovalCompensationAttempt(
                            cancelled.id(),
                            new ApprovalCompletionAttempt(
                                    idService.nextId(), cancelled.id(),
                                    cancelled.attemptCount(), sequence,
                                    ApprovalCompletionAttempt.Event.CANCELLED,
                                    actorId, null, null, null, null, null,
                                    occurredAt)));
            if (stored.step().type()
                    == ApprovalCompletionStep.Type.SUBFLOW
                    && stored.status()
                    == ApprovalCompletionExecution.Status.RUNNING) {
                terminateActiveCompensationChild(
                        stored, actorId, occurredAt);
            }
        }
    }

    private void terminateActiveCompensationChild(
            ApprovalCompletionCompensation compensation,
            long actorId,
            Instant occurredAt
    ) {
        var wrapped = repository.findCompensationSubflowRunByAttempt(
                        compensation.id(), compensation.attemptCount())
                .orElse(null);
        if (wrapped == null || wrapped.run().terminal()) {
            return;
        }
        var child = repository.findInstanceForUpdate(
                        wrapped.run().childInstanceId())
                .orElse(null);
        if (child == null || child.status() != ApprovalInstance.Status.PENDING) {
            return;
        }
        var terminated = child.terminate(
                actorId, "Parent compensation was closed", occurredAt);
        persistCancelledExecutions(
                child, terminated, actorId, occurredAt);
        repository.saveInstance(terminated);
    }

    /** Parent is already locked; preserve parent -> execution -> child order. */
    private void terminateActiveSubflowChild(
            ApprovalCompletionExecution execution,
            long actorId,
            Instant occurredAt
    ) {
        var run = repository.findSubflowRunByExecutionAndAttempt(
                        execution.id(), execution.attemptCount())
                .orElse(null);
        if (run == null || run.terminal()) {
            return;
        }
        var child = repository.findInstanceForUpdate(run.childInstanceId())
                .orElse(null);
        if (child == null || child.status() != ApprovalInstance.Status.PENDING) {
            return;
        }
        var terminated = child.terminate(
                actorId, "Parent flow was closed", occurredAt);
        persistCancelledExecutions(
                child, terminated, actorId, occurredAt);
        repository.saveInstance(terminated);
    }

    public ApprovalInstance requireCurrentApprover(long instanceId, long actorId) {
        var instance = instance(instanceId);
        instance.requireCurrentApprover(actorId);
        return instance;
    }

    public synchronized ApprovalInstance transfer(
            ApprovalInstance expected,
            long actorId,
            long targetMemberId,
            String reason
    ) {
        return repository.saveInstance(expected.transfer(actorId, targetMemberId, reason, now()));
    }

    public synchronized ApprovalInstance addSign(
            ApprovalInstance expected,
            long actorId,
            long targetMemberId,
            ApprovalHistoryEvent.AssignmentPosition position,
            String reason
    ) {
        return repository.saveInstance(expected.addSign(
                actorId,
                targetMemberId,
                position,
                reason,
                now()
        ));
    }

    public synchronized ApprovalInstance returnToPrevious(
            long instanceId,
            long actorId,
            String reason
    ) {
        return repository.saveInstance(
                instance(instanceId).returnToPrevious(actorId, reason, now())
        );
    }

    public synchronized ApprovalInstance cancelClaim(
            long instanceId,
            long actorId,
            String reason
    ) {
        return repository.saveInstance(
                instance(instanceId).cancelClaim(actorId, reason, now())
        );
    }

    public synchronized ApprovalInstance claim(
            long instanceId,
            long actorId,
            String comment
    ) {
        return repository.saveInstance(instance(instanceId).claim(actorId, comment, now()));
    }

    public synchronized ApprovalInstance reduceSign(
            long instanceId,
            long actorId,
            Integer targetStepIndex,
            String reason
    ) {
        return repository.saveInstance(
                instance(instanceId).reduceSign(actorId, targetStepIndex, reason, now())
        );
    }

    public ApprovalInstance instance(long instanceId) {
        return repository.findInstance(instanceId)
                .orElseThrow(() -> new ApprovalDomainException(
                        INSTANCE_NOT_FOUND,
                        "Approval instance was not found"
                ));
    }

    private ApprovalInstance lockedInstance(long instanceId) {
        return repository.findInstanceForUpdate(instanceId)
                .orElseThrow(() -> new ApprovalDomainException(
                        INSTANCE_NOT_FOUND,
                        "Approval instance was not found"
                ));
    }

    private ApprovalInstance activateWaitingStage(
            ApprovalInstance decided,
            ContextualStageActivationResolver stageActivationResolver,
            Instant occurredAt
    ) {
        if (decided.status() != ApprovalInstance.Status.APPROVED
                || !decided.hasWaitingStage()) {
            return decided;
        }
        var definition = definitionVersion(
                decided.definitionId(), decided.definitionVersion());
        if (definition.approvalStages() == null
                || decided.currentStageIndex() + 1
                        >= definition.approvalStages().size()) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.INSTANCE_STATE_INVALID,
                    "Waiting stage has no matching published definition");
        }
        var nextStage = definition.approvalStages()
                .get(decided.currentStageIndex() + 1);
        var members = List.copyOf(stageActivationResolver.resolve(
                nextStage, decided.currentStage(), decided.startContext()));
        return decided.activateNextStage(nextStage, members, occurredAt);
    }

    private ApprovalInstance activateWaitingBranchStage(
            ApprovalInstance decided,
            String branchCode,
            BranchStageActivationResolver stageActivationResolver,
            Instant occurredAt
    ) {
        var branch = decided.parallelBranches().stream()
                .filter(candidate -> candidate.code().equals(branchCode))
                .findFirst()
                .orElseThrow(() -> new ApprovalDomainException(
                        ApprovalDomainException.Code.INSTANCE_STATE_INVALID,
                        "Approval branch was not found"));
        if (branch.status()
                        != com.unique.examine.flow.domain.ApprovalBranchExecution.Status.APPROVED
                || !branch.hasWaitingStage()) {
            return decided;
        }
        var definition = definitionVersion(
                decided.definitionId(), decided.definitionVersion());
        ApprovalBranchRoute route = definition.parallelGateway() != null
                ? definition.parallelGateway().branches().stream()
                        .filter(candidate -> candidate.code().equals(branchCode))
                        .findFirst()
                        .orElse(null)
                : definition.inclusiveGateway() != null
                        ? definition.inclusiveGateway().branches().stream()
                                .filter(candidate ->
                                        candidate.code().equals(branchCode))
                                .findFirst()
                                .orElse(null)
                        : null;
        if (route == null
                || route.approvalStages() == null
                || branch.currentStageIndex() + 1
                        >= route.approvalStages().size()) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.INSTANCE_STATE_INVALID,
                    "Waiting branch stage has no matching published definition");
        }
        var nextStage = route.approvalStages()
                .get(branch.currentStageIndex() + 1);
        var members = List.copyOf(stageActivationResolver.resolve(
                branchCode, nextStage, branch.currentStage(),
                decided.startContext()));
        return decided.activateNextBranchStage(
                branchCode, nextStage, members, occurredAt);
    }

    public Page<ApprovalInstance> instances(int page, int size) {
        return instances(null, null, null, page, size);
    }

    public Page<ApprovalInstance> instances(
            ApprovalInstance.Status status,
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size
    ) {
        requireInstanceRange(fromInclusive, toExclusive);
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findInstances(
                        status, fromInclusive, toExclusive,
                        bounds.offset(), bounds.size()),
                page,
                size,
                repository.countInstances(status, fromInclusive, toExclusive)
        );
    }

    public Page<ApprovalInstance> approvalTasks(
            long approverId,
            ApprovalTaskStatus status,
            int page,
            int size
    ) {
        if (approverId <= 0) {
            throw new IllegalArgumentException("approverId must be positive");
        }
        Objects.requireNonNull(status, "status");
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findApprovalTasks(approverId, status, bounds.offset(), bounds.size()),
                page,
                size,
                repository.countApprovalTasks(approverId, status)
        );
    }

    public Page<ApprovalTaskAssignment> approvalTaskAssignments(
            long actorMemberId,
            ApprovalTaskStatus status,
            int page,
            int size
    ) {
        if (actorMemberId <= 0) {
            throw new IllegalArgumentException("actorMemberId must be positive");
        }
        Objects.requireNonNull(status, "status");
        var bounds = bounds(page, size);
        var effectiveAt = now();
        return new Page<>(
                repository.findApprovalTaskAssignments(
                        actorMemberId,
                        status,
                        effectiveAt,
                        bounds.offset(),
                        bounds.size()
                ),
                page,
                size,
                repository.countApprovalTaskAssignments(
                        actorMemberId,
                        status,
                        effectiveAt
                )
        );
    }

    public Page<ApprovalInstance> claimableTasks(int page, int size) {
        var bounds = bounds(page, size);
        return new Page<>(
                repository.findClaimableTasks(bounds.offset(), bounds.size()),
                page,
                size,
                repository.countClaimableTasks()
        );
    }

    public List<ApprovalHistoryEvent> history(long instanceId) {
        return instance(instanceId).history();
    }

    private ResolvedDecisionComment resolveDecisionComment(
            String explicitComment,
            DecisionEvidenceInput input
    ) {
        input = input == null ? DecisionEvidenceInput.none() : input;
        if (input.commentTemplateId() == null) {
            return new ResolvedDecisionComment(
                    explicitComment, input, null);
        }
        if (explicitComment != null) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.COMMENT_TEMPLATE_INVALID,
                    "Explicit comment and commentTemplateId are mutually exclusive"
            );
        }
        var template = decisionCommentTemplate(input.commentTemplateId());
        if (template.status()
                != ApprovalDecisionCommentTemplate.Status.ACTIVE) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.COMMENT_TEMPLATE_INVALID,
                    "Decision comment template is inactive"
            );
        }
        return new ResolvedDecisionComment(
                template.body(),
                input,
                new ApprovalDecisionEvidence.TemplateSelection(
                        template.id(),
                        template.currentVersion(),
                        template.name()
                )
        );
    }

    private static void validateDecisionEvidence(
            ApprovalDecisionEvidencePolicy policy,
            DecisionEvidenceInput input
    ) {
        if (policy == null) {
            if (!input.attachments().isEmpty()
                    || input.signature() != null) {
                throw new ApprovalDomainException(
                        ApprovalDomainException.Code.EVIDENCE_INVALID,
                        "This decision does not accept file or signature evidence"
                );
            }
            return;
        }
        policy.validate(input.attachments(), input.signature());
    }

    private ApprovalDecisionEvidence evidence(
            ApprovalInstance decided,
            String branchCode,
            int stageIndex,
            ApprovalInstance.Decision decision,
            long actorId,
            DecisionAuthority authority,
            Instant occurredAt,
            ApprovalDecisionEvidencePolicy policy,
            ResolvedDecisionComment resolved
    ) {
        if (policy == null
                && resolved.template() == null
                && resolved.input().attachments().isEmpty()
                && resolved.input().signature() == null) {
            return null;
        }
        return new ApprovalDecisionEvidence(
                idService.nextId(),
                decided.id(),
                decided.history().size(),
                branchCode,
                stageIndex,
                decision,
                resolved.input().attachments(),
                resolved.input().signature(),
                resolved.template(),
                actorId,
                authority.representedMemberId(),
                authority.delegationRuleId(),
                occurredAt
        );
    }

    private ApprovalInstance materializeCompletion(
            ApprovalInstance decided,
            long actorId,
            Instant occurredAt
    ) {
        if (decided.status() != ApprovalInstance.Status.APPROVED) {
            return decided;
        }
        var definition = definitionVersion(
                decided.definitionId(), decided.definitionVersion());
        if (definition.completionSteps().isEmpty()) {
            return decided;
        }
        var trigger = definition.triggerBinding() == null
                ? null
                : new WebhookPayloadEncoder.TriggerReference(
                        definition.triggerBinding().moduleCode(),
                        definition.triggerBinding().event().name()
                );
        var record = decided.recordBinding() != null
                ? new WebhookPayloadEncoder.RecordReference(
                        decided.recordBinding().moduleCode(),
                        decided.recordBinding().recordId())
                : decided.startContext() != null
                && decided.startContext().hasRecord()
                        ? new WebhookPayloadEncoder.RecordReference(
                                decided.startContext().moduleCode(),
                                decided.startContext().recordId())
                        : null;
        var decisions = java.util.stream.IntStream.range(
                        0, decided.history().size())
                .filter(index -> {
                    var type = decided.history().get(index).type();
                    return type == ApprovalHistoryEvent.Type.APPROVED
                            || type == ApprovalHistoryEvent.Type.REJECTED
                            || type == ApprovalHistoryEvent.Type
                                    .DEADLINE_AUTO_APPROVED
                            || type == ApprovalHistoryEvent.Type
                                    .DEADLINE_AUTO_REJECTED;
                })
                .mapToObj(index -> {
                    var event = decided.history().get(index);
                    var approved = event.type()
                            == ApprovalHistoryEvent.Type.APPROVED
                            || event.type() == ApprovalHistoryEvent.Type
                                    .DEADLINE_AUTO_APPROVED;
                    return new WebhookPayloadEncoder.DecisionReference(
                            index + 1,
                            approved ? "APPROVED" : "REJECTED",
                            event.evidence() == null
                                    ? null
                                    : event.evidence().id()
                    );
                })
                .toList();
        var executions = new java.util.ArrayList<
                ApprovalCompletionExecution>();
        for (var ordinal = 0;
                ordinal < definition.completionSteps().size();
                ordinal++) {
            var step = definition.completionSteps().get(ordinal);
            var executionId = idService.nextId();
            var facts = new WebhookPayloadEncoder.DeliveryFacts(
                    executionId,
                    systemId,
                    tenantId,
                    decided.id(),
                    decided.definitionId(),
                    decided.definitionVersion(),
                    ordinal,
                    step.code(),
                    step.name(),
                    step.type().name(),
                    decided.businessKey(),
                    decided.requesterId(),
                    trigger,
                    record,
                    decisions
            );
            executions.add(ApprovalCompletionExecution.materialize(
                    executionId,
                    decided.id(),
                    decided.definitionId(),
                    decided.definitionVersion(),
                    ordinal,
                    step,
                    new String(
                            completionPayloads.encode(facts),
                            StandardCharsets.UTF_8),
                    occurredAt
            ));
        }
        var initialStage = ApprovalCompletionStage.materializeInitialStage(
                executions, occurredAt);
        var persisted = repository.materializeCompletionExecutions(
                initialStage);
        for (var activated : persisted.stream()
                .filter(execution -> execution.status()
                        == ApprovalCompletionExecution.Status.AVAILABLE)
                .toList()) {
            repository.appendCompletionAttempt(
                    new ApprovalCompletionAttempt(
                            idService.nextId(),
                            activated.id(),
                            0,
                            1,
                            ApprovalCompletionAttempt.Event.ACTIVATED,
                            actorId,
                            null,
                            null,
                            null,
                            null,
                            null,
                            occurredAt
                    ));
        }
        return decided.beginCompletion(persisted);
    }

    private DecisionResult saveDecisionResult(
            ApprovalInstance decided,
            ApprovalDecisionEvidence evidence,
            long actorId,
            Instant occurredAt
    ) {
        decided = materializeCompletion(
                decided, actorId, occurredAt);
        var saved = repository.saveInstance(decided);
        if (evidence == null) {
            return new DecisionResult(saved, null);
        }
        var persistedEvidence = repository.saveDecisionEvidence(evidence);
        return new DecisionResult(saved, persistedEvidence);
    }

    private ApprovalDefinitionDraft draft(long definitionId) {
        return repository.findDraft(definitionId)
                .orElseThrow(() -> new ApprovalDomainException(
                        DRAFT_NOT_FOUND,
                        "Approval definition draft was not found"
                ));
    }

    private ApprovalInstance start(
            ApprovalDefinitionVersion definition,
            String businessKey,
            long requesterId,
            ApprovalInstance.RecordBinding recordBinding
    ) {
        return repository.saveInstance(
                ApprovalInstance.start(
                        idService.nextId(),
                        definition,
                        businessKey,
                        requesterId,
                        now(),
                        recordBinding
                )
        );
    }

    private DecisionAuthority decisionAuthority(
            ApprovalInstance instance,
            long actorMemberId,
            Long requestedRepresentedMemberId
    ) {
        if (actorMemberId <= 0) {
            throw new ApprovalDomainException(
                    DELEGATION_FORBIDDEN,
                    "Decision actor member id must be positive"
            );
        }
        var representedMemberId = requestedRepresentedMemberId == null
                ? actorMemberId
                : requestedRepresentedMemberId;
        if (representedMemberId == actorMemberId) {
            return new DecisionAuthority(actorMemberId, null);
        }
        var effectiveAt = now();
        var rule = repository.findActiveDelegationForUpdate(
                actorMemberId,
                representedMemberId,
                instance.definitionId(),
                effectiveAt
        ).filter(value -> value.isEffectiveAt(
                effectiveAt,
                instance.definitionId()
        )).orElseThrow(() -> new ApprovalDomainException(
                DELEGATION_INACTIVE,
                "No active direct delegation authorizes this represented member"
        ));
        return new DecisionAuthority(representedMemberId, rule.id());
    }

    private static void requireDelegationManager(
            long actorMemberId,
            boolean canManageOthers,
            long delegatorMemberId
    ) {
        if (actorMemberId <= 0 || delegatorMemberId <= 0) {
            throw delegationInvalid(
                    "Delegation actor and delegator member ids must be positive"
            );
        }
        if (actorMemberId != delegatorMemberId && !canManageOthers) {
            throw new ApprovalDomainException(
                    DELEGATION_FORBIDDEN,
                    "Only the delegator or tenant management may manage this rule"
            );
        }
    }

    private static ApprovalDomainException delegationInvalid(String message) {
        return new ApprovalDomainException(DELEGATION_RULE_INVALID, message);
    }

    private static ApprovalDomainException delegationConflict(String message) {
        return new ApprovalDomainException(DELEGATION_CONFLICT, message);
    }

    private Instant now() {
        return clock.instant();
    }

    private static PageBounds bounds(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        var offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page and size produce an unsupported offset");
        }
        return new PageBounds((int) offset, size);
    }

    private static void requireInstanceRange(
            Instant fromInclusive,
            Instant toExclusive
    ) {
        if (fromInclusive != null && toExclusive != null
                && !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    public record Page<T>(List<T> items, int page, int size, long total) {
        public Page {
            items = List.copyOf(items);
            if (page < 1 || size < 1 || total < 0) {
                throw new IllegalArgumentException("Flow page metadata is invalid");
            }
        }
    }

    private record PageBounds(int offset, int size) {
    }

    private record DecisionAuthority(
            long representedMemberId,
            Long delegationRuleId
    ) {
    }

    private record ResolvedDecisionComment(
            String comment,
            DecisionEvidenceInput input,
            ApprovalDecisionEvidence.TemplateSelection template
    ) {
    }
}
