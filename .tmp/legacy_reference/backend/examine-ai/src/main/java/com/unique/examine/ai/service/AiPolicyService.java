package com.unique.examine.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.repository.AiRepository;
import com.unique.examine.core.ai.AiRecordPolicyCatalogFacade;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.unique.examine.ai.AiSupport.conflict;
import static com.unique.examine.ai.AiSupport.invalid;
import static com.unique.examine.ai.AiSupport.notFound;

@Service
public class AiPolicyService {
    private final AiRepository repository;
    private final AiRecordPolicyCatalogFacade catalog;
    private final IdService ids;
    private final Clock clock;
    private final ObjectMapper json;

    public AiPolicyService(
            AiRepository repository,
            AiRecordPolicyCatalogFacade catalog,
            IdService ids,
            Clock clock,
            ObjectMapper json
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.json = Objects.requireNonNull(json, "json");
    }

    public AiPolicy.Draft draft(AiActor actor) {
        actor.require("ai.policy.manage");
        return repository.policyDraft(actor.systemId(), actor.tenantId())
                .orElseThrow(() -> notFound("AI policy draft does not exist"));
    }

    @Transactional
    public AiPolicy.Draft save(
            AiActor actor, long expectedRevision, DraftCommand command) {
        actor.require("ai.policy.manage");
        Objects.requireNonNull(command, "command");
        var provider = repository.provider(
                actor.systemId(), actor.tenantId(), command.providerId())
                .orElseThrow(() -> invalid(
                        "AI_POLICY_INVALID", "AI policy provider does not exist"));
        var fields = command.outboundFields();
        var hash = hash(command, provider.version());
        var now = clock.instant();
        var current = repository.policyDraft(actor.systemId(), actor.tenantId());
        if (current.isEmpty()) {
            if (expectedRevision != 0) {
                throw conflict("AI_POLICY_VERSION_CONFLICT", "AI policy revision is stale");
            }
            var created = new AiPolicy.Draft(
                    ids.nextId(), actor.systemId(), actor.tenantId(), 1,
                    AiPolicy.DraftStatus.DRAFT, provider.id(), provider.version(),
                    command.moduleCodes(), fields, command.allowedOperations(),
                    command.writableFields(), command.fillFields(), command.maxRows(),
                    command.confirmationMode(), command.confirmationExpiresSeconds(),
                    command.enabled(), command.redactionMode(),
                    command.promptVersion(), hash, null,
                    now, actor.memberId());
            repository.insertPolicyDraft(created);
            return created;
        }
        var prior = current.get();
        if (prior.revision() != expectedRevision) {
            throw conflict("AI_POLICY_VERSION_CONFLICT", "AI policy revision is stale");
        }
        var revised = new AiPolicy.Draft(
                prior.id(), prior.systemId(), prior.tenantId(),
                prior.revision() + 1, AiPolicy.DraftStatus.DRAFT,
                provider.id(), provider.version(), command.moduleCodes(), fields,
                command.allowedOperations(), command.writableFields(),
                command.fillFields(), command.maxRows(), command.confirmationMode(),
                command.confirmationExpiresSeconds(), command.enabled(), command.redactionMode(),
                command.promptVersion(), hash, prior.activeVersionId(),
                now, actor.memberId());
        if (!repository.updatePolicyDraft(revised, expectedRevision)) {
            throw conflict("AI_POLICY_VERSION_CONFLICT", "AI policy revision is stale");
        }
        return revised;
    }

