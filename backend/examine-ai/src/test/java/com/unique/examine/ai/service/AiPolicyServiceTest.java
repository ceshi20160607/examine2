package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.service.AiPolicyService;
import com.unique.examine.core.ai.AiRecordPolicyCatalogFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPolicyServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void savesChecksPublishesImmutableSnapshotAndExactlyReplays() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(provider(true));
        AiRecordPolicyCatalogFacade catalog = request ->
                new AiRecordPolicyCatalogFacade.Result(
                        request.moduleCode(), "99", 41,
                        Set.of("status", "amount"));
        var service = new AiPolicyService(
                repository, catalog, new SequenceIdService(100),
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        var actor = actor(2);
        var command = command(Set.of("status", "amount"));

        var draft = service.save(actor, 0, command);
        var check = service.check(actor);
        var published = service.publish(actor, draft.revision(), "publish-one");
        var replayed = service.publish(actor, draft.revision(), "publish-one");

        assertThat(check.valid()).isTrue();
        assertThat(published).isEqualTo(replayed);
        assertThat(published.allowedOperations()).containsExactly("RECORD_QUERY");
        assertThat(published.outboundFields())
                .containsEntry("orders", Set.of("status", "amount"));
        assertThat(published.providerVersion()).isEqualTo(3);
        assertThat(repository.replays).hasSize(1);

        var revised = service.save(actor, draft.revision(), command(Set.of("status")));
        service.check(actor);
        assertThatThrownBy(() -> service.publish(
                actor, revised.revision(), "publish-one"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_POLICY_REPLAY_CONFLICT");
    }

    @Test
    void checkBlocksDisabledProviderAndUnreadableFields() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(provider(false));
        AiRecordPolicyCatalogFacade catalog = request ->
                new AiRecordPolicyCatalogFacade.Result(
                        request.moduleCode(), "99", 41, Set.of("status"));
        var service = new AiPolicyService(
                repository, catalog, new SequenceIdService(200),
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());

        var draft = service.save(actor(2), 0, command(Set.of("status", "amount")));
        var check = service.check(actor(2));

        assertThat(check.valid()).isFalse();
        assertThat(check.issues()).extracting(AiPolicy.Issue::code)
                .containsExactlyInAnyOrder(
                        "AI_PROVIDER_UNAVAILABLE", "AI_FIELD_UNREADABLE");
        assertThatThrownBy(() -> service.publish(
                actor(2), draft.revision(), "blocked"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_POLICY_CHECK_FAILED");
    }

    @Test
    void repositoryLookupAndCommandsRemainTenantScoped() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(provider(true));
        var service = new AiPolicyService(
                repository,
                request -> new AiRecordPolicyCatalogFacade.Result(
                        request.moduleCode(), "99", 1, Set.of("status")),
                new SequenceIdService(300), Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper());

        assertThatThrownBy(() -> service.save(actor(3), 0, command(Set.of("status"))))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_POLICY_INVALID");
    }

    @Test
    void publishesIndependentCreateUpdateFieldScopeAndConfirmationSnapshot() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(provider(true));
        var service = new AiPolicyService(
                repository,
                request -> new AiRecordPolicyCatalogFacade.Result(
                        request.moduleCode(), "99", 41, Set.of("status")),
                new SequenceIdService(350), Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper());
        var actor = new AiActor(5,
                1, 2, 7, Set.of(
                "ai.policy.manage", "module.orders.create",
                "module.orders.update"), 41, "request-1", "trace-1");
        var command = new AiPolicyService.DraftCommand(
                80, Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "RECORD_CREATE", "RECORD_UPDATE"),
                Map.of("orders", Set.of("amount")), 10,
                AiPolicy.ConfirmationMode.REQUIRED, 900,
                AiPolicy.RedactionMode.STRICT, "v2", true);

        var draft = service.save(actor, 0, command);
        assertThat(service.check(actor).valid()).isTrue();
        var version = service.publish(actor, draft.revision(), "write-policy");

        assertThat(version.allowedOperations()).containsExactlyInAnyOrder(
                "RECORD_QUERY", "RECORD_CREATE", "RECORD_UPDATE");
        assertThat(version.writableFields())
                .containsEntry("orders", Set.of("amount"));
        assertThat(version.outboundFields())
                .containsEntry("orders", Set.of("status"));
        assertThat(version.confirmationMode())
                .isEqualTo(AiPolicy.ConfirmationMode.REQUIRED);
        assertThat(version.confirmationExpiresSeconds()).isEqualTo(900);
    }

    @Test
    void publishesAiFillTargetScopeInTheImmutablePolicySnapshot() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(provider(true));
        var service = new AiPolicyService(
                repository,
                request -> new AiRecordPolicyCatalogFacade.Result(
                        request.moduleCode(), "99", 41,
                        Set.of("status", "ai_summary")),
                new SequenceIdService(375), Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper());
        var actor = new AiActor(5,
                1, 2, 7, Set.of(
                "ai.policy.manage", "module.orders.update"),
                41, "request-1", "trace-1");
        var command = new AiPolicyService.DraftCommand(
                80, Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "AI_FILL"), Map.of(),
                Map.of("orders", Set.of("ai_summary")), 10,
                AiPolicy.ConfirmationMode.REQUIRED, 600,
                AiPolicy.RedactionMode.STRICT, "v3", true);

        var draft = service.save(actor, 0, command);
        assertThat(service.check(actor).valid()).isTrue();
        var version = service.publish(actor, draft.revision(), "fill-policy");

        assertThat(version.allowedOperations())
                .containsExactlyInAnyOrder("RECORD_QUERY", "AI_FILL");
        assertThat(version.writableFields()).isEmpty();
        assertThat(version.fillFields())
                .containsEntry("orders", Set.of("ai_summary"));
    }

    @Test
    void providerRejectsPlaintextCredentials() {
        assertThatThrownBy(() -> new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-read", "plaintext-api-key", 10, true, 0,
                NOW, 7, NOW, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SecretRef");
    }

    private static AiPolicyService.DraftCommand command(Set<String> fields) {
        return new AiPolicyService.DraftCommand(
                80, Set.of("orders"), Map.of("orders", fields), 10,
                AiPolicy.RedactionMode.STRICT, "v1", true);
    }

    private static AiProvider provider(boolean enabled) {
        return new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-read", "vault://tenant/openai", 10, enabled, 3,
                NOW, 7, NOW, 7);
    }

    private static AiActor actor(long tenantId) {
        return new AiActor(5,
                1, tenantId, 7, Set.of("ai.policy.manage"),
                41, "request-1", "trace-1");
    }
}
