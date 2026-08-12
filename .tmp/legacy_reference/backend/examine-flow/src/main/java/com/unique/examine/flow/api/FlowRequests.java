package com.unique.examine.flow.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public final class FlowRequests {
    private FlowRequests() {
    }

    public record CreateDefinition(
            String name,
            String approverId,
            List<String> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            Gateway gateway,
            String approvalMode,
            ParallelGateway parallelGateway,
            InclusiveGateway inclusiveGateway,
            ApproverSource approverSource,
            QuorumRule quorumRule,
            DeadlinePolicy deadlinePolicy,
            DecisionCommentPolicy decisionCommentPolicy,
            List<ApprovalStage> approvalStages,
            DecisionEvidencePolicy decisionEvidencePolicy,
            List<CompletionStep> completionSteps,
            String completionFailurePolicy
    ) {
        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy,
                List<ApprovalStage> approvalStages,
                DecisionEvidencePolicy decisionEvidencePolicy,
                List<CompletionStep> completionSteps
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, approvalMode,
                    parallelGateway, inclusiveGateway, approverSource,
                    quorumRule, deadlinePolicy, decisionCommentPolicy,
                    approvalStages, decisionEvidencePolicy, completionSteps,
                    null);
        }

        public CreateDefinition {
            completionSteps = completionSteps == null
                    ? List.of()
                    : List.copyOf(completionSteps);
        }

        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy,
                List<ApprovalStage> approvalStages,
                DecisionEvidencePolicy decisionEvidencePolicy
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, approvalMode, parallelGateway,
                    inclusiveGateway, approverSource, quorumRule, deadlinePolicy,
                    decisionCommentPolicy, approvalStages,
                    decisionEvidencePolicy, null
            );
        }

        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy
        ) {
            this(
                    name, approverId, approverIds, triggerBinding, recordStatusMapping,
                    gateway, approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, quorumRule, deadlinePolicy, null, null, null
            );
        }

        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource,
                QuorumRule quorumRule
        ) {
            this(
                    name, approverId, approverIds, triggerBinding, recordStatusMapping,
                    gateway, approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, quorumRule, null, null, null, null
            );
        }

        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource
        ) {
            this(
                    name, approverId, approverIds, triggerBinding, recordStatusMapping,
                    gateway, approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, null, null, null, null, null
            );
        }

        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, approvalMode, parallelGateway,
                    null, null, null, null, null, null, null
            );
        }

        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, approvalMode,
                    null, null, null, null, null, null, null, null
            );
        }

        public CreateDefinition(String name, String approverId, List<String> approverIds) {
            this(
                    name, approverId, approverIds, null, null, null, null,
                    null, null, null, null, null, null, null, null
            );
        }

        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding
        ) {
            this(
                    name, approverId, approverIds, triggerBinding, null, null,
                    null, null, null, null, null, null, null, null, null
            );
        }

        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, null, null, null, null, null, null,
                    null, null, null, null
            );
        }

        public CreateDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, null, null, null, null, null,
                    null, null, null, null
            );
        }
    }

    public record ReviseDefinition(
            String name,
            String approverId,
            List<String> approverIds,
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping,
            Gateway gateway,
            String approvalMode,
            ParallelGateway parallelGateway,
            InclusiveGateway inclusiveGateway,
            ApproverSource approverSource,
            QuorumRule quorumRule,
            DeadlinePolicy deadlinePolicy,
            DecisionCommentPolicy decisionCommentPolicy,
            List<ApprovalStage> approvalStages,
            DecisionEvidencePolicy decisionEvidencePolicy,
            List<CompletionStep> completionSteps,
            String completionFailurePolicy
    ) {
        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy,
                List<ApprovalStage> approvalStages,
                DecisionEvidencePolicy decisionEvidencePolicy,
                List<CompletionStep> completionSteps
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, approvalMode,
                    parallelGateway, inclusiveGateway, approverSource,
                    quorumRule, deadlinePolicy, decisionCommentPolicy,
                    approvalStages, decisionEvidencePolicy, completionSteps,
                    null);
        }

        public ReviseDefinition {
            completionSteps = completionSteps == null
                    ? List.of()
                    : List.copyOf(completionSteps);
        }

        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy,
                List<ApprovalStage> approvalStages,
                DecisionEvidencePolicy decisionEvidencePolicy
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, approvalMode, parallelGateway,
                    inclusiveGateway, approverSource, quorumRule, deadlinePolicy,
                    decisionCommentPolicy, approvalStages,
                    decisionEvidencePolicy, null
            );
        }

        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy
        ) {
            this(
                    name, approverId, approverIds, triggerBinding, recordStatusMapping,
                    gateway, approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, quorumRule, deadlinePolicy, null, null, null
            );
        }

        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource,
                QuorumRule quorumRule
        ) {
            this(
                    name, approverId, approverIds, triggerBinding, recordStatusMapping,
                    gateway, approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, quorumRule, null, null, null, null
            );
        }

        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource
        ) {
            this(
                    name, approverId, approverIds, triggerBinding, recordStatusMapping,
                    gateway, approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, null, null, null, null, null
            );
        }

        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, approvalMode, parallelGateway,
                    null, null, null, null, null, null, null
            );
        }

        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, approvalMode,
                    null, null, null, null, null, null, null, null
            );
        }

        public ReviseDefinition(String name, String approverId, List<String> approverIds) {
            this(
                    name, approverId, approverIds, null, null, null, null,
                    null, null, null, null, null, null, null, null
            );
        }

        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding
        ) {
            this(
                    name, approverId, approverIds, triggerBinding, null, null,
                    null, null, null, null, null, null, null, null, null
            );
        }

        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, null, null, null, null, null, null,
                    null, null, null, null
            );
        }

        public ReviseDefinition(
                String name,
                String approverId,
                List<String> approverIds,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway
        ) {
            this(
                    name, approverId, approverIds, triggerBinding,
                    recordStatusMapping, gateway, null, null, null, null, null,
                    null, null, null, null
            );
        }
    }

    public record Gateway(List<GatewayBranch> branches) {
    }

    public record GatewayBranch(
            String code,
            String name,
            Boolean defaultBranch,
            List<TriggerCondition> conditions,
            List<String> approverIds,
            String approvalMode,
            ApproverSource approverSource,
            QuorumRule quorumRule,
            DeadlinePolicy deadlinePolicy,
            DecisionCommentPolicy decisionCommentPolicy,
            List<ApprovalStage> approvalStages,
            DecisionEvidencePolicy decisionEvidencePolicy
    ) {
        public GatewayBranch(
                String code,
                String name,
                Boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, deadlinePolicy,
                    null, null, null
            );
        }

        public GatewayBranch(
                String code,
                String name,
                Boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, null, null, null,
                    null
            );
        }

        public GatewayBranch(
                String code,
                String name,
                Boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, null, null, null, null,
                    null
            );
        }

        public GatewayBranch(
                String code,
                String name,
                Boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, null, null, null, null, null,
                    null
            );
        }

        public GatewayBranch(
                String code,
                String name,
                Boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    null, null, null, null, null, null,
                    null
            );
        }
    }

    public record ParallelGateway(List<ParallelBranch> branches) {
    }

    public record ParallelBranch(
            String code,
            String name,
            List<String> approverIds,
            String approvalMode,
            ApproverSource approverSource,
            QuorumRule quorumRule,
            DeadlinePolicy deadlinePolicy,
            DecisionCommentPolicy decisionCommentPolicy,
            List<ApprovalStage> approvalStages,
            DecisionEvidencePolicy decisionEvidencePolicy
    ) {
        public ParallelBranch(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy
        ) {
            this(
                    code, name, approverIds, approvalMode,
                    approverSource, quorumRule, deadlinePolicy, null, null, null
            );
        }

        public ParallelBranch(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule
        ) {
            this(
                    code, name, approverIds, approvalMode,
                    approverSource, quorumRule, null, null, null, null
            );
        }

        public ParallelBranch(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource
        ) {
            this(
                    code, name, approverIds, approvalMode, approverSource,
                    null, null, null, null, null);
        }

        public ParallelBranch(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode
        ) {
            this(
                    code, name, approverIds, approvalMode,
                    null, null, null, null, null, null);
        }
    }

    public record InclusiveGateway(List<InclusiveBranch> branches) {
    }

    public record InclusiveBranch(
            String code,
            String name,
            Boolean defaultBranch,
            List<TriggerCondition> conditions,
            List<String> approverIds,
            String approvalMode,
            ApproverSource approverSource,
            QuorumRule quorumRule,
            DeadlinePolicy deadlinePolicy,
            DecisionCommentPolicy decisionCommentPolicy,
            List<ApprovalStage> approvalStages,
            DecisionEvidencePolicy decisionEvidencePolicy
    ) {
        public InclusiveBranch(
                String code,
                String name,
                Boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, deadlinePolicy,
                    null, null, null
            );
        }

        public InclusiveBranch(
                String code,
                String name,
                Boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, null, null, null,
                    null
            );
        }

        public InclusiveBranch(
                String code,
                String name,
                Boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, null, null, null, null,
                    null
            );
        }

        public InclusiveBranch(
                String code,
                String name,
                Boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, null, null, null, null, null,
                    null
            );
        }
    }

    public record ApproverSource(String kind, String sourceId, String moduleCode) {
        public ApproverSource(String kind, String sourceId) {
            this(kind, sourceId, null);
        }
    }

    public record ApprovalStage(
            String code,
            String name,
            List<String> approverIds,
            String approvalMode,
            ApproverSource approverSource,
            QuorumRule quorumRule,
            DeadlinePolicy deadlinePolicy,
            DecisionCommentPolicy decisionCommentPolicy,
            DecisionEvidencePolicy decisionEvidencePolicy
    ) {
        public ApprovalStage {
            approverIds = approverIds == null ? null : List.copyOf(approverIds);
        }

        public ApprovalStage(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy
        ) {
            this(
                    code, name, approverIds, approvalMode, approverSource,
                    quorumRule, deadlinePolicy, decisionCommentPolicy, null
            );
        }
    }

    public record QuorumRule(String type, Integer value) {
    }

    public record DeadlinePolicy(
            Integer timeoutMinutes,
            Integer remindBeforeMinutes,
            String timeoutAction
    ) {
    }

    public record DecisionCommentPolicy(
            Boolean approveRequired,
            Boolean rejectRequired,
            Integer minimumLength
    ) {
    }

    public record DecisionEvidencePolicy(
            Integer minimumAttachments,
            Integer maximumAttachments,
            List<String> allowedMimeFamilies,
            String signatureMode
    ) {
        public DecisionEvidencePolicy {
            allowedMimeFamilies = allowedMimeFamilies == null
                    ? null
                    : List.copyOf(allowedMimeFamilies);
        }
    }

    public record CompletionStep(
            String code,
            String name,
            String type,
            ExternalTaskCompletion externalTask,
            WebhookCompletion webhook,
            SubflowCompletion subflow,
            String parallelGroup,
            Compensation compensation
    ) {
        public CompletionStep(
                String code,
                String name,
                String type,
                ExternalTaskCompletion externalTask,
                WebhookCompletion webhook,
                SubflowCompletion subflow,
                String parallelGroup
        ) {
            this(
                    code, name, type, externalTask, webhook, subflow,
                    parallelGroup, null);
        }

        public CompletionStep(
                String code,
                String name,
                String type,
                ExternalTaskCompletion externalTask,
                WebhookCompletion webhook,
                SubflowCompletion subflow
        ) {
            this(
                    code, name, type, externalTask, webhook, subflow, null,
                    null);
        }

        public CompletionStep(
                String code,
                String name,
                String type,
                ExternalTaskCompletion externalTask,
                WebhookCompletion webhook
        ) {
            this(code, name, type, externalTask, webhook, null);
        }
    }

    public record Compensation(
            String type,
            ExternalTaskCompletion externalTask,
            WebhookCompletion webhook,
            SubflowCompletion subflow
    ) {
    }

    public record ExternalTaskCompletion(
            String topic,
            Integer leaseSeconds,
            Integer maxAttempts,
            Integer resultJsonLimitBytes
    ) {
    }

    public record WebhookCompletion(
            String url,
            String secretRef,
            Integer timeoutSeconds,
            Integer maxAttempts,
            Integer baseBackoffSeconds
    ) {
    }

    /** Exact published child definition selected by a SUBFLOW step. */
    public record SubflowCompletion(
            String definitionId,
            Integer version
    ) {
    }

    public record TriggerBinding(
            String moduleCode,
            String event,
            Integer priority,
            Boolean exclusive,
            List<TriggerCondition> conditions,
            String startAt,
            Integer intervalMinutes
    ) {
        public TriggerBinding(
                String moduleCode,
                String event,
                Integer priority,
                Boolean exclusive
        ) {
            this(moduleCode, event, priority, exclusive, null, null, null);
        }

        public TriggerBinding(
                String moduleCode,
                String event,
                Integer priority,
                Boolean exclusive,
                List<TriggerCondition> conditions
        ) {
            this(moduleCode, event, priority, exclusive, conditions, null, null);
        }
    }

    public record TriggerCondition(
            String fieldCode,
            String operator,
            JsonNode value
    ) {
    }

    public record RecordStatusMapping(
            String fieldCode,
            String approvedValue,
            String rejectedValue,
            String withdrawnValue,
            String terminatedValue
    ) {
    }

    public record SimulateDefinition(
            String requesterId,
            String businessKey,
            TriggerSample trigger,
            Map<String, JsonNode> values
    ) {
        public SimulateDefinition {
            values = values == null ? Map.of() : Map.copyOf(values);
        }

        public SimulateDefinition(
                String requesterId,
                String businessKey,
                TriggerSample trigger
        ) {
            this(requesterId, businessKey, trigger, null);
        }
    }

    public record TriggerSample(
            String moduleCode,
            String event,
            Map<String, JsonNode> values
    ) {
        public TriggerSample {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record StartInstance(
            Integer definitionVersion,
            String businessKey,
            RecordBinding recordBinding,
            Map<String, JsonNode> values
    ) {
        public StartInstance {
            values = values == null ? Map.of() : Map.copyOf(values);
        }

        public StartInstance(Integer definitionVersion, String businessKey) {
            this(definitionVersion, businessKey, null, null);
        }

        public StartInstance(
                Integer definitionVersion,
                String businessKey,
                RecordBinding recordBinding
        ) {
            this(definitionVersion, businessKey, recordBinding, null);
        }
    }

    public record RecordBinding(String moduleCode, String recordId) {
    }

    public record Decision(
            String comment,
            String representedMemberId,
            List<String> attachmentFileIds,
            String signatureFileId,
            String typedSignature,
            String commentTemplateId
    ) {
        public Decision {
            attachmentFileIds = attachmentFileIds == null
                    ? null
                    : List.copyOf(attachmentFileIds);
        }

        public Decision(String comment, String representedMemberId) {
            this(comment, representedMemberId, null, null, null, null);
        }

        public Decision(String comment) {
            this(comment, null);
        }
    }

    public record Rejection(
            String reason,
            String representedMemberId,
            List<String> attachmentFileIds,
            String signatureFileId,
            String typedSignature,
            String commentTemplateId
    ) {
        public Rejection {
            attachmentFileIds = attachmentFileIds == null
                    ? null
                    : List.copyOf(attachmentFileIds);
        }

        public Rejection(String reason, String representedMemberId) {
            this(reason, representedMemberId, null, null, null, null);
        }

        public Rejection(String reason) {
            this(reason, null);
        }
    }

    public record CreateDecisionCommentTemplate(
            String name,
            String body
    ) {
    }

    public record ReviseDecisionCommentTemplate(
            String name,
            String body
    ) {
    }

    public record CreateDelegation(
            String delegatorMemberId,
            String delegateMemberId,
            String startsAt,
            String endsAt,
            String definitionId
    ) {
    }

    public record Withdrawal(String reason) {
    }

    public record Termination(String reason) {
    }

    public record Urge(String message) {
    }

    public record Comment(String body) {
    }

    public record Transfer(String targetMemberId, String reason) {
    }

    public record AddSign(String targetMemberId, String position, String reason) {
    }

    public record Return(String reason) {
    }

    public record CancelClaim(String reason) {
    }

    public record Claim(String comment) {
    }

    public record ReduceSign(Integer targetStepIndex, String reason) {
    }

    public record Copy(String targetMemberId, String message) {
    }

    public record ExternalTaskLease(String leaseToken) {
    }

    public record CompleteExternalTask(
            String leaseToken,
            JsonNode result
    ) {
    }

    public record FailExternalTask(
            String leaseToken,
            String code,
            String message
    ) {
    }
}
