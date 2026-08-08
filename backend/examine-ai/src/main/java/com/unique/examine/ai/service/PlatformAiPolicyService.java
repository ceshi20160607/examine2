package com.unique.examine.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.PlatformAiActor;
import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.repository.PlatformAiRepository;
import com.unique.examine.core.id.IdService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Service
public class PlatformAiPolicyService {
    private final PlatformAiRepository repository;
    private final IdService ids;
    private final Clock clock;
    private final ObjectMapper json;

    public PlatformAiPolicyService(
            PlatformAiRepository repository, IdService ids,
            Clock clock, ObjectMapper json) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.json = Objects.requireNonNull(json, "json");
    }

    public PlatformAiPolicy.Draft draft(PlatformAiActor actor) {
        actor.require("platform.ai.policy.manage");
        return repository.policyDraft().orElse(null);
    }

    @Transactional
    public PlatformAiPolicy.Draft save(
            PlatformAiActor actor, long expectedRevision, DraftCommand command) {
        actor.require("platform.ai.policy.manage");
        if (expectedRevision < 0) invalid("expectedVersion is invalid");
        Objects.requireNonNull(command, "command");
        var provider = repository.provider(command.providerId()).orElseThrow(() ->
                AiSupport.notFound("Platform AI provider does not exist"));
        var settings = command.settings();
        var now = clock.instant();
        var current = repository.policyDraft().orElse(null);
        if (current == null && expectedRevision != 0
                || current != null && current.revision() != expectedRevision) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_POLICY_VERSION_CONFLICT",
                    "Platform AI policy version is stale");
        }
        var revision = current == null ? 1 : current.revision() + 1;
        var id = current == null ? ids.nextId() : current.id();
        var value = new PlatformAiPolicy.Draft(
                id, revision, PlatformAiPolicy.DraftStatus.DRAFT, provider.id(),
                provider.version(), settings,
                draftHash(provider.id(), provider.version(), settings),
                current == null ? null : current.activeVersionId(), now,
                actor.accountId());
        if (current == null) {
            try {
                repository.insertPolicyDraft(value);
            } catch (DuplicateKeyException duplicate) {
                throw AiSupport.conflict(
                        "PLATFORM_AI_POLICY_VERSION_CONFLICT",
                        "Platform AI policy version is stale");
            }
        }
        else if (!repository.updatePolicyDraft(value, expectedRevision)) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_POLICY_VERSION_CONFLICT",
                    "Platform AI policy version is stale");
        }
        repository.insertAudit(event(
                actor, value.id(), "POLICY_SAVED", "OK", now));
        return value;
    }

    @Transactional
    public PlatformAiPolicy.Check check(PlatformAiActor actor) {
        actor.require("platform.ai.policy.manage");
        var draft = repository.policyDraft().orElseThrow(() ->
                AiSupport.notFound("Platform AI policy does not exist"));
        var issues = new ArrayList<PlatformAiPolicy.Issue>();
        var provider = repository.provider(draft.providerId()).orElse(null);
        if (provider == null || !provider.enabled()
                || provider.version() != draft.providerVersion()) {
            issues.add(new PlatformAiPolicy.Issue(
                    "PLATFORM_AI_PROVIDER_UNAVAILABLE", "providerId",
                    "The selected platform provider is disabled, missing or revised",
                    PlatformAiPolicy.Severity.BLOCKER));
        }
        if (!draft.settings().allowedOperations().containsAll(
                PlatformAiPolicy.SUPPORTED_OPERATIONS)) {
            issues.add(new PlatformAiPolicy.Issue(
                    "PLATFORM_AI_OPERATION_INCOMPLETE", "allowedOperations",
                    "The first platform journey requires query and switch guidance",
                    PlatformAiPolicy.Severity.BLOCKER));
        }
        var now = clock.instant();
        var check = new PlatformAiPolicy.Check(
                ids.nextId(), draft.id(), draft.revision(), draft.draftHash(),
                issues, now, actor.accountId());
        repository.insertPolicyCheck(check);
        var checked = copy(
                draft, draft.revision(),
                PlatformAiPolicy.DraftStatus.CHECKED, now, actor.accountId(),
                draft.activeVersionId());
        if (!repository.updatePolicyDraft(checked, draft.revision())) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_POLICY_VERSION_CONFLICT",
                    "Platform AI policy version changed during check");
        }
        // The check describes the exact pre-check draft revision/hash; the
        // checked state advances only the mutable CAS revision.
        repository.insertAudit(event(
                actor, draft.id(), "POLICY_CHECKED",
                check.valid() ? "OK" : "PLATFORM_AI_POLICY_CHECK_FAILED", now));
        return check;
    }

    @Transactional
    public PlatformAiPolicy.Version publish(
            PlatformAiActor actor, long expectedRevision, String idempotencyKey) {
        actor.require("platform.ai.policy.manage");
        idempotencyKey = requiredKey(idempotencyKey);
        var draft = repository.policyDraft().orElseThrow(() ->
                AiSupport.notFound("Platform AI policy does not exist"));
        if (draft.revision() != expectedRevision) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_POLICY_VERSION_CONFLICT",
                    "Platform AI policy version is stale");
        }
        var requestHash = AiSupport.sha256(
                draft.id() + ":" + expectedRevision + ":" + draft.draftHash());
        var replay = repository.publishReplay(draft.id(), idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw AiSupport.conflict(
                        "PLATFORM_AI_POLICY_REPLAY_CONFLICT",
                        "Platform AI publish key was used for another revision");
            }
            return repository.policyVersion(replay.get().versionId()).orElseThrow();
        }
        var check = repository.policyCheck(
                draft.id(), draft.revision()).filter(PlatformAiPolicy.Check::valid)
                .filter(value -> value.draftHash().equals(draft.draftHash()))
                .orElseThrow(() -> AiSupport.conflict(
                        "PLATFORM_AI_POLICY_CHECK_REQUIRED",
                        "Platform AI policy must pass its current check"));
        var provider = repository.provider(draft.providerId())
                .filter(value -> value.enabled()
                        && value.version() == draft.providerVersion())
                .orElseThrow(() -> AiSupport.unavailable(
                        "PLATFORM_AI_PROVIDER_UNAVAILABLE",
                        "Platform AI provider is unavailable"));
        var now = clock.instant();
        var versionId = ids.nextId();
        var versionNumber = repository.nextPolicyVersionNumber(draft.id());
        var snapshotHash = snapshotHash(
                draft.id(), versionNumber, provider.id(), provider.version(),
                provider.model(), draft.settings());
        var version = new PlatformAiPolicy.Version(
                versionId, draft.id(), versionNumber, provider.id(),
                provider.version(), provider.model(), draft.settings(),
                snapshotHash, now, actor.accountId());
        var published = copy(
                draft, draft.revision(),
                PlatformAiPolicy.DraftStatus.PUBLISHED, now, actor.accountId(),
                versionId);
        var publishedReplay = new PlatformAiPolicy.PublishReplay(
                ids.nextId(), draft.id(), idempotencyKey, requestHash,
                versionId, now);
        if (!repository.publishPolicy(
                draft, published, version, publishedReplay)) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_POLICY_VERSION_CONFLICT",
                    "Platform AI policy version changed during publish");
        }
        repository.insertAudit(event(
                actor, draft.id(), "POLICY_PUBLISHED", "OK", now));
        return version;
    }

    private static PlatformAiPolicy.Draft copy(
            PlatformAiPolicy.Draft value, long revision,
            PlatformAiPolicy.DraftStatus status, java.time.Instant now,
            long actorId, Long activeVersionId) {
        return new PlatformAiPolicy.Draft(
                value.id(), revision, status, value.providerId(),
                value.providerVersion(), value.settings(), value.draftHash(),
                activeVersionId, now, actorId);
    }

    private String draftHash(
            long providerId, long providerVersion,
            PlatformAiPolicy.Settings settings) {
        var value = settingsMap(settings);
        value.put("providerId", providerId);
        value.put("providerVersion", providerVersion);
        return hash(value);
    }

    private String snapshotHash(
            long policyId, int versionNumber, long providerId,
            long providerVersion, String model,
            PlatformAiPolicy.Settings settings) {
        var value = settingsMap(settings);
        value.put("policyId", policyId);
        value.put("versionNumber", versionNumber);
        value.put("providerId", providerId);
        value.put("providerVersion", providerVersion);
        value.put("model", model);
        return hash(value);
    }

    private static TreeMap<String, Object> settingsMap(
            PlatformAiPolicy.Settings value) {
        var result = new TreeMap<String, Object>();
        result.put("allowedOperations", new TreeSet<>(value.allowedOperations()));
        result.put("maxSystems", value.maxSystems());
        result.put("dailyRequestQuota", value.dailyRequestQuota());
        result.put("dailyTokenQuota", value.dailyTokenQuota());
        result.put("maxConcurrency", value.maxConcurrency());
        result.put("strictRedaction", value.strictRedaction());
        result.put("dataResidency", value.dataResidency().name());
        result.put("promptVersion", value.promptVersion());
        result.put("enabled", value.enabled());
        return result;
    }

    private String hash(Map<String, Object> value) {
        try {
            return AiSupport.sha256(json.writeValueAsBytes(value));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot hash platform AI policy", failure);
        }
    }

    private PlatformAiConversation.AuditEvent event(
            PlatformAiActor actor, long aggregateId, String type,
            String resultCode, java.time.Instant now) {
        return new PlatformAiConversation.AuditEvent(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), "POLICY", aggregateId, type, resultCode,
                actor.requestId(), actor.traceId(), AiSupport.sha256(
                "POLICY:" + aggregateId + ":" + type + ":" + resultCode), now);
    }

    private static String requiredKey(String value) {
        if (value == null || !value.strip().matches(
                "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_IDEMPOTENCY_KEY_INVALID",
                    "Platform AI Idempotency-Key is invalid");
        }
        return value.strip();
    }

    private static void invalid(String message) {
        throw AiSupport.invalid(
                "PLATFORM_AI_POLICY_REQUEST_INVALID", message);
    }

    public record DraftCommand(
            long providerId,
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
        public DraftCommand {
            if (providerId <= 0) {
                throw new IllegalArgumentException(
                        "Platform AI providerId must be positive");
            }
            allowedOperations = Set.copyOf(Objects.requireNonNull(
                    allowedOperations, "allowedOperations"));
            // Validate the public command at construction time.
            new PlatformAiPolicy.Settings(
                    allowedOperations, maxSystems, dailyRequestQuota,
                    dailyTokenQuota, maxConcurrency, strictRedaction,
                    dataResidency, promptVersion, enabled);
        }

        public PlatformAiPolicy.Settings settings() {
            return new PlatformAiPolicy.Settings(
                    allowedOperations, maxSystems, dailyRequestQuota,
                    dailyTokenQuota, maxConcurrency, strictRedaction,
                    dataResidency, promptVersion, enabled);
        }
    }
}