    @Transactional
    public AiPolicy.Check check(AiActor actor) {
        actor.require("ai.policy.manage");
        var draft = repository.policyDraft(actor.systemId(), actor.tenantId())
                .orElseThrow(() -> notFound("AI policy draft does not exist"));
        var issues = new ArrayList<AiPolicy.Issue>();
        var provider = repository.provider(
                actor.systemId(), actor.tenantId(), draft.providerId());
        if (provider.isEmpty()) {
            issues.add(issue("AI_PROVIDER_MISSING", "providerId",
                    "Configured provider does not exist"));
        } else if (!provider.get().enabled()
                || provider.get().version() != draft.providerVersion()) {
            issues.add(issue("AI_PROVIDER_UNAVAILABLE", "providerId",
                    "Configured provider is disabled or changed"));
        }
        for (var moduleCode : draft.allowedModuleCodes()) {
            final AiRecordPolicyCatalogFacade.Result capability;
            try {
                capability = catalog.catalog(
                        new AiRecordPolicyCatalogFacade.Request(
                                actor.systemId(), actor.tenantId(),
                                actor.memberId(), actor.effectivePermissions(),
                                moduleCode));
            } catch (RuntimeException unavailable) {
                issues.add(issue("AI_MODULE_MISSING", "moduleCodes." + moduleCode,
                        "Configured module is unavailable to the current member"));
                continue;
            }
            var unreadable = new TreeSet<>(draft.outboundFields().get(moduleCode));
            unreadable.removeAll(capability.readableFieldCodes());
            if (!unreadable.isEmpty()) {
                issues.add(issue("AI_FIELD_UNREADABLE",
                        "outputFieldCodes." + moduleCode,
                        "Fields are not readable: " + String.join(",", unreadable)));
            }
            if (!draft.writableFields().getOrDefault(moduleCode, Set.of()).isEmpty()) {
                if (draft.allowedOperations().contains("RECORD_CREATE")
                        && !actor.effectivePermissions().contains(
                        "module." + moduleCode + ".create")) {
                    issues.add(issue("AI_MODULE_CREATE_DENIED",
                            "allowedOperations.RECORD_CREATE." + moduleCode,
                            "Current member cannot create records in the module"));
                }
                if (draft.allowedOperations().contains("RECORD_UPDATE")
                        && !actor.effectivePermissions().contains(
                        "module." + moduleCode + ".update")) {
                    issues.add(issue("AI_MODULE_UPDATE_DENIED",
                            "allowedOperations.RECORD_UPDATE." + moduleCode,
                            "Current member cannot update records in the module"));
                }
            }
            var unreadableFill = new TreeSet<>(
                    draft.fillFields().getOrDefault(moduleCode, Set.of()));
            unreadableFill.removeAll(capability.readableFieldCodes());
            if (!unreadableFill.isEmpty()) {
                issues.add(issue("AI_FILL_FIELD_UNREADABLE",
                        "fillFields." + moduleCode,
                        "AI fill fields are not readable: "
                                + String.join(",", unreadableFill)));
            }
        }
        var check = new AiPolicy.Check(
                ids.nextId(), draft.systemId(), draft.tenantId(), draft.id(),
                draft.revision(), draft.draftHash(), issues,
                clock.instant(), actor.memberId());
        var checked = copy(draft, AiPolicy.DraftStatus.CHECKED,
                draft.activeVersionId(), clock.instant(), actor.memberId());
        if (!repository.updatePolicyDraft(checked, draft.revision())) {
            throw conflict("AI_POLICY_VERSION_CONFLICT", "AI policy revision changed");
        }
        repository.insertPolicyCheck(check);
        return check;
    }

