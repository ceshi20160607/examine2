package com.unique.examine.flow.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.interaction.FlowInteractionMutationService;
import com.unique.examine.flow.interaction.FlowInteractionServiceFactory;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowMutationService;
import com.unique.examine.flow.service.FlowRequestServiceFactory;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/flow")
public class FlowController {
    private final FlowRequestServiceFactory services;
    private final FlowMutationService mutations;
    private final FlowInteractionServiceFactory interactions;
    private final FlowInteractionMutationService interactionMutations;
    private com.unique.examine.flow.service
            .FlowCompletionExecutionService completions;
    private com.unique.examine.flow.service
            .FlowCompensationRuntimeService compensations;

    public FlowController(
            FlowRequestServiceFactory services,
            FlowMutationService mutations,
            FlowInteractionServiceFactory interactions,
            FlowInteractionMutationService interactionMutations
    ) {
        this.services = services;
        this.mutations = mutations;
        this.interactions = interactions;
        this.interactionMutations = interactionMutations;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void configureCompletionExecutions(
            com.unique.examine.flow.service.FlowCompletionExecutionService
                    completions
    ) {
        this.completions = completions;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void configureCompensationExecutions(
            com.unique.examine.flow.service.FlowCompensationRuntimeService
                    compensations
    ) {
        this.compensations = compensations;
    }

    @GetMapping("/startable-definitions")
    public ApiResponse<FlowViews.StartableDefinitionPage> startableDefinitions(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_START);
        var response = FlowHttpErrors.execute(() ->
                services.forTenant(systemId, session.tenantId()).startableDefinitions(page, size));
        return ok(FlowViews.StartableDefinitionPage.from(response), request);
    }

    @GetMapping("/definitions")
    public ApiResponse<FlowViews.DefinitionPage> definitions(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() ->
                services.forTenant(systemId, session.tenantId()).definitions(page, size));
        return ok(FlowViews.DefinitionPage.from(response), request);
    }

    @GetMapping("/approver-sources/record-member-fields")
    public ApiResponse<FlowViews.RecordMemberFieldSourceCatalog> recordMemberFields(
            @PathVariable long systemId,
            @RequestParam String moduleCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(
                () -> mutations.recordMemberFieldCatalog(session, moduleCode));
        return ok(response, request);
    }

    @GetMapping("/decision-comment-templates")
    public ApiResponse<FlowViews.DecisionCommentTemplatePage>
            decisionCommentTemplates(
                    @PathVariable long systemId,
                    @RequestParam(defaultValue = "1") int page,
                    @RequestParam(defaultValue = "20") int size,
                    @RequestAttribute(
                            value = RequestSession.REQUEST_ATTRIBUTE,
                            required = false
                    ) Object value,
                    HttpServletRequest request
            ) {
        var session = FlowSession.requireAny(
                value,
                systemId,
                FlowPermissions.DEFINITION_MANAGE,
                FlowPermissions.INSTANCE_DECIDE
        );
        var activeOnly = !session.permissions()
                .contains(FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() ->
                mutations.decisionCommentTemplates(
                        session, activeOnly, page, size));
        return ok(response, request);
    }

