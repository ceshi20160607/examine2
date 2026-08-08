package com.unique.examine.web.ai;

import com.unique.examine.ai.PlatformAiAgentFacade;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.context.RequestSession;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/platform/ai")
public final class PlatformAiAgentController {
    private final PlatformAiAgentFacade ai;

    public PlatformAiAgentController(PlatformAiAgentFacade ai) {
        this.ai = Objects.requireNonNull(ai, "ai");
    }

    @GetMapping("/capability")
    public ApiResponse<PlatformAiAgentFacade.CapabilityView> capability(
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.capability(
                PlatformAiControllerSupport.actor(session, request)), request);
    }

    @GetMapping("/sessions")
    public ApiResponse<PlatformAiAgentFacade.SessionPage> sessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.sessions(
                PlatformAiControllerSupport.actor(session, request),
                page, size), request);
    }

    @PostMapping("/sessions")
    public ApiResponse<PlatformAiAgentFacade.SessionView> createSession(
            @RequestBody CreateSessionBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.createSession(
                PlatformAiControllerSupport.actor(session, request),
                new PlatformAiAgentFacade.CreateSession(
                        body == null ? null : body.title())), request);
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<PlatformAiAgentFacade.DetailView> detail(
            @PathVariable String sessionId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.detail(
                PlatformAiControllerSupport.actor(session, request),
                sessionId), request);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<PlatformAiAgentFacade.TurnView> submit(
            @PathVariable String sessionId,
            @RequestBody SubmitMessageBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.submit(
                PlatformAiControllerSupport.actor(session, request), sessionId,
                new PlatformAiAgentFacade.SubmitMessage(
                        body == null ? null : body.content())), request);
    }

    @GetMapping("/sessions/{sessionId}/proposals/{proposalId}")
    public ApiResponse<PlatformAiAgentFacade.TaskProposalView> proposal(
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.proposal(
                PlatformAiControllerSupport.actor(session, request),
                sessionId, proposalId), request);
    }

    @PostMapping("/sessions/{sessionId}/proposals/{proposalId}/confirm")
    public ApiResponse<PlatformAiAgentFacade.TaskProposalView> confirmProposal(
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ProposalActionBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireProposalBody(body);
        return PlatformAiControllerSupport.success(ai.confirmTask(
                PlatformAiControllerSupport.actor(session, request),
                sessionId, proposalId,
                new PlatformAiAgentFacade.ConfirmTaskProposal(
                        body.expectedRevision()), idempotencyKey), request);
    }

    @PostMapping("/sessions/{sessionId}/proposals/{proposalId}/reject")
    public ApiResponse<PlatformAiAgentFacade.TaskProposalView> rejectProposal(
            @PathVariable String sessionId,
            @PathVariable String proposalId,
            @RequestBody ProposalActionBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireProposalBody(body);
        return PlatformAiControllerSupport.success(ai.rejectTask(
                PlatformAiControllerSupport.actor(session, request),
                sessionId, proposalId,
                new PlatformAiAgentFacade.RejectTaskProposal(
                        body.expectedRevision())), request);
    }

    private static void requireProposalBody(ProposalActionBody body) {
        if (body == null || body.expectedRevision() < 0) {
            throw PlatformAiControllerSupport.invalid(
                    "Platform AI task proposal expectedRevision must not be negative");
        }
    }

    public record CreateSessionBody(String title) {
    }

    public record SubmitMessageBody(String content) {
    }

    public record ProposalActionBody(long expectedRevision) {
    }
}