    @Transactional
    public AiPolicy.Version publish(
            AiActor actor, long expectedRevision, String requestKey) {
        actor.require("ai.policy.manage");
        requestKey = requiredKey(requestKey);
        var draft = repository.policyDraft(actor.systemId(), actor.tenantId())
                .orElseThrow(() -> notFound("AI policy draft does not exist"));
        var requestHash = AiSupport.sha256(
                draft.id() + ":" + expectedRevision + ":" + draft.draftHash());
        var replay = repository.publishReplay(
                actor.systemId(), actor.tenantId(), draft.id(), requestKey);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw conflict("AI_POLICY_REPLAY_CONFLICT",
                        "AI policy publish key was used for another request");
            }
            return repository.policyVersion(
                    actor.systemId(), actor.tenantId(), replay.get().versionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "AI policy replay version is missing"));
        }
        if (draft.revision() != expectedRevision
                || draft.status() != AiPolicy.DraftStatus.CHECKED) {
            throw conflict("AI_POLICY_VERSION_CONFLICT",
                    "AI policy must be checked at the expected revision");
        }
        var check = repository.policyCheck(
                draft.systemId(), draft.tenantId(), draft.id(), draft.revision())
                .filter(value -> value.draftHash().equals(draft.draftHash()))
                .orElseThrow(() -> conflict(
                        "AI_POLICY_CHECK_REQUIRED", "AI policy check is missing"));
        if (!check.valid()) {
            throw invalid("AI_POLICY_CHECK_FAILED", "AI policy has blocking issues");
        }
        var provider = repository.provider(
                draft.systemId(), draft.tenantId(), draft.providerId())
                .filter(AiProvider::enabled)
                .filter(value -> value.version() == draft.providerVersion())
                .orElseThrow(() -> conflict(
                        "AI_PROVIDER_CHANGED", "AI policy provider changed after check"));
        var versionId = ids.nextId();
        var now = clock.instant();
        var version = new AiPolicy.Version(
                versionId, draft.systemId(), draft.tenantId(), draft.id(),
                repository.nextPolicyVersionNumber(
                        draft.systemId(), draft.tenantId(), draft.id()),
                provider.id(), provider.version(), provider.model(),
                draft.allowedModuleCodes(), draft.outboundFields(),
                draft.allowedOperations(), draft.writableFields(), draft.fillFields(),
                draft.maxRows(),
                draft.confirmationMode(), draft.confirmationExpiresSeconds(), draft.enabled(),
                draft.redactionMode(), draft.promptVersion(),
                AiSupport.sha256(draft.draftHash() + ":" + provider.model()),
                now, actor.memberId());
        var published = copy(draft, AiPolicy.DraftStatus.PUBLISHED,
                version.id(), now, actor.memberId());
        var receipt = new AiPolicy.PublishReplay(
                ids.nextId(), draft.systemId(), draft.tenantId(), draft.id(), requestKey,
                requestHash, version.id(), now);
        if (!repository.publishPolicy(draft, published, version, receipt)) {
            var concurrent = repository.publishReplay(
                    draft.systemId(), draft.tenantId(), draft.id(), requestKey);
            if (concurrent.isPresent()
                    && concurrent.get().requestHash().equals(requestHash)) {
                return repository.policyVersion(
                        draft.systemId(), draft.tenantId(),
                        concurrent.get().versionId()).orElseThrow();
            }
            throw conflict("AI_POLICY_VERSION_CONFLICT", "AI policy publish lost CAS");
        }
        return version;
    }

    public AiPolicy.Version active(AiActor actor) {
        return repository.activePolicy(actor.systemId(), actor.tenantId())
                .orElseThrow(() -> notFound("AI policy is not published"));
    }

    private String hash(DraftCommand command, long providerVersion) {
        var value = new TreeMap<String, Object>();
        value.put("enabled", command.enabled());
        value.put("maxRows", command.maxRows());
        value.put("moduleCodes", new TreeSet<>(command.moduleCodes()));
        value.put("allowedOperations", new TreeSet<>(command.allowedOperations()));
        value.put("confirmationExpiresSeconds", command.confirmationExpiresSeconds());
        value.put("confirmationMode", command.confirmationMode().name());
        value.put("outputFields", command.outboundFields().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new TreeSet<>(entry.getValue()),
                        (left, right) -> left,
                        TreeMap::new)));
        value.put("writableFields", command.writableFields().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new TreeSet<>(entry.getValue()),
                        (left, right) -> left,
                        TreeMap::new)));
        value.put("fillFields", command.fillFields().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new TreeSet<>(entry.getValue()),
                        (left, right) -> left,
                        TreeMap::new)));
        value.put("promptVersion", command.promptVersion());
        value.put("providerId", command.providerId());
        value.put("providerVersion", providerVersion);
        value.put("redactionMode", command.redactionMode().name());
        try {
            return AiSupport.sha256(json.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot hash AI policy", exception);
        }
    }

    private static AiPolicy.Draft copy(
            AiPolicy.Draft value,
            AiPolicy.DraftStatus status,
            Long activeVersionId,
            java.time.Instant now,
            long actorId) {
        return new AiPolicy.Draft(
                value.id(), value.systemId(), value.tenantId(), value.revision(),
                status, value.providerId(), value.providerVersion(),
                value.allowedModuleCodes(), value.outboundFields(),
                value.allowedOperations(), value.writableFields(), value.fillFields(),
                value.maxRows(),
                value.confirmationMode(), value.confirmationExpiresSeconds(),
                value.enabled(), value.redactionMode(), value.promptVersion(),
                value.draftHash(), activeVersionId, now, actorId);
    }

    private static AiPolicy.Issue issue(String code, String path, String message) {
        return new AiPolicy.Issue(
                code, path, message, AiPolicy.Severity.BLOCKER);
    }

    private static String requiredKey(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("AI_IDEMPOTENCY_KEY_INVALID",
                    "AI publish Idempotency-Key must contain 1..128 characters");
        }
        value = value.strip();
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw invalid("AI_IDEMPOTENCY_KEY_INVALID",
                    "AI publish Idempotency-Key is invalid");
        }
        return value;
    }

    public record DraftCommand(
            long providerId,
            Set<String> moduleCodes,
            Map<String, Set<String>> outboundFields,
            Set<String> allowedOperations,
            Map<String, Set<String>> writableFields,
            Map<String, Set<String>> fillFields,
            int maxRows,
            AiPolicy.ConfirmationMode confirmationMode,
            int confirmationExpiresSeconds,
            AiPolicy.RedactionMode redactionMode,
            String promptVersion,
            boolean enabled
    ) {
        public DraftCommand {
            if (providerId <= 0) {
                throw new IllegalArgumentException("AI providerId must be positive");
            }
            moduleCodes = Set.copyOf(moduleCodes);
            Objects.requireNonNull(outboundFields, "outboundFields");
            var normalized = new TreeMap<String, Set<String>>();
            outboundFields.forEach((module, fields) -> normalized.put(
                    module, Set.copyOf(fields)));
            outboundFields = Map.copyOf(normalized);
            allowedOperations = Set.copyOf(Objects.requireNonNull(
                    allowedOperations, "allowedOperations"));
            var normalizedWritable = new TreeMap<String, Set<String>>();
            Objects.requireNonNull(writableFields, "writableFields")
                    .forEach((module, fields) -> normalizedWritable.put(
                            module, Set.copyOf(fields)));
            writableFields = Map.copyOf(normalizedWritable);
            var normalizedFill = new TreeMap<String, Set<String>>();
            Objects.requireNonNull(fillFields, "fillFields")
                    .forEach((module, fields) -> normalizedFill.put(
                            module, Set.copyOf(fields)));
            fillFields = Map.copyOf(normalizedFill);
            if (moduleCodes.isEmpty() || outboundFields.isEmpty()
                    || !outboundFields.keySet().equals(moduleCodes)
                    || outboundFields.values().stream().anyMatch(Set::isEmpty)) {
                throw new IllegalArgumentException(
                        "AI policy outbound fields must cover exactly its modules");
            }
            if (allowedOperations.isEmpty()
                    || !AiPolicy.SUPPORTED_OPERATIONS.containsAll(allowedOperations)
                    || (allowedOperations.contains("RECORD_CREATE")
                    || allowedOperations.contains("RECORD_UPDATE"))
                    != !writableFields.isEmpty()
                    || !moduleCodes.containsAll(writableFields.keySet())) {
                throw new IllegalArgumentException(
                        "AI policy write operation or field scope is invalid");
            }
            if (allowedOperations.contains("AI_FILL") != !fillFields.isEmpty()
                    || !moduleCodes.containsAll(fillFields.keySet())) {
                throw new IllegalArgumentException(
                        "AI policy fill operation or field scope is invalid");
            }
            if (maxRows < 1 || maxRows > 50) {
                throw new IllegalArgumentException("AI maxRows must be within 1..50");
            }
            Objects.requireNonNull(confirmationMode, "confirmationMode");
            if (confirmationExpiresSeconds < 60
                    || confirmationExpiresSeconds > 3600) {
                throw new IllegalArgumentException(
                        "AI confirmation expiry must be within 60..3600 seconds");
            }
            Objects.requireNonNull(redactionMode, "redactionMode");
            if (promptVersion == null
                    || !promptVersion.matches(
                    "^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")) {
                throw new IllegalArgumentException("AI promptVersion is invalid");
            }
        }

        public DraftCommand(
                long providerId, Set<String> moduleCodes,
                Map<String, Set<String>> outboundFields, int maxRows,
                AiPolicy.RedactionMode redactionMode, String promptVersion,
                boolean enabled
        ) {
            this(providerId, moduleCodes, outboundFields,
                    Set.of("RECORD_QUERY"), Map.of(), Map.of(), maxRows,
                    AiPolicy.ConfirmationMode.REQUIRED, 600,
                    redactionMode, promptVersion, enabled);
        }

        public DraftCommand(
                long providerId, Set<String> moduleCodes,
                Map<String, Set<String>> outboundFields,
                Set<String> allowedOperations,
                Map<String, Set<String>> writableFields,
                int maxRows, AiPolicy.ConfirmationMode confirmationMode,
                int confirmationExpiresSeconds,
                AiPolicy.RedactionMode redactionMode, String promptVersion,
                boolean enabled
        ) {
            this(providerId, moduleCodes, outboundFields, allowedOperations,
                    writableFields, Map.of(), maxRows, confirmationMode,
                    confirmationExpiresSeconds, redactionMode, promptVersion,
                    enabled);
        }

    }
}