    @PostMapping("/decision-comment-templates")
    public ResponseEntity<ApiResponse<FlowViews.DecisionCommentTemplate>>
            createDecisionCommentTemplate(
                    @PathVariable long systemId,
                    @RequestBody
                            FlowRequests.CreateDecisionCommentTemplate body,
                    @RequestAttribute(
                            value = RequestSession.REQUEST_ATTRIBUTE,
                            required = false
                    ) Object value,
                    HttpServletRequest request
            ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() ->
                mutations.createDecisionCommentTemplate(session, body));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ok(response, request));
    }

    @PutMapping("/decision-comment-templates/{templateId}")
    public ApiResponse<FlowViews.DecisionCommentTemplate>
            reviseDecisionCommentTemplate(
                    @PathVariable long systemId,
                    @PathVariable long templateId,
                    @RequestBody
                            FlowRequests.ReviseDecisionCommentTemplate body,
                    @RequestAttribute(
                            value = RequestSession.REQUEST_ATTRIBUTE,
                            required = false
                    ) Object value,
                    HttpServletRequest request
            ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() ->
                mutations.reviseDecisionCommentTemplate(
                        session, templateId, body));
        return ok(response, request);
    }

    @PostMapping("/decision-comment-templates/{templateId}:activate")
    public ApiResponse<FlowViews.DecisionCommentTemplate>
            activateDecisionCommentTemplate(
                    @PathVariable long systemId,
                    @PathVariable long templateId,
                    @RequestAttribute(
                            value = RequestSession.REQUEST_ATTRIBUTE,
                            required = false
                    ) Object value,
                    HttpServletRequest request
            ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() ->
                mutations.activateDecisionCommentTemplate(
                        session, templateId));
        return ok(response, request);
    }

    @PostMapping("/decision-comment-templates/{templateId}:deactivate")
    public ApiResponse<FlowViews.DecisionCommentTemplate>
            deactivateDecisionCommentTemplate(
                    @PathVariable long systemId,
                    @PathVariable long templateId,
                    @RequestAttribute(
                            value = RequestSession.REQUEST_ATTRIBUTE,
                            required = false
                    ) Object value,
                    HttpServletRequest request
            ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() ->
                mutations.deactivateDecisionCommentTemplate(
                        session, templateId));
        return ok(response, request);
    }

    @PostMapping("/definitions")
    public ResponseEntity<ApiResponse<FlowViews.DefinitionDraft>> createDefinition(
            @PathVariable long systemId,
            @RequestBody FlowRequests.CreateDefinition body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
        HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() -> mutations.createDefinition(session, body));
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(response, request));
    }

    @PutMapping("/definitions/{definitionId}/draft")
    public ApiResponse<FlowViews.DefinitionDraft> reviseDefinition(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestBody FlowRequests.ReviseDefinition body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
        HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(
                () -> mutations.reviseDefinition(session, definitionId, body));
        return ok(response, request);
    }

    @PostMapping("/definitions/{definitionId}:publish")
    public ApiResponse<FlowViews.DefinitionVersion> publishDefinition(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() -> mutations.publish(session, definitionId));
        return ok(response, request);
    }

    @PostMapping("/definitions/{definitionId}/draft:check")
    public ApiResponse<FlowViews.DraftCheck> checkDefinitionDraft(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() -> mutations.checkDraft(session, definitionId));
        return ok(response, request);
    }

    @PostMapping("/definitions/{definitionId}/draft:simulate")
    public ApiResponse<FlowViews.DraftSimulation> simulateDefinitionDraft(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestBody(required = false) FlowRequests.SimulateDefinition body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(
                () -> mutations.simulateDraft(session, definitionId, body));
        return ok(response, request);
    }

    @GetMapping("/definitions/{definitionId}/periodic-schedule")
    public ApiResponse<FlowViews.PeriodicScheduleState> periodicSchedule(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() ->
                services.forTenant(systemId, session.tenantId()).periodicSchedule(definitionId));
        return ok(FlowViews.PeriodicScheduleState.from(response), request);
    }

    @GetMapping("/definitions/{definitionId}/versions")
    public ApiResponse<FlowViews.DefinitionVersionPage> definitionVersions(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() -> services
                .forTenant(systemId, session.tenantId())
                .definitionVersions(definitionId, page, size));
        return ok(FlowViews.DefinitionVersionPage.from(response), request);
    }

    @PostMapping("/definitions/{definitionId}/versions/{version}:restore")
    public ApiResponse<FlowViews.DefinitionDraft> restoreDefinitionVersion(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @PathVariable int version,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        var response = FlowHttpErrors.execute(() -> services
                .forTenant(systemId, session.tenantId())
                .restoreVersion(definitionId, version, session.memberId()));
        return ok(FlowViews.DefinitionDraft.from(response), request);
    }

    @PostMapping("/definitions/{definitionId}/instances")
    public ResponseEntity<ApiResponse<FlowViews.Instance>> startInstance(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestBody FlowRequests.StartInstance body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_START);
        var response = FlowHttpErrors.execute(() -> mutations.start(
                session,
                definitionId,
                body,
                idempotencyKey,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(response, request));
    }

    @PostMapping("/instances/{instanceId}:approve")
    public ApiResponse<FlowViews.Instance> approve(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Decision body,
            @RequestHeader(name = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_DECIDE);
        var response = FlowHttpErrors.execute(
                () -> mutations.approve(
                        session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:reject")
    public ApiResponse<FlowViews.Instance> reject(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Rejection body,
            @RequestHeader(name = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_DECIDE);
        var response = FlowHttpErrors.execute(() ->
                mutations.reject(
                        session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}/branches/{branchCode}:approve")
    public ApiResponse<FlowViews.Instance> approveBranch(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable String branchCode,
            @RequestBody FlowRequests.Decision body,
            @RequestHeader(name = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_DECIDE);
        var response = FlowHttpErrors.execute(
                () -> mutations.approveBranch(
                        session, instanceId, branchCode, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}/branches/{branchCode}:reject")
    public ApiResponse<FlowViews.Instance> rejectBranch(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable String branchCode,
            @RequestBody FlowRequests.Rejection body,
            @RequestHeader(name = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_DECIDE);
        var response = FlowHttpErrors.execute(
                () -> mutations.rejectBranch(
                        session, instanceId, branchCode, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:withdraw")
    public ApiResponse<FlowViews.Instance> withdraw(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Withdrawal body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_WITHDRAW);
        var response = FlowHttpErrors.execute(
                () -> mutations.withdraw(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:terminate")
    public ApiResponse<FlowViews.Instance> terminate(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Termination body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_TERMINATE);
        var response = FlowHttpErrors.execute(
                () -> mutations.terminate(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:transfer")
    public ApiResponse<FlowViews.Instance> transfer(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Transfer body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_TRANSFER);
        var response = FlowHttpErrors.execute(
                () -> mutations.transfer(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:add-sign")
    public ApiResponse<FlowViews.Instance> addSign(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.AddSign body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_ADD_SIGN);
        var response = FlowHttpErrors.execute(
                () -> mutations.addSign(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:return")
    public ApiResponse<FlowViews.Instance> returnToPrevious(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Return body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_RETURN);
        var response = FlowHttpErrors.execute(
                () -> mutations.returnToPrevious(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:cancel-claim")
    public ApiResponse<FlowViews.Instance> cancelClaim(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.CancelClaim body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_CANCEL_CLAIM);
        var response = FlowHttpErrors.execute(
                () -> mutations.cancelClaim(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:claim")
    public ApiResponse<FlowViews.Instance> claim(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Claim body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_CLAIM);
        var response = FlowHttpErrors.execute(
                () -> mutations.claim(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:reduce-sign")
    public ApiResponse<FlowViews.Instance> reduceSign(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.ReduceSign body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_REDUCE_SIGN);
        var response = FlowHttpErrors.execute(
                () -> mutations.reduceSign(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @PostMapping("/instances/{instanceId}:urge")
    public ApiResponse<FlowViews.Urge> urge(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Urge body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_URGE);
        var response = FlowHttpErrors.execute(
                () -> interactionMutations.urge(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @GetMapping("/instances/{instanceId}/urges")
    public ApiResponse<FlowViews.UrgePage> urges(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_READ);
        var response = FlowHttpErrors.execute(() ->
                interactions.forTenant(systemId, session.tenantId()).urges(instanceId, page, size));
        return ok(FlowViews.UrgePage.from(response), request);
    }

    @PostMapping("/instances/{instanceId}/comments")
    public ApiResponse<FlowViews.Comment> comment(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Comment body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_COMMENT);
        var response = FlowHttpErrors.execute(
                () -> interactionMutations.comment(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @GetMapping("/instances/{instanceId}/comments")
    public ApiResponse<FlowViews.CommentPage> comments(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_READ);
        var response = FlowHttpErrors.execute(() ->
                interactions.forTenant(systemId, session.tenantId()).comments(instanceId, page, size));
        return ok(FlowViews.CommentPage.from(response), request);
    }

    @PostMapping("/instances/{instanceId}/copies")
    public ApiResponse<FlowViews.Copy> copy(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestBody FlowRequests.Copy body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_COPY);
        var response = FlowHttpErrors.execute(
                () -> interactionMutations.copy(session, instanceId, body, idempotencyKey));
        return ok(response, request);
    }

    @GetMapping("/instances/{instanceId}/copies")
    public ApiResponse<FlowViews.CopyPage> copies(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_READ);
        var response = FlowHttpErrors.execute(() ->
                interactions.forTenant(systemId, session.tenantId())
                        .copies(instanceId, page, size));
        return ok(FlowViews.CopyPage.from(response), request);
    }

    @PostMapping("/delegations")
    public ResponseEntity<ApiResponse<FlowViews.DelegationRule>> createDelegation(
            @PathVariable long systemId,
            @RequestBody FlowRequests.CreateDelegation body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request
    ) {
        var session = delegationSession(value, systemId);
        var response = FlowHttpErrors.execute(() ->
                mutations.createDelegation(session, body));
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(response, request));
    }

    @GetMapping("/delegations")
    public ApiResponse<FlowViews.DelegationRulePage> delegations(
            @PathVariable long systemId,
            @RequestParam(required = false) String delegatorMemberId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request
    ) {
        var session = delegationSession(value, systemId);
        var response = FlowHttpErrors.execute(() ->
                mutations.delegations(
                        session, delegatorMemberId, page, size));
        return ok(response, request);
    }

    @PostMapping("/delegations/{delegationRuleId}/revoke")
    public ApiResponse<FlowViews.DelegationRule> revokeDelegation(
            @PathVariable long systemId,
            @PathVariable long delegationRuleId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request
    ) {
        var session = delegationSession(value, systemId);
        var response = FlowHttpErrors.execute(() ->
                mutations.revokeDelegation(session, delegationRuleId));
        return ok(response, request);
    }

    @GetMapping("/instances")
    public ApiResponse<FlowViews.InstancePage> instances(
            @PathVariable long systemId,
            @RequestParam(required = false) ApprovalInstance.Status status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_READ);
        var response = FlowHttpErrors.execute(() ->
                services.forTenant(systemId, session.tenantId()).instances(
                        status,
                        from == null ? null
                                : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                        to == null ? null
                                : to.atStartOfDay(ZoneOffset.UTC).toInstant(),
                        page,
                        size));
        return ok(FlowViews.InstancePage.from(response), request);
    }

    @GetMapping("/tasks")
    public ApiResponse<FlowViews.TaskPage> approvalTasks(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "PENDING") ApprovalTaskStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_READ);
        var response = FlowHttpErrors.execute(() -> services.forTenant(systemId, session.tenantId())
                .approvalTaskAssignments(session.memberId(), status, page, size));
        return ok(FlowViews.TaskPage.from(response), request);
    }

    @GetMapping("/claimable-tasks")
    public ApiResponse<FlowViews.InstancePage> claimableTasks(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_CLAIM);
        var response = FlowHttpErrors.execute(() ->
                services.forTenant(systemId, session.tenantId()).claimableTasks(page, size));
        return ok(FlowViews.InstancePage.from(response), request);
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResponse<FlowViews.Instance> instance(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_READ);
        var response = FlowHttpErrors.execute(() ->
                services.forTenant(systemId, session.tenantId()).instance(instanceId));
        var executionViews = completions == null
                ? java.util.List.<FlowViews.CompletionExecution>of()
                : FlowHttpErrors.execute(() ->
                        completions.executions(session, instanceId));
        var compensationViews = compensations == null
                ? java.util.List.<FlowViews.CompensationExecution>of()
                : FlowHttpErrors.execute(() ->
                        compensations.executions(session, instanceId));
        return ok(FlowViews.Instance.from(
                response, executionViews, compensationViews), request);
    }

    @GetMapping("/instances/{instanceId}/history")
    public ApiResponse<FlowViews.History> history(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(value, systemId, FlowPermissions.INSTANCE_READ);
        var history = FlowHttpErrors.execute(() ->
                services.forTenant(systemId, session.tenantId()).history(instanceId));
        var completionHistory = completions == null
                ? java.util.List.<FlowViews.CompletionHistory>of()
                : FlowHttpErrors.execute(() ->
                        completions.completionHistory(session, instanceId));
        var compensationHistory = compensations == null
                ? java.util.List.<FlowViews.CompensationHistory>of()
                : FlowHttpErrors.execute(() ->
                        compensations.history(session, instanceId));
        return ok(FlowViews.History.from(
                instanceId, history, completionHistory,
                compensationHistory), request);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(
                data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)
        );
    }

    private static String attribute(HttpServletRequest request, String name) {
        return String.valueOf(request.getAttribute(name));
    }

    private static FlowSession delegationSession(
            Object value,
            long requestedSystemId
    ) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException(
                    "AUTH_REQUIRED",
                    "Authentication is required",
                    HttpStatus.UNAUTHORIZED
            );
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null
                || session.systemId() != requestedSystemId
                || session.tenantId() == null
                || session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH",
                    "The authenticated system member context does not match the request",
                    HttpStatus.FORBIDDEN
            );
        }
        return new FlowSession(
                session.accountId(),
                requestedSystemId,
                session.tenantId(),
                session.memberId(),
                session.permissions()
        );
    }
}
