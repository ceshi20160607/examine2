package com.unique.examine.flow.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.flow.domain.ApprovalDefinitionDraft;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDelegationRule;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.ApprovalInclusiveGateway;
import com.unique.examine.flow.domain.ApprovalBranchExecution;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalSubflowRun;
import com.unique.examine.flow.domain.ApprovalParallelGateway;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import com.unique.examine.flow.domain.ApprovalApproverSources;
import com.unique.examine.flow.domain.ApprovalTaskAssignment;
import com.unique.examine.flow.domain.FlowDraftPreflight;
import com.unique.examine.flow.interaction.FlowComment;
import com.unique.examine.flow.interaction.FlowCopy;
import com.unique.examine.flow.interaction.FlowInteractionService;
import com.unique.examine.flow.interaction.FlowUrge;
import com.unique.examine.flow.service.ApprovalWorkflowService;

import java.util.List;

public final class FlowViews {
    private FlowViews() {
    }

    public record DraftIssue(
            String severity,
            String code,
            String path,
            String message
    ) {
        static DraftIssue from(FlowDraftPreflight.Issue value) {
            return new DraftIssue(
                    value.severity().name(),
                    value.code(),
                    value.path(),
                    value.message()
            );
        }
    }

    public record DraftCheck(
            String definitionId,
            int revision,
            String verdict,
            int blockerCount,
            int warningCount,
            List<DraftIssue> issues
    ) {
        public DraftCheck {
            issues = List.copyOf(issues);
        }

        public static DraftCheck from(FlowDraftPreflight.Check value) {
            return new DraftCheck(
                    Long.toString(value.definitionId()),
                    value.revision(),
                    value.verdict().name(),
                    value.blockerCount(),
                    value.warningCount(),
                    value.issues().stream().map(DraftIssue::from).toList()
            );
        }
    }

    public record SimulationStep(int index, String approverId, boolean initial) {
        static SimulationStep from(FlowDraftPreflight.Step value) {
            return new SimulationStep(
                    value.index(),
                    Long.toString(value.approverId()),
                    value.initial()
            );
        }
    }

    public record SimulationTrigger(
            boolean configured,
            String event,
            String moduleCode,
            boolean matched,
            String reason
    ) {
        static SimulationTrigger from(FlowDraftPreflight.TriggerResult value) {
            return new SimulationTrigger(
                    value.configured(),
                    value.event(),
                    value.moduleCode(),
                    value.matched(),
                    value.reason()
            );
        }
    }

    public record SimulationRoute(
            boolean configured,
            String branchCode,
            String branchName,
            boolean defaultBranch,
            String approvalMode,
            int requiredApprovals,
            List<String> activeApproverIds,
            DeadlinePolicy deadlinePolicy,
            DecisionCommentPolicy decisionCommentPolicy,
            List<ApprovalStage> approvalStages
    ) {
        public SimulationRoute(
                boolean configured,
                String branchCode,
                String branchName,
                boolean defaultBranch,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy
        ) {
            this(
                    configured, branchCode, branchName, defaultBranch,
                    approvalMode, requiredApprovals, activeApproverIds,
                    deadlinePolicy, decisionCommentPolicy, null
            );
        }

        public SimulationRoute(
                boolean configured,
                String branchCode,
                String branchName,
                boolean defaultBranch,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                DeadlinePolicy deadlinePolicy
        ) {
            this(
                    configured, branchCode, branchName, defaultBranch,
                    approvalMode, requiredApprovals, activeApproverIds,
                    deadlinePolicy, null, null
            );
        }

        public SimulationRoute(
                boolean configured,
                String branchCode,
                String branchName,
                boolean defaultBranch,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds
        ) {
            this(
                    configured, branchCode, branchName, defaultBranch,
                    approvalMode, requiredApprovals, activeApproverIds,
                    null, null, null
            );
        }

        public SimulationRoute(
                boolean configured,
                String branchCode,
                String branchName,
                boolean defaultBranch,
                String approvalMode,
                List<String> activeApproverIds
        ) {
            this(
                    configured,
                    branchCode,
                    branchName,
                    defaultBranch,
                    approvalMode,
                    activeApproverIds == null || activeApproverIds.isEmpty()
                            ? 0
                            : "ANY".equals(approvalMode) ? 1 : activeApproverIds.size(),
                    activeApproverIds,
                    null,
                    null,
                    null
            );
        }

        public SimulationRoute {
            activeApproverIds = List.copyOf(activeApproverIds);
            approvalStages = approvalStages == null
                    ? null
                    : List.copyOf(approvalStages);
        }

        public SimulationRoute(
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
                    "SEQUENTIAL",
                    0,
                    List.of(),
                    null,
                    null,
                    null
            );
        }

        static SimulationRoute from(FlowDraftPreflight.RouteResult value) {
            return from(value, null);
        }

