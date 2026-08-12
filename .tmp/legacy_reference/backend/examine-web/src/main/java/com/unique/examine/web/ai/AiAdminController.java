package com.unique.examine.web.ai;

import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiAdminFacade;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.service.AiPolicyService;
import com.unique.examine.ai.service.AiProviderService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/ai")
public final class AiAdminController {
    private final AiAdminFacade ai;

    public AiAdminController(AiAdminFacade ai) {
        this.ai = Objects.requireNonNull(ai, "ai");
    }

    @GetMapping("/providers")
    public ApiResponse<List<AiAdminFacade.ProviderView>> providers(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.providers(actor(session, systemId, request)), request);
    }

    @PostMapping("/providers")
    public ApiResponse<AiAdminFacade.ProviderView> createProvider(
            @PathVariable long systemId,
            @RequestBody ProviderCreateBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.createProvider(
                actor(session, systemId, request), provider(body)), request);
    }

    @PutMapping("/providers/{providerId}")
    public ApiResponse<AiAdminFacade.ProviderView> updateProvider(
            @PathVariable long systemId,
            @PathVariable String providerId,
            @RequestBody ProviderUpdateBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        if (body == null || body.expectedVersion() < 0) {
            throw invalid("AI provider expectedVersion must not be negative");
        }
        return success(ai.updateProvider(
                actor(session, systemId, request), providerId,
                body.expectedVersion(), provider(body)), request);
    }

    @GetMapping("/policy")
    public ApiResponse<AiAdminFacade.PolicyView> policy(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.policy(actor(session, systemId, request)), request);
    }

    @PutMapping("/policy")
    public ApiResponse<AiAdminFacade.PolicyView> savePolicy(
            @PathVariable long systemId,
            @RequestBody PolicyBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        if (body == null || body.expectedVersion() < 0) {
            throw invalid("AI policy expectedVersion must not be negative");
        }
        return success(ai.savePolicy(
                actor(session, systemId, request), body.expectedVersion(),
                policy(body)), request);
    }

    @PostMapping("/policy:check")
    public ApiResponse<AiAdminFacade.CheckView> checkPolicy(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.checkPolicy(actor(session, systemId, request)), request);
    }

    @PostMapping("/policy:publish")
    public ApiResponse<AiAdminFacade.PolicyView> publishPolicy(
            @PathVariable long systemId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody PublishBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        if (body == null || body.expectedVersion() <= 0) {
            throw invalid("AI policy expectedVersion must be positive");
        }
        return success(ai.publishPolicy(
                actor(session, systemId, request), body.expectedVersion(),
                idempotencyKey), request);
    }

    @GetMapping("/capability")
    public ApiResponse<AiAdminFacade.CapabilityView> capability(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(ai.capability(actor(session, systemId, request)), request);
    }

    private static AiProviderService.Command provider(ProviderBody body) {
        if (body == null) throw invalid("AI provider body is required");
        try {
            return new AiProviderService.Command(
                    body.code(), body.name(), body.baseUrl(), body.model(),
                    body.secretRef(), body.timeoutSeconds(), body.enabled());
        } catch (IllegalArgumentException failure) {
            throw invalid(failure.getMessage());
        }
    }

    private static AiPolicyService.DraftCommand policy(PolicyBody body) {
        try {
            var modules = body.moduleCodes() == null
                    ? Set.<String>of() : new TreeSet<>(body.moduleCodes());
            var fields = new TreeMap<String, Set<String>>();
            if (body.outboundFields() != null) {
                body.outboundFields().forEach((module, values) -> fields.put(
                        module, values == null ? Set.of() : new TreeSet<>(values)));
            }
            var operations = body.allowedOperations() == null
                    ? Set.of("RECORD_QUERY")
                    : new TreeSet<>(body.allowedOperations());
            var writableFields = new TreeMap<String, Set<String>>();
            if (body.writableFields() != null) {
                body.writableFields().forEach((module, values) -> writableFields.put(
                        module, values == null ? Set.of() : new TreeSet<>(values)));
            }
            var fillFields = new TreeMap<String, Set<String>>();
            if (body.fillFields() != null) {
                body.fillFields().forEach((module, values) -> fillFields.put(
                        module, values == null ? Set.of() : new TreeSet<>(values)));
            }
            return new AiPolicyService.DraftCommand(
                    positiveId(body.providerId(), "providerId"), modules, fields,
                    operations, writableFields, fillFields, body.maxRows(),
                    body.confirmationMode() == null
                            ? AiPolicy.ConfirmationMode.REQUIRED
                            : body.confirmationMode(),
                    body.confirmationExpiresSeconds() == 0
                            ? 600 : body.confirmationExpiresSeconds(),
                    body.redactionMode(), body.promptVersion(), body.enabled());
        } catch (BusinessException expected) {
            throw expected;
        } catch (RuntimeException failure) {
            throw invalid(failure.getMessage());
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

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw invalid("AI " + field + " must be a positive id");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                "AI_REQUEST_INVALID",
                message == null || message.isBlank() ? "AI request is invalid" : message,
                HttpStatus.UNPROCESSABLE_ENTITY);
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

    private interface ProviderBody {
        String code();
        String name();
        String baseUrl();
        String model();
        String secretRef();
        int timeoutSeconds();
        boolean enabled();
    }

    public record ProviderCreateBody(
            String code,
            String name,
            String baseUrl,
            String model,
            String secretRef,
            int timeoutSeconds,
            boolean enabled
    ) implements ProviderBody {
    }

    public record ProviderUpdateBody(
            String code,
            String name,
            String baseUrl,
            String model,
            String secretRef,
            int timeoutSeconds,
            boolean enabled,
            long expectedVersion
    ) implements ProviderBody {
    }

    public record PolicyBody(
            long expectedVersion,
            String providerId,
            Set<String> moduleCodes,
            Map<String, List<String>> outboundFields,
            Set<String> allowedOperations,
            Map<String, List<String>> writableFields,
            Map<String, List<String>> fillFields,
            int maxRows,
            AiPolicy.ConfirmationMode confirmationMode,
            int confirmationExpiresSeconds,
            AiPolicy.RedactionMode redactionMode,
            String promptVersion,
            boolean enabled
    ) {
    }

    public record PublishBody(long expectedVersion) {
    }
}
