package com.unique.examine.ai.service;

import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.repository.AiRepository;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import static com.unique.examine.ai.AiSupport.conflict;
import static com.unique.examine.ai.AiSupport.notFound;

@Service
public class AiProviderService {
    private final AiRepository repository;
    private final IdService ids;
    private final Clock clock;

    public AiProviderService(AiRepository repository, IdService ids, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<AiProvider> list(AiActor actor) {
        actor.require("ai.policy.manage");
        return repository.providers(actor.systemId(), actor.tenantId(), 0, 100);
    }

    @Transactional
    public AiProvider create(AiActor actor, Command command) {
        actor.require("ai.policy.manage");
        Objects.requireNonNull(command, "command");
        if (repository.providerByCode(
                actor.systemId(), actor.tenantId(), command.code()).isPresent()) {
            throw conflict("AI_PROVIDER_CODE_CONFLICT", "AI provider code already exists");
        }
        var now = clock.instant();
        var provider = new AiProvider(
                ids.nextId(), actor.systemId(), actor.tenantId(),
                command.code(), command.name(), command.baseUrl(), command.model(),
                command.secretRef(), command.timeoutSeconds(), command.enabled(),
                0, now, actor.memberId(), now, actor.memberId());
        repository.insertProvider(provider);
        return provider;
    }

    @Transactional
    public AiProvider update(
            AiActor actor,
            long providerId,
            long expectedVersion,
            Command command
    ) {
        actor.require("ai.policy.manage");
        Objects.requireNonNull(command, "command");
        var current = repository.provider(
                actor.systemId(), actor.tenantId(), providerId)
                .orElseThrow(() -> notFound("AI provider does not exist"));
        if (current.version() != expectedVersion) {
            throw conflict("AI_PROVIDER_VERSION_CONFLICT", "AI provider version is stale");
        }
        if (!current.code().equals(command.code())) {
            throw conflict("AI_PROVIDER_CODE_IMMUTABLE", "AI provider code is immutable");
        }
        var revised = current.revise(
                command.name(), command.baseUrl(), command.model(),
                command.secretRef(), command.timeoutSeconds(), command.enabled(),
                clock.instant(), actor.memberId());
        if (!repository.updateProvider(revised, expectedVersion)) {
            throw conflict("AI_PROVIDER_VERSION_CONFLICT", "AI provider version is stale");
        }
        return revised;
    }

    public record Command(
            String code,
            String name,
            String baseUrl,
            String model,
            String secretRef,
            int timeoutSeconds,
            boolean enabled
    ) {
    }
}