        static SimulationRoute from(
                FlowDraftPreflight.RouteResult value,
                List<com.unique.examine.flow.domain.ApprovalStage> approvalStages
        ) {
            return new SimulationRoute(
                    value.configured(),
                    value.branchCode(),
                    value.branchName(),
                    value.defaultBranch(),
                    value.approvalMode().name(),
                    value.requiredApprovals(),
                    value.activeApproverIds().stream().map(String::valueOf).toList(),
                    DeadlinePolicy.from(value.deadlinePolicy()),
                    DecisionCommentPolicy.from(value.decisionCommentPolicy()),
                    ApprovalStage.list(approvalStages)
            );
        }
    }

    public record DraftSimulation(
            String definitionId,
            int revision,
            DraftCheck check,
            String requesterId,
            String businessKey,
            boolean startable,
            String reason,
            List<SimulationStep> steps,
            SimulationTrigger trigger,
            RecordStatusMapping statusEffects,
            SimulationRoute route,
            List<SimulationRoute> parallelRoutes,
            List<ApprovalStage> approvalStages,
            List<CompletionStep> completionSteps,
            String completionFailurePolicy
    ) {
        public DraftSimulation(
                String definitionId,
                int revision,
                DraftCheck check,
                String requesterId,
                String businessKey,
                boolean startable,
                String reason,
                List<SimulationStep> steps,
                SimulationTrigger trigger,
                RecordStatusMapping statusEffects,
                SimulationRoute route,
                List<SimulationRoute> parallelRoutes,
                List<ApprovalStage> approvalStages,
                List<CompletionStep> completionSteps
        ) {
            this(
                    definitionId, revision, check, requesterId, businessKey,
                    startable, reason, steps, trigger, statusEffects, route,
                    parallelRoutes, approvalStages, completionSteps, null);
        }

        public DraftSimulation {
            steps = List.copyOf(steps);
            parallelRoutes = List.copyOf(parallelRoutes);
            approvalStages = approvalStages == null
                    ? null
                    : List.copyOf(approvalStages);
            completionSteps = completionSteps == null
                    ? List.of()
                    : List.copyOf(completionSteps);
        }

        public DraftSimulation(
                String definitionId,
                int revision,
                DraftCheck check,
                String requesterId,
                String businessKey,
                boolean startable,
                String reason,
                List<SimulationStep> steps,
                SimulationTrigger trigger,
                RecordStatusMapping statusEffects,
                SimulationRoute route
        ) {
            this(
                    definitionId, revision, check, requesterId, businessKey,
                    startable, reason, steps, trigger, statusEffects, route,
                    List.of(), null, null
            );
        }

        public DraftSimulation(
                String definitionId,
                int revision,
                DraftCheck check,
                String requesterId,
                String businessKey,
                boolean startable,
                String reason,
                List<SimulationStep> steps,
                SimulationTrigger trigger,
                RecordStatusMapping statusEffects,
                SimulationRoute route,
                List<SimulationRoute> parallelRoutes,
                List<ApprovalStage> approvalStages
        ) {
            this(
                    definitionId, revision, check, requesterId, businessKey,
                    startable, reason, steps, trigger, statusEffects, route,
                    parallelRoutes, approvalStages, null
            );
        }

        public static DraftSimulation from(FlowDraftPreflight.Simulation value) {
            return from(value, null, java.util.Map.of());
        }

        public static DraftSimulation from(
                FlowDraftPreflight.Simulation value,
                List<com.unique.examine.flow.domain.ApprovalStage> approvalStages
        ) {
            return from(value, approvalStages, java.util.Map.of(), null);
        }

        public static DraftSimulation from(
                FlowDraftPreflight.Simulation value,
                List<com.unique.examine.flow.domain.ApprovalStage> approvalStages,
                java.util.Map<
                        String,
                        List<com.unique.examine.flow.domain.ApprovalStage>>
                        branchApprovalStages
        ) {
            return from(
                    value, approvalStages, branchApprovalStages, null);
        }

        public static DraftSimulation from(
                FlowDraftPreflight.Simulation value,
                List<com.unique.examine.flow.domain.ApprovalStage> approvalStages,
                java.util.Map<
                        String,
                        List<com.unique.examine.flow.domain.ApprovalStage>>
                        branchApprovalStages,
                List<ApprovalCompletionStep> completionSteps
        ) {
            return from(
                    value, approvalStages, branchApprovalStages,
                    completionSteps, null);
        }

        public static DraftSimulation from(
                FlowDraftPreflight.Simulation value,
                List<com.unique.examine.flow.domain.ApprovalStage> approvalStages,
                java.util.Map<
                        String,
                        List<com.unique.examine.flow.domain.ApprovalStage>>
                        branchApprovalStages,
                List<ApprovalCompletionStep> completionSteps,
                com.unique.examine.flow.domain.CompletionFailurePolicy
                        completionFailurePolicy
        ) {
            var effects = value.statusEffects();
            java.util.Map<
                    String,
                    List<com.unique.examine.flow.domain.ApprovalStage>>
                    branchPlans = branchApprovalStages == null
                    ? java.util.Map.<
                            String,
                            List<com.unique.examine.flow.domain.ApprovalStage>>of()
                    : java.util.Map.copyOf(branchApprovalStages);
            var routeStages = value.route().branchCode() == null
                    ? approvalStages
                    : branchPlans.get(value.route().branchCode());
            return new DraftSimulation(
                    Long.toString(value.definitionId()),
                    value.revision(),
                    DraftCheck.from(value.check()),
                    Long.toString(value.requesterId()),
                    value.businessKey(),
                    value.startable(),
                    value.reason(),
                    value.steps().stream().map(SimulationStep::from).toList(),
                    SimulationTrigger.from(value.trigger()),
                    effects == null
                            ? null
                            : new RecordStatusMapping(
                                    effects.fieldCode(),
                                    effects.approvedValue(),
                                    effects.rejectedValue(),
                                    effects.withdrawnValue(),
                                    effects.terminatedValue()
                            ),
                    SimulationRoute.from(value.route(), routeStages),
                    value.parallelRoutes().stream()
                            .map(route -> SimulationRoute.from(
                                    route,
                                    branchPlans.get(route.branchCode())))
                            .toList(),
                    ApprovalStage.list(approvalStages),
                    CompletionStep.list(completionSteps),
                    completionFailurePolicy == null
                            ? "MANUAL_RETRY"
                            : completionFailurePolicy.name()
            );
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
            approverIds = List.copyOf(approverIds);
        }

        static ApprovalStage from(
                com.unique.examine.flow.domain.ApprovalStage value
        ) {
            return new ApprovalStage(
                    value.code(),
                    value.name(),
                    value.approverIds().stream().map(String::valueOf).toList(),
                    value.approvalMode().name(),
                    ApproverSource.from(value.approverSource()),
                    QuorumRule.from(value.quorumRule()),
                    DeadlinePolicy.from(value.deadlinePolicy()),
                    DecisionCommentPolicy.from(value.decisionCommentPolicy()),
                    DecisionEvidencePolicy.from(
                            value.decisionEvidencePolicy())
            );
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

        static List<ApprovalStage> list(
                List<com.unique.examine.flow.domain.ApprovalStage> values
        ) {
            return values == null
                    ? null
                    : values.stream().map(ApprovalStage::from).toList();
        }
    }

    public record DefinitionDraft(
            String definitionId,
            String name,
            String approverId,
            List<String> approverIds,
            int revision,
            String updatedAt,
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
        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt,
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
                    definitionId, name, approverId, approverIds, revision,
                    updatedAt, triggerBinding, recordStatusMapping, gateway,
                    approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, quorumRule, deadlinePolicy,
                    decisionCommentPolicy, approvalStages,
                    decisionEvidencePolicy, completionSteps, "MANUAL_RETRY");
        }

        public DefinitionDraft {
            approvalStages = approvalStages == null
                    ? null
                    : List.copyOf(approvalStages);
            completionSteps = completionSteps == null
                    ? List.of()
                    : List.copyOf(completionSteps);
        }

        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt,
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
                    definitionId, name, approverId, approverIds, revision,
                    updatedAt, triggerBinding, recordStatusMapping, gateway,
                    approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, quorumRule, deadlinePolicy,
                    decisionCommentPolicy, approvalStages,
                    decisionEvidencePolicy, null
            );
        }

        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt,
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
                List<ApprovalStage> approvalStages
        ) {
            this(
                    definitionId, name, approverId, approverIds, revision,
                    updatedAt, triggerBinding, recordStatusMapping, gateway,
                    approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, quorumRule, deadlinePolicy,
                    decisionCommentPolicy, approvalStages, null
            );
        }

        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt,
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
                    definitionId, name, approverId, approverIds, revision, updatedAt,
                    triggerBinding, recordStatusMapping, gateway, approvalMode,
                    parallelGateway, inclusiveGateway, approverSource, quorumRule,
                    deadlinePolicy, null, null
            );
        }

        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt,
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
                    definitionId, name, approverId, approverIds, revision, updatedAt,
                    triggerBinding, recordStatusMapping, gateway, approvalMode,
                    parallelGateway, inclusiveGateway, approverSource, quorumRule,
                    null, null, null
            );
        }

        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource
        ) {
            this(
                    definitionId, name, approverId, approverIds, revision, updatedAt,
                    triggerBinding, recordStatusMapping, gateway, approvalMode,
                    parallelGateway, inclusiveGateway, approverSource,
                    null, null, null, null
            );
        }

        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway
        ) {
            this(
                    definitionId, name, approverId, approverIds, revision, updatedAt,
                    triggerBinding, recordStatusMapping, gateway, approvalMode,
                    parallelGateway, null, null, null, null, null, null
            );
        }

        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode
        ) {
            this(
                    definitionId, name, approverId, approverIds, revision, updatedAt,
                    triggerBinding, recordStatusMapping, gateway, approvalMode,
                    null, null, null, null, null, null, null
            );
        }

        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt
        ) {
            this(
                    definitionId,
                    name,
                    approverId,
                    approverIds,
                    revision,
                    updatedAt,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public DefinitionDraft(
                String definitionId,
                String name,
                String approverId,
                List<String> approverIds,
                int revision,
                String updatedAt,
                TriggerBinding triggerBinding
        ) {
            this(
                    definitionId,
                    name,
                    approverId,
                    approverIds,
                    revision,
                    updatedAt,
                    triggerBinding,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public static DefinitionDraft from(ApprovalDefinitionDraft value) {
            return new DefinitionDraft(
                    Long.toString(value.id()),
                    value.name(),
                    Long.toString(value.approverId()),
                    value.approverIds().stream().map(id -> Long.toString(id)).toList(),
                    value.revision(),
                    value.updatedAt().toString(),
                    TriggerBinding.from(value.triggerBinding()),
                    RecordStatusMapping.from(value.recordStatusMapping()),
                    Gateway.from(
                            value.gateway(), value.approverSources(), value.quorumRules(),
                            value.deadlinePolicies(),
                            value.decisionCommentPolicies(),
                            value.decisionEvidencePolicies()),
                    value.approvalMode().name(),
                    ParallelGateway.from(
                            value.parallelGateway(), value.approverSources(), value.quorumRules(),
                            value.deadlinePolicies(),
                            value.decisionCommentPolicies(),
                            value.decisionEvidencePolicies()),
                    InclusiveGateway.from(
                            value.inclusiveGateway(), value.approverSources(), value.quorumRules(),
                            value.deadlinePolicies(),
                            value.decisionCommentPolicies(),
                            value.decisionEvidencePolicies()),
                    ApproverSource.from(value.approverSources().route()),
                    QuorumRule.from(value.quorumRules().primary()),
                    DeadlinePolicy.from(value.deadlinePolicies().primary()),
                    DecisionCommentPolicy.from(value.decisionCommentPolicies().primary()),
                    ApprovalStage.list(value.approvalStages()),
                    DecisionEvidencePolicy.from(
                            value.decisionEvidencePolicies().primary()),
                    CompletionStep.list(value.completionSteps()),
                    value.completionFailurePolicy().name()
            );
        }
    }

    public record DefinitionVersion(
            String definitionId,
            int version,
            String name,
            String approverId,
            List<String> approverIds,
            int sourceRevision,
            String publishedAt,
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
        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt,
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
                    definitionId, version, name, approverId, approverIds,
                    sourceRevision, publishedAt, triggerBinding,
                    recordStatusMapping, gateway, approvalMode,
                    parallelGateway, inclusiveGateway, approverSource,
                    quorumRule, deadlinePolicy, decisionCommentPolicy,
                    approvalStages, decisionEvidencePolicy, completionSteps,
                    "MANUAL_RETRY");
        }

        public DefinitionVersion {
            approvalStages = approvalStages == null
                    ? null
                    : List.copyOf(approvalStages);
            completionSteps = completionSteps == null
                    ? List.of()
                    : List.copyOf(completionSteps);
        }

        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt,
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
                    definitionId, version, name, approverId, approverIds,
                    sourceRevision, publishedAt, triggerBinding,
                    recordStatusMapping, gateway, approvalMode,
                    parallelGateway, inclusiveGateway, approverSource,
                    quorumRule, deadlinePolicy, decisionCommentPolicy,
                    approvalStages, decisionEvidencePolicy, null
            );
        }

        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt,
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
                List<ApprovalStage> approvalStages
        ) {
            this(
                    definitionId, version, name, approverId, approverIds,
                    sourceRevision, publishedAt, triggerBinding,
                    recordStatusMapping, gateway, approvalMode,
                    parallelGateway, inclusiveGateway, approverSource,
                    quorumRule, deadlinePolicy, decisionCommentPolicy,
                    approvalStages, null
            );
        }

        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt,
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
                    definitionId, version, name, approverId, approverIds,
                    sourceRevision, publishedAt, triggerBinding, recordStatusMapping,
                    gateway, approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, quorumRule, deadlinePolicy, null, null
            );
        }

        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt,
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
                    definitionId, version, name, approverId, approverIds,
                    sourceRevision, publishedAt, triggerBinding, recordStatusMapping,
                    gateway, approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, quorumRule, null, null, null
            );
        }

        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway,
                InclusiveGateway inclusiveGateway,
                ApproverSource approverSource
        ) {
            this(
                    definitionId, version, name, approverId, approverIds,
                    sourceRevision, publishedAt, triggerBinding, recordStatusMapping,
                    gateway, approvalMode, parallelGateway, inclusiveGateway,
                    approverSource, null, null, null, null
            );
        }

        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode,
                ParallelGateway parallelGateway
        ) {
            this(
                    definitionId, version, name, approverId, approverIds,
                    sourceRevision, publishedAt, triggerBinding,
                    recordStatusMapping, gateway, approvalMode, parallelGateway,
                    null, null, null, null, null, null
            );
        }

        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt,
                TriggerBinding triggerBinding,
                RecordStatusMapping recordStatusMapping,
                Gateway gateway,
                String approvalMode
        ) {
            this(
                    definitionId, version, name, approverId, approverIds,
                    sourceRevision, publishedAt, triggerBinding,
                    recordStatusMapping, gateway, approvalMode,
                    null, null, null, null, null, null, null
            );
        }

        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt
        ) {
            this(
                    definitionId,
                    version,
                    name,
                    approverId,
                    approverIds,
                    sourceRevision,
                    publishedAt,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public DefinitionVersion(
                String definitionId,
                int version,
                String name,
                String approverId,
                List<String> approverIds,
                int sourceRevision,
                String publishedAt,
                TriggerBinding triggerBinding
        ) {
            this(
                    definitionId,
                    version,
                    name,
                    approverId,
                    approverIds,
                    sourceRevision,
                    publishedAt,
                    triggerBinding,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public static DefinitionVersion from(ApprovalDefinitionVersion value) {
            return new DefinitionVersion(
                    Long.toString(value.definitionId()),
                    value.version(),
                    value.name(),
                    Long.toString(value.approverId()),
                    value.approverIds().stream().map(id -> Long.toString(id)).toList(),
                    value.sourceRevision(),
                    value.publishedAt().toString(),
                    TriggerBinding.from(value.triggerBinding()),
                    RecordStatusMapping.from(value.recordStatusMapping()),
                    Gateway.from(
                            value.gateway(), value.approverSources(), value.quorumRules(),
                            value.deadlinePolicies(),
                            value.decisionCommentPolicies(),
                            value.decisionEvidencePolicies()),
                    value.approvalMode().name(),
                    ParallelGateway.from(
                            value.parallelGateway(), value.approverSources(), value.quorumRules(),
                            value.deadlinePolicies(),
                            value.decisionCommentPolicies(),
                            value.decisionEvidencePolicies()),
                    InclusiveGateway.from(
                            value.inclusiveGateway(), value.approverSources(), value.quorumRules(),
                            value.deadlinePolicies(),
                            value.decisionCommentPolicies(),
                            value.decisionEvidencePolicies()),
                    ApproverSource.from(value.approverSources().route()),
                    QuorumRule.from(value.quorumRules().primary()),
                    DeadlinePolicy.from(value.deadlinePolicies().primary()),
                    DecisionCommentPolicy.from(value.decisionCommentPolicies().primary()),
                    ApprovalStage.list(value.approvalStages()),
                    DecisionEvidencePolicy.from(
                            value.decisionEvidencePolicies().primary()),
                    CompletionStep.list(value.completionSteps()),
                    value.completionFailurePolicy().name()
            );
        }
    }

    public record Gateway(List<GatewayBranch> branches) {
        public Gateway {
            branches = List.copyOf(branches);
        }

        static Gateway from(
                ApprovalGateway value,
                ApprovalApproverSources sources,
                com.unique.examine.flow.domain.ApprovalQuorumRules quorumRules,
                com.unique.examine.flow.domain.ApprovalDeadlinePolicies deadlinePolicies,
                com.unique.examine.flow.domain.ApprovalDecisionCommentPolicies
                        decisionCommentPolicies,
                com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicies
                        decisionEvidencePolicies
        ) {
            return value == null
                    ? null
                    : new Gateway(value.branches().stream()
                            .map(branch -> GatewayBranch.from(
                                    branch,
                                    sources.branch(branch.code()),
                                    quorumRules.branch(branch.code()),
                                    deadlinePolicies.branch(branch.code()),
                                    decisionCommentPolicies.branch(branch.code()),
                                    decisionEvidencePolicies.branch(
                                            branch.code())))
                            .toList());
        }
    }

    public record GatewayBranch(
            String code,
            String name,
            boolean defaultBranch,
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
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy,
                List<ApprovalStage> approvalStages
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, deadlinePolicy,
                    decisionCommentPolicy, approvalStages, null
            );
        }

        public GatewayBranch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, deadlinePolicy,
                    decisionCommentPolicy, null
            );
        }

        public GatewayBranch(
                String code,
                String name,
                boolean defaultBranch,
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
                    null, null
            );
        }

        public GatewayBranch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, null, null, null
            );
        }

        public GatewayBranch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, null, null, null, null
            );
        }

        public GatewayBranch {
            conditions = List.copyOf(conditions);
            approverIds = List.copyOf(approverIds);
            approvalStages = approvalStages == null
                    ? null
                    : List.copyOf(approvalStages);
        }

        public GatewayBranch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    "SEQUENTIAL", null, null, null, null, null);
        }

        public GatewayBranch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, null, null, null, null, null);
        }

        static GatewayBranch from(
                ApprovalGateway.Branch value,
                ApprovalApproverSource source,
                com.unique.examine.flow.domain.ApprovalQuorumRule quorumRule,
                com.unique.examine.flow.domain.ApprovalDeadlinePolicy deadlinePolicy,
                com.unique.examine.flow.domain.ApprovalDecisionCommentPolicy
                        decisionCommentPolicy,
                com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicy
                        decisionEvidencePolicy
        ) {
            return new GatewayBranch(
                    value.code(),
                    value.name(),
                    value.defaultBranch(),
                    value.conditions().stream().map(TriggerCondition::from).toList(),
                    value.approverIds().stream().map(String::valueOf).toList(),
                    value.approvalMode().name(),
                    ApproverSource.from(source),
                    QuorumRule.from(quorumRule),
                    DeadlinePolicy.from(deadlinePolicy),
                    DecisionCommentPolicy.from(decisionCommentPolicy),
                    ApprovalStage.list(value.approvalStages()),
                    DecisionEvidencePolicy.from(decisionEvidencePolicy)
            );
        }
    }

    public record ParallelGateway(List<ParallelBranch> branches) {
        public ParallelGateway {
            branches = List.copyOf(branches);
        }

        static ParallelGateway from(
                ApprovalParallelGateway value,
                ApprovalApproverSources sources,
                com.unique.examine.flow.domain.ApprovalQuorumRules quorumRules,
                com.unique.examine.flow.domain.ApprovalDeadlinePolicies deadlinePolicies,
                com.unique.examine.flow.domain.ApprovalDecisionCommentPolicies
                        decisionCommentPolicies,
                com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicies
                        decisionEvidencePolicies
        ) {
            return value == null
                    ? null
                    : new ParallelGateway(
                            value.branches().stream()
                                    .map(branch -> ParallelBranch.from(
                                            branch,
                                            sources.branch(branch.code()),
                                            quorumRules.branch(branch.code()),
                                            deadlinePolicies.branch(branch.code()),
                                            decisionCommentPolicies.branch(branch.code()),
                                            decisionEvidencePolicies.branch(
                                                    branch.code())))
                                    .toList()
                    );
        }
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
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy,
                List<ApprovalStage> approvalStages
        ) {
            this(
                    code, name, approverIds, approvalMode, approverSource,
                    quorumRule, deadlinePolicy, decisionCommentPolicy,
                    approvalStages, null
            );
        }

        public ParallelBranch(
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
                    approverSource, quorumRule, deadlinePolicy, null, null
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
                    approverSource, quorumRule, null, null, null
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
                    code, name, approverIds, approvalMode,
                    approverSource, null, null, null, null
            );
        }

        public ParallelBranch {
            approverIds = List.copyOf(approverIds);
            approvalStages = approvalStages == null
                    ? null
                    : List.copyOf(approvalStages);
        }

        public ParallelBranch(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode
        ) {
            this(
                    code, name, approverIds, approvalMode,
                    null, null, null, null, null);
        }

        static ParallelBranch from(
                ApprovalParallelGateway.Branch value,
                ApprovalApproverSource source,
                com.unique.examine.flow.domain.ApprovalQuorumRule quorumRule,
                com.unique.examine.flow.domain.ApprovalDeadlinePolicy deadlinePolicy,
                com.unique.examine.flow.domain.ApprovalDecisionCommentPolicy
                        decisionCommentPolicy,
                com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicy
                        decisionEvidencePolicy
        ) {
            return new ParallelBranch(
                    value.code(),
                    value.name(),
                    value.approverIds().stream().map(String::valueOf).toList(),
                    value.approvalMode().name(),
                    ApproverSource.from(source),
                    QuorumRule.from(quorumRule),
                    DeadlinePolicy.from(deadlinePolicy),
                    DecisionCommentPolicy.from(decisionCommentPolicy),
                    ApprovalStage.list(value.approvalStages()),
                    DecisionEvidencePolicy.from(decisionEvidencePolicy)
            );
        }
    }

    public record InclusiveGateway(List<InclusiveBranch> branches) {
        public InclusiveGateway {
            branches = List.copyOf(branches);
        }

        static InclusiveGateway from(
                ApprovalInclusiveGateway value,
                ApprovalApproverSources sources,
                com.unique.examine.flow.domain.ApprovalQuorumRules quorumRules,
                com.unique.examine.flow.domain.ApprovalDeadlinePolicies deadlinePolicies,
                com.unique.examine.flow.domain.ApprovalDecisionCommentPolicies
                        decisionCommentPolicies,
                com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicies
                        decisionEvidencePolicies
        ) {
            return value == null
                    ? null
                    : new InclusiveGateway(
                            value.branches().stream()
                                    .map(branch -> InclusiveBranch.from(
                                            branch,
                                            sources.branch(branch.code()),
                                            quorumRules.branch(branch.code()),
                                            deadlinePolicies.branch(branch.code()),
                                            decisionCommentPolicies.branch(branch.code()),
                                            decisionEvidencePolicies.branch(
                                                    branch.code())))
                                    .toList()
                    );
        }
    }

    public record InclusiveBranch(
            String code,
            String name,
            boolean defaultBranch,
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
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy,
                List<ApprovalStage> approvalStages
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, deadlinePolicy,
                    decisionCommentPolicy, approvalStages, null
            );
        }

        public InclusiveBranch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule,
                DeadlinePolicy deadlinePolicy,
                DecisionCommentPolicy decisionCommentPolicy
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, deadlinePolicy,
                    decisionCommentPolicy, null
            );
        }

        public InclusiveBranch(
                String code,
                String name,
                boolean defaultBranch,
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
                    null, null
            );
        }

        public InclusiveBranch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource,
                QuorumRule quorumRule
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, quorumRule, null, null, null
            );
        }

        public InclusiveBranch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode,
                ApproverSource approverSource
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, approverSource, null, null, null, null
            );
        }

        public InclusiveBranch {
            conditions = List.copyOf(conditions);
            approverIds = List.copyOf(approverIds);
            approvalStages = approvalStages == null
                    ? null
                    : List.copyOf(approvalStages);
        }

        public InclusiveBranch(
                String code,
                String name,
                boolean defaultBranch,
                List<TriggerCondition> conditions,
                List<String> approverIds,
                String approvalMode
        ) {
            this(
                    code, name, defaultBranch, conditions, approverIds,
                    approvalMode, null, null, null, null, null);
        }

        static InclusiveBranch from(
                ApprovalInclusiveGateway.Branch value,
                ApprovalApproverSource source,
                com.unique.examine.flow.domain.ApprovalQuorumRule quorumRule,
                com.unique.examine.flow.domain.ApprovalDeadlinePolicy deadlinePolicy,
                com.unique.examine.flow.domain.ApprovalDecisionCommentPolicy
                        decisionCommentPolicy,
                com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicy
                        decisionEvidencePolicy
        ) {
            return new InclusiveBranch(
                    value.code(),
                    value.name(),
                    value.defaultBranch(),
                    value.conditions().stream().map(TriggerCondition::from).toList(),
                    value.approverIds().stream().map(String::valueOf).toList(),
                    value.approvalMode().name(),
                    ApproverSource.from(source),
                    QuorumRule.from(quorumRule),
                    DeadlinePolicy.from(deadlinePolicy),
                    DecisionCommentPolicy.from(decisionCommentPolicy),
                    ApprovalStage.list(value.approvalStages()),
                    DecisionEvidencePolicy.from(decisionEvidencePolicy)
            );
        }
    }

    public record ApproverSource(String kind, String sourceId, String moduleCode) {
        static ApproverSource from(ApprovalApproverSource value) {
            return new ApproverSource(
                    value.kind().name(),
                    value.sourceId() == null ? null : value.sourceId().toString(),
                    value.moduleCode()
            );
        }
    }

    public record RecordMemberFieldSource(
            String sourceId,
            String moduleCode,
            String fieldCode,
            String fieldName
    ) {
    }

    public record RecordMemberFieldSourceCatalog(
            List<RecordMemberFieldSource> items
    ) {
        public RecordMemberFieldSourceCatalog {
            items = List.copyOf(items);
        }
    }

    public record QuorumRule(String type, int value) {
        static QuorumRule from(
                com.unique.examine.flow.domain.ApprovalQuorumRule value
        ) {
            return value == null
                    ? null
                    : new QuorumRule(value.type().name(), value.value());
        }
    }

    public record DeadlinePolicy(
            int timeoutMinutes,
            Integer remindBeforeMinutes,
            String timeoutAction
    ) {
        static DeadlinePolicy from(
                com.unique.examine.flow.domain.ApprovalDeadlinePolicy value
        ) {
            return value == null
                    ? null
                    : new DeadlinePolicy(
                            value.timeoutMinutes(),
                            value.remindBeforeMinutes(),
                            value.timeoutAction().name()
                    );
        }
    }

    public record DecisionCommentPolicy(
            boolean approveRequired,
            boolean rejectRequired,
            int minimumLength
    ) {
        static DecisionCommentPolicy from(
                com.unique.examine.flow.domain.ApprovalDecisionCommentPolicy value
        ) {
            return value == null
                    ? null
                    : new DecisionCommentPolicy(
                            value.approveRequired(),
                            value.rejectRequired(),
                            value.minimumLength()
                    );
        }
    }

    public record DecisionEvidencePolicy(
            int minimumAttachments,
            int maximumAttachments,
            List<String> allowedMimeFamilies,
            String signatureMode
    ) {
        public DecisionEvidencePolicy {
            allowedMimeFamilies = allowedMimeFamilies == null
                    ? null
                    : List.copyOf(allowedMimeFamilies);
        }

        static DecisionEvidencePolicy from(
                com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicy value
        ) {
            return value == null
                    ? null
                    : new DecisionEvidencePolicy(
                            value.minimumAttachments(),
                            value.maximumAttachments(),
                            value.allowedMimeFamilies() == null
                                    ? null
                                    : value.allowedMimeFamilies().stream()
                                            .map(Enum::name)
                                            .sorted()
                                            .toList(),
                            value.signatureMode().name()
                    );
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
                    code, name, type, externalTask, webhook, subflow, null);
        }

        public CompletionStep(
                String code,
                String name,
                String type,
                ExternalTaskCompletion externalTask,
                WebhookCompletion webhook
        ) {
            this(code, name, type, externalTask, webhook, null, null);
        }

        static CompletionStep from(ApprovalCompletionStep value) {
            return new CompletionStep(
                    value.code(),
                    value.name(),
                    value.type().name(),
                    ExternalTaskCompletion.from(value.externalTask()),
                    WebhookCompletion.from(value.webhook()),
                    SubflowCompletion.from(value.subflow()),
                    value.parallelGroup(),
                    Compensation.from(value.compensation())
            );
        }

        static List<CompletionStep> list(
                List<ApprovalCompletionStep> values
        ) {
            return values == null
                    ? List.of()
                    : values.stream().map(CompletionStep::from).toList();
        }
    }

    public record Compensation(
            String type,
            ExternalTaskCompletion externalTask,
            WebhookCompletion webhook,
            SubflowCompletion subflow
    ) {
        static Compensation from(
                ApprovalCompletionStep.Compensation value
        ) {
            return value == null
                    ? null
                    : new Compensation(
                            value.type().name(),
                            ExternalTaskCompletion.from(value.externalTask()),
                            WebhookCompletion.from(value.webhook()),
                            SubflowCompletion.from(value.subflow()));
        }
    }

    public record ExternalTaskCompletion(
            String topic,
            int leaseSeconds,
            int maxAttempts,
            int resultJsonLimitBytes
    ) {
        static ExternalTaskCompletion from(
                ApprovalCompletionStep.ExternalTask value
        ) {
            return value == null
                    ? null
                    : new ExternalTaskCompletion(
                            value.topic(),
                            value.leaseSeconds(),
                            value.maxAttempts(),
                            value.resultJsonLimitBytes()
                    );
        }
    }

    public record WebhookCompletion(
            String url,
            String secretRef,
            boolean secretConfigured,
            int timeoutSeconds,
            int maxAttempts,
            int baseBackoffSeconds
    ) {
        static WebhookCompletion from(
                ApprovalCompletionStep.Webhook value
        ) {
            return value == null
                    ? null
                    : new WebhookCompletion(
                            value.url(),
                            value.secretRef() == null ? null : "********",
                            value.secretRef() != null,
                            value.timeoutSeconds(),
                            value.maxAttempts(),
                            value.baseBackoffSeconds()
                    );
        }
    }

    public record SubflowCompletion(
            String definitionId,
            int version
    ) {
        static SubflowCompletion from(
                ApprovalCompletionStep.Subflow value
        ) {
            return value == null
                    ? null
                    : new SubflowCompletion(
                            Long.toString(value.definitionId()),
                            value.version()
                    );
        }
    }

    public record DecisionCommentTemplate(
            String templateId,
            String name,
            String body,
            int currentVersion,
            String status,
            String createdBy,
            String createdAt,
            String updatedBy,
            String updatedAt
    ) {
        public static DecisionCommentTemplate from(
                com.unique.examine.flow.domain.ApprovalDecisionCommentTemplate value
        ) {
            return new DecisionCommentTemplate(
                    Long.toString(value.id()),
                    value.name(),
                    value.body(),
                    value.currentVersion(),
                    value.status().name(),
                    Long.toString(value.createdBy()),
                    value.createdAt().toString(),
                    Long.toString(value.updatedBy()),
                    value.updatedAt().toString()
            );
        }
    }

    public record DecisionCommentTemplatePage(
            List<DecisionCommentTemplate> items,
            int page,
            int size,
            long total
    ) {
        public DecisionCommentTemplatePage {
            items = List.copyOf(items);
        }

        public static DecisionCommentTemplatePage from(
                ApprovalWorkflowService.Page<
                        com.unique.examine.flow.domain
                                .ApprovalDecisionCommentTemplate> value
        ) {
            return new DecisionCommentTemplatePage(
                    value.items().stream()
                            .map(DecisionCommentTemplate::from)
                            .toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }

    public record TriggerBinding(
            String moduleCode,
            String event,
            int priority,
            boolean exclusive,
            List<TriggerCondition> conditions,
            String startAt,
            Integer intervalMinutes,
            String requesterMemberId
    ) {
        private static final ObjectMapper JSON = new ObjectMapper();

        public TriggerBinding {
            conditions = List.copyOf(conditions);
        }

        public TriggerBinding(
                String moduleCode,
                String event,
                int priority,
                boolean exclusive,
                List<TriggerCondition> conditions
        ) {
            this(moduleCode, event, priority, exclusive, conditions, null, null, null);
        }

        static TriggerBinding from(com.unique.examine.flow.domain.TriggerBinding value) {
            return value == null
                    ? null
                    : new TriggerBinding(
                            value.moduleCode(),
                            value.event().name(),
                            value.priority(),
                            value.exclusive(),
                            value.conditions().stream().map(TriggerCondition::from).toList(),
                            value.periodicSchedule() == null
                                    ? null
                                    : value.periodicSchedule().startAt().toString(),
                            value.periodicSchedule() == null
                                    ? null
                                    : value.periodicSchedule().intervalMinutes(),
                            value.periodicSchedule() == null
                                    ? null
                                    : Long.toString(value.periodicSchedule().requesterMemberId())
                    );
        }

        private static JsonNode parse(String value) {
            if (value == null) {
                return null;
            }
            try {
                return JSON.readTree(value);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Published trigger condition JSON is invalid", exception);
            }
        }
    }

    public record TriggerCondition(
            String fieldCode,
            String operator,
            JsonNode value
    ) {
        public TriggerCondition {
            value = value == null ? null : value.deepCopy();
        }

        static TriggerCondition from(com.unique.examine.flow.domain.TriggerCondition value) {
            return new TriggerCondition(
                    value.fieldCode(),
                    value.operator().name(),
                    TriggerBinding.parse(value.valueJson())
            );
        }
    }

    public record PeriodicScheduleState(
            String definitionId,
            int definitionVersion,
            String requesterId,
            int intervalMinutes,
            String startAt,
            String nextFireAt,
            String lastScheduledAt,
            String lastInstanceId,
            String status,
            String pauseReason,
            String updatedAt
    ) {
        static PeriodicScheduleState from(
                com.unique.examine.flow.domain.FlowPeriodicScheduleState value
        ) {
            return new PeriodicScheduleState(
                    Long.toString(value.definitionId()),
                    value.definitionVersion(),
                    Long.toString(value.requesterId()),
                    value.intervalMinutes(),
                    value.startAt().toString(),
                    value.nextFireAt().toString(),
                    value.lastScheduledAt() == null ? null : value.lastScheduledAt().toString(),
                    value.lastInstanceId() == null
                            ? null
                            : Long.toString(value.lastInstanceId()),
                    value.status().name(),
                    value.pauseReason(),
                    value.updatedAt().toString()
            );
        }
    }

    public record RecordStatusMapping(
            String fieldCode,
            String approvedValue,
            String rejectedValue,
            String withdrawnValue,
            String terminatedValue
    ) {
        static RecordStatusMapping from(
                com.unique.examine.flow.domain.RecordStatusMapping value
        ) {
            return value == null
                    ? null
                    : new RecordStatusMapping(
                            value.fieldCode(),
                            value.approvedValue(),
                            value.rejectedValue(),
                            value.withdrawnValue(),
                            value.terminatedValue()
                    );
        }
    }

    public record DefinitionPage(
            List<DefinitionDraft> items,
            int page,
            int size,
            long total
    ) {
        public DefinitionPage {
            items = List.copyOf(items);
        }

        public static DefinitionPage from(ApprovalWorkflowService.Page<ApprovalDefinitionDraft> value) {
            return new DefinitionPage(
                    value.items().stream().map(DefinitionDraft::from).toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }

    public record DefinitionVersionPage(
            List<DefinitionVersion> items,
            int page,
            int size,
            long total
    ) {
        public DefinitionVersionPage {
            items = List.copyOf(items);
        }

        public static DefinitionVersionPage from(
                ApprovalWorkflowService.Page<ApprovalDefinitionVersion> value
        ) {
            return new DefinitionVersionPage(
                    value.items().stream().map(DefinitionVersion::from).toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }

    public record StartableDefinition(
            String definitionId,
            String name,
            int latestVersion,
            String publishedAt
    ) {
        static StartableDefinition from(ApprovalDefinitionVersion value) {
            return new StartableDefinition(
                    Long.toString(value.definitionId()),
                    value.name(),
                    value.version(),
                    value.publishedAt().toString()
            );
        }
    }

    public record StartableDefinitionPage(
            List<StartableDefinition> items,
            int page,
            int size,
            long total
    ) {
        public StartableDefinitionPage {
            items = List.copyOf(items);
        }

        public static StartableDefinitionPage from(
                ApprovalWorkflowService.Page<ApprovalDefinitionVersion> value
        ) {
            return new StartableDefinitionPage(
                    value.items().stream().map(StartableDefinition::from).toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }

    public record StageExecution(
            int stageIndex,
            String code,
            String name,
            String status,
            List<String> approverIds,
            String approvalMode,
            int requiredApprovals,
            List<String> actualHandlerIds,
            String startedAt,
            String completedAt,
            DeadlineState deadline,
            DecisionCommentPolicy decisionCommentPolicy,
            DecisionEvidencePolicy decisionEvidencePolicy
    ) {
        public StageExecution {
            approverIds = List.copyOf(approverIds);
            actualHandlerIds = List.copyOf(actualHandlerIds);
        }

        public StageExecution(
                int stageIndex,
                String code,
                String name,
                String status,
                List<String> approverIds,
                String approvalMode,
                int requiredApprovals,
                List<String> actualHandlerIds,
                String startedAt,
                String completedAt,
                DeadlineState deadline,
                DecisionCommentPolicy decisionCommentPolicy
        ) {
            this(
                    stageIndex, code, name, status, approverIds, approvalMode,
                    requiredApprovals, actualHandlerIds, startedAt,
                    completedAt, deadline, decisionCommentPolicy, null
            );
        }

        static StageExecution from(
                com.unique.examine.flow.domain.ApprovalStageExecution value
        ) {
            return new StageExecution(
                    value.stageIndex(),
                    value.code(),
                    value.name(),
                    value.status().name(),
                    value.approverIds().stream().map(String::valueOf).toList(),
                    value.approvalMode().name(),
                    value.requiredApprovals(),
                    value.actualHandlerIds().stream().map(String::valueOf).toList(),
                    value.startedAt() == null ? null : value.startedAt().toString(),
                    value.completedAt() == null ? null : value.completedAt().toString(),
                    DeadlineState.from(value.deadline()),
                    DecisionCommentPolicy.from(value.decisionCommentPolicy()),
                    DecisionEvidencePolicy.from(
                            value.decisionEvidencePolicy())
            );
        }
    }

    public record Instance(
            String instanceId,
            String definitionId,
            int definitionVersion,
            String businessKey,
            String requesterId,
            String approverId,
            List<String> approverIds,
            String status,
            String startedAt,
            String completedAt,
            int currentStepIndex,
            String claimState,
            RecordBinding recordBinding,
            String approvalMode,
            int requiredApprovals,
            List<String> activeApproverIds,
            List<String> approvedApproverIds,
            List<String> rejectedApproverIds,
            List<BranchExecution> parallelBranches,
            DeadlineState deadline,
            DecisionCommentPolicy decisionCommentPolicy,
            List<RepresentedAuthority> representedAuthorities,
            int currentStageIndex,
            String currentStageCode,
            List<StageExecution> stages,
            DecisionEvidencePolicy decisionEvidencePolicy,
            String completionPhase,
            Integer activeCompletionOrdinal,
            List<CompletionExecution> completionExecutions,
            List<Integer> activeCompletionOrdinals,
            List<CompensationExecution> compensationExecutions,
            String completionFailurePolicy
    ) {
        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                List<BranchExecution> parallelBranches,
                DeadlineState deadline,
                DecisionCommentPolicy decisionCommentPolicy,
                List<RepresentedAuthority> representedAuthorities,
                int currentStageIndex,
                String currentStageCode,
                List<StageExecution> stages,
                DecisionEvidencePolicy decisionEvidencePolicy,
                String completionPhase,
                Integer activeCompletionOrdinal,
                List<CompletionExecution> completionExecutions,
                List<Integer> activeCompletionOrdinals,
                List<CompensationExecution> compensationExecutions
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode, requiredApprovals, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, parallelBranches,
                    deadline, decisionCommentPolicy, representedAuthorities,
                    currentStageIndex, currentStageCode, stages,
                    decisionEvidencePolicy, completionPhase,
                    activeCompletionOrdinal, completionExecutions,
                    activeCompletionOrdinals, compensationExecutions,
                    "MANUAL_RETRY");
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                List<BranchExecution> parallelBranches,
                DeadlineState deadline,
                DecisionCommentPolicy decisionCommentPolicy,
                List<RepresentedAuthority> representedAuthorities,
                int currentStageIndex,
                String currentStageCode,
                List<StageExecution> stages,
                DecisionEvidencePolicy decisionEvidencePolicy,
                String completionPhase,
                Integer activeCompletionOrdinal,
                List<CompletionExecution> completionExecutions,
                List<Integer> activeCompletionOrdinals
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode, requiredApprovals, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, parallelBranches,
                    deadline, decisionCommentPolicy, representedAuthorities,
                    currentStageIndex, currentStageCode, stages,
                    decisionEvidencePolicy, completionPhase,
                    activeCompletionOrdinal, completionExecutions,
                    activeCompletionOrdinals, null);
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                List<BranchExecution> parallelBranches,
                DeadlineState deadline,
                DecisionCommentPolicy decisionCommentPolicy,
                List<RepresentedAuthority> representedAuthorities,
                int currentStageIndex,
                String currentStageCode,
                List<StageExecution> stages,
                DecisionEvidencePolicy decisionEvidencePolicy,
                String completionPhase,
                Integer activeCompletionOrdinal,
                List<CompletionExecution> completionExecutions
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode, requiredApprovals, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, parallelBranches,
                    deadline, decisionCommentPolicy, representedAuthorities,
                    currentStageIndex, currentStageCode, stages,
                    decisionEvidencePolicy, completionPhase,
                    activeCompletionOrdinal, completionExecutions, null);
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                List<BranchExecution> parallelBranches,
                DeadlineState deadline,
                DecisionCommentPolicy decisionCommentPolicy,
                List<RepresentedAuthority> representedAuthorities,
                int currentStageIndex,
                String currentStageCode,
                List<StageExecution> stages,
                DecisionEvidencePolicy decisionEvidencePolicy
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode, requiredApprovals, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, parallelBranches,
                    deadline, decisionCommentPolicy, representedAuthorities,
                    currentStageIndex, currentStageCode, stages,
                    decisionEvidencePolicy, null, null, null
            );
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                List<BranchExecution> parallelBranches,
                DeadlineState deadline,
                DecisionCommentPolicy decisionCommentPolicy,
                List<RepresentedAuthority> representedAuthorities,
                int currentStageIndex,
                String currentStageCode,
                List<StageExecution> stages
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode, requiredApprovals, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, parallelBranches,
                    deadline, decisionCommentPolicy, representedAuthorities,
                    currentStageIndex, currentStageCode, stages, null
            );
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                List<BranchExecution> parallelBranches,
                DeadlineState deadline,
                DecisionCommentPolicy decisionCommentPolicy
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode, requiredApprovals, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, parallelBranches,
                    deadline, decisionCommentPolicy, List.of(),
                    0, null, List.of()
            );
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                List<BranchExecution> parallelBranches,
                DeadlineState deadline
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode, requiredApprovals, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, parallelBranches,
                    deadline, null, List.of(), 0, null, List.of()
            );
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                int requiredApprovals,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                List<BranchExecution> parallelBranches
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode, requiredApprovals, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, parallelBranches,
                    null, null, List.of(), 0, null, List.of()
            );
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                List<BranchExecution> parallelBranches
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode,
                    "ANY".equals(approvalMode) ? 1 : approverIds.size(),
                    activeApproverIds, approvedApproverIds, rejectedApproverIds,
                    parallelBranches
            );
        }

        public Instance {
            activeApproverIds = List.copyOf(activeApproverIds);
            approvedApproverIds = List.copyOf(approvedApproverIds);
            rejectedApproverIds = List.copyOf(rejectedApproverIds);
            parallelBranches = List.copyOf(parallelBranches);
            representedAuthorities = List.copyOf(representedAuthorities);
            stages = List.copyOf(stages);
            completionExecutions = completionExecutions == null
                    ? List.of()
                    : List.copyOf(completionExecutions);
            activeCompletionOrdinals = activeCompletionOrdinals == null
                    ? List.of()
                    : List.copyOf(activeCompletionOrdinals);
            compensationExecutions = compensationExecutions == null
                    ? List.of()
                    : List.copyOf(compensationExecutions);
            completionFailurePolicy = completionFailurePolicy == null
                    ? "MANUAL_RETRY"
                    : completionFailurePolicy;
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState,
                RecordBinding recordBinding,
                String approvalMode,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds
        ) {
            this(
                    instanceId, definitionId, definitionVersion, businessKey,
                    requesterId, approverId, approverIds, status, startedAt,
                    completedAt, currentStepIndex, claimState, recordBinding,
                    approvalMode,
                    "ANY".equals(approvalMode) ? 1 : approverIds.size(),
                    activeApproverIds, approvedApproverIds,
                    rejectedApproverIds, List.of()
            );
        }

        public Instance(
                String instanceId,
                String definitionId,
                int definitionVersion,
                String businessKey,
                String requesterId,
                String approverId,
                List<String> approverIds,
                String status,
                String startedAt,
                String completedAt,
                int currentStepIndex,
                String claimState
        ) {
            this(
                    instanceId,
                    definitionId,
                    definitionVersion,
                    businessKey,
                    requesterId,
                    approverId,
                    approverIds,
                    status,
                    startedAt,
                    completedAt,
                    currentStepIndex,
                    claimState,
                    null,
                    "SEQUENTIAL",
                    approverIds.size(),
                    List.of(approverId),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        public static Instance from(ApprovalInstance value) {
            return fromWithAuthorities(value, List.of(), null, null);
        }

        public static Instance from(
                ApprovalInstance value,
                List<CompletionExecution> completionExecutions
        ) {
            return fromWithAuthorities(
                    value, List.of(), completionExecutions, null);
        }

        public static Instance from(
                ApprovalInstance value,
                List<CompletionExecution> completionExecutions,
                List<CompensationExecution> compensationExecutions
        ) {
            return fromWithAuthorities(
                    value, List.of(), completionExecutions,
                    compensationExecutions);
        }

        static Instance from(ApprovalTaskAssignment value) {
            return fromWithAuthorities(
                    value.instance(),
                    value.representedAuthorities().stream()
                            .map(RepresentedAuthority::from)
                            .toList(),
                    null,
                    null
            );
        }

        private static Instance fromWithAuthorities(
                ApprovalInstance value,
                List<RepresentedAuthority> representedAuthorities,
                List<CompletionExecution> requestedCompletionExecutions,
                List<CompensationExecution> requestedCompensationExecutions
        ) {
            return new Instance(
                    Long.toString(value.id()),
                    Long.toString(value.definitionId()),
                    value.definitionVersion(),
                    value.businessKey(),
                    Long.toString(value.requesterId()),
                    Long.toString(value.approverId()),
                    value.approverIds().stream().map(id -> Long.toString(id)).toList(),
                    value.status().name(),
                    value.startedAt().toString(),
                    value.completedAt() == null ? null : value.completedAt().toString(),
                    value.currentStepIndex(),
                    value.claimState().name(),
                    RecordBinding.from(value.recordBinding()),
                    value.approvalMode().name(),
                    value.requiredApprovals(),
                    value.activeApproverIds().stream().map(String::valueOf).toList(),
                    value.approvedApproverIds().stream().map(String::valueOf).toList(),
                    value.rejectedApproverIds().stream().map(String::valueOf).toList(),
                    value.parallelBranches().stream().map(BranchExecution::from).toList(),
                    DeadlineState.from(value.deadline()),
                    DecisionCommentPolicy.from(value.decisionCommentPolicy()),
                    representedAuthorities,
                    value.currentStageIndex(),
                    value.stages().get(value.currentStageIndex()).code(),
                    value.stages().stream().map(StageExecution::from).toList(),
                    DecisionEvidencePolicy.from(
                            value.decisionEvidencePolicy()),
                    value.completionPhase() == null
                            ? "HUMAN_APPROVAL"
                            : value.completionPhase().name(),
                    value.activeCompletionOrdinal(),
                    requestedCompletionExecutions == null
                            ? value.completionExecutions().stream()
                                    .map(execution ->
                                            CompletionExecution.from(
                                                    execution, List.of()))
                                    .toList()
                            : requestedCompletionExecutions,
                    value.activeCompletionOrdinals(),
                    requestedCompensationExecutions,
                    value.completionFailurePolicy().name()
            );
        }
    }

    public record CompensationExecution(
            String compensationExecutionId,
            String originalExecutionId,
            int originalOrdinal,
            String instanceId,
            String definitionId,
            int definitionVersion,
            String type,
            String status,
            int attemptCount,
            int maxAttempts,
            String availableAt,
            String leaseExpiresAt,
            String createdAt,
            String startedAt,
            String completedAt,
            ExternalTaskExecution externalTask,
            WebhookExecution webhook,
            SubflowCompletion subflow,
            JsonNode result,
            CompletionFailure failure,
            List<CompletionAttempt> attempts,
            List<SubflowRun> subflowRuns
    ) {
        public CompensationExecution {
            result = result == null ? null : result.deepCopy();
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
            subflowRuns = subflowRuns == null
                    ? List.of()
                    : List.copyOf(subflowRuns);
        }

        public static CompensationExecution from(
                com.unique.examine.flow.domain.ApprovalCompletionCompensation value,
                List<com.unique.examine.flow.domain.ApprovalCompensationAttempt>
                        attempts,
                List<com.unique.examine.flow.domain
                        .ApprovalCompensationSubflowRun> subflowRuns
        ) {
            var execution = value.execution();
            return new CompensationExecution(
                    Long.toString(value.id()),
                    Long.toString(value.originalExecutionId()),
                    value.originalOrdinal(),
                    Long.toString(value.instanceId()),
                    Long.toString(execution.definitionId()),
                    execution.definitionVersion(),
                    value.step().type().name(),
                    value.status().name(),
                    value.attemptCount(),
                    value.step().maxAttempts(),
                    time(execution.availableAt()),
                    execution.lease() == null
                            ? null
                            : time(execution.lease().expiresAt()),
                    time(execution.createdAt()),
                    time(execution.startedAt()),
                    time(execution.terminalAt()),
                    ExternalTaskExecution.from(value.step().externalTask()),
                    WebhookExecution.from(value.step().webhook()),
                    SubflowCompletion.from(value.step().subflow()),
                    CompletionExecution.json(execution.resultJson()),
                    CompletionFailure.from(execution.failure()),
                    attempts == null
                            ? List.of()
                            : attempts.stream()
                                    .map(item -> CompletionAttempt.from(
                                            item.fact()))
                                    .toList(),
                    subflowRuns == null
                            ? List.of()
                            : subflowRuns.stream()
                                    .map(item -> SubflowRun.from(item.run()))
                                    .toList());
        }
    }

    public record CompletionExecution(
            String executionId,
            String instanceId,
            String definitionId,
            int definitionVersion,
            int ordinal,
            String code,
            String name,
            String type,
            String status,
            int attemptCount,
            int maxAttempts,
            String availableAt,
            String leaseExpiresAt,
            String createdAt,
            String startedAt,
            String completedAt,
            ExternalTaskExecution externalTask,
            WebhookExecution webhook,
            JsonNode result,
            CompletionFailure failure,
            List<CompletionAttempt> attempts,
            List<SubflowRun> subflowRuns,
            String parallelGroup
    ) {
        public CompletionExecution(
                String executionId,
                String instanceId,
                String definitionId,
                int definitionVersion,
                int ordinal,
                String code,
                String name,
                String type,
                String status,
                int attemptCount,
                int maxAttempts,
                String availableAt,
                String leaseExpiresAt,
                String createdAt,
                String startedAt,
                String completedAt,
                ExternalTaskExecution externalTask,
                WebhookExecution webhook,
                JsonNode result,
                CompletionFailure failure,
                List<CompletionAttempt> attempts,
                List<SubflowRun> subflowRuns
        ) {
            this(
                    executionId, instanceId, definitionId,
                    definitionVersion, ordinal, code, name, type, status,
                    attemptCount, maxAttempts, availableAt, leaseExpiresAt,
                    createdAt, startedAt, completedAt, externalTask, webhook,
                    result, failure, attempts, subflowRuns, null);
        }

        private static final ObjectMapper JSON = new ObjectMapper();

        public CompletionExecution {
            result = result == null ? null : result.deepCopy();
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
            subflowRuns = subflowRuns == null
                    ? List.of()
                    : List.copyOf(subflowRuns);
        }

        public CompletionExecution(
                String executionId,
                String instanceId,
                String definitionId,
                int definitionVersion,
                int ordinal,
                String code,
                String name,
                String type,
                String status,
                int attemptCount,
                int maxAttempts,
                String availableAt,
                String leaseExpiresAt,
                String createdAt,
                String startedAt,
                String completedAt,
                ExternalTaskExecution externalTask,
                WebhookExecution webhook,
                JsonNode result,
                CompletionFailure failure,
                List<CompletionAttempt> attempts
        ) {
            this(
                    executionId, instanceId, definitionId,
                    definitionVersion, ordinal, code, name, type, status,
                    attemptCount, maxAttempts, availableAt, leaseExpiresAt,
                    createdAt, startedAt, completedAt, externalTask, webhook,
                    result, failure, attempts, null
            );
        }

        public static CompletionExecution from(
                ApprovalCompletionExecution value,
                List<ApprovalCompletionAttempt> attempts
        ) {
            return from(value, attempts, List.of());
        }

        public static CompletionExecution from(
                ApprovalCompletionExecution value,
                List<ApprovalCompletionAttempt> attempts,
                List<ApprovalSubflowRun> subflowRuns
        ) {
            return new CompletionExecution(
                    Long.toString(value.id()),
                    Long.toString(value.instanceId()),
                    Long.toString(value.definitionId()),
                    value.definitionVersion(),
                    value.ordinal(),
                    value.step().code(),
                    value.step().name(),
                    value.step().type().name(),
                    value.status().name(),
                    value.attemptCount(),
                    value.step().maxAttempts(),
                    time(value.availableAt()),
                    value.lease() == null
                            ? null
                            : time(value.lease().expiresAt()),
                    time(value.createdAt()),
                    time(value.startedAt()),
                    time(value.terminalAt()),
                    ExternalTaskExecution.from(value.step().externalTask()),
                    WebhookExecution.from(value.step().webhook()),
                    json(value.resultJson()),
                    CompletionFailure.from(value.failure()),
                    attempts == null
                            ? List.of()
                            : attempts.stream()
                                    .map(CompletionAttempt::from)
                                    .toList(),
                    subflowRuns == null
                            ? List.of()
                            : subflowRuns.stream()
                                    .map(SubflowRun::from)
                                    .toList(),
                    value.step().parallelGroup()
            );
        }

        private static JsonNode json(String value) {
            if (value == null) {
                return null;
            }
            try {
                return JSON.readTree(value);
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException(
                        "Completion result JSON is invalid", failure);
            }
        }
    }

    public record ExternalTaskExecution(
            String topic,
            int leaseSeconds,
            int resultJsonLimitBytes
    ) {
        static ExternalTaskExecution from(
                ApprovalCompletionStep.ExternalTask value
        ) {
            return value == null
                    ? null
                    : new ExternalTaskExecution(
                            value.topic(),
                            value.leaseSeconds(),
                            value.resultJsonLimitBytes()
                    );
        }
    }

    public record WebhookExecution(
            String url,
            String secretRef,
            boolean secretConfigured,
            int timeoutSeconds,
            int baseBackoffSeconds
    ) {
        static WebhookExecution from(
                ApprovalCompletionStep.Webhook value
        ) {
            return value == null
                    ? null
                    : new WebhookExecution(
                            value.url(),
                            value.secretRef() == null ? null : "********",
                            value.secretRef() != null,
                            value.timeoutSeconds(),
                            value.baseBackoffSeconds()
                    );
        }
    }

    public record CompletionFailure(
            String code,
            String message,
            boolean retryable
    ) {
        static CompletionFailure from(
                ApprovalCompletionExecution.Failure value
        ) {
            return value == null
                    ? null
                    : new CompletionFailure(
                            value.code(), value.message(), value.retryable());
        }
    }

    /** Sanitized child link; launch keys, root/depth and result coordination
     * metadata intentionally never cross the API boundary. */
    public record SubflowRun(
            int attempt,
            String childInstanceId,
            String targetDefinitionId,
            int targetVersion,
            String childStatus,
            String launchedAt,
            String terminalAt
    ) {
        public static SubflowRun from(ApprovalSubflowRun value) {
            return value == null
                    ? null
                    : new SubflowRun(
                            value.attemptNumber(),
                            Long.toString(value.childInstanceId()),
                            Long.toString(value.targetDefinitionId()),
                            value.targetVersion(),
                            value.status().name(),
                            time(value.launchedAt()),
                            time(value.terminalAt())
                    );
        }
    }

    public record CompletionAttempt(
            int attemptNumber,
            String status,
            Integer httpStatus,
            Long durationMs,
            String responseSha256,
            String failureCode,
            String failureMessage,
            String startedAt,
            String completedAt
    ) {
        static CompletionAttempt from(ApprovalCompletionAttempt value) {
            return new CompletionAttempt(
                    value.attemptNumber(),
                    value.event().name(),
                    value.httpStatus(),
                    value.durationMs(),
                    value.responseSha256(),
                    value.failureCode(),
                    value.failureMessage(),
                    time(value.startedAt()),
                    time(value.completedAt())
            );
        }
    }

    public record ExternalTaskPage(
            List<CompletionExecution> items,
            int page,
            int size,
            long total
    ) {
        public ExternalTaskPage {
            items = List.copyOf(items);
        }
    }

    public record ExternalTaskClaim(
            CompletionExecution execution,
            String leaseToken
    ) {
    }

    public record CompensationExternalTaskPage(
            List<CompensationExecution> items,
            int page,
            int size,
            long total
    ) {
        public CompensationExternalTaskPage {
            items = List.copyOf(items);
        }
    }

    public record CompensationExternalTaskClaim(
            CompensationExecution execution,
            String leaseToken
    ) {
    }

    private static String time(java.time.Instant value) {
        return value == null ? null : value.toString();
    }

    public record RepresentedAuthority(
            String representedMemberId,
            String delegationRuleId
    ) {
        static RepresentedAuthority from(
                ApprovalTaskAssignment.RepresentedAuthority value
        ) {
            return new RepresentedAuthority(
                    Long.toString(value.representedMemberId()),
                    value.delegationRuleId() == null
                            ? null
                            : Long.toString(value.delegationRuleId())
            );
        }
    }

    public record BranchExecution(
            String code,
            String name,
            List<String> approverIds,
            String approvalMode,
            int requiredApprovals,
            String approverId,
            int currentStepIndex,
            String status,
            List<String> activeApproverIds,
            List<String> approvedApproverIds,
            List<String> rejectedApproverIds,
            String startedAt,
            String completedAt,
            DeadlineState deadline,
            DecisionCommentPolicy decisionCommentPolicy,
            int currentStageIndex,
            String currentStageCode,
            List<StageExecution> stages,
            DecisionEvidencePolicy decisionEvidencePolicy
    ) {
        public BranchExecution(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode,
                int requiredApprovals,
                String approverId,
                int currentStepIndex,
                String status,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                String startedAt,
                String completedAt,
                DeadlineState deadline,
                DecisionCommentPolicy decisionCommentPolicy,
                int currentStageIndex,
                String currentStageCode,
                List<StageExecution> stages
        ) {
            this(
                    code, name, approverIds, approvalMode, requiredApprovals,
                    approverId, currentStepIndex, status, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, startedAt,
                    completedAt, deadline, decisionCommentPolicy,
                    currentStageIndex, currentStageCode, stages, null
            );
        }

        public BranchExecution(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode,
                int requiredApprovals,
                String approverId,
                int currentStepIndex,
                String status,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                String startedAt,
                String completedAt,
                DeadlineState deadline,
                DecisionCommentPolicy decisionCommentPolicy
        ) {
            this(
                    code, name, approverIds, approvalMode, requiredApprovals,
                    approverId, currentStepIndex, status, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, startedAt,
                    completedAt, deadline, decisionCommentPolicy,
                    0, null, List.of()
            );
        }

        public BranchExecution(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode,
                int requiredApprovals,
                String approverId,
                int currentStepIndex,
                String status,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                String startedAt,
                String completedAt,
                DeadlineState deadline
        ) {
            this(
                    code, name, approverIds, approvalMode, requiredApprovals,
                    approverId, currentStepIndex, status, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, startedAt,
                    completedAt, deadline, null, 0, null, List.of()
            );
        }

        public BranchExecution(
                String code,
                String name,
                List<String> approverIds,
                String approvalMode,
                int requiredApprovals,
                String approverId,
                int currentStepIndex,
                String status,
                List<String> activeApproverIds,
                List<String> approvedApproverIds,
                List<String> rejectedApproverIds,
                String startedAt,
                String completedAt
        ) {
            this(
                    code, name, approverIds, approvalMode, requiredApprovals,
                    approverId, currentStepIndex, status, activeApproverIds,
                    approvedApproverIds, rejectedApproverIds, startedAt,
                    completedAt, null, null, 0, null, List.of()
            );
        }

        public BranchExecution {
            approverIds = List.copyOf(approverIds);
            activeApproverIds = List.copyOf(activeApproverIds);
            approvedApproverIds = List.copyOf(approvedApproverIds);
            rejectedApproverIds = List.copyOf(rejectedApproverIds);
            stages = stages == null ? List.of() : List.copyOf(stages);
        }

        static BranchExecution from(ApprovalBranchExecution value) {
            return new BranchExecution(
                    value.code(),
                    value.name(),
                    value.approverIds().stream().map(String::valueOf).toList(),
                    value.approvalMode().name(),
                    value.requiredApprovals(),
                    Long.toString(value.approverId()),
                    value.currentStepIndex(),
                    value.status().name(),
                    value.activeApproverIds().stream().map(String::valueOf).toList(),
                    value.approvedApproverIds().stream().map(String::valueOf).toList(),
                    value.rejectedApproverIds().stream().map(String::valueOf).toList(),
                    value.startedAt().toString(),
                    value.completedAt() == null ? null : value.completedAt().toString(),
                    DeadlineState.from(value.deadline()),
                    DecisionCommentPolicy.from(value.decisionCommentPolicy()),
                    value.currentStageIndex(),
                    value.currentStage() == null
                            ? null
                            : value.currentStage().code(),
                    value.stages() == null
                            ? List.of()
                            : value.stages().stream()
                                    .map(StageExecution::from)
                                    .toList(),
                    DecisionEvidencePolicy.from(
                            value.decisionEvidencePolicy())
            );
        }
    }

    public record DeadlineState(
            DeadlinePolicy policy,
            String remindAt,
            String dueAt,
            String remindedAt,
            String processedAt,
            boolean overdue
    ) {
        static DeadlineState from(
                com.unique.examine.flow.domain.ApprovalDeadlineState value
        ) {
            return value == null
                    ? null
                    : new DeadlineState(
                            DeadlinePolicy.from(value.policy()),
                            value.remindAt() == null ? null : value.remindAt().toString(),
                            value.dueAt().toString(),
                            value.remindedAt() == null ? null : value.remindedAt().toString(),
                            value.processedAt() == null ? null : value.processedAt().toString(),
                            value.processedAt() != null
                                    && value.policy().timeoutAction()
                                    == com.unique.examine.flow.domain.ApprovalDeadlinePolicy.TimeoutAction.NONE
                    );
        }
    }

    public record RecordBinding(String moduleCode, String recordId) {
        static RecordBinding from(ApprovalInstance.RecordBinding value) {
            return value == null
                    ? null
                    : new RecordBinding(value.moduleCode(), Long.toString(value.recordId()));
        }
    }

    /**
     * Stable external status projection. Deliberately excludes actors, decisions,
     * evidence, history, completion executions and compensation details.
     */
    public record OpenApiInstanceStatus(
            String instanceId,
            String definitionId,
            int definitionVersion,
            String businessKey,
            String status,
            String startedAt,
            String completedAt,
            int currentStepIndex,
            int currentStageIndex,
            String currentStageCode,
            String approvalMode,
            String completionPhase,
            RecordBinding recordBinding
    ) {
        public static OpenApiInstanceStatus from(ApprovalInstance value) {
            return new OpenApiInstanceStatus(
                    Long.toString(value.id()),
                    Long.toString(value.definitionId()),
                    value.definitionVersion(),
                    value.businessKey(),
                    value.status().name(),
                    value.startedAt().toString(),
                    value.completedAt() == null ? null : value.completedAt().toString(),
                    value.currentStepIndex(),
                    value.currentStageIndex(),
                    value.currentStage().code(),
                    value.approvalMode().name(),
                    value.completionPhase() == null
                            ? "HUMAN_APPROVAL"
                            : value.completionPhase().name(),
                    RecordBinding.from(value.recordBinding())
            );
        }
    }

    public record InstancePage(
            List<Instance> items,
            int page,
            int size,
            long total
    ) {
        public InstancePage {
            items = List.copyOf(items);
        }

        public static InstancePage from(ApprovalWorkflowService.Page<ApprovalInstance> value) {
            return new InstancePage(
                    value.items().stream().map(Instance::from).toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }

    public record TaskPage(
            List<Instance> items,
            int page,
            int size,
            long total
    ) {
        public TaskPage {
            items = List.copyOf(items);
        }

        public static TaskPage from(
                ApprovalWorkflowService.Page<ApprovalTaskAssignment> value
        ) {
            return new TaskPage(
                    value.items().stream().map(Instance::from).toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }

    public record HistoryEvent(
            int sequence,
            String type,
            String actorId,
            String fromStatus,
            String toStatus,
            String comment,
            String occurredAt,
            String targetMemberId,
            String position,
            Integer targetStepIndex,
            String representedMemberId,
            String delegationRuleId,
            DecisionEvidence evidence,
            CompletionHistory completionExecution,
            CompensationHistory compensationExecution
    ) {
        public HistoryEvent(
                int sequence,
                String type,
                String actorId,
                String fromStatus,
                String toStatus,
                String comment,
                String occurredAt,
                String targetMemberId,
                String position,
                Integer targetStepIndex,
                String representedMemberId,
                String delegationRuleId,
                DecisionEvidence evidence,
                CompletionHistory completionExecution
        ) {
            this(
                    sequence, type, actorId, fromStatus, toStatus, comment,
                    occurredAt, targetMemberId, position, targetStepIndex,
                    representedMemberId, delegationRuleId, evidence,
                    completionExecution, null);
        }

        public HistoryEvent(
                int sequence,
                String type,
                String actorId,
                String fromStatus,
                String toStatus,
                String comment,
                String occurredAt,
                String targetMemberId,
                String position,
                Integer targetStepIndex,
                String representedMemberId,
                String delegationRuleId,
                DecisionEvidence evidence
        ) {
            this(
                    sequence, type, actorId, fromStatus, toStatus, comment,
                    occurredAt, targetMemberId, position, targetStepIndex,
                    representedMemberId, delegationRuleId, evidence, null
            );
        }

        public HistoryEvent(
                int sequence,
                String type,
                String actorId,
                String fromStatus,
                String toStatus,
                String comment,
                String occurredAt,
                String targetMemberId,
                String position,
                Integer targetStepIndex,
                String representedMemberId,
                String delegationRuleId
        ) {
            this(
                    sequence, type, actorId, fromStatus, toStatus, comment,
                    occurredAt, targetMemberId, position, targetStepIndex,
                    representedMemberId, delegationRuleId, null
            );
        }

        public HistoryEvent(
                int sequence,
                String type,
                String actorId,
                String fromStatus,
                String toStatus,
                String comment,
                String occurredAt,
                String targetMemberId,
                String position,
                Integer targetStepIndex
        ) {
            this(
                    sequence, type, actorId, fromStatus, toStatus, comment,
                    occurredAt, targetMemberId, position, targetStepIndex,
                    null, null, null
            );
        }

        static HistoryEvent from(int sequence, ApprovalHistoryEvent value) {
            return new HistoryEvent(
                    sequence,
                    value.type().name(),
                    Long.toString(value.actorId()),
                    value.fromStatus() == null ? null : value.fromStatus().name(),
                    value.toStatus().name(),
                    value.comment(),
                    value.occurredAt().toString(),
                    value.targetMemberId() == null ? null : Long.toString(value.targetMemberId()),
                    value.assignmentPosition() == null ? null : value.assignmentPosition().name(),
                    value.targetStepIndex(),
                    value.representedMemberId() == null
                            ? null
                            : Long.toString(value.representedMemberId()),
                    value.delegationRuleId() == null
                            ? null
                            : Long.toString(value.delegationRuleId()),
                    DecisionEvidence.from(value.evidence())
            );
        }
    }

    public record CompletionHistory(
            String executionId,
            int ordinal,
            String code,
            String type,
            String event,
            String status,
            int attemptNumber,
            String occurredAt,
            String failureCode,
            SubflowRun subflowRun,
            String parallelGroup
    ) {
        public CompletionHistory(
                String executionId,
                int ordinal,
                String code,
                String type,
                String event,
                String status,
                int attemptNumber,
                String occurredAt,
                String failureCode,
                SubflowRun subflowRun
        ) {
            this(
                    executionId, ordinal, code, type, event, status,
                    attemptNumber, occurredAt, failureCode, subflowRun, null);
        }

        public CompletionHistory(
                String executionId,
                int ordinal,
                String code,
                String type,
                String event,
                String status,
                int attemptNumber,
                String occurredAt,
                String failureCode
        ) {
            this(
                    executionId, ordinal, code, type, event, status,
                    attemptNumber, occurredAt, failureCode, null);
        }

        public static CompletionHistory from(
                ApprovalCompletionExecution execution,
                ApprovalCompletionAttempt attempt
        ) {
            return from(execution, attempt, null);
        }

        public static CompletionHistory from(
                ApprovalCompletionExecution execution,
                ApprovalCompletionAttempt attempt,
                ApprovalSubflowRun subflowRun
        ) {
            return new CompletionHistory(
                    Long.toString(execution.id()),
                    execution.ordinal(),
                    execution.step().code(),
                    execution.step().type().name(),
                    attempt.event().name(),
                    attemptStatus(attempt.event()),
                    attempt.attemptNumber(),
                    time(attempt.occurredAt()),
                    attempt.failureCode(),
                    SubflowRun.from(subflowRun),
                    execution.step().parallelGroup()
            );
        }

        private static String attemptStatus(
                ApprovalCompletionAttempt.Event event
        ) {
            return switch (event) {
                case ACTIVATED -> "AVAILABLE";
                case STARTED -> "RUNNING";
                case CLAIMED -> "LEASED";
                case LEASE_EXPIRED -> "AVAILABLE";
                case RETRIED -> "RETRYING";
                case SUCCEEDED -> "SUCCEEDED";
                case FAILED -> "FAILED";
                case CANCELLED -> "CANCELLED";
                case STAGE_JOINED -> "SUCCEEDED";
            };
        }
    }

    public record CompensationHistory(
            String compensationExecutionId,
            String originalExecutionId,
            int originalOrdinal,
            String type,
            String event,
            String status,
            int attemptNumber,
            String occurredAt,
            String failureCode,
            SubflowRun subflowRun
    ) {
        public static CompensationHistory from(
                com.unique.examine.flow.domain.ApprovalCompletionCompensation
                        compensation,
                com.unique.examine.flow.domain.ApprovalCompensationAttempt
                        attempt,
                com.unique.examine.flow.domain
                        .ApprovalCompensationSubflowRun subflowRun
        ) {
            return new CompensationHistory(
                    Long.toString(compensation.id()),
                    Long.toString(compensation.originalExecutionId()),
                    compensation.originalOrdinal(),
                    compensation.step().type().name(),
                    attempt.fact().event().name(),
                    CompletionHistory.attemptStatus(
                            attempt.fact().event()),
                    attempt.fact().attemptNumber(),
                    time(attempt.fact().occurredAt()),
                    attempt.fact().failureCode(),
                    subflowRun == null
                            ? null
                            : SubflowRun.from(subflowRun.run()));
        }
    }

    public record DecisionEvidenceFile(
            String fileId,
            String originalName,
            String contentType,
            long size,
            String sha256,
            String mimeFamily
    ) {
        static DecisionEvidenceFile from(
                com.unique.examine.flow.domain.ApprovalDecisionEvidenceFile value
        ) {
            return value == null
                    ? null
                    : new DecisionEvidenceFile(
                            Long.toString(value.fileId()),
                            value.originalName(),
                            value.contentType(),
                            value.sizeBytes(),
                            value.sha256(),
                            value.mimeFamily().name()
                    );
        }
    }

    public record DecisionEvidenceSignature(
            String kind,
            DecisionEvidenceFile file,
            String typedValue
    ) {
        static DecisionEvidenceSignature from(
                com.unique.examine.flow.domain.ApprovalDecisionEvidence.Signature value
        ) {
            return value == null
                    ? null
                    : new DecisionEvidenceSignature(
                            value.kind().name(),
                            DecisionEvidenceFile.from(value.file()),
                            value.typedValue()
                    );
        }
    }

    public record DecisionEvidenceTemplate(
            String templateId,
            int version,
            String name
    ) {
        static DecisionEvidenceTemplate from(
                com.unique.examine.flow.domain.ApprovalDecisionEvidence.TemplateSelection value
        ) {
            return value == null
                    ? null
                    : new DecisionEvidenceTemplate(
                            Long.toString(value.templateId()),
                            value.version(),
                            value.name()
                    );
        }
    }

    public record DecisionEvidence(
            String evidenceId,
            String instanceId,
            int historySequence,
            String branchCode,
            int stageIndex,
            String decision,
            List<DecisionEvidenceFile> attachments,
            DecisionEvidenceSignature signature,
            DecisionEvidenceTemplate template,
            String actorId,
            String representedMemberId,
            String delegationRuleId,
            String decidedAt
    ) {
        public DecisionEvidence {
            attachments = List.copyOf(attachments);
        }

        static DecisionEvidence from(
                com.unique.examine.flow.domain.ApprovalDecisionEvidence value
        ) {
            return value == null
                    ? null
                    : new DecisionEvidence(
                            Long.toString(value.id()),
                            Long.toString(value.instanceId()),
                            value.historySequence(),
                            value.branchCode(),
                            value.stageIndex(),
                            value.decision().name(),
                            value.attachments().stream()
                                    .map(DecisionEvidenceFile::from)
                                    .toList(),
                            DecisionEvidenceSignature.from(value.signature()),
                            DecisionEvidenceTemplate.from(value.template()),
                            Long.toString(value.actorId()),
                            Long.toString(value.representedMemberId()),
                            value.delegationRuleId() == null
                                    ? null
                                    : Long.toString(value.delegationRuleId()),
                            value.decidedAt().toString()
                    );
        }
    }

    public record History(String instanceId, List<HistoryEvent> events) {
        public History {
            events = List.copyOf(events);
        }

        public static History from(long instanceId, List<ApprovalHistoryEvent> values) {
            return from(instanceId, values, List.of());
        }

        public static History from(
                long instanceId,
                List<ApprovalHistoryEvent> values,
                List<CompletionHistory> completionValues
        ) {
            return from(
                    instanceId, values, completionValues, List.of());
        }

        public static History from(
                long instanceId,
                List<ApprovalHistoryEvent> values,
                List<CompletionHistory> completionValues,
                List<CompensationHistory> compensationValues
        ) {
            record Ordered(
                    java.time.Instant occurredAt,
                    int stableOrder,
                    HistoryEvent human,
                    CompletionHistory completion,
                    CompensationHistory compensation
            ) {
            }
            var ordered = new java.util.ArrayList<Ordered>();
            for (var index = 0; index < values.size(); index++) {
                var value = values.get(index);
                ordered.add(new Ordered(
                        value.occurredAt(), index,
                        HistoryEvent.from(0, value), null, null));
            }
            for (var index = 0;
                    index < completionValues.size(); index++) {
                var value = completionValues.get(index);
                ordered.add(new Ordered(
                        java.time.Instant.parse(value.occurredAt()),
                        values.size() + index,
                        null,
                        value,
                        null
                ));
            }
            for (var index = 0;
                    index < compensationValues.size(); index++) {
                var value = compensationValues.get(index);
                ordered.add(new Ordered(
                        java.time.Instant.parse(value.occurredAt()),
                        values.size() + completionValues.size() + index,
                        null,
                        null,
                        value));
            }
            ordered.sort(java.util.Comparator
                    .comparing(Ordered::occurredAt)
                    .thenComparingInt(Ordered::stableOrder));
            var events = java.util.stream.IntStream.range(
                            0, ordered.size())
                    .mapToObj(index -> {
                        var value = ordered.get(index);
                        if (value.human() != null) {
                            var event = value.human();
                            return new HistoryEvent(
                                    index + 1,
                                    event.type(),
                                    event.actorId(),
                                    event.fromStatus(),
                                    event.toStatus(),
                                    event.comment(),
                                    event.occurredAt(),
                                    event.targetMemberId(),
                                    event.position(),
                                    event.targetStepIndex(),
                                    event.representedMemberId(),
                                    event.delegationRuleId(),
                                    event.evidence(),
                                    null,
                                    null
                            );
                        }
                        if (value.compensation() != null) {
                            var compensation = value.compensation();
                            return new HistoryEvent(
                                    index + 1,
                                    "COMPLETION_COMPENSATION",
                                    null,
                                    null,
                                    compensation.status(),
                                    null,
                                    compensation.occurredAt(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    compensation);
                        }
                        var completion = value.completion();
                        return new HistoryEvent(
                                index + 1,
                                "COMPLETION_EXECUTION",
                                null,
                                null,
                                completion.status(),
                                null,
                                completion.occurredAt(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                completion,
                                null
                        );
                    })
                    .toList();
            return new History(Long.toString(instanceId), events);
        }
    }

    public record DelegationRule(
            String delegationRuleId,
            String delegatorMemberId,
            String delegateMemberId,
            String startsAt,
            String endsAt,
            String definitionId,
            String status,
            String createdByMemberId,
            String createdAt,
            String revokedByMemberId,
            String revokedAt
    ) {
        public static DelegationRule from(ApprovalDelegationRule value) {
            return new DelegationRule(
                    Long.toString(value.id()),
                    Long.toString(value.delegatorMemberId()),
                    Long.toString(value.delegateMemberId()),
                    value.startsAt().toString(),
                    value.endsAt().toString(),
                    value.definitionId() == null
                            ? null
                            : Long.toString(value.definitionId()),
                    value.status().name(),
                    Long.toString(value.createdByMemberId()),
                    value.createdAt().toString(),
                    value.revokedByMemberId() == null
                            ? null
                            : Long.toString(value.revokedByMemberId()),
                    value.revokedAt() == null ? null : value.revokedAt().toString()
            );
        }
    }

    public record DelegationRulePage(
            List<DelegationRule> items,
            int page,
            int size,
            long total
    ) {
        public DelegationRulePage {
            items = List.copyOf(items);
        }

        public static DelegationRulePage from(
                ApprovalWorkflowService.Page<ApprovalDelegationRule> value
        ) {
            return new DelegationRulePage(
                    value.items().stream().map(DelegationRule::from).toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }

    public record Urge(
            String urgeId,
            String instanceId,
            String actorId,
            String recipientId,
            String message,
            String createdAt
    ) {
        public static Urge from(FlowUrge value) {
            return new Urge(
                    Long.toString(value.id()),
                    Long.toString(value.instanceId()),
                    Long.toString(value.actorId()),
                    Long.toString(value.recipientId()),
                    value.message(),
                    value.createdAt().toString()
            );
        }
    }

    public record UrgePage(List<Urge> items, int page, int size, long total) {
        public UrgePage {
            items = List.copyOf(items);
        }

        public static UrgePage from(FlowInteractionService.Page<FlowUrge> value) {
            return new UrgePage(
                    value.items().stream().map(Urge::from).toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }

    public record Comment(
            String commentId,
            String instanceId,
            String authorId,
            String body,
            String createdAt
    ) {
        public static Comment from(FlowComment value) {
            return new Comment(
                    Long.toString(value.id()),
                    Long.toString(value.instanceId()),
                    Long.toString(value.authorId()),
                    value.body(),
                    value.createdAt().toString()
            );
        }
    }

    public record CommentPage(List<Comment> items, int page, int size, long total) {
        public CommentPage {
            items = List.copyOf(items);
        }

        public static CommentPage from(FlowInteractionService.Page<FlowComment> value) {
            return new CommentPage(
                    value.items().stream().map(Comment::from).toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }

    public record Copy(
            String copyId,
            String instanceId,
            String actorId,
            String recipientId,
            String message,
            String createdAt
    ) {
        public static Copy from(FlowCopy value) {
            return new Copy(
                    Long.toString(value.id()),
                    Long.toString(value.instanceId()),
                    Long.toString(value.actorId()),
                    Long.toString(value.recipientId()),
                    value.message(),
                    value.createdAt().toString()
            );
        }
    }

    public record CopyPage(List<Copy> items, int page, int size, long total) {
        public CopyPage {
            items = List.copyOf(items);
        }

        public static CopyPage from(FlowInteractionService.Page<FlowCopy> value) {
            return new CopyPage(
                    value.items().stream().map(Copy::from).toList(),
                    value.page(),
                    value.size(),
                    value.total()
            );
        }
    }
}
