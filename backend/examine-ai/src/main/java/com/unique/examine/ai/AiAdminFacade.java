package com.unique.examine.ai;

import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.repository.AiRepository;
import com.unique.examine.ai.service.AiPolicyService;
import com.unique.examine.ai.service.AiProviderService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AiAdminFacade {
    private final AiProviderService providers;
    private final AiPolicyService policies;
    private final AiRepository repository;

    public AiAdminFacade(
            AiProviderService providers,
            AiPolicyService policies,
            AiRepository repository
    ) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<ProviderView> providers(AiActor actor) {
        return providers.list(actor).stream().map(AiAdminFacade::view).toList();
    }

    public ProviderView createProvider(
            AiActor actor, AiProviderService.Command command) {
        return view(providers.create(actor, command));
    }

    public ProviderView updateProvider(
            AiActor actor,
            String providerId,
            long expectedVersion,
            AiProviderService.Command command
    ) {
        return view(providers.update(
                actor, positiveId(providerId, "providerId"),
                expectedVersion, command));
    }

    public PolicyView policy(AiActor actor) {
        actor.require("ai.policy.manage");
        return repository.policyDraft(actor.systemId(), actor.tenantId())
                .map(AiAdminFacade::view)
                .orElseGet(AiAdminFacade::emptyPolicy);
    }

    public PolicyView savePolicy(
            AiActor actor,
            long expectedVersion,
            AiPolicyService.DraftCommand command
    ) {
        return view(policies.save(actor, expectedVersion, command));
    }

    public CheckView checkPolicy(AiActor actor) {
        return view(policies.check(actor));
    }

    public PolicyView publishPolicy(
            AiActor actor,
            long expectedVersion,
            String idempotencyKey
    ) {
        policies.publish(actor, expectedVersion, idempotencyKey);
        return view(repository.policyDraft(actor.systemId(), actor.tenantId())
                .orElseThrow());
    }

    public CapabilityView capability(AiActor actor) {
        actor.require("ai.policy.manage");
        var version = repository.activePolicy(actor.systemId(), actor.tenantId());
        if (version.isEmpty()) {
            return new CapabilityView(false, "AI_POLICY_NOT_PUBLISHED", null);
        }
        if (!version.get().enabled()) {
            return new CapabilityView(
                    false, "AI_POLICY_DISABLED", Long.toString(version.get().id()));
        }
        var provider = repository.provider(
                actor.systemId(), actor.tenantId(), version.get().providerId());
        if (provider.isEmpty() || !provider.get().enabled()
                || provider.get().version() != version.get().providerVersion()) {
            return new CapabilityView(
                    false, "AI_PROVIDER_UNAVAILABLE",
                    Long.toString(version.get().id()));
        }
        return new CapabilityView(
                true, null, Long.toString(version.get().id()));
    }

    private static ProviderView view(AiProvider value) {
        return new ProviderView(
                Long.toString(value.id()), value.code(), value.name(),
                value.baseUrl(), value.model(), value.secretRef(),
                value.timeoutSeconds(), value.enabled(), value.version());
    }

    private static PolicyView view(AiPolicy.Draft value) {
        var outputFields = new LinkedHashMap<String, List<String>>();
        value.outboundFields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> outputFields.put(
                        entry.getKey(), entry.getValue().stream().sorted().toList()));
        var writableFields = new LinkedHashMap<String, List<String>>();
        value.writableFields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> writableFields.put(
                        entry.getKey(), entry.getValue().stream().sorted().toList()));
        var fillFields = new LinkedHashMap<String, List<String>>();
        value.fillFields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> fillFields.put(
                        entry.getKey(), entry.getValue().stream().sorted().toList()));
        return new PolicyView(
                value.revision(), value.activeVersionId() == null
                ? null : Long.toString(value.activeVersionId()),
                Long.toString(value.providerId()),
                value.allowedModuleCodes().stream().sorted().toList(),
                Map.copyOf(outputFields),
                value.allowedOperations().stream().sorted().toList(),
                Map.copyOf(writableFields), Map.copyOf(fillFields), value.maxRows(),
                value.confirmationMode().name(), value.confirmationExpiresSeconds(),
                value.redactionMode().name(), value.promptVersion(),
                value.enabled());
    }

    private static PolicyView emptyPolicy() {
        return new PolicyView(
                0, null, null, List.of(), Map.of(), List.of("RECORD_QUERY"),
                Map.of(), Map.of(), 20,
                AiPolicy.ConfirmationMode.REQUIRED.name(), 600,
                AiPolicy.RedactionMode.STRICT.name(), "v1", false);
    }

    private static CheckView view(AiPolicy.Check value) {
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
            throw AiSupport.invalid("AI_ID_INVALID", field + " must be a positive id");
        }
    }

    public record ProviderView(
            String id,
            String code,
            String name,
            String baseUrl,
            String model,
            String secretRef,
            int timeoutSeconds,
            boolean enabled,
            long version
    ) {
    }

    public record PolicyView(
            long draftVersion,
            String activeVersionId,
            String providerId,
            List<String> moduleCodes,
            Map<String, List<String>> outboundFields,
            List<String> allowedOperations,
            Map<String, List<String>> writableFields,
            Map<String, List<String>> fillFields,
            int maxRows,
            String confirmationMode,
            int confirmationExpiresSeconds,
            String redactionMode,
            String promptVersion,
            boolean enabled
    ) {
        public PolicyView {
            moduleCodes = List.copyOf(moduleCodes);
            outboundFields = Map.copyOf(outboundFields);
            allowedOperations = List.copyOf(allowedOperations);
            writableFields = Map.copyOf(writableFields);
            fillFields = Map.copyOf(fillFields);
        }

        public PolicyView(
                long draftVersion, String activeVersionId, String providerId,
                List<String> moduleCodes,
                Map<String, List<String>> outboundFields,
                List<String> allowedOperations,
                Map<String, List<String>> writableFields,
                int maxRows, String confirmationMode,
                int confirmationExpiresSeconds, String redactionMode,
                String promptVersion, boolean enabled
        ) {
            this(draftVersion, activeVersionId, providerId, moduleCodes,
                    outboundFields, allowedOperations, writableFields, Map.of(),
                    maxRows, confirmationMode, confirmationExpiresSeconds,
                    redactionMode, promptVersion, enabled);
        }

        public PolicyView(
                long draftVersion, String activeVersionId, String providerId,
                List<String> moduleCodes,
                Map<String, List<String>> outboundFields,
                int maxRows, String redactionMode, String promptVersion,
                boolean enabled
        ) {
            this(draftVersion, activeVersionId, providerId, moduleCodes,
                    outboundFields, List.of("RECORD_QUERY"), Map.of(), Map.of(),
                    maxRows,
                    AiPolicy.ConfirmationMode.REQUIRED.name(), 600,
                    redactionMode, promptVersion, enabled);
        }
    }

    public record CheckView(String status, List<IssueView> issues) {
        public CheckView {
            issues = List.copyOf(issues);
        }
    }

    public record IssueView(
            String code,
            String message,
            String path,
            String severity
    ) {
    }

    public record CapabilityView(
            boolean available,
            String reason,
            String policyVersion
    ) {
    }
}
