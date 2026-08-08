package com.unique.examine.ai.service;

import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.PlatformAiActor;
import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiProvider;
import com.unique.examine.ai.repository.PlatformAiRepository;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

@Service
public class PlatformAiProviderService {
    private final PlatformAiRepository repository;
    private final IdService ids;
    private final Clock clock;

    public PlatformAiProviderService(
            PlatformAiRepository repository, IdService ids, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<PlatformAiProvider> list(PlatformAiActor actor) {
        actor.require("platform.ai.policy.manage");
        return repository.providers(0, 100);
    }

    @Transactional
    public PlatformAiProvider create(PlatformAiActor actor, Command command) {
        actor.require("platform.ai.policy.manage");
        Objects.requireNonNull(command, "command");
        if (repository.providerByCode(command.code()).isPresent()) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_PROVIDER_CODE_CONFLICT",
                    "Platform AI provider code already exists");
        }
        var now = clock.instant();
        var value = new PlatformAiProvider(
                ids.nextId(), command.code(), command.name(), command.baseUrl(),
                command.model(), command.secretRef(), command.timeoutSeconds(),
                command.enabled(), 0, now, actor.accountId(), now,
                actor.accountId());
        repository.insertProvider(value);
        repository.insertAudit(event(actor, value.id(), "PROVIDER_CREATED", now));
        return value;
    }

    @Transactional
    public PlatformAiProvider update(
            PlatformAiActor actor, long providerId, long expectedVersion,
            Command command) {
        actor.require("platform.ai.policy.manage");
        if (providerId <= 0 || expectedVersion < 0) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_PROVIDER_REQUEST_INVALID",
                    "Platform AI provider id or version is invalid");
        }
        Objects.requireNonNull(command, "command");
        var current = repository.provider(providerId).orElseThrow(() ->
                AiSupport.notFound("Platform AI provider does not exist"));
        if (current.version() != expectedVersion) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_PROVIDER_VERSION_CONFLICT",
                    "Platform AI provider version is stale");
        }
        if (!current.code().equals(command.code())) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_PROVIDER_CODE_IMMUTABLE",
                    "Platform AI provider code is immutable");
        }
        var now = clock.instant();
        var revised = current.revise(
                command.name(), command.baseUrl(), command.model(),
                command.secretRef(), command.timeoutSeconds(), command.enabled(),
                now, actor.accountId());
        if (!repository.updateProvider(revised, expectedVersion)) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_PROVIDER_VERSION_CONFLICT",
                    "Platform AI provider version is stale");
        }
        repository.insertAudit(event(actor, revised.id(), "PROVIDER_UPDATED", now));
        return revised;
    }

    private PlatformAiConversation.AuditEvent event(
            PlatformAiActor actor, long aggregateId, String type,
            java.time.Instant now) {
        return new PlatformAiConversation.AuditEvent(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), "PROVIDER", aggregateId, type, "OK",
                actor.requestId(), actor.traceId(), AiSupport.sha256(
                "PROVIDER:" + aggregateId + ":" + type + ":" + actor.accountId()),
                now);
    }

    public record Command(
            String code, String name, String baseUrl, String model,
            String secretRef, int timeoutSeconds, boolean enabled) { }
}
