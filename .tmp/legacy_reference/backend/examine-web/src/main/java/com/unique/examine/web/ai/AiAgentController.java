package com.unique.examine.web.ai;

import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiAgentFacade;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/ai")
public final class AiAgentController {
    private final AiAgentFacade ai;

    public AiAgentController(AiAgentFacade ai) {
        this.ai = Objects.requireNonNull(ai, "ai");
    }

    @GetMapping("/capability")
    public ApiResponse<AiAgentFacade.CapabilityView> capability(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.capability(actor(session, systemId, request)), request);
    }

    @GetMapping("/sessions")
    public ApiResponse<AiAgentFacade.SessionPage> sessions(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.sessions(actor(session, systemId, request), page, size), request);
    }

    @PostMapping("/sessions")
    public ApiResponse<AiAgentFacade.SessionView> createSession(
            @PathVariable long systemId,
            @RequestBody CreateSessionBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.createSession(
                actor(session, systemId, request),
                new AiAgentFacade.CreateSession(body == null ? null : body.title())),
                request);
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<AiAgentFacade.DetailView> detail(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.detail(
                actor(session, systemId, request), sessionId), request);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<AiAgentFacade.TurnView> submit(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @RequestBody SubmitMessageBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.submit(
                actor(session, systemId, request), sessionId,
                new AiAgentFacade.SubmitMessage(
                        body == null ? null : body.content())), request);
    }

    @GetMapping("/sessions/{sessionId}/configuration-proposals/{proposalId}")
    public ApiResponse<AiAgentFacade.ConfigurationProposalView>
    configurationProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.configurationProposal(
                actor(session, systemId, request), sessionId, proposalId), request);
    }

    @PostMapping("/sessions/{sessionId}/configuration-proposals/{proposalId}/confirm")
    public ApiResponse<AiAgentFacade.ConfigurationProposalView>
    confirmConfigurationProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ConfigurationProposalBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfigurationProposalBody(body);
        return success(ai.confirmConfigurationField(
                actor(session, systemId, request), sessionId, proposalId,
                body.expectedRevision(), idempotencyKey), request);
    }

    @PostMapping("/sessions/{sessionId}/configuration-proposals/{proposalId}/reject")
    public ApiResponse<AiAgentFacade.ConfigurationProposalView>
    rejectConfigurationProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestBody ConfigurationProposalBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfigurationProposalBody(body);
        return success(ai.rejectConfigurationField(
                actor(session, systemId, request), sessionId, proposalId,
                body.expectedRevision()), request);
    }

    @GetMapping("/sessions/{sessionId}/configuration-artifact-proposals/{proposalId}")
    public ApiResponse<AiAgentFacade.ArtifactProposalView> artifactProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.artifactProposal(
                actor(session, systemId, request), sessionId, proposalId), request);
    }

    @PostMapping("/sessions/{sessionId}/configuration-artifact-proposals/{proposalId}/confirm")
    public ApiResponse<AiAgentFacade.ArtifactProposalView> confirmArtifactProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ConfigurationProposalBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfigurationProposalBody(body);
        return success(ai.confirmArtifact(
                actor(session, systemId, request), sessionId, proposalId,
                body.expectedRevision(), idempotencyKey), request);
    }

    @PostMapping("/sessions/{sessionId}/configuration-artifact-proposals/{proposalId}/reject")
    public ApiResponse<AiAgentFacade.ArtifactProposalView> rejectArtifactProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestBody ConfigurationProposalBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfigurationProposalBody(body);
        return success(ai.rejectArtifact(
                actor(session, systemId, request), sessionId, proposalId,
                body.expectedRevision()), request);
    }

    @GetMapping("/sessions/{sessionId}/work-proposals/{proposalId}")
    public ApiResponse<AiAgentFacade.WorkProposalView> workProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.workProposal(
                actor(session, systemId, request), sessionId, proposalId), request);
    }

    @PostMapping("/sessions/{sessionId}/work-proposals/{proposalId}/confirm")
    public ApiResponse<AiAgentFacade.WorkProposalView> confirmWorkProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ConfigurationProposalBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfigurationProposalBody(body);
        return success(ai.confirmWorkProposal(
                actor(session, systemId, request), sessionId, proposalId,
                body.expectedRevision(), idempotencyKey), request);
    }

    @PostMapping("/sessions/{sessionId}/work-proposals/{proposalId}/reject")
    public ApiResponse<AiAgentFacade.WorkProposalView> rejectWorkProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestBody ConfigurationProposalBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfigurationProposalBody(body);
        return success(ai.rejectWorkProposal(
                actor(session, systemId, request), sessionId, proposalId,
                body.expectedRevision()), request);
    }

    @GetMapping("/sessions/{sessionId}/generated-draft-proposals/{proposalId}")
    public ApiResponse<AiAgentFacade.GeneratedDraftProposalView>
    generatedDraftProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.generatedDraftProposal(
                actor(session, systemId, request), sessionId, proposalId), request);
    }

    @PostMapping("/sessions/{sessionId}/generated-draft-proposals/{proposalId}/confirm")
    public ApiResponse<AiAgentFacade.GeneratedDraftProposalView>
    confirmGeneratedDraftProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ConfigurationProposalBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfigurationProposalBody(body);
        return success(ai.confirmGeneratedDraftProposal(
                actor(session, systemId, request), sessionId, proposalId,
                body.expectedRevision(), idempotencyKey), request);
    }

    @PostMapping("/sessions/{sessionId}/generated-draft-proposals/{proposalId}/reject")
    public ApiResponse<AiAgentFacade.GeneratedDraftProposalView>
    rejectGeneratedDraftProposal(
            @PathVariable long systemId,
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestBody ConfigurationProposalBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfigurationProposalBody(body);
        return success(ai.rejectGeneratedDraftProposal(
                actor(session, systemId, request), sessionId, proposalId,
                body.expectedRevision()), request);
    }

    @GetMapping("/confirmations/{confirmationId}")
    public ApiResponse<AiAgentFacade.ConfirmationView> confirmation(
            @PathVariable long systemId,
            @PathVariable String confirmationId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.confirmation(
                actor(session, systemId, request), confirmationId), request);
    }

    @PostMapping("/confirmations/{confirmationId}/confirm")
    public ApiResponse<AiAgentFacade.ConfirmationView> confirm(
            @PathVariable long systemId,
            @PathVariable String confirmationId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ConfirmationBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfirmationBody(body);
        return success(ai.confirm(
                actor(session, systemId, request), confirmationId,
                body.expectedVersion(), idempotencyKey), request);
    }

    @PostMapping("/confirmations/{confirmationId}/reject")
    public ApiResponse<AiAgentFacade.ConfirmationView> reject(
            @PathVariable long systemId,
            @PathVariable String confirmationId,
            @RequestBody ConfirmationBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireConfirmationBody(body);
        return success(ai.reject(
                actor(session, systemId, request), confirmationId,
                body.expectedVersion()), request);
    }

    private static void requireConfirmationBody(ConfirmationBody body) {
        if (body == null || body.expectedVersion() < 0) {
            throw new BusinessException(
                    "AI_REQUEST_INVALID",
                    "AI confirmation expectedVersion must not be negative",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static void requireConfigurationProposalBody(
            ConfigurationProposalBody body) {
        if (body == null || body.expectedRevision() < 0) {
            throw new BusinessException(
                    "AI_REQUEST_INVALID",
                    "AI configuration proposal expectedRevision must not be negative",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static AiActor actor(
            Object value, long systemId, HttpServletRequest request) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException(
                    "AUTH_REQUIRED", "Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null || session.systemId() != systemId
                || session.tenantId() == null || session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH",
                    "The authenticated system member context does not match the request",
                    HttpStatus.FORBIDDEN);
        }
        try {
            return new AiActor(
                    session.accountId(), systemId, session.tenantId(), session.memberId(),
                    session.permissions(), session.permissionVersion(),
                    attribute(request, WebRequestAttributes.REQUEST_ID),
                    attribute(request, WebRequestAttributes.TRACE_ID));
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(
                    "AI_CONTEXT_INVALID", "AI request context is invalid",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(
                value,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    public record CreateSessionBody(String title) {
    }

    public record SubmitMessageBody(String content) {
    }

    public record ConfirmationBody(long expectedVersion) {
    }

    public record ConfigurationProposalBody(long expectedRevision) {
    }
}
