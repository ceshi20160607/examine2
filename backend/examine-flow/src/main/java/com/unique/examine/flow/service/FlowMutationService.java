package com.unique.examine.flow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ApprovalEvidenceFileFacade;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.api.RuntimeApproverDirectoryFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.core.runtime.RuntimeRecordMemberFieldFacade;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.api.FlowHttpErrors;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import com.unique.examine.flow.domain.ApprovalApproverSources;
import com.unique.examine.flow.domain.ApprovalBranchRoute;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicies;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicies;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicies;
import com.unique.examine.flow.domain.ApprovalDecisionEvidence;
import com.unique.examine.flow.domain.ApprovalDecisionEvidenceFile;
import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.ApprovalInclusiveGateway;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalParallelGateway;
import com.unique.examine.flow.domain.ApprovalQuorumRules;
import com.unique.examine.flow.domain.ApprovalStage;
import com.unique.examine.flow.domain.ApprovalStageExecution;
import com.unique.examine.flow.domain.ApprovalStartContext;
import com.unique.examine.flow.domain.ResolvedApprovalBranchRoute;
import com.unique.examine.flow.service.ApprovalRouteResolver.ResolvedRoute;
import com.unique.examine.flow.domain.FlowDraftPreflight;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.extension.FlowExtensionService;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FlowMutationService {
    private static final String IDEMPOTENCY_SCOPE = "FLOW_INSTANCE";
    private static final OperationAuditFacade NO_OPERATION_AUDIT = new OperationAuditFacade() {
        @Override
        public void recordSuccess(OperationAudit audit) {
        }

        @Override
        public void recordDenied(OperationAudit audit) {
        }

        @Override
        public void recordFailed(OperationAudit audit) {
        }
    };

    private final FlowRequestServiceFactory services;
    private final IdempotencyFacade idempotency;
    private final ObjectMapper objectMapper;
    private final RuntimeActiveMemberFacade activeMembers;
    private final RuntimeRecordFlowFacade recordFlows;
    private final RuntimeRecordAccessFacade recordAccess;
    private final OperationAuditFacade operationAudit;
    private final ApprovalApproverSourceResolver approverSources;
    private final ApprovalEvidenceFileFacade evidenceFiles;
    private FlowCompletionDefinitionMapper completionDefinitions;
    private FlowExtensionService extensions;

    @Autowired
    public FlowMutationService(
            FlowRequestServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeApproverDirectoryFacade approverDirectory,
            RuntimeRecordFlowFacade recordFlows,
            OperationAuditFacade operationAudit,
            RuntimeRecordMemberFieldFacade recordMemberFields,
            RuntimeRecordAccessFacade recordAccess,
            ApprovalEvidenceFileFacade evidenceFiles
    ) {
        this.services = Objects.requireNonNull(services, "services");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.activeMembers = Objects.requireNonNull(activeMembers, "activeMembers");
        this.approverSources = new ApprovalApproverSourceResolver(
                approverDirectory, recordMemberFields, activeMembers);
        this.recordFlows = Objects.requireNonNull(recordFlows, "recordFlows");
        this.recordAccess = Objects.requireNonNull(recordAccess, "recordAccess");
        this.operationAudit = Objects.requireNonNull(operationAudit, "operationAudit");
        this.evidenceFiles = Objects.requireNonNull(
                evidenceFiles, "evidenceFiles");
    }

    public FlowMutationService(
            FlowRequestServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeApproverDirectoryFacade approverDirectory,
            RuntimeRecordFlowFacade recordFlows,
            OperationAuditFacade operationAudit,
            RuntimeRecordMemberFieldFacade recordMemberFields,
            RuntimeRecordAccessFacade recordAccess
    ) {
        this(
                services, idempotency, objectMapper, activeMembers,
                approverDirectory, recordFlows, operationAudit,
                recordMemberFields, recordAccess, unsupportedEvidenceFiles()
        );
    }

    @Autowired
    void configureCompletionDefinitions(
            FlowCompletionDefinitionMapper completionDefinitions
    ) {
        this.completionDefinitions = Objects.requireNonNull(
                completionDefinitions, "completionDefinitions");
    }

    @Autowired(required = false)
    void configureExtensions(FlowExtensionService extensions) {
        this.extensions = Objects.requireNonNull(extensions, "extensions");
    }

    public FlowMutationService(
            FlowRequestServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeApproverDirectoryFacade approverDirectory,
            RuntimeRecordFlowFacade recordFlows,
            OperationAuditFacade operationAudit
    ) {
        this(
                services, idempotency, objectMapper, activeMembers,
                approverDirectory, recordFlows, operationAudit,
                unsupportedRecordMemberFields(), unsupportedRecordAccess()
        );
    }

    public FlowMutationService(
            FlowRequestServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeApproverDirectoryFacade approverDirectory,
            RuntimeRecordFlowFacade recordFlows,
            OperationAuditFacade operationAudit,
            RuntimeRecordMemberFieldFacade recordMemberFields
    ) {
        this(
                services, idempotency, objectMapper, activeMembers,
                approverDirectory, recordFlows, operationAudit,
                recordMemberFields, unsupportedRecordAccess()
        );
    }

    public FlowMutationService(
            FlowRequestServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeRecordFlowFacade recordFlows
    ) {
        this(
                services, idempotency, objectMapper, activeMembers,
                unsupportedApproverDirectory(), recordFlows, NO_OPERATION_AUDIT
        );
    }

    public FlowMutationService(
            FlowRequestServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeRecordFlowFacade recordFlows,
            OperationAuditFacade operationAudit
    ) {
        this(
                services, idempotency, objectMapper, activeMembers,
                unsupportedApproverDirectory(), recordFlows, operationAudit
        );
    }

    public FlowMutationService(
            FlowRequestServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            RuntimeActiveMemberFacade activeMembers
    ) {
        this(services, idempotency, objectMapper, activeMembers, unsupportedRecordFlows());
    }

    public FlowMutationService(
            FlowRequestServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeApproverDirectoryFacade approverDirectory
    ) {
        this(
                services, idempotency, objectMapper, activeMembers,
                approverDirectory, unsupportedRecordFlows()
        );
    }

    public FlowMutationService(
            FlowRequestServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeApproverDirectoryFacade approverDirectory,
            RuntimeRecordFlowFacade recordFlows
    ) {
        this(
                services, idempotency, objectMapper, activeMembers,
                approverDirectory, recordFlows, NO_OPERATION_AUDIT
        );
    }

    @Transactional
    public FlowViews.DefinitionDraft createDefinition(
            FlowSession session,
            FlowRequests.CreateDefinition body
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(body, "body");
        var configuration = FlowHttpErrors.definitionConfiguration(
                body.triggerBinding(),
                body.recordStatusMapping(),
                session.memberId()
        );
        var dynamicMembers = (java.util.function.Function<
                com.unique.examine.flow.domain.ApprovalApproverSource,
                java.util.List<Long>>) source -> draftRouteMembers(session, source);
        var route = definitionRoute(
                session,
                body.approverId(),
                body.approverIds(),
                body.gateway(),
                body.approvalMode(),
                body.parallelGateway(),
                body.inclusiveGateway(),
                body.approverSource(),
                body.quorumRule(),
                body.deadlinePolicy(),
                body.decisionCommentPolicy(),
                body.approvalStages(),
                body.decisionEvidencePolicy(),
                dynamicMembers
        );
        var completionSteps = completionDefinitions(
                session, body.completionSteps(), null);
        var result = services.forTenant(session.systemId(), session.tenantId()).createDraft(
                body.name(),
                route.approverIds(),
                configuration.triggerBinding(),
                configuration.recordStatusMapping(),
                route.gateway(),
                route.approvalMode(),
                route.parallelGateway(),
                route.inclusiveGateway(),
                route.approverSources(),
                route.quorumRules(),
                route.deadlinePolicies(),
                route.decisionCommentPolicies(),
                route.approvalStages(),
                route.decisionEvidencePolicies(),
                completionSteps,
                completionFailurePolicy(body.completionFailurePolicy())
        );
        return FlowViews.DefinitionDraft.from(result);
    }

    @Transactional
    public FlowViews.DefinitionDraft reviseDefinition(
            FlowSession session,
            long definitionId,
            FlowRequests.ReviseDefinition body
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(body, "body");
        var configuration = FlowHttpErrors.definitionConfiguration(
                body.triggerBinding(),
                body.recordStatusMapping(),
                session.memberId()
        );
        var dynamicMembers = (java.util.function.Function<
                com.unique.examine.flow.domain.ApprovalApproverSource,
                java.util.List<Long>>) source -> draftRouteMembers(session, source);
        var route = definitionRoute(
                session,
                body.approverId(),
                body.approverIds(),
                body.gateway(),
                body.approvalMode(),
                body.parallelGateway(),
                body.inclusiveGateway(),
                body.approverSource(),
                body.quorumRule(),
                body.deadlinePolicy(),
                body.decisionCommentPolicy(),
                body.approvalStages(),
                body.decisionEvidencePolicy(),
                dynamicMembers
        );
        var workflow = services.forTenant(
                session.systemId(), session.tenantId());
        var current = workflow.definitionDraft(definitionId);
        var completionSteps = completionDefinitions(
                session, body.completionSteps(), current.completionSteps());
        var result = workflow.reviseDraft(
                definitionId,
                body.name(),
                route.approverIds(),
                configuration.triggerBinding(),
                configuration.recordStatusMapping(),
                route.gateway(),
                route.approvalMode(),
                route.parallelGateway(),
                route.inclusiveGateway(),
                route.approverSources(),
                route.quorumRules(),
                route.deadlinePolicies(),
                route.decisionCommentPolicies(),
                route.approvalStages(),
                route.decisionEvidencePolicies(),
                completionSteps,
                completionFailurePolicy(body.completionFailurePolicy())
        );
        return FlowViews.DefinitionDraft.from(result);
    }

    @Transactional
    public FlowViews.DraftCheck checkDraft(FlowSession session, long definitionId) {
        Objects.requireNonNull(session, "session");
        var draft = services.forTenant(session.systemId(), session.tenantId())
                .definitionDraft(definitionId);
        return FlowViews.DraftCheck.from(check(session, draft));
    }

    @Transactional(readOnly = true)
    public FlowViews.RecordMemberFieldSourceCatalog recordMemberFieldCatalog(
            FlowSession session,
            String moduleCode
    ) {
        Objects.requireNonNull(session, "session");
        if (moduleCode == null
                || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_INVALID",
                    "moduleCode must be canonical",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        var items = approverSources.catalog(session.systemId(), moduleCode)
                .stream()
                .flatMap(catalog -> catalog.fields().stream())
                .map(field -> new FlowViews.RecordMemberFieldSource(
                        Long.toString(field.fieldId()),
                        moduleCode,
                        field.fieldCode(),
                        field.fieldName()
                ))
                .toList();
        return new FlowViews.RecordMemberFieldSourceCatalog(items);
    }

    @Transactional
    public FlowViews.DecisionCommentTemplate createDecisionCommentTemplate(
            FlowSession session,
            FlowRequests.CreateDecisionCommentTemplate request
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(request, "request");
        return FlowViews.DecisionCommentTemplate.from(
                services.forTenant(session.systemId(), session.tenantId())
                        .createDecisionCommentTemplate(
                                request.name(),
                                request.body(),
                                session.memberId()
                        )
        );
    }

    @Transactional
    public FlowViews.DecisionCommentTemplate reviseDecisionCommentTemplate(
            FlowSession session,
            long templateId,
            FlowRequests.ReviseDecisionCommentTemplate request
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(request, "request");
        return FlowViews.DecisionCommentTemplate.from(
                services.forTenant(session.systemId(), session.tenantId())
                        .reviseDecisionCommentTemplate(
                                templateId,
                                request.name(),
                                request.body(),
                                session.memberId()
                        )
        );
    }

    @Transactional
    public FlowViews.DecisionCommentTemplate activateDecisionCommentTemplate(
            FlowSession session,
            long templateId
    ) {
        Objects.requireNonNull(session, "session");
        return FlowViews.DecisionCommentTemplate.from(
                services.forTenant(session.systemId(), session.tenantId())
                        .activateDecisionCommentTemplate(
                                templateId, session.memberId())
        );
    }

    @Transactional
    public FlowViews.DecisionCommentTemplate deactivateDecisionCommentTemplate(
            FlowSession session,
            long templateId
    ) {
        Objects.requireNonNull(session, "session");
        return FlowViews.DecisionCommentTemplate.from(
                services.forTenant(session.systemId(), session.tenantId())
                        .deactivateDecisionCommentTemplate(
                                templateId, session.memberId())
        );
    }

    @Transactional(readOnly = true)
    public FlowViews.DecisionCommentTemplatePage decisionCommentTemplates(
            FlowSession session,
            boolean activeOnly,
            int page,
            int size
    ) {
        Objects.requireNonNull(session, "session");
        return FlowViews.DecisionCommentTemplatePage.from(
                services.forTenant(session.systemId(), session.tenantId())
                        .decisionCommentTemplates(activeOnly, page, size)
        );
    }

    @Transactional
    public FlowViews.DraftSimulation simulateDraft(
            FlowSession session,
            long definitionId,
            FlowRequests.SimulateDefinition request
    ) {
        Objects.requireNonNull(session, "session");
        var workflow = services.forTenant(session.systemId(), session.tenantId());
        var draft = workflow.definitionDraft(definitionId);
        var check = check(session, draft);
        var requesterId = simulationRequester(
                session,
                request == null ? null : request.requesterId()
        );
        var businessKey = simulationBusinessKey(
                draft.id(),
                draft.revision(),
                request == null ? null : request.businessKey()
        );
        var trigger = simulationTrigger(
                draft.triggerBinding(),
                request == null ? null : request.trigger()
        );
        var startable = check.verdict() == FlowDraftPreflight.Verdict.READY
                && trigger.matched();
        var reason = check.verdict() == FlowDraftPreflight.Verdict.BLOCKED
                ? "DRAFT_BLOCKED"
                : trigger.reason();
        var contextualSourceUnavailable = false;
        final java.util.List<FlowDraftPreflight.Step> steps;
        final FlowDraftPreflight.RouteResult routeResult;
        final java.util.List<FlowDraftPreflight.RouteResult> parallelRoutes;
        if (draft.parallelGateway() == null && draft.inclusiveGateway() == null) {
            var route = new ApprovalRouteResolver(
                    new TriggerConditionMatcher(objectMapper)
            ).resolve(
                    draft.gateway(),
                    draft.approverIds(),
                    draft.approvalMode(),
                    simulationRouteValues(request)
            );
            var routeMembers = simulationRouteMembers(
                    session,
                    draft.approverSources(),
                    route.branchCode(),
                    route.approverIds(),
                    requesterId,
                    simulationRouteValues(request)
            );
            if (routeMembers.isEmpty()) {
                contextualSourceUnavailable = true;
            }
            steps = java.util.stream.IntStream.range(0, routeMembers.size())
                    .mapToObj(index -> new FlowDraftPreflight.Step(
                            index,
                            routeMembers.get(index),
                            route.approvalMode()
                                    != com.unique.examine.flow.domain.ApprovalMode.SEQUENTIAL
                                    || index == 0
                    ))
                    .toList();
            routeResult = routeResult(
                    draft.gateway() != null,
                    route,
                    routeMembers,
                    route.branchCode() == null
                            ? draft.quorumRules().primary()
                            : draft.quorumRules().branch(route.branchCode()),
                    route.branchCode() == null
                            ? draft.deadlinePolicies().primary()
                            : draft.deadlinePolicies().branch(route.branchCode()),
                    route.branchCode() == null
                            ? draft.decisionCommentPolicies().primary()
                            : draft.decisionCommentPolicies().branch(route.branchCode())
            );
            parallelRoutes = java.util.List.of();
        } else {
            var nextSteps = new java.util.ArrayList<FlowDraftPreflight.Step>();
            var nextRoutes = new java.util.ArrayList<FlowDraftPreflight.RouteResult>();
            var selectedBranches = draft.parallelGateway() != null
                    ? draft.parallelGateway().branches()
                    : new ApprovalInclusiveRouteResolver(
                            new TriggerConditionMatcher(objectMapper)
                    ).resolve(
                            draft.inclusiveGateway(),
                            simulationRouteValues(request)
                    );
            if (selectedBranches.isEmpty()) {
                startable = false;
                reason = "INCLUSIVE_NO_MATCH";
            }
            for (var branch : selectedBranches) {
                var routeMembers = simulationRouteMembers(
                        session,
                        draft.approverSources(),
                        branch.code(),
                        branch.approverIds(),
                        requesterId,
                        simulationRouteValues(request)
                );
                if (routeMembers.isEmpty()) {
                    contextualSourceUnavailable = true;
                }
                var defaultBranch = draft.inclusiveGateway() != null
                        && draft.inclusiveGateway().defaultBranch()
                                .map(value -> value.code().equals(branch.code()))
                                .orElse(false);
                nextRoutes.add(new FlowDraftPreflight.RouteResult(
                        true,
                        branch.code(),
                        branch.name(),
                        defaultBranch,
                        branch.approvalMode(),
                        simulationRequiredApprovals(
                                branch.approvalMode(),
                                draft.quorumRules().branch(branch.code()),
                                routeMembers.size()
                        ),
                        branch.approvalMode()
                                == com.unique.examine.flow.domain.ApprovalMode.SEQUENTIAL
                                ? routeMembers.stream().findFirst().stream().toList()
                                : routeMembers,
                        draft.deadlinePolicies().branch(branch.code()),
                        draft.decisionCommentPolicies().branch(branch.code())
                ));
                for (var index = 0; index < routeMembers.size(); index++) {
                    nextSteps.add(new FlowDraftPreflight.Step(
                            nextSteps.size(),
                            routeMembers.get(index),
                            branch.approvalMode()
                                    != com.unique.examine.flow.domain.ApprovalMode.SEQUENTIAL
                                    || index == 0
                    ));
                }
            }
            steps = java.util.List.copyOf(nextSteps);
            parallelRoutes = java.util.List.copyOf(nextRoutes);
            routeResult = new FlowDraftPreflight.RouteResult(
                    false,
                    null,
                    null,
                    true,
                    com.unique.examine.flow.domain.ApprovalMode.SEQUENTIAL,
                    0,
                    java.util.List.of()
            );
        }
        if (contextualSourceUnavailable
                && check.verdict() == FlowDraftPreflight.Verdict.READY) {
            var contextualIssues = new java.util.ArrayList<>(check.issues());
            var recordSourceUnavailable = containsRecordMemberField(
                    draft.approverSources());
            contextualIssues.add(new FlowDraftPreflight.Issue(
                    FlowDraftPreflight.Severity.BLOCKER,
                    recordSourceUnavailable
                            ? "RECORD_MEMBER_FIELD_UNAVAILABLE"
                            : "REQUESTER_MANAGER_UNAVAILABLE",
                    "/approverSource",
                    recordSourceUnavailable
                            ? "The explicit simulation values do not resolve an active "
                                    + "record member field approver"
                            : "The simulation requester has no active direct manager "
                                    + "in the current system and tenant"
            ));
            check = new FlowDraftPreflight.Check(
                    check.definitionId(),
                    check.revision(),
                    FlowDraftPreflight.Verdict.BLOCKED,
                    contextualIssues
            );
            startable = false;
            reason = "DRAFT_BLOCKED";
        }
        var branchApprovalStages = new java.util.LinkedHashMap<
                String, java.util.List<ApprovalStage>>();
        if (draft.gateway() != null) {
            draft.gateway().branches().stream()
                    .filter(branch -> branch.approvalStages() != null)
                    .forEach(branch -> branchApprovalStages.put(
                            branch.code(), branch.approvalStages()));
        } else if (draft.parallelGateway() != null) {
            draft.parallelGateway().branches().stream()
                    .filter(branch -> branch.approvalStages() != null)
                    .forEach(branch -> branchApprovalStages.put(
                            branch.code(), branch.approvalStages()));
        } else if (draft.inclusiveGateway() != null) {
            draft.inclusiveGateway().branches().stream()
                    .filter(branch -> branch.approvalStages() != null)
                    .forEach(branch -> branchApprovalStages.put(
                            branch.code(), branch.approvalStages()));
        }
        return FlowViews.DraftSimulation.from(new FlowDraftPreflight.Simulation(
                draft.id(),
                draft.revision(),
                check,
                requesterId,
                businessKey,
                startable,
                reason,
                steps,
                trigger,
                FlowDraftPreflight.StatusEffects.from(draft.recordStatusMapping()),
                routeResult,
                parallelRoutes
        ), draft.approvalStages(), branchApprovalStages,
                draft.completionSteps(), draft.completionFailurePolicy());
    }

    @Transactional
    public FlowViews.DefinitionVersion publish(FlowSession session, long definitionId) {
        Objects.requireNonNull(session, "session");
        var workflow = services.forTenant(session.systemId(), session.tenantId());
        var draft = workflow.definitionDraft(definitionId);
        var check = check(session, draft);
        if (check.verdict() == FlowDraftPreflight.Verdict.BLOCKED) {
            throw new BusinessException(
                    "FLOW_DRAFT_CHECK_BLOCKED",
                    "Flow draft has " + check.blockerCount()
                            + " publish blocker(s); run draft check for field details",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        var published = workflow.publish(definitionId);
        if (extensions != null) {
            extensions.published(session, draft, published);
        }
        return FlowViews.DefinitionVersion.from(published);
    }

    @Transactional
    public FlowViews.Instance start(
            FlowSession session,
            long definitionId,
            FlowRequests.StartInstance request
    ) {
        return start(
                session,
                definitionId,
                request,
                null,
                RuntimeRecordFlowFacade.BindingSource.MANUAL);
    }

    @Transactional
    public FlowViews.Instance start(
            FlowSession session,
            long definitionId,
            FlowRequests.StartInstance request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        Objects.requireNonNull(session, "session");
        requireKey(idempotencyKey);
        var scopeKey = session.systemId() + ":" + session.tenantId() + ":" + session.memberId()
                + ":" + definitionId + ":start";
        var requestHash = sha256(write(request));
        var existing = idempotency.find(IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, "Flow start");
        }
        final long id;
        try {
            id = idempotency.begin(
                    IDEMPOTENCY_SCOPE,
                    scopeKey,
                    idempotencyKey,
                    requestHash,
                    Duration.ofHours(24));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("REQUEST_IN_PROGRESS", "The Flow start request is already being processed");
        }
        var result = start(
                session,
                definitionId,
                request,
                null,
                RuntimeRecordFlowFacade.BindingSource.MANUAL);
        operationAudit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(session.accountId(), "WEB"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                new AggregateRef("FLOW_INSTANCE", result.instanceId()),
                "FLOW_INSTANCE_STARTED",
                null,
                Map.of(
                        "definitionId", result.definitionId(),
                        "definitionVersion", result.definitionVersion(),
                        "status", result.status(),
                        "recordBound", result.recordBinding() != null
                ),
                requestId,
                traceId
        ));
        idempotency.complete(id, 201, "OK", write(result));
        return result;
    }

    @Transactional
    public FlowViews.Instance startOpenApi(
            FlowSession session,
            long applicationId,
            long definitionId,
            FlowRequests.StartInstance request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        Objects.requireNonNull(session, "session");
        if (applicationId <= 0) {
            throw new IllegalArgumentException("OpenAPI application ID must be positive");
        }
        requireKey(idempotencyKey);
        var scopeKey = session.systemId() + ":" + session.tenantId() + ":" + session.memberId()
                + ":" + applicationId + ":" + definitionId + ":openapi-start";
        var requestHash = sha256(write(request));
        var existing = idempotency.find(IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, "OpenAPI Flow start");
        }
        final long id;
        try {
            id = idempotency.begin(
                    IDEMPOTENCY_SCOPE,
                    scopeKey,
                    idempotencyKey,
                    requestHash,
                    Duration.ofHours(24));
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "REQUEST_IN_PROGRESS",
                    "The OpenAPI Flow start request is already being processed");
        }
        var result = start(
                session,
                definitionId,
                request,
                null,
                RuntimeRecordFlowFacade.BindingSource.OPENAPI);
        operationAudit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(session.accountId(), "OPENAPI"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                new AggregateRef("FLOW_INSTANCE", result.instanceId()),
                "FLOW_INSTANCE_STARTED",
                null,
                Map.of(
                        "applicationId", Long.toString(applicationId),
                        "definitionId", result.definitionId(),
                        "definitionVersion", result.definitionVersion(),
                        "status", result.status(),
                        "recordBound", result.recordBinding() != null
                ),
                requestId,
                traceId
        ));
        idempotency.complete(id, 201, "OK", write(result));
        return result;
    }

    @Transactional
    public FlowViews.Instance startTriggered(
            FlowSession session,
            long definitionId,
            FlowRequests.StartInstance request
    ) {
        return start(
                session,
                definitionId,
                request,
                null,
                RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);
    }

    @Transactional
    public FlowViews.Instance startAdditional(
            FlowSession session,
            long definitionId,
            FlowRequests.StartInstance request,
            String eventKey
    ) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("Additional Flow binding event key is invalid");
        }
        return start(
                session,
                definitionId,
                request,
                eventKey,
                RuntimeRecordFlowFacade.BindingSource.MANUAL);
    }

    @Transactional
    public FlowViews.Instance startAdditionalTriggered(
            FlowSession session,
            long definitionId,
            FlowRequests.StartInstance request,
            String eventKey
    ) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("Additional Flow binding event key is invalid");
        }
        return start(
                session,
                definitionId,
                request,
                eventKey,
                RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);
    }

    private FlowViews.Instance start(
            FlowSession session,
            long definitionId,
            FlowRequests.StartInstance request,
            String additionalEventKey,
            RuntimeRecordFlowFacade.BindingSource bindingSource
    ) {
        Objects.requireNonNull(session, "session");
        if (request == null) {
            throw new BusinessException(
                    "FLOW_REQUEST_INVALID",
                    "A Flow start request is required",
                    HttpStatus.BAD_REQUEST
            );
        }
        var binding = recordBinding(request.recordBinding());
        var workflow = services.forTenant(session.systemId(), session.tenantId());
        var definition = request.definitionVersion() == null
                ? workflow.latestDefinitionVersion(definitionId)
                : workflow.definitionVersion(definitionId, request.definitionVersion());
        var valuesJson = startValues(request.values());
        if (binding != null
                && bindingSource
                        != RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT
                && hasLaterRecordSource(definition)) {
            recordAccess.requireView(
                    new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                            session.systemId(),
                            session.tenantId(),
                            session.memberId(),
                            session.permissions(),
                            binding.moduleCode(),
                            binding.recordId()
                    ));
        }
        var startContext = new ApprovalStartContext(
                session.memberId(),
                binding == null ? null : binding.moduleCode(),
                binding == null ? null : binding.recordId(),
                valuesJson
        );
        final ApprovalInstance instance;
        if (definition.parallelGateway() != null) {
            var resolvedBranches = definition.parallelGateway().branches().stream()
                    .map(branch -> resolveBranch(
                            session,
                            definition.approverSources(),
                            branch,
                            binding,
                            bindingSource,
                            valuesJson
                    ))
                    .toList();
            instance = workflow.startBranches(
                    definition.definitionId(),
                    definition.version(),
                    resolvedBranches,
                    request.businessKey(),
                    session.memberId(),
                    binding,
                    startContext
            );
        } else if (definition.inclusiveGateway() != null) {
            var selectedBranches = new ApprovalInclusiveRouteResolver(
                    new TriggerConditionMatcher(objectMapper)
            ).resolve(
                    definition.inclusiveGateway(),
                    valuesJson
            );
            if (selectedBranches.isEmpty()) {
                throw new BusinessException(
                        "FLOW_GATEWAY_NO_MATCH",
                        "Inclusive gateway did not match a branch and has no default",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }
            var resolvedBranches = selectedBranches.stream()
                    .map(branch -> resolveBranch(
                            session,
                            definition.approverSources(),
                            branch,
                            binding,
                            bindingSource,
                            valuesJson
                    ))
                    .toList();
            instance = workflow.startBranches(
                    definition.definitionId(),
                    definition.version(),
                    resolvedBranches,
                    request.businessKey(),
                    session.memberId(),
                    binding,
                    startContext
            );
        } else {
            var route = new ApprovalRouteResolver(
                    new TriggerConditionMatcher(objectMapper)
            ).resolve(
                    definition.gateway(),
                    definition.approverIds(),
                    definition.approvalMode(),
                    valuesJson
            );
            var resolvedApproverIds = requiredRouteMembers(
                    session,
                    definition.approverSources(),
                    route.branchCode(),
                    route.approverIds(),
                    binding,
                    bindingSource,
                    valuesJson
            );
            if (route.branchCode() != null) {
                var selectedBranch = definition.gateway().branches().stream()
                        .filter(branch ->
                                branch.code().equals(route.branchCode()))
                        .findFirst()
                        .orElseThrow();
                instance = workflow.startBranch(
                        definition.definitionId(),
                        definition.version(),
                        selectedBranch,
                        resolvedApproverIds,
                        request.businessKey(),
                        session.memberId(),
                        binding,
                        startContext
                );
            } else {
                instance = workflow.startResolved(
                        definition.definitionId(),
                        definition.version(),
                        resolvedApproverIds,
                        route.approvalMode(),
                        com.unique.examine.flow.domain.ApprovalQuorumRules
                                .requiredApprovals(
                                        route.approvalMode(),
                                        definition.quorumRules().primary(),
                                        resolvedApproverIds.size()
                                ),
                        definition.deadlinePolicies().primary(),
                        definition.decisionCommentPolicies().primary(),
                        request.businessKey(),
                        session.memberId(),
                        binding,
                        startContext
                );
            }
        }
        if (binding != null) {
            var recordStatusMapping = recordStatusMapping(workflow.definitionVersion(
                    instance.definitionId(),
                    instance.definitionVersion()
            ).recordStatusMapping());
            if (additionalEventKey == null) {
                recordFlows.bind(new RuntimeRecordFlowFacade.BindRequest(
                        session.systemId(),
                        session.tenantId(),
                        session.memberId(),
                        session.permissions(),
                        binding.moduleCode(),
                        binding.recordId(),
                        instance.id(),
                        instance.startedAt(),
                        recordStatusMapping,
                        bindingSource
                ));
            } else {
                recordFlows.bindAdditional(new RuntimeRecordFlowFacade.AdditionalBindRequest(
                        session.systemId(),
                        session.tenantId(),
                        session.memberId(),
                        session.permissions(),
                        binding.moduleCode(),
                        binding.recordId(),
                        instance.id(),
                        instance.startedAt(),
                        additionalEventKey,
                        recordStatusMapping,
                        bindingSource
                ));
            }
        }
        return FlowViews.Instance.from(instance);
    }

    /**
     * Starts the exact child version frozen in a SUBFLOW execution. This path
     * intentionally skips record-flow binding/projection: the child inherits
     * immutable record facts for approver resolution but only the parent owns
     * the record projection.
     */
    @Transactional
    public ApprovalInstance startSubflow(
            long systemId,
            long tenantId,
            ApprovalInstance parent,
            com.unique.examine.flow.domain.ApprovalCompletionStep.Subflow target,
            String launchKey
    ) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(target, "target");
        if (launchKey == null
                || !launchKey.matches("^[0-9a-f]{64}$")) {
            throw new BusinessException(
                    "FLOW_SUBFLOW_LAUNCH_CONFLICT",
                    "Subflow launch identity is invalid",
                    HttpStatus.CONFLICT
            );
        }
        var session = new FlowSession(
                parent.requesterId(), systemId, tenantId,
                parent.requesterId(), java.util.Set.of());
        var workflow = services.forTenant(systemId, tenantId);
        var definition = workflow.definitionVersion(
                target.definitionId(), target.version());
        var binding = parent.recordBinding();
        var inherited = parent.startContext() == null
                ? new ApprovalStartContext(
                        parent.requesterId(),
                        binding == null ? null : binding.moduleCode(),
                        binding == null ? null : binding.recordId(),
                        Map.of())
                : parent.startContext();
        // Nested children do not own a projection binding, but resolver
        // context must continue carrying the immutable record identity.
        final ApprovalInstance.RecordBinding resolverBinding = binding != null
                ? binding
                : inherited.hasRecord()
                        ? new ApprovalInstance.RecordBinding(
                                inherited.moduleCode(), inherited.recordId())
                        : null;
        var rootId = inherited.rootInstanceId() == null
                ? parent.id()
                : inherited.rootInstanceId();
        if (inherited.rootInstanceId() == null) {
            inherited = inherited.root(rootId);
        }
        var childContext = inherited.child(
                rootId, inherited.subflowDepth() + 1);
        var valuesJson = childContext.valuesJson();
        final ApprovalInstance child;
        if (definition.parallelGateway() != null) {
            var resolvedBranches = definition.parallelGateway().branches()
                    .stream()
                    .map(branch -> resolveBranch(
                            session,
                            definition.approverSources(),
                            branch,
                            resolverBinding,
                            RuntimeRecordFlowFacade.BindingSource
                                    .AUTOMATIC_EVENT,
                            valuesJson
                    ))
                    .toList();
            child = workflow.startBranches(
                    definition.definitionId(), definition.version(),
                    resolvedBranches, launchKey, parent.requesterId(),
                    null, childContext);
        } else if (definition.inclusiveGateway() != null) {
            var selected = new ApprovalInclusiveRouteResolver(
                    new TriggerConditionMatcher(objectMapper)
            ).resolve(definition.inclusiveGateway(), valuesJson);
            if (selected.isEmpty()) {
                throw new BusinessException(
                        "FLOW_GATEWAY_NO_MATCH",
                        "Subflow inclusive gateway did not match a branch",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }
            var resolvedBranches = selected.stream()
                    .map(branch -> resolveBranch(
                            session,
                            definition.approverSources(),
                            branch,
                            resolverBinding,
                            RuntimeRecordFlowFacade.BindingSource
                                    .AUTOMATIC_EVENT,
                            valuesJson
                    ))
                    .toList();
            child = workflow.startBranches(
                    definition.definitionId(), definition.version(),
                    resolvedBranches, launchKey, parent.requesterId(),
                    null, childContext);
        } else {
            var route = new ApprovalRouteResolver(
                    new TriggerConditionMatcher(objectMapper)
            ).resolve(
                    definition.gateway(), definition.approverIds(),
                    definition.approvalMode(), valuesJson);
            var resolvedApprovers = requiredRouteMembers(
                    session,
                    definition.approverSources(),
                    route.branchCode(),
                    route.approverIds(),
                    resolverBinding,
                    RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT,
                    valuesJson
            );
            if (route.branchCode() != null) {
                var selectedBranch = definition.gateway().branches().stream()
                        .filter(branch -> branch.code().equals(
                                route.branchCode()))
                        .findFirst()
                        .orElseThrow();
                child = workflow.startBranch(
                        definition.definitionId(), definition.version(),
                        selectedBranch, resolvedApprovers, launchKey,
                        parent.requesterId(), null, childContext);
            } else {
                child = workflow.startResolved(
                        definition.definitionId(), definition.version(),
                        resolvedApprovers, route.approvalMode(),
                        com.unique.examine.flow.domain.ApprovalQuorumRules
                                .requiredApprovals(
                                        route.approvalMode(),
                                        definition.quorumRules().primary(),
                                        resolvedApprovers.size()),
                        definition.deadlinePolicies().primary(),
                        definition.decisionCommentPolicies().primary(),
                        launchKey, parent.requesterId(), null,
                        childContext);
            }
        }
        return child;
    }

    private static boolean hasLaterRecordSource(
            com.unique.examine.flow.domain.ApprovalDefinitionVersion definition
    ) {
        if (containsLaterRecordSource(definition.approvalStages())) {
            return true;
        }
        java.util.stream.Stream<? extends ApprovalBranchRoute> branches =
                definition.gateway() != null
                        ? definition.gateway().branches().stream()
                        : definition.parallelGateway() != null
                                ? definition.parallelGateway().branches().stream()
                                : definition.inclusiveGateway() != null
                                        ? definition.inclusiveGateway().branches()
                                                .stream()
                                        : java.util.stream.Stream.empty();
        return branches.anyMatch(branch ->
                containsLaterRecordSource(branch.approvalStages()));
    }

    private static boolean containsLaterRecordSource(
            List<ApprovalStage> stages
    ) {
        return stages != null
                && stages.stream().skip(1).anyMatch(stage ->
                        stage.approverSource().kind()
                                == ApprovalApproverSource.Kind
                                        .RECORD_MEMBER_FIELD);
    }

    @Transactional
    public FlowViews.DelegationRule createDelegation(
            FlowSession session,
            FlowRequests.CreateDelegation request
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(request, "request");
        var delegatorMemberId = delegationMemberId(
                request.delegatorMemberId(),
                session.memberId(),
                "delegatorMemberId"
        );
        var delegateMemberId = delegationMemberId(
                request.delegateMemberId(),
                null,
                "delegateMemberId"
        );
        if (!active(session, delegatorMemberId)
                || !active(session, delegateMemberId)) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.DELEGATION_RULE_INVALID,
                    "Delegator and delegate must be active members of this tenant"
            );
        }
        var result = services.forTenant(session.systemId(), session.tenantId())
                .createDelegation(
                        session.tenantId(),
                        session.memberId(),
                        canManageDelegations(session),
                        delegatorMemberId,
                        delegateMemberId,
                        delegationInstant(request.startsAt(), "startsAt"),
                        delegationInstant(request.endsAt(), "endsAt"),
                        optionalDelegationId(
                                request.definitionId(),
                                "definitionId"
                        )
                );
        return FlowViews.DelegationRule.from(result);
    }

    public FlowViews.DelegationRulePage delegations(
            FlowSession session,
            String delegatorMemberId,
            int page,
            int size
    ) {
        Objects.requireNonNull(session, "session");
        var targetMemberId = delegationMemberId(
                delegatorMemberId,
                session.memberId(),
                "delegatorMemberId"
        );
        var result = services.forTenant(session.systemId(), session.tenantId())
                .delegations(
                        session.memberId(),
                        canManageDelegations(session),
                        targetMemberId,
                        page,
                        size
                );
        return FlowViews.DelegationRulePage.from(result);
    }

    @Transactional
    public FlowViews.DelegationRule revokeDelegation(
            FlowSession session,
            long delegationRuleId
    ) {
        Objects.requireNonNull(session, "session");
        var result = services.forTenant(session.systemId(), session.tenantId())
                .revokeDelegation(
                        session.tenantId(),
                        delegationRuleId,
                        session.memberId(),
                        canManageDelegations(session)
                );
        return FlowViews.DelegationRule.from(result);
    }

    @Transactional
    public FlowViews.Instance approve(
            FlowSession session,
            long instanceId,
            FlowRequests.Decision request
    ) {
        Objects.requireNonNull(session, "session");
        var instance = approveDecision(session, instanceId, request);
        projectTerminal(session, instance);
        return FlowViews.Instance.from(instance);
    }

    @Transactional
    public FlowViews.Instance approve(
            FlowSession session,
            long instanceId,
            FlowRequests.Decision request,
            String idempotencyKey
    ) {
        if (idempotencyKey == null) {
            return approve(session, instanceId, request);
        }
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "approve",
                "approval",
                () -> {
                    var instance = approveDecision(
                            session, instanceId, request);
                    projectTerminal(session, instance);
                    return instance;
                }
        );
    }

    @Transactional
    public FlowViews.Instance reject(
            FlowSession session,
            long instanceId,
            FlowRequests.Rejection request
    ) {
        Objects.requireNonNull(session, "session");
        var instance = rejectDecision(session, instanceId, request);
        projectTerminal(session, instance);
        return FlowViews.Instance.from(instance);
    }

    @Transactional
    public FlowViews.Instance reject(
            FlowSession session,
            long instanceId,
            FlowRequests.Rejection request,
            String idempotencyKey
    ) {
        if (idempotencyKey == null) {
            return reject(session, instanceId, request);
        }
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "reject",
                "rejection",
                () -> {
                    var instance = rejectDecision(
                            session, instanceId, request);
                    projectTerminal(session, instance);
                    return instance;
                }
        );
    }

    @Transactional
    public FlowViews.Instance approveBranch(
            FlowSession session,
            long instanceId,
            String branchCode,
            FlowRequests.Decision request
    ) {
        Objects.requireNonNull(session, "session");
        var instance = approveBranchDecision(
                session, instanceId, branchCode, request);
        projectTerminal(session, instance);
        return FlowViews.Instance.from(instance);
    }

    @Transactional
    public FlowViews.Instance approveBranch(
            FlowSession session,
            long instanceId,
            String branchCode,
            FlowRequests.Decision request,
            String idempotencyKey
    ) {
        if (idempotencyKey == null) {
            return approveBranch(
                    session, instanceId, branchCode, request);
        }
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "approve:" + branchCode,
                "branch approval",
                () -> {
                    var instance = approveBranchDecision(
                            session, instanceId, branchCode, request);
                    projectTerminal(session, instance);
                    return instance;
                }
        );
    }

    @Transactional
    public FlowViews.Instance rejectBranch(
            FlowSession session,
            long instanceId,
            String branchCode,
            FlowRequests.Rejection request
    ) {
        Objects.requireNonNull(session, "session");
        var instance = rejectBranchDecision(
                session, instanceId, branchCode, request);
        projectTerminal(session, instance);
        return FlowViews.Instance.from(instance);
    }

    @Transactional
    public FlowViews.Instance rejectBranch(
            FlowSession session,
            long instanceId,
            String branchCode,
            FlowRequests.Rejection request,
            String idempotencyKey
    ) {
        if (idempotencyKey == null) {
            return rejectBranch(
                    session, instanceId, branchCode, request);
        }
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "reject:" + branchCode,
                "branch rejection",
                () -> {
                    var instance = rejectBranchDecision(
                            session, instanceId, branchCode, request);
                    projectTerminal(session, instance);
                    return instance;
                }
        );
    }

    private ApprovalInstance approveDecision(
            FlowSession session,
            long instanceId,
            FlowRequests.Decision request
    ) {
        var selection = decisionEvidence(
                session,
                request == null ? null : request.attachmentFileIds(),
                request == null ? null : request.signatureFileId(),
                request == null ? null : request.typedSignature(),
                request == null ? null : request.commentTemplateId()
        );
        var result = services.forTenant(
                        session.systemId(), session.tenantId())
                .approveWithEvidence(
                        instanceId,
                        session.memberId(),
                        representedMemberId(
                                request == null
                                        ? null
                                        : request.representedMemberId()),
                        request == null ? null : request.comment(),
                        selection.input(),
                        (nextStage, completedStage, startContext) ->
                                stageActivationMembers(
                                        session, nextStage, completedStage,
                                        startContext)
                );
        attachEvidence(session, result.evidence());
        return result.instance();
    }

    private ApprovalInstance rejectDecision(
            FlowSession session,
            long instanceId,
            FlowRequests.Rejection request
    ) {
        var selection = decisionEvidence(
                session,
                request == null ? null : request.attachmentFileIds(),
                request == null ? null : request.signatureFileId(),
                request == null ? null : request.typedSignature(),
                request == null ? null : request.commentTemplateId()
        );
        var result = services.forTenant(
                        session.systemId(), session.tenantId())
                .rejectWithEvidence(
                        instanceId,
                        session.memberId(),
                        representedMemberId(
                                request == null
                                        ? null
                                        : request.representedMemberId()),
                        request == null ? null : request.reason(),
                        selection.input()
                );
        attachEvidence(session, result.evidence());
        return result.instance();
    }

    private ApprovalInstance approveBranchDecision(
            FlowSession session,
            long instanceId,
            String branchCode,
            FlowRequests.Decision request
    ) {
        var selection = decisionEvidence(
                session,
                request == null ? null : request.attachmentFileIds(),
                request == null ? null : request.signatureFileId(),
                request == null ? null : request.typedSignature(),
                request == null ? null : request.commentTemplateId()
        );
        var result = services.forTenant(
                        session.systemId(), session.tenantId())
                .approveBranchWithEvidence(
                        instanceId,
                        branchCode,
                        session.memberId(),
                        representedMemberId(
                                request == null
                                        ? null
                                        : request.representedMemberId()),
                        request == null ? null : request.comment(),
                        selection.input(),
                        (code, nextStage, completedStage, startContext) ->
                                stageActivationMembers(
                                        session, nextStage, completedStage,
                                        startContext)
                );
        attachEvidence(session, result.evidence());
        return result.instance();
    }

    private ApprovalInstance rejectBranchDecision(
            FlowSession session,
            long instanceId,
            String branchCode,
            FlowRequests.Rejection request
    ) {
        var selection = decisionEvidence(
                session,
                request == null ? null : request.attachmentFileIds(),
                request == null ? null : request.signatureFileId(),
                request == null ? null : request.typedSignature(),
                request == null ? null : request.commentTemplateId()
        );
        var result = services.forTenant(
                        session.systemId(), session.tenantId())
                .rejectBranchWithEvidence(
                        instanceId,
                        branchCode,
                        session.memberId(),
                        representedMemberId(
                                request == null
                                        ? null
                                        : request.representedMemberId()),
                        request == null ? null : request.reason(),
                        selection.input()
                );
        attachEvidence(session, result.evidence());
        return result.instance();
    }

    private DecisionEvidenceSelection decisionEvidence(
            FlowSession session,
            List<String> attachmentValues,
            String signatureValue,
            String typedSignature,
            String commentTemplateValue
    ) {
        var attachmentIds = evidenceFileIds(
                attachmentValues, "attachmentFileIds", 5);
        var signatureFileId = evidenceId(
                signatureValue, "signatureFileId");
        var commentTemplateId = evidenceId(
                commentTemplateValue, "commentTemplateId");
        if (signatureFileId != null && typedSignature != null) {
            throw evidenceInvalid(
                    "signatureFileId and typedSignature are mutually exclusive");
        }
        var referencedIds = new java.util.LinkedHashSet<Long>(attachmentIds);
        if (signatureFileId != null) {
            referencedIds.add(signatureFileId);
        }
        final List<ApprovalEvidenceFileFacade.FileMetadata> metadata;
        try {
            metadata = evidenceFiles.validateSelection(
                    evidenceActor(session), List.copyOf(referencedIds));
        } catch (ApprovalEvidenceFileFacade.EvidenceFileException failure) {
            throw evidenceFileFailure(failure);
        }
        var snapshots = new java.util.LinkedHashMap<
                Long, ApprovalDecisionEvidenceFile>();
        metadata.forEach(file -> snapshots.put(
                file.fileId(),
                new ApprovalDecisionEvidenceFile(
                        file.fileId(),
                        file.originalName(),
                        file.contentType(),
                        file.size(),
                        file.sha256()
                )));
        var attachments = attachmentIds.stream()
                .map(snapshots::get)
                .toList();
        ApprovalDecisionEvidence.Signature signature = null;
        if (signatureFileId != null) {
            signature = ApprovalDecisionEvidence.Signature.file(
                    Objects.requireNonNull(snapshots.get(signatureFileId)));
        } else if (typedSignature != null) {
            try {
                signature = ApprovalDecisionEvidence.Signature.typed(
                        typedSignature);
            } catch (RuntimeException failure) {
                throw evidenceInvalid(failure.getMessage());
            }
        }
        return new DecisionEvidenceSelection(
                new ApprovalWorkflowService.DecisionEvidenceInput(
                        attachments, signature, commentTemplateId)
        );
    }

    private void attachEvidence(
            FlowSession session,
            ApprovalDecisionEvidence evidence
    ) {
        if (evidence == null) {
            return;
        }
        var fileIds = evidence.referencedFiles().stream()
                .map(ApprovalDecisionEvidenceFile::fileId)
                .toList();
        try {
            evidenceFiles.attachEvidence(
                    evidenceActor(session), evidence.id(), fileIds);
        } catch (ApprovalEvidenceFileFacade.EvidenceFileException failure) {
            throw evidenceFileFailure(failure);
        }
    }

    private static ApprovalEvidenceFileFacade.EvidenceActor evidenceActor(
            FlowSession session
    ) {
        return new ApprovalEvidenceFileFacade.EvidenceActor(
                session.systemId(),
                session.tenantId(),
                session.memberId(),
                session.permissions()
        );
    }

    private static List<Long> evidenceFileIds(
            List<String> values,
            String field,
            int maximum
    ) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > maximum) {
            throw evidenceInvalid(
                    field + " accepts at most " + maximum + " file ids");
        }
        var ids = values.stream()
                .map(value -> {
                    var parsed = evidenceId(value, field);
                    if (parsed == null) {
                        throw evidenceInvalid(
                                field + " must contain positive id strings");
                    }
                    return parsed;
                })
                .toList();
        if (new java.util.LinkedHashSet<>(ids).size() != ids.size()) {
            throw evidenceInvalid(field + " must contain distinct file ids");
        }
        return ids;
    }

    private static Long evidenceId(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            if (value.isBlank()) {
                throw new NumberFormatException();
            }
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw evidenceInvalid(field + " must be a positive id string");
        }
    }

    private static BusinessException evidenceInvalid(String message) {
        return new BusinessException(
                "FLOW_EVIDENCE_INVALID",
                message,
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private static BusinessException evidenceFileFailure(
            ApprovalEvidenceFileFacade.EvidenceFileException failure
    ) {
        var status = switch (failure.code()) {
            case "EVIDENCE_FILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "EVIDENCE_FILE_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return new BusinessException(
                "FLOW_" + failure.code(),
                failure.getMessage(),
                status
        );
    }

    private record DecisionEvidenceSelection(
            ApprovalWorkflowService.DecisionEvidenceInput input
    ) {
    }

    @Transactional
    public FlowViews.Instance withdraw(
            FlowSession session,
            long instanceId,
            FlowRequests.Withdrawal request,
            String idempotencyKey
    ) {
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "withdraw",
                "withdrawal",
                () -> {
                    var instance = services.forTenant(session.systemId(), session.tenantId())
                            .withdraw(
                                    instanceId,
                                    session.memberId(),
                                    request == null ? null : request.reason()
                            );
                    projectTerminal(session, instance);
                    return instance;
                }
        );
    }

    @Transactional
    public FlowViews.Instance terminate(
            FlowSession session,
            long instanceId,
            FlowRequests.Termination request,
            String idempotencyKey
    ) {
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "terminate",
                "termination",
                () -> {
                    var instance = services.forTenant(session.systemId(), session.tenantId())
                            .terminate(
                                    instanceId,
                                    session.memberId(),
                                    request == null ? null : request.reason()
                            );
                    projectTerminal(session, instance);
                    return instance;
                }
        );
    }

    @Transactional
    public FlowViews.Instance transfer(
            FlowSession session,
            long instanceId,
            FlowRequests.Transfer request,
            String idempotencyKey
    ) {
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "transfer",
                "transfer",
                () -> {
                    var service = services.forTenant(session.systemId(), session.tenantId());
                    var expected = service.requireCurrentApprover(instanceId, session.memberId());
                    var targetMemberId = activeTarget(
                            session,
                            request == null ? null : request.targetMemberId()
                    );
                    return service.transfer(
                            expected,
                            session.memberId(),
                            targetMemberId,
                            request == null ? null : request.reason()
                    );
                }
        );
    }

    @Transactional
    public FlowViews.Instance addSign(
            FlowSession session,
            long instanceId,
            FlowRequests.AddSign request,
            String idempotencyKey
    ) {
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "add-sign",
                "add-sign",
                () -> {
                    var service = services.forTenant(session.systemId(), session.tenantId());
                    var expected = service.requireCurrentApprover(instanceId, session.memberId());
                    var targetMemberId = activeTarget(
                            session,
                            request == null ? null : request.targetMemberId()
                    );
                    return service.addSign(
                            expected,
                            session.memberId(),
                            targetMemberId,
                            assignmentPosition(request == null ? null : request.position()),
                            request == null ? null : request.reason()
                    );
                }
        );
    }

    @Transactional
    public FlowViews.Instance returnToPrevious(
            FlowSession session,
            long instanceId,
            FlowRequests.Return request,
            String idempotencyKey
    ) {
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "return",
                "return",
                () -> services.forTenant(session.systemId(), session.tenantId())
                        .returnToPrevious(
                                instanceId,
                                session.memberId(),
                                request == null ? null : request.reason()
                        )
        );
    }

    @Transactional
    public FlowViews.Instance cancelClaim(
            FlowSession session,
            long instanceId,
            FlowRequests.CancelClaim request,
            String idempotencyKey
    ) {
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "cancel-claim",
                "cancel-claim",
                () -> services.forTenant(session.systemId(), session.tenantId())
                        .cancelClaim(
                                instanceId,
                                session.memberId(),
                                request == null ? null : request.reason()
                        )
        );
    }

    @Transactional
    public FlowViews.Instance claim(
            FlowSession session,
            long instanceId,
            FlowRequests.Claim request,
            String idempotencyKey
    ) {
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "claim",
                "claim",
                () -> services.forTenant(session.systemId(), session.tenantId())
                        .claim(
                                instanceId,
                                session.memberId(),
                                request == null ? null : request.comment()
                        )
        );
    }

    @Transactional
    public FlowViews.Instance reduceSign(
            FlowSession session,
            long instanceId,
            FlowRequests.ReduceSign request,
            String idempotencyKey
    ) {
        return mutate(
                session,
                instanceId,
                request,
                idempotencyKey,
                "reduce-sign",
                "reduce-sign",
                () -> services.forTenant(session.systemId(), session.tenantId())
                        .reduceSign(
                                instanceId,
                                session.memberId(),
                                request == null ? null : request.targetStepIndex(),
                                request == null ? null : request.reason()
                        )
        );
    }

    private FlowViews.Instance mutate(
            FlowSession session,
            long instanceId,
            Object request,
            String idempotencyKey,
            String action,
            String actionName,
            java.util.function.Supplier<com.unique.examine.flow.domain.ApprovalInstance> mutation
    ) {
        Objects.requireNonNull(session, "session");
        requireKey(idempotencyKey);
        var scopeKey = session.systemId() + ":" + session.tenantId() + ":" + session.memberId()
                + ":" + instanceId + ":" + action;
        var requestHash = sha256(write(request));
        var existing = idempotency.find(IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, actionName);
        }
        final long id;
        try {
            id = idempotency.begin(
                    IDEMPOTENCY_SCOPE,
                    scopeKey,
                    idempotencyKey,
                    requestHash,
                    Duration.ofHours(24));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("REQUEST_IN_PROGRESS", "The " + actionName + " request is already being processed");
        }
        var result = FlowViews.Instance.from(mutation.get());
        idempotency.complete(id, 200, "OK", write(result));
        return result;
    }

    private List<com.unique.examine.flow.domain.ApprovalCompletionStep>
            completionDefinitions(
                    FlowSession session,
                    List<FlowRequests.CompletionStep> requests,
                    List<com.unique.examine.flow.domain
                            .ApprovalCompletionStep> current
            ) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (completionDefinitions == null) {
            throw new BusinessException(
                    "FLOW_COMPLETION_CONFIGURATION_UNAVAILABLE",
                    "Completion-step validation is unavailable",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        return current == null
                ? completionDefinitions.create(
                        session.systemId(), session.tenantId(), requests)
                : completionDefinitions.revise(
                        session.systemId(), session.tenantId(),
                        requests, current);
    }

    private static com.unique.examine.flow.domain.CompletionFailurePolicy
            completionFailurePolicy(String value) {
        if (value == null || value.isBlank()) {
            return com.unique.examine.flow.domain.CompletionFailurePolicy
                    .MANUAL_RETRY;
        }
        try {
            return com.unique.examine.flow.domain.CompletionFailurePolicy
                    .valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException failure) {
            throw new BusinessException(
                    "FLOW_COMPLETION_FAILURE_POLICY_INVALID",
                    "completionFailurePolicy must be MANUAL_RETRY or COMPENSATE",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private DefinitionRoute definitionRoute(
            FlowSession session,
            String approverId,
            java.util.List<String> approverIds,
            FlowRequests.Gateway gatewayRequest,
            String approvalModeRequest,
            FlowRequests.ParallelGateway parallelGatewayRequest,
            FlowRequests.InclusiveGateway inclusiveGatewayRequest,
            FlowRequests.ApproverSource approverSourceRequest,
            FlowRequests.QuorumRule quorumRuleRequest,
            FlowRequests.DeadlinePolicy deadlinePolicyRequest,
            FlowRequests.DecisionCommentPolicy decisionCommentPolicyRequest,
            java.util.List<FlowRequests.ApprovalStage> approvalStageRequests,
            FlowRequests.DecisionEvidencePolicy decisionEvidencePolicyRequest,
            java.util.function.Function<ApprovalApproverSource, java.util.List<Long>>
                    dynamicMembers
    ) {
        if (approvalStageRequests == null) {
            var gateway = FlowHttpErrors.gateway(gatewayRequest, dynamicMembers);
            var parallelGateway = FlowHttpErrors.parallelGateway(
                    parallelGatewayRequest, dynamicMembers);
            var inclusiveGateway = FlowHttpErrors.inclusiveGateway(
                    inclusiveGatewayRequest, dynamicMembers);
            var sources = FlowHttpErrors.approverSources(
                    approverSourceRequest,
                    gatewayRequest,
                    parallelGatewayRequest,
                    inclusiveGatewayRequest
            );
            var quorumRules = FlowHttpErrors.quorumRules(
                    approvalModeRequest,
                    quorumRuleRequest,
                    gatewayRequest,
                    parallelGatewayRequest,
                    inclusiveGatewayRequest
            );
            var deadlinePolicies = FlowHttpErrors.deadlinePolicies(
                    deadlinePolicyRequest,
                    gatewayRequest,
                    parallelGatewayRequest,
                    inclusiveGatewayRequest
            );
            var decisionCommentPolicies = FlowHttpErrors.decisionCommentPolicies(
                    decisionCommentPolicyRequest,
                    gatewayRequest,
                    parallelGatewayRequest,
                    inclusiveGatewayRequest
            );
            var decisionEvidencePolicies =
                    FlowHttpErrors.decisionEvidencePolicies(
                            decisionEvidencePolicyRequest,
                            gatewayRequest,
                            parallelGatewayRequest,
                            inclusiveGatewayRequest
                    );
            var primaryBranch = primaryBranch(
                    gateway, parallelGateway, inclusiveGateway);
            var branchStageProjection = primaryBranch != null
                    && primaryBranch.approvalStages() != null;
            if (branchStageProjection) {
                sources = new ApprovalApproverSources(
                        sources.branch(primaryBranch.code()), sources.branches());
                quorumRules = new ApprovalQuorumRules(
                        quorumRules.branch(primaryBranch.code()),
                        quorumRules.branches());
                deadlinePolicies = new ApprovalDeadlinePolicies(
                        deadlinePolicies.branch(primaryBranch.code()),
                        deadlinePolicies.branches());
                decisionCommentPolicies = new ApprovalDecisionCommentPolicies(
                        decisionCommentPolicies.branch(primaryBranch.code()),
                        decisionCommentPolicies.branches());
                decisionEvidencePolicies =
                        new ApprovalDecisionEvidencePolicies(
                                decisionEvidencePolicies.branch(
                                        primaryBranch.code()),
                                decisionEvidencePolicies.branches());
            }
            return new DefinitionRoute(
                    branchStageProjection
                            ? primaryBranch.approverIds()
                            : FlowHttpErrors.routeApproverIds(
                                    approverId, approverIds,
                                    approverSourceRequest, dynamicMembers),
                    gateway,
                    branchStageProjection
                            ? primaryBranch.approvalMode()
                            : FlowHttpErrors.approvalMode(approvalModeRequest),
                    parallelGateway,
                    inclusiveGateway,
                    sources,
                    quorumRules,
                    deadlinePolicies,
                    decisionCommentPolicies,
                    null,
                    decisionEvidencePolicies
            );
        }
        if (gatewayRequest != null
                || parallelGatewayRequest != null
                || inclusiveGatewayRequest != null) {
            throw new BusinessException(
                    "FLOW_APPROVAL_STAGES_GATEWAY_UNSUPPORTED",
                    "Explicit approval stages cannot be combined with a gateway",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        var stages = FlowHttpErrors.approvalStages(approvalStageRequests);
        if (stages == null || stages.isEmpty()) {
            throw invalidApprovalStageMirror(
                    "Explicit approvalStages must contain 2 to 10 stages");
        }
        var first = stages.getFirst();
        if (first.approverSource().kind()
                == ApprovalApproverSource.Kind.PREVIOUS_HANDLER) {
            throw invalidApprovalStageMirror(
                    "The first approval stage cannot use PREVIOUS_HANDLER");
        }
        requireStageZeroMirror(
                first,
                approverId,
                approverIds,
                approvalModeRequest,
                approverSourceRequest,
                quorumRuleRequest,
                deadlinePolicyRequest,
                decisionCommentPolicyRequest,
                decisionEvidencePolicyRequest
        );
        var routeMembers = first.approverSource().dynamic()
                ? draftRouteMembers(session, first.approverSource())
                : first.approverIds();
        return new DefinitionRoute(
                routeMembers,
                null,
                first.approvalMode(),
                null,
                null,
                new ApprovalApproverSources(first.approverSource(), Map.of()),
                new ApprovalQuorumRules(first.quorumRule(), Map.of()),
                new ApprovalDeadlinePolicies(first.deadlinePolicy(), Map.of()),
                new ApprovalDecisionCommentPolicies(
                        first.decisionCommentPolicy(), Map.of()),
                stages,
                new ApprovalDecisionEvidencePolicies(
                        first.decisionEvidencePolicy(), Map.of())
        );
    }

    private static ApprovalBranchRoute primaryBranch(
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        if (gateway != null) {
            return gateway.defaultBranch();
        }
        if (parallelGateway != null) {
            return parallelGateway.branches().getFirst();
        }
        return inclusiveGateway == null
                ? null
                : inclusiveGateway.branches().getFirst();
    }

    private static void requireStageZeroMirror(
            ApprovalStage first,
            String approverId,
            java.util.List<String> approverIds,
            String approvalMode,
            FlowRequests.ApproverSource approverSource,
            FlowRequests.QuorumRule quorumRule,
            FlowRequests.DeadlinePolicy deadlinePolicy,
            FlowRequests.DecisionCommentPolicy decisionCommentPolicy,
            FlowRequests.DecisionEvidencePolicy decisionEvidencePolicy
    ) {
        if ((approverId != null || approverIds != null)
                && !FlowHttpErrors.approverIds(approverId, approverIds)
                        .equals(first.approverIds())) {
            throw invalidApprovalStageMirror(
                    "Legacy approver fields must exactly mirror approvalStages[0]");
        }
        if (approvalMode != null
                && FlowHttpErrors.approvalMode(approvalMode) != first.approvalMode()) {
            throw invalidApprovalStageMirror(
                    "Legacy approvalMode must exactly mirror approvalStages[0]");
        }
        if (approverSource != null
                && !FlowHttpErrors.approverSource(approverSource)
                        .equals(first.approverSource())) {
            throw invalidApprovalStageMirror(
                    "Legacy approverSource must exactly mirror approvalStages[0]");
        }
        if (quorumRule != null
                && !Objects.equals(
                        FlowHttpErrors.quorumRules(
                                first.approvalMode().name(),
                                quorumRule,
                                null,
                                null,
                                null
                        ).primary(),
                        first.quorumRule())) {
            throw invalidApprovalStageMirror(
                    "Legacy quorumRule must exactly mirror approvalStages[0]");
        }
        if (deadlinePolicy != null
                && !Objects.equals(
                        FlowHttpErrors.deadlinePolicies(
                                deadlinePolicy, null, null, null).primary(),
                        first.deadlinePolicy())) {
            throw invalidApprovalStageMirror(
                    "Legacy deadlinePolicy must exactly mirror approvalStages[0]");
        }
        if (decisionCommentPolicy != null
                && !Objects.equals(
                        FlowHttpErrors.decisionCommentPolicies(
                                decisionCommentPolicy, null, null, null).primary(),
                        first.decisionCommentPolicy())) {
            throw invalidApprovalStageMirror(
                    "Legacy decisionCommentPolicy must exactly mirror approvalStages[0]");
        }
        if (decisionEvidencePolicy != null
                && !Objects.equals(
                        FlowHttpErrors.decisionEvidencePolicy(
                                decisionEvidencePolicy),
                        first.decisionEvidencePolicy())) {
            throw invalidApprovalStageMirror(
                    "Legacy decisionEvidencePolicy must exactly mirror "
                            + "approvalStages[0]");
        }
    }

    private static ApprovalDomainException invalidApprovalStageMirror(
            String message
    ) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID,
                message
        );
    }

    private record DefinitionRoute(
            java.util.List<Long> approverIds,
            ApprovalGateway gateway,
            ApprovalMode approvalMode,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway,
            ApprovalApproverSources approverSources,
            ApprovalQuorumRules quorumRules,
            ApprovalDeadlinePolicies deadlinePolicies,
            ApprovalDecisionCommentPolicies decisionCommentPolicies,
            java.util.List<ApprovalStage> approvalStages,
            ApprovalDecisionEvidencePolicies decisionEvidencePolicies
    ) {
    }

    private FlowDraftPreflight.Check check(
            FlowSession session,
            com.unique.examine.flow.domain.ApprovalDefinitionDraft draft
    ) {
        var issues = new ArrayList<FlowDraftPreflight.Issue>();
        if (completionDefinitions != null) {
            issues.addAll(completionDefinitions.preflight(
                    session.systemId(),
                    session.tenantId(),
                    draft.id(),
                    draft.completionSteps()
            ));
        }
        if (extensions != null) {
            issues.addAll(extensions.preflight(session, draft));
        }
        var binding = draft.triggerBinding();
        var periodic = binding != null
                && binding.event() == TriggerBinding.Event.PERIODIC;
        if (draft.parallelGateway() != null) {
            for (var branchIndex = 0;
                 branchIndex < draft.parallelGateway().branches().size();
                 branchIndex++) {
                checkApproverRoute(
                        session,
                        draft.approverSources().branch(
                                draft.parallelGateway().branches().get(branchIndex).code()),
                        draft.parallelGateway().branches().get(branchIndex).approverIds(),
                        draft.parallelGateway().branches().get(branchIndex).approvalMode(),
                        draft.quorumRules().branch(
                                draft.parallelGateway().branches().get(branchIndex).code()),
                        "/parallelGateway/branches/" + branchIndex,
                        periodic,
                        issues
                );
            }
        } else if (draft.inclusiveGateway() != null) {
            for (var branchIndex = 0;
                 branchIndex < draft.inclusiveGateway().branches().size();
                 branchIndex++) {
                checkApproverRoute(
                        session,
                        draft.approverSources().branch(
                                draft.inclusiveGateway().branches().get(branchIndex).code()),
                        draft.inclusiveGateway().branches().get(branchIndex).approverIds(),
                        draft.inclusiveGateway().branches().get(branchIndex).approvalMode(),
                        draft.quorumRules().branch(
                                draft.inclusiveGateway().branches().get(branchIndex).code()),
                        "/inclusiveGateway/branches/" + branchIndex,
                        periodic,
                        issues
                );
            }
        } else if (draft.gateway() == null) {
            checkApproverRoute(
                    session,
                    draft.approverSources().route(),
                    draft.approverIds(),
                    draft.approvalMode(),
                    draft.quorumRules().primary(),
                    "",
                    periodic,
                    issues
            );
        } else {
            for (var branchIndex = 0;
                 branchIndex < draft.gateway().branches().size();
                 branchIndex++) {
                checkApproverRoute(
                        session,
                        draft.approverSources().branch(
                                draft.gateway().branches().get(branchIndex).code()),
                        draft.gateway().branches().get(branchIndex).approverIds(),
                        draft.gateway().branches().get(branchIndex).approvalMode(),
                        draft.quorumRules().branch(
                                draft.gateway().branches().get(branchIndex).code()),
                        "/gateway/branches/" + branchIndex,
                        periodic,
                        issues
                );
            }
        }
        if (draft.approvalStages() != null) {
            checkLaterStages(
                    session, draft.approvalStages(), "/approvalStages",
                    periodic, issues);
        }
        if (draft.gateway() != null) {
            for (var branchIndex = 0;
                 branchIndex < draft.gateway().branches().size();
                 branchIndex++) {
                checkLaterStages(
                        session,
                        draft.gateway().branches().get(branchIndex)
                                .approvalStages(),
                        "/gateway/branches/" + branchIndex
                                + "/approvalStages",
                        periodic,
                        issues);
            }
        } else if (draft.parallelGateway() != null) {
            for (var branchIndex = 0;
                 branchIndex < draft.parallelGateway().branches().size();
                 branchIndex++) {
                checkLaterStages(
                        session,
                        draft.parallelGateway().branches().get(branchIndex)
                                .approvalStages(),
                        "/parallelGateway/branches/" + branchIndex
                                + "/approvalStages",
                        periodic,
                        issues);
            }
        } else if (draft.inclusiveGateway() != null) {
            for (var branchIndex = 0;
                 branchIndex < draft.inclusiveGateway().branches().size();
                 branchIndex++) {
                checkLaterStages(
                        session,
                        draft.inclusiveGateway().branches().get(branchIndex)
                                .approvalStages(),
                        "/inclusiveGateway/branches/" + branchIndex
                                + "/approvalStages",
                        periodic,
                        issues);
            }
        }
        if (binding != null
                && binding.event() == TriggerBinding.Event.PERIODIC
                && draft.inclusiveGateway() != null
                && new ApprovalInclusiveRouteResolver(
                        new TriggerConditionMatcher(objectMapper)
                ).resolve(draft.inclusiveGateway(), java.util.Map.of()).isEmpty()) {
            issues.add(new FlowDraftPreflight.Issue(
                    FlowDraftPreflight.Severity.BLOCKER,
                    "INCLUSIVE_NO_MATCH",
                    "/inclusiveGateway",
                    "Periodic inclusive gateways require a branch that matches empty input "
                            + "or a default branch"
            ));
        }
        if (binding != null
                && binding.event() == TriggerBinding.Event.PERIODIC
                && !active(session, binding.periodicSchedule().requesterMemberId())) {
            issues.add(new FlowDraftPreflight.Issue(
                    FlowDraftPreflight.Severity.BLOCKER,
                    "PERIODIC_REQUESTER_INACTIVE",
                    "/triggerBinding/requesterMemberId",
                    "Periodic requester " + binding.periodicSchedule().requesterMemberId()
                            + " is not active in the current system and tenant"
            ));
        }
        var verdict = issues.stream().anyMatch(
                issue -> issue.severity() == FlowDraftPreflight.Severity.BLOCKER)
                ? FlowDraftPreflight.Verdict.BLOCKED
                : FlowDraftPreflight.Verdict.READY;
        return new FlowDraftPreflight.Check(
                draft.id(),
                draft.revision(),
                verdict,
                issues
        );
    }

    private void checkLaterStages(
            FlowSession session,
            List<ApprovalStage> stages,
            String path,
            boolean periodic,
            List<FlowDraftPreflight.Issue> issues
    ) {
        if (stages == null) {
            return;
        }
        for (var stageIndex = 1; stageIndex < stages.size(); stageIndex++) {
            var stage = stages.get(stageIndex);
            if (stage.approverSource().kind()
                    == ApprovalApproverSource.Kind.PREVIOUS_HANDLER) {
                continue;
            }
            checkApproverRoute(
                    session,
                    stage.approverSource(),
                    stage.approverIds(),
                    stage.approvalMode(),
                    stage.quorumRule(),
                    path + "/" + stageIndex,
                    periodic,
                    issues
            );
        }
    }

    private void checkApproverRoute(
            FlowSession session,
            ApprovalApproverSource source,
            java.util.List<Long> approverIds,
            com.unique.examine.flow.domain.ApprovalMode approvalMode,
            com.unique.examine.flow.domain.ApprovalQuorumRule quorumRule,
            String path,
            boolean periodic,
            java.util.List<FlowDraftPreflight.Issue> issues
    ) {
        if (source.kind() == ApprovalApproverSource.Kind.REQUESTER
                || source.kind()
                        == ApprovalApproverSource.Kind.REQUESTER_MANAGER
                || source.kind()
                        == ApprovalApproverSource.Kind
                                .REQUESTER_DEPARTMENT_LEADER) {
            if (periodic) {
                issues.add(new FlowDraftPreflight.Issue(
                        FlowDraftPreflight.Severity.BLOCKER,
                        "PERIODIC_REQUESTER_CONTEXT_UNAVAILABLE",
                        path + "/approverSource",
                        "Periodic flows cannot use requester-context sources because "
                                + "no human triggering requester exists"
                ));
            } else {
                // Requester-context sources always resolve to at most one member.
                // Availability is intentionally evaluated with the actual
                // requester during simulation and start.
                checkQuorumRule(approvalMode, quorumRule, 1, path, issues);
            }
            return;
        }
        if (source.kind() == ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD) {
            if (periodic) {
                issues.add(new FlowDraftPreflight.Issue(
                        FlowDraftPreflight.Severity.BLOCKER,
                        "PERIODIC_RECORD_CONTEXT_UNAVAILABLE",
                        path + "/approverSource",
                        "Periodic flows cannot use RECORD_MEMBER_FIELD because "
                                + "no record context exists"
                ));
                return;
            }
            if (!approverSources.publishedSourceActive(session.systemId(), source)) {
                issues.add(new FlowDraftPreflight.Issue(
                        FlowDraftPreflight.Severity.BLOCKER,
                        "APPROVER_SOURCE_INACTIVE",
                        path + "/approverSource",
                        "RECORD_MEMBER_FIELD approver source "
                                + source.moduleCode() + "/" + source.sourceId()
                                + " is not an eligible published MEMBER field"
                ));
                return;
            }
            checkQuorumRule(approvalMode, quorumRule, 1, path, issues);
            return;
        }
        if (source.dynamic()) {
            var resolution = approverSources.resolve(
                    session.systemId(),
                    session.tenantId(),
                    source,
                    approverIds,
                    session.memberId());
            if (!resolution.sourceActive()) {
                issues.add(new FlowDraftPreflight.Issue(
                        FlowDraftPreflight.Severity.BLOCKER,
                        "APPROVER_SOURCE_INACTIVE",
                        path + "/approverSource",
                        source.kind() + " approver source " + source.sourceId()
                                + " is missing or inactive in the current system and tenant"
                ));
                return;
            }
            if (resolution.memberIds().isEmpty()) {
                issues.add(new FlowDraftPreflight.Issue(
                        FlowDraftPreflight.Severity.BLOCKER,
                        "APPROVER_SOURCE_EMPTY",
                        path + "/approverSource",
                        source.kind() + " approver source " + source.sourceId()
                                + " has no active members in the current system and tenant"
                ));
                return;
            }
            if (resolution.memberIds().size() > 10) {
                issues.add(new FlowDraftPreflight.Issue(
                        FlowDraftPreflight.Severity.BLOCKER,
                        "APPROVER_SOURCE_TOO_LARGE",
                        path + "/approverSource",
                        source.kind() + " approver source " + source.sourceId()
                                + " has more than 10 active members"
                ));
            }
            checkQuorumRule(
                    approvalMode,
                    quorumRule,
                    resolution.memberIds().size(),
                    path,
                    issues
            );
            return;
        }
        var visited = new HashSet<Long>();
        for (var index = 0; index < approverIds.size(); index++) {
            var approverId = approverIds.get(index);
            if (!visited.add(approverId)) {
                issues.add(new FlowDraftPreflight.Issue(
                        FlowDraftPreflight.Severity.WARNING,
                        "APPROVER_REPEATED",
                        path + "/approverIds/" + index,
                        "Approver " + approverId + " appears more than once in the route"
                ));
                continue;
            }
            if (!active(session, approverId)) {
                issues.add(new FlowDraftPreflight.Issue(
                        FlowDraftPreflight.Severity.BLOCKER,
                        "APPROVER_INACTIVE",
                        path + "/approverIds/" + index,
                        "Approver " + approverId
                                + " is not active in the current system and tenant"
                ));
            }
        }
        checkQuorumRule(approvalMode, quorumRule, approverIds.size(), path, issues);
    }

    private static void checkQuorumRule(
            com.unique.examine.flow.domain.ApprovalMode approvalMode,
            com.unique.examine.flow.domain.ApprovalQuorumRule quorumRule,
            int memberCount,
            String path,
            java.util.List<FlowDraftPreflight.Issue> issues
    ) {
        if (approvalMode != com.unique.examine.flow.domain.ApprovalMode.QUORUM) {
            return;
        }
        try {
            quorumRule.requiredApprovals(memberCount);
        } catch (RuntimeException exception) {
            issues.add(new FlowDraftPreflight.Issue(
                    FlowDraftPreflight.Severity.BLOCKER,
                    "QUORUM_RULE_UNSATISFIABLE",
                    path + "/quorumRule/value",
                    "Quorum rule cannot be satisfied by the resolved member count "
                            + memberCount
            ));
        }
    }

    private ResolvedApprovalBranchRoute resolveBranch(
            FlowSession session,
            ApprovalApproverSources sources,
            ApprovalBranchRoute branch,
            ApprovalInstance.RecordBinding binding,
            RuntimeRecordFlowFacade.BindingSource bindingSource,
            Map<String, String> valuesJson
    ) {
        var source = sources.branch(branch.code());
        return ResolvedApprovalBranchRoute.from(
                branch,
                approverSources.requireMembers(
                        session.systemId(),
                        session.tenantId(),
                        source,
                        branch.approverIds(),
                        session.memberId(),
                        recordContext(
                                session, source, binding, bindingSource, valuesJson)
                )
        );
    }

    private java.util.List<Long> requiredRouteMembers(
            FlowSession session,
            ApprovalApproverSources sources,
            String branchCode,
            java.util.List<Long> fixedMemberIds,
            ApprovalInstance.RecordBinding binding,
            RuntimeRecordFlowFacade.BindingSource bindingSource,
            Map<String, String> valuesJson
    ) {
        var source = branchCode == null
                ? sources.route()
                : sources.branch(branchCode);
        return approverSources.requireMembers(
                session.systemId(),
                session.tenantId(),
                source,
                fixedMemberIds,
                session.memberId(),
                recordContext(session, source, binding, bindingSource, valuesJson)
        );
    }

    private java.util.List<Long> draftRouteMembers(
            FlowSession session,
            ApprovalApproverSource source
    ) {
        if (source.kind() == ApprovalApproverSource.Kind.REQUESTER
                || source.kind()
                        == ApprovalApproverSource.Kind.REQUESTER_MANAGER
                || source.kind()
                        == ApprovalApproverSource.Kind
                                .REQUESTER_DEPARTMENT_LEADER
                || source.kind()
                        == ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD) {
            // Draft route records require a non-empty structural mirror. It is
            // not an instance participant snapshot and is replaced by
            // contextual resolution during simulation/start.
            return java.util.List.of(session.memberId());
        }
        return approverSources.requireMembers(
                session.systemId(),
                session.tenantId(),
                source,
                java.util.List.of(),
                session.memberId()
        );
    }

    private java.util.List<Long> stageActivationMembers(
            FlowSession session,
            ApprovalStage nextStage,
            ApprovalStageExecution completedStage,
            ApprovalStartContext startContext
    ) {
        var source = nextStage.approverSource();
        var memberIds = source.kind()
                        == ApprovalApproverSource.Kind.PREVIOUS_HANDLER
                ? completedStage.actualHandlerIds()
                : approverSources.requireMembers(
                        session.systemId(),
                        session.tenantId(),
                        source,
                        nextStage.approverIds(),
                        startContext == null
                                ? null
                                : startContext.requesterMemberId(),
                        stageRecordContext(source, startContext)
                );
        if (memberIds == null || memberIds.isEmpty()) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_EMPTY",
                    "The next approval stage resolved no human handler",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        for (var memberId : memberIds) {
            if (!active(session, memberId)) {
                throw new BusinessException(
                        "FLOW_APPROVER_SOURCE_INACTIVE",
                        "Next-stage approver " + memberId
                                + " is inactive in the current system and tenant",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }
        }
        return List.copyOf(memberIds);
    }

    private static ApprovalApproverSourceResolver.RecordContext
            stageRecordContext(
                    ApprovalApproverSource source,
                    ApprovalStartContext startContext
            ) {
        if (source.kind()
                != ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD) {
            return null;
        }
        if (startContext == null
                || !startContext.hasRecord()
                || !source.moduleCode().equals(startContext.moduleCode())) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_RECORD_CONTEXT_REQUIRED",
                    "The immutable start context does not match the "
                            + "RECORD_MEMBER_FIELD source",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        return ApprovalApproverSourceResolver.RecordContext.snapshot(
                startContext.moduleCode(), startContext.valuesJson());
    }

    private java.util.List<Long> simulationRouteMembers(
            FlowSession session,
            ApprovalApproverSources sources,
            String branchCode,
            java.util.List<Long> fixedMemberIds,
            long requesterMemberId,
            Map<String, String> valuesJson
    ) {
        var source = branchCode == null
                ? sources.route()
                : sources.branch(branchCode);
        var resolution = approverSources.resolve(
                session.systemId(),
                session.tenantId(),
                source,
                fixedMemberIds,
                requesterMemberId,
                source.kind() == ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD
                        ? ApprovalApproverSourceResolver.RecordContext.snapshot(
                                source.moduleCode(), valuesJson)
                        : null
        );
        return resolution.sourceActive()
                && !resolution.memberIds().isEmpty()
                && resolution.memberIds().size() <= 10
                ? resolution.memberIds()
                : java.util.List.of();
    }

    private ApprovalApproverSourceResolver.RecordContext recordContext(
            FlowSession session,
            ApprovalApproverSource source,
            ApprovalInstance.RecordBinding binding,
            RuntimeRecordFlowFacade.BindingSource bindingSource,
            Map<String, String> valuesJson
    ) {
        if (source.kind() != ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD) {
            return null;
        }
        if (binding == null) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_RECORD_CONTEXT_REQUIRED",
                    "RECORD_MEMBER_FIELD approver source requires recordBinding",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        if (!source.moduleCode().equals(binding.moduleCode())) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_RECORD_MODULE_MISMATCH",
                    "recordBinding module must match the RECORD_MEMBER_FIELD source module",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        if (bindingSource == RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT) {
            return ApprovalApproverSourceResolver.RecordContext.snapshot(
                    binding.moduleCode(), valuesJson);
        }
        recordAccess.requireView(new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                session.systemId(),
                session.tenantId(),
                session.memberId(),
                session.permissions(),
                binding.moduleCode(),
                binding.recordId()
        ));
        return ApprovalApproverSourceResolver.RecordContext.current(
                binding.moduleCode(), binding.recordId());
    }

    private static boolean containsRecordMemberField(ApprovalApproverSources sources) {
        return sources.route().kind()
                        == ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD
                || sources.branches().values().stream().anyMatch(
                        source -> source.kind()
                                == ApprovalApproverSource.Kind.RECORD_MEMBER_FIELD);
    }

    private boolean active(FlowSession session, long memberId) {
        return activeMembers.lockActiveMember(
                session.systemId(),
                session.tenantId(),
                memberId
        ).filter(member -> member.memberId() == memberId).isPresent();
    }

    private static boolean canManageDelegations(FlowSession session) {
        return session.permissions().contains(FlowPermissions.DELEGATION_MANAGE);
    }

    private static long delegationMemberId(
            String value,
            Long defaultValue,
            String field
    ) {
        if ((value == null || value.isBlank()) && defaultValue != null) {
            return defaultValue;
        }
        var parsed = optionalDelegationId(value, field);
        if (parsed == null) {
            throw delegationInvalid(field + " is required");
        }
        return parsed;
    }

    private static Long optionalDelegationId(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw delegationInvalid(field + " must be a positive id string");
        }
    }

    private static Instant delegationInstant(String value, String field) {
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException();
            }
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw delegationInvalid(field + " must be an ISO-8601 instant");
        }
    }

    private static Long representedMemberId(String value) {
        return optionalDelegationId(value, "representedMemberId");
    }

    private static ApprovalDomainException delegationInvalid(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.DELEGATION_RULE_INVALID,
                message
        );
    }

    private long simulationRequester(FlowSession session, String value) {
        final long requesterId;
        try {
            requesterId = value == null || value.isBlank()
                    ? session.memberId()
                    : Long.parseLong(value);
            if (requesterId <= 0) {
                throw new NumberFormatException();
            }
        } catch (RuntimeException exception) {
            throw simulationInvalid("requesterId must be a positive member-id string");
        }
        if (!active(session, requesterId)) {
            throw simulationInvalid(
                    "Simulation requester must be active in the current system and tenant");
        }
        return requesterId;
    }

    private static String simulationBusinessKey(long definitionId, int revision, String value) {
        var businessKey = value == null || value.isBlank()
                ? "simulation:" + definitionId + ":r" + revision
                : value.strip();
        if (businessKey.length() > 160) {
            throw simulationInvalid("businessKey must contain 1 to 160 characters");
        }
        return businessKey;
    }

    private FlowDraftPreflight.TriggerResult simulationTrigger(
            TriggerBinding binding,
            FlowRequests.TriggerSample sample
    ) {
        if (binding == null) {
            return new FlowDraftPreflight.TriggerResult(
                    false,
                    null,
                    null,
                    sample == null,
                    sample == null ? "MANUAL_START" : "NO_TRIGGER_CONFIGURED"
            );
        }
        if (binding.event() == TriggerBinding.Event.PERIODIC) {
            var matched = sample == null || periodicSample(sample);
            return new FlowDraftPreflight.TriggerResult(
                    true,
                    binding.event().name(),
                    null,
                    matched,
                    matched ? "PERIODIC_SCHEDULE" : "TRIGGER_EVENT_MISMATCH"
            );
        }
        if (sample == null) {
            return new FlowDraftPreflight.TriggerResult(
                    true,
                    binding.event().name(),
                    binding.moduleCode(),
                    false,
                    "TRIGGER_SAMPLE_REQUIRED"
            );
        }
        var sampleEvent = simulationEvent(sample.event());
        if (!binding.moduleCode().equals(sample.moduleCode())
                || binding.event() != sampleEvent) {
            return new FlowDraftPreflight.TriggerResult(
                    true,
                    binding.event().name(),
                    binding.moduleCode(),
                    false,
                    "TRIGGER_EVENT_MISMATCH"
            );
        }
        var values = simulationValues(sample.values());
        var matched = new TriggerConditionMatcher(objectMapper).matches(binding, values);
        return new FlowDraftPreflight.TriggerResult(
                true,
                binding.event().name(),
                binding.moduleCode(),
                matched,
                matched ? "TRIGGER_MATCHED" : "TRIGGER_CONDITIONS_NOT_MATCHED"
        );
    }

    private static boolean periodicSample(FlowRequests.TriggerSample sample) {
        return sample.moduleCode() == null
                && "PERIODIC".equals(sample.event())
                && sample.values().isEmpty();
    }

    private static TriggerBinding.Event simulationEvent(String value) {
        try {
            return TriggerBinding.Event.valueOf(value);
        } catch (RuntimeException exception) {
            throw simulationInvalid("trigger.event must be a supported Flow event");
        }
    }

    private static Map<String, String> simulationValues(
            Map<String, com.fasterxml.jackson.databind.JsonNode> values
    ) {
        if (values == null || values.size() > 100) {
            throw simulationInvalid("trigger.values accepts at most 100 fields");
        }
        var result = new java.util.LinkedHashMap<String, String>();
        values.forEach((field, value) -> {
            if (field == null
                    || !field.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                    || value == null) {
                throw simulationInvalid(
                        "trigger.values requires canonical field codes and JSON values");
            }
            result.put(field, value.toString());
        });
        return Map.copyOf(result);
    }

    private static Map<String, String> simulationRouteValues(
            FlowRequests.SimulateDefinition request
    ) {
        return request == null || request.trigger() == null
                ? request == null
                        ? Map.of()
                        : simulationValues(request.values())
                : simulationValues(request.values().isEmpty()
                        ? request.trigger().values()
                        : request.values());
    }

    private static FlowDraftPreflight.RouteResult routeResult(
            boolean configured,
            ResolvedRoute route,
            java.util.List<Long> routeMembers,
            com.unique.examine.flow.domain.ApprovalQuorumRule quorumRule,
            com.unique.examine.flow.domain.ApprovalDeadlinePolicy deadlinePolicy,
            com.unique.examine.flow.domain.ApprovalDecisionCommentPolicy
                    decisionCommentPolicy
    ) {
        return new FlowDraftPreflight.RouteResult(
                configured,
                route.branchCode(),
                route.branchName(),
                route.defaultBranch(),
                route.approvalMode(),
                simulationRequiredApprovals(
                        route.approvalMode(),
                        quorumRule,
                        routeMembers.size()
                ),
                route.approvalMode() == com.unique.examine.flow.domain.ApprovalMode.SEQUENTIAL
                        ? routeMembers.stream().findFirst().stream().toList()
                        : routeMembers,
                deadlinePolicy,
                decisionCommentPolicy
        );
    }

    private static int simulationRequiredApprovals(
            com.unique.examine.flow.domain.ApprovalMode mode,
            com.unique.examine.flow.domain.ApprovalQuorumRule rule,
            int memberCount
    ) {
        if (memberCount == 0) {
            return 0;
        }
        try {
            return com.unique.examine.flow.domain.ApprovalQuorumRules.requiredApprovals(
                    mode,
                    rule,
                    memberCount
            );
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private static Map<String, String> startValues(
            Map<String, com.fasterxml.jackson.databind.JsonNode> values
    ) {
        if (values == null || values.size() > 100) {
            throw routeValuesInvalid();
        }
        var result = new java.util.LinkedHashMap<String, String>();
        values.forEach((field, value) -> {
            if (field == null
                    || !field.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                    || value == null) {
                throw routeValuesInvalid();
            }
            result.put(field, value.toString());
        });
        return Map.copyOf(result);
    }

    private static BusinessException routeValuesInvalid() {
        return new BusinessException(
                "FLOW_ROUTE_VALUES_INVALID",
                "values accepts at most 100 canonical field codes with JSON values",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private static BusinessException simulationInvalid(String message) {
        return new BusinessException(
                "FLOW_SIMULATION_REQUEST_INVALID",
                message,
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private FlowViews.Instance replay(IdempotencyRecord record, String requestHash, String actionName) {
        if (!requestHash.equals(record.requestHash())) {
            throw conflict(
                    "IDEMPOTENCY_CONFLICT",
                    "The same idempotency key cannot be reused for a different " + actionName + " request");
        }
        if (!"COMPLETED".equals(record.status()) || record.responseBody() == null) {
            throw conflict("REQUEST_IN_PROGRESS", "The " + actionName + " request is already being processed");
        }
        return read(record.responseBody());
    }

    private void projectTerminal(FlowSession session, ApprovalInstance instance) {
        if (instance.recordBinding() == null || instance.status() == ApprovalInstance.Status.PENDING) {
            return;
        }
        recordFlows.transition(new RuntimeRecordFlowFacade.TransitionRequest(
                session.systemId(),
                session.tenantId(),
                instance.id(),
                RuntimeRecordFlowFacade.FlowStatus.valueOf(instance.status().name()),
                session.memberId(),
                Objects.requireNonNull(instance.completedAt(), "terminal completion time")
        ));
    }

    private static ApprovalInstance.RecordBinding recordBinding(FlowRequests.RecordBinding value) {
        if (value == null) {
            return null;
        }
        var moduleCode = value.moduleCode();
        var recordId = value.recordId();
        if (moduleCode == null
                || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                || recordId == null
                || !recordId.matches("^[1-9][0-9]{0,18}$")) {
            throw invalidRecordBinding();
        }
        try {
            return new ApprovalInstance.RecordBinding(moduleCode, Long.parseLong(recordId));
        } catch (RuntimeException exception) {
            throw invalidRecordBinding();
        }
    }

    private static RuntimeRecordFlowFacade.RecordStatusMapping recordStatusMapping(
            com.unique.examine.flow.domain.RecordStatusMapping value
    ) {
        return value == null
                ? null
                : new RuntimeRecordFlowFacade.RecordStatusMapping(
                        value.fieldCode(),
                        value.approvedValue(),
                        value.rejectedValue(),
                        value.withdrawnValue(),
                        value.terminatedValue()
                );
    }

    private static BusinessException invalidRecordBinding() {
        return new BusinessException(
                "FLOW_RECORD_BINDING_INVALID",
                "recordBinding must contain a canonical moduleCode and positive recordId string",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private static RuntimeRecordFlowFacade unsupportedRecordFlows() {
        return new RuntimeRecordFlowFacade() {
            @Override
            public RecordFlowState bind(BindRequest request) {
                throw new IllegalStateException("Runtime record Flow binding is unavailable");
            }

            @Override
            public RecordFlowState bindAdditional(AdditionalBindRequest request) {
                throw new IllegalStateException("Runtime additional record Flow binding is unavailable");
            }

            @Override
            public RecordFlowState transition(TransitionRequest request) {
                throw new IllegalStateException("Runtime record Flow projection is unavailable");
            }
        };
    }

    private static RuntimeRecordMemberFieldFacade unsupportedRecordMemberFields() {
        return new RuntimeRecordMemberFieldFacade() {
            @Override
            public java.util.Optional<PublishedFieldCatalog> publishedEligibleFields(
                    long systemId,
                    String moduleCode
            ) {
                return java.util.Optional.empty();
            }

            @Override
            public Resolution resolveCurrent(CurrentRecordRequest request) {
                return Resolution.sourceMissing();
            }

            @Override
            public Resolution resolveSnapshot(SnapshotRequest request) {
                return Resolution.sourceMissing();
            }
        };
    }

    private static RuntimeRecordAccessFacade unsupportedRecordAccess() {
        return request -> {
            throw new IllegalStateException("Runtime record access is unavailable");
        };
    }

    private static ApprovalEvidenceFileFacade unsupportedEvidenceFiles() {
        return new ApprovalEvidenceFileFacade() {
            @Override
            public List<FileMetadata> validateSelection(
                    EvidenceActor actor,
                    List<Long> fileIds
            ) {
                if (fileIds == null || fileIds.isEmpty()) {
                    return List.of();
                }
                throw new IllegalStateException(
                        "Approval evidence file bridge is unavailable");
            }

            @Override
            public void attachEvidence(
                    EvidenceActor actor,
                    long evidenceId,
                    List<Long> fileIds
            ) {
                if (fileIds != null && !fileIds.isEmpty()) {
                    throw new IllegalStateException(
                            "Approval evidence file bridge is unavailable");
                }
            }
        };
    }

    private static RuntimeApproverDirectoryFacade unsupportedApproverDirectory() {
        return new RuntimeApproverDirectoryFacade() {
            @Override
            public Resolution resolveRoleMembers(long systemId, long tenantId, long roleId) {
                return Resolution.missing();
            }

            @Override
            public Resolution resolveDepartmentMembers(
                    long systemId,
                    long tenantId,
                    long departmentId
            ) {
                return Resolution.missing();
            }
        };
    }

    private long activeTarget(FlowSession session, String value) {
        final long targetMemberId;
        try {
            targetMemberId = Long.parseLong(value);
            if (targetMemberId <= 0) {
                throw new NumberFormatException();
            }
        } catch (RuntimeException exception) {
            throw assignmentInvalid("targetMemberId must be a positive member-id string");
        }
        var active = activeMembers.lockActiveMember(
                session.systemId(),
                session.tenantId(),
                targetMemberId
        ).filter(member -> member.memberId() == targetMemberId);
        if (active.isEmpty()) {
            throw assignmentInvalid(
                    "Assignment target must be an active member in the current system and tenant"
            );
        }
        return targetMemberId;
    }

    private static ApprovalHistoryEvent.AssignmentPosition assignmentPosition(String value) {
        try {
            return ApprovalHistoryEvent.AssignmentPosition.valueOf(value);
        } catch (RuntimeException exception) {
            throw assignmentInvalid("position must be exactly BEFORE or AFTER");
        }
    }

    private static ApprovalDomainException assignmentInvalid(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.ASSIGNMENT_REQUEST_INVALID,
                message
        );
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Flow mutation payload must be JSON serializable", exception);
        }
    }

    private FlowViews.Instance read(String value) {
        try {
            return objectMapper.readValue(value, FlowViews.Instance.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read idempotent Flow mutation response", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

}
