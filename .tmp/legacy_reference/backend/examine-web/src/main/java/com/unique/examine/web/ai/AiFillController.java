package com.unique.examine.web.ai;

import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiFillFacade;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/modules/{moduleCode}/records/"
        + "{recordId}/ai-fill/{fieldCode}")
public final class AiFillController {
    private final AiFillFacade fills;

    public AiFillController(AiFillFacade fills) {
        this.fills = Objects.requireNonNull(fills, "fills");
    }

    @PostMapping("/proposals")
    public ApiResponse<AiFillFacade.ProposalView> propose(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @PathVariable String fieldCode,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ProposeBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        if (body == null || body.expectedRecordVersion() < 0) {
            throw invalid("AI fill expectedRecordVersion must not be negative");
        }
        return success(fills.propose(
                actor(session, systemId, request), moduleCode, recordId, fieldCode,
                body.expectedRecordVersion(), idempotencyKey), request);
    }

    @GetMapping("/proposals/{proposalId}")
    public ApiResponse<AiFillFacade.ProposalView> proposal(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @PathVariable String fieldCode,
            @PathVariable String proposalId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(fills.proposal(
                actor(session, systemId, request), moduleCode, recordId,
                fieldCode, proposalId), request);
    }

    @PostMapping("/proposals/{proposalId}/confirm")
    public ApiResponse<AiFillFacade.ProposalView> confirm(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @PathVariable String fieldCode,
            @PathVariable String proposalId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ActionBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireAction(body);
        return success(fills.confirm(
                actor(session, systemId, request), moduleCode, recordId,
                fieldCode, proposalId, body.expectedVersion(), idempotencyKey), request);
    }

    @PostMapping("/proposals/{proposalId}/reject")
    public ApiResponse<AiFillFacade.ProposalView> reject(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @PathVariable String fieldCode,
            @PathVariable String proposalId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ActionBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        requireAction(body);
        return success(fills.reject(
                actor(session, systemId, request), moduleCode, recordId,
                fieldCode, proposalId, body.expectedVersion(), idempotencyKey), request);
    }

    private static void requireAction(ActionBody body) {
        if (body == null || body.expectedVersion() < 0) {
            throw invalid("AI fill expectedVersion must not be negative");
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

    private static BusinessException invalid(String message) {
        return new BusinessException(
                "AI_FILL_REQUEST_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
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

    public record ProposeBody(long expectedRecordVersion) {
    }

    public record ActionBody(long expectedVersion) {
    }
}
