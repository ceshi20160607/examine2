package com.unique.examine.web.ai;

import com.unique.examine.ai.PlatformAiAdminFacade;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.service.PlatformAiPolicyService;
import com.unique.examine.ai.service.PlatformAiProviderService;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/v1/platform/admin/ai")
public final class PlatformAiAdminController {
    private final PlatformAiAdminFacade ai;

    public PlatformAiAdminController(PlatformAiAdminFacade ai) {
        this.ai = Objects.requireNonNull(ai, "ai");
    }

    @GetMapping("/providers")
    public ApiResponse<List<PlatformAiAdminFacade.ProviderView>> providers(
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.providers(
                PlatformAiControllerSupport.actor(session, request)), request);
    }

    @PostMapping("/providers")
    public ApiResponse<PlatformAiAdminFacade.ProviderView> createProvider(
            @RequestBody ProviderCreateBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.createProvider(
                PlatformAiControllerSupport.actor(session, request),
                provider(body)), request);
    }

    @PutMapping("/providers/{providerId}")
    public ApiResponse<PlatformAiAdminFacade.ProviderView> updateProvider(
            @PathVariable String providerId,
            @RequestBody ProviderUpdateBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        if (body == null || body.expectedVersion() < 0) {
            throw PlatformAiControllerSupport.invalid(
                    "Platform AI provider expectedVersion must not be negative");
        }
        return PlatformAiControllerSupport.success(ai.updateProvider(
                PlatformAiControllerSupport.actor(session, request), providerId,
                body.expectedVersion(), provider(body)), request);
    }

    @GetMapping("/policy")
    public ApiResponse<PlatformAiAdminFacade.PolicyView> policy(
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.policy(
                PlatformAiControllerSupport.actor(session, request)), request);
    }

    @PutMapping("/policy")
    public ApiResponse<PlatformAiAdminFacade.PolicyView> savePolicy(
            @RequestBody PolicyBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        if (body == null || body.expectedVersion() < 0) {
            throw PlatformAiControllerSupport.invalid(
                    "Platform AI policy expectedVersion must not be negative");
        }
        return PlatformAiControllerSupport.success(ai.savePolicy(
                PlatformAiControllerSupport.actor(session, request),
                body.expectedVersion(), policy(body)), request);
    }

    @PostMapping("/policy:check")
    public ApiResponse<PlatformAiAdminFacade.CheckView> checkPolicy(
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.checkPolicy(
                PlatformAiControllerSupport.actor(session, request)), request);
    }

    @PostMapping("/policy:publish")
    public ApiResponse<PlatformAiAdminFacade.PolicyView> publishPolicy(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody PublishBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        if (body == null || body.expectedVersion() <= 0) {
            throw PlatformAiControllerSupport.invalid(
                    "Platform AI policy expectedVersion must be positive");
        }
        return PlatformAiControllerSupport.success(ai.publishPolicy(
                PlatformAiControllerSupport.actor(session, request),
                body.expectedVersion(), idempotencyKey), request);
    }

    @GetMapping("/capability")
    public ApiResponse<PlatformAiAdminFacade.CapabilityView> capability(
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return PlatformAiControllerSupport.success(ai.capability(
                PlatformAiControllerSupport.actor(session, request)), request);
    }

    private static PlatformAiProviderService.Command provider(ProviderBody body) {
        if (body == null) {
            throw PlatformAiControllerSupport.invalid(
                    "Platform AI provider body is required");
        }
        try {
            return new PlatformAiProviderService.Command(
                    body.code(), body.name(), body.baseUrl(), body.model(),
                    body.secretRef(), body.timeoutSeconds(), body.enabled());
        } catch (RuntimeException failure) {
            throw PlatformAiControllerSupport.invalid(failure.getMessage());
        }
    }

    private static PlatformAiPolicyService.DraftCommand policy(PolicyBody body) {
        try {
            var operations = body.allowedOperations() == null
                    ? Set.<String>of()
                    : new TreeSet<>(body.allowedOperations());
            return new PlatformAiPolicyService.DraftCommand(
                    positiveId(body.providerId(), "providerId"), operations,
                    body.maxSystems(), body.dailyRequestQuota(),
                    body.dailyTokenQuota(), body.maxConcurrency(),
                    body.strictRedaction(), body.dataResidency(),
                    body.promptVersion(), body.enabled());
        } catch (BusinessException expected) {
            throw expected;
        } catch (RuntimeException failure) {
            throw PlatformAiControllerSupport.invalid(failure.getMessage());
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
            throw PlatformAiControllerSupport.invalid(
                    "Platform AI " + field + " must be a positive id");
        }
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
            Set<String> allowedOperations,
            int maxSystems,
            int dailyRequestQuota,
            int dailyTokenQuota,
            int maxConcurrency,
            boolean strictRedaction,
            PlatformAiPolicy.DataResidency dataResidency,
            String promptVersion,
            boolean enabled
    ) {
    }

    public record PublishBody(long expectedVersion) {
    }
}
