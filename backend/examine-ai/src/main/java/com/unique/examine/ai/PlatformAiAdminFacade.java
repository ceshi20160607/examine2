package com.unique.examine.ai;

import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.domain.PlatformAiProvider;
import com.unique.examine.ai.repository.PlatformAiRepository;
import com.unique.examine.ai.service.PlatformAiPolicyService;
import com.unique.examine.ai.service.PlatformAiProviderService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PlatformAiAdminFacade {
    private final PlatformAiProviderService providers;
    private final PlatformAiPolicyService policies;
    private final PlatformAiRepository repository;

    public PlatformAiAdminFacade(
            PlatformAiProviderService providers,
            PlatformAiPolicyService policies,
            PlatformAiRepository repository) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<ProviderView> providers(PlatformAiActor actor) {
        return providers.list(actor).stream()
                .map(PlatformAiAdminFacade::view).toList();
    }

    public ProviderView createProvider(
            PlatformAiActor actor, PlatformAiProviderService.Command command) {
        return view(providers.create(actor, command));
    }

    public ProviderView updateProvider(
            PlatformAiActor actor, String providerId, long expectedVersion,
            PlatformAiProviderService.Command command) {
        return view(providers.update(actor, positiveId(providerId, "providerId"),
                expectedVersion, command));
    }

    public PolicyView policy(PlatformAiActor actor) {
        actor.require("platform.ai.policy.manage");
        return repository.policyDraft().map(PlatformAiAdminFacade::view)
                .orElseGet(PlatformAiAdminFacade::emptyPolicy);
    }

    public PolicyView savePolicy(
            PlatformAiActor actor, long expectedVersion,
            PlatformAiPolicyService.DraftCommand command) {
        return view(policies.save(actor, expectedVersion, command));
    }

    public CheckView checkPolicy(PlatformAiActor actor) {
        return view(policies.check(actor));
    }

    public PolicyView publishPolicy(
            PlatformAiActor actor, long expectedVersion,
            String idempotencyKey) {
        policies.publish(actor, expectedVersion, idempotencyKey);
        return view(repository.policyDraft().orElseThrow());
    }

    public CapabilityView capability(PlatformAiActor actor) {
        actor.require("platform.ai.policy.manage");
        return capability(repository);
    }

    static CapabilityView capability(PlatformAiRepository repository) {
        var policy = repository.activePolicy();
        if (policy.isEmpty()) {
            return new CapabilityView(
                    false, "PLATFORM_AI_POLICY_NOT_PUBLISHED", null);
        }
        if (!policy.get().settings().enabled()) {
            return new CapabilityView(
                    false, "PLATFORM_AI_POLICY_DISABLED",
                    Long.toString(policy.get().id()));
        }
        var provider = repository.provider(policy.get().providerId());
        if (provider.isEmpty() || !provider.get().enabled()
                || provider.get().version() != policy.get().providerVersion()) {
            return new CapabilityView(
                    false, "PLATFORM_AI_PROVIDER_UNAVAILABLE",
                    Long.toString(policy.get().id()));
        }
        return new CapabilityView(true, null, Long.toString(policy.get().id()));
    }

    private static ProviderView view(PlatformAiProvider value) {
        return new ProviderView(
                Long.toString(value.id()), value.code(), value.name(),
                value.baseUrl(), value.model(), value.secretRef(),
                value.timeoutSeconds(), value.enabled(), value.version());
    }

    private static PolicyView view(PlatformAiPolicy.Draft value) {
        var settings = value.settings();
        return new PolicyView(
                value.revision(), value.status().name(),
                value.activeVersionId() == null ? null
                        : Long.toString(value.activeVersionId()),
                Long.toString(value.providerId()), value.providerVersion(),
                settings.allowedOperations().stream().sorted().toList(),
                settings.maxSystems(), settings.dailyRequestQuota(),
                settings.dailyTokenQuota(), settings.maxConcurrency(),
                settings.strictRedaction(), settings.dataResidency().name(),
                settings.promptVersion(), settings.enabled());
    }

    private static PolicyView emptyPolicy() {
        return new PolicyView(
                0, "DRAFT", null, null, 0,
                PlatformAiPolicy.SUPPORTED_OPERATIONS.stream().sorted().toList(),
                50, 100, 100_000, 2, true,
                PlatformAiPolicy.DataResidency.PLATFORM_METADATA_ONLY.name(),
                "v1", false);
    }

    private static CheckView view(PlatformAiPolicy.Check value) {
        return new CheckView(
                value.valid() ? "PASSED" : "FAILED",
                value.issues().stream().map(issue -> new IssueView(
                        issue.code(), issue.message(), issue.path(),
                        issue.severity().name())).toList());
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_ID_INVALID",
                    field + " must be a positive id");
        }
    }

    public record ProviderView(
            String id, String code, String name, String baseUrl,
            String model, String secretRef, int timeoutSeconds,
            boolean enabled, long version) { }

    public record PolicyView(
            long draftVersion,
            String status,
            String activeVersionId,
            String providerId,
            long providerVersion,
            List<String> allowedOperations,
            int maxSystems,
            int dailyRequestQuota,
            int dailyTokenQuota,
            int maxConcurrency,
            boolean strictRedaction,
            String dataResidency,
            String promptVersion,
            boolean enabled
    ) {
        public PolicyView {
            allowedOperations = List.copyOf(allowedOperations);
        }
    }

    public record CheckView(String status, List<IssueView> issues) {
        public CheckView { issues = List.copyOf(issues); }
    }

    public record IssueView(
            String code, String message, String path, String severity) { }

    public record CapabilityView(
            boolean available, String reason, String policyVersion) { }
}
